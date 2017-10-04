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
import static com.google.common.base.Strings.isNullOrEmpty;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.registry.Registries.getTlds;

import com.google.common.base.Optional;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;
import google.registry.model.Buildable;
import google.registry.model.ImmutableObject;
import google.registry.model.common.EntityGroupRoot;
import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservedList.ReservedListEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/**
 * Base class for {@link ReservedList} and {@link PremiumList} objects stored in Datastore.
 *
 * @param <T> The type of the root value being listed, e.g. {@link ReservationType}.
 * @param <R> The type of domain label entry being listed, e.g. {@link ReservedListEntry} (note,
 *            must subclass {@link DomainLabelEntry}.
 */
public abstract class BaseDomainLabelList<T extends Comparable<?>, R extends DomainLabelEntry<T, ?>>
    extends ImmutableObject implements Buildable {

  @Id
  String name;

  @Parent
  Key<EntityGroupRoot> parent = getCrossTldKey();

  DateTime creationTime;

  DateTime lastUpdateTime;

  String description;

  public String getName() {
    return name;
  }

  public DateTime getCreationTime() {
    return creationTime;
  }

  public DateTime getLastUpdateTime() {
    return lastUpdateTime;
  }

  /**
   * Turns the list CSV data into a map of labels to parsed data of type R.
   *
   * @param lines the CSV file, line by line
   */
  public ImmutableMap<String, R> parse(Iterable<String> lines) {
    Map<String, R> labelsToEntries = new HashMap<>();
    Multiset<String> duplicateLabels = HashMultiset.create();
    for (String line : lines) {
      R entry = createFromLine(line);
      if (entry == null) {
        continue;
      }
      String label = entry.getLabel();
      // Check if the label was already processed for this list (which is an error), and if so,
      // accumulate it so that a list of all duplicates can be thrown.
      if (labelsToEntries.containsKey(label)) {
        duplicateLabels.add(label, duplicateLabels.contains(label) ? 1 : 2);
      } else {
        labelsToEntries.put(label, entry);
      }
    }
    if (!duplicateLabels.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "List '%s' cannot contain duplicate labels. Dupes (with counts) were: %s",
              name, duplicateLabels));
    }
    return ImmutableMap.copyOf(labelsToEntries);
  }

  /**
   * Creates a new entry in the label list from the given line of text. Returns null if the line is
   * empty and does not contain an entry.
   *
   * @throws IllegalArgumentException if the line cannot be parsed correctly.
   */
  @Nullable
  abstract R createFromLine(String line);

  /**
   * Helper function to extract the comment from an input line. Returns a list containing the line
   * (sans comment) and the comment (in that order). If the line was blank or empty, then this
   * method returns an empty list.
   */
  protected static List<String> splitOnComment(String line) {
    String comment = "";
    int index = line.indexOf('#');
    if (index != -1) {
      comment = line.substring(index + 1).trim();
      line = line.substring(0, index).trim();
    } else {
      line = line.trim();
    }
    return line.isEmpty() ? ImmutableList.<String>of() : ImmutableList.<String>of(line, comment);
  }

  /** Gets the names of the tlds that reference this list. */
  public final ImmutableSet<String> getReferencingTlds() {
    ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<>();
    Key<? extends BaseDomainLabelList<?, ?>> key = Key.create(this);
    for (String tld : getTlds()) {
      if (refersToKey(Registry.get(tld), key)) {
        builder.add(tld);
      }
    }
    return builder.build();
  }

  protected abstract boolean refersToKey(
      Registry registry, Key<? extends BaseDomainLabelList<?, ?>> key);

  protected static <R> Optional<R> getFromCache(String listName, LoadingCache<String, R> cache) {
    try {
      return Optional.of(cache.get(listName));
    } catch (InvalidCacheLoadException e) {
      return Optional.absent();
    } catch (ExecutionException e) {
      throw new UncheckedExecutionException("Could not retrieve list named " + listName, e);
    }
  }

  /** Base builder for derived classes of {@link BaseDomainLabelList}. */
  public abstract static class Builder<T extends BaseDomainLabelList<?, ?>, B extends Builder<T, ?>>
      extends GenericBuilder<T, B> {

    public Builder() {}

    protected Builder(T instance) {
      super(instance);
    }

    public B setName(String name) {
      getInstance().name = name;
      return thisCastToDerived();
    }

    public B setCreationTime(DateTime creationTime) {
      getInstance().creationTime = creationTime;
      return thisCastToDerived();
    }

    public B setLastUpdateTime(DateTime lastUpdateTime) {
      getInstance().lastUpdateTime = lastUpdateTime;
      return thisCastToDerived();
    }

    public B setDescription(String description) {
      getInstance().description = description;
      return thisCastToDerived();
    }

    @Override
    public T build() {
      checkArgument(!isNullOrEmpty(getInstance().name), "List must have a name");
      return super.build();
    }
  }
}
