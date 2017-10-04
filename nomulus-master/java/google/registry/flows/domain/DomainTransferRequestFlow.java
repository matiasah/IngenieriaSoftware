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

import static google.registry.flows.FlowUtils.validateClientIsLoggedIn;
import static google.registry.flows.ResourceFlowUtils.loadAndVerifyExistence;
import static google.registry.flows.ResourceFlowUtils.verifyAuthInfo;
import static google.registry.flows.ResourceFlowUtils.verifyAuthInfoPresentForResourceTransfer;
import static google.registry.flows.ResourceFlowUtils.verifyNoDisallowedStatuses;
import static google.registry.flows.domain.DomainFlowUtils.checkAllowedAccessToTld;
import static google.registry.flows.domain.DomainFlowUtils.updateAutorenewRecurrenceEndTime;
import static google.registry.flows.domain.DomainFlowUtils.validateFeeChallenge;
import static google.registry.flows.domain.DomainFlowUtils.verifyPremiumNameIsNotBlocked;
import static google.registry.flows.domain.DomainFlowUtils.verifyUnitIsYears;
import static google.registry.flows.domain.DomainTransferUtils.createLosingTransferPollMessage;
import static google.registry.flows.domain.DomainTransferUtils.createPendingTransferData;
import static google.registry.flows.domain.DomainTransferUtils.createTransferResponse;
import static google.registry.flows.domain.DomainTransferUtils.createTransferServerApproveEntities;
import static google.registry.model.domain.DomainResource.extendRegistrationWithCap;
import static google.registry.model.eppoutput.Result.Code.SUCCESS_WITH_ACTION_PENDING;
import static google.registry.model.ofy.ObjectifyService.ofy;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.objectify.Key;
import google.registry.flows.EppException;
import google.registry.flows.ExtensionManager;
import google.registry.flows.FlowModule.ClientId;
import google.registry.flows.FlowModule.Superuser;
import google.registry.flows.FlowModule.TargetId;
import google.registry.flows.TransactionalFlow;
import google.registry.flows.annotations.ReportingSpec;
import google.registry.flows.exceptions.AlreadyPendingTransferException;
import google.registry.flows.exceptions.InvalidTransferPeriodValueException;
import google.registry.flows.exceptions.ObjectAlreadySponsoredException;
import google.registry.flows.exceptions.SuperuserExtensionAndAutorenewGracePeriodException;
import google.registry.flows.exceptions.TransferPeriodMustBeOneYearException;
import google.registry.flows.exceptions.TransferPeriodZeroAndFeeTransferExtensionException;
import google.registry.model.domain.DomainCommand.Transfer;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.Period;
import google.registry.model.domain.fee.FeeTransferCommandExtension;
import google.registry.model.domain.fee.FeeTransformResponseExtension;
import google.registry.model.domain.metadata.MetadataExtension;
import google.registry.model.domain.rgp.GracePeriodStatus;
import google.registry.model.domain.superuser.DomainTransferRequestSuperuserExtension;
import google.registry.model.eppcommon.AuthInfo;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.ResourceCommand;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.poll.PollMessage;
import google.registry.model.registry.Registry;
import google.registry.model.reporting.DomainTransactionRecord;
import google.registry.model.reporting.DomainTransactionRecord.TransactionReportField;
import google.registry.model.reporting.HistoryEntry;
import google.registry.model.reporting.IcannReportingTypes.ActivityReportField;
import google.registry.model.transfer.TransferData;
import google.registry.model.transfer.TransferData.Builder;
import google.registry.model.transfer.TransferData.TransferServerApproveEntity;
import google.registry.model.transfer.TransferResponse.DomainTransferResponse;
import google.registry.model.transfer.TransferStatus;
import javax.inject.Inject;
import org.joda.money.Money;
import org.joda.time.DateTime;

