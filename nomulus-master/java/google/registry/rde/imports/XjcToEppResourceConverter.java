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

package google.registry.rde.imports;

import static java.nio.charset.StandardCharsets.UTF_8;

import google.registry.model.EppResource;
import google.registry.xjc.XjcXmlTransformer;
import google.registry.xml.XmlException;
import java.io.ByteArrayOutputStream;

/**
 * Base class for Jaxb object to {@link EppResource} converters
 */
public abstract class XjcToEppResourceConverter {

  protected static byte[] getObjectXml(Object jaxbElement) {
    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      XjcXmlTransformer.marshalLenient(jaxbElement, bout, UTF_8);
      return bout.toByteArray();
    } catch (XmlException e) {
      throw new RuntimeException(e);
    }
  }
}
