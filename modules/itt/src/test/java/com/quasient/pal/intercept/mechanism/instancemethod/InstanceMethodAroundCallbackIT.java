/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.mechanism.instancemethod;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptPhase;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for AROUND instance method intercept callback dispatch.
 *
 * <p>These tests verify the callback <b>dispatch mechanism</b> for AROUND intercepts on instance
 * methods (EXEC_INSTANCE_METHOD). They verify that callbacks are sent with the correct structure
 * but do NOT execute callback handlers.
 *
 * <p><b>Key verifications for AROUND callbacks:</b>
 *
 * <ul>
 *   <li>Message type is INTERCEPT_CALLBACK_REQUEST
 *   <li>Intercept type is AROUND
 *   <li>Phase is BEFORE (first phase of AROUND)
 *   <li>timeoutMs field is set (> 0)
 *   <li>callbackId field is set (non-empty, for correlating BEFORE/AFTER phases)
 *   <li>Method parameters are present
 * </ul>
 *
 * <p><b>NOTE:</b> These tests verify intercepts at the hot-path (via quantization, which happens at
 * the call-site), and so, we need to invoke via RPC a method/ctor that triggers the actual
 * interception target.
 */
public class InstanceMethodAroundCallbackIT extends AbstractInterceptIT {

  /**
   * Tests single AROUND callback dispatch on instance method.
   *
   * <p>Registers an AROUND intercept on multiplyBy, calls a wrapper that invokes it once (n=1), and
   * verifies:
   *
   * <ul>
   *   <li>Exactly 2 callbacks are received (BEFORE phase + AFTER phase)
   *   <li>First callback is BEFORE phase with method parameters
   *   <li>Second callback is AFTER phase with return value
   *   <li>Both have same callbackId (correlating the phases)
   *   <li>Intercept type is AROUND
   *   <li>timeoutMs is set (> 0) on BEFORE phase
   * </ul>
   */
  @Test
  public void testSingleAroundCallback() throws Exception {
    logger.info("===== testSingleAroundCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 1; // Number of times to call multiplyBy
    final int multiplier = 3;

    // 1. Register an AROUND intercept on multiplyBy method
    logger.info("Creating AROUND intercept request for multiplyBy method");
    // UUID for the intercept registration.
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            myPeerUuid,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "multiplyBy", Collections.singletonList("java.lang.Integer")));

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

    // 3. Invoke multiplyCounterNTimesBy which internally calls multiplyBy and triggers intercept
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) which should trigger {} AROUND callback(s)",
        n,
        multiplier,
        n);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyCounterNTimesBy",
            appInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed");

    // 4. Verify invocation succeeded (or timed out waiting for callback response)
    // Note: Since we're using ThinPeer which doesn't execute handlers, the interceptable
    // peer will timeout waiting for a response. We just need to verify the callback was sent.

    // 5. Retrieve and verify callbacks
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

    // 6. Verify BEFORE phase callback (first callback)
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

    // Verify method parameters are present in BEFORE phase
    assertThat(
        "BEFORE callback should have exec with instance method call",
        beforeReq.getExec(),
        is(notNullValue()));
    assertThat(
        "BEFORE callback should have instance method call",
        beforeReq.getExec().getInstanceMethodCall(),
        is(notNullValue()));
    assertThat(
        "BEFORE callback should have 1 parameter (the Integer argument)",
        beforeReq.getExec().getInstanceMethodCall().getParameters().length,
        is(1));

    // 7. Verify AFTER phase callback (second callback)
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
    // multiplyBy returns void
    assertThat(
        "multiplyBy returns void, so isVoid should be true",
        afterReq.getExec().getReturnValue().isVoid,
        is(true));

    logger.info("===== testSingleAroundCallback: TEST COMPLETED SUCCESSFULLY =====");
  }
}
