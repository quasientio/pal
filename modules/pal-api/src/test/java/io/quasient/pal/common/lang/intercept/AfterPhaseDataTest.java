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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link AfterPhaseData}.
 *
 * <p>Verifies the internal data record used to communicate AFTER phase results.
 */
public class AfterPhaseDataTest {

  /** Tests construction with return value for non-void method. */
  @Test
  public void testWithReturnValue() {
    Object returnValue = "result";
    AfterPhaseData data = new AfterPhaseData(returnValue, null, false);

    assertEquals("result", data.returnValue());
    assertNull(data.thrownException());
    assertFalse(data.isVoid());
    assertFalse(data.hasException());
  }

  /** Tests construction for void method. */
  @Test
  public void testVoidMethod() {
    AfterPhaseData data = new AfterPhaseData(null, null, true);

    assertNull(data.returnValue());
    assertNull(data.thrownException());
    assertTrue(data.isVoid());
    assertFalse(data.hasException());
  }

  /** Tests construction with exception. */
  @Test
  public void testWithException() {
    RuntimeException exception = new RuntimeException("error");
    AfterPhaseData data = new AfterPhaseData(null, exception, false);

    assertNull(data.returnValue());
    assertSame(exception, data.thrownException());
    assertFalse(data.isVoid());
    assertTrue(data.hasException());
  }

  /** Tests void method that threw exception. */
  @Test
  public void testVoidMethodWithException() {
    RuntimeException exception = new RuntimeException("void method error");
    AfterPhaseData data = new AfterPhaseData(null, exception, true);

    assertNull(data.returnValue());
    assertSame(exception, data.thrownException());
    assertTrue(data.isVoid());
    assertTrue(data.hasException());
  }

  /** Tests that null return value is distinct from void. */
  @Test
  public void testNullReturnValueNotVoid() {
    AfterPhaseData data = new AfterPhaseData(null, null, false);

    assertNull(data.returnValue());
    assertFalse(data.isVoid());
    assertFalse(data.hasException());
  }

  /** Tests record equality. */
  @Test
  public void testRecordEquality() {
    RuntimeException exception = new RuntimeException("test");

    AfterPhaseData data1 = new AfterPhaseData("value", null, false);
    AfterPhaseData data2 = new AfterPhaseData("value", null, false);
    AfterPhaseData data3 = new AfterPhaseData("other", null, false);

    assertEquals(data1, data2);
    assertNotEquals(data1, data3);

    // With exception - same instance
    AfterPhaseData dataWithEx1 = new AfterPhaseData(null, exception, false);
    AfterPhaseData dataWithEx2 = new AfterPhaseData(null, exception, false);
    assertEquals(dataWithEx1, dataWithEx2);
  }

  /** Tests various return value types. */
  @Test
  public void testVariousReturnTypes() {
    // Integer
    AfterPhaseData intData = new AfterPhaseData(42, null, false);
    assertEquals(42, intData.returnValue());

    // Array
    int[] array = {1, 2, 3};
    AfterPhaseData arrayData = new AfterPhaseData(array, null, false);
    assertSame(array, arrayData.returnValue());

    // Custom object
    Object obj = new Object();
    AfterPhaseData objData = new AfterPhaseData(obj, null, false);
    assertSame(obj, objData.returnValue());
  }

  /** Tests checked exception support. */
  @Test
  public void testCheckedExceptionSupport() {
    Exception checkedException = new Exception("checked");
    AfterPhaseData data = new AfterPhaseData(null, checkedException, false);

    assertTrue(data.hasException());
    assertSame(checkedException, data.thrownException());
  }
}
