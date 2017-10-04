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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Optional;
import com.googlecode.objectify.annotation.Embed;
import google.registry.model.Buildable;
import google.registry.model.Buildable.Overlayable;
import google.registry.model.ImmutableObject;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.NormalizedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Implementation of both "postalInfoType" and "chgPostalInfoType" from
 * {@link "http://tools.ietf.org/html/rfc5733"}.
 */
@Embed
@XmlType(propOrder = {"name", "org", "address", "type"})
public class PostalInfo extends ImmutableObject implements Overlayable<PostalInfo> {

  /** The type of the address, either localized or international. */
  public enum Type {
    @XmlEnumValue("loc")
    LOCALIZED,
    @XmlEnumValue("int")
    INTERNATIONALIZED
  }

  @XmlJavaTypeAdapter(NormalizedStringAdapter.class)
  String name;

  @XmlJavaTypeAdapter(NormalizedStringAdapter.class)
  String org;

  @XmlElement(name = "addr")
  ContactAddress address;

  @XmlAttribute
  Type type;

  public String getName() {
    return name;
  }

  public String getOrg() {
    return org;
  }

  public ContactAddress getAddress() {
    return address;
  }

  public Type getType() {
    return type;
  }

  @Override
  public PostalInfo overlay(PostalInfo source) {
    // Don't overlay the type field, as that should never change.
    checkState(source.type == null || source.type == type);
    return asBuilder()
        .setName(Optional.fromNullable(source.getName()).or(Optional.fromNullable(name)).orNull())
        .setOrg(Optional.fromNullable(source.getOrg()).or(Optional.fromNullable(org)).orNull())
        .setAddress(
            Optional.fromNullable(source.getAddress()).or(Optional.fromNullable(address)).orNull())
        .build();
  }

  @Override
  public Builder asBuilder() {
    return new Builder(clone(this));
  }

  /** A builder for constructing {@link PostalInfo}, since its changes get overlayed. */
  public static class Builder extends Buildable.Builder<PostalInfo> {
    public Builder() {}

    private Builder(PostalInfo instance) {
      super(instance);
    }

    public Builder setName(String name) {
      getInstance().name = name;
      return this;
    }

    public Builder setOrg(String org) {
      getInstance().org = org;
      return this;
    }

    public Builder setAddress(ContactAddress address) {
      getInstance().address = address;
      return this;
    }

    public Builder setType(Type type) {
      getInstance().type = type;
      return this;
    }
  }
}
