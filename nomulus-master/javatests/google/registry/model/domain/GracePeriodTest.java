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

package google.registry.model.domain;

import static com.google.common.truth.Truth.assertThat;
import static org.joda.time.DateTimeZone.UTC;

import com.googlecode.objectify.Key;
import google.registry.model.billing.BillingEvent;
import google.registry.model.billing.BillingEvent.Reason;
import google.registry.model.billing.BillingEvent.Recurring;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.reporting.HistoryEntry;
import google.registry.testing.AppEngineRule;
import google.registry.testing.ExceptionRule;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link GracePeriod}. */
@RunWith(JUnit4.class)
public class GracePeriodTest {

  @Rule
  public final AppEngineRule appEngine = AppEngineRule.builder()
      .withDatastore()  // Needed to be able to construct Keys.
      .build();

  @Rule
  public final ExceptionRule thrown = new ExceptionRule();

  private final DateTime now = DateTime.now(UTC);
  private BillingEvent.OneTime onetime;

  @Before
  public void before() {
    onetime = new BillingEvent.OneTime.Builder()
      .setEventTime(now)
      .setBillingTime(now.plusDays(1))
      .setClientId("TheRegistrar")
      .setCost(Money.of(CurrencyUnit.USD, 42))
      .setParent(Key.create(HistoryEntry.class, 12345))
      .setReason(Reason.CREATE)
      .setPeriodYears(1)
      .setTargetId("foo.google")
      .build();
  }

  @Test
  public void testSuccess_forBillingEvent() {
    GracePeriod gracePeriod = GracePeriod.forBillingEvent(GracePeriodStatus.ADD, onetime);
    assertThat(gracePeriod.getType()).isEqualTo(GracePeriodStatus.ADD);
    assertThat(gracePeriod.getOneTimeBillingEvent()).isEqualTo(Key.create(onetime));
    assertThat(gracePeriod.getRecurringBillingEvent()).isNull();
    assertThat(gracePeriod.getClientId()).isEqualTo("TheRegistrar");
    assertThat(gracePeriod.getExpirationTime()).isEqualTo(now.plusDays(1));
    assertThat(gracePeriod.isSunrushAddGracePeriod()).isFalse();
    assertThat(gracePeriod.hasBillingEvent()).isTrue();
  }

  @Test
  public void testSuccess_forBillingEvent_sunrushAdd() {
    GracePeriod gracePeriod = GracePeriod.forBillingEvent(GracePeriodStatus.SUNRUSH_ADD, onetime);
    assertThat(gracePeriod.getType()).isEqualTo(GracePeriodStatus.ADD);
    assertThat(gracePeriod.isSunrushAddGracePeriod()).isTrue();
  }

  @Test
  public void testSuccess_createWithoutBillingEvent() {
    GracePeriod gracePeriod = GracePeriod.createWithoutBillingEvent(
        GracePeriodStatus.REDEMPTION, now, "TheRegistrar");
    assertThat(gracePeriod.getType()).isEqualTo(GracePeriodStatus.REDEMPTION);
    assertThat(gracePeriod.getOneTimeBillingEvent()).isNull();
    assertThat(gracePeriod.getRecurringBillingEvent()).isNull();
    assertThat(gracePeriod.getClientId()).isEqualTo("TheRegistrar");
    assertThat(gracePeriod.getExpirationTime()).isEqualTo(now);
    assertThat(gracePeriod.hasBillingEvent()).isFalse();
  }

  @Test
  public void testFailure_forBillingEvent_autoRenew() {
    thrown.expect(IllegalArgumentException.class, "autorenew");
    GracePeriod.forBillingEvent(GracePeriodStatus.AUTO_RENEW, onetime);
  }

  @Test
  public void testFailure_createForRecurring_notAutoRenew() {
    thrown.expect(IllegalArgumentException.class, "autorenew");
    GracePeriod.createForRecurring(
        GracePeriodStatus.RENEW,
        now.plusDays(1),
        "TheRegistrar",
        Key.create(Recurring.class, 12345));
  }
}
