/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.InvalidCallbackExceptionException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility class for validating callback exceptions against method signatures.
 *
 * <p>This validator ensures that exceptions thrown by intercept callbacks comply with the
 * intercepted method's exception contract. The validation logic implements Java's checked exception
 * rules:
 *
 * <ul>
 *   <li>{@link RuntimeException} and {@link Error} subclasses always pass validation (unchecked)
 *   <li>Checked exceptions must be assignable to one of the method's declared exception types
 *   <li>Invalid checked exceptions are handled according to the {@link CheckedExceptionPolicy}
 * </ul>
 *
 * <p>The validator handles edge cases gracefully:
 *
 * <ul>
 *   <li>If {@code declaredExceptions} is null, validation is skipped (fail-open behavior)
 *   <li>If class loading fails during assignability checks, returns false conservatively
 *   <li>Empty declared exceptions array means no checked exceptions are allowed
 * </ul>
 *
 * <p><b>Example usage:</b>
 *
 * <pre>{@code
 * // Validate an exception thrown by a callback
 * String[] declaredExceptions = new String[] {"java.io.IOException"};
 * Throwable callbackException = new SQLException("Database error");
 *
 * try {
 *   Throwable validated = ExceptionValidator.validateThrowable(
 *       callbackException,
 *       declaredExceptions,
 *       CheckedExceptionPolicy.REJECT);
 * } catch (InvalidCallbackExceptionException e) {
 *   // SQLException is not compatible with IOException
 *   // Policy is REJECT, so InvalidCallbackExceptionException is thrown
 * }
 * }</pre>
 *
 * @see CheckedExceptionPolicy
 * @see InvalidCallbackExceptionException
 */
public final class ExceptionValidator {

  /** Private constructor to prevent instantiation of utility class. */
  private ExceptionValidator() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Validates a throwable against the declared exceptions of a method signature.
   *
   * <p>This method implements Java's checked exception validation rules:
   *
   * <ol>
   *   <li><b>Unchecked exceptions:</b> {@link RuntimeException} and {@link Error} subclasses always
   *       pass validation and are returned unchanged
   *   <li><b>Null declared exceptions:</b> If {@code declaredExceptions} is null, validation is
   *       skipped (fail-open) and the exception is returned unchanged
   *   <li><b>Compatible checked exceptions:</b> If the exception is assignable to any declared
   *       exception type, it passes validation and is returned unchanged
   *   <li><b>Incompatible checked exceptions:</b> Handling depends on the policy:
   *       <ul>
   *         <li>{@code WRAP}: Returns the exception wrapped in a {@link RuntimeException}
   *         <li>{@code REJECT}: Throws {@link InvalidCallbackExceptionException}
   *         <li>{@code ALLOW_ALL}: Returns the exception unchanged (bypasses validation)
   *       </ul>
   * </ol>
   *
   * @param exception the exception thrown by the callback; must not be null
   * @param declaredExceptions array of fully-qualified exception class names declared by the
   *     intercepted method; null means skip validation (fail-open)
   * @param policy the policy for handling incompatible checked exceptions; must not be null
   * @return the validated exception (either original or wrapped), ready to be thrown
   * @throws InvalidCallbackExceptionException if the policy is {@code REJECT} and the exception is
   *     an incompatible checked exception
   * @throws NullPointerException if {@code exception} or {@code policy} is null
   */
  @Nonnull
  public static Throwable validateThrowable(
      @Nonnull Throwable exception,
      @Nullable String[] declaredExceptions,
      @Nonnull CheckedExceptionPolicy policy) {

    if (exception == null) {
      throw new NullPointerException("exception must not be null");
    }
    if (policy == null) {
      throw new NullPointerException("policy must not be null");
    }

    // Step 1: Unchecked exceptions (RuntimeException and Error) always pass
    if (exception instanceof RuntimeException || exception instanceof Error) {
      return exception;
    }

    // Step 2: If declaredExceptions is null, skip validation (fail-open)
    if (declaredExceptions == null) {
      return exception;
    }

    // Step 3: Check if the checked exception is compatible with declared exceptions
    String exceptionClassName = exception.getClass().getName();
    boolean isCompatible = false;

    for (String declaredClassName : declaredExceptions) {
      if (isAssignableTo(exceptionClassName, declaredClassName)) {
        isCompatible = true;
        break;
      }
    }

    // Step 4: If compatible, return the exception unchanged
    if (isCompatible) {
      return exception;
    }

    // Step 5: Handle incompatible checked exception based on policy
    return switch (policy) {
      case WRAP ->
          // Wrap in RuntimeException to bypass checked exception requirements
          new RuntimeException("Callback threw undeclared checked exception", exception);
      case REJECT -> {
        // Convert String[] to Class<?>[] for InvalidCallbackExceptionException
        Class<?>[] declaredExceptionClasses = loadDeclaredExceptionClasses(declaredExceptions);
        throw new InvalidCallbackExceptionException(exception, declaredExceptionClasses);
      }
      case ALLOW_ALL ->
          // Bypass validation entirely
          exception;
    };
  }

