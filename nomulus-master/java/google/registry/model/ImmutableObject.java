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

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Maps.transformValues;
import static google.registry.model.ofy.ObjectifyService.ofy;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Ignore;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.annotation.concurrent.Immutable;
import javax.xml.bind.annotation.XmlTransient;

/** An immutable object that implements {@link #equals}, {@link #hashCode} and {@link #toString}. */
@Immutable
@XmlTransient
public abstract class ImmutableObject implements Cloneable {

  /** Marker to indicate that {@link #toHydratedString} should not hydrate a field. */
  @Documented
  @Retention(RUNTIME)
  @Target(FIELD)
  public static @interface DoNotHydrate {}

  @Ignore
  @XmlTransient
  Integer hashCode;

  private boolean equalsImmutableObject(ImmutableObject other) {
    return getClass().equals(other.getClass())
        && hashCode() == other.hashCode()
        && ModelUtils.getFieldValues(this).equals(ModelUtils.getFieldValues(other));
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ImmutableObject && equalsImmutableObject((ImmutableObject) other);
  }

  @Override
  public int hashCode() {
    if (hashCode == null) {
      hashCode = Arrays.hashCode(ModelUtils.getFieldValues(this).values().toArray());
    }
    return hashCode;
  }

  /** Returns a clone of the given object. */
  @SuppressWarnings("unchecked")
  protected static <T extends ImmutableObject> T clone(T t) {
    try {
      T clone = (T) t.clone();
      // Clear the hashCode since we often mutate clones before handing them out.
      clone.hashCode = null;
      return clone;
    } catch (CloneNotSupportedException e) {  // Yes it is.
      throw new IllegalStateException();
    }
  }

  /** Returns a clone of the given object with empty fields set to null. */
  protected static <T extends ImmutableObject> T cloneEmptyToNull(T t) {
    return ModelUtils.cloneEmptyToNull(t);
  }

  /**
   * Returns a string view of the object, formatted like:
   *
   * <pre>
   * ModelObject (@12345): {
   *   field1=value1
   *   field2=[a,b,c]
   *   field3=AnotherModelObject: {
   *     foo=bar
   *   }
   * }
   * </pre>
   */
  @Override
  public String toString() {
    NavigableMap<String, Object> sortedFields = new TreeMap<>();
    for (Entry<Field, Object> entry : ModelUtils.getFieldValues(this).entrySet()) {
      sortedFields.put(entry.getKey().getName(), entry.getValue());
    }
    return toStringHelper(sortedFields);
  }

  /** Similar to toString(), with a full expansion of referenced keys, including in collections. */
  public String toHydratedString() {
    // We can't use ImmutableSortedMap because we need to allow null values.
    NavigableMap<String, Object> sortedFields = new TreeMap<>();
    for (Entry<Field, Object> entry : ModelUtils.getFieldValues(this).entrySet()) {
      Field field = entry.getKey();
      Object value = entry.getValue();
      sortedFields.put(
          field.getName(),
          field.isAnnotationPresent(DoNotHydrate.class) ? value : HYDRATOR.apply(value));
    }
    return toStringHelper(sortedFields);
  }

  public String toStringHelper(SortedMap<String, Object> fields) {
    return String.format(
        "%s (@%s): {\n%s",
        getClass().getSimpleName(),
        System.identityHashCode(this),
        Joiner.on('\n').join(fields.entrySet()))
            .replaceAll("\n", "\n    ") + "\n}";
  }

  /** Helper function to recursively hydrate an ImmutableObject. */
  private static final Function<Object, Object> HYDRATOR =
      new Function<Object, Object>() {
        @Override
        public Object apply(Object value) {
          if (value instanceof Key) {
            return apply(ofy().load().key((Key<?>) value).now());
          } else if (value instanceof Map) {
            return transformValues((Map<?, ?>) value, this);
          } else if (value instanceof Collection) {
            return transform((Collection<?>) value, this);
          } else if (value instanceof ImmutableObject) {
            return ((ImmutableObject) value).toHydratedString();
          }
          return value;
        }};

  /** Helper function to recursively convert a ImmutableObject to a Map of generic objects. */
  private static final Function<Object, Object> TO_MAP_HELPER = new Function<Object, Object>() {
    @Override
    public Object apply(Object o) {
      if (o == null) {
        return null;
      } else if (o instanceof ImmutableObject) {
        // LinkedHashMap to preserve field ordering and because ImmutableMap forbids null values.
        Map<String, Object> result = new LinkedHashMap<>();
        for (Entry<Field, Object> entry : ModelUtils.getFieldValues(o).entrySet()) {
          result.put(entry.getKey().getName(), apply(entry.getValue()));
        }
        return result;
      } else if (o instanceof Map) {
        return Maps.transformValues((Map<?, ?>) o, this);
      } else if (o instanceof Set) {
        return FluentIterable.from((Set<?>) o).transform(this).toSet();
      } else if (o instanceof Collection) {
        return FluentIterable.from((Collection<?>) o).transform(this).toList();
      } else if (o instanceof Number || o instanceof Boolean) {
        return o;
      } else {
        return o.toString();
      }
    }};

  /** Returns a map of all object fields (including sensitive data) that's used to produce diffs. */
  @SuppressWarnings("unchecked")
  public Map<String, Object> toDiffableFieldMap() {
    return (Map<String, Object>) TO_MAP_HELPER.apply(this);
  }
}
