/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.local.method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.LocalInterceptTestSuite;
import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import java.io.IOException;
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
      "io.quasient.pal.apps.callbacks.local.LocalInterceptCallbacks";
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
   * Clears the application log and resets callback counters before each test.
   *
   * @throws IOException if log file cannot be cleared
   */
  @Before
  public void clearAppLogBeforeTest() throws IOException {
    LocalInterceptTestSuite.clearAppLog();
    // Reset callback counters in the peer via RPC
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid, TARGET_CLASS, "resetLocalInterceptCallbacks", null, null, null, null));
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

    // 5. Verify local BEFORE callback was invoked (via log output)
    assertTrue(
        "Local BEFORE callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*multiplyBy.*count=1"));

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

    // 5. Verify local AFTER callback was invoked (via log output)
    assertTrue(
        "Local AFTER callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER:.*multiplyBy.*count=1"));

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

    // 4. Verify local BEFORE callbacks were invoked n times (via log output)
    assertTrue(
        "Local BEFORE callback should have been invoked " + n + " times",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*multiplyBy.*count=" + n));

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

    // 4. Verify both callbacks were invoked (via log output)
    assertTrue(
        "Local BEFORE callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*multiplyBy.*count=1"));
    assertTrue(
        "Local AFTER callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER:.*multiplyBy.*count=1"));

    logger.info("===== testLocalBeforeAndAfterCallbacks [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Argument Mutation Tests
  // ===========================================================================

  /**
   * Tests that a BEFORE callback can mutate arguments.
   *
   * <p>Registers a BEFORE intercept that doubles the first argument, then verifies the method
   * receives the mutated value.
   */
  @Test
  public void testBeforeArgMutation() throws Exception {
    logger.info("===== testBeforeArgMutation [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that doubles the multiplier
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "onBeforeMutateArg");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp with counter=10
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {10}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Invoke multiplyBy(3) - callback should double to 6, so counter = 10 * 6 = 60
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify arg was mutated (via log)
    assertTrue(
        "BEFORE callback should have mutated arg 3 -> 6",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE_MUTATE_ARG: 3 -> 6"));

    // 5. Verify the counter value is 60 (10 * 6), not 30 (10 * 3)
    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid, TARGET_CLASS, "getCounter", appInstance, new String[] {}, null));
    int counterValue =
        (Integer) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    assertThat("Counter should be 60 (10 * 6) due to arg mutation", counterValue, is(60));

    logger.info("===== testBeforeArgMutation [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AROUND callback can mutate arguments before proceeding.
   *
   * <p>Registers an AROUND intercept that doubles the first argument before calling proceed().
   */
  @Test
  public void testAroundMutateArgBeforeProceed() throws Exception {
    logger.info("===== testAroundMutateArgBeforeProceed [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that doubles arg before proceed
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyBy",
            "java.lang.Integer",
            "onAroundMutateArgBeforeProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp with counter=10
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {10}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Invoke multiplyBy(5) - callback should double to 10, so counter = 10 * 10 = 100
    ExecMessage response = invokeMultiplyByOnce(appInstance, 5);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify arg was mutated (via log)
    assertTrue(
        "AROUND callback should have mutated arg 5 -> 10",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AROUND_MUTATE_ARG: 5 -> 10"));

    // 5. Verify the counter value is 100 (10 * 10)
    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid, TARGET_CLASS, "getCounter", appInstance, new String[] {}, null));
    int counterValue =
        (Integer) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    assertThat("Counter should be 100 (10 * 10) due to arg mutation", counterValue, is(100));

    logger.info("===== testAroundMutateArgBeforeProceed [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Return Value Override Tests
  // ===========================================================================

  /**
   * Tests that an AFTER callback can override the return value.
   *
   * <p>Registers an AFTER intercept that doubles the return value.
   */
  @Test
  public void testAfterReturnOverride() throws Exception {
    logger.info("===== testAfterReturnOverride [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept on multiplyStaticBy that doubles return value
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AFTER, "multiplyStaticBy", "java.lang.Integer", "onAfterOverrideReturn");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Set static counter to 10
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {10}));

    // 3. Invoke multiplyStaticBy(3) - method returns 30, callback doubles to 60
    ExecMessage response;
    if (path == InvocationPath.HOT_PATH) {
      ObjectRef appInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "callMultiplyStaticBy",
                  appInstance,
                  new String[] {"java.lang.Integer"},
                  new Object[] {3}));
    } else {
      response =
          invoke(
              messageBuilder.buildClassMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "multiplyStaticBy",
                  new String[] {"java.lang.Integer"},
                  null,
                  null,
                  new Object[] {3}));
    }

    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify return value was overridden (via log)
    assertTrue(
        "AFTER callback should have overridden return 30 -> 60",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER_OVERRIDE_RETURN: 30 -> 60"));

    // 5. Verify we received the overridden value (60)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be 60 (30 * 2) due to override", returnValue, is(60));

    logger.info("===== testAfterReturnOverride [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AROUND callback can override return value after proceeding.
   *
   * <p>Registers an AROUND intercept that calls proceed() then doubles the return value.
   */
  @Test
  public void testAroundOverrideReturnAfterProceed() throws Exception {
    logger.info("===== testAroundOverrideReturnAfterProceed [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that doubles return after proceed
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyStaticBy",
            "java.lang.Integer",
            "onAroundOverrideReturnAfterProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Set static counter to 5
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {5}));

    // 3. Invoke multiplyStaticBy(4) - method returns 20, callback doubles to 40
    ExecMessage response;
    if (path == InvocationPath.HOT_PATH) {
      ObjectRef appInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "callMultiplyStaticBy",
                  appInstance,
                  new String[] {"java.lang.Integer"},
                  new Object[] {4}));
    } else {
      response =
          invoke(
              messageBuilder.buildClassMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "multiplyStaticBy",
                  new String[] {"java.lang.Integer"},
                  null,
                  null,
                  new Object[] {4}));
    }

    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify return value was overridden (via log)
    assertTrue(
        "AROUND callback should have overridden return 20 -> 40",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AROUND_OVERRIDE_RETURN: 20 -> 40"));

    // 5. Verify we received the overridden value (40)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be 40 (20 * 2) due to override", returnValue, is(40));

    logger.info("===== testAroundOverrideReturnAfterProceed [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // AROUND Skip Tests
  // ===========================================================================

  /**
   * Tests that an AROUND callback can skip proceed and return a custom value.
   *
   * <p>Registers an AROUND intercept that skips proceed and returns 42.
   */
  @Test
  public void testAroundSkipWithReturnValue() throws Exception {
    logger.info("===== testAroundSkipWithReturnValue [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that skips and returns 42
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyStaticBy",
            "java.lang.Integer",
            "onAroundSkipWithReturn");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Set static counter to 100
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {100}));

    // 3. Invoke multiplyStaticBy(5) - callback should skip and return 42
    ExecMessage response;
    if (path == InvocationPath.HOT_PATH) {
      ObjectRef appInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "callMultiplyStaticBy",
                  appInstance,
                  new String[] {"java.lang.Integer"},
                  new Object[] {5}));
    } else {
      response =
          invoke(
              messageBuilder.buildClassMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "multiplyStaticBy",
                  new String[] {"java.lang.Integer"},
                  null,
                  null,
                  new Object[] {5}));
    }

    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify skip was logged
    assertTrue(
        "AROUND callback should have skipped with return 42",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AROUND_SKIP_WITH_RETURN: returning 42"));

    // 5. Verify return value is 42 (not 500)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be 42 from skip", returnValue, is(42));

    // 6. Verify static counter is still 100 (method was skipped)
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid, TARGET_CLASS, "getStaticCounter", new String[] {}, null, null, null));
    int staticCounter = (Integer) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    assertThat("Static counter should still be 100 (method skipped)", staticCounter, is(100));

    logger.info("===== testAroundSkipWithReturnValue [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AROUND callback can skip proceed and return null explicitly.
   *
   * <p>Registers an AROUND intercept that skips proceed and returns null.
   */
  @Test
  public void testAroundSkipWithNullReturnValue() throws Exception {
    logger.info("===== testAroundSkipWithNullReturnValue [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that skips and returns null
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyStaticBy",
            "java.lang.Integer",
            "onAroundSkipWithNullReturn");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke multiplyStaticBy - callback should skip and return null
    ExecMessage response;
    if (path == InvocationPath.HOT_PATH) {
      ObjectRef appInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "callMultiplyStaticBy",
                  appInstance,
                  new String[] {"java.lang.Integer"},
                  new Object[] {5}));
    } else {
      response =
          invoke(
              messageBuilder.buildClassMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "multiplyStaticBy",
                  new String[] {"java.lang.Integer"},
                  null,
                  null,
                  new Object[] {5}));
    }

    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify skip was logged
    assertTrue(
        "AROUND callback should have skipped with null return",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_SKIP_WITH_NULL_RETURN: returning null"));

    // 4. Verify return value is null
    Object returnValue = Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be null from skip", returnValue, is(nullValue()));

    logger.info("===== testAroundSkipWithNullReturnValue [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AROUND callback can skip proceed and throw an exception.
   *
   * <p>Registers an AROUND intercept that skips proceed and throws SecurityException.
   */
  @Test
  public void testAroundSkipWithException() throws Exception {
    logger.info("===== testAroundSkipWithException [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that skips and throws exception
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyStaticBy",
            "java.lang.Integer",
            "onAroundSkipWithException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke multiplyStaticBy - callback should skip and throw exception
    ExecMessage response;
    if (path == InvocationPath.HOT_PATH) {
      ObjectRef appInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "callMultiplyStaticBy",
                  appInstance,
                  new String[] {"java.lang.Integer"},
                  new Object[] {5}));
    } else {
      response =
          invoke(
              messageBuilder.buildClassMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "multiplyStaticBy",
                  new String[] {"java.lang.Integer"},
                  null,
                  null,
                  new Object[] {5}));
    }

    // 3. Verify exception was raised
    assertThat(
        "Exception should have been raised", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be SecurityException",
        response.getRaisedThrowable().getThrowable().getType(),
        is(SecurityException.class.getName()));
    assertThat(
        "Exception message should match",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Access denied by AROUND skip"));

    // 4. Verify skip was logged
    assertTrue(
        "AROUND callback should have logged skip with exception",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_SKIP_WITH_EXCEPTION: throwing SecurityException"));

    logger.info("===== testAroundSkipWithException [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Exception Throwing Tests
  // ===========================================================================

  /**
   * Tests that a BEFORE callback can throw an exception.
   *
   * <p>Registers a BEFORE intercept that throws SecurityException.
   */
  @Test
  public void testBeforeThrowsException() throws Exception {
    logger.info("===== testBeforeThrowsException [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that throws exception
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "onBeforeThrowException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke multiplyBy - callback should throw exception
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);

    // 4. Verify exception was raised
    assertThat(
        "Exception should have been raised", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be SecurityException",
        response.getRaisedThrowable().getThrowable().getType(),
        is(SecurityException.class.getName()));
    assertThat(
        "Exception message should match",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Access denied by BEFORE callback"));

    // 5. Verify exception was logged
    assertTrue(
        "BEFORE callback should have logged exception",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_BEFORE_THROW_EXCEPTION: setting SecurityException via ctx"));

    logger.info("===== testBeforeThrowsException [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AFTER callback can throw an exception.
   *
   * <p>Registers an AFTER intercept that throws SecurityException.
   */
  @Test
  public void testAfterThrowsException() throws Exception {
    logger.info("===== testAfterThrowsException [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept that throws exception
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "onAfterThrowException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke multiplyBy - method executes but AFTER throws exception
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);

    // 4. Verify exception was raised
    assertThat(
        "Exception should have been raised", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be SecurityException",
        response.getRaisedThrowable().getThrowable().getType(),
        is(SecurityException.class.getName()));
    assertThat(
        "Exception message should match",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Access denied by AFTER callback"));

    // 5. Verify exception was logged
    assertTrue(
        "AFTER callback should have logged exception",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_THROW_EXCEPTION: setting SecurityException via ctx"));

    logger.info("===== testAfterThrowsException [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AROUND callback can throw an exception.
   *
   * <p>Registers an AROUND intercept that throws SecurityException before proceeding.
   */
  @Test
  public void testAroundThrowsException() throws Exception {
    logger.info("===== testAroundThrowsException [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that throws exception
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND, "multiplyBy", "java.lang.Integer", "onAroundThrowException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke multiplyBy - callback should throw exception before proceeding
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);

    // 4. Verify exception was raised
    assertThat(
        "Exception should have been raised", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be SecurityException",
        response.getRaisedThrowable().getThrowable().getType(),
        is(SecurityException.class.getName()));
    assertThat(
        "Exception message should match",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Access denied by AROUND callback"));

    // 5. Verify exception was logged
    assertTrue(
        "AROUND callback should have logged exception",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_THROW_EXCEPTION: setting SecurityException via ctx"));

    logger.info("===== testAroundThrowsException [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Illegal Operation Tests (BEFORE)
  // ===========================================================================

  /** Tests that getReturnValue() throws UnsupportedOperationException in BEFORE callback. */
  @Test
  public void testBeforeGetReturnValueThrowsUnsupported() throws Exception {
    logger.info("===== testBeforeGetReturnValueThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that attempts getReturnValue()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE,
            "multiplyBy",
            "java.lang.Integer",
            "onBeforeAttemptGetReturnValue");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "BEFORE callback should have caught UnsupportedOperationException for getReturnValue()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_BEFORE_ILLEGAL_GET_RETURN: correctly threw UnsupportedOperationException"));

    logger.info("===== testBeforeGetReturnValueThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that setReturnValue() throws UnsupportedOperationException in BEFORE callback. */
  @Test
  public void testBeforeSetReturnValueThrowsUnsupported() throws Exception {
    logger.info("===== testBeforeSetReturnValueThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that attempts setReturnValue()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE,
            "multiplyBy",
            "java.lang.Integer",
            "onBeforeAttemptSetReturnValue");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "BEFORE callback should have caught UnsupportedOperationException for setReturnValue()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_BEFORE_ILLEGAL_SET_RETURN: correctly threw UnsupportedOperationException"));

    logger.info("===== testBeforeSetReturnValueThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that proceed() throws UnsupportedOperationException in BEFORE callback. */
  @Test
  public void testBeforeProceedThrowsUnsupported() throws Exception {
    logger.info("===== testBeforeProceedThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that attempts proceed()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE, "multiplyBy", "java.lang.Integer", "onBeforeAttemptProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "BEFORE callback should have caught UnsupportedOperationException for proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_BEFORE_ILLEGAL_PROCEED: correctly threw UnsupportedOperationException"));

    logger.info("===== testBeforeProceedThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that getThrownException() throws UnsupportedOperationException in BEFORE callback. */
  @Test
  public void testBeforeGetThrownExceptionThrowsUnsupported() throws Exception {
    logger.info(
        "===== testBeforeGetThrownExceptionThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that attempts getThrownException()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.BEFORE,
            "multiplyBy",
            "java.lang.Integer",
            "onBeforeAttemptGetThrownException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "BEFORE callback should have caught UnsupportedOperationException for getThrownException()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_BEFORE_ILLEGAL_GET_THROWN: correctly threw UnsupportedOperationException"));

    logger.info(
        "===== testBeforeGetThrownExceptionThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Illegal Operation Tests (AFTER)
  // ===========================================================================

  /** Tests that setArg() throws UnsupportedOperationException in AFTER callback. */
  @Test
  public void testAfterSetArgThrowsUnsupported() throws Exception {
    logger.info("===== testAfterSetArgThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept that attempts setArg()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "onAfterAttemptSetArg");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "AFTER callback should have caught UnsupportedOperationException for setArg()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_ILLEGAL_SET_ARG: correctly threw UnsupportedOperationException"));

    logger.info("===== testAfterSetArgThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that proceed() throws UnsupportedOperationException in AFTER callback. */
  @Test
  public void testAfterProceedThrowsUnsupported() throws Exception {
    logger.info("===== testAfterProceedThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept that attempts proceed()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "onAfterAttemptProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "AFTER callback should have caught UnsupportedOperationException for proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_ILLEGAL_PROCEED: correctly threw UnsupportedOperationException"));

    logger.info("===== testAfterProceedThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Illegal Operation Tests (AROUND)
  // ===========================================================================

  /**
   * Tests that getReturnValue() throws IllegalStateException before proceed() in AROUND callback.
   */
  @Test
  public void testAroundGetReturnValueBeforeProceedThrowsIllegalState() throws Exception {
    logger.info(
        "===== testAroundGetReturnValueBeforeProceedThrowsIllegalState [{}]: TEST STARTED =====",
        path);

    // 1. Register AROUND intercept that attempts getReturnValue() before proceed()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyBy",
            "java.lang.Integer",
            "onAroundAttemptGetReturnBeforeProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught IllegalStateException
    assertTrue(
        "AROUND callback should have caught IllegalStateException for getReturnValue() before proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_ILLEGAL_GET_RETURN_BEFORE_PROCEED: correctly threw IllegalStateException"));

    logger.info(
        "===== testAroundGetReturnValueBeforeProceedThrowsIllegalState [{}]: TEST COMPLETED =====",
        path);
  }

  /**
   * Tests that getThrownException() throws IllegalStateException before proceed() in AROUND
   * callback.
   */
  @Test
  public void testAroundGetThrownExceptionBeforeProceedThrowsIllegalState() throws Exception {
    logger.info(
        "===== testAroundGetThrownExceptionBeforeProceedThrowsIllegalState [{}]: TEST STARTED =====",
        path);

    // 1. Register AROUND intercept that attempts getThrownException() before proceed()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyBy",
            "java.lang.Integer",
            "onAroundAttemptGetThrownBeforeProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught IllegalStateException
    assertTrue(
        "AROUND callback should have caught IllegalStateException for getThrownException() before proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_ILLEGAL_GET_THROWN_BEFORE_PROCEED: correctly threw IllegalStateException"));

    logger.info(
        "===== testAroundGetThrownExceptionBeforeProceedThrowsIllegalState [{}]: TEST COMPLETED =====",
        path);
  }

  /** Tests that setArg() throws IllegalStateException after proceed() in AROUND callback. */
  @Test
  public void testAroundSetArgAfterProceedThrowsIllegalState() throws Exception {
    logger.info(
        "===== testAroundSetArgAfterProceedThrowsIllegalState [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that attempts setArg() after proceed()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyBy",
            "java.lang.Integer",
            "onAroundAttemptSetArgAfterProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught IllegalStateException
    assertTrue(
        "AROUND callback should have caught IllegalStateException for setArg() after proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_ILLEGAL_SET_ARG_AFTER_PROCEED: correctly threw IllegalStateException"));

    logger.info(
        "===== testAroundSetArgAfterProceedThrowsIllegalState [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that skipProceed() without setReturnValue() throws IllegalStateException. */
  @Test
  public void testAroundSkipWithoutReturnValueThrowsIllegalState() throws Exception {
    logger.info(
        "===== testAroundSkipWithoutReturnValueThrowsIllegalState [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that attempts skipProceed without setReturnValue
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AROUND,
            "multiplyStaticBy",
            "java.lang.Integer",
            "onAroundSkipWithoutReturnValue");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke the method
    ExecMessage response;
    if (path == InvocationPath.HOT_PATH) {
      ObjectRef appInstance =
          ObjectRef.from(
              invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "callMultiplyStaticBy",
                  appInstance,
                  new String[] {"java.lang.Integer"},
                  new Object[] {5}));
    } else {
      response =
          invoke(
              messageBuilder.buildClassMethod(
                  myPeerUuid,
                  TARGET_CLASS,
                  "multiplyStaticBy",
                  new String[] {"java.lang.Integer"},
                  null,
                  null,
                  new Object[] {5}));
    }

    // 3. Verify IllegalStateException was raised
    assertThat(
        "Exception should have been raised", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be IllegalStateException",
        response.getRaisedThrowable().getThrowable().getType(),
        is(IllegalStateException.class.getName()));
    assertThat(
        "Exception message should mention setReturnValue",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("setReturnValue"));

    // 4. Verify the callback logged the attempt
    assertTrue(
        "AROUND callback should have logged skip without return value attempt",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_SKIP_WITHOUT_RETURN: attempting skipProceed without setReturnValue"));

    logger.info(
        "===== testAroundSkipWithoutReturnValueThrowsIllegalState [{}]: TEST COMPLETED =====",
        path);
  }

  // ===========================================================================
  // Void Method Tests
  // ===========================================================================

  /** Tests that setReturnValue() on void method throws IllegalStateException in AFTER callback. */
  @Test
  public void testAfterVoidMethodSetReturnThrowsIllegalState() throws Exception {
    logger.info(
        "===== testAfterVoidMethodSetReturnThrowsIllegalState [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept on void method multiplyBy
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalMethodIntercept(
            InterceptType.AFTER, "multiplyBy", "java.lang.Integer", "onAfterVoidMethodSetReturn");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke void method
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());
    ExecMessage response = invokeMultiplyByOnce(appInstance, 3);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify isVoid() returned true and setReturnValue threw IllegalStateException
    assertTrue(
        "AFTER callback should have detected isVoid=true",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER_VOID_CHECK: isVoid=true"));
    assertTrue(
        "AFTER callback should have caught IllegalStateException for setReturnValue on void",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_VOID_SET_RETURN: correctly threw IllegalStateException"));

    logger.info(
        "===== testAfterVoidMethodSetReturnThrowsIllegalState [{}]: TEST COMPLETED =====", path);
  }
}
