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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.apps.quantized.intercept.callback.LocalInterceptCallbacks;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for synchronous local method intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify local intercepts where the callback runs in the same peer as the
 * intercepted method. Local intercepts use {@code LocalInterceptCallbackDispatcher} instead of
 * sending RPC messages to a remote peer.
 *
 * <p>Key differences from remote intercepts:
 *
 * <ul>
 *   <li>Callback peer UUID equals interceptable peer UUID
 *   <li>Callback is invoked directly via reflection, no ZMQ message passing
 *   <li>Callback has access to live Java objects (not serialized copies)
 *   <li>Argument mutations are immediately visible to the intercepted method
 * </ul>
 */
@RunWith(Parameterized.class)
public class LocalMethodSyncCallbackIT extends AbstractInterceptIT {

  private static final String CALLBACK_CLASS =
      "com.quasient.pal.apps.quantized.intercept.callback.LocalInterceptCallbacks";
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public LocalMethodSyncCallbackIT(InvocationPath path) {
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
    // For local intercepts, callback peer UUID = interceptable peer UUID
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID, // callback peer = interceptable peer
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
   * Tests that a local BEFORE callback is invoked.
   *
   * <p>Registers a local BEFORE intercept on multiplyBy, invokes it, and verifies the callback was
   * invoked by checking the counter in LocalInterceptCallbacks.
   */
  @Test
  public void testLocalBeforeCallback() throws Exception {
    logger.info("===== testLocalBeforeCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local BEFORE intercept on multiplyBy method
    logger.info("Creating local BEFORE intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "onBefore");

    logger.info("Registering intercept request with callback peer = interceptable peer");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    logger.info("Creating InterceptableApp instance");
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

    // 5. Verify local BEFORE callback was invoked
    // Allow brief delay for callback execution
    Thread.sleep(50);
    assertThat(
        "Local BEFORE callback should have been invoked",
        LocalInterceptCallbacks.getBeforeCallCount(),
        is(greaterThan(0)));

    logger.info("Local BEFORE callback count: {}", LocalInterceptCallbacks.getBeforeCallCount());
    logger.info("===== testLocalBeforeCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER callback is invoked.
   *
   * <p>Registers a local AFTER intercept on multiplyBy, invokes it, and verifies the callback was
   * invoked by checking the counter in LocalInterceptCallbacks.
   */
  @Test
  public void testLocalAfterCallback() throws Exception {
    logger.info("===== testLocalAfterCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local AFTER intercept on multiplyBy method
    logger.info("Creating local AFTER intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "onAfter");

    logger.info("Registering intercept request with callback peer = interceptable peer");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    logger.info("Creating InterceptableApp instance");
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

    // 5. Verify local AFTER callback was invoked
    Thread.sleep(50);
    assertThat(
        "Local AFTER callback should have been invoked",
        LocalInterceptCallbacks.getAfterCallCount(),
        is(greaterThan(0)));

    logger.info("Local AFTER callback count: {}", LocalInterceptCallbacks.getAfterCallCount());
    logger.info("===== testLocalAfterCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that multiple local BEFORE callbacks are invoked.
   *
   * <p>Invokes multiplyBy N times and verifies the BEFORE callback is invoked N times.
   */
  @Test
  public void testMultipleLocalBeforeCallbacks() throws Exception {
    logger.info("===== testMultipleLocalBeforeCallbacks [{}]: TEST STARTED =====", path);

    final int n = 3;

    // 1. Register a local BEFORE intercept on multiplyBy method
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "onBefore");
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

    // 4. Verify local BEFORE callback was invoked n times
    Thread.sleep(50);
    assertThat(
        "Local BEFORE callback should have been invoked " + n + " times",
        LocalInterceptCallbacks.getBeforeCallCount(),
        is(n));

    logger.info("===== testMultipleLocalBeforeCallbacks [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that both local BEFORE and AFTER callbacks are invoked.
   *
   * <p>Registers both BEFORE and AFTER intercepts and verifies both are called.
   */
  @Test
  public void testLocalBeforeAndAfterCallbacks() throws Exception {
    logger.info("===== testLocalBeforeAndAfterCallbacks [{}]: TEST STARTED =====", path);

    // 1. Register both BEFORE and AFTER intercepts
    InterceptRequest<InterceptableMethodCall> beforeIntercept =
        createLocalMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "onBefore");
    InterceptRequest<InterceptableMethodCall> afterIntercept =
        createLocalMethodIntercept(
            InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "onAfter");

    register(beforeIntercept);
    register(afterIntercept);
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

    // 4. Verify both callbacks were invoked
    Thread.sleep(50);
    assertThat(
        "Local BEFORE callback should have been invoked",
        LocalInterceptCallbacks.getBeforeCallCount(),
        is(greaterThan(0)));
    assertThat(
        "Local AFTER callback should have been invoked",
        LocalInterceptCallbacks.getAfterCallCount(),
        is(greaterThan(0)));

    logger.info("===== testLocalBeforeAndAfterCallbacks [{}]: TEST COMPLETED =====", path);
  }
}
