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

package google.registry.model.domain.metadata;

import google.registry.model.ImmutableObject;
import google.registry.model.eppinput.EppInput.CommandExtension;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/** A metadata extension that may be present on EPP create/mutate commands. */
@XmlRootElement(name = "metadata")
public class MetadataExtension extends ImmutableObject implements CommandExtension {

  /** The reason for the change. */
  @XmlElement(name = "reason")
  String reason;

  /** Whether a change was requested by a registrar. */
  @XmlElement(name = "requestedByRegistrar")
  boolean requestedByRegistrar;

  /**
   * Whether a domain is being created for an anchor tenant. This field is only
   * relevant for domain creates, and should be omitted for all other operations.
   */
  @XmlElement(name = "anchorTenant")
  boolean isAnchorTenant;

  public String getReason() {
    return reason;
  }

  public boolean getRequestedByRegistrar() {
    return requestedByRegistrar;
  }

  public boolean getIsAnchorTenant() {
    return isAnchorTenant;
  }
}
