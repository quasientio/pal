/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.endtoend.method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.foobar.apps.callbacks.method.MethodHandlers;
import io.quasient.foobar.apps.quantized.intercept.StringMethods;
import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptApiMisuseException;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for AFTER method intercept callbacks with return value override.
 *
 * <p>These tests verify the end-to-end callback mechanism for AFTER method intercepts, including
 * return value override via static callback methods invoked using reflection.
 *
 * <p>Tests use the shared intercept peer with StringMethods application class and MethodHandlers
 * callback handlers (both in itt-apps module).
 *
 * <p><b>Parameterized:</b> Each test runs through both invocation paths (HOT_PATH and
 * INCOMING_RPC).
 */
@RunWith(Parameterized.class)
public class AfterMethodCallbackIT extends AbstractInterceptIT {

  /** Method invocation descriptors. */
  private static final MethodInvocation ECHO = new MethodInvocation("callEcho", "echo");

  private static final MethodInvocation MULTIPLY = new MethodInvocation("callMultiply", "multiply");
  private static final MethodInvocation PRINT_MESSAGE =
      new MethodInvocation("callPrintMessage", "printMessage");

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** The invocation path for this parameterized test run. */
  private final InvocationPath invocationPath;

  public AfterMethodCallbackIT(InvocationPath invocationPath) {
    this.invocationPath = invocationPath;
  }

  @Parameterized.Parameters(name = "{index}: path={0}")
  public static Collection<Object[]> data() {
    return invocationPathParameters();
  }