/**
 * An EPP flow that requests a transfer on a domain.
 *
 * <p>The "gaining" registrar requests a transfer from the "losing" (aka current) registrar. The
 * losing registrar has a "transfer" time period to respond (by default five days) after which the
 * transfer is automatically approved. Within that window, the transfer might be approved explicitly
 * by the losing registrar or rejected, and the gaining registrar can also cancel the transfer
 * request.
 *
 * <p>When a transfer is requested, poll messages and billing events are saved to Datastore with
 * timestamps such that they only become active when the server-approval period passes. Keys to
 * these speculative objects are saved in the domain's transfer data, and on explicit approval,
 * rejection or cancellation of the request, they will be deleted (and in the approval case,
 * replaced with new ones with the correct approval time).
 *
 * @error {@link google.registry.flows.ResourceFlowUtils.BadAuthInfoForResourceException}
 * @error {@link google.registry.flows.ResourceFlowUtils.ResourceDoesNotExistException}
 * @error {@link google.registry.flows.exceptions.AlreadyPendingTransferException}
 * @error {@link google.registry.flows.exceptions.MissingTransferRequestAuthInfoException}
 * @error {@link google.registry.flows.exceptions.ObjectAlreadySponsoredException}
 * @error {@link google.registry.flows.exceptions.ResourceStatusProhibitsOperationException}
 * @error {@link google.registry.flows.exceptions.SuperuserExtensionAndAutorenewGracePeriodException}
 * @error {@link google.registry.flows.exceptions.TransferPeriodMustBeOneYearException}
 * @error {@link InvalidTransferPeriodValueException}
 * @error {@link google.registry.flows.exceptions.TransferPeriodZeroAndFeeTransferExtensionException}
 * @error {@link DomainFlowUtils.BadPeriodUnitException}
 * @error {@link DomainFlowUtils.CurrencyUnitMismatchException}
 * @error {@link DomainFlowUtils.CurrencyValueScaleException}
 * @error {@link DomainFlowUtils.FeesMismatchException}
 * @error {@link DomainFlowUtils.FeesRequiredForPremiumNameException}
 * @error {@link DomainFlowUtils.NotAuthorizedForTldException}
 * @error {@link DomainFlowUtils.PremiumNameBlockedException}
 * @error {@link DomainFlowUtils.UnsupportedFeeAttributeException}
 */
@ReportingSpec(ActivityReportField.DOMAIN_TRANSFER_REQUEST)
public final class DomainTransferRequestFlow implements TransactionalFlow {

  private static final ImmutableSet<StatusValue> DISALLOWED_STATUSES = ImmutableSet.of(
      StatusValue.CLIENT_TRANSFER_PROHIBITED,
      StatusValue.PENDING_DELETE,
      StatusValue.SERVER_TRANSFER_PROHIBITED);

  @Inject ResourceCommand resourceCommand;
  @Inject ExtensionManager extensionManager;
  @Inject EppInput eppInput;
  @Inject Optional<AuthInfo> authInfo;
  @Inject @ClientId String gainingClientId;
  @Inject @TargetId String targetId;
  @Inject @Superuser boolean isSuperuser;
  @Inject HistoryEntry.Builder historyBuilder;
  @Inject Trid trid;
  @Inject EppResponse.Builder responseBuilder;
  @Inject DomainPricingLogic pricingLogic;
  @Inject DomainTransferRequestFlow() {}

