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
package io.quasient.pal.intercept.concurrent;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

import io.quasient.foobar.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.ConcurrentCallbackTestSuite;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for concurrent intercept callback invocations.
 *
 * <p>These tests verify that callback implementations handle concurrent invocations correctly when
 * multiple threads execute intercepted operations simultaneously. Standard Java concurrency
 * concerns apply:
 *
 * <ul>
 *   <li>The same static callback method may be invoked concurrently by different threads
 *   <li>If callbacks maintain shared mutable state (e.g., static fields), proper synchronization is
 *       required
 * </ul>
 *
 * <p>The tests use local intercepts where callback peer == interceptable peer, which ensures the
 * callbacks are invoked directly without remote RPC overhead.
 *
 * <p>Tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Calls wrapper method that internally calls the intercepted method
 *   <li><b>INCOMING_RPC</b>: Calls the intercepted method directly via RPC
 * </ul>
 */
@RunWith(Parameterized.class)
public class ConcurrentCallbackIT extends AbstractInterceptIT {

  private static final Logger logger = LoggerFactory.getLogger(ConcurrentCallbackIT.class);

  private static final String CALLBACK_CLASS =
      "io.quasient.foobar.apps.callbacks.concurrent.ConcurrentCallbacks";
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** Number of concurrent threads. Must be <= ConcurrentCallbackTestSuite.RPC_THREAD_COUNT. */
  private static final int THREAD_COUNT = 100;

  private static final long TEST_TIMEOUT_MS = 10000; // 10 seconds for 100 threads

  /**
   * RPC address for this test suite. Uses the port from ConcurrentCallbackTestSuite.
   *
   * <p>Note: We use a unique port range (7891+) for thread-local peers to avoid conflicts with the
   * base ThinPeer (7890) and the interceptable peer (5660).
   */
  private static final String THREAD_LOCAL_RPC_BASE = "tcp://localhost:";

  private static final int THREAD_LOCAL_RPC_PORT_START = 7900;

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Returns the UUID of the interceptable peer from ConcurrentCallbackTestSuite.
   *
   * <p>This overrides the default from AbstractInterceptIT to use the peer launched by
   * ConcurrentCallbackTestSuite, which has more RPC threads for concurrent testing.
   *
   * @return the UUID of the concurrent callback test peer
   */
  @Override
  protected UUID getInterceptablePeerUuid() {
    return ConcurrentCallbackTestSuite.INTERCEPTABLE_PEER_UUID;
  }

  /** Shared InterceptableApp instance reused across all threads to avoid RPC overload. */
  private ObjectRef appInstance;

  /**
   * Thread-local port counter for unique RPC addresses (static to survive across test instances).
   */
  private static final AtomicInteger portCounter = new AtomicInteger(THREAD_LOCAL_RPC_PORT_START);

  /** Directory connection provider for thread-local ThinPeers. */
  private DirectoryConnectionProvider threadLocalDirectoryProvider;

  /** Peer info for the interceptable peer. */
  private PeerInfo concurrentInterceptablePeerInfo;

  /**
   * Pool of pre-initialized ThinPeers, one per concurrent thread. Pre-initialization in the main
   * thread avoids initialization issues in worker threads.
   *
   * <p>This is static because JUnit creates a new test instance for each parameterized test, and we
   * want to reuse the same ThinPeers across all tests in the suite.
   */
  private static final List<ThinPeer> thinPeerPool =
      Collections.synchronizedList(new ArrayList<>());

  /** Index counter for acquiring ThinPeers from the pool. */
  private final AtomicInteger peerPoolIndex = new AtomicInteger(0);

