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

package google.registry.model;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadAtPointInTime;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistResourceWithCommitLog;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.time.DateTimeZone.UTC;

import google.registry.model.host.HostResource;
import google.registry.model.ofy.Ofy;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.InjectRule;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link EppResourceUtils}. */
@RunWith(JUnit4.class)
public class EppResourceUtilsTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .withTaskQueue()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final FakeClock clock = new FakeClock(DateTime.now(UTC));

  @Before
  public void init() throws Exception {
    createTld("tld");
    inject.setStaticField(Ofy.class, "clock", clock);
  }

  @Test
  public void testLoadAtPointInTime_beforeCreated_returnsNull() throws Exception {
    clock.advanceOneMilli();
    // Don't save a commit log, we shouldn't need one.
    HostResource host = persistResource(
        newHostResource("ns1.cat.tld").asBuilder()
            .setCreationTimeForTest(clock.nowUtc())
            .build());
    assertThat(loadAtPointInTime(host, clock.nowUtc().minus(1)).now()).isNull();
  }

  @Test
  public void testLoadAtPointInTime_atOrAfterLastAutoUpdateTime_returnsResource() throws Exception {
    clock.advanceOneMilli();
    // Don't save a commit log, we shouldn't need one.
    HostResource host = persistResource(
        newHostResource("ns1.cat.tld").asBuilder()
            .setCreationTimeForTest(START_OF_TIME)
            .build());
    assertThat(loadAtPointInTime(host, clock.nowUtc()).now()).isEqualTo(host);
  }

  @Test
  public void testLoadAtPointInTime_usingIntactRevisionHistory_returnsMutationValue()
      throws Exception {
    clock.advanceOneMilli();
    // Save resource with a commit log that we can read in later as a revisions map value.
    HostResource oldHost = persistResourceWithCommitLog(
        newHostResource("ns1.cat.tld").asBuilder()
            .setCreationTimeForTest(START_OF_TIME)
            .setPersistedCurrentSponsorClientId("OLD")
            .build());
    // Advance a day so that the next created revision entry doesn't overwrite the existing one.
    clock.advanceBy(Duration.standardDays(1));
    // Overwrite the current host with one that has different data.
    HostResource currentHost = persistResource(oldHost.asBuilder()
            .setPersistedCurrentSponsorClientId("NEW")
            .build());
    // Load at the point in time just before the latest update; the floor entry of the revisions
    // map should point to the manifest for the first save, so we should get the old host.
    assertThat(loadAtPointInTime(currentHost, clock.nowUtc().minusMillis(1)).now())
        .isEqualTo(oldHost);
  }

  @Test
  public void testLoadAtPointInTime_brokenRevisionHistory_returnsResourceAsIs()
      throws Exception {
    // Don't save a commit log since we want to test the handling of a broken revisions key.
    HostResource oldHost = persistResource(
        newHostResource("ns1.cat.tld").asBuilder()
            .setCreationTimeForTest(START_OF_TIME)
            .setPersistedCurrentSponsorClientId("OLD")
            .build());
    // Advance a day so that the next created revision entry doesn't overwrite the existing one.
    clock.advanceBy(Duration.standardDays(1));
    // Overwrite the existing resource to force revisions map use.
    HostResource host = persistResource(oldHost.asBuilder()
        .setPersistedCurrentSponsorClientId("NEW")
        .build());
    // Load at the point in time just before the latest update; the old host is not recoverable
    // (revisions map link is broken, and guessing using the oldest revision map entry finds the
    // same broken link), so just returns the current host.
    assertThat(loadAtPointInTime(host, clock.nowUtc().minusMillis(1)).now()).isEqualTo(host);
  }

  @Test
  public void testLoadAtPointInTime_fallback_returnsMutationValueForOldestRevision()
      throws Exception {
    clock.advanceOneMilli();
    // Save a commit log that we can fall back to.
    HostResource oldHost = persistResourceWithCommitLog(
        newHostResource("ns1.cat.tld").asBuilder()
            .setCreationTimeForTest(START_OF_TIME)
            .setPersistedCurrentSponsorClientId("OLD")
            .build());
    // Advance a day so that the next created revision entry doesn't overwrite the existing one.
    clock.advanceBy(Duration.standardDays(1));
    // Overwrite the current host with one that has different data.
    HostResource currentHost = persistResource(oldHost.asBuilder()
        .setPersistedCurrentSponsorClientId("NEW")
        .build());
    // Load at the point in time before the first update; there will be no floor entry for the
    // revisions map, so give up and return the oldest revision entry's mutation value (the old host
    // data).
    assertThat(loadAtPointInTime(currentHost, clock.nowUtc().minusDays(2)).now())
        .isEqualTo(oldHost);
  }

  @Test
  public void testLoadAtPointInTime_ultimateFallback_onlyOneRevision_returnsCurrentResource()
      throws Exception {
    clock.advanceOneMilli();
    // Don't save a commit log; we want to test that we load from the current resource.
    HostResource host = persistResource(
        newHostResource("ns1.cat.tld").asBuilder()
            .setCreationTimeForTest(START_OF_TIME)
            .setPersistedCurrentSponsorClientId("OLD")
            .build());
    // Load at the point in time before the first save; there will be no floor entry for the
    // revisions map.  Since the oldest revision entry is the only (i.e. current) revision, return
    // the resource.
    assertThat(loadAtPointInTime(host, clock.nowUtc().minusMillis(1)).now()).isEqualTo(host);
  }

  @Test
  public void testLoadAtPointInTime_moreThanThirtyDaysInPast_historyIsPurged() throws Exception {
    clock.advanceOneMilli();
    HostResource host =
        persistResourceWithCommitLog(newHostResource("ns1.example.net"));
    assertThat(host.getRevisions()).hasSize(1);
    clock.advanceBy(Duration.standardDays(31));
    host = persistResourceWithCommitLog(host);
    assertThat(host.getRevisions()).hasSize(2);
    clock.advanceBy(Duration.standardDays(31));
    host = persistResourceWithCommitLog(host);
    assertThat(host.getRevisions()).hasSize(2);
    // Even though there is no revision, make a best effort guess to use the oldest revision.
    assertThat(
        loadAtPointInTime(host, clock.nowUtc().minus(Duration.standardDays(32)))
          .now().getUpdateAutoTimestamp().getTimestamp())
              .isEqualTo(host.getRevisions().firstKey());
  }
}
