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

package google.registry.model.server;

import static com.google.common.base.Preconditions.checkArgument;
import static google.registry.model.common.EntityGroupRoot.getCrossTldKey;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Parent;
import google.registry.model.Buildable;
import google.registry.model.CreateAutoTimestamp;
import google.registry.model.ImmutableObject;
import google.registry.model.annotations.ReportedOn;

/**
 * An encrypted value.
 *
 * <p>Used to store passwords and other sensitive information in Datastore. Multiple versions of a
 * {@link KmsSecretRevision} may be persisted but only the latest version is primary. A key to the
 * primary version is stored by {@link KmsSecret#latestRevision}.
 *
 * <p>The value can be encrypted and decrypted using Cloud KMS.
 *
 * @see <a href="https://cloud.google.com/kms/docs/">Google Cloud Key Management Service
 *     Documentation</a>
 * @see google.registry.keyring.kms.KmsKeyring
 */
@Entity
@ReportedOn
public class KmsSecretRevision extends ImmutableObject {

  /**
   * The maximum allowable secret size. Although Datastore allows entities up to 1 MB in size,
   * BigQuery imports of Datastore backups limit individual columns (entity attributes) to 64 KB.
   */
  private static final int MAX_SECRET_SIZE_BYTES = 64 * 1024 * 1024;

  /** The revision of this secret. */
  @Id long revisionKey;

  /** The parent {@link KmsSecret} which contains metadata about this {@link KmsSecretRevision}. */
  @Parent Key<KmsSecret> parent;

  /**
   * The name of the {@code cryptoKeyVersion} associated with this {@link KmsSecretRevision}.
   *
   * @see <a
   *     href="https://cloud.google.com/kms/docs/reference/rest/v1/projects.locations.keyRings.cryptoKeys.cryptoKeyVersions">projects.locations.keyRings.cryptoKeys.cryptoKeyVersions</a>
   */
  String kmsCryptoKeyVersionName;

  /**
   * The base64-encoded encrypted value of this {@link KmsSecretRevision} as returned by the Cloud
   * KMS API.
   *
   * @see <a
   *     href="https://cloud.google.com/kms/docs/reference/rest/v1/projects.locations.keyRings.cryptoKeys/encrypt">projects.locations.keyRings.cryptoKeys.encrypt</a>
   */
  String encryptedValue;

  /** An automatically managed creation timestamp. */
  CreateAutoTimestamp creationTime = CreateAutoTimestamp.create(null);

  public String getKmsCryptoKeyVersionName() {
    return kmsCryptoKeyVersionName;
  }

  public String getEncryptedValue() {
    return encryptedValue;
  }

  /** A builder for constructing {@link KmsSecretRevision} entities, since they are immutable. */
  public static class Builder extends Buildable.Builder<KmsSecretRevision> {

    public Builder setKmsCryptoKeyVersionName(String kmsCryptoKeyVersionName) {
      getInstance().kmsCryptoKeyVersionName = kmsCryptoKeyVersionName;
      return this;
    }

    public Builder setEncryptedValue(String encryptedValue) {
      checkArgument(
          encryptedValue.length() <= MAX_SECRET_SIZE_BYTES,
          "Secret is greater than %s bytes",
          MAX_SECRET_SIZE_BYTES);

      getInstance().encryptedValue = encryptedValue;
      return this;
    }

    /**
     * Set the parent {@link KmsSecret}.
     *
     * <p>The secret may not exist yet, so it is referred to by name rather than by reference.
     */
    public Builder setParent(String secretName) {
      getInstance().parent = Key.create(getCrossTldKey(), KmsSecret.class, secretName);
      return this;
    }
  }
}
