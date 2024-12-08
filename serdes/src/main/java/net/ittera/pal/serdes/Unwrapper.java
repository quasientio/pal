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

package net.ittera.pal.serdes;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.colfer.ObjUnwrappableAdapter;
import net.ittera.pal.serdes.colfer.Wrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * On dynamically creating arrays with generics: see Rohit Jain answer. <a
 * href="https://stackoverflow.com/questions/18581002/how-to-create-a-generic-array">...</a>
 */
public class Unwrapper {

  private static final Logger logger = LoggerFactory.getLogger(Unwrapper.class);

  private Unwrapper() {}

  @SuppressWarnings("unchecked")
  private static <T> T reconstructCharSequence(T t, String value) {
    Class<?> charSeqClass =
        Wrapper.getReconstructableCharSeqClasses().stream()
            .filter(c -> c.equals(t))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("No matching CharSequence class found"));

    Constructor<?> c;
    try {
      c = charSeqClass.getConstructor(String.class);
    } catch (NoSuchMethodException e) {
      logger.warn("Couldn't get constructor for char seq object", e);
      return null;
    }

    T newObject;
    try {
      newObject = (T) c.newInstance(value);
    } catch (Exception e) {
      logger.warn("Couldn't instantiate char seq object", e);
      return null;
    }

    return newObject;
  }

  /**
   * Returns objects in objectList as Object array with each object typed as its type in classList
   * This method undoes the wrapping of objects done by Wrapper.getWrappedObject()
   *
   * @param object the Obj instance to unwrap
   * @param clazz the class of the object to unwrap
   * @return the unwrapped Object
   */
  @SuppressWarnings("JdkObsolete") // silence errorprone warning about StringBuffer
  public static Object unwrapObject(Unwrappable object, Class<?> clazz) {
    if (logger.isTraceEnabled()) {
      logger.trace("in with unwrappable:\n{}, clazz:\n{}", object.asString(), clazz);
    }

    if (object.isNull()) {
      return null;
    }

    // if clazz (from parameter type) is null
    if (clazz == null) {
      if (object.getType() == null || object.getType().isEmpty()) {

        // without object.getClazz we cannot do anything
        throw new IllegalArgumentException("Type is null and wrapped object has no class");
      } else {

        // unwrap with the class found in Obj.getClazz -- recursive
        Class<?> objectActualClass;
        try {
          objectActualClass =
              Class.forName(object.getType(), true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
          throw new IllegalArgumentException(
              "Class in obj.clazz not found: " + object.getType(), e);
        }
        return unwrapObject(object, objectActualClass);
      }
    }

    if (clazz == String.class) {
      // no casting needed
      return object.getValue();
    }

    if (Wrapper.isWrappableCharSeqClass(clazz)) {
      return reconstructCharSequence(clazz, object.getValue());
    }

    // is primitive
    if (Classes.isPrimitiveOrWrapper(clazz)) {
      return parsePrimitiveValue(object.getValue(), clazz);
    } else if (clazz.isArray()) { // ARRAY
      Class<?> componentType = clazz.getComponentType();
      // primitive or wrapper array
      if (Classes.isPrimitiveOrWrapper(componentType)) {
        return unwrapPrimitiveArray(object.getArrayValues(), componentType);
      }
      final Unwrappable[] arrayValues = object.getArrayValues();
      // String[]
      if (clazz == String[].class) {
        final String[] array = new String[arrayValues.length];
        int idx = 0;
        for (Unwrappable strObj : arrayValues) {
          array[idx++] = strObj.getValue();
        }
        return array;
      } else if (Wrapper.getReconstructableCharSeqClasses().contains(componentType)) {
        switch (componentType.getName()) {
          case "java.lang.StringBuilder":
            final StringBuilder[] sbArray = new StringBuilder[arrayValues.length];
            int sbIdx = 0;
            for (Unwrappable strObj : arrayValues) {
              sbArray[sbIdx++] = new StringBuilder(strObj.getValue());
            }
            return sbArray;
          case "java.lang.StringBuffer":
            final StringBuffer[] sbfArray = new StringBuffer[arrayValues.length];
            int sbfIdx = 0;
            for (Unwrappable strObj : arrayValues) {
              sbfArray[sbfIdx++] = new StringBuffer(strObj.getValue());
            }
            return sbfArray;
          default:
            throw new IllegalArgumentException(
                "Unsupported char seq array type:" + clazz.getName());
        }
      } else {
        throw new IllegalArgumentException("Unsupported array type:" + clazz.getName());
      }
    } else {
      // no other types supported
      throw new IllegalArgumentException("Unsupported object type:" + clazz.getName());
    }
  }

  public static Object unwrapObject(Obj object) throws ClassNotFoundException {
    return unwrapObject(new ObjUnwrappableAdapter(object));
  }

  public static Object unwrapObject(Obj object, Class<?> clazz) {
    return unwrapObject(new ObjUnwrappableAdapter(object), clazz);
  }

  public static Object unwrapObject(Unwrappable wrappedObject) throws ClassNotFoundException {
    String className = wrappedObject.getType();
    Class<?> clazz = Classes.getClassForPrimitive(className);
    if (clazz == null) {
      clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
    }

    return unwrapObject(wrappedObject, clazz);
  }

  private static Object unwrapPrimitiveArray(Unwrappable[] arrayValues, Class<?> componentType) {
    int length = arrayValues.length;
    Object array = Array.newInstance(componentType, length);
    for (int i = 0; i < length; i++) {
      Object value = unwrapObject(arrayValues[i], componentType);
      Array.set(array, i, value);
    }
    return array;
  }

  private static Object parsePrimitiveValue(String value, Class<?> clazz) {
    if (clazz == int.class || clazz == Integer.class) {
      return Integer.parseInt(value);
    }

    if (clazz == long.class || clazz == Long.class) {
      return Long.parseLong(value);
    }

    if (clazz == short.class || clazz == Short.class) {
      return Short.parseShort(value);
    }

    if (clazz == byte.class || clazz == Byte.class) {
      return Byte.parseByte(value);
    }

    if (clazz == float.class || clazz == Float.class) {
      return Float.parseFloat(value);
    }

    if (clazz == double.class || clazz == Double.class) {
      return Double.parseDouble(value);
    }

    if (clazz == boolean.class || clazz == Boolean.class) {
      return Boolean.parseBoolean(value);
    }

    if (clazz == char.class || clazz == Character.class) {
      return value.charAt(0);
    }

    if (clazz == void.class || clazz == Void.class) {
      return null;
    }

    throw new IllegalArgumentException("Unsupported primitive type: " + clazz.getName());
  }
}
