/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.serdes.colfer;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.NonWrappableObjectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WRAPPING METHODS: Two versions of these exist, as we have generally more information when
 * messages are built from local calls (with full reflection details), than when these messages are
 * built for remote calls, and not all context and type information is available.
 */
public final class Wrapper {

  private static final Logger logger = LoggerFactory.getLogger(Wrapper.class);

  private static final int MAX_WRAPPABLE_COLLECTION_SIZE = 1000;

  private Wrapper() {
    // avoid instantiation
  }

  /**
   * Helper method for getWrappedObject() that does the actual wrapping work.
   *
   * @param wrappedObject the object to wrap into
   * @param object the object to wrap
   * @param givenClassName the class of the object to wrap
   * @param objectRef the object reference, if any
   * @param wrapPolicy the wrapping policy to use
   * @return a Colfer Obj (object) instance
   */
  private static Obj getWrappedObjectAux(
      Obj wrappedObject,
      Object object,
      String givenClassName,
      ObjectRef objectRef,
      WrapPolicy wrapPolicy) {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObjectAux with object: {}, class: {}, objectRef: {}",
          object,
          givenClassName,
          objectRef);
    }
    // set isNull
    boolean isNull = (object == null && objectRef == null);
    wrappedObject.setIsNull(isNull);

    // set ref
    if (objectRef != null) {
      wrappedObject.setRef(String.valueOf(objectRef.getRef()));
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

    if (logger.isDebugEnabled()) {
      logger.debug("isSimpleType = {}", isSimpleType);
      logger.debug("isSimpleTypeArray = {}", isSimpleTypeArray);
    }
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

  /* We use the givenClassName if
   - object is null, or
   - the object is a wrapper instance and the givenClassName is the corresponding primitive
   Otherwise use the object's class
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

  // returns true if object is a primitive wrapper instance and className
  // is the corresponding primitive class name
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
   *
   *
   * <pre>
   * Wrappable objects are:
   *
   * Simple types:
   *  - null
   *  - primitive types
   *  - primitive wrapper types
   *  - strings
   *
   * Arrays & collections, if length < MAX_COLLECTION_SIZE:
   *  - Lists (if all their elements are wrappable)
   *  - Maps (if all their keys and values are wrappable)
   *  - Arrays (if all their elements are wrappable)
   * </pre>
   *
   * @param object the object to check
   * @return true if the object is wrappable, false otherwise
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

  static boolean isSimpleTypeArray(Object object) {
    return object != null
        && object.getClass().isArray()
        && classIsSimpleType(object.getClass().getComponentType())
        && Array.getLength(object) <= MAX_WRAPPABLE_COLLECTION_SIZE;
  }

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
   * Wraps objects into a Colfer Obj(ect), which can be later unwrapped into the original object.
   * See getWrappedObjectAux() for a list of valid wrappable types.
   *
   * @param object the object to wrap
   * @param classname the class name of the object to wrap
   * @param objectRef the object reference, if any
   * @param wrapPolicy the wrapping policy to use
   * @return a Colfer Obj (object) instance
   * @throws NonWrappableObjectException if the object is not wrappable
   */
  static Obj getWrappedObject(
      @Nullable java.lang.Object object,
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

  static net.ittera.pal.messages.colfer.Class getWrappedClass(String className) {
    final net.ittera.pal.messages.colfer.Class wrappedClass =
        new net.ittera.pal.messages.colfer.Class();
    wrappedClass.setName(Objects.requireNonNullElse(className, ""));
    return wrappedClass;
  }

  static net.ittera.pal.messages.colfer.Class getWrappedClass(Class<?> clazz) {
    final net.ittera.pal.messages.colfer.Class wrappedClass =
        new net.ittera.pal.messages.colfer.Class();
    if (clazz == null) {
      wrappedClass.setName("");
    } else {
      wrappedClass.setName(clazz.getName());
    }
    return wrappedClass;
  }

  static net.ittera.pal.messages.colfer.Field getWrappedField(Field field) {
    final net.ittera.pal.messages.colfer.Field wrappedField =
        new net.ittera.pal.messages.colfer.Field();
    wrappedField.setName(field.getName());
    wrappedField.setClazz(getWrappedClass(field.getDeclaringClass()));
    wrappedField.setModifiers(field.getModifiers());
    return wrappedField;
  }

  static net.ittera.pal.messages.colfer.Field getWrappedField(
      Class<?> clazz, String fieldName, int modifiers) {
    final net.ittera.pal.messages.colfer.Field wrappedField =
        new net.ittera.pal.messages.colfer.Field();
    wrappedField.setName(fieldName);
    wrappedField.setClazz(getWrappedClass(clazz));
    wrappedField.setModifiers(modifiers);
    return wrappedField;
  }

  static net.ittera.pal.messages.colfer.Field getWrappedField(
      String className, String fieldName, int modifiers) {
    final net.ittera.pal.messages.colfer.Field wrappedField =
        new net.ittera.pal.messages.colfer.Field();
    wrappedField.setName(fieldName);
    wrappedField.setClazz(getWrappedClass(className));
    wrappedField.setModifiers(modifiers);
    return wrappedField;
  }

  static net.ittera.pal.messages.colfer.Context getWrappedContext(
      @Nullable Context context, java.lang.Object sender, ObjectRef senderObjRef) {

    final net.ittera.pal.messages.colfer.Context wrappedCtxt =
        new net.ittera.pal.messages.colfer.Context();

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
