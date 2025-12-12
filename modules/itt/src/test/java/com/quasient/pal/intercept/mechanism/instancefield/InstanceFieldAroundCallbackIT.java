/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.mechanism.instancefield;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptPhase;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for AROUND instance field intercept callback dispatch.
 *
 * <p>These tests verify the callback <b>dispatch mechanism</b> for AROUND intercepts on instance
 * field operations (EXEC_GET_FIELD and EXEC_PUT_FIELD). They verify that callbacks are sent with
 * the correct structure but do NOT execute callback handlers.
 *
 * <p><b>Key verifications for AROUND callbacks:</b>
 *
 * <ul>
 *   <li>Message type is INTERCEPT_CALLBACK_REQUEST
 *   <li>Intercept type is AROUND
 *   <li>Two callbacks sent: BEFORE phase + AFTER phase
 *   <li>Both phases have same callbackId for correlation
 *   <li>timeoutMs field is set (> 0) on BEFORE phase
 * </ul>
 *
 * <p><b>NOTE:</b>These tests verify intercepts at the hot-path (via quantization, which happens at
 * the call-site), and so, we need to invoke via RPC a method/ctor that triggers the actual
 * interception target.
 */
public class InstanceFieldAroundCallbackIT extends AbstractInterceptIT {

  /**
   * Tests single AROUND callback dispatch on instance field GET operation.
   *
   * <p>Registers an AROUND intercept on counter GET, creates an app instance, calls a getter once,
   * and verifies:
   *
   * <ul>
   *   <li>Exactly 2 callbacks are received (BEFORE phase + AFTER phase)
   *   <li>First callback is BEFORE phase with field GET info
   *   <li>Second callback is AFTER phase with field value
   *   <li>Both have same callbackId (correlating the phases)
   *   <li>Intercept type is AROUND
   *   <li>timeoutMs is set (> 0) on BEFORE phase
   * </ul>
   */
  @Test
  public void testSingleAroundCallbackOnGet() throws Exception {
    logger.info("===== testSingleAroundCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    // 1. Register an AROUND intercept on counter field GET
    logger.info("Creating AROUND intercept request for counter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create InterceptableApp instance
    logger.info("Creating InterceptableApp instance");
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("InterceptableApp instance created with ref: {}", appInstance);

    // 3. Invoke getCounter which triggers GET_FIELD and callback
    logger.info("Invoking getCounter() which should trigger 2 AROUND callbacks");
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "getCounter",
            appInstance,
            new String[] {},
            new Object[] {}));
    logger.info("getCounter invocation completed");

    // 4. Retrieve and verify callbacks
    // AROUND intercepts send 2 callbacks: BEFORE phase + AFTER phase
    final int expectedCallbacks = 2;
    logger.info(
        "Waiting for {} AROUND callback(s) to be received (BEFORE + AFTER)", expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("All {} AROUND callback(s) received successfully", expectedCallbacks);

    assertThat(
        "Should receive exactly 2 callbacks (BEFORE + AFTER phases)",
        callbacks.size(),
        is(expectedCallbacks));

    // 5. Verify BEFORE phase callback (first callback)
    Message beforeCallback = callbacks.get(0);
    assertThat("BEFORE callback message should not be null", beforeCallback, is(notNullValue()));
    assertThat(
        "BEFORE callback should be INTERCEPT_CALLBACK_REQUEST type",
        beforeCallback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));

    InterceptCallbackRequestMessage beforeReq = beforeCallback.getInterceptCallbackRequestMessage();
    assertThat("BEFORE callback request should not be null", beforeReq, is(notNullValue()));

    // Verify callback class and method
    assertThat("Callback class should match", beforeReq.getCallbackClass(), is(callbackClass));
    assertThat("Callback method should match", beforeReq.getCallbackMethod(), is(callbackMethod));

    // Verify AROUND-specific fields for BEFORE phase
    assertThat(
        "Intercept type should be AROUND",
        beforeReq.getInterceptType(),
        is(InterceptType.AROUND.toByte()));

    assertThat(
        "First callback phase should be BEFORE",
        beforeReq.getPhase(),
        is(InterceptPhase.BEFORE.toByte()));

    assertThat(
        "timeoutMs should be set (> 0) for AROUND BEFORE phase",
        beforeReq.getTimeoutMs(),
        is(greaterThan(0)));

    String beforeCallbackId = beforeReq.getCallbackId();
    assertThat(
        "callbackId should be set (non-empty) for correlating BEFORE/AFTER phases",
        beforeCallbackId,
        is(not(emptyOrNullString())));

    // Verify instance field GET in BEFORE phase
    assertThat(
        "BEFORE callback should have exec with instance field get",
        beforeReq.getExec(),
        is(notNullValue()));
    assertThat(
        "BEFORE callback should have InstanceFieldGet",
        beforeReq.getExec().getInstanceFieldGet(),
        is(notNullValue()));

    // 6. Verify AFTER phase callback (second callback)
    Message afterCallback = callbacks.get(1);
    assertThat("AFTER callback message should not be null", afterCallback, is(notNullValue()));
    assertThat(
        "AFTER callback should be INTERCEPT_CALLBACK_REQUEST type",
        afterCallback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));

