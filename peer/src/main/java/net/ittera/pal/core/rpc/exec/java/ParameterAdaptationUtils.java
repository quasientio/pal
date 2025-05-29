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
 * by methods or constructors via reflection.
 *
 * <p>This class mainly addresses the typical issue introduced by JSON deserialization where all
 * numbers are interpreted as doubles. It provides conversion routines for adapting numeric values,
 * collections, and maps to the required types. Advanced conversion cases (e.g. BigInteger or
 * special parameterized types) might need further error handling as noted by the TODO comments.
 */
public final class ParameterAdaptationUtils {

  /** Private constructor to prevent instantiation. */
  private ParameterAdaptationUtils() {}

  /**
   * Adapts the raw MessageArgument array to the parameter types expected by the given method.
   *
   * <p>Each raw argument is converted to its corresponding parameter type considering generic and
   * primitive types. If an argument is passed by reference, no adaptation is performed.
   *
   * @param method the target method whose parameter types are used for adaptation; must not be
   *     null.
   * @param rawArgs an array of raw MessageArgument instances, typically produced by JSON
   *     deserialization.
   * @return an array of objects adapted to match the parameter types of the provided method.
   * @throws IllegalArgumentException if the number of provided arguments does not match the
   *     method's parameter count.
   */
  public static Object[] adaptParametersForMethod(Method method, MessageArgument[] rawArgs) {
    Type[] genericParamTypes = method.getGenericParameterTypes();
    return adaptParameters(genericParamTypes, rawArgs);
  }

  /**
   * Adapts the raw MessageArgument array to the parameter types expected by the given constructor.
   *
   * <p>This method converts each raw argument to match the constructor's expected parameter type,
   * taking into account numeric conversions and parameterized types.
   *
   * @param ctor the constructor whose parameter types are targeted; must not be null.
   * @param rawArgs an array of raw MessageArgument instances, typically produced by JSON
   *     deserialization.
   * @return an array of objects adapted to match the constructor's parameter types.
   * @throws IllegalArgumentException if the number of provided arguments does not match the
   *     constructor's parameter count.
   */
  public static Object[] adaptParametersForConstructor(
      Constructor<?> ctor, MessageArgument[] rawArgs) {
    Type[] genericParamTypes = ctor.getGenericParameterTypes();
    return adaptParameters(genericParamTypes, rawArgs);
  }

