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

import static google.registry.model.ofy.ObjectifyService.ofy;
import static google.registry.util.CollectionUtils.isNullOrEmpty;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import google.registry.model.common.Cursor;
import google.registry.model.common.Cursor.CursorType;
import google.registry.model.registry.Registry;
import google.registry.tools.params.DateTimeParameter;
import java.util.List;
import org.joda.time.DateTime;

/** Modifies {@link Cursor} timestamps used by locking rolling cursor tasks, like in RDE. */
@Parameters(separators = " =", commandDescription = "Modifies cursor timestamps used by LRC tasks")
final class UpdateCursorsCommand extends MutatingCommand {

  @Parameter(description = "TLDs on which to operate. Omit for global cursors.")
  private List<String> tlds;

  @Parameter(
      names = "--type",
      description = "Which cursor to update.",
      required = true)
  private CursorType cursorType;

  @Parameter(
      names = "--timestamp",
      description = "The new timestamp to set.",
      validateWith = DateTimeParameter.class,
      required = true)
  private DateTime newTimestamp;

  @Override
  protected void init() throws Exception {
    if (isNullOrEmpty(tlds)) {
      Cursor cursor = ofy().load().key(Cursor.createGlobalKey(cursorType)).now();
      stageEntityChange(cursor, Cursor.createGlobal(cursorType, newTimestamp));
    } else {
      for (String tld : tlds) {
        Registry registry = Registry.get(tld);
        Cursor cursor = ofy().load().key(Cursor.createKey(cursorType, registry)).now();
        stageEntityChange(
            cursor,
            Cursor.create(cursorType, newTimestamp, registry));
      }
    }
  }
}
