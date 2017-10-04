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
import static google.registry.flows.domain.DomainTransferFlowTestCase.persistWithPendingTransfer;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.assertBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.getOnlyHistoryEntryOfType;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistDeletedDomain;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainResourceSubject.assertAboutDomains;
import static google.registry.testing.HistoryEntrySubject.assertAboutHistoryEntries;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.EUR;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.googlecode.objectify.Key;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import google.registry.flows.domain.DomainFlowUtils.BadPeriodUnitException;
import google.registry.flows.domain.DomainFlowUtils.CurrencyUnitMismatchException;
import google.registry.flows.domain.DomainFlowUtils.CurrencyValueScaleException;
import google.registry.flows.domain.DomainFlowUtils.ExceedsMaxRegistrationYearsException;
import google.registry.flows.domain.DomainFlowUtils.FeesMismatchException;
import google.registry.flows.domain.DomainFlowUtils.FeesRequiredForPremiumNameException;
import google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import google.registry.flows.domain.DomainFlowUtils.UnsupportedFeeAttributeException;
import google.registry.flows.domain.DomainRenewFlow.IncorrectCurrentExpirationDateException;
import google.registry.flows.exceptions.ResourceStatusProhibitsOperationException;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.DomainTransactionRecord;
import google.registry.model.reporting.DomainTransactionRecord.TransactionReportField;
import google.registry.model.reporting.HistoryEntry;
import java.util.Map;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainRenewFlow}. */
public class DomainRenewFlowTest extends ResourceFlowTestCase<DomainRenewFlow, DomainResource> {

  private static final ImmutableMap<String, String> FEE_06_MAP =
      ImmutableMap.of("FEE_VERSION", "0.6", "FEE_NS", "fee");
  private static final ImmutableMap<String, String> FEE_11_MAP =
      ImmutableMap.of("FEE_VERSION", "0.11", "FEE_NS", "fee11");
  private static final ImmutableMap<String, String> FEE_12_MAP =
      ImmutableMap.of("FEE_VERSION", "0.12", "FEE_NS", "fee12");

  final DateTime expirationTime = DateTime.parse("2000-04-03T22:00:00.0Z");

  public DomainRenewFlowTest() {
    clock.setTo(expirationTime.minusMillis(2));
    setEppInput("domain_renew.xml");
  }

  @Before
  public void initDomainTest() {
    createTld("tld");
  }

  private void persistDomain(StatusValue... statusValues) throws Exception {
    DomainResource domain = newDomainResource(getUniqueIdFromCommand());
    HistoryEntry historyEntryDomainCreate = persistResource(
        new HistoryEntry.Builder()
            .setParent(domain)
            .setType(HistoryEntry.Type.DOMAIN_CREATE)
            .build());
    BillingEvent.Recurring autorenewEvent = persistResource(
        new BillingEvent.Recurring.Builder()
            .setReason(Reason.RENEW)
            .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
            .setTargetId(getUniqueIdFromCommand())
            .setClientId("TheRegistrar")
            .setEventTime(expirationTime)
            .setRecurrenceEndTime(END_OF_TIME)
            .setParent(historyEntryDomainCreate)
            .build());
    PollMessage.Autorenew autorenewPollMessage = persistResource(
        new PollMessage.Autorenew.Builder()
            .setTargetId(getUniqueIdFromCommand())
            .setClientId("TheRegistrar")
            .setEventTime(expirationTime)
            .setAutorenewEndTime(END_OF_TIME)
            .setMsg("Domain was auto-renewed.")
            .setParent(historyEntryDomainCreate)
            .build());
    domain = persistResource(domain.asBuilder()
        .setRegistrationExpirationTime(expirationTime)
        .setStatusValues(ImmutableSet.copyOf(statusValues))
        .setAutorenewBillingEvent(Key.create(autorenewEvent))
        .setAutorenewPollMessage(Key.create(autorenewPollMessage))
        .build());
    clock.advanceOneMilli();
  }

  private void doSuccessfulTest(String responseFilename, int renewalYears) throws Exception {
    doSuccessfulTest(responseFilename, renewalYears, ImmutableMap.<String, String>of());
  }

