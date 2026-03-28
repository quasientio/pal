/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.intercept;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.CodeSignature;

/**
 * Unified parameter type extraction utility for the intercept hot-path.
 *
 * <p>This class replaces multiple {@code Arrays.stream(paramTypes).map(Class::getName).toList()}
 * calls in {@link io.quasient.pal.core.execution.java.BaseExecMessageDispatcher} and {@link
 * InterceptChecker} with a single extraction per dispatch that reuses a thread-local {@code
 * String[]} buffer.
 *
 * <p>Key optimizations:
 *
 * <ul>
 *   <li>Thread-local buffer reuse: avoids allocating a new {@code String[]} on every dispatch when
 *       the parameter count is the same
 *   <li>Signature-based caching: since {@code ProceedingJoinPoint.getSignature()} returns the same
 *       {@code Signature} object for repeated calls to the same method, the extracted {@code
 *       String[]} is cached per signature in a {@code ConcurrentHashMap}
 *   <li>Loop-based extraction: avoids Stream/lambda allocation overhead
 * </ul>
 *
 * <p>The extractor distinguishes between:
 *
 * <ul>
 *   <li>Methods/constructors: returns a {@code String[]} of fully-qualified parameter type names
 *       (may be empty for no-arg)
 *   <li>Fields: returns {@code null} (fields have no parameters)
 * </ul>
 */
@SuppressFBWarnings(
    value = "PZLA_PREFER_ZERO_LENGTH_ARRAYS",
    justification =
        "Null return signals field operation (no parameter types) vs empty array for"
            + " no-arg methods/constructors; this distinction is required by InFlightDispatchTracker")
public final class ParamTypeExtractor {

  /** Empty string array constant to avoid allocation for no-arg methods/constructors. */
  private static final String[] EMPTY = new String[0];

  /** Unmodifiable empty list constant for no-arg methods/constructors. */
  private static final List<String> EMPTY_LIST = Collections.emptyList();

  /**
   * Thread-local buffer for reusing {@code String[]} arrays. The buffer is only re-allocated when
   * the input length changes.
   */
  private static final ThreadLocal<String[]> TL_BUFFER = new ThreadLocal<>();

  /**
   * Cache of extracted parameter type names per AspectJ {@code Signature} object. Since the same
   * Signature instance is returned for repeated calls to the same method, this cache eliminates
   * even the single extraction for repeated calls.
   */
  private static final ConcurrentHashMap<Signature, String[]> SIGNATURE_CACHE =
      new ConcurrentHashMap<>();

  /** Private constructor to prevent instantiation. */
  private ParamTypeExtractor() {}

  /**
   * Extracts parameter type names from a {@code Class[]} array using a thread-local reusable
   * buffer.
   *
   * <p>When consecutive calls have the same number of parameters, the same {@code String[]} buffer
   * is reused (its contents are overwritten). This eliminates array allocation on every dispatch.
   *
   * <p>The returned array is <b>not safe to store</b> across calls — it may be overwritten by the
   * next call to this method on the same thread. Callers must consume the result immediately or
   * copy it if needed.
   *
   * @param paramTypes the parameter types to extract names from, or {@code null} for field
   *     operations
   * @return array of fully-qualified type names using {@link Class#getName()}, or {@code null} if
   *     input is {@code null}
   */
  public static String[] extractParamTypes(Class<?>[] paramTypes) {
    if (paramTypes == null) {
      return null;
    }
    int len = paramTypes.length;
    if (len == 0) {
      return EMPTY;
    }
    String[] buffer = TL_BUFFER.get();
    if (buffer == null || buffer.length != len) {
      buffer = new String[len];
      TL_BUFFER.set(buffer);
    }
    for (int i = 0; i < len; i++) {
      buffer[i] = paramTypes[i].getName();
    }
    return buffer;
  }

