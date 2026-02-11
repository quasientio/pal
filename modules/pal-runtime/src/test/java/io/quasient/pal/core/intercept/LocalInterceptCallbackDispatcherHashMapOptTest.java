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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.core.intercept.InterceptCallbackDispatcher.ConsolidatedCallbackResponse;
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
 * Unit tests for HashMap reuse optimization in {@link LocalInterceptCallbackDispatcher}.
 *
 * <p>These tests verify that the optimized {@code sendLocalBeforeCallbacks()} and {@code
 * sendLocalAfterCallbacks()} methods work correctly with reusable HashMaps. Specifically, they
 * ensure that thread-local reusable HashMaps are properly cleared between consecutive dispatches
 * and do not leak mutations across calls.
 *
 * @see LocalInterceptCallbackDispatcher
 */
public class LocalInterceptCallbackDispatcherHashMapOptTest {

  private CallbackResolver callbackResolver;
  private LocalInterceptCallbackDispatcher dispatcher;
  private ExecutorService asyncExecutor;

  private static final String TEST_CLASS = "com.example.Calculator";
  private static final String TEST_METHOD = "add";
  private static final List<String> TEST_PARAM_TYPES = List.of("int", "int");
  private static final String TEST_PEER_UUID = "test-peer-uuid";
  private static final String CALLBACK_CLASS =
      "io.quasient.pal.core.intercept.LocalInterceptCallbackDispatcherHashMapOptTest$TestCallbacks";

  /** Sets up test fixtures. */
  @Before
  public void setUp() {
    callbackResolver = new CallbackResolver();
    asyncExecutor = Executors.newSingleThreadExecutor();
    dispatcher = new LocalInterceptCallbackDispatcher(callbackResolver, asyncExecutor);
    // Reset static state
    TestCallbacks.lastContext = null;
  }

  /** Cleans up executor after test. */
  @After
  public void tearDown() {
    if (asyncExecutor != null) {
      asyncExecutor.shutdownNow();
    }
  }

  /**
   * Tests that an empty intercept list returns a proceed response.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherHashMapOptTest.shouldReturnProceedWithEmptyInterceptList]
   * Empty list returns proceed
   */
  @Test
  @Ignore("Awaiting implementation in #683")
  public void shouldReturnProceedWithEmptyInterceptList() {
    // Given: Empty localIntercepts list
    List<InterceptMessage> emptyIntercepts = new ArrayList<>();

    // When: sendLocalBeforeCallbacks() called
    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalBeforeCallbacks(
            emptyIntercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    // Then: Returns ConsolidatedCallbackResponse.proceed()
    assertTrue("Should proceed with empty intercept list", response.shouldProceed());
    assertFalse("Should have no arg mutations", response.hasArgMutations());
    assertFalse("Should not throw exception", response.shouldThrowException());

    // TODO(#683): Verify that no HashMap allocation occurred (reusable HashMap not touched)
    fail("Not yet implemented");
  }

  /**
   * Tests that no mutations are aggregated when a callback reads but does not mutate args.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherHashMapOptTest.shouldAggregateNoMutationsWhenCallbackDoesNotMutate]
   * No mutations when callback doesn't mutate
   */
  @Test
  @Ignore("Awaiting implementation in #683")
  public void shouldAggregateNoMutationsWhenCallbackDoesNotMutate() {
    // Given: 1 BEFORE intercept with a callback that reads but doesn't mutate args
    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "readOnlyCallback");
    List<InterceptMessage> intercepts = List.of(intercept);

    // When: sendLocalBeforeCallbacks() called
    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {5, 3},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    // Then: Response has empty mutations map, proceed=true
    assertTrue("Should proceed", response.shouldProceed());
    assertFalse(
        "Should have no arg mutations when callback only reads", response.hasArgMutations());
    assertFalse("Should not throw exception", response.shouldThrowException());

    // TODO(#683): Verify reusable HashMap was cleared/empty after use
    fail("Not yet implemented");
  }

