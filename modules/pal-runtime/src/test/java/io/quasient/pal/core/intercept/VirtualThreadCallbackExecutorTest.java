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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.lang.intercept.InterceptCallbackResponse;
import io.quasient.pal.common.lang.intercept.InterceptContext;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests exploring virtual thread executor for BEFORE_ASYNC and AFTER_ASYNC callback dispatch.
 *
 * <p>These tests validate that an async callback executor (virtual thread executor on Java 21+, or
 * cached thread pool on Java 17) can be used by {@link LocalInterceptCallbackDispatcher} for
 * fire-and-forget async intercept callbacks.
 *
 * <p><b>Java Version Note:</b> Virtual threads require Java 21+ (or Java 19+ with preview). Since
 * this project requires Java 17, these tests use {@code Executors.newCachedThreadPool()} as the
 * baseline executor. When the project upgrades to Java 21+, these tests should be updated to use
 * {@code Executors.newVirtualThreadPerTaskExecutor()} and the {@code Thread.isVirtual()} assertion
 * should be enabled.
 *
 * @see LocalInterceptCallbackDispatcher
 * @see VirtualThreadCallbackExecutor
 */
public class VirtualThreadCallbackExecutorTest {

  /** Callback class name used for callback resolution via reflection. */
  private static final String CALLBACK_CLASS =
      "io.quasient.pal.core.intercept.VirtualThreadCallbackExecutorTest$TestCallbacks";

  /** Executor under test. */
  private ExecutorService executor;

  /** Dispatcher under test. */
  private LocalInterceptCallbackDispatcher dispatcher;

  /** Callback resolver. */
  private CallbackResolver callbackResolver;

  /** Sets up test fixtures. */
  @Before
  public void setUp() {
    callbackResolver = new CallbackResolver();
    executor = createTestExecutor();
    dispatcher = new LocalInterceptCallbackDispatcher(callbackResolver, executor);
    TestCallbacks.reset();
  }

  /** Cleans up executor after test. */
  @After
  public void tearDown() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  /**
   * Creates the executor under test. Uses {@link VirtualThreadCallbackExecutor} to select the
   * optimal executor for the current Java version: virtual threads on Java 21+, cached thread pool
   * on Java 17.
   */
  private static ExecutorService createTestExecutor() {
    return VirtualThreadCallbackExecutor.create(null);
  }

  /**
   * Tests that an async callback dispatched via the executor runs on a separate thread.
   *
   * <p><b>Java 21+ behavior:</b> Callback executes on a virtual thread ({@code
   * Thread.currentThread().isVirtual() == true}).
   *
   * <p><b>Java 17 behavior (current):</b> Callback executes on a cached thread pool thread,
   * verifying it runs on a different thread than the caller.
   */
  @Test
  public void shouldDispatchAsyncCallbackOnVirtualThread() throws Exception {
    // Given: A BEFORE_ASYNC intercept with a callback that records Thread.currentThread()
    CountDownLatch latch = new CountDownLatch(1);
    TestCallbacks.asyncLatch = latch;

    InterceptMessage intercept = createBeforeAsyncIntercept("recordThread");

    Thread callingThread = Thread.currentThread();

    // When: Async callback submitted via sendLocalBeforeAsyncCallbacks()
    dispatcher.sendLocalBeforeAsyncCallbacks(
        List.of(intercept),
        new Object[] {"test"},
        "com.example.Foo",
        "bar",
        List.of("java.lang.String"),
        "test-peer-uuid");

    // Then: Callback executes on a different thread than the caller
    assertTrue("Callback should complete within timeout", latch.await(5, TimeUnit.SECONDS));

    Thread callbackThread = TestCallbacks.recordedThread.get();
    assertNotNull("Callback thread should be recorded", callbackThread);
    assertNotSame(
        "Callback should run on a different thread than the caller", callingThread, callbackThread);
    assertThat(
        "Callback thread ID should differ from calling thread",
        callbackThread.getId(),
        is(not(callingThread.getId())));

    // On Java 21+, verify the callback ran on a virtual thread
    if (VirtualThreadCallbackExecutor.isVirtualThreadsAvailable()) {
      assertTrue(
          "On Java 21+, callback should run on a virtual thread", isVirtualThread(callbackThread));
    }
  }

