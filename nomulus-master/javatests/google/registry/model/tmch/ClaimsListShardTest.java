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

package google.registry.model.tmch;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableMap;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import google.registry.model.tmch.ClaimsListShard.ClaimsListRevision;
import google.registry.model.tmch.ClaimsListShard.UnshardedSaveException;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.InjectRule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ClaimsListShard}. */
@RunWith(JUnit4.class)
public class ClaimsListShardTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  @Before
  public void before() throws Exception {
    inject.setStaticField(ClaimsListShard.class, "shardSize", 10);
  }

  @Test
  public void test_unshardedSaveFails() throws Exception {
    thrown.expect(UnshardedSaveException.class);
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ClaimsListShard claimsList =
            ClaimsListShard.create(ofy().getTransactionTime(), ImmutableMap.of("a", "b"));
        claimsList.id = 1;  // Without an id this won't save anyways.
        claimsList.parent = ClaimsListRevision.createKey();
        ofy().saveWithoutBackup().entity(claimsList).now();
      }});
  }

  @Test
  public void testGet_safelyLoadsEmptyClaimsList_whenNoShardsExist() throws Exception {
    assertThat(ClaimsListShard.get().labelsToKeys).isEmpty();
    assertThat(ClaimsListShard.get().creationTime).isEqualTo(START_OF_TIME);
  }

  @Test
  public void test_savesAndGets_withSharding() throws Exception {
    // Create a ClaimsList that will need 4 shards to save.
    Map<String, String> labelsToKeys = new HashMap<>();
    for (int i = 0; i <= ClaimsListShard.shardSize * 3; i++) {
      labelsToKeys.put(Integer.toString(i), Integer.toString(i));
    }
    DateTime now = DateTime.now(UTC);
    // Save it with sharding, and make sure that reloading it works.
    ClaimsListShard unsharded = ClaimsListShard.create(now, ImmutableMap.copyOf(labelsToKeys));
    unsharded.save();
    assertThat(ClaimsListShard.get().labelsToKeys).isEqualTo(unsharded.labelsToKeys);
    List<ClaimsListShard> shards1 = ofy().load().type(ClaimsListShard.class).list();
    assertThat(shards1).hasSize(4);
    assertThat(ClaimsListShard.get().getClaimKey("1")).isEqualTo("1");
    assertThat(ClaimsListShard.get().getClaimKey("a")).isNull();
    assertThat(ClaimsListShard.getCurrentRevision()).isEqualTo(shards1.get(0).parent);

    // Create a smaller ClaimsList that will need only 2 shards to save.
    labelsToKeys = new HashMap<>();
    for (int i = 0; i <= ClaimsListShard.shardSize; i++) {
      labelsToKeys.put(Integer.toString(i), Integer.toString(i));
    }
    unsharded = ClaimsListShard.create(now.plusDays(1), ImmutableMap.copyOf(labelsToKeys));
    unsharded.save();
    ofy().clearSessionCache();
    assertThat(ClaimsListShard.get().labelsToKeys).hasSize(unsharded.labelsToKeys.size());
    assertThat(ClaimsListShard.get().labelsToKeys).isEqualTo(unsharded.labelsToKeys);
    List<ClaimsListShard> shards2 = ofy().load().type(ClaimsListShard.class).list();
    assertThat(shards2).hasSize(2);

    // Expect that the old revision is deleted.
    assertThat(ClaimsListShard.getCurrentRevision()).isEqualTo(shards2.get(0).parent);
  }

  /**
   * Returns a created claims list shard with the specified parent key for testing purposes only.
   */
  public static ClaimsListShard createTestClaimsListShard(
      DateTime creationTime,
      ImmutableMap<String, String> labelsToKeys,
      Key<ClaimsListRevision> revision) {
    ClaimsListShard claimsList = ClaimsListShard.create(creationTime, labelsToKeys);
    claimsList.isShard = true;
    claimsList.parent = revision;
    return claimsList;
  }
}
