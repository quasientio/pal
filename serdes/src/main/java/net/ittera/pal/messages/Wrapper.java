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

package net.ittera.pal.messages;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import net.ittera.pal.common.lang.Context;
import net.ittera.pal.common.lang.ObjectRef;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.messages.protobuf.Ctxt;
import net.ittera.pal.messages.protobuf.Primitives;
import net.ittera.pal.messages.protobuf.Primitives.Object;
import net.ittera.pal.messages.protobuf.Primitives.Object.Builder;
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

  static boolean isWrappableCharSeqClass(Class clazz) {
    return reconstructableCharSeqClasses.contains(clazz);
  }

  /**
   * Wrappable objects: - null, void.class, Void.class - all primitive types - all wrapper types -
   * reconstructable char sequence types: String, StringBuilder, StringBuffer
   *
   * @param builder
   * @param object
   * @param t
   * @param objectRef
   * @param <T>
   * @return
   */
  private static <T> Object getWrappedObjectAux(
      Builder builder, java.lang.Object object, T t, ObjectRef objectRef) {

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
    builder.setIsNull(object == null && objectRef == null);
    builder.setIsVoid(object == void.class || object == Void.class);

    if (clazz != null) {
      builder.setClass_(getWrappedClass(clazz));
      builder.setIsArray(clazz.isArray());
    } else {
      builder.setClass_(getWrappedClass(className));
    }
    if (objectRef != null) {
      builder.setRef(String.valueOf(objectRef.getRef()));
    }
    builder.setIdentityHash(System.identityHashCode(object));

    // wrap object
    if (object != null) {
      builder.setHash(object.hashCode());
      if ((object instanceof String) || isWrappableCharSeqClass(clazz)) {
        builder.setValue(object.toString());
      } else if (object.getClass().isArray()) {
        builder.setIsArray(true);
        // TODO only handles 1-dimensional arrays ?? Check out Arrays.deepToString

        final int length = Array.getLength(object);
        // NOTE: we iterate using reflection (Array) because the array type is unknown
        for (int i = 0; i < length; i++) {
          final java.lang.Object arrayElem = Array.get(object, i);
          // wrap and all array elements -- recursive
          builder.addArrayValue(getWrappedObject(arrayElem, arrayElem.getClass(), null));
        }
      } else if (Classes.isPrimitiveOrWrapper(object.getClass())) {
        builder.setValue(String.valueOf(object));
      } else {
        // nothing we can do but leave a trace
        if (logger.isTraceEnabled()) {
          logger.trace("Don't know what to do to wrap object: {} of class: {}", object, t);
        }
      }
    }

    final Object builtValue = builder.build();
    if (logger.isTraceEnabled()) {
      logger.trace("out with wrappedValue: {}", builtValue);
    }
    return builtValue;
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
   * @param object
   * @param t
   * @param objectRef
   * @param <T>
   * @return
   */
  static <T> Object getWrappedObject(java.lang.Object object, T t, ObjectRef objectRef) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "in getWrappedObject with object: {}, class: {}, objectRef: {}", object, t, objectRef);
    }
    final Builder builder = Primitives.Object.newBuilder();
    if (objectRef == null && !isWrappable(object)) {
      throw new NonWrappableObjectException(object);
    }
    return getWrappedObjectAux(builder, object, t, objectRef);
  }

  static Primitives.Class getWrappedClass(String className) {
    final Primitives.Class.Builder clazzBuilder = Primitives.Class.newBuilder();
    if (className == null) {
      clazzBuilder.setUnknown(true);
    } else {
      clazzBuilder.setName(className);
    }
    return clazzBuilder.build();
  }

  static Primitives.Class getWrappedClass(Class clazz) {
    final Primitives.Class.Builder clazzBuilder = Primitives.Class.newBuilder();
    if (clazz == null) {
      clazzBuilder.setUnknown(true);
    } else {
      clazzBuilder.setName(clazz.getName());
    }
    // TODO: fill all other available Class info
    return clazzBuilder.build();
  }

  static Primitives.Field getWrappedField(Field field) {
    final Primitives.Field.Builder fieldBuilder = Primitives.Field.newBuilder();
    fieldBuilder.setName(field.getName());
    fieldBuilder.setClass_(getWrappedClass(field.getDeclaringClass()));
    fieldBuilder.setRepr(field.toGenericString());
    return fieldBuilder.build();
  }

  static Primitives.Field getWrappedField(Class clazz, String fieldName) {
    final Primitives.Field.Builder fieldBuilder = Primitives.Field.newBuilder();

    fieldBuilder.setName(fieldName);
    fieldBuilder.setClass_(getWrappedClass(clazz));
    return fieldBuilder.build();
  }

  static Primitives.Field getWrappedField(String className, String fieldName) {
    final Primitives.Field.Builder fieldBuilder = Primitives.Field.newBuilder();

    fieldBuilder.setName(fieldName);
    fieldBuilder.setClass_(getWrappedClass(className));
    return fieldBuilder.build();
  }

  static Ctxt.Context getWrappedContext(
      Context context, java.lang.Object sender, ObjectRef senderObjRef) {
    final Ctxt.Context.Builder ctxtBuilder = Ctxt.Context.newBuilder();

    ctxtBuilder.setSenderClass(getWrappedClass(context.getWithinType()));
    if (sender != null) {
      ctxtBuilder.setSender(getWrappedObject(sender, context.getWithinType(), senderObjRef));
    }

    if (context.getFileName() != null) {
      ctxtBuilder.setSourceLocationFile(context.getFileName());
    }

    ctxtBuilder.setSourceLocationLine(context.getSourceLine());

    if (context.getWithinType() != null) {
      ctxtBuilder.setSourceLocationType(context.getWithinType().getName());
    }

    return ctxtBuilder.build();
  }
}
