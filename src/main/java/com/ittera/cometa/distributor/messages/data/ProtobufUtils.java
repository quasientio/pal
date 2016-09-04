package com.ittera.cometa.distributor.messages.data;

import java.util.List;
import java.util.ArrayList;

public class ProtobufUtils {

  /**
   * Returns objects in objectList as Object array with each object typed as its type in classList
   * This method undoes the wrapping of objects done in DataMessageFactory.getWrappedValue()
   *
   * @param object
   * @param clazz
   * @return
   */
  public static Object unwrapObject(Primitives.Object object, Class clazz) {

    //is primitive
    if (clazz.isPrimitive()) {
      if (clazz == byte.class || clazz == Byte.class) {
        return Byte.valueOf(object.getValue());
      } else if (clazz == short.class || clazz == Short.class) {
        return Short.valueOf(object.getValue());
      } else if (clazz == int.class || clazz == Integer.class) {
        return Integer.valueOf(object.getValue());
      } else if (clazz == long.class || clazz == Long.class) {
        return Long.valueOf(object.getValue());
      } else if (clazz == float.class || clazz == Float.class) {
        return Float.valueOf(object.getValue());
      } else if (clazz == double.class || clazz == Double.class) {
        return Double.valueOf(object.getValue());
      } else if (clazz == char.class || clazz == Character.class) {
        return Character.valueOf(object.getValue().charAt(0));
      } else if (clazz == boolean.class || clazz == Boolean.class) {
        return Boolean.valueOf(object.getValue());
      } else {
        throw new IllegalArgumentException("Unsupported primitive type:" + clazz.getName());
      }

      //is Array
    } else if (clazz.isArray()) {
      if (!object.getIsArray()) {
        throw new IllegalArgumentException("Type is array but wrapped object isn't:" + clazz.getName());
      }

      if (clazz == String[].class) {
        List<String> strList = new ArrayList<>();
        for (Primitives.Object strObj : object.getArrayValueList()) {
          //TODO shouldn't we recursively unwrapObject here?
          strList.add(strObj.getValue());
        }
        return (String[]) strList.toArray(new String[]{});
      }
      //TODO all primitive types
      else {
        throw new IllegalArgumentException("Unsupported array type:" + clazz.getName());
      }

      //is String
    } else if (clazz == String.class) {
      //no casting needed
      return object.getValue();

      //no other types supported
    } else {
      throw new IllegalArgumentException("Unsupported object type:" + clazz.getName());
    }
  }
}
