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

package google.registry.model.domain.secdns;

import com.google.common.annotations.VisibleForTesting;
import com.googlecode.objectify.annotation.Embed;
import google.registry.model.ImmutableObject;
import javax.xml.bind.DatatypeConverter;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Holds the data necessary to construct a single Delegation Signer (DS) record for a domain.
 *
 * @see <a href="http://tools.ietf.org/html/rfc5910">RFC 5910</a>
 * @see <a href="http://tools.ietf.org/html/rfc4034">RFC 4034</a>
 */
@Embed
@XmlType(name = "dsData")
public class DelegationSignerData
    extends ImmutableObject implements Comparable<DelegationSignerData> {

  /** The identifier for this particular key in the domain. */
  int keyTag;

  /**
   * The algorithm used by this key.
   *
   * @see <a href="http://tools.ietf.org/html/rfc4034#appendix-A.1">RFC 4034 Appendix A.1</a>
   */
  @XmlElement(name = "alg")
  int algorithm;

  /**
   * The algorithm used to generate the digest.
   *
   * @see <a href="http://tools.ietf.org/html/rfc4034#appendix-A.2">RFC 4034 Appendix A.2</a>
   */
  int digestType;

  /**
   * The hexBinary digest of the public key.
   *
   * @see <a href="http://tools.ietf.org/html/rfc4034#section-5.1.4">RFC 4034 Section 5.1.4</a>
   */
  @XmlJavaTypeAdapter(HexBinaryAdapter.class)
  byte[] digest;

  public int getKeyTag() {
    return keyTag;
  }

  public int getAlgorithm() {
    return algorithm;
  }

  public int getDigestType() {
    return digestType;
  }

  public byte[] getDigest() {
    return digest;
  }

  @VisibleForTesting
  public static DelegationSignerData create(
      int keyTag, int algorithm, int digestType, byte[] digest) {
    DelegationSignerData instance = new DelegationSignerData();
    instance.keyTag = keyTag;
    instance.algorithm = algorithm;
    instance.digestType = digestType;
    instance.digest = digest;
    return instance;
  }

  @Override
  public int compareTo(DelegationSignerData other) {
    return Integer.compare(getKeyTag(), other.getKeyTag());
  }

  /**
   * Returns the presentation format of this DS record.
   *
   * @see <a href="https://tools.ietf.org/html/rfc4034#section-5.3">RFC 4034 Section 5.3</a>
   */
  public String toRrData() {
    return String.format(
        "%d %d %d %s",
        this.keyTag, this.algorithm, this.digestType, DatatypeConverter.printHexBinary(digest));
  }
}
