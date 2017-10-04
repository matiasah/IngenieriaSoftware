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

package google.registry.model.domain;


import com.googlecode.objectify.annotation.Embed;
import google.registry.model.ImmutableObject;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlValue;

/** The "periodType" from {@link "http://tools.ietf.org/html/rfc5731"}. */
@Embed
public class Period extends ImmutableObject {

  @XmlAttribute
  Unit unit;

  @XmlValue
  Integer value;

  public Unit getUnit() {
    return unit;
  }

  public Integer getValue() {
    return value;
  }

  /** The unit enum. */
  public enum Unit {
    @XmlEnumValue("y")
    YEARS,

    @XmlEnumValue("m")
    MONTHS,
  }

  public static Period create(int value, Unit unit) {
    Period instance = new Period();
    instance.value = value;
    instance.unit = unit;
    return instance;
  }
}
