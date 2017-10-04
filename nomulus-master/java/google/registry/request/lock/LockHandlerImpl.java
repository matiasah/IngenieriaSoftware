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

package google.registry.request.lock;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedSet;
import google.registry.model.server.Lock;
import google.registry.util.AppEngineTimeLimiter;
import google.registry.util.FormattingLogger;
import google.registry.util.RequestStatusChecker;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import javax.inject.Inject;
import org.joda.time.Duration;

/** Implementation of {@link LockHandler} that uses the datastore lock. */
public class LockHandlerImpl implements LockHandler {

  private static final long serialVersionUID = 6551645164118637767L;

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  /** Fudge factor to make sure we kill threads before a lock actually expires. */
  private static final Duration LOCK_TIMEOUT_FUDGE = Duration.standardSeconds(5);

  @Inject RequestStatusChecker requestStatusChecker;

  @Inject public LockHandlerImpl() {}

  /**
   * Acquire one or more locks and execute a Void {@link Callable}.
   *
   * <p>Thread will be killed if it doesn't complete before the lease expires.
   *
   * <p>Note that locks are specific either to a given tld or to the entire system (in which case
   * tld should be passed as null).
   *
   * @return whether all locks were acquired and the callable was run.
   */
  @Override
  public boolean executeWithLocks(
      final Callable<Void> callable,
      @Nullable String tld,
      Duration leaseLength,
      String... lockNames) {
    try {
      return AppEngineTimeLimiter.create().callWithTimeout(
          new LockingCallable(callable, Strings.emptyToNull(tld), leaseLength, lockNames),
          leaseLength.minus(LOCK_TIMEOUT_FUDGE).getMillis(),
          TimeUnit.MILLISECONDS,
          true);
    } catch (Exception e) {
      throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  /** Allows injection of mock Lock in tests. */
  @VisibleForTesting
  Optional<Lock> acquire(String lockName, @Nullable String tld, Duration leaseLength) {
    return Lock.acquire(lockName, tld, leaseLength, requestStatusChecker);
  }

  /** A {@link Callable} that acquires and releases a lock around a delegate {@link Callable}. */
  private class LockingCallable implements Callable<Boolean> {
    final Callable<Void> delegate;
    @Nullable final String tld;
    final Duration leaseLength;
    final Set<String> lockNames;

    LockingCallable(
        Callable<Void> delegate,
        String tld,
        Duration leaseLength,
        String... lockNames) {
      checkArgument(leaseLength.isLongerThan(LOCK_TIMEOUT_FUDGE));
      this.delegate = delegate;
      this.tld = tld;
      this.leaseLength = leaseLength;
      // Make sure we join locks in a fixed (lexicographical) order to avoid deadlock.
      this.lockNames = ImmutableSortedSet.copyOf(lockNames);
    }

    @Override
    public Boolean call() throws Exception {
      Set<Lock> acquiredLocks = new HashSet<>();
      try {
        for (String lockName : lockNames) {
          Optional<Lock> lock = acquire(lockName, tld, leaseLength);
          if (!lock.isPresent()) {
            logger.infofmt("Couldn't acquire lock named: %s for TLD: %s", lockName, tld);
            return false;
          }
          logger.infofmt("Acquired lock: %s", lock);
          acquiredLocks.add(lock.get());
        }
        delegate.call();
        return true;
      } finally {
        for (Lock lock : acquiredLocks) {
          lock.release();
          logger.infofmt("Released lock: %s", lock);
        }
      }
    }
  }
}
