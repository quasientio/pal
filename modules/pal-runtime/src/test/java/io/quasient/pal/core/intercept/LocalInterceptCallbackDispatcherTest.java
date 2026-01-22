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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.LocalAroundAccessor;
import io.quasient.pal.core.intercept.InterceptCallbackDispatcher.ConsolidatedCallbackResponse;
import io.quasient.pal.core.intercept.LocalInterceptCallbackDispatcher.LocalAroundCallbackState;
import io.quasient.pal.core.intercept.LocalInterceptCallbackDispatcher.LocalAroundConsolidatedResponse;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@link LocalInterceptCallbackDispatcher}.
 *
 * <p>Verifies local intercept callback dispatch for BEFORE, AFTER, and AROUND phases.
 */
@SuppressWarnings("StaticAssignmentOfThrowable")
public class LocalInterceptCallbackDispatcherTest {

  private CallbackResolver callbackResolver;
  private LocalInterceptCallbackDispatcher dispatcher;
  private ExecutorService asyncExecutor;

  private static final String TEST_CLASS = "com.example.Calculator";
  private static final String TEST_METHOD = "add";
  private static final List<String> TEST_PARAM_TYPES = List.of("int", "int");
  private static final String TEST_PEER_UUID = "test-peer-uuid";
  private static final String CALLBACK_CLASS =
      "io.quasient.pal.core.intercept.LocalInterceptCallbackDispatcherTest$TestCallbacks";

  /** Sets up test fixtures. */
  @Before
  public void setUp() {
    callbackResolver = new CallbackResolver();
    asyncExecutor = Executors.newSingleThreadExecutor();
    dispatcher = new LocalInterceptCallbackDispatcher(callbackResolver, asyncExecutor);
    // Reset static state
    TestCallbacks.lastContext = null;
    TestCallbacks.exceptionToThrow = null;
  }

  /** Cleans up executor after test. */
  @After
  public void tearDown() {
    if (asyncExecutor != null) {
      asyncExecutor.shutdownNow();
    }
  }

  // ===== BEFORE Callback Tests =====

  /** Tests that empty intercept list returns proceed response. */
  @Test
  public void testSendLocalBeforeCallbacksEmptyList() {
    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalBeforeCallbacks(
            new ArrayList<>(),
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    assertTrue(response.shouldProceed());
    assertFalse(response.hasArgMutations());
    assertFalse(response.shouldThrowException());
  }

  /** Tests that BEFORE callback is invoked and can mutate args. */
  @Test
  public void testSendLocalBeforeCallbackMutatesArgs() {
    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "doubleFirstArg");
    List<InterceptMessage> intercepts = List.of(intercept);

    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {5, 3},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    assertTrue(response.shouldProceed());
    assertTrue(response.hasArgMutations());
    assertEquals(10, response.getMutatedArgs().get(0));
  }