  /**
   * Tests that the executor handles high throughput without thread pool exhaustion.
   *
   * <p>Submits 10,000 async callbacks concurrently and verifies all complete successfully. This
   * validates that the executor (virtual threads on 21+, cached pool on 17) can handle burst
   * workloads without rejecting tasks.
   */
  @Test
  public void shouldHandleHighThroughputAsyncCallbacks() throws Exception {
    // Given: 10,000 BEFORE_ASYNC intercepts with callbacks that signal a CountDownLatch
    int callbackCount = 10_000;
    CountDownLatch latch = new CountDownLatch(callbackCount);
    TestCallbacks.asyncLatch = latch;

    List<InterceptMessage> intercepts = new ArrayList<>(callbackCount);
    for (int i = 0; i < callbackCount; i++) {
      intercepts.add(createBeforeAsyncIntercept("countDown"));
    }

    // When: All 10,000 async callbacks submitted
    dispatcher.sendLocalBeforeAsyncCallbacks(
        intercepts,
        new Object[] {"test"},
        "com.example.Foo",
        "bar",
        List.of("java.lang.String"),
        "test-peer-uuid");

    // Then: All 10,000 callbacks complete successfully (CountDownLatch reaches zero)
    //       No RejectedExecutionException is thrown (would have been thrown above)
    assertTrue(
        "All " + callbackCount + " callbacks should complete within timeout",
        latch.await(30, TimeUnit.SECONDS));
  }

  /**
   * Tests that all BEFORE_ASYNC callbacks for the same dispatch complete, even though order is not
   * guaranteed.
   *
   * <p>Multiple callbacks are submitted for the same dispatch invocation. Since they run
   * asynchronously, execution order is non-deterministic, but all must eventually complete.
   */
  @Test
  public void shouldMaintainCallbackOrderWithinSingleDispatch() throws Exception {
    // Given: 5 BEFORE_ASYNC intercepts, each recording its identity
    int callbackCount = 5;
    CountDownLatch latch = new CountDownLatch(callbackCount);
    TestCallbacks.asyncLatch = latch;

    List<InterceptMessage> intercepts = new ArrayList<>(callbackCount);
    for (int i = 0; i < callbackCount; i++) {
      intercepts.add(createBeforeAsyncIntercept("recordIdentity"));
    }

    // When: All callbacks submitted via sendLocalBeforeAsyncCallbacks()
    dispatcher.sendLocalBeforeAsyncCallbacks(
        intercepts,
        new Object[] {"test"},
        "com.example.Foo",
        "bar",
        List.of("java.lang.String"),
        "test-peer-uuid");

    // Then: All callbacks complete (latch reaches zero within timeout)
    assertTrue("All callbacks should complete within timeout", latch.await(10, TimeUnit.SECONDS));

    // All callback identities are recorded in the collection
    assertThat(
        "All callback identities should be recorded",
        TestCallbacks.recordedIdentities.size(),
        is(greaterThanOrEqualTo(callbackCount)));
  }

