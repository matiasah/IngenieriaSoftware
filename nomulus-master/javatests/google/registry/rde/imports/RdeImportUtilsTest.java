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

package google.registry.rde.imports;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistNewRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.tools.cloudstorage.GcsFilename;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import google.registry.gcs.GcsUtils;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DesignatedContact.Type;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.EppResourceIndexBucket;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry.TldState;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.ShardableTestCase;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RdeImportUtils} */
@RunWith(JUnit4.class)
public class RdeImportUtilsTest extends ShardableTestCase {

  private static final ByteSource DEPOSIT_XML = RdeImportsTestData.get("deposit_full.xml");
  private static final ByteSource DEPOSIT_BADTLD_XML =
      RdeImportsTestData.get("deposit_full_badtld.xml");
  private static final ByteSource DEPOSIT_GETLD_XML =
      RdeImportsTestData.get("deposit_full_getld.xml");
  private static final ByteSource DEPOSIT_BADREGISTRAR_XML =
      RdeImportsTestData.get("deposit_full_badregistrar.xml");

  private InputStream xmlInput;

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final GcsUtils gcsUtils = mock(GcsUtils.class);

  private RdeImportUtils rdeImportUtils;
  private FakeClock clock;

  @Before
  public void before() {
    clock = new FakeClock();
    clock.setTo(DateTime.now(UTC));
    rdeImportUtils = new RdeImportUtils(ofy(), clock, "import-bucket", gcsUtils);
    createTld("test", TldState.PREDELEGATION);
    createTld("getld", TldState.GENERAL_AVAILABILITY);
    persistNewRegistrar("RegistrarX", "RegistrarX", Registrar.Type.REAL, 1L);
  }

  @After
  public void after() throws IOException {
    if (xmlInput != null) {
      xmlInput.close();
    }
  }

