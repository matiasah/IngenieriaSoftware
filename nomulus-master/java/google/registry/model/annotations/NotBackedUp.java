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

package google.registry.model.annotations;

import com.googlecode.objectify.annotation.Entity;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for an Objectify {@link Entity} to indicate that it should not be backed up by the
 * default Datastore backup configuration (it may be backed up by something else).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NotBackedUp {
  Reason reason();

  /** Reasons why a given entity does not need to be be backed up. */
  public enum Reason {
    /** This entity is transient by design and has only a short-term useful lifetime. */
    TRANSIENT,

    /** This entity's data is already regularly pulled down from an external source. */
    EXTERNALLY_SOURCED,

    /** This entity is generated automatically by the app and will be recreated if need be. */
    AUTO_GENERATED,

    /** Commit log entities are exported separately from the regular backups, by design. */
    COMMIT_LOGS
  }
}
