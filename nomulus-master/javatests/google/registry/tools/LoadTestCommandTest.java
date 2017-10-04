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
import static google.registry.testing.DatastoreHelper.persistNewRegistrar;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.model.registrar.Registrar;
import google.registry.tools.ServerSideCommand.Connection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LoadTestCommandTest extends CommandTestCase<LoadTestCommand> {
  Connection connection = mock(Connection.class);

  @Before
  public void setUp() throws Exception {
    command.setConnection(connection);
    createTld("example");
    persistNewRegistrar("acme", "ACME", Registrar.Type.REAL, 99L);
  }

  @Test
  public void test_defaults() throws Exception {
    runCommandForced();
    ImmutableMap<String, Object> parms = new ImmutableMap.Builder<String, Object>()
        .put("tld", "example")
        .put("clientId", "acme")
        .put("successfulHostCreates", 1)
        .put("successfulDomainCreates", 1)
        .put("successfulContactCreates", 1)
        .put("hostInfos", 1)
        .put("domainInfos", 1)
        .put("contactInfos", 1)
        .put("runSeconds", 4600)
        .build();
    verify(connection).send(
        eq("/_dr/loadtest"),
        eq(parms),
        eq(MediaType.PLAIN_TEXT_UTF_8),
        eq(new byte[0]));
  }

  @Test
  public void test_overrides() throws Exception {
    createTld("foo");
    runCommandForced(
        "--tld=foo",
        "--client_id=NewRegistrar",
        "--successful_host_creates=10",
        "--successful_domain_creates=11",
        "--successful_contact_creates=12",
        "--host_infos=13",
        "--domain_infos=14",
        "--contact_infos=15",
        "--run_seconds=16");
    ImmutableMap<String, Object> parms = new ImmutableMap.Builder<String, Object>()
        .put("tld", "foo")
        .put("clientId", "NewRegistrar")
        .put("successfulHostCreates", 10)
        .put("successfulDomainCreates", 11)
        .put("successfulContactCreates", 12)
        .put("hostInfos", 13)
        .put("domainInfos", 14)
        .put("contactInfos", 15)
        .put("runSeconds", 16)
        .build();
    verify(connection).send(
        eq("/_dr/loadtest"),
        eq(parms),
        eq(MediaType.PLAIN_TEXT_UTF_8),
        eq(new byte[0]));
  }
  @Test
  public void test_prompt() throws Exception {
    thrown.expect(IllegalStateException.class);
    runCommand();
    verifyZeroInteractions(connection);
    assertInStderr("Unable to access stdin (are you running with bazel run?)");
  }

  @Test
  public void test_badTLD() throws Exception {
    runCommand("--tld=bogus");
    verifyZeroInteractions(connection);
    assertInStderr("No such TLD: bogus");
  }

  @Test
  public void test_badClientId() throws Exception {
    runCommand("--client_id=badaddry");
    verifyZeroInteractions(connection);
    assertInStderr("No such client: badaddry");
  }

  @Test
  public void test_noProduction() throws Exception {
    runCommandInEnvironment(RegistryToolEnvironment.PRODUCTION);
    assertInStderr("You may not run a load test against production.");
  }
}
