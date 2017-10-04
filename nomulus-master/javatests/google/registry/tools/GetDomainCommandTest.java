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
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistDeletedDomain;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link GetDomainCommand}. */
public class GetDomainCommandTest extends CommandTestCase<GetDomainCommand> {

  DateTime now = DateTime.now(UTC);

  @Before
  public void initialize() {
    createTld("tld");
  }

  @Test
  public void testSuccess() throws Exception {
    persistActiveDomain("example.tld");
    runCommand("example.tld");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("contact=Key<?>(ContactResource(\"3-ROID\"))");
    assertInStdout("Websafe key: agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw");
  }

  @Test
  public void testSuccess_expand() throws Exception {
    persistActiveDomain("example.tld");
    runCommand("example.tld", "--expand");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("contactId=contact1234");
    assertInStdout("Websafe key: agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw");
    assertNotInStdout("LiveRef");
  }

  @Test
  public void testSuccess_multipleArguments() throws Exception {
    persistActiveDomain("example.tld");
    persistActiveDomain("example2.tld");
    runCommand("example.tld", "example2.tld");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("fullyQualifiedDomainName=example2.tld");
    assertInStdout("Websafe key: agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw");
    assertInStdout("Websafe key: agR0ZXN0chULEgpEb21haW5CYXNlIgU0LVRMRAw");
  }

  @Test
  public void testSuccess_domainDeletedInFuture() throws Exception {
    persistResource(newDomainResource("example.tld").asBuilder()
        .setDeletionTime(now.plusDays(1)).build());
    runCommand("example.tld", "--read_timestamp=" + now.plusMonths(1));
    assertInStdout("Domain 'example.tld' does not exist or is deleted");
  }

  @Test
  public void testSuccess_deletedDomain() throws Exception {
    persistDeletedDomain("example.tld", now.minusDays(1));
    runCommand("example.tld");
    assertInStdout("Domain 'example.tld' does not exist or is deleted");
  }

  @Test
  public void testSuccess_domainDoesNotExist() throws Exception {
    runCommand("something.tld");
    assertInStdout("Domain 'something.tld' does not exist or is deleted");
  }

  @Test
  public void testFailure_tldDoesNotExist() throws Exception {
    runCommand("example.foo");
    assertInStdout("Domain 'example.foo' does not exist or is deleted");
  }

  @Test
  public void testFailure_noDomainName() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand();
  }

  @Test
  public void testSuccess_oneDomainDoesNotExist() throws Exception {
    persistActiveDomain("example.tld");
    createTld("com");
    runCommand("example.com", "example.tld");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("Domain 'example.com' does not exist or is deleted");
  }
}
