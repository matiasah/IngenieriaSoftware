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

import static google.registry.model.EppResourceUtils.loadByForeignKey;

import com.google.common.base.Optional;
import com.google.common.net.InternetDomainName;
import google.registry.model.domain.DomainResource;
import org.joda.time.DateTime;

/** Represents a WHOIS lookup on a domain name (i.e. SLD). */
public class DomainLookupCommand extends DomainOrHostLookupCommand {

  public DomainLookupCommand(InternetDomainName domainName) {
    super(domainName, "Domain");
  }

  @Override
  protected Optional<WhoisResponse> getResponse(InternetDomainName domainName, DateTime now) {
    final DomainResource domainResource =
        loadByForeignKey(DomainResource.class, domainName.toString(), now);
    return Optional.<WhoisResponse>fromNullable(
        domainResource == null ? null : new DomainWhoisResponse(domainResource, now));
  }
}
