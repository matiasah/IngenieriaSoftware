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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.registrar.Registrar.State.ACTIVE;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT_HASH;
import static google.registry.testing.DatastoreHelper.createTld;
import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistPremiumList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registrar.Registrar;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldState;
import google.registry.testing.DeterministicStringGenerator;
import google.registry.util.CidrAddressBlock;
import java.security.cert.CertificateParsingException;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link SetupOteCommand}. */
public class SetupOteCommandTest extends CommandTestCase<SetupOteCommand> {

  ImmutableList<String> passwords = ImmutableList.of(
      "abcdefghijklmnop", "qrstuvwxyzabcdef", "ghijklmnopqrstuv", "wxyzabcdefghijkl");
  DeterministicStringGenerator passwordGenerator =
      new DeterministicStringGenerator("abcdefghijklmnopqrstuvwxyz");

  @Before
  public void init() {
    command.validDnsWriterNames = ImmutableSet.of("FooDnsWriter", "BarDnsWriter", "VoidDnsWriter");
    command.passwordGenerator = passwordGenerator;
    persistPremiumList("default_sandbox_list", "sandbox,USD 1000");
    persistPremiumList("alternate_list", "rich,USD 3000");
  }

  /** Verify TLD creation. */
  private void verifyTldCreation(
      String tldName,
      String roidSuffix,
      TldState tldState,
      String dnsWriter,
      String premiumList,
      Duration addGracePeriodLength,
      Duration redemptionGracePeriodLength,
      Duration pendingDeleteLength) {
    Registry registry = Registry.get(tldName);
    assertThat(registry).isNotNull();
    assertThat(registry.getRoidSuffix()).isEqualTo(roidSuffix);
    assertThat(registry.getTldState(DateTime.now(UTC))).isEqualTo(tldState);
    assertThat(registry.getDnsWriters()).containsExactly(dnsWriter);
    assertThat(registry.getPremiumList()).isNotNull();
    assertThat(registry.getPremiumList().getName()).isEqualTo(premiumList);
    assertThat(registry.getAddGracePeriodLength()).isEqualTo(addGracePeriodLength);
    assertThat(registry.getRedemptionGracePeriodLength()).isEqualTo(redemptionGracePeriodLength);
    assertThat(registry.getPendingDeleteLength()).isEqualTo(pendingDeleteLength);
  }

  /** Verify TLD creation with registry default durations. */
  private void verifyTldCreation(
      String tldName, String roidSuffix, TldState tldState, String dnsWriter, String premiumList) {
    verifyTldCreation(
        tldName,
        roidSuffix,
        tldState,
        dnsWriter,
        premiumList,
        Registry.DEFAULT_ADD_GRACE_PERIOD,
        Registry.DEFAULT_REDEMPTION_GRACE_PERIOD,
        Registry.DEFAULT_PENDING_DELETE_LENGTH);
  }

  private void verifyRegistrarCreation(
      String registrarName,
      String allowedTld,
      String password,
      ImmutableList<CidrAddressBlock> ipWhitelist) {
    Registrar registrar = loadRegistrar(registrarName);
    assertThat(registrar).isNotNull();
    assertThat(registrar.getAllowedTlds()).containsExactlyElementsIn(ImmutableSet.of(allowedTld));
    assertThat(registrar.getRegistrarName()).isEqualTo(registrarName);
    assertThat(registrar.getState()).isEqualTo(ACTIVE);
    assertThat(registrar.testPassword(password)).isTrue();
    assertThat(registrar.getIpAddressWhitelist()).isEqualTo(ipWhitelist);
    assertThat(registrar.getClientCertificate()).isEqualTo(SAMPLE_CERT);
    assertThat(registrar.getClientCertificateHash()).isEqualTo(SAMPLE_CERT_HASH);
  }

