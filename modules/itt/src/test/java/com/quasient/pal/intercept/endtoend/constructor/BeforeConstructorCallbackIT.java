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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
 * Integration tests for BEFORE intercept callbacks on constructors with argument mutation.
 *
 * <p>These tests verify the end-to-end callback mechanism for BEFORE intercepts on constructors,
 * including argument mutation via static callback methods invoked using reflection.
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and
 * ConstructorHandlers callback handlers (both in itt-apps module).
 *
 * <p><b>Constructor Interception Pattern:</b> Constructors are intercepted using method name "new"
 * and the constructor parameter types as the signature.
 */
public class BeforeConstructorCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests constructor argument mutation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept on InterceptableApp(Integer) constructor that doubles the
   * argument. Verifies that the constructed object has the mutated value by checking the counter
   * field.
   */
  @Test
  public void testConstructorArgumentMutation() throws Exception {
    logger.info("===== testConstructorArgumentMutation: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "doubleFirstIntArg";
    final int inputValue = 10;
    final int expectedValue = 20; // 10 * 2 = 20

    // 1. Register a BEFORE intercept on parameterized constructor
    logger.info("Creating BEFORE intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Callback to interceptor peer
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke factory method that calls constructor (triggers intercept via call-site)
    logger.info(
        "Invoking createWithCounter({}) which should receive mutated arg {}",
        inputValue,
        expectedValue);
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
    logger.info("createWithCounter invocation completed");

    // 3. Get the created instance and verify the counter value
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());
    logger.info("InterceptableApp instance created with ref: {}", appInstance);

    // 4. Call getCounter to verify the constructor received the mutated argument
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
        "Counter should be 20 (constructor arg was doubled by callback: 10 * 2 = 20)",
        counterValue,
        is(expectedValue));

    // Verify callback logged the mutation in application log
    assertTrue(
        "Expected doubleFirstIntArg callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleFirstIntArg: 10 -> 20"));

    logger.info("===== testConstructorArgumentMutation: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests constructor exception propagation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept on InterceptableApp(Integer) constructor that throws a
   * SecurityException. Verifies that the exception is propagated back to the caller and the
   * constructor is never executed.
   */
  @Test
  public void testConstructorCallbackThrowsException() throws Exception {
    logger.info("===== testConstructorCallbackThrowsException: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "throwException";

    // 1. Register a BEFORE intercept on parameterized constructor that throws exception
    logger.info(
        "Creating BEFORE intercept request for parameterized constructor with throwing callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke factory method - should throw SecurityException from callback
    logger.info("Invoking createWithCounter which should throw SecurityException from callback");
    try {
      ExecMessage response =
          invoke(
              messageBuilder.buildClassMethod(
                  myPeerUuid,
                  InterceptableApp.class.getName(),
                  "createWithCounter",
                  new String[] {"java.lang.Integer"},
                  null,
                  null,
                  new Object[] {10}));

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
            "Exception message should mention constructor callback",
            exceptionMessage,
            containsString("Access denied by constructor intercept callback"));

        // Verify callback logged that it was throwing an exception
        assertTrue(
            "Expected throwException callback to log",
            InterceptEndToEndTestSuite.waitForAppLogLine(
                "throwException: throwing SecurityException from constructor callback"));

        logger.info(
            "===== testConstructorCallbackThrowsException: TEST COMPLETED SUCCESSFULLY =====");
      } else {
        fail("Expected SecurityException to be thrown by callback, but no exception was raised");
      }
    } catch (Exception e) {
      logger.error("Unexpected exception during test", e);
      throw e;
    }
  }

  /**
   * Tests no-op callback behavior on constructor.
   *
   * <p>Registers a BEFORE intercept with a no-op callback that doesn't mutate arguments. Verifies
   * that the constructor receives the original arguments unchanged.
   */
  @Test
  public void testConstructorNoOpCallback() throws Exception {
    logger.info("===== testConstructorNoOpCallback: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "noOp";
    final int inputValue = 42;

    // 1. Register a BEFORE intercept on parameterized constructor with no-op callback
    logger.info(
        "Creating BEFORE intercept request for parameterized constructor with no-op callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke factory method that calls constructor
    logger.info("Invoking createWithCounter({}) with no-op callback", inputValue);
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
    logger.info("createWithCounter invocation completed");

    // 3. Get the created instance and verify the counter value is unchanged
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());
    logger.info("InterceptableApp instance created with ref: {}", appInstance);

    // 4. Call getCounter to verify the constructor received the original argument
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
        "Counter should be unchanged (no-op callback doesn't mutate)",
        counterValue,
        is(inputValue));

    // Verify callback logged no mutations in application log
    assertTrue(
        "Expected noOp callback to log no mutations",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations"));

    logger.info("===== testConstructorNoOpCallback: TEST COMPLETED SUCCESSFULLY =====");
  }
}
