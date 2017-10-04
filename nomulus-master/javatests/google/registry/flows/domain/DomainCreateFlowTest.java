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

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static google.registry.model.domain.fee.Fee.FEE_EXTENSION_URIS;
import static google.registry.model.eppcommon.StatusValue.OK;
import static google.registry.model.eppcommon.StatusValue.SERVER_TRANSFER_PROHIBITED;
import static google.registry.model.eppcommon.StatusValue.SERVER_UPDATE_PROHIBITED;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.pricing.PricingEngineProxy.isDomainPremium;
import static google.registry.testing.DatastoreHelper.assertBillingEvents;
import static google.registry.testing.DatastoreHelper.assertPollMessagesForResource;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.deleteTld;
import static google.registry.testing.DatastoreHelper.getHistoryEntries;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.newContactResource;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveDomainApplication;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistDeletedDomain;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainResourceSubject.assertAboutDomains;
import static google.registry.testing.EppExceptionSubject.assertAboutEppExceptions;
import static google.registry.testing.TaskQueueHelper.assertDnsTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertNoDnsTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static google.registry.tmch.LordnTask.QUEUE_CLAIMS;
import static google.registry.tmch.LordnTask.QUEUE_SUNRISE;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.EUR;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import google.registry.flows.EppException.UnimplementedExtensionException;
import google.registry.flows.EppRequestSource;
import google.registry.flows.ExtensionManager.UndeclaredServiceExtensionException;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.domain.DomainCreateFlow.DomainHasOpenApplicationsException;
import google.registry.flows.domain.DomainCreateFlow.NoGeneralRegistrationsInCurrentPhaseException;
import google.registry.flows.domain.DomainFlowUtils.AcceptedTooLongAgoException;
import google.registry.flows.domain.DomainFlowUtils.BadDomainNameCharacterException;
import google.registry.flows.domain.DomainFlowUtils.BadDomainNamePartsCountException;
import google.registry.flows.domain.DomainFlowUtils.BadPeriodUnitException;
import google.registry.flows.domain.DomainFlowUtils.ClaimsPeriodEndedException;
import google.registry.flows.domain.DomainFlowUtils.CurrencyUnitMismatchException;
import google.registry.flows.domain.DomainFlowUtils.CurrencyValueScaleException;
import google.registry.flows.domain.DomainFlowUtils.DashesInThirdAndFourthException;
import google.registry.flows.domain.DomainFlowUtils.DomainLabelTooLongException;
import google.registry.flows.domain.DomainFlowUtils.DomainNameExistsAsTldException;
import google.registry.flows.domain.DomainFlowUtils.DomainNotAllowedForTldWithCreateRestrictionException;
import google.registry.flows.domain.DomainFlowUtils.DomainReservedException;
import google.registry.flows.domain.DomainFlowUtils.DuplicateContactForRoleException;
import google.registry.flows.domain.DomainFlowUtils.EmptyDomainNamePartException;
import google.registry.flows.domain.DomainFlowUtils.ExceedsMaxRegistrationYearsException;
import google.registry.flows.domain.DomainFlowUtils.ExpiredClaimException;
import google.registry.flows.domain.DomainFlowUtils.FeesMismatchException;
import google.registry.flows.domain.DomainFlowUtils.FeesRequiredForPremiumNameException;
import google.registry.flows.domain.DomainFlowUtils.InvalidIdnDomainLabelException;
import google.registry.flows.domain.DomainFlowUtils.InvalidLrpTokenException;
import google.registry.flows.domain.DomainFlowUtils.InvalidPunycodeException;
import google.registry.flows.domain.DomainFlowUtils.InvalidTcnIdChecksumException;
import google.registry.flows.domain.DomainFlowUtils.InvalidTrademarkValidatorException;
import google.registry.flows.domain.DomainFlowUtils.LeadingDashException;
import google.registry.flows.domain.DomainFlowUtils.LinkedResourceInPendingDeleteProhibitsOperationException;
import google.registry.flows.domain.DomainFlowUtils.LinkedResourcesDoNotExistException;
import google.registry.flows.domain.DomainFlowUtils.MalformedTcnIdException;
import google.registry.flows.domain.DomainFlowUtils.MaxSigLifeNotSupportedException;
import google.registry.flows.domain.DomainFlowUtils.MissingAdminContactException;
import google.registry.flows.domain.DomainFlowUtils.MissingClaimsNoticeException;
import google.registry.flows.domain.DomainFlowUtils.MissingContactTypeException;
import google.registry.flows.domain.DomainFlowUtils.MissingRegistrantException;
import google.registry.flows.domain.DomainFlowUtils.MissingTechnicalContactException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotAllowedForDomainException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotAllowedForTldException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotSpecifiedForNameserverRestrictedDomainException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotSpecifiedForTldWithNameserverWhitelistException;
import google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import google.registry.flows.domain.DomainFlowUtils.PremiumNameBlockedException;
import google.registry.flows.domain.DomainFlowUtils.RegistrantNotAllowedException;
import google.registry.flows.domain.DomainFlowUtils.TldDoesNotExistException;
import google.registry.flows.domain.DomainFlowUtils.TooManyDsRecordsException;
import google.registry.flows.domain.DomainFlowUtils.TooManyNameserversException;
import google.registry.flows.domain.DomainFlowUtils.TrailingDashException;
import google.registry.flows.domain.DomainFlowUtils.UnexpectedClaimsNoticeException;
import google.registry.flows.domain.DomainFlowUtils.UnsupportedFeeAttributeException;
import google.registry.flows.domain.DomainFlowUtils.UnsupportedMarkTypeException;
import google.registry.flows.exceptions.OnlyToolCanPassMetadataException;
import google.registry.flows.exceptions.ResourceAlreadyExistsException;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Flag;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.domain.LrpTokenEntity;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.registry.Registry.TldType;
import google.registry.model.reporting.DomainTransactionRecord;
import google.registry.model.reporting.DomainTransactionRecord.TransactionReportField;
import google.registry.model.reporting.HistoryEntry;
import google.registry.monitoring.whitebox.EppMetric;
import google.registry.testing.DatastoreHelper;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import java.util.Map;
import javax.annotation.Nullable;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainCreateFlow}. */
public class DomainCreateFlowTest extends ResourceFlowTestCase<DomainCreateFlow, DomainResource> {

  private static final String CLAIMS_KEY = "2013041500/2/6/9/rJ1NrDO92vDsAzf7EQzgjX4R0000000001";

  public DomainCreateFlowTest() {
    setEppInput("domain_create.xml");
    clock.setTo(DateTime.parse("1999-04-03T22:00:00.0Z").minus(1));
  }