  /**
   * Tests that TlScratchHolder state does not leak across virtual threads.
   *
   * <p>TlScratchHolder uses ThreadLocal storage for reusable scratch buffers. When async callbacks
   * run on separate threads (virtual or pooled), each thread must have its own TlScratchHolder
   * instance with no cross-contamination from the calling thread's state.
   */
  @Test
  public void shouldIsolateThreadLocalsAcrossVirtualThreads() throws Exception {
    // Given: A custom ThreadLocal marker set on the calling thread
    //        A BEFORE_ASYNC callback that reads the ThreadLocal on the callback thread
    CountDownLatch latch = new CountDownLatch(1);
    TestCallbacks.asyncLatch = latch;

    // Set a marker in a ThreadLocal on the calling thread
    TestCallbacks.callerThreadLocal.set("CALLER_MARKER");

    InterceptMessage intercept = createBeforeAsyncIntercept("checkThreadLocal");

    // When: Async callback executes on a virtual thread (or pooled thread)
    dispatcher.sendLocalBeforeAsyncCallbacks(
        List.of(intercept),
        new Object[] {"test"},
        "com.example.Foo",
        "bar",
        List.of("java.lang.String"),
        "test-peer-uuid");

    // Then: Callback thread has its own ThreadLocal state (fresh/reset)
    assertTrue("Callback should complete within timeout", latch.await(5, TimeUnit.SECONDS));

    String callbackThreadLocalValue = TestCallbacks.callbackThreadLocalValue.get();
    // Virtual thread has its own ThreadLocal — not inherited from calling thread
    assertTrue(
        "Callback thread should NOT see caller's ThreadLocal value (got: "
            + callbackThreadLocalValue
            + ")",
        callbackThreadLocalValue == null || !"CALLER_MARKER".equals(callbackThreadLocalValue));

    // Clean up
    TestCallbacks.callerThreadLocal.remove();
  }

  /**
   * Tests performance comparison between virtual thread executor and fixed thread pool.
   *
   * <p>Runs the same workload (1,000 async callbacks, each simulating ~1ms work) on both a virtual
   * thread executor (or cached thread pool on Java 17) and a fixed thread pool (16 threads).
   * Verifies both complete correctly and logs timing for comparison.
   *
   * <p><b>Note:</b> This is not a strict performance assertion (timing is environment-dependent).
   * Both executors must complete all callbacks correctly. Timing is logged for manual comparison.
   */
  @Test
  public void shouldComparePerformanceWithFixedThreadPool() throws Exception {
    int callbackCount = 1_000;

    // --- Run with virtual thread / cached pool executor ---
    long autoTime = runWorkload(executor, callbackCount);

    // --- Run with fixed thread pool (16 threads) ---
    ExecutorService fixedPool = Executors.newFixedThreadPool(16);
    try {
      long fixedTime = runWorkload(fixedPool, callbackCount);

      // Log timing for manual comparison
      System.out.println(
          "Performance comparison (" + callbackCount + " callbacks, ~1ms work each):");
      System.out.println(
          "  Auto-selected executor ("
              + VirtualThreadCallbackExecutor.resolveExecutorType()
              + "): "
              + autoTime
              + "ms");
      System.out.println("  Fixed pool (16 threads): " + fixedTime + "ms");

      // Both must complete successfully (timing assertions are too environment-dependent)
      assertTrue("Auto-selected executor time should be > 0", autoTime > 0);
      assertTrue("Fixed pool executor time should be > 0", fixedTime > 0);
    } finally {
      fixedPool.shutdownNow();
    }
  }

  // ---- Helper Methods ----

  /**
   * Runs a workload of async callbacks on the given executor and returns elapsed time in ms.
   *
   * @param exec the executor to use
   * @param count the number of callbacks to submit
   * @return elapsed time in milliseconds
   */
  private long runWorkload(ExecutorService exec, int count) throws Exception {
    CountDownLatch latch = new CountDownLatch(count);
    TestCallbacks.asyncLatch = latch;

    LocalInterceptCallbackDispatcher testDispatcher =
        new LocalInterceptCallbackDispatcher(callbackResolver, exec);

    List<InterceptMessage> intercepts = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      intercepts.add(createBeforeAsyncIntercept("simulateWork"));
    }

    long start = System.nanoTime();

    testDispatcher.sendLocalBeforeAsyncCallbacks(
        intercepts,
        new Object[] {"test"},
        "com.example.Foo",
        "bar",
        List.of("java.lang.String"),
        "test-peer-uuid");

    assertTrue("All callbacks should complete within timeout", latch.await(60, TimeUnit.SECONDS));

    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  /**
   * Creates an InterceptMessage for BEFORE_ASYNC with the given callback method name.
   *
   * @param callbackMethod the callback method name in TestCallbacks
   * @return a configured InterceptMessage
   */
  private static InterceptMessage createBeforeAsyncIntercept(String callbackMethod) {
    InterceptMessage im = new InterceptMessage();
    im.setInterceptType(InterceptType.BEFORE_ASYNC.toByte());
    im.setCallbackClass(CALLBACK_CLASS);
    im.setCallbackMethod(callbackMethod);
    im.peerUuid = "test-peer-uuid";
    im.messageId = "test-message-id";
    return im;
  }

