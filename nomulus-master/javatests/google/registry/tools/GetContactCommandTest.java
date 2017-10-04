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

import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.newContactResource;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistDeletedContact;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link GetContactCommand}. */
public class GetContactCommandTest extends CommandTestCase<GetContactCommand> {

  DateTime now = DateTime.now(UTC);

  @Before
  public void initialize() {
    createTld("tld");
  }

  @Test
  public void testSuccess() throws Exception {
    persistActiveContact("sh8013");
    runCommand("sh8013");
    assertInStdout("contactId=sh8013");
    assertInStdout("Websafe key: agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjItUk9JRAw");
  }

  @Test
  public void testSuccess_expand() throws Exception {
    persistActiveContact("sh8013");
    runCommand("sh8013", "--expand");
    assertInStdout("contactId=sh8013");
    assertInStdout("Websafe key: agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjItUk9JRAw");
    assertNotInStdout("LiveRef");
  }

  @Test
  public void testSuccess_multipleArguments() throws Exception {
    persistActiveContact("sh8013");
    persistActiveContact("jd1234");
    runCommand("sh8013", "jd1234");
    assertInStdout("contactId=sh8013");
    assertInStdout("contactId=jd1234");
    assertInStdout("Websafe key: agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjItUk9JRAw");
    assertInStdout("Websafe key: agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjMtUk9JRAw");
  }

  @Test
  public void testSuccess_deletedContact() throws Exception {
    persistDeletedContact("sh8013", now.minusDays(1));
    runCommand("sh8013");
    assertInStdout("Contact 'sh8013' does not exist or is deleted");
  }

  @Test
  public void testSuccess_contactDoesNotExist() throws Exception {
    runCommand("nope");
    assertInStdout("Contact 'nope' does not exist or is deleted");
  }

  @Test
  public void testFailure_noContact() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand();
  }

  @Test
  public void testSuccess_contactDeletedInFuture() throws Exception {
    persistResource(
        newContactResource("sh8013").asBuilder().setDeletionTime(now.plusDays(1)).build());
    runCommand("sh8013", "--read_timestamp=" + now.plusMonths(1));
    assertInStdout("Contact 'sh8013' does not exist or is deleted");
  }
}
