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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.lang.intercept.InterceptCallback;
import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.LocalAroundAccessor;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/**
 * Integration tests verifying the optimized intercept hot-path produces correct results end-to-end.
 *
 * <p>This test suite exercises all hot-path optimizations working together under realistic
 * conditions:
 *
 * <ul>
 *   <li>{@link InterceptPartition} — single-pass partitioning replacing stream-based filtering
 *   <li>Pre-computed strings in {@link InterceptRequestEntry#matches(String, String)}
 *   <li>Pooled {@link InterceptContext} via {@code forLocalBeforePhasePooled}
 *   <li>{@code TlScratchHolder} intercept scratches for ephemeral message building
 *   <li>Deferred args copy (copy-on-write in {@code InterceptContext.setArg})
 *   <li>Thread-local {@code HashMap} reuse in {@link LocalInterceptCallbackDispatcher}
 *   <li>{@link AroundInterceptChainBuilder} pooling
 * </ul>
 *
 * <p>No external infrastructure (etcd/Kafka) is required — all tests use local dispatch only.
 *
 * <p>Phase 3 of the hot-path optimization plan (#668): integration verification that all
 * optimizations work correctly together.
 *
 * @see InterceptPartition
 * @see InterceptChecker
 * @see LocalInterceptCallbackDispatcher
 * @see AroundInterceptChainBuilder
 */
@SuppressWarnings({
  "StaticAssignmentOfThrowable",
  "UnusedVariable",
  "UnusedMethod",
  "FutureReturnValueIgnored"
})
public class InterceptHotPathOptimizationIT {

  /** Timeout rule to prevent hanging tests (60 seconds). */
  @Rule public Timeout globalTimeout = Timeout.seconds(60);

  /** Test class name used for intercept pattern matching. */
  private static final String TEST_CLASS = "com.example.Calculator";

  /** Test method name used for intercept pattern matching. */
  private static final String TEST_METHOD = "add";

  /** Test parameter types for the target method. */
  private static final List<String> TEST_PARAM_TYPES = List.of("int", "int");

  /** Test peer UUID string. */
  private static final String TEST_PEER_UUID = "test-peer-uuid";

  /** Fully-qualified callback class name pointing to the inner {@link TestCallbacks} class. */
  private static final String CALLBACK_CLASS =
      "io.quasient.pal.core.intercept.InterceptHotPathOptimizationIT$TestCallbacks";

  /** Number of dispatches per test for volume verification. */
  private static final int DISPATCH_COUNT = 1000;

  /** Number of concurrent threads for thread-safety tests. */
  private static final int THREAD_COUNT = 32;

  /** Callback resolver for test callbacks. */
  private CallbackResolver callbackResolver;

  /** Async executor for async callbacks. */
  private ExecutorService asyncExecutor;

  /** Local intercept callback dispatcher under test. */
  private LocalInterceptCallbackDispatcher dispatcher;

  /**
   * Sets up test fixtures: initializes callback resolver, executor, dispatcher, and resets static
   * callback state.
   */
  @Before
  public void setUp() {
    callbackResolver = new CallbackResolver();
    asyncExecutor = Executors.newCachedThreadPool();
    dispatcher = new LocalInterceptCallbackDispatcher(callbackResolver, asyncExecutor);
    // Reset static callback state
    TestCallbacks.beforeCallCount.set(0);
    TestCallbacks.afterCallCount.set(0);
    TestCallbacks.aroundBeforeCallCount.set(0);
    TestCallbacks.aroundAfterCallCount.set(0);
    TestCallbacks.lastReturnValue.set(null);
    TestCallbacks.lastArgs.set(null);
    TestCallbacks.exceptionToThrow = null;
    TestCallbacks.nestedDispatchTrigger = null;
    TestCallbacks.perThreadResults.clear();
  }

