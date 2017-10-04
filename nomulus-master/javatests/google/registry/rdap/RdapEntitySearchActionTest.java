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
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistResources;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static google.registry.testing.FullFieldsTestEntityHelper.makeAndPersistContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.appengine.api.users.User;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.model.ImmutableObject;
import google.registry.model.contact.ContactResource;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdapEntitySearchAction}. */
@RunWith(JUnit4.class)
public class RdapEntitySearchActionTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();
  @Rule public final InjectRule inject = new InjectRule();

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2000-01-01T00:00:00Z"));
  private final SessionUtils sessionUtils = mock(SessionUtils.class);
  private final User user = new User("rdap.user@example.com", "gmail.com", "12345");
  UserAuthInfo userAuthInfo = UserAuthInfo.create(user, false);

  private final RdapEntitySearchAction action = new RdapEntitySearchAction();

  private ContactResource contact;
  private Registrar registrarDeleted;
  private Registrar registrarInactive;
  private Registrar registrarTest;

  private Object generateActualJsonWithFullName(String fn) {
    action.fnParam = Optional.of(fn);
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  private Object generateActualJsonWithHandle(String handle) {
    action.handleParam = Optional.of(handle);
    action.run();
    return JSONValue.parse(response.getPayload());
  }

  @Before
  public void setUp() throws Exception {
    inject.setStaticField(Ofy.class, "clock", clock);

    createTld("tld");

    // deleted
    registrarDeleted =
        persistResource(
            makeRegistrar("2-Registrar", "Yes Virginia <script>", Registrar.State.ACTIVE, 20L));
    persistSimpleResources(makeRegistrarContacts(registrarDeleted));

    // inactive
    registrarInactive =
        persistResource(makeRegistrar("2-RegistrarInact", "No Way", Registrar.State.PENDING, 21L));
    persistSimpleResources(makeRegistrarContacts(registrarInactive));

    // test
    registrarTest =
        persistResource(
            makeRegistrar("2-RegistrarTest", "No Way", Registrar.State.ACTIVE)
                .asBuilder()
                .setType(Registrar.Type.TEST)
                .setIanaIdentifier(null)
                .build());
    persistSimpleResources(makeRegistrarContacts(registrarTest));

    contact = makeAndPersistContactResource(
        "blinky",
        "Blinky (赤ベイ)",
        "blinky@b.tld",
        ImmutableList.of("123 Blinky St", "Blinkyland"),
        clock.nowUtc(),
        registrarTest);

    makeAndPersistContactResource(
        "blindly",
        "Blindly",
        "blindly@b.tld",
        ImmutableList.of("123 Blindly St", "Blindlyland"),
        clock.nowUtc(),
        registrarTest);

    persistResource(
        makeContactResource("clyde", "Clyde (愚図た)", "clyde@c.tld", registrarDeleted)
            .asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());

    action.clock = clock;
    action.request = request;
    action.requestPath = RdapEntitySearchAction.PATH;
    action.response = response;
    action.rdapJsonFormatter = RdapTestHelper.getTestRdapJsonFormatter();
    action.rdapResultSetMaxSize = 4;
    action.rdapLinkBase = "https://example.com/rdap/";
    action.rdapWhoisServer = null;
    action.fnParam = Optional.absent();
    action.handleParam = Optional.absent();
    action.sessionUtils = sessionUtils;
    action.authResult = AuthResult.create(AuthLevel.USER, userAuthInfo);
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(true);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("2-RegistrarTest");
  }

  private Object generateExpectedJson(String expectedOutputFile) {
    return JSONValue.parse(loadFileWithSubstitutions(
        this.getClass(),
        expectedOutputFile,
        ImmutableMap.of("TYPE", "entity")));
  }

  private Object generateExpectedJson(String name, String expectedOutputFile) {
    return generateExpectedJson(name, null, null, null, expectedOutputFile);
  }

  private Object generateExpectedJson(
      String handle,
      @Nullable String fullName,
      @Nullable String email,
      @Nullable String address,
      String expectedOutputFile) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    builder.put("NAME", handle);
    if (fullName != null) {
      builder.put("FULLNAME", fullName);
    }
    if (email != null) {
      builder.put("EMAIL", email);
    }
    if (address != null) {
      builder.put("ADDRESS", address);
    }
    builder.put("TYPE", "entity");
    return JSONValue.parse(
        loadFileWithSubstitutions(this.getClass(), expectedOutputFile, builder.build()));
  }

  private Object generateExpectedJsonForEntity(
      String handle,
      String fullName,
      @Nullable String email,
      @Nullable String address,
      String expectedOutputFile) {
    Object obj =
        generateExpectedJson(
            handle, fullName, email, address, expectedOutputFile);
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    builder.put("entitySearchResults", ImmutableList.of(obj));
    builder.put("rdapConformance", ImmutableList.of("rdap_level_0"));
    RdapTestHelper.addNotices(builder, "https://example.com/rdap/");
    RdapTestHelper.addNonDomainBoilerplateRemarks(builder);
    return builder.build();
  }

  private void createManyContactsAndRegistrars(
      int numContacts, int numRegistrars, Registrar contactRegistrar) {
    ImmutableList.Builder<ImmutableObject> resourcesBuilder = new ImmutableList.Builder<>();
    for (int i = 1; i <= numContacts; i++) {
      resourcesBuilder.add(makeContactResource(
          String.format("contact%d", i),
          String.format("Entity %d", i),
          String.format("contact%d@gmail.com", i),
          contactRegistrar));
    }
    persistResources(resourcesBuilder.build());
    for (int i = 1; i <= numRegistrars; i++) {
      resourcesBuilder.add(
          makeRegistrar(
              String.format("registrar%d", i),
              String.format("Entity %d", i + numContacts),
              Registrar.State.ACTIVE,
              300L + i));
    }
    persistResources(resourcesBuilder.build());
  }

  private void checkNumberOfEntitiesInResult(Object obj, int expected) {
    assertThat(obj).isInstanceOf(Map.class);

    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) obj;

    @SuppressWarnings("unchecked")
    List<Object> domains = (List<Object>) map.get("entitySearchResults");

    assertThat(domains).hasSize(expected);
  }

  @Test
  public void testInvalidPath_rejected() throws Exception {
    action.requestPath = RdapEntitySearchAction.PATH + "/path";
    action.run();
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testInvalidRequest_rejected() throws Exception {
    action.run();
    assertThat(JSONValue.parse(response.getPayload()))
        .isEqualTo(
            generateExpectedJson(
                "You must specify either fn=XXXX or handle=YYYY", "rdap_error_400.json"));
    assertThat(response.getStatus()).isEqualTo(400);
  }

  @Test
  public void testNameMatch_suffixRejected() throws Exception {
    assertThat(generateActualJsonWithFullName("exam*ple"))
        .isEqualTo(
            generateExpectedJson("Suffix not allowed after wildcard", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testHandleMatch_suffixRejected() throws Exception {
    assertThat(generateActualJsonWithHandle("exam*ple"))
        .isEqualTo(
            generateExpectedJson("Suffix not allowed after wildcard", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testMultipleWildcards_rejected() throws Exception {
    assertThat(generateActualJsonWithHandle("*.*"))
        .isEqualTo(generateExpectedJson("Only one wildcard allowed", "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNoCharactersToMatch_rejected() throws Exception {
    assertThat(generateActualJsonWithHandle("*"))
        .isEqualTo(
            generateExpectedJson(
                "Initial search string required in wildcard entity handle searches",
                "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testFewerThanTwoCharactersToMatch_rejected() throws Exception {
    assertThat(generateActualJsonWithHandle("a*"))
        .isEqualTo(
            generateExpectedJson(
                "Initial search string required in wildcard entity handle searches",
                "rdap_error_422.json"));
    assertThat(response.getStatus()).isEqualTo(422);
  }

  @Test
  public void testNameMatch_contactFound() throws Exception {
    assertThat(generateActualJsonWithFullName("Blinky (赤ベイ)"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                "2-ROID",
                "Blinky (赤ベイ)",
                "blinky@b.tld",
                "\"123 Blinky St\", \"Blinkyland\"",
                "rdap_contact.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_contactFound_asAdministrator() throws Exception {
    UserAuthInfo adminUserAuthInfo = UserAuthInfo.create(user, true);
    action.authResult = AuthResult.create(AuthLevel.USER, adminUserAuthInfo);
    when(sessionUtils.checkRegistrarConsoleLogin(request, adminUserAuthInfo)).thenReturn(false);
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("noregistrar");
    assertThat(generateActualJsonWithFullName("Blinky (赤ベイ)"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                "2-ROID",
                "Blinky (赤ベイ)",
                "blinky@b.tld",
                "\"123 Blinky St\", \"Blinkyland\"",
                "rdap_contact.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_contactFound_notLoggedIn() throws Exception {
    when(sessionUtils.checkRegistrarConsoleLogin(request, userAuthInfo)).thenReturn(false);
    assertThat(generateActualJsonWithFullName("Blinky (赤ベイ)"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                "2-ROID",
                "Blinky (赤ベイ)",
                "blinky@b.tld",
                "\"123 Blinky St\", \"Blinkyland\"",
                "rdap_contact_no_personal_data_with_remark.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_contactFound_loggedInAsOtherRegistrar() throws Exception {
    when(sessionUtils.getRegistrarClientId(request)).thenReturn("otherregistrar");
    assertThat(generateActualJsonWithFullName("Blinky (赤ベイ)"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                "2-ROID",
                "Blinky (赤ベイ)",
                "blinky@b.tld",
                "\"123 Blinky St\", \"Blinkyland\"",
                "rdap_contact_no_personal_data_with_remark.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_contactWildcardFound() throws Exception {
    assertThat(generateActualJsonWithFullName("Blinky*"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                "2-ROID",
                "Blinky (赤ベイ)",
                "blinky@b.tld",
                "\"123 Blinky St\", \"Blinkyland\"",
                "rdap_contact.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_contactWildcardFoundBoth() throws Exception {
    assertThat(generateActualJsonWithFullName("Blin*"))
        .isEqualTo(generateExpectedJson("rdap_multiple_contacts2.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_deletedContactNotFound() throws Exception {
    generateActualJsonWithFullName("Cl*");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameMatch_registrarFound() throws Exception {
    assertThat(generateActualJsonWithFullName("Yes Virginia <script>"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                "20", "Yes Virginia <script>", null, null, "rdap_registrar.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_nonTruncatedContacts() throws Exception {
    createManyContactsAndRegistrars(4, 0, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_nontruncated_contacts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_truncatedContacts() throws Exception {
    createManyContactsAndRegistrars(5, 0, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_contacts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_reallyTruncatedContacts() throws Exception {
    createManyContactsAndRegistrars(9, 0, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_contacts.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_nonTruncatedRegistrars() throws Exception {
    createManyContactsAndRegistrars(0, 4, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_nontruncated_registrars.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_truncatedRegistrars() throws Exception {
    createManyContactsAndRegistrars(0, 5, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_registrars.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_reallyTruncatedRegistrars() throws Exception {
    createManyContactsAndRegistrars(0, 9, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_registrars.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_truncatedMixOfContactsAndRegistrars() throws Exception {
    createManyContactsAndRegistrars(3, 3, registrarTest);
    assertThat(generateActualJsonWithFullName("Entity *"))
        .isEqualTo(generateExpectedJson("rdap_truncated_mixed_entities.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHandleMatch_2roid_found() throws Exception {
    assertThat(generateActualJsonWithHandle("2-ROID"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                "2-ROID",
                "Blinky (赤ベイ)",
                "blinky@b.tld",
                "\"123 Blinky St\", \"Blinkyland\"",
                "rdap_contact.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHandleMatch_20_found() throws Exception {
    assertThat(generateActualJsonWithHandle("20"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                "20", "Yes Virginia <script>", null, null, "rdap_registrar.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testNameMatch_2registrarInactive_notFound() throws Exception {
    generateActualJsonWithHandle("2-RegistrarInact");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameMatch_2registrarTest_notFound() throws Exception {
    generateActualJsonWithHandle("2-RegistrarTest");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testNameMatch_testAndInactiveRegistrars_notFound() throws Exception {
    generateActualJsonWithHandle("No Way");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testHandleMatch_2rstarWithResultSetSize1_foundOne() throws Exception {
    action.rdapResultSetMaxSize = 1;
    assertThat(generateActualJsonWithHandle("2-R*"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                "2-ROID",
                "Blinky (赤ベイ)",
                "blinky@b.tld",
                "\"123 Blinky St\", \"Blinkyland\"",
                "rdap_contact.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHandleMatch_2rostar_found() throws Exception {
    assertThat(generateActualJsonWithHandle("2-RO*"))
        .isEqualTo(
            generateExpectedJsonForEntity(
                contact.getRepoId(),
                "Blinky (赤ベイ)",
                "blinky@b.tld",
                "\"123 Blinky St\", \"Blinkyland\"",
                "rdap_contact.json"));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  public void testHandleMatch_20star_notFound() throws Exception {
    generateActualJsonWithHandle("20*");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testHandleMatch_3teststar_notFound() throws Exception {
    generateActualJsonWithHandle("3test*");
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  public void testHandleMatch_truncatedEntities() throws Exception {
    createManyContactsAndRegistrars(300, 0, registrarTest);
    Object obj = generateActualJsonWithHandle("10*");
    assertThat(response.getStatus()).isEqualTo(200);
    checkNumberOfEntitiesInResult(obj, 4);
  }
}