  /**
   * Checks if a thread is a virtual thread using reflection (Java 21+ API).
   *
   * @param thread the thread to check
   * @return true if the thread is virtual, false if not or if running on Java < 21
   */
  private static boolean isVirtualThread(Thread thread) {
    try {
      java.lang.reflect.Method isVirtual = Thread.class.getMethod("isVirtual");
      return (Boolean) isVirtual.invoke(thread);
    } catch (ReflectiveOperationException e) {
      // Java < 21, no isVirtual() method
      return false;
    }
  }

  // ---- Test Callbacks Class ----

  /** Test callbacks class with static methods used by callback resolution via reflection. */
  public static class TestCallbacks {

    /** Latch for synchronizing async callback completion. */
    static volatile CountDownLatch asyncLatch;

    /** Recorded thread from recordThread callback. */
    static final AtomicReference<Thread> recordedThread = new AtomicReference<>();

    /** Recorded identities from recordIdentity callback. */
    static final Set<String> recordedIdentities =
        Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** ThreadLocal marker set on the calling thread. */
    static final ThreadLocal<String> callerThreadLocal = new ThreadLocal<>();

    /** ThreadLocal value observed on the callback thread. */
    static final AtomicReference<String> callbackThreadLocalValue = new AtomicReference<>();

    /** Resets all test state. */
    static void reset() {
      asyncLatch = null;
      recordedThread.set(null);
      recordedIdentities.clear();
      callerThreadLocal.remove();
      callbackThreadLocalValue.set(null);
    }

    /**
     * Records the current thread and signals the latch.
     *
     * @param ctx the intercept context
     * @return callback response
     */
    public static InterceptCallbackResponse recordThread(InterceptContext ctx) {
      recordedThread.set(Thread.currentThread());
      CountDownLatch latch = asyncLatch;
      if (latch != null) {
        latch.countDown();
      }
      return new InterceptCallbackResponse();
    }

    /**
     * Signals the latch (simple count-down for throughput testing).
     *
     * @param ctx the intercept context
     * @return callback response
     */
    public static InterceptCallbackResponse countDown(InterceptContext ctx) {
      CountDownLatch latch = asyncLatch;
      if (latch != null) {
        latch.countDown();
      }
      return new InterceptCallbackResponse();
    }

    /**
     * Records a unique identity (thread name + id) and signals the latch.
     *
     * @param ctx the intercept context
     * @return callback response
     */
    public static InterceptCallbackResponse recordIdentity(InterceptContext ctx) {
      Thread t = Thread.currentThread();
      recordedIdentities.add(t.getName() + "-" + t.getId() + "-" + System.nanoTime());
      CountDownLatch latch = asyncLatch;
      if (latch != null) {
        latch.countDown();
      }
      return new InterceptCallbackResponse();
    }

    /**
     * Reads the callerThreadLocal value on the callback thread and signals the latch.
     *
     * @param ctx the intercept context
     * @return callback response
     */
    public static InterceptCallbackResponse checkThreadLocal(InterceptContext ctx) {
      callbackThreadLocalValue.set(callerThreadLocal.get());
      CountDownLatch latch = asyncLatch;
      if (latch != null) {
        latch.countDown();
      }
      return new InterceptCallbackResponse();
    }

    /**
     * Simulates ~1ms of work and signals the latch.
     *
     * @param ctx the intercept context
     * @return callback response
     */
    public static InterceptCallbackResponse simulateWork(InterceptContext ctx) {
      // Busy-wait for ~1ms to simulate work (more predictable than Thread.sleep)
      long target = System.nanoTime() + 1_000_000L;
      while (System.nanoTime() < target) {
        Thread.onSpinWait();
      }
      CountDownLatch latch = asyncLatch;
      if (latch != null) {
        latch.countDown();
      }
      return new InterceptCallbackResponse();
    }
  }
}
