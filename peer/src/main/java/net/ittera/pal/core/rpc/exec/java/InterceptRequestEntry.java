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

package net.ittera.pal.core.rpc.exec.java;

import static java.lang.String.format;

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import java.util.Objects;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.colfer.InterceptableMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates the details of an intercepted request entry for a class member.
 *
 * <p>This class builds a matching pattern from a given intercept message that targets either a
 * method or a field. For method entries, it records parameter types and the number of parameters to
 * aid in precise matching of method invocations.
 */
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
   * Matcher for comparing dot-separated paths, configured to trim tokens and ignore case. Caution:
   * This instance is not thread-safe.
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
    // create executable pattern to match
    this.pattern =
        format(
            "%s.%s",
            interceptMessage.getClazz(),
            isMethod ? interceptableMethod.getName() : interceptMessage.getField().getName());
    // add param info
    if (isMethod) {
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
    // TODO it won't be this simple; we'll need to take extends and impl into account
    if (paramTypes == null) {
      return parameterTypes == null;
    }

    return Objects.equals(paramTypes, String.join(",", parameterTypes));
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
        + interceptMessage
        + '}';
  }
}
