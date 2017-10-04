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

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistDeletedDomain;
import static org.joda.time.DateTimeZone.UTC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableSet;
import google.registry.dns.DnsQueue;
import google.registry.testing.FakeResponse;
import google.registry.testing.InjectRule;
import google.registry.testing.mapreduce.MapreduceTestCase;
import google.registry.tools.server.RefreshDnsForAllDomainsAction.RefreshDnsForAllDomainsActionMapper;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RefreshDnsForAllDomainsAction}. */
@RunWith(JUnit4.class)
public class RefreshDnsForAllDomainsActionTest
    extends MapreduceTestCase<RefreshDnsForAllDomainsAction> {

  @Rule public final InjectRule inject = new InjectRule();

  private final DnsQueue dnsQueue = mock(DnsQueue.class);

  @Before
  public void init() {
    inject.setStaticField(RefreshDnsForAllDomainsActionMapper.class, "dnsQueue", dnsQueue);

    action = new RefreshDnsForAllDomainsAction();
    action.mrRunner = makeDefaultRunner();
    action.response = new FakeResponse();
  }

  private void runMapreduce() throws Exception {
    action.run();
    executeTasksUntilEmpty("mapreduce");
  }

  @Test
  public void test_runAction_successfullyEnqueuesDnsRefreshes() throws Exception {
    createTld("bar");
    persistActiveDomain("foo.bar");
    persistActiveDomain("low.bar");
    action.tlds = ImmutableSet.of("bar");
    runMapreduce();
    verify(dnsQueue).addDomainRefreshTask("foo.bar");
    verify(dnsQueue).addDomainRefreshTask("low.bar");
  }

  @Test
  public void test_runAction_doesntRefreshDeletedDomain() throws Exception {
    createTld("bar");
    persistActiveDomain("foo.bar");
    persistDeletedDomain("deleted.bar", DateTime.now(UTC).minusYears(1));
    action.tlds = ImmutableSet.of("bar");
    runMapreduce();
    verify(dnsQueue).addDomainRefreshTask("foo.bar");
    verify(dnsQueue, never()).addDomainRefreshTask("deleted.bar");
  }

  @Test
  public void test_runAction_ignoresDomainsOnOtherTlds() throws Exception {
    createTlds("bar", "baz");
    persistActiveDomain("foo.bar");
    persistActiveDomain("low.bar");
    persistActiveDomain("ignore.baz");
    action.tlds = ImmutableSet.of("bar");
    runMapreduce();
    verify(dnsQueue).addDomainRefreshTask("foo.bar");
    verify(dnsQueue).addDomainRefreshTask("low.bar");
    verify(dnsQueue, never()).addDomainRefreshTask("ignore.baz");
  }
}
