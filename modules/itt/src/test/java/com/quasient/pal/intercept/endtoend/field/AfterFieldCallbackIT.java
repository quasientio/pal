/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.endtoend.field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.InterceptEndToEndTestSuite;
import com.quasient.pal.apps.callbacks.field.FieldHandlers;
import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for AFTER intercept callbacks on field operations.
 *
 * <p>These tests verify the end-to-end callback mechanism for AFTER intercepts on field GET and PUT
 * operations, including:
 *
 * <ul>
 *   <li>Generic no-op callbacks that verify callback invocation without modification
 *   <li>GET return value override via AFTER callbacks (doubling or adding to the returned value)
 *   <li>Exception propagation from AFTER callbacks
 * </ul>
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and FieldHandlers
 * callback handlers (both in itt-apps module).
 */
public class AfterFieldCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  // ===========================================================================
  // Instance Field Tests
  // ===========================================================================

  /**
   * Tests no-op AFTER callback on instance field GET.
   *
   * <p>Verifies that the callback is invoked without affecting the returned value.
   */
  @Test
  public void testInstanceFieldGetNoOpCallback() throws Exception {
    logger.info("===== testInstanceFieldGetNoOpCallback: TEST STARTED =====");

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "logGetValue";
    final int initialValue = 42;

    // 1. Create InterceptableApp instance with initial value using factory method
    logger.info("Creating InterceptableApp instance with initial value {}", initialValue);
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {initialValue}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register an AFTER intercept on counter field GET
    logger.info("Creating AFTER intercept request for counter GET");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter to trigger the callback
    logger.info("Invoking getCounter()");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    // 4. Verify no exception and value is unchanged
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat("Counter value should be unchanged", counterValue, is(initialValue));

    // Verify callback logged in application log
    assertTrue(
        "Expected logGetValue callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "logGetValue: read value=" + initialValue + ", isVoid=false"));

    logger.info("===== testInstanceFieldGetNoOpCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests no-op AFTER callback on instance field PUT.
   *
   * <p>Verifies that the callback is invoked without affecting the written value.
   */
  @Test
  public void testInstanceFieldPutNoOpCallback() throws Exception {
    logger.info("===== testInstanceFieldPutNoOpCallback: TEST STARTED =====");

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "noOp";
    final int newValue = 100;

    // 1. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register an AFTER intercept on counter field PUT
    logger.info("Creating AFTER intercept request for counter PUT");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call setCounter to trigger the callback
    logger.info("Invoking setCounter({})", newValue);
    ExecMessage setResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "setCounter",
                appInstance,
                new String[] {"java.lang.Integer"},
                new Object[] {newValue}));

    assertThat(
        "setCounter should not raise exception", setResponse.getRaisedThrowable(), is(nullValue()));

    // 4. Verify the value was written correctly
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Counter value after set: {}", counterValue);

    assertThat("Counter should have the set value", counterValue, is(newValue));

    // Verify callback logged in application log
    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations, phase=AFTER"));

    logger.info("===== testInstanceFieldPutNoOpCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests GET return value override via AFTER callback on instance field.
   *
   * <p>Registers an AFTER intercept on counter GET that doubles the returned value. Verifies that
   * the caller receives the doubled value even though the actual field contains the original.
   */
  @Test
  public void testInstanceFieldGetReturnValueOverride() throws Exception {
    logger.info("===== testInstanceFieldGetReturnValueOverride: TEST STARTED =====");

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "doubleGetValue";
    final int actualValue = 25;
    final int expectedValue = 50; // 25 * 2 = 50

    // 1. Create InterceptableApp instance with initial value
    logger.info("Creating InterceptableApp instance with value {}", actualValue);
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {actualValue}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register an AFTER intercept on counter field GET that doubles value
    logger.info("Creating AFTER intercept request for counter GET with doubling callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter - callback should double the return value
    logger.info(
        "Invoking getCounter() which should return {} (doubled from {})",
        expectedValue,
        actualValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Counter value returned: {}", counterValue);

    assertThat(
        "Counter should return doubled value (25 * 2 = 50)", counterValue, is(expectedValue));

    // Verify callback logged the mutation in application log
    assertTrue(
        "Expected doubleGetValue callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleGetValue: 25 -> 50"));

    logger.info("===== testInstanceFieldGetReturnValueOverride: TEST COMPLETED SUCCESSFULLY =====");
  }

  // ===========================================================================
  // Static Field Tests
  // ===========================================================================

  /**
   * Tests no-op AFTER callback on static field GET.
   *
   * <p>Verifies that the callback is invoked without affecting the returned value.
   */
  @Test
  public void testStaticFieldGetNoOpCallback() throws Exception {
    logger.info("===== testStaticFieldGetNoOpCallback: TEST STARTED =====");

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "logGetValue";
    final int initialValue = 200;

    // 1. First set the static counter to a known value
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {initialValue}));

    // 2. Register an AFTER intercept on staticCounter field GET
    logger.info("Creating AFTER intercept request for staticCounter GET");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getStaticCounter to trigger the callback
    logger.info("Invoking getStaticCounter()");
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getStaticCounter",
                new String[] {},
                null,
                null,
                new Object[] {}));

    // 4. Verify no exception and value is unchanged
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Static counter value: {}", counterValue);

    assertThat("Static counter value should be unchanged", counterValue, is(initialValue));

    // Verify callback logged in application log
    assertTrue(
        "Expected logGetValue callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "logGetValue: read value=" + initialValue + ", isVoid=false"));

    logger.info("===== testStaticFieldGetNoOpCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests GET return value override via AFTER callback on static field.
   *
   * <p>Registers an AFTER intercept on staticCounter GET that adds 100 to the returned value.
   * Verifies that the caller receives the modified value.
   */
  @Test
  public void testStaticFieldGetReturnValueOverride() throws Exception {
    logger.info("===== testStaticFieldGetReturnValueOverride: TEST STARTED =====");

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "addHundredToGetValue";
    final int actualValue = 50;
    final int expectedValue = 150; // 50 + 100 = 150

    // 1. First set the static counter to a known value
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {actualValue}));

    // 2. Register an AFTER intercept on staticCounter field GET that adds 100
    logger.info("Creating AFTER intercept request for staticCounter GET with add100 callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getStaticCounter - callback should add 100 to the return value
    logger.info(
        "Invoking getStaticCounter() which should return {} (actual + 100 = {} + 100)",
        expectedValue,
        actualValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getStaticCounter",
                new String[] {},
                null,
                null,
                new Object[] {}));

    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Static counter value returned: {}", counterValue);

    assertThat(
        "Static counter should return modified value (50 + 100 = 150)",
        counterValue,
        is(expectedValue));

    // Verify callback logged the mutation in application log
    assertTrue(
        "Expected addHundredToGetValue callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("addHundredToGetValue: 50 -> 150"));

    logger.info("===== testStaticFieldGetReturnValueOverride: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests exception propagation via AFTER callback on instance field GET.
   *
   * <p>Registers an AFTER intercept that throws a SecurityException. Verifies that the exception is
   * propagated.
   */
  @Test
  public void testInstanceFieldGetCallbackThrowsException() throws Exception {
    logger.info("===== testInstanceFieldGetCallbackThrowsException: TEST STARTED =====");

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "throwExceptionOnGet";
    final int initialValue = 10;

    // 1. Create InterceptableApp instance with initial value
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {initialValue}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register an AFTER intercept on counter field GET that throws
    logger.info("Creating AFTER intercept request for counter GET with throwing callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter - should throw SecurityException
    logger.info("Invoking getCounter() which should throw SecurityException");
    try {
      ExecMessage getResponse =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  InterceptableApp.class.getName(),
                  "getCounter",
                  appInstance,
                  new String[] {},
                  new Object[] {}));

      if (getResponse.getRaisedThrowable() != null) {
        String exceptionClass = getResponse.getRaisedThrowable().getThrowable().getType();
        String exceptionMessage = getResponse.getRaisedThrowable().getThrowable().getMessage();

        logger.info("Received exception: {} with message: {}", exceptionClass, exceptionMessage);

        assertThat(
            "Exception should be SecurityException",
            exceptionClass,
            is("java.lang.SecurityException"));
        assertThat(
            "Exception message should mention field GET callback",
            exceptionMessage,
            containsString("Access denied by field GET intercept callback"));

        // Verify callback logged in application log
        assertTrue(
            "Expected throwExceptionOnGet callback to log",
            InterceptEndToEndTestSuite.waitForAppLogLine(
                "throwExceptionOnGet: throwing SecurityException"));
      } else {
        fail("Expected SecurityException to be thrown by callback");
      }
    } catch (Exception e) {
      logger.error("Unexpected exception during test", e);
      throw e;
    }

    logger.info(
        "===== testInstanceFieldGetCallbackThrowsException: TEST COMPLETED SUCCESSFULLY =====");
  }
}
