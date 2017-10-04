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

package google.registry.util;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.util.CollectionUtils.nullToEmpty;
import static google.registry.util.CollectionUtils.partitionMap;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import google.registry.testing.ExceptionRule;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CollectionUtils} */
@RunWith(JUnit4.class)
public class CollectionUtilsTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testNullToEmptyMap_leavesNonNullAlone() {
    Map<String, Integer> map = ImmutableMap.of("hello", 1);
    assertThat(nullToEmpty(map)).isEqualTo(map);
  }

  @Test
  public void testNullToEmptyMap_convertsNullToEmptyMap() {
    Map<String, Integer> map = null;
    Map<String, Integer> convertedMap = nullToEmpty(map);
    assertThat(map).isNull();
    assertThat(convertedMap).isNotNull();
    assertThat(convertedMap).isEmpty();
  }

  @Test
  public void testPartitionMap() {
    Map<String, String> map = ImmutableMap.of("ka", "va", "kb", "vb", "kc", "vc");
    assertThat(partitionMap(map, 2)).containsExactlyElementsIn(ImmutableList.of(
        ImmutableMap.of("ka", "va", "kb", "vb"),
        ImmutableMap.of("kc", "vc")));
  }

  @Test
  public void testPartitionMap_emptyInput() {
    assertThat(partitionMap(ImmutableMap.of(), 100)).isEmpty();
  }

  @Test
  public void testPartitionMap_negativePartitionSize() {
    thrown.expect(IllegalArgumentException.class);
    partitionMap(ImmutableMap.of("A", "b"), -2);
  }

  @Test
  public void testPartitionMap_nullMap() {
    thrown.expect(NullPointerException.class);
    partitionMap(null, 100);
  }

  @Test
  public void testDeadCodeWeDontWantToDelete() throws Exception {
    CollectionUtils.nullToEmpty(HashMultimap.create());
  }
}
