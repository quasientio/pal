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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
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

  static final List<Class<?>> reconstructableCharSeqClasses;
  static final List<String> reconstructableCharSeqClassNames;

  static {
    // we don't include String's here, as we check separately
    reconstructableCharSeqClasses = Arrays.asList(StringBuilder.class, StringBuffer.class);
    reconstructableCharSeqClassNames =
        reconstructableCharSeqClasses.stream().map(Class::getName).collect(Collectors.toList());
  }

  private Wrapper() {
    // avoid instantiation
  }

  public static boolean isWrappableCharSeqClass(Class<?> clazz) {
    return reconstructableCharSeqClasses.contains(clazz);
  }

  public static boolean isWrappableCharSeqClass(String classname) {
    return reconstructableCharSeqClassNames.contains(classname);
  }

  public static List<Class<?>> getReconstructableCharSeqClasses() {
    return reconstructableCharSeqClasses;
  }

  /**
   * Helper method for getWrappedObject() that does the actual wrapping work. It is recursive and
   * will be called for each element of an array/collection.
   *
   * <pre>
   * Wrappable objects:
   *  - null
   *  - all primitive types
   *  - all primitive wrapper types
   *  - reconstructable char sequence types: String, StringBuilder, StringBuffer
   *  - arrays of all of the above
   * </pre>
   *
   * @param wrappedObject the object to wrap into
   * @param object the object to wrap
   * @param givenClassName the class of the object to wrap
   * @param objectRef the object reference, if any
   * @return a Colfer Obj (object) instance
   * @throws NullPointerException if t is null
   */
  private static Obj getWrappedObjectAux(
      Obj wrappedObject, java.lang.Object object, String givenClassName, ObjectRef objectRef) {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObjectAux with object: {}, class: {}, objectRef: {}",
          object,
          givenClassName,
          objectRef);
    }

    // set required fields
    boolean isNull = object == null && objectRef == null;

    wrappedObject.setIsNull(isNull);

    /* We use the givenClassName if
    - object is null, or
    - the object is a wrapper instance and the givenClassName is the corresponding primitive
    Otherwise use the object's class
    */
    final String className;
    if (object == null) {
      className = givenClassName;
    } else {
      if (isCorrespondingPrimitive(object, givenClassName)) {
        className = givenClassName;
      } else {
        className = object.getClass().getName();
      }
    }

    // wrap object's class
    wrappedObject.setClazz(getWrappedClass(className));

    // wrap object reference
    if (objectRef != null) {
      wrappedObject.setRef(String.valueOf(objectRef.getRef()));
    }

    // wrap object
    if (object != null) {
      // wrap the object if:
      // 1. is String or char sequence
      // 2. is primitive or wrapper
      // 3. is array of primitives or wrappers
      // 4. is array of String or char sequences

      if ((object instanceof String) || isWrappableCharSeqClass(className)) {
        // 1. is String or char sequence
        wrappedObject.setValue(object.toString());
      } else if (Classes.isPrimitiveOrWrapper(object.getClass())) {
        // 2. is primitive or wrapper
        wrappedObject.setValue(String.valueOf(object));
      } else if (object.getClass().isArray() // array
          &&
          // 3. is array of primitives or wrappers
          (Classes.isPrimitiveOrWrapper(object.getClass().getComponentType())
              // 4. is array of Strings or char sequences
              || String.class.equals(object.getClass().getComponentType())
              || isWrappableCharSeqClass(object.getClass().getComponentType().getName()))) {
        // TODO only handles 1-dimensional arrays ?? Check out Arrays.deepToString
        final int length = Array.getLength(object);
        final Obj[] arrayElements = new Obj[length];
        // NOTE: we iterate using reflection (Array) because the array type is unknown
        for (int i = 0; i < length; i++) {
          final java.lang.Object arrayElem = Array.get(object, i);
          // wrap all array elements -- recursive
          arrayElements[i] = getWrappedObject(arrayElem, arrayElem.getClass().getName(), null);
        }
        wrappedObject.setArrayValues(arrayElements);
      } else {
        // not wrappable
        boolean isVoid = object == void.class || object == Void.class;
        if (!isVoid && objectRef == null) {
          throw new NonWrappableObjectException(
              "ObjectRef is null and object is not wrappable", object);
        }
      }
    }

    if (logger.isTraceEnabled()) {
      logger.trace("out with wrappedValue: {}", ColferUtils.format(wrappedObject));
    }
    return wrappedObject;
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
   * See getWrappedObjectAux() for a list of valid wrappable types.
   *
   * @param object the object to check
   * @return true if the object is wrappable, false otherwise
   */
  public static boolean isWrappable(java.lang.Object object) {
    return object == null
        || object == Void.class
        || object == void.class
        || Classes.isPrimitiveOrWrapper(object.getClass())
        || object instanceof String
        || isWrappableCharSeqClass(object.getClass())
        || (object.getClass().isArray()
            && Classes.isPrimitiveOrWrapper(object.getClass().getComponentType()))
        ||
        // String[] will pass the last check so this check is redundant, but they're so common we
        // can optimize a bit by checking first for String[] and avoid going through its interfaces
        // as the next check does *
        (object.getClass().isArray() && String.class.equals(object.getClass().getComponentType()))
        || (object.getClass().isArray()
            && isWrappableCharSeqClass(object.getClass().getComponentType()));
  }

  /**
   * Wraps objects into a Colfer Obj(ect), which can be later unwrapped into the original object.
   * See getWrappedObjectAux() for a list of valid wrappable types.
   *
   * @param object the object to wrap
   * @param classname the class name of the object to wrap
   * @param objectRef the object reference, if any
   * @return a Colfer Obj (object) instance
   * @throws NonWrappableObjectException if the object is not wrappable
   */
  static Obj getWrappedObject(
      @Nullable java.lang.Object object,
      @Nullable String classname,
      @Nullable ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObject with object: {}, class: {}, objectRef: {}",
          object,
          classname,
          objectRef);
    }

    if (object == null && classname == null && objectRef == null) {
      return getWrappedObjectAux(new Obj(), null, null, null);
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
    return getWrappedObjectAux(new Obj(), object, classname, objectRef);
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
            getWrappedObject(sender, context.getWithinType().getName(), senderObjRef));
      }
      wrappedCtxt.setSourceLocationFile(context.getSourceFilename());
      wrappedCtxt.setSourceLocationLine(context.getSourceLine());
      wrappedCtxt.setSourceLocationType(context.getWithinType().getName());
    } else {
      wrappedCtxt.setSenderClass(getWrappedClass(sender.getClass()));
      wrappedCtxt.setSender(getWrappedObject(sender, sender.getClass().getName(), senderObjRef));
    }

    return wrappedCtxt;
  }
}
