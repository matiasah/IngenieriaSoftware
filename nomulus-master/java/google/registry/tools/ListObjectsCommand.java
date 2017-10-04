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

package google.registry.tools;

import static google.registry.security.JsonHttp.JSON_SAFETY_PREFIX;
import static google.registry.tools.server.ListObjectsAction.FIELDS_PARAM;
import static google.registry.tools.server.ListObjectsAction.FULL_FIELD_NAMES_PARAM;
import static google.registry.tools.server.ListObjectsAction.PRINT_HEADER_ROW_PARAM;

import com.beust.jcommander.Parameter;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import google.registry.tools.Command.RemoteApiCommand;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.json.simple.JSONValue;

/**
 * Abstract base class for commands that list objects by calling a server task.
 *
 * <p>The formatting is done on the server side; this class just dumps the results to the screen.
 */
abstract class ListObjectsCommand implements RemoteApiCommand, ServerSideCommand {

  @Nullable
  @Parameter(
      names = {"-f", "--fields"},
      description = "Comma-separated list of fields to show for each object listed")
  private String fields;

  @Nullable
  @Parameter(
      names = {"--header"},
      description = "Whether or not to print a header row for the resulting output - default is to "
          + "only print headers when more than one column is output",
      arity = 1)
  private Boolean printHeaderRow;

  @Parameter(
      names = {"--full_field_names"},
      description = "Whether to print full field names in header row (as opposed to aliases)")
  private boolean fullFieldNames = false;

  private Connection connection;

  @Override
  public void setConnection(Connection connection) {
    this.connection = connection;
  }

  /** Returns the path to the servlet task. */
  abstract String getCommandPath();

  /** Returns a map of parameters to be sent to the server
   * (in addition to the usual ones). */
  @Nullable
  ImmutableMap<String, Object> getParameterMap() {
    return null;
  }

  @Override
  public void run() throws Exception {
    ImmutableMap.Builder<String, Object> params = new ImmutableMap.Builder<>();
    if (fields != null) {
      params.put(FIELDS_PARAM, fields);
    }
    if (printHeaderRow != null) {
      params.put(PRINT_HEADER_ROW_PARAM, printHeaderRow);
    }
    if (fullFieldNames) {
      params.put(FULL_FIELD_NAMES_PARAM, Boolean.TRUE);
    }
    ImmutableMap<String, Object> extraParams = getParameterMap();
    if (extraParams != null) {
      params.putAll(extraParams);
    }
    // Call the server and get the response data.
    String response = connection.send(
        getCommandPath(),
        params.build(),
        MediaType.PLAIN_TEXT_UTF_8,
        new byte[0]);
    // Parse the returned JSON and make sure it's a map.
    Object obj = JSONValue.parse(response.substring(JSON_SAFETY_PREFIX.length()));
    if (!(obj instanceof Map<?, ?>)) {
      throw new VerifyException("Server returned unexpected JSON: " + response);
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> responseMap = (Map<String, Object>) obj;
    // Get the status.
    obj = responseMap.get("status");
    if (obj == null) {
      throw new VerifyException("Server returned no status");
    }
    if (!(obj instanceof String)) {
      throw new VerifyException("Server returned non-string status");
    }
    String status = (String) obj;
    // Handle errors.
    if (status.equals("error")) {
      obj = responseMap.get("error");
      if (obj == null) {
        throw new VerifyException("Server returned no error message");
      }
      System.out.println(obj);
    // Handle success.
    } else if (status.equals("success")) {
      obj = responseMap.get("lines");
      if (obj == null) {
        throw new VerifyException("Server returned no response data");
      }
      if (!(obj instanceof List<?>)) {
        throw new VerifyException("Server returned unexpected response data");
      }
      for (Object lineObj : (List<?>) obj) {
        System.out.println(lineObj);
      }
    // Handle unexpected status values.
    } else {
      throw new VerifyException("Server returned unexpected status");
    }
  }
}
