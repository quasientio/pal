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

  static final List<Class> reconstructableCharSeqClasses;
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

  public static boolean isWrappableCharSeqClass(Class clazz) {
    return reconstructableCharSeqClasses.contains(clazz);
  }

  public static boolean isWrappableCharSeqClass(String classname) {
    return reconstructableCharSeqClassNames.contains(classname);
  }

  public static boolean isArrayClassName(String className) {
    return className.startsWith("[");
  }

  /**
   * Helper method for getWrappedObject() that does the actual wrapping work. It is recursive and
   * will be called for each element of an array/collection.
   *
   * <p>
   *
   * <pre>
   * Wrappable objects:
   *  - null
   *  - void.class, Void.class
   *  - all primitive types
   *  - all primitive wrapper types
   *  - reconstructable char sequence types: String, StringBuilder, StringBuffer
   *  - arrays of all of the above
   * </pre>
   *
   * @param wrappedObject the object to wrap into
   * @param object the object to wrap
   * @param classname the class of the object to wrap
   * @param objectRef the object reference, if any
   * @return a Colfer Obj (object) instance
   * @throws NullPointerException if t is null
   */
  private static Obj getWrappedObjectAux(
      Obj wrappedObject, java.lang.Object object, String classname, ObjectRef objectRef) {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObjectAux with object: {}, class: {}, objectRef: {}",
          object,
          classname,
          objectRef);
    }

    // set required fields
    wrappedObject.setIsNull(object == null && objectRef == null);
    wrappedObject.setIsVoid(object == void.class || object == Void.class);
    wrappedObject.setClazz(getWrappedClass(classname));
    wrappedObject.setIsArray(isArrayClassName(classname));

    if (objectRef != null) {
      wrappedObject.setRef(String.valueOf(objectRef.getRef()));
    }
    wrappedObject.setIdentityHash(System.identityHashCode(object));

    // wrap object
    if (object != null) {
      wrappedObject.setHash(object.hashCode());
      if ((object instanceof String) || isWrappableCharSeqClass(classname)) {
        wrappedObject.setValue(object.toString());
      } else if (object.getClass().isArray()) {
        wrappedObject.setIsArray(true);
        // TODO only handles 1-dimensional arrays ?? Check out Arrays.deepToString

        final int length = Array.getLength(object);
        final Obj[] arrayElems = new Obj[length];
        // NOTE: we iterate using reflection (Array) because the array type is unknown
        for (int i = 0; i < length; i++) {
          final java.lang.Object arrayElem = Array.get(object, i);
          // wrap all array elements -- recursive
          arrayElems[i] = getWrappedObject(arrayElem, arrayElem.getClass().getName(), null);
        }
        wrappedObject.setArrayValues(arrayElems);
      } else if (Classes.isPrimitiveOrWrapper(object.getClass())) {
        wrappedObject.setValue(String.valueOf(object));
      } else {
        // nothing we can do but leave a trace
        if (logger.isTraceEnabled()) {
          logger.warn("Don't know what to do to wrap object: {} of class: {}", object, classname);
        }
      }
    }

    if (logger.isTraceEnabled()) {
      logger.trace("out with wrappedValue: {}", ColferUtils.format(wrappedObject));
    }
    return wrappedObject;
  }

  /**
   * See getWrappedObjectAux() for a list of valid wrappable types
   *
   * @param object
   * @return
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
        /**
         * String[] will pass the last check so this check is redundant, but they're so common we
         * can optimize a bit by checking first for String[] and avoid going through its interfaces
         * as the next check does *
         */
        (object.getClass().isArray() && String.class.equals(object.getClass().getComponentType()))
        || (object.getClass().isArray()
            && isWrappableCharSeqClass(object.getClass().getComponentType()));
  }

  private static boolean isValidClassName(String className) {
    String arrayRegex = "(\\[)*";
    String baseTypeRegex = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*";
    String primitiveArrayRegex = "\\[+[BCDFIJSZ]";
    String endRegex = "(;)?";
    String regex = "(" + arrayRegex + "?" + baseTypeRegex + endRegex + ")|" + primitiveArrayRegex;
    return className.matches(regex);
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
  static <T> Obj getWrappedObject(java.lang.Object object, String classname, ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObject with object: {}, class: {}, objectRef: {}",
          object,
          classname,
          objectRef);
    }
    final Obj obj = new Obj();
    if (object instanceof Obj) {
      throw new NonWrappableObjectException(
          "Unexpected instance of Obj. Cannot wrap an already wrapped object", object);
    }
    if (objectRef == null && !isWrappable(object)) {
      throw new NonWrappableObjectException(object);
    }
    if (!isValidClassName(classname)) {
      throw new IllegalArgumentException("Invalid class name: " + classname);
    }
    return getWrappedObjectAux(obj, object, classname, objectRef);
  }

  static net.ittera.pal.messages.colfer.Class getWrappedClass(String className) {
    final net.ittera.pal.messages.colfer.Class wrappedClass =
        new net.ittera.pal.messages.colfer.Class();
    if (className == null) {
      wrappedClass.setUnknown(true);
    } else {
      wrappedClass.setName(className);
    }
    return wrappedClass;
  }

  static net.ittera.pal.messages.colfer.Class getWrappedClass(Class clazz) {
    final net.ittera.pal.messages.colfer.Class wrappedClass =
        new net.ittera.pal.messages.colfer.Class();
    if (clazz == null) {
      wrappedClass.setUnknown(true);
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
    wrappedField.setRepr(field.toGenericString());
    return wrappedField;
  }

  static net.ittera.pal.messages.colfer.Field getWrappedField(Class clazz, String fieldName) {
    final net.ittera.pal.messages.colfer.Field wrappedField =
        new net.ittera.pal.messages.colfer.Field();
    wrappedField.setName(fieldName);
    wrappedField.setClazz(getWrappedClass(clazz));
    return wrappedField;
  }

  static net.ittera.pal.messages.colfer.Field getWrappedField(String className, String fieldName) {
    final net.ittera.pal.messages.colfer.Field wrappedField =
        new net.ittera.pal.messages.colfer.Field();
    wrappedField.setName(fieldName);
    wrappedField.setClazz(getWrappedClass(className));
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

      if (context.getSourceFilename() != null) {
        wrappedCtxt.setSourceLocationFile(context.getSourceFilename());
      }

      wrappedCtxt.setSourceLocationLine(context.getSourceLine());

      if (context.getWithinType() != null) {
        wrappedCtxt.setSourceLocationType(context.getWithinType().getName());
      }
    } else {
      wrappedCtxt.setSenderClass(getWrappedClass(sender.getClass()));
      wrappedCtxt.setSender(getWrappedObject(sender, sender.getClass().getName(), senderObjRef));
    }

    return wrappedCtxt;
  }
}
