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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.EppResourceUtils.loadByForeignKey;
import static google.registry.testing.DatastoreHelper.assertNoBillingEvents;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.newDomainResource;
import static google.registry.testing.DatastoreHelper.persistActiveDomain;
import static google.registry.testing.DatastoreHelper.persistActiveHost;
import static google.registry.testing.DatastoreHelper.persistDeletedHost;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.HostResourceSubject.assertAboutHosts;
import static google.registry.testing.TaskQueueHelper.assertDnsTasksEnqueued;
import static google.registry.testing.TaskQueueHelper.assertNoDnsTasksEnqueued;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppXmlTransformer.IpAddressVersionMismatchException;
import google.registry.flows.ResourceFlowTestCase;
import google.registry.flows.exceptions.ResourceAlreadyExistsException;
import google.registry.flows.host.HostCreateFlow.SubordinateHostMustHaveIpException;
import google.registry.flows.host.HostCreateFlow.UnexpectedExternalHostIpException;
import google.registry.flows.host.HostFlowUtils.HostNameNotLowerCaseException;
import google.registry.flows.host.HostFlowUtils.HostNameNotNormalizedException;
import google.registry.flows.host.HostFlowUtils.HostNameNotPunyCodedException;
import google.registry.flows.host.HostFlowUtils.HostNameTooLongException;
import google.registry.flows.host.HostFlowUtils.HostNameTooShallowException;
import google.registry.flows.host.HostFlowUtils.InvalidHostNameException;
import google.registry.flows.host.HostFlowUtils.SuperordinateDomainDoesNotExistException;
import google.registry.flows.host.HostFlowUtils.SuperordinateDomainInPendingDeleteException;
import google.registry.model.domain.DomainResource;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.host.HostResource;
import google.registry.model.reporting.HistoryEntry;
import org.joda.time.DateTime;
import org.junit.Test;

/** Unit tests for {@link HostCreateFlow}. */
public class HostCreateFlowTest extends ResourceFlowTestCase<HostCreateFlow, HostResource> {

  private void setEppHostCreateInput(String hostName, String hostAddrs) {
    setEppInput(
        "host_create.xml",
        ImmutableMap.of(
            "HOSTNAME", hostName,
            "HOSTADDRS", (hostAddrs == null) ? "" : hostAddrs));
  }

  private void setEppHostCreateInputWithIps(String hostName) {
    setEppHostCreateInput(
        hostName,
        "<host:addr ip=\"v4\">192.0.2.2</host:addr>\n"
            + "<host:addr ip=\"v4\">192.0.2.29</host:addr>\n"
            + "<host:addr ip=\"v6\">1080:0:0:0:8:800:200C:417A</host:addr>");
  }

  public HostCreateFlowTest() {
    setEppHostCreateInput("ns1.example.tld", null);
    clock.setTo(DateTime.parse("1999-04-03T22:00:00.0Z"));
  }

  private void doSuccessfulTest() throws Exception {
    clock.advanceOneMilli();
    assertTransactionalFlow(true);
    runFlowAssertResponse(readFile("host_create_response.xml"));
    // Check that the host was created and persisted with a history entry.
    assertAboutHosts().that(reloadResourceByForeignKey())
        .hasLastSuperordinateChange(null).and()
        .hasOnlyOneHistoryEntryWhich()
        .hasType(HistoryEntry.Type.HOST_CREATE);
    assertNoBillingEvents();
    assertEppResourceIndexEntityFor(reloadResourceByForeignKey());
  }

  private void doSuccessfulInternalTest(String tld) throws Exception {
    setEppHostCreateInputWithIps("ns1.example.tld");
    createTld(tld);
    persistActiveDomain("example.tld");
    doSuccessfulTest();
  }

  @Test
  public void testDryRun() throws Exception {
    dryRunFlowAssertResponse(readFile("host_create_response.xml"));
  }

