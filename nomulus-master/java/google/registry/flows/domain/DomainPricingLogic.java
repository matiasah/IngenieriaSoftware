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

package google.registry.flows.domain;

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.pricing.PricingEngineProxy.getDomainCreateCost;
import static google.registry.pricing.PricingEngineProxy.getDomainFeeClass;
import static google.registry.pricing.PricingEngineProxy.getDomainRenewCost;

import com.google.common.base.Optional;
import com.google.common.net.InternetDomainName;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.FlowScope;
import google.registry.flows.custom.DomainPricingCustomLogic;
import google.registry.flows.custom.DomainPricingCustomLogic.ApplicationUpdatePriceParameters;
import google.registry.flows.custom.DomainPricingCustomLogic.CreatePriceParameters;
import google.registry.flows.custom.DomainPricingCustomLogic.RenewPriceParameters;
import google.registry.flows.custom.DomainPricingCustomLogic.RestorePriceParameters;
import google.registry.flows.custom.DomainPricingCustomLogic.TransferPriceParameters;
import google.registry.flows.custom.DomainPricingCustomLogic.UpdatePriceParameters;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.LrpTokenEntity;
import google.registry.model.domain.fee.BaseFee;
import google.registry.model.domain.fee.BaseFee.FeeType;
import google.registry.model.domain.fee.Fee;
import google.registry.model.registry.Registry;
import javax.inject.Inject;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * Provides pricing for create, renew, etc, operations, with call-outs that can be customized by
 * providing a {@link DomainPricingCustomLogic} implementation that operates on cross-TLD or per-TLD
 * logic.
 */
@FlowScope
public final class DomainPricingLogic {

  @Inject DomainPricingCustomLogic customLogic;

  @Inject
  DomainPricingLogic() {}

  /** Returns a new create price for the Pricer. */
  public FeesAndCredits getCreatePrice(
      Registry registry, String domainName, DateTime date, int years) throws EppException {
    CurrencyUnit currency = registry.getCurrency();

    // Get the vanilla create cost.
    BaseFee createFeeOrCredit =
        Fee.create(getDomainCreateCost(domainName, date, years).getAmount(), FeeType.CREATE);

    // Create fees for the cost and the EAP fee, if any.
    Fee eapFee = registry.getEapFeeFor(date);
    FeesAndCredits.Builder feesBuilder =
        new FeesAndCredits.Builder().setCurrency(currency).addFeeOrCredit(createFeeOrCredit);
    if (!eapFee.hasZeroCost()) {
      feesBuilder.addFeeOrCredit(eapFee);
    }

    // Apply custom logic to the create fee, if any.
    return customLogic.customizeCreatePrice(
        CreatePriceParameters.newBuilder()
            .setFeesAndCredits(feesBuilder.build())
            .setRegistry(registry)
            .setDomainName(InternetDomainName.from(domainName))
            .setAsOfDate(date)
            .setYears(years)
            .build());

  }

  // TODO: (b/33000134) clean up the rest of the pricing calls.

  /** Returns a new renew price for the pricer. */
  @SuppressWarnings("unused")
  public FeesAndCredits getRenewPrice(
      Registry registry,
      String domainName,
      DateTime date,
      int years)
      throws EppException {
    Money renewCost = getDomainRenewCost(domainName, date, years);
    return customLogic.customizeRenewPrice(
        RenewPriceParameters.newBuilder()
            .setFeesAndCredits(
                new FeesAndCredits.Builder()
                    .setCurrency(registry.getCurrency())
                    .addFeeOrCredit(Fee.create(renewCost.getAmount(), FeeType.RENEW))
                    .build())
            .setRegistry(registry)
            .setDomainName(InternetDomainName.from(domainName))
            .setAsOfDate(date)
            .setYears(years)
            .build());
  }

