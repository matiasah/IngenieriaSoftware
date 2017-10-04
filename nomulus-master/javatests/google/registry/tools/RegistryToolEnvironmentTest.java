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

import google.registry.testing.ExceptionRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link RegistryToolEnvironment}. */
@RunWith(JUnit4.class)
public class RegistryToolEnvironmentTest {

  @Rule
  public ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testGet_withoutSetup_throws() throws Exception {
    thrown.expect(IllegalStateException.class);
    RegistryToolEnvironment.get();
  }

  @Test
  public void testSetup_changesEnvironmentReturnedByGet() throws Exception {
    RegistryToolEnvironment.UNITTEST.setup();
    assertThat(RegistryToolEnvironment.get()).isEqualTo(RegistryToolEnvironment.UNITTEST);

    RegistryToolEnvironment.ALPHA.setup();
    assertThat(RegistryToolEnvironment.get()).isEqualTo(RegistryToolEnvironment.ALPHA);
  }

  @Test
  public void testFromArgs_shortNotation_works() throws Exception {
    assertThat(RegistryToolEnvironment.parseFromArgs(new String[] { "-e", "alpha" }))
        .isEqualTo(RegistryToolEnvironment.ALPHA);
  }

  @Test
  public void testFromArgs_longNotation_works() throws Exception {
    assertThat(RegistryToolEnvironment.parseFromArgs(new String[] { "--environment", "alpha" }))
        .isEqualTo(RegistryToolEnvironment.ALPHA);
  }

  @Test
  public void testFromArgs_uppercase_works() throws Exception {
    assertThat(RegistryToolEnvironment.parseFromArgs(new String[] { "-e", "QA" }))
        .isEqualTo(RegistryToolEnvironment.QA);
  }

  @Test
  public void testFromArgs_equalsNotation_works() throws Exception {
    assertThat(RegistryToolEnvironment.parseFromArgs(new String[] { "-e=sandbox" }))
        .isEqualTo(RegistryToolEnvironment.SANDBOX);
    assertThat(RegistryToolEnvironment.parseFromArgs(new String[] { "--environment=sandbox" }))
        .isEqualTo(RegistryToolEnvironment.SANDBOX);
  }

  @Test
  public void testFromArgs_envFlagAfterCommandName_getsIgnored() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    RegistryToolEnvironment.parseFromArgs(new String[] {
        "registrar_activity_report",
        "-e", "1406851199"});
  }

  @Test
  public void testFromArgs_missingEnvironmentFlag_throwsIae() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    RegistryToolEnvironment.parseFromArgs(new String[] {});
  }

  @Test
  public void testFromArgs_extraEnvFlagAfterCommandName_getsIgnored() throws Exception {
    String[] args = new String[] {
        "-e", "alpha",
        "registrar_activity_report",
        "-e", "1406851199"};
    assertThat(RegistryToolEnvironment.parseFromArgs(args))
        .isEqualTo(RegistryToolEnvironment.ALPHA);
  }

  @Test
  public void testFromArgs_loggingFlagWithUnderscores_isntConsideredCommand() throws Exception {
    String[] args = new String[] {
        "--logging_properties_file", "my_file.properties",
        "-e", "alpha",
        "list_tlds"};
    assertThat(RegistryToolEnvironment.parseFromArgs(args))
        .isEqualTo(RegistryToolEnvironment.ALPHA);
  }

  @Test
  public void testFromArgs_badName_throwsIae() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    RegistryToolEnvironment.parseFromArgs(new String[] { "-e", "alphaville" });
  }
}
