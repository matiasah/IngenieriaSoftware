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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.testing.TestLogHandler;
import java.util.logging.LogRecord;

/** Utility methods for working with Guava's {@link TestLogHandler}. */
public final class TestLogHandlerUtils {
  private TestLogHandlerUtils() {}

  /**
   * Find the first log message stored in the handler that has the provided prefix, and return that
   * message with the prefix stripped off.
   */
  public static String findFirstLogMessageByPrefix(TestLogHandler handler, String prefix) {
    return findFirstLogRecordWithMessagePrefix(handler, prefix)
        .getMessage()
        .replaceFirst("^" + prefix, "");
  }

  /** Returns the first log record stored in handler whose message has the provided prefix. */
  public static LogRecord findFirstLogRecordWithMessagePrefix(
      TestLogHandler handler, final String prefix) {
    return Iterables.find(
        handler.getStoredLogRecords(),
        new Predicate<LogRecord>() {
          @Override
          public boolean apply(LogRecord logRecord) {
            return logRecord.getMessage().startsWith(prefix);
          }
        });
  }
}
