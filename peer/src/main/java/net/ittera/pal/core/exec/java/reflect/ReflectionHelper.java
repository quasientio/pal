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

package net.ittera.pal.core.exec.java.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.core.exec.java.AmbiguousCallException;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NOTE: lookupMethod will not currently find inherited methods if they're not public, because
 * Class.getDeclaredMethods does not include inherited methods, only Class.getMethods does.
 */
@Singleton
public class ReflectionHelper {
  private static final Logger logger = LoggerFactory.getLogger(ReflectionHelper.class);
  private static final boolean ALLOW_NON_PUBLIC_DEFAULT = false;
  private final boolean allowNonPublic;

  public ReflectionHelper() {
    this(ALLOW_NON_PUBLIC_DEFAULT);
  }

  public ReflectionHelper(boolean allowNonPublic) {
    this.allowNonPublic = allowNonPublic;
  }

  @Inject
  public ReflectionHelper(@Named("rpc.allow_nonpublic") String rpcAllowNonpublicStr) {
    this.allowNonPublic = Boolean.parseBoolean(rpcAllowNonpublicStr);
  }

  private static final Map<String, String> shortToLongNames =
      new HashMap<String, String>() {
        {
          put("string", "java.lang.String");
          put("String", "java.lang.String");
          put("Character", "java.lang.Character");
          put("Boolean", "java.lang.Boolean");
          put("Byte", "java.lang.Byte");
          put("Short", "java.lang.Short");
          put("Integer", "java.lang.Integer");
          put("Long", "java.lang.Long");
          put("Float", "java.lang.Float");
          put("Double", "java.lang.Double");
        }
      };

  private final Map<String, Method> matchedMethodsCache = new ConcurrentHashMap<>();
  private final Map<String, Constructor<?>> matchedConstructorsCache = new ConcurrentHashMap<>();

  /**
   * Converts short type names to canonical names (ie. java.lang.String instead of String) Works for
   * String and primitive wrappers
   *
   * @param shortTypeNames
   * @return
   */
  private List<String> shortTypeNamesToCanonical(List<String> shortTypeNames) {
    return shortTypeNames.stream()
        .map(shortName -> shortToLongNames.getOrDefault(shortName, shortName))
        .collect(Collectors.toList());
  }

  /**
   * Gets the right method when a parameter is a subtype of a method's formal parameter type (based
   * on http://stackoverflow.com/a/2580699)
   *
   * @param clazz
   * @param parameters
   * @param parameterTypeNames
   * @param methodName
   * @return
   */
  public Method lookupMethod(
      Class clazz, Object[] parameters, List<String> parameterTypeNames, String methodName)
      throws AmbiguousCallException, NoSuchMethodException {
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
      traceParameters(parameters, parameterTypeNames);
    }

    // convert short type names to fully qualified names
    List<String> fullyQualifiedParamTypeNames = shortTypeNamesToCanonical(parameterTypeNames);

