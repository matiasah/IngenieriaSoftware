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

package google.registry.flows.host;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistDeletedHost;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.HostResourceSubject.assertAboutHosts;
import static google.registry.testing.TaskQueueHelper.assertNoDnsTasksEnqueued;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import google.registry.flows.exceptions.ResourceStatusProhibitsOperationException;
import google.registry.flows.exceptions.ResourceToDeleteIsReferencedException;
import google.registry.flows.host.HostFlowUtils.HostNameNotLowerCaseException;
import google.registry.flows.host.HostFlowUtils.HostNameNotNormalizedException;
import google.registry.flows.host.HostFlowUtils.HostNameNotPunyCodedException;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppcommon.Trid;
import google.registry.model.host.HostResource;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferStatus;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link HostDeleteFlow}. */
public class HostDeleteFlowTest extends ResourceFlowTestCase<HostDeleteFlow, HostResource> {

  @Before
  public void initFlowTest() {
    setEppInput("host_delete.xml", ImmutableMap.of("HOSTNAME", "ns1.example.tld"));
  }

  @Test
  public void testDryRun() throws Exception {
    persistActiveHost("ns1.example.tld");
    dryRunFlowAssertResponse(readFile("host_delete_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    persistActiveHost("ns1.example.tld");
    clock.advanceOneMilli();
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile("host_delete_response.xml"));
    HostResource deletedHost = reloadResourceByForeignKey();
    assertAboutHosts().that(deletedHost).hasStatusValue(StatusValue.PENDING_DELETE);
    assertAsyncDeletionTaskEnqueued(
        deletedHost, "TheRegistrar", Trid.create("ABC-12345", "server-trid"), false);
    assertAboutHosts().that(deletedHost)
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.HOST_PENDING_DELETE);
    assertNoBillingEvents();
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testFailure_neverExisted() throws Exception {
    thrown.expect(ResourceDoesNotExistException.class, "(ns1.example.tld)");
    runFlow();
  }

  @Test
  public void testFailure_existedButWasDeleted() throws Exception {
    persistDeletedHost("ns1.example.tld", clock.nowUtc().minusDays(1));
    thrown.expect(ResourceDoesNotExistException.class, "(ns1.example.tld)");
    runFlow();
  }

  private void doFailingStatusTest(StatusValue statusValue, Class<? extends Exception> exception)
      throws Exception {
    persistResource(
        newHostResource("ns1.example.tld").asBuilder()
            .setStatusValues(ImmutableSet.of(statusValue))
            .build());
    thrown.expect(exception, statusValue.getXmlName());
    runFlow();
  }

  @Test
  public void testFailure_existedButWasClientDeleteProhibited() throws Exception {
    doFailingStatusTest(
        StatusValue.CLIENT_DELETE_PROHIBITED, ResourceStatusProhibitsOperationException.class);
  }

  @Test
  public void testFailure_existedButWasServerDeleteProhibited() throws Exception {
    doFailingStatusTest(
        StatusValue.SERVER_DELETE_PROHIBITED, ResourceStatusProhibitsOperationException.class);
  }

  @Test
  public void testFailure_existedButWasPendingDelete() throws Exception {
    doFailingStatusTest(
        StatusValue.PENDING_DELETE, ResourceStatusProhibitsOperationException.class);
  }

  @Test
  public void testFailure_unauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistActiveHost("ns1.example.tld");
    thrown.expect(ResourceNotOwnedException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistActiveHost("ns1.example.tld");
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("host_delete_response.xml"));
    HostResource deletedHost = reloadResourceByForeignKey();
    assertAboutHosts().that(deletedHost).hasStatusValue(StatusValue.PENDING_DELETE);
    assertAsyncDeletionTaskEnqueued(
        deletedHost, "NewRegistrar", Trid.create("ABC-12345", "server-trid"), true);
    assertAboutHosts().that(deletedHost)
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.HOST_PENDING_DELETE);
    assertNoBillingEvents();
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testSuccess_authorizedClientReadFromSuperordinate() throws Exception {
    sessionMetadata.setClientId("TheRegistrar");
    createTld("tld");
    DomainResource domain = persistResource(
        newDomainResource("example.tld").asBuilder()
            .setPersistedCurrentSponsorClientId("TheRegistrar")
            .build());
    persistResource(
        newHostResource("ns1.example.tld").asBuilder()
            .setPersistedCurrentSponsorClientId("NewRegistrar")  // Shouldn't hurt.
            .setSuperordinateDomain(Key.create(domain))
            .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("host_delete_response.xml"));
  }

