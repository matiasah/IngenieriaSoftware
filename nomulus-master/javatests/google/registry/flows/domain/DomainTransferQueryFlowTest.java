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

package google.registry.flows.domain;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.assertBillingEvents;
import static google.registry.testing.DatastoreHelper.deleteResource;
import static google.registry.testing.DatastoreHelper.getPollMessages;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainResourceSubject.assertAboutDomains;

import google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.exceptions.NoTransferHistoryToQueryException;
import google.registry.flows.exceptions.NotAuthorizedToViewTransferException;
import google.registry.model.contact.ContactAuthInfo;
import google.registry.model.domain.DomainAuthInfo;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppcommon.AuthInfo.PasswordAuth;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferStatus;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainTransferQueryFlow}. */
public class DomainTransferQueryFlowTest
    extends DomainTransferFlowTestCase<DomainTransferQueryFlow, DomainResource> {

  @Before
  public void setUp() throws Exception {
    setEppInput("domain_transfer_query.xml");
    setClientIdForFlow("NewRegistrar");
    setupDomainWithPendingTransfer("example", "tld");
  }

  private void doSuccessfulTest(
      String commandFilename,
      String expectedXmlFilename) throws Exception {
    setEppInput(commandFilename);
    // Replace the ROID in the xml file with the one generated in our test.
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // Setup done; run the test.
    assertTransactionalFlow(false);
    runFlowAssertResponse(readFile(expectedXmlFilename));
    assertAboutDomains().that(domain).hasOneHistoryEntryEachOfTypes(
        HistoryEntry.Type.DOMAIN_CREATE,
        HistoryEntry.Type.DOMAIN_TRANSFER_REQUEST);
    assertBillingEvents(
        getBillingEventForImplicitTransfer(),
        getGainingClientAutorenewEvent(),
        getLosingClientAutorenewEvent());
    // Look in the future and make sure the poll messages for implicit ack are there.
    assertThat(getPollMessages("NewRegistrar", clock.nowUtc().plusYears(1))).hasSize(1);
    assertThat(getPollMessages("TheRegistrar", clock.nowUtc().plusYears(1))).hasSize(1);
  }

  private void doFailingTest(String commandFilename) throws Exception {
    setEppInput(commandFilename);
    // Replace the ROID in the xml file with the one generated in our test.
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
    // Setup done; run the test.
    assertTransactionalFlow(false);
    runFlow();
  }

  @Test
  public void testSuccess() throws Exception {
    doSuccessfulTest(
        "domain_transfer_query.xml",
        "domain_transfer_query_response.xml");
  }

  @Test
  public void testSuccess_sponsoringClient() throws Exception {
    setClientIdForFlow("TheRegistrar");
    doSuccessfulTest(
        "domain_transfer_query.xml",
        "domain_transfer_query_response.xml");
  }

  @Test
  public void testSuccess_domainAuthInfo() throws Exception {
    setClientIdForFlow("ClientZ");
    doSuccessfulTest(
        "domain_transfer_query_domain_authinfo.xml",
        "domain_transfer_query_response.xml");
  }

  @Test
  public void testSuccess_contactAuthInfo() throws Exception {
    setClientIdForFlow("ClientZ");
    doSuccessfulTest(
        "domain_transfer_query_contact_authinfo.xml",
        "domain_transfer_query_response.xml");
  }

  @Test
  public void testSuccess_clientApproved() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_APPROVED);
    doSuccessfulTest(
        "domain_transfer_query.xml",
        "domain_transfer_query_response_client_approved.xml");
  }

 @Test
  public void testSuccess_clientRejected() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_REJECTED);
    doSuccessfulTest(
        "domain_transfer_query.xml",
        "domain_transfer_query_response_client_rejected.xml");
  }

 @Test
  public void testSuccess_clientCancelled() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_CANCELLED);
    doSuccessfulTest(
        "domain_transfer_query.xml",
        "domain_transfer_query_response_client_cancelled.xml");
  }

  @Test
  public void testSuccess_serverApproved() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_APPROVED);
    doSuccessfulTest(
        "domain_transfer_query.xml",
        "domain_transfer_query_response_server_approved.xml");
  }

  @Test
  public void testSuccess_serverCancelled() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    doSuccessfulTest(
        "domain_transfer_query.xml",
        "domain_transfer_query_response_server_cancelled.xml");
  }

  @Test
  public void testSuccess_tenYears() throws Exception {
    // Extend registration by 9 years here; with the extra 1 year from the transfer, we should
    // hit the 10-year capping.
    domain = persistResource(domain.asBuilder()
        .setRegistrationExpirationTime(domain.getRegistrationExpirationTime().plusYears(9))
        .build());
    doSuccessfulTest(
        "domain_transfer_query.xml",
        "domain_transfer_query_response_10_years.xml");
  }

  @Test
  public void testFailure_pendingDeleteDomain() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    domain = persistResource(
        domain.asBuilder().setDeletionTime(clock.nowUtc().plusDays(1)).build());
    doSuccessfulTest(
        "domain_transfer_query.xml",
        "domain_transfer_query_response_server_cancelled.xml");
  }

  @Test
  public void testFailure_badContactPassword() throws Exception {
    // Change the contact's password so it does not match the password in the file.
    contact = persistResource(
        contact.asBuilder()
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("badpassword")))
            .build());
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("domain_transfer_query_contact_authinfo.xml");
  }

  @Test
  public void testFailure_badDomainPassword() throws Exception {
    // Change the domain's password so it does not match the password in the file.
    domain = persistResource(domain.asBuilder()
        .setAuthInfo(DomainAuthInfo.create(PasswordAuth.create("badpassword")))
        .build());
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("domain_transfer_query_domain_authinfo.xml");
  }

  @Test
  public void testFailure_neverBeenTransferred() throws Exception {
    changeTransferStatus(null);
    thrown.expect(NoTransferHistoryToQueryException.class);
    doFailingTest("domain_transfer_query.xml");
  }

  @Test
  public void testFailure_unrelatedClient() throws Exception {
    setClientIdForFlow("ClientZ");
    thrown.expect(NotAuthorizedToViewTransferException.class);
    doFailingTest("domain_transfer_query.xml");
  }

  @Test
  public void testFailure_deletedDomain() throws Exception {
    domain = persistResource(
            domain.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    doFailingTest("domain_transfer_query.xml");
  }

  @Test
  public void testFailure_nonexistentDomain() throws Exception {
    deleteResource(domain);
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    doFailingTest("domain_transfer_query.xml");
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-dom-transfer-query");
    assertTldsFieldLogged("tld");
  }
}
