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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.reporting.DomainTransactionRecord.TransactionReportField.NET_ADDS_4_YR;
import static google.registry.model.reporting.DomainTransactionRecord.TransactionReportField.TRANSFER_SUCCESSFUL;
import static google.registry.model.reporting.HistoryEntry.Type.DOMAIN_CREATE;
import static google.registry.model.reporting.HistoryEntry.Type.DOMAIN_TRANSFER_APPROVE;
import static google.registry.model.reporting.HistoryEntry.Type.DOMAIN_TRANSFER_REQUEST;
import static google.registry.testing.DatastoreHelper.assertBillingEventsForResource;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.deleteResource;
import static google.registry.testing.DatastoreHelper.getOnlyHistoryEntryOfType;
import static google.registry.testing.DatastoreHelper.getOnlyPollMessage;
import static google.registry.testing.DatastoreHelper.getPollMessages;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainResourceSubject.assertAboutDomains;
import static google.registry.testing.HistoryEntrySubject.assertAboutHistoryEntries;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import google.registry.flows.exceptions.NotPendingTransferException;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Cancellation;
import google.registry.model.billing.BillingEvent.Cancellation.Builder;
import google.registry.model.billing.BillingEvent.OneTime;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.contact.ContactAuthInfo;
import google.registry.model.domain.DomainAuthInfo;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.Period;
import google.registry.model.domain.Period.Unit;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.eppcommon.AuthInfo.PasswordAuth;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppcommon.Trid;
import google.registry.model.poll.PendingActionNotificationResponse;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.DomainTransactionRecord;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferResponse.DomainTransferResponse;
import google.registry.model.transfer.TransferStatus;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainTransferApproveFlow}. */
public class DomainTransferApproveFlowTest
    extends DomainTransferFlowTestCase<DomainTransferApproveFlow, DomainResource> {

  @Before
  public void setUp() throws Exception {
    setEppInput("domain_transfer_approve.xml");
    // Change the registry so that the renew price changes a day minus 1 millisecond before the
    // transfer (right after there will be an autorenew in the test case that has one) and then
    // again a millisecond after the transfer request time. These changes help us ensure that the
    // flows are using prices from the moment of transfer request (or autorenew) and not from the
    // moment that the transfer is approved.
    createTld("tld");
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setRenewBillingCostTransitions(
                new ImmutableSortedMap.Builder<DateTime, Money>(Ordering.natural())
                    .put(START_OF_TIME, Money.of(USD, 1))
                    .put(clock.nowUtc().minusDays(1).plusMillis(1), Money.of(USD, 22))
                    .put(TRANSFER_REQUEST_TIME.plusMillis(1), Money.of(USD, 333))
                    .build())
            .build());
    setClientIdForFlow("TheRegistrar");
    createTld("extra");
    setupDomainWithPendingTransfer("example", "tld");
    clock.advanceOneMilli();
  }

  private void assertTransferApproved(DomainResource domain) {
    assertAboutDomains().that(domain)
        .hasTransferStatus(TransferStatus.CLIENT_APPROVED).and()
        .hasCurrentSponsorClientId("NewRegistrar").and()
        .hasLastTransferTime(clock.nowUtc()).and()
        .hasPendingTransferExpirationTime(clock.nowUtc()).and()
        .doesNotHaveStatusValue(StatusValue.PENDING_TRANSFER);
  }

  private void setEppLoader(String commandFilename) {
    setEppInput(commandFilename);
    // Replace the ROID in the xml file with the one generated in our test.
    eppLoader.replaceAll("JD1234-REP", contact.getRepoId());
  }

  /**
   * Runs a successful test, with the expectedCancellationBillingEvents parameter containing a list
   * of billing event builders that will be filled out with the correct HistoryEntry parent as it is
   * created during the execution of this test.
   */
  private void doSuccessfulTest(
      String tld,
      String commandFilename,
      String expectedXmlFilename,
      DateTime expectedExpirationTime,
      int expectedYearsToCharge,
      BillingEvent.Cancellation.Builder... expectedCancellationBillingEvents) throws Exception {
    runSuccessfulFlowWithAssertions(
        tld,
        commandFilename,
        expectedXmlFilename,
        expectedExpirationTime);
    assertHistoryEntriesContainBillingEventsAndGracePeriods(
        tld, expectedYearsToCharge, expectedCancellationBillingEvents);
  }

  private void runSuccessfulFlowWithAssertions(
      String tld,
      String commandFilename,
      String expectedXmlFilename,
      DateTime expectedExpirationTime) throws Exception {
    setEppLoader(commandFilename);
    Registry registry = Registry.get(tld);
    // Make sure the implicit billing event is there; it will be deleted by the flow.
    // We also expect to see autorenew events for the gaining and losing registrars.
    assertBillingEventsForResource(
        domain,
        getBillingEventForImplicitTransfer(),
        getGainingClientAutorenewEvent(),
        getLosingClientAutorenewEvent());
    // Look in the future and make sure the poll messages for implicit ack are there.
    assertThat(getPollMessages(domain, "NewRegistrar", clock.nowUtc().plusMonths(1))).hasSize(1);
    assertThat(getPollMessages(domain, "TheRegistrar", clock.nowUtc().plusMonths(1))).hasSize(1);
    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile(expectedXmlFilename));
    // Transfer should have succeeded. Verify correct fields were set.
    domain = reloadResourceByForeignKey();
    assertAboutDomains()
        .that(domain)
        .hasOneHistoryEntryEachOfTypes(
            DOMAIN_CREATE, DOMAIN_TRANSFER_REQUEST, DOMAIN_TRANSFER_APPROVE);
    final HistoryEntry historyEntryTransferApproved =
        getOnlyHistoryEntryOfType(domain, DOMAIN_TRANSFER_APPROVE);
    assertAboutHistoryEntries().that(historyEntryTransferApproved)
        .hasOtherClientId("NewRegistrar");
    assertTransferApproved(domain);
    assertAboutDomains().that(domain).hasRegistrationExpirationTime(expectedExpirationTime);
    assertThat(ofy().load().key(domain.getAutorenewBillingEvent()).now().getEventTime())
        .isEqualTo(expectedExpirationTime);
    // The poll message (in the future) to the losing registrar for implicit ack should be gone.
    assertThat(getPollMessages(domain, "TheRegistrar", clock.nowUtc().plusMonths(1))).isEmpty();

    // The poll message in the future to the gaining registrar should be gone too, but there
    // should be one at the current time to the gaining registrar, as well as one at the domain's
    // autorenew time.
    assertThat(getPollMessages(domain, "NewRegistrar", clock.nowUtc().plusMonths(1))).hasSize(1);
    assertThat(getPollMessages(domain, "NewRegistrar", domain.getRegistrationExpirationTime()))
        .hasSize(2);

    PollMessage gainingTransferPollMessage =
        getOnlyPollMessage(domain, "NewRegistrar", clock.nowUtc(), PollMessage.OneTime.class);
    PollMessage gainingAutorenewPollMessage = getOnlyPollMessage(
        domain,
        "NewRegistrar",
        domain.getRegistrationExpirationTime(),
        PollMessage.Autorenew.class);
    assertThat(gainingTransferPollMessage.getEventTime()).isEqualTo(clock.nowUtc());
    assertThat(gainingAutorenewPollMessage.getEventTime())
        .isEqualTo(domain.getRegistrationExpirationTime());
    DomainTransferResponse transferResponse = getOnlyElement(FluentIterable
        .from(gainingTransferPollMessage.getResponseData())
        .filter(DomainTransferResponse.class));
    assertThat(transferResponse.getTransferStatus()).isEqualTo(TransferStatus.CLIENT_APPROVED);
    assertThat(transferResponse.getExtendedRegistrationExpirationTime())
        .isEqualTo(domain.getRegistrationExpirationTime());
    PendingActionNotificationResponse panData = Iterables.getOnlyElement(FluentIterable
        .from(gainingTransferPollMessage.getResponseData())
        .filter(PendingActionNotificationResponse.class));
    assertThat(panData.getTrid())
        .isEqualTo(Trid.create("transferClient-trid", "transferServer-trid"));
    assertThat(panData.getActionResult()).isTrue();

    // After the expected grace time, the grace period should be gone.
    assertThat(
        domain.cloneProjectedAtTime(clock.nowUtc().plus(registry.getTransferGracePeriodLength()))
            .getGracePeriods()).isEmpty();
  }

  private void assertHistoryEntriesContainBillingEventsAndGracePeriods(
      String tld,
      int expectedYearsToCharge,
      BillingEvent.Cancellation.Builder... expectedCancellationBillingEvents)
      throws Exception {
    Registry registry = Registry.get(tld);
    domain = reloadResourceByForeignKey();
    final HistoryEntry historyEntryTransferApproved =
        getOnlyHistoryEntryOfType(domain, DOMAIN_TRANSFER_APPROVE);
    // We expect three billing events: one for the transfer, a closed autorenew for the losing
    // client and an open autorenew for the gaining client that begins at the new expiration time.
    OneTime transferBillingEvent =
        new BillingEvent.OneTime.Builder()
            .setReason(Reason.TRANSFER)
            .setTargetId(domain.getFullyQualifiedDomainName())
            .setEventTime(clock.nowUtc())
            .setBillingTime(clock.nowUtc().plus(registry.getTransferGracePeriodLength()))
            .setClientId("NewRegistrar")
            .setCost(Money.of(USD, 11).multipliedBy(expectedYearsToCharge))
            .setPeriodYears(expectedYearsToCharge)
            .setParent(historyEntryTransferApproved)
            .build();
    assertBillingEventsForResource(
        domain,
        FluentIterable.from(expectedCancellationBillingEvents)
            .transform(
                new Function<BillingEvent.Cancellation.Builder, BillingEvent>() {
                  @Override
                  public Cancellation apply(Builder builder) {
                    return builder.setParent(historyEntryTransferApproved).build();
                  }
                })
            .append(
                transferBillingEvent,
                getLosingClientAutorenewEvent()
                    .asBuilder()
                    .setRecurrenceEndTime(clock.nowUtc())
                    .build(),
                getGainingClientAutorenewEvent()
                    .asBuilder()
                    .setEventTime(domain.getRegistrationExpirationTime())
                    .setParent(historyEntryTransferApproved)
                    .build())
            .toArray(BillingEvent.class));
    // There should be a grace period for the new transfer billing event.
    assertGracePeriods(
        domain.getGracePeriods(),
        ImmutableMap.of(
            GracePeriod.create(
                GracePeriodStatus.TRANSFER,
                clock.nowUtc().plus(registry.getTransferGracePeriodLength()),
                "NewRegistrar",
                null),
            transferBillingEvent));
  }

  private void assertHistoryEntriesDoNotContainTransferBillingEventsOrGracePeriods(
      BillingEvent.Cancellation.Builder... expectedCancellationBillingEvents)
      throws Exception {
    domain = reloadResourceByForeignKey();
    final HistoryEntry historyEntryTransferApproved =
        getOnlyHistoryEntryOfType(domain, DOMAIN_TRANSFER_APPROVE);
    // We expect two billing events: a closed autorenew for the losing client and an open autorenew
    // for the gaining client that begins at the new expiration time.
    assertBillingEventsForResource(
        domain,
        FluentIterable.from(expectedCancellationBillingEvents)
            .transform(
                new Function<BillingEvent.Cancellation.Builder, BillingEvent>() {
                  @Override
                  public Cancellation apply(Builder builder) {
                    return builder.setParent(historyEntryTransferApproved).build();
                  }
                })
            .append(
                getLosingClientAutorenewEvent()
                    .asBuilder()
                    .setRecurrenceEndTime(clock.nowUtc())
                    .build(),
                getGainingClientAutorenewEvent()
                    .asBuilder()
                    .setEventTime(domain.getRegistrationExpirationTime())
                    .setParent(historyEntryTransferApproved)
                    .build())
            .toArray(BillingEvent.class));
    // There should be no grace period.
    assertGracePeriods(domain.getGracePeriods(), ImmutableMap.<GracePeriod, BillingEvent>of());
  }

  private void doSuccessfulTest(String tld, String commandFilename, String expectedXmlFilename)
      throws Exception {
    clock.advanceOneMilli();
    doSuccessfulTest(
        tld,
        commandFilename,
        expectedXmlFilename,
        domain.getRegistrationExpirationTime().plusYears(1),
        1);
  }

  private void doFailingTest(String commandFilename) throws Exception {
    setEppLoader(commandFilename);
    // Setup done; run the test.
    assertTransactionalFlow(true);
    runFlow();
  }

  @Test
  public void testDryRun() throws Exception {
    setEppLoader("domain_transfer_approve.xml");
    dryRunFlowAssertResponse(readFile("domain_transfer_approve_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    doSuccessfulTest("tld", "domain_transfer_approve.xml", "domain_transfer_approve_response.xml");
  }

  @Test
  public void testSuccess_nonDefaultTransferGracePeriod() throws Exception {
    // We have to set up a new domain in a different TLD so that the billing event will be persisted
    // with the new transfer grace period in mind.
    createTld("net");
    persistResource(
        Registry.get("net")
            .asBuilder()
            .setTransferGracePeriodLength(Duration.standardMinutes(10))
            .build());
    setupDomainWithPendingTransfer("example", "net");
    doSuccessfulTest(
        "net",
        "domain_transfer_approve_net.xml",
        "domain_transfer_approve_response_net.xml");
  }

  @Test
  public void testSuccess_domainAuthInfo() throws Exception {
    doSuccessfulTest(
        "tld",
        "domain_transfer_approve_domain_authinfo.xml",
        "domain_transfer_approve_response.xml");
  }

  @Test
  public void testSuccess_contactAuthInfo() throws Exception {
    doSuccessfulTest(
        "tld",
        "domain_transfer_approve_contact_authinfo.xml",
        "domain_transfer_approve_response.xml");
  }

  @Test
  public void testSuccess_autorenewBeforeTransfer() throws Exception {
    DomainResource domain = reloadResourceByForeignKey();
    DateTime oldExpirationTime = clock.nowUtc().minusDays(1);
    persistResource(domain.asBuilder()
        .setRegistrationExpirationTime(oldExpirationTime)
        .build());
    // The autorenew should be subsumed into the transfer resulting in 1 year of renewal in total.
    clock.advanceOneMilli();
    doSuccessfulTest(
        "tld",
        "domain_transfer_approve_domain_authinfo.xml",
        "domain_transfer_approve_response_autorenew.xml",
        oldExpirationTime.plusYears(1),
        1,
        // Expect the grace period for autorenew to be cancelled.
        new BillingEvent.Cancellation.Builder()
            .setReason(Reason.RENEW)
            .setTargetId("example.tld")
            .setClientId("TheRegistrar")
            .setEventTime(clock.nowUtc())  // The cancellation happens at the moment of transfer.
            .setBillingTime(
                oldExpirationTime.plus(Registry.get("tld").getAutoRenewGracePeriodLength()))
            .setRecurringEventKey(domain.getAutorenewBillingEvent()));
  }

  @Test
  public void testFailure_badContactPassword() throws Exception {
    // Change the contact's password so it does not match the password in the file.
    contact = persistResource(
        contact.asBuilder()
            .setAuthInfo(ContactAuthInfo.create(PasswordAuth.create("badpassword")))
            .build());
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("domain_transfer_approve_contact_authinfo.xml");
  }

  @Test
  public void testFailure_badDomainPassword() throws Exception {
    // Change the domain's password so it does not match the password in the file.
    domain = persistResource(domain.asBuilder()
        .setAuthInfo(DomainAuthInfo.create(PasswordAuth.create("badpassword")))
        .build());
    thrown.expect(BadAuthInfoForResourceException.class);
    doFailingTest("domain_transfer_approve_domain_authinfo.xml");
  }

  @Test
  public void testFailure_neverBeenTransferred() throws Exception {
    changeTransferStatus(null);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("domain_transfer_approve.xml");
  }

  @Test
  public void testFailure_clientApproved() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_APPROVED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("domain_transfer_approve.xml");
  }

 @Test
  public void testFailure_clientRejected() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_REJECTED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("domain_transfer_approve.xml");
  }

 @Test
  public void testFailure_clientCancelled() throws Exception {
    changeTransferStatus(TransferStatus.CLIENT_CANCELLED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("domain_transfer_approve.xml");
  }

  @Test
  public void testFailure_serverApproved() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_APPROVED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("domain_transfer_approve.xml");
  }

  @Test
  public void testFailure_serverCancelled() throws Exception {
    changeTransferStatus(TransferStatus.SERVER_CANCELLED);
    thrown.expect(NotPendingTransferException.class);
    doFailingTest("domain_transfer_approve.xml");
  }

  @Test
  public void testFailure_gainingClient() throws Exception {
    setClientIdForFlow("NewRegistrar");
    thrown.expect(ResourceNotOwnedException.class);
    doFailingTest("domain_transfer_approve.xml");
  }

  @Test
  public void testFailure_unrelatedClient() throws Exception {
    setClientIdForFlow("ClientZ");
    thrown.expect(ResourceNotOwnedException.class);
    doFailingTest("domain_transfer_approve.xml");
  }

  @Test
  public void testFailure_deletedDomain() throws Exception {
    domain = persistResource(
        domain.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    thrown.expect(ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    doFailingTest("domain_transfer_approve.xml");
  }

  @Test
  public void testFailure_nonexistentDomain() throws Exception {
    deleteResource(domain);
    thrown.expect(ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    doFailingTest("domain_transfer_approve.xml");
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    persistResource(
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    thrown.expect(NotAuthorizedForTldException.class);
    doSuccessfulTest("tld", "domain_transfer_approve.xml", "domain_transfer_approve_response.xml");
  }

  @Test
  public void testSuccess_superuserNotAuthorizedForTld() throws Exception {
    persistResource(
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_transfer_approve_response.xml"));
  }

  // NB: No need to test pending delete status since pending transfers will get cancelled upon
  // entering pending delete phase. So it's already handled in that test case.

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-dom-transfer-approve");
    assertTldsFieldLogged("tld");
  }

  private void setUpGracePeriodDurations() {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAutomaticTransferLength(Duration.standardDays(2))
            .setTransferGracePeriodLength(Duration.standardDays(3))
            .build());
  }

  @Test
  public void testIcannTransactionRecord_noRecordsToCancel() throws Exception {
    setUpGracePeriodDurations();
    clock.advanceOneMilli();
    runFlow();
    HistoryEntry persistedEntry = getOnlyHistoryEntryOfType(domain, DOMAIN_TRANSFER_APPROVE);
    // We should only produce a transfer success record for (now + transfer grace period)
    assertThat(persistedEntry.getDomainTransactionRecords())
        .containsExactly(
            DomainTransactionRecord.create(
                "tld", clock.nowUtc().plusDays(3), TRANSFER_SUCCESSFUL, 1));
  }

  @Test
  public void testIcannTransactionRecord_cancelsPreviousRecords() throws Exception {
    clock.advanceOneMilli();
    setUpGracePeriodDurations();
    DomainTransactionRecord previousSuccessRecord =
        DomainTransactionRecord.create(
            "tld", clock.nowUtc().plusDays(1), TRANSFER_SUCCESSFUL, 1);
    // We only want to cancel TRANSFER_SUCCESSFUL records
    DomainTransactionRecord notCancellableRecord =
        DomainTransactionRecord.create("tld", clock.nowUtc().plusDays(1), NET_ADDS_4_YR, 5);
    persistResource(
        new HistoryEntry.Builder()
            .setType(DOMAIN_TRANSFER_REQUEST)
            .setParent(domain)
            .setModificationTime(clock.nowUtc().minusDays(4))
            .setDomainTransactionRecords(
                ImmutableSet.of(previousSuccessRecord, notCancellableRecord))
            .build());
    runFlow();
    HistoryEntry persistedEntry = getOnlyHistoryEntryOfType(domain, DOMAIN_TRANSFER_APPROVE);
    // We should only produce cancellation records for the original reporting date (now + 1 day) and
    // success records for the new reporting date (now + transferGracePeriod=3 days)
    assertThat(persistedEntry.getDomainTransactionRecords())
        .containsExactly(
            previousSuccessRecord.asBuilder().setReportAmount(-1).build(),
            DomainTransactionRecord.create(
                "tld", clock.nowUtc().plusDays(3), TRANSFER_SUCCESSFUL, 1));

  }

  @Test
  public void testSuccess_superuserExtension_transferPeriodZero() throws Exception {
    DomainResource domain = reloadResourceByForeignKey();
    TransferData.Builder transferDataBuilder = domain.getTransferData().asBuilder();
    persistResource(
        domain
            .asBuilder()
            .setTransferData(
                transferDataBuilder.setTransferPeriod(Period.create(0, Unit.YEARS)).build())
            .build());
    clock.advanceOneMilli();
    runSuccessfulFlowWithAssertions(
        "tld",
        "domain_transfer_approve.xml",
        "domain_transfer_approve_response_zero_period.xml",
        domain.getRegistrationExpirationTime().plusYears(0));
    assertHistoryEntriesDoNotContainTransferBillingEventsOrGracePeriods();
  }
}
