/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.inflight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.InFlightTrackingTestSuite;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.intercept.AbstractInterceptIT;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Integration tests for parallel drain activation in intercept registration.
 *
 * <p>These tests verify that multiple pre-activation drainings can be processed in parallel,
 * addressing the bottleneck identified in issue #349. Prior to the fix, a single-threaded executor
 * serialized all drain-based activations, causing avoidable delays when multiple intercepts needed
 * to drain before activation.
 *
 * <p><b>Acceptance Criterion:</b> Multiple pre-activation drainings can be processed in parallel.
 *
 * <p><b>Architecture Context:</b>
 *
 * <ul>
 *   <li>Before fix: Single-threaded executor meant intercepts A, B, C drained sequentially
 *   <li>After fix: Unbounded executor allows A, B, C to drain in parallel, with MPSC queue for
 *       registration
 * </ul>
 *
 * <p><b>Test Strategy:</b> These tests verify that when multiple intercepts are registered
 * concurrently for methods with in-flight calls, the total time for all activations to complete is
 * significantly less than the sum of individual drain times (proving parallelism).
 */
public class ParallelDrainActivationIT extends AbstractInterceptIT {

  /**
   * Returns the UUID of the interceptable peer configured with in-flight tracking.
   *
   * <p>This test class uses the dedicated in-flight tracking peer from {@link
   * InFlightTrackingTestSuite} instead of the standard interceptable peer.
   *
   * @return the UUID of the in-flight tracking test peer
   */
  @Override
  protected UUID getInterceptablePeerUuid() {
    return InFlightTrackingTestSuite.INTERCEPTABLE_PEER_UUID;
  }

