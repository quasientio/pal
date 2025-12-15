/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.local.constructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.apps.quantized.intercept.callback.LocalInterceptCallbacks;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for asynchronous local constructor intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify fire-and-forget async local intercepts on constructors. Async callbacks are
 * submitted to an executor and do not block the constructor execution.
 *
 * <p>Key characteristics of async local intercepts:
 *
 * <ul>
 *   <li>Callback runs asynchronously in a separate thread
 *   <li>Constructor does not wait for callback completion
 *   <li>Cannot mutate arguments or return values (fire-and-forget)
 *   <li>Used for logging, metrics, audit trails
 * </ul>
 */
@RunWith(Parameterized.class)
public class LocalConstructorAsyncCallbackIT extends AbstractInterceptIT {

  private static final String CALLBACK_CLASS =
      "com.quasient.pal.apps.quantized.intercept.callback.LocalInterceptCallbacks";
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** Constructor invocation descriptor for parameterized tests. */
  private static final ConstructorInvocation WITH_COUNTER =
      new ConstructorInvocation("createWithCounter", List.of("java.lang.Integer"));

  /** Timeout for waiting for async callbacks (milliseconds). */
  private static final long ASYNC_CALLBACK_TIMEOUT_MS = 2000;

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public LocalConstructorAsyncCallbackIT(InvocationPath path) {
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

  /** Resets callback state before each test. */
  @Before
  public void resetCallbacks() {
    LocalInterceptCallbacks.reset();
  }

  /**
   * Creates a local intercept request for a constructor where callback peer = interceptable peer.
   *
   * @param type the intercept type
   * @param paramTypes the constructor parameter types
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalConstructorIntercept(
      InterceptType type, String paramTypes, String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID, // callback peer = interceptable peer
        type,
        TARGET_CLASS,
        CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("new", Collections.singletonList(paramTypes)));
  }

  /**
   * Tests that a local BEFORE_ASYNC callback is invoked for a constructor.
   *
   * <p>Registers a local BEFORE_ASYNC intercept, invokes the constructor, and verifies the async
   * callback was eventually invoked using a latch.
   */
  @Test
  public void testLocalBeforeAsyncConstructorCallback() throws Exception {
    logger.info("===== testLocalBeforeAsyncConstructorCallback [{}]: TEST STARTED =====", path);

    // 1. Set up latch for async callback
    LocalInterceptCallbacks.setAsyncLatch(1);

    // 2. Register a local BEFORE_ASYNC intercept on parameterized constructor
    logger.info("Creating local BEFORE_ASYNC intercept request for constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.BEFORE_ASYNC, "java.lang.Integer", "onBeforeAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Invoke constructor
    logger.info("Invoking constructor via {} path", path);
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});

    // 4. Verify invocation succeeded
    assertThat(
        "Constructor should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Wait for async callback to complete
    boolean callbackInvoked =
        LocalInterceptCallbacks.awaitAsyncCallbacks(ASYNC_CALLBACK_TIMEOUT_MS);
    assertTrue("Async BEFORE callback should complete within timeout", callbackInvoked);

    // 6. Verify callback was invoked
    assertThat(
        "Local BEFORE_ASYNC callback should have been invoked",
        LocalInterceptCallbacks.getBeforeAsyncCallCount(),
        is(greaterThan(0)));

    logger.info(
        "Local BEFORE_ASYNC callback count: {}", LocalInterceptCallbacks.getBeforeAsyncCallCount());
    logger.info("===== testLocalBeforeAsyncConstructorCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER_ASYNC callback is invoked for a constructor.
   *
   * <p>Registers a local AFTER_ASYNC intercept, invokes the constructor, and verifies the async
   * callback was eventually invoked using a latch.
   */
  @Test
  public void testLocalAfterAsyncConstructorCallback() throws Exception {
    logger.info("===== testLocalAfterAsyncConstructorCallback [{}]: TEST STARTED =====", path);

    // 1. Set up latch for async callback
    LocalInterceptCallbacks.setAsyncLatch(1);

    // 2. Register a local AFTER_ASYNC intercept on parameterized constructor
    logger.info("Creating local AFTER_ASYNC intercept request for constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AFTER_ASYNC, "java.lang.Integer", "onAfterAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Invoke constructor
    logger.info("Invoking constructor via {} path", path);
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});

    // 4. Verify invocation succeeded
    assertThat(
        "Constructor should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Wait for async callback to complete
    boolean callbackInvoked =
        LocalInterceptCallbacks.awaitAsyncCallbacks(ASYNC_CALLBACK_TIMEOUT_MS);
    assertTrue("Async AFTER callback should complete within timeout", callbackInvoked);

    // 6. Verify callback was invoked
    assertThat(
        "Local AFTER_ASYNC callback should have been invoked",
        LocalInterceptCallbacks.getAfterAsyncCallCount(),
        is(greaterThan(0)));

    logger.info(
        "Local AFTER_ASYNC callback count: {}", LocalInterceptCallbacks.getAfterAsyncCallCount());
    logger.info("===== testLocalAfterAsyncConstructorCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that both local BEFORE_ASYNC and AFTER_ASYNC callbacks are invoked for a constructor.
   *
   * <p>Registers both async intercepts and verifies both are called.
   */
  @Test
  public void testLocalBeforeAndAfterAsyncConstructorCallbacks() throws Exception {
    logger.info(
        "===== testLocalBeforeAndAfterAsyncConstructorCallbacks [{}]: TEST STARTED =====", path);

    // 1. Set up latch for 2 async callbacks
    LocalInterceptCallbacks.setAsyncLatch(2);

    // 2. Register both BEFORE_ASYNC and AFTER_ASYNC intercepts
    InterceptRequest<InterceptableMethodCall> beforeIntercept =
        createLocalConstructorIntercept(
            InterceptType.BEFORE_ASYNC, "java.lang.Integer", "onBeforeAsync");
    InterceptRequest<InterceptableMethodCall> afterIntercept =
        createLocalConstructorIntercept(
            InterceptType.AFTER_ASYNC, "java.lang.Integer", "onAfterAsync");

    register(beforeIntercept);
    register(afterIntercept);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Invoke constructor
    logger.info("Invoking constructor via {} path", path);
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});

    // 4. Verify invocation succeeded
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 5. Wait for async callbacks to complete
    boolean callbacksInvoked =
        LocalInterceptCallbacks.awaitAsyncCallbacks(ASYNC_CALLBACK_TIMEOUT_MS);
    assertTrue("Both async callbacks should complete within timeout", callbacksInvoked);

    // 6. Verify both callbacks were invoked
    assertThat(
        "Local BEFORE_ASYNC callback should have been invoked",
        LocalInterceptCallbacks.getBeforeAsyncCallCount(),
        is(greaterThan(0)));
    assertThat(
        "Local AFTER_ASYNC callback should have been invoked",
        LocalInterceptCallbacks.getAfterAsyncCallCount(),
        is(greaterThan(0)));

    logger.info(
        "===== testLocalBeforeAndAfterAsyncConstructorCallbacks [{}]: TEST COMPLETED =====", path);
  }
}
