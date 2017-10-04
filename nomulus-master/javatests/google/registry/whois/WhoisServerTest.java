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

package google.registry.whois;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.registrar.Registrar.State.ACTIVE;
import static google.registry.model.registrar.Registrar.Type.PDT;
import static google.registry.model.registry.Registries.getTlds;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static google.registry.testing.FullFieldsTestEntityHelper.makeContactResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeDomainResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeHostResource;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrar;
import static google.registry.testing.FullFieldsTestEntityHelper.makeRegistrarContacts;
import static google.registry.whois.WhoisHelper.loadWhoisTestFile;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreTimeoutException;
import google.registry.model.domain.DomainResource;
import google.registry.model.ofy.Ofy;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.FakeClock;
import google.registry.testing.FakeResponse;
import google.registry.testing.FakeSleeper;
import google.registry.testing.InjectRule;
import google.registry.util.Retrier;
import google.registry.whois.WhoisMetrics.WhoisMetric;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link WhoisServer}. */
@RunWith(JUnit4.class)
public class WhoisServerTest {

  @Rule public final AppEngineRule appEngine = AppEngineRule.builder().withDatastore().build();
  @Rule public final InjectRule inject = new InjectRule();

  private final FakeResponse response = new FakeResponse();
  private final FakeClock clock = new FakeClock(DateTime.parse("2009-06-29T20:13:00Z"));

  private WhoisServer newWhoisServer(String input) {
    WhoisServer whoisServer = new WhoisServer();
    whoisServer.clock = clock;
    whoisServer.input = new StringReader(input);
    whoisServer.response = response;
    whoisServer.whoisReader = new WhoisReader(new WhoisCommandFactory());
    whoisServer.whoisMetrics = new WhoisMetrics();
    whoisServer.metricBuilder = WhoisMetric.builderForRequest(clock);
    whoisServer.disclaimer =
        "Doodle Disclaimer\nI exist so that carriage return\nin disclaimer can be tested.";
    whoisServer.retrier = new Retrier(new FakeSleeper(clock), 3);
    return whoisServer;
  }

  @Before
  public void setUp() throws Exception {
    createTlds("lol", "xn--q9jyb4c", "1.test");
    inject.setStaticField(Ofy.class, "clock", clock);
  }

