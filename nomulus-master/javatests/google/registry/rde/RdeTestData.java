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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;

/** Utility class providing easy access to contents of the {@code testdata/} directory. */
public final class RdeTestData {

  /** Returns {@link ByteSource} for file in {@code rde/testdata/} directory. */
  public static ByteSource get(String filename) {
    return Resources.asByteSource(getUrl(filename));
  }

  /**
   * Loads data from file in {@code rde/testdata/} as a String (assuming file is UTF-8).
   *
   * @throws IOException if the file couldn't be loaded from the jar.
   */
  public static String loadUtf8(String filename) throws IOException {
    return Resources.asCharSource(getUrl(filename), UTF_8).read();
  }

  private static URL getUrl(String filename) {
    return Resources.getResource(RdeTestData.class, "testdata/" + filename);
  }
}
