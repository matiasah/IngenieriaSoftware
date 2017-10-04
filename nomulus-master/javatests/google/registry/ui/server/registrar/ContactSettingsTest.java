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

package google.registry.ui.server.registrar;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.testing.AppEngineRule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for contact_settings.js use of {@link RegistrarSettingsAction}.
 *
 * <p>The default read and session validation tests are handled by the
 * superclass.
 */
@RunWith(JUnit4.class)
public class ContactSettingsTest extends RegistrarSettingsActionTestCase {

  @Test
  public void testPost_readContacts_success() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "read",
        "args", ImmutableMap.of()));
    @SuppressWarnings("unchecked")
    List<Map<String, ?>> results = (List<Map<String, ?>>) response.get("results");
    assertThat(results.get(0).get("contacts"))
        .isEqualTo(loadRegistrar(CLIENT_ID).toJsonMap().get("contacts"));
  }

  @Test
  public void testPost_loadSaveRegistrar_success() throws Exception {
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", loadRegistrar(CLIENT_ID).toJsonMap()));
    assertThat(response).containsEntry("status", "SUCCESS");
  }

  @Test
  public void testPost_updateContacts_success() throws Exception {
    // Remove all the contacts but the first by updating with list of
    // just it.
    Map<String, /* @Nullable */ Object> adminContact1 = new HashMap<>();
    adminContact1.put("name", "contact1");
    adminContact1.put("emailAddress", "contact1@email.com");
    adminContact1.put("phoneNumber", "+1.2125650001");
    // Have to keep ADMIN or else expect FormException for at-least-one.
    adminContact1.put("types", "ADMIN");

    Registrar registrar = loadRegistrar(CLIENT_ID);
    Map<String, Object> regMap = registrar.toJsonMap();
    regMap.put("contacts", ImmutableList.of(adminContact1));
    Map<String, Object> response =
        action.handleJsonRequest(ImmutableMap.of("op", "update", "args", regMap));
    assertThat(response).containsEntry("status", "SUCCESS");

    RegistrarContact newContact = new RegistrarContact.Builder()
        .setParent(registrar)
        .setName((String) adminContact1.get("name"))
        .setEmailAddress((String) adminContact1.get("emailAddress"))
        .setPhoneNumber((String) adminContact1.get("phoneNumber"))
        .setTypes(ImmutableList.of(RegistrarContact.Type.ADMIN))
        .build();
    assertThat(loadRegistrar(CLIENT_ID).getContacts()).containsExactly(newContact);
  }

  @Test
  public void testPost_updateContacts_requiredTypes_error() throws Exception {
    Map<String, Object> reqJson = loadRegistrar(CLIENT_ID).toJsonMap();
    reqJson.put("contacts",
        ImmutableList.of(AppEngineRule.makeRegistrarContact2()
            .asBuilder()
            .setTypes(ImmutableList.<RegistrarContact.Type>of())
            .build().toJsonMap()));
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", reqJson));
    assertThat(response).containsEntry("status", "ERROR");
    assertThat(response).containsEntry("message", "Must have at least one "
        + RegistrarContact.Type.ADMIN.getDisplayName() + " contact");
  }

  @Test
  public void testPost_updateContacts_requireTechPhone_error() throws Exception {
    // First make the contact a tech contact as well.
    Registrar registrar = loadRegistrar(CLIENT_ID);
    RegistrarContact rc = AppEngineRule.makeRegistrarContact2()
        .asBuilder()
        .setTypes(ImmutableSet.of(RegistrarContact.Type.ADMIN, RegistrarContact.Type.TECH))
        .build();
    // Lest we anger the timestamp inversion bug.
    persistResource(registrar);
    persistSimpleResource(rc);

    // Now try to remove the phone number.
    rc = rc.asBuilder().setPhoneNumber(null).build();
    Map<String, Object> reqJson = registrar.toJsonMap();
    reqJson.put("contacts", ImmutableList.of(rc.toJsonMap()));
    Map<String, Object> response = action.handleJsonRequest(ImmutableMap.of(
        "op", "update",
        "args", reqJson));
    assertThat(response).containsEntry("status", "ERROR");
    assertThat(response).containsEntry("message", "Please provide a phone number for at least one "
        + RegistrarContact.Type.TECH.getDisplayName() + " contact");
  }

  @Test
  public void testPost_updateContacts_cannotRemoveWhoisAbuseContact_error() throws Exception {
    // First make the contact's info visible in whois as abuse contact info.
    Registrar registrar = loadRegistrar(CLIENT_ID);
    RegistrarContact rc =
        AppEngineRule.makeRegistrarContact2()
            .asBuilder()
            .setVisibleInDomainWhoisAsAbuse(true)
            .build();
    // Lest we anger the timestamp inversion bug.
    persistResource(registrar);
    persistSimpleResource(rc);

    // Now try to remove the contact.
    rc = rc.asBuilder().setVisibleInDomainWhoisAsAbuse(false).build();
    Map<String, Object> reqJson = registrar.toJsonMap();
    reqJson.put("contacts", ImmutableList.of(rc.toJsonMap()));
    Map<String, Object> response =
        action.handleJsonRequest(ImmutableMap.of("op", "update", "args", reqJson));
    assertThat(response).containsEntry("status", "ERROR");
    assertThat(response)
        .containsEntry(
            "message", "An abuse contact visible in domain WHOIS query must be designated");
  }

  @Test
  public void testPost_updateContacts_whoisAbuseContactMustHavePhoneNumber_error()
      throws Exception {
    // First make the contact's info visible in whois as abuse contact info.
    Registrar registrar = loadRegistrar(CLIENT_ID);
    RegistrarContact rc =
        AppEngineRule.makeRegistrarContact2()
            .asBuilder()
            .setVisibleInDomainWhoisAsAbuse(true)
            .build();
    // Lest we anger the timestamp inversion bug.
    persistResource(registrar);
    persistSimpleResource(rc);

    // Now try to set the phone number to null.
    rc = rc.asBuilder().setPhoneNumber(null).build();
    Map<String, Object> reqJson = registrar.toJsonMap();
    reqJson.put("contacts", ImmutableList.of(rc.toJsonMap()));
    Map<String, Object> response =
        action.handleJsonRequest(ImmutableMap.of("op", "update", "args", reqJson));
    assertThat(response).containsEntry("status", "ERROR");
    assertThat(response)
        .containsEntry(
            "message", "The abuse contact visible in domain WHOIS query must have a phone number");
  }
}
