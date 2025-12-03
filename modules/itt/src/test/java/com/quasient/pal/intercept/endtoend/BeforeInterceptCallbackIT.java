/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.endtoend;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import com.quasient.pal.apps.callbacks.BeforeCallbackHandlers;
import com.quasient.pal.apps.quantized.intercept.StringMethods;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for BEFORE intercept callbacks with argument mutation.
 *
 * <p>These tests verify the end-to-end callback mechanism for BEFORE intercepts, including argument
 * mutation via static callback methods invoked using reflection.
 *
 * <p>Tests use the shared intercept peer with StringMethods application class and
 * BeforeCallbackHandlers callback handlers (both in itt-apps module).
 */
public class BeforeInterceptCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests single-argument mutation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept on StringMethods.echo() that converts the argument to
   * uppercase. Verifies that the method receives the mutated argument by checking the return value.
   */
  @Test
  public void testSingleArgumentMutation() throws Exception {
    logger.info("===== testSingleArgumentMutation: TEST STARTED =====");

    final String callbackClass = BeforeCallbackHandlers.class.getName();
    final String callbackMethod = "uppercaseFirstArg";
    final String inputValue = "hello";
    final String expectedValue = "HELLO";

    // 1. Register a BEFORE intercept on echo method
    logger.info("Creating BEFORE intercept request for echo method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Callback to interceptor peer
            InterceptType.BEFORE,
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
        "Invoking callEcho(\"{}\") which should receive mutated arg \"{}\"",
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

    // 4. Verify the return value is uppercase (proving arg was mutated before method execution)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be uppercase (argument was mutated by BEFORE callback)",
        returnValue,
        is(expectedValue));

    logger.info("===== testSingleArgumentMutation: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multi-argument mutation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept on StringMethods.concatenate() that converts both arguments to
   * uppercase. Verifies that both arguments were mutated by checking the concatenated result.
   */
  @Test
  public void testMultiArgumentMutation() throws Exception {
    logger.info("===== testMultiArgumentMutation: TEST STARTED =====");

    final String callbackClass = BeforeCallbackHandlers.class.getName();
    final String callbackMethod = "uppercaseBothArgs";
    final String inputA = "hello";
    final String inputB = "world";
    final String expectedResult = "HELLOWORLD";

    // 1. Register a BEFORE intercept on concatenate method
    logger.info("Creating BEFORE intercept request for concatenate method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "concatenate", Arrays.asList("java.lang.String", "java.lang.String")));

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

    // 3. Invoke callConcatenate with lowercase inputs (wrapper will call concatenate internally)
    logger.info(
        "Invoking callConcatenate(\"{}\", \"{}\") which should receive mutated args",
        inputA,
        inputB);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callConcatenate",
                stringMethodsInstance,
                new String[] {"java.lang.String", "java.lang.String"},
                new Object[] {inputA, inputB}));
    logger.info("callConcatenate invocation completed");

    // 4. Verify the return value is all uppercase
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be all uppercase (both arguments were mutated)",
        returnValue,
        is(expectedResult));

    logger.info("===== testMultiArgumentMutation: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests primitive argument mutation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept on StringMethods.multiply() that doubles the first integer
   * argument. Verifies the mutation by checking the multiplication result.
   */
  @Test
  public void testPrimitiveArgumentMutation() throws Exception {
    logger.info("===== testPrimitiveArgumentMutation: TEST STARTED =====");

    final String callbackClass = BeforeCallbackHandlers.class.getName();
    final String callbackMethod = "doubleFirstIntArg";
    final int inputValue = 5;
    final int factor = 3;
    final int expectedResult = 30; // (5 * 2) * 3 = 30

    // 1. Register a BEFORE intercept on multiply method
    logger.info("Creating BEFORE intercept request for multiply method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("multiply", Arrays.asList("int", "int")));

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
        "Invoking callMultiply({}, {}) which should receive mutated first arg (doubled)",
        inputValue,
        factor);
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

    // 4. Verify the return value reflects the doubled first argument
    int returnValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be 30 (first arg was doubled by callback: (5*2)*3=30)",
        returnValue,
        is(expectedResult));

    logger.info("===== testPrimitiveArgumentMutation: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests exception propagation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept that throws a SecurityException. Verifies that the exception is
   * propagated back to the caller and the method is never executed.
   */
  @Test
  public void testCallbackThrowsException() throws Exception {
    logger.info("===== testCallbackThrowsException: TEST STARTED =====");

    final String callbackClass = BeforeCallbackHandlers.class.getName();
    final String callbackMethod = "throwException";

    // 1. Register a BEFORE intercept on echo method that throws exception
    logger.info("Creating BEFORE intercept request for echo method with throwing callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
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

    // 3. Invoke callEcho - should throw SecurityException from callback (wrapper calls echo
    // internally)
    logger.info("Invoking callEcho which should throw SecurityException from callback");
    try {
      ExecMessage response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  StringMethods.class.getName(),
                  "callEcho",
                  stringMethodsInstance,
                  new String[] {"java.lang.String"},
                  new Object[] {"test"}));

      // Check if response contains a raised exception
      if (response.getRaisedThrowable() != null) {
        String exceptionClass = response.getRaisedThrowable().getThrowable().getType();
        String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();

        logger.info("Received exception: {} with message: {}", exceptionClass, exceptionMessage);

        assertThat(
            "Exception should be SecurityException",
            exceptionClass,
            is("java.lang.SecurityException"));
        assertThat(
            "Exception message should mention callback",
            exceptionMessage,
            containsString("Access denied by intercept callback"));

        logger.info("===== testCallbackThrowsException: TEST COMPLETED SUCCESSFULLY =====");
      } else {
        fail("Expected SecurityException to be thrown by callback, but no exception was raised");
      }
    } catch (Exception e) {
      logger.error("Unexpected exception during test", e);
      throw e;
    }
  }

  /**
   * Tests no-op callback behavior.
   *
   * <p>Registers a BEFORE intercept with a no-op callback that doesn't mutate arguments. Verifies
   * that the method receives the original arguments unchanged.
   */
  @Test
  public void testNoOpCallback() throws Exception {
    logger.info("===== testNoOpCallback: TEST STARTED =====");

    final String callbackClass = BeforeCallbackHandlers.class.getName();
    final String callbackMethod = "noOp";
    final String inputValue = "hello";

    // 1. Register a BEFORE intercept on echo method with no-op callback
    logger.info("Creating BEFORE intercept request for echo method with no-op callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
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
        "Return value should be unchanged (no-op callback doesn't mutate)",
        returnValue,
        is(inputValue));

    logger.info("===== testNoOpCallback: TEST COMPLETED SUCCESSFULLY =====");
  }
}
