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

package google.registry.rde;

import com.google.auto.value.AutoValue;
import com.google.common.base.Optional;
import google.registry.model.common.Cursor.CursorType;
import google.registry.model.rde.RdeMode;
import java.io.Serializable;
import org.joda.time.DateTime;
import org.joda.time.Duration;

/** Container representing a single RDE or BRDA XML escrow deposit that needs to be created. */
@AutoValue
public abstract class PendingDeposit implements Serializable {

  private static final long serialVersionUID = 3141095605225904433L;

  /**
   * True if deposits should be generated via manual operation, which does not update the cursor,
   * and saves the generated deposits in a special manual subdirectory tree.
   */
  public abstract boolean manual();

  /** TLD for which a deposit should be generated. */
  public abstract String tld();

  /** Watermark date for which a deposit should be generated. */
  public abstract DateTime watermark();

  /** Which type of deposit to generate: full (RDE) or thin (BRDA). */
  public abstract RdeMode mode();

  /** The cursor type to update (not used in manual operation). */
  public abstract Optional<CursorType> cursor();

  /** Amount of time to increment the cursor (not used in manual operation). */
  public abstract Optional<Duration> interval();

  /**
   * Subdirectory of bucket/manual in which files should be placed, including a trailing slash (used
   * only in manual operation).
   */
  public abstract Optional<String> directoryWithTrailingSlash();

  /**
   * Revision number for generated files; if absent, use the next available in the sequence (used
   * only in manual operation).
   */
  public abstract Optional<Integer> revision();

  static PendingDeposit create(
      String tld, DateTime watermark, RdeMode mode, CursorType cursor, Duration interval) {
    return new AutoValue_PendingDeposit(
        false,
        tld,
        watermark,
        mode,
        Optional.of(cursor),
        Optional.of(interval),
        Optional.<String>absent(),
        Optional.<Integer>absent());
  }

  static PendingDeposit createInManualOperation(
      String tld,
      DateTime watermark,
      RdeMode mode,
      String directoryWithTrailingSlash,
      Optional<Integer> revision) {
    return new AutoValue_PendingDeposit(
        true,
        tld,
        watermark,
        mode,
        Optional.<CursorType>absent(),
        Optional.<Duration>absent(),
        Optional.of(directoryWithTrailingSlash),
        revision);
  }

  PendingDeposit() {}
}