  /**
   * Tests that argument mutations from a single callback are correctly aggregated.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherHashMapOptTest.shouldAggregateArgMutationsFromSingleCallback]
   * Single callback mutation aggregated
   */
  @Test
  @Ignore("Awaiting implementation in #683")
  public void shouldAggregateArgMutationsFromSingleCallback() {
    // Given: 1 BEFORE intercept with a callback that mutates arg[0]
    InterceptMessage intercept = createIntercept(InterceptType.BEFORE, "mutateFirstArg");
    List<InterceptMessage> intercepts = List.of(intercept);

    // When: sendLocalBeforeCallbacks() called
    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {5, 3},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    // Then: Response.mutations contains {0: newValue}
    assertTrue("Should proceed", response.shouldProceed());
    assertTrue("Should have arg mutations", response.hasArgMutations());
    assertEquals("Arg[0] should be mutated to 10", 10, response.getMutatedArgs().get(0));

    // TODO(#683): Verify mutation was stored in reusable HashMap
    fail("Not yet implemented");
  }

  /**
   * Tests that argument mutations from multiple callbacks are correctly aggregated.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherHashMapOptTest.shouldAggregateArgMutationsFromMultipleCallbacks]
   * Multiple callback mutations aggregated
   */
  @Test
  @Ignore("Awaiting implementation in #683")
  public void shouldAggregateArgMutationsFromMultipleCallbacks() {
    // Given: 2 BEFORE intercepts, first mutates arg[0], second mutates arg[1]
    InterceptMessage intercept1 = createIntercept(InterceptType.BEFORE, "mutateFirstArg");
    InterceptMessage intercept2 = createIntercept(InterceptType.BEFORE, "mutateSecondArg");
    List<InterceptMessage> intercepts = List.of(intercept1, intercept2);

    // When: sendLocalBeforeCallbacks() called
    ConsolidatedCallbackResponse response =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {5, 3},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    // Then: Response.mutations contains both mutations
    assertTrue("Should proceed", response.shouldProceed());
    assertTrue("Should have arg mutations", response.hasArgMutations());
    assertEquals("Arg[0] should be mutated to 10", 10, response.getMutatedArgs().get(0));
    assertEquals("Arg[1] should be mutated to 6", 6, response.getMutatedArgs().get(1));

    // TODO(#683): Verify both mutations coexist in the same reusable HashMap
    fail("Not yet implemented");
  }

  /**
   * Tests that the thread-local reusable HashMap is clean across consecutive calls on the same
   * thread.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherHashMapOptTest.shouldHandleReusableHashMapAcrossConsecutiveCalls]
   * HashMap reuse is clean
   */
  @Test
  @Ignore("Awaiting implementation in #683")
  public void shouldHandleReusableHashMapAcrossConsecutiveCalls() {
    // Given: Thread-local reusable HashMap
    // First call: callback mutates arg[0]
    InterceptMessage intercept1 = createIntercept(InterceptType.BEFORE, "mutateFirstArg");
    List<InterceptMessage> intercepts1 = List.of(intercept1);

    // When: sendLocalBeforeCallbacks() called first time
    ConsolidatedCallbackResponse response1 =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts1,
            new Object[] {5, 3},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    // Verify first call produced mutations
    assertTrue("First call should have mutations", response1.hasArgMutations());
    assertEquals("First call arg[0] should be 10", 10, response1.getMutatedArgs().get(0));

    // Second call: callback only reads args (no mutations)
    InterceptMessage intercept2 = createIntercept(InterceptType.BEFORE, "readOnlyCallback");
    List<InterceptMessage> intercepts2 = List.of(intercept2);

    // When: sendLocalBeforeCallbacks() called second time on same thread
    ConsolidatedCallbackResponse response2 =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts2,
            new Object[] {7, 8},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    // Then: Second call has clean HashMap (no leftover mutations from first call)
    assertFalse(
        "Second call should have no mutations (HashMap should be clean)",
        response2.hasArgMutations());
    assertTrue("Second call should proceed", response2.shouldProceed());

    // TODO(#683): Verify the reusable HashMap was properly cleared between calls
    fail("Not yet implemented");
  }

