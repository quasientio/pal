/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.common.util.Classes;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.serdes.NonWrappableObjectException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for wrapping Java objects into Colfer {@code Obj} instances. Provides methods to
 * serialize objects, manage object references, and handle contextual wrapping based on specified
 * wrapping policies.
 *
 * <p>This class supports wrapping of simple types, collections, and maps, adhering to size
 * constraints and wrapping policies to provide a flexible and consistent serialization.
 */
public final class Wrapper {

  /** Logger instance for logging. */
  private static final Logger logger = LoggerFactory.getLogger(Wrapper.class);

  /** Maximum number of elements allowed in a collection to be considered wrappable. */
  private static final int MAX_WRAPPABLE_COLLECTION_SIZE = 1000;

  /** Per-type cache of Colfer {@code Class} wrappers keyed by Java {@link Class}. */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static final ClassValue<com.quasient.pal.messages.colfer.Class> WRAPPED_CLASS_BY_TYPE =
      new ClassValue<>() {
        @Override
        protected com.quasient.pal.messages.colfer.Class computeValue(Class<?> type) {
          com.quasient.pal.messages.colfer.Class c = new com.quasient.pal.messages.colfer.Class();
          c.setName(type == null ? "" : type.getName());
          return c;
        }
      };

  /** Intern table of Colfer {@code Class} by fully qualified class name. */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static final ConcurrentHashMap<String, com.quasient.pal.messages.colfer.Class>
      WRAPPED_CLASS_BY_NAME = new ConcurrentHashMap<>();

  /** Per-declaring-class cache of wrapped fields keyed by {@link Field}. */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static final ClassValue<ConcurrentHashMap<Field, com.quasient.pal.messages.colfer.Field>>
      WRAPPED_FIELD_BY_DECL_CLASS =
          new ClassValue<>() {
            @Override
            protected ConcurrentHashMap<Field, com.quasient.pal.messages.colfer.Field> computeValue(
                Class<?> type) {
              return new ConcurrentHashMap<>();
            }
          };

  /** Intern table of wrapped fields keyed by {@code "class#name#mod"}. */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static final ConcurrentHashMap<String, com.quasient.pal.messages.colfer.Field>
      WRAPPED_FIELD_BY_TRIPLE = new ConcurrentHashMap<>();

  /** Private constructor to prevent instantiation of the Wrapper class. */
  private Wrapper() {
    // avoid instantiation
  }

  /**
   * Public adapter around getWrappedObjectAux
   *
   * @param dst the Colfer Obj instance to populate with wrapped data
   * @param object the object to be wrapped
   * @param classname the class name of the object to wrap
   * @param objectRef the reference to the object, if any
   * @param wrapPolicy the policy that dictates how the object should be wrapped
   * @return the populated Colfer Obj instance representing the wrapped object
   */
  public static Obj wrapInto(
      Obj dst,
      @Nullable Object object,
      @Nullable String classname,
      @Nullable ObjectRef objectRef,
      @Nullable WrapPolicy wrapPolicy) {
    return getWrappedObjectAux(
        dst, object, classname, objectRef, wrapPolicy != null ? wrapPolicy : WrapPolicy.DETECT);
  }

