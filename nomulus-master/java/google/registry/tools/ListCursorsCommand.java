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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.collect.Ordering;
import google.registry.model.common.Cursor;
import google.registry.model.common.Cursor.CursorType;
import google.registry.model.registry.Registries;
import google.registry.model.registry.Registry;
import google.registry.model.registry.Registry.TldType;
import google.registry.tools.Command.RemoteApiCommand;
import java.util.ArrayList;
import java.util.List;

/** Lists {@link Cursor} timestamps used by locking rolling cursor tasks, like in RDE. */
@Parameters(separators = " =", commandDescription = "Lists cursor timestamps used by LRC tasks")
final class ListCursorsCommand implements RemoteApiCommand {

  @Parameter(
      names = "--type",
      description = "Which cursor to list.",
      required = true)
  private CursorType cursorType;

  @Parameter(
      names = "--tld_type",
      description = "Filter TLDs of a certain type (REAL or TEST.)")
  private TldType filterTldType = TldType.REAL;

  @Parameter(
      names = "--escrow_enabled",
      description = "Filter TLDs to only include those with RDE escrow enabled.")
  private boolean filterEscrowEnabled;

  @Override
  public void run() throws Exception {
    List<String> lines = new ArrayList<>();
    for (String tld : Registries.getTlds()) {
      Registry registry = Registry.get(tld);
      if (filterTldType != registry.getTldType()) {
        continue;
      }
      if (filterEscrowEnabled && !registry.getEscrowEnabled()) {
        continue;
      }
      Cursor cursor = ofy().load().key(Cursor.createKey(cursorType, registry)).now();
      lines.add(String.format("%-25s%s", cursor != null ? cursor.getCursorTime() : "absent", tld));
    }
    for (String line : Ordering.natural().sortedCopy(lines)) {
      System.out.println(line);
    }
  }
}
