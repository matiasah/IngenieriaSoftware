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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.registrar.RegistrarContact.Type.ABUSE;
import static google.registry.model.registrar.RegistrarContact.Type.ADMIN;
import static google.registry.model.registrar.RegistrarContact.Type.TECH;
import static google.registry.model.registrar.RegistrarContact.Type.WHOIS;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link RegistrarContactCommand}. */
public class RegistrarContactCommandTest extends CommandTestCase<RegistrarContactCommand> {

  private String output;

  @Before
  public void before() throws Exception {
    output = tmpDir.newFile().toString();
  }

  @Test
  public void testList() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    RegistrarContact.updateContacts(
        registrar,
        ImmutableSet.of(
            new RegistrarContact.Builder()
                .setParent(registrar)
                .setName("John Doe")
                .setEmailAddress("john.doe@example.com")
                .setTypes(ImmutableSet.of(ADMIN))
                .setVisibleInWhoisAsAdmin(true)
                .build()));
    runCommandForced("--mode=LIST", "--output=" + output, "NewRegistrar");
    assertThat(Files.readAllLines(Paths.get(output), UTF_8))
        .containsExactly(
            "John Doe",
            "john.doe@example.com",
            "Types: [ADMIN]",
            "Visible in registrar WHOIS query as Admin contact: Yes",
            "Visible in registrar WHOIS query as Technical contact: No",
            "Phone number and email visible in domain WHOIS query as "
                + "Registrar Abuse contact info: No");
  }

  @Test
  public void testUpdate() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    ImmutableList<RegistrarContact> contacts = ImmutableList.of(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Judith Doe")
            .setEmailAddress("judith.doe@example.com")
            .setTypes(ImmutableSet.of(WHOIS))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(true)
            .setVisibleInDomainWhoisAsAbuse(false)
            .build());
    persistSimpleResources(contacts);
    runCommandForced(
        "--mode=UPDATE",
        "--name=Judith Registrar",
        "--email=judith.doe@example.com",
        "--phone=+1.2125650000",
        "--fax=+1.2125650001",
        "--contact_type=WHOIS",
        "--visible_in_whois_as_admin=true",
        "--visible_in_whois_as_tech=false",
        "--visible_in_domain_whois_as_abuse=false",
        "NewRegistrar");
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact).isEqualTo(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Judith Registrar")
            .setEmailAddress("judith.doe@example.com")
            .setPhoneNumber("+1.2125650000")
            .setFaxNumber("+1.2125650001")
            .setTypes(ImmutableSet.of(WHOIS))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(false)
            .setVisibleInDomainWhoisAsAbuse(false)
            .build());
  }

  @Test
  public void testUpdate_enableConsoleAccess() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Jane Doe")
            .setEmailAddress("jane.doe@example.com")
            .build());
    runCommandForced(
        "--mode=UPDATE",
        "--email=jane.doe@example.com",
        "--allow_console_access=true",
        "NewRegistrar");
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getGaeUserId()).matches("-?[0-9]+");
  }

  @Test
  public void testUpdate_disableConsoleAccess() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Judith Doe")
            .setEmailAddress("judith.doe@example.com")
            .setGaeUserId("11111")
            .build());
    runCommandForced(
        "--mode=UPDATE",
        "--email=judith.doe@example.com",
        "--allow_console_access=false",
        "NewRegistrar");
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getGaeUserId()).isNull();
  }

  @Test
  public void testUpdate_unsetOtherWhoisAbuseFlags() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("John Doe")
            .setEmailAddress("john.doe@example.com")
            .setGaeUserId("11111")
            .build());
    persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Johnna Doe")
            .setEmailAddress("johnna.doe@example.com")
            .setGaeUserId("11112")
            .setVisibleInDomainWhoisAsAbuse(true)
            .build());
    runCommandForced(
        "--mode=UPDATE",
        "--email=john.doe@example.com",
        "--visible_in_domain_whois_as_abuse=true",
        "NewRegistrar");
    ImmutableList<RegistrarContact> registrarContacts =
        loadRegistrar("NewRegistrar").getContacts().asList();
    for (RegistrarContact registrarContact : registrarContacts) {
      if (registrarContact.getName().equals("John Doe")) {
        assertThat(registrarContact.getVisibleInDomainWhoisAsAbuse()).isTrue();
      } else {
        assertThat(registrarContact.getVisibleInDomainWhoisAsAbuse()).isFalse();
      }
    }
  }

  @Test
  public void testUpdate_cannotUnsetOnlyWhoisAbuseContact() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("John Doe")
            .setEmailAddress("john.doe@example.com")
            .setGaeUserId("11111")
            .setVisibleInDomainWhoisAsAbuse(true)
            .build());
    try {
      runCommandForced(
          "--mode=UPDATE",
          "--email=john.doe@example.com",
          "--visible_in_domain_whois_as_abuse=false",
          "NewRegistrar");
      throw new Exception(
          "Expected IllegalArgumentException: Cannot clear visible_in_domain_whois_as_abuse flag");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Cannot clear visible_in_domain_whois_as_abuse flag");
    }
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getVisibleInDomainWhoisAsAbuse()).isTrue();
  }

  @Test
  public void testUpdate_emptyCommandModifiesNothing() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    RegistrarContact existingContact = persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("John Doe")
            .setEmailAddress("john.doe@example.com")
            .setGaeUserId("11111")
            .setPhoneNumber("123-456-7890")
            .setFaxNumber("123-456-7890")
            .setTypes(ImmutableSet.of(ADMIN, ABUSE))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(true)
            .setVisibleInDomainWhoisAsAbuse(true)
            .build());
    runCommandForced("--mode=UPDATE", "--email=john.doe@example.com", "NewRegistrar");
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getEmailAddress()).isEqualTo(existingContact.getEmailAddress());
    assertThat(registrarContact.getName()).isEqualTo(existingContact.getName());
    assertThat(registrarContact.getGaeUserId()).isEqualTo(existingContact.getGaeUserId());
    assertThat(registrarContact.getPhoneNumber()).isEqualTo(existingContact.getPhoneNumber());
    assertThat(registrarContact.getFaxNumber()).isEqualTo(existingContact.getFaxNumber());
    assertThat(registrarContact.getTypes()).isEqualTo(existingContact.getTypes());
    assertThat(registrarContact.getVisibleInWhoisAsAdmin())
        .isEqualTo(existingContact.getVisibleInWhoisAsAdmin());
    assertThat(registrarContact.getVisibleInWhoisAsTech())
        .isEqualTo(existingContact.getVisibleInWhoisAsTech());
    assertThat(registrarContact.getVisibleInDomainWhoisAsAbuse())
        .isEqualTo(existingContact.getVisibleInDomainWhoisAsAbuse());
  }

  @Test
  public void testUpdate_listOfTypesWorks() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("John Doe")
            .setEmailAddress("john.doe@example.com")
            .setGaeUserId("11111")
            .setPhoneNumber("123-456-7890")
            .setFaxNumber("123-456-7890")
            .setTypes(ImmutableSet.of(ADMIN, ABUSE))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(true)
            .setVisibleInDomainWhoisAsAbuse(true)
            .build());
    runCommandForced(
        "--mode=UPDATE",
        "--email=john.doe@example.com",
        "--contact_type=ADMIN,TECH",
        "NewRegistrar");
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getTypes()).containsExactly(ADMIN, TECH);
  }

  @Test
  public void testUpdate_clearAllTypes() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("John Doe")
            .setEmailAddress("john.doe@example.com")
            .setTypes(ImmutableSet.of(ADMIN, ABUSE))
            .build());
    runCommandForced(
        "--mode=UPDATE",
        "--email=john.doe@example.com",
        "--contact_type=",
        "NewRegistrar");
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getTypes()).isEmpty();
  }

  @Test
  public void testCreate_withAdminType() throws Exception {
    Registrar registrar = loadRegistrar("NewRegistrar");
    runCommandForced(
        "--mode=CREATE",
        "--name=Jim Doe",
        "--email=jim.doe@example.com",
        "--contact_type=ADMIN,ABUSE",
        "--visible_in_whois_as_admin=true",
        "--visible_in_whois_as_tech=false",
        "--visible_in_domain_whois_as_abuse=true",
        "NewRegistrar");
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact).isEqualTo(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Jim Doe")
            .setEmailAddress("jim.doe@example.com")
            .setTypes(ImmutableSet.of(ADMIN, ABUSE))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(false)
            .setVisibleInDomainWhoisAsAbuse(true)
            .build());
    assertThat(registrarContact.getGaeUserId()).isNull();
  }

  @Test
  public void testDelete() throws Exception {
    assertThat(loadRegistrar("NewRegistrar").getContacts()).isNotEmpty();
    runCommandForced(
        "--mode=DELETE",
        "--email=janedoe@theregistrar.com",
        "NewRegistrar");
    assertThat(loadRegistrar("NewRegistrar").getContacts()).isEmpty();
  }

  @Test
  public void testDelete_failsOnDomainWhoisAbuseContact() throws Exception {
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(0);
    persistSimpleResource(
        registrarContact.asBuilder().setVisibleInDomainWhoisAsAbuse(true).build());
    try {
      runCommandForced(
          "--mode=DELETE",
          "--email=janedoe@theregistrar.com",
          "NewRegistrar");
      throw new Exception(
          "Expected IllegalArgumentException: Cannot delete the domain WHOIS abuse contact");
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Cannot delete the domain WHOIS abuse contact");
    }
    assertThat(loadRegistrar("NewRegistrar").getContacts()).isNotEmpty();
  }

  @Test
  public void testCreate_withConsoleAccessEnabled() throws Exception {
    runCommandForced(
        "--mode=CREATE",
        "--name=Jim Doe",
        "--email=jim.doe@example.com",
        "--allow_console_access=true",
        "--contact_type=ADMIN,ABUSE",
        "NewRegistrar");
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getGaeUserId()).matches("-?[0-9]+");
  }

  @Test
  public void testCreate_withNoContactTypes() throws Exception {
    runCommandForced(
        "--mode=CREATE", "--name=Jim Doe", "--email=jim.doe@example.com", "NewRegistrar");
    RegistrarContact registrarContact = loadRegistrar("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getTypes()).isEmpty();
  }

  @Test
  public void testCreate_syncingRequiredSetToTrue() throws Exception {
    persistResource(
        loadRegistrar("NewRegistrar").asBuilder().setContactsRequireSyncing(false).build());

    assertThat(loadRegistrar("NewRegistrar").getContactsRequireSyncing()).isFalse();
    runCommandForced(
        "--mode=CREATE", "--name=Jim Doe", "--email=jim.doe@example.com", "NewRegistrar");
    assertThat(loadRegistrar("NewRegistrar").getContactsRequireSyncing()).isTrue();
  }
}