  /**
   * Tests that mutations do not leak between consecutive callback dispatches.
   *
   * <p>Acceptance Criteria:
   * [TEST:LocalInterceptCallbackDispatcherHashMapOptTest.shouldNotLeakMutationsBetweenConsecutiveCallbackDispatches]
   * No cross-dispatch mutation leaks
   */
  @Test
  @Ignore("Awaiting implementation in #683")
  public void shouldNotLeakMutationsBetweenConsecutiveCallbackDispatches() {
    // Given: First dispatch produces 3 mutations (arg[0], arg[1], arg[2])
    InterceptMessage intercept1 = createIntercept(InterceptType.BEFORE, "mutateThreeArgs");
    List<InterceptMessage> intercepts1 = List.of(intercept1);

    // When: First dispatch on same thread
    ConsolidatedCallbackResponse response1 =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts1,
            new Object[] {1, 2, 3},
            TEST_CLASS,
            "threeArgMethod",
            List.of("int", "int", "int"),
            TEST_PEER_UUID);

    // Verify first dispatch produced 3 mutations
    assertTrue("First dispatch should have mutations", response1.hasArgMutations());
    assertEquals("First dispatch should have 3 mutations", 3, response1.getMutatedArgs().size());

    // Second dispatch: empty intercept list (produces 0 mutations)
    List<InterceptMessage> intercepts2 = new ArrayList<>();

    // When: Second dispatch on same thread with empty list
    ConsolidatedCallbackResponse response2 =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts2,
            new Object[] {1, 2, 3},
            TEST_CLASS,
            "threeArgMethod",
            List.of("int", "int", "int"),
            TEST_PEER_UUID);

    // Then: Second dispatch response has empty mutations
    assertFalse(
        "Second dispatch should have no mutations (no leaks from first dispatch)",
        response2.hasArgMutations());
    assertTrue("Second dispatch should proceed", response2.shouldProceed());

    // TODO(#683): Verify that no stale entries from first dispatch appear in second dispatch
    fail("Not yet implemented");
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
    msg.setExceptionPropagationPolicy((byte) 255);
    msg.setCheckedExceptionPolicy((byte) 255);
    return msg;
  }

  // ===== Test Callbacks Class =====

  /** Test callbacks class with static methods for testing HashMap reuse optimization. */
  public static class TestCallbacks {

    /** Last context passed to a callback (for verification). */
    static InterceptContext lastContext;

    /**
     * Read-only callback that accesses args but does not mutate them.
     *
     * @param ctx the intercept context
     * @return the callback response
     */
    public static InterceptCallbackResponse readOnlyCallback(InterceptContext ctx) {
      lastContext = ctx;
      // Read args but don't mutate
      Object[] args = ctx.getArgs();
      if (args != null && args.length > 0) {
        // Read only — no setArg() call
        @SuppressWarnings("unused")
        Object firstArg = args[0];
      }
      return new InterceptCallbackResponse();
    }

    /**
     * Mutates the first argument by doubling it.
     *
     * @param ctx the intercept context
     * @return the callback response
     */
    public static InterceptCallbackResponse mutateFirstArg(InterceptContext ctx) {
      lastContext = ctx;
      Object[] args = ctx.getArgs();
      if (args != null && args.length > 0 && args[0] instanceof Integer val) {
        ctx.setArg(0, val * 2);
      }
      return new InterceptCallbackResponse();
    }

    /**
     * Mutates the second argument by doubling it.
     *
     * @param ctx the intercept context
     * @return the callback response
     */
    public static InterceptCallbackResponse mutateSecondArg(InterceptContext ctx) {
      lastContext = ctx;
      Object[] args = ctx.getArgs();
      if (args != null && args.length > 1 && args[1] instanceof Integer val) {
        ctx.setArg(1, val * 2);
      }
      return new InterceptCallbackResponse();
    }

    /**
     * Mutates three arguments by doubling each.
     *
     * @param ctx the intercept context
     * @return the callback response
     */
    public static InterceptCallbackResponse mutateThreeArgs(InterceptContext ctx) {
      lastContext = ctx;
      Object[] args = ctx.getArgs();
      if (args != null) {
        for (int i = 0; i < Math.min(args.length, 3); i++) {
          if (args[i] instanceof Integer val) {
            ctx.setArg(i, val * 2);
          }
        }
      }
      return new InterceptCallbackResponse();
    }
  }
}
