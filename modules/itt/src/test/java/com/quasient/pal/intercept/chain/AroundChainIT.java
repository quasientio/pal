/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.chain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.InterceptEndToEndTestSuite;
import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for AROUND intercept chain behavior (onion model).
 *
 * <p>These tests verify the proper chaining of local and remote AROUND intercepts, where:
 *
 * <ul>
 *   <li>Local AROUND intercepts are the outermost layers
 *   <li>Remote AROUND intercepts are inner layers
 *   <li>Method execution is the innermost layer
 *   <li>Each proceed() invokes the next layer, not the method directly
 *   <li>Return values propagate outward through the chain
 *   <li>Skipping proceed() skips the entire inner chain (including remote AROUND)
 * </ul>
 *
 * <p>Chain structure:
 *
 * <pre>
 * Local AROUND-1 BEFORE (outermost)
 *   → Local AROUND-2 BEFORE
 *     → Remote AROUND-1 BEFORE
 *       → [METHOD EXECUTION]
 *     ← Remote AROUND-1 AFTER
 *   ← Local AROUND-2 AFTER
 * ← Local AROUND-1 AFTER
 * </pre>
 */
@RunWith(Parameterized.class)
public class AroundChainIT extends AbstractInterceptIT {

  /** Fully qualified name of the local callback handler class. */
  private static final String LOCAL_CALLBACK_CLASS =
      "com.quasient.pal.apps.callbacks.chain.AroundChainCallbacks";

  /** Fully qualified name of the remote callback handler class. */
  private static final String REMOTE_CALLBACK_CLASS =
      "com.quasient.pal.apps.callbacks.chain.RemoteAroundChainCallbacks";

  /** Target class for interception. */
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public AroundChainIT(InvocationPath path) {
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
   * Clears state before each test.
   *
   * @throws Exception if cleanup fails
   */
  @Before
  public void clearStateBeforeTest() throws Exception {
    // Clear the app log
    InterceptEndToEndTestSuite.clearAppLog();

    // Reset local callback state via RPC
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid, LOCAL_CALLBACK_CLASS, "reset", null, null, null, null));

