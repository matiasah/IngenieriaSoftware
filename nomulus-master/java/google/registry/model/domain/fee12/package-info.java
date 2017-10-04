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

@XmlSchema(
    namespace = "urn:ietf:params:xml:ns:fee-0.12",
    xmlns = @XmlNs(prefix = "fee12", namespaceURI = "urn:ietf:params:xml:ns:fee-0.12"),
    elementFormDefault = XmlNsForm.QUALIFIED)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlJavaTypeAdapters({
    @XmlJavaTypeAdapter(CurrencyUnitAdapter.class),
    @XmlJavaTypeAdapter(UtcDateTimeAdapter.class)})
package google.registry.model.domain.fee12;

import google.registry.model.translators.CurrencyUnitAdapter;
import google.registry.xml.UtcDateTimeAdapter;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlNs;
import javax.xml.bind.annotation.XmlNsForm;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters;

