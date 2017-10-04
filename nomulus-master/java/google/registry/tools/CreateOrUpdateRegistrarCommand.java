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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Predicates.isNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static google.registry.util.DomainNameUtils.canonicalizeDomainName;
import static google.registry.util.RegistrarUtils.normalizeRegistrarName;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.joda.time.DateTimeZone.UTC;

import com.beust.jcommander.Parameter;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import google.registry.model.billing.RegistrarBillingUtils;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.Registrar.BillingMethod;
import google.registry.model.registrar.RegistrarAddress;
import google.registry.model.registry.Registry;
import google.registry.tools.params.KeyValueMapParameter.CurrencyUnitToStringMap;
import google.registry.tools.params.OptionalLongParameter;
import google.registry.tools.params.OptionalPhoneNumberParameter;
import google.registry.tools.params.OptionalStringParameter;
import google.registry.tools.params.PathParameter;
import google.registry.util.CidrAddressBlock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;

/** Shared base class for commands to create or update a {@link Registrar}. */
abstract class CreateOrUpdateRegistrarCommand extends MutatingCommand {

  @Parameter(
      description = "Client identifier of the registrar account",
      required = true)
  List<String> mainParameters;

  @Parameter(
      names = "--registrar_type",
      description = "Type of the registrar")
  Registrar.Type registrarType;

  @Nullable
  @Parameter(
      names = "--registrar_state",
      description = "Initial state of the registrar")
  Registrar.State registrarState;

  @Parameter(
      names = "--allowed_tlds",
      description = "Comma-delimited list of TLDs which the registrar is allowed to use")
  List<String> allowedTlds = new ArrayList<>();

  @Parameter(
      names = "--add_allowed_tlds",
      description = "Comma-delimited list of TLDs to add to TLDs a registrar is allowed to use")
  List<String> addAllowedTlds = new ArrayList<>();

  @Nullable
  @Parameter(
      names = "--password",
      description = "Password for the registrar account")
  String password;

  @Nullable
  @Parameter(
      names = "--name",
      description = "Name of the registrar")
  String registrarName;

  @Nullable
  @Parameter(
      names = "--email",
      description = "Email address of registrar",
      converter = OptionalStringParameter.class,
      validateWith = OptionalStringParameter.class)
  Optional<String> email;

  @Nullable
  @Parameter(
      names = "--icann_referral_email",
      description = "ICANN referral email, as specified in registrar contract")
  String icannReferralEmail;

  @Nullable
  @Parameter(
      names = "--url",
      description = "URL of registrar's website",
      converter = OptionalStringParameter.class,
      validateWith = OptionalStringParameter.class)
  private Optional<String> url;

  @Nullable
  @Parameter(
      names = "--phone",
      description = "E.164 phone number, e.g. +1.2125650666",
      converter = OptionalPhoneNumberParameter.class,
      validateWith = OptionalPhoneNumberParameter.class)
  Optional<String> phone;

  @Nullable
  @Parameter(
      names = "--fax",
      description = "E.164 fax number, e.g. +1.2125650666",
      converter = OptionalPhoneNumberParameter.class,
      validateWith = OptionalPhoneNumberParameter.class)
  Optional<String> fax;

  @Nullable
  @Parameter(
      names = "--cert_file",
      description = "File containing client certificate (X.509 PEM)",
      validateWith = PathParameter.InputFile.class)
  Path clientCertificateFilename;

  @Nullable
  @Parameter(
      names = "--cert_hash",
      description = "Hash of client certificate (SHA256 base64 no padding). Do not use this unless "
          + "you want to store ONLY the hash and not the full certificate")
  private String clientCertificateHash;

  @Nullable
  @Parameter(
      names = "--failover_cert_file",
      description = "File containing failover client certificate (X.509 PEM)",
      validateWith = PathParameter.InputFile.class)
  Path failoverClientCertificateFilename;

  @Parameter(
      names = "--ip_whitelist",
      description = "Comma-delimited list of IP ranges")
  List<String> ipWhitelist = new ArrayList<>();

  @Nullable
  @Parameter(
      names = "--iana_id",
      description = "Registrar IANA ID",
      converter = OptionalLongParameter.class,
      validateWith = OptionalLongParameter.class)
  Optional<Long> ianaId;

  @Nullable
  @Parameter(
      names = "--billing_id",
      description = "Registrar Billing ID (i.e. Oracle #)",
      converter = OptionalLongParameter.class,
      validateWith = OptionalLongParameter.class)
  private Optional<Long> billingId;

  @Nullable
  @Parameter(
    names = "--billing_account_map",
    description =
        "Registrar Billing Account key-value pairs (formatted as key=value[,key=value...]), "
            + "where key is a currency unit (USD, JPY, etc) and value is the registrar's billing "
            + "account id for that currency. During update, only the pairs that need updating need "
            + "to be provided.",
    converter = CurrencyUnitToStringMap.class,
    validateWith = CurrencyUnitToStringMap.class
  )
  private Map<CurrencyUnit, String> billingAccountMap;

