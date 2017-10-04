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

package google.registry.model.pricing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.emptyToNull;
import static google.registry.model.registry.Registry.TldState.SUNRISE;
import static google.registry.model.registry.label.PremiumListUtils.getPremiumPrice;
import static google.registry.model.registry.label.ReservationType.NAME_COLLISION;
import static google.registry.model.registry.label.ReservedList.getReservationTypes;
import static google.registry.util.DomainNameUtils.getTldFromDomainName;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.net.InternetDomainName;
import google.registry.model.registry.Registry;
import javax.inject.Inject;
import org.joda.money.Money;
import org.joda.time.DateTime;

/** A premium list pricing engine that stores static pricing information in Datastore entities. */
public final class StaticPremiumListPricingEngine implements PremiumPricingEngine {

  /** The name of the pricing engine, as used in {@code Registry.pricingEngineClassName}. */
  public static final String NAME = "google.registry.model.pricing.StaticPremiumListPricingEngine";

  @Inject StaticPremiumListPricingEngine() {}

  @Override
  public DomainPrices getDomainPrices(String fullyQualifiedDomainName, DateTime priceTime) {
    String tld = getTldFromDomainName(fullyQualifiedDomainName);
    String label = InternetDomainName.from(fullyQualifiedDomainName).parts().get(0);
    Registry registry = Registry.get(checkNotNull(tld, "tld"));
    Optional<Money> premiumPrice = getPremiumPrice(label, registry);
    boolean isNameCollisionInSunrise =
        registry.getTldState(priceTime).equals(SUNRISE)
            && getReservationTypes(label, tld).contains(NAME_COLLISION);
    String feeClass = emptyToNull(Joiner.on('-').skipNulls().join(
            premiumPrice.isPresent() ? "premium" : null,
            isNameCollisionInSunrise ? "collision" : null));
    return DomainPrices.create(
        premiumPrice.isPresent(),
        premiumPrice.or(registry.getStandardCreateCost()),
        premiumPrice.or(registry.getStandardRenewCost(priceTime)),
        Optional.<String>fromNullable(feeClass));
  }
}