  private void doSuccessfulTest(
      String responseFilename, int renewalYears, Map<String, String> substitutions)
      throws Exception {
    doSuccessfulTest(
        responseFilename,
        renewalYears,
        substitutions,
        Money.of(USD, 11).multipliedBy(renewalYears));
  }

  private void doSuccessfulTest(
      String responseFilename,
      int renewalYears,
      Map<String, String> substitutions,
      Money totalRenewCost) throws Exception {
    assertTransactionalFlow(true);
    DateTime currentExpiration = reloadResourceByForeignKey().getRegistrationExpirationTime();
    DateTime newExpiration = currentExpiration.plusYears(renewalYears);
    runFlowAssertResponse(readFile(responseFilename, substitutions));
    DomainResource domain = reloadResourceByForeignKey();
    HistoryEntry historyEntryDomainRenew =
        getOnlyHistoryEntryOfType(domain, HistoryEntry.Type.DOMAIN_RENEW);
    assertThat(ofy().load().key(domain.getAutorenewBillingEvent()).now().getEventTime())
        .isEqualTo(newExpiration);
    assertAboutDomains().that(domain)
        .isActiveAt(clock.nowUtc()).and()
        .hasRegistrationExpirationTime(newExpiration).and()
        .hasOneHistoryEntryEachOfTypes(
            HistoryEntry.Type.DOMAIN_CREATE,
            HistoryEntry.Type.DOMAIN_RENEW);
    assertAboutHistoryEntries().that(historyEntryDomainRenew).hasPeriodYears(renewalYears);
    BillingEvent.OneTime renewBillingEvent = new BillingEvent.OneTime.Builder()
        .setReason(Reason.RENEW)
        .setTargetId(getUniqueIdFromCommand())
        .setClientId("TheRegistrar")
        .setCost(totalRenewCost)
        .setPeriodYears(renewalYears)
        .setEventTime(clock.nowUtc())
        .setBillingTime(clock.nowUtc().plus(Registry.get("tld").getRenewGracePeriodLength()))
        .setParent(historyEntryDomainRenew)
        .build();
    assertBillingEvents(
        renewBillingEvent,
        new BillingEvent.Recurring.Builder()
            .setReason(Reason.RENEW)
            .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
            .setTargetId(getUniqueIdFromCommand())
            .setClientId("TheRegistrar")
            .setEventTime(expirationTime)
            .setRecurrenceEndTime(clock.nowUtc())
            .setParent(getOnlyHistoryEntryOfType(domain, HistoryEntry.Type.DOMAIN_CREATE))
            .build(),
        new BillingEvent.Recurring.Builder()
            .setReason(Reason.RENEW)
            .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
            .setTargetId(getUniqueIdFromCommand())
            .setClientId("TheRegistrar")
            .setEventTime(domain.getRegistrationExpirationTime())
            .setRecurrenceEndTime(END_OF_TIME)
            .setParent(historyEntryDomainRenew)
            .build());
    // There should only be the new autorenew poll message, as the old one will have been deleted
    // since it had no messages left to deliver.
    assertPollMessages(
        new PollMessage.Autorenew.Builder()
            .setTargetId(getUniqueIdFromCommand())
            .setClientId("TheRegistrar")
            .setEventTime(domain.getRegistrationExpirationTime())
            .setAutorenewEndTime(END_OF_TIME)
            .setMsg("Domain was auto-renewed.")
            .setParent(historyEntryDomainRenew)
            .build());
    assertGracePeriods(
        domain.getGracePeriods(),
        ImmutableMap.of(
            GracePeriod.create(
                GracePeriodStatus.RENEW,
                clock.nowUtc().plus(Registry.get("tld").getRenewGracePeriodLength()),
                "TheRegistrar",
                null),
            renewBillingEvent));
  }