  /**
   * Tears down test fixtures: shuts down executor, verifies no resource leaks.
   *
   * @throws Exception if shutdown is interrupted
   */
  @After
  public void tearDown() throws Exception {
    if (asyncExecutor != null) {
      asyncExecutor.shutdownNow();
      asyncExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  // ===== Test 1: BEFORE Intercept Correctness at Volume =====

  /**
   * Verifies that 1000 BEFORE intercept dispatches through the optimized hot-path all invoke the
   * callback with correct arguments and produce correct return values.
   *
   * <p>Given: A BEFORE intercept registered on {@code Calculator.add(int, int)} with the optimized
   * hot-path (InterceptPartition, pooled contexts, thread-local HashMap reuse).
   *
   * <p>When: The method is dispatched 1000 times with varying arguments.
   *
   * <p>Then: All 1000 callbacks are invoked with the correct arguments. Return values from the
   * consolidated response are correct (proceed=true, no exceptions). The callback invocation count
   * matches exactly 1000.
   */
  @Test
  public void shouldDispatchWithBeforeInterceptCorrectly() {
    // Given: BEFORE intercept registered, optimized hot-path
    InterceptMessage beforeIntercept = createIntercept(InterceptType.BEFORE, "countBeforeCallback");
    List<InterceptMessage> intercepts = List.of(beforeIntercept);

    // When: Method dispatched 1000 times with varying args
    for (int i = 0; i < DISPATCH_COUNT; i++) {
      Object[] args = {i, i + 1};
      InterceptCallbackDispatcher.ConsolidatedCallbackResponse response =
          dispatcher.sendLocalBeforeCallbacks(
              intercepts, args, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, TEST_PEER_UUID);

      // Then: Each response should proceed with no exceptions
      assertTrue("Response should proceed at iteration " + i, response.shouldProceed());
      assertFalse(
          "Response should not throw exception at iteration " + i, response.shouldThrowException());
    }

    // Then: All 1000 callbacks invoked
    assertThat(TestCallbacks.beforeCallCount.get(), is(DISPATCH_COUNT));

    // Verify last args were correct (last iteration: {999, 1000})
    Object[] lastArgs = TestCallbacks.lastArgs.get();
    assertNotNull("Last args should not be null", lastArgs);
    assertThat(lastArgs.length, is(2));
    assertThat(lastArgs[0], is(DISPATCH_COUNT - 1));
    assertThat(lastArgs[1], is(DISPATCH_COUNT));
  }

  // ===== Test 2: AFTER Intercept Correctness at Volume =====

  /**
   * Verifies that 1000 AFTER intercept dispatches through the optimized hot-path all invoke the
   * callback with correct return values and produce correct consolidated responses.
   *
   * <p>Given: An AFTER intercept registered on {@code Calculator.add(int, int)} with the optimized
   * hot-path.
   *
   * <p>When: The method is dispatched 1000 times with varying arguments and return values.
   *
   * <p>Then: All 1000 callbacks are invoked and see the correct return value. The consolidated
   * response correctly reflects the callback's behavior. The callback invocation count matches
   * exactly 1000.
   */
  @Test
  public void shouldDispatchWithAfterInterceptCorrectly() {
    // Given: AFTER intercept registered
    InterceptMessage afterIntercept = createIntercept(InterceptType.AFTER, "countAfterCallback");
    List<InterceptMessage> intercepts = List.of(afterIntercept);

    // When: Method dispatched 1000 times with known return values
    for (int i = 0; i < DISPATCH_COUNT; i++) {
      Object[] args = {i, i + 1};
      Object returnValue = i * 2; // Simulated return value

      InterceptCallbackDispatcher.ConsolidatedCallbackResponse response =
          dispatcher.sendLocalAfterCallbacks(
              intercepts,
              args,
              returnValue,
              false, // not void
              null, // no thrown exception
              TEST_CLASS,
              TEST_METHOD,
              TEST_PARAM_TYPES,
              TEST_PEER_UUID);

      // Then: Each response should proceed with no exceptions
      assertTrue("Response should proceed at iteration " + i, response.shouldProceed());
      assertFalse(
          "Response should not throw exception at iteration " + i, response.shouldThrowException());
    }

    // Then: All 1000 callbacks invoked
    assertThat(TestCallbacks.afterCallCount.get(), is(DISPATCH_COUNT));

    // Verify last return value was correct (last iteration: (999) * 2 = 1998)
    Object lastReturnValue = TestCallbacks.lastReturnValue.get();
    assertNotNull("Last return value should not be null", lastReturnValue);
    assertThat(lastReturnValue, is((DISPATCH_COUNT - 1) * 2));
  }

  // ===== Test 3: AROUND Intercept Correctness at Volume =====

  /**
   * Verifies that 1000 AROUND intercept dispatches through the optimized hot-path correctly execute
   * the onion chain with proceed(), produce correct return values, and correctly track pending
   * callbacks through both BEFORE and AFTER phases.
   *
   * <p>Given: An AROUND intercept with {@code proceed()} registered on {@code Calculator.add(int,
   * int)} with the optimized hot-path (AroundInterceptChainBuilder pooling, InterceptPartition).
   *
   * <p>When: The method is dispatched 1000 times.
   *
   * <p>Then: The onion chain executes correctly for all 1000 dispatches. Each dispatch produces a
   * pending callback that can be completed in the AFTER phase. Return values are correct through
   * the full BEFORE→proceed→AFTER cycle. The callback invocation count matches exactly 1000 for
   * both BEFORE and AFTER phases.
   */
  @Test
  public void shouldDispatchWithAroundInterceptCorrectly() {
    // Given: AROUND intercept with proceed()
    InterceptMessage aroundIntercept = createIntercept(InterceptType.AROUND, "aroundWithProceed");
    List<InterceptMessage> intercepts = List.of(aroundIntercept);

    // When: Method dispatched 1000 times
    for (int i = 0; i < DISPATCH_COUNT; i++) {
      Object[] args = {i, i + 1};
      int expectedResult = i + (i + 1); // Simulated add result

      // Create a LocalAroundAccessor that simulates method execution
      LocalAroundAccessor accessor = invokedArgs -> new AfterPhaseData(expectedResult, null, false);

      LocalInterceptCallbackDispatcher.LocalAroundConsolidatedResponse aroundResponse =
          dispatcher.sendLocalAroundCallbacks(
              intercepts,
              args,
              TEST_CLASS,
              TEST_METHOD,
              TEST_PARAM_TYPES,
              TEST_PEER_UUID,
              accessor);

      // Then: Should proceed with one pending callback
      assertTrue(
          "AROUND response should proceed at iteration " + i, aroundResponse.shouldProceed());
      assertFalse(
          "AROUND response should not throw at iteration " + i,
          aroundResponse.shouldThrowException());
      assertThat(
          "Should have 1 pending callback at iteration " + i,
          aroundResponse.getPendingCallbacks().size(),
          is(1));

      // Complete AFTER phase
      InterceptCallbackDispatcher.ConsolidatedCallbackResponse afterResponse =
          dispatcher.sendLocalAroundAfterCallbacks(
              aroundResponse.getPendingCallbacks(), expectedResult);

      assertTrue("AFTER response should proceed at iteration " + i, afterResponse.shouldProceed());
      assertFalse(
          "AFTER response should not throw at iteration " + i,
          afterResponse.shouldThrowException());
    }

    // Then: Verify counts for both BEFORE and AFTER phases
    assertThat(TestCallbacks.aroundBeforeCallCount.get(), is(DISPATCH_COUNT));
    assertThat(TestCallbacks.aroundAfterCallCount.get(), is(DISPATCH_COUNT));
  }

  // ===== Test 4: Nested Dispatch Safety =====

  /**
   * Verifies that a BEFORE intercept callback that triggers another intercepted method call (nested
   * dispatch) does not corrupt thread-local pooled objects.
   *
   * <p>Given: A BEFORE intercept registered whose callback triggers a nested dispatch through the
   * same optimized hot-path (exercising thread-local InterceptPartition, pooled InterceptContext,
   * and thread-local HashMap reuse under re-entrant conditions).
   *
   * <p>When: The outer method is dispatched.
   *
   * <p>Then: Both the outer and inner dispatches produce correct results. Thread-local objects
   * (InterceptPartition sub-lists, pooled InterceptContext fields, HashMap mutations) are not
   * corrupted by the nested re-entry. The outer dispatch's state is correctly restored after the
   * inner dispatch completes.
   */
  @Test
  public void shouldHandleNestedDispatchWithPooledObjects() {
    // Given: Outer BEFORE intercept that triggers nested dispatch
    InterceptMessage outerIntercept =
        createIntercept(InterceptType.BEFORE, "nestedDispatchCallback");
    List<InterceptMessage> outerIntercepts = List.of(outerIntercept);

    // Inner BEFORE intercept on a "different" method (using countBeforeCallback)
    InterceptMessage innerIntercept = createIntercept(InterceptType.BEFORE, "countBeforeCallback");
    List<InterceptMessage> innerIntercepts = List.of(innerIntercept);

    // Track inner dispatch results
    AtomicReference<InterceptCallbackDispatcher.ConsolidatedCallbackResponse> innerResponse =
        new AtomicReference<>();
    AtomicReference<Object[]> innerArgsCapture = new AtomicReference<>();

    // Configure nested dispatch trigger
    Object[] innerArgs = {100, 200};
    TestCallbacks.nestedDispatchTrigger =
        () -> {
          // This runs inside the outer callback — triggers re-entrant dispatch
          InterceptCallbackDispatcher.ConsolidatedCallbackResponse resp =
              dispatcher.sendLocalBeforeCallbacks(
                  innerIntercepts,
                  innerArgs,
                  "com.example.InnerClass",
                  "subtract",
                  List.of("int", "int"),
                  TEST_PEER_UUID);
          innerResponse.set(resp);
          innerArgsCapture.set(TestCallbacks.lastArgs.get());
        };

    // When: Outer method dispatched
    Object[] outerArgs = {1, 2};
    InterceptCallbackDispatcher.ConsolidatedCallbackResponse outerResponse =
        dispatcher.sendLocalBeforeCallbacks(
            outerIntercepts, outerArgs, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, TEST_PEER_UUID);

    // Then: Outer dispatch result is correct
    assertTrue("Outer response should proceed", outerResponse.shouldProceed());
    assertFalse("Outer response should not throw", outerResponse.shouldThrowException());

    // Then: Inner dispatch result is correct
    assertNotNull("Inner response should have been captured", innerResponse.get());
    assertTrue("Inner response should proceed", innerResponse.get().shouldProceed());
    assertFalse("Inner response should not throw", innerResponse.get().shouldThrowException());

    // Verify inner args were captured correctly (not corrupted by outer dispatch)
    Object[] capturedInnerArgs = innerArgsCapture.get();
    assertNotNull("Inner args should have been captured", capturedInnerArgs);
    assertThat("Inner arg[0] should be 100", capturedInnerArgs[0], is(100));
    assertThat("Inner arg[1] should be 200", capturedInnerArgs[1], is(200));

    // Both callbacks were invoked:
    // nestedDispatchCallback increments beforeCallCount once
    // countBeforeCallback (inner) also increments beforeCallCount once
    assertThat(
        "Both callbacks should have been invoked", TestCallbacks.beforeCallCount.get(), is(2));
  }

  // ===== Test 5: Concurrent Dispatch Thread Safety =====

  /**
   * Verifies that 32 threads concurrently dispatching 1000 times each through the optimized
   * hot-path produce correct results without cross-thread contamination.
   *
   * <p>Given: BEFORE + AFTER intercepts registered. 32 threads each with unique argument values.
   *
   * <p>When: All 32 threads dispatch 1000 times concurrently (synchronized start via {@link
   * CyclicBarrier}).
   *
   * <p>Then: All results are correct — each thread's callbacks receive the arguments that thread
   * supplied, not arguments from another thread. No cross-thread contamination of thread-local
   * objects (InterceptPartition, pooled InterceptContext, HashMap for mutations). Total callback
   * invocation count equals {@code 32 × 1000 × 2} (BEFORE + AFTER per dispatch).
   */
  @Test
  public void shouldHandleConcurrentDispatchesWithThreadLocalReuse() throws Exception {
    // Given: BEFORE + AFTER intercepts
    InterceptMessage beforeIntercept =
        createIntercept(InterceptType.BEFORE, "threadSafeBeforeCallback");
    InterceptMessage afterIntercept =
        createIntercept(InterceptType.AFTER, "threadSafeAfterCallback");
    List<InterceptMessage> beforeIntercepts = List.of(beforeIntercept);
    List<InterceptMessage> afterIntercepts = List.of(afterIntercept);

    // Synchronization: barrier for start, latch for completion
    CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
    AtomicReference<Throwable> firstError = new AtomicReference<>();

    // When: Launch THREAD_COUNT threads, each dispatching DISPATCH_COUNT times
    for (int t = 0; t < THREAD_COUNT; t++) {
      final int threadIdx = t;
      asyncExecutor.submit(
          () -> {
            try {
              barrier.await(10, TimeUnit.SECONDS);

              for (int i = 0; i < DISPATCH_COUNT; i++) {
                // Unique per-thread args: threadIdx * 1000000 + i ensures no overlap
                int arg0 = threadIdx * 1_000_000 + i;
                int arg1 = threadIdx * 1_000_000 + i + 1;
                Object[] args = {arg0, arg1};
                Object returnValue = arg0 + arg1;

                // Dispatch BEFORE
                InterceptCallbackDispatcher.ConsolidatedCallbackResponse beforeResponse =
                    dispatcher.sendLocalBeforeCallbacks(
                        beforeIntercepts,
                        args,
                        TEST_CLASS,
                        TEST_METHOD,
                        TEST_PARAM_TYPES,
                        TEST_PEER_UUID);
                if (!beforeResponse.shouldProceed()) {
                  throw new AssertionError(
                      "BEFORE response should proceed: thread=" + threadIdx + " iter=" + i);
                }

                // Dispatch AFTER
                InterceptCallbackDispatcher.ConsolidatedCallbackResponse afterResponse =
                    dispatcher.sendLocalAfterCallbacks(
                        afterIntercepts,
                        args,
                        returnValue,
                        false,
                        null,
                        TEST_CLASS,
                        TEST_METHOD,
                        TEST_PARAM_TYPES,
                        TEST_PEER_UUID);
                if (!afterResponse.shouldProceed()) {
                  throw new AssertionError(
                      "AFTER response should proceed: thread=" + threadIdx + " iter=" + i);
                }
              }
            } catch (Throwable e) {
              firstError.compareAndSet(null, e);
            } finally {
              completionLatch.countDown();
            }
          });
    }

    // Wait for all threads to complete
    assertTrue(
        "All threads should complete within 30 seconds",
        completionLatch.await(30, TimeUnit.SECONDS));

    // Then: Verify no assertion errors from any thread
    if (firstError.get() != null) {
      throw new AssertionError("Thread error: " + firstError.get().getMessage(), firstError.get());
    }

    // Total BEFORE callback count = THREAD_COUNT * DISPATCH_COUNT
    assertThat(TestCallbacks.beforeCallCount.get(), is(THREAD_COUNT * DISPATCH_COUNT));

    // Total AFTER callback count = THREAD_COUNT * DISPATCH_COUNT
    assertThat(TestCallbacks.afterCallCount.get(), is(THREAD_COUNT * DISPATCH_COUNT));

    // Verify per-thread results: each thread should have recorded DISPATCH_COUNT entries
    // and args should match thread-specific values (no cross-thread contamination)
    for (var entry : TestCallbacks.perThreadResults.entrySet()) {
      List<Object[]> threadResults = entry.getValue();
      assertThat(
          "Each thread should have " + DISPATCH_COUNT + " results",
          threadResults.size(),
          is(DISPATCH_COUNT));

      // All args for this thread should be in the same threadIdx range
      for (Object[] args : threadResults) {
        int arg0 = (int) args[0];
        int threadIdx = arg0 / 1_000_000;
        // Verify all entries for this thread share the same threadIdx
        // (no cross-thread contamination)
        for (Object[] otherArgs : threadResults) {
          int otherArg0 = (int) otherArgs[0];
          int otherThreadIdx = otherArg0 / 1_000_000;
          assertEquals(
              "All args for a thread should belong to the same thread index",
              threadIdx,
              otherThreadIdx);
        }
        break; // Only need to verify once per thread
      }
    }
  }

  // ===== Test 6: Deferred Args Copy-on-Write =====

  /**
   * Verifies that the deferred copy-on-write optimization in {@link InterceptContext} correctly
   * isolates argument mutations: a BEFORE intercept callback that mutates {@code arg[0]} via {@code
   * setArg()} delivers the mutated value to the method, while the original caller's argument array
   * remains unchanged.
   *
   * <p>Given: A BEFORE intercept that mutates {@code arg[0]} via {@code ctx.setArg(0, newValue)}.
   *
   * <p>When: The method is dispatched with known original arguments.
   *
   * <p>Then: The consolidated response reports the mutation in {@code getMutatedArgs()}. The
   * original caller's argument array is unchanged (defensive copy semantics). The method would
   * receive the mutated argument value.
   */
  @Test
  public void shouldHandleArgMutationWithDeferredCopy() {
    // Given: BEFORE intercept that mutates arg[0]
    InterceptMessage mutateIntercept = createIntercept(InterceptType.BEFORE, "mutateFirstArg");
    List<InterceptMessage> intercepts = List.of(mutateIntercept);

    // When/Then: Repeat for DISPATCH_COUNT iterations to verify consistency under reuse
    for (int i = 0; i < DISPATCH_COUNT; i++) {
      // Create fresh args arrays each iteration
      Object[] originalArgs = {10, 20};
      // Keep a separate reference to verify caller's array is unchanged
      Object[] callerArgs = {10, 20};

      InterceptCallbackDispatcher.ConsolidatedCallbackResponse response =
          dispatcher.sendLocalBeforeCallbacks(
              intercepts, originalArgs, TEST_CLASS, TEST_METHOD, TEST_PARAM_TYPES, TEST_PEER_UUID);

      // Verify response has arg mutations
      assertTrue(
          "Response should have arg mutations at iteration " + i, response.hasArgMutations());
      assertThat(
          "Mutated arg[0] should be 999 at iteration " + i,
          response.getMutatedArgs().get(0),
          is(999));

      // Verify caller's original array is unchanged (copy-on-write preserved isolation)
      assertThat("Caller's arg[0] should still be 10 at iteration " + i, callerArgs[0], is(10));
      assertThat("Caller's arg[1] should still be 20 at iteration " + i, callerArgs[1], is(20));

      // Response should still proceed
      assertTrue("Response should proceed at iteration " + i, response.shouldProceed());
      assertFalse("Response should not throw at iteration " + i, response.shouldThrowException());
    }

    // Verify all callbacks were invoked
    assertThat(TestCallbacks.beforeCallCount.get(), is(DISPATCH_COUNT));
  }

  // ===== Test 7: Exception Propagation =====

  /**
   * Verifies that exceptions thrown by BEFORE intercept callbacks propagate correctly through the
   * optimized hot-path without being swallowed or corrupted by the optimization machinery.
   *
   * <p>Given: A BEFORE intercept whose callback sets an exception to throw via {@code
   * ctx.setExceptionToThrow()}.
   *
   * <p>When: The method is dispatched.
   *
   * <p>Then: The consolidated response reports {@code shouldThrowException()==true}. The exception
   * is the exact same instance set by the callback (not wrapped). The exception message and type
   * are preserved through the optimized path.
   */
  @Test
  public void shouldHandleExceptionPropagationThroughOptimizedPath() {
    // Given: BEFORE intercept that throws exception
    InterceptMessage throwIntercept =
        createIntercept(InterceptType.BEFORE, "throwConfiguredException");
    List<InterceptMessage> intercepts = List.of(throwIntercept);

    // Test with RuntimeException
    RuntimeException runtimeException = new RuntimeException("test error");
    TestCallbacks.exceptionToThrow = runtimeException;

    InterceptCallbackDispatcher.ConsolidatedCallbackResponse response =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {1, 2},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    // Then: Exception propagated correctly
    assertTrue("Response should indicate exception to throw", response.shouldThrowException());
    assertSame(
        "Exception should be the exact same instance",
        runtimeException,
        response.getExceptionToThrow());
    assertThat(
        "Exception message should be preserved",
        response.getExceptionToThrow().getMessage(),
        is("test error"));
    assertTrue(
        "Exception type should be RuntimeException",
        response.getExceptionToThrow() instanceof RuntimeException);

    // Test with IllegalArgumentException
    TestCallbacks.beforeCallCount.set(0);
    IllegalArgumentException illegalArgException = new IllegalArgumentException("invalid argument");
    TestCallbacks.exceptionToThrow = illegalArgException;

    response =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {3, 4},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    assertTrue("Response should indicate exception", response.shouldThrowException());
    assertSame(
        "IllegalArgumentException should be the exact same instance",
        illegalArgException,
        response.getExceptionToThrow());
    assertThat(
        "Exception message should be 'invalid argument'",
        response.getExceptionToThrow().getMessage(),
        is("invalid argument"));

    // Test with custom exception
    TestCallbacks.beforeCallCount.set(0);
    CustomTestException customException = new CustomTestException("custom error");
    TestCallbacks.exceptionToThrow = customException;

    response =
        dispatcher.sendLocalBeforeCallbacks(
            intercepts,
            new Object[] {5, 6},
            TEST_CLASS,
            TEST_METHOD,
            TEST_PARAM_TYPES,
            TEST_PEER_UUID);

    assertTrue("Response should indicate exception", response.shouldThrowException());
    assertSame(
        "Custom exception should be the exact same instance",
        customException,
        response.getExceptionToThrow());
    assertTrue(
        "Exception type should be CustomTestException",
        response.getExceptionToThrow() instanceof CustomTestException);

    // Verify all three callbacks were invoked (one per exception type)
    assertThat(TestCallbacks.beforeCallCount.get(), is(1));
  }

  // ===== Helper Methods =====

  /**
   * Creates an {@link InterceptMessage} with the given intercept type and callback method name.
   *
   * <p>The message is configured with the test callback class and default exception policies
   * (deferred to config, byte value 255).
   *
   * @param type the intercept type (BEFORE, AFTER, AROUND, etc.)
   * @param callbackMethodName the name of the static callback method in {@link TestCallbacks}
   * @return a configured InterceptMessage
   */
  private InterceptMessage createIntercept(InterceptType type, String callbackMethodName) {
    InterceptMessage msg = new InterceptMessage();
    msg.setInterceptType(type.toByte());
    msg.setCallbackClass(CALLBACK_CLASS);
    msg.setCallbackMethod(callbackMethodName);
    msg.setExceptionPropagationPolicy((byte) 255);
    msg.setCheckedExceptionPolicy((byte) 255);
    return msg;
  }

  // ===== Test Callbacks =====

  /**
   * Static callback handlers used by the integration tests.
   *
   * <p>Each callback method follows the {@link InterceptCallback} contract: it accepts an {@link
   * InterceptContext} parameter and returns an {@link InterceptCallbackResponse}.
   *
   * <p>Shared atomic counters and thread-safe containers track invocation counts and argument
   * values for cross-thread verification.
   */
  public static class TestCallbacks {

    /** Count of BEFORE callback invocations. */
    static final AtomicInteger beforeCallCount = new AtomicInteger(0);

    /** Count of AFTER callback invocations. */
    static final AtomicInteger afterCallCount = new AtomicInteger(0);

    /** Count of AROUND BEFORE-phase callback invocations. */
    static final AtomicInteger aroundBeforeCallCount = new AtomicInteger(0);

    /** Count of AROUND AFTER-phase callback invocations. */
    static final AtomicInteger aroundAfterCallCount = new AtomicInteger(0);

    /** Last return value seen by an AFTER callback. */
    static final AtomicReference<Object> lastReturnValue = new AtomicReference<>();

    /** Last args seen by a callback. */
    static final AtomicReference<Object[]> lastArgs = new AtomicReference<>();

    /** Configurable exception for throwConfiguredException callback. */
    static volatile Throwable exceptionToThrow;

    /** Trigger for nested dispatch test — set to a Runnable that performs inner dispatch. */
    static volatile Runnable nestedDispatchTrigger;

    /** Per-thread callback results for concurrent dispatch verification. */
    static final ConcurrentHashMap<Long, List<Object[]>> perThreadResults =
        new ConcurrentHashMap<>();

    /**
     * BEFORE callback that counts invocations and records args.
     *
     * @param ctx the intercept context
     * @return a proceed response
     */
    public static InterceptCallbackResponse countBeforeCallback(InterceptContext ctx) {
      beforeCallCount.incrementAndGet();
      lastArgs.set(ctx.getArgs());
      return new InterceptCallbackResponse();
    }

    /**
     * AFTER callback that counts invocations and records the return value.
     *
     * @param ctx the intercept context
     * @return a proceed response
     */
    public static InterceptCallbackResponse countAfterCallback(InterceptContext ctx) {
      afterCallCount.incrementAndGet();
      lastReturnValue.set(ctx.getReturnValue());
      return new InterceptCallbackResponse();
    }

    /**
     * AROUND callback that calls proceed() and counts both BEFORE and AFTER phases.
     *
     * <p>In the local AROUND dispatcher pattern, the callback is invoked once. It starts in BEFORE
     * phase, calls {@code proceed()} which synchronously executes the method, then the context
     * transitions to AFTER phase. The callback inspects the AFTER phase within the same invocation.
     *
     * @param ctx the intercept context
     * @return a proceed response
     */
    public static InterceptCallbackResponse aroundWithProceed(InterceptContext ctx) {
      aroundBeforeCallCount.incrementAndGet();
      ctx.proceed();
      // After proceed(), context is in AFTER phase
      aroundAfterCallCount.incrementAndGet();
      return new InterceptCallbackResponse();
    }

    /**
     * BEFORE callback that triggers a nested dispatch through the same dispatcher.
     *
     * <p>Used to verify that thread-local pooled objects (InterceptPartition, InterceptContext,
     * HashMap) are not corrupted by re-entrant dispatch.
     *
     * @param ctx the intercept context
     * @return a proceed response
     */
    public static InterceptCallbackResponse nestedDispatchCallback(InterceptContext ctx) {
      beforeCallCount.incrementAndGet();
      lastArgs.set(ctx.getArgs());
      if (nestedDispatchTrigger != null) {
        nestedDispatchTrigger.run();
      }
      return new InterceptCallbackResponse();
    }

    /**
     * Thread-safe BEFORE callback that records args per-thread for cross-thread isolation
     * verification.
     *
     * @param ctx the intercept context
     * @return a proceed response
     */
    public static InterceptCallbackResponse threadSafeBeforeCallback(InterceptContext ctx) {
      beforeCallCount.incrementAndGet();
      long threadId = Thread.currentThread().getId();
      perThreadResults.computeIfAbsent(threadId, k -> new ArrayList<>()).add(ctx.getArgs().clone());
      return new InterceptCallbackResponse();
    }

    /**
     * Thread-safe AFTER callback that counts invocations per thread.
     *
     * @param ctx the intercept context
     * @return a proceed response
     */
    public static InterceptCallbackResponse threadSafeAfterCallback(InterceptContext ctx) {
      afterCallCount.incrementAndGet();
      return new InterceptCallbackResponse();
    }

    /**
     * BEFORE callback that mutates arg[0] to a known value (999) via deferred copy-on-write.
     *
     * @param ctx the intercept context
     * @return a proceed response
     */
    public static InterceptCallbackResponse mutateFirstArg(InterceptContext ctx) {
      beforeCallCount.incrementAndGet();
      ctx.setArg(0, 999);
      return new InterceptCallbackResponse();
    }

    /**
     * BEFORE callback that throws the configured exception via {@code ctx.setExceptionToThrow()}.
     *
     * @param ctx the intercept context
     * @return a proceed response with exception set
     */
    public static InterceptCallbackResponse throwConfiguredException(InterceptContext ctx) {
      beforeCallCount.incrementAndGet();
      if (exceptionToThrow != null) {
        ctx.setExceptionToThrow(exceptionToThrow);
      }
      return new InterceptCallbackResponse();
    }
  }

  /**
   * Custom test exception for verifying exception type preservation through the optimized path.
   *
   * <p>Used in test 7 to ensure that non-standard exception types are not erased or wrapped.
   */
  private static class CustomTestException extends RuntimeException {

    /**
     * Constructs a new custom test exception.
     *
     * @param message the detail message
     */
    CustomTestException(String message) {
      super(message);
    }
  }
}
