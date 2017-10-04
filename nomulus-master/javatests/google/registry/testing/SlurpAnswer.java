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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/** Utility class for capturing channel output bytes from google cloud storage library mock. */
public final class SlurpAnswer implements Answer<Integer> {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();

  @Override
  public Integer answer(@SuppressWarnings("null") InvocationOnMock invocation) throws Exception {
    ByteBuffer bytes = (ByteBuffer) invocation.getArguments()[0];
    int count = 0;
    while (bytes.hasRemaining()) {
      out.write(bytes.get());
      ++count;
    }
    return count;
  }

  public byte[] toByteArray() {
    return out.toByteArray();
  }
}