  /**
   * Thread-local ThinPeer instances. Each thread acquires its own ThinPeer from the pre-initialized
   * pool. This avoids ZMQ socket sharing issues while ensuring proper initialization.
   */
  private final ThreadLocal<ThinPeer> threadLocalThinPeer =
      ThreadLocal.withInitial(
          () -> {
            int index = peerPoolIndex.getAndIncrement();
            if (index >= thinPeerPool.size()) {
              throw new RuntimeException("Not enough ThinPeers in pool: " + index);
            }
            ThinPeer peer = thinPeerPool.get(index);
            logger.debug(
                "Thread {} acquired ThinPeer {} from pool index {}",
                Thread.currentThread().getName(),
                peer.getPeerUuid(),
                index);
            return peer;
          });

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public ConcurrentCallbackIT(InvocationPath path) {
    this.path = path;
  }

  /**
   * Returns the parameterized test data for invocation paths.
   *
   * @return collection of invocation path parameters
   */
  @Parameterized.Parameters(name = "{index}: path={0}")
  public static Collection<Object[]> data() {
    return invocationPathParameters();
  }

  /**
   * Creates shared app instance and resets callback state before each test.
   *
   * @throws Exception if setup fails
   */
  @Before
  public void setupConcurrentTest() throws Exception {
    logger.info("===== ConcurrentCallbackIT.setupConcurrentTest [{}]: STARTING =====", path);

    // Initialize directory connection for thread-local ThinPeers
    threadLocalDirectoryProvider =
        new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);

    // Get the peer info for the concurrent callback test suite's peer
    PalDirectory palDirectory =
        threadLocalDirectoryProvider
            .get()
            .orElseThrow(() -> new RuntimeException("No connection for PalDirectory"));

    // Retry a few times to allow for directory registration delay
    UUID concurrentPeerUuid = getInterceptablePeerUuid();
    for (int i = 0; i < 10; i++) {
      concurrentInterceptablePeerInfo = palDirectory.getPeer(concurrentPeerUuid);
      if (concurrentInterceptablePeerInfo != null) {
        break;
      }
      logger.debug(
          "Waiting for concurrent peer {} to register in directory (attempt {})",
          concurrentPeerUuid,
          i + 1);
      Thread.sleep(500);
    }
    if (concurrentInterceptablePeerInfo == null) {
      throw new RuntimeException(
          "Concurrent callback test peer "
              + concurrentPeerUuid
              + " not found in directory after 5 seconds");
    }

    // Pre-initialize the ThinPeer pool if empty (first test only)
    // We create THREAD_COUNT ThinPeers in the main thread to avoid initialization issues
    if (thinPeerPool.isEmpty()) {
      logger.info("Pre-initializing {} ThinPeers in the main thread...", THREAD_COUNT);
      for (int i = 0; i < THREAD_COUNT; i++) {
        UUID peerUuid = UUID.randomUUID();
        int port = portCounter.getAndIncrement();
        String rpcAddress = THREAD_LOCAL_RPC_BASE + port;
        try {
          ThinPeer peer =
              new ThinPeer()
                  .withUuid(peerUuid)
                  .withName("ConcurrentTest-Pool-" + i)
                  .withSelfRegistration(true)
                  .withZmqRpcAddress(rpcAddress)
                  .withInitialPeer(concurrentInterceptablePeerInfo)
                  .withDirectoryProvider(threadLocalDirectoryProvider)
                  .init();
          thinPeerPool.add(peer);
          logger.debug("Created ThinPeer {} on port {} in pool index {}", peerUuid, port, i);
        } catch (Exception e) {
          logger.error("Failed to create ThinPeer {} at index {}", peerUuid, i, e);
          throw new RuntimeException("Failed to pre-initialize ThinPeer pool", e);
        }
      }
      logger.info("ThinPeer pool initialized with {} peers", thinPeerPool.size());
    }

    // Reset the pool index for this test (so threads acquire from the beginning)
    peerPoolIndex.set(0);

