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

package google.registry.flows;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.model.tmch.ClaimsListShardTest.createTestClaimsListShard;
import static google.registry.testing.LogsSubject.assertAboutLogs;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.TestLogHandler;
import com.googlecode.objectify.Key;
import google.registry.flows.FlowUtils.NotLoggedInException;
import google.registry.model.EppResource;
import google.registry.model.EppResourceUtils;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.launch.ApplicationIdTargetExtension;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput.ResourceCommandWrapper;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.index.EppResourceIndex;
import google.registry.model.index.EppResourceIndexBucket;
import google.registry.model.tmch.ClaimsListShard.ClaimsListRevision;
import google.registry.model.tmch.ClaimsListShard.ClaimsListSingleton;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.util.TypeUtils.TypeInstantiator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Test;

/**
 * Base class for resource flow unit tests.
 *
 * @param <F> the flow type
 * @param <R> the resource type
 */
public abstract class ResourceFlowTestCase<F extends Flow, R extends EppResource>
    extends FlowTestCase<F> {

  private final TestLogHandler logHandler = new TestLogHandler();

  @Before
  public void beforeResourceFlowTestCase() {
    // Attach TestLogHandler to the root logger so it has access to all log messages.
    // Note that in theory for assertIcannReportingActivityFieldLogged() below it would suffice to
    // attach it only to the FlowRunner logger, but for some reason this doesn't work for all flows.
    Logger.getLogger("").addHandler(logHandler);
  }

  protected R reloadResourceByForeignKey(DateTime now) throws Exception {
    // Force the session to be cleared so that when we read it back, we read from Datastore and not
    // from the transaction's session cache.
    ofy().clearSessionCache();
    return loadByForeignKey(getResourceClass(), getUniqueIdFromCommand(), now);
  }

  protected R reloadResourceByForeignKey() throws Exception {
    return reloadResourceByForeignKey(clock.nowUtc());
  }

  protected DomainApplication reloadDomainApplication() throws Exception {
    ofy().clearSessionCache();
    return EppResourceUtils.loadDomainApplication(getUniqueIdFromCommand(), clock.nowUtc());
  }

  protected <T extends EppResource> T reloadResourceAndCloneAtTime(T resource, DateTime now) {
    // Force the session to be cleared.
    ofy().clearSessionCache();
    @SuppressWarnings("unchecked")
    T refreshedResource = (T) ofy().load().entity(resource).now().cloneProjectedAtTime(now);
    return refreshedResource;
  }

  protected ResourceCommand.SingleResourceCommand getResourceCommand() throws Exception {
    return (ResourceCommand.SingleResourceCommand)
        ((ResourceCommandWrapper) eppLoader.getEpp().getCommandWrapper().getCommand())
            .getResourceCommand();
  }

  /**
   * We have to duplicate the logic from SingleResourceFlow.getTargetId() here. However, given the
   * choice between making that method public, and duplicating its logic, it seems better to muddy
   * the test code rather than the production code.
   */
  protected String getUniqueIdFromCommand() throws Exception {
    ApplicationIdTargetExtension extension =
        eppLoader.getEpp().getSingleExtension(ApplicationIdTargetExtension.class);
    return extension == null ? getResourceCommand().getTargetId() : extension.getApplicationId();
  }

  protected Class<R> getResourceClass() {
    return new TypeInstantiator<R>(getClass()){}.getExactType();
  }

  /**
   * Persists a testing claims list to Datastore that contains a single shard.
   */
  protected void persistClaimsList(ImmutableMap<String, String> labelsToKeys) {
    ClaimsListSingleton singleton = new ClaimsListSingleton();
    Key<ClaimsListRevision> revision = ClaimsListRevision.createKey(singleton);
    singleton.setActiveRevision(revision);
    ofy().saveWithoutBackup().entity(singleton).now();
    if (!labelsToKeys.isEmpty()) {
      ofy().saveWithoutBackup()
          .entity(createTestClaimsListShard(clock.nowUtc(), labelsToKeys, revision))
          .now();
    }
  }

  @Test
  public void testRequiresLogin() throws Exception {
    sessionMetadata.setClientId(null);
    thrown.expect(NotLoggedInException.class);
    runFlow();
  }

  /**
   * Confirms that an EppResourceIndex entity exists in Datastore for a given resource.
   */
  protected static <T extends EppResource> void assertEppResourceIndexEntityFor(final T resource) {
    ImmutableList<EppResourceIndex> indices = FluentIterable
        .from(ofy().load()
            .type(EppResourceIndex.class)
            .filter("kind", Key.getKind(resource.getClass())))
        .filter(new Predicate<EppResourceIndex>() {
            @Override
            public boolean apply(EppResourceIndex index) {
              return Key.create(resource).equals(index.getKey())
                  && ofy().load().key(index.getKey()).now().equals(resource);
            }})
        .toList();
    assertThat(indices).hasSize(1);
    assertThat(indices.get(0).getBucket())
        .isEqualTo(EppResourceIndexBucket.getBucketKey(Key.create(resource)));
  }

  /** Asserts the presence of a single enqueued async contact or host deletion */
  protected <T extends EppResource> void assertAsyncDeletionTaskEnqueued(
      T resource, String requestingClientId, Trid trid, boolean isSuperuser) throws Exception {
    assertTasksEnqueued(
        "async-delete-pull",
        new TaskMatcher()
            .etaDelta(Duration.standardSeconds(75), Duration.standardSeconds(105)) // expected: 90
            .param("resourceKey", Key.create(resource).getString())
            .param("requestingClientId", requestingClientId)
            .param("clientTransactionId", trid.getClientTransactionId())
            .param("serverTransactionId", trid.getServerTransactionId())
            .param("isSuperuser", Boolean.toString(isSuperuser))
            .param("requestedTime", clock.nowUtc().toString()));
  }


  protected void assertClientIdFieldLogged(String clientId) {
    assertAboutLogs().that(logHandler)
        .hasLogAtLevelWithMessage(Level.INFO, "FLOW-LOG-SIGNATURE-METADATA")
        .which()
        .contains("\"clientId\":" + JSONValue.toJSONString(clientId));
  }

  protected void assertTldsFieldLogged(String... tlds) {
    assertAboutLogs().that(logHandler)
        .hasLogAtLevelWithMessage(Level.INFO, "FLOW-LOG-SIGNATURE-METADATA")
        .which()
        .contains("\"tlds\":" + JSONValue.toJSONString(ImmutableList.copyOf(tlds)));
  }

  protected void assertIcannReportingActivityFieldLogged(String fieldName) {
    assertAboutLogs().that(logHandler)
        .hasLogAtLevelWithMessage(Level.INFO, "FLOW-LOG-SIGNATURE-METADATA")
        .which()
        .contains("\"icannActivityReportField\":" + JSONValue.toJSONString(fieldName));
  }
}
