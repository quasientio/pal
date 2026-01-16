/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.util;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides utility methods for handling Java Class objects, particularly focusing on primitive
 * types, their wrapper classes, and array representations. This class offers functionality to
 * identify, map, and validate class names related to primitive types and their corresponding
 * wrapper classes.
 */
public final class Classes {

  /** An unmodifiable list containing all Java primitive type classes. */
  private static final List<Class<?>> PRIMITIVE_CLASSES =
      Arrays.asList(
          boolean.class,
          byte.class,
          char.class,
          double.class,
          float.class,
          int.class,
          long.class,
          short.class);

  /** An unmodifiable list containing all Java primitive wrapper classes. */
  private static final List<Class<?>> PRIMITIVE_WRAPPER_CLASSES =
      Arrays.asList(
          Boolean.class,
          Byte.class,
          Character.class,
          Double.class,
          Float.class,
          Integer.class,
          Long.class,
          Short.class);

  /** An unmodifiable list containing all one-dimensional Java primitive array classes. */
  private static final List<Class<?>> PRIMITIVE_ARRAY_CLASSES =
      Arrays.asList(
          boolean[].class,
          byte[].class,
          char[].class,
          double[].class,
          float[].class,
          int[].class,
          long[].class,
          short[].class);

  /** An unmodifiable list containing all one-dimensional Java primitive wrapper array classes. */
  private static final List<Class<?>> PRIMITIVE_WRAPPER_ARRAY_CLASSES =
      Arrays.asList(
          Boolean[].class,
          Byte[].class,
          Character[].class,
          Double[].class,
          Float[].class,
          Integer[].class,
          Long[].class,
          Short[].class);

  /**
   * A mapping from simple class names to their fully qualified names for certain commonly used
   * classes.
   */
  private static final ImmutableMap<String, String> simpleToLongNames =
      ImmutableMap.<String, String>builder()
          .put("String", "java.lang.String")
          .put("Character", "java.lang.Character")
          .put("Boolean", "java.lang.Boolean")
          .put("Byte", "java.lang.Byte")
          .put("Short", "java.lang.Short")
          .put("Integer", "java.lang.Integer")
          .put("Long", "java.lang.Long")
          .put("Float", "java.lang.Float")
          .put("Double", "java.lang.Double")
          .build();

  /**
   * An unmodifiable map that associates primitive type names with their corresponding Class
   * objects.
   */
  private static final Map<String, Class<?>> PRIMITIVE_NAME_TO_CLASS;

  static {
    Map<String, Class<?>> map = new HashMap<>();
    map.put("boolean", Boolean.TYPE);
    map.put("byte", Byte.TYPE);
    map.put("char", Character.TYPE);
    map.put("short", Short.TYPE);
    map.put("int", Integer.TYPE);
    map.put("long", Long.TYPE);
    map.put("double", Double.TYPE);
    map.put("float", Float.TYPE);
    map.put("void", Void.TYPE);
    PRIMITIVE_NAME_TO_CLASS = Collections.unmodifiableMap(map);
  }

  /**
   * An unmodifiable set of Java primitive type names, excluding "void". Used to verify if a given
   * name corresponds strictly to a primitive type.
   */
  private static final Set<String> ONLY_PRIMITIVE_NAMES;

  static {
    Set<String> primitives = new HashSet<>(PRIMITIVE_NAME_TO_CLASS.keySet());
    primitives.remove("void");
    ONLY_PRIMITIVE_NAMES = Collections.unmodifiableSet(primitives);
  }

  /** An unmodifiable set of Java primitive wrapper class names. */
  private static final Set<String> PRIMITIVE_WRAPPER_NAMES;

  static {
    Set<String> wrappers = new HashSet<>();
    for (Class<?> clazz : PRIMITIVE_WRAPPER_CLASSES) {
      wrappers.add(clazz.getSimpleName());
    }
    PRIMITIVE_WRAPPER_NAMES = Collections.unmodifiableSet(wrappers);
  }

  /** Private constructor to prevent instantiation of this utility class. */
  private Classes() {}

  /**
   * Retrieves an unmodifiable list of all Java primitive type classes.
   *
   * @return an unmodifiable List containing all primitive Class objects.
   */
  public static List<Class<?>> getPrimitiveClasses() {
    return Collections.unmodifiableList(PRIMITIVE_CLASSES);
  }

