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

package google.registry.backup;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verifyNotNull;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Lists.partition;
import static google.registry.backup.BackupUtils.GcsMetadataKeys.LOWER_BOUND_CHECKPOINT;
import static google.registry.backup.BackupUtils.GcsMetadataKeys.NUM_TRANSACTIONS;
import static google.registry.backup.BackupUtils.GcsMetadataKeys.UPPER_BOUND_CHECKPOINT;
import static google.registry.backup.BackupUtils.serializeEntity;
import static google.registry.model.ofy.CommitLogBucket.getBucketKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static google.registry.util.DateTimeUtils.isAtOrAfter;
import static google.registry.util.FormattingLogger.getLoggerForCallerClass;
import static java.nio.channels.Channels.newOutputStream;
import static java.util.Arrays.asList;

import com.google.appengine.tools.cloudstorage.GcsFileOptions;
import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.appengine.tools.cloudstorage.GcsService;
import com.google.common.base.Function;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.config.RegistryConfig.Config;
import google.registry.model.ImmutableObject;
import google.registry.model.ofy.CommitLogBucket;
import google.registry.model.ofy.CommitLogCheckpoint;
import google.registry.model.ofy.CommitLogManifest;
import google.registry.model.ofy.CommitLogMutation;
import google.registry.request.Action;
import google.registry.request.Parameter;
import google.registry.request.auth.Auth;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.DateTime;

/** Action that exports the diff between two commit log checkpoints to GCS. */
@Action(
  path = ExportCommitLogDiffAction.PATH,
  method = Action.Method.POST,
  automaticallyPrintOk = true,
  auth = Auth.AUTH_INTERNAL_ONLY
)
public final class ExportCommitLogDiffAction implements Runnable {

  private static final FormattingLogger logger = getLoggerForCallerClass();

  static final String PATH = "/_dr/task/exportCommitLogDiff";
  static final String UPPER_CHECKPOINT_TIME_PARAM = "upperCheckpointTime";
  static final String LOWER_CHECKPOINT_TIME_PARAM = "lowerCheckpointTime";

  public static final String DIFF_FILE_PREFIX = "commit_diff_until_";

  @Inject GcsService gcsService;
  @Inject @Config("commitLogGcsBucket") String gcsBucket;
  @Inject @Config("commitLogDiffExportBatchSize") int batchSize;
  @Inject @Parameter(LOWER_CHECKPOINT_TIME_PARAM) DateTime lowerCheckpointTime;
  @Inject @Parameter(UPPER_CHECKPOINT_TIME_PARAM) DateTime upperCheckpointTime;
  @Inject ExportCommitLogDiffAction() {}

