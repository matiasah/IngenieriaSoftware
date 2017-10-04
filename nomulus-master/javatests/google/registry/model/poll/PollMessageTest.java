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

package google.registry.model.poll;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistResource;
import static java.nio.charset.StandardCharsets.UTF_8;

import google.registry.model.EntityTestCase;
import google.registry.model.domain.Period;
import google.registry.model.eppcommon.Trid;
import google.registry.model.reporting.HistoryEntry;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link PollMessage}. */
public class PollMessageTest extends EntityTestCase {

  HistoryEntry historyEntry;

  @Before
  public void setUp() throws Exception {
    createTld("foobar");
    historyEntry = persistResource(new HistoryEntry.Builder()
      .setParent(persistActiveDomain("foo.foobar"))
      .setType(HistoryEntry.Type.DOMAIN_CREATE)
      .setPeriod(Period.create(1, Period.Unit.YEARS))
      .setXmlBytes("<xml></xml>".getBytes(UTF_8))
      .setModificationTime(clock.nowUtc())
      .setClientId("foo")
      .setTrid(Trid.create("ABC-123", "server-trid"))
      .setBySuperuser(false)
      .setReason("reason")
      .setRequestedByRegistrar(false)
      .build());
  }

  @Test
  public void testPersistenceOneTime() throws Exception {
    PollMessage.OneTime pollMessage = persistResource(
        new PollMessage.OneTime.Builder()
            .setClientId("TheRegistrar")
            .setEventTime(clock.nowUtc())
            .setMsg("Test poll message")
            .setParent(historyEntry)
            .build());
    assertThat(ofy().load().entity(pollMessage).now()).isEqualTo(pollMessage);
  }

  @Test
  public void testPersistenceAutorenew() throws Exception {
    PollMessage.Autorenew pollMessage = persistResource(
        new PollMessage.Autorenew.Builder()
            .setClientId("TheRegistrar")
            .setEventTime(clock.nowUtc())
            .setMsg("Test poll message")
            .setParent(historyEntry)
            .setAutorenewEndTime(clock.nowUtc().plusDays(365))
            .setTargetId("foobar.foo")
            .build());
    assertThat(ofy().load().entity(pollMessage).now()).isEqualTo(pollMessage);
  }

  @Test
  public void testIndexingAutorenew() throws Exception {
    PollMessage.Autorenew pollMessage = persistResource(
        new PollMessage.Autorenew.Builder()
            .setClientId("TheRegistrar")
            .setEventTime(clock.nowUtc())
            .setMsg("Test poll message")
            .setParent(historyEntry)
            .setAutorenewEndTime(clock.nowUtc().plusDays(365))
            .setTargetId("foobar.foo")
            .build());
    verifyIndexing(pollMessage);
  }
}
