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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.io.File;

/** Compare two database backups. */
class CompareDbBackups {

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      System.err.println("Usage: compare_db_backups <directory1> <directory2>");
      return;
    }

    ImmutableSet<ComparableEntity> entities1 =
        new RecordAccumulator().readDirectory(new File(args[0])).getComparableEntitySet();
    ImmutableSet<ComparableEntity> entities2 =
        new RecordAccumulator().readDirectory(new File(args[1])).getComparableEntitySet();

    // Calculate the entities added and removed.
    SetView<ComparableEntity> added = Sets.difference(entities2, entities1);
    SetView<ComparableEntity> removed = Sets.difference(entities1, entities2);

    printHeader(
        String.format("First backup: %d records", entities1.size()),
        String.format("Second backup: %d records", entities2.size()));

    if (!removed.isEmpty()) {
      printHeader(removed.size() + " records were removed:");
      for (ComparableEntity entity : removed) {
        System.out.println(entity);
      }
    }

    if (!added.isEmpty()) {
      printHeader(added.size() + " records were added:");
      for (ComparableEntity entity : added) {
        System.out.println(entity);
      }
    }
  }

  /** Print out multi-line text in a pretty ASCII header frame. */
  private static void printHeader(String... headerLines) {
    System.out.println("========================================================================");
    for (String line : headerLines) {
      System.out.println("| " + line);
    }
    System.out.println("========================================================================");
  }
}
