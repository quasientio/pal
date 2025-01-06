package net.ittera.pal.core.rpc.exec.java;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility methods to adapt raw, deserialized arguments into the specific parameter types expected
 * by a method or constructor via reflection. Specifically, this address the issue of Json
 * deserializing all numbers as doubles. TODO We may require more robust error handling, and perhaps
 * other numeric conversions (i.e. BigInteger).
 */
public final class ParameterAdaptationUtils {

  private ParameterAdaptationUtils() {}

  /*
   * Adapts a list of raw arguments (e.g. from JSON) to match the parameter types
   * of the given Method. Returns an array of adapted arguments in the same order.
   */
  public static Object[] adaptParametersForMethod(Method method, MessageArgument[] rawArgs) {
    Type[] genericParamTypes = method.getGenericParameterTypes();
    return adaptParameters(genericParamTypes, rawArgs);
  }

  /*
   * Adapts a list of raw arguments to match the parameter types
   * of the given Constructor. Returns an array of adapted arguments.
   */
  public static Object[] adaptParametersForConstructor(
      Constructor<?> ctor, MessageArgument[] rawArgs) {
    Type[] genericParamTypes = ctor.getGenericParameterTypes();
    return adaptParameters(genericParamTypes, rawArgs);
  }

  /*
   * Internal helper to adapt each rawArg to the expected genericParamType.
   */
  private static Object[] adaptParameters(Type[] genericParamTypes, MessageArgument[] rawArgs) {
    if (rawArgs == null) {
      return new Object[0];
    }
    if (rawArgs.length != genericParamTypes.length) {
      throw new IllegalArgumentException(
          "Mismatch: method/constructor expects "
              + genericParamTypes.length
              + " args, but got "
              + rawArgs.length);
    }

    Object[] adapted = new Object[rawArgs.length];
    for (int i = 0; i < rawArgs.length; i++) {
      if (rawArgs[i].byReference()) {
        adapted[i] = rawArgs[i].object();
      } else {
        // we only adapt parameters that are passed by-value
        Type paramType = genericParamTypes[i];
        Object rawValue = rawArgs[i].object();
        adapted[i] = adaptToMethodParam(rawValue, paramType);
      }
    }
    return adapted;
  }

  /*
   * Adapts a single raw value (e.g. from Gson) to match the expected Type
   * (which may be a Class, a ParameterizedType, etc.).
   *
   * Examples:
   *  - If the paramType is List<Integer>, and rawValue is a List<Double>,
   *    convert doubles-without-decimals to integers.
   *  - If paramType is Map<String, Long>, do the same numeric conversions
   *    for all values that are numeric.
   */
  public static Object adaptToMethodParam(Object rawValue, Type paramType) {
    if (rawValue == null) {
      return null;
    }

    // If paramType is a ParameterizedType (e.g. List<Integer>, Map<String,Long>)
    if (paramType instanceof ParameterizedType pType) {
      Type raw = pType.getRawType(); // e.g. java.util.List
      if (raw instanceof Class<?> rawClass) {
        if (Collection.class.isAssignableFrom(rawClass)) { // Handle collections
          return adaptCollection(rawValue, pType);
        } else if (Map.class.isAssignableFrom(rawClass)) { // Handle maps
          return adaptMap(rawValue, pType);
        }
        // TODO handle other parameterized classes as required
      }
      // If we get here, we either don't handle it or it's some advanced generic
      return rawValue;
    } else if (paramType instanceof Class<?> clazz) { // If it's just a Class<?> (non-parameterized)
      if (isCompatibleWithPrimitive(clazz, rawValue)) {
        return rawValue; // Skip adaptation
      }

      // If we already match, skip adaptation
      if (clazz.isInstance(rawValue)) {
        return rawValue;
      }

      // If we need numeric adaptation or other conversions:
      return adaptToClass(rawValue, clazz);
    }
    // If something else (WildcardType, TypeVariable, etc.), fallback
    return rawValue;
  }

  /*
   * Checks if the rawValue is compatible with the primitive type represented by clazz.
   * For example:
   * - If clazz is boolean.class, rawValue can be a Boolean.
   * - If clazz is int.class, rawValue can be an Integer.
   */
  private static boolean isCompatibleWithPrimitive(Class<?> clazz, Object rawValue) {
    if (!clazz.isPrimitive()) {
      return false;
    }

    if (clazz == boolean.class && rawValue instanceof Boolean) {
      return true;
    }
    if (clazz == int.class && rawValue instanceof Integer) {
      return true;
    }
    if (clazz == long.class && rawValue instanceof Long) {
      return true;
    }
    if (clazz == double.class && rawValue instanceof Double) {
      return true;
    }
    if (clazz == float.class && rawValue instanceof Float) {
      return true;
    }
    if (clazz == short.class && rawValue instanceof Short) {
      return true;
    }
    if (clazz == byte.class && rawValue instanceof Byte) {
      return true;
    }
    if (clazz == char.class && rawValue instanceof Character) {
      return true;
    }

    return false; // Not compatible
  }

