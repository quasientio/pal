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

import java.lang.reflect.*;
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
   * Convenience method to use when looking up methods for JSON-RPC calls and the parameter types
   * are not known.
   *
   * @param clazz
   * @param parameters
   * @param methodName
   * @return the matching method
   * @throws AmbiguousCallException if more than one method matches the call
   * @throws NoSuchMethodException if no method matches the call
   */
  public Method lookupMethod(Class<?> clazz, Object[] parameters, String methodName)
      throws AmbiguousCallException, NoSuchMethodException {
    return lookupMethod(clazz, parameters, null, methodName);
  }

  /**
   * Gets the right method when a parameter is a subtype of a method's formal parameter type. Uses
   * ClassUtils.isAssignable() and Class.isAssignableFrom() to check for assignability.
   *
   * <p>If parameterTypes are given, method matching will be done using these types. The list of
   * parameterTypes should be the same length as the parameters list. Also, when parameterTypes are
   * given, no primitive widening will be allowed, i.e. if a method has a formal parameter of type
   * int, it will not match a call with a parameter of type long. This is the method to use when
   * looking up methods for regular RPC calls, which normally include the parameter types in the
   * call.
   *
   * <p>If parameterTypes is null, method matching will be done using the parameters' actual types.
   * In this case, primitive widening assignment will be allowed for method matching. Use the
   * convenience method lookupMethod(Class, Object[], String) when looking up methods for JSON-RPC
   * calls and the parameter types are not known.
   *
   * @param clazz
   * @param parameters
   * @param parameterTypes
   * @param methodName
   * @return the matching method
   * @throws AmbiguousCallException if more than one method matches the call
   * @throws NoSuchMethodException if no method matches the call
   */
  public Method lookupMethod(
      Class<?> clazz,
      Object[] parameters,
      @Nullable List<Class<?>> parameterTypes,
      String methodName)
      throws AmbiguousCallException, NoSuchMethodException {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ class:{} and method:{}", clazz.getName(), methodName);
    }

    if (parameterTypes != null && parameterTypes.size() != parameters.length) {
      throw new IllegalArgumentException(
          String.format(
              "Parameters length=%s, different from parameter types length=%s",
              parameters.length, parameterTypes.size()));
    }

    // trace params
    if (logger.isTraceEnabled()) {
      traceParameters(parameters, parameterTypes);
    }

    boolean parameterTypesGiven = parameterTypes != null;

    if (!parameterTypesGiven) {
      parameterTypes = Arrays.stream(parameters).map(Object::getClass).collect(Collectors.toList());
    }

    // lookup in cache
    Method cached = (Method) lookupInCache(clazz, methodName, parameterTypes, Method.class);
    if (cached != null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Got cached method with signature in step0: {}", cached.toGenericString());
      }
      return cached;
    }

    Class<?>[] parameterTypesArray = parameterTypes.toArray(new Class[0]);
    // let's try an exact match
    try {
      Method methodFound;
      try {
        methodFound = clazz.getMethod(methodName, parameterTypesArray);
      } catch (NoSuchMethodException e) {
        if (allowNonPublic) {
          methodFound = clazz.getDeclaredMethod(methodName, parameterTypesArray);
        } else {
          throw e; // rethrow so we can catch it below
        }
      }
      cache(clazz, methodName, parameterTypes, methodFound);
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
                candidate -> {
                  final Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
                  for (int i = 0; i < candidateParameterTypes.length; i++) {
                    if (!isAssignable(
                        parameters[i], parameterTypesArray[i], candidateParameterTypes[i])) {
                      return false;
                    }
                  }
                  return true;
                })
            .collect(Collectors.toList());

    if (!matchingMethods.isEmpty()) {
      if (matchingMethods.size() > 1) {
        throw new AmbiguousCallException(
            clazz.getName(), methodName, parameterTypes, matchingMethods);
      }
      Method method = matchingMethods.get(0);
      cache(clazz, methodName, parameterTypes, method);
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
                  candidate -> {
                    final Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
                    for (int i = 0; i < candidateParameterTypes.length; i++) {
                      if (!isAssignable(
                          parameters[i], parameterTypesArray[i], candidateParameterTypes[i])) {
                        return false;
                      }
                    }
                    return true;
                  })
              .collect(Collectors.toList());

      if (!matchingMethods.isEmpty()) {
        if (matchingMethods.size() > 1) {
          throw new AmbiguousCallException(
              clazz.getName(), methodName, parameterTypes, matchingMethods);
        }
        Method method = matchingMethods.get(0);
        cache(clazz, methodName, parameterTypes, method);
        if (logger.isDebugEnabled()) {
          logger.debug("Got method with signature in step3: {}", method.toGenericString());
        }
        return method;
      }
    }
    throw new NoSuchMethodException(
        String.format(
            "No matching method found for name:%s and parameter types: (%s)",
            methodName,
            parameterTypes.stream().map(Class::getName).collect(Collectors.joining(", "))));
  }

  /**
   * Convenience method to use when looking up constructors for JSON-RPC calls and the parameter
   * types are not known.
   *
   * @param clazz
   * @param parameters
   * @return the matching constructor
   * @throws AmbiguousCallException if more than one constructor matches the call
   * @throws NoSuchMethodException if no constructor matches the call
   */
  public Constructor<?> lookupConstructor(Class<?> clazz, Object[] parameters)
      throws AmbiguousCallException, NoSuchMethodException {
    return lookupConstructor(clazz, parameters, null);
  }
  /**
   * Gets the right constructor when a parameter is a subtype of a constructor's formal parameter
   * type. Uses ClassUtils.isAssignable() and Class.isAssignableFrom() to check for assignability.
   *
   * <p>If parameterTypes are given, constructor matching will be done using these types. The list
   * of parameterTypes should be the same length as the parameters list. Also, when parameterTypes
   * are given, no primitive widening will be allowed, ie. if a constructor has a formal parameter
   * of type int, it will not match a call with a parameter of type long. This is the method to use
   * when looking up constructors for regular RPC calls, which normally include the parameter types
   * in the call.
   *
   * <p>If parameterTypes is null, constructor matching will be done using the parameters' actual
   * types. In this case, primitive widening assignment will be allowed for constructor matching.
   * Use the convenience method lookupConstructor(Class, Object[]) when looking up constructors for
   * JSON-RPC calls and the parameter types are not known.
   *
   * @param clazz
   * @param parameters
   * @param parameterTypes
   * @return the matching constructor
   * @throws AmbiguousCallException if more than one constructor matches the call
   * @throws NoSuchMethodException if no constructor matches the call
   */
  public Constructor<?> lookupConstructor(
      Class<?> clazz, Object[] parameters, @Nullable List<Class<?>> parameterTypes)
      throws AmbiguousCallException, NoSuchMethodException {
    if (logger.isTraceEnabled()) {
      logger.trace("in w/ class:{}", clazz.getName());
    }

    if (parameterTypes != null && parameterTypes.size() != parameters.length) {
      throw new IllegalArgumentException(
          String.format(
              "Parameters length=%s, different from parameter types length=%s",
              parameters.length, parameterTypes.size()));
    }

    // trace params
    if (logger.isTraceEnabled()) {
      traceParameters(parameters, parameterTypes);
    }

    boolean parameterTypesGiven = parameterTypes != null;

    if (!parameterTypesGiven) {
      parameterTypes = Arrays.stream(parameters).map(Object::getClass).collect(Collectors.toList());
    }

    // lookup in cache
    Constructor<?> cached =
        (Constructor<?>) lookupInCache(clazz, null, parameterTypes, Constructor.class);
    if (cached != null) {
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Got cached constructor with signature in step0: {}", cached.toGenericString());
      }
      return cached;
    }

    // let's try an exact match
    Class<?>[] parameterTypesArray = parameterTypes.toArray(new Class[0]);
    try {
      Constructor<?> constructorFound;
      try {
        constructorFound = clazz.getConstructor(parameterTypesArray);
      } catch (NoSuchMethodException e) {
        if (allowNonPublic) {
          constructorFound = clazz.getDeclaredConstructor(parameterTypesArray);
        } else {
          throw e; // rethrow so we can catch it below
        }
      }
      cache(clazz, null, parameterTypes, constructorFound);
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
                candidate -> {
                  final Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
                  for (int i = 0; i < candidateParameterTypes.length; i++) {
                    if (!isAssignable(
                        parameters[i], parameterTypesArray[i], candidateParameterTypes[i])) {
                      return false;
                    }
                  }
                  return true;
                })
            .collect(Collectors.toList());

    if (!matchingConstructors.isEmpty()) {
      if (matchingConstructors.size() > 1) {
        throw new AmbiguousCallException(clazz.getName(), parameterTypes, matchingConstructors);
      }
      Constructor<?> constructor = matchingConstructors.get(0);
      cache(clazz, null, parameterTypes, constructor);
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
                  candidate -> {
                    final Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
                    for (int i = 0; i < candidateParameterTypes.length; i++) {
                      if (!isAssignable(
                          parameters[i], parameterTypesArray[i], candidateParameterTypes[i])) {
                        return false;
                      }
                    }
                    return true;
                  })
              .collect(Collectors.toList());

      if (!matchingConstructors.isEmpty()) {
        if (matchingConstructors.size() > 1) {
          throw new AmbiguousCallException(clazz.getName(), parameterTypes, matchingConstructors);
        }
        Constructor<?> constructor = matchingConstructors.get(0);
        cache(clazz, null, parameterTypes, constructor);
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
            clazz.getName(),
            parameterTypes.stream().map(Class::getName).collect(Collectors.joining(", "))));
  }

  private void cache(
      Class<?> clazz, String memberName, List<Class<?>> parameterTypes, Member member) {
    String key = buildKey(clazz, memberName, parameterTypes);
    if (member instanceof Method) {
      matchedMethodsCache.put(key, (Method) member);
    } else if (member instanceof Constructor) {
      matchedConstructorsCache.put(key, (Constructor<?>) member);
    }
  }

  Member lookupInCache(
      Class<?> clazz,
      String memberName,
      List<Class<?>> parameterTypes,
      Class<? extends Member> memberType) {
    String key = buildKey(clazz, memberName, parameterTypes);
    if (memberType == Method.class) {
      return matchedMethodsCache.get(key);
    } else if (memberType == Constructor.class) {
      return matchedConstructorsCache.get(key);
    }
    return null;
  }

  private String buildKey(
      Class<?> clazz, @Nullable String memberName, List<Class<?>> parameterTypes) {
    StringBuilder keyBuilder = new StringBuilder(memberName == null ? "" : memberName);
    ClassLoader cl = clazz.getClassLoader();
    keyBuilder.append(cl == null ? "bootstrapCL" : cl.toString());
    keyBuilder.append(clazz.getName());
    parameterTypes.stream().map(Class::getName).forEach(keyBuilder::append);
    return keyBuilder.toString();
  }

  private boolean isAssignable(Object parameter, @Nullable Class<?> parameterType, Class<?> clazz) {
    if (parameter == null) {
      return !clazz.isPrimitive();
    } else {
      if (parameterType == null) {
        return ClassUtils.isAssignable(parameter.getClass(), clazz);
      }
      // NOTE: if available, use parameterType for checking assignability, instead of the
      // parameter's actual type
      return ClassUtils.isAssignable(parameterType, clazz);
    }
  }

  private boolean isAssignable(
      Object parameter,
      @Nullable Class<?> parameterType,
      Class<?> clazz,
      boolean allowPrimitiveWidening) {
    if (allowPrimitiveWidening) {
      return isAssignable(parameter, parameterType, clazz); // use ClassUtils isAssignable
    } else {
      if (parameterType == null) {
        return clazz.isAssignableFrom(parameter.getClass());
      }
      // NOTE: if available, use parameterType for checking assignability, instead of the
      // parameter's actual type
      return clazz.isAssignableFrom(parameterType);
    }
  }

  private void traceParameters(Object[] parameters, @Nullable List<Class<?>> parameterTypes) {
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
            .append(parameterTypes != null ? parameterTypes.get(i).getName() : '?')
            .append('\n');
      }
      logger.trace(stringBuilder.toString());
    }
  }
}
