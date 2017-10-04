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
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import javax.annotation.WillCloseWhenClosed;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * {@link InputStream} wrapper that offers some additional magic.
 *
 * <ul>
 * <li>Byte counting
 * <li>Log byte count on close
 * <li>Check expected byte count when closed (Optional)
 * <li>Close original {@link InputStream} when closed (Optional)
 * <li>Overridable {@link #onClose()} method
 * <li>Throws {@link NullPointerException} if read after {@link #close()}
 * </ul>
 *
 * @see ImprovedOutputStream
 * @see com.google.common.io.CountingInputStream
 */
@NotThreadSafe
public class ImprovedInputStream extends FilterInputStream {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private long count;
  private long mark = -1;
  private final long expected;
  private final boolean shouldClose;

  public ImprovedInputStream(@WillCloseWhenClosed InputStream out) {
    this(out, true, -1);
  }

  public ImprovedInputStream(InputStream in, boolean shouldClose, long expected) {
    super(checkNotNull(in, "in"));
    checkArgument(expected >= -1, "expected >= 0 or -1");
    this.shouldClose = shouldClose;
    this.expected = expected;
  }

  @Override
  @OverridingMethodsMustInvokeSuper
  public int read() throws IOException {
    int result = in.read();
    if (result != -1) {
      count++;
    }
    return result;
  }

  @Override
  public final int read(byte[] b) throws IOException {
    return this.read(b, 0, b.length);
  }

  @Override
  @OverridingMethodsMustInvokeSuper
  public int read(byte[] b, int off, int len) throws IOException {
    int result = in.read(b, off, len);
    if (result != -1) {
      count += result;
    }
    return result;
  }

  @Override
  public long skip(long n) throws IOException {
    long result = in.skip(n);
    count += result;
    return result;
  }

  @Override
  public synchronized void mark(int readlimit) {
    in.mark(readlimit);
    mark = count;
    // it's okay to mark even if mark isn't supported, as reset won't work
  }

  @Override
  public synchronized void reset() throws IOException {
    if (!in.markSupported()) {
      throw new IOException("Mark not supported");
    }
    if (mark == -1) {
      throw new IOException("Mark not set");
    }

    in.reset();
    count = mark;
  }

  /**
   * Logs byte count, checks byte count (optional), closes (optional), and self-sabotages.
   *
   * <p>This method may not be overridden, use {@link #onClose()} instead.
   *
   * @see InputStream#close()
   */
  @Override
  @NonFinalForTesting
  public void close() throws IOException {
    if (in == null) {
      return;
    }
    logger.infofmt("%s closed with %,d bytes read", getClass().getSimpleName(), count);
    if (expected != -1 && count != expected) {
      throw new IOException(String.format("%s expected %,d bytes but read %,d bytes",
          getClass().getCanonicalName(), expected, count));
    }
    onClose();
    if (shouldClose) {
      in.close();
    }
    in = null;
  }

  /**
   * Overridable method that's called by {@link #close()}.
   *
   * <p>This method does nothing by default.
   *
   * @throws IOException
   */
  protected void onClose() throws IOException {
    // Does nothing by default.
  }
}
