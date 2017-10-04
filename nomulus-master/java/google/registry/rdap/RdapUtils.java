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

import static com.google.common.collect.Iterables.tryFind;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import google.registry.model.registrar.Registrar;

/** Utility functions for RDAP. */
public final class RdapUtils {

  private RdapUtils() {}

  /** Looks up a registrar by its IANA identifier. */
  static Optional<Registrar> getRegistrarByIanaIdentifier(final long ianaIdentifier) {
    return tryFind(
        Registrar.loadAllCached(),
        new Predicate<Registrar>() {
          @Override
          public boolean apply(Registrar registrar) {
            Long registrarIanaIdentifier = registrar.getIanaIdentifier();
            return (registrarIanaIdentifier != null) && (registrarIanaIdentifier == ianaIdentifier);
          }});
  }
}
