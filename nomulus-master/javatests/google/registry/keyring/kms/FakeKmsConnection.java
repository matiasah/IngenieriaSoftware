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

package google.registry.keyring.kms;

import com.google.common.io.BaseEncoding;
import java.io.IOException;
import org.bouncycastle.util.Arrays;

class FakeKmsConnection implements KmsConnection {

  FakeKmsConnection() {}

  /**
   * Returns a dummy {@link EncryptResponse}.
   *
   * <p>The "encrypted value" in the response is the provided value reversed and then base64-encoded
   * and the name of the cryptoKeyVersion is {@code cryptoKeyName + "/foo"}.
   */
  @Override
  public EncryptResponse encrypt(String cryptoKeyName, byte[] plaintext) throws IOException {
    return EncryptResponse.create(
        BaseEncoding.base64().encode(Arrays.reverse(plaintext)), cryptoKeyName + "/foo");
  }

  /**
   * Returns a "decrypted" plaintext.
   *
   * <p>The plaintext is the encodedCiphertext base64-decoded and then reversed.
   */
  @Override
  public byte[] decrypt(String cryptoKeyName, String encodedCiphertext) throws IOException {
    return Arrays.reverse(BaseEncoding.base64().decode(encodedCiphertext));
  }
}
