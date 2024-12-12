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

package net.ittera.pal.common.util;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Classes {
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

  public static List<Class<?>> getPrimitiveClasses() {
    return Collections.unmodifiableList(PRIMITIVE_CLASSES);
  }

  public static List<Class<?>> getPrimitiveWrapperClasses() {
    return Collections.unmodifiableList(PRIMITIVE_WRAPPER_CLASSES);
  }

  public static List<Class<?>> getPrimitiveArrayClasses() {
    return Collections.unmodifiableList(PRIMITIVE_ARRAY_CLASSES);
  }

  public static List<Class<?>> getPrimitiveWrapperArrayClasses() {
    return Collections.unmodifiableList(PRIMITIVE_WRAPPER_ARRAY_CLASSES);
  }

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

  private static final Set<String> ONLY_PRIMITIVE_NAMES;

  static {
    Set<String> primitives = new HashSet<>(PRIMITIVE_NAME_TO_CLASS.keySet());
    primitives.remove("void");
    ONLY_PRIMITIVE_NAMES = Collections.unmodifiableSet(primitives);
  }

  private static final Set<String> PRIMITIVE_WRAPPER_NAMES;

  static {
    Set<String> wrappers = new HashSet<>();
    for (Class<?> clazz : PRIMITIVE_WRAPPER_CLASSES) {
      wrappers.add(clazz.getSimpleName());
    }
    PRIMITIVE_WRAPPER_NAMES = Collections.unmodifiableSet(wrappers);
  }

  private Classes() {}

  public static boolean isPrimitiveWrapper(Class<?> type) {
    return PRIMITIVE_WRAPPER_CLASSES.contains(type);
  }

  public static String simpleToLongName(String shortName) {
    return simpleToLongNames.get(shortName);
  }

  // return primitive class corresponding to wrapper class
  public static Class<?> getPrimitiveClassForWrapper(Class<?> wrapper) {
    if (wrapper == null) {
      return null;
    }

    if (isPrimitiveWrapper(wrapper)) {
      return PRIMITIVE_CLASSES.get(PRIMITIVE_WRAPPER_CLASSES.indexOf(wrapper));
    }

    return null;
  }

  @SuppressWarnings("rawtypes")
  public static Class getClassForPrimitive(String primitiveName) {
    return PRIMITIVE_NAME_TO_CLASS.get(primitiveName);
  }

  public static boolean isPrimitiveOrWrapper(Class<?> type) {
    if (type == null) {
      return false;
    }

    return type.isPrimitive() || isPrimitiveWrapper(type);
  }

  public static boolean isPrimitive(String className) {
    return ONLY_PRIMITIVE_NAMES.contains(className);
  }

  public static boolean isOneDimensionalPrimitiveArray(String className) {
    return className.matches("\\[[BCDFIJSZ]");
  }

  public static boolean isOneDimensionalPrimitiveWrapperArray(String className) {
    return className.matches(
        "\\[L(java\\.lang\\.)?(Boolean|Byte|Character|Short|Integer|Long|Float|Double);");
  }

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

  public static boolean isValidNonArrayClassName(String className) {
    if (className == null || className.isEmpty()) {
      return false;
    }
    String baseTypeRegex = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)*[a-zA-Z_$][a-zA-Z\\d_$]*";
    return className.matches(baseTypeRegex);
  }

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
