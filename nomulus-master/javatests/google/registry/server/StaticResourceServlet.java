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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import com.google.common.primitives.Ints;
import google.registry.util.FormattingLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.PostConstruct;
import javax.servlet.ServletConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.mortbay.jetty.servlet.ServletHolder;

/**
 * Servlet for serving static resources on a Jetty development server path prefix.
 *
 * <p>This servlet can serve either a single file or the contents of a directory.
 *
 * <p><b>Note:</b> This code should never be used in production. It's for testing purposes only.
 */
public final class StaticResourceServlet extends HttpServlet {

  private static final FormattingLogger logger = FormattingLogger.getLoggerForCallerClass();

  private static final MediaType DEFAULT_MIME_TYPE = MediaType.OCTET_STREAM;

  private static final ImmutableMap<String, MediaType> MIMES_BY_EXTENSION =
      new ImmutableMap.Builder<String, MediaType>()
          .put("css", MediaType.CSS_UTF_8)
          .put("csv", MediaType.CSV_UTF_8)
          .put("gif", MediaType.GIF)
          .put("html", MediaType.HTML_UTF_8)
          .put("jpeg", MediaType.JPEG)
          .put("jpg", MediaType.JPEG)
          .put("js", MediaType.JAVASCRIPT_UTF_8)
          .put("json", MediaType.JSON_UTF_8)
          .put("png", MediaType.PNG)
          .put("txt", MediaType.PLAIN_TEXT_UTF_8)
          .put("xml", MediaType.XML_UTF_8)
          .build();

  /**
   * Creates a servlet holder for this servlet so it can be used with Jetty.
   *
   * @param prefix servlet path starting with a slash and ending with {@code "/*"} if {@code root}
   *     is a directory
   * @param root file or root directory to serve
   */
  public static ServletHolder create(String prefix, Path root) {
    root = root.toAbsolutePath();
    checkArgument(Files.exists(root), "Root must exist: %s", root);
    checkArgument(prefix.startsWith("/"), "Prefix must start with a slash: %s", prefix);
    ServletHolder holder = new ServletHolder(StaticResourceServlet.class);
    holder.setInitParameter("root", root.toString());
    if (Files.isDirectory(root)) {
      checkArgument(prefix.endsWith("/*"),
          "Prefix (%s) must end with /* since root (%s) is a directory", prefix, root);
      holder.setInitParameter("prefix", prefix.substring(0, prefix.length() - 1));
    } else {
      holder.setInitParameter("prefix", prefix);
    }
    return holder;
  }

  private Optional<FileServer> fileServer = Optional.absent();

  @Override
  @PostConstruct
  public void init(ServletConfig config) {
    Path root = Paths.get(config.getInitParameter("root"));
    String prefix = config.getInitParameter("prefix");
    verify(prefix.startsWith("/"));
    boolean isDirectory = Files.isDirectory(root);
    verify(!isDirectory || isDirectory && prefix.endsWith("/"));
    fileServer = Optional.of(new FileServer(root, prefix));
  }

  @Override
  public void doHead(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    fileServer.get().doHead(req, rsp);
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
    fileServer.get().doGet(req, rsp);
  }

  private static final class FileServer {
    private final Path root;
    private final String prefix;

    FileServer(Path root, String prefix) {
      this.root = root;
      this.prefix = prefix;
    }

    Optional<Path> doHead(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
      verify(req.getRequestURI().startsWith(prefix));
      Path file = root.resolve(req.getRequestURI().substring(prefix.length()));
      if (!Files.exists(file)) {
        logger.infofmt("Not found: %s (%s)", req.getRequestURI(), file);
        rsp.sendError(SC_NOT_FOUND, "Not found");
        return Optional.absent();
      }
      if (Files.isDirectory(file)) {
        logger.infofmt("Directory listing forbidden: %s (%s)", req.getRequestURI(), file);
        rsp.sendError(SC_FORBIDDEN, "No directory listing");
        return Optional.absent();
      }
      rsp.setContentType(
          firstNonNull(
                  MIMES_BY_EXTENSION.get(getExtension(file.getFileName().toString())),
                  DEFAULT_MIME_TYPE)
              .toString());
      rsp.setContentLength(Ints.checkedCast(Files.size(file)));
      return Optional.of(file);
    }

    void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
      for (Path file : doHead(req, rsp).asSet()) {
        rsp.getOutputStream().write(Files.readAllBytes(file));
      }
    }
  }

  private static String getExtension(String filename) {
    int dot = filename.lastIndexOf('.');
    return dot == -1 ? "" : filename.substring(dot + 1);
  }
}
