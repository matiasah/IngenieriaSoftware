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
import static google.registry.util.DateTimeUtils.END_OF_TIME;
import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static google.registry.util.DateTimeUtils.earliestOf;
import static google.registry.util.DateTimeUtils.isAtOrAfter;
import static google.registry.util.DateTimeUtils.isBeforeOrAt;
import static google.registry.util.DateTimeUtils.latestOf;
import static google.registry.util.DateTimeUtils.leapSafeAddYears;

import com.google.common.collect.ImmutableList;
import google.registry.testing.ExceptionRule;
import org.joda.time.DateTime;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link DateTimeUtils}. */
@RunWith(JUnit4.class)
public class DateTimeUtilsTest {

  ImmutableList<DateTime> sampleDates = ImmutableList.of(
      START_OF_TIME, START_OF_TIME.plusDays(1), END_OF_TIME, END_OF_TIME);

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  @Test
  public void testSuccess_earliestOf() {
    assertThat(earliestOf(START_OF_TIME, END_OF_TIME)).isEqualTo(START_OF_TIME);
    assertThat(earliestOf(sampleDates)).isEqualTo(START_OF_TIME);
  }

  @Test
  public void testSuccess_latestOf() {
    assertThat(latestOf(START_OF_TIME, END_OF_TIME)).isEqualTo(END_OF_TIME);
    assertThat(latestOf(sampleDates)).isEqualTo(END_OF_TIME);
  }

  @Test
  public void testSuccess_isBeforeOrAt() {
    assertThat(isBeforeOrAt(START_OF_TIME, START_OF_TIME.plusDays(1))).isTrue();
    assertThat(isBeforeOrAt(START_OF_TIME, START_OF_TIME)).isTrue();
    assertThat(isBeforeOrAt(START_OF_TIME.plusDays(1), START_OF_TIME)).isFalse();
  }

  @Test
  public void testSuccess_isAtOrAfter() {
    assertThat(isAtOrAfter(START_OF_TIME, START_OF_TIME.plusDays(1))).isFalse();
    assertThat(isAtOrAfter(START_OF_TIME, START_OF_TIME)).isTrue();
    assertThat(isAtOrAfter(START_OF_TIME.plusDays(1), START_OF_TIME)).isTrue();
  }

  @Test
  public void testSuccess_leapSafeAddYears() {
    DateTime startDate = DateTime.parse("2012-02-29T00:00:00Z");
    assertThat(startDate.plusYears(4)).isEqualTo(DateTime.parse("2016-02-29T00:00:00Z"));
    assertThat(leapSafeAddYears(startDate, 4)).isEqualTo(DateTime.parse("2016-02-28T00:00:00Z"));
  }

  @Test
  public void testFailure_earliestOfEmpty() {
    thrown.expect(IllegalArgumentException.class);
    earliestOf(ImmutableList.<DateTime>of());
  }

  @Test
  public void testFailure_latestOfEmpty() {
    thrown.expect(IllegalArgumentException.class);
    earliestOf(ImmutableList.<DateTime>of());
  }
}
