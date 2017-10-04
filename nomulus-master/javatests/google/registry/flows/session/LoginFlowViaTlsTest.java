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

package google.registry.flows.session;

import static google.registry.testing.DatastoreHelper.persistResource;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import google.registry.flows.TlsCredentials;
import google.registry.flows.TlsCredentials.BadRegistrarCertificateException;
import google.registry.flows.TlsCredentials.BadRegistrarIpAddressException;
import google.registry.flows.TlsCredentials.MissingRegistrarCertificateException;
import google.registry.flows.TlsCredentials.NoSniException;
import google.registry.model.registrar.Registrar;
import google.registry.testing.CertificateSamples;
import google.registry.util.CidrAddressBlock;
import org.junit.Test;

/** Unit tests for {@link LoginFlow} when accessed via a TLS transport. */
public class LoginFlowViaTlsTest extends LoginFlowTestCase {

  private static final String GOOD_CERT = CertificateSamples.SAMPLE_CERT_HASH;
  private static final String BAD_CERT = CertificateSamples.SAMPLE_CERT2_HASH;
  private static final Optional<String> GOOD_IP = Optional.of("192.168.1.1");
  private static final Optional<String> BAD_IP = Optional.of("1.1.1.1");
  private static final Optional<String> GOOD_IPV6 = Optional.of("2001:db8::1");
  private static final Optional<String> BAD_IPV6 = Optional.of("2001:db8::2");

  @Override
  protected Registrar.Builder getRegistrarBuilder() {
    return super.getRegistrarBuilder()
        .setClientCertificateHash(GOOD_CERT)
        .setIpAddressWhitelist(ImmutableList.of(
            CidrAddressBlock.create(InetAddresses.forString(GOOD_IP.get()), 32)));
  }

  @Test
  public void testSuccess_withGoodCredentials() throws Exception {
    persistResource(getRegistrarBuilder().build());
    credentials = new TlsCredentials(GOOD_CERT, GOOD_IP, "goo.example");
    doSuccessfulTest("login_valid.xml");
  }

  @Test
  public void testSuccess_withGoodCredentialsIpv6() throws Exception {
    persistResource(
        getRegistrarBuilder()
            .setIpAddressWhitelist(ImmutableList.of(
                CidrAddressBlock.create("2001:db8:0:0:0:0:1:1/32")))
            .build());
    credentials = new TlsCredentials(GOOD_CERT, GOOD_IPV6, "goo.example");
    doSuccessfulTest("login_valid.xml");
  }

  @Test
  public void testSuccess_withIpv6AddressInSubnet() throws Exception {
    persistResource(
        getRegistrarBuilder()
            .setIpAddressWhitelist(ImmutableList.of(
                CidrAddressBlock.create("2001:db8:0:0:0:0:1:1/32")))
            .build());
    credentials = new TlsCredentials(GOOD_CERT, GOOD_IPV6, "goo.example");
    doSuccessfulTest("login_valid.xml");
  }

  @Test
  public void testSuccess_withIpv4AddressInSubnet() throws Exception {
    persistResource(
        getRegistrarBuilder()
            .setIpAddressWhitelist(ImmutableList.of(
                CidrAddressBlock.create("192.168.1.255/24")))
            .build());
    credentials = new TlsCredentials(GOOD_CERT, GOOD_IP, "goo.example");
    doSuccessfulTest("login_valid.xml");
  }

  @Test
  public void testFailure_incorrectClientCertificateHash() throws Exception {
    persistResource(getRegistrarBuilder().build());
    credentials = new TlsCredentials(BAD_CERT, GOOD_IP, "goo.example");
    doFailingTest("login_valid.xml", BadRegistrarCertificateException.class);
  }

  @Test
  public void testFailure_missingClientCertificateHash() throws Exception {
    persistResource(getRegistrarBuilder().build());
    credentials = new TlsCredentials(null, GOOD_IP, "goo.example");
    doFailingTest("login_valid.xml", MissingRegistrarCertificateException.class);
  }

  @Test
  public void testFailure_noSniAndCertRequired() throws Exception {
    persistResource(getRegistrarBuilder().build());
    credentials = new TlsCredentials(null, GOOD_IP, null);
    doFailingTest("login_valid.xml", NoSniException.class);
  }

  @Test
  public void testFailure_missingClientIpAddress() throws Exception {
    persistResource(
        getRegistrarBuilder()
            .setIpAddressWhitelist(ImmutableList.of(
                CidrAddressBlock.create(InetAddresses.forString("192.168.1.1"), 32),
                CidrAddressBlock.create(InetAddresses.forString("2001:db8::1"), 128)))
            .build());
    credentials = new TlsCredentials(GOOD_CERT, Optional.<String>absent(), "goo.example");
    doFailingTest("login_valid.xml", BadRegistrarIpAddressException.class);
  }

  @Test
  public void testFailure_incorrectClientIpv4Address() throws Exception {
    persistResource(
        getRegistrarBuilder()
            .setIpAddressWhitelist(ImmutableList.of(
                CidrAddressBlock.create(InetAddresses.forString("192.168.1.1"), 32),
                CidrAddressBlock.create(InetAddresses.forString("2001:db8::1"), 128)))
            .build());
    credentials = new TlsCredentials(GOOD_CERT, BAD_IP, "goo.example");
    doFailingTest("login_valid.xml", BadRegistrarIpAddressException.class);
  }

  @Test
  public void testFailure_incorrectClientIpv6Address() throws Exception {
    persistResource(
        getRegistrarBuilder()
            .setIpAddressWhitelist(ImmutableList.of(
                CidrAddressBlock.create(InetAddresses.forString("192.168.1.1"), 32),
                CidrAddressBlock.create(InetAddresses.forString("2001:db8::1"), 128)))
            .build());
    credentials = new TlsCredentials(GOOD_CERT, BAD_IPV6, "goo.example");
    doFailingTest("login_valid.xml", BadRegistrarIpAddressException.class);
  }
}
