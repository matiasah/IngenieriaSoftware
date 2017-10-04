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

package google.registry.keyring.api;

import static com.google.common.base.Strings.emptyToNull;

import com.google.common.base.Optional;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Documented;
import javax.inject.Qualifier;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;

/** Dagger module for keys stored in {@link Keyring}. */
@Module
public final class KeyModule {

  /** Dagger qualifier for keys from {@link Keyring}. */
  @Qualifier
  @Documented
  public static @interface Key {
    String value();
  }

  @Provides
  @Key("brdaReceiverKey")
  static PGPPublicKey provideBrdaReceiverKey(Keyring keyring) {
    return keyring.getBrdaReceiverKey();
  }

  @Provides
  @Key("brdaSigningKey")
  static PGPKeyPair provideBrdaSigningKey(Keyring keyring) {
    return keyring.getBrdaSigningKey();
  }

  @Provides
  @Key("icannReportingPassword")
  static String provideIcannReportingPassword(Keyring keyring) {
    return keyring.getIcannReportingPassword();
  }

  @Provides
  @Key("marksdbDnlLogin")
  static Optional<String> provideMarksdbDnlLogin(Keyring keyring) {
    return Optional.fromNullable(emptyToNull(keyring.getMarksdbDnlLogin()));
  }

  @Provides
  @Key("marksdbLordnPassword")
  static Optional<String> provideMarksdbLordnPassword(Keyring keyring) {
    return Optional.fromNullable(emptyToNull(keyring.getMarksdbLordnPassword()));
  }

  @Provides
  @Key("marksdbSmdrlLogin")
  static Optional<String> provideMarksdbSmdrlLogin(Keyring keyring) {
    return Optional.fromNullable(emptyToNull(keyring.getMarksdbSmdrlLogin()));
  }

  @Provides
  @Key("rdeStagingEncryptionKey")
  static PGPPublicKey provideRdeStagingEncryptionKey(Keyring keyring) {
    return keyring.getRdeStagingEncryptionKey();
  }

  @Provides
  @Key("rdeStagingEncryptionKey")
  static byte[] provideRdeStagingEncryptionKeyAsBytes(
      @Key("rdeStagingEncryptionKey") PGPPublicKey rdeStagingEncryptionKey) {
    return PgpHelper.convertPublicKeyToBytes(rdeStagingEncryptionKey);
  }

  @Provides
  @Key("rdeStagingDecryptionKey")
  static PGPPrivateKey provideRdeStagingDecryptionKey(Keyring keyring) {
    return keyring.getRdeStagingDecryptionKey();
  }

  @Provides
  @Key("rdeReceiverKey")
  static PGPPublicKey provideRdeReceiverKey(Keyring keyring) {
    return keyring.getRdeReceiverKey();
  }

  @Provides
  @Key("rdeSigningKey")
  static PGPKeyPair provideRdeSigningKey(Keyring keyring) {
    return keyring.getRdeSigningKey();
  }

  @Provides
  @Key("rdeSshClientPrivateKey")
  static String provideRdeSshClientPrivateKey(Keyring keyring) {
    return keyring.getRdeSshClientPrivateKey();
  }

  @Provides
  @Key("rdeSshClientPublicKey")
  static String provideRdeSshClientPublicKey(Keyring keyring) {
    return keyring.getRdeSshClientPublicKey();
  }

  @Provides
  @Key("jsonCredential")
  static String provideJsonCredential(Keyring keyring) {
    return keyring.getJsonCredential();
  }

  @Provides
  @Key("braintreePrivateKey")
  static String provideBraintreePrivateKey(Keyring keyring) {
    return keyring.getBraintreePrivateKey();
  }
}
