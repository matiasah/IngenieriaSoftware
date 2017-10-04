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

package google.registry.whois;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.tryFind;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.isNullOrEmpty;
import static google.registry.xml.UtcDateTimeAdapter.getFormattedString;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.googlecode.objectify.Key;
import google.registry.model.contact.ContactPhoneNumber;
import google.registry.model.contact.ContactResource;
import google.registry.model.contact.PostalInfo;
import google.registry.model.domain.DesignatedContact;
import google.registry.model.domain.DesignatedContact.Type;
import google.registry.model.domain.DomainResource;
import google.registry.model.domain.GracePeriod;
import google.registry.model.eppcommon.StatusValue;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.translators.EnumToAttributeAdapter.EppEnum;
import google.registry.util.FormattingLogger;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/** Represents a WHOIS response to a domain query. */
final class DomainWhoisResponse extends WhoisResponseImpl {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** Prefix for status value URLs. */
  private static final String ICANN_STATUS_URL_PREFIX = "https://icann.org/epp#";

  /** Message required to be appended to all domain WHOIS responses. */
  private static final String ICANN_AWIP_INFO_MESSAGE =
      "For more information on Whois status codes, please visit https://icann.org/epp\r\n";

  /** Domain which was the target of this WHOIS command. */
  private final DomainResource domain;

  /** Creates new WHOIS domain response on the given domain. */
  DomainWhoisResponse(DomainResource domain, DateTime timestamp) {
    super(timestamp);
    this.domain = checkNotNull(domain, "domain");
  }

  @Override
  public WhoisResponseResults getResponse(final boolean preferUnicode, String disclaimer) {
    Optional<Registrar> registrarOptional =
        Registrar.loadByClientIdCached(domain.getCurrentSponsorClientId());
    checkState(
        registrarOptional.isPresent(),
        "Could not load registrar %s",
        domain.getCurrentSponsorClientId());
    Registrar registrar = registrarOptional.get();
    Optional<RegistrarContact> abuseContact =
        Iterables.tryFind(
            registrar.getContacts(),
            new Predicate<RegistrarContact>() {
              @Override
              public boolean apply(@Nullable RegistrarContact contact) {
                return contact.getVisibleInDomainWhoisAsAbuse();
              }
            });
    String plaintext =
        new DomainEmitter()
            .emitField(
                "Domain Name",
                maybeFormatHostname(domain.getFullyQualifiedDomainName(), preferUnicode))
            .emitField("Registry Domain ID", domain.getRepoId())
            .emitField("Registrar WHOIS Server", registrar.getWhoisServer())
            .emitField("Registrar URL", registrar.getReferralUrl())
            .emitFieldIfDefined("Updated Date", getFormattedString(domain.getLastEppUpdateTime()))
            .emitField("Creation Date", getFormattedString(domain.getCreationTime()))
            .emitField(
                "Registry Expiry Date", getFormattedString(domain.getRegistrationExpirationTime()))
            .emitField("Registrar", registrar.getRegistrarName())
            .emitField(
                "Registrar IANA ID",
                Objects.toString(registrar.getIanaIdentifier(), ""))
            // Email address is a required field for registrar contacts. Therefore as long as there
            // is an abuse contact, we can get an email address from it.
            .emitFieldIfDefined(
                "Registrar Abuse Contact Email",
                abuseContact.isPresent() ? abuseContact.get().getEmailAddress() : null)
            .emitFieldIfDefined(
                "Registrar Abuse Contact Phone",
                abuseContact.isPresent() ? abuseContact.get().getPhoneNumber() : null)
            .emitStatusValues(domain.getStatusValues(), domain.getGracePeriods())
            .emitContact("Registrant", domain.getRegistrant(), preferUnicode)
            .emitContact("Admin", getContactReference(Type.ADMIN), preferUnicode)
            .emitContact("Tech", getContactReference(Type.TECH), preferUnicode)
            .emitContact("Billing", getContactReference(Type.BILLING), preferUnicode)
            .emitSet(
                "Name Server",
                domain.loadNameserverFullyQualifiedHostNames(),
                new Function<String, String>() {
                  @Override
                  public String apply(String hostName) {
                    return maybeFormatHostname(hostName, preferUnicode);
                  }
                })
            .emitField(
                "DNSSEC", isNullOrEmpty(domain.getDsData()) ? "unsigned" : "signedDelegation")
            .emitWicfLink()
            .emitLastUpdated(getTimestamp())
            .emitAwipMessage()
            .emitFooter(disclaimer)
            .toString();
    return WhoisResponseResults.create(plaintext, 1);
  }

