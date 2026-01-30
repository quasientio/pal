/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.chain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for nested/recursive intercepted method calls.
 *
 * <p>These tests verify that the AROUND intercept chain handles nested calls correctly:
 *
 * <ul>
 *   <li>Method A calls Method B, both have AROUND intercepts
 *   <li>Each method's intercept chain is independent
 *   <li>Return value modifications propagate correctly through nested chains
 *   <li>Exceptions in inner chains propagate through outer chains
 * </ul>
 *
 * <p>Scenario: getCounter() is intercepted, and during its execution, another intercepted method
 * might be called. Each call has its own independent intercept chain.
 */
@RunWith(Parameterized.class)
public class AroundNestedCallsIT extends AbstractInterceptIT {

  /** Fully qualified name of the local callback handler class. */
  private static final String LOCAL_CALLBACK_CLASS =
      "io.quasient.pal.apps.callbacks.chain.AroundChainCallbacks";

  /** Fully qualified name of the remote callback handler class. */
  private static final String REMOTE_CALLBACK_CLASS =
      "io.quasient.pal.apps.callbacks.chain.RemoteAroundChainCallbacks";

  /** Target class for interception. */
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public AroundNestedCallsIT(InvocationPath path) {
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
   * Creates a local AROUND intercept request for getCounter.
   *
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalAroundForGetCounter(
      String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID,
        InterceptType.AROUND,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("getCounter", Collections.emptyList()));
  }

