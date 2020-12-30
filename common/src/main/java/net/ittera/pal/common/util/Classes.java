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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class Classes {

  private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_WRAPPER;

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
    PRIMITIVE_TO_WRAPPER = Collections.unmodifiableMap(map);
  }

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

  // reverse map of primitiveWrapper
  private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE;

  static {
    Map<Class<?>, Class<?>> map =
        PRIMITIVE_TO_WRAPPER.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    WRAPPER_TO_PRIMITIVE = Collections.unmodifiableMap(map);
  }

  private Classes() {}

  public static boolean isPrimitiveWrapper(Class<?> type) {
    return type != void.class && WRAPPER_TO_PRIMITIVE.containsKey(type);
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
}
