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
import static google.registry.testing.DatastoreHelper.createTlds;

import com.beust.jcommander.ParameterException;
import org.junit.Test;

/** Unit tests for {@link GetTldCommand}. */
public class GetTldCommandTest extends CommandTestCase<GetTldCommand> {

  @Test
  public void testSuccess() throws Exception {
    createTld("xn--q9jyb4c");
    runCommand("xn--q9jyb4c");
  }

  @Test
  public void testSuccess_multipleArguments() throws Exception {
    createTlds("xn--q9jyb4c", "example");
    runCommand("xn--q9jyb4c", "example");
  }


  @Test
  public void testFailure_tldDoesNotExist() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    runCommand("xn--q9jyb4c");
  }

  @Test
  public void testFailure_noTldName() throws Exception {
    thrown.expect(ParameterException.class);
    runCommand();
  }

  @Test
  public void testFailure_oneTldDoesNotExist() throws Exception {
    createTld("xn--q9jyb4c");
    thrown.expect(IllegalArgumentException.class);
    runCommand("xn--q9jyb4c", "example");
  }
}
