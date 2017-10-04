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

package google.registry.model.ofy;

import static com.google.appengine.api.datastore.DatastoreServiceFactory.getDatastoreService;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.ofy.Ofy.getBaseEntityClassFromEntityOrKey;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newContactResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.fail;

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.taskqueue.TransientFailureException;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.Work;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.OnLoad;
import com.googlecode.objectify.annotation.OnSave;
import com.googlecode.objectify.annotation.Parent;
import google.registry.model.ImmutableObject;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppcommon.Trid;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.DatastoreHelper;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.util.SystemClock;
import java.util.ConcurrentModificationException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for our wrapper around Objectify. */
@RunWith(JUnit4.class)
public class OfyTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  /** An entity to use in save and delete tests. */
  private HistoryEntry someObject;

  @Before
  public void init() {
    createTld("tld");
    someObject = new HistoryEntry.Builder()
        .setClientId("client id")
        .setModificationTime(START_OF_TIME)
        .setParent(persistActiveContact("parentContact"))
        .setTrid(Trid.create("client", "server"))
        .setXmlBytes("<xml></xml>".getBytes(UTF_8))
        .build();
    // This can't be initialized earlier because namespaces need the AppEngineRule to work.
  }

  private void doBackupGroupRootTimestampInversionTest(VoidWork work) {
    DateTime groupTimestamp = ofy().load().key(someObject.getParent()).now()
        .getUpdateAutoTimestamp().getTimestamp();
    // Set the clock in Ofy to the same time as the backup group root's save time.
    Ofy ofy = new Ofy(new FakeClock(groupTimestamp));
    thrown.expect(
        TimestampInversionException.class,
        String.format(
            "Timestamp inversion between transaction time (%s) and entities rooted under:\n"
                + "{Key<?>(ContactResource(\"2-ROID\"))=%s}",
            groupTimestamp,
            groupTimestamp));
    ofy.transact(work);
  }

  @Test
  public void testBackupGroupRootTimestampsMustIncreaseOnSave() {
    doBackupGroupRootTimestampInversionTest(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(someObject);
      }});
  }

  @Test
  public void testBackupGroupRootTimestampsMustIncreaseOnDelete() {
    doBackupGroupRootTimestampInversionTest(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().entity(someObject);
      }});
  }

  @Test
  public void testSavingKeyTwice() {
    thrown.expect(IllegalArgumentException.class, "Multiple entries with same key");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(someObject);
        ofy().save().entity(someObject);
      }});
  }

  @Test
  public void testDeletingKeyTwice() {
    thrown.expect(IllegalArgumentException.class, "Multiple entries with same key");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().entity(someObject);
        ofy().delete().entity(someObject);
      }});
  }

  @Test
  public void testSaveDeleteKey() {
    thrown.expect(IllegalArgumentException.class, "Multiple entries with same key");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(someObject);
        ofy().delete().entity(someObject);
      }});
  }

  @Test
  public void testDeleteSaveKey() {
    thrown.expect(IllegalArgumentException.class, "Multiple entries with same key");
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().entity(someObject);
        ofy().save().entity(someObject);
      }});
  }

  @Test
  public void testSavingKeyTwiceInOneCall() {
    thrown.expect(IllegalArgumentException.class);
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entities(someObject, someObject);
      }});
  }

  /** Simple entity class with lifecycle callbacks. */
  @com.googlecode.objectify.annotation.Entity
  public static class LifecycleObject extends ImmutableObject {

    @Parent
    Key<?> parent = getCrossTldKey();

    @Id
    long id = 1;

    boolean onLoadCalled;
    boolean onSaveCalled;

    @OnLoad
    public void load() {
      onLoadCalled = true;
    }

    @OnSave
    public void save() {
      onSaveCalled = true;
    }
  }

  @Test
  public void testLifecycleCallbacks_loadFromEntity() {
    ofy().factory().register(LifecycleObject.class);
    LifecycleObject object = new LifecycleObject();
    Entity entity = ofy().save().toEntity(object);
    assertThat(object.onSaveCalled).isTrue();
    assertThat(ofy().load().<LifecycleObject>fromEntity(entity).onLoadCalled).isTrue();
  }

  @Test
  public void testLifecycleCallbacks_loadFromDatastore() {
    ofy().factory().register(LifecycleObject.class);
    final LifecycleObject object = new LifecycleObject();
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().save().entity(object).now();
      }});
    assertThat(object.onSaveCalled).isTrue();
    ofy().clearSessionCache();
    assertThat(ofy().load().entity(object).now().onLoadCalled).isTrue();
  }

  /** Avoid regressions of b/21309102 where transaction time did not change on each retry. */
  @Test
  public void testTransact_getsNewTimestampOnEachTry() {
    ofy().transact(new VoidWork() {

      DateTime firstAttemptTime;

      @Override
      public void vrun() {
        if (firstAttemptTime == null) {
          // Sleep a bit to ensure that the next attempt is at a new millisecond.
          firstAttemptTime = ofy().getTransactionTime();
          sleepUninterruptibly(10, MILLISECONDS);
          throw new ConcurrentModificationException();
        }
        assertThat(ofy().getTransactionTime()).isGreaterThan(firstAttemptTime);
      }});
  }

  @Test
  public void testTransact_transientFailureException_retries() {
    assertThat(ofy().transact(new Work<Integer>() {

      int count = 0;

      @Override
      public Integer run() {
        count++;
        if (count == 3) {
          return count;
        }
        throw new TransientFailureException("");
      }})).isEqualTo(3);
  }

  @Test
  public void testTransact_datastoreTimeoutException_noManifest_retries() {
    assertThat(ofy().transact(new Work<Integer>() {

      int count = 0;

      @Override
      public Integer run() {
        // We don't write anything in this transaction, so there is no commit log manifest.
        // Therefore it's always safe to retry since nothing got written.
        count++;
        if (count == 3) {
          return count;
        }
        throw new DatastoreTimeoutException("");
      }})).isEqualTo(3);
  }

  @Test
  public void testTransact_datastoreTimeoutException_manifestNotWrittenToDatastore_retries() {
    assertThat(ofy().transact(new Work<Integer>() {

      int count = 0;

      @Override
      public Integer run() {
        // There will be something in the manifest now, but it won't be committed if we throw.
        ofy().save().entity(someObject);
        count++;
        if (count == 3) {
          return count;
        }
        throw new DatastoreTimeoutException("");
      }})).isEqualTo(3);
  }

  @Test
  public void testTransact_datastoreTimeoutException_manifestWrittenToDatastore_returnsSuccess() {
    // A work unit that throws if it is ever retried.
    VoidWork work = new VoidWork() {
      boolean firstCallToVrun = true;

      @Override
      public void vrun() {
        if (firstCallToVrun) {
          firstCallToVrun = false;
          ofy().save().entity(someObject);
          return;
        }
        fail("Shouldn't have retried.");
      }};
    // A commit logged work that throws on the first attempt to get its result.
    CommitLoggedWork<Void> commitLoggedWork = new CommitLoggedWork<Void>(work, new SystemClock()) {
      boolean firstCallToGetResult = true;

      @Override
      public Void getResult() {
        if (firstCallToGetResult) {
          firstCallToGetResult = false;
          throw new DatastoreTimeoutException("");
        }
        return null;
      }};
    // Despite the DatastoreTimeoutException in the first call to getResult(), this should succeed
    // without retrying. If a retry is triggered, the test should fail due to the call to fail().
    ofy().transactCommitLoggedWork(commitLoggedWork);
  }

  void doReadOnlyRetryTest(final RuntimeException e) {
    assertThat(ofy().transactNewReadOnly(new Work<Integer>() {

      int count = 0;

      @Override
      public Integer run() {
        count++;
        if (count == 3) {
          return count;
        }
        throw e;
      }})).isEqualTo(3);
  }

  @Test
  public void testTransactNewReadOnly_transientFailureException_retries() {
    doReadOnlyRetryTest(new TransientFailureException(""));
  }

  @Test
  public void testTransactNewReadOnly_datastoreTimeoutException_retries() {
    doReadOnlyRetryTest(new DatastoreTimeoutException(""));
  }

  @Test
  public void testTransactNewReadOnly_datastoreFailureException_retries() {
    doReadOnlyRetryTest(new DatastoreFailureException(""));
  }

  @Test
  public void test_getBaseEntityClassFromEntityOrKey_regularEntity() {
    ContactResource contact = newContactResource("testcontact");
    assertThat(getBaseEntityClassFromEntityOrKey(contact)).isEqualTo(ContactResource.class);
    assertThat(getBaseEntityClassFromEntityOrKey(Key.create(contact)))
        .isEqualTo(ContactResource.class);
  }

  @Test
  public void test_getBaseEntityClassFromEntityOrKey_subclassEntity() {
    DomainResource domain = DatastoreHelper.newDomainResource("test.tld");
    assertThat(getBaseEntityClassFromEntityOrKey(domain)).isEqualTo(DomainBase.class);
    assertThat(getBaseEntityClassFromEntityOrKey(Key.create(domain)))
        .isEqualTo(DomainBase.class);
  }

  @Test
  public void test_getBaseEntityClassFromEntityOrKey_unregisteredEntity() {
    thrown.expect(IllegalStateException.class, "SystemClock");
    getBaseEntityClassFromEntityOrKey(new SystemClock());
  }

  @Test
  public void test_getBaseEntityClassFromEntityOrKey_unregisteredEntityKey() {
    thrown.expect(IllegalArgumentException.class, "UnknownKind");
    getBaseEntityClassFromEntityOrKey(Key.create(
        com.google.appengine.api.datastore.KeyFactory.createKey("UnknownKind", 1)));
  }

  @Test
  public void test_doWithFreshSessionCache() {
    ofy().saveWithoutBackup().entity(someObject).now();
    final HistoryEntry modifiedObject =
        someObject.asBuilder().setModificationTime(END_OF_TIME).build();
    // Mutate the saved objected, bypassing the Objectify session cache.
    getDatastoreService().put(ofy().saveWithoutBackup().toEntity(modifiedObject));
    // Normal loading should come from the session cache and shouldn't reflect the mutation.
    assertThat(ofy().load().entity(someObject).now()).isEqualTo(someObject);
    // Loading inside doWithFreshSessionCache() should reflect the mutation.
    boolean ran = ofy().doWithFreshSessionCache(new Work<Boolean>() {
      @Override
      public Boolean run() {
        assertThat(ofy().load().entity(someObject).now()).isEqualTo(modifiedObject);
        return true;
      }});
    assertThat(ran).isTrue();
    // Test the normal loading again to verify that we've restored the original session unchanged.
    assertThat(ofy().load().entity(someObject).now()).isEqualTo(someObject);
  }
}
