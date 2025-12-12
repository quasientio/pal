/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.lang.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link AroundTimeoutException}.
 *
 * <p>Verifies the exception thrown when AROUND proceed() times out.
 */
public class AroundTimeoutExceptionTest {

  /** Tests construction with message only. */
  @Test
  public void testWithMessage() {
    AroundTimeoutException exception =
        new AroundTimeoutException("Timeout after 30000ms waiting for AFTER phase");

    assertEquals("Timeout after 30000ms waiting for AFTER phase", exception.getMessage());
    assertNull(exception.getCause());
  }

  /** Tests construction with message and cause. */
  @Test
  public void testWithMessageAndCause() {
    InterruptedException cause = new InterruptedException("Thread interrupted");
    AroundTimeoutException exception =
        new AroundTimeoutException("Timeout waiting for response", cause);

    assertEquals("Timeout waiting for response", exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  /** Tests that exception inherits from RuntimeException (verified at compile time). */
  @Test
  public void testIsRuntimeException() {
    AroundTimeoutException exception = new AroundTimeoutException("test");

    // Assign to RuntimeException to verify inheritance at compile time
    RuntimeException runtimeException = exception;
    assertEquals("test", runtimeException.getMessage());
  }

  /** Tests that exception can be caught as RuntimeException. */
  @Test
  public void testCanBeCaughtAsRuntimeException() {
    boolean caught = false;
    try {
      throw new AroundTimeoutException("test timeout");
    } catch (RuntimeException e) {
      caught = true;
      assertTrue(e instanceof AroundTimeoutException);
    }
    assertTrue(caught);
  }

  /** Tests exception message formatting for typical timeout scenario. */
  @Test
  public void testTypicalTimeoutMessage() {
    int timeoutMs = 5000;
    String callbackId = "cb-123";

    AroundTimeoutException exception =
        new AroundTimeoutException(
            "Timeout waiting for AFTER phase from interceptable peer after "
                + timeoutMs
                + "ms, callbackId="
                + callbackId);

    assertTrue(exception.getMessage().contains("5000ms"));
    assertTrue(exception.getMessage().contains("cb-123"));
  }

  /** Tests that exception preserves stack trace with cause. */
  @Test
  public void testPreservesStackTraceWithCause() {
    Exception originalCause = new Exception("original error");
    AroundTimeoutException exception =
        new AroundTimeoutException("Timeout occurred", originalCause);

    // Stack trace should include both the AroundTimeoutException and the cause
    StackTraceElement[] stackTrace = exception.getStackTrace();
    assertTrue(stackTrace.length > 0);
    assertSame(originalCause, exception.getCause());
  }
}
