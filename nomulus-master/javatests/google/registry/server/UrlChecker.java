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

package google.registry.server;

import static com.google.common.base.Throwables.throwIfUnchecked;

import com.google.common.util.concurrent.SimpleTimeLimiter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

final class UrlChecker {

  private static final int READ_TIMEOUT_MS = 1000;
  private static final int CONNECT_TIMEOUT_MS = 500;

  /** Probes {@code url} until it becomes available. */
  static void waitUntilAvailable(final URL url, int timeoutMs) {
    try {
      new SimpleTimeLimiter().callWithTimeout(new Callable<Void>() {
        @Nullable
        @Override
        public Void call() throws InterruptedException, IOException {
          int exponentialBackoffMs = 1;
          while (true) {
            if (isAvailable(url)) {
              return null;
            }
            Thread.sleep(exponentialBackoffMs *= 2);
          }
        }
      }, timeoutMs, TimeUnit.MILLISECONDS, true);
    } catch (Exception e) {
      throwIfUnchecked(e);
      throw new RuntimeException(e);
    }
  }

  /** Returns {@code true} if page is available and returns {@code 200 OK}. */
  static boolean isAvailable(URL url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    try {
      connection.connect();
      return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
    } catch (IOException e) {
      return false;
    } finally {
      connection.disconnect();
    }
  }

  private UrlChecker() {}
}
