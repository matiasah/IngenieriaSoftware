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

package google.registry.config;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.config.YamlUtils.mergeYaml;

import com.google.common.base.Joiner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link YamlUtils}. */
@RunWith(JUnit4.class)
public class YamlUtilsTest {

  @Test
  public void testSuccess_mergeSimpleMaps() {
    String defaultYaml = join("one: ay", "two: bee", "three: sea");
    String customYaml = join("two: dee", "four: ignored");
    assertThat(mergeYaml(defaultYaml, customYaml)).isEqualTo("{one: ay, two: dee, three: sea}\n");
  }

  @Test
  public void testSuccess_mergeNestedMaps() {
    String defaultYaml = join("non: ay", "nested:", "  blah: tim", "  neat: 12");
    String customYaml = join("nested:", "  blah: max", "  extra: none");
    assertThat(mergeYaml(defaultYaml, customYaml))
        .isEqualTo(join("non: ay", "nested: {blah: max, neat: 12}"));
  }

  @Test
  public void testSuccess_listsAreOverridden() {
    String defaultYaml = join("non: ay", "list:", "  - foo", "  - bar", "  - baz");
    String customYaml = join("list:", "  - crackle", "  - pop var");
    assertThat(mergeYaml(defaultYaml, customYaml))
        .isEqualTo(join("non: ay", "list: [crackle, pop var]"));
  }

  @Test
  public void testSuccess_mergeEmptyMap_isNoop() {
    String defaultYaml = join("one: ay", "two: bee", "three: sea");
    assertThat(mergeYaml(defaultYaml, "# Just a comment\n"))
        .isEqualTo("{one: ay, two: bee, three: sea}\n");
  }

  @Test
  public void testSuccess_mergeNamedMap_overwritesEntirelyWithNewKey() {
    String defaultYaml = join("one: ay", "two: bee", "threeMap:", "  foo: bar", "  baz: gak");
    assertThat(mergeYaml(defaultYaml, "threeMap: {time: money}"))
        .isEqualTo(join("one: ay", "two: bee", "threeMap: {time: money}"));
  }

  private static String join(CharSequence... strings) {
    return Joiner.on('\n').join(strings) + "\n";
  }
}
