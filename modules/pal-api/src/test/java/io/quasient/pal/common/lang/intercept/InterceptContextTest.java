/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.lang.intercept;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.messages.colfer.ExecMessage;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link InterceptContext}.
 *
 * <p>Verifies argument/return value access and mutation logic.
 */
public class InterceptContextTest {

  private ExecMessage execMessage;
  private String peerUuid;

  /** Sets up test fixtures. */
  @Before
  public void setUp() {
    execMessage = new ExecMessage();
    execMessage.setMessageId("test-msg-123");
    peerUuid = "peer-uuid-456";
  }

  /**
   * Tests that {@link InterceptContext#forBeforePhase} creates a BEFORE-phase context with correct
   * state.
   */
  @Test
  public void testForBeforePhase() {
    Object[] args = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, args);

    assertEquals(InterceptPhase.BEFORE, ctx.getPhase());
    assertEquals(InterceptType.BEFORE, ctx.getInterceptType());
    assertEquals(peerUuid, ctx.getInterceptedPeerUuid());
    assertEquals(execMessage, ctx.getExec());
    assertArrayEquals(new Object[] {"hello", 42}, ctx.getArgs());
    assertFalse(ctx.isVoid());
    // Note: getReturnValue() and getThrownException() throw in BEFORE phase
    // Those behaviors are tested in testGetReturnValueThrowsForBeforeIntercept
    // and testGetThrownExceptionThrowsForBeforeIntercept
  }

  /**
   * Tests that {@link InterceptContext#forAfterPhase} creates an AFTER-phase context with correct
   * state.
   */
  @Test
  public void testForAfterPhase() {
    Object[] args = new Object[] {"hello", 42};
    Object returnValue = "result";

    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, args, returnValue, false, null);

    assertEquals(InterceptPhase.AFTER, ctx.getPhase());
    assertEquals(InterceptType.AFTER, ctx.getInterceptType());
    assertEquals(peerUuid, ctx.getInterceptedPeerUuid());
    assertEquals(execMessage, ctx.getExec());
    assertArrayEquals(new Object[] {"hello", 42}, ctx.getArgs());
    assertEquals("result", ctx.getReturnValue());
    assertFalse(ctx.isVoid());
    assertNull(ctx.getThrownException());
  }

  /** Tests that {@link InterceptContext#forAfterPhase} handles void methods correctly. */
  @Test
  public void testForAfterPhaseVoidMethod() {
    Object[] args = new Object[] {};

    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, args, null, true, null);

    assertTrue(ctx.isVoid());
    assertNull(ctx.getReturnValue());
  }

  /** Tests that {@link InterceptContext#forAfterPhase} handles exceptions correctly. */
  @Test
  public void testForAfterPhaseWithException() {
    Object[] args = new Object[] {100};
    Throwable exception = new RuntimeException("Something went wrong");

    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, args, null, false, exception);

    assertNull(ctx.getReturnValue());
    assertEquals(exception, ctx.getThrownException());
  }

  /**
   * Tests that {@link InterceptContext#getArgs()} returns a defensive copy.
   *
   * <p>Modifying the returned array should not affect the context's internal state.
   */
  @Test
  public void testGetArgsReturnsDefensiveCopy() {
    Object[] originalArgs = new Object[] {"original", 123};

    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, originalArgs);

    Object[] args1 = ctx.getArgs();
    args1[0] = "modified";

    Object[] args2 = ctx.getArgs();
    assertEquals("original", args2[0]); // should not be modified

    assertNotSame(args1, args2); // each call returns a new array
  }

  /** Tests that {@link InterceptContext#setArg(int, Object)} modifies arguments correctly. */
  @Test
  public void testSetArgModifiesArgument() {
    Object[] originalArgs = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, originalArgs);

    ctx.setArg(0, "HELLO");
    ctx.setArg(1, 84);

    Object[] modifiedArgs = ctx.getArgs();
    assertEquals("HELLO", modifiedArgs[0]);
    assertEquals(84, modifiedArgs[1]);
  }

  /**
   * Tests that {@link InterceptContext#setArg(int, Object)} uses copy-on-write.
   *
   * <p>Original array passed to factory method should not be modified.
   */
  @Test
  public void testSetArgUsesCopyOnWrite() {
    Object[] originalArgs = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, originalArgs);

    ctx.setArg(0, "HELLO");

    // Original array should not be modified
    assertEquals("hello", originalArgs[0]);
  }

  /** Tests that {@link InterceptContext#setArg(int, Object)} throws for out-of-bounds index. */
  @Test
  public void testSetArgOutOfBounds() {
    Object[] args = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, args);

    try {
      ctx.setArg(-1, "value");
      fail("Expected IndexOutOfBoundsException for negative index");
    } catch (IndexOutOfBoundsException e) {
      assertTrue(e.getMessage().contains("index -1"));
    }

    try {
      ctx.setArg(2, "value");
      fail("Expected IndexOutOfBoundsException for index >= length");
    } catch (IndexOutOfBoundsException e) {
      assertTrue(e.getMessage().contains("index 2"));
    }
  }

  /** Tests that {@link InterceptContext#setArg(int, Object)} throws when no args available. */
  @Test
  public void testSetArgWithNoArgs() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, null);

    try {
      ctx.setArg(0, "value");
      fail("Expected IllegalStateException when no args available");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("No arguments available"));
    }
  }

  /** Tests that {@link InterceptContext#setReturnValue(Object)} modifies return value correctly. */
  @Test
  public void testSetReturnValue() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, new Object[] {}, 100, false, null);

    ctx.setReturnValue(200);

    assertEquals(200, ctx.getReturnValue());
  }

  /** Tests that {@link InterceptContext#setReturnValue(Object)} throws for void methods. */
  @Test
  public void testSetReturnValueForVoidMethod() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, new Object[] {}, null, true, null);

    try {
      ctx.setReturnValue("should fail");
      fail("Expected InterceptApiMisuseException for void method");
    } catch (InterceptApiMisuseException e) {
      assertTrue(e.getMessage().contains("Cannot set return value for void method"));
    }
  }

  /** Tests that {@link InterceptContext#isArgsModified()} tracks modification state correctly. */
  @Test
  public void testIsArgsModified() {
    Object[] args = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, args);

    assertFalse(ctx.isArgsModified());

    ctx.setArg(0, "HELLO");

    assertTrue(ctx.isArgsModified());
  }

  /**
   * Tests that {@link InterceptContext#isReturnValueModified()} tracks modification state
   * correctly.
   */
  @Test
  public void testIsReturnValueModified() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, new Object[] {}, 100, false, null);

    assertFalse(ctx.isReturnValueModified());

    ctx.setReturnValue(200);

    assertTrue(ctx.isReturnValueModified());
  }

  /** Tests that {@link InterceptContext#getArgs()} returns empty array when args are null. */
  @Test
  public void testGetArgsWithNullArgs() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, null);

    Object[] args = ctx.getArgs();

    assertNotNull(args);
    assertEquals(0, args.length);
  }

  /** Tests that factory methods require non-null parameters. */
  @Test
  public void testFactoryMethodsRequireNonNullParams() {
    try {
      InterceptContext.forBeforePhase(null, InterceptType.BEFORE, peerUuid, new Object[] {});
      fail("Expected NullPointerException for null exec");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("exec"));
    }

    try {
      InterceptContext.forBeforePhase(execMessage, null, peerUuid, new Object[] {});
      fail("Expected NullPointerException for null interceptType");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("interceptType"));
    }

    try {
      InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, null, new Object[] {});
      fail("Expected NullPointerException for null peerUuid");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("interceptedPeerUuid"));
    }
  }

  /**
   * Tests that {@link InterceptContext#setArg(int, Object)} throws
   * InterceptTypeNotSupportedException for BEFORE_ASYNC intercepts.
   */
  @Test
  public void testSetArgThrowsForBeforeAsync() {
    Object[] args = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE_ASYNC, peerUuid, args);

    try {
      ctx.setArg(0, "MODIFIED");
      fail("Expected InterceptTypeNotSupportedException for BEFORE_ASYNC");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("setArg()", e.getOperation());
      assertEquals(InterceptType.BEFORE_ASYNC, e.getInterceptType());
    }
  }

  /**
   * Tests that {@link InterceptContext#setReturnValue(Object)} throws
   * InterceptTypeNotSupportedException for AFTER_ASYNC intercepts.
   */
  @Test
  public void testSetReturnValueThrowsForAfterAsync() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER_ASYNC, peerUuid, new Object[] {}, 100, false, null);

    try {
      ctx.setReturnValue(200);
      fail("Expected InterceptTypeNotSupportedException for AFTER_ASYNC");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("setReturnValue()", e.getOperation());
      assertEquals(InterceptType.AFTER_ASYNC, e.getInterceptType());
    }
  }

  /** Tests that reading args and return value is still allowed for ASYNC intercepts. */
  @Test
  public void testReadOnlyAccessAllowedForAsync() {
    Object[] args = new Object[] {"hello", 42};
    Object returnValue = "result";

    // BEFORE_ASYNC - can read args
    InterceptContext beforeCtx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE_ASYNC, peerUuid, args);
    Object[] readArgs = beforeCtx.getArgs();
    assertArrayEquals(new Object[] {"hello", 42}, readArgs);

    // AFTER_ASYNC - can read return value
    InterceptContext afterCtx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER_ASYNC, peerUuid, args, returnValue, false, null);
    assertEquals("result", afterCtx.getReturnValue());
    assertArrayEquals(new Object[] {"hello", 42}, afterCtx.getArgs());
  }

  // ---- AROUND intercept proceed() tests ----

  /**
   * Tests that proceed() throws InterceptTypeNotSupportedException for non-AROUND intercept
   * (BEFORE).
   */
  @Test
  public void testProceedThrowsForNonAroundIntercept() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, new Object[0]);

    try {
      ctx.proceed();
      fail("Expected InterceptTypeNotSupportedException for non-AROUND intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("proceed()", e.getOperation());
      assertEquals(InterceptType.BEFORE, e.getInterceptType());
    }
  }

  /** Tests that proceed() throws IllegalStateException when called twice. */
  @Test
  public void testProceedThrowsWhenCalledTwice() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    // Set up a mock accessor that returns valid data
    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData("result", null, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    // First call should succeed
    ctx.proceed();

    // Second call should fail
    try {
      ctx.proceed();
      fail("Expected InterceptApiMisuseException when proceed() called twice");
    } catch (InterceptApiMisuseException e) {
      assertTrue(e.getMessage().contains("once"));
    }
  }

  /** Tests that proceed() throws IllegalStateException when accessor not set. */
  @Test
  public void testProceedThrowsWhenAccessorNotSet() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    // Don't set the accessor (neither AroundSocketAccessor nor LocalAroundAccessor)
    try {
      ctx.proceed();
      fail("Expected IllegalStateException when accessor not set");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("No AROUND accessor set"));
    }
  }

  /** Tests that proceed() returns ProceedResult with return value. */
  @Test
  public void testProceedReturnsResultWithReturnValue() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.AROUND, peerUuid, new Object[] {"arg1"});

    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData("computed result", null, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    ProceedResult result = ctx.proceed();

    assertEquals("computed result", result.getReturnValue());
    assertNull(result.getThrownException());
    assertFalse(result.hasException());
  }

  /** Tests that proceed() returns ProceedResult with exception. */
  @Test
  public void testProceedReturnsResultWithException() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    RuntimeException thrownEx = new RuntimeException("method failed");
    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData(null, thrownEx, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    ProceedResult result = ctx.proceed();

    assertNull(result.getReturnValue());
    assertSame(thrownEx, result.getThrownException());
    assertTrue(result.hasException());
  }

  /** Tests that proceed() updates context phase to AFTER. */
  @Test
  public void testProceedUpdatesPhaseToAfter() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    assertEquals(InterceptPhase.BEFORE, ctx.getPhase());

    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData("result", null, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    ctx.proceed();

    assertEquals(InterceptPhase.AFTER, ctx.getPhase());
  }

  /** Tests that proceed() updates context with return value. */
  @Test
  public void testProceedUpdatesContextReturnValue() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    // Before proceed(), getReturnValue() throws InterceptPhaseViolationException (supported but
    // wrong phase)
    try {
      ctx.getReturnValue();
      fail("Expected InterceptPhaseViolationException before proceed()");
    } catch (InterceptPhaseViolationException e) {
      assertEquals("getReturnValue()", e.getOperation());
      assertEquals(InterceptPhase.BEFORE, e.getCurrentPhase());
      assertEquals(InterceptPhase.AFTER, e.getRequiredPhase());
    }

    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData(42, null, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    ctx.proceed();

    // After proceed(), getReturnValue() returns the value
    assertEquals(42, ctx.getReturnValue());
  }

  /** Tests that proceed() updates context with thrown exception. */
  @Test
  public void testProceedUpdatesContextThrownException() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    // Before proceed(), getThrownException() throws InterceptPhaseViolationException (supported but
    // wrong phase)
    try {
      ctx.getThrownException();
      fail("Expected InterceptPhaseViolationException before proceed()");
    } catch (InterceptPhaseViolationException e) {
      assertEquals("getThrownException()", e.getOperation());
      assertEquals(InterceptPhase.BEFORE, e.getCurrentPhase());
      assertEquals(InterceptPhase.AFTER, e.getRequiredPhase());
    }

    RuntimeException exception = new RuntimeException("error");
    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData(null, exception, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    ctx.proceed();

    // After proceed(), getThrownException() returns the exception
    assertSame(exception, ctx.getThrownException());
  }

  /** Tests that isProceedCalled() returns false before proceed() and true after. */
  @Test
  public void testIsProceedCalled() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    assertFalse(ctx.isProceedCalled());

    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData("result", null, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    ctx.proceed();

    assertTrue(ctx.isProceedCalled());
  }

  /** Tests that setAroundAccessor() requires non-null accessor. */
  @Test
  public void testSetAroundAccessorRequiresNonNullAccessor() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    try {
      ctx.setAroundAccessor(null, "callback-123", 30000);
      fail("Expected NullPointerException for null accessor");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("accessor"));
    }
  }

  /** Tests that setAroundAccessor() requires non-null callbackId. */
  @Test
  public void testSetAroundAccessorRequiresNonNullCallbackId() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData("result", null, false);

    try {
      ctx.setAroundAccessor(mockAccessor, null, 30000);
      fail("Expected NullPointerException for null callbackId");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("callbackId"));
    }
  }

  /** Tests that return value can be modified after proceed(). */
  @Test
  public void testSetReturnValueAfterProceed() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData(100, null, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    ctx.proceed();
    assertEquals(100, ctx.getReturnValue());

    // Modify return value after proceed
    ctx.setReturnValue(200);
    assertEquals(200, ctx.getReturnValue());
    assertTrue(ctx.isReturnValueModified());
  }

  /**
   * Tests that setReturnValue(null) still sets returnValueModified=true.
   *
   * <p>This is important for distinguishing between "return value not set" and "return value
   * explicitly set to null".
   */
  @Test
  public void testSetReturnValueNullSetsModifiedFlag() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, new Object[] {}, "original", false, null);

    assertFalse(ctx.isReturnValueModified());

    ctx.setReturnValue(null);

    assertNull(ctx.getReturnValue());
    assertTrue(ctx.isReturnValueModified());
  }

  /** Tests proceed() with void method. */
  @Test
  public void testProceedWithVoidMethod() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData(null, null, true);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    ProceedResult result = ctx.proceed();

    assertNull(result.getReturnValue());
    assertFalse(result.hasException());
    assertTrue(ctx.isVoid());
  }

  /**
   * Tests that {@link InterceptContext#setExceptionToThrow(Throwable)} works for AFTER intercepts.
   */
  @Test
  public void testSetExceptionToThrowWorksForAfter() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, new Object[0], "result", false, null);

    RuntimeException exception = new RuntimeException("test exception");
    ctx.setExceptionToThrow(exception);

    assertSame(exception, ctx.getExceptionToThrow());
  }

  /**
   * Tests that {@link InterceptContext#setExceptionToThrow(Throwable)} works for AROUND intercepts.
   */
  @Test
  public void testSetExceptionToThrowWorksForAround() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, new Object[0]);

    RuntimeException exception = new RuntimeException("test exception");
    ctx.setExceptionToThrow(exception);

    assertSame(exception, ctx.getExceptionToThrow());
  }

  /**
   * Tests that {@link InterceptContext#setExceptionToThrow(Throwable)} throws
   * InterceptTypeNotSupportedException for BEFORE_ASYNC intercepts.
   */
  @Test
  public void testSetExceptionToThrowThrowsForBeforeAsync() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE_ASYNC, peerUuid, new Object[0]);

    try {
      ctx.setExceptionToThrow(new RuntimeException("test"));
      fail("Expected InterceptTypeNotSupportedException for BEFORE_ASYNC");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("setExceptionToThrow()", e.getOperation());
      assertEquals(InterceptType.BEFORE_ASYNC, e.getInterceptType());
    }
  }

  /**
   * Tests that {@link InterceptContext#setExceptionToThrow(Throwable)} throws
   * InterceptTypeNotSupportedException for AFTER_ASYNC intercepts.
   */
  @Test
  public void testSetExceptionToThrowThrowsForAfterAsync() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER_ASYNC, peerUuid, new Object[0], 100, false, null);

    try {
      ctx.setExceptionToThrow(new RuntimeException("test"));
      fail("Expected InterceptTypeNotSupportedException for AFTER_ASYNC");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("setExceptionToThrow()", e.getOperation());
      assertEquals(InterceptType.AFTER_ASYNC, e.getInterceptType());
    }
  }

  /** Tests that {@link InterceptContext#setExceptionToThrow(Throwable)} rejects null. */
  @Test
  public void testSetExceptionToThrowRejectsNull() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, new Object[0], "result", false, null);

    try {
      ctx.setExceptionToThrow(null);
      fail("Expected NullPointerException for null exception");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("exception cannot be null"));
    }
  }

  // ========================================================================
  // Phase/Type Validation Tests - setArg()
  // ========================================================================

  /**
   * Tests that setArg() throws InterceptTypeNotSupportedException for AFTER intercepts.
   *
   * <p>AFTER intercepts cannot mutate arguments because execution has already happened.
   */
  @Test
  public void testSetArgThrowsForAfterIntercept() {
    Object[] args = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, args, "result", false, null);

    try {
      ctx.setArg(0, "MODIFIED");
      fail("Expected InterceptTypeNotSupportedException for AFTER intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("setArg()", e.getOperation());
      assertEquals(InterceptType.AFTER, e.getInterceptType());
    }
  }

  /**
   * Tests that setArg() throws InterceptPhaseViolationException for AROUND intercept after
   * proceed().
   *
   * <p>After proceed() is called, the AROUND intercept is in AFTER phase and cannot mutate args.
   */
  @Test
  public void testSetArgThrowsForAroundAfterProceed() {
    Object[] args = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, args);

    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData("result", null, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    // proceed() transitions to AFTER phase
    ctx.proceed();

    try {
      ctx.setArg(0, "MODIFIED");
      fail("Expected InterceptPhaseViolationException for AROUND after proceed()");
    } catch (InterceptPhaseViolationException e) {
      assertEquals("setArg()", e.getOperation());
      assertEquals(InterceptPhase.AFTER, e.getCurrentPhase());
      assertEquals(InterceptPhase.BEFORE, e.getRequiredPhase());
    }
  }

  /**
   * Tests that setArg() throws InterceptTypeNotSupportedException for AFTER_ASYNC intercepts.
   *
   * <p>ASYNC intercepts are fire-and-forget and cannot affect execution.
   */
  @Test
  public void testSetArgThrowsForAfterAsyncIntercept() {
    Object[] args = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER_ASYNC, peerUuid, args, "result", false, null);

    try {
      ctx.setArg(0, "MODIFIED");
      fail("Expected InterceptTypeNotSupportedException for AFTER_ASYNC intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("setArg()", e.getOperation());
      assertEquals(InterceptType.AFTER_ASYNC, e.getInterceptType());
    }
  }

  // ========================================================================
  // Phase/Type Validation Tests - getReturnValue()
  // ========================================================================

  /**
   * Tests that getReturnValue() throws InterceptTypeNotSupportedException for BEFORE intercepts.
   *
   * <p>BEFORE intercepts never have access to return value in any phase.
   */
  @Test
  public void testGetReturnValueThrowsForBeforeIntercept() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE, peerUuid, new Object[] {"hello"});

    try {
      ctx.getReturnValue();
      fail("Expected InterceptTypeNotSupportedException for BEFORE intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("getReturnValue()", e.getOperation());
      assertEquals(InterceptType.BEFORE, e.getInterceptType());
    }
  }

  /**
   * Tests that getReturnValue() throws InterceptPhaseViolationException for AROUND before
   * proceed().
   *
   * <p>Before proceed() is called, return value is not yet available.
   */
  @Test
  public void testGetReturnValueThrowsForAroundBeforeProceed() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.AROUND, peerUuid, new Object[] {"hello"});

    try {
      ctx.getReturnValue();
      fail("Expected InterceptPhaseViolationException for AROUND before proceed()");
    } catch (InterceptPhaseViolationException e) {
      assertEquals("getReturnValue()", e.getOperation());
      assertEquals(InterceptPhase.BEFORE, e.getCurrentPhase());
      assertEquals(InterceptPhase.AFTER, e.getRequiredPhase());
    }
  }

  /**
   * Tests that getReturnValue() throws InterceptTypeNotSupportedException for BEFORE_ASYNC
   * intercepts.
   *
   * <p>BEFORE_ASYNC intercepts never have access to return value in any phase.
   */
  @Test
  public void testGetReturnValueThrowsForBeforeAsyncIntercept() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE_ASYNC, peerUuid, new Object[] {"hello"});

    try {
      ctx.getReturnValue();
      fail("Expected InterceptTypeNotSupportedException for BEFORE_ASYNC intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("getReturnValue()", e.getOperation());
      assertEquals(InterceptType.BEFORE_ASYNC, e.getInterceptType());
    }
  }

  // ========================================================================
  // Phase/Type Validation Tests - setReturnValue()
  // ========================================================================

  /**
   * Tests that setReturnValue() throws InterceptTypeNotSupportedException for BEFORE intercepts.
   *
   * <p>BEFORE intercepts cannot override return value - use AFTER or AROUND for that.
   */
  @Test
  public void testSetReturnValueThrowsForBeforeIntercept() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE, peerUuid, new Object[] {"hello"});

    try {
      ctx.setReturnValue("overridden");
      fail("Expected InterceptTypeNotSupportedException for BEFORE intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("setReturnValue()", e.getOperation());
      assertEquals(InterceptType.BEFORE, e.getInterceptType());
    }
  }

  /**
   * Tests that setReturnValue() throws InterceptTypeNotSupportedException for BEFORE_ASYNC
   * intercepts.
   *
   * <p>ASYNC intercepts are fire-and-forget and cannot affect execution.
   */
  @Test
  public void testSetReturnValueThrowsForBeforeAsyncIntercept() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE_ASYNC, peerUuid, new Object[] {"hello"});

    try {
      ctx.setReturnValue("overridden");
      fail("Expected InterceptTypeNotSupportedException for BEFORE_ASYNC intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("setReturnValue()", e.getOperation());
      assertEquals(InterceptType.BEFORE_ASYNC, e.getInterceptType());
    }
  }

  // Note: testSetReturnValueThrowsForAfterAsync already exists above

  // ========================================================================
  // Phase/Type Validation Tests - getThrownException()
  // ========================================================================

  /**
   * Tests that getThrownException() throws InterceptTypeNotSupportedException for BEFORE
   * intercepts.
   *
   * <p>BEFORE intercepts never have access to thrown exception in any phase.
   */
  @Test
  public void testGetThrownExceptionThrowsForBeforeIntercept() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE, peerUuid, new Object[] {"hello"});

    try {
      ctx.getThrownException();
      fail("Expected InterceptTypeNotSupportedException for BEFORE intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("getThrownException()", e.getOperation());
      assertEquals(InterceptType.BEFORE, e.getInterceptType());
    }
  }

  /**
   * Tests that getThrownException() throws InterceptPhaseViolationException for AROUND before
   * proceed().
   *
   * <p>Before proceed() is called, thrown exception is not yet available.
   */
  @Test
  public void testGetThrownExceptionThrowsForAroundBeforeProceed() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.AROUND, peerUuid, new Object[] {"hello"});

    try {
      ctx.getThrownException();
      fail("Expected InterceptPhaseViolationException for AROUND before proceed()");
    } catch (InterceptPhaseViolationException e) {
      assertEquals("getThrownException()", e.getOperation());
      assertEquals(InterceptPhase.BEFORE, e.getCurrentPhase());
      assertEquals(InterceptPhase.AFTER, e.getRequiredPhase());
    }
  }

  /**
   * Tests that getThrownException() throws InterceptTypeNotSupportedException for BEFORE_ASYNC
   * intercepts.
   *
   * <p>BEFORE_ASYNC intercepts never have access to thrown exception in any phase.
   */
  @Test
  public void testGetThrownExceptionThrowsForBeforeAsyncIntercept() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE_ASYNC, peerUuid, new Object[] {"hello"});

    try {
      ctx.getThrownException();
      fail("Expected InterceptTypeNotSupportedException for BEFORE_ASYNC intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("getThrownException()", e.getOperation());
      assertEquals(InterceptType.BEFORE_ASYNC, e.getInterceptType());
    }
  }

  // ========================================================================
  // Phase/Type Validation Tests - setExceptionToThrow()
  // ========================================================================

  /**
   * Tests that setExceptionToThrow() succeeds for BEFORE intercepts.
   *
   * <p>BEFORE intercepts can throw exceptions to reject execution before it happens (e.g., security
   * checks, validation, rate limiting).
   */
  @Test
  public void testSetExceptionToThrowSucceedsForBeforeIntercept() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE, peerUuid, new Object[] {"hello"});

    RuntimeException testException = new RuntimeException("security check failed");
    ctx.setExceptionToThrow(testException);

    assertEquals(testException, ctx.getExceptionToThrow());
  }

  // Note: testSetExceptionToThrowThrowsForBeforeAsync and
  // testSetExceptionToThrowThrowsForAfterAsync
  // already exist above

  // ========================================================================
  // Phase/Type Validation Tests - proceed()
  // ========================================================================

  /**
   * Tests that proceed() throws InterceptTypeNotSupportedException for AFTER intercepts.
   *
   * <p>proceed() is only valid for AROUND intercepts.
   */
  @Test
  public void testProceedThrowsForAfterIntercept() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, new Object[0], "result", false, null);

    try {
      ctx.proceed();
      fail("Expected InterceptTypeNotSupportedException for AFTER intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("proceed()", e.getOperation());
      assertEquals(InterceptType.AFTER, e.getInterceptType());
    }
  }

  /**
   * Tests that proceed() throws InterceptTypeNotSupportedException for BEFORE_ASYNC intercepts.
   *
   * <p>proceed() is only valid for AROUND intercepts.
   */
  @Test
  public void testProceedThrowsForBeforeAsyncIntercept() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE_ASYNC, peerUuid, new Object[0]);

    try {
      ctx.proceed();
      fail("Expected InterceptTypeNotSupportedException for BEFORE_ASYNC intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("proceed()", e.getOperation());
      assertEquals(InterceptType.BEFORE_ASYNC, e.getInterceptType());
    }
  }

  /**
   * Tests that proceed() throws InterceptTypeNotSupportedException for AFTER_ASYNC intercepts.
   *
   * <p>proceed() is only valid for AROUND intercepts.
   */
  @Test
  public void testProceedThrowsForAfterAsyncIntercept() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER_ASYNC, peerUuid, new Object[0], "result", false, null);

    try {
      ctx.proceed();
      fail("Expected InterceptTypeNotSupportedException for AFTER_ASYNC intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("proceed()", e.getOperation());
      assertEquals(InterceptType.AFTER_ASYNC, e.getInterceptType());
    }
  }

  // ===== Local Intercept Factory Method Tests =====

  /** Tests that forLocalBeforePhase() creates a valid BEFORE-phase local context. */
  @Test
  public void testForLocalBeforePhase() {
    Object[] args = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forLocalBeforePhase(
            "com.example.MyClass",
            "myMethod",
            List.of("String", "int"),
            InterceptType.BEFORE,
            peerUuid,
            args);

    assertEquals(InterceptPhase.BEFORE, ctx.getPhase());
    assertEquals(InterceptType.BEFORE, ctx.getInterceptType());
    assertEquals(peerUuid, ctx.getInterceptedPeerUuid());
    assertNull(ctx.getExec()); // Local intercepts have no ExecMessage
    assertTrue(ctx.isLocalIntercept());
    assertNotNull(ctx.getLocalMetadata());
    assertEquals("com.example.MyClass", ctx.getLocalMetadata().className());
    assertEquals("myMethod", ctx.getLocalMetadata().methodName());
    assertEquals(List.of("String", "int"), ctx.getLocalMetadata().paramTypes());
    assertArrayEquals(new Object[] {"hello", 42}, ctx.getArgs());
  }

  /** Tests that forLocalBeforePhase() copies the args array to prevent external modification. */
  @Test
  public void testForLocalBeforePhaseArgsAreCopied() {
    Object[] args = new Object[] {"original"};

    InterceptContext ctx =
        InterceptContext.forLocalBeforePhase(
            "com.example.MyClass",
            "myMethod",
            List.of("String"),
            InterceptType.BEFORE,
            peerUuid,
            args);

    // Modify original array
    args[0] = "modified";

    // Context should have the original value
    assertEquals("original", ctx.getArgs()[0]);
  }

  /** Tests that forLocalAfterPhase() creates a valid AFTER-phase local context. */
  @Test
  public void testForLocalAfterPhase() {
    Object[] args = new Object[] {"arg1"};
    Object returnValue = "result";

    InterceptContext ctx =
        InterceptContext.forLocalAfterPhase(
            "com.example.MyClass",
            "myMethod",
            List.of("String"),
            InterceptType.AFTER,
            peerUuid,
            args,
            returnValue,
            false,
            null);

    assertEquals(InterceptPhase.AFTER, ctx.getPhase());
    assertEquals(InterceptType.AFTER, ctx.getInterceptType());
    assertEquals(peerUuid, ctx.getInterceptedPeerUuid());
    assertNull(ctx.getExec()); // Local intercepts have no ExecMessage
    assertTrue(ctx.isLocalIntercept());
    assertEquals("result", ctx.getReturnValue());
    assertFalse(ctx.isVoid());
    assertNull(ctx.getThrownException());
  }

  /** Tests that forLocalAfterPhase() with exception captures the thrown exception. */
  @Test
  public void testForLocalAfterPhaseWithException() {
    RuntimeException exception = new RuntimeException("test error");

    InterceptContext ctx =
        InterceptContext.forLocalAfterPhase(
            "com.example.MyClass",
            "myMethod",
            List.of(),
            InterceptType.AFTER,
            peerUuid,
            null,
            null,
            false,
            exception);

    assertSame(exception, ctx.getThrownException());
  }

  /** Tests that forLocalAroundPhase() creates a valid AROUND-phase local context. */
  @Test
  public void testForLocalAroundPhase() {
    Object[] args = new Object[] {"arg1", 100};

    InterceptContext ctx =
        InterceptContext.forLocalAroundPhase(
            "com.example.Calculator", "add", List.of("int", "int"), peerUuid, args);

    assertEquals(InterceptPhase.BEFORE, ctx.getPhase());
    assertEquals(InterceptType.AROUND, ctx.getInterceptType());
    assertEquals(peerUuid, ctx.getInterceptedPeerUuid());
    assertNull(ctx.getExec()); // Local intercepts have no ExecMessage
    assertTrue(ctx.isLocalIntercept());
    assertArrayEquals(new Object[] {"arg1", 100}, ctx.getArgs());
    assertFalse(ctx.isProceedCalled());
  }

  // ===== Local AROUND Accessor Tests =====

  /** Tests that setLocalAroundAccessor() sets the accessor correctly. */
  @Test
  public void testSetLocalAroundAccessor() {
    InterceptContext ctx =
        InterceptContext.forLocalAroundPhase(
            "com.example.MyClass", "myMethod", List.of(), peerUuid, null);

    LocalAroundAccessor accessor = (args) -> new AfterPhaseData("result", null, false);
    ctx.setLocalAroundAccessor(accessor);

    // Verify proceed() works with local accessor
    ProceedResult result = ctx.proceed();
    assertEquals("result", result.getReturnValue());
    assertFalse(result.hasException());
  }

  /** Tests that setLocalAroundAccessor() requires non-null accessor. */
  @Test
  public void testSetLocalAroundAccessorRequiresNonNull() {
    InterceptContext ctx =
        InterceptContext.forLocalAroundPhase(
            "com.example.MyClass", "myMethod", List.of(), peerUuid, null);

    try {
      ctx.setLocalAroundAccessor(null);
      fail("Expected NullPointerException for null accessor");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("accessor"));
    }
  }

  /** Tests that proceed() uses LocalAroundAccessor when set. */
  @Test
  public void testProceedWithLocalAroundAccessor() {
    InterceptContext ctx =
        InterceptContext.forLocalAroundPhase(
            "com.example.Calculator", "add", List.of("int", "int"), peerUuid, new Object[] {5, 3});

    // Local accessor that simulates adding two numbers
    LocalAroundAccessor accessor =
        (args) -> {
          int a = (args != null && args.length > 0) ? (Integer) args[0] : 0;
          int b = (args != null && args.length > 1) ? (Integer) args[1] : 0;
          return new AfterPhaseData(a + b, null, false);
        };
    ctx.setLocalAroundAccessor(accessor);

    ProceedResult result = ctx.proceed();

    assertEquals(8, result.getReturnValue());
    assertFalse(result.hasException());
    assertTrue(ctx.isProceedCalled());
    assertEquals(InterceptPhase.AFTER, ctx.getPhase());
  }

  /** Tests that proceed() with local accessor handles exceptions. */
  @Test
  public void testProceedWithLocalAroundAccessorException() {
    InterceptContext ctx =
        InterceptContext.forLocalAroundPhase(
            "com.example.MyClass", "myMethod", List.of(), peerUuid, null);

    RuntimeException exception = new RuntimeException("method threw");
    LocalAroundAccessor accessor = (args) -> new AfterPhaseData(null, exception, false);
    ctx.setLocalAroundAccessor(accessor);

    ProceedResult result = ctx.proceed();

    assertNull(result.getReturnValue());
    assertTrue(result.hasException());
    assertSame(exception, result.getThrownException());
    assertSame(exception, ctx.getThrownException());
  }

  /** Tests that proceed() with local accessor handles void methods. */
  @Test
  public void testProceedWithLocalAroundAccessorVoidMethod() {
    InterceptContext ctx =
        InterceptContext.forLocalAroundPhase(
            "com.example.MyClass", "voidMethod", List.of(), peerUuid, null);

    LocalAroundAccessor accessor = (args) -> new AfterPhaseData(null, null, true);
    ctx.setLocalAroundAccessor(accessor);

    ProceedResult result = ctx.proceed();

    assertNull(result.getReturnValue());
    assertFalse(result.hasException());
    assertTrue(ctx.isVoid());
  }

  /** Tests that local accessor takes precedence over remote accessor when both are set. */
  @Test
  public void testLocalAccessorTakesPrecedenceOverRemote() {
    InterceptContext ctx =
        InterceptContext.forLocalAroundPhase(
            "com.example.MyClass", "myMethod", List.of(), peerUuid, null);

    // Set both accessors
    AroundSocketAccessor remoteAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData("remote", null, false);
    ctx.setAroundAccessor(remoteAccessor, "callback-123", 30000);

    LocalAroundAccessor localAccessor = (args) -> new AfterPhaseData("local", null, false);
    ctx.setLocalAroundAccessor(localAccessor);

    ProceedResult result = ctx.proceed();

    // Local accessor should be used
    assertEquals("local", result.getReturnValue());
  }

  /** Tests that args can be modified before proceed() and are passed to local accessor. */
  @Test
  public void testArgsPassedToLocalAccessorAfterModification() {
    InterceptContext ctx =
        InterceptContext.forLocalAroundPhase(
            "com.example.MyClass",
            "myMethod",
            List.of("String"),
            peerUuid,
            new Object[] {"original"});

    // Modify args before proceed()
    ctx.setArg(0, "modified");

    final Object[] capturedArgs = new Object[1];
    LocalAroundAccessor accessor =
        (args) -> {
          capturedArgs[0] = args != null ? args[0] : null;
          return new AfterPhaseData("result", null, false);
        };
    ctx.setLocalAroundAccessor(accessor);

    ctx.proceed();

    assertEquals("modified", capturedArgs[0]);
  }

  // ========================================================================
  // Custom Exception Type Tests - #270
  // ========================================================================

  /**
   * Tests that getReturnValue() throws InterceptTypeNotSupportedException for BEFORE intercepts.
   *
   * <p>Verifies that the new custom exception type is thrown instead of
   * UnsupportedOperationException when attempting to access return value in a BEFORE intercept.
   */
  @Test
  public void shouldThrowInterceptTypeNotSupportedForGetReturnValueInBeforeIntercept() {
    // Given: A BEFORE intercept context
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE, peerUuid, new Object[] {"hello"});

    // When: getReturnValue() is called
    // Then: InterceptTypeNotSupportedException is thrown with operation="getReturnValue()" and
    // interceptType=BEFORE
    try {
      ctx.getReturnValue();
      fail("Expected InterceptTypeNotSupportedException for BEFORE intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("getReturnValue()", e.getOperation());
      assertEquals(InterceptType.BEFORE, e.getInterceptType());
      assertNull(e.getInterceptPhase());
    }
  }

  /**
   * Tests that getReturnValue() throws InterceptPhaseViolationException for AROUND before
   * proceed().
   *
   * <p>Verifies that the new custom exception type is thrown instead of IllegalStateException when
   * attempting to access return value in an AROUND intercept before proceed() is called.
   */
  @Test
  public void shouldThrowInterceptPhaseViolationForGetReturnValueBeforeProceed() {
    // Given: An AROUND intercept context in BEFORE phase (proceed() not called)
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.AROUND, peerUuid, new Object[] {"hello"});

    // When: getReturnValue() is called
    // Then: InterceptPhaseViolationException is thrown with operation="getReturnValue()",
    // currentPhase=BEFORE, requiredPhase=AFTER
    try {
      ctx.getReturnValue();
      fail("Expected InterceptPhaseViolationException for AROUND before proceed()");
    } catch (InterceptPhaseViolationException e) {
      assertEquals("getReturnValue()", e.getOperation());
      assertEquals(InterceptPhase.BEFORE, e.getCurrentPhase());
      assertEquals(InterceptPhase.AFTER, e.getRequiredPhase());
    }
  }

  /**
   * Tests that setArg() throws InterceptTypeNotSupportedException for AFTER intercepts.
   *
   * <p>Verifies that the new custom exception type is thrown instead of
   * UnsupportedOperationException when attempting to modify arguments in an AFTER intercept.
   */
  @Test
  public void shouldThrowInterceptTypeNotSupportedForSetArgInAfterIntercept() {
    // Given: An AFTER intercept context
    Object[] args = new Object[] {"hello", 42};
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER, peerUuid, args, "result", false, null);

    // When: setArg() is called
    // Then: InterceptTypeNotSupportedException is thrown with operation="setArg()" and
    // interceptType=AFTER
    try {
      ctx.setArg(0, "MODIFIED");
      fail("Expected InterceptTypeNotSupportedException for AFTER intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("setArg()", e.getOperation());
      assertEquals(InterceptType.AFTER, e.getInterceptType());
      assertNull(e.getInterceptPhase());
    }
  }

  /**
   * Tests that setArg() throws InterceptPhaseViolationException for AROUND after proceed().
   *
   * <p>Verifies that the new custom exception type is thrown instead of IllegalStateException when
   * attempting to modify arguments in an AROUND intercept after proceed() is called.
   */
  @Test
  public void shouldThrowInterceptPhaseViolationForSetArgAfterProceed() {
    // Given: An AROUND intercept context where proceed() has been called (now in AFTER phase)
    Object[] args = new Object[] {"hello", 42};
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.AROUND, peerUuid, args);

    AroundSocketAccessor mockAccessor =
        (beforeResponse, timeoutMs) -> new AfterPhaseData("result", null, false);
    ctx.setAroundAccessor(mockAccessor, "callback-123", 30000);

    // proceed() transitions to AFTER phase
    ctx.proceed();

    // When: setArg() is called
    // Then: InterceptPhaseViolationException is thrown with operation="setArg()",
    // currentPhase=AFTER, requiredPhase=BEFORE
    try {
      ctx.setArg(0, "MODIFIED");
      fail("Expected InterceptPhaseViolationException for AROUND after proceed()");
    } catch (InterceptPhaseViolationException e) {
      assertEquals("setArg()", e.getOperation());
      assertEquals(InterceptPhase.AFTER, e.getCurrentPhase());
      assertEquals(InterceptPhase.BEFORE, e.getRequiredPhase());
    }
  }

  /**
   * Tests that proceed() throws InterceptTypeNotSupportedException for non-AROUND intercepts.
   *
   * <p>Verifies that the new custom exception type is thrown instead of
   * UnsupportedOperationException when attempting to call proceed() in a BEFORE intercept (or any
   * non-AROUND intercept type).
   */
  @Test
  public void shouldThrowInterceptTypeNotSupportedForProceedInNonAroundIntercept() {
    // Given: A BEFORE intercept context (non-AROUND type)
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, new Object[0]);

    // When: proceed() is called
    // Then: InterceptTypeNotSupportedException is thrown with operation="proceed()" and
    // interceptType=BEFORE
    try {
      ctx.proceed();
      fail("Expected InterceptTypeNotSupportedException for non-AROUND intercept");
    } catch (InterceptTypeNotSupportedException e) {
      assertEquals("proceed()", e.getOperation());
      assertEquals(InterceptType.BEFORE, e.getInterceptType());
      assertNull(e.getInterceptPhase());
    }
  }
}
