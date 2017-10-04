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

package google.registry.batch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistDeletedDomain;
import static google.registry.testing.DatastoreHelper.persistDomainAsDeleted;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResource;
import static google.registry.testing.TaskQueueHelper.assertDnsTasksEnqueued;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.time.DateTimeZone.UTC;

import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.model.ImmutableObject;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.domain.DomainResource;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.ForeignKeyIndex;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldType;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeResponse;
import google.registry.testing.mapreduce.MapreduceTestCase;
import java.util.Set;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DeleteProberDataAction}. */
@RunWith(JUnit4.class)
public class DeleteProberDataActionTest extends MapreduceTestCase<DeleteProberDataAction> {

  private static final DateTime DELETION_TIME = DateTime.parse("2010-01-01T00:00:00.000Z");

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Before
  public void init() {
    // Entities in these two should not be touched.
    createTld("tld", "TLD");
    // Since "example" doesn't end with .test, its entities won't be deleted even though it is of
    // TEST type.
    createTld("example", "EXAMPLE");
    persistResource(Registry.get("example").asBuilder().setTldType(TldType.TEST).build());

    // Entities in these two should be deleted.
    createTld("ib-any.test", "IBANYT");
    persistResource(Registry.get("ib-any.test").asBuilder().setTldType(TldType.TEST).build());
    createTld("oa-canary.test", "OACANT");
    persistResource(Registry.get("oa-canary.test").asBuilder().setTldType(TldType.TEST).build());

    resetAction();
  }

  private void resetAction() {
    action = new DeleteProberDataAction();
    action.mrRunner = makeDefaultRunner();
    action.response = new FakeResponse();
    action.isDryRun = false;
    action.registryAdminClientId = "TheRegistrar";
  }

