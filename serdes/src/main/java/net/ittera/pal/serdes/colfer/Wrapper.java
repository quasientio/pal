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

  // we don't include String's here, as we check separately
  static final List<Class> reconstructableCharSeqClasses =
      Arrays.asList(StringBuilder.class, StringBuffer.class);

  private Wrapper() {
    // avoid instantiation
  }

  public static boolean isWrappableCharSeqClass(Class clazz) {
    return reconstructableCharSeqClasses.contains(clazz);
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
   * @param t the class of the object to wrap
   * @param objectRef the object reference, if any
   * @param <T> the type of the object to wrap
   * @return a Colfer Obj (object) instance
   * @throws NullPointerException if t is null
   * @throws IllegalArgumentException if t is not a Class nor a String
   */
  private static <T> Obj getWrappedObjectAux(
      Obj wrappedObject, java.lang.Object object, T t, ObjectRef objectRef) {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObjectAux with object: {}, class: {}, objectRef: {}", object, t, objectRef);
    }

    if (t == null) {
      throw new NullPointerException("class(name) parameter cannot be null nor empty");
    }

    // we got a class or classname?
    String className = null;
    Class clazz = null;
    if (t instanceof Class) {
      clazz = (Class) t;
    } else if (t instanceof String) {
      className = (String) t;
    } else {
      throw new IllegalArgumentException("Type of t parameter is neither Class nor String");
    }

    // set required fields
    wrappedObject.setIsNull(object == null && objectRef == null);
    wrappedObject.setIsVoid(object == void.class || object == Void.class);

    if (clazz != null) {
      wrappedObject.setClazz(getWrappedClass(clazz));
      wrappedObject.setIsArray(clazz.isArray());
    } else {
      wrappedObject.setClazz(getWrappedClass(className));
    }
    if (objectRef != null) {
      wrappedObject.setRef(String.valueOf(objectRef.getRef()));
    }
    wrappedObject.setIdentityHash(System.identityHashCode(object));

    // wrap object
    if (object != null) {
      wrappedObject.setHash(object.hashCode());
      if ((object instanceof String) || isWrappableCharSeqClass(clazz)) {
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
          arrayElems[i] = getWrappedObject(arrayElem, arrayElem.getClass(), null);
        }
        wrappedObject.setArrayValues(arrayElems);
      } else if (Classes.isPrimitiveOrWrapper(object.getClass())) {
        wrappedObject.setValue(String.valueOf(object));
      } else {
        // nothing we can do but leave a trace
        if (logger.isTraceEnabled()) {
          logger.warn("Don't know what to do to wrap object: {} of class: {}", object, t);
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

  /**
   * Wraps objects into a Colfer Obj(ect), which can be later unwrapped into the original object.
   * See getWrappedObjectAux() for a list of valid wrappable types.
   *
   * @param object the object to wrap
   * @param t the class of the object to wrap
   * @param objectRef the object reference, if any
   * @param <T> the type of the object to wrap
   * @return a Colfer Obj (object) instance
   * @throws NonWrappableObjectException if the object is not wrappable
   */
  static <T> Obj getWrappedObject(java.lang.Object object, T t, ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObject with object: {}, class: {}, objectRef: {}", object, t, objectRef);
    }
    final Obj obj = new Obj();
    if (objectRef == null && !isWrappable(object)) {
      throw new NonWrappableObjectException(object);
    }
    if (object instanceof Obj) {
      throw new NonWrappableObjectException(
          "Unexpected instance of Obj. Cannot wrap an already wrapped object", object);
    }
    return getWrappedObjectAux(obj, object, t, objectRef);
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
      Context context, java.lang.Object sender, ObjectRef senderObjRef) {

    final net.ittera.pal.messages.colfer.Context wrappedCtxt =
        new net.ittera.pal.messages.colfer.Context();

    wrappedCtxt.setSenderClass(getWrappedClass(context.getWithinType()));
    if (sender != null) {
      wrappedCtxt.setSender(getWrappedObject(sender, context.getWithinType(), senderObjRef));
    }

    if (context.getSourceFilename() != null) {
      wrappedCtxt.setSourceLocationFile(context.getSourceFilename());
    }

    wrappedCtxt.setSourceLocationLine(context.getSourceLine());

    if (context.getWithinType() != null) {
      wrappedCtxt.setSourceLocationType(context.getWithinType().getName());
    }

    return wrappedCtxt;
  }
}