  @Before
  public void initCreateTest() throws Exception {
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder()
        .setReservedLists(persistReservedList(
            "tld-reserved",
            "reserved,FULLY_BLOCKED",
            "anchor,RESERVED_FOR_ANCHOR_TENANT,2fooBAR"))
        .build());
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
  }

  /**
   * Create host and contact entries for testing.
   * @param hostTld the TLD of the host (which might be an external TLD)
   */
  private void persistContactsAndHosts(String hostTld) {
    for (int i = 1; i <= 14; ++i) {
      persistActiveHost(String.format("ns%d.example.%s", i, hostTld));
    }
    persistActiveContact("jd1234");
    persistActiveContact("sh8013");
    clock.advanceOneMilli();
  }

  private void persistContactsAndHosts() {
    persistContactsAndHosts("net");  // domain_create.xml uses hosts on "net".
  }

  private void assertSuccessfulCreate(String domainTld, boolean isAnchorTenant) throws Exception {
    DomainResource domain = reloadResourceByForeignKey();

    // Calculate the total cost.
    Money cost = isDomainPremium(getUniqueIdFromCommand(), clock.nowUtc())
        ? Money.of(USD, 200)
        : Money.of(USD, 26);
    Money eapFee = Money.of(Registry.get(domainTld).getCurrency(),
        Registry.get(domainTld).getEapFeeFor(clock.nowUtc()).getCost());

    DateTime billingTime = isAnchorTenant
        ? clock.nowUtc().plus(Registry.get(domainTld).getAnchorTenantAddGracePeriodLength())
        : clock.nowUtc().plus(Registry.get(domainTld).getAddGracePeriodLength());
    ImmutableSet<BillingEvent.Flag> billingFlags = isAnchorTenant
        ? ImmutableSet.of(BillingEvent.Flag.ANCHOR_TENANT)
        : ImmutableSet.<BillingEvent.Flag>of();
    HistoryEntry historyEntry = getHistoryEntries(domain).get(0);
    assertAboutDomains().that(domain)
        .hasRegistrationExpirationTime(
            ofy().load().key(domain.getAutorenewBillingEvent()).now().getEventTime()).and()
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.DOMAIN_CREATE).and()
        .hasPeriodYears(2);
    // There should be one bill for the create and one for the recurring autorenew event.
    BillingEvent.OneTime createBillingEvent =
        new BillingEvent.OneTime.Builder()
            .setReason(Reason.CREATE)
            .setTargetId(getUniqueIdFromCommand())
            .setClientId("TheRegistrar")
            .setCost(cost)
            .setPeriodYears(2)
            .setEventTime(clock.nowUtc())
            .setBillingTime(billingTime)
            .setFlags(billingFlags)
            .setParent(historyEntry)
            .build();

    BillingEvent.Recurring renewBillingEvent =
        new BillingEvent.Recurring.Builder()
            .setReason(Reason.RENEW)
            .setFlags(ImmutableSet.of(Flag.AUTO_RENEW))
            .setTargetId(getUniqueIdFromCommand())
            .setClientId("TheRegistrar")
            .setEventTime(domain.getRegistrationExpirationTime())
            .setRecurrenceEndTime(END_OF_TIME)
            .setParent(historyEntry)
            .build();

    ImmutableSet.Builder<BillingEvent> expectedBillingEvents =
        new ImmutableSet.Builder<BillingEvent>().add(createBillingEvent).add(renewBillingEvent);

    // If EAP is applied, a billing event for EAP should be present.
    if (!eapFee.isZero()) {
      BillingEvent.OneTime eapBillingEvent =
          new BillingEvent.OneTime.Builder()
              .setReason(Reason.FEE_EARLY_ACCESS)
              .setTargetId(getUniqueIdFromCommand())
              .setClientId("TheRegistrar")
              .setCost(eapFee)
              .setEventTime(clock.nowUtc())
              .setBillingTime(billingTime)
              .setFlags(billingFlags)
              .setParent(historyEntry)
              .build();
      expectedBillingEvents.add(eapBillingEvent);
    }
    assertBillingEvents(expectedBillingEvents.build());
    assertPollMessagesForResource(
        domain,
        new PollMessage.Autorenew.Builder()
            .setTargetId(domain.getFullyQualifiedDomainName())
            .setClientId("TheRegistrar")
            .setEventTime(domain.getRegistrationExpirationTime())
            .setMsg("Domain was auto-renewed.")
            .setParent(historyEntry)
            .build());

    assertGracePeriods(
        domain.getGracePeriods(),
        ImmutableMap.of(
            GracePeriod.create(
                GracePeriodStatus.ADD,
                billingTime,
                "TheRegistrar",
                null),
            createBillingEvent));
    assertDnsTasksEnqueued(getUniqueIdFromCommand());
    assertEppResourceIndexEntityFor(domain);
  }

  private void assertNoLordn() throws Exception {
    assertNoLordn(null, null);
  }

  private void assertNoLordn(
      @Nullable String smdId, @Nullable LaunchNotice launchNotice) throws Exception {
    assertAboutDomains().that(reloadResourceByForeignKey())
        .hasSmdId(smdId).and()
        .hasLaunchNotice(launchNotice);
    assertNoTasksEnqueued(QUEUE_CLAIMS, QUEUE_SUNRISE);
  }

  private void assertSunriseLordn() throws Exception {
    assertAboutDomains().that(reloadResourceByForeignKey())
        .hasSmdId("0000001761376042759136-65535").and()
        .hasLaunchNotice(null);
    TaskMatcher task = new TaskMatcher().payload(
        reloadResourceByForeignKey().getRepoId()
            + ",test-validate.tld,0000001761376042759136-65535,1,2014-09-09T09:09:09.001Z");
    assertTasksEnqueued(QUEUE_SUNRISE, task);
  }

  private void assertClaimsLordn() throws Exception {
    assertAboutDomains().that(reloadResourceByForeignKey())
        .hasSmdId(null).and()
        .hasLaunchNotice(LaunchNotice.create(
            "370d0b7c9223372036854775807",
            "tmch",
            DateTime.parse("2010-08-16T09:00:00.0Z"),
            DateTime.parse("2009-08-16T09:00:00.0Z")));
    TaskMatcher task =
        new TaskMatcher()
            .payload(
                reloadResourceByForeignKey().getRepoId()
                    + ",example-one.tld,370d0b7c9223372036854775807,1,"
                    + "2009-08-16T09:00:00.001Z,2009-08-16T09:00:00.000Z");
    assertTasksEnqueued(QUEUE_CLAIMS, task);
  }

  private void doSuccessfulTest(
      String domainTld,
      String responseXmlFile,
      UserPrivileges userPrivileges) throws Exception {
    doSuccessfulTest(domainTld, responseXmlFile, userPrivileges, ImmutableMap.<String, String>of());
  }

  private void doSuccessfulTest(
      String domainTld,
      String responseXmlFile,
      UserPrivileges userPrivileges,
      Map<String, String> substitutions) throws Exception {
    assertTransactionalFlow(true);
    runFlowAssertResponse(
        CommitMode.LIVE, userPrivileges, readFile(responseXmlFile, substitutions));
    assertSuccessfulCreate(domainTld, false);
    assertNoLordn();
  }

  private void doSuccessfulTest(
      String domainTld,
      String responseXmlFile,
      Map<String, String> substitutions) throws Exception {
    doSuccessfulTest(domainTld, responseXmlFile, UserPrivileges.NORMAL, substitutions);
  }

  private void doSuccessfulTest(String domainTld, String responseXmlFile) throws Exception {
    doSuccessfulTest(domainTld, responseXmlFile, UserPrivileges.NORMAL);
  }

  private void doSuccessfulTest(String domainTld) throws Exception {
    doSuccessfulTest(domainTld, "domain_create_response.xml");
  }

  private void doSuccessfulTest() throws Exception {
    doSuccessfulTest("tld");
  }

  @Test
  public void testDryRun() throws Exception {
    persistContactsAndHosts();
    dryRunFlowAssertResponse(readFile("domain_create_response.xml"));
  }

  @Test
  public void testSuccess_neverExisted() throws Exception {
    persistContactsAndHosts();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_multipartTld() throws Exception {
    createTld("foo.tld");
    setEppInput("domain_create_with_tld.xml", ImmutableMap.of("TLD", "foo.tld"));
    persistContactsAndHosts("foo.tld");
    assertTransactionalFlow(true);
    String expectedResponseXml = loadFileWithSubstitutions(
        DomainCreateFlowTest.class,
        "domain_create_response_wildcard.xml",
        ImmutableMap.of("DOMAIN", "example.foo.tld"));
    runFlowAssertResponse(CommitMode.LIVE, UserPrivileges.NORMAL, expectedResponseXml);
    assertSuccessfulCreate("foo.tld", false);
    assertNoLordn();
  }

  @Test
  public void testFailure_domainNameExistsAsTld_lowercase() throws Exception {
    createTlds("foo.tld", "tld");
    setEppInput("domain_create_wildcard.xml", ImmutableMap.of("DOMAIN", "foo.tld"));
    persistContactsAndHosts();
    thrown.expect(DomainNameExistsAsTldException.class);
    runFlow();
  }

  @Test
  public void testFailure_domainNameExistsAsTld_uppercase() throws Exception {
    createTlds("foo.tld", "tld");
    setEppInput("domain_create_wildcard.xml", ImmutableMap.of("DOMAIN", "FOO.TLD"));
    persistContactsAndHosts();
    thrown.expect(BadDomainNameCharacterException.class);
    runFlow();
  }

  @Test
  public void testSuccess_anchorTenantViaExtension() throws Exception {
    eppRequestSource = EppRequestSource.TOOL;
    setEppInput("domain_create_anchor_tenant.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response.xml"));
    assertSuccessfulCreate("tld", true);
    assertNoLordn();
  }


  @Test
  public void testSuccess_lrp() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    LrpTokenEntity token = persistResource(
        new LrpTokenEntity.Builder()
            .setToken("lrptokentest")
            .setAssignee("example.tld")
            .setValidTlds(ImmutableSet.of("tld"))
            .build());
    setEppInput("domain_create_lrp.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response.xml"));
    assertSuccessfulCreate("tld", false);
    assertNoLordn();
    assertThat(ofy().load().entity(token).now().getRedemptionHistoryEntry()).isNotNull();
  }

  @Test
  public void testSuccess_withLrpToken_outsideOfLrp() throws Exception {
    // If a valid LRP token is passed in even though the TLD is not currently in an LRP phase,
    // just ignore the token and proceed with normal GA registration (i.e. LRP token should
    // remain unredeemed).
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(2), clock.nowUtc().minusDays(1)))
        .build());
    LrpTokenEntity token = persistResource(
        new LrpTokenEntity.Builder()
            .setToken("lrptokentest")
            .setAssignee("example.tld")
            .setValidTlds(ImmutableSet.of("tld"))
            .build());
    setEppInput("domain_create_lrp.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response.xml"));
    assertSuccessfulCreate("tld", false);
    assertNoLordn();
    assertThat(ofy().load().entity(token).now().getRedemptionHistoryEntry()).isNull();
  }

  @Test
  public void testSuccess_outsideOfLrp() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(2), clock.nowUtc().minusDays(1)))
        .build());
    persistContactsAndHosts();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_generalAvailability_ignoresEncodedSignedMark() throws Exception {
    createTld("tld", TldState.GENERAL_AVAILABILITY);
    clock.setTo(DateTime.parse("2014-09-09T09:09:09Z"));
    setEppInput("domain_create_registration_encoded_signed_mark.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlow();
    assertSuccessfulCreate("tld", true);
    assertNoLordn("0000001761376042759136-65535", null);
  }

  @Test
  public void testSuccess_generalAvailability_ignoresEncodedSignedMarkMismatch() throws Exception {
    createTld("tld", TldState.GENERAL_AVAILABILITY);
    clock.setTo(DateTime.parse("2014-09-09T09:09:09Z"));
    setEppInput("domain_create_registration_encoded_signed_mark_mismatched_label.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlowAsSuperuser();
    assertSuccessfulCreate("tld", true);
    assertNoLordn();
  }

  @Test
  public void testSuccess_fee_v06() throws Exception {
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    doSuccessfulTest(
        "tld", "domain_create_response_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
  }

  @Test
  public void testSuccess_fee_v11() throws Exception {
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    doSuccessfulTest(
        "tld", "domain_create_response_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
  }

  @Test
  public void testSuccess_fee_v12() throws Exception {
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    doSuccessfulTest(
        "tld", "domain_create_response_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes_v06() throws Exception {
    setEppInput("domain_create_fee_defaults.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    doSuccessfulTest(
        "tld", "domain_create_response_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes_v11() throws Exception {
    setEppInput("domain_create_fee_defaults.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    doSuccessfulTest(
        "tld", "domain_create_response_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
  }

  @Test
  public void testSuccess_fee_withDefaultAttributes_v12() throws Exception {
    setEppInput("domain_create_fee_defaults.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    doSuccessfulTest(
        "tld", "domain_create_response_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
  }

  @Test
  public void testFailure_refundableFee_v06() throws Exception {
    setEppInput("domain_create_fee_refundable.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_refundableFee_v11() throws Exception {
    setEppInput("domain_create_fee_refundable.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_refundableFee_v12() throws Exception {
    setEppInput("domain_create_fee_refundable.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_gracePeriodFee_v06() throws Exception {
    setEppInput("domain_create_fee_grace_period.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_gracePeriodFee_v11() throws Exception {
    setEppInput("domain_create_fee_grace_period.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_gracePeriodFee_v12() throws Exception {
    setEppInput("domain_create_fee_grace_period.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_appliedFee_v06() throws Exception {
    setEppInput("domain_create_fee_applied.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_appliedFee_v11() throws Exception {
    setEppInput("domain_create_fee_applied.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_appliedFee_v12() throws Exception {
    setEppInput("domain_create_fee_applied.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testSuccess_metadata() throws Exception {
    eppRequestSource = EppRequestSource.TOOL;
    setEppInput("domain_create_metadata.xml");
    persistContactsAndHosts();
    doSuccessfulTest();
    assertAboutDomains().that(reloadResourceByForeignKey())
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.DOMAIN_CREATE).and()
        .hasMetadataReason("domain-create-test").and()
        .hasMetadataRequestedByRegistrar(false);
  }

  @Test
  public void testFailure_metadataNotFromTool() throws Exception {
    setEppInput("domain_create_metadata.xml");
    persistContactsAndHosts();
    thrown.expect(OnlyToolCanPassMetadataException.class);
    runFlow();
  }

  @Test
  public void testFailure_lrp_badToken() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    LrpTokenEntity token = persistResource(
        new LrpTokenEntity.Builder()
            .setToken("otherlrptoken")
            .setAssignee("example.tld")
            .setValidTlds(ImmutableSet.of("tld"))
            .build());
    setEppInput("domain_create_lrp.xml");
    persistContactsAndHosts();
    thrown.expect(InvalidLrpTokenException.class, "Invalid limited registration period token");
    runFlow();
    assertThat(ofy().load().entity(token).now().getRedemptionHistoryEntry()).isNull();
  }

  @Test
  public void testFailure_lrp_noToken() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    persistContactsAndHosts();
    thrown.expect(InvalidLrpTokenException.class, "Invalid limited registration period token");
    runFlow();
  }

  @Test
  public void testSuccess_premium() throws Exception {
    createTld("example");
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    setEppInput("domain_create_premium.xml");
    persistContactsAndHosts("net");
    doSuccessfulTest("example", "domain_create_response_premium.xml");
  }

  /**
   * Test fix for a bug where we were looking at the length of the unicode string but indexing
   * into the punycode string. In rare cases (3 and 4 letter labels) this would cause us to think
   * there was a trailing dash in the domain name and fail to create it.
   */
  @Test
  public void testSuccess_unicodeLengthBug() throws Exception {
    createTld("xn--q9jyb4c");
    persistContactsAndHosts("net");
    eppLoader.replaceAll("example.tld", "osx.xn--q9jyb4c");
    runFlow();
  }

  @Test
  public void testSuccess_nonDefaultAddGracePeriod() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setAddGracePeriodLength(Duration.standardMinutes(6))
        .build());
    persistContactsAndHosts();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_existedButWasDeleted() throws Exception {
    persistContactsAndHosts();
    persistDeletedDomain(getUniqueIdFromCommand(), clock.nowUtc().minusDays(1));
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_maxNumberOfNameservers() throws Exception {
    setEppInput("domain_create_13_nameservers.xml");
    persistContactsAndHosts();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_secDns() throws Exception {
    setEppInput("domain_create_dsdata_no_maxsiglife.xml");
    persistContactsAndHosts("tld");  // For some reason this sample uses "tld".
    doSuccessfulTest("tld");
    assertAboutDomains().that(reloadResourceByForeignKey()).hasExactlyDsData(
        DelegationSignerData.create(12345, 3, 1, base16().decode("49FD46E6C4B45C55D4AC")));
  }

  @Test
  public void testSuccess_secDnsMaxRecords() throws Exception {
    setEppInput("domain_create_dsdata_8_records.xml");
    persistContactsAndHosts("tld");  // For some reason this sample uses "tld".
    doSuccessfulTest("tld");
    assertAboutDomains().that(reloadResourceByForeignKey()).hasNumDsData(8);
  }

  @Test
  public void testSuccess_idn() throws Exception {
    createTld("xn--q9jyb4c");
    setEppInput("domain_create_idn_minna.xml");
    persistContactsAndHosts("net");
    runFlowAssertResponse(readFile("domain_create_response_idn_minna.xml"));
    assertSuccessfulCreate("xn--q9jyb4c", false);
    assertDnsTasksEnqueued("xn--abc-873b2e7eb1k8a4lpjvv.xn--q9jyb4c");
  }

  @Test
  public void testSuccess_noNameserversOrDsData() throws Exception {
    setEppInput("domain_create_no_hosts_or_dsdata.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response.xml"));
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testSuccess_periodNotSpecified() throws Exception {
    setEppInput("domain_create_missing_period.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response.xml"),
        "epp.response.resData.creData.exDate");  // Ignore expiration date; we verify it below
    assertAboutDomains().that(reloadResourceByForeignKey())
        .hasRegistrationExpirationTime(clock.nowUtc().plusYears(1));
    assertDnsTasksEnqueued("example.tld");
  }

  @Test
  public void testFailure_periodInMonths() throws Exception {
    setEppInput("domain_create_months.xml");
    persistContactsAndHosts();
    thrown.expect(BadPeriodUnitException.class);
    runFlow();
  }

  @Test
  public void testSuccess_claimsNotice() throws Exception {
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_claim_notice.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response_claims.xml"));
    assertSuccessfulCreate("tld", false);
    assertDnsTasksEnqueued("example-one.tld");
    assertClaimsLordn();
  }

  @Test
  public void testSuccess_noClaimsNotice_forClaimsListName_afterClaimsPeriodEnd() throws Exception {
    persistClaimsList(ImmutableMap.of("example", CLAIMS_KEY));
    setEppInput("domain_create.xml");
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setClaimsPeriodEnd(clock.nowUtc())
        .build());
    runFlowAssertResponse(readFile("domain_create_response.xml"));
    assertSuccessfulCreate("tld", false);
    assertDnsTasksEnqueued("example.tld");
  }

  @Test
  public void testFailure_missingClaimsNotice() throws Exception {
    persistClaimsList(ImmutableMap.of("example", CLAIMS_KEY));
    setEppInput("domain_create.xml");
    persistContactsAndHosts();
    thrown.expect(MissingClaimsNoticeException.class);
    runFlow();
  }

  @Test
  public void testFailure_claimsNoticeProvided_nameNotOnClaimsList() throws Exception {
    setEppInput("domain_create_claim_notice.xml");
    persistClaimsList(ImmutableMap.<String, String>of());
    persistContactsAndHosts();
    thrown.expect(UnexpectedClaimsNoticeException.class);
    runFlow();
  }

  @Test
  public void testFailure_claimsNoticeProvided_claimsPeriodEnded() throws Exception {
    setEppInput("domain_create_claim_notice.xml");
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setClaimsPeriodEnd(clock.nowUtc())
        .build());
    thrown.expect(ClaimsPeriodEndedException.class);
    runFlow();
  }

  @Test
  public void testFailure_tooManyNameservers() throws Exception {
    setEppInput("domain_create_14_nameservers.xml");
    persistContactsAndHosts();
    thrown.expect(TooManyNameserversException.class);
    runFlow();
  }

  @Test
  public void testFailure_secDnsMaxSigLife() throws Exception {
    setEppInput("domain_create_dsdata.xml");
    persistContactsAndHosts();
    thrown.expect(MaxSigLifeNotSupportedException.class);
    runFlow();
  }

  @Test
  public void testFailure_secDnsTooManyDsRecords() throws Exception {
    setEppInput("domain_create_dsdata_9_records.xml");
    persistContactsAndHosts();
    thrown.expect(TooManyDsRecordsException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongExtension() throws Exception {
    setEppInput("domain_create_wrong_extension.xml");
    persistContactsAndHosts();
    thrown.expect(UnimplementedExtensionException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeAmount_v06() throws Exception {
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistResource(
        Registry.get("tld").asBuilder().setCreateBillingCost(Money.of(USD, 20)).build());
    persistContactsAndHosts();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeAmount_v11() throws Exception {
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistResource(
        Registry.get("tld").asBuilder().setCreateBillingCost(Money.of(USD, 20)).build());
    persistContactsAndHosts();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeAmount_v12() throws Exception {
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistResource(
        Registry.get("tld").asBuilder().setCreateBillingCost(Money.of(USD, 20)).build());
    persistContactsAndHosts();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency_v06() throws Exception {
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistResource(Registry.get("tld").asBuilder()
        .setCurrency(CurrencyUnit.EUR)
        .setCreateBillingCost(Money.of(EUR, 13))
        .setRestoreBillingCost(Money.of(EUR, 11))
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
        .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
        .setServerStatusChangeBillingCost(Money.of(EUR, 19))
        .build());
    persistContactsAndHosts();
    thrown.expect(CurrencyUnitMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency_v11() throws Exception {
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistResource(Registry.get("tld").asBuilder()
        .setCurrency(CurrencyUnit.EUR)
        .setCreateBillingCost(Money.of(EUR, 13))
        .setRestoreBillingCost(Money.of(EUR, 11))
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
        .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
        .setServerStatusChangeBillingCost(Money.of(EUR, 19))
        .build());
    persistContactsAndHosts();
    thrown.expect(CurrencyUnitMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency_v12() throws Exception {
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistResource(Registry.get("tld").asBuilder()
        .setCurrency(CurrencyUnit.EUR)
        .setCreateBillingCost(Money.of(EUR, 13))
        .setRestoreBillingCost(Money.of(EUR, 11))
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
        .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
        .setServerStatusChangeBillingCost(Money.of(EUR, 19))
        .build());
    persistContactsAndHosts();
    thrown.expect(CurrencyUnitMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_alreadyExists() throws Exception {
    persistContactsAndHosts();
    persistActiveDomain(getUniqueIdFromCommand());
    try {
      runFlow();
      assert_().fail(
          "Expected to throw ResourceAlreadyExistsException with message "
              + "Object with given ID (%s) already exists",
          getUniqueIdFromCommand());
    } catch (ResourceAlreadyExistsException e) {
      assertAboutEppExceptions().that(e).marshalsToXml().and().hasMessage(
          String.format("Object with given ID (%s) already exists", getUniqueIdFromCommand()));
    }
  }


  @Test
  public void testFailure_reserved() throws Exception {
    setEppInput("domain_create_reserved.xml");
    persistContactsAndHosts();
    thrown.expect(DomainReservedException.class);
    runFlow();
  }

  @Test
  public void testFailure_anchorTenantViaAuthCode_wrongAuthCode() throws Exception {
    setEppInput("domain_create_anchor_wrong_authcode.xml");
    persistContactsAndHosts();
    thrown.expect(DomainReservedException.class);
    runFlow();
  }

  @Test
  public void testSuccess_anchorTenantViaAuthCode_matchingLrpToken() throws Exception {
    // This is definitely a corner case, as (without superuser) anchor tenants may only register
    // via auth code during GA. We're running this as superuser to bypass the state checks, though
    // anchor tenant code checks and LRP token redemption still happen regardless.
    createTld("tld", TldState.LANDRUSH);
    persistResource(Registry.get("tld").asBuilder()
        .setReservedLists(persistReservedList(
            "tld-reserved",
            "anchor,RESERVED_FOR_ANCHOR_TENANT,2fooBAR"))
        .build());
    LrpTokenEntity token = persistResource(new LrpTokenEntity.Builder()
        .setToken("2fooBAR")
        .setAssignee("anchor.tld")
        .build());
    setEppInput("domain_create_anchor_authcode.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_create_anchor_response.xml"));
    assertSuccessfulCreate("tld", true);
    // Token should not be marked as used, since interpreting the authcode as anchor tenant should
    // take precedence.
    assertThat(ofy().load().entity(token).now().getRedemptionHistoryEntry()).isNull();
  }

  @Test
  public void testSuccess_anchorTenantViaAuthCode() throws Exception {
    setEppInput("domain_create_anchor_authcode.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_anchor_response.xml"));
    assertSuccessfulCreate("tld", true);
    assertNoLordn();
  }

  @Test
  public void testSuccess_anchorTenantViaAuthCode_duringLrp() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    setEppInput("domain_create_anchor_authcode.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_anchor_response.xml"));
    assertSuccessfulCreate("tld", true);
    assertNoLordn();
  }

  @Test
  public void testSuccess_anchorTenantViaExtension_duringLrp() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    eppRequestSource = EppRequestSource.TOOL;
    setEppInput("domain_create_anchor_tenant.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response.xml"));
    assertSuccessfulCreate("tld", true);
    assertNoLordn();
  }


  @Test
  public void testSuccess_anchorTenantViaAuthCode_withClaims() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setReservedLists(persistReservedList(
            "anchor-with-claims",
            "example-one,RESERVED_FOR_ANCHOR_TENANT,2fooBAR"))
        .build());
    setEppInput("domain_create_claim_notice.xml");
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response_claims.xml"));
    assertSuccessfulCreate("tld", true);
    assertDnsTasksEnqueued("example-one.tld");
    assertClaimsLordn();
  }

  @Test
  public void testSuccess_superuserReserved() throws Exception {
    setEppInput("domain_create_reserved.xml");
    persistContactsAndHosts();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_create_reserved_response.xml"));
    assertSuccessfulCreate("tld", false);
  }

  @Test
  public void testFailure_missingHost() throws Exception {
    persistActiveHost("ns1.example.net");
    persistActiveContact("jd1234");
    persistActiveContact("sh8013");
    thrown.expect(
        LinkedResourcesDoNotExistException.class,
        "(ns2.example.net)");
    runFlow();
  }

  @Test
  public void testFailure_pendingDeleteHost() throws Exception {
    persistActiveHost("ns1.example.net");
    persistActiveContact("jd1234");
    persistActiveContact("sh8013");
    persistResource(newHostResource("ns2.example.net").asBuilder()
        .addStatusValue(StatusValue.PENDING_DELETE)
        .build());
    clock.advanceOneMilli();
    thrown.expect(
        LinkedResourceInPendingDeleteProhibitsOperationException.class,
        "ns2.example.net");
    runFlow();
  }

  @Test
  public void testFailure_openApplication() throws Exception {
    persistContactsAndHosts();
    persistActiveDomainApplication(getUniqueIdFromCommand());
    clock.advanceOneMilli();
    thrown.expect(DomainHasOpenApplicationsException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserOpenApplication() throws Exception {
    persistContactsAndHosts();
    persistActiveDomainApplication(getUniqueIdFromCommand());
    doSuccessfulTest("tld", "domain_create_response.xml", UserPrivileges.SUPERUSER);
  }

  @Test
  public void testSuccess_rejectedApplication() throws Exception {
    persistContactsAndHosts();
    persistResource(newDomainApplication(getUniqueIdFromCommand()).asBuilder()
        .setApplicationStatus(ApplicationStatus.REJECTED)
        .build());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testFailure_missingContact() throws Exception {
    persistActiveHost("ns1.example.net");
    persistActiveHost("ns2.example.net");
    persistActiveContact("jd1234");
    thrown.expect(
        LinkedResourcesDoNotExistException.class,
        "(sh8013)");
    runFlow();
  }

  @Test
  public void testFailure_pendingDeleteContact() throws Exception {
    persistActiveHost("ns1.example.net");
    persistActiveHost("ns2.example.net");
    persistActiveContact("sh8013");
    persistResource(newContactResource("jd1234").asBuilder()
        .addStatusValue(StatusValue.PENDING_DELETE)
        .build());
    clock.advanceOneMilli();
    thrown.expect(
        LinkedResourceInPendingDeleteProhibitsOperationException.class,
        "jd1234");
    runFlow();
  }

  @Test
  public void testFailure_wrongTld() throws Exception {
    persistContactsAndHosts("net");
    deleteTld("tld");
    thrown.expect(TldDoesNotExistException.class);
    runFlow();
  }

  @Test
  public void testFailure_predelegation() throws Exception {
    createTld("tld", TldState.PREDELEGATION);
    persistContactsAndHosts();
    thrown.expect(NoGeneralRegistrationsInCurrentPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_sunrise() throws Exception {
    createTld("tld", TldState.SUNRISE);
    persistContactsAndHosts();
    thrown.expect(NoGeneralRegistrationsInCurrentPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_sunrush() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistContactsAndHosts();
    thrown.expect(NoGeneralRegistrationsInCurrentPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrush() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    thrown.expect(NoGeneralRegistrationsInCurrentPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_quietPeriod() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistContactsAndHosts();
    thrown.expect(NoGeneralRegistrationsInCurrentPhaseException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserPredelegation() throws Exception {
    createTld("tld", TldState.PREDELEGATION);
    persistContactsAndHosts();
    doSuccessfulTest("tld", "domain_create_response.xml", UserPrivileges.SUPERUSER);
  }

  @Test
  public void testSuccess_superuserSunrush() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistContactsAndHosts();
    doSuccessfulTest("tld", "domain_create_response.xml", UserPrivileges.SUPERUSER);
  }

  @Test
  public void testSuccess_superuserQuietPeriod() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistContactsAndHosts();
    doSuccessfulTest("tld", "domain_create_response.xml", UserPrivileges.SUPERUSER);
  }

  @Test
  public void testSuccess_superuserOverridesPremiumNameBlock() throws Exception {
    createTld("example");
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    setEppInput("domain_create_premium.xml");
    persistContactsAndHosts("net");
    // Modify the Registrar to block premium names.
    persistResource(loadRegistrar("TheRegistrar").asBuilder().setBlockPremiumNames(true).build());
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_create_response_premium.xml"));
    assertSuccessfulCreate("example", false);
  }

  @Test
  public void testSuccess_customLogicIsCalled_andSavesExtraEntity() throws Exception {
    // @see TestDomainCreateFlowCustomLogic for what the label "custom-logic-test" triggers.
    ImmutableMap<String, String> substitutions = ImmutableMap.of("DOMAIN", "custom-logic-test.tld");
    setEppInput("domain_create_wildcard.xml", substitutions);
    persistContactsAndHosts();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.NORMAL,
        readFile("domain_create_response_wildcard.xml", substitutions));
    DomainResource domain = reloadResourceByForeignKey();
    HistoryEntry historyEntry = getHistoryEntries(domain).get(0);
    assertPollMessagesForResource(
        domain,
        new PollMessage.Autorenew.Builder()
            .setTargetId(domain.getFullyQualifiedDomainName())
            .setClientId("TheRegistrar")
            .setEventTime(domain.getRegistrationExpirationTime())
            .setMsg("Domain was auto-renewed.")
            .setParent(historyEntry)
            .build(),
        new PollMessage.OneTime.Builder()
            .setParent(historyEntry)
            .setEventTime(domain.getCreationTime())
            .setClientId("TheRegistrar")
            .setMsg("Custom logic was triggered")
            .setId(1L)
            .build());
  }

  @Test
  public void testFailure_duplicateContact() throws Exception {
    setEppInput("domain_create_duplicate_contact.xml");
    persistContactsAndHosts();
    thrown.expect(DuplicateContactForRoleException.class);
    runFlow();
  }

  @Test
  public void testFailure_missingContactType() throws Exception {
    // We need to test for missing type, but not for invalid - the schema enforces that for us.
    setEppInput("domain_create_missing_contact_type.xml");
    persistContactsAndHosts();
    thrown.expect(MissingContactTypeException.class);
    runFlow();
  }

  @Test
  public void testFailure_missingRegistrant() throws Exception {
    setEppInput("domain_create_missing_registrant.xml");
    persistContactsAndHosts();
    thrown.expect(MissingRegistrantException.class);
    runFlow();
  }

  @Test
  public void testFailure_missingAdmin() throws Exception {
    setEppInput("domain_create_missing_admin.xml");
    persistContactsAndHosts();
    thrown.expect(MissingAdminContactException.class);
    runFlow();
  }

  @Test
  public void testFailure_missingTech() throws Exception {
    setEppInput("domain_create_missing_tech.xml");
    persistContactsAndHosts();
    thrown.expect(MissingTechnicalContactException.class);
    runFlow();
  }

  @Test
  public void testFailure_missingNonRegistrantContacts() throws Exception {
    setEppInput("domain_create_missing_non_registrant_contacts.xml");
    persistContactsAndHosts();
    thrown.expect(MissingAdminContactException.class);
    runFlow();
  }

  @Test
  public void testFailure_badIdn() throws Exception {
    createTld("xn--q9jyb4c");
    setEppInput("domain_create_bad_idn_minna.xml");
    persistContactsAndHosts("net");
    thrown.expect(InvalidIdnDomainLabelException.class);
    runFlow();
  }

  @Test
  public void testFailure_badValidatorId() throws Exception {
    setEppInput("domain_create_bad_validator_id.xml");
    persistClaimsList(ImmutableMap.of("exampleone", CLAIMS_KEY));
    persistContactsAndHosts();
    thrown.expect(InvalidTrademarkValidatorException.class);
    runFlow();
  }

  @Test
  public void testFailure_codeMark() throws Exception {
    setEppInput("domain_create_code_with_mark.xml");
    persistContactsAndHosts();
    thrown.expect(UnsupportedMarkTypeException.class);
    runFlow();
  }

  @Test
  public void testFailure_signedMarkWithoutAnchorTenant() throws Exception {
    createTld("tld", TldState.SUNRISE);
    setEppInput("domain_create_signed_mark.xml");
    persistContactsAndHosts();
    thrown.expect(NoGeneralRegistrationsInCurrentPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_expiredClaim() throws Exception {
    clock.setTo(DateTime.parse("2010-08-17T09:00:00.0Z"));
    setEppInput("domain_create_claim_notice.xml");
    persistContactsAndHosts();
    thrown.expect(ExpiredClaimException.class);
    runFlow();
  }

  @Test
  public void testFailure_expiredAcceptance() throws Exception {
    clock.setTo(DateTime.parse("2009-09-16T09:00:00.0Z"));
    setEppInput("domain_create_claim_notice.xml");
    persistContactsAndHosts();
    thrown.expect(AcceptedTooLongAgoException.class);
    runFlow();
  }

  @Test
  public void testFailure_malformedTcnIdWrongLength() throws Exception {
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_malformed_claim_notice1.xml");
    persistContactsAndHosts();
    thrown.expect(MalformedTcnIdException.class);
    runFlow();
  }

  @Test
  public void testFailure_malformedTcnIdBadChar() throws Exception {
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_malformed_claim_notice2.xml");
    persistContactsAndHosts();
    thrown.expect(MalformedTcnIdException.class);
    runFlow();
  }

  @Test
  public void testFailure_badTcnIdChecksum() throws Exception {
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_bad_checksum_claim_notice.xml");
    persistContactsAndHosts();
    thrown.expect(InvalidTcnIdChecksumException.class);
    runFlow();
  }

  @Test
  public void testFailure_premiumBlocked() throws Exception {
    createTld("example");
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    setEppInput("domain_create_premium.xml");
    persistContactsAndHosts("net");
    // Modify the Registrar to block premium names.
    persistResource(loadRegistrar("TheRegistrar").asBuilder()
        .setBlockPremiumNames(true)
        .build());
    thrown.expect(PremiumNameBlockedException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeNotProvidedOnPremiumName() throws Exception {
    createTld("example");
    setEppInput("domain_create_premium.xml");
    persistContactsAndHosts("net");
    thrown.expect(FeesRequiredForPremiumNameException.class);
    runFlow();
  }

  @Test
  public void testFailure_omitFeeExtensionOnLogin_v06() throws Exception {
    for (String uri : FEE_EXTENSION_URIS) {
      removeServiceExtensionUri(uri);
    }
    createTld("net");
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    thrown.expect(UndeclaredServiceExtensionException.class);
    runFlow();
  }

  @Test
  public void testFailure_omitFeeExtensionOnLogin_v11() throws Exception {
    for (String uri : FEE_EXTENSION_URIS) {
      removeServiceExtensionUri(uri);
    }
    createTld("net");
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    thrown.expect(UndeclaredServiceExtensionException.class);
    runFlow();
  }

  @Test
  public void testFailure_omitFeeExtensionOnLogin_v12() throws Exception {
    for (String uri : FEE_EXTENSION_URIS) {
      removeServiceExtensionUri(uri);
    }
    createTld("net");
    setEppInput("domain_create_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    thrown.expect(UndeclaredServiceExtensionException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v06() throws Exception {
    setEppInput("domain_create_fee_bad_scale.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    thrown.expect(CurrencyValueScaleException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v11() throws Exception {
    setEppInput("domain_create_fee_bad_scale.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    thrown.expect(CurrencyValueScaleException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v12() throws Exception {
    setEppInput("domain_create_fee_bad_scale.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    thrown.expect(CurrencyValueScaleException.class);
    runFlow();
  }

  private void doFailingDomainNameTest(
      String domainName,
      Class<? extends Throwable> exception) throws Exception {
    setEppInput("domain_create_uppercase.xml");
    eppLoader.replaceAll("Example.tld", domainName);
    persistContactsAndHosts();
    thrown.expect(exception);
    runFlow();
  }

  @Test
  public void testFailure_uppercase() throws Exception {
    doFailingDomainNameTest("Example.tld", BadDomainNameCharacterException.class);
  }

  @Test
  public void testFailure_badCharacter() throws Exception {
    doFailingDomainNameTest("test_example.tld", BadDomainNameCharacterException.class);
  }

  @Test
  public void testFailure_leadingDash() throws Exception {
    doFailingDomainNameTest("-example.tld", LeadingDashException.class);
  }

  @Test
  public void testFailure_trailingDash() throws Exception {
    doFailingDomainNameTest("example-.tld", TrailingDashException.class);
  }

  @Test
  public void testFailure_tooLong() throws Exception {
    doFailingDomainNameTest(Strings.repeat("a", 64) + ".tld", DomainLabelTooLongException.class);
  }

  @Test
  public void testFailure_leadingDot() throws Exception {
    doFailingDomainNameTest(".example.tld", EmptyDomainNamePartException.class);
  }

  @Test
  public void testFailure_leadingDotTld() throws Exception {
    doFailingDomainNameTest("foo..tld", EmptyDomainNamePartException.class);
  }

  @Test
  public void testFailure_tooManyParts() throws Exception {
    doFailingDomainNameTest("foo.example.tld", BadDomainNamePartsCountException.class);
  }

  @Test
  public void testFailure_tooFewParts() throws Exception {
    doFailingDomainNameTest("tld", BadDomainNamePartsCountException.class);
  }

  @Test
  public void testFailure_invalidPunycode() throws Exception {
    doFailingDomainNameTest("xn--abcdefg.tld", InvalidPunycodeException.class);
  }

  @Test
  public void testFailure_dashesInThirdAndFourthPosition() throws Exception {
    doFailingDomainNameTest("ab--cdefg.tld", DashesInThirdAndFourthException.class);
  }

  @Test
  public void testFailure_tldDoesNotExist() throws Exception {
    doFailingDomainNameTest("foo.nosuchtld", TldDoesNotExistException.class);
  }

  @Test
  public void testFailure_invalidIdnCodePoints() throws Exception {
    // ❤☀☆☂☻♞☯.tld
    doFailingDomainNameTest("xn--k3hel9n7bxlu1e.tld", InvalidIdnDomainLabelException.class);
  }

  @Test
  public void testFailure_sunriseRegistration() throws Exception {
    createTld("tld", TldState.SUNRISE);
    setEppInput("domain_create_registration_sunrise.xml");
    persistContactsAndHosts();
    thrown.expect(NoGeneralRegistrationsInCurrentPhaseException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserSunriseRegistration() throws Exception {
    createTld("tld", TldState.SUNRISE);
    setEppInput("domain_create_registration_sunrise.xml");
    persistContactsAndHosts();
    doSuccessfulTest("tld", "domain_create_response.xml", UserPrivileges.SUPERUSER);
  }

  @Test
  public void testSuccess_qlpSunriseRegistration() throws Exception {
    createTld("tld", TldState.SUNRISE);
    setEppInput("domain_create_registration_qlp_sunrise.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response.xml"));
    assertSuccessfulCreate("tld", true);
    assertNoLordn();
  }

  @Test
  public void testSuccess_qlpSunriseRegistration_withEncodedSignedMark() throws Exception {
    createTld("tld", TldState.SUNRISE);
    clock.setTo(DateTime.parse("2014-09-09T09:09:09Z"));
    setEppInput("domain_create_registration_qlp_sunrise_encoded_signed_mark.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response_encoded_signed_mark_name.xml"));
    assertSuccessfulCreate("tld", true);
    assertSunriseLordn();
  }

  @Test
  public void testSuccess_qlpSunriseRegistration_withClaimsNotice() throws Exception {
    createTld("tld", TldState.SUNRISE);
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_registration_qlp_sunrise_claims_notice.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response_claims.xml"));
    assertSuccessfulCreate("tld", true);
    assertClaimsLordn();
  }

  @Test
  public void testFailure_sunrushRegistration() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_registration_sunrush.xml");
    persistContactsAndHosts();
    thrown.expect(NoGeneralRegistrationsInCurrentPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    createTld("irrelevant", "IRR");
    DatastoreHelper.persistResource(
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of("irrelevant"))
            .build());
    persistContactsAndHosts();
    thrown.expect(NotAuthorizedForTldException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserSunrushRegistration() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_registration_sunrush.xml");
    persistContactsAndHosts();
    doSuccessfulTest("tld", "domain_create_response.xml", UserPrivileges.SUPERUSER);
  }

  @Test
  public void testSuccess_qlpSunrushRegistration() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_registration_qlp_sunrush.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response.xml"));
    assertSuccessfulCreate("tld", true);
    assertNoLordn();
  }

  @Test
  public void testSuccess_qlpSunrushRegistration_withEncodedSignedMark() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    clock.setTo(DateTime.parse("2014-09-09T09:09:09Z"));
    setEppInput("domain_create_registration_qlp_sunrush_encoded_signed_mark.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response_encoded_signed_mark_name.xml"));
    assertSuccessfulCreate("tld", true);
    assertSunriseLordn();
  }

  @Test
  public void testSuccess_qlpSunrushRegistration_withClaimsNotice() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_registration_qlp_sunrush_claims_notice.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response_claims.xml"));
    assertSuccessfulCreate("tld", true);
    assertClaimsLordn();
  }

  @Test
  public void testFailure_landrushRegistration() throws Exception {
    thrown.expect(NoGeneralRegistrationsInCurrentPhaseException.class);
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_registration_landrush.xml");
    persistContactsAndHosts();
    runFlow();
  }

  @Test
  public void testSuccess_superuserLandrushRegistration() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_registration_landrush.xml");
    persistContactsAndHosts();
    doSuccessfulTest("tld", "domain_create_response.xml", UserPrivileges.SUPERUSER);
  }

  @Test
  public void testSuccess_qlpLandrushRegistration() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_registration_qlp_landrush.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response.xml"));
    assertSuccessfulCreate("tld", true);
    assertNoLordn();
  }

  @Test
  public void testSuccess_qlpLandrushRegistration_ignoresEncodedSignedMark() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    clock.setTo(DateTime.parse("2014-09-09T09:09:09Z"));
    setEppInput("domain_create_registration_qlp_landrush_encoded_signed_mark.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlow();
    assertSuccessfulCreate("tld", true);
    assertNoLordn("0000001761376042759136-65535", null);
  }

  @Test
  public void testSuccess_qlpLandrushRegistration_withClaimsNotice() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_registration_qlp_landrush_claims_notice.xml");
    eppRequestSource = EppRequestSource.TOOL;  // Only tools can pass in metadata.
    persistContactsAndHosts();
    runFlowAssertResponse(readFile("domain_create_response_claims.xml"));
    assertSuccessfulCreate("tld", true);
    assertClaimsLordn();
  }

  @Test
  public void testFailure_registrantNotWhitelisted() throws Exception {
    persistActiveContact("someone");
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setAllowedRegistrantContactIds(ImmutableSet.of("someone"))
        .build());
    thrown.expect(RegistrantNotAllowedException.class, "jd1234");
    runFlow();
  }

  @Test
  public void testFailure_nameserverNotWhitelisted() throws Exception {
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setAllowedFullyQualifiedHostNames(ImmutableSet.of("ns2.example.net"))
        .build());
    thrown.expect(NameserversNotAllowedForTldException.class, "ns1.example.net");
    runFlow();
  }

  @Test
  public void testFailure_emptyNameserverFailsWhitelist() throws Exception {
    setEppInput("domain_create_no_hosts_or_dsdata.xml");
    persistResource(Registry.get("tld").asBuilder()
        .setAllowedFullyQualifiedHostNames(ImmutableSet.of("somethingelse.example.net"))
        .build());
    persistContactsAndHosts();
    thrown.expect(NameserversNotSpecifiedForTldWithNameserverWhitelistException.class);
    runFlow();
  }

  @Test
  public void testSuccess_nameserverAndRegistrantWhitelisted() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setAllowedRegistrantContactIds(ImmutableSet.of("jd1234"))
        .setAllowedFullyQualifiedHostNames(ImmutableSet.of("ns1.example.net", "ns2.example.net"))
        .build());
    persistContactsAndHosts();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_domainNameserverRestricted_allNameserversAllowed() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "example,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns3.example.net"))
            .build());
    doSuccessfulTest();
  }

  @Test
  public void testFailure_domainNameserverRestricted_noNameservers() throws Exception {
    setEppInput("domain_create_no_hosts_or_dsdata.xml");
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "example,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns3.example.net"))
            .build());
    thrown.expect(NameserversNotSpecifiedForNameserverRestrictedDomainException.class);
    runFlow();
  }

  @Test
  public void testFailure_domainNameserverRestricted_someNameserversDisallowed() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns2.example.net:ns3.example.net"))
            .build());
    thrown.expect(NameserversNotAllowedForDomainException.class, "ns1.example.net");
    runFlow();
  }

  @Test
  public void testFailure_domainCreateRestricted_domainNotReserved() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setDomainCreateRestricted(true)
            .setReservedLists(
                persistReservedList(
                    "reserved", "lol,NAMESERVER_RESTRICTED,ns1.example.net:ns2.example.net"))
            .build());
    thrown.expect(DomainNotAllowedForTldWithCreateRestrictionException.class, "example.tld");
    runFlow();
  }

  @Test
  public void testSuccess_domainCreateNotRestricted_domainNotReserved() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "lol,NAMESERVER_RESTRICTED,ns1.example.net:ns2.example.net"))
            .build());
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_tldAndDomainNameserversWhitelistBothSatisfied() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setDomainCreateRestricted(true)
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.net", "ns2.example.net", "ns3.example.net"))
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "example,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns4.example.net"))
            .build());
    doSuccessfulTest();
  }

  @Test
  public void testFailure_domainNameserversAllowed_tldNameserversDisallowed() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns2.example.net", "ns3.example.net", "ns4.example.net"))
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "example,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns3.example.net"))
            .build());
    thrown.expect(NameserversNotAllowedForTldException.class, "ns1.example.net");
    runFlow();
  }

  @Test
  public void testFailure_domainNameserversDisallowed_tldNameserversAllowed() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.net", "ns2.example.net", "ns3.example.net"))
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "example,NAMESERVER_RESTRICTED,"
                        + "ns2.example.net:ns3.example.net:ns4.example.net"))
            .build());
    thrown.expect(NameserversNotAllowedForDomainException.class, "ns1.example.net");
    runFlow();
  }

  @Test
  public void testFailure_tldNameserversAllowed_domainCreateRestricted_domainNotReserved()
      throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setDomainCreateRestricted(true)
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.net", "ns2.example.net", "ns3.example.net"))
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "lol,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns3.example.net"))
            .build());
    thrown.expect(DomainNotAllowedForTldWithCreateRestrictionException.class, "example.tld");
    runFlow();
  }

  @Test
  public void testSuccess_domainCreateRestricted_serverStatusSet() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setDomainCreateRestricted(true)
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "example,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns3.example.net"))
            .build());
    doSuccessfulTest();
    assertThat(reloadResourceByForeignKey().getStatusValues())
        .containsExactly(SERVER_UPDATE_PROHIBITED, SERVER_TRANSFER_PROHIBITED);
  }

  @Test
  public void testSuccess_domainCreateNotRestricted_serverStatusNotSet() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "example,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns3.example.net"))
            .build());
    doSuccessfulTest();
    assertThat(reloadResourceByForeignKey().getStatusValues()).containsExactly(OK);
  }

  @Test
  public void testSuccess_eapFeeApplied_v06() throws Exception {
    setEppInput("domain_create_eap_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setEapFeeSchedule(ImmutableSortedMap.of(
            START_OF_TIME, Money.of(USD, 0),
            clock.nowUtc().minusDays(1), Money.of(USD, 100),
            clock.nowUtc().plusDays(1), Money.of(USD, 0)))
        .build());
    doSuccessfulTest(
        "tld", "domain_create_response_eap_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
  }

  @Test
  public void testSuccess_eapFeeApplied_v11() throws Exception {
    setEppInput("domain_create_eap_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setEapFeeSchedule(ImmutableSortedMap.of(
            START_OF_TIME, Money.of(USD, 0),
            clock.nowUtc().minusDays(1), Money.of(USD, 100),
            clock.nowUtc().plusDays(1), Money.of(USD, 0)))
        .build());
    doSuccessfulTest(
        "tld", "domain_create_response_eap_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
  }

  @Test
  public void testSuccess_eapFeeApplied_v12() throws Exception {
    setEppInput("domain_create_eap_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setEapFeeSchedule(ImmutableSortedMap.of(
            START_OF_TIME, Money.of(USD, 0),
            clock.nowUtc().minusDays(1), Money.of(USD, 100),
            clock.nowUtc().plusDays(1), Money.of(USD, 0)))
        .build());
    doSuccessfulTest(
        "tld", "domain_create_response_eap_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
  }

  @Test
  public void testSuccess_eapFee_beforeEntireSchedule() throws Exception {
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setEapFeeSchedule(ImmutableSortedMap.of(
            START_OF_TIME, Money.of(USD, 0),
            clock.nowUtc().plusDays(1), Money.of(USD, 10),
            clock.nowUtc().plusDays(2), Money.of(USD, 0)))
        .build());
    doSuccessfulTest("tld", "domain_create_response.xml");
  }

  @Test
  public void testSuccess_eapFee_afterEntireSchedule() throws Exception {
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setEapFeeSchedule(ImmutableSortedMap.of(
            START_OF_TIME, Money.of(USD, 0),
            clock.nowUtc().minusDays(2), Money.of(USD, 100),
            clock.nowUtc().minusDays(1), Money.of(USD, 0)))
        .build());
    doSuccessfulTest("tld", "domain_create_response.xml");
  }

  @Test
  public void testFailure_max10Years() throws Exception {
    setEppInput("domain_create_11_years.xml");
    persistContactsAndHosts();
    thrown.expect(ExceedsMaxRegistrationYearsException.class);
    runFlow();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    persistContactsAndHosts();
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-dom-create");
    assertTldsFieldLogged("tld");
    // Ensure we log the client ID for srs-dom-create so we can also use it for attempted-adds.
    assertClientIdFieldLogged("TheRegistrar");
  }

  @Test
  public void testIcannTransactionRecord_getsStored() throws Exception {
    persistContactsAndHosts();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAddGracePeriodLength(Duration.standardMinutes(9))
            .build());
    runFlow();
    DomainResource domain = reloadResourceByForeignKey();
    HistoryEntry historyEntry = getHistoryEntries(domain).get(0);
    assertThat(historyEntry.getDomainTransactionRecords()).containsExactly(
        DomainTransactionRecord.create(
            "tld",
            historyEntry.getModificationTime().plusMinutes(9),
            TransactionReportField.netAddsFieldFromYears(2),
            1));
  }

  @Test
  public void testIcannTransactionRecord_testTld_notStored() throws Exception {
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder().setTldType(TldType.TEST).build());
    runFlow();
    DomainResource domain = reloadResourceByForeignKey();
    HistoryEntry historyEntry = getHistoryEntries(domain).get(0);
    // No transaction records should be stored for test TLDs
    assertThat(historyEntry.getDomainTransactionRecords()).isEmpty();
  }

  @Test
  public void testEppMetric_isSuccessfullyCreated() throws Exception {
    persistContactsAndHosts();
    runFlow();
    EppMetric eppMetric = getEppMetric();
    assertThat(eppMetric.getCommandName()).hasValue("DomainCreate");
    assertThat(eppMetric.getAttempts()).isEqualTo(1);
  }
}
