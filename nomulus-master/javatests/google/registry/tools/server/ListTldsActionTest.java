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

import com.google.common.base.Optional;
import google.registry.testing.FakeClock;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit tests for {@link ListTldsAction}.
 */
@RunWith(JUnit4.class)
public class ListTldsActionTest extends ListActionTestCase {

  ListTldsAction action;

  @Before
  public void init() throws Exception {
    createTld("xn--q9jyb4c");
    action = new ListTldsAction();
    action.clock = new FakeClock(DateTime.parse("2000-01-01TZ"));
  }

  @Test
  public void testRun_noParameters() throws Exception {
    testRunSuccess(action, null, null, null, "xn--q9jyb4c");
  }

  @Test
  public void testRun_withParameters() throws Exception {
    testRunSuccess(
        action,
        Optional.of("tldType"),
        null,
        null,
        "TLD          tldType",
        "-----------  -------",
        "xn--q9jyb4c  REAL   ");
  }

  @Test
  public void testRun_withWildcard() throws Exception {
    testRunSuccess(
        action,
        Optional.of("*"),
        null,
        null,
        "^TLD          .*tldType",
        "^-----------  .*-------",
        "^xn--q9jyb4c  .*REAL   ");
  }

  @Test
  public void testRun_withBadField_returnsError() throws Exception {
    testRunError(
        action,
        Optional.of("badfield"),
        null,
        null,
        "^Field 'badfield' not found - recognized fields are:");
  }
}
