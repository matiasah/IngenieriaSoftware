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

package google.registry.flows.contact;

import static google.registry.testing.ContactResourceSubject.assertAboutContacts;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.newContactResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistDeletedContact;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.ResourceFlowUtils.AddRemoveSameValueException;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import google.registry.flows.ResourceFlowUtils.StatusNotClientSettableException;
import google.registry.flows.contact.ContactFlowUtils.BadInternationalizedPostalInfoException;
import google.registry.flows.contact.ContactFlowUtils.DeclineContactDisclosureFieldDisallowedPolicyException;
import google.registry.flows.exceptions.ResourceHasClientUpdateProhibitedException;
import google.registry.flows.exceptions.ResourceStatusProhibitsOperationException;
import google.registry.model.contact.ContactAddress;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.PostalInfo;
import google.registry.model.contact.PostalInfo.Type;
import google.registry.model.eppcommon.StatusValue;
import org.junit.Test;

/** Unit tests for {@link ContactUpdateFlow}. */
public class ContactUpdateFlowTest
    extends ResourceFlowTestCase<ContactUpdateFlow, ContactResource> {

  public ContactUpdateFlowTest() {
    setEppInput("contact_update.xml");
  }

  private void doSuccessfulTest() throws Exception {
    persistActiveContact(getUniqueIdFromCommand());
    clock.advanceOneMilli();
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile("contact_update_response.xml"));
    // Check that the contact was updated. This value came from the xml.
    assertAboutContacts().that(reloadResourceByForeignKey())
        .hasAuthInfoPwd("2fooBAR").and()
        .hasOnlyOneHistoryEntryWhich()
        .hasNoXml();
    assertNoBillingEvents();
  }

  @Test
  public void testDryRun() throws Exception {
    persistActiveContact(getUniqueIdFromCommand());
    dryRunFlowAssertResponse(readFile("contact_update_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_updatingInternationalizedPostalInfoDeletesLocalized() throws Exception {
    ContactResource contact =
        persistResource(
            newContactResource(getUniqueIdFromCommand()).asBuilder()
                .setLocalizedPostalInfo(new PostalInfo.Builder()
                    .setType(Type.LOCALIZED)
                    .setAddress(new ContactAddress.Builder()
                        .setStreet(ImmutableList.of("111 8th Ave", "4th Floor"))
                        .setCity("New York")
                        .setState("NY")
                        .setZip("10011")
                        .setCountryCode("US")
                        .build())
                    .build())
                .build());
    clock.advanceOneMilli();
    // The test xml updates the internationalized postal info and should therefore implicitly delete
    // the localized one since they are treated as a pair for update purposes.
    assertAboutContacts().that(contact)
        .hasNonNullLocalizedPostalInfo().and()
        .hasNullInternationalizedPostalInfo();

    runFlowAssertResponse(readFile("contact_update_response.xml"));
    assertAboutContacts().that(reloadResourceByForeignKey())
        .hasNullLocalizedPostalInfo().and()
        .hasInternationalizedPostalInfo(new PostalInfo.Builder()
            .setType(Type.INTERNATIONALIZED)
            .setAddress(new ContactAddress.Builder()
                .setStreet(ImmutableList.of("124 Example Dr.", "Suite 200"))
                .setCity("Dulles")
                .setState("VA")
                .setZip("20166-6503")
                .setCountryCode("US")
                .build())
            .build());
  }

  @Test
  public void testSuccess_updatingLocalizedPostalInfoDeletesInternationalized() throws Exception {
    setEppInput("contact_update_localized.xml");
    ContactResource contact =
        persistResource(
            newContactResource(getUniqueIdFromCommand()).asBuilder()
                .setInternationalizedPostalInfo(new PostalInfo.Builder()
                    .setType(Type.INTERNATIONALIZED)
                    .setAddress(new ContactAddress.Builder()
                        .setStreet(ImmutableList.of("111 8th Ave", "4th Floor"))
                        .setCity("New York")
                        .setState("NY")
                        .setZip("10011")
                        .setCountryCode("US")
                        .build())
                    .build())
                .build());
    clock.advanceOneMilli();
    // The test xml updates the localized postal info and should therefore implicitly delete
    // the internationalized one since they are treated as a pair for update purposes.
    assertAboutContacts().that(contact)
        .hasNonNullInternationalizedPostalInfo().and()
        .hasNullLocalizedPostalInfo();

    runFlowAssertResponse(readFile("contact_update_response.xml"));
    assertAboutContacts().that(reloadResourceByForeignKey())
        .hasNullInternationalizedPostalInfo().and()
        .hasLocalizedPostalInfo(new PostalInfo.Builder()
            .setType(Type.LOCALIZED)
            .setAddress(new ContactAddress.Builder()
                .setStreet(ImmutableList.of("124 Example Dr.", "Suite 200"))
                .setCity("Dulles")
                .setState("VA")
                .setZip("20166-6503")
                .setCountryCode("US")
                .build())
            .build());
  }

  @Test
  public void testSuccess_partialPostalInfoUpdate() throws Exception {
    setEppInput("contact_update_partial_postalinfo.xml");
    persistResource(
        newContactResource(getUniqueIdFromCommand()).asBuilder()
            .setLocalizedPostalInfo(new PostalInfo.Builder()
                .setType(Type.LOCALIZED)
                .setName("A. Person")
                .setOrg("Company Inc.")
                .setAddress(new ContactAddress.Builder()
                    .setStreet(ImmutableList.of("123 4th st", "5th Floor"))
                    .setCity("City")
                    .setState("AB")
                    .setZip("12345")
                    .setCountryCode("US")
                    .build())
                .build())
            .build());
    clock.advanceOneMilli();
    // The test xml updates the address of the postal info and should leave the name untouched.
    runFlowAssertResponse(readFile("contact_update_response.xml"));
    assertAboutContacts().that(reloadResourceByForeignKey()).hasLocalizedPostalInfo(
        new PostalInfo.Builder()
            .setType(Type.LOCALIZED)
            .setName("A. Person")
            .setOrg("Company Inc.")
            .setAddress(new ContactAddress.Builder()
                .setStreet(ImmutableList.of("456 5th st"))
                .setCity("Place")
                .setState("CD")
                .setZip("54321")
                .setCountryCode("US")
                .build())
            .build());
  }


  @Test
  public void testSuccess_updateOnePostalInfo_touchOtherPostalInfoPreservesIt() throws Exception {
    setEppInput("contact_update_partial_postalinfo_preserve_int.xml");
    persistResource(
        newContactResource(getUniqueIdFromCommand()).asBuilder()
        .setLocalizedPostalInfo(new PostalInfo.Builder()
            .setType(Type.LOCALIZED)
            .setName("A. Person")
            .setOrg("Company Inc.")
            .setAddress(new ContactAddress.Builder()
                .setStreet(ImmutableList.of("123 4th st", "5th Floor"))
                .setCity("City")
                .setState("AB")
                .setZip("12345")
                .setCountryCode("US")
                .build())
            .build())
        .setInternationalizedPostalInfo(new PostalInfo.Builder()
            .setType(Type.INTERNATIONALIZED)
            .setName("B. Person")
            .setOrg("Company Co.")
            .setAddress(new ContactAddress.Builder()
                .setStreet(ImmutableList.of("100 200th Dr.", "6th Floor"))
                .setCity("Town")
                .setState("CD")
                .setZip("67890")
                .setCountryCode("US")
                .build())
            .build())
        .build());
    clock.advanceOneMilli();
    // The test xml updates the address of the localized postal info. It also sets the name of the
    // internationalized postal info to the same value it previously had, which causes it to be
    // preserved. If the xml had not mentioned the internationalized one at all it would have been
    // deleted.
    runFlowAssertResponse(readFile("contact_update_response.xml"));
    assertAboutContacts().that(reloadResourceByForeignKey())
        .hasLocalizedPostalInfo(
            new PostalInfo.Builder()
                .setType(Type.LOCALIZED)
                .setName("A. Person")
                .setOrg("Company Inc.")
                .setAddress(new ContactAddress.Builder()
                    .setStreet(ImmutableList.of("456 5th st"))
                    .setCity("Place")
                    .setState("CD")
                    .setZip("54321")
                    .setCountryCode("US")
                    .build())
                .build())
        .and()
        .hasInternationalizedPostalInfo(
            new PostalInfo.Builder()
                .setType(Type.INTERNATIONALIZED)
                .setName("B. Person")
                .setOrg("Company Co.")
                .setAddress(new ContactAddress.Builder()
                    .setStreet(ImmutableList.of("100 200th Dr.", "6th Floor"))
                    .setCity("Town")
                    .setState("CD")
                    .setZip("67890")
                    .setCountryCode("US")
                    .build())
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
    persistDeletedContact(getUniqueIdFromCommand(), clock.nowUtc().minusDays(1));
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_statusValueNotClientSettable() throws Exception {
    setEppInput("contact_update_prohibited_status.xml");
    persistActiveContact(getUniqueIdFromCommand());
    thrown.expect(StatusNotClientSettableException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserStatusValueNotClientSettable() throws Exception {
    setEppInput("contact_update_prohibited_status.xml");
    persistActiveContact(getUniqueIdFromCommand());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("contact_update_response.xml"));
  }

  @Test
  public void testFailure_unauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistActiveContact(getUniqueIdFromCommand());
    thrown.expect(ResourceNotOwnedException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistActiveContact(getUniqueIdFromCommand());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("contact_update_response.xml"));
  }

  @Test
  public void testSuccess_clientUpdateProhibited_removed() throws Exception {
    setEppInput("contact_update_remove_client_update_prohibited.xml");
    persistResource(
        newContactResource(getUniqueIdFromCommand()).asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED))
            .build());
    doSuccessfulTest();
    assertAboutContacts().that(reloadResourceByForeignKey())
        .doesNotHaveStatusValue(StatusValue.CLIENT_UPDATE_PROHIBITED);
  }

  @Test
  public void testSuccess_superuserClientUpdateProhibited_notRemoved() throws Exception {
    setEppInput("contact_update_prohibited_status.xml");
    persistResource(
        newContactResource(getUniqueIdFromCommand()).asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED))
            .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("contact_update_response.xml"));
    assertAboutContacts().that(reloadResourceByForeignKey())
        .hasStatusValue(StatusValue.CLIENT_UPDATE_PROHIBITED).and()
        .hasStatusValue(StatusValue.SERVER_DELETE_PROHIBITED);
  }

  @Test
  public void testFailure_clientUpdateProhibited_notRemoved() throws Exception {
    persistResource(
        newContactResource(getUniqueIdFromCommand()).asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED))
            .build());
    thrown.expect(ResourceHasClientUpdateProhibitedException.class);
    runFlow();
  }

  @Test
  public void testFailure_serverUpdateProhibited() throws Exception {
    persistResource(
        newContactResource(getUniqueIdFromCommand()).asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.SERVER_UPDATE_PROHIBITED))
            .build());
    thrown.expect(ResourceStatusProhibitsOperationException.class, "serverUpdateProhibited");
    runFlow();
  }

  @Test
  public void testFailure_pendingDeleteProhibited() throws Exception {
    persistResource(
        newContactResource(getUniqueIdFromCommand()).asBuilder()
            .setStatusValues(ImmutableSet.of(StatusValue.PENDING_DELETE))
            .build());
    thrown.expect(ResourceStatusProhibitsOperationException.class, "pendingDelete");
    runFlow();
  }

  @Test
  public void testSuccess_nonAsciiInLocAddress() throws Exception {
    setEppInput("contact_update_hebrew_loc.xml");
    doSuccessfulTest();
  }

  @Test
  public void testFailure_nonAsciiInIntAddress() throws Exception {
    setEppInput("contact_update_hebrew_int.xml");
    persistActiveContact(getUniqueIdFromCommand());
    thrown.expect(BadInternationalizedPostalInfoException.class);
    runFlow();
  }

  @Test
  public void testFailure_declineDisclosure() throws Exception {
    setEppInput("contact_update_decline_disclosure.xml");
    persistActiveContact(getUniqueIdFromCommand());
    thrown.expect(DeclineContactDisclosureFieldDisallowedPolicyException.class);
    runFlow();
  }

  @Test
  public void testFailure_addRemoveSameValue() throws Exception {
    setEppInput("contact_update_add_remove_same.xml");
    persistActiveContact(getUniqueIdFromCommand());
    thrown.expect(AddRemoveSameValueException.class);
    runFlow();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    persistActiveContact(getUniqueIdFromCommand());
    clock.advanceOneMilli();
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-cont-update");
  }
}
