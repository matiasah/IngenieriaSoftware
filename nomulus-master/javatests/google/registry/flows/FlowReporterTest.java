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

import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.truth.Truth.assertThat;
import static google.registry.testing.TestDataHelper.loadFileWithSubstitutions;
import static google.registry.testing.TestLogHandlerUtils.findFirstLogMessageByPrefix;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.testing.TestLogHandler;
import google.registry.flows.annotations.ReportingSpec;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppoutput.EppOutput.ResponseOrGreeting;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.reporting.IcannReportingTypes.ActivityReportField;
import google.registry.testing.ShardableTestCase;
import java.util.Map;
import java.util.logging.Logger;
import org.json.simple.JSONValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link FlowReporter}. */
@RunWith(JUnit4.class)
public class FlowReporterTest extends ShardableTestCase {

  static class TestCommandFlow implements Flow {
    @Override
    public ResponseOrGreeting run() throws EppException {
      return mock(EppResponse.class);
    }
  }

  @ReportingSpec(ActivityReportField.CONTACT_CHECK)
  static class TestReportingSpecCommandFlow implements Flow {
    @Override
    public ResponseOrGreeting run() throws EppException {
      return mock(EppResponse.class);
    }
  }

  private final FlowReporter flowReporter = new FlowReporter();
  private final TestLogHandler handler = new TestLogHandler();

  @Before
  public void before() {
    Logger.getLogger(FlowReporter.class.getCanonicalName()).addHandler(handler);
    flowReporter.trid = Trid.create("client-123", "server-456");
    flowReporter.clientId = "TheRegistrar";
    flowReporter.inputXmlBytes = "<xml/>".getBytes(UTF_8);
    flowReporter.flowClass = TestCommandFlow.class;
    flowReporter.eppInput = mock(EppInput.class);
    when(flowReporter.eppInput.getCommandType()).thenReturn("info");
    when(flowReporter.eppInput.getResourceType()).thenReturn(Optional.of("domain"));
    when(flowReporter.eppInput.getSingleTargetId()).thenReturn(Optional.of("target.foo"));
    when(flowReporter.eppInput.getTargetIds()).thenReturn(ImmutableList.of("target.foo"));
  }

