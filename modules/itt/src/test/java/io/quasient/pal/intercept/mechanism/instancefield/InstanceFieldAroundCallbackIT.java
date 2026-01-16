/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.mechanism.instancefield;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;

import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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
 * <p>Tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Intercepts triggered via AspectJ weaving at call-site (getter/setter calls
 *       field access)
 *   <li><b>INCOMING_RPC</b>: Intercepts triggered via direct RPC message dispatch
 * </ul>
 *
 * <p><b>Note:</b> Instance field PUT tests have path-dependent callback counts. HOT_PATH triggers
 * the field initializer when creating the instance, resulting in extra callbacks.
 */
@RunWith(Parameterized.class)
public class InstanceFieldAroundCallbackIT extends AbstractInterceptIT {

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public InstanceFieldAroundCallbackIT(InvocationPath path) {
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
   * Tests single AROUND callback dispatch on instance field GET operation.
   *
   * <p>Registers an AROUND intercept on counter GET, creates an app instance, invokes GET through
   * the specified path, and verifies:
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
    logger.info("===== testSingleAroundCallbackOnGet [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // 3. Invoke field GET through the specified path
    logger.info("Invoking field GET via {} path which should trigger 2 AROUND callbacks", path);
    if (path == InvocationPath.HOT_PATH) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "getCounter",
              appInstance,
              new String[] {},
              new Object[] {}));
    } else {
      invoke(
          messageBuilder.buildGetObject(
              myPeerUuid, InterceptableApp.class.getName(), "counter", appInstance));
    }
    logger.info("Field GET invocation completed");

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

    logger.info(
        "===== testSingleAroundCallbackOnGet [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests AROUND callback dispatch on instance field PUT operation.
   *
   * <p>Registers an AROUND intercept on counter PUT, creates an app instance, invokes PUT through
   * the specified path, and verifies the callbacks.
   *
   * <p>For HOT_PATH: Creating the app instance also triggers a PUT for the field initializer, so we
   * expect 4 callbacks total (2 from init + 2 from setter). For INCOMING_RPC: Only the direct PUT
   * call triggers callbacks, so we expect 2 callbacks. To achieve this, we create the instance
   * before registering the intercept for INCOMING_RPC.
   */
  @Test
  public void testAroundCallbackOnPut() throws Exception {
    logger.info("===== testAroundCallbackOnPut [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // For INCOMING_RPC, create instance BEFORE registering intercept to avoid
    // intercepting the field initializer PUT
    ObjectRef appInstance = null;
    if (path == InvocationPath.INCOMING_RPC) {
      logger.info("Creating InterceptableApp instance before intercept registration");
      appInstance =
          ObjectRef.from(
              invoke(
                      messageBuilder.buildEmptyConstructor(
                          myPeerUuid, InterceptableApp.class.getName()))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      logger.info("InterceptableApp instance created with ref: {}", appInstance);
    }

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

    // 2. For HOT_PATH, create InterceptableApp instance after registering intercept
    // (field initializer will be intercepted)
    if (path == InvocationPath.HOT_PATH) {
      logger.info("Creating InterceptableApp instance (will trigger field initializer callback)");
      appInstance =
          ObjectRef.from(
              invoke(
                      messageBuilder.buildEmptyConstructor(
                          myPeerUuid, InterceptableApp.class.getName()))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      logger.info("InterceptableApp instance created with ref: {}", appInstance);
    }

    // 3. Invoke field PUT through the specified path
    logger.info("Invoking field PUT via {} path which should trigger AROUND callbacks", path);
    if (path == InvocationPath.HOT_PATH) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "setCounter",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {newValue}));
    } else {
      invoke(
          messageBuilder.buildPutObject(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "counter",
              appInstance,
              "java.lang.Integer",
              newValue));
    }
    logger.info("Field PUT invocation completed");

    // 4. Retrieve and verify callbacks based on path
    // HOT_PATH: 4 callbacks (2 from field initializer + 2 from setter)
    // INCOMING_RPC: 2 callbacks (only direct PUT call)
    final int expectedCallbacks = (path == InvocationPath.HOT_PATH) ? 4 : 2;
    logger.info("Waiting for {} AROUND callback(s) to be received", expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("All {} AROUND callback(s) received successfully", expectedCallbacks);

    assertThat(
        "Should receive exactly " + expectedCallbacks + " callbacks",
        callbacks.size(),
        is(expectedCallbacks));

    // 5. Verify the PUT callbacks (for HOT_PATH: last 2 callbacks, for INCOMING_RPC: all callbacks)
    int putBeforeIndex = (path == InvocationPath.HOT_PATH) ? 2 : 0;
    int putAfterIndex = (path == InvocationPath.HOT_PATH) ? 3 : 1;

    Message putBeforeCallback = callbacks.get(putBeforeIndex);
    assertThat("PUT BEFORE callback should not be null", putBeforeCallback, is(notNullValue()));

    InterceptCallbackRequestMessage putBeforeReq =
        putBeforeCallback.getInterceptCallbackRequestMessage();
    assertThat(
        "Intercept type should be AROUND",
        putBeforeReq.getInterceptType(),
        is(InterceptType.AROUND.toByte()));

    assertThat(
        "PUT BEFORE phase should be BEFORE",
        putBeforeReq.getPhase(),
        is(InterceptPhase.BEFORE.toByte()));

    assertThat(
        "timeoutMs should be set (> 0) for AROUND BEFORE phase",
        putBeforeReq.getTimeoutMs(),
        is(greaterThan(0)));

    String putCallbackId = putBeforeReq.getCallbackId();
    assertThat(
        "callbackId should be set (non-empty) for correlating BEFORE/AFTER phases",
        putCallbackId,
        is(not(emptyOrNullString())));

    // Verify instance field PUT in BEFORE phase
    assertThat(
        "PUT BEFORE callback should have InstanceFieldPut",
        putBeforeReq.getExec().getInstanceFieldPut(),
        is(notNullValue()));

    // Verify AFTER phase
    Message putAfterCallback = callbacks.get(putAfterIndex);
    InterceptCallbackRequestMessage putAfterReq =
        putAfterCallback.getInterceptCallbackRequestMessage();

    assertThat(
        "PUT AFTER phase should be AFTER",
        putAfterReq.getPhase(),
        is(InterceptPhase.AFTER.toByte()));

    assertThat(
        "PUT AFTER callback should have same callbackId as BEFORE",
        putAfterReq.getCallbackId(),
        is(putCallbackId));

    // Verify PUT_DONE in AFTER phase
    assertThat(
        "PUT AFTER callback should have InstanceFieldPutDone",
        putAfterReq.getExec().getInstanceFieldPutDone(),
        is(notNullValue()));

    logger.info("===== testAroundCallbackOnPut [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }
}