    // lookup in cache
    Method cached =
        (Method) lookupInCache(clazz, methodName, fullyQualifiedParamTypeNames, Method.class);
    if (cached != null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Got cached method with signature in step0: {}", cached.toGenericString());
      }
      return cached;
    }

    // let's try an exact match
    try {
      // create type array
      Class[] parameterTypes = new Class[fullyQualifiedParamTypeNames.size()];
      for (int i = 0; i < fullyQualifiedParamTypeNames.size(); i++) {
        parameterTypes[i] =
            Classes.getClassForPrimitiveOrWrapper(fullyQualifiedParamTypeNames.get(i));
        if (parameterTypes[i]
            == null) { // not a primitive or wrapper  - try to load the class by name
          parameterTypes[i] =
              Class.forName(
                  fullyQualifiedParamTypeNames.get(i),
                  false,
                  Thread.currentThread().getContextClassLoader());
        }
      }

      Method methodFound;
      try {
        methodFound = clazz.getMethod(methodName, parameterTypes);
      } catch (NoSuchMethodException e) {
        if (allowNonPublic) {
          methodFound = clazz.getDeclaredMethod(methodName, parameterTypes);
        } else {
          throw e; // rethrow so we can catch it below
        }
      }
      cache(clazz, methodName, fullyQualifiedParamTypeNames, methodFound);
      if (logger.isDebugEnabled()) {
        logger.debug("Got method with signature in step1: {}", methodFound.toGenericString());
      }
      return methodFound;
    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Could not find method the easy way - {}", e.getMessage());
      }
    }

    // scan public methods that are assignable from the parameters
    List<Method> matchingMethods =
        Arrays.stream(clazz.getMethods())
            .filter(m -> methodName.equals(m.getName()))
            .filter(m -> m.getParameterTypes().length == parameters.length)
            .filter(
                method -> {
                  final Class<?>[] parameterTypes = method.getParameterTypes();
                  for (int i = 0; i < parameterTypes.length; i++) {
                    if (!isAssignable(parameters[i], parameterTypes[i])) {
                      return false;
                    }
                  }
                  return true;
                })
            .collect(Collectors.toList());

    if (!matchingMethods.isEmpty()) {
      if (matchingMethods.size() > 1) {
        throw new AmbiguousCallException(
            clazz.getName(), methodName, fullyQualifiedParamTypeNames, matchingMethods);
      }
      Method method = matchingMethods.get(0);
      cache(clazz, methodName, fullyQualifiedParamTypeNames, method);
      if (logger.isDebugEnabled()) {
        logger.debug("Got method with signature in step2: {}", method.toGenericString());
      }
      return method;
    }

    // now scan other (ie. non-public) methods
    if (allowNonPublic) {
      matchingMethods =
          Arrays.stream(clazz.getDeclaredMethods())
              .filter(m -> methodName.equals(m.getName()))
              .filter(
                  m -> !Modifier.isPublic(m.getModifiers())) // we already checked the public ones
              .filter(m -> m.getParameterTypes().length == parameters.length)
              .filter(
                  method -> {
                    final Class<?>[] parameterTypes = method.getParameterTypes();
                    for (int i = 0; i < parameterTypes.length; i++) {
                      if (!isAssignable(parameters[i], parameterTypes[i])) {
                        return false;
                      }
                    }
                    return true;
                  })
              .collect(Collectors.toList());

      if (!matchingMethods.isEmpty()) {
        if (matchingMethods.size() > 1) {
          throw new AmbiguousCallException(
              clazz.getName(), methodName, fullyQualifiedParamTypeNames, matchingMethods);
        }
        Method method = matchingMethods.get(0);
        cache(clazz, methodName, fullyQualifiedParamTypeNames, method);
        if (logger.isDebugEnabled()) {
          logger.debug("Got method with signature in step3: {}", method.toGenericString());
        }
        return method;
      }
    }
    throw new NoSuchMethodException(
        String.format(
            "No matching method found for name:%s and parameter types: (%s)",
            methodName, String.join(", ", parameterTypeNames)));
  }

  public Constructor<?> lookupConstructor(
      Class<?> clazz, Object[] parameters, List<String> parameterTypeNames)
      throws AmbiguousCallException, NoSuchMethodException {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ class:{}", clazz.getName());
    }

    if (parameters.length != parameterTypeNames.size()) {
      throw new IllegalArgumentException(
          String.format(
              "Params length=%s, different from parameter types length=%s",
              parameters.length, parameterTypeNames.size()));
    }

    // trace params
    if (logger.isTraceEnabled()) {
      traceParameters(parameters, parameterTypeNames);
    }

    // convert short type names to fully qualified names
    List<String> fullyQualifiedParamTypeNames = shortTypeNamesToCanonical(parameterTypeNames);

    // lookup in cache
    Constructor<?> cached =
        (Constructor<?>)
            lookupInCache(clazz, null, fullyQualifiedParamTypeNames, Constructor.class);
    if (cached != null) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Got cached constructor with signature in step0: {}", cached.toGenericString());
      }
      return cached;
    }

    // let's try an exact match
    try {
      // create type array
      Class[] parameterTypes = new Class[fullyQualifiedParamTypeNames.size()];
      for (int i = 0; i < fullyQualifiedParamTypeNames.size(); i++) {
        parameterTypes[i] =
            Classes.getClassForPrimitiveOrWrapper(fullyQualifiedParamTypeNames.get(i));
        if (parameterTypes[i]
            == null) { // not a primitive or wrapper  - try to load the class by name
          parameterTypes[i] =
              Class.forName(
                  fullyQualifiedParamTypeNames.get(i),
                  false,
                  Thread.currentThread().getContextClassLoader());
        }
      }

      Constructor<?> constructorFound;
      try {
        constructorFound = clazz.getConstructor(parameterTypes);
      } catch (NoSuchMethodException e) {
        if (allowNonPublic) {
          constructorFound = clazz.getDeclaredConstructor(parameterTypes);
        } else {
          throw e; // rethrow so we can catch it below
        }
      }
      cache(clazz, null, fullyQualifiedParamTypeNames, constructorFound);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Got constructor with signature in step1: {}", constructorFound.toGenericString());
      }
      return constructorFound;
    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Could not find constructor the easy way - {}", e.getMessage());
      }
    }

    // scan public constructors that are assignable from the parameters
    List<Constructor<?>> matchingConstructors =
        Arrays.stream(clazz.getConstructors())
            .filter(constructor -> constructor.getParameterTypes().length == parameters.length)
            .filter(
                constructor -> {
                  final Class<?>[] parameterTypes = constructor.getParameterTypes();
                  for (int i = 0; i < parameterTypes.length; i++) {
                    if (!isAssignable(parameters[i], parameterTypes[i])) {
                      return false;
                    }
                  }
                  return true;
                })
            .collect(Collectors.toList());

    if (!matchingConstructors.isEmpty()) {
      if (matchingConstructors.size() > 1) {
        throw new AmbiguousCallException(
            clazz.getName(), fullyQualifiedParamTypeNames, matchingConstructors);
      }
      Constructor<?> constructor = matchingConstructors.get(0);
      cache(clazz, null, fullyQualifiedParamTypeNames, constructor);
      if (logger.isDebugEnabled()) {
        logger.debug("Got constructor with signature in step2: {}", constructor.toGenericString());
      }
      return constructor;
    }

    // now scan other (ie. non-public) constructors
    if (allowNonPublic) {
      matchingConstructors =
          Arrays.stream(clazz.getDeclaredConstructors())
              .filter(
                  m -> !Modifier.isPublic(m.getModifiers())) // we already checked the public ones
              .filter(m -> m.getParameterTypes().length == parameters.length)
              .filter(
                  method -> {
                    final Class<?>[] parameterTypes = method.getParameterTypes();
                    for (int i = 0; i < parameterTypes.length; i++) {
                      if (!isAssignable(parameters[i], parameterTypes[i])) {
                        return false;
                      }
                    }
                    return true;
                  })
              .collect(Collectors.toList());

      if (!matchingConstructors.isEmpty()) {
        if (matchingConstructors.size() > 1) {
          throw new AmbiguousCallException(
              clazz.getName(), fullyQualifiedParamTypeNames, matchingConstructors);
        }
        Constructor<?> constructor = matchingConstructors.get(0);
        cache(clazz, null, fullyQualifiedParamTypeNames, constructor);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Got constructor with signature in step3: {}", constructor.toGenericString());
        }
        return constructor;
      }
    }
    throw new NoSuchMethodException(
        String.format(
            "No matching constructor found for class:%s and parameter types: (%s)",
            clazz.getName(), String.join(", ", parameterTypeNames)));
  }

  private void cache(
      Class<?> clazz, String memberName, List<String> parameterTypeNames, Member member) {
    String key = buildKey(clazz, memberName, parameterTypeNames);
    if (member instanceof Method) {
      matchedMethodsCache.put(key, (Method) member);
    } else if (member instanceof Constructor) {
      matchedConstructorsCache.put(key, (Constructor<?>) member);
    }
  }

  Member lookupInCache(
      Class<?> clazz,
      String memberName,
      List<String> parameterTypeNames,
      Class<? extends Member> memberType) {
    String key = buildKey(clazz, memberName, parameterTypeNames);
    if (memberType == Method.class) {
      return matchedMethodsCache.get(key);
    } else if (memberType == Constructor.class) {
      return matchedConstructorsCache.get(key);
    }
    return null;
  }

  private String buildKey(
      Class<?> clazz, @Nullable String memberName, List<String> parameterTypeNames) {
    StringBuilder keyBuilder = new StringBuilder(memberName == null ? "" : memberName);
    ClassLoader cl = clazz.getClassLoader();
    keyBuilder.append(cl == null ? "bootstrapCL" : cl.toString());
    keyBuilder.append(clazz.getName());
    parameterTypeNames.forEach(keyBuilder::append);
    return keyBuilder.toString();
  }

  private boolean isAssignable(Object object, Class<?> clazz) {
    if (object == null) {
      return !clazz.isPrimitive();
    } else {
      return ClassUtils.isAssignable(object.getClass(), clazz);
    }
  }

  private void traceParameters(Object[] parameters, List<String> parameterTypeNames) {
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
}
