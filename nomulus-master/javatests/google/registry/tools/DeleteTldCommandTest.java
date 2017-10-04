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

import static google.registry.testing.DatastoreHelper.allowRegistrarAccess;
import static google.registry.testing.DatastoreHelper.newRegistry;
import static google.registry.testing.DatastoreHelper.persistDeletedDomain;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableSortedMap;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.RegistryNotFoundException;
import google.registry.model.registry.Registry.TldState;
import google.registry.model.registry.Registry.TldType;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link DeleteTldCommand}. */
public class DeleteTldCommandTest extends CommandTestCase<DeleteTldCommand> {

  private static final String TLD_REAL = "tldreal";
  private static final String TLD_TEST = "tldtest";

  @Before
  public void setUp() {
    persistResource(
        newRegistry(
            TLD_REAL,
            Ascii.toUpperCase(TLD_REAL),
            ImmutableSortedMap.of(START_OF_TIME, TldState.GENERAL_AVAILABILITY),
            TldType.REAL));
    persistResource(
        newRegistry(
            TLD_TEST,
            Ascii.toUpperCase(TLD_TEST),
            ImmutableSortedMap.of(START_OF_TIME, TldState.GENERAL_AVAILABILITY),
            TldType.TEST));
  }

  @Test
  public void testSuccess_otherTldUnaffected() throws Exception {
    runCommandForced("--tld=" + TLD_TEST);

    Registry.get(TLD_REAL);
    thrown.expect(RegistryNotFoundException.class);
    Registry.get(TLD_TEST);
  }

  @Test
  public void testFailure_whenTldDoesNotExist() throws Exception {
    thrown.expect(RegistryNotFoundException.class);
    runCommandForced("--tld=nonexistenttld");
  }

  @Test
  public void testFailure_whenTldIsReal() throws Exception {
    thrown.expect(IllegalStateException.class);
    runCommandForced("--tld=" + TLD_REAL);
  }

  @Test
  public void testFailure_whenDomainsArePresent() throws Exception {
    persistDeletedDomain("domain." + TLD_TEST, DateTime.parse("2000-01-01TZ"));

    thrown.expect(IllegalStateException.class);
    runCommandForced("--tld=" + TLD_TEST);
  }

  @Test
  public void testFailure_whenRegistrarLinksToTld() throws Exception {
    allowRegistrarAccess("TheRegistrar", TLD_TEST);

    thrown.expect(IllegalStateException.class);
    runCommandForced("--tld=" + TLD_TEST);
  }
}