  @Test
  public void testRun_badRequest_stillSends200() throws Exception {
    newWhoisServer("\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_no_command.txt"));
  }

  @Test
  public void testRun_domainQuery_works() throws Exception {
    Registrar registrar = persistResource(makeRegistrar(
        "evilregistrar", "Yes Virginia <script>", ACTIVE));
    persistResource(makeDomainResource(
        "cat.lol",
        persistResource(makeContactResource("5372808-ERL", "Goblin Market", "lol@cat.lol")),
        persistResource(makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.lol")),
        persistResource(makeContactResource("5372808-TRL", "The Raven", "bog@cat.lol")),
        persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4")),
        persistResource(makeHostResource("ns2.cat.lol", "bad:f00d:cafe::15:beef")),
        registrar));
    persistSimpleResources(makeRegistrarContacts(registrar));
    newWhoisServer("domain cat.lol\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_domain.txt"));
  }

  @Test
  public void testRun_idnDomain_works() throws Exception {
    Registrar registrar = persistResource(makeRegistrar(
        "evilregistrar", "Yes Virginia <script>", ACTIVE));
    persistResource(makeDomainResource(
        "cat.みんな",
        persistResource(makeContactResource("5372808-ERL", "(◕‿◕)", "lol@cat.みんな")),
        persistResource(makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.みんな")),
        persistResource(makeContactResource("5372808-TRL", "The Raven", "bog@cat.みんな")),
        persistResource(makeHostResource("ns1.cat.みんな",  "1.2.3.4")),
        persistResource(makeHostResource("ns2.cat.みんな",  "bad:f00d:cafe::15:beef")),
        registrar));
    persistSimpleResources(makeRegistrarContacts(registrar));
    newWhoisServer("domain cat.みんな\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_idn_punycode.txt"));
  }

  @Test
  public void testRun_punycodeDomain_works() throws Exception {
    Registrar registrar = persistResource(makeRegistrar(
        "evilregistrar", "Yes Virginia <script>", ACTIVE));
    persistResource(makeDomainResource(
        "cat.みんな",
        persistResource(makeContactResource("5372808-ERL", "(◕‿◕)", "lol@cat.みんな")),
        persistResource(makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.みんな")),
        persistResource(makeContactResource("5372808-TRL", "The Raven", "bog@cat.みんな")),
        persistResource(makeHostResource("ns1.cat.みんな",  "1.2.3.4")),
        persistResource(makeHostResource("ns2.cat.みんな",  "bad:f00d:cafe::15:beef")),
        registrar));
    persistSimpleResources(makeRegistrarContacts(registrar));
    newWhoisServer("domain cat.xn--q9jyb4c\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_idn_punycode.txt"));
  }

  @Test
  public void testRun_domainNotFound_returns200OkAndPlainTextResponse() throws Exception {
    newWhoisServer("domain cat.lol\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload())
        .isEqualTo(loadWhoisTestFile("whois_server_domain_not_found.txt"));
  }

  // todo (b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testRun_domainInTestTld_isConsideredNotFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    Registrar registrar = persistResource(makeRegistrar(
        "evilregistrar", "Yes Virginia <script>", ACTIVE));
    persistResource(makeDomainResource(
        "cat.lol",
        persistResource(makeContactResource("5372808-ERL", "Goblin Market", "lol@cat.lol")),
        persistResource(makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.lol")),
        persistResource(makeContactResource("5372808-TRL", "The Raven", "bog@cat.lol")),
        persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4")),
        persistResource(makeHostResource("ns2.cat.lol", "bad:f00d:cafe::15:beef")),
        registrar));
    persistSimpleResources(makeRegistrarContacts(registrar));
    newWhoisServer("domain cat.lol\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload())
        .isEqualTo(loadWhoisTestFile("whois_server_domain_not_found.txt"));
  }

  @Test
  public void testRun_domainFlaggedAsDeletedInDatastore_isConsideredNotFound() throws Exception {
    Registrar registrar;
    persistResource(makeDomainResource("cat.lol",
        persistResource(
            makeContactResource("5372808-ERL", "Peter Murphy", "lol@cat.lol")),
        persistResource(
            makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.lol")),
        persistResource(
            makeContactResource("5372808-TRL", "The Raven", "bog@cat.lol")),
        persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4")),
        persistResource(makeHostResource("ns2.cat.lol", "bad:f00d:cafe::15:beef")),
        persistResource(
            (registrar = makeRegistrar("example", "Example Registrar", ACTIVE))))
                .asBuilder().setDeletionTime(clock.nowUtc().minusDays(1)).build());
    persistSimpleResources(makeRegistrarContacts(registrar));
    newWhoisServer("domain cat.lol\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload())
        .isEqualTo(loadWhoisTestFile("whois_server_domain_not_found.txt"));
  }

  /**
   * Create a deleted domain and an active domain with the same label, and make sure only the
   * active one is returned.
   */
  @Test
  public void testRun_domainDeletedThenRecreated_isFound() throws Exception {
    Registrar registrar;
    DomainResource domain1 = persistResource(makeDomainResource("cat.lol",
        persistResource(
            makeContactResource("5372808-ERL", "Peter Murphy", "lol@cat.lol")),
        persistResource(
            makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.lol")),
        persistResource(
            makeContactResource("5372808-TRL", "The Raven", "bog@cat.lol")),
        persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4")),
        persistResource(makeHostResource("ns2.cat.lol", "bad:f00d:cafe::15:beef")),
        persistResource(
            makeRegistrar("example", "Example Registrar", ACTIVE))).asBuilder()
                .setCreationTimeForTest(clock.nowUtc().minusDays(2))
                .setDeletionTime(clock.nowUtc().minusDays(1)).build());
    DomainResource domain2 = persistResource(makeDomainResource("cat.lol",
        persistResource(
            makeContactResource(
                "5372809-ERL", "Mrs. Alice Crypto", "alice@example.lol")),
        persistResource(
            makeContactResource("5372809-IRL", "Mr. Bob Crypto", "bob@example.lol")),
        persistResource(
            makeContactResource("5372809-TRL", "Dr. Pablo", "pmy@example.lol")),
        persistResource(makeHostResource("ns1.google.lol", "9.9.9.9")),
        persistResource(makeHostResource("ns2.google.lol", "4311::f143")),
        persistResource((registrar = makeRegistrar(
            "example", "Example Registrar", ACTIVE)))).asBuilder()
            .setCreationTimeForTest(clock.nowUtc()).build());
    persistSimpleResources(makeRegistrarContacts(registrar));
    assertThat(domain1.getRepoId()).isNotEqualTo(domain2.getRepoId());
    newWhoisServer("domain cat.lol\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).contains("Dr. Pablo");
    assertThat(response.getPayload()).contains("ns1.google.lol");
  }

  @Test
  public void testRun_nameserverQuery_works() throws Exception {
    persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    newWhoisServer("nameserver ns1.cat.lol\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_nameserver.txt"));
  }

  @Test
  public void testRun_ipv6_displaysInCollapsedReadableFormat() throws Exception {
    persistResource(makeHostResource("ns1.cat.lol", "bad:f00d:cafe::15:beef"));
    newWhoisServer("nameserver ns1.cat.lol\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).contains("ns1.cat.lol");
    // The most important thing this tests is that the outputted address is compressed!
    assertThat(response.getPayload()).contains("bad:f00d:cafe::15:beef");
    assertThat(response.getPayload()).doesNotContain("bad:f00d:cafe:0:0:0:15:beef");
  }

  @Test
  public void testRun_idnNameserver_works() throws Exception {
    persistResource(makeHostResource("ns1.cat.みんな", "1.2.3.4"));
    newWhoisServer("nameserver ns1.cat.みんな\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).contains("ns1.cat.xn--q9jyb4c");
    assertThat(response.getPayload()).contains("1.2.3.4");
  }

  @Test
  public void testRun_punycodeNameserver_works() throws Exception {
    persistResource(makeHostResource("ns1.cat.みんな", "1.2.3.4"));
    newWhoisServer("nameserver ns1.cat.xn--q9jyb4c\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).contains("ns1.cat.xn--q9jyb4c");
    assertThat(response.getPayload()).contains("1.2.3.4");
  }

  @Test
  public void testRun_nameserverNotFound_returns200AndText() throws Exception {
    persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    newWhoisServer("nameserver ns1.cat.lulz\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload())
        .isEqualTo(loadWhoisTestFile("whois_server_nameserver_not_found.txt"));
  }

  @Test
  public void testRun_nameserverFlaggedAsDeletedInDatastore_doesntGetLeaked() throws Exception {
    persistResource(
        makeHostResource("ns1.cat.lol", "1.2.3.4").asBuilder()
            .setDeletionTime(clock.nowUtc().minusDays(1)).build());
    newWhoisServer("nameserver ns1.cat.lol\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload())
        .isEqualTo(loadWhoisTestFile("whois_server_nameserver_not_found.txt"));
  }

  @Test
  public void testRun_ipNameserverLookup_works() throws Exception {
    persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    newWhoisServer("nameserver 1.2.3.4").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).contains("ns1.cat.lol");
  }

  @Test
  public void testRun_ipMapsToMultipleNameservers_theyAllGetReturned() throws Exception {
    persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    persistResource(makeHostResource("ns2.cat.lol", "1.2.3.4"));
    newWhoisServer("nameserver 1.2.3.4").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).contains("ns1.cat.lol");
    assertThat(response.getPayload()).contains("ns2.cat.lol");
  }

  @Test
  public void testRun_ipMapsToMultipleNameserverInDifferentTlds_showsThemAll() throws Exception {
    persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    persistResource(
        makeHostResource("ns1.cat.xn--q9jyb4c", "1.2.3.4"));
    newWhoisServer("nameserver 1.2.3.4").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).contains("ns1.cat.lol");
    assertThat(response.getPayload()).contains("ns1.cat.xn--q9jyb4c");
  }

  @Test
  public void testRun_ipNameserverEntityDoesNotExist_returns200NotFound() throws Exception {
    newWhoisServer("nameserver feed:a:bee::acab\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_ip_not_found.txt"));
  }

  @Test
  public void testRun_ipMapsToNameserverUnderNonAuthoritativeTld_notFound() throws Exception {
    assertThat(getTlds()).doesNotContain("com");
    persistResource(makeHostResource("ns1.google.com", "1.2.3.4"));
    newWhoisServer("nameserver 1.2.3.4").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_ip_not_found.txt"));
  }

  @Test
  public void testRun_nameserverUnderNonAuthoritativeTld_notFound() throws Exception {
    assertThat(getTlds()).doesNotContain("com");
    persistResource(makeHostResource("ns1.google.com", "1.2.3.4"));
    newWhoisServer("nameserver ns1.google.com").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload())
        .isEqualTo(loadWhoisTestFile("whois_server_nameserver_not_found.txt"));
  }

  // todo (b/27378695): reenable or delete this test
  @Ignore
  @Test
  public void testRun_nameserverInTestTld_notFound() throws Exception {
    persistResource(Registry.get("lol").asBuilder().setTldType(Registry.TldType.TEST).build());
    persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    newWhoisServer("nameserver ns1.cat.lol").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload())
        .isEqualTo(loadWhoisTestFile("whois_server_nameserver_not_found.txt"));
  }

  @Test
  public void testRun_registrarLookup_works() throws Exception {
    Registrar registrar = persistResource(
        makeRegistrar("example", "Example Registrar, Inc.", ACTIVE));
    persistSimpleResources(makeRegistrarContacts(registrar));
    // Notice the partial search without "inc".
    newWhoisServer("registrar example registrar").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_registrar.txt"));
  }

  @Test
  public void testRun_pdtRegistrarLookup_works() throws Exception {
    Registrar registrar =
        persistResource(
            makeRegistrar("example", "Example Registrar, Inc.", ACTIVE)
                .asBuilder()
                .setIanaIdentifier(9995L)
                .setType(PDT)
                .build());
    persistSimpleResources(makeRegistrarContacts(registrar));
    // Notice the partial search without "inc".
    newWhoisServer("registrar example registrar").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_registrar.txt"));
  }

  @Test
  public void testRun_registrarLookupInPendingState_returnsNotFound() throws Exception {
    Registrar registrar = persistResource(
        makeRegistrar("example", "Example Registrar, Inc.", Registrar.State.PENDING));
    persistSimpleResources(makeRegistrarContacts(registrar));
    newWhoisServer("registrar Example Registrar, Inc.").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload())
        .isEqualTo(loadWhoisTestFile("whois_server_registrar_not_found.txt"));
  }

  @Test
  public void testRun_registrarLookupWithTestType_returnsNotFound() throws Exception {
    Registrar registrar = persistResource(
        makeRegistrar("example", "Example Registrar, Inc.", ACTIVE)
            .asBuilder()
            .setIanaIdentifier(null)
            .setType(Registrar.Type.TEST)
            .build());
    persistSimpleResources(makeRegistrarContacts(registrar));
    newWhoisServer("registrar Example Registrar, Inc.").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload())
        .isEqualTo(loadWhoisTestFile("whois_server_registrar_not_found.txt"));
  }

  @Test
  public void testRun_multilevelDomain_isNotConsideredAHostname() throws Exception {
    Registrar registrar =
        persistResource(makeRegistrar("example", "Example Registrar", ACTIVE));
    persistResource(makeDomainResource("cat.1.test",
        persistResource(makeContactResource("5372808-ERL", "(◕‿◕)", "lol@cat.1.test")),
        persistResource(makeContactResource("5372808-IRL", "Santa Claus", "BOFH@cat.1.test")),
        persistResource(makeContactResource("5372808-TRL", "The Raven", "bog@cat.1.test")),
        persistResource(makeHostResource("ns1.cat.1.test", "1.2.3.4")),
        persistResource(makeHostResource("ns2.cat.1.test", "bad:f00d:cafe::15:beef")),
        registrar));
    persistSimpleResources(makeRegistrarContacts(registrar));

    newWhoisServer("domain cat.1.test\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).contains("Domain Name: cat.1.test\r\n");
  }

  @Test
  public void testRun_hostnameWithMultilevelTld_isStillConsideredHostname() throws Exception {
    persistResource(makeHostResource("ns1.cat.1.test", "1.2.3.4"));
    newWhoisServer("nameserver ns1.cat.1.test\r\n").run();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getPayload()).contains("ns1.cat.1.test");
    assertThat(response.getPayload()).contains("1.2.3.4");
  }

  @Test
  public void testRun_metricsLoggedForSuccessfulCommand() throws Exception {
    persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    persistResource(makeHostResource("ns2.cat.lol", "1.2.3.4"));
    WhoisServer server = newWhoisServer("nameserver 1.2.3.4");
    server.whoisMetrics = mock(WhoisMetrics.class);
    server.run();
    WhoisMetric expected =
        WhoisMetric.builderForRequest(clock)
            .setCommandName("NameserverLookupByIp")
            .setNumResults(2)
            .setStatus(SC_OK)
            .build();
    verify(server.whoisMetrics).recordWhoisMetric(eq(expected));
  }

  @Test
  public void testRun_metricsLoggedForUnsuccessfulCommand() throws Exception {
    WhoisServer server = newWhoisServer("domain cat.lol\r\n");
    server.whoisMetrics = mock(WhoisMetrics.class);
    server.run();
    WhoisMetric expected =
        WhoisMetric.builderForRequest(clock)
            .setCommandName("DomainLookup")
            .setNumResults(0)
            .setStatus(SC_NOT_FOUND)
            .build();
    verify(server.whoisMetrics).recordWhoisMetric(eq(expected));
  }

  @Test
  public void testRun_metricsLoggedForInternalServerError() throws Exception {
    persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    WhoisServer server = newWhoisServer("ns1.cat.lol");
    server.whoisReader = mock(WhoisReader.class);
    when(server.whoisReader.readCommand(any(Reader.class), any(DateTime.class)))
        .thenThrow(new IOException("missing cat interface"));
    server.whoisMetrics = mock(WhoisMetrics.class);

    server.run();
    WhoisMetric expected =
        WhoisMetric.builderForRequest(clock)
            .setNumResults(0)
            .setStatus(SC_INTERNAL_SERVER_ERROR)
            .build();
    verify(server.whoisMetrics).recordWhoisMetric(eq(expected));
    assertThat(response.getPayload()).isEqualTo("Internal Server Error");
  }

  @Test
  public void testRun_retryOnTransientFailure() throws Exception {
    persistResource(makeHostResource("ns1.cat.lol", "1.2.3.4"));
    WhoisServer server = newWhoisServer("ns1.cat.lol");
    WhoisResponse expectedResponse =
        server.whoisReader.readCommand(server.input, clock.nowUtc()).executeQuery(clock.nowUtc());

    WhoisReader mockReader = mock(WhoisReader.class);
    WhoisCommand mockCommand = mock(WhoisCommand.class);
    when(mockReader.readCommand(any(Reader.class), any(DateTime.class))).thenReturn(mockCommand);
    when(mockCommand.executeQuery(any(DateTime.class)))
        .thenThrow(new DatastoreFailureException("Expected transient exception #1"))
        .thenThrow(new DatastoreTimeoutException("Expected transient exception #2"))
        .thenReturn(expectedResponse);

    server.whoisReader = mockReader;
    server.run();
    assertThat(response.getPayload()).isEqualTo(loadWhoisTestFile("whois_server_nameserver.txt"));
  }
}
