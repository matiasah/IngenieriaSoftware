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

import static com.google.common.collect.Iterables.getLast;
import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static google.registry.model.index.DomainApplicationIndex.loadActiveApplicationsByDomainName;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.deleteTld;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainApplicationSubject.assertAboutApplications;
import static google.registry.testing.EppExceptionSubject.assertAboutEppExceptions;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.money.CurrencyUnit.EUR;
import static org.joda.money.CurrencyUnit.USD;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException.UnimplementedExtensionException;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.domain.DomainApplicationCreateFlow.LandrushApplicationDisallowedDuringSunriseException;
import google.registry.flows.domain.DomainApplicationCreateFlow.NoticeCannotBeUsedWithSignedMarkException;
import google.registry.flows.domain.DomainApplicationCreateFlow.SunriseApplicationDisallowedDuringLandrushException;
import google.registry.flows.domain.DomainApplicationCreateFlow.UncontestedSunriseApplicationBlockedInLandrushException;
import google.registry.flows.domain.DomainFlowTmchUtils.Base64RequiredForEncodedSignedMarksException;
import google.registry.flows.domain.DomainFlowTmchUtils.NoMarksFoundMatchingDomainException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarkCertificateExpiredException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarkCertificateInvalidException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarkCertificateNotYetValidException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarkCertificateRevokedException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarkCertificateSignatureException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarkEncodingErrorException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarkParsingErrorException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarkRevokedErrorException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarkSignatureException;
import google.registry.flows.domain.DomainFlowTmchUtils.SignedMarksMustBeEncodedException;
import google.registry.flows.domain.DomainFlowTmchUtils.TooManySignedMarksException;
import google.registry.flows.domain.DomainFlowUtils.AcceptedTooLongAgoException;
import google.registry.flows.domain.DomainFlowUtils.BadCommandForRegistryPhaseException;
import google.registry.flows.domain.DomainFlowUtils.BadDomainNameCharacterException;
import google.registry.flows.domain.DomainFlowUtils.BadDomainNamePartsCountException;
import google.registry.flows.domain.DomainFlowUtils.BadPeriodUnitException;
import google.registry.flows.domain.DomainFlowUtils.ClaimsPeriodEndedException;
import google.registry.flows.domain.DomainFlowUtils.CurrencyUnitMismatchException;
import google.registry.flows.domain.DomainFlowUtils.CurrencyValueScaleException;
import google.registry.flows.domain.DomainFlowUtils.DashesInThirdAndFourthException;
import google.registry.flows.domain.DomainFlowUtils.DomainLabelTooLongException;
import google.registry.flows.domain.DomainFlowUtils.DomainNameExistsAsTldException;
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
import google.registry.flows.domain.DomainFlowUtils.LaunchPhaseMismatchException;
import google.registry.flows.domain.DomainFlowUtils.LeadingDashException;
import google.registry.flows.domain.DomainFlowUtils.LinkedResourcesDoNotExistException;
import google.registry.flows.domain.DomainFlowUtils.MalformedTcnIdException;
import google.registry.flows.domain.DomainFlowUtils.MaxSigLifeNotSupportedException;
import google.registry.flows.domain.DomainFlowUtils.MissingClaimsNoticeException;
import google.registry.flows.domain.DomainFlowUtils.MissingContactTypeException;
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
import google.registry.flows.exceptions.ResourceAlreadyExistsException;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.LrpTokenEntity;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.launch.LaunchNotice;
import google.registry.model.domain.launch.LaunchPhase;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.registry.label.ReservedList;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.smd.SignedMarkRevocationList;
import google.registry.tmch.TmchCertificateAuthority;
import google.registry.tmch.TmchXmlSignature;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainApplicationCreateFlow}. */
public class DomainApplicationCreateFlowTest
    extends ResourceFlowTestCase<DomainApplicationCreateFlow, DomainApplication> {

  private static final String CLAIMS_KEY = "2013041500/2/6/9/rJ1NrDO92vDsAzf7EQzgjX4R0000000001";

  /** This is the id of the SMD stored in "domain_create_sunrise_encoded_signed_mark.xml". */
  public static final String SMD_ID = "0000001761376042759136-65535";

  private ReservedList createReservedList() {
    return persistReservedList(
        "tld-reserved",
        "testandvalidate,FULLY_BLOCKED",
        "test---validate,ALLOWED_IN_SUNRISE");
  }

  @Before
  public void setUp() throws Exception {
    setEppInput("domain_create_sunrise_encoded_signed_mark.xml");
    createTld("tld", TldState.SUNRISE);
    persistResource(Registry.get("tld").asBuilder().setReservedLists(createReservedList()).build());
    inject.setStaticField(TmchCertificateAuthority.class, "clock", clock);
    clock.setTo(DateTime.parse("2014-09-09T09:09:09Z"));
  }

  /** Create host and contact entries for testing. */
  private void persistContactsAndHosts() {
    for (int i = 1; i <= 14; ++i) {
      persistActiveHost(String.format("ns%d.example.net", i));
    }
    persistActiveContact("jd1234");
    persistActiveContact("sh8013");
    clock.advanceOneMilli();
  }

  private void doSuccessfulTest(String responseXmlFile, boolean sunriseApplication)
      throws Exception {
    doSuccessfulTest(responseXmlFile, sunriseApplication, 1, null, null);
  }

  private void doSuccessfulTest(
      String responseXmlFile, boolean sunriseApplication, int years)
      throws Exception {
    doSuccessfulTest(responseXmlFile, sunriseApplication, years, null, null);
  }

  private void doSuccessfulTest(
      String responseXmlFile,
      boolean sunriseApplication,
      String feeExtensionVersion,
      String feeExtensionNamespace) throws Exception {
    doSuccessfulTest(
        responseXmlFile, sunriseApplication, 1, feeExtensionVersion, feeExtensionNamespace);
  }

  private void doSuccessfulTest(
      String responseXmlFile,
      boolean sunriseApplication,
      int years, String feeExtensionVersion,
      String feeExtensionNamespace) throws Exception {
    assertTransactionalFlow(true);
    runFlowAssertResponse(
        (feeExtensionVersion == null)
          ? readFile(responseXmlFile)
          : readFile(
              responseXmlFile,
              ImmutableMap.of("FEE_VERSION", feeExtensionVersion, "FEE_NS", feeExtensionNamespace)),
        "epp.response.extension.creData.applicationID",
        "epp.response.resData.creData.crDate");
    // Check that the domain application was created and persisted with a history entry.
    // We want the one with the newest creation time, but lacking an index we need this code.
    List<DomainApplication> applications = ofy().load().type(DomainApplication.class).list();
    Collections.sort(applications, new Comparator<DomainApplication>() {
      @Override
      public int compare(DomainApplication a, DomainApplication b) {
        return a.getCreationTime().compareTo(b.getCreationTime());
      }});
    assertAboutApplications().that(getLast(applications))
        .hasFullyQualifiedDomainName(getUniqueIdFromCommand()).and()
        .hasNumEncodedSignedMarks(sunriseApplication ? 1 : 0).and()
        .hasPeriodYears(years).and()
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_CREATE).and()
        .hasPeriodYears(years);
    assertNoBillingEvents();
    assertThat(loadActiveApplicationsByDomainName(getUniqueIdFromCommand(), clock.nowUtc()))
        .containsExactlyElementsIn(applications);
    assertEppResourceIndexEntityFor(getLast(applications));
  }

  private void runSuperuserFlow(String filename) throws Exception {
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile(filename),
        "epp.response.extension.creData.applicationID",
        "epp.response.resData.creData.crDate",
        "epp.response.extension.creData.phase");
  }

  @Test
  public void testDryRun_sunrushApplication() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    dryRunFlowAssertResponse(readFile("domain_create_sunrush_encoded_signed_mark_response.xml"),
        "epp.response.extension.creData.applicationID", "epp.response.resData.creData.crDate");
  }

  @Test
  public void testFailure_signedMarkCorrupt() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark_corrupt.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SignedMarkParsingErrorException.class);
    runFlow();
  }

  @Test
  public void testFailure_signedMarkCertificateRevoked() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark_revoked_cert.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SignedMarkCertificateRevokedException.class);
    runFlow();
  }

  @Test
  public void testFailure_signedMarkCertificateExpired() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    clock.setTo(DateTime.parse("2022-01-01"));
    clock.setTo(DateTime.parse("2022-01-01"));
    thrown.expect(SignedMarkCertificateExpiredException.class);
    runFlow();
  }

  @Test
  public void testFailure_signedMarkCertificateNotYetValid() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    clock.setTo(DateTime.parse("2012-07-22T00:01:00Z"));
    thrown.expect(SignedMarkCertificateNotYetValidException.class);
    runFlow();
  }

  @Test
  public void testFailure_signedMarkCertificateCorrupt() throws Exception {
    useTmchProdCert();
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark_certificate_corrupt.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    // It's hard to make the real verification code throw a GeneralSecurityException. Instead,
    // replace the TmchXmlSignature with a stub that throws it for us.
    this.testTmchXmlSignature =  new TmchXmlSignature(null) {
      @Override
      public void verify(byte[] smdXml) throws GeneralSecurityException {
        throw new GeneralSecurityException();
      }};
    thrown.expect(SignedMarkCertificateInvalidException.class);
    runFlow();
  }

  @Test
  public void testFailure_signedMarkCertificateSignature() throws Exception {
    useTmchProdCert();
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SignedMarkCertificateSignatureException.class);
    runFlow();
  }

  @Test
  public void testFailure_signedMarkSignature() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark_signature_corrupt.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SignedMarkSignatureException.class);
    runFlow();
  }

  @Test
  public void testDryRun_landrushApplicationInSunrush() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    dryRunFlowAssertResponse(readFile("domain_create_sunrush_response.xml"),
        "epp.response.extension.creData.applicationID", "epp.response.resData.creData.crDate");
  }

  @Test
  public void testDryRun_landrushWithClaims() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_sunrush_claim_notice.xml");
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    dryRunFlowAssertResponse(readFile("domain_create_sunrush_response_claims.xml"),
        "epp.response.extension.creData.applicationID", "epp.response.resData.creData.crDate");
  }

  @Test
  public void testSuccess_sunrushApplication() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrush_encoded_signed_mark_response.xml", true);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class))
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testSuccess_sunrushApplicationReservedAllowedInSunrise() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_allowedinsunrise.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrush_allowedinsunrise_response.xml", true);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class))
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testSuccess_smdTrumpsClaimsList() throws Exception {
    persistClaimsList(ImmutableMap.of("test-validate", CLAIMS_KEY));
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_encoded_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrush_encoded_signed_mark_response.xml", true);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class))
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testSuccess_landrushApplicationInSunrush() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrush_response.xml", false);
  }

  @Test
  public void testFailure_landrushApplicationReservedAllowedInSunrise() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistResource(Registry.get("tld").asBuilder()
        .setReservedLists(ImmutableSet.of(createReservedList())).build());
    setEppInput("domain_create_landrush_allowedinsunrise.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(DomainReservedException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushApplicationPremiumBlocked() throws Exception {
    createTld("example", TldState.SUNRUSH);
    setEppInput("domain_create_landrush_premium.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    // Modify the Registrar to block premium names.
    persistResource(loadRegistrar("TheRegistrar").asBuilder().setBlockPremiumNames(true).build());
    thrown.expect(PremiumNameBlockedException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushFeeNotProvidedOnPremiumName() throws Exception {
    createTld("example", TldState.SUNRUSH);
    setEppInput("domain_create_landrush_premium.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(FeesRequiredForPremiumNameException.class);
    runFlow();
  }

  @Test
  public void testSuccess_landrushWithClaimsInSunrush() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_sunrush_claim_notice.xml");
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrush_response_claims.xml", false);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class))
        .hasLaunchNotice(LaunchNotice.create(
            "370d0b7c9223372036854775807",
            "tmch",
            DateTime.parse("2010-08-16T09:00:00.0Z"),
            DateTime.parse("2009-08-16T09:00:00.0Z")))
        .and()
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testSuccess_landrushWithoutClaimsInSunrush_forClaimsListName_afterClaimsPeriodEnd()
      throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistClaimsList(ImmutableMap.of("test-validate", CLAIMS_KEY));
    setEppInput("domain_create_sunrush.xml");
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setClaimsPeriodEnd(clock.nowUtc())
        .build());
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrush_response.xml", false);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class))
        .hasLaunchNotice(null)
        .and()
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testSuccess_landrushApplication() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_response.xml", false);
  }

  @Test
  public void testSuccess_landrushApplicationWithFee_v06() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_fee_response.xml", false, "0.6", "fee");
  }

  @Test
  public void testSuccess_landrushApplicationWithFee_v11() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_fee_response.xml", false, "0.11", "fee11");
  }

  @Test
  public void testSuccess_landrushApplicationWithFee_v12() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_fee_response.xml", false, "0.12", "fee12");
  }

  @Test
  public void testSuccess_landrushApplicationWithFee_withDefaultAttributes_v06() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee_defaults.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_fee_response.xml", false, "0.6", "fee");
  }

  @Test
  public void testSuccess_landrushApplicationWithFee_withDefaultAttributes_v11() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee_defaults.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_fee_response.xml", false, "0.11", "fee11");
  }

  @Test
  public void testSuccess_landrushApplicationWithFee_withDefaultAttributes_v12() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee_defaults.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_fee_response.xml", false, "0.12", "fee12");
  }

  @Test
  public void testFailure_landrushApplicationWithRefundableFee_v06() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    setEppInput("domain_create_landrush_fee_refundable.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushApplicationWithRefundableFee_v11() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    setEppInput(
        "domain_create_landrush_fee_refundable.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushApplicationWithRefundableFee_v12() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    setEppInput(
        "domain_create_landrush_fee_refundable.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushApplicationWithGracePeriodFee_v06() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    setEppInput(
        "domain_create_landrush_fee_grace_period.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushApplicationWithGracePeriodFee_v11() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    setEppInput(
        "domain_create_landrush_fee_grace_period.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushApplicationWithGracePeriodFee_v12() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    setEppInput(
        "domain_create_landrush_fee_grace_period.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushApplicationWithAppliedFee_v06() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    setEppInput("domain_create_landrush_fee_applied.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushApplicationWithAppliedFee_v11() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    setEppInput("domain_create_landrush_fee_applied.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushApplicationWithAppliedFee_v12() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    setEppInput("domain_create_landrush_fee_applied.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    thrown.expect(UnsupportedFeeAttributeException.class);
    runFlow();
  }

  @Test
  public void testSuccess_landrushWithClaims() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_landrush_claim_notice.xml");
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_response_claims.xml", false);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class))
        .hasLaunchNotice(LaunchNotice.create(
            "370d0b7c9223372036854775807",
            "tmch",
            DateTime.parse("2010-08-16T09:00:00.0Z"),
            DateTime.parse("2009-08-16T09:00:00.0Z")))
        .and()
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testSuccess_landrushWithoutClaims_forClaimsListName_afterClaimsPeriodEnd()
      throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistClaimsList(ImmutableMap.of("test-validate", CLAIMS_KEY));
    setEppInput("domain_create_landrush.xml");
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setClaimsPeriodEnd(clock.nowUtc())
        .build());
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_response.xml", false);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class))
        .hasLaunchNotice(null)
        .and()
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testSuccess_maxNumberOfNameservers() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_13_nameservers.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrush_response.xml", false);
  }

  @Test
  public void testSuccess_encodedSignedMarkWithWhitespace() throws Exception {
    setEppInput("domain_create_sunrise_encoded_signed_mark_with_whitespace.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrise_encoded_signed_mark_response.xml", true);
  }

  @Test
  public void testSuccess_atMarkCreationTime() throws Exception {
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrise_encoded_signed_mark_response.xml", true);
  }

  @Test
  public void testSuccess_smdRevokedInFuture() throws Exception {
    SignedMarkRevocationList.create(
        clock.nowUtc(), ImmutableMap.of(SMD_ID, clock.nowUtc().plusDays(1))).save();
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrise_encoded_signed_mark_response.xml", true);
  }

  @Test
  public void testSuccess_justBeforeMarkExpirationTime() throws Exception {
    persistContactsAndHosts();
    clock.advanceOneMilli();
    clock.setTo(DateTime.parse("2017-07-23T22:00:00.000Z").minusSeconds(1));
    doSuccessfulTest("domain_create_sunrise_encoded_signed_mark_response.xml", true);
  }

  @Test
  public void testSuccess_secDns() throws Exception {
    setEppInput("domain_create_sunrise_signed_mark_with_secdns.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrise_encoded_signed_mark_response.xml", true);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class))
        .hasExactlyDsData(DelegationSignerData.create(
            12345, 3, 1, base16().decode("49FD46E6C4B45C55D4AC")));
  }

  @Test
  public void testSuccess_secDnsMaxRecords() throws Exception {
    setEppInput("domain_create_sunrise_signed_mark_with_secdns_8_records.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrise_encoded_signed_mark_response.xml", true);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class)).hasNumDsData(8);
  }

  @Test
  public void testFailure_missingMarks() throws Exception {
    setEppInput("domain_create_sunrise_without_marks.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(LandrushApplicationDisallowedDuringSunriseException.class);
    runFlow();
  }

  @Test
  public void testFailure_sunriseApplicationInLandrush() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SunriseApplicationDisallowedDuringLandrushException.class);
    runFlow();
  }

  @Test
  public void testFailure_smdRevoked() throws Exception {
    SignedMarkRevocationList.create(clock.nowUtc(), ImmutableMap.of(SMD_ID, clock.nowUtc())).save();
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SignedMarkRevokedErrorException.class);
    runFlow();
  }

  @Test
  public void testFailure_tooManyNameservers() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_14_nameservers.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(TooManyNameserversException.class);
    runFlow();
  }

  @Test
  public void testFailure_secDnsMaxSigLife() throws Exception {
    setEppInput("domain_create_sunrise_with_secdns_maxsiglife.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(MaxSigLifeNotSupportedException.class);
    runFlow();
  }

  @Test
  public void testFailure_secDnsTooManyDsRecords() throws Exception {
    setEppInput("domain_create_sunrise_signed_mark_with_secdns_9_records.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(TooManyDsRecordsException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongExtension() throws Exception {
    setEppInput("domain_create_sunrise_wrong_extension.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(UnimplementedExtensionException.class);
    runFlow();
  }

  @Test
  public void testFailure_reserved() throws Exception {
    setEppInput("domain_create_sunrise_signed_mark_reserved.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(DomainReservedException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserReserved() throws Exception {
    setEppInput("domain_create_sunrise_signed_mark_reserved.xml");
    createTld("tld", TldState.SUNRISE);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runSuperuserFlow("domain_create_sunrise_signed_mark_reserved_response.xml");
  }

  @Test
  public void testSuccess_premiumNotBlocked() throws Exception {
    createTld("example", TldState.SUNRUSH);
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    setEppInput("domain_create_landrush_premium.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runFlowAssertResponse(
        readFile("domain_create_landrush_premium_response.xml"),
        "epp.response.extension.creData.applicationID",
        "epp.response.resData.creData.crDate",
        "epp.response.extension.creData.phase");
  }

  @Test
  public void testSuccess_superuserPremiumNameBlock() throws Exception {
    createTld("example", TldState.SUNRUSH);
    persistResource(Registry.get("example").asBuilder().setPremiumPriceAckRequired(false).build());
    setEppInput("domain_create_landrush_premium.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    // Modify the Registrar to block premium names.
    persistResource(loadRegistrar("TheRegistrar").asBuilder().setBlockPremiumNames(true).build());
    runSuperuserFlow("domain_create_landrush_premium_response.xml");
  }

  private DomainApplication persistSunriseApplication() throws Exception {
    return persistResource(newDomainApplication(getUniqueIdFromCommand())
        .asBuilder()
        .setPhase(LaunchPhase.SUNRISE)
        .build());
  }

  @Test
  public void testFailure_landrushWithOneSunriseApplication() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush.xml");
    persistSunriseApplication();
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(UncontestedSunriseApplicationBlockedInLandrushException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserLandrushWithOneSunriseApplication() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush.xml");
    persistSunriseApplication();
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runSuperuserFlow("domain_create_landrush_response.xml");
  }

  @Test
  public void testSuccess_landrushWithTwoSunriseApplications() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush.xml");
    persistSunriseApplication();
    persistSunriseApplication();
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_response.xml", false);
  }

  @Test
  public void testSuccess_landrushWithPeriodSpecified() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_two_years.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_response.xml", false, 2);
  }

  @Test
  public void testSuccess_landrushLrpApplication() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    LrpTokenEntity token = persistResource(
        new LrpTokenEntity.Builder()
            .setToken("lrptokentest")
            .setAssignee("test-validate.tld")
            .setValidTlds(ImmutableSet.of("tld"))
            .build());
    setEppInput("domain_create_landrush_lrp.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_landrush_response.xml", false);
    assertThat(ofy().load().entity(token).now().getRedemptionHistoryEntry()).isNotNull();
  }

  @Test
  public void testSuccess_landrushLrpApplication_superuser() throws Exception {
    // Using an LRP token as superuser should still mark the token as redeemed (i.e. same effect
    // as non-superuser).
    createTld("tld", TldState.LANDRUSH);
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    LrpTokenEntity token = persistResource(
        new LrpTokenEntity.Builder()
            .setToken("lrptokentest")
            .setAssignee("test-validate.tld")
            .setValidTlds(ImmutableSet.of("tld"))
            .build());
    setEppInput("domain_create_landrush_lrp.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runSuperuserFlow("domain_create_landrush_response.xml");
    assertThat(ofy().load().entity(token).now().getRedemptionHistoryEntry()).isNotNull();
  }

  @Test
  public void testFailure_landrushLrpApplication_badToken() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    persistResource(new LrpTokenEntity.Builder()
        .setToken("lrptokentest2")
        .setAssignee("test-validate.tld")
        .setValidTlds(ImmutableSet.of("tld"))
        .build());
    setEppInput("domain_create_landrush_lrp.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(InvalidLrpTokenException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushLrpApplication_tokenForWrongTld() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    persistResource(new LrpTokenEntity.Builder()
        .setToken("lrptokentest")
        // The below assignee doesn't really make sense here, but as of right now the validation
        // in DomainPricingLogic is just a match on the domain name, so this test ensures that
        // the registration fails due to invalid TLDs even if everything else otherwise matches.
        .setAssignee("test-validate.tld")
        .setValidTlds(ImmutableSet.of("other"))
        .build());
    setEppInput("domain_create_landrush_lrp.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(InvalidLrpTokenException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushLrpApplication_usedToken() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    persistResource(new LrpTokenEntity.Builder()
        .setToken("lrptokentest")
        .setAssignee("test-validate.tld")
        .setValidTlds(ImmutableSet.of("tld"))
        .setRedemptionHistoryEntry(
            Key.create(HistoryEntry.class, "1")) // as long as it's not null
        .build());
    setEppInput("domain_create_landrush_lrp.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(InvalidLrpTokenException.class);
    runFlow();
  }

  @Test
  public void testSuccess_landrushApplicationWithLrpToken_notInLrp() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    LrpTokenEntity token =
        persistResource(
            new LrpTokenEntity.Builder()
                .setToken("lrptokentest")
                .setAssignee("test-validate.tld")
                .setValidTlds(ImmutableSet.of("tld"))
                .build());
    setEppInput("domain_create_landrush_lrp.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    // Application should continue as normal, since the LRP token will just be ignored
    doSuccessfulTest("domain_create_landrush_response.xml", false);
    // Token should not be marked as used, since this isn't an LRP state
    assertThat(ofy().load().entity(token).now().getRedemptionHistoryEntry()).isNull();
  }

  @Test
  public void testSuccess_landrushApplicationWithLrpToken_noLongerLrp() throws Exception {
    createTld("tld");
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(2), clock.nowUtc().minusDays(1)))
        .setTldStateTransitions(ImmutableSortedMap.of(
            START_OF_TIME, TldState.SUNRISE,
            clock.nowUtc(), TldState.LANDRUSH))
        .build());
    LrpTokenEntity token = persistResource(
        new LrpTokenEntity.Builder()
            .setToken("lrptokentest")
            .setAssignee("test-validate.tld")
            .setValidTlds(ImmutableSet.of("tld"))
            .build());
    setEppInput("domain_create_landrush_lrp.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    // Application should continue as normal, since the LRP token will just be ignored
    doSuccessfulTest("domain_create_landrush_response.xml", false);
    // Token should not be marked as used, since this isn't an LRP state
    assertThat(ofy().load().entity(token).now().getRedemptionHistoryEntry()).isNull();
  }

  @Test
  public void testFailure_landrush_duringLrpWithMissingToken() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    persistResource(Registry.get("tld").asBuilder()
        .setLrpPeriod(new Interval(clock.nowUtc().minusDays(1), clock.nowUtc().plusDays(1)))
        .build());
    setEppInput("domain_create_landrush.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(InvalidLrpTokenException.class);
    runFlow();
  }

  @Test
  public void testFailure_landrushWithPeriodInMonths() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_months.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(BadPeriodUnitException.class);
    runFlow();
  }

  @Test
  public void testFailure_missingHost() throws Exception {
    persistActiveHost("ns1.example.net");
    persistActiveContact("jd1234");
    persistActiveContact("sh8013");
    thrown.expect(LinkedResourcesDoNotExistException.class, "(ns2.example.net)");
    runFlow();
  }

  @Test
  public void testFailure_missingContact() throws Exception {
    persistActiveHost("ns1.example.net");
    persistActiveHost("ns2.example.net");
    persistActiveContact("jd1234");
    thrown.expect(LinkedResourcesDoNotExistException.class, "(sh8013)");
    runFlow();
  }

  @Test
  public void testFailure_wrongTld() throws Exception {
    deleteTld("tld");
    createTld("foo", TldState.SUNRISE);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(TldDoesNotExistException.class);
    runFlow();
  }

  @Test
  public void testFailure_predelegation() throws Exception {
    createTld("tld", TldState.PREDELEGATION);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(BadCommandForRegistryPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    persistResource(
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    persistContactsAndHosts();
    thrown.expect(NotAuthorizedForTldException.class);
    runFlow();
  }

  @Test
  public void testFailure_sunrush() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(LaunchPhaseMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_quietPeriod() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(BadCommandForRegistryPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_generalAvailability() throws Exception {
    createTld("tld", TldState.GENERAL_AVAILABILITY);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(BadCommandForRegistryPhaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongDeclaredPhase() throws Exception {
    setEppInput("domain_create_landrush_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(LaunchPhaseMismatchException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserPredelegation() throws Exception {
    createTld("tld", TldState.PREDELEGATION);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runSuperuserFlow("domain_create_sunrush_response.xml");
  }

  @Test
  public void testSuccess_superuserNotAuthorizedForTld() throws Exception {
    persistResource(
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runSuperuserFlow("domain_create_sunrise_encoded_signed_mark_response.xml");
  }

  @Test
  public void testSuccess_superuserSunrush() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runSuperuserFlow("domain_create_sunrush_response.xml");
  }

  @Test
  public void testSuccess_superuserQuietPeriod() throws Exception {
    createTld("tld", TldState.QUIET_PERIOD);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runSuperuserFlow("domain_create_sunrush_response.xml");
  }

  @Test
  public void testSuccess_superuserGeneralAvailability() throws Exception {
    createTld("tld", TldState.GENERAL_AVAILABILITY);
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runSuperuserFlow("domain_create_sunrush_response.xml");
  }

  @Test
  public void testSuccess_superuserWrongDeclaredPhase() throws Exception {
    setEppInput("domain_create_landrush_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runSuperuserFlow("domain_create_sunrush_response.xml");
  }

  @Test
  public void testFailure_duplicateContact() throws Exception {
    setEppInput("domain_create_sunrise_duplicate_contact.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(DuplicateContactForRoleException.class);
    runFlow();
  }

  @Test
  public void testFailure_missingContactType() throws Exception {
    setEppInput("domain_create_sunrise_missing_contact_type.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    // We need to test for missing type, but not for invalid - the schema enforces that for us.
    thrown.expect(MissingContactTypeException.class);
    runFlow();
  }

  @Test
  public void testFailure_noMatchingMarks() throws Exception {
    setEppInput("domain_create_sunrise_no_matching_marks.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(NoMarksFoundMatchingDomainException.class);
    runFlow();
  }

  @Test
  public void testFailure_beforeMarkCreationTime() throws Exception {
    // If we move now back in time a bit, the mark will not have gone into effect yet.
    clock.setTo(DateTime.parse("2013-08-09T10:05:59Z").minusSeconds(1));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(NoMarksFoundMatchingDomainException.class);
    runFlow();
  }

  @Test
  public void testFailure_atMarkExpirationTime() throws Exception {
    // Move time forward to the mark expiration time.
    clock.setTo(DateTime.parse("2017-07-23T22:00:00.000Z"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(NoMarksFoundMatchingDomainException.class);
    runFlow();
  }

  @Test
  public void testFailure_hexEncoding() throws Exception {
    setEppInput("domain_create_sunrise_hex_encoding.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(Base64RequiredForEncodedSignedMarksException.class);
    runFlow();
  }

  @Test
  public void testFailure_badEncoding() throws Exception {
    setEppInput("domain_create_sunrise_bad_encoding.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SignedMarkEncodingErrorException.class);
    runFlow();
  }

  @Test
  public void testFailure_badEncodedXml() throws Exception {
    setEppInput("domain_create_sunrise_bad_encoded_xml.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SignedMarkParsingErrorException.class);
    runFlow();
  }

  @Test
  public void testFailure_badIdn() throws Exception {
    createTld("xn--q9jyb4c", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_bad_idn_minna.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(InvalidIdnDomainLabelException.class);
    runFlow();
  }

  @Test
  public void testFailure_badValidatorId() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    setEppInput("domain_create_sunrush_bad_validator_id.xml");
    persistClaimsList(ImmutableMap.of("exampleone", CLAIMS_KEY));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(InvalidTrademarkValidatorException.class);
    runFlow();
  }

  @Test
  public void testFailure_signedMark() throws Exception {
    setEppInput("domain_create_sunrise_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SignedMarksMustBeEncodedException.class);
    runFlow();
  }

  @Test
  public void testFailure_codeMark() throws Exception {
    setEppInput("domain_create_sunrise_code_with_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(UnsupportedMarkTypeException.class);
    runFlow();
  }

  @Test
  public void testFailure_emptyEncodedMarkData() throws Exception {
    setEppInput("domain_create_sunrise_empty_encoded_signed_mark.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(SignedMarkParsingErrorException.class);
    runFlow();
  }

  @Test
  public void testFailure_signedMarkAndNotice() throws Exception {
    setEppInput("domain_create_sunrise_signed_mark_and_notice.xml");
    persistClaimsList(ImmutableMap.of("exampleone", CLAIMS_KEY));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(NoticeCannotBeUsedWithSignedMarkException.class);
    runFlow();
  }

  @Test
  public void testFailure_twoSignedMarks() throws Exception {
    setEppInput("domain_create_sunrise_two_signed_marks.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(TooManySignedMarksException.class);
    runFlow();
  }

  @Test
  public void testFailure_missingClaimsNotice() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistClaimsList(ImmutableMap.of("test-validate", CLAIMS_KEY));
    setEppInput("domain_create_sunrush.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(MissingClaimsNoticeException.class);
    runFlow();
  }

  @Test
  public void testFailure_claimsNoticeProvided_nameNotOnClaimsList() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_claim_notice.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(UnexpectedClaimsNoticeException.class);
    runFlow();
  }

  @Test
  public void testFailure_claimsNoticeProvided_claimsPeriodEnded() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_claim_notice.xml");
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    persistContactsAndHosts();
    persistResource(Registry.get("tld").asBuilder()
        .setClaimsPeriodEnd(clock.nowUtc())
        .build());
    thrown.expect(ClaimsPeriodEndedException.class);
    runFlow();
  }

  @Test
  public void testFailure_expiredClaim() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    clock.setTo(DateTime.parse("2010-08-17T09:00:00.0Z"));
    setEppInput("domain_create_sunrush_claim_notice.xml");
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(ExpiredClaimException.class);
    runFlow();
  }

  @Test
  public void testFailure_expiredAcceptance() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    clock.setTo(DateTime.parse("2009-09-16T09:00:00.0Z"));
    setEppInput("domain_create_sunrush_claim_notice.xml");
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(AcceptedTooLongAgoException.class);
    runFlow();
  }

  @Test
  public void testFailure_malformedTcnIdWrongLength() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_sunrush_malformed_claim_notice1.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(MalformedTcnIdException.class);
    runFlow();
  }

  @Test
  public void testFailure_malformedTcnIdBadChar() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_sunrush_malformed_claim_notice2.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(MalformedTcnIdException.class);
    runFlow();
  }

  @Test
  public void testFailure_badTcnIdChecksum() throws Exception {
    createTld("tld", TldState.SUNRUSH);
    clock.setTo(DateTime.parse("2009-08-16T09:00:00.0Z"));
    setEppInput("domain_create_sunrush_bad_checksum_claim_notice.xml");
    persistClaimsList(ImmutableMap.of("example-one", CLAIMS_KEY));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(InvalidTcnIdChecksumException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeLandrushApplication_v06() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistResource(
        Registry.get("tld").asBuilder().setCreateBillingCost(Money.of(USD, 20)).build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeLandrushApplication_v11() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistResource(
        Registry.get("tld").asBuilder().setCreateBillingCost(Money.of(USD, 20)).build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongFeeLandrushApplication_v12() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistResource(
        Registry.get("tld").asBuilder().setCreateBillingCost(Money.of(USD, 20)).build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency_v06() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistResource(Registry.get("tld").asBuilder()
        .setCurrency(CurrencyUnit.EUR)
        .setCreateBillingCost(Money.of(EUR, 13))
        .setRestoreBillingCost(Money.of(EUR, 11))
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
        .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
        .setServerStatusChangeBillingCost(Money.of(EUR, 19))
        .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(CurrencyUnitMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency_v11() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistResource(Registry.get("tld").asBuilder()
        .setCurrency(CurrencyUnit.EUR)
        .setCreateBillingCost(Money.of(EUR, 13))
        .setRestoreBillingCost(Money.of(EUR, 11))
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
        .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
        .setServerStatusChangeBillingCost(Money.of(EUR, 19))
        .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(CurrencyUnitMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongCurrency_v12() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistResource(Registry.get("tld").asBuilder()
        .setCurrency(CurrencyUnit.EUR)
        .setCreateBillingCost(Money.of(EUR, 13))
        .setRestoreBillingCost(Money.of(EUR, 11))
        .setRenewBillingCostTransitions(ImmutableSortedMap.of(START_OF_TIME, Money.of(EUR, 7)))
        .setEapFeeSchedule(ImmutableSortedMap.of(START_OF_TIME, Money.zero(EUR)))
        .setServerStatusChangeBillingCost(Money.of(EUR, 19))
        .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(CurrencyUnitMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v06() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee_bad_scale.xml", ImmutableMap.of("FEE_VERSION", "0.6"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(CurrencyValueScaleException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v11() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee_bad_scale.xml", ImmutableMap.of("FEE_VERSION", "0.11"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(CurrencyValueScaleException.class);
    runFlow();
  }

  @Test
  public void testFailure_feeGivenInWrongScale_v12() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_fee_bad_scale.xml", ImmutableMap.of("FEE_VERSION", "0.12"));
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(CurrencyValueScaleException.class);
    runFlow();
  }


  @Test
  public void testFailure_alreadyExists() throws Exception {
    persistContactsAndHosts();
    clock.advanceOneMilli();
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
  public void testSuccess_nameserverAndRegistrantWhitelisted() throws Exception {
    persistResource(Registry.get("tld").asBuilder()
        .setAllowedRegistrantContactIds(ImmutableSet.of("jd1234"))
        .setAllowedFullyQualifiedHostNames(ImmutableSet.of("ns1.example.net", "ns2.example.net"))
        .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrise_encoded_signed_mark_response.xml", true);
    assertAboutApplications().that(getOnlyGlobalResource(DomainApplication.class))
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testFailure_emptyNameserverFailsWhitelist() throws Exception {
    setEppInput("domain_create_sunrise_encoded_signed_mark_no_hosts.xml");
    persistResource(Registry.get("tld").asBuilder()
        .setAllowedRegistrantContactIds(ImmutableSet.of("jd1234"))
        .setAllowedFullyQualifiedHostNames(ImmutableSet.of("somethingelse.example.net"))
        .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(NameserversNotSpecifiedForTldWithNameserverWhitelistException.class);
    runFlow();
  }

  @Test
  public void testSuccess_domainNameserverRestricted_allNameserversAllowed() throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "test-validate,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns3.example.net"))
            .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrise_encoded_signed_mark_response.xml", true);
    assertAboutApplications()
        .that(getOnlyGlobalResource(DomainApplication.class))
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testFailure_domainNameserverRestricted_someNameserversDisallowed() throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "test-validate,NAMESERVER_RESTRICTED,ns2.example.net:ns3.example.net"))
            .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(NameserversNotAllowedForDomainException.class, "ns1.example.net");
    runFlow();
  }

  @Test
  public void testFailure_domainNameserverRestricted_noNameserversAllowed() throws Exception {
    setEppInput("domain_create_sunrise_encoded_signed_mark_no_hosts.xml");
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "test-validate,NAMESERVER_RESTRICTED,ns2.example.net:ns3.example.net"))
            .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(NameserversNotSpecifiedForNameserverRestrictedDomainException.class);
    runFlow();
  }

  @Test
  public void testSuccess_tldAndDomainNameserversWhitelistBothSatistfied() throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setDomainCreateRestricted(true)
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "test-validate,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns3.example.net"))
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.net", "ns2.example.net", "ns4.examplet.net"))
            .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    doSuccessfulTest("domain_create_sunrise_encoded_signed_mark_response.xml", true);
    assertAboutApplications()
        .that(getOnlyGlobalResource(DomainApplication.class))
        .hasApplicationStatus(ApplicationStatus.VALIDATED);
  }

  @Test
  public void testFailure_domainNameserversDisallowed_tldNameserversAllowed() throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "test-validate,NAMESERVER_RESTRICTED,"
                        + "ns2.example.net:ns3.example.net:ns4.example.net"))
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.net", "ns2.example.net", "ns3.examplet.net"))
            .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(NameserversNotAllowedForDomainException.class, "ns1.example.net");
    runFlow();
  }

  @Test
  public void testFailure_domainNameserversAllowed_tldNameserversDisallowed() throws Exception {
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved",
                    "test-validate,NAMESERVER_RESTRICTED,"
                        + "ns1.example.net:ns2.example.net:ns3.example.net"))
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns2.example.net", "ns3.example.net", "ns4.examplet.net"))
            .build());
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(NameserversNotAllowedForTldException.class, "ns1.example.net");
    runFlow();
  }

  @Test
  public void testFailure_max10Years() throws Exception {
    createTld("tld", TldState.LANDRUSH);
    setEppInput("domain_create_landrush_11_years.xml");
    persistContactsAndHosts();
    clock.advanceOneMilli();
    thrown.expect(ExceedsMaxRegistrationYearsException.class);
    runFlow();
  }

  private void doFailingDomainNameTest(String domainName, Class<? extends Throwable> exception)
      throws Exception {
    setEppInput("domain_create_sunrise_signed_mark_uppercase.xml");
    eppLoader.replaceAll("TEST-VALIDATE.tld", domainName);
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
  public void testFailure_domainNameExistsAsTld_lowercase() throws Exception {
    createTlds("foo.tld", "tld");
    doFailingDomainNameTest("foo.tld", DomainNameExistsAsTldException.class);
  }

  @Test
  public void testFailure_domainNameExistsAsTld_uppercase() throws Exception {
    createTlds("foo.tld", "tld");
    doFailingDomainNameTest("FOO.TLD", BadDomainNameCharacterException.class);
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
  public void testIcannActivityReportField_getsLogged() throws Exception {
    persistContactsAndHosts();
    clock.advanceOneMilli();
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-dom-create");
    assertTldsFieldLogged("tld");
    // Ensure we log the client ID for srs-dom-create so we can also use it for attempted-adds.
    assertClientIdFieldLogged("TheRegistrar");
  }
}
