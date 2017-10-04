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

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.TestLogHandler;
import com.google.common.truth.Correspondence;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.SimpleSubjectBuilder;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import google.registry.testing.TruthChainer.Which;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** Utility methods for asserting things about logging {@link Handler} instances. */
public class LogsSubject extends Subject<LogsSubject, TestLogHandler> {

  /** A factory for instances of this subject. */
  private static class SubjectFactory
      extends ReflectiveSubjectFactory<TestLogHandler, LogsSubject> {}

  public LogsSubject(FailureStrategy strategy, TestLogHandler subject) {
    super(strategy, subject);
  }

  private static final Correspondence<String, String> CONTAINS_CORRESPONDENCE =
      new Correspondence<String, String>() {
        @Override
        public boolean compare(String actual, String expected) {
          return actual.contains(expected);
        }

        @Override
        public String toString() {
          return "contains";
        }
      };

  private List<String> getMessagesAtLevel(Level level) {
    ImmutableList.Builder<String> builder = ImmutableList.<String>builder();
    for (LogRecord log : actual().getStoredLogRecords()) {
      if (log.getLevel().equals(level)) {
        builder.add(log.getMessage());
      }
    }
    return builder.build();
  }

  public void hasNoLogsAtLevel(Level level) {
    check()
        .withMessage("Logs at level %s", level)
        .that(getMessagesAtLevel(level))
        .isEmpty();
  }

  public Which<StringSubject> hasLogAtLevelWithMessage(Level level, String message) {
    List<String> messagesAtLevel = getMessagesAtLevel(level);
    check()
        .withMessage("Logs at level %s", level)
        .that(messagesAtLevel)
        .comparingElementsUsing(CONTAINS_CORRESPONDENCE)
        .contains(message);
    for (String messageCandidate : messagesAtLevel) {
      if (messageCandidate.contains(message)) {
        return new Which<>(assertThat(messageCandidate)
            .named(String.format("log message at %s matching '%s'", level, message)));
      }
    }
    throw new AssertionError("Message check passed yet matching message not found");
  }

  public static SimpleSubjectBuilder<LogsSubject, TestLogHandler> assertAboutLogs() {
    return assertAbout(new SubjectFactory());
  }
}
