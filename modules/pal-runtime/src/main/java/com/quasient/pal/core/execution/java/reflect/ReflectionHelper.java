/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java.reflect;

import com.quasient.pal.core.execution.java.AmbiguousCallException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to facilitate reflective lookup of methods and constructors.
 *
 * <p>This helper provides functionality to locate the appropriate method or constructor based on
 * provided parameters and known parameter types, including handling subtype relationships and
 * primitive widening where applicable. It also supports caching of lookup results and can
 * optionally allow access to non-public members.
 *
 * <p>NOTE: lookupMethod will not currently find inherited methods if they are not public.
 */
@Singleton
@SuppressFBWarnings(
    value = "UPM_UNCALLED_PRIVATE_METHOD",
    justification = "Unused method kept for future use")
public class ReflectionHelper {
  /** Logger for internal diagnostics and tracing of reflection operations. */
  private static final Logger logger = LoggerFactory.getLogger(ReflectionHelper.class);

  /**
   * Default flag indicating whether non-public methods and constructors are allowed in lookup
   * operations.
   */
  private static final boolean ALLOW_NON_PUBLIC_DEFAULT = false;

  /**
   * Flag that determines if non-public constructors and methods should be considered during lookup.
   */
  private final boolean allowNonPublic;

  /** Cache to store resolved methods keyed by unique signatures. */
  private final Map<String, Method> matchedMethodsCache = new ConcurrentHashMap<>();

  /** Cache to store resolved constructors keyed by unique signatures. */
  private final Map<String, Constructor<?>> matchedConstructorsCache = new ConcurrentHashMap<>();

  /**
   * Constructs a ReflectionHelper using the default configuration.
   *
   * <p>Non-public member lookup is configured based on a predetermined default flag.
   */
  public ReflectionHelper() {
    this(ALLOW_NON_PUBLIC_DEFAULT);
  }

  /**
   * Constructs a ReflectionHelper with a specified configuration.
   *
   * @param allowNonPublic if true, allows lookup of non-public methods and constructors; otherwise,
   *     only public members are considered.
   */
  public ReflectionHelper(boolean allowNonPublic) {
    this.allowNonPublic = allowNonPublic;
  }

  /**
   * Constructs a ReflectionHelper with a configuration provided via dependency injection.
   *
   * <p>The provided string is parsed as a boolean to determine if non-public member lookup is
   * permitted.
   *
   * @param rpcAllowNonpublicStr a string representing a boolean value ("true" or "false") to enable
   *     non-public member access.
   */
  @Inject
  public ReflectionHelper(@Named("rpc.allow_nonpublic") String rpcAllowNonpublicStr) {
    this.allowNonPublic = Boolean.parseBoolean(rpcAllowNonpublicStr);
  }

