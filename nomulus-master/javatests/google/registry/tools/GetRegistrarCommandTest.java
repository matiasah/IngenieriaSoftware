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

import com.beust.jcommander.ParameterException;
import org.junit.Test;

/** Unit tests for {@link GetRegistrarCommand}. */
public class GetRegistrarCommandTest extends CommandTestCase<GetRegistrarCommand> {

  @Test
  public void testSuccess() throws Exception {
    // This registrar is created by AppEngineRule.
    runCommand("NewRegistrar");
  }

  @Test
  public void testSuccess_multipleArguments() throws Exception {
    // Registrars are created by AppEngineRule.
    runCommand("NewRegistrar", "TheRegistrar");
  }

  @Test
  public void testFailure_registrarDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Registrar with id ClientZ does not exist");
    runCommand("ClientZ");
  }

  @Test
  public void testFailure_noRegistrarName() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand();
  }

  @Test
  public void testFailure_oneRegistrarDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class, "Registrar with id ClientZ does not exist");
    runCommand("NewRegistrar", "ClientZ");
  }
}
