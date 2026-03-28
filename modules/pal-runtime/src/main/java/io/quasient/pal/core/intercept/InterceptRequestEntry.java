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

import static java.lang.String.format;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.azagniotov.matcher.AntPathMatcherArrays;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.InterceptableMethod;
import io.quasient.pal.serdes.colfer.ColferUtils;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates the details of an intercepted request entry for a class member.
 *
 * <p>This class builds a matching pattern from a given intercept message that targets either a
 * method or a field. For method entries, it records parameter types and the number of parameters to
 * aid in precise matching of method invocations.
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Entry wrapper - intercept message intentionally shared")
public class InterceptRequestEntry {
  /** Logger for outputting debug and trace messages related to interception matching. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptRequestEntry.class);

  /**
   * Composite pattern in the form "className.executableName" used to match interception requests.
   */
  private final String pattern;

  /**
   * Comma-separated list of parameter types for the intercepted method; null when the intercepted
   * element is a field.
   */
  private final String paramTypes;

  /** The number of parameters for the intercepted method; zero for field interceptions. */
  private final int numberOfParams;

  /** Indicates whether the intercepted element is a method (true) or a field (false). */
  private final boolean isMethod;

  /** The intercept message containing details about the intercepted class member. */
  private final InterceptMessage interceptMessage;

  /**
   * Matcher for comparing dot-separated paths, configured to trim tokens and ignore case.
   *
   * <p><b>Thread safety:</b> This shared static instance is safe for concurrent use. {@code
   * AntPathMatcherArrays} (v1.0.0) is an immutable, effectively thread-safe class: all instance
   * fields are {@code final}, there is no mutable state (no caches, buffers, or counters), and
   * {@code isMatch()} is a pure function that operates only on method-local {@code char[]} arrays
   * and final fields. See {@link io.quasient.pal.core.intercept.AntPathMatcherThreadSafetyTest} for
   * empirical verification.
   */
  private static final AntPathMatcherArrays matcher =
      new AntPathMatcherArrays.Builder()
          .withPathSeparator('.')
          .withTrimTokens()
          .withIgnoreCase()
          .build();

  /**
   * Constructs an interception request entry from the specified intercept message.
   *
   * <p>This constructor extracts the target class and either the method or field name from the
   * intercept message to build a composite pattern. If the intercepted element is a method, it also
   * processes the parameter types and counts the parameters.
   *
   * @param interceptMessage the intercept message containing details of the class member to
   *     intercept
   */
  public InterceptRequestEntry(InterceptMessage interceptMessage) {
    InterceptableMethod interceptableMethod = interceptMessage.getMethod();
    this.isMethod = interceptableMethod != null;
    final String elementName =
        (interceptableMethod != null)
            ? interceptableMethod.getName()
            : interceptMessage.getField().getName();
    // create executable pattern to match
    this.pattern = format("%s.%s", interceptMessage.getClazz(), elementName);
    // add param info
    if (interceptableMethod != null) {
      String[] parameterTypes = interceptableMethod.getParameterTypes();
      this.numberOfParams = parameterTypes.length;
      if (numberOfParams > 0) {
        this.paramTypes = String.join(",", parameterTypes);
      } else {
        this.paramTypes = "";
      }
    } else {
      numberOfParams = 0;
      paramTypes = null;
    }
    this.interceptMessage = interceptMessage;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InterceptRequestEntry that = (InterceptRequestEntry) o;
    return numberOfParams == that.numberOfParams
        && isMethod == that.isMethod
        && pattern.equals(that.pattern)
        && paramTypes.equals(that.paramTypes)
        && interceptMessage.equals(that.interceptMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pattern, paramTypes, numberOfParams, isMethod, interceptMessage);
  }