  /**
   * Internally adapts raw arguments to match the provided generic parameter types.
   *
   * <p>The method verifies that the number of raw arguments matches the expected parameter count.
   * For by-reference arguments (as indicated by MessageArgument.byReference()), no conversion is
   * performed.
   *
   * @param genericParamTypes an array of expected parameter types.
   * @param rawArgs an array of raw MessageArgument instances.
   * @return an array of objects adapted to the corresponding generic parameter types.
   * @throws IllegalArgumentException if the number of provided arguments does not match the
   *     expected count.
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

  /**
   * Adapts a single raw value to match the expected method parameter type.
   *
   * <p>Depending on the nature of the expected type (e.g. parameterized type, primitive, or class),
   * this method attempts conversion such as adapting a collection, map, or numeric value. If the
   * raw value is already an instance of the expected type, it is returned unchanged.
   *
   * @param rawValue the raw value to be adapted.
   * @param paramType the expected type of the parameter, which can be a Class or ParameterizedType.
   * @return the adapted object matching the expected type, or the original value if no adaptation
   *     is needed.
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

  /**
   * Checks whether the provided raw value is compatible with the specified primitive type.
   *
   * <p>For instance, if the target primitive is int, the raw value must be an instance of Integer.
   * This method supports standard primitive types and their corresponding wrapper classes.
   *
   * @param clazz the primitive class (e.g. int.class) to check against.
   * @param rawValue the value whose compatibility is to be tested.
   * @return true if rawValue is compatible with the specified primitive type; false otherwise.
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

  /**
   * Adapts a raw value to a collection type as specified by the given parameterized type.
   *
   * <p>The method expects rawValue to be a Collection and adapts each element to match the expected
   * element type. If the raw collection already matches the expected type and its elements conform
   * to the expected element type, the original collection is returned.
   *
   * @param rawValue the raw value expected to be a Collection.
   * @param pType the parameterized type that specifies the collection type and its element type.
   * @return a collection with each element adapted to match the specified generic type.
   * @throws IllegalArgumentException if rawValue is not an instance of Collection.
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

  /**
   * Checks if every element in the given collection is either null or an instance of the specified
   * element type.
   *
   * @param collection the collection whose elements are to be validated.
   * @param elementType the expected type for the elements in the collection.
   * @return true if all non-null elements are instances of elementType; false otherwise.
   */
  private static boolean elementsMatchType(Collection<?> collection, Type elementType) {
    for (Object elem : collection) {
      if (elem != null && !isInstanceOfType(elem, elementType)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Determines if the given value is an instance of the specified type.
   *
   * <p>This method currently supports Class types and can be extended to support other Type
   * implementations.
   *
   * @param value the object to test.
   * @param type the expected type against which the value is checked.
   * @return true if value is an instance of type; false otherwise.
   */
  private static boolean isInstanceOfType(Object value, Type type) {
    if (type instanceof Class<?> clazz) {
      return clazz.isInstance(value);
    }
    // Add more cases if needed for ParameterizedType, WildcardType, etc.
    return false;
  }

  /**
   * Creates a new instance of the given collection class if it is concrete and has a default
   * constructor.
   *
   * <p>If instantiation is not possible (e.g. when the class is an interface or abstract), the
   * method returns a new ArrayList.
   *
   * @param rawClass the collection class to instantiate.
   * @return a new instance of the specified collection class, or an ArrayList if instantiation
   *     fails.
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

  /**
   * Adapts a raw value to a map type as defined by the provided parameterized type.
   *
   * <p>The method expects rawValue to be a Map and converts each key and value according to the
   * specified generic types. If the parameterized type does not specify both key and value types,
   * the original raw value is returned.
   *
   * @param rawValue the raw value expected to be a Map.
   * @param pType the parameterized type that specifies the map's key and value types.
   * @return a map with keys and values adapted to the specified types.
   * @throws IllegalArgumentException if rawValue is not an instance of Map.
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

  /**
   * Creates a new instance of the specified map class if possible.
   *
   * <p>If the provided map class is concrete and has a default constructor, it is instantiated.
   * Otherwise, a new LinkedHashMap is returned as a fallback.
   *
   * @param rawClass the map class to instantiate.
   * @return a new Map instance corresponding to the given class, or a LinkedHashMap if
   *     instantiation fails.
   */
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

  /**
   * Adapts a raw value to match the specified class type.
   *
   * <p>For numeric types (including their primitive representations), this method attempts an
   * appropriate numeric conversion. If rawValue is already an instance of the target class, it is
   * returned directly.
   *
   * @param rawValue the raw value to be adapted.
   * @param clazz the target class type for the conversion.
   * @return the adapted value matching the target class.
   */
  private static Object adaptToClass(Object rawValue, Class<?> clazz) {
    if (Number.class.isAssignableFrom(clazz)
        || (clazz.isPrimitive() && !clazz.equals(Boolean.TYPE))) {
      return adaptNumeric(rawValue, clazz);
    }

    // TODO handle other special cases here like strings, enums, etc. if required
    return rawValue;
  }

  /**
   * Converts a numeric raw value to match the target numeric type.
   *
   * <p>This method handles conversions between various numeric types, for example, converting a
   * Double to an Integer, while ensuring that the fractional part of a numeric value is zero when
   * converting to an integral type.
   *
   * @param rawValue the raw numeric value to be adapted; must be an instance of Number.
   * @param clazz the target numeric class (or primitive) for conversion.
   * @return the numeric value adapted to the target type.
   * @throws IllegalArgumentException if rawValue is not an instance of Number.
   * @throws RuntimeException if converting to an integral type and the fractional part is non-zero.
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