  @Override
  public void run() {
    checkArgument(isAtOrAfter(lowerCheckpointTime, START_OF_TIME));
    checkArgument(lowerCheckpointTime.isBefore(upperCheckpointTime));
    // Load the boundary checkpoints - lower is exclusive and may not exist (on the first export,
    // when lowerCheckpointTime is START_OF_TIME), whereas the upper is inclusive and must exist.
    CommitLogCheckpoint lowerCheckpoint = lowerCheckpointTime.isAfter(START_OF_TIME)
        ? verifyNotNull(ofy().load().key(CommitLogCheckpoint.createKey(lowerCheckpointTime)).now())
        : null;
    CommitLogCheckpoint upperCheckpoint =
        verifyNotNull(ofy().load().key(CommitLogCheckpoint.createKey(upperCheckpointTime)).now());

    // Load the keys of all the manifests to include in this diff.
    List<Key<CommitLogManifest>> sortedKeys = loadAllDiffKeys(lowerCheckpoint, upperCheckpoint);
    logger.infofmt("Found %d manifests to export", sortedKeys.size());
    // Open an output channel to GCS, wrapped in a stream for convenience.
    try (OutputStream gcsStream = newOutputStream(gcsService.createOrReplace(
        new GcsFilename(gcsBucket, DIFF_FILE_PREFIX + upperCheckpointTime),
        new GcsFileOptions.Builder()
            .addUserMetadata(LOWER_BOUND_CHECKPOINT, lowerCheckpointTime.toString())
            .addUserMetadata(UPPER_BOUND_CHECKPOINT, upperCheckpointTime.toString())
            .addUserMetadata(NUM_TRANSACTIONS, Integer.toString(sortedKeys.size()))
            .build()))) {
      // Export the upper checkpoint itself.
      serializeEntity(upperCheckpoint, gcsStream);
      // If there are no manifests to export, stop early, now that we've written out the file with
      // the checkpoint itself (which is needed for restores, even if it's empty).
      if (sortedKeys.isEmpty()) {
        return;
      }
      // Export to GCS in chunks, one per fixed batch of commit logs.  While processing one batch,
      // asynchronously load the entities for the next one.
      List<List<Key<CommitLogManifest>>> keyChunks = partition(sortedKeys, batchSize);
      // Objectify's map return type is asynchronous. Calling .values() will block until it loads.
      Map<?, CommitLogManifest> nextChunkToExport = ofy().load().keys(keyChunks.get(0));
      for (int i = 0; i < keyChunks.size(); i++) {
        // Force the async load to finish.
        Collection<CommitLogManifest> chunkValues = nextChunkToExport.values();
        logger.infofmt("Loaded %d manifests", chunkValues.size());
        // Since there is no hard bound on how much data this might be, take care not to let the
        // Objectify session cache fill up and potentially run out of memory. This is the only safe
        // point to do this since at this point there is no async load in progress.
        ofy().clearSessionCache();
        // Kick off the next async load, which can happen in parallel to the current GCS export.
        if (i + 1 < keyChunks.size()) {
          nextChunkToExport = ofy().load().keys(keyChunks.get(i + 1));
        }
        exportChunk(gcsStream, chunkValues);
        logger.infofmt("Exported %d manifests", chunkValues.size());
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    logger.infofmt("Exported %d manifests in total", sortedKeys.size());
  }

  /**
   * Loads all the diff keys, sorted in a transaction-consistent chronological order.
   *
   * @param lowerCheckpoint exclusive lower bound on keys in this diff, or null if no lower bound
   * @param upperCheckpoint inclusive upper bound on keys in this diff
   */
  private ImmutableList<Key<CommitLogManifest>> loadAllDiffKeys(
      @Nullable final CommitLogCheckpoint lowerCheckpoint,
      final CommitLogCheckpoint upperCheckpoint) {
    // Fetch the keys (no data) between these checkpoints, and sort by timestamp. This ordering is
    // transaction-consistent by virtue of our checkpoint strategy and our customized Ofy; see
    // CommitLogCheckpointStrategy for the proof. We break ties by sorting on bucket ID to ensure
    // a deterministic order.
    return FluentIterable.from(upperCheckpoint.getBucketTimestamps().keySet())
        .transformAndConcat(new Function<Integer, Iterable<Key<CommitLogManifest>>>() {
          @Override
          public Iterable<Key<CommitLogManifest>> apply(Integer bucketNum) {
            return loadDiffKeysFromBucket(lowerCheckpoint, upperCheckpoint, bucketNum);
          }})
        .toSortedList(new Comparator<Key<CommitLogManifest>>() {
          @Override
          public int compare(Key<CommitLogManifest> a, Key<CommitLogManifest> b) {
            // Compare keys by timestamp (which is encoded in the id as millis), then by bucket id.
            return ComparisonChain.start()
                .compare(a.getId(), b.getId())
                .compare(a.getParent().getId(), b.getParent().getId())
                .result();
          }});
  }

  /**
   * Loads the diff keys for one bucket.
   *
   * @param lowerCheckpoint exclusive lower bound on keys in this diff, or null if no lower bound
   * @param upperCheckpoint inclusive upper bound on keys in this diff
   * @param bucketNum the bucket to load diff keys from
   */
  private Iterable<Key<CommitLogManifest>> loadDiffKeysFromBucket(
      @Nullable CommitLogCheckpoint lowerCheckpoint,
      CommitLogCheckpoint upperCheckpoint,
      int bucketNum) {
    // If no lower checkpoint exists, use START_OF_TIME as the effective exclusive lower bound.
    DateTime lowerCheckpointBucketTime = lowerCheckpoint == null
        ? START_OF_TIME
        : lowerCheckpoint.getBucketTimestamps().get(bucketNum);
    // Since START_OF_TIME=0 is not a valid id in a key, add 1 to both bounds. Then instead of
    // loading lowerBound < x <= upperBound, we can load lowerBound <= x < upperBound.
    DateTime lowerBound = lowerCheckpointBucketTime.plusMillis(1);
    DateTime upperBound = upperCheckpoint.getBucketTimestamps().get(bucketNum).plusMillis(1);
    // If the lower and upper bounds are equal, there can't be any results, so skip the query.
    if (lowerBound.equals(upperBound)) {
      return ImmutableSet.of();
    }
    Key<CommitLogBucket> bucketKey = getBucketKey(bucketNum);
    return ofy().load()
        .type(CommitLogManifest.class)
        .ancestor(bucketKey)
        .filterKey(">=", CommitLogManifest.createKey(bucketKey, lowerBound))
        .filterKey("<", CommitLogManifest.createKey(bucketKey, upperBound))
        .keys();
  }

  /** Writes a chunks-worth of manifests and associated mutations to GCS. */
  private void exportChunk(OutputStream gcsStream, Collection<CommitLogManifest> chunk)
      throws IOException {
    // Kickoff async loads for all the manifests in the chunk.
    ImmutableList.Builder<Iterable<? extends ImmutableObject>> entities =
        new ImmutableList.Builder<>();
    for (CommitLogManifest manifest : chunk) {
      entities.add(asList(manifest));
      entities.add(ofy().load().type(CommitLogMutation.class).ancestor(manifest));
    }
    for (ImmutableObject entity : concat(entities.build())) {
      serializeEntity(entity, gcsStream);
    }
  }
}
