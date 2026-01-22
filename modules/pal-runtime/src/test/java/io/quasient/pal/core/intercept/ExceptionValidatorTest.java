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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.InvalidCallbackExceptionException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import org.junit.Test;

/**
 * Unit tests for {@link ExceptionValidator}.
 *
 * <p>Tests verify the exception validation logic that ensures callback exceptions comply with the
 * intercepted method's exception contract. The validator must:
 *
 * <ul>
 *   <li>Always allow {@link RuntimeException} and {@link Error} (unchecked exceptions)
 *   <li>Allow checked exceptions that match or are subclasses of declared exceptions
 *   <li>Reject or wrap checked exceptions based on {@link
 *       io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy}
 *   <li>Handle edge cases gracefully (null declarations, missing classes)
 * </ul>
 */
public class ExceptionValidatorTest {

  /**
   * Tests that RuntimeExceptions bypass validation regardless of declared exceptions.
   *
   * <p><b>Given:</b> Method declares IOException; callback throws IllegalArgumentException
   *
   * <p><b>When:</b> Validating the exception
   *
   * <p><b>Then:</b> No exception thrown (RuntimeExceptions always allowed)
   */
  @Test
  public void shouldAllowRuntimeExceptionAlways() {
    // Given: Method declares IOException; callback throws IllegalArgumentException
    String[] declaredExceptions = new String[] {"java.io.IOException"};
    IllegalArgumentException runtimeException = new IllegalArgumentException("test");

    // When: Validating the exception with REJECT policy (strictest policy)
    Throwable result =
        ExceptionValidator.validateThrowable(
            runtimeException, declaredExceptions, CheckedExceptionPolicy.REJECT);

    // Then: Returns the same exception unchanged (RuntimeExceptions always allowed)
    assertSame(runtimeException, result);
  }

  /**
   * Tests that Errors bypass validation regardless of declared exceptions.
   *
   * <p><b>Given:</b> Method declares IOException; callback throws OutOfMemoryError
   *
   * <p><b>When:</b> Validating the exception
   *
   * <p><b>Then:</b> No exception thrown (Errors always allowed)
   */
  @Test
  public void shouldAllowErrorAlways() {
    // Given: Method declares IOException; callback throws OutOfMemoryError
    String[] declaredExceptions = new String[] {"java.io.IOException"};
    OutOfMemoryError error = new OutOfMemoryError("test");

    // When: Validating the exception with REJECT policy (strictest policy)
    Throwable result =
        ExceptionValidator.validateThrowable(
            error, declaredExceptions, CheckedExceptionPolicy.REJECT);

    // Then: Returns the same error unchanged (Errors always allowed)
    assertSame(error, result);
  }

  /**
   * Tests that checked exceptions matching declared exceptions are allowed.
   *
   * <p><b>Given:</b> Method declares IOException; callback throws IOException
   *
   * <p><b>When:</b> Validating the exception
   *
   * <p><b>Then:</b> No exception thrown (exact match allowed)
   */
  @Test
  public void shouldAllowDeclaredCheckedException() {
    // Given: Method declares IOException; callback throws IOException
    String[] declaredExceptions = new String[] {"java.io.IOException"};
    IOException checkedException = new IOException("test");

    // When: Validating the exception
    Throwable result =
        ExceptionValidator.validateThrowable(
            checkedException, declaredExceptions, CheckedExceptionPolicy.REJECT);

    // Then: Returns the same exception unchanged (exact match allowed)
    assertSame(checkedException, result);
  }

