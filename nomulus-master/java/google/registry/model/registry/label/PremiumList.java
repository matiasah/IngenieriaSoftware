// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.model.registry.label;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.hash.Funnels.unencodedCharsFunnel;
import static google.registry.config.RegistryConfig.getDomainLabelListCacheDuration;
import static google.registry.config.RegistryConfig.getSingletonCachePersistDuration;
import static google.registry.config.RegistryConfig.getStaticPremiumListMaxCachedEntries;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.allocateId;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.BloomFilter;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;
import google.registry.model.Buildable;
import google.registry.model.ImmutableObject;
import google.registry.model.annotations.ReportedOn;
import google.registry.model.registry.Registry;
import google.registry.util.NonFinalForTesting;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.joda.money.Money;
import org.joda.time.Duration;

/**
 * A premium list entity, persisted to Datastore, that is used to check domain label prices.
 */
@ReportedOn
@Entity
public final class PremiumList extends BaseDomainLabelList<Money, PremiumList.PremiumListEntry> {

  /** Stores the revision key for the set of currently used premium list entry entities. */
  Key<PremiumListRevision> revisionKey;

  /** Virtual parent entity for premium list entry entities associated with a single revision. */
  @ReportedOn
  @Entity
  public static class PremiumListRevision extends ImmutableObject {

    @Parent
    Key<PremiumList> parent;

    @Id
    long revisionId;

    /**
     * A Bloom filter that is used to determine efficiently and quickly whether a label might be
     * premium.
     *
     * <p>If the label might be premium, then the premium list entry must be loaded by key and
     * checked for existence.  Otherwise, we know it's not premium, and no Datastore load is
     * required.
     */
    private BloomFilter<String> probablePremiumLabels;

    /**
     * Get the Bloom filter.
     *
     * <p>Note that this is not a copy, but the mutable object itself, because copying would be
     * expensive. You probably should not modify the filter unless you know what you're doing.
     */
    public BloomFilter<String> getProbablePremiumLabels() {
      return probablePremiumLabels;
    }

    /**
     * The maximum size of the Bloom filter.
     *
     * <p>Trying to set it any larger will throw an error, as we know it won't fit into a Datastore
     * entity. We use 90% of the 1 MB Datastore limit to leave some wriggle room for the other
     * fields and miscellaneous entity serialization overhead.
     */
    private static final int MAX_BLOOM_FILTER_BYTES = 900000;

    /** Returns a new PremiumListRevision for the given key and premium list map. */
    @VisibleForTesting
    public static PremiumListRevision create(PremiumList parent, Set<String> premiumLabels) {
      PremiumListRevision revision = new PremiumListRevision();
      revision.parent = Key.create(parent);
      revision.revisionId = allocateId();
      // All premium list labels are already punycoded, so don't perform any further character
      // encoding on them.
      revision.probablePremiumLabels =
          BloomFilter.create(unencodedCharsFunnel(), premiumLabels.size());
      for (String label : premiumLabels) {
        revision.probablePremiumLabels.put(label);
      }
      try {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        revision.probablePremiumLabels.writeTo(bos);
        checkArgument(bos.size() <= MAX_BLOOM_FILTER_BYTES,
            "Too many premium labels were specified; Bloom filter exceeds max entity size");
      } catch (IOException e) {
        throw new IllegalStateException("Could not serialize premium labels Bloom filter", e);
      }
      return revision;
    }
  }

  /**
   * In-memory cache for premium lists.
   *
   * <p>This is cached for a shorter duration because we need to periodically reload this entity to
   * check if a new revision has been published, and if so, then use that.
   */
  static final LoadingCache<String, PremiumList> cachePremiumLists =
      CacheBuilder.newBuilder()
          .expireAfterWrite(getDomainLabelListCacheDuration().getMillis(), MILLISECONDS)
          .build(new CacheLoader<String, PremiumList>() {
            @Override
            public PremiumList load(final String listName) {
              return ofy().doTransactionless(new Work<PremiumList>() {
                @Override
                public PremiumList run() {
                  return ofy().load()
                      .type(PremiumList.class)
                      .parent(getCrossTldKey())
                      .id(listName)
                      .now();
                }});
            }});

  /**
   * In-memory cache for {@link PremiumListRevision}s, used for retrieving Bloom filters quickly.
   *
   * <p>This is cached for a long duration (essentially indefinitely) because a given
   * {@link PremiumListRevision} is immutable and cannot ever be changed once created, so its cache
   * need not ever expire.
   */
  static final LoadingCache<Key<PremiumListRevision>, PremiumListRevision>
      cachePremiumListRevisions =
          CacheBuilder.newBuilder()
              .expireAfterWrite(getSingletonCachePersistDuration().getMillis(), MILLISECONDS)
              .build(
                  new CacheLoader<Key<PremiumListRevision>, PremiumListRevision>() {
                    @Override
                    public PremiumListRevision load(final Key<PremiumListRevision> revisionKey) {
                      return ofy()
                          .doTransactionless(
                              new Work<PremiumListRevision>() {
                                @Override
                                public PremiumListRevision run() {
                                  return ofy().load().key(revisionKey).now();
                                }});
                    }});

