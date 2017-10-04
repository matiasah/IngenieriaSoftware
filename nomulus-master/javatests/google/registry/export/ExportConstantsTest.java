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

package google.registry.export;

import static com.google.common.base.Strings.repeat;
import static com.google.common.io.Resources.getResource;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.export.ExportConstants.getBackupKinds;
import static google.registry.export.ExportConstants.getReportingKinds;
import static google.registry.util.ResourceUtils.readResourceUtf8;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.re2j.Pattern;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link ExportConstants}. */
@RunWith(JUnit4.class)
public class ExportConstantsTest {

  private static final String GOLDEN_BACKUP_KINDS_FILENAME = "backup_kinds.txt";

  private static final String GOLDEN_REPORTING_KINDS_FILENAME = "reporting_kinds.txt";

  private static final String UPDATE_INSTRUCTIONS_TEMPLATE = Joiner.on('\n').join(
      "",
      repeat("-", 80),
      "Your changes affect the list of %s kinds in the golden file:",
      "  %s",
      "If these changes are desired, update the golden file with the following contents:",
      repeat("=", 80),
      "%s",
      repeat("=", 80),
      "");

  @Test
  public void testBackupKinds_matchGoldenBackupKindsFile() throws Exception {
    checkKindsMatchGoldenFile("backed-up", GOLDEN_BACKUP_KINDS_FILENAME, getBackupKinds());
  }

  @Test
  public void testReportingKinds_matchGoldenReportingKindsFile() throws Exception {
    checkKindsMatchGoldenFile("reporting", GOLDEN_REPORTING_KINDS_FILENAME, getReportingKinds());
  }

  @Test
  public void testReportingKinds_areSubsetOfBackupKinds() throws Exception {
    assertThat(getBackupKinds()).containsAllIn(getReportingKinds());
  }

  private static void checkKindsMatchGoldenFile(
      String kindsName, String goldenFilename, ImmutableSet<String> actualKinds) {
    String updateInstructions =
        String.format(
            UPDATE_INSTRUCTIONS_TEMPLATE,
            kindsName,
            getResource(ExportConstantsTest.class, goldenFilename).toString(),
            Joiner.on('\n').join(actualKinds));
    assertWithMessage(updateInstructions)
        .that(actualKinds)
        .containsExactlyElementsIn(extractListFromFile(goldenFilename))
        .inOrder();
  }

  /**
   * Helper method to extract list from file
   *
   * @param filename
   * @return ImmutableList<String>
   */
  private static ImmutableList<String> extractListFromFile(String filename) {
    String fileContents = readResourceUtf8(ExportConstantsTest.class, filename);
    final Pattern stripComments = Pattern.compile("\\s*#.*$");
    return FluentIterable.from(Splitter.on('\n').split(fileContents.trim()))
        .transform(
            new Function<String, String>() {
              @Override
              @Nullable
              public String apply(@Nullable String line) {
                return stripComments.matcher(line).replaceFirst("");
              }
            })
        .toList();
  }
}
