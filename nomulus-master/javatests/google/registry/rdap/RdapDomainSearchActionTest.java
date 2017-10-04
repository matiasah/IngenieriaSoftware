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

package google.registry.rdap;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistDomainAsDeleted;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistResources;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistHostResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeDomainResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeHistoryEntry;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.net.InetAddresses;
import com.googlecode.objectify.Key;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.Period;
import google.registry.model.host.HostResource;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.HistoryEntry;
import google.registry.request.auth.AuthLevel;
import google.registry.request.auth.AuthResult;
import google.registry.request.auth.UserAuthInfo;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.ui.server.registrar.SessionUtils;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.joda.time.DateTime;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapDomainSearchAction}. */
@RunWith(JUnit4.class)
public class RdapDomainSearchActionTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final InjectRule inject = new InjectRule();

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01T00:00:00Z"));
  private final SessionUtils sessionUtils = mock(SessionUtils.class);
  private final User user = new User("rdap.user@example.com", "gmail.com", "12345");
  UserAuthInfo userAuthInfo = UserAuthInfo.create(user, false);

  private final RdapDomainSearchAction action = new RdapDomainSearchAction();

  private Registrar registrar;
  private DomainResource domainCatLol;
  private DomainResource domainCatLol2;
  private DomainResource domainCatExample;
  private ContactResource contact1;
  private ContactResource contact2;
  private ContactResource contact3;
  private HostResource hostNs1CatLol;
  private HostResource hostNs2CatLol;

  enum RequestType { NONE, NAME, NS_LDH_NAME, NS_IP }

  private Object generateActualJson(RequestType requestType, String paramValue) {
    action.requestPath = RdapDomainSearchAction.PATH;
    switch (requestType) {
      case NAME:
        action.nameParam = Optional.of(paramValue);
        action.nsLdhNameParam = Optional.absent();
        action.nsIpParam = Optional.absent();
        break;
      case NS_LDH_NAME:
        action.nameParam = Optional.absent();
        action.nsLdhNameParam = Optional.of(paramValue);
        action.nsIpParam = Optional.absent();
        break;
      case NS_IP:
        action.nameParam = Optional.absent();
        action.nsLdhNameParam = Optional.absent();
        action.nsIpParam = Optional.of(InetAddresses.forString(paramValue));
        break;
      default:
        action.nameParam = Optional.absent();
        action.nsLdhNameParam = Optional.absent();
        action.nsIpParam = Optional.absent();
        break;
    }
    action.rdapResultSetMaxSize = 4;
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);

    // cat.lol and cat2.lol
    createTld("lol");
    registrar = persistResource(
        makeRegistrar("evilregistrar", "Yes Virginia <script>", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrar));
    domainCatLol = persistResource(
        makeDomainResource(
            "cat.lol",
            contact1 = makeAndPersistContactResource(
                "5372808-ERL",
                "Goblin Market",
                "lol@cat.lol",
                clock.nowUtc().minusYears(1),
                registrar),
            contact2 = makeAndPersistContactResource(
                "5372808-IRL",
                "Santa Claus",
                "BOFH@cat.lol",
                clock.nowUtc().minusYears(2),
                registrar),
            contact3 = makeAndPersistContactResource(
                "5372808-TRL",
                "The Raven",
                "bog@cat.lol",
                clock.nowUtc().minusYears(3),
                registrar),
            hostNs1CatLol = makeAndPersistHostResource(
                "ns1.cat.lol",
                "1.2.3.4",
                clock.nowUtc().minusYears(1)),
            hostNs2CatLol = makeAndPersistHostResource(
                "ns2.cat.lol",
                "bad:f00d:cafe::15:beef",
                clock.nowUtc().minusYears(2)),
            registrar)
        .asBuilder()
        .setSubordinateHosts(ImmutableSet.of("ns1.cat.lol", "ns2.cat.lol"))
        .setCreationTimeForTest(clock.nowUtc().minusYears(3))
        .build());
    persistResource(
        hostNs1CatLol.asBuilder().setSuperordinateDomain(Key.create(domainCatLol)).build());
    persistResource(
        hostNs2CatLol.asBuilder().setSuperordinateDomain(Key.create(domainCatLol)).build());
    domainCatLol2 = persistResource(
        makeDomainResource(
            "cat2.lol",
            makeAndPersistContactResource(
                "6372808-ERL",
                "Siegmund",
                "siegmund@cat2.lol",
                clock.nowUtc().minusYears(1),
                registrar),
            makeAndPersistContactResource(
                "6372808-IRL",
                "Sieglinde",
                "sieglinde@cat2.lol",
                clock.nowUtc().minusYears(2),
                registrar),
            makeAndPersistContactResource(
                "6372808-TRL",
                "Siegfried",
                "siegfried@cat2.lol",
                clock.nowUtc().minusYears(3),
                registrar),
            makeAndPersistHostResource(
                "ns1.cat.example", "10.20.30.40", clock.nowUtc().minusYears(1)),
            makeAndPersistHostResource(
                "ns2.dog.lol", "12:feed:5000::15:beef", clock.nowUtc().minusYears(2)),
            registrar)
        .asBuilder()
        .setCreationTimeForTest(clock.nowUtc().minusYears(3))
        .build());
    // cat.example
    createTld("example");
    registrar = persistResource(
        makeRegistrar("goodregistrar", "St. John Chrysostom", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrar));
    domainCatExample = persistResource(
        makeDomainResource(
            "cat.example",
            makeAndPersistContactResource(
                "7372808-ERL",
                "Matthew",
                "lol@cat.lol",
                clock.nowUtc().minusYears(1),
                registrar),
            makeAndPersistContactResource(
                "7372808-IRL",
                "Mark",
                "BOFH@cat.lol",
                clock.nowUtc().minusYears(2),
                registrar),
            makeAndPersistContactResource(
                "7372808-TRL",
                "Luke",
                "bog@cat.lol",
                clock.nowUtc().minusYears(3),
                registrar),
            hostNs1CatLol,
            makeAndPersistHostResource(
                "ns2.external.tld", "bad:f00d:cafe::15:beef", clock.nowUtc().minusYears(2)),
            registrar)
        .asBuilder()
        .setCreationTimeForTest(clock.nowUtc().minusYears(3))
        .build());
    // cat.みんな
    createTld("xn--q9jyb4c");
    registrar = persistResource(makeRegistrar("unicoderegistrar", "みんな", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrar));
    persistResource(
        makeDomainResource(
            "cat.みんな",
            makeAndPersistContactResource(
                "8372808-ERL",
                "(◕‿◕)",
                "lol@cat.みんな",
                clock.nowUtc().minusYears(1),
                registrar),
            makeAndPersistContactResource(
                "8372808-IRL",
                "Santa Claus",
                "BOFH@cat.みんな",
                clock.nowUtc().minusYears(2),
                registrar),
            makeAndPersistContactResource(
                "8372808-TRL",
                "The Raven",
                "bog@cat.みんな",
                clock.nowUtc().minusYears(3),
                registrar),
            makeAndPersistHostResource("ns1.cat.みんな", "1.2.3.5", clock.nowUtc().minusYears(1)),
            makeAndPersistHostResource(
                "ns2.cat.みんな", "bad:f00d:cafe::14:beef", clock.nowUtc().minusYears(2)),
            registrar)
        .asBuilder()
        .setCreationTimeForTest(clock.nowUtc().minusYears(3))
        .build());
    // cat.1.test
    createTld("1.test");
    registrar =
        persistResource(makeRegistrar("unicoderegistrar", "1.test", Registrar.State.ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrar));
    persistResource(makeDomainResource(
            "cat.1.test",
            makeAndPersistContactResource(
                "9372808-ERL",
                "(◕‿◕)",
                "lol@cat.みんな",
                clock.nowUtc().minusYears(1),
                registrar),
            makeAndPersistContactResource(
                "9372808-IRL",
                "Santa Claus",
                "BOFH@cat.みんな",
                clock.nowUtc().minusYears(2),
                registrar),
            makeAndPersistContactResource(
                "9372808-TRL",
                "The Raven",
                "bog@cat.みんな",
                clock.nowUtc().minusYears(3),
                registrar),
            makeAndPersistHostResource("ns1.cat.1.test", "1.2.3.5", clock.nowUtc().minusYears(1)),
            makeAndPersistHostResource(
                "ns2.cat.2.test", "bad:f00d:cafe::14:beef", clock.nowUtc().minusYears(2)),
            registrar)
        .asBuilder()
        .setSubordinateHosts(ImmutableSet.of("ns1.cat.1.test"))
        .setCreationTimeForTest(clock.nowUtc().minusYears(3))
        .build());

    // history entries
    persistResource(
        makeHistoryEntry(
            domainCatLol,
            HistoryEntry.Type.DOMAIN_CREATE,
            Period.create(1, Period.Unit.YEARS),
            "created",
            clock.nowUtc()));
    persistResource(
        makeHistoryEntry(
            domainCatLol2,
            HistoryEntry.Type.DOMAIN_CREATE,
            Period.create(1, Period.Unit.YEARS),
            "created",
            clock.nowUtc()));
    persistResource(
        makeHistoryEntry(
            domainCatExample,
            HistoryEntry.Type.DOMAIN_CREATE,
            Period.create(1, Period.Unit.YEARS),
            "created",
            clock.nowUtc()));

    action.clock = clock;
    action.request = request;
    action.response = response;
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapLinkBase = "https://example.com/rdap/";
    action.rdapWhoisServer = null;
    action.sessionUtils = sessionUtils;
    action.authResult = AuthResult.create(AuthLevel.USER, userAuthInfo);
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("evilregistrar");
  }

  private Object generateExpectedJson(String expectedOutputFile) {
    return JSONValue.parse(loadFileWithSubstitutions(
        this.getClass(),
        expectedOutputFile,
        ImmutableMap.of("TYPE", "domain name")));
  }

  private Object generateExpectedJson(String name, String expectedOutputFile) {
    return generateExpectedJson(name, null, null, null, expectedOutputFile);
  }

  private Object generateExpectedJson(
      String name,
      String punycodeName,
      String handle,
      @Nullable List<String> contactRoids,
      String expectedOutputFile) {
    ImmutableMap.Builder<String, String> substitutionsBuilder = new ImmutableMap.Builder<>();
    substitutionsBuilder.put("NAME", name);
    substitutionsBuilder.put("PUNYCODENAME", (punycodeName == null) ? name : punycodeName);
    substitutionsBuilder.put("HANDLE", (handle == null) ? "(null)" : handle);
    substitutionsBuilder.put("TYPE", "domain name");
    if (contactRoids != null) {
      for (int i = 0; i < contactRoids.size(); i++) {
        substitutionsBuilder.put("CONTACT" + (i + 1) + "ROID", contactRoids.get(i));
      }
    }
    return JSONValue.parse(
        loadFileWithSubstitutions(
            this.getClass(), expectedOutputFile, substitutionsBuilder.build()));
  }

  private Object generateExpectedJsonForDomain(
      String name,
      String punycodeName,
      String handle,
      @Nullable List<String> contactRoids,
      String expectedOutputFile) {
    Object obj = generateExpectedJson(name, punycodeName, handle, contactRoids, expectedOutputFile);
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("domainSearchResults", ImmutableList.of(obj));
    builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
    RdapTestHelper.addNotices(builder, "https://example.com/rdap/");
    RdapTestHelper.addDomainBoilerplateRemarks(builder);
    return builder.build();
  }

  @Test
  public void testInvalidPath_rejected() throws Exception {
    action.requestPath = RdapDomainSearchAction.PATH + "/path";
    action.run();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testInvalidRequest_rejected() throws Exception {
    assertThat(generateActualJson(RequestType.NONE, null))
        .isEqualTo(generateExpectedJson(
            "You must specify either name=XXXX, nsLdhName=YYYY or nsIp=ZZZZ",
            "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testInvalidWildcard_rejected() throws Exception {
    assertThat(generateActualJson(RequestType.NAME, "exam*ple"))
        .isEqualTo(generateExpectedJson(
            "Suffix after wildcard must be one or more domain"
                + " name labels, e.g. exam*.tld, ns*.example.tld",
            "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testMultipleWildcards_rejected() throws Exception {
    assertThat(generateActualJson(RequestType.NAME, "*.*"))
        .isEqualTo(generateExpectedJson("Only one wildcard allowed", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNoCharactersToMatch_rejected() throws Exception {
    assertThat(generateActualJson(RequestType.NAME, "*"))
        .isEqualTo(
            generateExpectedJson(
                "Initial search string is required for wildcard domain searches without a TLD"
                    + " suffix",
                "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testFewerThanTwoCharactersToMatch_rejected() throws Exception {
    assertThat(generateActualJson(RequestType.NAME, "a*"))
        .isEqualTo(
            generateExpectedJson(
                "Initial search string must be at least 2 characters for wildcard domain searches"
                    + " without a TLD suffix",
                "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testDomainMatch_found() throws Exception {
    assertThat(generateActualJson(RequestType.NAME, "cat.lol"))
        .isEqualTo(
            generateExpectedJsonForDomain(
                "cat.lol",
                null,
                "C-LOL",
                ImmutableList.of("4-ROID", "6-ROID", "2-ROID"),
                "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_found_asAdministrator() throws Exception {
    UserAuthInfo adminUserAuthInfo = UserAuthInfo.create(user, true);
    action.authResult = AuthResult.create(AuthLevel.USER, adminUserAuthInfo);
    when(sessionUtils.checkRegistrarConsoleLogin(request, adminUserAuthInfo)).thenReturn(false);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("noregistrar");
    assertThat(generateActualJson(RequestType.NAME, "cat.lol"))
        .isEqualTo(
            generateExpectedJsonForDomain(
                "cat.lol",
                null,
                "C-LOL",
                ImmutableList.of("4-ROID", "6-ROID", "2-ROID"),
                "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_found_loggedInAsOtherRegistrar() throws Exception {
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("otherregistrar");
    assertThat(generateActualJson(RequestType.NAME, "cat.lol"))
        .isEqualTo(
            generateExpectedJsonForDomain(
                "cat.lol",
                null,
                "C-LOL",
                null,
                "rdap_domain_no_contacts_with_remark.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  /*
   * This test is flaky because IDN.toASCII may or may not remove the trailing dot of its own
   * accord. If it does, the test will pass.
   */
  @Ignore
  @Test
  public void testDomainMatchWithTrailingDot_notFound() throws Exception {
    generateActualJson(RequestType.NAME, "cat.lol.");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatch_cat2_lol_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat2.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_example_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.example");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_idn_unicode_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.みんな");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_idn_punycode_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.xn--q9jyb4c");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_1_test_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.1.test");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_castar_1_test_found() throws Exception {
    generateActualJson(RequestType.NAME, "ca*.1.test");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_castar_test_notFound() throws Exception {
    generateActualJson(RequestType.NAME, "ca*.test");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatch_catstar_lol_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat*.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cstar_lol_found() throws Exception {
    generateActualJson(RequestType.NAME, "c*.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_star_lol_found() throws Exception {
    generateActualJson(RequestType.NAME, "*.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_star_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.*");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_cat_lstar_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat.l*");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_catstar_found() throws Exception {
    generateActualJson(RequestType.NAME, "cat*");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatchWithWildcardAndEmptySuffix_fails() throws Exception {
    // Unfortunately, we can't be sure which error is going to be returned. The version of
    // IDN.toASCII used in Eclipse drops a trailing dot, if any. But the version linked in by
    // Blaze throws an error in that situation. So just check that it returns an error.
    generateActualJson(RequestType.NAME, "exam*..");
    assertThat(response.getStatus()).isIn(Range.closed(400, 499));
  }

  @Test
  public void testDomainMatch_dog_notFound() throws Exception {
    generateActualJson(RequestType.NAME, "dog*");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatchDeletedDomain_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc().minusDays(1));
    assertThat(generateActualJson(RequestType.NAME, "cat.lol"))
        .isEqualTo(generateExpectedJson("No domains found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatchDeletedDomainWithWildcard_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc().minusDays(1));
    assertThat(generateActualJson(RequestType.NAME, "cat.lo*"))
        .isEqualTo(generateExpectedJson("No domains found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testDomainMatchDeletedDomainsWithWildcardAndTld_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc().minusDays(1));
    persistDomainAsDeleted(domainCatLol2, clock.nowUtc().minusDays(1));
    generateActualJson(RequestType.NAME, "cat*.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  // TODO(b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testDomainMatchDomainInTestTld_notFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    generateActualJson(RequestType.NAME, "cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  private void createManyDomainsAndHosts(
      int numActiveDomains, int numTotalDomainsPerActiveDomain, int numHosts) {
    ImmutableSet.Builder<Key<HostResource>> hostKeysBuilder = new ImmutableSet.Builder<>();
    ImmutableSet.Builder<String> subordinateHostsBuilder = new ImmutableSet.Builder<>();
    String mainDomainName = String.format("domain%d.lol", numTotalDomainsPerActiveDomain);
    for (int i = 1; i <= numHosts; i++) {
      String hostName = String.format("ns%d.%s", i, mainDomainName);
      subordinateHostsBuilder.add(hostName);
      HostResource host = makeAndPersistHostResource(
          hostName, String.format("5.5.%d.%d", 5 + i / 250, i % 250), clock.nowUtc().minusYears(1));
      hostKeysBuilder.add(Key.create(host));
    }
    ImmutableSet<Key<HostResource>> hostKeys = hostKeysBuilder.build();
    // Create all the domains at once, then persist them in parallel, for increased efficiency.
    ImmutableList.Builder<DomainResource> domainsBuilder = new ImmutableList.Builder<>();
    for (int i = 1; i <= numActiveDomains * numTotalDomainsPerActiveDomain; i++) {
      String domainName = String.format("domain%d.lol", i);
      DomainResource.Builder builder =
          makeDomainResource(
              domainName, contact1, contact2, contact3, null, null, registrar)
          .asBuilder()
          .setNameservers(hostKeys)
          .setCreationTimeForTest(clock.nowUtc().minusYears(3));
      if (domainName.equals(mainDomainName)) {
        builder.setSubordinateHosts(subordinateHostsBuilder.build());
      }
      if (i % numTotalDomainsPerActiveDomain != 0) {
        builder = builder.setDeletionTime(clock.nowUtc().minusDays(1));
      }
      domainsBuilder.add(builder.build());
    }
    persistResources(domainsBuilder.build());
  }

  private Object readMultiDomainFile(
      String fileName,
      String domainName1,
      String domainHandle1,
      String domainName2,
      String domainHandle2,
      String domainName3,
      String domainHandle3,
      String domainName4,
      String domainHandle4) {
    return JSONValue.parse(loadFileWithSubstitutions(
        this.getClass(),
        fileName,
        new ImmutableMap.Builder<String, String>()
            .put("DOMAINNAME1", domainName1)
            .put("DOMAINHANDLE1", domainHandle1)
            .put("DOMAINNAME2", domainName2)
            .put("DOMAINHANDLE2", domainHandle2)
            .put("DOMAINNAME3", domainName3)
            .put("DOMAINHANDLE3", domainHandle3)
            .put("DOMAINNAME4", domainName4)
            .put("DOMAINHANDLE4", domainHandle4)
            .build()));
  }

  private void checkNumberOfDomainsInResult(Object obj, int expected) {
    assertThat(obj).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) obj;

    @SuppressWarnings("unchecked")
    List<Object> domains = (List<Object>) map.get("domainSearchResults");

    assertThat(domains).hasSize(expected);
  }

  @Test
  public void testDomainMatch_manyDeletedDomains_fullResultSet() throws Exception {
    // There are enough domains to fill a full result set; deleted domains are ignored.
    createManyDomainsAndHosts(4, 4, 2);
    Object obj = generateActualJson(RequestType.NAME, "domain*.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    checkNumberOfDomainsInResult(obj, 4);
  }

  @Test
  public void testDomainMatch_manyDeletedDomains_partialResultSetDueToInsufficientDomains()
      throws Exception {
    // There are not enough domains to fill a full result set.
    createManyDomainsAndHosts(3, 20, 2);
    Object obj = generateActualJson(RequestType.NAME, "domain*.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    checkNumberOfDomainsInResult(obj, 3);
  }

  @Test
  public void testDomainMatch_manyDeletedDomains_partialResultSetDueToFetchingLimit()
      throws Exception {
    // This is not exactly desired behavior, but expected: There are enough domains to fill a full
    // result set, but there are so many deleted domains that we run out of patience before we work
    // our way through all of them.
    createManyDomainsAndHosts(4, 50, 2);
    Object obj = generateActualJson(RequestType.NAME, "domain*.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    checkNumberOfDomainsInResult(obj, 3);
  }

  @Test
  public void testDomainMatch_nontruncatedResultsSet() throws Exception {
    createManyDomainsAndHosts(4, 1, 2);
    assertThat(generateActualJson(RequestType.NAME, "domain*.lol"))
        .isEqualTo(readMultiDomainFile(
            "rdap_nontruncated_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_truncatedResultsSet() throws Exception {
    createManyDomainsAndHosts(5, 1, 2);
    assertThat(generateActualJson(RequestType.NAME, "domain*.lol"))
        .isEqualTo(readMultiDomainFile(
            "rdap_truncated_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_reallyTruncatedResultsSet() throws Exception {
    // Don't use 10 or more domains for this test, because domain10.lol will come before
    // domain2.lol, and you'll get the wrong domains in the result set.
    createManyDomainsAndHosts(9, 1, 2);
    assertThat(generateActualJson(RequestType.NAME, "domain*.lol"))
        .isEqualTo(readMultiDomainFile(
            "rdap_truncated_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testDomainMatch_truncatedResultsAfterMultipleChunks() throws Exception {
    createManyDomainsAndHosts(5, 6, 2);
    assertThat(generateActualJson(RequestType.NAME, "domain*.lol"))
        .isEqualTo(readMultiDomainFile(
            "rdap_truncated_domains.json",
            "domain12.lol",
            "4C-LOL",
            "domain18.lol",
            "52-LOL",
            "domain24.lol",
            "58-LOL",
            "domain30.lol",
            "5E-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_foundMultiple() throws Exception {
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.lol"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchWithWildcard_found() throws Exception {
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns2.cat.l*"))
        .isEqualTo(
            generateExpectedJsonForDomain(
                "cat.lol",
                null,
                "C-LOL",
                ImmutableList.of("4-ROID", "6-ROID", "2-ROID"),
                "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchWithWildcardAndDomainSuffix_notFound() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns5*.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchWithNoPrefixWildcardAndDomainSuffix_found() throws Exception {
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "*.cat.lol"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchWithOneCharacterPrefixWildcardAndDomainSuffix_found()
      throws Exception {
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "n*.cat.lol"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchWithTwoCharacterPrefixWildcardAndDomainSuffix_found()
      throws Exception {
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns*.cat.lol"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchWithWildcardAndEmptySuffix_unprocessable() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns*.");
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNameserverMatchWithWildcardAndInvalidSuffix_unprocessable() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns*.google.com");
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNameserverMatch_ns2_cat_lol_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns2.cat.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_ns2_dog_lol_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns2.dog.lol");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_ns1_cat_idn_unicode_badRequest() throws Exception {
    // nsLdhName must use punycode.
    generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.みんな");
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testNameserverMatch_ns1_cat_idn_punycode_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.xn--q9jyb4c");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_ns1_cat_1_test_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.1.test");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_nsstar_cat_1_test_found() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns*.cat.1.test");
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_nsstar_test_unprocessable() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns*.1.test");
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNameserverMatchMissing_notFound() throws Exception {
    generateActualJson(RequestType.NS_LDH_NAME, "ns1.missing.com");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  // todo (b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testNameserverMatchDomainsInTestTld_notFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    generateActualJson(RequestType.NS_LDH_NAME, "ns2.cat.lol");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchOneDeletedDomain_foundTheOther() throws Exception {
    persistDomainAsDeleted(domainCatExample, clock.nowUtc().minusDays(1));
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.lol"))
        .isEqualTo(
            generateExpectedJsonForDomain(
                "cat.lol",
                null,
                "C-LOL",
                ImmutableList.of("4-ROID", "6-ROID", "2-ROID"),
                "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatchTwoDeletedDomains_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc().minusDays(1));
    persistDomainAsDeleted(domainCatExample, clock.nowUtc().minusDays(1));
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.lol"))
        .isEqualTo(generateExpectedJson("No domains found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchDeletedNameserver_notFound() throws Exception {
    persistResource(
        hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.lol"))
        .isEqualTo(generateExpectedJson("No matching nameservers found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchDeletedNameserverWithWildcard_notFound() throws Exception {
    persistResource(
        hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.cat.l*"))
        .isEqualTo(generateExpectedJson("No matching nameservers found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchDeletedNameserverWithWildcardAndSuffix_notFound()
      throws Exception {
    persistResource(
        hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1*.cat.lol"))
        .isEqualTo(generateExpectedJson("No matching nameservers found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameserverMatchManyNameserversForTheSameDomains() throws Exception {
    // 40 nameservers for each of 3 domains; we should get back all three undeleted domains, because
    // each one references the nameserver.
    createManyDomainsAndHosts(3, 1, 40);
    Object obj = generateActualJson(RequestType.NS_LDH_NAME, "ns1.domain1.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    checkNumberOfDomainsInResult(obj, 3);
  }

  @Test
  public void testNameserverMatchManyNameserversForTheSameDomainsWithWildcard() throws Exception {
    // Same as above, except with a wildcard (that still only finds one nameserver).
    createManyDomainsAndHosts(3, 1, 40);
    Object obj = generateActualJson(RequestType.NS_LDH_NAME, "ns1.domain1.l*");
    assertThat(response.getStatus()).isEqualTo(200);
    checkNumberOfDomainsInResult(obj, 3);
  }

  @Test
  public void testNameserverMatchManyNameserversForTheSameDomainsWithSuffix() throws Exception {
    // Same as above, except that we find all 40 nameservers because of the wildcard. But we
    // should still only return 3 domains, because we merge duplicate domains together in a set.
    // Since we fetch domains by nameserver in batches of 30 nameservers, we need to make sure to
    // have more than that number of nameservers for an effective test.
    createManyDomainsAndHosts(3, 1, 40);
    Object obj = generateActualJson(RequestType.NS_LDH_NAME, "ns*.domain1.lol");
    assertThat(response.getStatus()).isEqualTo(200);
    checkNumberOfDomainsInResult(obj, 3);
  }

  @Test
  public void testNameserverMatch_nontruncatedResultsSet() throws Exception {
    createManyDomainsAndHosts(4, 1, 2);
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.domain1.lol"))
        .isEqualTo(readMultiDomainFile(
            "rdap_nontruncated_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_truncatedResultsSet() throws Exception {
    createManyDomainsAndHosts(5, 1, 2);
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.domain1.lol"))
        .isEqualTo(readMultiDomainFile(
            "rdap_truncated_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_reallyTruncatedResultsSet() throws Exception {
    createManyDomainsAndHosts(9, 1, 2);
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns1.domain1.lol"))
        .isEqualTo(readMultiDomainFile(
            "rdap_truncated_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_duplicatesNotTruncated() throws Exception {
    // 60 nameservers for each of 4 domains; these should translate into 2 30-nameserver domain
    // fetches, which should _not_ trigger the truncation warning because all the domains will be
    // duplicates.
    createManyDomainsAndHosts(4, 1, 60);
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns*.domain1.lol"))
        .isEqualTo(readMultiDomainFile(
            "rdap_nontruncated_domains.json",
            "domain1.lol",
            "B5-LOL",
            "domain2.lol",
            "B6-LOL",
            "domain3.lol",
            "B7-LOL",
            "domain4.lol",
            "B8-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameserverMatch_incompleteResultsSet() throws Exception {
    createManyDomainsAndHosts(2, 1, 2500);
    assertThat(generateActualJson(RequestType.NS_LDH_NAME, "ns*.domain1.lol"))
        .isEqualTo(readMultiDomainFile(
            "rdap_incomplete_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchV4Address_foundMultiple() throws Exception {
    assertThat(generateActualJson(RequestType.NS_IP, "1.2.3.4"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchV6Address_foundMultiple() throws Exception {
    assertThat(generateActualJson(RequestType.NS_IP, "bad:f00d:cafe::15:beef"))
        .isEqualTo(generateExpectedJson("rdap_multiple_domains.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchLocalhost_notFound() throws Exception {
    generateActualJson(RequestType.NS_IP, "127.0.0.1");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  // todo (b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testAddressMatchDomainsInTestTld_notFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    persistResource(Registry.get("example").asBuilder().setTldType(Registry.TldType.TEST).build());
    generateActualJson(RequestType.NS_IP, "1.2.3.4");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testAddressMatchOneDeletedDomain_foundTheOther() throws Exception {
    persistDomainAsDeleted(domainCatExample, clock.nowUtc().minusDays(1));
    assertThat(generateActualJson(RequestType.NS_IP, "1.2.3.4"))
        .isEqualTo(
            generateExpectedJsonForDomain(
                "cat.lol",
                null,
                "C-LOL",
                ImmutableList.of("4-ROID", "6-ROID", "2-ROID"),
                "rdap_domain.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatchTwoDeletedDomains_notFound() throws Exception {
    persistDomainAsDeleted(domainCatLol, clock.nowUtc().minusDays(1));
    persistDomainAsDeleted(domainCatExample, clock.nowUtc().minusDays(1));
    assertThat(generateActualJson(RequestType.NS_IP, "1.2.3.4"))
        .isEqualTo(generateExpectedJson("No domains found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testAddressMatchDeletedNameserver_notFound() throws Exception {
    persistResource(hostNs1CatLol.asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    assertThat(generateActualJson(RequestType.NS_IP, "1.2.3.4"))
        .isEqualTo(generateExpectedJson("No domains found", "rdap_error_404.json"));
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testAddressMatch_nontruncatedResultsSet() throws Exception {
    createManyDomainsAndHosts(4, 1, 2);
    assertThat(generateActualJson(RequestType.NS_IP, "5.5.5.1"))
        .isEqualTo(readMultiDomainFile(
            "rdap_nontruncated_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatch_truncatedResultsSet() throws Exception {
    createManyDomainsAndHosts(5, 1, 2);
    assertThat(generateActualJson(RequestType.NS_IP, "5.5.5.1"))
        .isEqualTo(readMultiDomainFile(
            "rdap_truncated_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testAddressMatch_reallyTruncatedResultsSet() throws Exception {
    createManyDomainsAndHosts(9, 1, 2);
    assertThat(generateActualJson(RequestType.NS_IP, "5.5.5.1"))
        .isEqualTo(readMultiDomainFile(
            "rdap_truncated_domains.json",
            "domain1.lol",
            "41-LOL",
            "domain2.lol",
            "42-LOL",
            "domain3.lol",
            "43-LOL",
            "domain4.lol",
            "44-LOL"));
    assertThat(response.getStatus()).isEqualTo(200);
  }
}
