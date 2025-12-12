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
import static org.hamcrest.Matchers.nullValue;
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
 * Integration tests for AROUND intercept callbacks on constructors.
 *
 * <p>These tests verify the end-to-end callback mechanism for AROUND intercepts on constructors,
 * including:
 *
 * <ul>
 *   <li>Constructor argument mutation before proceed
 *   <li>Post-construction logic after proceed
 *   <li>Skip-proceed with exception
 *   <li>No-op callback behavior
 * </ul>
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and
 * ConstructorHandlers callback handlers (both in itt-apps module).
 *
 * <p><b>Constructor Interception Pattern:</b> Constructors are intercepted using method name "new"
 * and the constructor parameter types as the signature.
 */
public class AroundConstructorCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  // ===========================================================================
  // Argument Mutation Tests
  // ===========================================================================

  /**
   * Tests constructor argument mutation via AROUND callback.
   *
   * <p>Registers an AROUND intercept on InterceptableApp(Integer) constructor that doubles the
   * argument before proceeding. Verifies that the constructed object has the mutated value.
   */
  @Test
  public void testConstructorArgMutationBeforeProceed() throws Exception {
    logger.info("===== testConstructorArgMutationBeforeProceed: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "doubleConstructorArgAndProceed";
    final int inputValue = 10;
    final int expectedValue = 20; // 10 * 2 = 20

    // 1. Register an AROUND intercept on parameterized constructor
    logger.info("Creating AROUND intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

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
        "Counter should be 20 (constructor arg was doubled: 10 * 2 = 20)",
        counterValue,
        is(expectedValue));

    assertTrue(
        "Expected doubleConstructorArgAndProceed callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleConstructorArgAndProceed: 10 -> 20"));

    logger.info("===== testConstructorArgMutationBeforeProceed: TEST COMPLETED SUCCESSFULLY =====");
  }

  // ===========================================================================
  // Post-Construction Logic Tests
  // ===========================================================================

  /**
   * Tests post-construction logging via AROUND callback.
   *
   * <p>Registers an AROUND intercept that proceeds with construction and logs the constructed
   * object's type.
   */
  @Test
  public void testLogConstructedObjectAfterProceed() throws Exception {
    logger.info("===== testLogConstructedObjectAfterProceed: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "logConstructedObjectAfterProceed";
    final int inputValue = 42;

    // 1. Register an AROUND intercept on parameterized constructor
    logger.info("Creating AROUND intercept request for constructor with post-proceed logging");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke factory method that calls constructor
    logger.info("Invoking createWithCounter({}) with post-proceed logging callback", inputValue);
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

    // 3. Verify construction succeeded
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());
    logger.info("InterceptableApp instance created with ref: {}", appInstance);

    // 4. Verify the counter value is unchanged (callback doesn't modify)
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

    assertThat("Counter should be unchanged (callback only logs)", counterValue, is(inputValue));

    assertTrue(
        "Expected logConstructedObjectAfterProceed callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "logConstructedObjectAfterProceed: constructed object"));

    logger.info("===== testLogConstructedObjectAfterProceed: TEST COMPLETED SUCCESSFULLY =====");
  }

  // ===========================================================================
  // Skip-Proceed with Exception Tests
  // ===========================================================================

  /**
   * Tests AROUND callback that skips constructor and throws exception.
   *
   * <p>Registers an AROUND intercept that skips construction and throws SecurityException.
   */
  @Test
  public void testConstructorSkipAndThrowException() throws Exception {
    logger.info("===== testConstructorSkipAndThrowException: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "skipAndThrowException";

    // 1. Register an AROUND intercept on parameterized constructor
    logger.info("Creating AROUND intercept request for constructor with skip-and-throw");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke factory method - should throw SecurityException from callback
    logger.info("Invoking createWithCounter which should throw SecurityException");
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

      if (response.getRaisedThrowable() != null) {
        String exceptionClass = response.getRaisedThrowable().getThrowable().getType();
        String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();

        logger.info("Received exception: {} with message: {}", exceptionClass, exceptionMessage);

        assertThat(
            "Exception should be SecurityException",
            exceptionClass,
            is("java.lang.SecurityException"));
        assertThat(
            "Exception message should mention AROUND callback",
            exceptionMessage,
            containsString("Access denied by AROUND intercept callback"));

        assertTrue(
            "Expected skipAndThrowException callback to log",
            InterceptEndToEndTestSuite.waitForAppLogLine(
                "skipAndThrowException: skipping execution, throwing SecurityException"));
      } else {
        fail("Expected SecurityException to be thrown by callback");
      }
    } catch (Exception e) {
      logger.error("Unexpected exception during test", e);
      throw e;
    }

    logger.info("===== testConstructorSkipAndThrowException: TEST COMPLETED SUCCESSFULLY =====");
  }

  // ===========================================================================
  // No-Op Callback Tests
  // ===========================================================================

  /**
   * Tests no-op AROUND callback on constructor.
   *
   * <p>Registers an AROUND intercept with a no-op callback that simply proceeds. Verifies that the
   * constructor executes normally with unchanged arguments.
   */
  @Test
  public void testConstructorNoOpCallback() throws Exception {
    logger.info("===== testConstructorNoOpCallback: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "noOpAround";
    final int inputValue = 77;

    // 1. Register an AROUND intercept on parameterized constructor with no-op callback
    logger.info("Creating AROUND intercept request for constructor with no-op callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke factory method that calls constructor
    logger.info("Invoking createWithCounter({}) with no-op AROUND callback", inputValue);
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

    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: proceeding with no modifications"));

    logger.info("===== testConstructorNoOpCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  // ===========================================================================
  // skipProceed() Validation Tests
  // ===========================================================================

  /**
   * Tests that skipProceed() without setting a return value throws IllegalStateException.
   *
   * <p>Registers an AROUND intercept that calls skipProceed() without providing a return value.
   * Verifies that the server throws IllegalStateException, which is propagated to the caller.
   */
  @Test
  public void testSkipProceedWithoutReturnValueThrows() throws Exception {
    logger.info("===== testSkipProceedWithoutReturnValueThrows: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "skipWithoutReturnValue";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Invoke createWithCounter - should throw IllegalStateException due to missing return value
    logger.info("Invoking createWithCounter which should throw IllegalStateException");
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

    // Verify IllegalStateException was thrown
    assertTrue(
        "Expected IllegalStateException to be thrown due to missing return value",
        response.getRaisedThrowable() != null);
    assertThat(
        "Expected IllegalStateException type",
        response.getRaisedThrowable().getThrowable().getType(),
        is(IllegalStateException.class.getName()));
    assertThat(
        "Expected exception message to mention return value or setReturnValue",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("setReturnValue"));

    logger.info("===== testSkipProceedWithoutReturnValueThrows: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that skipProceed() with explicit null return value is accepted.
   *
   * <p>Registers an AROUND intercept that calls setReturnValue(null) then skipProceed(). Verifies
   * that null is properly returned to the caller (not an exception).
   */
  @Test
  public void testSkipProceedWithNullReturnValueSucceeds() throws Exception {
    logger.info("===== testSkipProceedWithNullReturnValueSucceeds: TEST STARTED =====");

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "skipWithNullReturnValue";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Invoke createWithCounter - should return null (not throw)
    logger.info("Invoking createWithCounter which should return null");
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

    // Verify no exception was thrown
    if (response.getRaisedThrowable() != null) {
      fail(
          "Unexpected exception: "
              + response.getRaisedThrowable().getThrowable().getType()
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    // Verify return value is null
    Object returnValue = Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be null", returnValue, is(nullValue()));

    assertTrue(
        "Expected callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "skipWithNullReturnValue: skipping execution with explicit null return value"));

    logger.info(
        "===== testSkipProceedWithNullReturnValueSucceeds: TEST COMPLETED SUCCESSFULLY =====");
  }
}