  /**
   * Tests that subclasses of declared exceptions are allowed.
   *
   * <p><b>Given:</b> Method declares IOException; callback throws FileNotFoundException
   *
   * <p><b>When:</b> Validating the exception
   *
   * <p><b>Then:</b> No exception thrown (FileNotFoundException extends IOException)
   */
  @Test
  public void shouldAllowSubclassOfDeclaredException() {
    // Given: Method declares IOException; callback throws FileNotFoundException
    String[] declaredExceptions = new String[] {"java.io.IOException"};
    FileNotFoundException subclassException = new FileNotFoundException("test");

    // When: Validating the exception
    Throwable result =
        ExceptionValidator.validateThrowable(
            subclassException, declaredExceptions, CheckedExceptionPolicy.REJECT);

    // Then: Returns the same exception unchanged (FileNotFoundException extends IOException)
    assertSame(subclassException, result);
  }

  /**
   * Tests that undeclared checked exceptions are rejected when policy is REJECT.
   *
   * <p><b>Given:</b> Method declares IOException; callback throws SQLException
   *
   * <p><b>When:</b> Validating with REJECT policy
   *
   * <p><b>Then:</b> InvalidCallbackExceptionException thrown
   */
  @Test(expected = InvalidCallbackExceptionException.class)
  public void shouldRejectUndeclaredCheckedException() {
    // Given: Method declares IOException; callback throws SQLException
    String[] declaredExceptions = new String[] {"java.io.IOException"};
    SQLException undeclaredException = new SQLException("test");

    // When: Validating with REJECT policy
    // Then: InvalidCallbackExceptionException thrown
    ExceptionValidator.validateThrowable(
        undeclaredException, declaredExceptions, CheckedExceptionPolicy.REJECT);
  }

  /**
   * Tests that undeclared checked exceptions are wrapped when policy is WRAP.
   *
   * <p><b>Given:</b> Method declares IOException; callback throws SQLException
   *
   * <p><b>When:</b> Validating with WRAP policy
   *
   * <p><b>Then:</b> Returns RuntimeException wrapping SQLException
   */
  @Test
  public void shouldWrapUndeclaredCheckedException() {
    // Given: Method declares IOException; callback throws SQLException
    String[] declaredExceptions = new String[] {"java.io.IOException"};
    SQLException undeclaredException = new SQLException("test");

    // When: Validating with WRAP policy
    Throwable result =
        ExceptionValidator.validateThrowable(
            undeclaredException, declaredExceptions, CheckedExceptionPolicy.WRAP);

    // Then: Returns RuntimeException wrapping SQLException
    assertTrue(result instanceof RuntimeException);
    assertSame(undeclaredException, result.getCause());
    assertEquals("Callback threw undeclared checked exception", result.getMessage());
  }

  /**
   * Tests that validation is skipped when declared exceptions are null (fail-open behavior).
   *
   * <p><b>Given:</b> Method with null declaredExceptions
   *
   * <p><b>When:</b> Validating any exception
   *
   * <p><b>Then:</b> No exception thrown (fail-open)
   */
  @Test
  public void shouldSkipValidationWhenDeclaredExceptionsNull() {
    // Given: Method with null declaredExceptions
    SQLException checkedException = new SQLException("test");

    // When: Validating with null declaredExceptions (fail-open)
    Throwable result =
        ExceptionValidator.validateThrowable(checkedException, null, CheckedExceptionPolicy.REJECT);

    // Then: Returns the same exception unchanged (validation skipped)
    assertSame(checkedException, result);
  }

  /**
   * Tests that ClassNotFoundException is handled gracefully during assignability checks.
   *
   * <p><b>Given:</b> Exception type not on classpath
   *
   * <p><b>When:</b> Checking isAssignableTo
   *
   * <p><b>Then:</b> Returns false gracefully (no CNFE thrown to caller)
   */
  @Test
  public void shouldHandleClassNotFoundGracefully() {
    // Given: Exception type not on classpath
    String exceptionClassName = "com.example.NonExistentException";
    String declaredClassName = "java.io.IOException";

    // When: Checking isAssignableTo with non-existent class
    boolean result = ExceptionValidator.isAssignableTo(exceptionClassName, declaredClassName);

    // Then: Returns false gracefully (no CNFE thrown to caller)
    assertFalse(result);
  }
}