  /**
   * Wraps the provided object into the specified wrappedObject based on the given parameters.
   * Determines whether the object should be serialized or referenced according to the wrap policy.
   *
   * @param wrappedObject the Colfer Obj instance to populate with wrapped data
   * @param object the object to be wrapped
   * @param givenClassName the class name of the object to wrap
   * @param objectRef the reference to the object, if any
   * @param wrapPolicy the policy that dictates how the object should be wrapped
   * @return the populated Colfer Obj instance representing the wrapped object
   */
  private static Obj getWrappedObjectAux(
      Obj wrappedObject,
      Object object,
      String givenClassName,
      ObjectRef objectRef,
      WrapPolicy wrapPolicy) {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObjectAux with object: {}, class: {}, objectRef: {}, wrapPolicy: {}",
          object,
          givenClassName,
          objectRef,
          wrapPolicy);
    }
    // set isNull
    boolean isNull = (object == null && objectRef == null);
    wrappedObject.setIsNull(isNull);

    // set ref
    if (objectRef != null) {
      wrappedObject.setRef(objectRef.getRef());
    }

    // determine and set class name
    final String className = pickClassName(object, givenClassName);
    wrappedObject.setClazz(getWrappedClass(className));

    // determine if we will serialize the value (as JSON)
    boolean wrapValue =
        switch (wrapPolicy) {
          case PREFER_REFERENCE -> // wrap value only if we don't have ref
              objectRef == null && isWrappable(object);
          case FORCE_BY_VALUE -> { // always wrap value if possible
            if (!isWrappable(object)) {
              throw new NonWrappableObjectException("Object cannot be serialized");
            }
            yield true;
          }
          case DETECT -> objectRef == null || isWrappable(object);
        };

    // regardless of the wrap policy, simple types and arrays of simple types are ALWAYS serialized
    boolean isSimpleType = isSimpleType(object);
    boolean isSimpleTypeArray = isSimpleTypeArray(object);

    if (isSimpleType || isSimpleTypeArray) {
      wrapValue = true;
    }

    if (object != null && wrapValue) {
      // try to wrap
      try {
        String json = JsonUtil.toJson(object);
        wrappedObject.setValue(json);
      } catch (Exception e) {
        if (objectRef == null) {
          throw new RuntimeException("ObjectRef is null but object could not be serialized", e);
        }
      }
    }
    if (logger.isTraceEnabled()) {
      logger.trace("out with wrappedValue: {}", ColferUtils.format(wrappedObject));
    }
    return wrappedObject;
  }

  /**
   * Determines the appropriate class name to use for wrapping. Uses the given class name if the
   * object is null or if the object is a primitive wrapper corresponding to the given class name.
   * Otherwise, it uses the object's actual class name.
   *
   * @param object the object whose class name is to be determined
   * @param givenClassName the provided class name
   * @return the class name to be used for wrapping
   */
  private static String pickClassName(Object object, String givenClassName) {
    if (object == null) {
      return givenClassName;
    }
    if (isCorrespondingPrimitive(object, givenClassName)) {
      return givenClassName;
    } else {
      return object.getClass().getName();
    }
  }

  /**
   * Checks if the given object is an instance of a primitive wrapper corresponding to the specified
   * class name.
   *
   * @param object the object to check
   * @param className the class name to compare against
   * @return true if the object is a primitive wrapper matching the class name, false otherwise
   */
  private static boolean isCorrespondingPrimitive(Object object, String className) {

    if (object == null) {
      return false;
    }
    Class<?> objectClass = object.getClass();

    if (Classes.isPrimitiveWrapper(objectClass) && className != null) {
      return Classes.getPrimitiveClassForWrapper(objectClass).getName().equals(className);
    }

    return false;
  }

  /**
   * Determines whether the provided object is eligible for wrapping. Wrappable objects include
   * simple types (null, primitives, primitive wrappers, strings), and arrays or collections with
   * elements that are themselves wrappable and do not exceed the maximum wrappable collection size.
   *
   * @param object the object to check
   * @return {@code true} if the object is wrappable; {@code false} otherwise
   */
  public static boolean isWrappable(Object object) {
    if (isSimpleType(object)) {
      return true;
    }

    Class<?> clazz = object.getClass();
    // handle arrays
    if (clazz.isArray()) {
      return Array.getLength(object) <= MAX_WRAPPABLE_COLLECTION_SIZE;
    }

    // handle collections
    if (object instanceof Collection<?> collection) {
      return collection.size() <= MAX_WRAPPABLE_COLLECTION_SIZE;
    }

    // handle Maps
    if (object instanceof Map<?, ?> map) {
      return map.size() <= MAX_WRAPPABLE_COLLECTION_SIZE;
    }

    // if none of the above
    return false;
  }

  /**
   * Checks if the provided object is an array of simple types and does not exceed the maximum
   * wrappable collection size.
   *
   * @param object the object to check
   * @return {@code true} if the object is an array of simple types within size constraints; {@code
   *     false} otherwise
   */
  static boolean isSimpleTypeArray(Object object) {
    return object != null
        && object.getClass().isArray()
        && classIsSimpleType(object.getClass().getComponentType())
        && Array.getLength(object) <= MAX_WRAPPABLE_COLLECTION_SIZE;
  }

  /**
   * Determines if the specified class represents a simple type, which includes primitive types,
   * their wrapper classes, and {@link String}.
   *
   * @param clazz the class to check
   * @return {@code true} if the class is a simple type; {@code false} otherwise
   */
  static boolean classIsSimpleType(Class<?> clazz) {
    // is it a primitive or its wrapper?
    if (Classes.isPrimitiveOrWrapper(clazz)) {
      return true;
    }

    if (clazz.equals(String.class)) {
      return true;
    }

    return false;
  }

  /**
   * Determines whether the given object is a simple type. Simple types include {@code null},
   * primitive types, primitive wrapper types, and {@link String}.
   *
   * @param obj the object to check
   * @return {@code true} if the object is a simple type; {@code false} otherwise
   */
  static boolean isSimpleType(Object obj) {
    if (obj == null) {
      return true;
    }

    Class<?> clazz = obj.getClass();
    // is it a primitive or its wrapper?
    if (Classes.isPrimitiveOrWrapper(clazz)) {
      return true;
    }

    if (obj instanceof String) {
      return true;
    }

    // everything else is not "simple"
    return false;
  }

  /**
   * Wraps the provided object into a Colfer {@code Obj} instance, enabling it to be later
   * unwrapped. Supports various object types as defined by {@link #isWrappable(Object)}.
   *
   * @param object the object to wrap
   * @param classname the class name of the object to wrap
   * @param objectRef the reference to the object, if any
   * @param wrapPolicy the policy that dictates how the object should be wrapped
   * @return a Colfer {@code Obj} instance representing the wrapped object
   * @throws IllegalArgumentException if the provided class name is invalid
   * @throws NonWrappableObjectException if the object cannot be wrapped according to the policy
   */
  static Obj getWrappedObject(
      @Nullable Object object,
      @Nullable String classname,
      @Nullable ObjectRef objectRef,
      @Nullable WrapPolicy wrapPolicy) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObject with object: {}, class: {}, objectRef: {}",
          object,
          classname,
          objectRef);
    }

    WrapPolicy wrappingPolicy = wrapPolicy != null ? wrapPolicy : WrapPolicy.DETECT;

    if (object == null && classname == null && objectRef == null) {
      return getWrappedObjectAux(new Obj(), null, null, null, wrappingPolicy);
    }

    if (object instanceof Obj) {
      throw new NonWrappableObjectException(
          "Unexpected instance of Obj. Cannot wrap an already wrapped object", object);
    }
    if (objectRef == null && !isWrappable(object)) {
      throw new NonWrappableObjectException(
          "ObjectRef is null and object is not wrappable", object);
    }

    // we are lenient in our json-deserializer with array class names; here we map to proper array
    // class name, e.g.:
    // int[] -> [I
    // Integer[] -> [Ljava.lang.String;
    // String[] -> [Ljava.lang.String;
    // etc.
    String properArrayClassName = Classes.mapToProperArrayClassName(classname);
    if (properArrayClassName != null) {
      classname = properArrayClassName;
    }

    if (classname != null && !Classes.isValidClassName(classname)) {
      throw new IllegalArgumentException("Invalid class name: " + classname);
    }
    return getWrappedObjectAux(new Obj(), object, classname, objectRef, wrappingPolicy);
  }

  /**
   * Returns an interned Colfer {@code Class} by name, creating it if absent.
   *
   * @param name fully qualified class name; {@code null} becomes {@code ""}.
   * @return the interned {@code Class} wrapper
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static com.quasient.pal.messages.colfer.Class internClassByName(String name) {
    String n = (name == null ? "" : name);
    return WRAPPED_CLASS_BY_NAME.computeIfAbsent(
        n,
        k -> {
          com.quasient.pal.messages.colfer.Class c = new com.quasient.pal.messages.colfer.Class();
          c.setName(k);
          return c;
        });
  }

  /**
   * Creates a Colfer {@code Class} instance representing the specified class name.
   *
   * @param className the name of the class to wrap
   * @return a Colfer {@code Class} instance with the provided class name
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  static com.quasient.pal.messages.colfer.Class getWrappedClass(String className) {
    return internClassByName(className);
  }

  /**
   * Creates a Colfer {@code Class} instance representing the provided {@code Class} object.
   *
   * @param clazz the {@code Class} object to wrap
   * @return a Colfer {@code Class} instance with the name of the provided class, or an empty string
   *     if {@code clazz} is {@code null}
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  static com.quasient.pal.messages.colfer.Class getWrappedClass(@Nullable Class<?> clazz) {
    return (clazz == null) ? internClassByName("") : WRAPPED_CLASS_BY_TYPE.get(clazz);
  }

  /**
   * Creates a Colfer {@code Field} instance representing the specified {@code Field}.
   *
   * @param field the {@code Field} to wrap
   * @return a Colfer {@code Field} instance containing the field's name, class, and modifiers
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  static com.quasient.pal.messages.colfer.Field getWrappedField(Field field) {
    return WRAPPED_FIELD_BY_DECL_CLASS
        .get(field.getDeclaringClass())
        .computeIfAbsent(
            field,
            f -> {
              com.quasient.pal.messages.colfer.Field wrapped =
                  new com.quasient.pal.messages.colfer.Field();
              wrapped.setName(f.getName());
              wrapped.setClazz(getWrappedClass(f.getDeclaringClass()));
              wrapped.setModifiers(f.getModifiers());
              return wrapped;
            });
  }

  /**
   * Builds a stable key for a field.
   *
   * @param c declaring class
   * @param name field name
   * @param mod modifiers bitmask
   * @return key in the form {@code class#name#mod}
   */
  private static String key(Class<?> c, String name, int mod) {
    return c.getName() + '#' + name + '#' + mod;
  }

  /**
   * Builds a stable key for a field using a class name.
   *
   * @param cn declaring class name, {@code null} treated as {@code ""}
   * @param name field name
   * @param mod modifiers bitmask
   * @return key in the form {@code class#name#mod}
   */
  private static String key(String cn, String name, int mod) {
    return (cn == null ? "" : cn) + '#' + name + '#' + mod;
  }

  /**
   * Creates a Colfer {@code Field} instance with the specified class, field name, and modifiers.
   *
   * @param clazz the class declaring the field
   * @param fieldName the name of the field
   * @param modifiers the field's modifiers
   * @return a Colfer {@code Field} instance containing the provided information
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  static com.quasient.pal.messages.colfer.Field getWrappedField(
      Class<?> clazz, String fieldName, int modifiers) {
    return WRAPPED_FIELD_BY_TRIPLE.computeIfAbsent(
        key(clazz, fieldName, modifiers),
        k -> {
          com.quasient.pal.messages.colfer.Field f = new com.quasient.pal.messages.colfer.Field();
          f.setName(fieldName);
          f.setClazz(getWrappedClass(clazz));
          f.setModifiers(modifiers);
          return f;
        });
  }

  /**
   * Creates a Colfer {@code Field} instance with the specified class name, field name, and
   * modifiers.
   *
   * @param className the name of the class declaring the field
   * @param fieldName the name of the field
   * @param modifiers the field's modifiers
   * @return a Colfer {@code Field} instance containing the provided information
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  static com.quasient.pal.messages.colfer.Field getWrappedField(
      String className, String fieldName, int modifiers) {
    return WRAPPED_FIELD_BY_TRIPLE.computeIfAbsent(
        key(className, fieldName, modifiers),
        k -> {
          com.quasient.pal.messages.colfer.Field f = new com.quasient.pal.messages.colfer.Field();
          f.setName(fieldName);
          f.setClazz(getWrappedClass(className));
          f.setModifiers(modifiers);
          return f;
        });
  }

  /**
   * Wraps the provided context into a Colfer {@code Context} instance, encapsulating contextual
   * information such as sender details and source location.
   *
   * @param context the runtime context, may be {@code null}
   * @param sender the sender object
   * @param senderObjRef the reference to the sender object
   * @return a Colfer {@code Context} instance representing the provided context and sender
   *     information
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  static com.quasient.pal.messages.colfer.Context getWrappedContext(
      @Nullable Context context, Object sender, ObjectRef senderObjRef) {

    final com.quasient.pal.messages.colfer.Context wrappedCtxt =
        new com.quasient.pal.messages.colfer.Context();

    if (context != null) {
      wrappedCtxt.setSenderClass(getWrappedClass(context.getWithinType()));
      if (sender != null) {
        wrappedCtxt.setSender(
            getWrappedObject(
                sender,
                context.getWithinType().getName(),
                senderObjRef,
                WrapPolicy.PREFER_REFERENCE));
      }
      wrappedCtxt.setSourceLocationFile(context.getSourceFilename());
      wrappedCtxt.setSourceLocationLine(context.getSourceLine());
      wrappedCtxt.setSourceLocationType(context.getWithinType().getName());
    } else {
      wrappedCtxt.setSenderClass(getWrappedClass(sender.getClass()));
      wrappedCtxt.setSender(
          getWrappedObject(
              sender, sender.getClass().getName(), senderObjRef, WrapPolicy.PREFER_REFERENCE));
    }

    return wrappedCtxt;
  }
}