  /**
   * Checks if an exception class is assignable to a declared exception class.
   *
   * <p>This method performs inheritance checking by loading both classes and using {@link
   * Class#isAssignableFrom}. The method handles {@link ClassNotFoundException} gracefully by
   * returning false, which is a conservative approach that treats missing classes as incompatible.
   *
   * <p><b>Rationale for graceful handling:</b> If we cannot load a class, we cannot verify the
   * inheritance relationship. Returning false ensures we don't accidentally allow an incompatible
   * exception to pass validation. This is safer than throwing an exception, which would prevent
   * interception from working when classes are missing from the classpath.
   *
   * @param exceptionClassName the fully-qualified name of the exception class to check
   * @param declaredClassName the fully-qualified name of the declared exception class
   * @return true if exceptionClassName is assignable to declaredClassName, false otherwise (or if
   *     classes cannot be loaded)
   */
  public static boolean isAssignableTo(
      @Nonnull String exceptionClassName, @Nonnull String declaredClassName) {

    if (exceptionClassName == null) {
      throw new NullPointerException("exceptionClassName must not be null");
    }
    if (declaredClassName == null) {
      throw new NullPointerException("declaredClassName must not be null");
    }

    try {
      // Load both classes
      Class<?> exceptionClass = Class.forName(exceptionClassName);
      Class<?> declaredClass = Class.forName(declaredClassName);

      // Check if exceptionClass is assignable to declaredClass
      // This returns true if exceptionClass is the same as or a subclass of declaredClass
      return declaredClass.isAssignableFrom(exceptionClass);

    } catch (ClassNotFoundException e) {
      // Conservative handling: if we can't load the class, treat it as incompatible
      // This prevents accidentally allowing incompatible exceptions due to classpath issues
      return false;
    }
  }

  /**
   * Converts an array of fully-qualified exception class names to an array of Class objects.
   *
   * <p>This helper method is used when constructing {@link InvalidCallbackExceptionException},
   * which requires Class objects rather than class name strings. Classes that cannot be loaded are
   * skipped in the result array.
   *
   * @param declaredExceptions array of fully-qualified exception class names
   * @return array of loaded Class objects (may be smaller if some classes cannot be loaded)
   */
  private static Class<?>[] loadDeclaredExceptionClasses(String[] declaredExceptions) {
    List<Class<?>> loadedClasses = new ArrayList<>();

    for (String className : declaredExceptions) {
      try {
        loadedClasses.add(Class.forName(className));
      } catch (ClassNotFoundException e) {
        // Skip classes that cannot be loaded
        // The error message in InvalidCallbackExceptionException will show the class name
      }
    }

    return loadedClasses.toArray(new Class<?>[0]);
  }
}