  @Test
  public void testRecordToLogs_eppInput_basic() throws Exception {
    flowReporter.recordToLogs();
    assertThat(parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-EPPINPUT: ")))
        .containsExactly(
              "xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><xml/>\n",
              "xmlBytes", "PHhtbC8+"); // Base64-encoding of "<xml/>".
  }

  @Test
  public void testRecordToLogs_eppInput_complex() throws Exception {
    String domainCreateXml = loadFileWithSubstitutions(
        getClass(), "domain_create_prettyprinted.xml", ImmutableMap.<String, String>of());
    flowReporter.inputXmlBytes = domainCreateXml.getBytes(UTF_8);
    flowReporter.recordToLogs();
    assertThat(parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-EPPINPUT: ")))
        .containsExactly(
              "xml", domainCreateXml,
              "xmlBytes", base64().encode(domainCreateXml.getBytes(UTF_8)));
  }

  @Test
  public void testRecordToLogs_metadata_basic() throws Exception {
    flowReporter.recordToLogs();
    assertThat(parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: ")))
        .containsExactly(
              "serverTrid", "server-456",
              "clientId", "TheRegistrar",
              "commandType", "info",
              "resourceType", "domain",
              "flowClassName", "TestCommandFlow",
              "targetId", "target.foo",
              "targetIds", ImmutableList.of("target.foo"),
              "tld", "foo",
              "tlds", ImmutableList.of("foo"),
              "icannActivityReportField", "");
  }

  @Test
  public void testRecordToLogs_metadata_withReportingSpec() throws Exception {
    flowReporter.flowClass = TestReportingSpecCommandFlow.class;
    flowReporter.recordToLogs();
    Map<String, Object> json =
        parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: "));
    assertThat(json).containsEntry("flowClassName", "TestReportingSpecCommandFlow");
    assertThat(json).containsEntry("icannActivityReportField", "srs-cont-check");
  }

  @Test
  public void testRecordToLogs_metadata_noClientId() throws Exception {
    flowReporter.clientId = "";
    flowReporter.recordToLogs();
    Map<String, Object> json =
        parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: "));
    assertThat(json).containsEntry("clientId", "");
  }

  @Test
  public void testRecordToLogs_metadata_notResourceFlow_noResourceTypeOrTld() throws Exception {
    when(flowReporter.eppInput.getResourceType()).thenReturn(Optional.<String>absent());
    flowReporter.recordToLogs();
    Map<String, Object> json =
        parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: "));
    assertThat(json).containsEntry("resourceType", "");
    assertThat(json).containsEntry("tld", "");
    assertThat(json).containsEntry("tlds", ImmutableList.of());
  }


  @Test
  public void testRecordToLogs_metadata_notDomainFlow_noTld() throws Exception {
    when(flowReporter.eppInput.getResourceType()).thenReturn(Optional.of("contact"));
    flowReporter.recordToLogs();
    Map<String, Object> json =
        parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: "));
    assertThat(json).containsEntry("resourceType", "contact");
    assertThat(json).containsEntry("tld", "");
    assertThat(json).containsEntry("tlds", ImmutableList.of());
  }

  @Test
  public void testRecordToLogs_metadata_multipartDomainName_multipartTld() throws Exception {
    when(flowReporter.eppInput.getSingleTargetId()).thenReturn(Optional.of("target.co.uk"));
    when(flowReporter.eppInput.getTargetIds()).thenReturn(ImmutableList.of("target.co.uk"));
    flowReporter.recordToLogs();
    Map<String, Object> json =
        parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: "));
    assertThat(json).containsEntry("targetId", "target.co.uk");
    assertThat(json).containsEntry("targetIds", ImmutableList.of("target.co.uk"));
    assertThat(json).containsEntry("tld", "co.uk");
    assertThat(json).containsEntry("tlds", ImmutableList.of("co.uk"));
  }

  @Test
  public void testRecordToLogs_metadata_multipleTargetIds_uniqueTldSet() throws Exception {
    when(flowReporter.eppInput.getSingleTargetId()).thenReturn(Optional.<String>absent());
    when(flowReporter.eppInput.getTargetIds())
        .thenReturn(ImmutableList.of("target.co.uk", "foo.uk", "bar.uk", "baz.com"));
    flowReporter.recordToLogs();
    Map<String, Object> json =
        parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: "));
    assertThat(json).containsEntry("targetId", "");
    assertThat(json).containsEntry(
        "targetIds", ImmutableList.of("target.co.uk", "foo.uk", "bar.uk", "baz.com"));
    assertThat(json).containsEntry("tld", "");
    assertThat(json).containsEntry("tlds", ImmutableList.of("co.uk", "uk", "com"));
  }

  @Test
  public void testRecordToLogs_metadata_uppercaseDomainName_lowercaseTld() throws Exception {
    when(flowReporter.eppInput.getSingleTargetId()).thenReturn(Optional.of("TARGET.FOO"));
    when(flowReporter.eppInput.getTargetIds()).thenReturn(ImmutableList.of("TARGET.FOO"));
    flowReporter.recordToLogs();
    Map<String, Object> json =
        parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: "));
    assertThat(json).containsEntry("targetId", "TARGET.FOO");
    assertThat(json).containsEntry("targetIds", ImmutableList.of("TARGET.FOO"));
    assertThat(json).containsEntry("tld", "foo");
    assertThat(json).containsEntry("tlds", ImmutableList.of("foo"));
  }

  @Test
  public void testRecordToLogs_metadata_invalidDomainName_stillGuessesTld() throws Exception {
    when(flowReporter.eppInput.getSingleTargetId()).thenReturn(Optional.of("<foo@bar.com>"));
    when(flowReporter.eppInput.getTargetIds()).thenReturn(ImmutableList.of("<foo@bar.com>"));
    flowReporter.recordToLogs();
    Map<String, Object> json =
        parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: "));
    assertThat(json).containsEntry("targetId", "<foo@bar.com>");
    assertThat(json).containsEntry("targetIds", ImmutableList.of("<foo@bar.com>"));
    assertThat(json).containsEntry("tld", "com>");
    assertThat(json).containsEntry("tlds", ImmutableList.of("com>"));
  }

  @Test
  public void testRecordToLogs_metadata_domainWithoutPeriod_noTld() throws Exception {
    when(flowReporter.eppInput.getSingleTargetId()).thenReturn(Optional.of("target,foo"));
    when(flowReporter.eppInput.getTargetIds()).thenReturn(ImmutableList.of("target,foo"));
    flowReporter.recordToLogs();
    Map<String, Object> json =
        parseJsonMap(findFirstLogMessageByPrefix(handler, "FLOW-LOG-SIGNATURE-METADATA: "));
    assertThat(json).containsEntry("targetId", "target,foo");
    assertThat(json).containsEntry("targetIds", ImmutableList.of("target,foo"));
    assertThat(json).containsEntry("tld", "");
    assertThat(json).containsEntry("tlds", ImmutableList.of());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJsonMap(String json) throws Exception {
    return (Map<String, Object>) JSONValue.parseWithException(json);
  }
}