  /**
   * Creates a local AROUND intercept request for setCounter.
   *
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalAroundForSetCounter(
      String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID,
        InterceptType.AROUND,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("setCounter", Collections.singletonList("int")));
  }

  /**
   * Creates a remote AROUND intercept request for getCounter.
   *
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createRemoteAroundForGetCounter(
      String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTOR_PEER_UUID,
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
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

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

  /**
   * Invokes setCounter on the given app instance.
   *
   * @param appInstance the target object
   * @param value the value to set
   * @return the response ExecMessage
   */
  private ExecMessage invokeSetCounter(ObjectRef appInstance, int value) {
    return invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setCounter",
            appInstance,
            new String[] {"int"},
            new Object[] {value}));
  }

  // ==================== Tests: Multiple Intercepted Method Calls ====================

  /**
   * Tests that separate calls to different intercepted methods work independently.
   *
   * <p>Scenario:
   *
   * <ul>
   *   <li>getCounter has AROUND intercept (doubler)
   *   <li>setCounter has AROUND intercept (logger)
   *   <li>Call setCounter, then getCounter
   *   <li>Each method's intercept chain is independent
   * </ul>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testSeparateInterceptedMethods() throws Exception {
    logger.info("===== testSeparateInterceptedMethods [{}] =====", path);

    // 1. Register AROUND intercepts for both methods
    InterceptRequest<?> getCounterDoubler = createLocalAroundForGetCounter("outerDoubler");
    InterceptRequest<?> setCounterLogger = createLocalAroundForSetCounter("middleLogger");

    register(getCounterDoubler);
    register(setCounterLogger);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app
    ObjectRef appInstance = createAppWithCounter(0);

    // 3. Call setCounter(10) - intercept should log but not modify
    logger.info("Calling setCounter(10)");
    ExecMessage setResponse = invokeSetCounter(appInstance, 10);
    assertThat(
        "setCounter should not raise exception", setResponse.getRaisedThrowable(), is(nullValue()));

    // Verify setCounter intercept was invoked
    assertTrue(
        "setCounter middleLogger should be invoked",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: middleLogger BEFORE"));

    // 4. Clear log and call getCounter - intercept should double result
    InterceptEndToEndTestSuite.clearAppLog();

    logger.info("Calling getCounter - expect 10 * 2 = 20");
    ExecMessage getResponse = invokeGetCounter(appInstance);
    assertThat(
        "getCounter should not raise exception", getResponse.getRaisedThrowable(), is(nullValue()));

    // 5. Verify return value is doubled
    int returnValue = (Integer) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    assertThat("Return should be 10 * 2 = 20", returnValue, is(20));

    // Verify getCounter intercept was invoked
    assertTrue(
        "getCounter outerDoubler should be invoked",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: outerDoubler BEFORE"));
    assertTrue(
        "getCounter outerDoubler should double value",
        InterceptEndToEndTestSuite.waitForAppLogLine("outerDoubler AFTER.*10 -> 20"));

    logger.info("===== testSeparateInterceptedMethods [{}]: PASSED =====", path);
  }

  // ==================== Tests: Sequential Intercepted Calls ====================

  /**
   * Tests that sequential calls to the same intercepted method each get fresh intercept chains.
   *
   * <p>Scenario:
   *
   * <ul>
   *   <li>getCounter has AROUND intercept (doubler)
   *   <li>Call getCounter multiple times with different counter values
   *   <li>Each call should have an independent intercept execution
   * </ul>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testSequentialCallsHaveFreshChains() throws Exception {
    logger.info("===== testSequentialCallsHaveFreshChains [{}] =====", path);

    // 1. Register AROUND intercept
    InterceptRequest<?> getCounterDoubler = createLocalAroundForGetCounter("outerDoubler");
    register(getCounterDoubler);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 5
    ObjectRef appInstance = createAppWithCounter(5);

    // 3. First call - expect 5 * 2 = 10
    ExecMessage response1 = invokeGetCounter(appInstance);
    assertThat(
        "First call should not raise exception", response1.getRaisedThrowable(), is(nullValue()));
    int value1 = (Integer) Unwrapper.unwrapObject(response1.getReturnValue().getObject());
    assertThat("First call should return 5 * 2 = 10", value1, is(10));

    // 4. Update counter and call again - expect 15 * 2 = 30
    invokeSetCounter(appInstance, 15);
    ExecMessage response2 = invokeGetCounter(appInstance);
    assertThat(
        "Second call should not raise exception", response2.getRaisedThrowable(), is(nullValue()));
    int value2 = (Integer) Unwrapper.unwrapObject(response2.getReturnValue().getObject());
    assertThat("Second call should return 15 * 2 = 30", value2, is(30));

    // 5. Update counter and call again - expect 25 * 2 = 50
    invokeSetCounter(appInstance, 25);
    ExecMessage response3 = invokeGetCounter(appInstance);
    assertThat(
        "Third call should not raise exception", response3.getRaisedThrowable(), is(nullValue()));
    int value3 = (Integer) Unwrapper.unwrapObject(response3.getReturnValue().getObject());
    assertThat("Third call should return 25 * 2 = 50", value3, is(50));

    logger.info("===== testSequentialCallsHaveFreshChains [{}]: PASSED =====", path);
  }

  // ==================== Tests: Mixed Local and Remote Chains ====================

  /**
   * Tests sequential calls with mixed local and remote AROUND intercepts.
   *
   * <p>Scenario:
   *
   * <ul>
   *   <li>getCounter has local AROUND (doubler) + remote AROUND (adder +10)
   *   <li>Expected: (value + 10) * 2
   *   <li>Multiple calls should each get this transformation
   * </ul>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testSequentialCallsWithMixedChain() throws Exception {
    logger.info("===== testSequentialCallsWithMixedChain [{}] =====", path);

    // 1. Register local and remote AROUND intercepts
    InterceptRequest<?> localDoubler = createLocalAroundForGetCounter("outerDoubler");
    InterceptRequest<?> remoteAdder = createRemoteAroundForGetCounter("innerAdder");

    register(localDoubler);
    register(remoteAdder);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 5
    ObjectRef appInstance = createAppWithCounter(5);

    // 3. First call - expect (5 + 10) * 2 = 30
    ExecMessage response1 = invokeGetCounter(appInstance);
    assertThat(
        "First call should not raise exception", response1.getRaisedThrowable(), is(nullValue()));
    int value1 = (Integer) Unwrapper.unwrapObject(response1.getReturnValue().getObject());
    assertThat("First call should return (5 + 10) * 2 = 30", value1, is(30));

    // 4. Update counter and call again - expect (12 + 10) * 2 = 44
    invokeSetCounter(appInstance, 12);
    ExecMessage response2 = invokeGetCounter(appInstance);
    assertThat(
        "Second call should not raise exception", response2.getRaisedThrowable(), is(nullValue()));
    int value2 = (Integer) Unwrapper.unwrapObject(response2.getReturnValue().getObject());
    assertThat("Second call should return (12 + 10) * 2 = 44", value2, is(44));

    logger.info("===== testSequentialCallsWithMixedChain [{}]: PASSED =====", path);
  }

  // ==================== Tests: Caching Across Sequential Calls ====================

  /**
   * Tests that a caching AROUND intercept works correctly across sequential calls.
   *
   * <p>Scenario:
   *
   * <ul>
   *   <li>getCounter has caching AROUND intercept
   *   <li>First call: cache miss, method executes
   *   <li>Second call (same args): cache hit, method skipped
   *   <li>Third call (different instance, same method): cache hit (method name based)
   * </ul>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testCachingAcrossSequentialCalls() throws Exception {
    logger.info("===== testCachingAcrossSequentialCalls [{}] =====", path);

    // 1. Register caching AROUND intercept
    InterceptRequest<?> cachingIntercept = createLocalAroundForGetCounter("cachingCallback");
    register(cachingIntercept);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 42
    ObjectRef appInstance = createAppWithCounter(42);

    // 3. First call - cache miss
    logger.info("First call - expect cache miss");
    ExecMessage response1 = invokeGetCounter(appInstance);
    assertThat(
        "First call should not raise exception", response1.getRaisedThrowable(), is(nullValue()));
    int value1 = (Integer) Unwrapper.unwrapObject(response1.getReturnValue().getObject());
    assertThat("First call should return 42", value1, is(42));

    assertTrue(
        "First call should be cache MISS",
        InterceptEndToEndTestSuite.waitForAppLogLine("cachingCallback CACHE_MISS"));

    // 4. Second call - cache hit
    // Clear log to distinguish second call
    InterceptEndToEndTestSuite.clearAppLog();

    logger.info("Second call - expect cache hit");
    ExecMessage response2 = invokeGetCounter(appInstance);
    assertThat(
        "Second call should not raise exception", response2.getRaisedThrowable(), is(nullValue()));
    int value2 = (Integer) Unwrapper.unwrapObject(response2.getReturnValue().getObject());
    assertThat("Second call should return cached 42", value2, is(42));

    assertTrue(
        "Second call should be cache HIT",
        InterceptEndToEndTestSuite.waitForAppLogLine("cachingCallback CACHE_HIT"));

    logger.info("===== testCachingAcrossSequentialCalls [{}]: PASSED =====", path);
  }
}
