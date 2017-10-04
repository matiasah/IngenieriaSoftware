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

import static google.registry.testing.DatastoreHelper.loadRegistrar;
import static google.registry.testing.DatastoreHelper.persistResource;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.model.registrar.Registrar;
import google.registry.tools.ServerSideCommand.Connection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

/** Unit tests for {@link VerifyOteCommand}. */
public class VerifyOteCommandTest extends CommandTestCase<VerifyOteCommand> {

  @Mock private Connection connection;

  @Before
  public void init() throws Exception {
    command.setConnection(connection);
    ImmutableMap<String, Object> response =
        ImmutableMap.<String, Object>of(
            "blobio", "Num actions: 19 - Reqs passed: 19/19 - Overall: PASS");
    when(connection.sendJson(anyString(), anyMapOf(String.class, Object.class)))
        .thenReturn(ImmutableMap.<String, Object>of("blobio", response));
  }

  @Test
  public void testSuccess_pass() throws Exception {
    Registrar registrar =
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setClientId("blobio-1")
            .setRegistrarName("blobio-1")
            .build();
    persistResource(registrar);
    runCommand("blobio");

    verify(connection)
        .sendJson(
            eq("/_dr/admin/verifyOte"),
            eq(ImmutableMap.of("summarize", "false", "registrars", ImmutableList.of("blobio"))));
    assertInStdout("blobio OT&E status");
    assertInStdout("Overall: PASS");
  }

  @Test
  public void testFailure_registrarDoesntExist() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Registrar blobio does not exist.");
    runCommand("blobio");
  }

  @Test
  public void testFailure_noRegistrarsNoCheckAll() throws Exception {
    thrown.expect(
        IllegalArgumentException.class,
        "Must provide at least one registrar name, or supply --check-all with no names.");
    runCommand("");
  }
}