  /**
   * Locates and returns a method in the specified class that best matches the provided method name
   * and parameters.
   *
   * <p>The resolution process applies assignability rules to check for both exact and subtype
   * matches of method parameters. It first attempts a direct lookup using the provided known
   * parameter types. If no exact match is found, it scans public and optionally non-public methods
   * to find assignable candidates. In ambiguous cases, the best candidate is chosen by narrowing
   * matches based on exact type equivalence for non-primitive and non-wrapper types.
   *
   * <p>Matching results are cached for improved performance on subsequent calls.
   *
   * @param clazz the class in which the method is to be located; must be non-null.
   * @param parameters the array of runtime parameters to match against the method signature; must
   *     be non-null.
   * @param knownParameterTypes list of known parameter types corresponding to the parameters; must
   *     be non-null and same length as parameters. A null entry indicates that the corresponding
   *     parameter's runtime type should be used.
   * @param methodName the name of the method to locate; must be non-null.
   * @return the method that best matches the provided parameters.
   * @throws AmbiguousCallException if multiple methods equally match the call.
   * @throws NoSuchMethodException if no matching method is found.
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
      logger.trace("in lookupMethod w/ class:{} and method: {}", clazz.getName(), methodName);
    }
    // replace null parameter types with actual parameter types
    final List<Class<?>> parameterTypes = resolveParameterTypes(parameters, knownParameterTypes);

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
    } catch (NoSuchMethodException | SecurityException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Could not find method the easy way - {}", e.getMessage());
      }
    }

    // scan public methods that are assignable from the parameters
    List<Method> matchingMethods =
        collectMatchingMethods(
            Arrays.stream(clazz.getMethods()), parameters, parameterTypesArray, methodName);

    Method methodPicked =
        pickMethodOrThrow(clazz, methodName, parameterTypes, parameterTypesArray, matchingMethods);
    if (methodPicked != null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Got method with signature in step2: {}", methodPicked.toGenericString());
      }
      return methodPicked;
    }

    // now scan other (i.e. non-public) methods
    if (allowNonPublic) {
      matchingMethods =
          collectMatchingMethods(
              Arrays.stream(clazz.getDeclaredMethods())
                  .filter(
                      m ->
                          !Modifier.isPublic(
                              m.getModifiers())), // we already checked the public ones
              parameters,
              parameterTypesArray,
              methodName);

      methodPicked =
          pickMethodOrThrow(
              clazz, methodName, parameterTypes, parameterTypesArray, matchingMethods);
      if (methodPicked != null) {
        if (logger.isDebugEnabled()) {
          logger.debug("Got method with signature in step3: {}", methodPicked.toGenericString());
        }
        return methodPicked;
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
   * Locates and returns a constructor in the specified class that best matches the provided
   * parameters.
   *
   * <p>The resolution process applies assignability rules similar to method lookup. It first
   * attempts an exact match using the known parameter types. If no exact public constructor is
   * found, it scans for assignable candidates among public and optionally non-public constructors,
   * narrowing down ambiguous matches based on type equivalence for non-primitive and non-wrapper
   * types.
   *
   * <p>Matching results are cached for improved performance on subsequent calls.
   *
   * @param clazz the class in which the constructor is to be located; must be non-null.
   * @param parameters the array of runtime parameters to match against the constructor signature;
   *     must be non-null.
   * @param knownParameterTypes list of known parameter types corresponding to the parameters; must
   *     be non-null and same length as parameters. A null entry indicates that the parameter's
   *     actual runtime type should be used.
   * @return the constructor that best matches the provided parameters.
   * @throws AmbiguousCallException if multiple constructors equally match the call.
   * @throws NoSuchMethodException if no matching constructor is found.
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
      logger.trace("in lookupConstructor w/ class: {}", clazz.getName());
    }

    // replace null parameter types with actual parameter types
    final List<Class<?>> parameterTypes = resolveParameterTypes(parameters, knownParameterTypes);

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
    } catch (NoSuchMethodException | SecurityException e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Could not find constructor the easy way - {}", e.getMessage());
      }
    }

    // scan public constructors that are assignable from the parameters
    List<Constructor<?>> matchingConstructors =
        collectMatchingConstructors(
            Arrays.stream(clazz.getConstructors()), parameters, parameterTypesArray);

    Constructor<?> picked =
        pickConstructorOrThrow(clazz, parameterTypes, parameterTypesArray, matchingConstructors);
    if (picked != null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Got constructor with signature in step2: {}", picked.toGenericString());
      }
      return picked;
    }

    // now scan other (i.e. non-public) constructors
    if (allowNonPublic) {
      matchingConstructors =
          collectMatchingConstructors(
              Arrays.stream(clazz.getDeclaredConstructors())
                  .filter(
                      m ->
                          !Modifier.isPublic(
                              m.getModifiers())), // we already checked the public ones
              parameters,
              parameterTypesArray);

      picked =
          pickConstructorOrThrow(clazz, parameterTypes, parameterTypesArray, matchingConstructors);
      if (picked != null) {
        if (logger.isDebugEnabled()) {
          logger.debug("Got constructor with signature in step3: {}", picked.toGenericString());
        }
        return picked;
      }
    }
    throw new NoSuchMethodException(
        String.format(
            "No matching constructor found in class '%s' with parameter types: [%s]",
            clazz.getName(),
            parameterTypes.stream().map(Class::getName).collect(Collectors.joining(", "))));
  }

  /**
   * Caches the resolved member (method or constructor) using a composite key based on class, member
   * name, and parameter types.
   *
   * <p>This caching optimizes lookup by reusing previously resolved members.
   *
   * @param clazz the class in which the member is defined.
   * @param memberName the name of the member; may be null for constructors.
   * @param parameterTypes the list of parameter types used in the lookup.
   * @param member the member (method or constructor) that was resolved.
   */
  private void cache(
      Class<?> clazz, String memberName, List<Class<?>> parameterTypes, Member member) {
    String key = buildKey(clazz, memberName, parameterTypes);
    if (member instanceof Method method) {
      matchedMethodsCache.put(key, method);
    } else if (member instanceof Constructor<?> ctor) {
      matchedConstructorsCache.put(key, ctor);
    }
  }