  /** Verifies import of a contact that has not been previously imported */
  @Test
  public void testImportNewContact() {
    final ContactResource newContact = buildNewContact();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        rdeImportUtils.importEppResource(newContact);
      }});
    assertEppResourceIndexEntityFor(newContact);
    assertForeignKeyIndexFor(newContact);

    // verify the new contact was saved
    ContactResource saved = getContact("TEST-123");
    assertThat(saved).isNotNull();
    assertThat(saved.getContactId()).isEqualTo(newContact.getContactId());
    assertThat(saved.getEmailAddress()).isEqualTo(newContact.getEmailAddress());
    assertThat(saved.getLastEppUpdateTime()).isEqualTo(newContact.getLastEppUpdateTime());
  }

  /** Verifies that a contact will not be imported more than once */
  @Test
  public void testImportExistingContact() {
    ContactResource newContact = buildNewContact();
    persistResource(newContact);
    final ContactResource updatedContact =
        newContact
            .asBuilder()
            .setLastEppUpdateTime(newContact.getLastEppUpdateTime().plusSeconds(1))
            .build();
    try {
      ofy().transact(new VoidWork() {
        @Override
        public void vrun() {
          rdeImportUtils.importEppResource(updatedContact);
        }});
      fail("Expected ResourceExistsException");
    } catch (ResourceExistsException expected) {
      // verify the updated contact was not saved
      ContactResource saved = getContact("TEST-123");
      assertThat(saved).isNotNull();
      assertThat(saved.getContactId()).isEqualTo(newContact.getContactId());
      assertThat(saved.getEmailAddress()).isEqualTo(newContact.getEmailAddress());
      assertThat(saved.getLastEppUpdateTime()).isEqualTo(newContact.getLastEppUpdateTime());
    }
  }

  /** Verifies import of a host that has not been previously imported */
  @Test
  public void testImportNewHost() throws UnknownHostException {
    final HostResource newHost = buildNewHost();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        rdeImportUtils.importEppResource(newHost);
      }});

    assertEppResourceIndexEntityFor(newHost);
    assertForeignKeyIndexFor(newHost);

    // verify the new contact was saved
    HostResource saved = getHost("FOO_ROID");
    assertThat(saved).isNotNull();
    assertThat(saved.getFullyQualifiedHostName()).isEqualTo(newHost.getFullyQualifiedHostName());
    assertThat(saved.getInetAddresses()).isEqualTo(newHost.getInetAddresses());
    assertThat(saved.getLastEppUpdateTime()).isEqualTo(newHost.getLastEppUpdateTime());
  }

  /** Verifies that a host will not be imported more than once */
  @Test
  public void testImportExistingHost() throws UnknownHostException {
    HostResource newHost = buildNewHost();
    persistResource(newHost);
    final HostResource updatedHost =
        newHost
          .asBuilder()
          .setLastEppUpdateTime(newHost.getLastEppUpdateTime().plusSeconds(1))
          .build();
    try {
      ofy().transact(new VoidWork() {
        @Override
        public void vrun() {
          rdeImportUtils.importEppResource(updatedHost);
        }});
      fail("Expected ResourceExistsException");
    } catch (ResourceExistsException expected) {
      // verify the contact was not updated
      HostResource saved = getHost("FOO_ROID");
      assertThat(saved).isNotNull();
      assertThat(saved.getFullyQualifiedHostName()).isEqualTo(newHost.getFullyQualifiedHostName());
      assertThat(saved.getInetAddresses()).isEqualTo(newHost.getInetAddresses());
      assertThat(saved.getLastEppUpdateTime()).isEqualTo(newHost.getLastEppUpdateTime());
    }
  }

  @Test
  public void testImportNewDomain() throws Exception {
    final DomainResource newDomain = buildNewDomain();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        rdeImportUtils.importEppResource(newDomain);
      }
    });

    DomainResource saved = getDomain("Dexample1-TEST");
    assertThat(saved.getFullyQualifiedDomainName())
        .isEqualTo(newDomain.getFullyQualifiedDomainName());
    assertThat(saved.getStatusValues()).isEqualTo(newDomain.getStatusValues());
    assertThat(saved.getRegistrant()).isEqualTo(newDomain.getRegistrant());
    assertThat(saved.getContacts()).isEqualTo(newDomain.getContacts());
    assertThat(saved.getCurrentSponsorClientId()).isEqualTo(newDomain.getCurrentSponsorClientId());
    assertThat(saved.getCreationClientId()).isEqualTo(newDomain.getCreationClientId());
    assertThat(saved.getCreationTime()).isEqualTo(newDomain.getCreationTime());
    assertThat(saved.getRegistrationExpirationTime())
        .isEqualTo(newDomain.getRegistrationExpirationTime());
  }

  @Test
  public void testImportExistingDomain() throws Exception {
    DomainResource newDomain = buildNewDomain();
    persistResource(newDomain);
    final DomainResource updatedDomain = newDomain.asBuilder()
        .setFullyQualifiedDomainName("1" + newDomain.getFullyQualifiedDomainName())
        .build();
    try {
      ofy().transact(new VoidWork() {
        @Override
        public void vrun() {
          rdeImportUtils.importEppResource(updatedDomain);
        }});
      fail("Expected ResourceExistsException");
    } catch (ResourceExistsException expected) {
      DomainResource saved = getDomain("Dexample1-TEST");
      assertThat(saved.getFullyQualifiedDomainName())
          .isEqualTo(newDomain.getFullyQualifiedDomainName());
      assertThat(saved.getStatusValues()).isEqualTo(newDomain.getStatusValues());
      assertThat(saved.getRegistrant()).isEqualTo(newDomain.getRegistrant());
      assertThat(saved.getContacts()).isEqualTo(newDomain.getContacts());
      assertThat(saved.getCurrentSponsorClientId())
          .isEqualTo(newDomain.getCurrentSponsorClientId());
      assertThat(saved.getCreationClientId()).isEqualTo(newDomain.getCreationClientId());
      assertThat(saved.getCreationTime()).isEqualTo(newDomain.getCreationTime());
      assertThat(saved.getRegistrationExpirationTime())
          .isEqualTo(newDomain.getRegistrationExpirationTime());
    }
  }

  private static ContactResource buildNewContact() {
    return new ContactResource.Builder()
        .setContactId("sh8013")
        .setEmailAddress("jdoe@example.com")
        .setLastEppUpdateTime(DateTime.parse("2010-10-10T00:00:00.000Z"))
        .setRepoId("TEST-123")
        .build();
  }

  private static HostResource buildNewHost() throws UnknownHostException {
    return new HostResource.Builder()
        .setFullyQualifiedHostName("foo.bar.example")
        .setInetAddresses(
            ImmutableSet.of(
                InetAddress.getByName("192.0.2.2"),
                InetAddress.getByName("192.0.2.29"),
                InetAddress.getByName("1080:0:0:0:8:800:200C:417A")))
        .setLastEppUpdateTime(DateTime.parse("2010-10-10T00:00:00.000Z"))
        .setRepoId("FOO_ROID")
        .build();
  }

  private DomainResource buildNewDomain() {
    ContactResource registrant = persistActiveContact("jd1234");
    ContactResource admin = persistActiveContact("sh8013");
    return new DomainResource.Builder()
        .setFullyQualifiedDomainName("example1.example")
        .setRepoId("Dexample1-TEST")
        .setStatusValues(ImmutableSet.of(StatusValue.OK))
        .setRegistrant(Key.create(registrant))
        .setContacts(ImmutableSet.of(
            DesignatedContact.create(Type.ADMIN, Key.create(admin)),
            DesignatedContact.create(Type.TECH, Key.create(admin))))
        .setPersistedCurrentSponsorClientId("registrarx")
        .setCreationClientId("registrarx")
        .setCreationTime(DateTime.parse("1999-04-03T22:00:00.0Z"))
        .setRegistrationExpirationTime(DateTime.parse("2015-04-03T22:00:00.0Z"))
        .build();
  }

  /** Verifies that no errors are thrown when a valid escrow file is validated */
  @Test
  public void testValidateEscrowFile_valid() throws Exception {
    xmlInput = DEPOSIT_XML.openBufferedStream();
    when(gcsUtils.openInputStream(any(GcsFilename.class))).thenReturn(xmlInput);
    rdeImportUtils.validateEscrowFileForImport("valid-deposit-file.xml");
    // stored to avoid an error in FOSS build, marked "CheckReturnValue"
    InputStream unusedResult = verify(gcsUtils).openInputStream(
        new GcsFilename("import-bucket", "valid-deposit-file.xml"));
  }

  /** Verifies thrown error when tld in escrow file is not in the registry */
  @Test
  public void testValidateEscrowFile_tldNotFound() throws Exception {
    xmlInput = DEPOSIT_BADTLD_XML.openBufferedStream();
    when(gcsUtils.openInputStream(any(GcsFilename.class))).thenReturn(xmlInput);
    thrown.expect(IllegalArgumentException.class, "Tld 'badtld' not found in the registry");
    rdeImportUtils.validateEscrowFileForImport("invalid-deposit-badtld.xml");
  }

  /** Verifies thrown errer when tld in escrow file is not in PREDELEGATION state */
  @Test
  public void testValidateEscrowFile_tldWrongState() throws Exception {
    xmlInput = DEPOSIT_GETLD_XML.openBufferedStream();
    when(gcsUtils.openInputStream(any(GcsFilename.class))).thenReturn(xmlInput);
    thrown.expect(
        IllegalArgumentException.class,
        "Tld 'getld' is in state GENERAL_AVAILABILITY and cannot be imported");
    rdeImportUtils.validateEscrowFileForImport("invalid-deposit-getld.xml");
  }

  /** Verifies thrown error when registrar in escrow file is not in the registry */
  @Test
  public void testValidateEscrowFile_badRegistrar() throws Exception {
    xmlInput = DEPOSIT_BADREGISTRAR_XML.openBufferedStream();
    when(gcsUtils.openInputStream(any(GcsFilename.class))).thenReturn(xmlInput);
    thrown.expect(
        IllegalArgumentException.class, "Registrar 'RegistrarY' not found in the registry");
    rdeImportUtils.validateEscrowFileForImport("invalid-deposit-badregistrar.xml");
  }

  /** Gets the contact with the specified ROID */
  private static ContactResource getContact(String repoId) {
    final Key<ContactResource> key = Key.create(ContactResource.class, repoId);
    return ofy().transact(new Work<ContactResource>() {
      @Override
      public ContactResource run() {
        return ofy().load().key(key).now();
      }});
  }

  /** Gets the host with the specified ROID */
  private static HostResource getHost(String repoId) {
    final Key<HostResource> key = Key.create(HostResource.class, repoId);
    return ofy().transact(new Work<HostResource>() {
      @Override
      public HostResource run() {
        return ofy().load().key(key).now();
      }
    });
  }

  /** Gets the domain with the specified ROID */
  private static DomainResource getDomain(String repoId) {
    final Key<DomainResource> key = Key.create(DomainResource.class, repoId);
    return ofy().transact(new Work<DomainResource>() {
      @Override
      public DomainResource run() {
        return ofy().load().key(key).now();
      }});
  }

  /** Confirms that a ForeignKeyIndex exists in Datastore for a given resource. */
  private <T extends EppResource> void assertForeignKeyIndexFor(final T resource) {
    assertThat(ForeignKeyIndex.load(resource.getClass(), resource.getForeignKey(), clock.nowUtc()))
        .isNotNull();
  }

  /** Confirms that an EppResourceIndex entity exists in Datastore for a given resource. */
  private static <T extends EppResource> void assertEppResourceIndexEntityFor(final T resource) {
    ImmutableList<EppResourceIndex> indices = FluentIterable
        .from(ofy().load()
            .type(EppResourceIndex.class)
            .filter("kind", Key.getKind(resource.getClass())))
        .filter(new Predicate<EppResourceIndex>() {
            @Override
            public boolean apply(EppResourceIndex index) {
              return ofy().load().key(index.getKey()).now().equals(resource);
            }})
        .toList();
    assertThat(indices).hasSize(1);
    assertThat(indices.get(0).getBucket())
        .isEqualTo(EppResourceIndexBucket.getBucketKey(Key.create(resource)));
  }
}
