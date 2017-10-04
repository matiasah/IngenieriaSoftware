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

package google.registry.tools.params;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link HostAndPortParameter}. */
@RunWith(JUnit4.class)
public class HostAndPortParameterTest {

  private final HostAndPortParameter instance = new HostAndPortParameter();

  @Test
  public void testConvert_hostOnly() throws Exception {
    assertThat(instance.convert("foo.bar").getHost()).isEqualTo("foo.bar");
    assertThat(instance.convert("foo.bar").getPortOrDefault(31337)).isEqualTo(31337);
  }

  @Test
  public void testConvert_hostAndPort() throws Exception {
    assertThat(instance.convert("foo.bar:1234").getHost()).isEqualTo("foo.bar");
    assertThat(instance.convert("foo.bar:1234").getPortOrDefault(31337)).isEqualTo(1234);
  }

  @Test
  public void testConvert_ipv6_hostOnly() throws Exception {
    assertThat(instance.convert("[feed:a:bee]").getHost()).isEqualTo("feed:a:bee");
    assertThat(instance.convert("[feed:a:bee]").getPortOrDefault(31337)).isEqualTo(31337);
  }

  @Test
  public void testConvert_ipv6_hostAndPort() throws Exception {
    assertThat(instance.convert("[feed:a:bee]:1234").getHost()).isEqualTo("feed:a:bee");
    assertThat(instance.convert("[feed:a:bee]:1234").getPortOrDefault(31337)).isEqualTo(1234);
  }
}
