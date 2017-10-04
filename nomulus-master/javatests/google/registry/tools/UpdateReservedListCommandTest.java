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

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.registry.label.ReservationType.FULLY_BLOCKED;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.util.DateTimeUtils.START_OF_TIME;

import com.google.common.collect.ImmutableList;
import google.registry.model.registry.label.ReservedList;
import org.junit.Test;

/** Unit tests for {@link UpdateReservedListCommand}. */
public class UpdateReservedListCommandTest extends
    CreateOrUpdateReservedListCommandTestCase<UpdateReservedListCommand> {

  private void populateInitialReservedList(boolean shouldPublish) {
    persistResource(
        new ReservedList.Builder()
            .setName("xn--q9jyb4c_common-reserved")
            .setReservedListMapFromLines(ImmutableList.of("helicopter,FULLY_BLOCKED"))
            .setCreationTime(START_OF_TIME)
            .setLastUpdateTime(START_OF_TIME)
            .setShouldPublish(shouldPublish)
            .build());
  }

  @Test
  public void testSuccess() throws Exception {
    runSuccessfulUpdateTest("--name=xn--q9jyb4c_common-reserved", "--input=" + reservedTermsPath);
  }

  @Test
  public void testSuccess_unspecifiedNameDefaultsToFileName() throws Exception {
    runSuccessfulUpdateTest("--input=" + reservedTermsPath);
  }

  @Test
  public void testSuccess_lastUpdateTime_updatedCorrectly() throws Exception {
    populateInitialReservedList(true);
    ReservedList original = ReservedList.get("xn--q9jyb4c_common-reserved").get();
    runCommandForced("--input=" + reservedTermsPath);
    ReservedList updated = ReservedList.get("xn--q9jyb4c_common-reserved").get();
    assertThat(updated.getLastUpdateTime()).isGreaterThan(original.getLastUpdateTime());
    assertThat(updated.getCreationTime()).isEqualTo(original.getCreationTime());
    assertThat(updated.getLastUpdateTime()).isGreaterThan(updated.getCreationTime());
  }

  @Test
  public void testSuccess_shouldPublish_setToFalseCorrectly() throws Exception {
    runSuccessfulUpdateTest("--input=" + reservedTermsPath, "--should_publish=false");
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved")).isPresent();
    ReservedList reservedList = ReservedList.get("xn--q9jyb4c_common-reserved").get();
    assertThat(reservedList.getShouldPublish()).isFalse();
  }

  @Test
  public void testSuccess_shouldPublish_doesntOverrideFalseIfNotSpecified() throws Exception {
    populateInitialReservedList(false);
    runCommandForced("--input=" + reservedTermsPath);
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved")).isPresent();
    ReservedList reservedList = ReservedList.get("xn--q9jyb4c_common-reserved").get();
    assertThat(reservedList.getShouldPublish()).isFalse();
  }

  private void runSuccessfulUpdateTest(String... args) throws Exception {
    populateInitialReservedList(true);
    runCommandForced(args);
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved")).isPresent();
    ReservedList reservedList = ReservedList.get("xn--q9jyb4c_common-reserved").get();
    assertThat(reservedList.getReservedListEntries()).hasSize(2);
    assertThat(reservedList.getReservationInList("baddies")).hasValue(FULLY_BLOCKED);
    assertThat(reservedList.getReservationInList("ford")).hasValue(FULLY_BLOCKED);
    assertThat(reservedList.getReservationInList("helicopter")).isAbsent();
  }

  @Test
  public void testFailure_reservedListDoesntExist() throws Exception {
    String errorMessage =
        "Could not update reserved list xn--q9jyb4c_poobah because it doesn't exist.";
    thrown.expect(IllegalArgumentException.class, errorMessage);
    runCommand("--force", "--name=xn--q9jyb4c_poobah", "--input=" + reservedTermsPath);
  }
}
