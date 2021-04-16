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

package net.ittera.pal.serdes.protobuf;

import java.lang.reflect.Constructor;
import java.util.Optional;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.messages.protobuf.Primitives.Object;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * On dynamically creating arrays with generics: see Rohit Jain answer in
 * https://stackoverflow.com/questions/18581002/how-to-create-a-generic-array
 */
public class Unwrapper {

  private static final Logger logger = LoggerFactory.getLogger(Unwrapper.class);

  private Unwrapper() {}

  private static <T> T reconstructCharSequence(T t, Object object) {
    Optional<Class> charSeqClass =
        Wrapper.reconstructableCharSeqClasses.stream().filter(c -> c.equals(t)).findFirst();

    Constructor c;
    try {
      c = charSeqClass.get().getConstructor(String.class);
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
   * @param object
   * @param clazz
   * @return
   */
  public static java.lang.Object unwrapObject(Object object, Class clazz) {
    if (logger.isTraceEnabled()) {
      logger.trace("in with object:\n{}, clazz:\n{}", object, clazz);
    }

    if (object.getIsNull()) {
      return null;
    }

    if (object.getIsVoid()) {
      return void.class;
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
    }
    // is Array
    else if (clazz.isArray()) {
      if (!object.getIsArray()) {
        throw new IllegalArgumentException(
            "Type is array but wrapped object isn't:" + clazz.getName());
      }
      // String[]
      if (clazz == String[].class) {
        String[] array = new String[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = strObj.getValue();
        }
        return array;
      }
      // PRIMITIVE WRAPPERS
      else if (clazz == Integer[].class) {
        Integer[] array = new Integer[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Integer.valueOf(strObj.getValue());
        }
        return array;
      } else if (clazz == Boolean[].class) {
        Boolean[] array = new Boolean[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Boolean.valueOf(strObj.getValue());
        }
        return array;
      } else if (clazz == Byte[].class) {
        Byte[] array = new Byte[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Byte.valueOf(strObj.getValue());
        }
        return array;
      } else if (clazz == Character[].class) {
        Character[] array = new Character[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = strObj.getValue().charAt(0);
        }
        return array;
      } else if (clazz == Short[].class) {
        Short[] array = new Short[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Short.valueOf(strObj.getValue());
        }
        return array;
      } else if (clazz == Long[].class) {
        Long[] array = new Long[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Long.valueOf(strObj.getValue());
        }
        return array;
      } else if (clazz == Float[].class) {
        Float[] array = new Float[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Float.valueOf(strObj.getValue());
        }
        return array;
      } else if (clazz == Double[].class) {
        Double[] array = new Double[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Double.valueOf(strObj.getValue());
        }
        return array;
      }
      // PRIMITIVES
      else if (clazz == int[].class) {
        int[] array = new int[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Integer.parseInt(strObj.getValue());
        }
        return array;
      } else if (clazz == boolean[].class) {
        boolean[] array = new boolean[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Boolean.parseBoolean(strObj.getValue());
        }
        return array;
      } else if (clazz == byte[].class) {
        byte[] array = new byte[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Byte.parseByte(strObj.getValue());
        }
        return array;
      } else if (clazz == char[].class) {
        char[] array = new char[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = strObj.getValue().charAt(0);
        }
        return array;
      } else if (clazz == short[].class) {
        short[] array = new short[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Short.parseShort(strObj.getValue());
        }
        return array;
      } else if (clazz == long[].class) {
        long[] array = new long[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Long.parseLong(strObj.getValue());
        }
        return array;
      } else if (clazz == float[].class) {
        float[] array = new float[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Float.parseFloat(strObj.getValue());
        }
        return array;
      } else if (clazz == double[].class) {
        double[] array = new double[object.getArrayValueList().size()];
        int idx = 0;
        for (Object strObj : object.getArrayValueList()) {
          array[idx++] = Double.parseDouble(strObj.getValue());
        }
        return array;

      } else {
        // TODO finish all primitive types
        throw new IllegalArgumentException("Unsupported array type:" + clazz.getName());
      }

      // is String
    } else if (clazz == String.class) {
      // no casting needed
      return object.getValue();

      // no other types supported
    } else {
      throw new IllegalArgumentException("Unsupported object type:" + clazz.getName());
    }
  }

  public static java.lang.Object unwrapObject(Object object) throws ClassNotFoundException {
    final String objClassName = object.getClass_().getName();
    Class objectClass = Classes.getClassForPrimitive(objClassName);
    if (objectClass == null) {
      objectClass =
          Class.forName(objClassName, true, Thread.currentThread().getContextClassLoader());
    }
    return unwrapObject(object, objectClass);
  }
}
