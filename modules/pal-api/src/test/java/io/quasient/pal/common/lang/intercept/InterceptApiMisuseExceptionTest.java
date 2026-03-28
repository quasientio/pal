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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Locale;
import org.junit.Test;

/**
 * Unit test specifications for {@link InterceptApiMisuseException} and its subclasses.
 *
 * <p>These are test stubs that specify the expected behavior of the new custom exception hierarchy
 * that distinguishes API misuse from intentional callback exceptions. The actual implementation
 * will be provided in a future implementation.
 *
 * <p>This hierarchy includes:
 *
 * <ul>
 *   <li>{@link InterceptApiMisuseException} - Base exception for API misuse
 *   <li>{@link InterceptTypeNotSupportedException} - Operation not supported for intercept type
 *   <li>{@link InterceptPhaseViolationException} - Operation called in wrong phase
 * </ul>
 */
public class InterceptApiMisuseExceptionTest {

  /**
   * Tests that the base InterceptApiMisuseException stores all context fields correctly.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> InterceptType.BEFORE, InterceptPhase.BEFORE, operation "getReturnValue()"
   *   <li><b>When:</b> InterceptApiMisuseException is constructed with these parameters
   *   <li><b>Then:</b> All fields are accessible via getters
   *   <li><b>And:</b> The exception message contains the operation name
   *   <li><b>And:</b> The InterceptType can be retrieved via getInterceptType()
   *   <li><b>And:</b> The InterceptPhase can be retrieved via getInterceptPhase() if applicable
   *   <li><b>And:</b> The operation name can be retrieved via getOperation()
   * </ul>
   */
  @Test
  public void shouldCreateBaseExceptionWithAllFields() {
    // Given: InterceptType.BEFORE, InterceptPhase.BEFORE, operation "getReturnValue()"
    InterceptType interceptType = InterceptType.BEFORE;
    InterceptPhase interceptPhase = InterceptPhase.BEFORE;
    String operation = "getReturnValue()";

    // When: InterceptApiMisuseException is constructed
    InterceptApiMisuseException exception =
        new InterceptApiMisuseException(
            "Test message for getReturnValue() operation",
            operation,
            interceptType,
            interceptPhase);

    // Then: All fields are accessible via getters
    assertNotNull(exception);
    assertEquals(operation, exception.getOperation());
    assertEquals(interceptType, exception.getInterceptType());
    assertEquals(interceptPhase, exception.getInterceptPhase());

    // And: Message contains operation name
    String message = exception.getMessage();
    assertNotNull(message);
    assertTrue("Message should contain operation name", message.contains("getReturnValue()"));
  }

  /**
   * Tests that InterceptTypeNotSupportedException formats its message correctly.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> Operation "getReturnValue()" and InterceptType.BEFORE
   *   <li><b>When:</b> InterceptTypeNotSupportedException is constructed
   *   <li><b>Then:</b> The exception message states "getReturnValue() is not supported for BEFORE
   *       intercepts"
   *   <li><b>And:</b> The operation and intercept type are accessible via getters
   *   <li><b>And:</b> The exception is an instance of InterceptApiMisuseException
   * </ul>
   */
  @Test
  public void shouldCreateTypeNotSupportedExceptionWithCorrectMessage() {
    // Given: Operation "getReturnValue()" and InterceptType.BEFORE
    String operation = "getReturnValue()";
    InterceptType interceptType = InterceptType.BEFORE;

    // When: InterceptTypeNotSupportedException is constructed
    InterceptTypeNotSupportedException exception =
        new InterceptTypeNotSupportedException(operation, interceptType);

    // Then: Message states operation is not supported for intercept type
    String message = exception.getMessage();
    assertNotNull(message);
    assertTrue("Message should contain operation name", message.contains("getReturnValue()"));
    assertTrue("Message should contain intercept type", message.contains("BEFORE"));
    assertTrue(
        "Message should indicate lack of support",
        message.toLowerCase(Locale.ROOT).contains("not supported"));

    // And: Getters work correctly
    assertEquals(operation, exception.getOperation());
    assertEquals(interceptType, exception.getInterceptType());
  }

