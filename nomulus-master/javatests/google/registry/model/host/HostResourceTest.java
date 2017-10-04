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

package google.registry.model.host;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.testing.DatastoreHelper.cloneAndSetAutoTimestamps;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.HostResourceSubject.assertAboutHosts;

import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import com.googlecode.objectify.Key;
import google.registry.model.EntityTestCase;
import google.registry.model.billing.BillingEvent;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppcommon.Trid;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferData.TransferServerApproveEntity;
import google.registry.model.transfer.TransferStatus;
import google.registry.testing.ExceptionRule;
import java.net.InetAddress;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/** Unit tests for {@link HostResource}. */
public class HostResourceTest extends EntityTestCase {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  final DateTime day3 = clock.nowUtc();
  final DateTime day2 = day3.minusDays(1);
  final DateTime day1 = day2.minusDays(1);

  DomainResource domain;
  HostResource host;

  @Before
  public void setUp() throws Exception {
    createTld("com");
    // Set up a new persisted registrar entity.
    domain = persistResource(
        newDomainResource("example.com").asBuilder()
            .setRepoId("1-COM")
            .setTransferData(new TransferData.Builder()
                .setGainingClientId("gaining")
                .setLosingClientId("losing")
                .setPendingTransferExpirationTime(clock.nowUtc())
                .setServerApproveEntities(
                    ImmutableSet.<Key<? extends TransferServerApproveEntity>>of(
                        Key.create(BillingEvent.OneTime.class, 1)))
                .setTransferRequestTime(clock.nowUtc())
                .setTransferStatus(TransferStatus.SERVER_APPROVED)
                .setTransferRequestTrid(Trid.create("client-trid", "server-trid"))
                .build())
            .build());
    host =
        persistResource(
            cloneAndSetAutoTimestamps(
                new HostResource.Builder()
                    .setRepoId("DEADBEEF-COM")
                    .setFullyQualifiedHostName("ns1.example.com")
                    .setCreationClientId("a registrar")
                    .setLastEppUpdateTime(clock.nowUtc())
                    .setLastEppUpdateClientId("another registrar")
                    .setLastTransferTime(clock.nowUtc())
                    .setInetAddresses(ImmutableSet.of(InetAddresses.forString("127.0.0.1")))
                    .setStatusValues(ImmutableSet.of(StatusValue.OK))
                    .setSuperordinateDomain(Key.create(domain))
                    .build()));
  }

  @Test
  public void testPersistence() throws Exception {
    assertThat(loadByForeignKey(
        HostResource.class, host.getForeignKey(), clock.nowUtc()))
            .isEqualTo(host);
  }

  @Test
  public void testIndexing() throws Exception {
    // Clone it and save it before running the indexing test so that its transferData fields are
    // populated from the superordinate domain.
    verifyIndexing(
        persistResource(host),
        "deletionTime",
        "fullyQualifiedHostName",
        "inetAddresses",
        "superordinateDomain",
        "currentSponsorClientId");
  }

  @Test
  public void testEmptyStringsBecomeNull() {
    assertThat(new HostResource.Builder().setPersistedCurrentSponsorClientId(null).build()
        .getPersistedCurrentSponsorClientId())
            .isNull();
    assertThat(new HostResource.Builder().setPersistedCurrentSponsorClientId("").build()
        .getPersistedCurrentSponsorClientId())
            .isNull();
    assertThat(new HostResource.Builder().setPersistedCurrentSponsorClientId(" ").build()
        .getPersistedCurrentSponsorClientId())
            .isNotNull();
  }

  @Test
  public void testEmptySetsBecomeNull() throws Exception {
    assertThat(new HostResource.Builder().setInetAddresses(null).build().inetAddresses).isNull();
    assertThat(new HostResource.Builder()
        .setInetAddresses(ImmutableSet.<InetAddress>of()).build().inetAddresses)
            .isNull();
    assertThat(
            new HostResource.Builder()
                .setInetAddresses(ImmutableSet.of(InetAddresses.forString("127.0.0.1")))
                .build()
                .inetAddresses)
        .isNotNull();
  }