  /**
   * Retrieves an unmodifiable list of all Java primitive wrapper classes.
   *
   * @return an unmodifiable List containing all primitive wrapper Class objects.
   */
  public static List<Class<?>> getPrimitiveWrapperClasses() {
    return Collections.unmodifiableList(PRIMITIVE_WRAPPER_CLASSES);
  }

  /**
   * Retrieves an unmodifiable list of all one-dimensional Java primitive array classes.
   *
   * @return an unmodifiable List containing all primitive array Class objects.
   */
  public static List<Class<?>> getPrimitiveArrayClasses() {
    return Collections.unmodifiableList(PRIMITIVE_ARRAY_CLASSES);
  }

  /**
   * Retrieves an unmodifiable list of all one-dimensional Java primitive wrapper array classes.
   *
   * @return an unmodifiable List containing all primitive wrapper array Class objects.
   */
  public static List<Class<?>> getPrimitiveWrapperArrayClasses() {
    return Collections.unmodifiableList(PRIMITIVE_WRAPPER_ARRAY_CLASSES);
  }

  /**
   * Determines whether the specified class is a primitive wrapper type.
   *
   * @param type the Class object to check
   * @return {@code true} if the specified class is a primitive wrapper; {@code false} otherwise
   * @see #PRIMITIVE_WRAPPER_CLASSES
   */
  public static boolean isPrimitiveWrapper(Class<?> type) {
    return PRIMITIVE_WRAPPER_CLASSES.contains(type);
  }

  /**
   * Converts a simple class name to its fully qualified name.
   *
   * @param shortName the simple name of the class
   * @return the fully qualified class name, or {@code null} if the simple name is not recognized
   * @see #simpleToLongNames
   */
  public static String simpleToLongName(String shortName) {
    return simpleToLongNames.get(shortName);
  }

  /**
   * Retrieves the corresponding primitive class for a given wrapper class.
   *
   * @param wrapper the wrapper Class to convert
   * @return the associated primitive Class, or {@code null} if the provided class is not a
   *     primitive wrapper
   * @see #PRIMITIVE_WRAPPER_CLASSES
   * @see #PRIMITIVE_CLASSES
   */
  public static Class<?> getPrimitiveClassForWrapper(Class<?> wrapper) {
    if (wrapper == null) {
      return null;
    }

    if (isPrimitiveWrapper(wrapper)) {
      return PRIMITIVE_CLASSES.get(PRIMITIVE_WRAPPER_CLASSES.indexOf(wrapper));
    }

    return null;
  }

  /**
   * Retrieves the Class object associated with a given primitive type name.
   *
   * @param primitiveName the name of the primitive type
   * @return the corresponding primitive Class, or {@code null} if the name does not correspond to a
   *     primitive type
   * @see #PRIMITIVE_NAME_TO_CLASS
   */
  @SuppressWarnings("rawtypes")
  public static Class getClassForPrimitive(String primitiveName) {
    return PRIMITIVE_NAME_TO_CLASS.get(primitiveName);
  }

  /**
   * Checks whether the specified class is either a primitive type or a primitive wrapper type.
   *
   * @param type the Class object to check
   * @return {@code true} if the class is a primitive or a primitive wrapper; {@code false}
   *     otherwise
   * @see #PRIMITIVE_CLASSES
   * @see #PRIMITIVE_WRAPPER_CLASSES
   */
  public static boolean isPrimitiveOrWrapper(Class<?> type) {
    if (type == null) {
      return false;
    }

    return type.isPrimitive() || isPrimitiveWrapper(type);
  }

  /**
   * Determines whether the given class name corresponds to a Java primitive type.
   *
   * @param className the name of the class to check
   * @return {@code true} if the class name is a primitive type; {@code false} otherwise
   * @see #ONLY_PRIMITIVE_NAMES
   */
  public static boolean isPrimitive(String className) {
    return ONLY_PRIMITIVE_NAMES.contains(className);
  }

  /**
   * Checks if the provided class name represents a one-dimensional primitive array.
   *
   * @param className the name of the class to check
   * @return {@code true} if the class name is a one-dimensional primitive array; {@code false}
   *     otherwise
   */
  public static boolean isOneDimensionalPrimitiveArray(String className) {
    return className.matches("\\[[BCDFIJSZ]");
  }

