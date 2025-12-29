/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.chain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for exception propagation through the AROUND intercept chain.
 *
 * <p>These tests verify that exceptions are properly propagated through the onion model:
 *
 * <ul>
 *   <li>Exceptions from the method propagate outward through AFTER phases
 *   <li>Exceptions from callbacks before proceed() skip inner layers
 *   <li>Exceptions from callbacks after proceed() override return values
 *   <li>Callbacks can suppress or replace exceptions
 * </ul>
 */
@RunWith(Parameterized.class)
public class AroundChainExceptionIT extends AbstractInterceptIT {

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
  public AroundChainExceptionIT(InvocationPath path) {
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
   * Creates a local AROUND intercept request for a specified method.
   *
   * @param callbackMethod the callback method name
   * @param targetMethod the method to intercept
   * @param paramTypes the parameter types
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalAroundInterceptFor(
      String callbackMethod, String targetMethod, List<String> paramTypes) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID,
        InterceptType.AROUND,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall(targetMethod, paramTypes));
  }

  /**
   * Creates a remote AROUND intercept request for getCounter.
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
   * Creates a remote AROUND intercept request for a specified method.
   *
   * @param callbackMethod the callback method name
   * @param targetMethod the method to intercept
   * @param paramTypes the parameter types
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createRemoteAroundInterceptFor(
      String callbackMethod, String targetMethod, List<String> paramTypes) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTOR_PEER_UUID,
        InterceptType.AROUND,
        TARGET_CLASS,
        REMOTE_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall(targetMethod, paramTypes));
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

  /**
   * Invokes maybeThrow on the given app instance.
   *
   * @param appInstance the target object
   * @param shouldThrow whether the method should throw
   * @return the response ExecMessage
   */
  private ExecMessage invokeMaybeThrow(ObjectRef appInstance, boolean shouldThrow) {
    return invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            TARGET_CLASS,
            "maybeThrow",
            appInstance,
            new String[] {"java.lang.Boolean"},
            new Object[] {shouldThrow}));
  }

  // ==================== Tests ====================

  /**
   * Tests that local AROUND callback can throw exception BEFORE proceed.
   *
   * <p>Chain: Local exceptionThrowerBefore → (not reached: method)
   *
   * <p>Expected: Exception propagates to caller, method NOT invoked.
   */
  @Test
  public void testLocalAroundThrowsBeforeProceed() throws Exception {
    logger.info("===== testLocalAroundThrowsBeforeProceed [{}] =====", path);

    // Register AROUND intercept that throws before proceed
    InterceptRequest<?> thrower = createLocalAroundIntercept("exceptionThrowerBefore");
    register(thrower);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app
    ObjectRef appInstance = createAppWithCounter(42);

    // Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // Verify exception was raised
    assertThat("Should have raised exception", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception message should match",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("exceptionThrowerBefore"));

    // Verify log shows callback was invoked
    assertTrue(
        "Callback should log THROWING",
        InterceptEndToEndTestSuite.waitForAppLogLine("exceptionThrowerBefore THROWING"));

    logger.info("===== testLocalAroundThrowsBeforeProceed [{}]: PASSED =====", path);
  }

  /**
   * Tests that local AROUND callback can throw exception AFTER proceed.
   *
   * <p>Chain: Local exceptionThrowerAfter (proceed → method → throw after)
   *
   * <p>Expected: Method executes successfully, then exception from callback overrides result.
   */
  @Test
  public void testLocalAroundThrowsAfterProceed() throws Exception {
    logger.info("===== testLocalAroundThrowsAfterProceed [{}] =====", path);

    // Register AROUND intercept that throws after proceed
    InterceptRequest<?> thrower = createLocalAroundIntercept("exceptionThrowerAfter");
    register(thrower);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app
    ObjectRef appInstance = createAppWithCounter(42);

    // Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // Verify exception was raised
    assertThat("Should have raised exception", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception message should match",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("exceptionThrowerAfter"));

    // Verify log shows method was invoked and callback threw after
    assertTrue(
        "Callback should log AFTER with return value",
        InterceptEndToEndTestSuite.waitForAppLogLine("exceptionThrowerAfter AFTER.*got return=42"));
    assertTrue(
        "Callback should log NOW THROWING",
        InterceptEndToEndTestSuite.waitForAppLogLine("NOW THROWING"));

    logger.info("===== testLocalAroundThrowsAfterProceed [{}]: PASSED =====", path);
  }

  /**
   * Tests that remote AROUND callback can throw exception BEFORE proceed.
   *
   * <p>Chain: Local logger (outer) → Remote exceptionThrowerBefore (throws) → (not reached: method)
   *
   * <p>Expected: Exception propagates through local outer layer, method NOT invoked.
   */
  @Test
  public void testRemoteAroundThrowsBeforeProceed() throws Exception {
    logger.info("===== testRemoteAroundThrowsBeforeProceed [{}] =====", path);

    // Register outer local logger and inner remote exception thrower
    InterceptRequest<?> outerLogger = createLocalAroundIntercept("exceptionLogger");
    InterceptRequest<?> remoteThrower = createRemoteAroundIntercept("innerExceptionThrowerBefore");

    register(outerLogger);
    register(remoteThrower);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app
    ObjectRef appInstance = createAppWithCounter(42);

    // Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // Verify exception was raised
    assertThat("Should have raised exception", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception message should contain remote thrower info",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("innerExceptionThrowerBefore"));

    // Verify outer logger saw the exception propagating
    assertTrue(
        "Outer logger should see exception",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "exceptionLogger AFTER.*exception propagating"));

    logger.info("===== testRemoteAroundThrowsBeforeProceed [{}]: PASSED =====", path);
  }

  /**
   * Tests that local AROUND callback can suppress method exception.
   *
   * <p>Chain: Local exceptionSuppressor → (method throws → suppressor catches and returns -999)
   *
   * <p>Expected: Caller receives -999 instead of exception.
   */
  @Test
  public void testLocalAroundSuppressesException() throws Exception {
    logger.info("===== testLocalAroundSuppressesException [{}] =====", path);

    // Register suppressor intercept for maybeThrow method
    InterceptRequest<?> suppressor =
        createLocalAroundInterceptFor(
            "exceptionSuppressor", "maybeThrow", List.of("java.lang.Boolean"));
    register(suppressor);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app
    ObjectRef appInstance = createAppWithCounter(42);

    // Invoke maybeThrow(true) - should throw but suppressor catches it
    ExecMessage response = invokeMaybeThrow(appInstance, true);

    // Verify no exception raised (suppressed)
    assertThat("Exception should be suppressed", response.getRaisedThrowable(), is(nullValue()));

    // Verify return value is the fallback value
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be fallback value -999", returnValue, is(-999));

    // Verify log shows suppression
    assertTrue(
        "Callback should log SUPPRESSING",
        InterceptEndToEndTestSuite.waitForAppLogLine("exceptionSuppressor SUPPRESSING"));

    logger.info("===== testLocalAroundSuppressesException [{}]: PASSED =====", path);
  }

  /**
   * Tests that local AROUND callback can replace one exception with another.
   *
   * <p>Chain: Local exceptionReplacer → (method throws IllegalArgumentException → replacer converts
   * to IllegalStateException)
   *
   * <p>Expected: Caller receives IllegalStateException wrapping original.
   */
  @Test
  public void testLocalAroundReplacesException() throws Exception {
    logger.info("===== testLocalAroundReplacesException [{}] =====", path);

    // Register replacer intercept for maybeThrow method
    InterceptRequest<?> replacer =
        createLocalAroundInterceptFor(
            "exceptionReplacer", "maybeThrow", List.of("java.lang.Boolean"));
    register(replacer);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app
    ObjectRef appInstance = createAppWithCounter(42);

    // Invoke maybeThrow(true) - throws IllegalArgumentException, replacer converts
    ExecMessage response = invokeMaybeThrow(appInstance, true);

    // Verify exception was raised
    assertThat("Should have raised exception", response.getRaisedThrowable(), is(notNullValue()));

    // Verify it's the replaced exception type
    assertThat(
        "Exception should be IllegalStateException",
        response.getRaisedThrowable().getThrowable().getType(),
        containsString("IllegalStateException"));
    assertThat(
        "Exception message should indicate replacement",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Replaced exception"));

    // Verify log shows replacement
    assertTrue(
        "Callback should log REPLACING",
        InterceptEndToEndTestSuite.waitForAppLogLine("exceptionReplacer REPLACING"));

    logger.info("===== testLocalAroundReplacesException [{}]: PASSED =====", path);
  }

  /**
   * Tests exception propagation through multi-layer chain (local + remote).
   *
   * <p>Chain: Local logger (outer) → Local logger (middle) → Remote logger (inner) → method throws
   *
   * <p>Expected: All layers see the exception in their AFTER phase.
   */
  @Test
  public void testExceptionPropagatesThroughChain() throws Exception {
    logger.info("===== testExceptionPropagatesThroughChain [{}] =====", path);

    // Register multiple loggers
    InterceptRequest<?> outer =
        createLocalAroundInterceptFor(
            "exceptionLogger", "maybeThrow", List.of("java.lang.Boolean"));
    InterceptRequest<?> middle =
        createLocalAroundInterceptFor("middleLogger", "maybeThrow", List.of("java.lang.Boolean"));
    InterceptRequest<?> inner =
        createRemoteAroundInterceptFor("innerLogger", "maybeThrow", List.of("java.lang.Boolean"));

    register(outer);
    register(middle);
    register(inner);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app
    ObjectRef appInstance = createAppWithCounter(42);

    // Invoke maybeThrow(true) - should throw
    ExecMessage response = invokeMaybeThrow(appInstance, true);

    // Verify exception was raised
    assertThat("Should have raised exception", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be IllegalArgumentException",
        response.getRaisedThrowable().getThrowable().getType(),
        containsString("IllegalArgumentException"));

    // Verify all layers logged their BEFORE/AFTER
    assertTrue(
        "exceptionLogger BEFORE",
        InterceptEndToEndTestSuite.waitForAppLogLine("exceptionLogger BEFORE"));
    assertTrue(
        "middleLogger BEFORE", InterceptEndToEndTestSuite.waitForAppLogLine("middleLogger BEFORE"));
    assertTrue(
        "innerLogger BEFORE", InterceptEndToEndTestSuite.waitForAppLogLine("innerLogger BEFORE"));

    // Outer layer should see exception propagating
    assertTrue(
        "exceptionLogger should see exception",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "exceptionLogger AFTER.*exception propagating"));

    logger.info("===== testExceptionPropagatesThroughChain [{}]: PASSED =====", path);
  }

  /**
   * Tests that normal return works when no exception is thrown (sanity check).
   *
   * <p>Chain: Local suppressor → maybeThrow(false) returns normally
   *
   * <p>Expected: Suppressor doesn't interfere, normal return value.
   */
  @Test
  public void testNoExceptionNormalReturn() throws Exception {
    logger.info("===== testNoExceptionNormalReturn [{}] =====", path);

    // Register suppressor intercept for maybeThrow method
    InterceptRequest<?> suppressor =
        createLocalAroundInterceptFor(
            "exceptionSuppressor", "maybeThrow", List.of("java.lang.Boolean"));
    register(suppressor);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app with counter = 77
    ObjectRef appInstance = createAppWithCounter(77);

    // Invoke maybeThrow(false) - should NOT throw
    ExecMessage response = invokeMaybeThrow(appInstance, false);

    // Verify no exception
    assertThat("Should NOT have raised exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify return value is the counter (not fallback)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be counter value", returnValue, is(77));

    // Verify log shows no exception case
    assertTrue(
        "Callback should log no exception",
        InterceptEndToEndTestSuite.waitForAppLogLine("exceptionSuppressor AFTER.*no exception"));

    logger.info("===== testNoExceptionNormalReturn [{}]: PASSED =====", path);
  }
}
