package net.ittera.pal.common.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Classes {

  private static final Map<Class<?>, Class<?>> primitiveToWrapper;

  static {
    Map<Class<?>, Class<?>> map = new HashMap<>();
    map.put(Boolean.TYPE, Boolean.class);
    map.put(Byte.TYPE, Byte.class);
    map.put(Character.TYPE, Character.class);
    map.put(Short.TYPE, Short.class);
    map.put(Integer.TYPE, Integer.class);
    map.put(Long.TYPE, Long.class);
    map.put(Double.TYPE, Double.class);
    map.put(Float.TYPE, Float.class);
    map.put(Void.TYPE, Void.TYPE);
    primitiveToWrapper = Collections.unmodifiableMap(map);
  }

  private static final Map<String, Class<?>> primitiveNameToClass;

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
    primitiveNameToClass = Collections.unmodifiableMap(map);
  }

  // reverse map of primitiveWrapper
  private static final Map<Class<?>, Class<?>> wrapperToPrimitive;

  static {
    Map<Class<?>, Class<?>> map =
        primitiveToWrapper.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    wrapperToPrimitive = Collections.unmodifiableMap(map);
  }

  public static boolean isPrimitiveWrapper(Class<?> type) {
    return type != void.class && wrapperToPrimitive.containsKey(type);
  }

  public static Class getClassForPrimitive(String primitiveName) {
    return primitiveNameToClass.get(primitiveName);
  }

  public static boolean isPrimitiveOrWrapper(Class<?> type) {
    return type != null && (type.isPrimitive() || isPrimitiveWrapper(type));
  }
}
