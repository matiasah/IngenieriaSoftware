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

import static com.google.appengine.api.taskqueue.QueueFactory.getQueue;
import static google.registry.flows.async.AsyncFlowEnqueuer.QUEUE_ASYNC_DELETE;
import static google.registry.flows.async.AsyncFlowEnqueuer.QUEUE_ASYNC_HOST_RENAME;
import static google.registry.flows.async.AsyncFlowMetrics.OperationType.DNS_REFRESH;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newDomainApplication;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.newHostResource;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistDeletedHost;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.TaskQueueHelper.assertDnsTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertNoDnsTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertNoTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertTasksEnqueued;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.time.Duration.millis;
import static org.joda.time.Duration.standardHours;
import static org.joda.time.Duration.standardSeconds;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.batch.RefreshDnsOnHostRenameAction.RefreshDnsOnHostRenameReducer;
import google.registry.flows.async.AsyncFlowEnqueuer;
import google.registry.flows.async.AsyncFlowMetrics;
import google.registry.flows.async.AsyncFlowMetrics.OperationResult;
import google.registry.model.host.HostResource;
import google.registry.testing.ExceptionRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.FakeSleeper;
import google.registry.testing.InjectRule;
import google.registry.testing.TaskQueueHelper.TaskMatcher;
import google.registry.testing.mapreduce.MapreduceTestCase;
import google.registry.util.Retrier;
import google.registry.util.Sleeper;
import google.registry.util.SystemSleeper;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RefreshDnsOnHostRenameAction}. */
@RunWith(JUnit4.class)
public class RefreshDnsOnHostRenameActionTest
    extends MapreduceTestCase<RefreshDnsOnHostRenameAction> {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Rule
  public InjectRule inject = new InjectRule();

  private AsyncFlowEnqueuer enqueuer;
  private final FakeClock clock = new FakeClock(DateTime.parse("2015-01-15T11:22:33Z"));

  @Before
  public void setup() throws Exception {
    createTld("tld");
    enqueuer =
        new AsyncFlowEnqueuer(
            getQueue(QUEUE_ASYNC_DELETE),
            getQueue(QUEUE_ASYNC_HOST_RENAME),
            Duration.ZERO,
            new Retrier(new FakeSleeper(clock), 1));
    AsyncFlowMetrics asyncFlowMetricsMock = mock(AsyncFlowMetrics.class);
    action = new RefreshDnsOnHostRenameAction();
    action.asyncFlowMetrics = asyncFlowMetricsMock;
    inject.setStaticField(
        RefreshDnsOnHostRenameReducer.class, "asyncFlowMetrics", asyncFlowMetricsMock);
    action.clock = clock;
    action.mrRunner = makeDefaultRunner();
    action.pullQueue = getQueue(QUEUE_ASYNC_HOST_RENAME);
    action.response = new FakeResponse();
    action.retrier = new Retrier(new FakeSleeper(clock), 1);
  }

  private void runMapreduce() throws Exception {
    clock.advanceOneMilli();
    // Use hard sleeps to ensure that the tasks are enqueued properly and will be leased.
    Sleeper sleeper = new SystemSleeper();
    sleeper.sleep(millis(50));
    action.run();
    sleeper.sleep(millis(50));
    executeTasksUntilEmpty("mapreduce", clock);
    sleeper.sleep(millis(50));
    clock.advanceBy(standardSeconds(5));
    ofy().clearSessionCache();
  }

  @Test
  public void testSuccess_dnsUpdateEnqueued() throws Exception {
    HostResource host = persistActiveHost("ns1.example.tld");
    persistResource(
        newDomainApplication("notadomain.tld")
            .asBuilder()
            .setNameservers(ImmutableSet.of(Key.create(host)))
            .build());
    persistResource(newDomainResource("example.tld", host));
    persistResource(newDomainResource("otherexample.tld", host));
    persistResource(newDomainResource("untouched.tld", persistActiveHost("ns2.example.tld")));
    DateTime timeEnqueued = clock.nowUtc();
    enqueuer.enqueueAsyncDnsRefresh(host, timeEnqueued);
    runMapreduce();
    assertDnsTasksEnqueued("example.tld", "otherexample.tld");
    assertNoTasksEnqueued(QUEUE_ASYNC_HOST_RENAME);
    verify(action.asyncFlowMetrics).recordDnsRefreshBatchSize(1L);
    verify(action.asyncFlowMetrics)
        .recordAsyncFlowResult(DNS_REFRESH, OperationResult.SUCCESS, timeEnqueued);
    verifyNoMoreInteractions(action.asyncFlowMetrics);
  }

  @Test
  public void testSuccess_multipleHostsProcessedInBatch() throws Exception {
    HostResource host1 = persistActiveHost("ns1.example.tld");
    HostResource host2 = persistActiveHost("ns2.example.tld");
    HostResource host3 = persistActiveHost("ns3.example.tld");
    persistResource(newDomainResource("example1.tld", host1));
    persistResource(newDomainResource("example2.tld", host2));
    persistResource(newDomainResource("example3.tld", host3));
    DateTime timeEnqueued = clock.nowUtc();
    DateTime laterTimeEnqueued = timeEnqueued.plus(standardSeconds(10));
    enqueuer.enqueueAsyncDnsRefresh(host1, timeEnqueued);
    enqueuer.enqueueAsyncDnsRefresh(host2, timeEnqueued);
    enqueuer.enqueueAsyncDnsRefresh(host3, laterTimeEnqueued);
    runMapreduce();
    assertDnsTasksEnqueued("example1.tld", "example2.tld", "example3.tld");
    assertNoTasksEnqueued(QUEUE_ASYNC_HOST_RENAME);
    verify(action.asyncFlowMetrics).recordDnsRefreshBatchSize(3L);
    verify(action.asyncFlowMetrics, times(2))
        .recordAsyncFlowResult(DNS_REFRESH, OperationResult.SUCCESS, timeEnqueued);
    verify(action.asyncFlowMetrics)
        .recordAsyncFlowResult(DNS_REFRESH, OperationResult.SUCCESS, laterTimeEnqueued);
    verifyNoMoreInteractions(action.asyncFlowMetrics);
  }

  @Test
  public void testSuccess_deletedHost_doesntTriggerDnsRefresh() throws Exception {
    HostResource host = persistDeletedHost("ns11.fakesss.tld", clock.nowUtc().minusDays(4));
    persistResource(newDomainResource("example1.tld", host));
    DateTime timeEnqueued = clock.nowUtc();
    enqueuer.enqueueAsyncDnsRefresh(host, timeEnqueued);
    runMapreduce();
    assertNoDnsTasksEnqueued();
    assertNoTasksEnqueued(QUEUE_ASYNC_HOST_RENAME);
    verify(action.asyncFlowMetrics).recordDnsRefreshBatchSize(1L);
    verify(action.asyncFlowMetrics)
        .recordAsyncFlowResult(DNS_REFRESH, OperationResult.STALE, timeEnqueued);
    verifyNoMoreInteractions(action.asyncFlowMetrics);
  }

  @Test
  public void testSuccess_noDnsTasksForDeletedDomain() throws Exception {
    HostResource renamedHost = persistActiveHost("ns1.example.tld");
    persistResource(
        newDomainResource("example.tld", renamedHost)
            .asBuilder()
            .setDeletionTime(START_OF_TIME)
            .build());
    enqueuer.enqueueAsyncDnsRefresh(renamedHost, clock.nowUtc());
    runMapreduce();
    assertNoDnsTasksEnqueued();
    assertNoTasksEnqueued(QUEUE_ASYNC_HOST_RENAME);
  }

  @Test
  public void testRun_hostDoesntExist_delaysTask() throws Exception {
    HostResource host = newHostResource("ns1.example.tld");
    enqueuer.enqueueAsyncDnsRefresh(host, clock.nowUtc());
    runMapreduce();
    assertNoDnsTasksEnqueued();
    assertTasksEnqueued(
        QUEUE_ASYNC_HOST_RENAME,
        new TaskMatcher()
            .etaDelta(standardHours(23), standardHours(25))
            .param("hostKey", Key.create(host).getString()));
  }
}