  @Override
  public final EppResponse run() throws EppException {
    extensionManager.register(
        DomainTransferRequestSuperuserExtension.class,
        FeeTransferCommandExtension.class,
        MetadataExtension.class);
    extensionManager.validate();
    validateClientIsLoggedIn(gainingClientId);
    DateTime now = ofy().getTransactionTime();
    DomainResource existingDomain = loadAndVerifyExistence(DomainResource.class, targetId, now);
    DomainTransferRequestSuperuserExtension superuserExtension =
        eppInput.getSingleExtension(DomainTransferRequestSuperuserExtension.class);
    Period period =
        (superuserExtension == null)
            ? ((Transfer) resourceCommand).getPeriod()
            : superuserExtension.getRenewalPeriod();
    verifyTransferAllowed(existingDomain, period, now, superuserExtension);
    String tld = existingDomain.getTld();
    Registry registry = Registry.get(tld);
    // An optional extension from the client specifying what they think the transfer should cost.
    FeeTransferCommandExtension feeTransfer =
        eppInput.getSingleExtension(FeeTransferCommandExtension.class);
    if (period.getValue() == 0 && feeTransfer != null) {
      // If the period is zero, then there is no transfer billing event, so using the fee transfer
      // extension does not make sense.
      throw new TransferPeriodZeroAndFeeTransferExtensionException();
    }
    // If the period is zero, then there is no fee for the transfer.
    Optional<FeesAndCredits> feesAndCredits =
        (period.getValue() == 0)
            ? Optional.<FeesAndCredits>absent()
            : Optional.of(pricingLogic.getTransferPrice(registry, targetId, now));
    if (feesAndCredits.isPresent()) {
      validateFeeChallenge(targetId, tld, now, feeTransfer, feesAndCredits.get());
    }
    HistoryEntry historyEntry = buildHistoryEntry(existingDomain, registry, now, period);
    DateTime automaticTransferTime =
        (superuserExtension == null)
            ? now.plus(registry.getAutomaticTransferLength())
            : now.plusDays(superuserExtension.getAutomaticTransferLength());
    // If the domain will be in the auto-renew grace period at the moment of transfer, the transfer
    // will subsume the autorenew, so we don't add the normal extra year from the transfer.
    // The gaining registrar is still billed for the extra year; the losing registrar will get a
    // cancellation for the autorenew written out within createTransferServerApproveEntities().
    //
    // See b/19430703#comment17 and https://www.icann.org/news/advisory-2002-06-06-en for the
    // policy documentation for transfers subsuming autorenews within the autorenew grace period.
    int extraYears = period.getValue();
    DomainResource domainAtTransferTime =
        existingDomain.cloneProjectedAtTime(automaticTransferTime);
    if (!domainAtTransferTime.getGracePeriodsOfType(GracePeriodStatus.AUTO_RENEW).isEmpty()) {
      if (superuserExtension != null) {
        // We don't allow the superuser extension for domains in the auto renew grace period
        throw new SuperuserExtensionAndAutorenewGracePeriodException();
      }
      extraYears = 0;
    }
    // The new expiration time if there is a server approval.
    DateTime serverApproveNewExpirationTime =
        extendRegistrationWithCap(
            automaticTransferTime,
            domainAtTransferTime.getRegistrationExpirationTime(),
            extraYears);
    // Create speculative entities in anticipation of an automatic server approval.
    ImmutableSet<TransferServerApproveEntity> serverApproveEntities =
        createTransferServerApproveEntities(
            automaticTransferTime,
            serverApproveNewExpirationTime,
            historyEntry,
            existingDomain,
            trid,
            gainingClientId,
            (feesAndCredits.isPresent())
                ? Optional.of(feesAndCredits.get().getTotalCost())
                : Optional.<Money>absent(),
            now);
    // Create the transfer data that represents the pending transfer.
    TransferData pendingTransferData =
        createPendingTransferData(
            createTransferDataBuilder(existingDomain, automaticTransferTime, now),
            serverApproveEntities,
            period);
    // Create a poll message to notify the losing registrar that a transfer was requested.
    PollMessage requestPollMessage = createLosingTransferPollMessage(
        targetId, pendingTransferData, serverApproveNewExpirationTime, historyEntry)
            .asBuilder().setEventTime(now).build();
    // End the old autorenew event and poll message at the implicit transfer time. This may delete
    // the poll message if it has no events left. Note that if the automatic transfer succeeds, then
    // cloneProjectedAtTime() will replace these old autorenew entities with the server approve ones
    // that we've created in this flow and stored in pendingTransferData.
    updateAutorenewRecurrenceEndTime(existingDomain, automaticTransferTime);
    DomainResource newDomain = existingDomain.asBuilder()
        .setTransferData(pendingTransferData)
        .addStatusValue(StatusValue.PENDING_TRANSFER)
        .build();
    ofy().save()
        .entities(new ImmutableSet.Builder<>()
            .add(newDomain, historyEntry, requestPollMessage)
            .addAll(serverApproveEntities)
            .build())
        .now();
    return responseBuilder
        .setResultFromCode(SUCCESS_WITH_ACTION_PENDING)
        .setResData(createResponse(period, existingDomain, newDomain, now))
        .setExtensions(createResponseExtensions(feesAndCredits, feeTransfer))
        .build();
  }

  private void verifyTransferAllowed(
      DomainResource existingDomain,
      Period period,
      DateTime now,
      final DomainTransferRequestSuperuserExtension superuserExtension)
      throws EppException {
    verifyNoDisallowedStatuses(existingDomain, DISALLOWED_STATUSES);
    verifyAuthInfoPresentForResourceTransfer(authInfo);
    verifyAuthInfo(authInfo.get(), existingDomain);
    // Verify that the resource does not already have a pending transfer.
    if (TransferStatus.PENDING.equals(existingDomain.getTransferData().getTransferStatus())) {
      throw new AlreadyPendingTransferException(targetId);
    }
    // Verify that this client doesn't already sponsor this resource.
    if (gainingClientId.equals(existingDomain.getCurrentSponsorClientId())) {
      throw new ObjectAlreadySponsoredException();
    }
    verifyTransferPeriod(period, superuserExtension);
    if (!isSuperuser) {
      checkAllowedAccessToTld(gainingClientId, existingDomain.getTld());
      verifyPremiumNameIsNotBlocked(targetId, now, gainingClientId);
    }
  }

