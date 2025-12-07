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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.InterceptEndToEndTestSuite;
import com.quasient.pal.apps.callbacks.AsyncCallbackHandlers;
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
 * Integration tests for BEFORE_ASYNC field intercept callbacks.
 *
 * <p>These tests verify that BEFORE_ASYNC callbacks on field operations:
 *
 * <ul>
 *   <li>Can read the PUT value but cannot mutate it
 *   <li>Are fire-and-forget (field operation does not wait for callback response)
 *   <li>Throw UnsupportedOperationException when attempting to mutate arguments
 * </ul>
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and
 * AsyncCallbackHandlers callback handlers (both in itt-apps module).
 */
public class BeforeFieldAsyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests that BEFORE_ASYNC callback can read PUT value without mutation on instance field.
   *
   * <p>Registers a BEFORE_ASYNC intercept on instance field PUT that only logs the value. Verifies
   * that the field receives the original value.
   */
  @Test
  public void testInstanceFieldPutAsyncCanReadValue() throws Exception {
    logger.info("===== testInstanceFieldPutAsyncCanReadValue: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "logArgs";
    final int newValue = 100;

    // 1. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register a BEFORE_ASYNC intercept on counter field PUT
    logger.info("Creating BEFORE_ASYNC intercept request for counter PUT");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call setCounter to trigger the callback
    logger.info("Invoking setCounter({}) with BEFORE_ASYNC callback", newValue);
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

    // 4. Verify the value was written correctly (unchanged by ASYNC callback)
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

    assertThat(
        "Counter should have the original value (ASYNC cannot mutate)", counterValue, is(newValue));

    // Verify callback logged the args in application log
    assertTrue(
        "Expected logArgs callback to log the args",
        InterceptEndToEndTestSuite.waitForAppLogLine("logArgs.*BEFORE_ASYNC.*args.*" + newValue));

    logger.info("===== testInstanceFieldPutAsyncCanReadValue: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that BEFORE_ASYNC callback throws UnsupportedOperationException on mutation attempt.
   *
   * <p>Registers a BEFORE_ASYNC intercept that attempts to mutate the PUT value. Verifies that the
   * field operation still completes with the original value.
   */
  @Test
  public void testInstanceFieldPutAsyncCannotMutate() throws Exception {
    logger.info("===== testInstanceFieldPutAsyncCannotMutate: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "attemptIntArgMutation";
    final int newValue = 50;

    // 1. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register a BEFORE_ASYNC intercept that attempts mutation
    logger.info("Creating BEFORE_ASYNC intercept request with mutation attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call setCounter - callback will throw but ASYNC is fire-and-forget
    logger.info(
        "Invoking setCounter({}) - callback should throw but PUT still completes", newValue);
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

    // 4. Verify original value was written (ASYNC exception doesn't affect operation)
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

    assertThat(
        "Counter should have original value (ASYNC callback exception doesn't stop operation)",
        counterValue,
        is(newValue));

    // Verify callback logged the mutation attempt in application log
    assertTrue(
        "Expected attemptIntArgMutation callback to log mutation attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptIntArgMutation.*BEFORE_ASYNC.*attempting to mutate"));

    logger.info("===== testInstanceFieldPutAsyncCannotMutate: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests BEFORE_ASYNC callback on static field PUT.
   *
   * <p>Verifies that ASYNC callbacks work correctly for static field operations.
   */
  @Test
  public void testStaticFieldPutAsyncNoOp() throws Exception {
    logger.info("===== testStaticFieldPutAsyncNoOp: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "logArgs";
    final int newValue = 300;

    // 1. Register a BEFORE_ASYNC intercept on staticCounter field PUT
    logger.info("Creating BEFORE_ASYNC intercept request for staticCounter PUT");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Call setStaticCounter to trigger the callback
    logger.info("Invoking setStaticCounter({}) with BEFORE_ASYNC callback", newValue);
    ExecMessage setResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "setStaticCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {newValue}));

    assertThat(
        "setStaticCounter should not raise exception",
        setResponse.getRaisedThrowable(),
        is(nullValue()));

    // 3. Verify the value was written correctly
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getStaticCounter",
                new String[] {},
                null,
                null,
                new Object[] {}));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Static counter value after set: {}", counterValue);

    assertThat("Static counter should have the set value", counterValue, is(newValue));

    // Verify callback logged the args in application log
    assertTrue(
        "Expected logArgs callback to log the args",
        InterceptEndToEndTestSuite.waitForAppLogLine("logArgs.*BEFORE_ASYNC.*args.*" + newValue));

    logger.info("===== testStaticFieldPutAsyncNoOp: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that BEFORE_ASYNC callback cannot throw exceptions via setExceptionToThrow.
   *
   * <p>Registers a BEFORE_ASYNC intercept on instance field PUT that attempts to call
   * setExceptionToThrow. Verifies that:
   *
   * <ol>
   *   <li>The callback throws UnsupportedOperationException when calling setExceptionToThrow
   *   <li>The field operation still executes (ASYNC is fire-and-forget)
   *   <li>The field value is set normally
   * </ol>
   */
  @Test
  public void testAsyncCallbackCannotThrowException() throws Exception {
    logger.info("===== testAsyncCallbackCannotThrowException: TEST STARTED =====");

    final String callbackClass = AsyncCallbackHandlers.class.getName();
    final String callbackMethod = "attemptThrowException";
    final int newValue = 99;

    // 1. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register a BEFORE_ASYNC intercept that attempts to throw exception
    logger.info("Creating BEFORE_ASYNC intercept request with exception throw attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call setCounter to trigger the callback
    logger.info(
        "Invoking setCounter({}) - callback should throw but field op should still execute",
        newValue);
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

    // 4. Verify field was set (operation ran normally)
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

    assertThat("Counter should have the new value", counterValue, is(newValue));

    // Verify callback logged the exception throw attempt in application log
    assertTrue(
        "Expected attemptThrowException callback to log exception throw attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptThrowException.*BEFORE_ASYNC.*attempting to set exception"));

    logger.info("===== testAsyncCallbackCannotThrowException: TEST COMPLETED SUCCESSFULLY =====");
  }
}
