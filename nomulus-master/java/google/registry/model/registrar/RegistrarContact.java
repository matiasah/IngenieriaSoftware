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

package google.registry.model.registrar;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Sets.difference;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.nullToEmptyImmutableSortedCopy;
import static google.registry.util.ObjectifyUtils.OBJECTS_TO_KEYS;

import com.google.common.base.Enums;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.VoidWork;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Parent;
import google.registry.model.Buildable;
import google.registry.model.ImmutableObject;
import google.registry.model.JsonMapBuilder;
import google.registry.model.Jsonifiable;
import google.registry.model.annotations.ReportedOn;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * A contact for a Registrar. Note, equality, hashCode and comparable have been overridden to only
 * enable key equality.
 *
 * <p>IMPORTANT NOTE: Any time that you change, update, or delete RegistrarContact entities, you
 * *MUST* also modify the persisted Registrar entity with {@link Registrar#contactsRequireSyncing}
 * set to true.
 */
@ReportedOn
@Entity
public class RegistrarContact extends ImmutableObject implements Jsonifiable {

  @Parent
  Key<Registrar> parent;

  /**
   * Registrar contacts types for partner communication tracking.
   *
   * <p><b>Note:</b> These types only matter to the registry. They are not meant to be used for
   * WHOIS or RDAP results.
   */
  public enum Type {
    ABUSE("abuse", true),
    ADMIN("primary", true),
    BILLING("billing", true),
    LEGAL("legal", true),
    MARKETING("marketing", false),
    TECH("technical", true),
    WHOIS("whois-inquiry", true);

    private final String displayName;

    private final boolean required;

    public String getDisplayName() {
      return displayName;
    }

    public boolean isRequired() {
      return required;
    }

    private Type(String display, boolean required) {
      this.displayName = display;
      this.required = required;
    }
  }

  /** The name of the contact. */
  String name;

  /** The email address of the contact. */
  @Id
  String emailAddress;

  /** The voice number of the contact. */
  String phoneNumber;

  /** The fax number of the contact. */
  String faxNumber;

  /**
   * Multiple types are used to associate the registrar contact with
   * various mailing groups. This data is internal to the registry.
   */
  Set<Type> types;

  /**
   * A GAE user ID allowed to act as this registrar contact.
   *
   * <p>This can be derived from a known email address using http://email-to-gae-id.appspot.com.
   *
   * @see com.google.appengine.api.users.User#getUserId()
   */
  @Index
  String gaeUserId;

  /**
   * Whether this contact is publicly visible in WHOIS registrar query results as an Admin contact.
   */
  boolean visibleInWhoisAsAdmin = false;

  /**
   * Whether this contact is publicly visible in WHOIS registrar query results as a Technical
   * contact.
   */
  boolean visibleInWhoisAsTech = false;

  /**
   * Whether this contact's phone number and email address is publicly visible in WHOIS domain query
   * results as registrar abuse contact info.
   */
  boolean visibleInDomainWhoisAsAbuse = false;

  public static ImmutableSet<Type> typesFromCSV(String csv) {
    return typesFromStrings(Arrays.asList(csv.split(",")));
  }

  public static ImmutableSet<Type> typesFromStrings(Iterable<String> typeNames) {
    return FluentIterable.from(typeNames).transform(Enums.stringConverter(Type.class)).toSet();
  }

  /**
   * Helper to update the contacts associated with a Registrar. This requires querying for the
   * existing contacts, deleting existing contacts that are not part of the given {@code contacts}
   * set, and then saving the given {@code contacts}.
   *
   * <p>IMPORTANT NOTE: If you call this method then it is your responsibility to also persist the
   * relevant Registrar entity with the {@link Registrar#contactsRequireSyncing} field set to true.
   */
  public static void updateContacts(
      final Registrar registrar, final Set<RegistrarContact> contacts) {
    ofy().transact(new VoidWork() {
      @Override
      public void vrun() {
        ofy().delete().keys(difference(
            ImmutableSet.copyOf(
                ofy().load().type(RegistrarContact.class).ancestor(registrar).keys()),
            FluentIterable.from(contacts).transform(OBJECTS_TO_KEYS).toSet()));
        ofy().save().entities(contacts);
      }});
  }

  public Key<Registrar> getParent() {
    return parent;
  }

  public String getName() {
    return name;
  }

  public String getEmailAddress() {
    return emailAddress;
  }

  public String getPhoneNumber() {
    return phoneNumber;
  }

  public String getFaxNumber() {
    return faxNumber;
  }

  public ImmutableSortedSet<Type> getTypes() {
    return nullToEmptyImmutableSortedCopy(types);
  }

  public boolean getVisibleInWhoisAsAdmin() {
    return visibleInWhoisAsAdmin;
  }

  public boolean getVisibleInWhoisAsTech() {
    return visibleInWhoisAsTech;
  }

  public boolean getVisibleInDomainWhoisAsAbuse() {
    return visibleInDomainWhoisAsAbuse;
  }

  public String getGaeUserId() {
    return gaeUserId;
  }

  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /**
   * Returns a string representation that's human friendly.
   *
   * <p>The output will look something like this:<pre>   {@code
   *
   *   Some Person
   *   person@example.com
   *   Tel: +1.2125650666
   *   Types: [ADMIN, WHOIS]
   *   Visible in WHOIS as Admin contact: Yes
   *   Visible in WHOIS as Technical contact: No
   *   GAE-UserID: 1234567890}</pre>
   */
  public String toStringMultilinePlainText() {
    StringBuilder result = new StringBuilder(256);
    result.append(getName()).append('\n');
    result.append(getEmailAddress()).append('\n');
    if (phoneNumber != null) {
      result.append("Tel: ").append(getPhoneNumber()).append('\n');
    }
    if (faxNumber != null) {
      result.append("Fax: ").append(getFaxNumber()).append('\n');
    }
    result.append("Types: ").append(getTypes()).append('\n');
    result
        .append("Visible in registrar WHOIS query as Admin contact: ")
        .append(getVisibleInWhoisAsAdmin() ? "Yes" : "No")
        .append("\n");
    result
        .append("Visible in registrar WHOIS query as Technical contact: ")
        .append(getVisibleInWhoisAsTech() ? "Yes" : "No")
        .append("\n");
    result
        .append(
            "Phone number and email visible in domain WHOIS query as "
                + "Registrar Abuse contact info: ")
        .append(getVisibleInDomainWhoisAsAbuse() ? "Yes" : "No")
        .append("\n");
    if (getGaeUserId() != null) {
      result.append("GAE-UserID: ").append(getGaeUserId()).append('\n');
    }
    return result.toString();
  }

  @Override
  public Map<String, Object> toJsonMap() {
    return new JsonMapBuilder()
        .put("name", name)
        .put("emailAddress", emailAddress)
        .put("phoneNumber", phoneNumber)
        .put("faxNumber", faxNumber)
        .put("types", Joiner.on(',').join(transform(getTypes(), toStringFunction())))
        .put("visibleInWhoisAsAdmin", visibleInWhoisAsAdmin)
        .put("visibleInWhoisAsTech", visibleInWhoisAsTech)
        .put("visibleInDomainWhoisAsAbuse", visibleInDomainWhoisAsAbuse)
        .put("gaeUserId", gaeUserId)
        .build();
  }

  /** A builder for constructing a {@link RegistrarContact}, since it is immutable. */
  public static class Builder extends Buildable.Builder<RegistrarContact> {
    public Builder() {}

    private Builder(RegistrarContact instance) {
      super(instance);
    }

    public Builder setParent(Registrar parent) {
      return this.setParent(Key.create(parent));
    }

    public Builder setParent(Key<Registrar> parentKey) {
      getInstance().parent = parentKey;
      return this;
    }

    /** Build the registrar, nullifying empty fields. */
    @Override
    public RegistrarContact build() {
      checkNotNull(getInstance().parent, "Registrar parent cannot be null");
      checkNotNull(getInstance().emailAddress, "Email address cannot be null");
      return cloneEmptyToNull(super.build());
    }

    public Builder setName(String name) {
      getInstance().name = name;
      return this;
    }

    public Builder setEmailAddress(String emailAddress) {
      getInstance().emailAddress = emailAddress;
      return this;
    }

    public Builder setPhoneNumber(String phoneNumber) {
      getInstance().phoneNumber = phoneNumber;
      return this;
    }

    public Builder setFaxNumber(String faxNumber) {
      getInstance().faxNumber = faxNumber;
      return this;
    }

    public Builder setTypes(Iterable<Type> types) {
      getInstance().types = ImmutableSet.copyOf(types);
      return this;
    }

    public Builder setVisibleInWhoisAsAdmin(boolean visible) {
      getInstance().visibleInWhoisAsAdmin = visible;
      return this;
    }

    public Builder setVisibleInWhoisAsTech(boolean visible) {
      getInstance().visibleInWhoisAsTech = visible;
      return this;
    }

    public Builder setVisibleInDomainWhoisAsAbuse(boolean visible) {
      getInstance().visibleInDomainWhoisAsAbuse = visible;
      return this;
    }

    public Builder setGaeUserId(String gaeUserId) {
      getInstance().gaeUserId = gaeUserId;
      return this;
    }
  }
}
