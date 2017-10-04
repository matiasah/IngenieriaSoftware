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

import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Lists.newArrayList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.primitives.Primitives;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/** Helper class for diff utilities. */
public final class DiffUtils {

  /**
   * A helper class to store the two sides of a diff. If both sides are Sets then they will be
   * diffed, otherwise the two objects are toStringed in Collection format "[a, b]".
   */
  private static class DiffPair {
    @Nullable
    final Object a;

    @Nullable
    final Object b;

    DiffPair(@Nullable Object a, @Nullable Object b) {
      this.a = a;
      this.b = b;
    }

    @Override
    public String toString() {
      // Note that we use newArrayList here instead of ImmutableList because a and b can be null.
      return newArrayList(a, b).toString();
    }
  }

  /** Pretty-prints a deep diff between two maps that represent Datastore entities. */
  public static String prettyPrintEntityDeepDiff(Map<?, ?> a, Map<?, ?> b) {
    return prettyPrintDiffedMap(deepDiff(a, b, true), null);
  }

  /**
   * Pretty-prints a deep diff between two maps that represent XML documents. Path is prefixed to
   * each output line of the diff.
   */
  public static String prettyPrintXmlDeepDiff(Map<?, ?> a, Map<?, ?> b, @Nullable String path) {
    return prettyPrintDiffedMap(deepDiff(a, b, false), path);
  }

  /** Compare two maps and return a map containing, at each key where they differed, both values. */
  public static ImmutableMap<?, ?> deepDiff(
      Map<?, ?> a, Map<?, ?> b, boolean ignoreNullToCollection) {
    ImmutableMap.Builder<Object, Object> diff = new ImmutableMap.Builder<>();
    for (Object key : Sets.union(a.keySet(), b.keySet())) {
      Object aValue = a.get(key);
      Object bValue = b.get(key);
      if (Objects.equals(aValue, bValue)) {
        // The objects are equal, so print nothing.
      } else if (ignoreNullToCollection
          && aValue == null
          && bValue instanceof Collection
          && ((Collection<?>) bValue).isEmpty()) {
        // Ignore a mismatch between Objectify's use of null to store empty collections and our
        // code's builder methods, which yield empty collections for the same fields.  This
        // prevents useless lines of the form "[null, []]" from appearing in diffs.
      } else {
        // The objects aren't equal, so output a diff.
        if (aValue instanceof String && bValue instanceof String
            && a.toString().contains("\n") && b.toString().contains("\n")) {
          aValue = stringToMap((String) aValue);
          bValue = stringToMap((String) bValue);
        } else if (aValue instanceof Set && bValue instanceof Set) {
          // Leave Sets alone; prettyPrintDiffedMap has special handling for Sets.
        } else if (aValue instanceof Iterable && bValue instanceof Iterable) {
          aValue = iterableToSortedMap((Iterable<?>) aValue);
          bValue = iterableToSortedMap((Iterable<?>) bValue);
        }
        diff.put(key, (aValue instanceof Map && bValue instanceof Map)
            ? deepDiff((Map<?, ?>) aValue, (Map<?, ?>) bValue, ignoreNullToCollection)
            : new DiffPair(aValue, bValue));
      }
    }
    return diff.build();
  }

  private static Map<Integer, ?> iterableToSortedMap(Iterable<?> iterable) {
    // We use a sorted map here so that the iteration across the keySet is consistent.
    ImmutableSortedMap.Builder<Integer, Object> builder =
        new ImmutableSortedMap.Builder<>(Ordering.natural());
    int i = 0;
    for (Object item : Iterables.filter(iterable, notNull())) {
      builder.put(i++, item);
    }
    return builder.build();
  }

  private static Map<String, ?> stringToMap(String string) {
    ImmutableMap.Builder<String, Object> builder = new ImmutableMap.Builder<>();
    int i = 0;
    for (String item : Splitter.on('\n').split(string)) {
      builder.put("Line " + i++, item);
    }
    return builder.build();
  }

  /** Recursively pretty prints the contents of a diffed map generated by {@link #deepDiff}. */
  public static String prettyPrintDiffedMap(Map<?, ?> map, @Nullable String path) {
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String newPath = (path == null ? "" : path + ".") + entry.getKey();
      String output;
      Object value = entry.getValue();
      if (value instanceof Map) {
        output = prettyPrintDiffedMap((Map<?, ?>) entry.getValue(), newPath);
      } else if (value instanceof DiffPair
          && ((DiffPair) value).a instanceof Set
          && ((DiffPair) value).b instanceof Set) {
        DiffPair pair = ((DiffPair) value);
        String prettyLineDiff = prettyPrintSetDiff((Set<?>) pair.a, (Set<?>) pair.b) + "\n";
        output = newPath + ((prettyLineDiff.startsWith("\n")) ? " ->" : " -> ") + prettyLineDiff;
      } else {
        output = newPath + " -> " + value + "\n";
      }
      builder.append(output);
    }
    return builder.toString();
  }

  /**
   * Returns a string displaying the differences between the old values in a set and the new ones.
   */
  @VisibleForTesting
  static String prettyPrintSetDiff(Set<?> a, Set<?> b) {
    Set<?> removed = Sets.difference(a, b);
    Set<?> added = Sets.difference(b, a);
    if (removed.isEmpty() && added.isEmpty()) {
      return "NO DIFFERENCES";
    }
    return Joiner.on("\n    ").skipNulls().join("",
        !added.isEmpty() ? ("ADDED:" + formatSetContents(added)) : null,
        !removed.isEmpty() ? ("REMOVED:" + formatSetContents(removed)) : null,
        "FINAL CONTENTS:" + formatSetContents(b));
  }

  /**
   * Returns a formatted listing of Set contents, using a single line format if all elements are
   * wrappers of primitive types or Strings, and a multiline (one object per line) format if they
   * are not.
   */
  private static String formatSetContents(Set<?> set) {
    for (Object obj : set) {
      if (!Primitives.isWrapperType(obj.getClass()) && !(obj instanceof String)) {
        return "\n        " + Joiner.on(",\n        ").join(set);
      }
    }
    return " " + set;
  }

  private DiffUtils() {}
}