  @Test
  public void testSuccess() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());

    verifyTldCreation(
        "blobio-sunrise", "BLOBIOS0", TldState.SUNRISE, "VoidDnsWriter", "default_sandbox_list");
    verifyTldCreation(
        "blobio-landrush", "BLOBIOL1", TldState.LANDRUSH, "VoidDnsWriter", "default_sandbox_list");
    verifyTldCreation(
        "blobio-ga",
        "BLOBIOG2",
        TldState.GENERAL_AVAILABILITY,
        "VoidDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5));

    ImmutableList<CidrAddressBlock> ipAddress = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", passwords.get(0), ipAddress);
    verifyRegistrarCreation("blobio-2", "blobio-landrush", passwords.get(1), ipAddress);
    verifyRegistrarCreation("blobio-3", "blobio-ga", passwords.get(2), ipAddress);
    verifyRegistrarCreation("blobio-4", "blobio-ga", passwords.get(3), ipAddress);
  }

  @Test
  public void testSuccess_multipleIps() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1,2.2.2.2",
        "--registrar=blobio",
        "--dns_writers=FooDnsWriter",
        "--certfile=" + getCertFilename());

    verifyTldCreation(
        "blobio-sunrise", "BLOBIOS0", TldState.SUNRISE, "FooDnsWriter", "default_sandbox_list");
    verifyTldCreation(
        "blobio-landrush", "BLOBIOL1", TldState.LANDRUSH, "FooDnsWriter", "default_sandbox_list");
    verifyTldCreation(
        "blobio-ga",
        "BLOBIOG2",
        TldState.GENERAL_AVAILABILITY,
        "FooDnsWriter",
        "default_sandbox_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5));

    ImmutableList<CidrAddressBlock> ipAddresses = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"),
        CidrAddressBlock.create("2.2.2.2"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", passwords.get(0), ipAddresses);
    verifyRegistrarCreation("blobio-2", "blobio-landrush", passwords.get(1), ipAddresses);
    verifyRegistrarCreation("blobio-3", "blobio-ga", passwords.get(2), ipAddresses);
    verifyRegistrarCreation("blobio-4", "blobio-ga", passwords.get(3), ipAddresses);
  }

  @Test
  public void testSuccess_alternatePremiumList() throws Exception {
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--certfile=" + getCertFilename(),
        "--dns_writers=BarDnsWriter",
        "--premium_list=alternate_list");

    verifyTldCreation(
        "blobio-sunrise", "BLOBIOS0", TldState.SUNRISE, "BarDnsWriter", "alternate_list");
    verifyTldCreation(
        "blobio-landrush", "BLOBIOL1", TldState.LANDRUSH, "BarDnsWriter", "alternate_list");
    verifyTldCreation(
        "blobio-ga",
        "BLOBIOG2",
        TldState.GENERAL_AVAILABILITY,
        "BarDnsWriter",
        "alternate_list",
        Duration.standardMinutes(60),
        Duration.standardMinutes(10),
        Duration.standardMinutes(5));

    ImmutableList<CidrAddressBlock> ipAddress = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", passwords.get(0), ipAddress);
    verifyRegistrarCreation("blobio-2", "blobio-landrush", passwords.get(1), ipAddress);
    verifyRegistrarCreation("blobio-3", "blobio-ga", passwords.get(2), ipAddress);
    verifyRegistrarCreation("blobio-4", "blobio-ga", passwords.get(3), ipAddress);
  }

  @Test
  public void testFailure_missingIpWhitelist() throws Exception {
    thrown.expect(ParameterException.class, "option is required: -w, --ip_whitelist");
    runCommandForced(
        "--registrar=blobio",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_missingRegistrar() throws Exception {
    thrown.expect(ParameterException.class, "option is required: -r, --registrar");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_missingCertificateFile() throws Exception {
    thrown.expect(ParameterException.class, "option is required: -c, --certfile");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--dns_writers=VoidDnsWriter",
        "--registrar=blobio");
  }

  @Test
  public void testFailure_missingDnsWriter() throws Exception {
    thrown.expect(ParameterException.class, "option is required: --dns_writers");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--certfile=" + getCertFilename(),
        "--registrar=blobio");
  }

  @Test
  public void testFailure_invalidCert() throws Exception {
    thrown.expect(CertificateParsingException.class, "No X509Certificate found");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--dns_writers=VoidDnsWriter",
        "--certfile=/dev/null");
  }

  @Test
  public void testFailure_invalidRegistrar() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Registrar name is invalid");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=3blobio",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_invalidDnsWriter() throws Exception {
    thrown.expect(
        IllegalArgumentException.class, "Invalid DNS writer name(s) specified: [InvalidDnsWriter]");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--dns_writers=InvalidDnsWriter",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_registrarTooShort() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Registrar name is invalid");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=bl",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_registrarTooLong() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Registrar name is invalid");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobiotoooolong",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_registrarInvalidCharacter() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Registrar name is invalid");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blo#bio",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_invalidPremiumList() throws Exception {
    thrown.expect(IllegalArgumentException.class, "The premium list 'foo' doesn't exist");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename(),
        "--premium_list=foo");
  }

  @Test
  public void testFailure_tldExists() throws Exception {
    createTld("blobio-sunrise");
    thrown.expect(IllegalStateException.class, "TLD 'blobio-sunrise' already exists");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());
  }

  @Test
  public void testFailure_registrarExists() throws Exception {
    Registrar registrar = loadRegistrar("TheRegistrar").asBuilder()
        .setClientId("blobio-1")
        .setRegistrarName("blobio-1")
        .build();
    persistResource(registrar);
    thrown.expect(IllegalStateException.class, "Registrar blobio-1 already exists");
    runCommandForced(
        "--ip_whitelist=1.1.1.1",
        "--registrar=blobio",
        "--dns_writers=VoidDnsWriter",
        "--certfile=" + getCertFilename());
  }
}
