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

import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.persistActiveContact;

import com.google.appengine.api.datastore.KeyFactory;
import com.google.common.base.Function;
import com.googlecode.objectify.Key;
import google.registry.model.ImmutableObject;
import google.registry.model.contact.ContactResource;
import google.registry.model.ofy.CommitLogManifest;
import google.registry.model.ofy.CommitLogMutation;
import org.junit.Test;

/** Unit tests for {@link ResaveEntitiesCommand}. */
public class ResaveEntitiesCommandTest extends CommandTestCase<ResaveEntitiesCommand> {

  @Test
  public void testSuccess_createsCommitLogs() throws Exception {
    ContactResource contact1 = persistActiveContact("contact1");
    ContactResource contact2 = persistActiveContact("contact2");
    deleteEntitiesOfTypes(CommitLogManifest.class, CommitLogMutation.class);
    assertThat(ofy().load().type(CommitLogManifest.class).keys()).isEmpty();
    assertThat(ofy().load().type(CommitLogMutation.class).keys()).isEmpty();
    runCommandForced(
        KeyFactory.keyToString(Key.create(contact1).getRaw()),
        KeyFactory.keyToString(Key.create(contact2).getRaw()));

    assertThat(ofy().load().type(CommitLogManifest.class).keys()).hasSize(1);
    Iterable<ImmutableObject> savedEntities =
        transform(
            ofy().load().type(CommitLogMutation.class).list(),
            new Function<CommitLogMutation, ImmutableObject>() {
              @Override
              public ImmutableObject apply(CommitLogMutation mutation) {
                return ofy().load().fromEntity(mutation.getEntity());
              }
            });
    // Reload the contacts before asserting, since their update times will have changed.
    ofy().clearSessionCache();
    assertThat(savedEntities)
        .containsExactlyElementsIn(ofy().load().entities(contact1, contact2).values());
  }

  @SafeVarargs
  private static void deleteEntitiesOfTypes(Class<? extends ImmutableObject>... types) {
    for (Class<? extends ImmutableObject> type : types) {
      ofy().deleteWithoutBackup().keys(ofy().load().type(type).keys()).now();
    }
  }
}
