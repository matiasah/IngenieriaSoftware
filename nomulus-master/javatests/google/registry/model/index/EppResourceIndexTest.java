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

package google.registry.model.index;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.config.RegistryConfig.getEppResourceIndexBucketCount;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.googlecode.objectify.Key;
import google.registry.model.EntityTestCase;
import google.registry.model.contact.ContactResource;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link EppResourceIndex}. */
public class EppResourceIndexTest extends EntityTestCase  {

  ContactResource contact;

  @Before
  public void setUp() throws Exception {
    createTld("tld");
    // The DatastoreHelper here creates the EppResourceIndex for us.
    contact = persistActiveContact("abcd1357");
  }

  @Test
  public void testPersistence() throws Exception {
    EppResourceIndex loadedIndex = Iterables.getOnlyElement(getEppResourceIndexObjects());
    assertThat(ofy().load().key(loadedIndex.reference).now()).isEqualTo(contact);
  }

  @Test
  public void testIndexing() throws Exception {
    verifyIndexing(Iterables.getOnlyElement(getEppResourceIndexObjects()), "kind");
  }

  @Test
  public void testIdempotentOnUpdate() throws Exception {
    contact = persistResource(contact.asBuilder().setEmailAddress("abc@def.fake").build());
    EppResourceIndex loadedIndex = Iterables.getOnlyElement(getEppResourceIndexObjects());
    assertThat(ofy().load().key(loadedIndex.reference).now()).isEqualTo(contact);
  }

  /**
   * Returns all EppResourceIndex objects across all buckets.
   */
  private static ImmutableList<EppResourceIndex> getEppResourceIndexObjects() {
    ImmutableList.Builder<EppResourceIndex> indexEntities = new ImmutableList.Builder<>();
    for (int i = 0; i < getEppResourceIndexBucketCount(); i++) {
      indexEntities.addAll(ofy().load()
          .type(EppResourceIndex.class)
          .ancestor(Key.create(EppResourceIndexBucket.class, i + 1)));
    }
    return indexEntities.build();
  }
}
