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

package google.registry.model.contact;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.EppResourceUtils.projectResourceOntoBuilderAtTime;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.IgnoreSave;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.condition.IfNull;
import google.registry.model.EppResource;
import google.registry.model.EppResource.ForeignKeyedEppResource;
import google.registry.model.EppResource.ResourceWithTransferData;
import google.registry.model.annotations.ExternalMessagingName;
import google.registry.model.annotations.ReportedOn;
import google.registry.model.contact.PostalInfo.Type;
import google.registry.model.transfer.TransferData;
import javax.xml.bind.annotation.XmlElement;
import org.joda.time.DateTime;

/**
 * A persistable contact resource including mutable and non-mutable fields.
 *
 * @see <a href="https://tools.ietf.org/html/rfc5733">RFC 5733</a>
 */
@ReportedOn
@Entity
@ExternalMessagingName("contact")
public class ContactResource extends EppResource implements
    ForeignKeyedEppResource, ResourceWithTransferData {

  /**
   * Unique identifier for this contact.
   *
   * <p>This is only unique in the sense that for any given lifetime specified as the time range
   * from (creationTime, deletionTime) there can only be one contact in Datastore with this id.
   * However, there can be many contacts with the same id and non-overlapping lifetimes.
   */
  String contactId;

  /**
   * Localized postal info for the contact. All contained values must be representable in the 7-bit
   * US-ASCII character set. Personal info; cleared by {@link Builder#wipeOut}.
   */
  @IgnoreSave(IfNull.class)
  PostalInfo localizedPostalInfo;

  /**
   * Internationalized postal info for the contact. Personal info; cleared by
   * {@link Builder#wipeOut}.
   */
  @IgnoreSave(IfNull.class)
  PostalInfo internationalizedPostalInfo;

  /**
   * Contact name used for name searches. This is set automatically to be the internationalized
   * postal name, or if null, the localized postal name, or if that is null as well, null. Personal
   * info; cleared by {@link Builder#wipeOut}.
   */
  @Index
  String searchName;

  /** Contact’s voice number. Personal info; cleared by {@link Builder#wipeOut}. */
  @IgnoreSave(IfNull.class)
  ContactPhoneNumber voice;

  /** Contact’s fax number. Personal info; cleared by {@link Builder#wipeOut}. */
  @IgnoreSave(IfNull.class)
  ContactPhoneNumber fax;

  /** Contact’s email address. Personal info; cleared by {@link Builder#wipeOut}. */
  @IgnoreSave(IfNull.class)
  String email;

  /** Authorization info (aka transfer secret) of the contact. */
  ContactAuthInfo authInfo;

  /** Data about any pending or past transfers on this contact. */
  TransferData transferData;

  /**
   * The time that this resource was last transferred.
   *
   * <p>Can be null if the resource has never been transferred.
   */
  DateTime lastTransferTime;

  // If any new fields are added which contain personal information, make sure they are cleared by
  // the wipeOut() function, so that data is not kept around for deleted contacts.

  /** Disclosure policy. */
  Disclose disclose;

  public String getContactId() {
    return contactId;
  }

  public PostalInfo getLocalizedPostalInfo() {
    return localizedPostalInfo;
  }

  public PostalInfo getInternationalizedPostalInfo() {
    return internationalizedPostalInfo;
  }

  public ContactPhoneNumber getVoiceNumber() {
    return voice;
  }

  public ContactPhoneNumber getFaxNumber() {
    return fax;
  }

  public String getEmailAddress() {
    return email;
  }

  public ContactAuthInfo getAuthInfo() {
    return authInfo;
  }

  public Disclose getDisclose() {
    return disclose;
  }

  public final String getCurrentSponsorClientId() {
    return getPersistedCurrentSponsorClientId();
  }

  @Override
  public final TransferData getTransferData() {
    return Optional.fromNullable(transferData).or(TransferData.EMPTY);
  }

  @Override
  public DateTime getLastTransferTime() {
    return lastTransferTime;
  }

  @Override
  public String getForeignKey() {
    return contactId;
  }

  /**
   * Postal info for the contact.
   *
   * <p>The XML marshalling expects the {@link PostalInfo} objects in a list, but we can't actually
   * persist them to Datastore that way because Objectify can't handle collections of embedded
   * objects that themselves contain collections, and there's a list of streets inside. This method
   * transforms the persisted format to the XML format for marshalling.
   */
  @XmlElement(name = "postalInfo")
  public ImmutableList<PostalInfo> getPostalInfosAsList() {
    return FluentIterable
        .from(Lists.newArrayList(localizedPostalInfo, internationalizedPostalInfo))
        .filter(Predicates.notNull())
        .toList();
  }

  @Override
  public ContactResource cloneProjectedAtTime(DateTime now) {
    Builder builder = this.asBuilder();
    projectResourceOntoBuilderAtTime(this, builder, now);
    return builder.build();
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for constructing {@link ContactResource}, since it is immutable. */
  public static class Builder extends EppResource.Builder<ContactResource, Builder>
      implements BuilderWithTransferData<Builder>{

    public Builder() {}

    private Builder(ContactResource instance) {
      super(instance);
    }

    public Builder setContactId(String contactId) {
      getInstance().contactId = contactId;
      return this;
    }

    public Builder setLocalizedPostalInfo(PostalInfo localizedPostalInfo) {
      checkArgument(localizedPostalInfo == null
          || Type.LOCALIZED.equals(localizedPostalInfo.getType()));
      getInstance().localizedPostalInfo = localizedPostalInfo;
      return this;
    }

    public Builder setInternationalizedPostalInfo(PostalInfo internationalizedPostalInfo) {
      checkArgument(internationalizedPostalInfo == null
          || Type.INTERNATIONALIZED.equals(internationalizedPostalInfo.getType()));
      getInstance().internationalizedPostalInfo = internationalizedPostalInfo;
      return this;
    }

    public Builder overlayLocalizedPostalInfo(PostalInfo localizedPostalInfo) {
      return setLocalizedPostalInfo(getInstance().localizedPostalInfo == null
          ? localizedPostalInfo
          : getInstance().localizedPostalInfo.overlay(localizedPostalInfo));
    }

    public Builder overlayInternationalizedPostalInfo(PostalInfo internationalizedPostalInfo) {
      return setInternationalizedPostalInfo(getInstance().internationalizedPostalInfo == null
          ? internationalizedPostalInfo
          : getInstance().internationalizedPostalInfo.overlay(internationalizedPostalInfo));
    }

    public Builder setVoiceNumber(ContactPhoneNumber voiceNumber) {
      getInstance().voice = voiceNumber;
      return this;
    }

    public Builder setFaxNumber(ContactPhoneNumber faxNumber) {
      getInstance().fax = faxNumber;
      return this;
    }

    public Builder setEmailAddress(String emailAddress) {
      getInstance().email = emailAddress;
      return this;
    }

    public Builder setAuthInfo(ContactAuthInfo authInfo) {
      getInstance().authInfo = authInfo;
      return this;
    }

    public Builder setDisclose(Disclose disclose) {
      getInstance().disclose = disclose;
      return this;
    }

    @Override
    public Builder setTransferData(TransferData transferData) {
      getInstance().transferData = transferData;
      return this;
    }

    @Override
    public Builder setLastTransferTime(DateTime lastTransferTime) {
      getInstance().lastTransferTime = lastTransferTime;
      return thisCastToDerived();
    }

    /**
     * Remove all personally identifying information about a contact.
     *
     * <p>This should be used when deleting a contact so that the soft-deleted entity doesn't
     * contain information that the registrant requested to be deleted.
     */
    public Builder wipeOut() {
      setEmailAddress(null);
      setFaxNumber(null);
      setInternationalizedPostalInfo(null);
      setLocalizedPostalInfo(null);
      setVoiceNumber(null);
      return this;
    }

    @Override
    public ContactResource build() {
      ContactResource instance = getInstance();
      // If TransferData is totally empty, set it to null.
      if (TransferData.EMPTY.equals(instance.transferData)) {
        setTransferData(null);
      }
      // Set the searchName using the internationalized and localized postal info names.
      if ((instance.internationalizedPostalInfo != null)
          && (instance.internationalizedPostalInfo.getName() != null)) {
        instance.searchName = instance.internationalizedPostalInfo.getName();
      } else if ((instance.localizedPostalInfo != null)
          && (instance.localizedPostalInfo.getName() != null)) {
        instance.searchName = instance.localizedPostalInfo.getName();
      } else {
        instance.searchName = null;
      }
      return super.build();
    }
  }
}