  /*
   * Converts a rawValue to a collection type (e.g. List<Integer>, Set<Long>, etc.).
   * - rawValue is expected to be a Collection<?> or something convertible to that.
   * - typeArgs[0] is the element type (e.g. Integer).
   */
  private static Object adaptCollection(Object rawValue, ParameterizedType pType) {
    if (!(rawValue instanceof Collection<?> rawCollection)) {
      throw new IllegalArgumentException("Expected a Collection but got: " + rawValue.getClass());
    }

    Class<?> rawClass = (Class<?>) pType.getRawType(); // e.g. List.class or ArrayList.class
    Type[] typeArgs = pType.getActualTypeArguments();
    Type elementType = (typeArgs.length > 0) ? typeArgs[0] : Object.class;

    // If rawValue already matches the expected collection type and its elements match
    if (rawClass.isInstance(rawValue) && elementsMatchType(rawCollection, elementType)) {
      return rawValue; // No need to create a new instance or adapt
    }

    // Otherwise, create a new collection and adapt elements
    Collection<Object> resultCollection = createCollectionInstance(rawClass);

    for (Object elem : rawCollection) {
      Object adaptedElem = adaptToMethodParam(elem, elementType);
      resultCollection.add(adaptedElem);
    }
    return resultCollection;
  }

  /*
   * Checks if all elements in the collection match the expected type.
   */
  private static boolean elementsMatchType(Collection<?> collection, Type elementType) {
    for (Object elem : collection) {
      if (elem != null && !isInstanceOfType(elem, elementType)) {
        return false;
      }
    }
    return true;
  }

  /*
   * Determines if a value is an instance of a given Type (handles Class<?> and ParameterizedType).
   */
  private static boolean isInstanceOfType(Object value, Type type) {
    if (type instanceof Class<?> clazz) {
      return clazz.isInstance(value);
    }
    // Add more cases if needed for ParameterizedType, WildcardType, etc.
    return false;
  }

  /*
   * Creates a new instance of the given collection rawClass if possible;
   * else defaults to ArrayList.
   */
  @SuppressWarnings("unchecked")
  private static Collection<Object> createCollectionInstance(Class<?> rawClass) {
    if (!rawClass.isInterface() && !Modifier.isAbstract(rawClass.getModifiers())) {
      try {
        return (Collection<Object>) rawClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException ignored) {
        // fallback below
      }
    }
    // If rawClass is an interface (like List) or abstract, fallback:
    return new ArrayList<>();
  }

  /*
   * Converts rawValue to a map type (e.g. Map<K, V>).
   * - rawValue is expected to be a Map<?, ?>
   * - typeArgs[0] is the key type, typeArgs[1] is the value type
   */
  private static Object adaptMap(Object rawValue, ParameterizedType pType) {
    if (!(rawValue instanceof Map<?, ?> rawMap)) {
      throw new IllegalArgumentException("Expected a Map but got: " + rawValue.getClass());
    }

    Class<?> rawClass = (Class<?>) pType.getRawType(); // e.g. HashMap.class, Map.class
    Type[] typeArgs = pType.getActualTypeArguments();
    if (typeArgs.length < 2) {
      return rawValue; // can't adapt well if missing generics
    }

    Type keyType = typeArgs[0];
    Type valType = typeArgs[1];

    Map<Object, Object> resultMap = createMapInstance(rawClass);

    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      Object adaptedKey = adaptToMethodParam(entry.getKey(), keyType);
      Object adaptedValue = adaptToMethodParam(entry.getValue(), valType);
      resultMap.put(adaptedKey, adaptedValue);
    }
    return resultMap;
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> createMapInstance(Class<?> rawClass) {
    if (!rawClass.isInterface() && !Modifier.isAbstract(rawClass.getModifiers())) {
      try {
        return (Map<Object, Object>) rawClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException ignored) {
        // fallback below
      }
    }
    // fallback for interface/abstract classes
    return new LinkedHashMap<>();
  }

  /*
   * Adapts rawValue to a specific Class (e.g. int.class, Integer.class, String.class, etc.).
   * For numeric conversions, tries to convert Double -> Integer if fractional == 0, etc.
   */
  private static Object adaptToClass(Object rawValue, Class<?> clazz) {
    if (Number.class.isAssignableFrom(clazz)
        || (clazz.isPrimitive() && !clazz.equals(Boolean.TYPE))) {
      return adaptNumeric(rawValue, clazz);
    }

    // TODO handle other special cases here like strings, enums, etc. if required
    return rawValue;
  }

  /*
   * Attempts to adapt a numeric rawValue (often a Double) to match the target numeric type (e.g. Integer).
   * This is a simple example that only handles int/Integer and long/Long conversions.
   */
  private static Object adaptNumeric(Object rawValue, Class<?> clazz) {
    if (!(rawValue instanceof Number number)) {
      throw new IllegalArgumentException("Expected a Number, got: " + rawValue.getClass());
    }

    // If the target is a primitive, use the wrapper type for easier comparison
    if (clazz.isPrimitive()) {
      if (clazz == int.class) {
        clazz = Integer.class;
      }
      if (clazz == long.class) {
        clazz = Long.class;
      }
      if (clazz == double.class) {
        clazz = Double.class;
      }
      if (clazz == float.class) {
        clazz = Float.class;
      }
      if (clazz == short.class) {
        clazz = Short.class;
      }
      if (clazz == char.class) {
        clazz = Character.class;
      }
      if (clazz == byte.class) {
        clazz = Byte.class;
      }
    }

    if (clazz == Integer.class) {
      double d = number.doubleValue();
      if (d == (int) d) {
        return (int) d;
      }
      throw new RuntimeException("Fractional part not zero for integer type");
    } else if (clazz == Long.class) {
      double d = number.doubleValue();
      if (d == (long) d) {
        return (long) d;
      }
      throw new RuntimeException("Fractional part not zero for long type");
    } else if (clazz == Double.class) {
      return number.doubleValue();
    } else if (clazz == Float.class) {
      return number.floatValue();
    } else if (clazz == Short.class) {
      return number.shortValue();
    } else if (clazz == Byte.class) {
      return number.byteValue();
    }

    // If unhandled, just return original
    return number;
  }
}