  /**
   * In-memory cache for {@link PremiumListEntry}s for a given label and {@link PremiumListRevision}
   *
   * <p>Because the PremiumList itself makes up part of the PremiumListRevision's key, this is
   * specific to a given premium list. Premium list entries might not be present, as indicated by
   * the Optional wrapper, and we want to cache that as well.
   *
   * <p>This is cached for a long duration (essentially indefinitely) because a given {@link
   * PremiumListRevision} and its child {@link PremiumListEntry}s are immutable and cannot ever be
   * changed once created, so the cache need not ever expire.
   *
   * <p>A maximum size is set here on the cache because it can potentially grow too big to fit in
   * memory if there are a large number of distinct premium list entries being queried (both those
   * that exist, as well as those that might exist according to the Bloom filter, must be cached).
   * The entries judged least likely to be accessed again will be evicted first.
   */
  @NonFinalForTesting @VisibleForTesting
  static LoadingCache<Key<PremiumListEntry>, Optional<PremiumListEntry>> cachePremiumListEntries =
      createCachePremiumListEntries(getSingletonCachePersistDuration());

  @VisibleForTesting
  static LoadingCache<Key<PremiumListEntry>, Optional<PremiumListEntry>>
      createCachePremiumListEntries(Duration cachePersistDuration) {
    return CacheBuilder.newBuilder()
        .expireAfterWrite(cachePersistDuration.getMillis(), MILLISECONDS)
        .maximumSize(getStaticPremiumListMaxCachedEntries())
        .build(
            new CacheLoader<Key<PremiumListEntry>, Optional<PremiumListEntry>>() {
              @Override
              public Optional<PremiumListEntry> load(final Key<PremiumListEntry> entryKey) {
                return ofy()
                    .doTransactionless(
                        new Work<Optional<PremiumListEntry>>() {
                          @Override
                          public Optional<PremiumListEntry> run() {
                            return Optional.fromNullable(ofy().load().key(entryKey).now());
                          }
                        });
              }
            });
  }

  @VisibleForTesting
  public Key<PremiumListRevision> getRevisionKey() {
    return revisionKey;
  }

  /** Returns the PremiumList with the specified name. */
  public static Optional<PremiumList> get(String name) {
    try {
      return Optional.of(cachePremiumLists.get(name));
    } catch (InvalidCacheLoadException e) {
      return Optional.<PremiumList> absent();
    } catch (ExecutionException e) {
      throw new UncheckedExecutionException("Could not retrieve premium list named " + name, e);
    }
  }

  /**
   * A premium list entry entity, persisted to Datastore.  Each instance represents the price of a
   * single label on a given TLD.
   */
  @ReportedOn
  @Entity
  public static class PremiumListEntry extends DomainLabelEntry<Money, PremiumListEntry>
      implements Buildable {

    @Parent
    Key<PremiumListRevision> parent;

    Money price;

    @Override
    public Money getValue() {
      return price;
    }

    @Override
    public Builder asBuilder() {
      return new Builder(clone(this));
    }

    /** A builder for constructing {@link PremiumListEntry} objects, since they are immutable. */
    public static class Builder extends DomainLabelEntry.Builder<PremiumListEntry, Builder> {
      public Builder() {}

      private Builder(PremiumListEntry instance) {
        super(instance);
      }

      public Builder setParent(Key<PremiumListRevision> parentKey) {
        getInstance().parent = parentKey;
        return this;
      }

      public Builder setPrice(Money price) {
        getInstance().price = price;
        return this;
      }
    }
  }

  @Override
  @Nullable
  PremiumListEntry createFromLine(String originalLine) {
    List<String> lineAndComment = splitOnComment(originalLine);
    if (lineAndComment.isEmpty()) {
      return null;
    }
    String line = lineAndComment.get(0);
    String comment = lineAndComment.get(1);
    List<String> parts = Splitter.on(',').trimResults().splitToList(line);
    checkArgument(parts.size() == 2, "Could not parse line in premium list: %s", originalLine);
    return new PremiumListEntry.Builder()
        .setLabel(parts.get(0))
        .setPrice(Money.parse(parts.get(1)))
        .setComment(comment)
        .build();
  }

  @Override
  public boolean refersToKey(Registry registry, Key<? extends BaseDomainLabelList<?, ?>> key) {
    return Objects.equals(registry.getPremiumList(), key);
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for constructing {@link PremiumList} objects, since they are immutable.  */
  public static class Builder extends BaseDomainLabelList.Builder<PremiumList, Builder> {

    public Builder() {}

    private Builder(PremiumList instance) {
      super(instance);
    }

    public Builder setRevision(Key<PremiumListRevision> revision) {
      getInstance().revisionKey = revision;
      return this;
    }

    @Override
    public PremiumList build() {
      return super.build();
    }
  }
}