  /** Returns the contact of the given type, or null if it does not exist. */
  @Nullable
  private Key<ContactResource> getContactReference(final Type type) {
    Optional<DesignatedContact> contactOfType = tryFind(domain.getContacts(),
        new Predicate<DesignatedContact>() {
          @Override
          public boolean apply(DesignatedContact d) {
            return d.getType() == type;
          }});
    return contactOfType.isPresent() ? contactOfType.get().getContactKey() : null;
  }

  /** Output emitter with logic for domains. */
  class DomainEmitter extends Emitter<DomainEmitter> {
    DomainEmitter emitPhone(
        String contactType, String title, @Nullable ContactPhoneNumber phoneNumber) {
      if (phoneNumber == null) {
        return this;
      }
      return emitFieldIfDefined(ImmutableList.of(contactType, title), phoneNumber.getPhoneNumber())
          .emitFieldIfDefined(
              ImmutableList.of(contactType, title, "Ext"), phoneNumber.getExtension());
    }

    /** Emit the contact entry of the given type. */
    DomainEmitter emitContact(
        String contactType,
        @Nullable Key<ContactResource> contact,
        boolean preferUnicode) {
      if (contact == null) {
        return this;
      }
      // If we refer to a contact that doesn't exist, that's a bug. It means referential integrity
      // has somehow been broken. We skip the rest of this contact, but log it to hopefully bring it
      // someone's attention.
      ContactResource contactResource = ofy().load().key(contact).now();
      if (contactResource == null) {
        logger.severefmt("(BUG) Broken reference found from domain %s to contact %s",
            domain.getFullyQualifiedDomainName(), contact);
        return this;
      }
      // ICANN Consistent Labeling & Display policy requires that this be the ROID.
      emitField(ImmutableList.of("Registry", contactType, "ID"), contactResource.getRepoId());
      PostalInfo postalInfo = chooseByUnicodePreference(
          preferUnicode,
          contactResource.getLocalizedPostalInfo(),
          contactResource.getInternationalizedPostalInfo());
      if (postalInfo != null) {
        emitFieldIfDefined(ImmutableList.of(contactType, "Name"), postalInfo.getName());
        emitFieldIfDefined(ImmutableList.of(contactType, "Organization"), postalInfo.getOrg());
        emitAddress(contactType, postalInfo.getAddress());
      }
      return emitPhone(contactType, "Phone", contactResource.getVoiceNumber())
          .emitPhone(contactType, "Fax", contactResource.getFaxNumber())
          .emitField(ImmutableList.of(contactType, "Email"), contactResource.getEmailAddress());
    }

    /** Emits status values and grace periods as a set, in the AWIP format. */
    DomainEmitter emitStatusValues(
        Set<StatusValue> statusValues, Set<GracePeriod> gracePeriods) {
      ImmutableSet.Builder<EppEnum> combinedStatuses = new ImmutableSet.Builder<>();
      combinedStatuses.addAll(statusValues);
      for (GracePeriod gracePeriod : gracePeriods) {
        combinedStatuses.add(gracePeriod.getType());
      }
      return emitSet(
          "Domain Status",
          combinedStatuses.build(),
          new Function<EppEnum, String>() {
            @Override
            public String apply(EppEnum status) {
              String xmlName = status.getXmlName();
              return String.format("%s %s%s", xmlName, ICANN_STATUS_URL_PREFIX, xmlName);
            }});
    }

    /** Emits the message that AWIP requires accompany all domain WHOIS responses. */
    DomainEmitter emitAwipMessage() {
      return emitRawLine(ICANN_AWIP_INFO_MESSAGE);
    }
  }
}