  /** Returns a new restore price for the pricer. */
  public FeesAndCredits getRestorePrice(Registry registry, String domainName, DateTime date)
      throws EppException {
    FeesAndCredits feesAndCredits =
        new FeesAndCredits.Builder()
            .setCurrency(registry.getCurrency())
            .addFeeOrCredit(
                Fee.create(getDomainRenewCost(domainName, date, 1).getAmount(), FeeType.RENEW))
            .addFeeOrCredit(
                Fee.create(registry.getStandardRestoreCost().getAmount(), FeeType.RESTORE))
            .build();
    return customLogic.customizeRestorePrice(
        RestorePriceParameters.newBuilder()
            .setFeesAndCredits(feesAndCredits)
            .setRegistry(registry)
            .setDomainName(InternetDomainName.from(domainName))
            .setAsOfDate(date)
            .build());
  }

  /** Returns a new transfer price for the pricer. */
  public FeesAndCredits getTransferPrice(Registry registry, String domainName, DateTime date)
      throws EppException {
    Money renewCost = getDomainRenewCost(domainName, date, 1);
    return customLogic.customizeTransferPrice(
        TransferPriceParameters.newBuilder()
            .setFeesAndCredits(
                new FeesAndCredits.Builder()
                    .setCurrency(registry.getCurrency())
                    .addFeeOrCredit(Fee.create(renewCost.getAmount(), FeeType.RENEW))
                    .build())
            .setRegistry(registry)
            .setDomainName(InternetDomainName.from(domainName))
            .setAsOfDate(date)
            .build());
  }

  /** Returns a new update price for the pricer. */
  public FeesAndCredits getUpdatePrice(Registry registry, String domainName, DateTime date)
      throws EppException {
    CurrencyUnit currency = registry.getCurrency();
    BaseFee feeOrCredit =
        Fee.create(Money.zero(registry.getCurrency()).getAmount(), FeeType.UPDATE);
    return customLogic.customizeUpdatePrice(
        UpdatePriceParameters.newBuilder()
            .setFeesAndCredits(
                new FeesAndCredits.Builder()
                    .setCurrency(currency)
                    .addFeeOrCredit(feeOrCredit)
                    .build())
            .setRegistry(registry)
            .setDomainName(InternetDomainName.from(domainName))
            .setAsOfDate(date)
            .build());
  }

  /** Returns a new domain application update price for the pricer. */
  @SuppressWarnings("unused")
  public FeesAndCredits getApplicationUpdatePrice(
      Registry registry, DomainApplication application, DateTime date) throws EppException {
    BaseFee feeOrCredit =
        Fee.create(Money.zero(registry.getCurrency()).getAmount(), FeeType.UPDATE);
    return customLogic.customizeApplicationUpdatePrice(
        ApplicationUpdatePriceParameters.newBuilder()
            .setFeesAndCredits(
                new FeesAndCredits.Builder()
                    .setCurrency(registry.getCurrency())
                    .setFeesAndCredits(feeOrCredit)
                    .build())
            .setRegistry(registry)
            .setDomainApplication(application)
            .setAsOfDate(date)
            .build());
  }

  /** Returns the fee class for a given domain and date. */
  public Optional<String> getFeeClass(String domainName, DateTime date) {
    return getDomainFeeClass(domainName, date);
  }

  /**
   * Checks whether an LRP token String maps to a valid {@link LrpTokenEntity} for the domain name's
   * TLD, and return that entity (wrapped in an {@link Optional}) if one exists.
   *
   * <p>This method has no knowledge of whether or not an auth code (interpreted here as an LRP
   * token) has already been checked against the reserved list for QLP (anchor tenant), as auth
   * codes are used for both types of registrations.
   */
  public static Optional<LrpTokenEntity> getMatchingLrpToken(
      String lrpToken, InternetDomainName domainName) {
    // Note that until the actual per-TLD logic is built out, what's being done here is a basic
    // domain-name-to-assignee match.
    if (!lrpToken.isEmpty()) {
      LrpTokenEntity token = ofy().load().key(Key.create(LrpTokenEntity.class, lrpToken)).now();
      if (token != null
          && token.getAssignee().equalsIgnoreCase(domainName.toString())
          && token.getRedemptionHistoryEntry() == null
          && token.getValidTlds().contains(domainName.parent().toString())) {
        return Optional.of(token);
      }
    }
    return Optional.<LrpTokenEntity>absent();
  }
}
