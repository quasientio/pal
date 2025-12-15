/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.local.field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.apps.quantized.intercept.callback.LocalInterceptCallbacks;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.util.Collection;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for asynchronous local field intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify fire-and-forget async local intercepts on field GET and PUT operations.
 * Async callbacks are submitted to an executor and do not block the field access.
 *
 * <p>Key characteristics of async local intercepts:
 *
 * <ul>
 *   <li>Callback runs asynchronously in a separate thread
 *   <li>Field access does not wait for callback completion
 *   <li>Cannot mutate values (fire-and-forget)
 *   <li>Used for logging, metrics, audit trails
 * </ul>
 */
@RunWith(Parameterized.class)
public class LocalFieldAsyncCallbackIT extends AbstractInterceptIT {

  private static final String CALLBACK_CLASS =
      "com.quasient.pal.apps.quantized.intercept.callback.LocalInterceptCallbacks";
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** Field invocation descriptor for instance field tests. */
  private static final FieldInvocation COUNTER =
      new FieldInvocation("getCounter", "setCounter", "counter", "java.lang.Integer", false);

  /** Timeout for waiting for async callbacks (milliseconds). */
  private static final long ASYNC_CALLBACK_TIMEOUT_MS = 2000;

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public LocalFieldAsyncCallbackIT(InvocationPath path) {
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
   * Creates a local intercept request for a field op where callback peer = interceptable peer.
   *
   * @param type the intercept type
   * @param fieldName the field name
   * @param fieldOpType the field operation type (GET or PUT)
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableFieldOp> createLocalFieldIntercept(
      InterceptType type, String fieldName, FieldOpType fieldOpType, String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID, // callback peer = interceptable peer
        type,
        TARGET_CLASS,
        CALLBACK_CLASS,
        callbackMethod,
        new InterceptableFieldOp(fieldName, fieldOpType));
  }

  // ===========================================================================
  // Field GET Async Tests
  // ===========================================================================

  /**
   * Tests that a local BEFORE_ASYNC callback is invoked for field GET.
   *
   * <p>Registers a local BEFORE_ASYNC intercept, invokes the field GET, and verifies the async
   * callback was eventually invoked using a latch.
   */
  @Test
  public void testLocalBeforeAsyncFieldGetCallback() throws Exception {
    logger.info("===== testLocalBeforeAsyncFieldGetCallback [{}]: TEST STARTED =====", path);

    // 1. Set up latch for async callback
    LocalInterceptCallbacks.setAsyncLatch(1);

    // 2. Register a local BEFORE_ASYNC intercept on counter field GET
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.BEFORE_ASYNC, "counter", FieldOpType.GET, "onBeforeAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {42}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 4. Invoke field GET
    logger.info("Invoking getCounter via {} path", path);
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);