  /**
   * Determines whether the provided class and executable (method/field) match this interception
   * entry.
   *
   * <p>The matching process composes an executable path in the form "classname.executableName" and
   * compares it against the stored pattern using a case-insensitive dot-separated matcher. For
   * method entries, it further compares the comma-separated parameter types.
   *
   * @param classname the fully qualified name of the class to match
   * @param executableName the name of the method or field to match
   * @param parameterTypes an array of parameter types if matching a method; may be null when
   *     matching a field
   * @return true if both the executable path and the parameter types (when applicable) match the
   *     stored entry; false otherwise
   */
  public boolean matches(String classname, String executableName, String[] parameterTypes) {
    // use matcher on pattern
    final String executablePath = format("%s.%s", classname, executableName);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Matching entry pattern '{}' against execMessage pattern '{}'", pattern, executablePath);
    }
    if (!matcher.isMatch(pattern, executablePath)) {
      return false;
    }

    // match parameter types
    if (paramTypes == null) {
      return parameterTypes == null;
    }

    // empty paramTypes on a method entry means "match any parameter list" (wildcard),
    // but should not match field access (null parameterTypes)
    if (isMethod && paramTypes.isEmpty()) {
      return parameterTypes != null;
    }

    return Objects.equals(paramTypes, String.join(",", parameterTypes));
  }

  /**
   * Determines whether the provided pre-computed executable path and joined parameter types match
   * this interception entry.
   *
   * <p>This is an optimized overload of {@link #matches(String, String, String[])} that accepts
   * pre-computed strings instead of computing them on every call. This reduces allocations from N*2
   * (where N = registered intercepts) to 2 total per intercept check, since the caller pre-computes
   * the "className.executableName" and "param1,param2" strings once and passes them to each entry.
   *
   * @param executablePath the pre-computed executable path in the form "className.executableName"
   * @param joinedParamTypes the pre-computed comma-separated parameter types, or null for field
   *     interceptions
   * @return true if both the executable path and the parameter types match the stored entry; false
   *     otherwise
   */
  public boolean matches(String executablePath, String joinedParamTypes) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Matching entry pattern '{}' against execMessage pattern '{}'", pattern, executablePath);
    }
    if (!matcher.isMatch(pattern, executablePath)) {
      return false;
    }
    if (paramTypes == null) {
      return joinedParamTypes == null;
    }
    // empty paramTypes on a method entry means "match any parameter list" (wildcard),
    // but should not match field access (null joinedParamTypes)
    if (isMethod && paramTypes.isEmpty()) {
      return joinedParamTypes != null;
    }
    return Objects.equals(paramTypes, joinedParamTypes);
  }

  /**
   * Returns the execution priority from the underlying intercept message.
   *
   * @return the priority value; lower values execute first
   */
  public int getPriority() {
    return interceptMessage.getPriority();
  }

  /**
   * Returns the TTL in seconds from the underlying intercept message.
   *
   * @return the TTL in seconds; zero means no TTL
   */
  public long getTtlSeconds() {
    return interceptMessage.getTtlSeconds();
  }

  /**
   * Returns the callback timeout in milliseconds from the underlying intercept message.
   *
   * <p>A value of {@code -1} means defer to the global peer configuration. {@code 0} means no
   * timeout (infinite wait). Positive values specify a per-intercept timeout in milliseconds.
   *
   * @return the callback timeout in milliseconds
   */
  public long getCallbackTimeoutMs() {
    return interceptMessage.getCallbackTimeoutMs();
  }

  /**
   * Retrieves the intercept message associated with this interception entry.
   *
   * @return the intercept message detailing the intercepted class member
   */
  public InterceptMessage getInterceptMessage() {
    return interceptMessage;
  }

  @Override
  public String toString() {
    return "InterceptRequestEntry{"
        + "pattern='"
        + pattern
        + '\''
        + ", paramTypes='"
        + paramTypes
        + '\''
        + ", numberOfParams="
        + numberOfParams
        + ", isMethod="
        + isMethod
        + ", interceptMessage="
        + ColferUtils.toJson(interceptMessage)
        + '}';
  }
}
