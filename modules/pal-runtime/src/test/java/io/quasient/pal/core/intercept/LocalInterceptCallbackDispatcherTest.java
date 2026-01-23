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

import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptApiMisuseException;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InvalidCallbackExceptionException;
import io.quasient.pal.common.lang.intercept.LocalAroundAccessor;
import io.quasient.pal.core.intercept.InterceptCallbackDispatcher.ConsolidatedCallbackResponse;
import io.quasient.pal.core.intercept.LocalInterceptCallbackDispatcher.LocalAroundCallbackState;
import io.quasient.pal.core.intercept.LocalInterceptCallbackDispatcher.LocalAroundConsolidatedResponse;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.After;
import org.junit.Before;
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
   * <p>The exception policies are set to 255 (defer to config) by default.
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
    // Set policies to 255 (defer to config) so tests can control via ExceptionPolicyConfig
    msg.setExceptionPropagationPolicy((byte) 255);
    msg.setCheckedExceptionPolicy((byte) 255);
    return msg;
  }

  // ===== Exception Handling Tests =====

  /**
   * Creates a dispatcher with a specific exception policy configuration.
   *
   * @param propagationPolicy the exception propagation policy
   * @param checkedPolicy the checked exception policy
   * @return a new dispatcher with the specified policies
   */
  private LocalInterceptCallbackDispatcher createDispatcherWithPolicy(
      ExceptionPropagationPolicy propagationPolicy, CheckedExceptionPolicy checkedPolicy) {
    ExceptionPolicyConfig config =
        new ExceptionPolicyConfig.Builder()
            .globalPropagationPolicy(propagationPolicy)
            .globalCheckedExceptionPolicy(checkedPolicy)
            .build();
    ExceptionPolicyResolver resolver = new ExceptionPolicyResolver(config);
    return new LocalInterceptCallbackDispatcher(callbackResolver, asyncExecutor, resolver);
  }

  /**
   * Tests that API misuse exceptions (InterceptApiMisuseException) are filtered and not propagated.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldFilterApiMisuseExceptionAndNotPropagate]
   */
  @Test
  public void shouldFilterApiMisuseExceptionAndNotPropagate() {
    // Given: Callback throws InterceptApiMisuseException (API misuse)
    // Configure with PROPAGATE_ALL to ensure only API misuse filtering is being tested
    LocalInterceptCallbackDispatcher policyDispatcher =
        createDispatcherWithPolicy(
            ExceptionPropagationPolicy.PROPAGATE_ALL, CheckedExceptionPolicy.ALLOW_ALL);

    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "throwApiMisuseException");
    List<InterceptMessage> intercepts = List.of(intercept);

    // When: Executing callback via dispatcher
    ConsolidatedCallbackResponse response =
        policyDispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            null);

    // Then: Exception is logged but not propagated; method continues normally
    assertTrue("Should proceed when API misuse exception is filtered", response.shouldProceed());
    assertFalse(
        "Should not throw exception when API misuse is filtered", response.shouldThrowException());
  }

  /**
   * Tests that explicit exceptions are propagated with PROPAGATE_ALL policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldPropagateExplicitExceptionWithPropagateAllPolicy]
   */
  @Test
  public void shouldPropagateExplicitExceptionWithPropagateAllPolicy() {
    // Given: Exception propagation policy is PROPAGATE_ALL
    LocalInterceptCallbackDispatcher policyDispatcher =
        createDispatcherWithPolicy(
            ExceptionPropagationPolicy.PROPAGATE_ALL, CheckedExceptionPolicy.ALLOW_ALL);

    RuntimeException expectedException = new RuntimeException("Test explicit exception");
    TestCallbacks.exceptionToThrow = expectedException;

    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "throwException");
    List<InterceptMessage> intercepts = List.of(intercept);

    // When: Executing callback via dispatcher
    ConsolidatedCallbackResponse response =
        policyDispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            null);

    // Then: Exception is propagated to caller
    assertTrue("Should throw exception with PROPAGATE_ALL", response.shouldThrowException());
    assertEquals(
        "Should return the expected exception", expectedException, response.getExceptionToThrow());
  }

  /**
   * Tests that direct throws are swallowed with PROPAGATE_EXPLICIT_ONLY policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldSwallowDirectThrowWithPropagateExplicitOnlyPolicy]
   */
  @Test
  public void shouldSwallowDirectThrowWithPropagateExplicitOnlyPolicy() {
    // Given: Exception propagation policy is PROPAGATE_EXPLICIT_ONLY
    LocalInterceptCallbackDispatcher policyDispatcher =
        createDispatcherWithPolicy(
            ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY, CheckedExceptionPolicy.ALLOW_ALL);

    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "throwDirectException");
    List<InterceptMessage> intercepts = List.of(intercept);

    // When: Executing callback via dispatcher (callback throws directly, not via
    // setExceptionToThrow)
    ConsolidatedCallbackResponse response =
        policyDispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            null);

    // Then: Exception is logged but swallowed; method continues normally
    assertTrue(
        "Should proceed when direct throw is swallowed with PROPAGATE_EXPLICIT_ONLY",
        response.shouldProceed());
    assertFalse(
        "Should not throw exception when direct throw is swallowed",
        response.shouldThrowException());
  }

  /**
   * Tests that all exceptions are swallowed with SWALLOW_ALL policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldSwallowAllWithSwallowAllPolicy]
   */
  @Test
  public void shouldSwallowAllWithSwallowAllPolicy() {
    // Given: Exception propagation policy is SWALLOW_ALL
    LocalInterceptCallbackDispatcher policyDispatcher =
        createDispatcherWithPolicy(
            ExceptionPropagationPolicy.SWALLOW_ALL, CheckedExceptionPolicy.ALLOW_ALL);

    RuntimeException expectedException = new RuntimeException("Test exception to swallow");
    TestCallbacks.exceptionToThrow = expectedException;

    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "throwException");
    List<InterceptMessage> intercepts = List.of(intercept);

    // When: Executing callback via dispatcher (with exception set via setExceptionToThrow)
    ConsolidatedCallbackResponse response =
        policyDispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            null);

    // Then: Exception is logged but swallowed; method continues normally
    assertTrue("Should proceed when all exceptions are swallowed", response.shouldProceed());
    assertFalse(
        "Should not throw exception with SWALLOW_ALL policy", response.shouldThrowException());
  }

  /**
   * Tests that checked exceptions are validated and wrapped with WRAP policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldValidateCheckedExceptionAndWrap]
   */
  @Test
  public void shouldValidateCheckedExceptionAndWrap() {
    // Given: Checked exception policy is WRAP
    LocalInterceptCallbackDispatcher policyDispatcher =
        createDispatcherWithPolicy(
            ExceptionPropagationPolicy.PROPAGATE_ALL, CheckedExceptionPolicy.WRAP);

    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "throwCheckedSqlException");
    List<InterceptMessage> intercepts = List.of(intercept);

    // Intercepted method declares IOException, callback throws SQLException
    String[] declaredExceptions = new String[] {"java.io.IOException"};

    // When: Executing callback via dispatcher
    ConsolidatedCallbackResponse response =
        policyDispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            declaredExceptions);

    // Then: SQLException is wrapped in RuntimeException before propagation
    assertTrue("Should throw exception", response.shouldThrowException());
    Throwable thrown = response.getExceptionToThrow();
    assertNotNull("Exception should not be null", thrown);
    assertTrue("Should be wrapped in RuntimeException", thrown instanceof RuntimeException);
    assertNotNull("Should have a cause", thrown.getCause());
    assertTrue("Cause should be SQLException", thrown.getCause() instanceof SQLException);
  }

  /**
   * Tests that checked exceptions are validated and rejected with REJECT policy.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherTest.shouldValidateCheckedExceptionAndReject]
   */
  @Test
  public void shouldValidateCheckedExceptionAndReject() {
    // Given: Checked exception policy is REJECT
    LocalInterceptCallbackDispatcher policyDispatcher =
        createDispatcherWithPolicy(
            ExceptionPropagationPolicy.PROPAGATE_ALL, CheckedExceptionPolicy.REJECT);

    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "throwCheckedSqlException");
    List<InterceptMessage> intercepts = List.of(intercept);

    // Intercepted method declares IOException, callback throws SQLException
    String[] declaredExceptions = new String[] {"java.io.IOException"};

    // When: Executing callback via dispatcher
    ConsolidatedCallbackResponse response =
        policyDispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID,
            declaredExceptions);

    // Then: InvalidCallbackExceptionException is thrown
    assertTrue("Should throw exception", response.shouldThrowException());
    Throwable thrown = response.getExceptionToThrow();
    assertNotNull("Exception should not be null", thrown);
    assertTrue(
        "Should be InvalidCallbackExceptionException",
        thrown instanceof InvalidCallbackExceptionException);
    assertNotNull("Should have a cause", thrown.getCause());
    assertTrue("Cause should be SQLException", thrown.getCause() instanceof SQLException);
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

    /** Throws an exception if exceptionToThrow is set via setExceptionToThrow. */
    public static InterceptCallbackResponse throwException(InterceptContext ctx) {
      lastContext = ctx;
      if (exceptionToThrow != null) {
        ctx.setExceptionToThrow(exceptionToThrow);
      }
      return new InterceptCallbackResponse();
    }

    /** Throws an exception directly (not via setExceptionToThrow). */
    public static InterceptCallbackResponse throwDirectException(InterceptContext ctx) {
      lastContext = ctx;
      throw new RuntimeException("Direct throw exception");
    }

    /** Throws an InterceptApiMisuseException. */
    public static InterceptCallbackResponse throwApiMisuseException(InterceptContext ctx) {
      lastContext = ctx;
      throw new InterceptApiMisuseException(
          "Test API misuse", "testOperation()", InterceptType.BEFORE, InterceptPhase.BEFORE);
    }

    /** Throws a checked SQLException via setExceptionToThrow. */
    public static InterceptCallbackResponse throwCheckedSqlException(InterceptContext ctx) {
      lastContext = ctx;
      ctx.setExceptionToThrow(new SQLException("Test SQL exception"));
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
