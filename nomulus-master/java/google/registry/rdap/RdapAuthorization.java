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

package google.registry.rdap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import google.registry.model.ImmutableObject;

/** Authorization information for RDAP data access. */
@AutoValue
public abstract class RdapAuthorization extends ImmutableObject {

  enum Role {
    ADMINISTRATOR,
    REGISTRAR,
    PUBLIC
  }

  /** The role to be used for access. */
  public abstract Role role();

  /** The registrar client IDs for which access is granted (used only if the role is REGISTRAR. */
  public abstract ImmutableList<String> clientIds();

  static RdapAuthorization create(Role role, String clientId) {
    return new AutoValue_RdapAuthorization(role, ImmutableList.of(clientId));
  }

  static RdapAuthorization create(Role role, ImmutableList<String> clientIds) {
    return new AutoValue_RdapAuthorization(role, clientIds);
  }

  boolean isAuthorizedForClientId(String clientId) {
    switch (role()) {
      case ADMINISTRATOR:
        return true;
      case REGISTRAR:
        return clientIds().contains(clientId);
      default:
        return false;
    }
  }

  public static final RdapAuthorization PUBLIC_AUTHORIZATION =
      create(Role.PUBLIC, ImmutableList.<String>of());

  public static final RdapAuthorization ADMINISTRATOR_AUTHORIZATION =
      create(Role.ADMINISTRATOR, ImmutableList.<String>of());
}