  private void runMapreduce() throws Exception {
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void test_deletesAllAndOnlyProberData() throws Exception {
    Set<ImmutableObject> tldEntities = persistLotsOfDomains("tld");
    Set<ImmutableObject> exampleEntities = persistLotsOfDomains("example");
    Set<ImmutableObject> ibEntities = persistLotsOfDomains("ib-any.test");
    Set<ImmutableObject> oaEntities = persistLotsOfDomains("oa-canary.test");
    runMapreduce();
    assertNotDeleted(tldEntities);
    assertNotDeleted(exampleEntities);
    assertDeleted(ibEntities);
    assertDeleted(oaEntities);
  }

  @Test
  public void testSuccess_doesntDeleteNicDomainForProbers() throws Exception {
    DomainResource nic = persistActiveDomain("nic.ib-any.test");
    ForeignKeyIndex<DomainResource> fkiNic =
        ForeignKeyIndex.load(DomainResource.class, "nic.ib-any.test", START_OF_TIME);
    Set<ImmutableObject> ibEntities = persistLotsOfDomains("ib-any.test");
    runMapreduce();
    assertDeleted(ibEntities);
    assertNotDeleted(ImmutableSet.<ImmutableObject>of(nic, fkiNic));
  }

  @Test
  public void testDryRun_doesntDeleteData() throws Exception {
    Set<ImmutableObject> tldEntities = persistLotsOfDomains("tld");
    Set<ImmutableObject> oaEntities = persistLotsOfDomains("oa-canary.test");
    action.isDryRun = true;
    runMapreduce();
    assertNotDeleted(tldEntities);
    assertNotDeleted(oaEntities);
  }

  @Test
  public void testSuccess_activeDomain_isSoftDeleted() throws Exception {
    DomainResource domain = persistResource(
        newDomainResource("blah.ib-any.test")
            .asBuilder()
            .setCreationTimeForTest(DateTime.now(UTC).minusYears(1))
            .build());
    runMapreduce();
    DateTime timeAfterDeletion = DateTime.now(UTC);
    assertThat(loadByForeignKey(DomainResource.class, "blah.ib-any.test", timeAfterDeletion))
        .isNull();
    assertThat(ofy().load().entity(domain).now().getDeletionTime()).isLessThan(timeAfterDeletion);
    assertDnsTasksEnqueued("blah.ib-any.test");
  }

  @Test
  public void testSuccess_activeDomain_doubleMapSoftDeletes() throws Exception {
    DomainResource domain = persistResource(
        newDomainResource("blah.ib-any.test")
            .asBuilder()
            .setCreationTimeForTest(DateTime.now(UTC).minusYears(1))
            .build());
    runMapreduce();
    DateTime timeAfterDeletion = DateTime.now(UTC);
    resetAction();
    runMapreduce();
    assertThat(loadByForeignKey(DomainResource.class, "blah.ib-any.test", timeAfterDeletion))
        .isNull();
    assertThat(ofy().load().entity(domain).now().getDeletionTime()).isLessThan(timeAfterDeletion);
    assertDnsTasksEnqueued("blah.ib-any.test");
  }

  @Test
  public void test_recentlyCreatedDomain_isntDeletedYet() throws Exception {
    persistResource(
        newDomainResource("blah.ib-any.test")
            .asBuilder()
            .setCreationTimeForTest(DateTime.now(UTC).minusSeconds(1))
            .build());
    runMapreduce();
    DomainResource domain =
        loadByForeignKey(DomainResource.class, "blah.ib-any.test", DateTime.now(UTC));
    assertThat(domain).isNotNull();
    assertThat(domain.getDeletionTime()).isEqualTo(END_OF_TIME);
  }

  @Test
  public void testDryRun_doesntSoftDeleteData() throws Exception {
    DomainResource domain = persistResource(
        newDomainResource("blah.ib-any.test")
            .asBuilder()
            .setCreationTimeForTest(DateTime.now(UTC).minusYears(1))
            .build());
    action.isDryRun = true;
    runMapreduce();
    assertThat(ofy().load().entity(domain).now().getDeletionTime()).isEqualTo(END_OF_TIME);
  }

  @Test
  public void test_domainWithSubordinateHosts_isSkipped() throws Exception {
    persistActiveHost("ns1.blah.ib-any.test");
    DomainResource nakedDomain =
        persistDeletedDomain("todelete.ib-any.test", DateTime.now(UTC).minusYears(1));
    DomainResource domainWithSubord =
        persistDomainAsDeleted(
            newDomainResource("blah.ib-any.test")
                .asBuilder()
                .setSubordinateHosts(ImmutableSet.of("ns1.blah.ib-any.test"))
                .build(),
            DateTime.now(UTC).minusYears(1));
    runMapreduce();
    assertThat(ofy().load().entity(domainWithSubord).now()).isNotNull();
    assertThat(ofy().load().entity(nakedDomain).now()).isNull();
  }

  @Test
  public void testFailure_registryAdminClientId_isRequiredForSoftDeletion() throws Exception {
    persistResource(
        newDomainResource("blah.ib-any.test")
            .asBuilder()
            .setCreationTimeForTest(DateTime.now(UTC).minusYears(1))
            .build());
    action.registryAdminClientId = null;
    try {
      runMapreduce();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessageThat().contains("Registry admin client ID must be configured");
      return;
    }
    assert_().fail("Expected IllegalStateException");
  }

  /**
   * Persists and returns a domain and a descendant history entry, billing event, and poll message,
   * along with the ForeignKeyIndex and EppResourceIndex.
   */
  private static Set<ImmutableObject> persistDomainAndDescendants(String fqdn) {
    DomainResource domain = persistDeletedDomain(fqdn, DELETION_TIME);
    HistoryEntry historyEntry = persistSimpleResource(
        new HistoryEntry.Builder()
            .setParent(domain)
            .setType(HistoryEntry.Type.DOMAIN_CREATE)
            .build());
    BillingEvent.OneTime billingEvent = persistSimpleResource(
        new BillingEvent.OneTime.Builder()
            .setParent(historyEntry)
            .setBillingTime(DELETION_TIME.plusYears(1))
            .setCost(Money.parse("USD 10"))
            .setPeriodYears(1)
            .setReason(Reason.CREATE)
            .setClientId("TheRegistrar")
            .setEventTime(DELETION_TIME)
            .setTargetId(fqdn)
            .build());
    PollMessage.OneTime pollMessage = persistSimpleResource(
        new PollMessage.OneTime.Builder()
            .setParent(historyEntry)
            .setEventTime(DELETION_TIME)
            .setClientId("TheRegistrar")
            .setMsg("Domain registered")
            .build());
    ForeignKeyIndex<DomainResource> fki =
        ForeignKeyIndex.load(DomainResource.class, fqdn, START_OF_TIME);
    EppResourceIndex eppIndex =
        ofy().load().entity(EppResourceIndex.create(Key.create(domain))).now();
    return ImmutableSet.<ImmutableObject>of(
        domain, historyEntry, billingEvent, pollMessage, fki, eppIndex);
  }

  private static Set<ImmutableObject> persistLotsOfDomains(String tld) {
    ImmutableSet.Builder<ImmutableObject> persistedObjects = new ImmutableSet.Builder<>();
    for (int i = 0; i < 20; i++) {
      persistedObjects.addAll(persistDomainAndDescendants(String.format("domain%d.%s", i, tld)));
    }
    return persistedObjects.build();
  }

  private static void assertNotDeleted(Iterable<ImmutableObject> entities) {
    for (ImmutableObject entity : entities) {
      assertThat(ofy().load().entity(entity).now()).isNotNull();
    }
  }

  private static void assertDeleted(Iterable<ImmutableObject> entities) {
    for (ImmutableObject entity : entities) {
      assertThat(ofy().load().entity(entity).now()).isNull();
    }
  }
}
