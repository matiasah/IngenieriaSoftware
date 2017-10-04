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

package google.registry.tldconfig.idn;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import google.registry.testing.ExceptionRule;
import java.net.URI;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link IdnTable}. */
@RunWith(JUnit4.class)
public class IdnTableTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testDigits() {
    ImmutableList<String> of = ImmutableList.<String>of(
        "# URL: https://love.example/lolcatattack.txt",
        "# Policy: https://love.example/policy.html",
        "U+0030",
        "U+0031",
        "U+0032",
        "U+0033",
        "U+0034",
        "U+0035",
        "U+0036",
        "U+0037",
        "U+0038",
        "U+0039");
    IdnTable idnTable =
        IdnTable.createFrom("lolcatattack", of, Optional.<LanguageValidator>absent());
    assertThat(idnTable.isValidLabel("0123456789")).isTrue();
    assertThat(idnTable.isValidLabel("54321a")).isFalse();
    assertThat(idnTable.isValidLabel("AAA000")).isFalse();
  }

  @Test
  public void testIgnoreCommentAndEmptyLines() {
    IdnTable idnTable = IdnTable.createFrom("lolcatattack", ImmutableList.<String>of(
        "# URL: https://love.example/lolcatattack.txt",
        "# Policy: https://love.example/policy.html",
        "U+0030",
        "#U+0031",
        "",
        "U+0032",
        "             ",
        "U+0033   # U+0031",
        "U+0034",
        "U+0035",
        "U+0036",
        "U+0037",
        "U+0038",
        "U+0039"), Optional.<LanguageValidator>absent());
    assertThat(idnTable.isValidLabel("0123456789")).isFalse();
    assertThat(idnTable.isValidLabel("023456789")).isTrue();  // Works when you remove 1
  }

  @Test
  public void testSurrogates() {
    IdnTable idnTable = IdnTable.createFrom("lolcatattack", ImmutableList.<String>of(
        "# URL: https://love.example/lolcatattack.txt",
        "# Policy: https://love.example/policy.html",
        "U+0035",
        "U+0036",
        "U+0037",
        "U+2070E",
        "U+20731"), Optional.<LanguageValidator>absent());
    assertThat(idnTable.getName()).isEqualTo("lolcatattack");
    assertThat(idnTable.isValidLabel("𠜎")).isTrue();
    assertThat(idnTable.isValidLabel("𠜱")).isTrue();
    assertThat(idnTable.isValidLabel("𠝹 ")).isFalse();
    assertThat(idnTable.isValidLabel("𠝹 0")).isFalse();
    assertThat(idnTable.isValidLabel("𠜎567𠜱")).isTrue();
  }

  @Test
  public void testSpecialComments_getParsed() {
    ImmutableList<String> of = ImmutableList.<String>of(
        "# URL: https://love.example/lolcatattack.txt",
        "# Policy: https://love.example/policy.html");
    IdnTable idnTable =
        IdnTable.createFrom("lolcatattack", of, Optional.<LanguageValidator>absent());
    assertThat(idnTable.getUrl()).isEqualTo(URI.create("https://love.example/lolcatattack.txt"));
    assertThat(idnTable.getPolicy()).isEqualTo(URI.create("https://love.example/policy.html"));
  }

  @Test
  public void testMissingUrl_throwsNpe() {
    ImmutableList<String> of = ImmutableList.<String>of(
        "# Policy: https://love.example/policy.html");
    thrown.expect(NullPointerException.class, "sloth missing '# URL:");
    IdnTable.createFrom("sloth", of, Optional.<LanguageValidator>absent());
  }

  @Test
  public void testMissingPolicy_throwsNpe() {
    ImmutableList<String> of = ImmutableList.<String>of(
        "# URL: https://love.example/sloth.txt");
    thrown.expect(NullPointerException.class, "sloth missing '# Policy:");
    IdnTable.createFrom("sloth", of, Optional.<LanguageValidator>absent());
  }
}
