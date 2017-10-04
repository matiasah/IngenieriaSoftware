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

package google.registry.model.domain.fee;

import com.google.common.base.Optional;
import google.registry.model.ImmutableObject;
import google.registry.model.domain.Period;
import javax.xml.bind.annotation.XmlTransient;
import org.joda.money.CurrencyUnit;
import org.joda.time.DateTime;

/**
 * Abstract base class for the fee request query items used in Check and Info commands. It handles
 * the period, which is always present. Derived classes must handle the command, which may be
 * implemented in different ways, and the currency, which may or may not be present, depending on
 * the version of the extension being used.
 */
@XmlTransient
public abstract class FeeQueryCommandExtensionItem extends ImmutableObject {

  /** The name of a command that might have an associated fee. */
  public enum CommandName {
    UNKNOWN,
    CREATE,
    RENEW,
    TRANSFER,
    RESTORE,
    UPDATE
  }

  /** The default validity period (if not specified) is 1 year for all operations. */
  static final Period DEFAULT_PERIOD = Period.create(1, Period.Unit.YEARS);

  /** The period for the command being checked. */
  Period period;

  /**
   * Three-character ISO4217 currency code.
   *
   * <p>Returns null if this version of the fee extension doesn't specify currency at the top level.
   */
  public abstract CurrencyUnit getCurrency();

  /** The as-of date for the fee extension to run. */
  public abstract Optional<DateTime> getEffectiveDate();

  /** The name of the command being checked. */
  public abstract CommandName getCommandName();

  /** The command name before being parsed into an enum, for use in error strings. */
  public abstract String getUnparsedCommandName();

  /** The phase of the command being checked. */
  public abstract String getPhase();

  /** The subphase of the command being checked. */
  public abstract String getSubphase();

  public Period getPeriod() {
    return Optional.fromNullable(period).or(DEFAULT_PERIOD);
  }
}
