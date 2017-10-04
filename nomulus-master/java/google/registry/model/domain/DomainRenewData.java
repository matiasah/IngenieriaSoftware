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

import google.registry.model.eppoutput.EppResponse.ResponseData;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.joda.time.DateTime;

/** The {@link ResponseData} returned when renewing a domain. */
@XmlRootElement(name = "renData")
@XmlType(propOrder = {"name", "expirationDate"})
public class DomainRenewData implements ResponseData {

  String name;

  @XmlElement(name = "exDate")
  DateTime expirationDate;

  public static DomainRenewData create(String name, DateTime expirationDate) {
    DomainRenewData instance = new DomainRenewData();
    instance.name = name;
    instance.expirationDate = expirationDate;
    return instance;
  }
}