  /**
   * Tests simple string return value override via AFTER callback.
   *
   * <p>Registers an AFTER intercept on StringMethods.echo() that converts the return value to
   * uppercase. Verifies that the caller receives the overridden return value.
   */
  @Test
  public void testSimpleReturnValueOverride() throws Exception {
    logger.info(
        "===== testSimpleReturnValueOverride [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "uppercaseReturnValue";
    final String inputValue = "hello";
    final String expectedValue = "HELLO";

    // 1. Register an AFTER intercept on echo method
    logger.info("Creating AFTER intercept request for echo method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Callback to interceptor peer
            InterceptType.AFTER,
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

    // 3. Invoke method via the parameterized path
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    // 4. Verify the return value is uppercase
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());

    assertThat(
        "Return value should be uppercase (overridden by AFTER callback)",
        returnValue,
        is(expectedValue));

    assertTrue(
        "Expected uppercaseReturnValue callback to log override",
        InterceptEndToEndTestSuite.waitForAppLogLine("uppercaseReturnValue: hello -> HELLO"));

    logger.info(
        "===== testSimpleReturnValueOverride [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests primitive return value override via AFTER callback.
   *
   * <p>Registers an AFTER intercept on StringMethods.multiply() that doubles the return value.
   * Verifies the override by checking the multiplication result.
   */
  @Test
  public void testPrimitiveReturnValueOverride() throws Exception {
    logger.info(
        "===== testPrimitiveReturnValueOverride [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "doubleReturnValue";
    final int inputValue = 5;
    final int factor = 3;
    final int expectedResult = 30; // (5 * 3) * 2 = 30

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("multiply", Arrays.asList("int", "int")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            MULTIPLY,
            stringMethodsInstance,
            new String[] {"int", "int"},
            new Object[] {inputValue, factor});

    int returnValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());

    assertThat(
        "Return value should be 30 (result was doubled by callback: (5*3)*2=30)",
        returnValue,
        is(expectedResult));

    assertTrue(
        "Expected doubleReturnValue callback to log override",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleReturnValue: 15 -> 30"));

    logger.info(
        "===== testPrimitiveReturnValueOverride [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests AFTER intercept on void method verifies isVoid() correctly.
   *
   * <p>Registers an AFTER intercept on StringMethods.printMessage() (void method) with a callback
   * that checks isVoid() returns true. The callback throws AssertionError if isVoid() is false.
   */
  @Test
  public void testVoidMethodIsVoidCheck() throws Exception {
    logger.info(
        "===== testVoidMethodIsVoidCheck [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "checkIsVoid";
    final String inputValue = "test message";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "printMessage", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            PRINT_MESSAGE,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    if (response.getRaisedThrowable() != null) {
      String exceptionClass = response.getRaisedThrowable().getThrowable().getType();
      throw new AssertionError(
          "Callback threw exception: "
              + exceptionClass
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    assertTrue(
        "Expected checkIsVoid callback to log confirmation",
        InterceptEndToEndTestSuite.waitForAppLogLine("checkIsVoid: confirmed method is void"));

    logger.info(
        "===== testVoidMethodIsVoidCheck [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that attempting to set return value on void method propagates
   * InterceptApiMisuseException.
   *
   * <p>Registers an AFTER intercept on StringMethods.printMessage() (void method) with a callback
   * that attempts to call setReturnValue(). Verifies that the InterceptApiMisuseException is
   * propagated to the caller, allowing developers to detect callback bugs.
   */
  @Test
  public void testVoidMethodCannotSetReturnValue() throws Exception {
    logger.info(
        "===== testVoidMethodCannotSetReturnValue [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptSetReturnValueOnVoid";
    final String inputValue = "test message";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "printMessage", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            PRINT_MESSAGE,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    // API misuse exceptions (InterceptApiMisuseException) are now propagated to the caller.
    // The callback attempts setReturnValue() on a void method, which throws
    // InterceptApiMisuseException. This exception bypasses all exception policies and is
    // propagated to the caller, ensuring developers are aware of callback bugs.

    assertThat(
        "API misuse exception should be propagated",
        response.getRaisedThrowable(),
        is(notNullValue()));

    // Verify exception type is InterceptApiMisuseException
    String exceptionType = response.getRaisedThrowable().getThrowable().getType();
    String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();
    String expectedTypeName = InterceptApiMisuseException.class.getName();
    boolean isCorrectException =
        exceptionType.equals(expectedTypeName) || exceptionMessage.contains(expectedTypeName);
    assertTrue(
        "Exception should be or contain InterceptApiMisuseException, but got type="
            + exceptionType
            + ", message="
            + exceptionMessage,
        isCorrectException);

    // Verify the callback logged its attempt to set return value (before the exception was thrown)
    assertTrue(
        "Expected attemptSetReturnValueOnVoid callback to log attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptSetReturnValueOnVoid: attempting to set return value on void method"));

    logger.info(
        "===== testVoidMethodCannotSetReturnValue [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests exception propagation via AFTER callback.
   *
   * <p>Registers an AFTER intercept that throws a SecurityException. Verifies that the exception is
   * propagated back to the caller.
   */
  @Test
  public void testCallbackThrowsException() throws Exception {
    logger.info(
        "===== testCallbackThrowsException [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "throwExceptionAfter";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"test"});

    if (response.getRaisedThrowable() != null) {
      String exceptionClass = response.getRaisedThrowable().getThrowable().getType();
      String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();

      assertThat(
          "Exception should be SecurityException",
          exceptionClass,
          is("java.lang.SecurityException"));
      assertThat(
          "Exception message should mention AFTER callback",
          exceptionMessage,
          containsString("Access denied by AFTER intercept callback"));

      assertTrue(
          "Expected throwException callback to log",
          InterceptEndToEndTestSuite.waitForAppLogLine(
              "throwExceptionAfter: throwing SecurityException"));

      logger.info(
          "===== testCallbackThrowsException [{}]: TEST COMPLETED SUCCESSFULLY =====",
          invocationPath.getDescription());
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
    logger.info("===== testNoOpCallback [{}]: TEST STARTED =====", invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "noOpAfter";
    final String inputValue = "hello";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());

    assertThat(
        "Return value should be unchanged (no-op callback doesn't override)",
        returnValue,
        is(inputValue));

    assertTrue(
        "Expected noOp callback to log no override",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOpAfter: no return value override"));

    logger.info(
        "===== testNoOpCallback [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  // ========================================================================
  // Phase/Type Restriction Tests - AFTER intercepts
  // ========================================================================

  /** Tests that setArg() throws InterceptApiMisuseException in AFTER intercept. */
  @Test
  public void testSetArgThrowsInAfter() throws Exception {
    logger.info(
        "===== testSetArgThrowsInAfter [{}]: TEST STARTED =====", invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptSetArgInAfter";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    if (response.getRaisedThrowable() != null) {
      fail(
          "Callback failed: "
              + response.getRaisedThrowable().getThrowable().getType()
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    assertTrue(
        "Expected callback to log InterceptApiMisuseException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptSetArgInAfter: correctly threw InterceptApiMisuseException"));

    logger.info(
        "===== testSetArgThrowsInAfter [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /** Tests that proceed() throws InterceptApiMisuseException in AFTER intercept. */
  @Test
  public void testProceedThrowsInAfter() throws Exception {
    logger.info(
        "===== testProceedThrowsInAfter [{}]: TEST STARTED =====", invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptProceedInAfter";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    if (response.getRaisedThrowable() != null) {
      fail(
          "Callback failed: "
              + response.getRaisedThrowable().getThrowable().getType()
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    assertTrue(
        "Expected callback to log InterceptApiMisuseException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptProceedInAfter: correctly threw InterceptApiMisuseException"));

    logger.info(
        "===== testProceedThrowsInAfter [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
