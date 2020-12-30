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

package net.ittera.pal.core.exec.java;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.ittera.pal.messages.protobuf.Primitives;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO: Is the use of isAssignable (from commons.lang3.ClassUtils) safe, considering the support
 * for widening? We should avoid that if 2 or more methods match (i.e. all params assignable), the
 * method with the least specific type are chosen <br>
 * TODO: WE MUST UNIT TEST THIS CLASS <br>
 * TODO: Use streams <br>
 */
public final class ReflectionHelper {

  private static final Logger logger = LoggerFactory.getLogger(ReflectionHelper.class);

  private static final Map<String, Method> matchedMethodsCache = new ConcurrentHashMap<>();

  private ReflectionHelper() {
    // avoid instantiation
  }

  /**
   * Gets the right method when a parameter is a subtype of a method's formal parameter type (based
   * on http://stackoverflow.com/a/2580699)
   *
   * @param clazz
   * @param parameters
   * @param methodName
   * @return
   */
  public static Method getMethodToInvoke(
      Class clazz,
      Object[] parameters,
      List<Primitives.Object> parameterTypeNames,
      String methodName) {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ class:{} and method:{}", clazz.getName(), methodName);
    }

    if (parameters.length != parameterTypeNames.size()) {
      throw new IllegalArgumentException(
          String.format(
              "Params length=%s, different from parameter types length=%s",
              parameters.length, parameterTypeNames.size()));
    }

    // trace params
    if (logger.isTraceEnabled()) {
      if (parameters.length == 0) {
        logger.trace("params of length=0");
      } else {
        final StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < parameters.length; i++) {
          stringBuilder
              .append("params[")
              .append(i)
              .append("]=")
              .append(parameters[i])
              .append(" type:")
              .append(parameterTypeNames.get(i))
              .append('\n');
        }
        logger.trace(stringBuilder.toString());
      }
    }

    // cache lookup
    Method cached = lookup(clazz, methodName, parameterTypeNames);
    if (cached != null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Got cached method with signature in step0: {}", cached);
      }
      return cached;
    }

    // let's try the easy way
    try {
      // create type array
      Class[] parameterTypes = new Class[parameterTypeNames.size()];
      for (int i = 0; i < parameterTypeNames.size(); i++) {
        parameterTypes[i] =
            Class.forName(
                parameterTypeNames.get(i).getClass_().getName(),
                true,
                Thread.currentThread().getContextClassLoader());
      }

      Method methodFound = clazz.getMethod(methodName, parameterTypes);
      cache(clazz, methodName, parameterTypeNames, methodFound);
      if (logger.isDebugEnabled()) {
        logger.debug("Got method with signature in step1: {}", methodFound);
      }
      return methodFound;
    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Could not find method the easy way - {}", e.getMessage());
      }
    }

    // scan public methods
    for (Method method : clazz.getMethods()) {
      if (logger.isTraceEnabled()) {
        logger.trace("public method: {}", method.getName());
      }
      if (!method.getName().equals(methodName)) {
        continue;
      }
      final Class<?>[] parameterTypes = method.getParameterTypes();
      if (parameterTypes.length != parameters.length) {
        continue;
      }

      boolean matches = true;
      for (int i = 0; i < parameterTypes.length; i++) {
        if (!isAssignable(parameters[i], parameterTypes[i])) {
          matches = false;
          break;
        }
      }
      if (matches) {
        cache(clazz, methodName, parameterTypeNames, method);
        if (logger.isDebugEnabled()) {
          logger.debug("Got method with signature in step2: {}", method);
        }
        return method;
      }
    }

    // now scan other methods
    for (Method method : clazz.getDeclaredMethods()) {
      if (logger.isDebugEnabled()) {
        logger.debug("declared method: {}", method.getName());
      }
      if (!method.getName().equals(methodName)) {
        continue;
      }
      final Class<?>[] parameterTypes = method.getParameterTypes();
      if (parameterTypes.length != parameters.length) {
        continue;
      }

      boolean matches = true;
      for (int i = 0; i < parameterTypes.length; i++) {
        if (!isAssignable(parameters[i], parameterTypes[i])) {
          matches = false;
          break;
        }
      }
      if (matches) {
        cache(clazz, methodName, parameterTypeNames, method);
        if (logger.isDebugEnabled()) {
          logger.debug("Got method with signature in step3: {}", method);
        }
        return method;
      }
    }

    logger.warn("No matching method found for name:{}", methodName);

    return null;
  }

  private static void cache(
      Class clazz, String methodName, List<Primitives.Object> parameterTypeNames, Method method) {
    String key = buildKey(clazz, methodName, parameterTypeNames);
    matchedMethodsCache.put(key, method);
  }

  private static Method lookup(
      Class clazz, String methodName, List<Primitives.Object> parameterTypeNames) {
    String key = buildKey(clazz, methodName, parameterTypeNames);
    return matchedMethodsCache.get(key);
  }

  private static String buildKey(
      Class clazz, String methodName, List<Primitives.Object> parameterTypeNames) {
    StringBuilder keyBuilder = new StringBuilder(methodName);
    ClassLoader cl = clazz.getClassLoader();
    keyBuilder.append(cl == null ? "bootstrapCL" : cl.toString());
    keyBuilder.append(clazz.getName());
    for (Primitives.Object paramType : parameterTypeNames) {
      keyBuilder.append(paramType.getClass_().getName());
    }
    return keyBuilder.toString();
  }

  private static boolean isAssignable(Object object, Class clazz) {
    if (object == null) {
      return !clazz.isPrimitive();
    } else {
      return ClassUtils.isAssignable(object.getClass(), clazz);
    }
  }
}
