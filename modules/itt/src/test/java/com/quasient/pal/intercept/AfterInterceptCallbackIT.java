/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import com.quasient.pal.InterceptTestSuite;
import com.quasient.pal.apps.callbacks.AfterCallbackHandlers;
import com.quasient.pal.apps.quantized.intercept.StringMethods;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for AFTER intercept callbacks with return value override.
 *
 * <p>These tests verify the end-to-end callback mechanism for AFTER intercepts, including return
 * value override via static callback methods invoked using reflection.
 *
 * <p>Tests use the shared intercept peer with StringMethods application class and
 * AfterCallbackHandlers callback handlers (both in itt-apps module).
 */
public class AfterInterceptCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests simple string return value override via AFTER callback.
   *
   * <p>Registers an AFTER intercept on StringMethods.echo() that converts the return value to
   * uppercase. Verifies that the caller receives the overridden return value.
   */
  @Test
  public void testSimpleReturnValueOverride() throws Exception {
    logger.info("===== testSimpleReturnValueOverride: TEST STARTED =====");

    final String callbackClass = AfterCallbackHandlers.class.getName();
    final String callbackMethod = "uppercaseReturnValue";
    final String inputValue = "hello";
    final String expectedValue = "HELLO";

    // 1. Register an AFTER intercept on echo method
    logger.info("Creating AFTER intercept request for echo method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID, // Callback to interceptor peer
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke callEcho with lowercase input (wrapper will call echo internally)
    logger.info(
        "Invoking callEcho(\"{}\") which should return overridden value \"{}\"",
        inputValue,
        expectedValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));
    logger.info("callEcho invocation completed");

    // 4. Verify the return value is uppercase (proving return value was overridden by AFTER
    // callback)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be uppercase (overridden by AFTER callback)",
        returnValue,
        is(expectedValue));

    logger.info("===== testSimpleReturnValueOverride: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests primitive return value override via AFTER callback.
   *
   * <p>Registers an AFTER intercept on StringMethods.multiply() that doubles the return value.
   * Verifies the override by checking the multiplication result.
   */
  @Test
  public void testPrimitiveReturnValueOverride() throws Exception {
    logger.info("===== testPrimitiveReturnValueOverride: TEST STARTED =====");

    final String callbackClass = AfterCallbackHandlers.class.getName();
    final String callbackMethod = "doubleReturnValue";
    final int inputValue = 5;
    final int factor = 3;
    final int expectedResult = 30; // (5 * 3) * 2 = 30

    // 1. Register an AFTER intercept on multiply method
    logger.info("Creating AFTER intercept request for multiply method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("multiply", java.util.Arrays.asList("int", "int")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke callMultiply with value=5, factor=3 (wrapper will call multiply internally)
    logger.info(
        "Invoking callMultiply({}, {}) which should return doubled result", inputValue, factor);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callMultiply",
                stringMethodsInstance,
                new String[] {"int", "int"},
                new Object[] {inputValue, factor}));
    logger.info("callMultiply invocation completed");

    // 4. Verify the return value reflects the doubled result
    int returnValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be 30 (result was doubled by callback: (5*3)*2=30)",
        returnValue,
        is(expectedResult));

    logger.info("===== testPrimitiveReturnValueOverride: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests AFTER intercept on void method verifies isVoid() correctly.
   *
   * <p>Registers an AFTER intercept on StringMethods.printMessage() (void method) with a callback
   * that checks isVoid() returns true. The callback throws AssertionError if isVoid() is false.
   */
  @Test
  public void testVoidMethodIsVoidCheck() throws Exception {
    logger.info("===== testVoidMethodIsVoidCheck: TEST STARTED =====");

    final String callbackClass = AfterCallbackHandlers.class.getName();
    final String callbackMethod = "checkIsVoid";
    final String inputValue = "test message";

    // 1. Register an AFTER intercept on printMessage method
    logger.info("Creating AFTER intercept request for printMessage method (void)");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "printMessage", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke callPrintMessage (wrapper will call printMessage internally)
    logger.info("Invoking callPrintMessage(\"{}\") (void method)", inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callPrintMessage",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));
    logger.info("callPrintMessage invocation completed");

    // 4. Verify the method completed successfully
    // If the callback threw AssertionError, the response would contain it
    if (response.getRaisedThrowable() != null) {
      String exceptionClass = response.getRaisedThrowable().getThrowable().getType();
      throw new AssertionError(
          "Callback threw exception: "
              + exceptionClass
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    logger.info("===== testVoidMethodIsVoidCheck: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that attempting to set return value on void method throws IllegalStateException.
   *
   * <p>Registers an AFTER intercept on StringMethods.printMessage() (void method) with a callback
   * that attempts to call setReturnValue(). Verifies that this throws IllegalStateException.
   */
  @Test
  public void testVoidMethodCannotSetReturnValue() throws Exception {
    logger.info("===== testVoidMethodCannotSetReturnValue: TEST STARTED =====");

    final String callbackClass = AfterCallbackHandlers.class.getName();
    final String callbackMethod = "attemptSetReturnValueOnVoid";
    final String inputValue = "test message";

    // 1. Register an AFTER intercept on printMessage method
    logger.info("Creating AFTER intercept request for printMessage method (void)");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "printMessage", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke callPrintMessage - callback should throw IllegalStateException
    logger.info(
        "Invoking callPrintMessage(\"{}\") - callback should throw IllegalStateException",
        inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callPrintMessage",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));
    logger.info("callPrintMessage invocation completed");

    // 4. Verify that IllegalStateException was thrown
    if (response.getRaisedThrowable() != null) {
      String exceptionClass = response.getRaisedThrowable().getThrowable().getType();
      String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();

      logger.info("Received exception: {} with message: {}", exceptionClass, exceptionMessage);

      assertThat(
          "Exception should be IllegalStateException",
          exceptionClass,
          is("java.lang.IllegalStateException"));
      assertThat(
          "Exception message should mention void method", exceptionMessage, containsString("void"));

      logger.info("===== testVoidMethodCannotSetReturnValue: TEST COMPLETED SUCCESSFULLY =====");
    } else {
      throw new AssertionError(
          "Expected IllegalStateException when attempting to set return value on void method, but no exception was raised");
    }
  }

  /**
   * Tests exception propagation via AFTER callback.
   *
   * <p>Registers an AFTER intercept that throws a SecurityException. Verifies that the exception is
   * propagated back to the caller.
   */
  @Test
  public void testCallbackThrowsException() throws Exception {
    logger.info("===== testCallbackThrowsException: TEST STARTED =====");

    final String callbackClass = AfterCallbackHandlers.class.getName();
    final String callbackMethod = "throwException";

    // 1. Register an AFTER intercept on echo method that throws exception
    logger.info("Creating AFTER intercept request for echo method with throwing callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke callEcho - should throw SecurityException from AFTER callback (wrapper calls echo
    // internally)
    logger.info("Invoking callEcho which should throw SecurityException from AFTER callback");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {"test"}));

    // 4. Check if response contains a raised exception
    if (response.getRaisedThrowable() != null) {
      String exceptionClass = response.getRaisedThrowable().getThrowable().getType();
      String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();

      logger.info("Received exception: {} with message: {}", exceptionClass, exceptionMessage);

      assertThat(
          "Exception should be SecurityException",
          exceptionClass,
          is("java.lang.SecurityException"));
      assertThat(
          "Exception message should mention AFTER callback",
          exceptionMessage,
          containsString("Access denied by AFTER intercept callback"));

      logger.info("===== testCallbackThrowsException: TEST COMPLETED SUCCESSFULLY =====");
    } else {
      throw new AssertionError(
          "Expected SecurityException to be thrown by callback, but no exception was raised");
    }
  }

  /**
   * Tests no-op callback behavior for AFTER intercepts.
   *
   * <p>Registers an AFTER intercept with a no-op callback that doesn't override the return value.
   * Verifies that the method returns the original value unchanged.
   */
  @Test
  public void testNoOpCallback() throws Exception {
    logger.info("===== testNoOpCallback: TEST STARTED =====");

    final String callbackClass = AfterCallbackHandlers.class.getName();
    final String callbackMethod = "noOp";
    final String inputValue = "hello";

    // 1. Register an AFTER intercept on echo method with no-op callback
    logger.info("Creating AFTER intercept request for echo method with no-op callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke callEcho with input value (wrapper calls echo internally)
    logger.info("Invoking callEcho(\"{}\") with no-op callback", inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));
    logger.info("callEcho invocation completed");

    // 4. Verify the return value is unchanged
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (no-op callback doesn't override)",
        returnValue,
        is(inputValue));

    logger.info("===== testNoOpCallback: TEST COMPLETED SUCCESSFULLY =====");
  }
}
