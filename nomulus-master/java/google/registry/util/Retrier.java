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

package google.registry.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.collect.Iterables.any;
import static com.google.common.math.IntMath.pow;
import static google.registry.util.PredicateUtils.supertypeOf;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.inject.Inject;
import javax.inject.Named;
import org.joda.time.Duration;

/** Wrapper that does retry with exponential backoff. */
public class Retrier implements Serializable {

  private static final long serialVersionUID = 1167386907195735483L;

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private final Sleeper sleeper;
  private final int attempts;

  /** Holds functions to call whenever the code being retried fails. */
  public static interface FailureReporter {

    /**
     * Called after a retriable failure happened.
     *
     * <p>Not called after the final failure, nor if the Throwable thrown isn't "a retriable error".
     *
     * <p>Not called at all if the retrier succeeded on its first attempt.
     */
    public void beforeRetry(Throwable thrown, int failures, int maxAttempts);

    /**
     * Called after a a non-retriable error.
     *
     * <p>Called either after the final failure, or if the Throwable thrown isn't "a retriable
     * error". The retrier throws right after calling this function.
     *
     * <p>Not called at all if the retrier succeeds.
     */
    public void afterFinalFailure(Throwable thrown, int failures);
  }

  @Inject
  public Retrier(Sleeper sleeper, @Named("transientFailureRetries") int transientFailureRetries) {
    this.sleeper = sleeper;
    checkArgument(transientFailureRetries > 0, "Number of attempts must be positive");
    this.attempts = transientFailureRetries;
  }

  /**
   * Retries a unit of work in the face of transient errors.
   *
   * <p>Retrying is done a fixed number of times, with exponential backoff, if the exception that is
   * thrown is deemed retryable by the predicate. If the error is not considered retryable, or if
   * the thread is interrupted, or if the allowable number of attempts has been exhausted, the
   * original exception is propagated through to the caller. Checked exceptions are wrapped in a
   * RuntimeException, while unchecked exceptions are propagated as-is.
   *
   * @return <V> the value returned by the {@link Callable}.
   */
  private <V> V callWithRetry(
      Callable<V> callable,
      FailureReporter failureReporter,
      Predicate<Throwable> isRetryable) {
    int failures = 0;
    while (true) {
      try {
        return callable.call();
      } catch (Throwable e) {
        if (++failures == attempts || !isRetryable.apply(e)) {
          failureReporter.afterFinalFailure(e, failures);
          throwIfUnchecked(e);
          throw new RuntimeException(e);
        }
        failureReporter.beforeRetry(e, failures, attempts);
        try {
          // Wait 100ms on the first attempt, doubling on each subsequent attempt.
          sleeper.sleep(Duration.millis(pow(2, failures) * 100));
        } catch (InterruptedException e2) {
          // Since we're not rethrowing InterruptedException, set the interrupt state on the thread
          // so the next blocking operation will know to abort the thread.
          Thread.currentThread().interrupt();
          throwIfUnchecked(e);
          throw new RuntimeException(e);
        }
      }
    }
  }

  private static final FailureReporter LOGGING_FAILURE_REPORTER = new FailureReporter() {
    @Override
    public void beforeRetry(Throwable thrown, int failures, int maxAttempts) {
      logger.infofmt(thrown, "Retrying transient error, attempt %d", failures);
    }

    @Override
    public void afterFinalFailure(Throwable thrown, int failures) {}
  };

  /**
   * Retries a unit of work in the face of transient errors.
   *
   * <p>Retrying is done a fixed number of times, with exponential backoff, if the exception that is
   * thrown is on a whitelist of retryable errors. If the error is not on the whitelist, or if the
   * thread is interrupted, or if the allowable number of attempts has been exhausted, the original
   * exception is propagated through to the caller. Checked exceptions are wrapped in a
   * RuntimeException, while unchecked exceptions are propagated as-is.
   *
   * <p>Uses a default FailureReporter that logs before each retry.
   *
   * @return <V> the value returned by the {@link Callable}.
   */
  @SafeVarargs
  public final <V> V callWithRetry(
      Callable<V> callable,
      Class<? extends Throwable> retryableError,
      Class<? extends Throwable>... moreRetryableErrors) {
    return callWithRetry(
        callable,
        LOGGING_FAILURE_REPORTER,
        retryableError,
        moreRetryableErrors);
  }

  /**
   * Retries a unit of work in the face of transient errors.
   *
   * <p>Retrying is done a fixed number of times, with exponential backoff, if the exception that is
   * thrown is on a whitelist of retryable errors. If the error is not on the whitelist, or if the
   * thread is interrupted, or if the allowable number of attempts has been exhausted, the original
   * exception is propagated through to the caller. Checked exceptions are wrapped in a
   * RuntimeException, while unchecked exceptions are propagated as-is.
   *
   * @return <V> the value returned by the {@link Callable}.
   */
  @SafeVarargs
  public final <V> V callWithRetry(
      Callable<V> callable,
      FailureReporter failureReporter,
      Class<? extends Throwable> retryableError,
      Class<? extends Throwable>... moreRetryableErrors) {
    final Set<Class<?>> retryables =
        new ImmutableSet.Builder<Class<?>>().add(retryableError).add(moreRetryableErrors).build();
    return callWithRetry(callable, failureReporter, new Predicate<Throwable>() {
      @Override
      public boolean apply(Throwable e) {
        return any(retryables, supertypeOf(e.getClass()));
      }});
  }
}
