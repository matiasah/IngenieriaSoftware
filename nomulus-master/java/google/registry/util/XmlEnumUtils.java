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

package google.registry.util;

import javax.xml.bind.annotation.XmlEnumValue;

/** Utility methods related to xml enums. */
public class XmlEnumUtils {
  /** Read the {@link XmlEnumValue} string off of an enum. */
  public static String enumToXml(Enum<?> input) {
    try {
      return input.getClass().getField(input.name()).getAnnotation(XmlEnumValue.class).value();
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }
}
