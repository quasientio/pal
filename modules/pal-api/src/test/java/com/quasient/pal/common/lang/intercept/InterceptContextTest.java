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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.messages.colfer.ExecMessage;
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
    assertNull(ctx.getReturnValue());
    assertNull(ctx.getThrownException());
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
      fail("Expected IllegalStateException for void method");
    } catch (IllegalStateException e) {
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
   * Tests that {@link InterceptContext#setArg(int, Object)} throws UnsupportedOperationException
   * for BEFORE_ASYNC intercepts.
   */
  @Test
  public void testSetArgThrowsForBeforeAsync() {
    Object[] args = new Object[] {"hello", 42};

    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE_ASYNC, peerUuid, args);

    try {
      ctx.setArg(0, "MODIFIED");
      fail("Expected UnsupportedOperationException for BEFORE_ASYNC");
    } catch (UnsupportedOperationException e) {
      assertTrue(e.getMessage().contains("BEFORE_ASYNC"));
      assertTrue(e.getMessage().contains("fire-and-forget"));
    }
  }

  /**
   * Tests that {@link InterceptContext#setReturnValue(Object)} throws UnsupportedOperationException
   * for AFTER_ASYNC intercepts.
   */
  @Test
  public void testSetReturnValueThrowsForAfterAsync() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER_ASYNC, peerUuid, new Object[] {}, 100, false, null);

    try {
      ctx.setReturnValue(200);
      fail("Expected UnsupportedOperationException for AFTER_ASYNC");
    } catch (UnsupportedOperationException e) {
      assertTrue(e.getMessage().contains("AFTER_ASYNC"));
      assertTrue(e.getMessage().contains("fire-and-forget"));
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

  /**
   * Tests that {@link InterceptContext#setExceptionToThrow(Throwable)} works for SYNC intercepts.
   */
  @Test
  public void testSetExceptionToThrow() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, new Object[0]);

    RuntimeException exception = new RuntimeException("test exception");
    ctx.setExceptionToThrow(exception);

    assertSame(exception, ctx.getExceptionToThrow());
  }

  /**
   * Tests that {@link InterceptContext#setExceptionToThrow(Throwable)} throws
   * UnsupportedOperationException for BEFORE_ASYNC intercepts.
   */
  @Test
  public void testSetExceptionToThrowThrowsForBeforeAsync() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(
            execMessage, InterceptType.BEFORE_ASYNC, peerUuid, new Object[0]);

    try {
      ctx.setExceptionToThrow(new RuntimeException("test"));
      fail("Expected UnsupportedOperationException for BEFORE_ASYNC");
    } catch (UnsupportedOperationException e) {
      assertTrue(e.getMessage().contains("BEFORE_ASYNC"));
      assertTrue(e.getMessage().contains("fire-and-forget"));
    }
  }

  /**
   * Tests that {@link InterceptContext#setExceptionToThrow(Throwable)} throws
   * UnsupportedOperationException for AFTER_ASYNC intercepts.
   */
  @Test
  public void testSetExceptionToThrowThrowsForAfterAsync() {
    InterceptContext ctx =
        InterceptContext.forAfterPhase(
            execMessage, InterceptType.AFTER_ASYNC, peerUuid, new Object[0], 100, false, null);

    try {
      ctx.setExceptionToThrow(new RuntimeException("test"));
      fail("Expected UnsupportedOperationException for AFTER_ASYNC");
    } catch (UnsupportedOperationException e) {
      assertTrue(e.getMessage().contains("AFTER_ASYNC"));
      assertTrue(e.getMessage().contains("fire-and-forget"));
    }
  }

  /** Tests that {@link InterceptContext#setExceptionToThrow(Throwable)} rejects null. */
  @Test
  public void testSetExceptionToThrowRejectsNull() {
    InterceptContext ctx =
        InterceptContext.forBeforePhase(execMessage, InterceptType.BEFORE, peerUuid, new Object[0]);

    try {
      ctx.setExceptionToThrow(null);
      fail("Expected NullPointerException for null exception");
    } catch (NullPointerException e) {
      assertTrue(e.getMessage().contains("exception cannot be null"));
    }
  }
}
