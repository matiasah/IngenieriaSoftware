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
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.ParameterException;
import google.registry.testing.ExceptionRule;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DateTimeParameter}. */
@RunWith(JUnit4.class)
public class DateTimeParameterTest {

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final DateTimeParameter instance = new DateTimeParameter();

  @Test
  public void testConvert_numeric_returnsMillisFromEpochUtc() throws Exception {
    assertThat(instance.convert("1234")).isEqualTo(new DateTime(1234L, UTC));
  }

  @Test
  public void testConvert_iso8601_returnsSameAsDateTimeParse() throws Exception {
    String exampleDate = "2014-01-01T01:02:03.004Z";
    assertThat(instance.convert(exampleDate))
        .isEqualTo(DateTime.parse(exampleDate));
  }

  @Test
  public void testConvert_isoDateTimeWithMillis_returnsSameAsDateTimeParse() throws Exception {
    String exampleDate = "2014-01-01T01:02:03.004Z";
    assertThat(instance.convert(exampleDate)).isEqualTo(DateTime.parse(exampleDate));
  }

  @Test
  public void testConvert_weirdTimezone_convertsToUtc() throws Exception {
    assertThat(instance.convert("1984-12-18T00:00:00-0520"))
        .isEqualTo(DateTime.parse("1984-12-18T05:20:00Z"));
  }

  @Test
  public void testConvert_null_throwsException() throws Exception {
    thrown.expect(NullPointerException.class);
    instance.convert(null);
  }

  @Test
  public void testConvert_empty_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("");
  }

  @Test
  public void testConvert_sillyString_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("foo");
  }

  @Test
  public void testConvert_partialDate_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("2014-01");
  }

  @Test
  public void testConvert_onlyDate_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("2014-01-01");
  }

  @Test
  public void testConvert_partialTime_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("T01:02");
  }

  @Test
  public void testConvert_onlyTime_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("T01:02:03");
  }

  @Test
  public void testConvert_partialDateAndPartialTime_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("9T9");
  }

  @Test
  public void testConvert_dateAndPartialTime_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("2014-01-01T01:02");
  }

  @Test
  public void testConvert_noTimeZone_throwsException() throws Exception {
    thrown.expect(IllegalArgumentException.class);
    instance.convert("2014-01-01T01:02:03");
  }

  @Test
  public void testValidate_sillyString_throwsParameterException() throws Exception {
    thrown.expect(ParameterException.class, "--time=foo not an ISO");
    instance.validate("--time", "foo");
  }

  @Test
  public void testValidate_correctInput_doesntThrow() throws Exception {
    instance.validate("--time", "123");
  }
}