  /**
   * Verify that the transfer period is one year. If the superuser extension is being used, then it
   * can be zero.
   *
   * <p>Restricting transfers to one year is seemingly required by ICANN's <a
   * href="https://www.icann.org/resources/pages/policy-2012-03-07-en">Policy on Transfer of
   * Registrations between Registrars</a>, section A.8. It states that "the completion by Registry
   * Operator of a holder-authorized transfer under this Part A shall result in a one-year extension
   * of the existing registration, provided that in no event shall the total unexpired term of a
   * registration exceed ten (10) years."
   *
   * <p>Even if not required, this policy is desirable because it dramatically simplifies the logic
   * in transfer flows. Registrars appear to never request 2+ year transfers in practice, and they
   * can always decompose an multi-year transfer into a 1-year transfer followed by a manual renewal
   * afterwards. The <a href="https://tools.ietf.org/html/rfc5731#section-3.2.4">EPP Domain RFC,
   * section 3.2.4</a> says about EPP transfer periods that "the number of units available MAY be
   * subject to limits imposed by the server" so we're just limiting the units to one.
   *
   * <p>Note that clients can omit the period element from the transfer EPP entirely, but then it
   * will simply default to one year.
   */
  private static void verifyTransferPeriod(
      Period period, DomainTransferRequestSuperuserExtension superuserExtension)
      throws EppException {
    verifyUnitIsYears(period);
    if (superuserExtension == null) {
      // If the superuser extension is not being used, then the period can only be one.
      if (period.getValue() != 1) {
        throw new TransferPeriodMustBeOneYearException();
      }
    } else {
      // If the superuser extension is being used, then the period can be one or zero.
      if (period.getValue() != 1 && period.getValue() != 0) {
        throw new InvalidTransferPeriodValueException();
      }
    }
  }

  private HistoryEntry buildHistoryEntry(
      DomainResource existingDomain, Registry registry, DateTime now, Period period) {
    return historyBuilder
        .setType(HistoryEntry.Type.DOMAIN_TRANSFER_REQUEST)
        .setOtherClientId(existingDomain.getCurrentSponsorClientId())
        .setPeriod(period)
        .setModificationTime(now)
        .setParent(Key.create(existingDomain))
        .setDomainTransactionRecords(
            ImmutableSet.of(
                DomainTransactionRecord.create(
                    registry.getTldStr(),
                    now.plus(registry.getAutomaticTransferLength())
                        .plus(registry.getTransferGracePeriodLength()),
                    TransactionReportField.TRANSFER_SUCCESSFUL,
                    1)))
        .build();
  }

  private Builder createTransferDataBuilder(
      DomainResource existingDomain, DateTime automaticTransferTime, DateTime now) {
    return new TransferData.Builder()
        .setTransferRequestTrid(trid)
        .setTransferRequestTime(now)
        .setGainingClientId(gainingClientId)
        .setLosingClientId(existingDomain.getCurrentSponsorClientId())
        .setPendingTransferExpirationTime(automaticTransferTime);
  }

  private DomainTransferResponse createResponse(
      Period period, DomainResource existingDomain, DomainResource newDomain, DateTime now) {
    // If the registration were approved this instant, this is what the new expiration would be,
    // because we cap at 10 years from the moment of approval. This is different than the server
    // approval new expiration time, which is capped at 10 years from the server approve time.
    DateTime approveNowExtendedRegistrationTime = extendRegistrationWithCap(
        now,
        existingDomain.getRegistrationExpirationTime(),
        period.getValue());
    return createTransferResponse(
        targetId, newDomain.getTransferData(), approveNowExtendedRegistrationTime);
  }

  private static ImmutableList<FeeTransformResponseExtension> createResponseExtensions(
      Optional<FeesAndCredits> feesAndCredits, FeeTransferCommandExtension feeTransfer) {
    return (feeTransfer == null || !feesAndCredits.isPresent())
        ? ImmutableList.<FeeTransformResponseExtension>of()
        : ImmutableList.of(feeTransfer.createResponseBuilder()
            .setFees(feesAndCredits.get().getFees())
            .setCredits(feesAndCredits.get().getCredits())
            .setCurrency(feesAndCredits.get().getCurrency())
            .build());
  }
}