  @Test
  public void testFailure_unauthorizedClientReadFromSuperordinate() throws Exception {
    sessionMetadata.setClientId("TheRegistrar");
    createTld("tld");
    DomainResource domain = persistResource(
        newDomainResource("example.tld").asBuilder()
            .setPersistedCurrentSponsorClientId("NewRegistrar")
            .build());
    persistResource(
        newHostResource("ns1.example.tld").asBuilder()
            .setPersistedCurrentSponsorClientId("TheRegistrar")  // Shouldn't help.
            .setSuperordinateDomain(Key.create(domain))
            .build());
    thrown.expect(ResourceNotOwnedException.class);
    runFlow();
  }

  @Test
  public void testSuccess_authorizedClientReadFromTransferredSuperordinate() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    createTld("tld");
    // Setup a transfer that should have been server approved a day ago.
    DateTime now = clock.nowUtc();
    DateTime requestTime = now.minusDays(1).minus(Registry.DEFAULT_AUTOMATIC_TRANSFER_LENGTH);
    DateTime transferExpirationTime = now.minusDays(1);
    DomainResource domain = persistResource(newDomainResource("example.tld").asBuilder()
        .setPersistedCurrentSponsorClientId("NewRegistrar")  // Shouldn't hurt.
        .addStatusValue(StatusValue.PENDING_TRANSFER)
        .setTransferData(new TransferData.Builder()
            .setTransferStatus(TransferStatus.PENDING)
            .setGainingClientId("NewRegistrar")
            .setTransferRequestTime(requestTime)
            .setLosingClientId("TheRegistrar")
            .setPendingTransferExpirationTime(transferExpirationTime)
            .build())
        .build());
    persistResource(
        newHostResource("ns1.example.tld").asBuilder()
            .setPersistedCurrentSponsorClientId("TheRegistrar")  // Shouldn't hurt.
            .setSuperordinateDomain(Key.create(domain))
            .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("host_delete_response.xml"));
  }

  @Test
  public void testFailure_unauthorizedClientReadFromTransferredSuperordinate() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    createTld("tld");
    // Setup a transfer that should have been server approved a day ago.
    DateTime now = clock.nowUtc();
    DateTime requestTime = now.minusDays(1).minus(Registry.DEFAULT_AUTOMATIC_TRANSFER_LENGTH);
    DateTime transferExpirationTime = now.minusDays(1);
    DomainResource domain = persistResource(newDomainResource("example.tld").asBuilder()
        .setPersistedCurrentSponsorClientId("NewRegistrar")  // Shouldn't help.
        .addStatusValue(StatusValue.PENDING_TRANSFER)
        .setTransferData(new TransferData.Builder()
            .setTransferStatus(TransferStatus.PENDING)
            .setGainingClientId("TheRegistrar")
            .setTransferRequestTime(requestTime)
            .setLosingClientId("NewRegistrar")
            .setPendingTransferExpirationTime(transferExpirationTime)
            .build())
        .build());
    persistResource(
        newHostResource("ns1.example.tld").asBuilder()
            .setPersistedCurrentSponsorClientId("NewRegistrar")  // Shouldn't help.
            .setSuperordinateDomain(Key.create(domain))
            .build());
    thrown.expect(ResourceNotOwnedException.class);
    runFlow();
  }

  @Test
  public void testFailure_failfastWhenLinkedToDomain() throws Exception {
    createTld("tld");
    persistResource(newDomainResource("example.tld").asBuilder()
        .setNameservers(ImmutableSet.of(
            Key.create(persistActiveHost("ns1.example.tld"))))
        .build());
    thrown.expect(ResourceToDeleteIsReferencedException.class);
    runFlow();
  }

  @Test
  public void testFailure_failfastWhenLinkedToApplication() throws Exception {
    createTld("tld");
    persistResource(newDomainApplication("example.tld").asBuilder()
        .setNameservers(ImmutableSet.of(
            Key.create(persistActiveHost("ns1.example.tld"))))
        .build());
    thrown.expect(ResourceToDeleteIsReferencedException.class);
    runFlow();
  }

  @Test
  public void testFailure_nonLowerCaseHostname() throws Exception {
    setEppInput("host_delete.xml", ImmutableMap.of("HOSTNAME", "NS1.EXAMPLE.NET"));
    thrown.expect(HostNameNotLowerCaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_nonPunyCodedHostname() throws Exception {
    setEppInput("host_delete.xml", ImmutableMap.of("HOSTNAME", "ns1.çauçalito.tld"));
    thrown.expect(HostNameNotPunyCodedException.class, "expected ns1.xn--aualito-txac.tld");
    runFlow();
  }

  @Test
  public void testFailure_nonCanonicalHostname() throws Exception {
    setEppInput("host_delete.xml", ImmutableMap.of("HOSTNAME", "ns1.example.tld."));
    thrown.expect(HostNameNotNormalizedException.class);
    runFlow();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    persistActiveHost("ns1.example.tld");
    clock.advanceOneMilli();
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-host-delete");
  }
}