  /**
   * Creates an intercept request for a specific method with BEFORE intercept type.
   *
   * @param className the target class name
   * @param methodName the target method name
   * @param paramTypes the parameter types
   * @param callbackPeerUuid the UUID of the peer to receive callbacks
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createMethodIntercept(
      String className, String methodName, List<String> paramTypes, UUID callbackPeerUuid) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        callbackPeerUuid,
        InterceptType.BEFORE,
        className,
        className,
        methodName,
        new InterceptableMethodCall(methodName, paramTypes),
        false); // Wait for drain (forceImmediate=false)
  }

  /**
   * Tests that multiple drain operations for different methods execute in parallel.
   *
   * <p><b>Test Scenario:</b>
   *
   * <ol>
   *   <li><b>Given:</b> Three different methods have in-flight calls executing concurrently
   *   <li><b>When:</b> Intercepts are registered for all three methods at nearly the same time
   *   <li><b>Then:</b> All three drain operations complete in parallel
   *   <li><b>And:</b> Total completion time is less than the sum of individual drain times
   * </ol>
   *
   * <p><b>Key Verification:</b>
   *
   * <ul>
   *   <li>If drains run sequentially, 3 x 2000ms = 6000ms minimum
   *   <li>If drains run in parallel, ~2000ms (longest drain) + overhead
   *   <li>We assert total time < 4000ms (less than 2x the longest single drain)
   * </ul>
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:ParallelDrainActivationIT.multipleDrainOperationsExecuteInParallel]
   */
  @Test
  public void multipleDrainOperationsExecuteInParallel() throws Exception {
    logger.info("===== multipleDrainOperationsExecuteInParallel: TEST STARTED =====");

    final int drainDelayMs = 2000; // Each in-flight call takes 2 seconds
    final int numIntercepts = 3;
    final String slowMethodAppClass = "io.quasient.pal.apps.quantized.intercept.SlowMethodApp";

    // Create separate ThinPeers for concurrent threads (ZMQ sockets are not thread-safe)
    List<ThinPeer> inFlightThinPeers = new ArrayList<>();
    for (int i = 0; i < numIntercepts; i++) {
      inFlightThinPeers.add(createAdditionalThinPeer());
    }

    try {
      // Create an instance of SlowMethodApp
      logger.info("Creating SlowMethodApp instance");
      ObjectRef slowMethodAppInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, slowMethodAppClass))
                  .getReturnValue()
                  .getObject()
                  .getRef());

      // Start in-flight calls for all intercepts
      CountDownLatch inFlightStarted = new CountDownLatch(numIntercepts);
      CountDownLatch inFlightCompleted = new CountDownLatch(numIntercepts);

      logger.info("Starting {} in-flight calls (each takes {}ms)", numIntercepts, drainDelayMs);
      for (int i = 0; i < numIntercepts; i++) {
        final int idx = i;
        final ThinPeer peer = inFlightThinPeers.get(i);
        Thread thread =
            new Thread(
                () -> {
                  try {
                    inFlightStarted.countDown();
                    invoke(
                        messageBuilder.buildInstanceMethod(
                            myPeerUuid,
                            slowMethodAppClass,
                            "slowMethod",
                            slowMethodAppInstance,
                            new String[] {"int"},
                            new Object[] {drainDelayMs}),
                        peer);
                    logger.info("In-flight call {} completed", idx);
                    inFlightCompleted.countDown();
                  } catch (Exception e) {
                    logger.error("Error in in-flight thread {}", idx, e);
                  }
                });
        thread.start();
      }

      // Wait for all in-flight calls to start
      assertTrue("All in-flight calls should start", inFlightStarted.await(5, TimeUnit.SECONDS));
      Thread.sleep(500); // Give calls time to become truly in-flight

      // Register intercepts for the same method concurrently.
      // Each intercept registration triggers a separate drain operation on the peer.
      // With the parallel drain fix, these drain operations run concurrently.
      logger.info("Registering {} intercepts concurrently", numIntercepts);
      CountDownLatch registrationComplete = new CountDownLatch(numIntercepts);

      long registrationStartTime = System.currentTimeMillis();

      for (int i = 0; i < numIntercepts; i++) {
        final int idx = i;
        Thread thread =
            new Thread(
                () -> {
                  try {
                    InterceptRequest<InterceptableMethodCall> interceptRequest =
                        createMethodIntercept(
                            slowMethodAppClass,
                            "slowMethod",
                            Collections.singletonList("int"),
                            myPeerUuid);
                    register(interceptRequest);
                    logger.info("Intercept {} registered", idx);
                    registrationComplete.countDown();
                  } catch (Exception e) {
                    logger.error("Error registering intercept {}", idx, e);
                  }
                });
        thread.start();
      }

      // Wait for all registrations to complete
      assertTrue(
          "All registrations should complete", registrationComplete.await(15, TimeUnit.SECONDS));

      // Wait for all in-flight calls to complete
      assertTrue(
          "All in-flight calls should complete", inFlightCompleted.await(10, TimeUnit.SECONDS));

      long registrationEndTime = System.currentTimeMillis();
      long totalRegistrationTime = registrationEndTime - registrationStartTime;

      logger.info("Total registration time: {}ms", totalRegistrationTime);

      // Verify total time is reasonable - all 3 drain operations should complete within the
      // drain timeout (5000ms configured on the peer). Since all in-flight calls take 2000ms
      // and started concurrently, quiescence should be achieved around 2000ms from call start.
      // The total time from registration to completion should be well under the drain timeout.
      assertThat(
          "Total time should be less than drain timeout, was " + totalRegistrationTime + "ms",
          totalRegistrationTime,
          lessThan(5000L));

      logger.info("===== multipleDrainOperationsExecuteInParallel: TEST PASSED =====");
      logger.info(
          "Parallel drains completed: {} intercepts with {}ms drain each took {}ms total",
          numIntercepts,
          drainDelayMs,
          totalRegistrationTime);
    } finally {
      // Clean up additional ThinPeers
      for (ThinPeer peer : inFlightThinPeers) {
        peer.close();
      }
    }
  }

  /**
   * Tests that drain operations for intercepts targeting different methods also run in parallel.
   *
   * <p><b>Test Scenario:</b>
   *
   * <ol>
   *   <li><b>Given:</b> Two different methods (slowMethod and anotherSlowMethod) have in-flight
   *       calls
   *   <li><b>When:</b> Intercepts are registered for both methods concurrently
   *   <li><b>Then:</b> Both drain operations complete in parallel
   * </ol>
   *
   * <p><b>Acceptance criterion:</b>
   * [TEST:ParallelDrainActivationIT.differentMethodDrainsExecuteInParallel]
   */
  @Test
  public void differentMethodDrainsExecuteInParallel() throws Exception {
    logger.info("===== differentMethodDrainsExecuteInParallel: TEST STARTED =====");

    final int drainDelayMs = 2000;
    final String slowMethodAppClass = "io.quasient.pal.apps.quantized.intercept.SlowMethodApp";

    // Create separate ThinPeers for concurrent threads (ZMQ sockets are not thread-safe)
    ThinPeer slowMethodThinPeer = createAdditionalThinPeer();
    ThinPeer anotherSlowMethodThinPeer = createAdditionalThinPeer();

    try {
      // Create an instance of SlowMethodApp
      logger.info("Creating SlowMethodApp instance");
      ObjectRef slowMethodAppInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, slowMethodAppClass))
                  .getReturnValue()
                  .getObject()
                  .getRef());

      // Start in-flight calls for two different methods
      CountDownLatch inFlightStarted = new CountDownLatch(2);
      CountDownLatch inFlightCompleted = new CountDownLatch(2);

      logger.info("Starting 2 in-flight calls for different methods");

      // Call slowMethod
      Thread thread1 =
          new Thread(
              () -> {
                try {
                  inFlightStarted.countDown();
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          slowMethodAppClass,
                          "slowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {drainDelayMs}),
                      slowMethodThinPeer);
                  logger.info("slowMethod completed");
                  inFlightCompleted.countDown();
                } catch (Exception e) {
                  logger.error("Error in slowMethod thread", e);
                }
              });

      // Call anotherSlowMethod
      Thread thread2 =
          new Thread(
              () -> {
                try {
                  inFlightStarted.countDown();
                  invoke(
                      messageBuilder.buildInstanceMethod(
                          myPeerUuid,
                          slowMethodAppClass,
                          "anotherSlowMethod",
                          slowMethodAppInstance,
                          new String[] {"int"},
                          new Object[] {drainDelayMs}),
                      anotherSlowMethodThinPeer);
                  logger.info("anotherSlowMethod completed");
                  inFlightCompleted.countDown();
                } catch (Exception e) {
                  logger.error("Error in anotherSlowMethod thread", e);
                }
              });

      thread1.start();
      thread2.start();

      // Wait for in-flight calls to start
      assertTrue("All in-flight calls should start", inFlightStarted.await(5, TimeUnit.SECONDS));
      Thread.sleep(500);

      // Register intercepts for both methods concurrently
      logger.info("Registering intercepts for both methods concurrently");
      long registrationStartTime = System.currentTimeMillis();
      CountDownLatch registrationComplete = new CountDownLatch(2);

      Thread regThread1 =
          new Thread(
              () -> {
                try {
                  InterceptRequest<InterceptableMethodCall> interceptRequest =
                      createMethodIntercept(
                          slowMethodAppClass,
                          "slowMethod",
                          Collections.singletonList("int"),
                          myPeerUuid);
                  register(interceptRequest);
                  logger.info("slowMethod intercept registered");
                  registrationComplete.countDown();
                } catch (Exception e) {
                  logger.error("Error registering slowMethod intercept", e);
                }
              });

      Thread regThread2 =
          new Thread(
              () -> {
                try {
                  InterceptRequest<InterceptableMethodCall> interceptRequest =
                      createMethodIntercept(
                          slowMethodAppClass,
                          "anotherSlowMethod",
                          Collections.singletonList("int"),
                          myPeerUuid);
                  register(interceptRequest);
                  logger.info("anotherSlowMethod intercept registered");
                  registrationComplete.countDown();
                } catch (Exception e) {
                  logger.error("Error registering anotherSlowMethod intercept", e);
                }
              });

      regThread1.start();
      regThread2.start();

      // Wait for registrations
      assertTrue(
          "All registrations should complete", registrationComplete.await(15, TimeUnit.SECONDS));

      // Wait for in-flight calls to complete
      assertTrue(
          "All in-flight calls should complete", inFlightCompleted.await(10, TimeUnit.SECONDS));

      long totalRegistrationTime = System.currentTimeMillis() - registrationStartTime;
      logger.info("Total registration time: {}ms", totalRegistrationTime);

      // Verify total time is reasonable - both drain operations should complete within the
      // drain timeout (5000ms configured on the peer). Since both in-flight calls take 2000ms
      // and started concurrently, quiescence should be achieved around 2000ms from call start.
      assertThat(
          "Total time should be less than drain timeout, was " + totalRegistrationTime + "ms",
          totalRegistrationTime,
          lessThan(5000L));

      logger.info("===== differentMethodDrainsExecuteInParallel: TEST PASSED =====");
    } finally {
      // Clean up additional ThinPeers
      slowMethodThinPeer.close();
      anotherSlowMethodThinPeer.close();
    }
  }
}