  @Nullable
  @Parameter(
      names = "--billing_method",
      description = "Method by which registry bills this registrar customer")
  private BillingMethod billingMethod;

  @Nullable
  @Parameter(
      names = "--street",
      variableArity = true,
      description = "Street lines of address. Can take up to 3 lines.")
  List<String> street;

  @Nullable
  @Parameter(
      names = "--city",
      description = "City of address")
  String city;

  @Nullable
  @Parameter(
      names = "--state",
      description = "State/Province of address. The value \"null\" clears this field.")
  String state;

  @Nullable
  @Parameter(
      names = "--zip",
      description = "Postal code of address. The value \"null\" clears this field.")
  String zip;

  @Nullable
  @Parameter(
      names = "--cc",
      description = "Country code of address")
  String countryCode;

  @Nullable
  @Parameter(
      names = "--block_premium",
      description = "Whether premium name registration should be blocked on this registrar",
      arity = 1)
  private Boolean blockPremiumNames;

  @Nullable
  @Parameter(
      names = "--sync_groups",
      description = "Whether this registrar's groups should be updated at the next scheduled sync",
      arity = 1)
  private Boolean contactsRequireSyncing;

  @Nullable
  @Parameter(
      names = "--drive_id",
      description = "Id of this registrar's folder in Drive",
      converter = OptionalStringParameter.class,
      validateWith = OptionalStringParameter.class)
  Optional<String> driveFolderId;

  @Nullable
  @Parameter(
      names = "--passcode",
      description = "Telephone support passcode")
  String phonePasscode;

  @Nullable
  @Parameter(
      names = "--whois",
      description = "Hostname of registrar WHOIS server. (Default: whois.nic.google)")
  String whoisServer;

  /** Returns the existing registrar (for update) or null (for creates). */
  @Nullable
  abstract Registrar getOldRegistrar(String clientId);

  protected void initRegistrarCommand() throws Exception {}

