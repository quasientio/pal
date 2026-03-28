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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;
import org.junit.Test;

/**
 * Unit test specifications for {@link InvalidCallbackExceptionException}.
 *
 * <p>Verifies that the exception correctly stores the original exception and declared exception
 * types, formats messages appropriately, and is a RuntimeException subclass.
 */
public class InvalidCallbackExceptionExceptionTest {

  /**
   * Tests that the exception stores the original exception and all declared exception types.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> A SQLException is thrown by a callback
   *   <li><b>And:</b> The intercepted method declares IOException and FileNotFoundException
   *   <li><b>When:</b> InvalidCallbackExceptionException is constructed with these parameters
   *   <li><b>Then:</b> getCause() returns the original SQLException
   *   <li><b>And:</b> getDeclaredExceptions() returns an array containing IOException and
   *       FileNotFoundException
   *   <li><b>And:</b> getMessage() describes the exception type mismatch
   * </ul>
   */
  @Test
  public void shouldStoreOriginalExceptionAndDeclaredTypes() {
    // Given: SQLException thrown, method declares IOException, FileNotFoundException
    SQLException thrownException = new SQLException("Database connection failed");
    Class<?>[] declaredExceptions = {IOException.class, FileNotFoundException.class};

    // When: InvalidCallbackExceptionException is constructed
    InvalidCallbackExceptionException exception =
        new InvalidCallbackExceptionException(thrownException, declaredExceptions);

    // Then: getCause() returns SQLException
    assertSame(thrownException, exception.getCause());

    // And: getDeclaredExceptions() returns [IOException, FileNotFoundException]
    Class<?>[] returnedExceptions = exception.getDeclaredExceptions();
    assertNotNull(returnedExceptions);
    assertEquals(2, returnedExceptions.length);
    assertEquals(IOException.class, returnedExceptions[0]);
    assertEquals(FileNotFoundException.class, returnedExceptions[1]);

    // And: getMessage() describes mismatch
    String message = exception.getMessage();
    assertNotNull(message);
    assertTrue(message.contains("SQLException"));
    assertTrue(message.contains("IOException"));
    assertTrue(message.contains("FileNotFoundException"));
  }

  /**
   * Tests that the exception message contains both the thrown exception type and declared exception
   * types.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> A SQLException is thrown by a callback
   *   <li><b>And:</b> The intercepted method declares IOException
   *   <li><b>When:</b> Getting the exception message
   *   <li><b>Then:</b> The message contains "SQLException"
   *   <li><b>And:</b> The message contains "IOException"
   * </ul>
   */
  @Test
  public void shouldFormatMessageWithExceptionTypes() {
    // Given: SQLException thrown, method declares IOException
    SQLException thrownException = new SQLException("Connection timeout");
    Class<?>[] declaredExceptions = {IOException.class};

    // When: InvalidCallbackExceptionException is constructed
    InvalidCallbackExceptionException exception =
        new InvalidCallbackExceptionException(thrownException, declaredExceptions);

    // When: Getting message
    String message = exception.getMessage();

    // Then: Message contains "SQLException" and "IOException"
    assertNotNull(message);
    assertTrue("Message should contain thrown exception type", message.contains("SQLException"));
    assertTrue("Message should contain declared exception type", message.contains("IOException"));
  }

  /**
   * Tests that InvalidCallbackExceptionException is a RuntimeException subclass.
   *
   * <p><b>Test Specification:</b>
   *
   * <ul>
   *   <li><b>Given:</b> An InvalidCallbackExceptionException instance
   *   <li><b>When:</b> Checking its type
   *   <li><b>Then:</b> It is an instanceof RuntimeException
   *   <li><b>And:</b> It can be caught as a RuntimeException
   * </ul>
   */
  @Test
  public void shouldBeInstanceOfRuntimeException() {
    // Given: InvalidCallbackExceptionException instance
    SQLException thrownException = new SQLException("Test exception");
    Class<?>[] declaredExceptions = {IOException.class};

    InvalidCallbackExceptionException exception =
        new InvalidCallbackExceptionException(thrownException, declaredExceptions);

    // When: Checking type
    // Then: Is instanceof RuntimeException (verified at compile time)
    RuntimeException runtimeException = exception;
    assertNotNull(runtimeException);

    // And: Can be caught as RuntimeException
    boolean caught = false;
    try {
      throw exception;
    } catch (RuntimeException e) {
      caught = true;
      assertTrue(e instanceof InvalidCallbackExceptionException);
    }
    assertTrue(caught);
  }
}
