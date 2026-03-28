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
package io.quasient.foobar.apps.quantized.intercept;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test application for integration testing in-flight tracking and intercept activation scenarios.
 *
 * <p>This class provides methods that simulate slow or blocking operations, allowing integration
 * tests to verify that the in-flight tracking system correctly handles:
 *
 * <ul>
 *   <li>Methods that are currently executing when an intercept is registered
 *   <li>Quiescence detection (waiting for in-flight calls to complete)
 *   <li>Fence coordination (blocking new calls while waiting for quiescence)
 *   <li>Timeout handling for long-running methods
 * </ul>
 *
 * <p>All methods in this class are woven with AspectJ (part of the quantized package) and can be
 * intercepted via PAL's interception system.
 *
 * <p><strong>Usage in Integration Tests:</strong>
 *
 * <ol>
 *   <li>Start a peer with {@code --in-flight-tracking} enabled
 *   <li>Invoke one of the slow/blocking methods via RPC
 *   <li>Register an intercept while the method is executing
 *   <li>Verify that the intercept activation waits for quiescence or respects the immediate
 *       activation flag
 * </ol>
 *
 * @see io.quasient.pal.core.intercept.InFlightDispatchTracker
 */
public class SlowMethodApp {

  /**
   * Sleeps for the specified duration and returns the timestamp when it completes.
   *
   * <p>This method simulates a slow operation with a predictable execution time. Tests can use this
   * to verify that in-flight tracking correctly detects methods that are executing when an
   * intercept is registered.
   *
   * <p><strong>Example test scenario:</strong>
   *
   * <pre>
   * 1. Call slowMethod(5000) via RPC (will sleep for 5 seconds)
   * 2. After 1 second, register an intercept for slowMethod
   * 3. Verify that intercept activation waits until the sleep completes
   * 4. Verify that the next call to slowMethod IS intercepted
   * </pre>
   *
   * @param delayMs duration to sleep in milliseconds
   * @return the system timestamp (milliseconds since epoch) when the method returns
   * @throws InterruptedException if the thread is interrupted while sleeping
   */
  public long slowMethod(int delayMs) throws InterruptedException {
    Thread.sleep(delayMs);
    return System.currentTimeMillis();
  }

  /**
   * Another slow method for testing parallel drain activation.
   *
   * <p>This method is identical to {@link #slowMethod(int)} but has a different name. It's used in
   * integration tests to verify that drain operations for different methods can execute in
   * parallel.
   *
   * @param delayMs duration to sleep in milliseconds
   * @return the system timestamp (milliseconds since epoch) when the method returns
   * @throws InterruptedException if the thread is interrupted while sleeping
   * @see #slowMethod(int)
   */
  public long anotherSlowMethod(int delayMs) throws InterruptedException {
    Thread.sleep(delayMs);
    return System.currentTimeMillis();
  }

  /**
   * Blocks until the provided latch is counted down to zero.
   *
   * <p>This method simulates a blocking operation with indeterminate duration (controlled
   * externally by the test). Tests can use this to verify timeout handling and immediate activation
   * overrides.
   *
   * <p><strong>Example test scenario:</strong>
   *
   * <pre>
   * 1. Create a CountDownLatch(1)
   * 2. Call blockingMethod(latch) via RPC (will block until latch reaches 0)
   * 3. Register an intercept for blockingMethod with immediate activation flag
   * 4. Verify that intercept activates immediately without waiting for quiescence
   * 5. Count down the latch to unblock the method
   * </pre>
   *
   * @param latch the countdown latch to wait on
   * @return the system timestamp (milliseconds since epoch) when the latch reaches zero
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public long blockingMethod(CountDownLatch latch) throws InterruptedException {
    latch.await();
    return System.currentTimeMillis();
  }

  /**
   * Loops continuously until the provided flag is set to false.
   *
   * <p>This method simulates a long-running operation that can be terminated externally. Each
   * iteration sleeps for 50ms to avoid busy-waiting. Tests can use this to verify hot-patching
   * scenarios where a method must be intercepted to break out of a loop or redirect its logic.
   *
   * <p><strong>Example test scenario:</strong>
   *
   * <pre>
   * 1. Create an AtomicBoolean(true)
   * 2. Call loopMethod(flag) via RPC (will loop continuously)
   * 3. Register an AROUND intercept that returns immediately without proceeding
   * 4. Verify that the intercept activates with immediate flag (bypassing quiescence)
   * 5. Verify that the looping thread terminates due to the intercept
   * </pre>
   *
   * @param shouldContinue atomic boolean flag; method loops while this is true
   * @return the system timestamp (milliseconds since epoch) when the loop exits
   * @throws InterruptedException if the thread is interrupted while sleeping
   */
  public long loopMethod(AtomicBoolean shouldContinue) throws InterruptedException {
    while (shouldContinue.get()) {
      Thread.sleep(50); // Sleep to avoid busy-waiting
    }
    return System.currentTimeMillis();
  }

  /**
   * Wrapper method that calls {@link #slowMethod(int)} internally.
   *
   * <p>CRITICAL: Direct RPC to slowMethod bypasses call-site weaving. Tests must call this wrapper
   * instead to ensure the slow method invocation is woven and interceptable.
   *
   * @param delayMs duration to sleep in milliseconds
   * @return the timestamp returned by slowMethod
   * @throws InterruptedException if interrupted while sleeping
   */
  public long callSlowMethod(int delayMs) throws InterruptedException {
    return slowMethod(delayMs);
  }

  /**
   * Wrapper method that calls {@link #blockingMethod(CountDownLatch)} internally.
   *
   * <p>CRITICAL: Direct RPC to blockingMethod bypasses call-site weaving. Tests must call this
   * wrapper instead to ensure the blocking method invocation is woven and interceptable.
   *
   * @param latch the countdown latch to wait on
   * @return the timestamp returned by blockingMethod
   * @throws InterruptedException if interrupted while waiting
   */
  public long callBlockingMethod(CountDownLatch latch) throws InterruptedException {
    return blockingMethod(latch);
  }

  /**
   * Wrapper method that calls {@link #loopMethod(AtomicBoolean)} internally.
   *
   * <p>CRITICAL: Direct RPC to loopMethod bypasses call-site weaving. Tests must call this wrapper
   * instead to ensure the looping method invocation is woven and interceptable.
   *
   * @param shouldContinue atomic boolean flag controlling the loop
   * @return the timestamp returned by loopMethod
   * @throws InterruptedException if interrupted while sleeping
   */
  public long callLoopMethod(AtomicBoolean shouldContinue) throws InterruptedException {
    return loopMethod(shouldContinue);
  }
}
