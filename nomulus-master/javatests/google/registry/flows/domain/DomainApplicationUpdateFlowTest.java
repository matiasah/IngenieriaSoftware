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

import static com.google.common.collect.Sets.union;
import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.generateNewDomainRoid;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DomainApplicationSubject.assertAboutApplications;
import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException.UnimplementedExtensionException;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.ResourceFlowUtils.AddRemoveSameValueException;
import google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException;
import google.registry.flows.ResourceFlowUtils.ResourceNotOwnedException;
import google.registry.flows.ResourceFlowUtils.StatusNotClientSettableException;
import google.registry.flows.domain.DomainApplicationUpdateFlow.ApplicationStatusProhibitsUpdateException;
import google.registry.flows.domain.DomainFlowUtils.ApplicationDomainNameMismatchException;
import google.registry.flows.domain.DomainFlowUtils.DuplicateContactForRoleException;
import google.registry.flows.domain.DomainFlowUtils.EmptySecDnsUpdateException;
import google.registry.flows.domain.DomainFlowUtils.FeesMismatchException;
import google.registry.flows.domain.DomainFlowUtils.LinkedResourcesDoNotExistException;
import google.registry.flows.domain.DomainFlowUtils.MaxSigLifeChangeNotSupportedException;
import google.registry.flows.domain.DomainFlowUtils.MissingAdminContactException;
import google.registry.flows.domain.DomainFlowUtils.MissingContactTypeException;
import google.registry.flows.domain.DomainFlowUtils.MissingTechnicalContactException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotAllowedForDomainException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotAllowedForTldException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotSpecifiedForNameserverRestrictedDomainException;
import google.registry.flows.domain.DomainFlowUtils.NameserversNotSpecifiedForTldWithNameserverWhitelistException;
import google.registry.flows.domain.DomainFlowUtils.NotAuthorizedForTldException;
import google.registry.flows.domain.DomainFlowUtils.RegistrantNotAllowedException;
import google.registry.flows.domain.DomainFlowUtils.SecDnsAllUsageException;
import google.registry.flows.domain.DomainFlowUtils.TooManyDsRecordsException;
import google.registry.flows.domain.DomainFlowUtils.TooManyNameserversException;
import google.registry.flows.domain.DomainFlowUtils.UrgentAttributeNotSupportedException;
import google.registry.flows.exceptions.ResourceHasClientUpdateProhibitedException;
import google.registry.flows.exceptions.ResourceStatusProhibitsOperationException;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DesignatedContact.Type;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainApplication.Builder;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.domain.secdns.DelegationSignerData;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.reporting.HistoryEntry;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DomainApplicationUpdateFlow}. */
public class DomainApplicationUpdateFlowTest
    extends ResourceFlowTestCase<DomainApplicationUpdateFlow, DomainApplication> {

  private static final DelegationSignerData SOME_DSDATA =
      DelegationSignerData.create(1, 2, 3, new byte[]{0, 1, 2});

  ContactResource sh8013Contact;
  ContactResource mak21Contact;
  ContactResource unusedContact;

  public DomainApplicationUpdateFlowTest() {
    // Note that "domain_update_sunrise.xml" tests adding and removing the same contact type.
    setEppInput("domain_update_sunrise.xml");
  }

  @Before
  public void setUp() {
    createTld("tld", TldState.SUNRUSH);
  }

  private void persistReferencedEntities() {
    // Grab the 1 id for use with the DomainApplication.
    generateNewDomainRoid("tld");
    for (int i = 1; i <= 14; ++i) {
      persistActiveHost(String.format("ns%d.example.tld", i));
    }
    sh8013Contact = persistActiveContact("sh8013");
    mak21Contact = persistActiveContact("mak21");
    unusedContact = persistActiveContact("unused");
  }

  private DomainApplication persistApplication() throws Exception {
    return persistResource(newApplicationBuilder()
        .setContacts(ImmutableSet.of(
            DesignatedContact.create(Type.TECH, Key.create(sh8013Contact)),
            DesignatedContact.create(Type.ADMIN, Key.create(unusedContact))))
        .setNameservers(ImmutableSet.of(Key.create(
            loadByForeignKey(HostResource.class, "ns1.example.tld", clock.nowUtc()))))
        .build());
  }

  private Builder newApplicationBuilder() throws Exception {
    return newDomainApplication("example.tld").asBuilder().setRepoId("1-TLD");
  }

  private DomainApplication persistNewApplication() throws Exception {
    return persistResource(newApplicationBuilder().build());
  }

  private void doSuccessfulTest() throws Exception {
    assertTransactionalFlow(true);
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("domain_update_response.xml"));
    // Check that the application was updated. These values came from the xml.
    DomainApplication application = reloadDomainApplication();
    assertAboutApplications().that(application)
        .hasStatusValue(StatusValue.CLIENT_HOLD).and()
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE);
    assertThat(application.getAuthInfo().getPw().getValue()).isEqualTo("2BARfoo");
    // Check that the hosts and contacts have correct linked status
    assertNoBillingEvents();
  }

  @Test
  public void testDryRun() throws Exception {
    persistReferencedEntities();
    persistApplication();
    dryRunFlowAssertResponse(readFile("domain_update_response.xml"));
  }

  @Test
  public void testSuccess() throws Exception {
    persistReferencedEntities();
    persistApplication();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_maxNumberOfNameservers() throws Exception {
    persistReferencedEntities();
    persistApplication();
    modifyApplicationToHave13Nameservers();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_removeContact() throws Exception {
    setEppInput("domain_update_sunrise_remove_contact.xml");
    persistReferencedEntities();
    persistApplication();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_registrantMovedToTechContact() throws Exception {
    setEppInput("domain_update_sunrise_registrant_to_tech.xml");
    persistReferencedEntities();
    ContactResource sh8013 = loadByForeignKey(ContactResource.class, "sh8013", clock.nowUtc());
    persistResource(
        newApplicationBuilder().setRegistrant(Key.create(sh8013)).build());
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("domain_update_response.xml"));
  }

  @Test
  public void testSuccess_multipleReferencesToSameContactRemoved() throws Exception {
    setEppInput("domain_update_sunrise_remove_multiple_contacts.xml");
    persistReferencedEntities();
    ContactResource sh8013 = loadByForeignKey(ContactResource.class, "sh8013", clock.nowUtc());
    Key<ContactResource> sh8013Key = Key.create(sh8013);
    persistResource(newApplicationBuilder()
        .setRegistrant(sh8013Key)
        .setContacts(ImmutableSet.of(
            DesignatedContact.create(Type.ADMIN, sh8013Key),
            DesignatedContact.create(Type.BILLING, sh8013Key),
            DesignatedContact.create(Type.TECH, sh8013Key)))
        .build());
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("domain_update_response.xml"));
  }

  @Test
  public void testSuccess_removeClientUpdateProhibited() throws Exception {
    persistReferencedEntities();
    persistResource(persistApplication().asBuilder().setStatusValues(
        ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED)).build());
    clock.advanceOneMilli();
    runFlow();
    assertAboutApplications().that(reloadDomainApplication())
        .doesNotHaveStatusValue(StatusValue.CLIENT_UPDATE_PROHIBITED);
  }

  private void doSecDnsSuccessfulTest(
      String xmlFilename,
      ImmutableSet<DelegationSignerData> originalDsData,
      ImmutableSet<DelegationSignerData> expectedDsData)
      throws Exception {
    setEppInput(xmlFilename);
    persistResource(newApplicationBuilder().setDsData(originalDsData).build());
    assertTransactionalFlow(true);
    clock.advanceOneMilli();
    runFlowAssertResponse(readFile("domain_update_response.xml"));
    assertAboutApplications().that(reloadDomainApplication())
        .hasExactlyDsData(expectedDsData).and()
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.DOMAIN_APPLICATION_UPDATE);
  }

  @Test
  public void testSuccess_secDnsAdd() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add.xml",
        null,
        ImmutableSet.of(DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))));
  }


  @Test
  public void testSuccess_secDnsAddPreservesExisting() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add.xml",
        ImmutableSet.of(SOME_DSDATA),
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))));
  }

  @Test
  public void testSuccess_secDnsAddToMaxRecords() throws Exception {
    ImmutableSet.Builder<DelegationSignerData> builder = new ImmutableSet.Builder<>();
    for (int i = 0; i < 7; ++i) {
      builder.add(DelegationSignerData.create(i, 2, 3, new byte[]{0, 1, 2}));
    }
    ImmutableSet<DelegationSignerData> commonDsData = builder.build();

    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add.xml",
        commonDsData,
        ImmutableSet.copyOf(
            union(commonDsData, ImmutableSet.of(DelegationSignerData.create(
                12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))))));
  }

  @Test
  public void testSuccess_secDnsRemove() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_rem.xml",
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))),
        ImmutableSet.of(SOME_DSDATA));
  }

  @Test
  public void testSuccess_secDnsRemoveAll() throws Exception {
    // As an aside, this test also validates that it's ok to set the 'urgent' attribute to false.
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_rem_all.xml",
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))),
        ImmutableSet.<DelegationSignerData>of());
  }

  @Test
  public void testSuccess_secDnsAddRemove() throws Exception {
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add_rem.xml",
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))),
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))));
  }

  @Test
  public void testSuccess_secDnsAddRemoveToMaxRecords() throws Exception {
    ImmutableSet.Builder<DelegationSignerData> builder = new ImmutableSet.Builder<>();
    for (int i = 0; i < 7; ++i) {
      builder.add(DelegationSignerData.create(i, 2, 3, new byte[]{0, 1, 2}));
    }
    ImmutableSet<DelegationSignerData> commonDsData = builder.build();

    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add_rem.xml",
        ImmutableSet.copyOf(
            union(commonDsData, ImmutableSet.of(DelegationSignerData.create(
                12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))))),
        ImmutableSet.copyOf(
            union(commonDsData, ImmutableSet.of(DelegationSignerData.create(
                12346, 3, 1, base16().decode("38EC35D5B3A34B44C39B"))))));
  }

  @Test
  public void testSuccess_secDnsAddRemoveSame() throws Exception {
    // Adding and removing the same dsData is a no-op because removes are processed first.
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_add_rem_same.xml",
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))),
        ImmutableSet.of(SOME_DSDATA, DelegationSignerData.create(
            12345, 3, 1, base16().decode("38EC35D5B3A34B33C99B"))));
  }

  @Test
  public void testSuccess_secDnsRemoveAlreadyNotThere() throws Exception {
    // Removing a dsData that isn't there is a no-op.
    doSecDnsSuccessfulTest(
        "domain_update_sunrise_dsdata_rem.xml",
        ImmutableSet.of(SOME_DSDATA),
        ImmutableSet.of(SOME_DSDATA));
  }

  private void doSecDnsFailingTest(Class<? extends Exception> expectedException, String xmlFilename)
      throws Exception {
    setEppInput(xmlFilename);
    persistReferencedEntities();
    persistNewApplication();
    thrown.expect(expectedException);
    runFlow();
  }

  @Test
  public void testFailure_secDnsAllCannotBeFalse() throws Exception {
    doSecDnsFailingTest(
        SecDnsAllUsageException.class, "domain_update_sunrise_dsdata_rem_all_false.xml");
  }

  @Test
  public void testFailure_secDnsEmptyNotAllowed() throws Exception {
    doSecDnsFailingTest(EmptySecDnsUpdateException.class, "domain_update_sunrise_dsdata_empty.xml");
  }

  @Test
  public void testFailure_secDnsUrgentNotSupported() throws Exception {
    doSecDnsFailingTest(
        UrgentAttributeNotSupportedException.class,
        "domain_update_sunrise_dsdata_urgent.xml");
  }

  @Test
  public void testFailure_secDnsChangeNotSupported() throws Exception {
    doSecDnsFailingTest(
        MaxSigLifeChangeNotSupportedException.class,
        "domain_update_sunrise_maxsiglife.xml");
  }

  @Test
  public void testFailure_secDnsTooManyDsRecords() throws Exception {
    ImmutableSet.Builder<DelegationSignerData> builder = new ImmutableSet.Builder<>();
    for (int i = 0; i < 8; ++i) {
      builder.add(DelegationSignerData.create(i, 2, 3, new byte[]{0, 1, 2}));
    }

    setEppInput("domain_update_sunrise_dsdata_add.xml");
    persistResource(newApplicationBuilder().setDsData(builder.build()).build());
    thrown.expect(TooManyDsRecordsException.class);
    runFlow();
  }

  private void modifyApplicationToHave13Nameservers() throws Exception {
    ImmutableSet.Builder<Key<HostResource>> nameservers = new ImmutableSet.Builder<>();
    for (int i = 1; i < 15; i++) {
      if (i != 2) { // Skip 2 since that's the one that the tests will add.
        nameservers.add(Key.create(loadByForeignKey(
            HostResource.class, String.format("ns%d.example.tld", i), clock.nowUtc())));
      }
    }
    persistResource(reloadDomainApplication().asBuilder()
        .setNameservers(nameservers.build())
        .build());
  }

  @Test
  public void testFailure_tooManyNameservers() throws Exception {
    persistReferencedEntities();
    persistApplication();
    // Modify application to have 13 nameservers. We will then remove one and add one in the test.
    modifyApplicationToHave13Nameservers();
    setEppInput("domain_update_sunrise_add_nameserver.xml");
    thrown.expect(TooManyNameserversException.class);
    runFlow();
  }

  @Test
  public void testFailure_wrongExtension() throws Exception {
    setEppInput("domain_update_sunrise_wrong_extension.xml");
    thrown.expect(UnimplementedExtensionException.class);
    runFlow();
  }

  @Test
  public void testFailure_applicationDomainNameMismatch() throws Exception {
    persistReferencedEntities();
    persistResource(newApplicationBuilder().setFullyQualifiedDomainName("something.tld").build());
    thrown.expect(ApplicationDomainNameMismatchException.class);
    runFlow();
  }

  @Test
  public void testFailure_neverExisted() throws Exception {
    persistReferencedEntities();
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_existedButWasDeleted() throws Exception {
    persistReferencedEntities();
    persistResource(newApplicationBuilder().setDeletionTime(START_OF_TIME).build());
    thrown.expect(
        ResourceDoesNotExistException.class,
        String.format("(%s)", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_clientUpdateProhibited() throws Exception {
    setEppInput("domain_update_sunrise_authinfo.xml");
    persistReferencedEntities();
    persistResource(newApplicationBuilder().setStatusValues(
        ImmutableSet.of(StatusValue.CLIENT_UPDATE_PROHIBITED)).build());
    thrown.expect(ResourceHasClientUpdateProhibitedException.class);
    runFlow();
  }

  @Test
  public void testFailure_serverUpdateProhibited() throws Exception {
    persistReferencedEntities();
    persistResource(newApplicationBuilder().setStatusValues(
        ImmutableSet.of(StatusValue.SERVER_UPDATE_PROHIBITED)).build());
    thrown.expect(ResourceStatusProhibitsOperationException.class, "serverUpdateProhibited");
    runFlow();
  }

  private void doIllegalApplicationStatusTest(ApplicationStatus status) throws Exception {
    persistReferencedEntities();
    persistResource(newApplicationBuilder().setApplicationStatus(status).build());
    thrown.expect(ApplicationStatusProhibitsUpdateException.class);
    runFlow();
  }

  @Test
  public void testFailure_allocatedApplicationStatus() throws Exception {
    doIllegalApplicationStatusTest(ApplicationStatus.ALLOCATED);
  }

  @Test
  public void testFailure_invalidApplicationStatus() throws Exception {
    doIllegalApplicationStatusTest(ApplicationStatus.INVALID);
  }

  @Test
  public void testFailure_rejectedApplicationStatus() throws Exception {
    doIllegalApplicationStatusTest(ApplicationStatus.REJECTED);
  }

  @Test
  public void testFailure_missingHost() throws Exception {
    persistActiveHost("ns1.example.tld");
    persistActiveContact("sh8013");
    persistActiveContact("mak21");
    persistNewApplication();
    thrown.expect(
        LinkedResourcesDoNotExistException.class,
        "(ns2.example.tld)");
    runFlow();
  }

  @Test
  public void testFailure_missingContact() throws Exception {
    persistActiveHost("ns1.example.tld");
    persistActiveHost("ns2.example.tld");
    persistActiveContact("mak21");
    persistNewApplication();
    thrown.expect(
        LinkedResourcesDoNotExistException.class,
        "(sh8013)");
    runFlow();
  }

  @Test
  public void testFailure_addingDuplicateContact() throws Exception {
    persistReferencedEntities();
    persistActiveContact("foo");
    persistNewApplication();
    // Add a tech contact to the persisted entity, which should cause the flow to fail when it tries
    // to add "mak21" as a second tech contact.
    persistResource(reloadDomainApplication().asBuilder().setContacts(ImmutableSet.of(
        DesignatedContact.create(Type.TECH, Key.create(
            loadByForeignKey(ContactResource.class, "foo", clock.nowUtc()))))).build());
    thrown.expect(DuplicateContactForRoleException.class);
    runFlow();
  }

  @Test
  public void testFailure_clientProhibitedStatusValue() throws Exception {
    setEppInput("domain_update_sunrise_prohibited_status.xml");
    persistReferencedEntities();
    persistNewApplication();
    thrown.expect(StatusNotClientSettableException.class);
    runFlow();
  }


  @Test
  public void testSuccess_superuserProhibitedStatusValue() throws Exception {
    setEppInput("domain_update_sunrise_prohibited_status.xml");
    persistReferencedEntities();
    persistNewApplication();
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE,
        UserPrivileges.SUPERUSER,
        readFile("domain_update_response.xml"));
  }

  @Test
  public void testFailure_duplicateContactInCommand() throws Exception {
    setEppInput("domain_update_sunrise_duplicate_contact.xml");
    persistReferencedEntities();
    persistNewApplication();
    thrown.expect(DuplicateContactForRoleException.class);
    runFlow();
  }

  @Test
  public void testFailure_missingContactType() throws Exception {
    setEppInput("domain_update_sunrise_missing_contact_type.xml");
    persistReferencedEntities();
    persistNewApplication();
    // We need to test for missing type, but not for invalid - the schema enforces that for us.
    thrown.expect(MissingContactTypeException.class);
    runFlow();
  }

  @Test
  public void testFailure_unauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistReferencedEntities();
    persistApplication();
    thrown.expect(ResourceNotOwnedException.class);
    runFlow();
  }

  @Test
  public void testSuccess_superuserUnauthorizedClient() throws Exception {
    sessionMetadata.setClientId("NewRegistrar");
    persistReferencedEntities();
    persistApplication();
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_update_response.xml"));
  }

  @Test
  public void testFailure_notAuthorizedForTld() throws Exception {
    persistResource(
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setAllowedTlds(ImmutableSet.<String>of())
            .build());
    persistReferencedEntities();
    persistApplication();
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
    persistReferencedEntities();
    persistApplication();
    clock.advanceOneMilli();
    runFlowAssertResponse(
        CommitMode.LIVE, UserPrivileges.SUPERUSER, readFile("domain_update_response.xml"));
  }

  @Test
  public void testFailure_sameNameserverAddedAndRemoved() throws Exception {
    setEppInput("domain_update_sunrise_add_remove_same_host.xml");
    persistReferencedEntities();
    persistResource(newApplicationBuilder()
        .setNameservers(ImmutableSet.of(Key.create(
            loadByForeignKey(HostResource.class, "ns1.example.tld", clock.nowUtc()))))
        .build());
    thrown.expect(AddRemoveSameValueException.class);
    runFlow();
  }

  @Test
  public void testFailure_sameContactAddedAndRemoved() throws Exception {
    setEppInput("domain_update_sunrise_add_remove_same_contact.xml");
    persistReferencedEntities();
    persistResource(newApplicationBuilder()
        .setContacts(ImmutableSet.of(DesignatedContact.create(
            Type.TECH,
            Key.create(
                loadByForeignKey(ContactResource.class, "sh8013", clock.nowUtc())))))
        .build());
    thrown.expect(AddRemoveSameValueException.class);
    runFlow();
  }

  @Test
  public void testFailure_removeAdmin() throws Exception {
    setEppInput("domain_update_sunrise_remove_admin.xml");
    persistReferencedEntities();
    persistResource(newApplicationBuilder()
        .setContacts(ImmutableSet.of(
            DesignatedContact.create(Type.ADMIN, Key.create(sh8013Contact)),
            DesignatedContact.create(Type.TECH, Key.create(sh8013Contact))))
        .build());
    thrown.expect(MissingAdminContactException.class);
    runFlow();
  }

  @Test
  public void testFailure_removeTech() throws Exception {
    setEppInput("domain_update_sunrise_remove_tech.xml");
    persistReferencedEntities();
    persistResource(newApplicationBuilder()
        .setContacts(ImmutableSet.of(
            DesignatedContact.create(Type.ADMIN, Key.create(sh8013Contact)),
            DesignatedContact.create(Type.TECH, Key.create(sh8013Contact))))
        .build());
    thrown.expect(MissingTechnicalContactException.class);
    runFlow();
  }

  @Test
  public void testFailure_newRegistrantNotWhitelisted() throws Exception {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld").asBuilder()
            .setAllowedRegistrantContactIds(ImmutableSet.of("contact1234"))
            .build());
    clock.advanceOneMilli();
    thrown.expect(RegistrantNotAllowedException.class);
    runFlow();
  }

  @Test
  public void testFailure_newNameserverNotWhitelisted() throws Exception {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld").asBuilder()
            .setAllowedFullyQualifiedHostNames(ImmutableSet.of("ns1.example.foo"))
            .build());
    clock.advanceOneMilli();
    thrown.expect(NameserversNotAllowedForTldException.class);
    runFlow();
  }

  @Test
  public void testSuccess_nameserverAndRegistrantWhitelisted() throws Exception {
    persistResource(
        Registry.get("tld").asBuilder()
            .setAllowedRegistrantContactIds(ImmutableSet.of("sh8013"))
            .setAllowedFullyQualifiedHostNames(ImmutableSet.of("ns2.example.tld"))
            .build());
    persistReferencedEntities();
    persistApplication();
    doSuccessfulTest();
  }

  @Test
  public void testFailure_tldWithNameserverWhitelist_removeLastNameserver() throws Exception {
    setEppInput("domain_update_sunrise_remove_nameserver.xml");
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    thrown.expect(NameserversNotSpecifiedForTldWithNameserverWhitelistException.class);
    runFlow();
  }

  @Test
  public void testSuccess_tldWithNameserverWhitelist_removeNameserver() throws Exception {
    setEppInput("domain_update_sunrise_remove_nameserver.xml");
    persistReferencedEntities();
    persistApplication();
    persistResource(
        reloadDomainApplication()
            .asBuilder()
            .addNameservers(
                ImmutableSet.of(
                    Key.create(
                        loadByForeignKey(HostResource.class, "ns2.example.tld", clock.nowUtc()))))
            .build());
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_domainNameserverRestricted_addedNameserverAllowed() throws Exception {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testFailure_domainNameserverRestricted_addedNameserverDisallowed() throws Exception {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns3.example.tld"))
            .build());
    clock.advanceOneMilli();
    thrown.expect(NameserversNotAllowedForDomainException.class, "ns2.example.tld");
    runFlow();
  }

  @Test
  public void testFailure_domainNameserverRestricted_removeLastNameserver() throws Exception {
    setEppInput("domain_update_sunrise_remove_nameserver.xml");
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    thrown.expect(NameserversNotSpecifiedForNameserverRestrictedDomainException.class);
    runFlow();
  }

  @Test
  public void testSuccess_domainNameserverRestricted_removeNameservers() throws Exception {
    setEppInput("domain_update_sunrise_remove_nameserver.xml");
    persistReferencedEntities();
    persistApplication();
    persistResource(
        reloadDomainApplication()
            .asBuilder()
            .addNameservers(
                ImmutableSet.of(
                    Key.create(
                        loadByForeignKey(HostResource.class, "ns2.example.tld", clock.nowUtc()))))
            .build());
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testSuccess_addedNameserversAllowedInTldAndDomainNameserversWhitelists()
      throws Exception {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns2.example.tld"))
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    doSuccessfulTest();
  }

  @Test
  public void testFailure_addedNameserversAllowedInTld_disallowedInDomainNameserversWhitelists()
      throws Exception {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns2.example.tld"))
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns3.example.tld"))
            .build());
    clock.advanceOneMilli();
    thrown.expect(NameserversNotAllowedForDomainException.class, "ns2.example.tld");
    runFlow();
  }

  @Test
  public void testFailure_addedNameserversDisallowedInTld_AllowedInDomainNameserversWhitelists()
      throws Exception {
    persistReferencedEntities();
    persistApplication();
    persistResource(
        Registry.get("tld")
            .asBuilder()
            .setAllowedFullyQualifiedHostNames(
                ImmutableSet.of("ns1.example.tld", "ns3.example.tld"))
            .setReservedLists(
                persistReservedList(
                    "reserved", "example,NAMESERVER_RESTRICTED,ns1.example.tld:ns2.example.tld"))
            .build());
    clock.advanceOneMilli();
    thrown.expect(NameserversNotAllowedForTldException.class, "ns2.example.tld");
    runFlow();
  }

  @Test
  public void testFailure_customPricingLogic_feeMismatch() throws Exception {
    persistReferencedEntities();
    persistResource(
        newDomainApplication("non-free-update.tld").asBuilder().setRepoId("1-ROID").build());
    setEppInput(
        "domain_update_sunrise_fee.xml",
        ImmutableMap.of("DOMAIN", "non-free-update.tld", "AMOUNT", "12.00"));
    clock.advanceOneMilli();
    thrown.expect(FeesMismatchException.class);
    runFlow();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    persistReferencedEntities();
    persistApplication();
    clock.advanceOneMilli();
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-dom-update");
    assertTldsFieldLogged("tld");
  }
}