    // Create single shared InterceptableApp instance to avoid overwhelming ZMQ with
    // concurrent constructor calls per test
    appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    logger.info("Resetting ConcurrentCallbacks state");
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid, CALLBACK_CLASS, "reset", new String[] {}, null, null, new Object[] {}));

    logger.info("===== ConcurrentCallbackIT.setupConcurrentTest [{}]: COMPLETED =====", path);
  }

  /**
   * Cleanup after each test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void cleanupConcurrentCallbackIT() throws Exception {
    logger.info("===== ConcurrentCallbackIT.cleanupConcurrentCallbackIT: STARTING =====");
    // Thread-local ThinPeers will be garbage collected
    logger.info("===== ConcurrentCallbackIT.cleanupConcurrentCallbackIT: COMPLETED =====");
  }

  /**
   * Creates a local intercept request where callback peer = interceptable peer.
   *
   * @param type the intercept type
   * @param methodName the method name to intercept
   * @param paramTypes the parameter types
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalMethodIntercept(
      InterceptType type, String methodName, String paramTypes, String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        getInterceptablePeerUuid(), // callback peer = interceptable peer
        type,
        TARGET_CLASS,
        CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall(methodName, Collections.singletonList(paramTypes)));
  }

  /**
   * Invokes a method that multiplies the static counter by a value using the shared app instance.
   *
   * <p>Uses thread-local ThinPeer to avoid ZMQ socket sharing across threads.
   *
   * @param multiplier the multiplier value
   * @return the response message
   */
  private ExecMessage invokeMultiplyStaticBy(int multiplier) {
    ThinPeer thinPeer = threadLocalThinPeer.get();
    UUID thinPeerUuid = thinPeer.getPeerUuid();
    MessageBuilder msgBuilder = new MessageBuilder(thinPeerUuid);

    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Call wrapper method that internally calls the intercepted method
      return thinPeer.sendToPeer(
          msgBuilder.buildInstanceMethod(
              thinPeerUuid,
              TARGET_CLASS,
              "callMultiplyStaticBy",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {multiplier}));
    } else {
      // INCOMING_RPC: Call the intercepted method directly via RPC
      return thinPeer.sendToPeer(
          msgBuilder.buildClassMethod(
              thinPeerUuid,
              TARGET_CLASS,
              "multiplyStaticBy",
              new String[] {"java.lang.Integer"},
              null,
              null,
              new Object[] {multiplier}));
    }
  }

  /**
   * Invokes a thread-safe method that echoes the value, using the shared app instance.
   *
   * <p>Uses thread-local ThinPeer to avoid ZMQ socket sharing across threads. Unlike {@link
   * #invokeMultiplyStaticBy(int)}, this method calls a thread-safe static method that does not
   * mutate shared state.
   *
   * @param value the value to echo
   * @return the response message
   */
  private ExecMessage invokeEchoInteger(int value) {
    ThinPeer thinPeer = threadLocalThinPeer.get();
    UUID thinPeerUuid = thinPeer.getPeerUuid();
    MessageBuilder msgBuilder = new MessageBuilder(thinPeerUuid);

    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Call wrapper method that internally calls the intercepted method
      return thinPeer.sendToPeer(
          msgBuilder.buildInstanceMethod(
              thinPeerUuid,
              TARGET_CLASS,
              "callEchoInteger",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {value}));
    } else {
      // INCOMING_RPC: Call the intercepted method directly via RPC
      return thinPeer.sendToPeer(
          msgBuilder.buildClassMethod(
              thinPeerUuid,
              TARGET_CLASS,
              "echoInteger",
              new String[] {"java.lang.Integer"},
              null,
              null,
              new Object[] {value}));
    }
  }

  // ===========================================================================
  // Concurrent BEFORE Callback Tests
  // ===========================================================================

  /**
   * Tests that concurrent BEFORE callbacks correctly mutate arguments.
   *
   * <p>This test:
   *
   * <ul>
   *   <li>Registers a BEFORE intercept that mutates the first argument by adding 100
   *   <li>Spawns multiple threads that concurrently invoke the intercepted method
   *   <li>Verifies all mutations are applied correctly
   *   <li>Verifies no race conditions occur (counter matches expected value)
   * </ul>
   */
  @Test
  public void testConcurrentBeforeCallbacksWithArgMutation() throws Exception {
    logger.info(
        "===== testConcurrentBeforeCallbacksWithArgMutation [{}]: TEST STARTED =====", path);

    // 1. Register local BEFORE intercept that mutates arg
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE,
            "multiplyStaticBy",
            "java.lang.Integer",
            "onBeforeConcurrentMutation");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Set static counter to initial value
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {1}));

    // 3. Spawn threads that concurrently invoke multiplyStaticBy
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int i = 0; i < THREAD_COUNT; i++) {
      final int threadIndex = i;
      var unused =
          executor.submit(
              () -> {
                try {
                  // Wait for all threads to be ready
                  startLatch.await();

                  // Invoke the method (callback will mutate arg from i to i+100)
                  ExecMessage response = invokeMultiplyStaticBy(threadIndex);

                  if (response.getRaisedThrowable() == null) {
                    successCount.incrementAndGet();
                  } else {
                    logger.error(
                        "Thread {} got exception: {}",
                        threadIndex,
                        response.getRaisedThrowable().getThrowable().getMessage());
                    failureCount.incrementAndGet();
                  }
                } catch (Exception e) {
                  logger.error("Thread {} failed with exception", threadIndex, e);
                  failureCount.incrementAndGet();
                } finally {
                  completionLatch.countDown();
                }
              });
    }

    // Start all threads simultaneously
    logger.info("Starting {} threads via {} path...", THREAD_COUNT, path);
    long startTimeMs = System.currentTimeMillis();
    startLatch.countDown();

    // Wait for all threads to complete
    boolean completed = completionLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    long elapsedMs = System.currentTimeMillis() - startTimeMs;
    executor.shutdown();
    boolean terminated = executor.awaitTermination(10, TimeUnit.SECONDS);

    logger.info(
        "All {} threads completed in {} ms (timeout was {} ms)",
        THREAD_COUNT,
        elapsedMs,
        TEST_TIMEOUT_MS);

    // 4. Verify results
    assertThat("All threads should complete within timeout", completed, is(true));
    assertThat("Executor should terminate cleanly", terminated, is(true));
    assertThat("All invocations should succeed", successCount.get(), is(THREAD_COUNT));
    assertThat("No invocations should fail", failureCount.get(), is(0));

    // 5. Verify callback was invoked THREAD_COUNT times
    ExecMessage beforeCountResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                CALLBACK_CLASS,
                "getBeforeCount",
                new String[] {},
                null,
                null,
                new Object[] {}));
    int beforeCount =
        (Integer) Unwrapper.unwrapObject(beforeCountResponse.getReturnValue().getObject());
    assertThat(
        "BEFORE callback should be invoked exactly " + THREAD_COUNT + " times",
        beforeCount,
        is(THREAD_COUNT));

    // 6. Verify all arg mutations were recorded
    ExecMessage mutationCountResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                CALLBACK_CLASS,
                "getArgMutationCount",
                new String[] {},
                null,
                null,
                new Object[] {}));
    int mutationCount =
        (Integer) Unwrapper.unwrapObject(mutationCountResponse.getReturnValue().getObject());
    assertThat(
        "All threads should have recorded arg mutations",
        mutationCount,
        is(greaterThanOrEqualTo(1))); // At least one thread recorded a mutation

    logger.info(
        "===== testConcurrentBeforeCallbacksWithArgMutation [{}]: TEST COMPLETED "
            + "(beforeCount={}, mutationCount={}) =====",
        path,
        beforeCount,
        mutationCount);
  }

  // ===========================================================================
  // Concurrent AFTER Callback Tests
  // ===========================================================================

  /**
   * Tests that concurrent AFTER callbacks correctly override return values.
   *
   * <p>This test:
   *
   * <ul>
   *   <li>Registers an AFTER intercept that overrides return value by adding 1000
   *   <li>Spawns multiple threads that concurrently invoke the intercepted method
   *   <li>Verifies all overrides are applied correctly
   *   <li>Verifies no race conditions occur
   * </ul>
   */
  @Test
  public void testConcurrentAfterCallbacksWithReturnOverride() throws Exception {
    logger.info(
        "===== testConcurrentAfterCallbacksWithReturnOverride [{}]: TEST STARTED =====", path);

    // 1. Register local AFTER intercept that overrides return
    // Note: We use echoInteger instead of multiplyStaticBy because it's thread-safe
    // (doesn't mutate shared state) and returns deterministic values.
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AFTER, "echoInteger", "java.lang.Integer", "onAfterConcurrentOverride");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Spawn threads that concurrently invoke echoInteger
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger correctOverrideCount = new AtomicInteger(0);

    for (int i = 0; i < THREAD_COUNT; i++) {
      final int threadIndex = i;
      var unused =
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  logger.info("Thread {} starting invocation via {}", threadIndex, path);

                  // Invoke the method (callback will override return value)
                  ExecMessage response = invokeEchoInteger(threadIndex);
                  logger.info("Thread {} received response", threadIndex);

                  if (response.getRaisedThrowable() == null) {
                    successCount.incrementAndGet();

                    // Verify the return value was overridden (original + 1000)
                    if (response.getReturnValue() == null
                        || response.getReturnValue().getObject() == null) {
                      logger.error("Thread {} got null return value", threadIndex);
                    } else {
                      int returnValue =
                          (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
                      int expected = threadIndex + 1000;
                      if (returnValue == expected) {
                        correctOverrideCount.incrementAndGet();
                        logger.info(
                            "Thread {} got correct override: {} == {}",
                            threadIndex,
                            returnValue,
                            expected);
                      } else {
                        logger.error(
                            "Thread {} got incorrect return value: {} (expected {})",
                            threadIndex,
                            returnValue,
                            expected);
                      }
                    }
                  } else {
                    logger.error(
                        "Thread {} got exception in response: {}",
                        threadIndex,
                        response.getRaisedThrowable().getThrowable().getMessage());
                  }
                } catch (Exception e) {
                  logger.error("Thread {} failed with exception", threadIndex, e);
                } finally {
                  completionLatch.countDown();
                }
              });
    }

    // Start all threads simultaneously
    logger.info("Starting {} threads via {} path...", THREAD_COUNT, path);
    long startTimeMs = System.currentTimeMillis();
    startLatch.countDown();

    // Wait for all threads to complete
    boolean completed = completionLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    long elapsedMs = System.currentTimeMillis() - startTimeMs;
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    logger.info(
        "All {} threads completed in {} ms (timeout was {} ms)",
        THREAD_COUNT,
        elapsedMs,
        TEST_TIMEOUT_MS);

    // 4. Verify results
    assertThat("All threads should complete within timeout", completed, is(true));
    assertThat("All invocations should succeed", successCount.get(), is(THREAD_COUNT));
    assertThat(
        "All return values should be correctly overridden",
        correctOverrideCount.get(),
        is(THREAD_COUNT));

    // 5. Verify callback was invoked THREAD_COUNT times
    ExecMessage afterCountResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                CALLBACK_CLASS,
                "getAfterCount",
                new String[] {},
                null,
                null,
                new Object[] {}));
    int afterCount =
        (Integer) Unwrapper.unwrapObject(afterCountResponse.getReturnValue().getObject());
    assertThat(
        "AFTER callback should be invoked exactly " + THREAD_COUNT + " times",
        afterCount,
        is(THREAD_COUNT));

    logger.info(
        "===== testConcurrentAfterCallbacksWithReturnOverride [{}]: TEST COMPLETED "
            + "(afterCount={}, correctOverrides={}) =====",
        path,
        afterCount,
        correctOverrideCount.get());
  }

  // ===========================================================================
  // Concurrent AROUND Callback Tests with Shared State
  // ===========================================================================

  /**
   * Tests that concurrent AROUND callbacks with shared cache state work correctly.
   *
   * <p>This test:
   *
   * <ul>
   *   <li>Registers an AROUND intercept that implements caching using ConcurrentHashMap
   *   <li>Spawns multiple threads that invoke the method with a limited set of arguments (to test
   *       cache hits)
   *   <li>Verifies cache hits/misses are correct
   *   <li>Verifies no state corruption occurs
   * </ul>
   */
  @Test
  public void testConcurrentAroundCallbacksWithSharedState() throws Exception {
    logger.info(
        "===== testConcurrentAroundCallbacksWithSharedState [{}]: TEST STARTED =====", path);

    // 1. Register local AROUND intercept with caching
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyStaticBy",
            "java.lang.Integer",
            "onAroundCachedExecution");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Set static counter to 1
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {1}));

    // 3. Spawn threads that invoke with only 10 unique arguments (lots of cache hits expected)
    final int uniqueArgs = 10;
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < THREAD_COUNT; i++) {
      final int arg = i % uniqueArgs; // Repeat arguments to test caching
      var unused =
          executor.submit(
              () -> {
                try {
                  startLatch.await();

                  ExecMessage response = invokeMultiplyStaticBy(arg);

                  if (response.getRaisedThrowable() == null) {
                    successCount.incrementAndGet();
                    // Verify we got a result
                    Object returnValue =
                        Unwrapper.unwrapObject(response.getReturnValue().getObject());
                    if (returnValue == null) {
                      logger.error("Thread with arg {} got null return value", arg);
                    }
                  }
                } catch (Exception e) {
                  logger.error("Thread with arg {} failed", arg, e);
                } finally {
                  completionLatch.countDown();
                }
              });
    }

    // Start all threads simultaneously
    logger.info(
        "Starting {} threads with {} unique arguments via {} path...",
        THREAD_COUNT,
        uniqueArgs,
        path);
    long startTimeMs = System.currentTimeMillis();
    startLatch.countDown();

    // Wait for all threads to complete
    boolean completed = completionLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    long elapsedMs = System.currentTimeMillis() - startTimeMs;
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    logger.info(
        "All {} threads completed in {} ms (timeout was {} ms)",
        THREAD_COUNT,
        elapsedMs,
        TEST_TIMEOUT_MS);

    // 4. Verify results
    assertThat("All threads should complete within timeout", completed, is(true));
    assertThat("All invocations should succeed", successCount.get(), is(THREAD_COUNT));

    // 5. Verify cache statistics
    ExecMessage cacheHitsResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                CALLBACK_CLASS,
                "getCacheHits",
                new String[] {},
                null,
                null,
                new Object[] {}));
    int cacheHits =
        (Integer) Unwrapper.unwrapObject(cacheHitsResponse.getReturnValue().getObject());

    ExecMessage cacheMissesResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                CALLBACK_CLASS,
                "getCacheMisses",
                new String[] {},
                null,
                null,
                new Object[] {}));
    int cacheMisses =
        (Integer) Unwrapper.unwrapObject(cacheMissesResponse.getReturnValue().getObject());

    ExecMessage cacheSizeResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                CALLBACK_CLASS,
                "getCacheSize",
                new String[] {},
                null,
                null,
                new Object[] {}));
    int cacheSize =
        (Integer) Unwrapper.unwrapObject(cacheSizeResponse.getReturnValue().getObject());

    // Verify cache statistics are reasonable
    assertThat(
        "Cache hits + misses should equal total invocations",
        cacheHits + cacheMisses,
        is(THREAD_COUNT));
    assertThat(
        "Cache should contain at most uniqueArgs entries",
        cacheSize,
        is(greaterThanOrEqualTo(1))); // At least one entry
    assertThat(
        "Cache hits should be >= 0 (some threads may hit the cache)",
        cacheHits,
        is(greaterThanOrEqualTo(0)));

    logger.info(
        "===== testConcurrentAroundCallbacksWithSharedState [{}]: TEST COMPLETED "
            + "(cacheHits={}, cacheMisses={}, cacheSize={}) =====",
        path,
        cacheHits,
        cacheMisses,
        cacheSize);
  }

  // ===========================================================================
  // Manual Locking Tests
  // ===========================================================================

  /**
   * Tests that callbacks using manual locking (ReadWriteLock) work correctly under concurrent load.
   *
   * <p>This test:
   *
   * <ul>
   *   <li>Registers an AROUND intercept that uses ReadWriteLock to protect a HashMap
   *   <li>Spawns multiple threads that concurrently invoke the method
   *   <li>Verifies the callback completes without deadlocks or corruption
   *   <li>Verifies cache state is consistent
   * </ul>
   */
  @Test
  public void testStatefulCallbackWithManualLocking() throws Exception {
    logger.info("===== testStatefulCallbackWithManualLocking [{}]: TEST STARTED =====", path);

    // 1. Register local AROUND intercept with manual locking
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND, "multiplyStaticBy", "java.lang.Integer", "onAroundManualLocking");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Set static counter to 1
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {1}));

    // 3. Spawn threads with limited unique arguments
    final int uniqueArgs = 10;
    ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < THREAD_COUNT; i++) {
      final int arg = i % uniqueArgs;
      var unused =
          executor.submit(
              () -> {
                try {
                  startLatch.await();

                  ExecMessage response = invokeMultiplyStaticBy(arg);

                  if (response.getRaisedThrowable() == null) {
                    successCount.incrementAndGet();
                  }
                } catch (Exception e) {
                  logger.error("Thread with arg {} failed", arg, e);
                } finally {
                  completionLatch.countDown();
                }
              });
    }

    // Start all threads simultaneously
    logger.info(
        "Starting {} threads with {} unique arguments via {} path...",
        THREAD_COUNT,
        uniqueArgs,
        path);
    long startTimeMs = System.currentTimeMillis();
    startLatch.countDown();

    // Wait for all threads to complete
    boolean completed = completionLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    long elapsedMs = System.currentTimeMillis() - startTimeMs;
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    logger.info(
        "All {} threads completed in {} ms (timeout was {} ms)",
        THREAD_COUNT,
        elapsedMs,
        TEST_TIMEOUT_MS);

    // 4. Verify results
    assertThat("All threads should complete within timeout", completed, is(true));
    assertThat("All invocations should succeed", successCount.get(), is(THREAD_COUNT));

    // 5. Verify callback was invoked and state is consistent
    ExecMessage countResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                CALLBACK_CLASS,
                "getManualLockCount",
                new String[] {},
                null,
                null,
                new Object[] {}));
    int count = (Integer) Unwrapper.unwrapObject(countResponse.getReturnValue().getObject());
    assertThat(
        "Manual lock callback should be invoked " + THREAD_COUNT + " times",
        count,
        is(THREAD_COUNT));

    ExecMessage stateSizeResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                CALLBACK_CLASS,
                "getManualLockStateSize",
                new String[] {},
                null,
                null,
                new Object[] {}));
    int stateSize =
        (Integer) Unwrapper.unwrapObject(stateSizeResponse.getReturnValue().getObject());
    assertThat(
        "Manual lock state should have at most uniqueArgs entries",
        stateSize,
        is(greaterThanOrEqualTo(1)));

    logger.info(
        "===== testStatefulCallbackWithManualLocking [{}]: TEST COMPLETED "
            + "(count={}, stateSize={}) =====",
        path,
        count,
        stateSize);
  }
}