  @Test
  public void testImplicitStatusValues() {
    // OK is implicit if there's no other statuses.
    assertAboutHosts()
        .that(new HostResource.Builder().build())
        .hasExactlyStatusValues(StatusValue.OK);
    // If there are other status values, OK should be suppressed.
    assertAboutHosts()
        .that(new HostResource.Builder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_HOLD))
            .build())
        .hasExactlyStatusValues(StatusValue.CLIENT_HOLD);
    // When OK is suppressed, it should be removed even if it was originally there.
    assertAboutHosts()
        .that(new HostResource.Builder()
            .setStatusValues(ImmutableSet.of(StatusValue.OK, StatusValue.CLIENT_HOLD))
            .build())
        .hasExactlyStatusValues(StatusValue.CLIENT_HOLD);
  }

  @Test
  public void testToHydratedString_notCircular() {
    // If there are circular references, this will overflow the stack.
    host.toHydratedString();
  }

  @Test
  public void testFailure_uppercaseHostName() {
    thrown.expect(
        IllegalArgumentException.class, "Host name must be in puny-coded, lower-case form");
    host.asBuilder().setFullyQualifiedHostName("AAA.BBB.CCC");
  }

  @Test
  public void testFailure_utf8HostName() {
    thrown.expect(
        IllegalArgumentException.class, "Host name must be in puny-coded, lower-case form");
    host.asBuilder().setFullyQualifiedHostName("みんな.みんな.みんな");
  }

  @Test
  public void testComputeLastTransferTime_hostNeverSwitchedDomains_domainWasNeverTransferred() {
    domain = domain.asBuilder().setLastTransferTime(null).build();
    host = host.asBuilder()
        .setLastTransferTime(null)
        .setLastSuperordinateChange(null)
        .build();
    assertThat(host.computeLastTransferTime(domain)).isNull();
  }

  @Test
  public void testComputeLastTransferTime_hostNeverSwitchedDomains_domainWasTransferred() {
    // Host was created on Day 1.
    // Domain was transferred on Day 2.
    // Host was always subordinate to domain (and was created before the transfer).
    domain = domain.asBuilder().setLastTransferTime(day2).build();
    host = host.asBuilder()
        .setCreationTimeForTest(day1)
        .setLastTransferTime(null)
        .setLastSuperordinateChange(null)
        .build();
    assertThat(host.computeLastTransferTime(domain)).isEqualTo(day2);
  }

  @Test
  public void testComputeLastTransferTime_hostCreatedAfterDomainWasTransferred() {
    // Domain was transferred on Day 1.
    // Host was created subordinate to domain on Day 2.
    domain = domain.asBuilder().setLastTransferTime(day1).build();
    host =
        persistResource(
            cloneAndSetAutoTimestamps(
                new HostResource.Builder()
                    .setCreationTime(day2)
                    .setRepoId("DEADBEEF-COM")
                    .setFullyQualifiedHostName("ns1.example.com")
                    .setCreationClientId("a registrar")
                    .setLastEppUpdateTime(clock.nowUtc())
                    .setLastEppUpdateClientId("another registrar")
                    .setInetAddresses(ImmutableSet.of(InetAddresses.forString("127.0.0.1")))
                    .setStatusValues(ImmutableSet.of(StatusValue.OK))
                    .setSuperordinateDomain(Key.create(domain))
                    .build()));
    assertThat(host.computeLastTransferTime(domain)).isNull();
  }

  @Test
  public void testComputeLastTransferTime_hostWasTransferred_domainWasNeverTransferred() {
    // Host was transferred on Day 1.
    // Host was made subordinate to domain on Day 2.
    // Domain was never transferred.
    domain = domain.asBuilder().setLastTransferTime(null).build();
    host = host.asBuilder()
        .setLastTransferTime(day1)
        .setLastSuperordinateChange(day2)
        .build();
    assertThat(host.computeLastTransferTime(domain)).isEqualTo(day1);
  }

  @Test
  public void testComputeLastTransferTime_domainWasTransferredBeforeHostBecameSubordinate() {
    // Host was transferred on Day 1.
    // Domain was transferred on Day 2.
    // Host was made subordinate to domain on Day 3.
    domain = domain.asBuilder().setLastTransferTime(day2).build();
    host = host.asBuilder()
        .setLastTransferTime(day1)
        .setLastSuperordinateChange(day3)
        .build();
    assertThat(host.computeLastTransferTime(domain)).isEqualTo(day1);
  }

  @Test
  public void testComputeLastTransferTime_domainWasTransferredAfterHostBecameSubordinate() {
    // Host was transferred on Day 1.
    // Host was made subordinate to domain on Day 2.
    // Domain was transferred on Day 3.
    domain = domain.asBuilder().setLastTransferTime(day3).build();
    host = host.asBuilder()
        .setLastTransferTime(day1)
        .setLastSuperordinateChange(day2)
        .build();
    assertThat(host.computeLastTransferTime(domain)).isEqualTo(day3);
  }
}
