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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link InterceptCallbackResponse}.
 *
 * <p>Verifies factory methods and fluent API behavior.
 */
public class InterceptCallbackResponseTest {

  /**
   * Tests that default constructor creates a response with default values.
   *
   * <p>shouldProceed should be true, exceptionToThrow should be null.
   */
  @Test
  public void testDefaultConstructor() {
    InterceptCallbackResponse response = new InterceptCallbackResponse();

    assertTrue(response.isShouldProceed());
    assertNull(response.getExceptionToThrow());
  }

  /**
   * Tests that {@link InterceptCallbackResponse#setShouldProceed(boolean)} sets the proceed flag.
   */
  @Test
  public void testSetShouldProceed() {
    InterceptCallbackResponse response = new InterceptCallbackResponse();

    response.setShouldProceed(false);

    assertFalse(response.isShouldProceed());
  }

  /**
   * Tests that {@link InterceptCallbackResponse#setShouldProceed(boolean)} returns this for method
   * chaining.
   */
  @Test
  public void testSetShouldProceedReturnsThis() {
    InterceptCallbackResponse response = new InterceptCallbackResponse();

    InterceptCallbackResponse result = response.setShouldProceed(false);

    assertSame(response, result);
  }

  /**
   * Tests that {@link InterceptCallbackResponse#setExceptionToThrow(Throwable)} sets the exception.
   */
  @Test
  public void testSetExceptionToThrow() {
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    RuntimeException exception = new RuntimeException("Test exception");

    response.setExceptionToThrow(exception);

    assertSame(exception, response.getExceptionToThrow());
  }

  /**
   * Tests that {@link InterceptCallbackResponse#setExceptionToThrow(Throwable)} returns this for
   * method chaining.
   */
  @Test
  public void testSetExceptionToThrowReturnsThis() {
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    RuntimeException exception = new RuntimeException("Test exception");

    InterceptCallbackResponse result = response.setExceptionToThrow(exception);

    assertSame(response, result);
  }

  /**
   * Tests that {@link InterceptCallbackResponse#setExceptionToThrow(Throwable)} accepts null to
   * clear the exception.
   */
  @Test
  public void testSetExceptionToThrowCanClearException() {
    InterceptCallbackResponse response = new InterceptCallbackResponse();
    RuntimeException exception = new RuntimeException("Test exception");

    response.setExceptionToThrow(exception);
    response.setExceptionToThrow(null);

    assertNull(response.getExceptionToThrow());
  }

  /**
   * Tests that {@link InterceptCallbackResponse#skipProceed()} creates a response with
   * shouldProceed=false.
   */
  @Test
  public void testSkipProceedFactory() {
    InterceptCallbackResponse response = InterceptCallbackResponse.skipProceed();

    assertNotNull(response);
    assertFalse(response.isShouldProceed());
    assertNull(response.getExceptionToThrow());
  }

  /**
   * Tests that {@link InterceptCallbackResponse#throwException(Throwable)} creates a response with
   * the specified exception.
   */
  @Test
  public void testThrowExceptionFactory() {
    RuntimeException exception = new RuntimeException("Test exception");

    InterceptCallbackResponse response = InterceptCallbackResponse.throwException(exception);

    assertNotNull(response);
    assertSame(exception, response.getExceptionToThrow());
    assertTrue(response.isShouldProceed()); // shouldProceed is still default true
  }

  /** Tests method chaining for setting multiple properties. */
  @Test
  public void testMethodChaining() {
    RuntimeException exception = new RuntimeException("Test exception");

    InterceptCallbackResponse response =
        new InterceptCallbackResponse().setShouldProceed(false).setExceptionToThrow(exception);

    assertFalse(response.isShouldProceed());
    assertSame(exception, response.getExceptionToThrow());
  }

  /**
   * Tests that factory method {@link InterceptCallbackResponse#skipProceed()} creates independent
   * instances.
   */
  @Test
  public void testSkipProceedCreatesIndependentInstances() {
    InterceptCallbackResponse response1 = InterceptCallbackResponse.skipProceed();
    InterceptCallbackResponse response2 = InterceptCallbackResponse.skipProceed();

    // Should be different instances
    assertFalse(response1 == response2);

    // Both should have shouldProceed=false
    assertFalse(response1.isShouldProceed());
    assertFalse(response2.isShouldProceed());

    // Modifying one should not affect the other
    response1.setShouldProceed(true);
    assertTrue(response1.isShouldProceed());
    assertFalse(response2.isShouldProceed());
  }

  /**
   * Tests that {@link InterceptCallbackResponse#throwException(Throwable)} preserves exception
   * type.
   */
  @Test
  public void testThrowExceptionPreservesExceptionType() {
    SecurityException securityException = new SecurityException("Access denied");

    InterceptCallbackResponse response =
        InterceptCallbackResponse.throwException(securityException);

    Throwable thrown = response.getExceptionToThrow();
    assertTrue(thrown instanceof SecurityException);
    assertEquals("Access denied", thrown.getMessage());
  }

  /** Tests that setting shouldProceed to true then false works correctly. */
  @Test
  public void testToggleShouldProceed() {
    InterceptCallbackResponse response = new InterceptCallbackResponse();

    assertTrue(response.isShouldProceed());

    response.setShouldProceed(false);
    assertFalse(response.isShouldProceed());

    response.setShouldProceed(true);
    assertTrue(response.isShouldProceed());
  }
}
