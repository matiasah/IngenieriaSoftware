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

import com.beust.jcommander.ParameterException;
import google.registry.testing.ExceptionRule;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link IntervalParameter}. */
@RunWith(JUnit4.class)
public class IntervalParameterTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final IntervalParameter instance = new IntervalParameter();

  @Test
  public void testConvert() throws Exception {
    assertThat(instance.convert("2004-06-09T12:30:00Z/2004-07-10T13:30:00Z"))
        .isEqualTo(new Interval(
            DateTime.parse("2004-06-09T12:30:00Z"),
            DateTime.parse("2004-07-10T13:30:00Z")));
  }

  @Test
  public void testConvert_singleDate() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("2004-06-09T12:30:00Z");
  }

  @Test
  public void testConvert_backwardsInterval() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("2004-07-10T13:30:00Z/2004-06-09T12:30:00Z");
  }

  @Test
  public void testConvert_empty_throws() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("");
  }

  @Test
  public void testConvert_null_throws() throws Exception {
    thrown.expect(NullPointerException.class);
    instance.convert(null);
  }

  @Test
  public void testConvert_sillyString_throws() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("foo");
  }

  @Test
  public void testValidate_sillyString_throws() throws Exception {
    thrown.expect(ParameterException.class, "--time=foo not an");
    instance.validate("--time", "foo");
  }
}