    // 5. Verify invocation succeeded
    assertThat(
        "Field GET should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 6. Wait for async callback to complete
    boolean callbackInvoked =
        LocalInterceptCallbacks.awaitAsyncCallbacks(ASYNC_CALLBACK_TIMEOUT_MS);
    assertTrue("Async BEFORE callback should complete within timeout", callbackInvoked);

    // 7. Verify callback was invoked
    assertThat(
        "Local BEFORE_ASYNC callback should have been invoked",
        LocalInterceptCallbacks.getBeforeAsyncCallCount(),
        is(greaterThan(0)));

    logger.info("===== testLocalBeforeAsyncFieldGetCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER_ASYNC callback is invoked for field GET.
   *
   * <p>Registers a local AFTER_ASYNC intercept, invokes the field GET, and verifies the async
   * callback was eventually invoked using a latch.
   */
  @Test
  public void testLocalAfterAsyncFieldGetCallback() throws Exception {
    logger.info("===== testLocalAfterAsyncFieldGetCallback [{}]: TEST STARTED =====", path);

    // 1. Set up latch for async callback
    LocalInterceptCallbacks.setAsyncLatch(1);

    // 2. Register a local AFTER_ASYNC intercept on counter field GET
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AFTER_ASYNC, "counter", FieldOpType.GET, "onAfterAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {42}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 4. Invoke field GET
    logger.info("Invoking getCounter via {} path", path);
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);

    // 5. Verify invocation succeeded
    assertThat(
        "Field GET should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 6. Wait for async callback to complete
    boolean callbackInvoked =
        LocalInterceptCallbacks.awaitAsyncCallbacks(ASYNC_CALLBACK_TIMEOUT_MS);
    assertTrue("Async AFTER callback should complete within timeout", callbackInvoked);

    // 7. Verify callback was invoked
    assertThat(
        "Local AFTER_ASYNC callback should have been invoked",
        LocalInterceptCallbacks.getAfterAsyncCallCount(),
        is(greaterThan(0)));

    logger.info("===== testLocalAfterAsyncFieldGetCallback [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Field PUT Async Tests
  // ===========================================================================

  /**
   * Tests that a local BEFORE_ASYNC callback is invoked for field PUT.
   *
   * <p>Registers a local BEFORE_ASYNC intercept, invokes the field PUT, and verifies the async
   * callback was eventually invoked using a latch.
   */
  @Test
  public void testLocalBeforeAsyncFieldPutCallback() throws Exception {
    logger.info("===== testLocalBeforeAsyncFieldPutCallback [{}]: TEST STARTED =====", path);

    // 1. Set up latch for async callback
    LocalInterceptCallbacks.setAsyncLatch(1);

    // 2. Register a local BEFORE_ASYNC intercept on counter field PUT
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.BEFORE_ASYNC, "counter", FieldOpType.PUT, "onBeforeAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 4. Invoke field PUT
    logger.info("Invoking setCounter via {} path", path);
    ExecMessage response = invokeFieldPut(path, TARGET_CLASS, COUNTER, appInstance, 100);

    // 5. Verify invocation succeeded
    assertThat(
        "Field PUT should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 6. Wait for async callback to complete
    boolean callbackInvoked =
        LocalInterceptCallbacks.awaitAsyncCallbacks(ASYNC_CALLBACK_TIMEOUT_MS);
    assertTrue("Async BEFORE callback should complete within timeout", callbackInvoked);

    // 7. Verify callback was invoked
    assertThat(
        "Local BEFORE_ASYNC callback should have been invoked",
        LocalInterceptCallbacks.getBeforeAsyncCallCount(),
        is(greaterThan(0)));

    logger.info("===== testLocalBeforeAsyncFieldPutCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER_ASYNC callback is invoked for field PUT.
   *
   * <p>Registers a local AFTER_ASYNC intercept, invokes the field PUT, and verifies the async
   * callback was eventually invoked using a latch.
   */
  @Test
  public void testLocalAfterAsyncFieldPutCallback() throws Exception {
    logger.info("===== testLocalAfterAsyncFieldPutCallback [{}]: TEST STARTED =====", path);

    // 1. Set up latch for async callback
    LocalInterceptCallbacks.setAsyncLatch(1);

    // 2. Register a local AFTER_ASYNC intercept on counter field PUT
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AFTER_ASYNC, "counter", FieldOpType.PUT, "onAfterAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 4. Invoke field PUT
    logger.info("Invoking setCounter via {} path", path);
    ExecMessage response = invokeFieldPut(path, TARGET_CLASS, COUNTER, appInstance, 100);

    // 5. Verify invocation succeeded
    assertThat(
        "Field PUT should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 6. Wait for async callback to complete
    boolean callbackInvoked =
        LocalInterceptCallbacks.awaitAsyncCallbacks(ASYNC_CALLBACK_TIMEOUT_MS);
    assertTrue("Async AFTER callback should complete within timeout", callbackInvoked);

    // 7. Verify callback was invoked
    assertThat(
        "Local AFTER_ASYNC callback should have been invoked",
        LocalInterceptCallbacks.getAfterAsyncCallCount(),
        is(greaterThan(0)));

    logger.info("===== testLocalAfterAsyncFieldPutCallback [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Combined Async Tests
  // ===========================================================================

  /**
   * Tests that both local BEFORE_ASYNC and AFTER_ASYNC callbacks are invoked for field GET.
   *
   * <p>Registers both async intercepts and verifies both are called.
   */
  @Test
  public void testLocalBeforeAndAfterAsyncFieldGetCallbacks() throws Exception {
    logger.info(
        "===== testLocalBeforeAndAfterAsyncFieldGetCallbacks [{}]: TEST STARTED =====", path);

    // 1. Set up latch for 2 async callbacks
    LocalInterceptCallbacks.setAsyncLatch(2);

    // 2. Register both BEFORE_ASYNC and AFTER_ASYNC intercepts
    InterceptRequest<InterceptableFieldOp> beforeIntercept =
        createLocalFieldIntercept(
            InterceptType.BEFORE_ASYNC, "counter", FieldOpType.GET, "onBeforeAsync");
    InterceptRequest<InterceptableFieldOp> afterIntercept =
        createLocalFieldIntercept(
            InterceptType.AFTER_ASYNC, "counter", FieldOpType.GET, "onAfterAsync");

    register(beforeIntercept);
    register(afterIntercept);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {42}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 4. Invoke field GET
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
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
        "===== testLocalBeforeAndAfterAsyncFieldGetCallbacks [{}]: TEST COMPLETED =====", path);
  }
}
