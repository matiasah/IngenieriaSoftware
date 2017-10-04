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

package google.registry.tools.server;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainResource;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.FakeResponse;
import google.registry.testing.mapreduce.MapreduceTestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ResaveAllHistoryEntriesAction}. */
@RunWith(JUnit4.class)
public class ResaveAllHistoryEntriesActionTest
    extends MapreduceTestCase<ResaveAllHistoryEntriesAction> {

  private static final DatastoreService datastoreService =
      DatastoreServiceFactory.getDatastoreService();

  @Before
  public void init() {
    action = new ResaveAllHistoryEntriesAction();
    action.mrRunner = makeDefaultRunner();
    action.response = new FakeResponse();
  }

  private void runMapreduce() throws Exception {
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void test_mapreduceSuccessfullyResavesEntity() throws Exception {
    createTld("tld");
    DomainResource domain = persistActiveDomain("test.tld");
    ContactResource contact = persistActiveContact("humanBeing");
    Entity domainEntry =
        ofy().save().toEntity(new HistoryEntry.Builder().setParent(domain).build());
    Entity contactEntry =
        ofy().save().toEntity(new HistoryEntry.Builder().setParent(contact).build());

    // Set raw properties outside the Objectify schema, which will be deleted upon re-save.
    domainEntry.setProperty("clientId", "validId");
    contactEntry.setProperty("otherClientId", "anotherId");
    domainEntry.setProperty("propertyToBeDeleted", "123blah");
    contactEntry.setProperty("alsoShouldBeDeleted", "456nah");
    datastoreService.put(domainEntry);
    datastoreService.put(contactEntry);
    ofy().clearSessionCache();
    runMapreduce();

    Entity updatedDomainEntry = datastoreService.get(domainEntry.getKey());
    Entity updatedContactEntry = datastoreService.get(contactEntry.getKey());
    assertThat(updatedDomainEntry.getProperty("clientId")).isEqualTo("validId");
    assertThat(updatedDomainEntry.getProperty("propertyToBeDeleted")).isNull();
    assertThat(updatedContactEntry.getProperty("otherClientId")).isEqualTo("anotherId");
    assertThat(updatedContactEntry.getProperty("alsoShouldBeDeleted")).isNull();
  }
}