  /**
   * Checks if the provided class name represents a one-dimensional primitive wrapper array.
   *
   * @param className the name of the class to check
   * @return {@code true} if the class name is a one-dimensional primitive wrapper array; {@code
   *     false} otherwise
   */
  public static boolean isOneDimensionalPrimitiveWrapperArray(String className) {
    return className.matches(
        "\\[L(java\\.lang\\.)?(Boolean|Byte|Character|Short|Integer|Long|Float|Double);");
  }

  /**
   * Validates whether the provided string is a valid Java class name, including array types.
   *
   * @param className the class name to validate
   * @return {@code true} if the class name is valid; {@code false} otherwise
   */
  public static boolean isValidClassName(String className) {
    if (className == null || className.isEmpty()) {
      return false;
    }
    String arrayRegex = "(\\[)*";
    String baseTypeRegex = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*";
    String primitiveArrayRegex = "\\[+[BCDFIJSZ]";
    String endRegex = "(;)?";
    String regex = "(" + arrayRegex + "?" + baseTypeRegex + endRegex + ")|" + primitiveArrayRegex;
    return className.matches(regex);
  }

  /**
   * Validates whether the provided string is a valid non-array Java class name.
   *
   * @param className the class name to validate
   * @return {@code true} if the class name is a valid non-array name; {@code false} otherwise
   */
  public static boolean isValidNonArrayClassName(String className) {
    if (className == null || className.isEmpty()) {
      return false;
    }
    String baseTypeRegex = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*";
    return className.matches(baseTypeRegex);
  }

  /**
   * Maps a given type string to its proper array class name representation.
   *
   * @param type the type string to map
   * @return the corresponding array class name, or {@code null} if the type cannot be mapped
   */
  public static String mapToProperArrayClassName(String type) {
    if (type == null) {
      return null;
    }

    if (isOneDimensionalPrimitiveArray(type)) {
      return type;
    }

    if (isOneDimensionalPrimitiveWrapperArray(type)) {
      return type;
    }

    if (type.endsWith("[]")) {
      // parse what comes before
      String suffixedType = type.substring(0, type.length() - 2);
      if (isValidNonArrayClassName(suffixedType)) {
        if (isPrimitive(suffixedType)) {
          return switch (suffixedType) {
            case "boolean" -> "[Z";
            case "byte" -> "[B";
            case "char" -> "[C";
            case "double" -> "[D";
            case "float" -> "[F";
            case "int" -> "[I";
            case "long" -> "[J";
            case "short" -> "[S";
            default -> null;
          };
        } else if (PRIMITIVE_WRAPPER_NAMES.contains(suffixedType)) {
          return "[Ljava.lang." + suffixedType + ";";
        } else if (suffixedType.equals("String")) {
          return "[Ljava.lang.String;";
        }
      }
    }
    return null;
  }

  /**
   * Maps a type string to its corresponding component Class object.
   *
   * @param givenType the type string to map
   * @return the corresponding component Class, or {@code null} if the type string does not match
   *     any known types
   */
  public static Class<?> mapTypeStringToComponentClass(String givenType) {
    return switch (givenType) {
      case "[I", "int[]" -> int.class;
      case "[Z", "boolean[]" -> boolean.class;
      case "[B", "byte[]" -> byte.class;
      case "[S", "short[]" -> short.class;
      case "[C", "char[]" -> char.class;
      case "[D", "double[]" -> double.class;
      case "[F", "float[]" -> float.class;
      case "[J", "long[]" -> long.class;
      case "[Ljava.lang.String;", "String[]" -> String.class;
      case "[Ljava.lang.Integer;", "Integer[]" -> Integer.class;
      case "[Ljava.lang.Boolean;", "Boolean[]" -> Boolean.class;
      case "[Ljava.lang.Long;", "Long[]" -> Long.class;
      case "[Ljava.lang.Double;", "Double[]" -> Double.class;
      case "[Ljava.lang.Float;", "Float[]" -> Float.class;
      case "[Ljava.lang.Short;", "Short[]" -> Short.class;
      case "[Ljava.lang.Character;", "Character[]" -> Character.class;
      case "[Ljava.lang.Byte;", "Byte[]" -> Byte.class;
      default -> null;
    };
  }
}
