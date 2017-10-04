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

import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.isNull;
import static com.google.common.base.Predicates.or;
import static com.google.common.collect.Iterables.all;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.transformValues;
import static com.google.common.collect.Sets.newLinkedHashSet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Ordering;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Ignore;
import com.googlecode.objectify.annotation.Parent;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A collection of static methods that deal with reflection on model classes. */
public class ModelUtils {

  /** Caches all instance fields on an object, including non-public and inherited fields. */
  private static final LoadingCache<Class<?>, ImmutableMap<String, Field>> ALL_FIELDS_CACHE =
      CacheBuilder.newBuilder().build(new CacheLoader<Class<?>, ImmutableMap<String, Field>>() {
          @Override
          public ImmutableMap<String, Field> load(Class<?> clazz) {
            Deque<Class<?>> hierarchy = new LinkedList<>();
            // Walk the hierarchy up to but not including ImmutableObject (to ignore hashCode).
            for (; clazz != ImmutableObject.class; clazz = clazz.getSuperclass()) {
              // Add to the front, so that shadowed fields show up later in the list.
              // This will mean that getFieldValues will show the most derived value.
              hierarchy.addFirst(clazz);
            }
            Map<String, Field> fields = new LinkedHashMap<>();
            for (Class<?> hierarchyClass : hierarchy) {
              // Don't use hierarchyClass.getFields() because it only picks up public fields.
              for (Field field : hierarchyClass.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                  field.setAccessible(true);
                  fields.put(field.getName(), field);
                }
              }
            }
            return ImmutableMap.copyOf(fields);
          }});

  /** Lists all instance fields on an object, including non-public and inherited fields. */
  static Map<String, Field> getAllFields(Class<?> clazz) {
    return ALL_FIELDS_CACHE.getUnchecked(clazz);
  }

  /** Return a string representing the persisted schema of a type or enum. */
  static String getSchema(Class<?> clazz) {
    StringBuilder stringBuilder = new StringBuilder();
    Iterable<?> body;
    if (clazz.isEnum()) {
      stringBuilder.append("enum ");
      body = FluentIterable.from(clazz.getEnumConstants());
    } else {
      stringBuilder.append("class ");
      body = FluentIterable.from(getAllFields(clazz).values())
          .filter(new Predicate<Field>() {
            @Override
            public boolean apply(Field field) {
              return !field.isAnnotationPresent(Ignore.class);
            }})
          .transform(new Function<Field, Object>() {
            @Override
            public Object apply(Field field) {
              String annotation = field.isAnnotationPresent(Id.class)
                  ?  "@Id "
                  : field.isAnnotationPresent(Parent.class)
                      ? "@Parent "
                      : "";
              String type = field.getType().isArray()
                  ? field.getType().getComponentType().getName() + "[]"
                  : field.getGenericType().toString().replaceFirst("class ", "");
              return String.format("%s%s %s", annotation, type, field.getName());
            }});
    }
    return stringBuilder
        .append(clazz.getName()).append(" {\n  ")
        .append(Joiner.on(";\n  ").join(Ordering.usingToString().sortedCopy(body)))
        .append(";\n}")
        .toString();
  }

  /**
   * Returns the set of Class objects of all persisted fields. This includes the parameterized
   * type(s) of any fields (if any).
   */
  static Set<Class<?>> getPersistedFieldTypes(Class<?> clazz) {
    ImmutableSet.Builder<Class<?>> builder = new ImmutableSet.Builder<>();
    for (Field field : getAllFields(clazz).values()) {
      // Skip fields that aren't persisted to Datastore.
      if (field.isAnnotationPresent(Ignore.class)) {
        continue;
      }

      // If the field's type is the same as the field's class object, then it's a non-parameterized
      // type, and thus we just add it directly. We also don't bother looking at the parameterized
      // types of Key objects, since they are just references to other objects and don't actually
      // embed themselves in the persisted object anyway.
      Class<?> fieldClazz = field.getType();
      Type fieldType = field.getGenericType();
      builder.add(fieldClazz);
      if (fieldType.equals(fieldClazz) || Key.class.equals(clazz)) {
        continue;
      }

      // If the field is a parameterized type, then also add the parameterized field.
      if (fieldType instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) fieldType;
        for (Type actualType : parameterizedType.getActualTypeArguments()) {
          if (actualType instanceof Class<?>) {
            builder.add((Class<?>) actualType);
          } else {
            // We intentionally ignore types that are parameterized on non-concrete types. In theory
            // we could have collections embedded within collections, but Objectify does not allow
            // that.
          }
        }
      }
    }
    return builder.build();
  }

  /** Retrieves a field value via reflection. */
  static Object getFieldValue(Object instance, Field field) {
    try {
      return field.get(instance);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Sets a field value via reflection. */
  static void setFieldValue(Object instance, Field field, Object value) {
    try {
      field.set(instance, value);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Returns a map from Field objects (including non-public and inherited fields) to values.
   *
   * <p>This turns arrays into {@link List} objects so that ImmutableObject can more easily use the
   * returned map in its implementation of {@link ImmutableObject#toString} and {@link
   * ImmutableObject#equals}, which work by comparing and printing these maps.
   */
  static Map<Field, Object> getFieldValues(Object instance) {
    // Don't make this ImmutableMap because field values can be null.
    Map<Field, Object> values = new LinkedHashMap<>();
    for (Field field : getAllFields(instance.getClass()).values()) {
      Object value = getFieldValue(instance, field);
      if (value != null && value.getClass().isArray()) {
        // It's surprisingly difficult to convert arrays into lists if the array might be primitive.
        final Object arrayValue = value;
        value = new AbstractList<Object>() {
            @Override
            public Object get(int index) {
              return Array.get(arrayValue, index);
            }

            @Override
            public int size() {
              return Array.getLength(arrayValue);
            }};
      }
      values.put(field, value);
    }
    return values;
  }

  /** Functional helper for {@link #cloneEmptyToNull}. */
  private static final Function<Object, ?> CLONE_EMPTY_TO_NULL = new Function<Object, Object>() {
    @Override
      public Object apply(Object obj) {
        if (obj instanceof ImmutableSortedMap) {
          // ImmutableSortedMapTranslatorFactory handles empty for us. If the object is null, then
          // its on-save hook can't run.
          return obj;
        }
        if ("".equals(obj)
            || (obj instanceof Collection && ((Collection<?>) obj).isEmpty())
            || (obj instanceof Map && ((Map<?, ?>) obj).isEmpty())
            || (obj != null && obj.getClass().isArray() && Array.getLength(obj) == 0)) {
          return null;
        }
        Predicate<Object> immutableObjectOrNull = or(isNull(), instanceOf(ImmutableObject.class));
        if ((obj instanceof Set || obj instanceof List)
            && all((Iterable<?>) obj, immutableObjectOrNull)) {
          // Recurse into sets and lists, but only if they contain ImmutableObjects.
          FluentIterable<?> fluent = FluentIterable.from((Iterable<?>) obj).transform(this);
          return (obj instanceof List) ? newArrayList(fluent) : newLinkedHashSet(fluent);
        }
        if (obj instanceof Map && all(((Map<?, ?>) obj).values(), immutableObjectOrNull)) {
          // Recurse into maps with ImmutableObject values.
          return transformValues((Map<?, ?>) obj, this);
        }
        if (obj instanceof ImmutableObject) {
          // Recurse on the fields of an ImmutableObject.
          ImmutableObject copy = ImmutableObject.clone((ImmutableObject) obj);
          for (Field field : getAllFields(obj.getClass()).values()) {
            Object oldValue = getFieldValue(obj, field);
            Object newValue = apply(oldValue);
            if (!Objects.equals(oldValue, newValue)) {
              setFieldValue(copy, field, newValue);
            }
          }
          return copy;
        }
        return obj;
      }};

  /** Returns a clone of the object and sets empty collections, arrays, maps and strings to null. */
  @SuppressWarnings("unchecked")
  protected static <T extends ImmutableObject> T cloneEmptyToNull(T obj) {
    return (T) CLONE_EMPTY_TO_NULL.apply(obj);
  }

  @VisibleForTesting
  static void resetCaches() {
    ALL_FIELDS_CACHE.invalidateAll();
  }
}
