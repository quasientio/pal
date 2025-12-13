/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.mechanism.constructor;

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
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for AROUND constructor intercept callback dispatch.
 *
 * <p>These tests verify the callback <b>dispatch mechanism</b> for AROUND intercepts on
 * constructors. They verify that callbacks are sent with the correct structure but do NOT execute
 * callback handlers.
 *
 * <p><b>Key verifications for AROUND callbacks:</b>
 *
 * <ul>
 *   <li>Message type is INTERCEPT_CALLBACK_REQUEST
 *   <li>Intercept type is AROUND
 *   <li>Two callbacks sent: BEFORE phase + AFTER phase
 *   <li>Both phases have same callbackId for correlation
 *   <li>timeoutMs field is set (> 0) on BEFORE phase
 *   <li>Constructor parameters present in BEFORE phase
 *   <li>Return value (constructed object) present in AFTER phase
 * </ul>
 *
 * <p>Tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Intercepts triggered via AspectJ weaving at call-site (factory method
 *       calls constructor)
 *   <li><b>INCOMING_RPC</b>: Intercepts triggered via direct RPC message dispatch
 * </ul>
 */
@RunWith(Parameterized.class)
public class ConstructorAroundCallbackIT extends AbstractInterceptIT {

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public ConstructorAroundCallbackIT(InvocationPath path) {
    this.path = path;
  }

  /**
   * Returns the parameterized test data for invocation paths.
   *
   * @return collection of invocation path parameters
   */
  @Parameterized.Parameters(name = "{index}: path={0}")
  public static Collection<Object[]> data() {
    return invocationPathParameters();
  }

  /**
   * Tests single AROUND callback dispatch on constructor.
   *
   * <p>Registers an AROUND intercept on the parameterized constructor, invokes it once through the
   * specified path, and verifies:
   *
   * <ul>
   *   <li>Exactly 2 callbacks are received (BEFORE phase + AFTER phase)
   *   <li>First callback is BEFORE phase with constructor parameters
   *   <li>Second callback is AFTER phase with constructed object
   *   <li>Both have same callbackId (correlating the phases)
   *   <li>Intercept type is AROUND
   *   <li>timeoutMs is set (> 0) on BEFORE phase
   * </ul>
   */
  @Test
  public void testSingleAroundCallback() throws Exception {
    logger.info("===== testSingleAroundCallback [{}]: TEST STARTED =====", path);

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int initialValue = 10;

    // 1. Register an AROUND intercept on parameterized constructor
    logger.info("Creating AROUND intercept request for parameterized constructor");
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            myPeerUuid,
            InterceptType.AROUND,
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

    // 2. Invoke constructor through the specified path
    logger.info("Invoking constructor via {} path which should trigger 2 AROUND callbacks", path);
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use factory method that calls constructor (triggers intercept via call-site)
      invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "createWithCounter",
              new String[] {"java.lang.Integer"},
              null,
              null,
              new Object[] {initialValue}));
    } else {
      // INCOMING_RPC: Call constructor directly
      invoke(
          messageBuilder.buildNonEmptyConstructor(
              myPeerUuid,
              InterceptableApp.class.getName(),
              new String[] {"java.lang.Integer"},
              new Object[] {initialValue},
              null));
    }
    logger.info("Constructor invocation completed");

    // 3. Retrieve and verify callbacks
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

    // 4. Verify BEFORE phase callback (first callback)
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

    // Verify constructor parameters are present in BEFORE phase
    assertThat(
        "BEFORE callback should have exec with constructor call",
        beforeReq.getExec(),
        is(notNullValue()));
    assertThat(
        "BEFORE callback should have constructor call",
        beforeReq.getExec().getConstructorCall(),
        is(notNullValue()));
    assertThat(
        "BEFORE callback should have 1 parameter (the Integer argument)",
        beforeReq.getExec().getConstructorCall().getParameters().length,
        is(1));

    // 5. Verify AFTER phase callback (second callback)
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

    // Verify return value structure in AFTER phase (constructed object)
    assertThat(
        "AFTER callback should have exec with return value",
        afterReq.getExec(),
        is(notNullValue()));
    assertThat(
        "AFTER callback should have return value",
        afterReq.getExec().getReturnValue(),
        is(notNullValue()));
    // Constructor returns object (not void)
    assertThat(
        "Constructor returns object, so isVoid should be false",
        afterReq.getExec().getReturnValue().isVoid,
        is(false));
    assertThat(
        "AFTER callback should have the constructed object",
        afterReq.getExec().getReturnValue().getObject(),
        is(notNullValue()));

    logger.info("===== testSingleAroundCallback [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }
}