  @Test
  public void testSuccess_externalNeverExisted() throws Exception {
    doSuccessfulTest();
    assertAboutHosts().that(reloadResourceByForeignKey()).hasSuperordinateDomain(null);
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testSuccess_internalNeverExisted() throws Exception {
    doSuccessfulInternalTest("tld");
    HostResource host = reloadResourceByForeignKey();
    DomainResource superordinateDomain =
        loadByForeignKey(DomainResource.class, "example.tld", clock.nowUtc());
    assertAboutHosts().that(host).hasSuperordinateDomain(Key.create(superordinateDomain));
    assertThat(superordinateDomain.getSubordinateHosts()).containsExactly("ns1.example.tld");
    assertDnsTasksEnqueued("ns1.example.tld");
  }

  @Test
  public void testFailure_multipartTLDsAndInvalidHost() throws Exception {
    createTlds("bar.tld", "tld");

    setEppHostCreateInputWithIps("ns1.bar.tld");
    thrown.expect(HostNameTooShallowException.class);
    runFlow();
  }

  @Test
  public void testSuccess_externalExistedButWasDeleted() throws Exception {
    persistDeletedHost(getUniqueIdFromCommand(), clock.nowUtc().minusDays(1));
    doSuccessfulTest();
    assertAboutHosts().that(reloadResourceByForeignKey()).hasSuperordinateDomain(null);
    assertNoDnsTasksEnqueued();
  }

  @Test
  public void testSuccess_internalExistedButWasDeleted() throws Exception {
    persistDeletedHost(getUniqueIdFromCommand(), clock.nowUtc().minusDays(1));
    doSuccessfulInternalTest("tld");
    HostResource host = reloadResourceByForeignKey();
    DomainResource superordinateDomain =
        loadByForeignKey(DomainResource.class, "example.tld", clock.nowUtc());
    assertAboutHosts().that(host).hasSuperordinateDomain(Key.create(superordinateDomain));
    assertThat(superordinateDomain.getSubordinateHosts()).containsExactly("ns1.example.tld");
    assertDnsTasksEnqueued("ns1.example.tld");
  }

  @Test
  public void testFailure_subordinateNeedsIps() throws Exception {
    setEppHostCreateInput("ns1.example.tld", null);
    createTld("tld");
    persistActiveDomain("example.tld");
    thrown.expect(SubordinateHostMustHaveIpException.class);
    runFlow();
  }

  @Test
  public void testFailure_externalMustNotHaveIps() throws Exception {
    setEppHostCreateInputWithIps("ns1.example.external");
    createTld("tld");
    persistActiveDomain("example.tld");
    thrown.expect(UnexpectedExternalHostIpException.class);
    runFlow();
  }

  @Test
  public void testFailure_superordinateMissing() throws Exception {
    setEppHostCreateInput("ns1.example.tld", null);
    createTld("tld");
    thrown.expect(
        SuperordinateDomainDoesNotExistException.class,
        "(example.tld)");
    runFlow();
  }

  @Test
  public void testFailure_superordinateInPendingDelete() throws Exception {
    setEppHostCreateInputWithIps("ns1.example.tld");
    createTld("tld");
    persistResource(newDomainResource("example.tld")
        .asBuilder()
        .setDeletionTime(clock.nowUtc().plusDays(35))
        .setStatusValues(ImmutableSet.of(StatusValue.PENDING_DELETE))
        .build());
    clock.advanceOneMilli();
    thrown.expect(
        SuperordinateDomainInPendingDeleteException.class,
        "Superordinate domain for this hostname is in pending delete");
    runFlow();
  }

  @Test
  public void testFailure_alreadyExists() throws Exception {
    setEppHostCreateInput("ns1.example.tld", null);
    persistActiveHost(getUniqueIdFromCommand());
    thrown.expect(
        ResourceAlreadyExistsException.class,
        String.format("Object with given ID (%s) already exists", getUniqueIdFromCommand()));
    runFlow();
  }

  @Test
  public void testFailure_nonLowerCaseHostname() throws Exception {
    setEppHostCreateInput("ns1.EXAMPLE.tld", null);
    thrown.expect(HostNameNotLowerCaseException.class);
    runFlow();
  }

  @Test
  public void testFailure_nonPunyCodedHostname() throws Exception {
    setEppHostCreateInput("ns1.çauçalito.みんな", null);
    thrown.expect(HostNameNotPunyCodedException.class, "expected ns1.xn--aualito-txac.xn--q9jyb4c");
    runFlow();
  }

  @Test
  public void testFailure_nonCanonicalHostname() throws Exception {
    setEppHostCreateInput("ns1.example.tld.", null);
    thrown.expect(HostNameNotNormalizedException.class);
    runFlow();
  }

  @Test
  public void testFailure_longHostName() throws Exception {
    setEppHostCreateInputWithIps("a" + Strings.repeat(".labelpart", 25) + ".tld");
    thrown.expect(HostNameTooLongException.class);
    runFlow();
  }

  @Test
  public void testFailure_ip4AddressWithIp6Declaration() throws Exception {
    setEppHostCreateInput(
        "ns1.example.tld",
        "<host:addr ip=\"v4\">192.0.2.2</host:addr>\n"
            + "<host:addr ip=\"v6\">192.0.2.29</host:addr>\n"
            + "<host:addr ip=\"v6\">1080:0:0:0:8:800:200C:417A</host:addr>");
    thrown.expect(IpAddressVersionMismatchException.class);
    runFlow();
  }

  private void doFailingHostNameTest(
      String hostName,
      Class<? extends Throwable> exception) throws Exception {
    setEppHostCreateInputWithIps(hostName);
    thrown.expect(exception);
    runFlow();
  }

  @Test
  public void testFailure_badCharacter() throws Exception {
    doFailingHostNameTest("foo bar", InvalidHostNameException.class);
  }

  @Test
  public void testFailure_tooShallowPublicSuffix() throws Exception {
    doFailingHostNameTest("example.tld", HostNameTooShallowException.class);
  }

  @Test
  public void testFailure_tooShallowCcTld() throws Exception {
    doFailingHostNameTest("foo.co.uk", HostNameTooShallowException.class);
  }

  @Test
  public void testFailure_barePublicSuffix() throws Exception {
    doFailingHostNameTest("com", HostNameTooShallowException.class);
  }

  @Test
  public void testFailure_bareCcTld() throws Exception {
    doFailingHostNameTest("co.uk", HostNameTooShallowException.class);
  }

  @Test
  public void testFailure_tooShallowNewTld() throws Exception {
    doFailingHostNameTest("example.lol", HostNameTooShallowException.class);
  }

  @Test
  public void testFailure_ccTldInBailiwick() throws Exception {
    createTld("co.uk");
    setEppHostCreateInputWithIps("foo.co.uk");
    thrown.expect(HostNameTooShallowException.class);
    runFlow();
  }

  @Test
  public void testIcannActivityReportField_getsLogged() throws Exception {
    runFlow();
    assertIcannReportingActivityFieldLogged("srs-host-create");
  }
}