    InterceptCallbackRequestMessage afterReq = afterCallback.getInterceptCallbackRequestMessage();
    assertThat("AFTER callback request should not be null", afterReq, is(notNullValue()));

    // Verify AROUND-specific fields for AFTER phase
    assertThat(
        "AFTER callback intercept type should be AROUND",
        afterReq.getInterceptType(),
        is(InterceptType.AROUND.toByte()));

    assertThat(
        "Second callback phase should be AFTER",
        afterReq.getPhase(),
        is(InterceptPhase.AFTER.toByte()));

    assertThat(
        "AFTER callback should have same callbackId as BEFORE (for correlation)",
        afterReq.getCallbackId(),
        is(beforeCallbackId));

    // Verify return value structure in AFTER phase
    assertThat(
        "AFTER callback should have exec with return value",
        afterReq.getExec(),
        is(notNullValue()));
    assertThat(
        "AFTER callback should have return value",
        afterReq.getExec().getReturnValue(),
        is(notNullValue()));
    assertThat(
        "GET returns value, so isVoid should be false",
        afterReq.getExec().getReturnValue().isVoid,
        is(false));

    logger.info("===== testSingleAroundCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AROUND callback dispatch on instance field PUT operation.
   *
   * <p>Registers an AROUND intercept on counter PUT, creates an app instance, calls a setter once,
   * and verifies the callbacks. Note: Creating the app instance also triggers a PUT for the field
   * initializer, so we expect 4 callbacks total (2 from init + 2 from setter).
   */
  @Test
  public void testAroundCallbackOnPut() throws Exception {
    logger.info("===== testAroundCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // 1. Register an AROUND intercept on counter field PUT
    logger.info("Creating AROUND intercept request for counter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create InterceptableApp instance (triggers field initializer PUT - 2 callbacks)
    logger.info("Creating InterceptableApp instance (triggers field initializer PUT)");
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("InterceptableApp instance created with ref: {}", appInstance);

    // 3. Invoke setCounter which triggers PUT_FIELD and callback (2 more callbacks)
    logger.info("Invoking setCounter({}) which should trigger 2 more AROUND callbacks", newValue);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setCounter",
            appInstance,
            new String[] {"java.lang.Integer"},
            new Object[] {newValue}));
    logger.info("setCounter invocation completed");

    // 4. Retrieve and verify callbacks
    // AROUND intercepts send 2 callbacks per operation: BEFORE + AFTER
    // We have 2 PUT operations (initializer + setter) = 4 callbacks total
    final int expectedCallbacks = 4;
    logger.info(
        "Waiting for {} AROUND callback(s) to be received (2 from init + 2 from setter)",
        expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("All {} AROUND callback(s) received successfully", expectedCallbacks);

    assertThat(
        "Should receive exactly 4 callbacks (2 from init + 2 from setter)",
        callbacks.size(),
        is(expectedCallbacks));

    // 5. Verify the setter callbacks (last 2 callbacks)
    // The first 2 callbacks are from field initializer, last 2 from setter

    Message setterBeforeCallback = callbacks.get(2);
    assertThat(
        "Setter BEFORE callback should not be null", setterBeforeCallback, is(notNullValue()));

    InterceptCallbackRequestMessage setterBeforeReq =
        setterBeforeCallback.getInterceptCallbackRequestMessage();
    assertThat(
        "Intercept type should be AROUND",
        setterBeforeReq.getInterceptType(),
        is(InterceptType.AROUND.toByte()));

    assertThat(
        "Setter BEFORE phase should be BEFORE",
        setterBeforeReq.getPhase(),
        is(InterceptPhase.BEFORE.toByte()));

    assertThat(
        "timeoutMs should be set (> 0) for AROUND BEFORE phase",
        setterBeforeReq.getTimeoutMs(),
        is(greaterThan(0)));

    String setterCallbackId = setterBeforeReq.getCallbackId();
    assertThat(
        "callbackId should be set (non-empty) for correlating BEFORE/AFTER phases",
        setterCallbackId,
        is(not(emptyOrNullString())));

    // Verify instance field PUT in setter BEFORE phase
    assertThat(
        "Setter BEFORE callback should have InstanceFieldPut",
        setterBeforeReq.getExec().getInstanceFieldPut(),
        is(notNullValue()));

    // Verify setter AFTER phase
    Message setterAfterCallback = callbacks.get(3);
    InterceptCallbackRequestMessage setterAfterReq =
        setterAfterCallback.getInterceptCallbackRequestMessage();

    assertThat(
        "Setter AFTER phase should be AFTER",
        setterAfterReq.getPhase(),
        is(InterceptPhase.AFTER.toByte()));

    assertThat(
        "Setter AFTER callback should have same callbackId as BEFORE",
        setterAfterReq.getCallbackId(),
        is(setterCallbackId));

    // Verify PUT_DONE in AFTER phase
    assertThat(
        "Setter AFTER callback should have InstanceFieldPutDone",
        setterAfterReq.getExec().getInstanceFieldPutDone(),
        is(notNullValue()));

    logger.info("===== testAroundCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }
}
