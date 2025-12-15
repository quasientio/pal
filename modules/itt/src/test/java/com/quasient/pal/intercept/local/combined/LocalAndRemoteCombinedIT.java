/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.local.combined;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.InterceptEndToEndTestSuite;
import com.quasient.pal.apps.callbacks.method.MethodHandlers;
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
 * Integration tests for combined local and remote intercepts on the same method.
 *
 * <p>These tests verify that local and remote intercepts can coexist on the same method, and that
 * both types of callbacks are invoked correctly. This is important for scenarios where:
 *
 * <ul>
 *   <li>A local callback handles fast, in-process operations (logging, metrics)
 *   <li>A remote callback handles cross-process operations (authorization, audit)
 * </ul>
 *
 * <p><b>Execution order:</b>
 *
 * <ul>
 *   <li>Local BEFORE callbacks run first (before remote BEFORE)
 *   <li>Remote BEFORE callbacks run after local BEFORE
 *   <li>Method executes
 *   <li>Local AFTER callbacks run first (before remote AFTER)
 *   <li>Remote AFTER callbacks run after local AFTER
 * </ul>
 */
@RunWith(Parameterized.class)
public class LocalAndRemoteCombinedIT extends AbstractInterceptIT {

  private static final String LOCAL_CALLBACK_CLASS =
      "com.quasient.pal.apps.quantized.intercept.callback.LocalInterceptCallbacks";
  private static final String REMOTE_CALLBACK_CLASS = MethodHandlers.class.getName();
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public LocalAndRemoteCombinedIT(InvocationPath path) {
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
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID, // callback peer = interceptable peer (local)
        type,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall(methodName, Collections.singletonList(paramTypes)));
  }

  /**
   * Creates a remote intercept request where callback peer = interceptor peer.
   *
   * @param type the intercept type
   * @param methodName the method name to intercept
   * @param paramTypes the parameter types
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createRemoteMethodIntercept(
      InterceptType type, String methodName, String paramTypes, String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTOR_PEER_UUID, // callback peer = interceptor peer (remote)
        type,
        TARGET_CLASS,
        REMOTE_CALLBACK_CLASS,
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
   * Tests that both local and remote BEFORE callbacks are invoked.
   *
   * <p>Registers a local BEFORE intercept and a remote BEFORE intercept on the same method.
   * Verifies that both callbacks are invoked when the method is called.
   */
  @Test
  public void testLocalAndRemoteBeforeCallbacks() throws Exception {
    logger.info("===== testLocalAndRemoteBeforeCallbacks [{}]: TEST STARTED =====", path);

    // 1. Register a local BEFORE intercept on multiplyBy method
    logger.info("Creating local BEFORE intercept request");
    InterceptRequest<InterceptableMethodCall> localIntercept =
        createLocalMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "onBefore");

    // 2. Register a remote BEFORE intercept on multiplyBy method
    logger.info("Creating remote BEFORE intercept request");
    InterceptRequest<InterceptableMethodCall> remoteIntercept =
        createRemoteMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "noOp");

    register(localIntercept);
    register(remoteIntercept);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 4. Invoke multiplyBy
    logger.info("Invoking multiplyBy via {} path", path);
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);

    // 5. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 6. Verify local BEFORE callback was invoked
    Thread.sleep(50);
    assertThat(
        "Local BEFORE callback should have been invoked",
        LocalInterceptCallbacks.getBeforeCallCount(),
        is(greaterThan(0)));

    // 7. Verify remote BEFORE callback was invoked (via app log)
    assertTrue(
        "Expected remote noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations"));

    logger.info("===== testLocalAndRemoteBeforeCallbacks [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that both local and remote AFTER callbacks are invoked.
   *
   * <p>Registers a local AFTER intercept and a remote AFTER intercept on the same method. Verifies
   * that both callbacks are invoked when the method is called.
   */
  @Test
  public void testLocalAndRemoteAfterCallbacks() throws Exception {
    logger.info("===== testLocalAndRemoteAfterCallbacks [{}]: TEST STARTED =====", path);

    // 1. Register a local AFTER intercept on multiplyBy method
    logger.info("Creating local AFTER intercept request");
    InterceptRequest<InterceptableMethodCall> localIntercept =
        createLocalMethodIntercept(
            InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "onAfter");

    // 2. Register a remote AFTER intercept on multiplyBy method
    logger.info("Creating remote AFTER intercept request");
    InterceptRequest<InterceptableMethodCall> remoteIntercept =
        createRemoteMethodIntercept(InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "noOp");

    register(localIntercept);
    register(remoteIntercept);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 4. Invoke multiplyBy
    logger.info("Invoking multiplyBy via {} path", path);
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);

    // 5. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 6. Verify local AFTER callback was invoked
    Thread.sleep(50);
    assertThat(
        "Local AFTER callback should have been invoked",
        LocalInterceptCallbacks.getAfterCallCount(),
        is(greaterThan(0)));

    // 7. Verify remote AFTER callback was invoked (via app log)
    assertTrue(
        "Expected remote noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations"));

    logger.info("===== testLocalAndRemoteAfterCallbacks [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that all four callbacks (local BEFORE, remote BEFORE, local AFTER, remote AFTER) are
   * invoked when both types of intercepts are registered for both phases.
   */
  @Test
  public void testLocalAndRemoteBeforeAndAfterCallbacks() throws Exception {
    logger.info("===== testLocalAndRemoteBeforeAndAfterCallbacks [{}]: TEST STARTED =====", path);

    // 1. Register all four intercepts
    InterceptRequest<InterceptableMethodCall> localBefore =
        createLocalMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "onBefore");
    InterceptRequest<InterceptableMethodCall> remoteBefore =
        createRemoteMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "noOp");
    InterceptRequest<InterceptableMethodCall> localAfter =
        createLocalMethodIntercept(
            InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "onAfter");
    InterceptRequest<InterceptableMethodCall> remoteAfter =
        createRemoteMethodIntercept(InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "noOp");

    register(localBefore);
    register(remoteBefore);
    register(localAfter);
    register(remoteAfter);
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

    // 5. Verify local callbacks were invoked
    Thread.sleep(50);
    assertThat(
        "Local BEFORE callback should have been invoked",
        LocalInterceptCallbacks.getBeforeCallCount(),
        is(greaterThan(0)));
    assertThat(
        "Local AFTER callback should have been invoked",
        LocalInterceptCallbacks.getAfterCallCount(),
        is(greaterThan(0)));

    // 6. Verify remote callbacks were invoked (via app log - should see at least 2 logs)
    // Note: We can't easily distinguish between remote BEFORE and AFTER logs with noOp
    assertTrue(
        "Expected remote noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations"));

    logger.info("===== testLocalAndRemoteBeforeAndAfterCallbacks [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests local async callback combined with remote sync callback.
   *
   * <p>Registers a local BEFORE_ASYNC intercept and a remote BEFORE intercept. The local async
   * callback fires asynchronously while the remote callback is synchronous.
   */
  @Test
  public void testLocalAsyncWithRemoteSyncCallbacks() throws Exception {
    logger.info("===== testLocalAsyncWithRemoteSyncCallbacks [{}]: TEST STARTED =====", path);

    // 1. Set up latch for async callback
    LocalInterceptCallbacks.setAsyncLatch(1);

    // 2. Register a local BEFORE_ASYNC intercept
    InterceptRequest<InterceptableMethodCall> localAsyncIntercept =
        createLocalMethodIntercept(
            InterceptType.BEFORE_ASYNC, "multiplyBy", "java.lang.Integer", "onBeforeAsync");

    // 3. Register a remote BEFORE intercept
    InterceptRequest<InterceptableMethodCall> remoteIntercept =
        createRemoteMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "noOp");

    register(localAsyncIntercept);
    register(remoteIntercept);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 4. Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 5. Invoke multiplyBy
    logger.info("Invoking multiplyBy via {} path", path);
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);

    // 6. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 7. Wait for async callback to complete
    boolean asyncInvoked = LocalInterceptCallbacks.awaitAsyncCallbacks(2000);
    assertTrue("Async callback should complete within timeout", asyncInvoked);

    // 8. Verify local async callback was invoked
    assertThat(
        "Local BEFORE_ASYNC callback should have been invoked",
        LocalInterceptCallbacks.getBeforeAsyncCallCount(),
        is(greaterThan(0)));

    // 9. Verify remote callback was invoked
    assertTrue(
        "Expected remote noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations"));

    logger.info("===== testLocalAsyncWithRemoteSyncCallbacks [{}]: TEST COMPLETED =====", path);
  }
}