    // Reset remote callback state via RPC
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid, REMOTE_CALLBACK_CLASS, "reset", null, null, null, null));
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a local AROUND intercept request.
   *
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalAroundIntercept(
      String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID, // local: callback peer = interceptable peer
        InterceptType.AROUND,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("getCounter", Collections.emptyList()));
  }

  /**
   * Creates a remote AROUND intercept request.
   *
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createRemoteAroundIntercept(
      String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTOR_PEER_UUID, // remote: callback peer = interceptor peer
        InterceptType.AROUND,
        TARGET_CLASS,
        REMOTE_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("getCounter", Collections.emptyList()));
  }

  /**
   * Creates an InterceptableApp instance and sets its counter to a specific value.
   *
   * @param initialValue the initial counter value
   * @return ObjectRef to the created instance
   */
  private ObjectRef createAppWithCounter(int initialValue) {
    // Create instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // Set counter to initial value
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setCounter",
            appInstance,
            new String[] {"int"},
            new Object[] {initialValue}));

    return appInstance;
  }

  /**
   * Invokes getCounter on the given app instance.
   *
   * @param appInstance the target object
   * @return the response ExecMessage
   */
  private ExecMessage invokeGetCounter(ObjectRef appInstance) {
    return invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid, TARGET_CLASS, "getCounter", appInstance, null, null));
  }

  // ==================== Tests ====================

  /**
   * Tests mixed local and remote AROUND chain with return value modification.
   *
   * <p>Chain:
   *
   * <ul>
   *   <li>Local #1 (outerDoubler): doubles return value after proceed
   *   <li>Local #2 (middleLogger): logs but doesn't modify
   *   <li>Remote #1 (innerAdder): adds 10 to return value after proceed
   * </ul>
   *
   * <p>Method getCounter() returns 5.
   *
   * <p>Expected flow:
   *
   * <ol>
   *   <li>outerDoubler BEFORE
   *   <li>middleLogger BEFORE
   *   <li>innerAdder BEFORE (remote)
   *   <li>Method returns 5
   *   <li>innerAdder AFTER: 5 + 10 = 15
   *   <li>middleLogger AFTER: sees 15, returns 15
   *   <li>outerDoubler AFTER: 15 * 2 = 30
   * </ol>
   *
   * <p>Final result: 30
   *
   * @throws Exception if test fails
   */
  @Test
  public void testMixedLocalRemoteAroundChain() throws Exception {
    logger.info("===== testMixedLocalRemoteAroundChain [{}] =====", path);

    // 1. Register AROUND intercepts in order: local #1, local #2, remote #1
    // Registration order determines execution order within each category
    InterceptRequest<?> outerDoubler = createLocalAroundIntercept("outerDoubler");
    InterceptRequest<?> middleLogger = createLocalAroundIntercept("middleLogger");
    InterceptRequest<?> innerAdder = createRemoteAroundIntercept("innerAdder");

    register(outerDoubler);
    register(middleLogger);
    register(innerAdder);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 5
    ObjectRef appInstance = createAppWithCounter(5);

    // 3. Invoke getCounter
    logger.info("Invoking getCounter via {} path", path);
    ExecMessage response = invokeGetCounter(appInstance);

    // 4. Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify return value: (5 + 10) * 2 = 30
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be (5 + 10) * 2 = 30 after chain processing", returnValue, is(30));

    // 6. Verify log shows proper execution order
    assertTrue(
        "outerDoubler BEFORE should execute first",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: outerDoubler BEFORE"));
    assertTrue(
        "middleLogger BEFORE should execute second",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: middleLogger BEFORE"));
    assertTrue(
        "innerAdder BEFORE should execute third (remote)",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: innerAdder BEFORE"));
    assertTrue(
        "innerAdder AFTER should show 5 + 10 = 15",
        InterceptEndToEndTestSuite.waitForAppLogLine("innerAdder AFTER.*5 \\+ 10 = 15"));
    assertTrue(
        "middleLogger AFTER should see 15",
        InterceptEndToEndTestSuite.waitForAppLogLine("middleLogger AFTER.*return=15"));
    assertTrue(
        "outerDoubler AFTER should show 15 -> 30",
        InterceptEndToEndTestSuite.waitForAppLogLine("outerDoubler AFTER.*15 -> 30"));

    logger.info("===== testMixedLocalRemoteAroundChain [{}]: PASSED =====", path);
  }

  /**
   * Tests that skipping proceed() in a local AROUND skips the entire inner chain.
   *
   * <p>Chain:
   *
   * <ul>
   *   <li>Local #1 (cachingCallback): caches results, skips proceed on cache hit
   *   <li>Remote #1 (innerLogger): logs invocations
   * </ul>
   *
   * <p>Test flow:
   *
   * <ol>
   *   <li>First call: cache miss, proceed() called, remote AROUND fires, method executes
   *   <li>Second call: cache hit, proceed() NOT called, remote AROUND does NOT fire
   * </ol>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testAroundChainWithSkip() throws Exception {
    logger.info("===== testAroundChainWithSkip [{}] =====", path);

    // 1. Register AROUND intercepts: local caching + remote logger
    InterceptRequest<?> cachingIntercept = createLocalAroundIntercept("cachingCallback");
    InterceptRequest<?> remoteLogger = createRemoteAroundIntercept("innerLogger");

    register(cachingIntercept);
    register(remoteLogger);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 42
    ObjectRef appInstance = createAppWithCounter(42);

    // 3. First call - cache miss
    logger.info("First call - expecting cache miss");
    ExecMessage response1 = invokeGetCounter(appInstance);

    assertThat(
        "First call should not raise exception", response1.getRaisedThrowable(), is(nullValue()));

    int returnValue1 = (Integer) Unwrapper.unwrapObject(response1.getReturnValue().getObject());
    assertThat("First call should return 42", returnValue1, is(42));

    // Verify cache miss and remote callback invoked
    assertTrue(
        "First call should be cache miss",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: cachingCallback CACHE_MISS"));
    assertTrue(
        "Remote innerLogger should be invoked on cache miss",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: innerLogger BEFORE"));

    // Clear log for second call
    InterceptEndToEndTestSuite.clearAppLog();

    // 4. Second call - cache hit (proceed should be skipped)
    logger.info("Second call - expecting cache hit");
    ExecMessage response2 = invokeGetCounter(appInstance);

    assertThat(
        "Second call should not raise exception", response2.getRaisedThrowable(), is(nullValue()));

    int returnValue2 = (Integer) Unwrapper.unwrapObject(response2.getReturnValue().getObject());
    assertThat("Second call should return cached 42", returnValue2, is(42));

    // Verify cache hit
    assertTrue(
        "Second call should be cache hit",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: cachingCallback CACHE_HIT"));

    // Verify remote callback was NOT invoked (since proceed was skipped)
    // Wait a bit and check that innerLogger was NOT called
    Thread.sleep(500);
    boolean remoteLoggerInvoked =
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: innerLogger", 1);
    assertThat(
        "Remote innerLogger should NOT be invoked on cache hit (proceed skipped)",
        remoteLoggerInvoked,
        is(false));

    logger.info("===== testAroundChainWithSkip [{}]: PASSED =====", path);
  }
}