  /**
   * Resolves the effective parameter types by validating length equality and replacing null entries
   * in the provided list of known parameter types with the corresponding runtime types from the
   * parameters array.
   *
   * <p>If a parameter type is unknown (null in {@code knownParameterTypes}) and the corresponding
   * runtime parameter is also null, this method cannot infer the type and throws an {@link
   * IllegalArgumentException}.
   *
   * @param parameters the runtime parameter values; must be non-null
   * @param knownParameterTypes the list of known parameter types aligned with {@code parameters};
   *     must be non-null. Null entries indicate the type should be inferred from the runtime value.
   * @return a list containing the resolved parameter types for the invocation
   * @throws IllegalArgumentException if the number of parameters and known parameter types differ,
   *     or if a null parameter is encountered where the type must be inferred
   */
  private List<Class<?>> resolveParameterTypes(
      Object[] parameters, List<Class<?>> knownParameterTypes) {
    if (knownParameterTypes.size() != parameters.length) {
      throw new IllegalArgumentException(
          String.format(
              "Parameters length=%s, different from parameter types length=%s",
              parameters.length, knownParameterTypes.size()));
    }

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
    return parameterTypes;
  }

  /**
   * Retrieves a cached member matching the specified class, member name, and parameter types.
   *
   * @param clazz the class in which the lookup was performed.
   * @param memberName the name of the member, or null for constructors.
   * @param parameterTypes the list of parameter types used in the lookup.
   * @param memberType the expected type of the member (Method or Constructor).
   * @return the cached member if found, or null otherwise.
   */
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

  /**
   * Generates a unique key for caching based on the class loader, class name, member name, and
   * parameter types.
   *
   * @param clazz the class for which the key is generated.
   * @param memberName the name of the member; may be null for constructors.
   * @param parameterTypes the list of parameter types used in lookup.
   * @return a string that uniquely identifies the member lookup signature.
   */
  private String buildKey(
      Class<?> clazz, @Nullable String memberName, List<Class<?>> parameterTypes) {
    StringBuilder keyBuilder = new StringBuilder(memberName == null ? "" : memberName);
    ClassLoader cl = clazz.getClassLoader();
    keyBuilder.append(cl == null ? "bootstrapCL" : cl.toString());
    keyBuilder.append(clazz.getName());
    parameterTypes.stream().map(Class::getName).forEach(keyBuilder::append);
    return keyBuilder.toString();
  }

  /**
   * Determines whether the provided parameter can be assigned to the candidate type.
   *
   * <p>This method takes into account varargs, using the component types of arrays when needed. If
   * the parameter is null, it is considered assignable only if the candidate type is not primitive.
   *
   * @param parameter the parameter object; may be null.
   * @param parameterType the expected type for the parameter; if null, the actual type of the
   *     parameter is used.
   * @param clazz the candidate type to check assignability against.
   * @param paramIsVarargs flag indicating if the parameter corresponds to a varargs position.
   * @return true if the parameter is assignable to the candidate type; false otherwise.
   */
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

