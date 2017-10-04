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

package google.registry.model;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.FluentIterable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Helper class for {@link Jsonifiable} classes to generate JSON maps for RPC responses.
 *
 * <p>The returned map is mutable. Map entries can be {@code null} but list entries can not. If a
 * list is passed as {@code null}, it'll be substituted with empty list. Lists are not mutable.
 */
public final class JsonMapBuilder {

  private static final Function<Jsonifiable, Map<String, Object>> TO_JSON_OBJECT =
      new Function<Jsonifiable, Map<String, Object>>() {
        @Override
        public Map<String, Object> apply(Jsonifiable input) {
          return input.toJsonMap();
        }};

  private final Map<String, Object> map = new LinkedHashMap<>();

  public JsonMapBuilder put(String name, @Nullable Boolean value) {
    map.put(name, value);
    return this;
  }

  public JsonMapBuilder put(String name, @Nullable Number value) {
    map.put(name, value);
    return this;
  }

  public JsonMapBuilder put(String name, @Nullable String value) {
    map.put(name, value);
    return this;
  }

  public JsonMapBuilder put(String name, @Nullable Jsonifiable value) {
    map.put(name, value == null ? null : value.toJsonMap());
    return this;
  }

  public JsonMapBuilder put(String name, @Nullable Enum<?> value) {
    map.put(name, value == null ? null : value.name());
    return this;
  }

  public <T> JsonMapBuilder putString(String name, @Nullable T value) {
    map.put(name, value == null ? null : value.toString());
    return this;
  }

  public <T> JsonMapBuilder putListOfStrings(String name, @Nullable Iterable<T> value) {
    map.put(name, value == null ? Collections.EMPTY_LIST
        : FluentIterable.from(value).transform(Functions.toStringFunction()).toList());
    return this;
  }

  public JsonMapBuilder putListOfJsonObjects(
      String name, @Nullable Iterable<? extends Jsonifiable> value) {
    map.put(name, value == null ? Collections.EMPTY_LIST
        : FluentIterable.from(value).transform(TO_JSON_OBJECT).toList());
    return this;
  }

  /** Returns mutable JSON object. Please dispose of the builder object after calling me. */
  public Map<String, Object> build() {
    return map;
  }
}