  @Test
  public void testDryRun() throws Exception {
    persistDomain();
    dryRunFlowAssertResponse(readFile("domain_renew_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    persistDomain();
    doSuccessfulTest("domain_renew_response.xml", 5);
  }

  @Test
  public void testSuccess_customLogicFee() throws Exception {
    // The "costly-renew" domain has an additional RENEW fee of 100 from custom logic on top of the
    // normal $11 standard renew price for this TLD.
    setEppInput(
        "domain_renew_fee_wildcard.xml",
        ImmutableMap.of("DOMAIN", "costly-renew.tld", "FEE_VERSION", "0.6", "AMOUNT", "111.00"));
    persistDomain();
    doSuccessfulTest(
        "domain_renew_response_fee_wildcard.xml",
        1,
        new ImmutableMap.Builder<String, String>()
            .put("DOMAIN", "costly-renew.tld")
            .put("FEE_VERSION", "0.6")
            .put("FEE_NS", "fee")
            .put("AMOUNT", "111.00")
            .put("EX_DATE", "2001-04-03T22:00:00.000Z")
            .build(),
        Money.of(USD, 111));
  }

  @Test
  public void testSuccess_fee_v06() throws Exception {
    setEppInput("domain_renew_fee.xml", FEE_06_MAP);
    persistDomain();
    doSuccessfulTest("domain_renew_response_fee.xml", 5, FEE_06_MAP);
  }

  @Test
  public void testSuccess_fee_v11() throws Exception {
    setEppInput("domain_renew_fee.xml", FEE_11_MAP);
    persistDomain();
    doSuccessfulTest("domain_renew_response_fee.xml", 5, FEE_11_MAP);
  }

  @Test
  public void testSuccess_fee_v12() throws Exception {
    setEppInput("domain_renew_fee.xml", FEE_12_MAP);
    persistDomain();
    doSuccessfulTest("domain_renew_response_fee.xml", 5, FEE_12_MAP);
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes_v06() throws Exception {
    setEppInput("domain_renew_fee_defaults.xml", FEE_06_MAP);
    persistDomain();
    doSuccessfulTest("domain_renew_response_fee.xml", 5, FEE_06_MAP);
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes_v11() throws Exception {
    setEppInput("domain_renew_fee_defaults.xml", FEE_11_MAP);
    persistDomain();
    doSuccessfulTest("domain_renew_response_fee.xml", 5, FEE_11_MAP);
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes_v12() throws Exception {
    setEppInput("domain_renew_fee_defaults.xml", FEE_12_MAP);
    persistDomain();
    doSuccessfulTest("domain_renew_response_fee.xml", 5, FEE_12_MAP);
  }

  @Test
  public void testFailure_refundableFee_v06() throws Exception {
    setEppInput("domain_renew_fee_refundable.xml", FEE_06_MAP);
    persistDomain();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_refundableFee_v11() throws Exception {
    setEppInput("domain_renew_fee_refundable.xml", FEE_11_MAP);
    persistDomain();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_refundableFee_v12() throws Exception {
    setEppInput("domain_renew_fee_refundable.xml", FEE_12_MAP);
    persistDomain();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_gracePeriodFee_v06() throws Exception {
    setEppInput("domain_renew_fee_grace_period.xml", FEE_06_MAP);
    persistDomain();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_gracePeriodFee_v11() throws Exception {
    setEppInput("domain_renew_fee_grace_period.xml", FEE_11_MAP);
    persistDomain();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_gracePeriodFee_v12() throws Exception {
    setEppInput("domain_renew_fee_grace_period.xml", FEE_12_MAP);
    persistDomain();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_appliedFee_v06() throws Exception {
    setEppInput("domain_renew_fee_applied.xml", FEE_06_MAP);
    persistDomain();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_appliedFee_v11() throws Exception {
    setEppInput("domain_renew_fee_applied.xml", FEE_11_MAP);
    persistDomain();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_appliedFee_v12() throws Exception {
    setEppInput("domain_renew_fee_applied.xml", FEE_12_MAP);
    persistDomain();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testSuccess_nonDefaultRenewGracePeriod() throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setRenewGracePeriodLength(Duration.standardMinutes(9))
            .build());
    persistDomain();
    doSuccessfulTest("domain_renew_response.xml", 5);
  }

  @Test
  public void testSuccess_missingPeriod() throws Exception {
    setEppInput("domain_renew_missing_period.xml");
    persistDomain();
    doSuccessfulTest("domain_renew_response_missing_period.xml", 1);
  }

  @Test
  public void testSuccess_autorenewPollMessageIsNotDeleted() throws Exception {
    persistDomain();
    // Modify the autorenew poll message so that it has an undelivered message in the past.
    persistResource(
        ofy().load().key(reloadResourceByForeignKey().getAutorenewPollMessage()).now().asBuilder()
            .setEventTime(expirationTime.minusYears(1))
            .build());
    runFlowAssertResponse(readFile("domain_renew_response.xml"));
    HistoryEntry historyEntryDomainRenew =
        getOnlyHistoryEntryOfType(reloadResourceByForeignKey(), HistoryEntry.Type.DOMAIN_RENEW);
    assertPollMessages(
        new PollMessage.Autorenew.Builder()
            .setTargetId(getUniqueIdFromCommand())
            .setClientId("TheRegistrar")
            .setEventTime(expirationTime.minusYears(1))
            .setAutorenewEndTime(clock.nowUtc())
            .setMsg("Domain was auto-renewed.")
            .setParent(getOnlyHistoryEntryOfType(
                reloadResourceByForeignKey(), HistoryEntry.Type.DOMAIN_CREATE))
            .build(),
        new PollMessage.Autorenew.Builder()
            .setTargetId(getUniqueIdFromCommand())
            .setClientId("TheRegistrar")
            .setEventTime(reloadResourceByForeignKey().getRegistrationExpirationTime())
            .setAutorenewEndTime(END_OF_TIME)
            .setMsg("Domain was auto-renewed.")
            .setParent(historyEntryDomainRenew)
            .build());
  }

  @Test
  public void testFailure_neverExisted() throws Exception {
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_existedButWasDeleted() throws Exception {
    persistDeletedDomain(getUniqueIdFromCommand(), clock.nowUtc().minusDays(1));
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_clientRenewProhibited() throws Exception {
    persistDomain(StatusValue.CLIENT_RENEW_PROHIBITED);
    thrown.expect(ResourceStatusProhibitsOperationException.class, "clientRenewProhibited");
    runFlow();
  }

  @Test
  public void testFailure_serverRenewProhibited() throws Exception {
    persistDomain(StatusValue.SERVER_RENEW_PROHIBITED);
    thrown.expect(ResourceStatusProhibitsOperationException.class, "serverRenewProhibited");
    runFlow();
  }

  @Test
  public void testFailure_pendingDelete() throws Exception {
    persistResource(newDomainResource(getUniqueIdFromCommand()).asBuilder()
        .setRegistrationExpirationTime(expirationTime)
        .setDeletionTime(clock.nowUtc().plusDays(1))
        .addStatusValue(StatusValue.PENDING_DELETE)
        .build());
    thrown.expect(ResourceStatusProhibitsOperationException.class, "pendingDelete");
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeAmount_v06() throws Exception {
    setEppInput("domain_renew_fee.xml", FEE_06_MAP);
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(USD, 20)))
            .build());
    persistDomain();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeAmount_v11() throws Exception {
    setEppInput("domain_renew_fee.xml", FEE_11_MAP);
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(USD, 20)))
            .build());
    persistDomain();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeAmount_v12() throws Exception {
    setEppInput("domain_renew_fee.xml", FEE_12_MAP);
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(USD, 20)))
            .build());
    persistDomain();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency_v06() throws Exception {
    setEppInput("domain_renew_fee.xml", FEE_06_MAP);
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setCurrency(EUR)
            .setCreateBillingCost(Money.of(EUR, 13))
            .setRestoreBillingCost(Money.of(EUR, 11))
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
            .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
            .setServerStatusChangeBillingCost(Money.of(EUR, 19))
            .build());
    persistDomain();
    thrown.expect(CurrencyUnitMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency_v11() throws Exception {
    setEppInput("domain_renew_fee.xml", FEE_11_MAP);
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setCurrency(EUR)
            .setCreateBillingCost(Money.of(EUR, 13))
            .setRestoreBillingCost(Money.of(EUR, 11))
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
            .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
            .setServerStatusChangeBillingCost(Money.of(EUR, 19))
            .build());
    persistDomain();
    thrown.expect(CurrencyUnitMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency_v12() throws Exception {
    setEppInput("domain_renew_fee.xml", FEE_12_MAP);
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setCurrency(EUR)
            .setCreateBillingCost(Money.of(EUR, 13))
            .setRestoreBillingCost(Money.of(EUR, 11))
            .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
            .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
            .setServerStatusChangeBillingCost(Money.of(EUR, 19))
            .build());
    persistDomain();
    thrown.expect(CurrencyUnitMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v06() throws Exception {
    setEppInput("domain_renew_fee_bad_scale.xml", FEE_06_MAP);
    persistDomain();
    thrown.expect(CurrencyValueScaleException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v11() throws Exception {
    setEppInput("domain_renew_fee_bad_scale.xml", FEE_11_MAP);
    persistDomain();
    thrown.expect(CurrencyValueScaleException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v12() throws Exception {
    setEppInput("domain_renew_fee_bad_scale.xml", FEE_12_MAP);
    persistDomain();
    thrown.expect(CurrencyValueScaleException.class);
    runFlow();
  }

  @Test
  public void testFailure_pendingTransfer() throws Exception {
    persistDomain();
    persistWithPendingTransfer(reloadResourceByForeignKey()
        .asBuilder()
        .setRegistrationExpirationTime(DateTime.parse("2001-09-08T22:00:00.0Z"))
        .build());
    thrown.expect(ResourceStatusProhibitsOperationException.class, "pendingTransfer");
    runFlow();
  }

  @Test
  public void testFailure_periodInMonths() throws Exception {
    setEppInput("domain_renew_months.xml");
    persistDomain();
    thrown.expect(BadPeriodUnitException.class);
    runFlow();
  }

  @Test
  public void testFailure_max10Years() throws Exception {
    setEppInput("domain_renew_11_years.xml");
    persistDomain();
    thrown.expect(ExceedsMaxRegistrationYearsException.class);
    runFlow();
  }

  @Test
  public void testFailure_curExpDateMustMatch() throws Exception {
    persistDomain();
    // Note expiration time is off by one day.
    persistResource(reloadResourceByForeignKey().asBuilder()
        .setRegistrationExpirationTime(DateTime.parse("2000-04-04T22:00:00.0Z"))
        .build());
    thrown.expect(IncorrectCurrentExpirationDateException.class);
    runFlow();
  }

  @Test
  public void testFailure_unauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistActiveDomain(getUniqueIdFromCommand());
    thrown.expect(ResourceNotOwnedException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistDomain();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_renew_response.xml"));
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    persistResource(
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    persistDomain();
    thrown.expect(NotAuthorizedForTldException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserNotAuthorizedForTld() throws Exception {
    persistResource(
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    persistDomain();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_renew_response.xml"));
  }

  @Test
  public void testFailure_feeNotProvidedOnPremiumName() throws Exception {
    createTld("example");
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(true).build());
    setEppInput("domain_renew_premium.xml");
    persistDomain();
    thrown.expect(FeesRequiredForPremiumNameException.class);
    runFlow();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    persistDomain();
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-dom-renew");
    assertTldsFieldLogged("tld");
  }

  @Test
  public void testIcannTransactionRecord_getsStored() throws Exception {
    persistDomain();
    // Test with a nonstandard Renew period to ensure the reporting time is correct regardless
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setRenewGracePeriodLength(Duration.standardMinutes(9))
            .build());
    runFlow();
    DomainResource domain = reloadResourceByForeignKey();
    HistoryEntry historyEntry =
        getOnlyHistoryEntryOfType(domain, HistoryEntry.Type.DOMAIN_RENEW);
    assertThat(historyEntry.getDomainTransactionRecords()).containsExactly(
        DomainTransactionRecord.create(
            "tld",
            historyEntry.getModificationTime().plusMinutes(9),
            TransactionReportField.netRenewsFieldFromYears(5),
            1));
  }
}
