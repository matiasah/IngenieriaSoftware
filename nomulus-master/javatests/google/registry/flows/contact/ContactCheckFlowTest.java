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

package google.registry.flows.contact;

import static google.registry.model.eppoutput.CheckData.ContactCheck.create;
import static google.registry.testing.DatastoreHelper.persistActiveContact;
import static google.registry.testing.DatastoreHelper.persistDeletedContact;

import google.registry.flows.ResourceCheckFlowTestCase;
import google.registry.flows.exceptions.TooManyResourceChecksException;
import google.registry.model.contact.ContactResource;
import org.junit.Test;

/** Unit tests for {@link ContactCheckFlow}. */
public class ContactCheckFlowTest
    extends ResourceCheckFlowTestCase<ContactCheckFlow, ContactResource> {

  public ContactCheckFlowTest() {
    setEppInput("contact_check.xml");
  }

  @Test
  public void testNothingExists() throws Exception {
    // These ids come from the check xml.
    doCheckTest(
        create(true, "sh8013", null),
        create(true, "sah8013", null),
        create(true, "8013sah", null));
  }

  @Test
  public void testOneExists() throws Exception {
    persistActiveContact("sh8013");
    // These ids come from the check xml.
    doCheckTest(
        create(false, "sh8013", "In use"),
        create(true, "sah8013", null),
        create(true, "8013sah", null));
  }

  @Test
  public void testOneExistsButWasDeleted() throws Exception {
    persistDeletedContact("sh8013", clock.nowUtc().minusDays(1));
    // These ids come from the check xml.
    doCheckTest(
        create(true, "sh8013", null),
        create(true, "sah8013", null),
        create(true, "8013sah", null));
  }

  @Test
  public void testXmlMatches() throws Exception {
    persistActiveContact("sah8013");
    runFlowAssertResponse(readFile("contact_check_response.xml"));
  }

  @Test
  public void test50IdsAllowed() throws Exception {
    // Make sure we don't have a regression that reduces the number of allowed checks.
    setEppInput("contact_check_50.xml");
    runFlow();
  }

  @Test
  public void testTooManyIds() throws Exception {
    setEppInput("contact_check_51.xml");
    thrown.expect(TooManyResourceChecksException.class);
    runFlow();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-cont-check");
  }
}