  /** Tests that BEFORE callback can throw exception. */
  @Test
  public void testSendLocalBeforeCallbackThrowsException() {
    RuntimeException expectedException = new RuntimeException("BEFORE exception");
    TestCallbacks.exceptionToThrow = expectedException;

    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "throwException");
    List<InterceptMessage> intercepts = List.of(intercept);

    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    assertTrue(response.shouldThrowException());
    assertEquals(expectedException, response.getExceptionToThrow());
  }

  // ===== AFTER Callback Tests =====

  /** Tests that empty intercept list returns proceed response. */
  @Test
  public void testSendLocalAfterCallbacksEmptyList() {
    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalAfterCallbacks(
            new ArrayList<>(),
            new Object[] {1, 2},
            42,
            false,
            null,
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    assertTrue(response.shouldProceed());
    assertFalse(response.hasReturnValueOverride());
    assertFalse(response.shouldThrowException());
  }

  /** Tests that AFTER callback is invoked and can override return value. */
  @Test
  public void testSendLocalAfterCallbackOverridesReturnValue() {
    InterceptMessage intercept = createIntercept(InterceptType.AFTER, "doubleReturnValue");
    List<InterceptMessage> intercepts = List.of(intercept);

    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalAfterCallbacks(
            intercepts,
            new Object[] {5, 3},
            8, // original return value
            false,
            null,
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    assertTrue(response.hasReturnValueOverride());
    assertEquals(16, response.getOverriddenReturnValue());
  }

  /** Tests that AFTER callback can throw exception. */
  @Test
  public void testSendLocalAfterCallbackThrowsException() {
    RuntimeException expectedException = new RuntimeException("AFTER exception");
    TestCallbacks.exceptionToThrow = expectedException;

    InterceptMessage intercept = createIntercept(InterceptType.AFTER, "throwException");
    List<InterceptMessage> intercepts = List.of(intercept);

    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalAfterCallbacks(
            intercepts,
            new Object[] {1, 2},
            42,
            false,
            null,
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    assertTrue(response.shouldThrowException());
    assertEquals(expectedException, response.getExceptionToThrow());
  }

  // ===== AROUND Callback Tests =====

  /** Tests that empty AROUND intercept list returns proceed response. */
  @Test
  public void testSendLocalAroundCallbacksEmptyList() {
    LocalAroundAccessor accessor = (args) -> new AfterPhaseData(100, null, false);

    LocalAroundConsolidatedResponse response =
        dispatcher.sendLocalAroundCallbacks(
            new ArrayList<>(),
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            accessor);

    assertTrue(response.shouldProceed());
    assertFalse(response.shouldThrowException());
    assertTrue(response.getPendingCallbacks().isEmpty());
  }

  /** Tests that AROUND callback that calls proceed() is tracked. */
  @Test
  public void testSendLocalAroundCallbackWithProceed() {
    InterceptMessage intercept = createIntercept(InterceptType.AROUND, "callProceed");
    List<InterceptMessage> intercepts = List.of(intercept);

    LocalAroundAccessor accessor = (args) -> new AfterPhaseData(100, null, false);

    LocalAroundConsolidatedResponse response =
        dispatcher.sendLocalAroundCallbacks(
            intercepts,
            new Object[] {5, 3},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            accessor);

    assertTrue(response.shouldProceed());
    assertFalse(response.shouldThrowException());
    assertEquals(1, response.getPendingCallbacks().size());

    // Verify the pending callback state
    LocalAroundCallbackState state = response.getPendingCallbacks().get(0);
    assertNotNull(state.context());
    assertTrue(state.context().isProceedCalled());
  }

  /** Tests that AROUND callback that skips proceed() returns skip response. */
  @Test
  public void testSendLocalAroundCallbackSkipsProceed() {
    InterceptMessage intercept = createIntercept(InterceptType.AROUND, "skipProceed");
    List<InterceptMessage> intercepts = List.of(intercept);

    LocalAroundAccessor accessor = (args) -> new AfterPhaseData(100, null, false);

    LocalAroundConsolidatedResponse response =
        dispatcher.sendLocalAroundCallbacks(
            intercepts,
            new Object[] {5, 3},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            accessor);

    assertFalse(response.shouldProceed());
    assertEquals(999, response.getSkipReturnValue());
  }

  /** Tests that AROUND callback can throw exception. */
  @Test
  public void testSendLocalAroundCallbackThrowsException() {
    RuntimeException expectedException = new RuntimeException("AROUND exception");
    TestCallbacks.exceptionToThrow = expectedException;

    InterceptMessage intercept = createIntercept(InterceptType.AROUND, "throwException");
    List<InterceptMessage> intercepts = List.of(intercept);

    LocalAroundAccessor accessor = (args) -> new AfterPhaseData(100, null, false);

    LocalAroundConsolidatedResponse response =
        dispatcher.sendLocalAroundCallbacks(
            intercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            accessor);

    assertTrue(response.shouldThrowException());
    assertEquals(expectedException, response.getExceptionToThrow());
  }

  // ===== AROUND AFTER Callback Tests =====

  /** Tests that AROUND AFTER with empty list returns proceed response. */
  @Test
  public void testSendLocalAroundAfterCallbacksEmptyList() {
    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalAroundAfterCallbacks(new ArrayList<>(), 100);

    assertTrue(response.shouldProceed());
    assertFalse(response.hasReturnValueOverride());
    assertFalse(response.shouldThrowException());
  }

  /** Tests that AROUND AFTER with null list returns proceed response. */
  @Test
  public void testSendLocalAroundAfterCallbacksNullList() {
    ConsolidatedCallbackResponse response = dispatcher.sendLocalAroundAfterCallbacks(null, 100);

    assertTrue(response.shouldProceed());
    assertFalse(response.hasReturnValueOverride());
    assertFalse(response.shouldThrowException());
  }

  /** Tests that AROUND AFTER detects return value override from callback. */
  @Test
  public void testSendLocalAroundAfterCallbackOverridesReturnValue() {
    InterceptMessage intercept = createIntercept(InterceptType.AROUND, "modifyReturnAfterProceed");
    List<InterceptMessage> intercepts = List.of(intercept);

    LocalAroundAccessor accessor = (args) -> new AfterPhaseData(100, null, false);

    // First send AROUND callbacks to get pending callbacks
    LocalAroundConsolidatedResponse aroundResponse =
        dispatcher.sendLocalAroundCallbacks(
            intercepts,
            new Object[] {5, 3},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            accessor);

    // Then send AROUND AFTER callbacks
    ConsolidatedCallbackResponse afterResponse =
        dispatcher.sendLocalAroundAfterCallbacks(aroundResponse.getPendingCallbacks(), 100);

    assertTrue(afterResponse.hasReturnValueOverride());
    assertEquals(999, afterResponse.getOverriddenReturnValue());
  }

  // ===== Helper Methods =====

  /**
   * Creates an intercept message with a static callback method.
   *
   * @param type the intercept type
   * @param methodName the callback method name in TestCallbacks class
   * @return the intercept message
   */
  private InterceptMessage createIntercept(InterceptType type, String methodName) {
    InterceptMessage msg = new InterceptMessage();
    msg.setInterceptType(type.toByte());
    msg.setCallbackClass(CALLBACK_CLASS);
    msg.setCallbackMethod(methodName);
    return msg;
  }

  // ===== Exception Handling Tests =====

  /**
   * Tests that API misuse exceptions (InterceptTypeNotSupportedException) are filtered and not
   * propagated.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldFilterApiMisuseExceptionAndNotPropagate]
   */
  @Test
  @Ignore("Awaiting implementation in #288")
  public void shouldFilterApiMisuseExceptionAndNotPropagate() {
    // Given: Callback throws InterceptTypeNotSupportedException (API misuse)
    // When: Executing callback via dispatcher
    // Then: Exception is logged but not propagated; method continues normally

    // TODO: Implement after #288 provides the implementation
    // 1. Create a callback that throws InterceptTypeNotSupportedException
    // 2. Send callback through dispatcher
    // 3. Verify shouldProceed() returns true
    // 4. Verify shouldThrowException() returns false
    // 5. Verify exception was logged (check log output if possible)
    fail("Not yet implemented");
  }

  /**
   * Tests that explicit exceptions are propagated with PROPAGATE_ALL policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldPropagateExplicitExceptionWithPropagateAllPolicy]
   */
  @Test
  @Ignore("Awaiting implementation in #288")
  public void shouldPropagateExplicitExceptionWithPropagateAllPolicy() {
    // Given: Exception propagation policy is PROPAGATE_ALL
    //        Callback sets exception via setExceptionToThrow()
    // When: Executing callback via dispatcher
    // Then: Exception is propagated to caller

    // TODO: Implement after #288 provides the implementation
    // 1. Configure dispatcher/intercept with PROPAGATE_ALL policy
    // 2. Create callback that calls ctx.setExceptionToThrow(new RuntimeException(...))
    // 3. Send callback through dispatcher
    // 4. Verify shouldThrowException() returns true
    // 5. Verify getExceptionToThrow() returns the expected exception
    fail("Not yet implemented");
  }

  /**
   * Tests that direct throws are swallowed with PROPAGATE_EXPLICIT_ONLY policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldSwallowDirectThrowWithPropagateExplicitOnlyPolicy]
   */
  @Test
  @Ignore("Awaiting implementation in #288")
  public void shouldSwallowDirectThrowWithPropagateExplicitOnlyPolicy() {
    // Given: Exception propagation policy is PROPAGATE_EXPLICIT_ONLY
    //        Callback throws exception directly (not via setExceptionToThrow)
    // When: Executing callback via dispatcher
    // Then: Exception is logged but swallowed; method continues normally

    // TODO: Implement after #288 provides the implementation
    // 1. Configure dispatcher/intercept with PROPAGATE_EXPLICIT_ONLY policy
    // 2. Create callback that throws exception directly (throw new RuntimeException(...))
    // 3. Send callback through dispatcher
    // 4. Verify shouldProceed() returns true
    // 5. Verify shouldThrowException() returns false
    // 6. Verify exception was logged
    fail("Not yet implemented");
  }

  /**
   * Tests that all exceptions are swallowed with SWALLOW_ALL policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldSwallowAllWithSwallowAllPolicy]
   */
  @Test
  @Ignore("Awaiting implementation in #288")
  public void shouldSwallowAllWithSwallowAllPolicy() {
    // Given: Exception propagation policy is SWALLOW_ALL
    //        Callback throws any exception (either direct throw or via setExceptionToThrow)
    // When: Executing callback via dispatcher
    // Then: Exception is logged but swallowed; method continues normally

    // TODO: Implement after #288 provides the implementation
    // 1. Configure dispatcher/intercept with SWALLOW_ALL policy
    // 2. Create callback that throws exception (try both direct and via setExceptionToThrow)
    // 3. Send callback through dispatcher
    // 4. Verify shouldProceed() returns true
    // 5. Verify shouldThrowException() returns false
    // 6. Verify exception was logged
    fail("Not yet implemented");
  }

  /**
   * Tests that checked exceptions are validated and wrapped with WRAP policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldValidateCheckedExceptionAndWrap]
   */
  @Test
  @Ignore("Awaiting implementation in #288")
  public void shouldValidateCheckedExceptionAndWrap() {
    // Given: Checked exception policy is WRAP
    //        Callback throws SQLException (checked exception)
    //        Intercepted method declares IOException
    // When: Executing callback via dispatcher
    // Then: SQLException is wrapped in RuntimeException before propagation

    // TODO: Implement after #288 provides the implementation
    // 1. Configure dispatcher/intercept with WRAP checked exception policy
    // 2. Create a test method signature that declares IOException
    // 3. Create callback that sets SQLException via setExceptionToThrow()
    // 4. Send callback through dispatcher
    // 5. Verify shouldThrowException() returns true
    // 6. Verify getExceptionToThrow() is RuntimeException (or wrapper)
    // 7. Verify getCause() returns the original SQLException
    fail("Not yet implemented");
  }

  /**
   * Tests that checked exceptions are validated and rejected with REJECT policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldValidateCheckedExceptionAndReject]
   */
  @Test
  @Ignore("Awaiting implementation in #288")
  public void shouldValidateCheckedExceptionAndReject() {
    // Given: Checked exception policy is REJECT
    //        Callback throws SQLException (checked exception)
    //        Intercepted method declares IOException
    // When: Executing callback via dispatcher
    // Then: InvalidCallbackExceptionException is thrown

    // TODO: Implement after #288 provides the implementation
    // 1. Configure dispatcher/intercept with REJECT checked exception policy
    // 2. Create a test method signature that declares IOException
    // 3. Create callback that sets SQLException via setExceptionToThrow()
    // 4. Send callback through dispatcher
    // 5. Verify shouldThrowException() returns true
    // 6. Verify getExceptionToThrow() is InvalidCallbackExceptionException
    // 7. Verify the cause is the original SQLException
    fail("Not yet implemented");
  }

  // ===== Test Callbacks Class =====

  /** Test callbacks class with static methods for testing. */
  public static class TestCallbacks {

    /** Last context passed to a callback (for verification). */
    static InterceptContext lastContext;

    /** Exception to throw when throwException callback is called. */
    static RuntimeException exceptionToThrow;

    /** Doubles the first argument. */
    public static InterceptCallbackResponse doubleFirstArg(InterceptContext ctx) {
      lastContext = ctx;
      Object[] args = ctx.getArgs();
      if (args != null && args.length > 0 && args[0] instanceof Integer val) {
        ctx.setArg(0, val * 2);
      }
      return new InterceptCallbackResponse();
    }

    /** Throws an exception if exceptionToThrow is set. */
    public static InterceptCallbackResponse throwException(InterceptContext ctx) {
      lastContext = ctx;
      if (exceptionToThrow != null) {
        ctx.setExceptionToThrow(exceptionToThrow);
      }
      return new InterceptCallbackResponse();
    }

    /** Doubles the return value. */
    public static InterceptCallbackResponse doubleReturnValue(InterceptContext ctx) {
      lastContext = ctx;
      Object returnValue = ctx.getReturnValue();
      if (returnValue instanceof Integer val) {
        ctx.setReturnValue(val * 2);
      }
      return new InterceptCallbackResponse();
    }

    /** Calls proceed() for AROUND intercepts. */
    public static InterceptCallbackResponse callProceed(InterceptContext ctx) {
      lastContext = ctx;
      ctx.proceed();
      return new InterceptCallbackResponse();
    }

    /** Skips proceed() and sets a cached return value. */
    public static InterceptCallbackResponse skipProceed(InterceptContext ctx) {
      lastContext = ctx;
      // Don't call proceed() - return cached value
      ctx.setReturnValue(999);
      return new InterceptCallbackResponse();
    }

    /** Calls proceed() and then modifies the return value. */
    public static InterceptCallbackResponse modifyReturnAfterProceed(InterceptContext ctx) {
      lastContext = ctx;
      ctx.proceed();
      // Override return value after proceed
      ctx.setReturnValue(999);
      return new InterceptCallbackResponse();
    }
  }
}