  @Override
  protected final void init() throws Exception {
    initRegistrarCommand();
    DateTime now = DateTime.now(UTC);
    for (String clientId : mainParameters) {
      Registrar oldRegistrar = getOldRegistrar(clientId);
      Registrar.Builder builder = (oldRegistrar == null)
          ? new Registrar.Builder().setClientId(clientId)
          : oldRegistrar.asBuilder();

      if (!isNullOrEmpty(password)) {
        builder.setPassword(password);
      }
      if (!isNullOrEmpty(registrarName)) {
        builder.setRegistrarName(registrarName);
      }
      if (email != null) {
        builder.setEmailAddress(email.orNull());
      }
      if (url != null) {
        builder.setUrl(url.orNull());
      }
      if (phone != null) {
        builder.setPhoneNumber(phone.orNull());
      }
      if (fax != null) {
        builder.setFaxNumber(fax.orNull());
      }
      if (registrarType != null) {
        builder.setType(registrarType);
      }
      if (registrarState != null) {
        builder.setState(registrarState);
      }
      if (driveFolderId != null) {
        builder.setDriveFolderId(driveFolderId.orNull());
      }
      if (!allowedTlds.isEmpty()) {
        checkArgument(addAllowedTlds.isEmpty(),
            "Can't specify both --allowedTlds and --addAllowedTlds");
        ImmutableSet.Builder<String> allowedTldsBuilder = new ImmutableSet.Builder<>();
        for (String allowedTld : allowedTlds) {
          allowedTldsBuilder.add(canonicalizeDomainName(allowedTld));
        }
        builder.setAllowedTlds(allowedTldsBuilder.build());
      }
      if (!addAllowedTlds.isEmpty()) {
        ImmutableSet.Builder<String> allowedTldsBuilder = new ImmutableSet.Builder<>();
        if (oldRegistrar != null) {
          allowedTldsBuilder.addAll(oldRegistrar.getAllowedTlds());
        }
        for (String allowedTld : addAllowedTlds) {
          allowedTldsBuilder.add(canonicalizeDomainName(allowedTld));
        }
        builder.setAllowedTlds(allowedTldsBuilder.build());
      }
      if (!ipWhitelist.isEmpty()) {
        ImmutableList.Builder<CidrAddressBlock> ipWhitelistBuilder = new ImmutableList.Builder<>();
        if (!(ipWhitelist.size() == 1 && ipWhitelist.get(0).contains("null"))) {
          for (String ipRange : ipWhitelist) {
            ipWhitelistBuilder.add(CidrAddressBlock.create(ipRange));
          }
        }
        builder.setIpAddressWhitelist(ipWhitelistBuilder.build());
      }
      if (clientCertificateFilename != null) {
        String asciiCert = new String(Files.readAllBytes(clientCertificateFilename), US_ASCII);
        builder.setClientCertificate(asciiCert, now);
      }
      if (failoverClientCertificateFilename != null) {
        String asciiCert =
            new String(Files.readAllBytes(failoverClientCertificateFilename), US_ASCII);
        builder.setFailoverClientCertificate(asciiCert, now);
      }
      if (!isNullOrEmpty(clientCertificateHash)) {
        checkArgument(clientCertificateFilename == null,
            "Can't specify both --cert_hash and --cert_file");
        if ("null".equals(clientCertificateHash)) {
          clientCertificateHash = null;
        }
        builder.setClientCertificateHash(clientCertificateHash);
      }
      if (ianaId != null) {
        builder.setIanaIdentifier(ianaId.orNull());
      }
      if (billingId != null) {
        builder.setBillingIdentifier(billingId.orNull());
      }
      if (billingAccountMap != null) {
        LinkedHashMap<CurrencyUnit, String> newBillingAccountMap = new LinkedHashMap<>();
        if (oldRegistrar != null && oldRegistrar.getBillingAccountMap() != null) {
          newBillingAccountMap.putAll(oldRegistrar.getBillingAccountMap());
        }
        newBillingAccountMap.putAll(billingAccountMap);
        builder.setBillingAccountMap(newBillingAccountMap);
      }
      if (billingMethod != null) {
        if (oldRegistrar != null && !billingMethod.equals(oldRegistrar.getBillingMethod())) {
          Map<CurrencyUnit, Money> balances = RegistrarBillingUtils.loadBalance(oldRegistrar);
          for (Money balance : balances.values()) {
            checkState(balance.isZero(),
                "Refusing to change billing method on Registrar '%s' from %s to %s"
                    + " because current balance is non-zero: %s",
                clientId, oldRegistrar.getBillingMethod(), billingMethod, balances);
          }
        }
        builder.setBillingMethod(billingMethod);
      }
      List<Object> streetAddressFields = Arrays.asList(street, city, state, zip, countryCode);
      checkArgument(Iterables.any(streetAddressFields, isNull())
          == Iterables.all(streetAddressFields, isNull()),
          "Must specify all fields of address");
      if (street != null) {
        // We always set the localized address for now. That should be safe to do since it supports
        // unrestricted UTF-8.
        builder.setLocalizedAddress(new RegistrarAddress.Builder()
            .setStreet(ImmutableList.copyOf(street))
            .setCity(city)
            .setState("null".equals(state) ? null : state)
            .setZip("null".equals(zip) ? null : zip)
            .setCountryCode(countryCode)
            .build());
      }
      if (blockPremiumNames != null) {
        builder.setBlockPremiumNames(blockPremiumNames);
      }
      if (contactsRequireSyncing != null) {
        builder.setContactsRequireSyncing(contactsRequireSyncing);
      }
      if (phonePasscode != null) {
        builder.setPhonePasscode(phonePasscode);
      }
      if (icannReferralEmail != null) {
        builder.setIcannReferralEmail(icannReferralEmail);
      }
      if (whoisServer != null) {
        builder.setWhoisServer(whoisServer);
      }

      // If the registrarName is being set, verify that it is either null or it normalizes uniquely.
      String oldRegistrarName = (oldRegistrar == null) ? null : oldRegistrar.getRegistrarName();
      if (registrarName != null && !registrarName.equals(oldRegistrarName)) {
        String normalizedName = normalizeRegistrarName(registrarName);
        for (Registrar registrar : Registrar.loadAll()) {
          if (registrar.getRegistrarName() != null) {
            checkArgument(
                !normalizedName.equals(normalizeRegistrarName(registrar.getRegistrarName())),
                "The registrar name %s normalizes identically to existing registrar name %s",
                registrarName,
                registrar.getRegistrarName());
          }
        }
      }

      Registrar newRegistrar = builder.build();

      // Apply some extra validation when creating a new REAL registrar or changing the type of a
      // registrar to REAL. Leave existing REAL registrars alone.
      if (Registrar.Type.REAL.equals(registrarType)) {
        // Require a phone passcode.
        checkArgument(
            newRegistrar.getPhonePasscode() != null, "--passcode is required for REAL registrars.");
        // Check if registrar has billing account IDs for the currency of the TLDs that it is
        // allowed to register.
        ImmutableSet<CurrencyUnit> tldCurrencies =
            FluentIterable.from(newRegistrar.getAllowedTlds())
                .transform(
                    new Function<String, CurrencyUnit>() {
                      @Override
                      public CurrencyUnit apply(String tld) {
                        return Registry.get(tld).getCurrency();
                      }
                    })
                .toSet();
        Set<CurrencyUnit> currenciesWithoutBillingAccountId =
            newRegistrar.getBillingAccountMap() == null
                ? tldCurrencies
                : Sets.difference(tldCurrencies, newRegistrar.getBillingAccountMap().keySet());
        checkArgument(
            currenciesWithoutBillingAccountId.isEmpty(),
            "Need billing account map entries for currencies: %s",
            Joiner.on(' ').join(currenciesWithoutBillingAccountId));
      }

      stageEntityChange(oldRegistrar, newRegistrar);
    }
  }
}
