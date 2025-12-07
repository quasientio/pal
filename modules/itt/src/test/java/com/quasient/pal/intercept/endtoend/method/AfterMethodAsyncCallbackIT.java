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
 * Integration tests for AFTER_ASYNC method intercept callbacks.
 *
 * <p>These tests verify that AFTER_ASYNC callbacks:
 *
 * <ul>
 *   <li>Can read return values but cannot override them
 *   <li>Are fire-and-forget (caller receives original return value)
 *   <li>Throw UnsupportedOperationException when attempting to override return value
 * </ul>
 *
 * <p>Tests use the shared intercept peer with StringMethods application class and
 * AsyncCallbackHandlers callback handlers (both in itt-apps module).
 */
public class AfterMethodAsyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests that AFTER_ASYNC callback can read return value without override.
   *
   * <p>Registers an AFTER_ASYNC intercept that only logs the return value. Verifies that the caller
   * receives the original return value.
   */
  @Test
  public void testAsyncCallbackCanReadReturnValue() throws Exception {
    logger.info("===== testAsyncCallbackCanReadReturnValue: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "logReturnValue";
    final String inputValue = "hello";

    // 1. Register an AFTER_ASYNC intercept on echo method
    logger.info("Creating AFTER_ASYNC intercept request for echo method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke callEcho - ASYNC callback should log return value but not override it
    logger.info("Invoking callEcho(\"{}\") with AFTER_ASYNC callback", inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

    // 4. Verify the return value is unchanged (ASYNC cannot override)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (ASYNC callbacks cannot override)",
        returnValue,
        is(inputValue));

    // Verify callback logged the return value in application log
    assertTrue(
        "Expected logReturnValue callback to log the return value",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "logReturnValue.*AFTER_ASYNC.*returnValue.*" + inputValue));

    logger.info("===== testAsyncCallbackCanReadReturnValue: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that AFTER_ASYNC callback throws UnsupportedOperationException on override attempt.
   *
   * <p>Registers an AFTER_ASYNC intercept that attempts to override return value. Verifies that:
   *
   * <ul>
   *   <li>The callback throws UnsupportedOperationException
   *   <li>The caller still receives the original return value (ASYNC is fire-and-forget)
   * </ul>
   */
  @Test
  public void testAsyncCallbackCannotOverrideReturnValue() throws Exception {
    logger.info("===== testAsyncCallbackCannotOverrideReturnValue: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "attemptReturnOverride";
    final String inputValue = "hello";

    // 1. Register an AFTER_ASYNC intercept that attempts override
    logger.info("Creating AFTER_ASYNC intercept request with override attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
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

    // 3. Invoke callEcho - callback will throw but ASYNC is fire-and-forget
    logger.info("Invoking callEcho - callback should throw but caller gets original return value");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

    // 4. Verify caller receives original return value (ASYNC exception doesn't affect caller)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (ASYNC callback exception doesn't affect caller)",
        returnValue,
        is(inputValue));

    // Verify callback logged the override attempt in application log
    assertTrue(
        "Expected attemptReturnOverride callback to log override attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptReturnOverride.*AFTER_ASYNC.*attempting to override return value"));

    logger.info(
        "===== testAsyncCallbackCannotOverrideReturnValue: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that AFTER_ASYNC callback receives correct return value.
   *
   * <p>Registers an AFTER_ASYNC intercept that verifies return value equals "hello". If not, the
   * callback throws AssertionError. This tests that return values are correctly serialized and
   * passed to ASYNC callbacks.
   */
  @Test
  public void testAsyncCallbackReceivesCorrectReturnValue() throws Exception {
    logger.info("===== testAsyncCallbackReceivesCorrectReturnValue: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "verifyReturnValueIsHello";
    final String inputValue = "hello";

    // 1. Register an AFTER_ASYNC intercept that verifies return value
    logger.info("Creating AFTER_ASYNC intercept request with return value verification");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
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

    // 3. Invoke callEcho with "hello" - callback will verify return value
    logger.info("Invoking callEcho(\"hello\") - callback should verify return value is 'hello'");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

    // 4. Caller receives original return value
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Caller should receive original return value", returnValue, is(inputValue));

    // Verify callback logged successful verification in application log
    assertTrue(
        "Expected verifyReturnValueIsHello callback to log verification",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "verifyReturnValueIsHello: verified return value is 'hello'"));

    logger.info(
        "===== testAsyncCallbackReceivesCorrectReturnValue: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that AFTER_ASYNC callback cannot throw exceptions via setExceptionToThrow.
   *
   * <p>Registers an AFTER_ASYNC intercept that attempts to call setExceptionToThrow. Verifies that:
   *
   * <ol>
   *   <li>The callback throws UnsupportedOperationException when calling setExceptionToThrow
   *   <li>The caller receives the original return value (ASYNC is fire-and-forget)
   * </ol>
   */
  @Test
  public void testAsyncCallbackCannotThrowException() throws Exception {
    logger.info("===== testAsyncCallbackCannotThrowException: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "attemptThrowException";
    final String inputValue = "hello";

    // 1. Register an AFTER_ASYNC intercept that attempts to throw exception
    logger.info("Creating AFTER_ASYNC intercept request with exception throw attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
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

    // 3. Invoke callEcho - method runs, callback throws UnsupportedOperationException after
    logger.info("Invoking callEcho - callback should throw but caller should get original result");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

    // 4. Verify caller receives original return value (ASYNC exception doesn't affect caller)
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
            "attemptThrowException.*AFTER_ASYNC.*attempting to set exception"));

    logger.info("===== testAsyncCallbackCannotThrowException: TEST COMPLETED SUCCESSFULLY =====");
  }
}
