/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.endtoend.method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.InterceptEndToEndTestSuite;
import com.quasient.pal.apps.callbacks.AsyncCallbackHandlers;
import com.quasient.pal.apps.quantized.intercept.StringMethods;
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
 * Integration tests for BEFORE_ASYNC method intercept callbacks.
 *
 * <p>These tests verify that BEFORE_ASYNC callbacks:
 *
 * <ul>
 *   <li>Can read arguments but cannot mutate them
 *   <li>Are fire-and-forget (method execution does not wait for callback response)
 *   <li>Throw UnsupportedOperationException when attempting to mutate arguments
 * </ul>
 *
 * <p>Tests use the shared intercept peer with StringMethods application class and
 * AsyncCallbackHandlers callback handlers (both in itt-apps module).
 */
public class BeforeMethodAsyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests that BEFORE_ASYNC callback can read arguments without mutation.
   *
   * <p>Registers a BEFORE_ASYNC intercept that only logs arguments. Verifies that the method
   * executes normally with unchanged arguments.
   */
  @Test
  public void testAsyncCallbackCanReadArgs() throws Exception {
    logger.info("===== testAsyncCallbackCanReadArgs: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "logArgs";
    final String inputValue = "hello";

    // 1. Register a BEFORE_ASYNC intercept on echo method
    logger.info("Creating BEFORE_ASYNC intercept request for echo method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke callEcho - ASYNC callback should log args but not mutate them
    logger.info("Invoking callEcho(\"{}\") with BEFORE_ASYNC callback", inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

    // 4. Verify the return value is unchanged (ASYNC cannot mutate)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (ASYNC callbacks cannot mutate)",
        returnValue,
        is(inputValue));

    // 5. Verify the callback logged the args in the application log
    assertTrue(
        "Expected logArgs callback to log the args in application log",
        InterceptEndToEndTestSuite.waitForAppLogLine("logArgs.*args.*" + inputValue));

    logger.info("===== testAsyncCallbackCanReadArgs: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that BEFORE_ASYNC callback throws UnsupportedOperationException on mutation attempt.
   *
   * <p>Registers a BEFORE_ASYNC intercept that attempts to mutate arguments. Verifies that:
   *
   * <ul>
   *   <li>The callback throws UnsupportedOperationException
   *   <li>The original method still executes (ASYNC is fire-and-forget)
   *   <li>Arguments are not mutated
   * </ul>
   */
  @Test
  public void testAsyncCallbackCannotMutateArgs() throws Exception {
    logger.info("===== testAsyncCallbackCannotMutateArgs: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "attemptArgMutation";
    final String inputValue = "hello";

    // 1. Register a BEFORE_ASYNC intercept that attempts mutation
    logger.info("Creating BEFORE_ASYNC intercept request with mutation attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke callEcho - callback will throw but ASYNC is fire-and-forget so method still runs
    logger.info("Invoking callEcho - callback should throw but method should still execute");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

    // 4. Verify method executed with unchanged args (ASYNC exception doesn't stop execution)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (ASYNC callback exception doesn't stop execution)",
        returnValue,
        is(inputValue));

    // Verify callback logged the mutation attempt in application log
    assertTrue(
        "Expected attemptArgMutation callback to log mutation attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptArgMutation.*BEFORE_ASYNC.*attempting to mutate"));

    logger.info("===== testAsyncCallbackCannotMutateArgs: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that BEFORE_ASYNC callback receives correct argument values.
   *
   * <p>Registers a BEFORE_ASYNC intercept that verifies the first argument equals "hello". If not,
   * the callback throws AssertionError. This tests that arguments are correctly deserialized and
   * passed to ASYNC callbacks.
   */
  @Test
  public void testAsyncCallbackReceivesCorrectArgs() throws Exception {
    logger.info("===== testAsyncCallbackReceivesCorrectArgs: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "verifyFirstArgIsHello";
    final String inputValue = "hello";

    // 1. Register a BEFORE_ASYNC intercept that verifies args
    logger.info("Creating BEFORE_ASYNC intercept request with arg verification");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke callEcho with "hello" - callback will verify and log success
    logger.info("Invoking callEcho(\"hello\") - callback should verify arg is 'hello'");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

    // 4. Method should execute normally
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Method should execute and return input", returnValue, is(inputValue));

    // 5. Verify the callback verified and logged success in the application log
    assertTrue(
        "Expected verifyFirstArgIsHello callback to log success in application log",
        InterceptEndToEndTestSuite.waitForAppLogLine("verifyFirstArgIsHello.*verified.*hello"));

    logger.info("===== testAsyncCallbackReceivesCorrectArgs: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that BEFORE_ASYNC callback cannot throw exceptions via setExceptionToThrow.
   *
   * <p>Registers a BEFORE_ASYNC intercept that attempts to call setExceptionToThrow. Verifies that:
   *
   * <ol>
   *   <li>The callback throws UnsupportedOperationException when calling setExceptionToThrow
   *   <li>The original method still executes (ASYNC is fire-and-forget)
   *   <li>The method returns normally with unchanged result
   * </ol>
   */
  @Test
  public void testAsyncCallbackCannotThrowException() throws Exception {
    logger.info("===== testAsyncCallbackCannotThrowException: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "attemptThrowException";
    final String inputValue = "hello";

    // 1. Register a BEFORE_ASYNC intercept that attempts to throw exception
    logger.info("Creating BEFORE_ASYNC intercept request with exception throw attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke callEcho - callback will throw UnsupportedOperationException but method still runs
    logger.info("Invoking callEcho - callback should throw but method should still execute");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

    // 4. Verify method executed normally (ASYNC exception doesn't affect caller)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (ASYNC callback exception doesn't affect caller)",
        returnValue,
        is(inputValue));

    // Verify callback logged the exception throw attempt in application log
    assertTrue(
        "Expected attemptThrowException callback to log exception throw attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptThrowException.*BEFORE_ASYNC.*attempting to set exception"));

    logger.info("===== testAsyncCallbackCannotThrowException: TEST COMPLETED SUCCESSFULLY =====");
  }
}
