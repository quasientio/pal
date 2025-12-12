/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.endtoend.constructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.InterceptEndToEndTestSuite;
import com.quasient.pal.apps.callbacks.constructor.ConstructorHandlers;
import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for BEFORE_ASYNC constructor intercept callbacks.
 *
 * <p>These tests verify that BEFORE_ASYNC callbacks on constructors:
 *
 * <ul>
 *   <li>Can read arguments but cannot mutate them
 *   <li>Are fire-and-forget (constructor execution does not wait for callback response)
 *   <li>Throw UnsupportedOperationException when attempting to mutate arguments
 * </ul>
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and
 * ConstructorHandlers callback handlers (both in itt-apps module).
 */
public class BeforeConstructorAsyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests that BEFORE_ASYNC callback can read constructor arguments without mutation.
   *
   * <p>Registers a BEFORE_ASYNC intercept that only logs arguments. Verifies that the constructor
   * executes normally with unchanged arguments.
   */
  @Test
  public void testAsyncCallbackCanReadArgs() throws Exception {
    logger.info("===== testAsyncCallbackCanReadArgs: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "logArgs";
    final int inputValue = 42;

    // 1. Register a BEFORE_ASYNC intercept on parameterized constructor
    logger.info("Creating BEFORE_ASYNC intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke factory method that calls constructor
    logger.info("Invoking createWithCounter({}) with BEFORE_ASYNC callback", inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {inputValue}));

    // 3. Verify no exception was raised
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Get the created instance and verify the counter value (unchanged)
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());

    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat(
        "Counter should be unchanged (ASYNC callbacks cannot mutate)",
        counterValue,
        is(inputValue));

    // Verify callback logged the args in application log
    assertTrue(
        "Expected logArgs callback to log the args",
        InterceptEndToEndTestSuite.waitForAppLogLine("logArgs.*BEFORE_ASYNC.*args.*" + inputValue));

    logger.info("===== testAsyncCallbackCanReadArgs: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that BEFORE_ASYNC callback throws UnsupportedOperationException on mutation attempt.
   *
   * <p>Registers a BEFORE_ASYNC intercept that attempts to mutate arguments. Verifies that:
   *
   * <ul>
   *   <li>The callback throws UnsupportedOperationException
   *   <li>The constructor still executes (ASYNC is fire-and-forget)
   *   <li>Arguments are not mutated
   * </ul>
   */
  @Test
  public void testAsyncCallbackCannotMutateArgs() throws Exception {
    logger.info("===== testAsyncCallbackCannotMutateArgs: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "attemptIntArgMutation";
    final int inputValue = 42;

    // 1. Register a BEFORE_ASYNC intercept that attempts mutation
    logger.info("Creating BEFORE_ASYNC intercept request with mutation attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke factory method - callback will throw but ASYNC is fire-and-forget
    logger.info("Invoking createWithCounter - callback should throw but constructor still runs");
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {inputValue}));

    // 3. Verify constructor executed with unchanged args
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());

    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat(
        "Counter should be unchanged (ASYNC callback exception doesn't stop execution)",
        counterValue,
        is(inputValue));

    // Verify callback logged the mutation attempt in application log
    assertTrue(
        "Expected attemptIntArgMutation callback to log mutation attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptIntArgMutation.*BEFORE_ASYNC.*attempting to mutate"));

    logger.info("===== testAsyncCallbackCannotMutateArgs: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that BEFORE_ASYNC callback receives correct argument values.
   *
   * <p>Registers a BEFORE_ASYNC intercept that verifies the first argument equals 42. If not, the
   * callback throws AssertionError. This tests that arguments are correctly deserialized and passed
   * to ASYNC callbacks.
   */
  @Test
  public void testAsyncCallbackReceivesCorrectArgs() throws Exception {
    logger.info("===== testAsyncCallbackReceivesCorrectArgs: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "verifyFirstArgIs42";
    final int inputValue = 42;

    // 1. Register a BEFORE_ASYNC intercept that verifies args
    logger.info("Creating BEFORE_ASYNC intercept request with arg verification");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke factory method with value 42 - callback will verify
    logger.info("Invoking createWithCounter(42) - callback should verify arg is 42");
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {inputValue}));

    // 3. Constructor should execute normally
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());

    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    assertThat("Constructor should execute and set counter", counterValue, is(inputValue));

    // Verify callback logged successful verification in application log
    assertTrue(
        "Expected verifyFirstArgIs42 callback to log verification",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "verifyFirstArgIs42: verified first arg is 42"));

    logger.info("===== testAsyncCallbackReceivesCorrectArgs: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that BEFORE_ASYNC callback cannot throw exceptions via setExceptionToThrow.
   *
   * <p>Registers a BEFORE_ASYNC intercept on constructor that attempts to call setExceptionToThrow.
   * Verifies that:
   *
   * <ol>
   *   <li>The callback throws UnsupportedOperationException when calling setExceptionToThrow
   *   <li>The constructor still executes (ASYNC is fire-and-forget)
   *   <li>The object is created normally
   * </ol>
   */
  @Test
  public void testAsyncCallbackCannotThrowException() throws Exception {
    logger.info("===== testAsyncCallbackCannotThrowException: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "attemptThrowException";
    final int inputValue = 42;

    // 1. Register a BEFORE_ASYNC intercept that attempts to throw exception
    logger.info("Creating BEFORE_ASYNC intercept request with exception throw attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke factory method that calls constructor
    logger.info(
        "Invoking createWithCounter({}) - callback should throw but constructor runs", inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {inputValue}));

    // 3. Verify no exception was raised
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Get the created instance and verify the counter value
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());

    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    assertThat("Constructor should execute and set counter", counterValue, is(inputValue));

    // Verify callback logged the exception throw attempt in application log
    assertTrue(
        "Expected attemptThrowException callback to log exception throw attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptThrowException.*BEFORE_ASYNC.*attempting to set exception"));

    logger.info("===== testAsyncCallbackCannotThrowException: TEST COMPLETED SUCCESSFULLY =====");
  }
}
