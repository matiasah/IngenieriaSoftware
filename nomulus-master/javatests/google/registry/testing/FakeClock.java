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

package google.registry.testing;

import static google.registry.util.DateTimeUtils.START_OF_TIME;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Duration.millis;

import google.registry.util.Clock;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.concurrent.ThreadSafe;
import org.joda.time.DateTime;
import org.joda.time.ReadableDuration;
import org.joda.time.ReadableInstant;

/** A mock clock for testing purposes that supports telling, setting, and advancing the time. */
@ThreadSafe
public final class FakeClock implements Clock, Serializable {

  private static final long serialVersionUID = 675054721685304599L;

  // Clock isn't a thread synchronization primitive, but tests involving
  // threads should see a consistent flow.
  private final AtomicLong currentTimeMillis = new AtomicLong();

  /** Creates a FakeClock that starts at START_OF_TIME. */
  public FakeClock() {
    this(START_OF_TIME);
  }

  /** Creates a FakeClock initialized to a specific time. */
  public FakeClock(ReadableInstant startTime) {
    setTo(startTime);
  }

  /** Returns the current time. */
  @Override
  public DateTime nowUtc() {
    return new DateTime(currentTimeMillis.get(), UTC);
  }

  /** Advances clock by one millisecond. */
  public void advanceOneMilli() {
    advanceBy(millis(1));
  }

  /** Advances clock by some duration. */
  public void advanceBy(ReadableDuration duration) {
    currentTimeMillis.addAndGet(duration.getMillis());
  }

  /** Sets the time to the specified instant. */
  public void setTo(ReadableInstant time) {
    currentTimeMillis.set(time.getMillis());
  }
}
