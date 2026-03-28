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
package io.quasient.pal.core.intercept;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Represents an intercept activation that has completed the drain phase and is ready for
 * registration.
 *
 * <p>This class is used to pass completed activations from the {@link
 * InterceptActivationCoordinator}'s background threads to the {@link InterceptMatcher} thread for
 * final registration. This decoupling allows multiple drain operations to run in parallel while
 * maintaining single-writer semantics for the intercept registry.
 *
 * <p><b>Registration synchronization:</b> The drain thread must wait for the intercept to be
 * registered before stopping fencing, so that threads unblocked by unfencing will see the newly
 * registered intercept. The {@link #awaitRegistration(long)} and {@link #signalRegistered()}
 * methods coordinate this: the drain thread calls {@code awaitRegistration()} after enqueuing, and
 * the InterceptMatcher calls {@code signalRegistered()} after registration.
 *
 * <p>The flow is:
 *
 * <ol>
 *   <li>{@link InterceptActivationCoordinator} receives activation request
 *   <li>Coordinator starts fencing and submits async drain task to executor pool
 *   <li>Async task waits for quiescence (in parallel with other drain tasks)
 *   <li>On quiescence, task creates this object and offers it to MPSC queue
 *   <li>Task calls {@link #awaitRegistration(long)} to wait for registration
 *   <li>{@link InterceptMatcher} polls the queue, registers the intercept, calls {@link
 *       #signalRegistered()}
 *   <li>Drain task's {@code awaitRegistration()} returns and task stops fencing
 * </ol>
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification =
        "Mutable InterceptMessage reference is intentional for efficiency; "
            + "latch is an internal coordination mechanism")
public class PendingInterceptActivation {

  /** The intercept message to register, already validated and with fencing completed. */
  private final InterceptMessage interceptMessage;

  /** The class pattern extracted from the message (for logging/debugging). */
  private final String classPattern;

  /** The method pattern extracted from the message (for logging/debugging). */
  private final String methodPattern;

  /**
   * The parameter types extracted from the message, used for stopping fencing after registration.
   *
   * <p>{@code null} for field intercepts, {@code new String[0]} for no-arg methods/constructors, or
   * a populated array for methods/constructors with parameters.
   */
  private final String[] parameterTypes;

  /** Latch used to synchronize the drain thread with the registration in InterceptMatcher. */
  private final CountDownLatch registrationLatch;

  /**
   * Constructs a new pending intercept activation.
   *
   * @param interceptMessage the intercept message to register
   * @param classPattern the class pattern for logging
   * @param methodPattern the method pattern for logging
   * @param parameterTypes the parameter types for fencing (null for field intercepts)
   */
  public PendingInterceptActivation(
      InterceptMessage interceptMessage,
      String classPattern,
      String methodPattern,
      String[] parameterTypes) {
    this.interceptMessage = interceptMessage;
    this.classPattern = classPattern;
    this.methodPattern = methodPattern;
    this.parameterTypes = parameterTypes;
    this.registrationLatch = new CountDownLatch(1);
  }

  /**
   * Returns the intercept message to register.
   *
   * @return the intercept message
   */
  public InterceptMessage interceptMessage() {
    return interceptMessage;
  }

  /**
   * Returns the class pattern.
   *
   * @return the class pattern
   */
  public String classPattern() {
    return classPattern;
  }

  /**
   * Returns the method pattern.
   *
   * @return the method pattern
   */
  public String methodPattern() {
    return methodPattern;
  }

  /**
   * Returns the parameter types used for fencing.
   *
   * @return the parameter types, or {@code null} for field intercepts
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Array is intentionally shared for pass-through to stopFencing")
  public String[] parameterTypes() {
    return parameterTypes;
  }

  /**
   * Signals that the intercept has been registered by InterceptMatcher.
   *
   * <p>Called by InterceptMatcher after successfully registering the intercept (or after
   * determining it cannot be registered, e.g., due to duplicate). This unblocks the drain thread
   * waiting in {@link #awaitRegistration(long)}.
   */
  public void signalRegistered() {
    registrationLatch.countDown();
  }

  /**
   * Waits for the intercept to be registered by InterceptMatcher.
   *
   * <p>Called by the drain thread after enqueuing to the MPSC queue. Blocks until {@link
   * #signalRegistered()} is called or the timeout expires.
   *
   * @param timeoutMs the maximum time to wait in milliseconds
   * @return {@code true} if registration completed, {@code false} if timeout expired
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public boolean awaitRegistration(long timeoutMs) throws InterruptedException {
    return registrationLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
  }
}
