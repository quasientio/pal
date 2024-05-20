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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

  private final Map<String, Method> matchedMethodsCache = new ConcurrentHashMap<>();
  private final Map<String, Constructor<?>> matchedConstructorsCache = new ConcurrentHashMap<>();

  /**
   * Gets the right method when a parameter is a subtype of a method's formal parameter type. Uses
   * ClassUtils.isAssignable() and Class.isAssignableFrom() to check for assignability.
   *
   * <p>If parameter types are given, method matching will be done using these types. The list of
   * parameter types should be the same length as the parameters list. When parameter types are
   * given, no primitive widening will be allowed, i.e. if a method has a formal parameter of type
   * int, it will not match a call with a parameter of type long.
   *
   * <p>If a parameter type is null, method matching will be done using the parameters' actual
   * types. In this case, primitive widening assignment will be allowed for method matching.
   *
   * @param clazz the class to look up the method in
   * @param parameters the parameters to match
   * @param knownParameterTypes the known parameter types; null if the parameter types are unknown
   * @param methodName the name of the method to look up
   * @return the matching method
   * @throws AmbiguousCallException if more than one method matches the call
   * @throws NoSuchMethodException if no method matches the call
   */
  public Method lookupMethod(
      @Nonnull Class<?> clazz,
      @Nonnull Object[] parameters,
      @Nonnull List<Class<?>> knownParameterTypes,
      @Nonnull String methodName)
      throws AmbiguousCallException, NoSuchMethodException {

    Objects.requireNonNull(clazz, "clazz cannot be null");
    Objects.requireNonNull(parameters, "the list of parameters cannot be null");
    Objects.requireNonNull(knownParameterTypes, "the list of parameter types cannot be null");
    Objects.requireNonNull(methodName, "method name cannot be null");

    if (logger.isTraceEnabled()) {
      logger.trace("in w/ class:{} and method:{}", clazz.getName(), methodName);
    }
    if (knownParameterTypes.size() != parameters.length) {
      throw new IllegalArgumentException(
          String.format(
              "Parameters length=%s, different from parameter types length=%s",
              parameters.length, knownParameterTypes.size()));
    }

    // trace params
    if (logger.isTraceEnabled()) {
      traceParameters(parameters, knownParameterTypes);
    }

    // replace null parameter types with actual parameter types
    final List<Class<?>> parameterTypes = new ArrayList<>();
    for (int i = 0; i < knownParameterTypes.size(); i++) {
      if (knownParameterTypes.get(i) != null) {
        parameterTypes.add(knownParameterTypes.get(i));
      } else {
        if (parameters[i] == null) {
          parameterTypes.add(null);
        } else {
          parameterTypes.add(parameters[i].getClass());
        }
      }
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
                  boolean methodIsVarargs = candidate.isVarArgs();
                  for (int i = 0; i < candidateParameterTypes.length; i++) {
                    boolean paramIsVarargs =
                        methodIsVarargs && i == candidateParameterTypes.length - 1;
                    if (!isAssignable(
                        parameters[i],
                        parameterTypesArray[i],
                        candidateParameterTypes[i],
                        paramIsVarargs)) {
                      return false;
                    }
                  }
                  return true;
                })
            .collect(Collectors.toList());

    if (!matchingMethods.isEmpty()) {
      final Method matchingMethod;
      if (matchingMethods.size() > 1) {
        final List<Method> narrowedDownMatches =
            narrowDownMethodMatches(parameterTypesArray, matchingMethods);
        if (narrowedDownMatches.size() != 1) {
          throw new AmbiguousCallException(
              clazz.getName(), methodName, parameterTypes, matchingMethods);
        } else {
          matchingMethod = narrowedDownMatches.get(0);
        }
      } else {
        matchingMethod = matchingMethods.get(0);
      }
      cache(clazz, methodName, parameterTypes, matchingMethod);
      if (logger.isDebugEnabled()) {
        logger.debug("Got method with signature in step2: {}", matchingMethod.toGenericString());
      }
      return matchingMethod;
    }

    // now scan other (i.e. non-public) methods
    if (allowNonPublic) {
      matchingMethods =
          Arrays.stream(clazz.getDeclaredMethods())
              .filter(m -> methodName.equals(m.getName()))
              .filter(
                  m -> !Modifier.isPublic(m.getModifiers())) // we already checked the public ones
              .filter(m -> m.getParameterTypes().length == parameters.length)
              .filter(
                  candidate -> {
                    boolean methodIsVarargs = candidate.isVarArgs();
                    final Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
                    for (int i = 0; i < candidateParameterTypes.length; i++) {
                      boolean paramIsVarargs =
                          methodIsVarargs && i == candidateParameterTypes.length - 1;
                      if (!isAssignable(
                          parameters[i],
                          parameterTypesArray[i],
                          candidateParameterTypes[i],
                          paramIsVarargs)) {
                        return false;
                      }
                    }
                    return true;
                  })
              .collect(Collectors.toList());

      if (!matchingMethods.isEmpty()) {
        final Method matchingMethod;
        if (matchingMethods.size() > 1) {
          final List<Method> narrowedDownMatches =
              narrowDownMethodMatches(parameterTypesArray, matchingMethods);
          if (narrowedDownMatches.size() != 1) {
            throw new AmbiguousCallException(
                clazz.getName(), methodName, parameterTypes, matchingMethods);
          } else {
            matchingMethod = narrowedDownMatches.get(0);
          }
        } else {
          matchingMethod = matchingMethods.get(0);
        }
        cache(clazz, methodName, parameterTypes, matchingMethod);
        if (logger.isDebugEnabled()) {
          logger.debug("Got method with signature in step3: {}", matchingMethod.toGenericString());
        }
        return matchingMethod;
      }
    }
    throw new NoSuchMethodException(
        String.format(
            "No matching method '%s' found in class '%s' with parameter types: [%s]",
            methodName,
            clazz.getName(),
            parameterTypes.stream().map(Class::getName).collect(Collectors.joining(", "))));
  }

  /**
   * Gets the right constructor when a parameter is a subtype of a constructor's formal parameter
   * type. Uses ClassUtils.isAssignable() and Class.isAssignableFrom() to check for assignability.
   *
   * <p>If parameter types are given, constructor matching will be done using these types. The list
   * of parameterTypes should be the same length as the parameters list. Also, when parameter types
   * are given, no primitive widening will be allowed, i.e. if a constructor has a formal parameter
   * of type int, it will not match a call with a parameter of type long.
   *
   * <p>If a parameter type is null, constructor matching is done using the parameters' actual type.
   * In this case, primitive widening assignment will be allowed for constructor matching.
   *
   * @param clazz the class to look up the constructor in
   * @param parameters the parameters to match
   * @param knownParameterTypes the known parameter types; null if the parameter types are unknown
   * @return the matching constructor
   * @throws AmbiguousCallException if more than one constructor matches the call
   * @throws NoSuchMethodException if no constructor matches the call
   */
  public Constructor<?> lookupConstructor(
      @Nonnull Class<?> clazz,
      @Nonnull Object[] parameters,
      @Nonnull List<Class<?>> knownParameterTypes)
      throws AmbiguousCallException, NoSuchMethodException {

    Objects.requireNonNull(clazz, "clazz cannot be null");
    Objects.requireNonNull(parameters, "the list of parameters cannot be null");
    Objects.requireNonNull(knownParameterTypes, "the list of parameter types cannot be null");

    if (logger.isTraceEnabled()) {
      logger.trace("in w/ class:{} and parameters", clazz.getName());
    }

    if (knownParameterTypes.size() != parameters.length) {
      throw new IllegalArgumentException(
          String.format(
              "Parameters length=%s, different from parameter types length=%s",
              parameters.length, knownParameterTypes.size()));
    }

    // trace params
    if (logger.isTraceEnabled()) {
      traceParameters(parameters, knownParameterTypes);
    }

    // replace null parameter types with actual parameter types
    final List<Class<?>> parameterTypes = new ArrayList<>();
    for (int i = 0; i < knownParameterTypes.size(); i++) {
      if (knownParameterTypes.get(i) != null) {
        parameterTypes.add(knownParameterTypes.get(i));
      } else {
        if (parameters[i] == null) {
          throw new IllegalArgumentException(
              "Cannot determine parameter type for null parameter at index " + i);
        }
        parameterTypes.add(parameters[i].getClass());
      }
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
                  boolean methodIsVarargs = candidate.isVarArgs();
                  final Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
                  for (int i = 0; i < candidateParameterTypes.length; i++) {
                    boolean paramIsVarargs =
                        methodIsVarargs && i == candidateParameterTypes.length - 1;
                    if (!isAssignable(
                        parameters[i],
                        parameterTypesArray[i],
                        candidateParameterTypes[i],
                        paramIsVarargs)) {
                      return false;
                    }
                  }
                  return true;
                })
            .collect(Collectors.toList());

    if (!matchingConstructors.isEmpty()) {
      final Constructor<?> matchingConstructor;
      if (matchingConstructors.size() > 1) {
        final List<Constructor<?>> narrowedDownMatches =
            narrowDownConstructorMatches(parameterTypesArray, matchingConstructors);
        if (narrowedDownMatches.size() != 1) {
          throw new AmbiguousCallException(clazz.getName(), parameterTypes, matchingConstructors);
        } else {
          matchingConstructor = narrowedDownMatches.get(0);
        }
      } else {
        matchingConstructor = matchingConstructors.get(0);
      }
      cache(clazz, null, parameterTypes, matchingConstructor);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Got constructor with signature in step2: {}", matchingConstructor.toGenericString());
      }
      return matchingConstructor;
    }

    // now scan other (i.e. non-public) constructors
    if (allowNonPublic) {
      matchingConstructors =
          Arrays.stream(clazz.getDeclaredConstructors())
              .filter(
                  m -> !Modifier.isPublic(m.getModifiers())) // we already checked the public ones
              .filter(m -> m.getParameterTypes().length == parameters.length)
              .filter(
                  candidate -> {
                    boolean methodIsVarargs = candidate.isVarArgs();
                    final Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
                    for (int i = 0; i < candidateParameterTypes.length; i++) {
                      boolean paramIsVarargs =
                          methodIsVarargs && i == candidateParameterTypes.length - 1;
                      if (!isAssignable(
                          parameters[i],
                          parameterTypesArray[i],
                          candidateParameterTypes[i],
                          paramIsVarargs)) {
                        return false;
                      }
                    }
                    return true;
                  })
              .collect(Collectors.toList());

      if (!matchingConstructors.isEmpty()) {
        final Constructor<?> matchingConstructor;
        if (matchingConstructors.size() > 1) {
          final List<Constructor<?>> narrowedDownMatches =
              narrowDownConstructorMatches(parameterTypesArray, matchingConstructors);
          if (narrowedDownMatches.size() != 1) {
            throw new AmbiguousCallException(clazz.getName(), parameterTypes, matchingConstructors);
          } else {
            matchingConstructor = narrowedDownMatches.get(0);
          }
        } else {
          matchingConstructor = matchingConstructors.get(0);
        }
        cache(clazz, null, parameterTypes, matchingConstructor);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "Got constructor with signature in step3: {}", matchingConstructor.toGenericString());
        }
        return matchingConstructor;
      }
    }
    throw new NoSuchMethodException(
        String.format(
            "No matching constructor found in class '%s' with parameter types: [%s]",
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

  private boolean isAssignable(
      Object parameter, @Nullable Class<?> parameterType, Class<?> clazz, boolean paramIsVarargs) {
    if (parameter == null) {
      return !clazz.isPrimitive();
    }

    // if available, use parameterType for checking assignability,
    // else use the parameter's actual type
    final Class<?> paramType = parameterType != null ? parameterType : parameter.getClass();

    if (paramIsVarargs) {
      if (paramType.isArray()) {
        // if the parameter type is an array, we need to check against its component type
        return isAssignable(
            parameter, paramType.getComponentType(), clazz.getComponentType(), false);
      } else {
        return isAssignable(parameter, paramType, clazz.getComponentType(), false);
      }
    }

    return ClassUtils.isAssignable(paramType, clazz);
  }

  @SuppressWarnings("unused")
  private boolean isAssignable_notInUse(
      Object parameter,
      @Nullable Class<?> parameterType,
      Class<?> clazz,
      boolean methodIsVarargs,
      boolean allowPrimitiveWidening) {
    if (allowPrimitiveWidening) {
      return isAssignable(
          parameter, parameterType, clazz, methodIsVarargs); // use ClassUtils isAssignable
    } else {
      if (parameterType == null) {
        return clazz.isAssignableFrom(parameter.getClass());
      }
      // NOTE: if available, use parameterType for checking assignability, instead of the
      // parameter's actual type
      return clazz.isAssignableFrom(parameterType);
    }
  }

  /**
   * Narrows down the list of matching constructors to those that have equal types for all the
   * parameters that are not primitive or wrapper types. This helps to solve ambiguity when there
   * are multiple matches only due to primitive widening/auto-boxing.
   *
   * @param parameterTypes the parameter types to match
   * @param matchingConstructors the list of matching constructors
   * @return the narrowed down list of matching constructors
   */
  private List<Constructor<?>> narrowDownConstructorMatches(
      Class<?>[] parameterTypes, List<Constructor<?>> matchingConstructors) {
    List<Constructor<?>> exactMatches = new ArrayList<>();
    for (Constructor<?> constructor : matchingConstructors) {
      Class<?>[] candidateTypes = constructor.getParameterTypes();
      boolean isExactMatch = true;
      for (int i = 0; i < parameterTypes.length; i++) {
        if (!ClassUtils.isPrimitiveOrWrapper(parameterTypes[i])) {
          if (!candidateTypes[i].equals(parameterTypes[i])) {
            isExactMatch = false;
            break;
          }
        }
      }
      if (isExactMatch) {
        exactMatches.add(constructor);
      }
    }
    return exactMatches;
  }

  /**
   * Narrows down the list of matching methods to those that have equal types for all the parameters
   * that are not primitive or wrapper types. This helps to solve ambiguity when there are multiple
   * matches only due to primitive widening/auto-boxing.
   *
   * @param parameterTypes the parameter types to match
   * @param matchingMethods the list of matching methods
   * @return the narrowed down list of matching methods
   */
  private List<Method> narrowDownMethodMatches(
      Class<?>[] parameterTypes, List<Method> matchingMethods) {
    List<Method> exactMatches = new ArrayList<>();
    for (Method method : matchingMethods) {
      Class<?>[] candidateTypes = method.getParameterTypes();
      boolean isExactMatch = true;
      for (int i = 0; i < parameterTypes.length; i++) {
        if (!ClassUtils.isPrimitiveOrWrapper(parameterTypes[i])) {
          if (!candidateTypes[i].equals(parameterTypes[i])) {
            isExactMatch = false;
            break;
          }
        }
      }
      if (isExactMatch) {
        exactMatches.add(method);
      }
    }
    return exactMatches;
  }

  private void traceParameters(Object[] parameters, @Nullable List<Class<?>> parameterTypes) {
    if (parameters.length == 0) {
      logger.trace("params of length=0");
    } else {
      final StringBuilder stringBuilder = new StringBuilder();
      for (int i = 0; i < parameters.length; i++) {
        String parameterType = "?";
        if (parameterTypes != null) {
          if (parameterTypes.get(i) != null) {
            parameterType = parameterTypes.get(i).getName();
          }
        }
        stringBuilder
            .append("params[")
            .append(i)
            .append("]=")
            .append(parameters[i])
            .append(" type:")
            .append(parameterType)
            .append('\n');
      }
      logger.trace(stringBuilder.toString());
    }
  }
}