  /**
   * Tests that InterceptPhaseViolationException formats its message correctly.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> Operation "getReturnValue()", currentPhase BEFORE, requiredPhase AFTER
   *   <li><b>When:</b> InterceptPhaseViolationException is constructed
   *   <li><b>Then:</b> The exception message states the phase mismatch
   *   <li><b>And:</b> The message indicates currentPhase and requiredPhase
   *   <li><b>And:</b> currentPhase is accessible via getCurrentPhase()
   *   <li><b>And:</b> requiredPhase is accessible via getRequiredPhase()
   *   <li><b>And:</b> The exception is an instance of InterceptApiMisuseException
   * </ul>
   */
  @Test
  public void shouldCreatePhaseViolationExceptionWithCorrectMessage() {
    // Given: Operation "getReturnValue()", currentPhase BEFORE, requiredPhase AFTER
    String operation = "getReturnValue()";
    InterceptPhase currentPhase = InterceptPhase.BEFORE;
    InterceptPhase requiredPhase = InterceptPhase.AFTER;

    // When: InterceptPhaseViolationException is constructed
    InterceptPhaseViolationException exception =
        new InterceptPhaseViolationException(operation, currentPhase, requiredPhase);

    // Then: Message states phase mismatch
    String message = exception.getMessage();
    assertNotNull(message);
    assertTrue("Message should contain operation name", message.contains("getReturnValue()"));
    assertTrue("Message should contain current phase", message.contains("BEFORE"));
    assertTrue("Message should contain required phase", message.contains("AFTER"));

    // And: Phases are accessible via getters
    assertEquals(currentPhase, exception.getCurrentPhase());
    assertEquals(requiredPhase, exception.getRequiredPhase());
  }

  /**
   * Tests that exception cause chain is preserved correctly.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> An underlying cause exception (e.g., NullPointerException)
   *   <li><b>When:</b> InterceptApiMisuseException is constructed with the cause
   *   <li><b>Then:</b> getCause() returns the original exception
   *   <li><b>And:</b> The cause is the exact same instance (identity check)
   *   <li><b>And:</b> All context fields are still accessible
   * </ul>
   */
  @Test
  public void shouldPreserveExceptionCause() {
    // Given: An underlying cause exception
    Exception cause = new NullPointerException("Test NPE");
    String operation = "getReturnValue()";
    InterceptType interceptType = InterceptType.BEFORE;
    InterceptPhase interceptPhase = InterceptPhase.BEFORE;

    // When: InterceptApiMisuseException is constructed with cause
    InterceptApiMisuseException exception =
        new InterceptApiMisuseException(
            "Test message", operation, interceptType, interceptPhase, cause);

    // Then: getCause() returns original exception
    Throwable returnedCause = exception.getCause();
    assertNotNull(returnedCause);
    assertSame("Cause should be same instance", cause, returnedCause);

    // And: All context fields are still accessible
    assertEquals(operation, exception.getOperation());
    assertEquals(interceptType, exception.getInterceptType());
    assertEquals(interceptPhase, exception.getInterceptPhase());
  }

  /**
   * Tests that all InterceptApiMisuseException subclasses are unchecked exceptions.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> Any InterceptApiMisuseException subclass
   *   <li><b>When:</b> Checking inheritance hierarchy
   *   <li><b>Then:</b> The exception is an instance of RuntimeException
   *   <li><b>And:</b> InterceptApiMisuseException extends RuntimeException
   *   <li><b>And:</b> InterceptTypeNotSupportedException is a RuntimeException
   *   <li><b>And:</b> InterceptPhaseViolationException is a RuntimeException
   * </ul>
   */
  @Test
  public void shouldBeInstanceOfRuntimeException() {
    // Given: InterceptApiMisuseException and its subclasses (classes are loaded for hierarchy
    // checks)

    // Then: Verify class hierarchy
    assertTrue(
        "InterceptApiMisuseException should extend RuntimeException",
        RuntimeException.class.isAssignableFrom(InterceptApiMisuseException.class));
    assertTrue(
        "InterceptTypeNotSupportedException should extend RuntimeException",
        RuntimeException.class.isAssignableFrom(InterceptTypeNotSupportedException.class));
    assertTrue(
        "InterceptPhaseViolationException should extend RuntimeException",
        RuntimeException.class.isAssignableFrom(InterceptPhaseViolationException.class));
  }
}
