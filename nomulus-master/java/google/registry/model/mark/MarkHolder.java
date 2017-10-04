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

package google.registry.model.mark;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;

/** Contact information for the holder of the mark. */
public class MarkHolder extends CommonMarkContactFields {

  /** The type of the entitlement for the holder of the mark. */
  @XmlEnum
  enum EntitlementType {
    @XmlEnumValue("owner")
    OWNER,

    @XmlEnumValue("assignee")
    ASSIGNEE,

    @XmlEnumValue("licensee")
    LICENSEE;
  }

  @XmlAttribute
  EntitlementType entitlement;

  public EntitlementType getEntitlementType() {
    return entitlement;
  }
}
