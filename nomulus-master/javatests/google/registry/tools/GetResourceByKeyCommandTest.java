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
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistDeletedContact;
import static google.registry.testing.DatastoreHelper.persistDeletedDomain;
import static google.registry.testing.DatastoreHelper.persistDeletedHost;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link GetResourceByKeyCommand}. */
public class GetResourceByKeyCommandTest extends CommandTestCase<GetResourceByKeyCommand> {

  DateTime now = DateTime.now(UTC);

  @Before
  public void initialize() {
    createTld("tld");
  }

  @Test
  public void testSuccess_domain() throws Exception {
    persistActiveDomain("example.tld");
    runCommand("agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("contact=Key<?>(ContactResource(\"3-ROID\"))");
  }

  @Test
  public void testSuccess_domain_expand() throws Exception {
    persistActiveDomain("example.tld");
    runCommand("agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw", "--expand");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("contactId=contact1234");
    assertNotInStdout("LiveRef");
  }

  @Test
  public void testSuccess_domain_multipleArguments() throws Exception {
    persistActiveDomain("example.tld");
    persistActiveDomain("example2.tld");
    runCommand(
        "agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw", "agR0ZXN0chULEgpEb21haW5CYXNlIgU0LVRMRAw");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("fullyQualifiedDomainName=example2.tld");
  }

  @Test
  public void testFailure_domain_oneDoesNotExist() throws Exception {
    persistActiveDomain("example.tld");
    thrown.expect(
        NullPointerException.class,
        "Could not load resource for key: Key<?>(DomainBase(\"4-TLD\"))");
    runCommand(
        "agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw", "agR0ZXN0chULEgpEb21haW5CYXNlIgU0LVRMRAw");
  }

  @Test
  public void testSuccess_deletedDomain() throws Exception {
    persistDeletedDomain("example.tld", now.minusDays(1));
    runCommand("agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("deletionTime=" + now.minusDays(1));
  }

  @Test
  public void testSuccess_contact() throws Exception {
    persistActiveContact("sh8013");
    runCommand("agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjItUk9JRAw");
    assertInStdout("contactId=sh8013");
  }

  @Test
  public void testSuccess_contact_expand() throws Exception {
    persistActiveContact("sh8013");
    runCommand("agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjItUk9JRAw", "--expand");
    assertInStdout("contactId=sh8013");
    assertNotInStdout("LiveRef");
  }

  @Test
  public void testSuccess_contact_multipleArguments() throws Exception {
    persistActiveContact("sh8013");
    persistActiveContact("jd1234");
    runCommand(
        "agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjItUk9JRAw",
        "agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjMtUk9JRAw");
    assertInStdout("contactId=sh8013");
    assertInStdout("contactId=jd1234");
  }

  @Test
  public void testFailure_contact_oneDoesNotExist() throws Exception {
    persistActiveContact("sh8013");
    thrown.expect(
        NullPointerException.class,
        "Could not load resource for key: Key<?>(ContactResource(\"3-ROID\"))");
    runCommand(
        "agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjItUk9JRAw",
        "agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjMtUk9JRAw");
  }

  @Test
  public void testSuccess_deletedContact() throws Exception {
    persistDeletedContact("sh8013", now.minusDays(1));
    runCommand("agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjItUk9JRAw");
    assertInStdout("contactId=sh8013");
    assertInStdout("deletionTime=" + now.minusDays(1));
  }

  @Test
  public void testSuccess_host() throws Exception {
    persistActiveHost("ns1.example.tld");
    runCommand("agR0ZXN0chgLEgxIb3N0UmVzb3VyY2UiBjItUk9JRAw");
    assertInStdout("fullyQualifiedHostName=ns1.example.tld");
  }

  @Test
  public void testSuccess_host_expand() throws Exception {
    persistActiveHost("ns1.example.tld");
    runCommand("agR0ZXN0chgLEgxIb3N0UmVzb3VyY2UiBjItUk9JRAw", "--expand");
    assertInStdout("fullyQualifiedHostName=ns1.example.tld");
    assertNotInStdout("LiveRef");
  }

  @Test
  public void testSuccess_host_multipleArguments() throws Exception {
    persistActiveHost("ns1.example.tld");
    persistActiveHost("ns2.example.tld");
    runCommand(
        "agR0ZXN0chgLEgxIb3N0UmVzb3VyY2UiBjItUk9JRAw",
        "agR0ZXN0chgLEgxIb3N0UmVzb3VyY2UiBjMtUk9JRAw");
    assertInStdout("fullyQualifiedHostName=ns1.example.tld");
    assertInStdout("fullyQualifiedHostName=ns2.example.tld");
  }

  @Test
  public void testFailure_host_oneDoesNotExist() throws Exception {
    persistActiveHost("ns1.example.tld");
    thrown.expect(
        NullPointerException.class,
        "Could not load resource for key: Key<?>(HostResource(\"3-ROID\"))");
    runCommand(
        "agR0ZXN0chgLEgxIb3N0UmVzb3VyY2UiBjItUk9JRAw",
        "agR0ZXN0chgLEgxIb3N0UmVzb3VyY2UiBjMtUk9JRAw");
  }

  @Test
  public void testSuccess_deletedHost() throws Exception {
    persistDeletedHost("ns1.example.tld", now.minusDays(1));
    runCommand("agR0ZXN0chgLEgxIb3N0UmVzb3VyY2UiBjItUk9JRAw");
    assertInStdout("fullyQualifiedHostName=ns1.example.tld");
    assertInStdout("deletionTime=" + now.minusDays(1));
  }

  @Test
  public void testSuccess_mixedTypes() throws Exception {
    persistActiveDomain("example.tld");
    persistActiveContact("sh8013");
    persistActiveHost("ns1.example.tld");
    runCommand(
        "agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw",
        "agR0ZXN0chsLEg9Db250YWN0UmVzb3VyY2UiBjQtUk9JRAw",
        "agR0ZXN0chgLEgxIb3N0UmVzb3VyY2UiBjUtUk9JRAw");
    assertInStdout("fullyQualifiedDomainName=example.tld");
    assertInStdout("contactId=sh8013");
    assertInStdout("fullyQualifiedHostName=ns1.example.tld");
  }

  @Test
  public void testFailure_keyDoesNotExist() throws Exception {
    thrown.expect(
        NullPointerException.class,
        "Could not load resource for key: Key<?>(DomainBase(\"2-TLD\"))");
    runCommand("agR0ZXN0chULEgpEb21haW5CYXNlIgUyLVRMRAw");
  }

  @Test
  public void testFailure_nonsenseKey() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Could not parse Reference");
    runCommand("agR0ZXN0chULEgpEb21haW5CYXN");
  }

  @Test
  public void testFailure_noParameters() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand();
  }
}