  /**
   * Collects constructors whose parameter list is assignable from the provided runtime parameters.
   *
   * @param constructors stream of constructors to evaluate
   * @param parameters runtime parameter values to match
   * @param parameterTypesArray array of expected parameter types aligned with parameters
   * @return list of constructors assignable from the provided parameters
   */
  private List<Constructor<?>> collectMatchingConstructors(
      Stream<Constructor<?>> constructors, Object[] parameters, Class<?>[] parameterTypesArray) {
    return constructors
        .filter(constructor -> constructor.getParameterTypes().length == parameters.length)
        .filter(
            candidate -> {
              boolean methodIsVarargs = candidate.isVarArgs();
              final Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
              for (int i = 0; i < candidateParameterTypes.length; i++) {
                boolean paramIsVarargs = methodIsVarargs && i == candidateParameterTypes.length - 1;
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
  }

  /**
   * Picks a single matching constructor or throws if ambiguity persists, and caches the result.
   *
   * @param clazz target class being inspected
   * @param parameterTypes list of parameter types used for lookup and caching
   * @param parameterTypesArray array form of parameter types used for narrowing matches
   * @param matchingConstructors list of candidate constructors after initial filtering
   * @return the selected constructor, or {@code null} if no candidates are present
   * @throws AmbiguousCallException if multiple constructors remain after narrowing
   */
  @Nullable
  private Constructor<?> pickConstructorOrThrow(
      Class<?> clazz,
      List<Class<?>> parameterTypes,
      Class<?>[] parameterTypesArray,
      List<Constructor<?>> matchingConstructors)
      throws AmbiguousCallException {
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
      return matchingConstructor;
    }
    return null;
  }

  /**
   * Collects methods with the given name whose parameter list is assignable from the provided
   * runtime parameters.
   *
   * @param methods stream of methods to evaluate
   * @param parameters runtime parameter values to match
   * @param parameterTypesArray array of expected parameter types aligned with parameters
   * @param methodName the method name to match
   * @return list of methods assignable from the provided parameters and name
   */
  private List<Method> collectMatchingMethods(
      Stream<Method> methods,
      Object[] parameters,
      Class<?>[] parameterTypesArray,
      String methodName) {
    return methods
        .filter(m -> methodName.equals(m.getName()))
        .filter(m -> m.getParameterTypes().length == parameters.length)
        .filter(
            candidate -> {
              final Class<?>[] candidateParameterTypes = candidate.getParameterTypes();
              boolean methodIsVarargs = candidate.isVarArgs();
              for (int i = 0; i < candidateParameterTypes.length; i++) {
                boolean paramIsVarargs = methodIsVarargs && i == candidateParameterTypes.length - 1;
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
  }

  /**
   * Picks a single matching method or throws if ambiguity persists, and caches the result.
   *
   * @param clazz target class being inspected
   * @param methodName the method name used for lookup and error reporting
   * @param parameterTypes list of parameter types used for lookup and caching
   * @param parameterTypesArray array form of parameter types used for narrowing matches
   * @param matchingMethods list of candidate methods after initial filtering
   * @return the selected method, or {@code null} if no candidates are present
   * @throws AmbiguousCallException if multiple methods remain after narrowing
   */
  @Nullable
  private Method pickMethodOrThrow(
      Class<?> clazz,
      String methodName,
      List<Class<?>> parameterTypes,
      Class<?>[] parameterTypesArray,
      List<Method> matchingMethods)
      throws AmbiguousCallException {
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
      return matchingMethod;
    }
    return null;
  }

  /**
   * (Currently unused) Checks assignability of a parameter to a candidate type with optional
   * primitive widening.
   *
   * <p>When allowPrimitiveWidening is true, the method delegates to the standard assignability
   * check; otherwise, it directly uses Class.isAssignableFrom or the provided parameter type.
   *
   * @param parameter the parameter object; must not be null if its type is undetermined.
   * @param parameterType the declared type of the parameter; may be null to indicate that the
   *     actual runtime type should be used.
   * @param clazz the candidate type to check assignability against.
   * @param methodIsVarargs flag indicating if the parameter is part of a varargs array.
   * @param allowPrimitiveWidening flag indicating if primitive widening should be considered.
   * @return true if the parameter is assignable to the candidate type with respect to the provided
   *     rules; false otherwise.
   */
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
   * Narrows down the list of matching constructors to those whose non-primitive (or non-wrapper)
   * parameter types exactly match the provided parameter types.
   *
   * <p>This approach assists in resolving ambiguities where multiple constructors are candidates
   * due to differences in primitive widening or auto-boxing conversions.
   *
   * @param parameterTypes the array of parameter types to exactly match.
   * @param matchingConstructors the list of candidate constructors that passed initial
   *     assignability checks.
   * @return a list of constructors that exactly match the non-primitive and non-wrapper types.
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
   * Narrows down the list of matching methods to those whose non-primitive (or non-wrapper)
   * parameter types exactly match the provided parameter types.
   *
   * <p>This assists in resolving ambiguities where multiple methods are candidates due to primitive
   * widening or auto-boxing.
   *
   * @param parameterTypes the array of parameter types to exactly match.
   * @param matchingMethods the list of candidate methods that passed initial assignability checks.
   * @return a list of methods that exactly match the non-primitive and non-wrapper types.
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
}
