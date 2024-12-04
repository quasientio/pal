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
import java.lang.reflect.Constructor;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.messages.colfer.Obj;
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
  private static <T> T reconstructCharSequence(T t, Obj object) {
    Class<?> charSeqClass =
        Wrapper.reconstructableCharSeqClasses.stream()
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
      newObject = (T) c.newInstance(object.getValue());
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
  public static java.lang.Object unwrapObject(Obj object, Class<?> clazz) {
    if (logger.isTraceEnabled()) {
      logger.trace("in with object:\n{}, clazz:\n{}", object, clazz);
    }

    if (object.getIsNull()) {
      return null;
    }

    // if clazz (from parameter type) is null
    if (clazz == null) {
      if (object.getClazz() == null || object.getClazz().getUnknown()) {

        // without object.getClazz we cannot do anything
        throw new IllegalArgumentException("Type is null and wrapped object has no class");
      } else {

        // unwrap with the class found in Obj.getClazz -- recursive
        Class<?> objectActualClass;
        try {
          objectActualClass =
              Class.forName(
                  object.getClazz().getName(),
                  true,
                  Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
          throw new IllegalArgumentException(
              "Class in obj.clazz not found: " + object.getClazz().getName(), e);
        }
        return unwrapObject(object, objectActualClass);
      }
    }

    if (clazz == String.class) {
      // no casting needed
      return object.getValue();
    }

    if (Wrapper.isWrappableCharSeqClass(clazz)) {
      return reconstructCharSequence(clazz, object);
    }

    // is primitive
    if (Classes.isPrimitiveOrWrapper(clazz)) {
      if (clazz == byte.class || clazz == Byte.class) {
        return Byte.parseByte(object.getValue());
      } else if (clazz == short.class || clazz == Short.class) {
        return Short.parseShort(object.getValue());
      } else if (clazz == int.class || clazz == Integer.class) {
        return Integer.parseInt(object.getValue());
      } else if (clazz == long.class || clazz == Long.class) {
        return Long.parseLong(object.getValue());
      } else if (clazz == float.class || clazz == Float.class) {
        return Float.parseFloat(object.getValue());
      } else if (clazz == double.class || clazz == Double.class) {
        return Double.parseDouble(object.getValue());
      } else if (clazz == char.class || clazz == Character.class) {
        return object.getValue().charAt(0);
      } else if (clazz == boolean.class || clazz == Boolean.class) {
        return Boolean.parseBoolean(object.getValue());
      } else {
        throw new IllegalArgumentException("Unsupported primitive type:" + clazz.getName());
      }
    } else if (clazz.isArray()) { // ARRAY
      Class<?> componentType = clazz.getComponentType();
      // primitive or wrapper array
      if (Classes.isPrimitiveOrWrapper(componentType)) {
        return unwrapPrimitiveArray(object.getArrayValues(), componentType);
      }
      final Obj[] arrayValues = object.getArrayValues();
      // String[]
      if (clazz == String[].class) {
        final String[] array = new String[arrayValues.length];
        int idx = 0;
        for (Obj strObj : arrayValues) {
          array[idx++] = strObj.getValue();
        }
        return array;
      } else if (Wrapper.reconstructableCharSeqClasses.contains(componentType)) {
        switch (componentType.getName()) {
          case "java.lang.StringBuilder":
            final StringBuilder[] sbArray = new StringBuilder[arrayValues.length];
            int sbIdx = 0;
            for (Obj strObj : arrayValues) {
              sbArray[sbIdx++] = new StringBuilder(strObj.getValue());
            }
            return sbArray;
          case "java.lang.StringBuffer":
            final StringBuffer[] sbfArray = new StringBuffer[arrayValues.length];
            int sbfIdx = 0;
            for (Obj strObj : arrayValues) {
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

  public static java.lang.Object unwrapObject(Obj object) throws ClassNotFoundException {
    final String objClassName = object.getClazz().getName();
    Class<?> objectClass = Classes.getClassForPrimitive(objClassName);
    if (objectClass == null) {
      objectClass =
          Class.forName(objClassName, true, Thread.currentThread().getContextClassLoader());
    }
    return unwrapObject(object, objectClass);
  }

  private static Object unwrapPrimitiveArray(Obj[] arrayValues, Class<?> componentType) {
    int length = arrayValues.length;
    Object array = Array.newInstance(componentType, length);
    for (int i = 0; i < length; i++) {
      Object value = unwrapObject(arrayValues[i], componentType);
      Array.set(array, i, value);
    }
    return array;
  }
}
