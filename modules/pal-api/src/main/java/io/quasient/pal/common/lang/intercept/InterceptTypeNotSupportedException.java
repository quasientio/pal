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
package io.quasient.pal.common.lang.intercept;

/**
 * Exception thrown when an operation is invoked that is not supported for the current {@link
 * InterceptType}.
 *
 * <p>Different intercept types support different operations on {@link InterceptContext}. For
 * example:
 *
 * <ul>
 *   <li>{@link InterceptType#BEFORE} intercepts cannot call {@code getReturnValue()} because the
 *       method has not yet executed
 *   <li>{@link InterceptType#AFTER} intercepts cannot call {@code proceed()} because the method has
 *       already executed
 *   <li>{@link InterceptType#AROUND} intercepts support both operations but in different phases
 * </ul>
 *
 * <p>This exception indicates a programming error where the callback code attempts an operation
 * that is fundamentally incompatible with the intercept type.
 *
 * @see InterceptApiMisuseException
 * @see InterceptPhaseViolationException
 * @see InterceptType
 */
public class InterceptTypeNotSupportedException extends InterceptApiMisuseException {

  /**
   * Constructs a new InterceptTypeNotSupportedException for the specified operation and intercept
   * type.
   *
   * <p>The exception message will be formatted as: "{operation} is not supported for {intercept
   * type} intercepts"
   *
   * @param operation the operation that was attempted (e.g., "getReturnValue()")
   * @param interceptType the intercept type for which the operation is not supported
   */
  public InterceptTypeNotSupportedException(String operation, InterceptType interceptType) {
    super(
        String.format("%s is not supported for %s intercepts", operation, interceptType),
        operation,
        interceptType,
        null);
  }

  /**
   * Constructs a new InterceptTypeNotSupportedException with the specified operation, intercept
   * type, and underlying cause.
   *
   * @param operation the operation that was attempted (e.g., "getReturnValue()")
   * @param interceptType the intercept type for which the operation is not supported
   * @param cause the underlying cause of this exception
   */
  public InterceptTypeNotSupportedException(
      String operation, InterceptType interceptType, Throwable cause) {
    super(
        String.format("%s is not supported for %s intercepts", operation, interceptType),
        operation,
        interceptType,
        null,
        cause);
  }
}
