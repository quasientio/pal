/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.local.method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.LocalInterceptTestSuite;
import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for asynchronous local method intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify local async intercepts where the callback runs in a background thread on
 * the same peer as the intercepted method. Async intercepts are fire-and-forget:
 *
 * <ul>
 *   <li>They do not block the intercepted method
 *   <li>They cannot mutate arguments or return values
 *   <li>Exceptions in callbacks are logged but swallowed
 * </ul>
 */
@RunWith(Parameterized.class)
public class LocalMethodAsyncCallbackIT extends AbstractInterceptIT {

  private static final String CALLBACK_CLASS =
      "com.quasient.pal.apps.callbacks.local.LocalInterceptCallbacks";
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public LocalMethodAsyncCallbackIT(InvocationPath path) {
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
        INTERCEPTABLE_PEER_UUID,
        type,
        TARGET_CLASS,
        CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall(methodName, Collections.singletonList(paramTypes)));
  }

  /**
   * Invokes multiplyBy once through the specified invocation path.
   *
   * @param appInstance the target object
   * @param multiplier the multiplier argument
   * @return the response ExecMessage
   */
  private ExecMessage invokeMultiplyByOnce(ObjectRef appInstance, int multiplier) {
    if (path == InvocationPath.HOT_PATH) {
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              TARGET_CLASS,
              "multiplyCounterNTimesBy",
              appInstance,
              new String[] {"java.lang.Integer", "java.lang.Integer"},
              new Object[] {1, multiplier}));
    } else {
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              TARGET_CLASS,
              "multiplyBy",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {multiplier}));
    }
  }

  /**
   * Tests that a local BEFORE_ASYNC callback is invoked asynchronously.
   *
   * <p>Registers a local BEFORE_ASYNC intercept on multiplyBy, invokes it, and verifies the
   * callback was invoked by checking the application log.
   */
  @Test
  public void testLocalBeforeAsyncCallback() throws Exception {
    logger.info("===== testLocalBeforeAsyncCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local BEFORE_ASYNC intercept on multiplyBy method
    logger.info("Creating local BEFORE_ASYNC intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE_ASYNC, "multiplyBy", "java.lang.Integer", "onBeforeAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke multiplyBy
    logger.info("Invoking multiplyBy via {} path", path);
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify local BEFORE_ASYNC callback was invoked (via log output)
    assertTrue(
        "Local BEFORE_ASYNC callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE_ASYNC:.*multiplyBy.*count=1"));

    logger.info("===== testLocalBeforeAsyncCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER_ASYNC callback is invoked asynchronously.
   *
   * <p>Registers a local AFTER_ASYNC intercept on multiplyBy, invokes it, and verifies the callback
   * was invoked by checking the application log.
   */
  @Test
  public void testLocalAfterAsyncCallback() throws Exception {
    logger.info("===== testLocalAfterAsyncCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local AFTER_ASYNC intercept on multiplyBy method
    logger.info("Creating local AFTER_ASYNC intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AFTER_ASYNC, "multiplyBy", "java.lang.Integer", "onAfterAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke multiplyBy
    logger.info("Invoking multiplyBy via {} path", path);
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify local AFTER_ASYNC callback was invoked (via log output)
    assertTrue(
        "Local AFTER_ASYNC callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER_ASYNC:.*multiplyBy.*count=1"));

    logger.info("===== testLocalAfterAsyncCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that multiple local BEFORE_ASYNC callbacks are invoked.
   *
   * <p>Invokes multiplyBy N times and verifies the BEFORE_ASYNC callback is invoked N times.
   */
  @Test
  public void testMultipleLocalBeforeAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleLocalBeforeAsyncCallbacks [{}]: TEST STARTED =====", path);

    final int n = 3;

    // 1. Register a local BEFORE_ASYNC intercept
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE_ASYNC, "multiplyBy", "java.lang.Integer", "onBeforeAsync");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke multiplyBy n times
    logger.info("Invoking multiplyBy {} times via {} path", n, path);
    if (path == InvocationPath.HOT_PATH) {
      ExecMessage response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "multiplyCounterNTimesBy",
                  appInstance,
                  new String[] {"java.lang.Integer", "java.lang.Integer"},
                  new Object[] {n, 2}));
      assertThat(response.getRaisedThrowable(), is(nullValue()));
    } else {
      for (int i = 0; i < n; i++) {
        ExecMessage response =
            invoke(
                messageBuilder.buildInstanceMethod(
                    myPeerUuid,
                    TARGET_CLASS,
                    "multiplyBy",
                    appInstance,
                    new String[] {"java.lang.Integer"},
                    new Object[] {2}));
        assertThat(response.getRaisedThrowable(), is(nullValue()));
      }
    }

    // 4. Verify local BEFORE_ASYNC callbacks were invoked n times (via log output)
    assertTrue(
        "Local BEFORE_ASYNC callback should have been invoked " + n + " times",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE_ASYNC:.*multiplyBy.*count=" + n));

    logger.info("===== testMultipleLocalBeforeAsyncCallbacks [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that both local BEFORE_ASYNC and AFTER_ASYNC callbacks are invoked.
   *
   * <p>Registers both BEFORE_ASYNC and AFTER_ASYNC intercepts and verifies both are called.
   */
  @Test
  public void testLocalBeforeAndAfterAsyncCallbacks() throws Exception {
    logger.info("===== testLocalBeforeAndAfterAsyncCallbacks [{}]: TEST STARTED =====", path);

    // 1. Register both BEFORE_ASYNC and AFTER_ASYNC intercepts
    InterceptRequest<InterceptableMethodCall> beforeAsyncIntercept =
        createLocalMethodIntercept(
            InterceptType.BEFORE_ASYNC, "multiplyBy", "java.lang.Integer", "onBeforeAsync");
    InterceptRequest<InterceptableMethodCall> afterAsyncIntercept =
        createLocalMethodIntercept(
            InterceptType.AFTER_ASYNC, "multiplyBy", "java.lang.Integer", "onAfterAsync");

    register(beforeAsyncIntercept);
    register(afterAsyncIntercept);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke multiplyBy
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify both callbacks were invoked (via log output)
    assertTrue(
        "Local BEFORE_ASYNC callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE_ASYNC:.*multiplyBy.*count=1"));
    assertTrue(
        "Local AFTER_ASYNC callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER_ASYNC:.*multiplyBy.*count=1"));

    logger.info("===== testLocalBeforeAndAfterAsyncCallbacks [{}]: TEST COMPLETED =====", path);
  }
}
