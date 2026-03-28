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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ProceedResult}.
 *
 * <p>Verifies the result container for AROUND intercept proceed() calls.
 */
public class ProceedResultTest {

  /** Tests construction with return value and no exception. */
  @Test
  public void testWithReturnValue() {
    Object returnValue = "test result";
    ProceedResult result = new ProceedResult(returnValue, null);

    assertEquals("test result", result.getReturnValue());
    assertNull(result.getThrownException());
    assertFalse(result.hasException());
  }

  /** Tests construction with null return value (void method or explicit null). */
  @Test
  public void testWithNullReturnValue() {
    ProceedResult result = new ProceedResult(null, null);

    assertNull(result.getReturnValue());
    assertNull(result.getThrownException());
    assertFalse(result.hasException());
  }

  /** Tests construction with exception and no return value. */
  @Test
  public void testWithException() {
    RuntimeException exception = new RuntimeException("test error");
    ProceedResult result = new ProceedResult(null, exception);

    assertNull(result.getReturnValue());
    assertSame(exception, result.getThrownException());
    assertTrue(result.hasException());
  }

  /** Tests getReturnValueOrThrow() returns value when no exception. */
  @Test
  public void testGetReturnValueOrThrowReturnsValue() throws Throwable {
    Object returnValue = 42;
    ProceedResult result = new ProceedResult(returnValue, null);

    assertEquals(42, result.getReturnValueOrThrow());
  }

  /** Tests getReturnValueOrThrow() throws when exception present. */
  @Test
  public void testGetReturnValueOrThrowThrowsException() {
    RuntimeException exception = new RuntimeException("expected error");
    ProceedResult result = new ProceedResult(null, exception);

    Throwable thrown = null;
    try {
      result.getReturnValueOrThrow();
    } catch (Throwable t) {
      thrown = t;
    }
    assertSame("Expected the stored exception to be thrown", exception, thrown);
  }

  /** Tests getReturnValueOrThrow() with null return value and no exception. */
  @Test
  public void testGetReturnValueOrThrowReturnsNull() throws Throwable {
    ProceedResult result = new ProceedResult(null, null);

    assertNull(result.getReturnValueOrThrow());
  }

  /** Tests that various return types are preserved. */
  @Test
  public void testVariousReturnTypes() {
    // Integer
    ProceedResult intResult = new ProceedResult(123, null);
    assertEquals(123, intResult.getReturnValue());

    // String
    ProceedResult stringResult = new ProceedResult("hello", null);
    assertEquals("hello", stringResult.getReturnValue());

    // Array
    int[] array = new int[] {1, 2, 3};
    ProceedResult arrayResult = new ProceedResult(array, null);
    assertSame(array, arrayResult.getReturnValue());

    // Custom object
    Object custom = new Object();
    ProceedResult customResult = new ProceedResult(custom, null);
    assertSame(custom, customResult.getReturnValue());
  }

  /** Tests that checked exceptions can be stored and retrieved. */
  @Test
  public void testCheckedExceptionSupport() {
    Exception checkedException = new Exception("checked error");
    ProceedResult result = new ProceedResult(null, checkedException);

    assertTrue(result.hasException());
    assertSame(checkedException, result.getThrownException());

    Throwable thrown = null;
    try {
      result.getReturnValueOrThrow();
    } catch (Throwable t) {
      thrown = t;
    }
    assertSame("Expected checked exception to be thrown", checkedException, thrown);
  }
}