  /**
   * Extracts parameter type names from an AspectJ {@code Signature}, with signature-based caching.
   *
   * <p>Since {@code ProceedingJoinPoint.getSignature()} returns the same {@code Signature} object
   * for repeated calls to the same method, the extracted {@code String[]} is cached and returned on
   * subsequent calls. This eliminates extraction overhead entirely for repeated calls to the same
   * method.
   *
   * <p>The returned array is cached and <b>must not be modified</b> by callers.
   *
   * @param signature the AspectJ signature to extract parameter types from
   * @return array of fully-qualified type names, or {@code null} for field operations
   */
  public static String[] extractFromSignature(Signature signature) {
    if (!(signature instanceof CodeSignature)) {
      return null;
    }
    return SIGNATURE_CACHE.computeIfAbsent(
        signature,
        sig -> {
          Class<?>[] types = ((CodeSignature) sig).getParameterTypes();
          if (types == null || types.length == 0) {
            return EMPTY;
          }
          // Allocate a dedicated array for the cache (not the TL buffer)
          String[] result = new String[types.length];
          for (int i = 0; i < types.length; i++) {
            result[i] = types[i].getName();
          }
          return result;
        });
  }

  /**
   * Wraps a {@code String[]} of parameter type names as an unmodifiable {@code List<String>}.
   *
   * <p>This provides a zero-copy view of the array for consumers that require {@code List<String>}
   * (such as {@link LocalInterceptCallbackDispatcher} and {@link AroundInterceptChainBuilder}).
   *
   * @param paramTypeNames the parameter type names array, or {@code null} for field operations
   * @return unmodifiable list view, or empty list if input is {@code null} or empty
   */
  public static List<String> asList(String[] paramTypeNames) {
    if (paramTypeNames == null || paramTypeNames.length == 0) {
      return EMPTY_LIST;
    }
    return Collections.unmodifiableList(Arrays.asList(paramTypeNames));
  }

  /**
   * Joins parameter type names with commas, for use in intercept pattern matching.
   *
   * <p>This is equivalent to {@code String.join(",", paramTypeNames)} but avoids the method call
   * overhead and handles {@code null} input.
   *
   * @param paramTypeNames the parameter type names array, or {@code null} for field operations
   * @return comma-separated parameter types string, or {@code null} if input is {@code null}
   */
  public static String joinParamTypes(String[] paramTypeNames) {
    if (paramTypeNames == null) {
      return null;
    }
    if (paramTypeNames.length == 0) {
      return "";
    }
    return String.join(",", paramTypeNames);
  }

  /**
   * Extracts parameter type names from actual argument objects, with fallback to declared types
   * from the signature for {@code null} arguments.
   *
   * <p>This method is used during replay to match actual runtime types against WAL-recorded types.
   * The WAL records the actual runtime types of arguments, so this method mirrors that behavior by
   * using {@code arg.getClass().getName()} for each non-null argument.
   *
   * <p>Unlike {@link #extractFromSignature(Signature)}, this method allocates a fresh array on each
   * call because the result is stored in {@link
   * io.quasient.pal.core.replay.OperationSignature#paramTypes()} and must remain stable.
   *
   * @param args the actual argument values from {@code ProceedingJoinPoint.getArgs()}
   * @param signature the AspectJ signature for fallback on {@code null} arguments
   * @return array of actual runtime type names, or {@code null} for field operations
   */
  public static String[] extractFromArgs(Object[] args, Signature signature) {
    if (!(signature instanceof CodeSignature codeSignature)) {
      return null;
    }
    if (args == null || args.length == 0) {
      return EMPTY;
    }
    Class<?>[] declaredTypes = codeSignature.getParameterTypes();
    String[] result = new String[args.length];
    for (int i = 0; i < args.length; i++) {
      if (declaredTypes != null && i < declaredTypes.length && declaredTypes[i].isPrimitive()) {
        // Use declared type for primitives: args are autoboxed (double → Double) but the WAL
        // records primitive type names (e.g., "double", "int", "boolean").
        result[i] = declaredTypes[i].getName();
      } else if (args[i] != null) {
        // Use actual runtime type for reference parameters to match WAL-recorded types
        // (e.g., String instead of Object for HashMap.put(Object, Object) called with String).
        result[i] = args[i].getClass().getName();
      } else if (declaredTypes != null && i < declaredTypes.length) {
        result[i] = declaredTypes[i].getName();
      } else {
        result[i] = "java.lang.Object";
      }
    }
    return result;
  }

  /**
   * Computes the executable path from class name and executable name.
   *
   * @param className the fully qualified class name
   * @param executableName the method, constructor ("new"), or field name
   * @return the executable path in the form "className.executableName"
   */
  public static String buildExecutablePath(String className, String executableName) {
    return className + "." + executableName;
  }
}
