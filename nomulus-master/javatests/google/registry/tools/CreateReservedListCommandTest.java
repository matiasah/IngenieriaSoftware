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
import static com.google.common.truth.Truth.assertWithMessage;
import static google.registry.model.registry.label.ReservationType.FULLY_BLOCKED;
import static google.registry.testing.DatastoreHelper.createTlds;
import static google.registry.testing.DatastoreHelper.persistReservedList;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.tools.CreateReservedListCommand.INVALID_FORMAT_ERROR_MESSAGE;
import static org.joda.time.DateTimeZone.UTC;

import google.registry.model.registry.Registry;
import google.registry.model.registry.label.ReservedList;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link CreateReservedListCommand}. */
public class CreateReservedListCommandTest extends
    CreateOrUpdateReservedListCommandTestCase<CreateReservedListCommand> {

  @Before
  public void initTest() throws Exception {
    createTlds("xn--q9jyb4c", "soy");
  }

  @Test
  public void testSuccess() throws Exception {
    runCommandForced("--name=xn--q9jyb4c_common-reserved", "--input=" + reservedTermsPath);
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved")).isPresent();
    ReservedList reservedList = ReservedList.get("xn--q9jyb4c_common-reserved").get();
    assertThat(reservedList.getReservedListEntries()).hasSize(2);
    assertThat(reservedList.getReservationInList("baddies")).hasValue(FULLY_BLOCKED);
    assertThat(reservedList.getReservationInList("ford")).hasValue(FULLY_BLOCKED);
  }

  @Test
  public void testSuccess_unspecifiedNameDefaultsToFileName() throws Exception {
    runCommandForced("--input=" + reservedTermsPath);
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved")).isPresent();
  }

  @Test
  public void testSuccess_timestampsSetCorrectly() throws Exception {
    DateTime before = DateTime.now(UTC);
    runCommandForced("--input=" + reservedTermsPath);
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved")).isPresent();
    ReservedList rl = ReservedList.get("xn--q9jyb4c_common-reserved").get();
    assertThat(rl.getCreationTime()).isAtLeast(before);
    assertThat(rl.getLastUpdateTime()).isEqualTo(rl.getCreationTime());
  }

  @Test
  public void testSuccess_shouldPublishDefaultsToTrue() throws Exception {
    runCommandForced("--input=" + reservedTermsPath);
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved")).isPresent();
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved").get().getShouldPublish()).isTrue();
  }

  @Test
  public void testSuccess_shouldPublishSetToTrue_works() throws Exception {
    runCommandForced("--input=" + reservedTermsPath, "--should_publish=true");
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved")).isPresent();
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved").get().getShouldPublish()).isTrue();
  }

  @Test
  public void testSuccess_shouldPublishSetToFalse_works() throws Exception {
    runCommandForced("--input=" + reservedTermsPath, "--should_publish=false");
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved")).isPresent();
    assertThat(ReservedList.get("xn--q9jyb4c_common-reserved").get().getShouldPublish()).isFalse();
  }

  @Test
  public void testFailure_reservedListWithThatNameAlreadyExists() throws Exception {
    ReservedList rl = persistReservedList("xn--q9jyb4c_foo", "jones,FULLY_BLOCKED");
    persistResource(Registry.get("xn--q9jyb4c").asBuilder().setReservedLists(rl).build());
    thrown.expect(IllegalArgumentException.class, "A reserved list already exists by this name");
    runCommandForced("--name=xn--q9jyb4c_foo", "--input=" + reservedTermsPath);
  }

  @Test
  public void testNamingRules_commonReservedList() throws Exception {
    runCommandForced("--name=common_abuse-list", "--input=" + reservedTermsPath);
    assertThat(ReservedList.get("common_abuse-list")).isPresent();
  }

  @Test
  public void testNamingRules_tldThatDoesNotExist_succeedsWithOverride() throws Exception {
    runNameTestWithOverride("footld_reserved-list");
  }

  @Test
  public void testNamingRules_tldThatDoesNotExist_failsWithoutOverride() throws Exception {
    runNameTestExpectedFailure("footld_reserved-list", "TLD footld does not exist");
  }

  @Test
  public void testNamingRules_underscoreIsMissing_succeedsWithOverride() throws Exception {
    runNameTestWithOverride("random-reserved-list");
  }

  @Test
  public void testNamingRules_underscoreIsMissing_failsWithoutOverride() throws Exception {
    runNameTestExpectedFailure("random-reserved-list", INVALID_FORMAT_ERROR_MESSAGE);
  }

  @Test
  public void testNamingRules_secondHalfOfNameIsMissing_succeedsWithOverride() throws Exception {
    runNameTestWithOverride("soy_");
  }

  @Test
  public void testNamingRules_secondHalfOfNameIsMissing_failsWithoutOverride() throws Exception {
    runNameTestExpectedFailure("soy_", INVALID_FORMAT_ERROR_MESSAGE);
  }

  @Test
  public void testNamingRules_onlyTldIsSpecifiedAsName_succeedsWithOverride() throws Exception {
    runNameTestWithOverride("soy");
  }

  @Test
  public void testNamingRules_onlyTldIsSpecifiedAsName_failsWithoutOverride() throws Exception {
    runNameTestExpectedFailure("soy", INVALID_FORMAT_ERROR_MESSAGE);
  }

  @Test
  public void testNamingRules_commonAsListName_succeedsWithOverride() throws Exception {
    runNameTestWithOverride("invalidtld_common");
  }

  @Test
  public void testNamingRules_commonAsListName_failsWithoutOverride() throws Exception {
    runNameTestExpectedFailure("invalidtld_common", "TLD invalidtld does not exist");
  }

  @Test
  public void testNamingRules_too_many_underscores_succeedsWithOverride() throws Exception {
    runNameTestWithOverride("soy_buffalo_buffalo_buffalo");
  }

  @Test
  public void testNamingRules_too_many_underscores_failsWithoutOverride() throws Exception {
    runNameTestExpectedFailure("soy_buffalo_buffalo_buffalo", INVALID_FORMAT_ERROR_MESSAGE);
  }

  @Test
  public void testNamingRules_withWeirdCharacters_succeedsWithOverride() throws Exception {
    runNameTestWithOverride("soy_$oy");
  }

  @Test
  public void testNamingRules_withWeirdCharacters_failsWithoutOverride() throws Exception {
    runNameTestExpectedFailure("soy_$oy", INVALID_FORMAT_ERROR_MESSAGE);
  }

  private void runNameTestExpectedFailure(String name, String expectedErrorMsg) throws Exception {
    try {
      runCommandForced("--name=" + name, "--input=" + reservedTermsPath);
      assertWithMessage("Expected IllegalArgumentException to be thrown").fail();
    } catch (IllegalArgumentException e) {
      assertThat(ReservedList.get(name)).isAbsent();
      assertThat(e).hasMessageThat().isEqualTo(expectedErrorMsg);
    }
  }

  private void runNameTestWithOverride(String name) throws Exception {
    runCommandForced("--name=" + name, "--override", "--input=" + reservedTermsPath);
    assertThat(ReservedList.get(name)).isPresent();
  }
}
