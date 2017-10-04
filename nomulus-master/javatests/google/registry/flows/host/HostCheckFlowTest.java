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

package google.registry.flows.host;

import static google.registry.model.eppoutput.CheckData.HostCheck.create;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistDeletedHost;

import google.registry.flows.ResourceCheckFlowTestCase;
import google.registry.flows.exceptions.TooManyResourceChecksException;
import google.registry.model.host.HostResource;
import org.junit.Test;

/** Unit tests for {@link HostCheckFlow}. */
public class HostCheckFlowTest extends ResourceCheckFlowTestCase<HostCheckFlow, HostResource> {

  public HostCheckFlowTest() {
    setEppInput("host_check.xml");
  }

  @Test
  public void testNothingExists() throws Exception {
    // These ids come from the check xml.
    doCheckTest(
        create(true, "ns1.example.tld", null),
        create(true, "ns2.example.tld", null),
        create(true, "ns3.example.tld", null));
  }

  @Test
  public void testOneExists() throws Exception {
    persistActiveHost("ns1.example.tld");
    // These ids come from the check xml.
    doCheckTest(
        create(false, "ns1.example.tld", "In use"),
        create(true, "ns2.example.tld", null),
        create(true, "ns3.example.tld", null));
  }

  @Test
  public void testOneExistsButWasDeleted() throws Exception {
    persistDeletedHost("ns1.example.tld", clock.nowUtc().minusDays(1));
    // These ids come from the check xml.
    doCheckTest(
        create(true, "ns1.example.tld", null),
        create(true, "ns2.example.tld", null),
        create(true, "ns3.example.tld", null));
  }

  @Test
  public void testXmlMatches() throws Exception {
    persistActiveHost("ns2.example.tld");
    runFlowAssertResponse(readFile("host_check_response.xml"));
  }

  @Test
  public void test50IdsAllowed() throws Exception {
    // Make sure we don't have a regression that reduces the number of allowed checks.
    setEppInput("host_check_50.xml");
    runFlow();
  }

  @Test
  public void testTooManyIds() throws Exception {
    setEppInput("host_check_51.xml");
    thrown.expect(TooManyResourceChecksException.class);
    runFlow();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-host-check");
  }
}
