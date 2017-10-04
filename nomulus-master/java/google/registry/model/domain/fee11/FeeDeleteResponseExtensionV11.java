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

package google.registry.model.domain.fee11;

import google.registry.model.domain.fee.FeeTransformResponseExtension;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * An XML data object that represents a fee extension that may be present on the response to EPP
 * domain create commands.
 */
@XmlRootElement(name = "delData")
@XmlType(propOrder = {"currency", "fees", "credits"})
public class FeeDeleteResponseExtensionV11 extends FeeTransformResponseExtension {

  /** Builder for {@link FeeDeleteResponseExtensionV11}. */
  public static class Builder extends FeeTransformResponseExtension.Builder {
    public Builder() {
      super(new FeeDeleteResponseExtensionV11());
    }
  }
}
