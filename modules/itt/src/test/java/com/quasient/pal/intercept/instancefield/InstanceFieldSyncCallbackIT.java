/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.instancefield;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for synchronous instance field intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the end-to-end callback mechanism for synchronous intercepts on instance
 * field operations (EXEC_GET_FIELD and EXEC_PUT_FIELD), including single callbacks for both BEFORE
 * and AFTER intercept types.
 */
public class InstanceFieldSyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests single BEFORE callback on instance field GET operation.
   *
   * <p>Registers a BEFORE intercept on getCounter (which triggers EXEC_GET_FIELD), calls it once,
   * and verifies exactly 1 callback is received.
   */
  @Test
  public void testSingleBeforeCallbackOnGet() throws Exception {
    logger.info("===== testSingleBeforeCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    // 1. Register a BEFORE intercept on counter field GET
    logger.info("Creating BEFORE intercept request for counter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.BEFORE,
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
    logger.info("Invoking getCounter() which should trigger 1 callback");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));
    logger.info("getCounter invocation completed");

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 6. Verify callback structure
    Message callback = callbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be INTERCEPT_CALLBACK_REQUEST type",
        callback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "Callback class should match",
        callback.getInterceptCallbackRequest().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequest().getCallbackMethod(),
        is(callbackMethod));
    // Verify the intercepted operation is an instance field GET
    assertThat(
        "Intercepted operation should be InstanceFieldGet",
        callback.getInterceptCallbackRequest().getExec().getInstanceFieldGet(),
        is(notNullValue()));

    logger.info("===== testSingleBeforeCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on instance field GET operation.
   *
   * <p>Registers an AFTER intercept on getCounter, calls it once, and verifies exactly 1 callback
   * is received after the field get.
   */
  @Test
  public void testSingleAfterCallbackOnGet() throws Exception {
    logger.info("===== testSingleAfterCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    // 1. Register an AFTER intercept on counter field GET
    logger.info("Creating AFTER intercept request for counter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.AFTER,
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
    logger.info("Invoking getCounter() which should trigger 1 callback");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));
    logger.info("getCounter invocation completed");

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 6. Verify callback structure
    Message callback = callbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be INTERCEPT_CALLBACK_REQUEST type",
        callback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "Callback class should match",
        callback.getInterceptCallbackRequest().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequest().getCallbackMethod(),
        is(callbackMethod));
    // AFTER GET callbacks wrap the ReturnValue (the field value that was read)
    assertThat(
        "Intercepted operation should have ReturnValue",
        callback.getInterceptCallbackRequest().getExec().getReturnValue(),
        is(notNullValue()));

    logger.info("===== testSingleAfterCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single BEFORE callback on instance field PUT operation.
   *
   * <p>Registers a BEFORE intercept on setCounter (which triggers EXEC_PUT_FIELD), calls it once,
   * and verifies exactly 2 callbacks are received (PUT + PUT_DONE).
   */
  @Test
  public void testSingleBeforeCallbackOnPut() throws Exception {
    logger.info("===== testSingleBeforeCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // 1. Register a BEFORE intercept on counter field PUT
    logger.info("Creating BEFORE intercept request for counter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.BEFORE,
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

    // 3. Invoke setCounter which triggers PUT_FIELD and callback
    // Note: PUT operations generate 2 messages (PUT + PUT_DONE), so we expect 2 callbacks
    logger.info(
        "Invoking setCounter({}) which should trigger 2 callbacks (PUT + PUT_DONE)", newValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "setCounter",
                appInstance,
                new String[] {"java.lang.Integer"},
                new Object[] {newValue}));
    logger.info("setCounter invocation completed");

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Retrieve and verify callbacks
    logger.info("Waiting for 2 callbacks to be received (PUT + PUT_DONE)");
    List<Message> callbacks = getCallbacks(2, 5000);
    logger.info("Callbacks received successfully");

    assertThat("Should receive exactly 2 callbacks (PUT + PUT_DONE)", callbacks.size(), is(2));

    // 6. Verify callback structure - first callback should be PUT, second should be PUT_DONE
    Message putCallback = callbacks.get(0);
    assertThat("PUT callback message should not be null", putCallback, is(notNullValue()));
    assertThat(
        "PUT callback should be INTERCEPT_CALLBACK_REQUEST type",
        putCallback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "PUT callback class should match",
        putCallback.getInterceptCallbackRequest().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "PUT callback method should match",
        putCallback.getInterceptCallbackRequest().getCallbackMethod(),
        is(callbackMethod));
    // BEFORE PUT callback wraps the PUT operation
    assertThat(
        "First callback should be InstanceFieldPut",
        putCallback.getInterceptCallbackRequest().getExec().getInstanceFieldPut(),
        is(notNullValue()));

    logger.info("===== testSingleBeforeCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on instance field PUT operation.
   *
   * <p>Registers an AFTER intercept on setCounter, calls it once, and verifies exactly 1 callback
   * is received after the field put.
   */
  @Test
  public void testSingleAfterCallbackOnPut() throws Exception {
    logger.info("===== testSingleAfterCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // 1. Register an AFTER intercept on counter field PUT
    logger.info("Creating AFTER intercept request for counter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.AFTER,
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

    // 3. Invoke setCounter which triggers PUT_FIELD and callback
    // Note: PUT operations generate 2 messages (PUT + PUT_DONE), so we expect 2 callbacks
    logger.info(
        "Invoking setCounter({}) which should trigger 2 callbacks (PUT + PUT_DONE)", newValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "setCounter",
                appInstance,
                new String[] {"java.lang.Integer"},
                new Object[] {newValue}));
    logger.info("setCounter invocation completed");

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Retrieve and verify callbacks
    logger.info("Waiting for 2 callbacks to be received (PUT + PUT_DONE)");
    List<Message> callbacks = getCallbacks(2, 5000);
    logger.info("Callbacks received successfully");

    assertThat("Should receive exactly 2 callbacks (PUT + PUT_DONE)", callbacks.size(), is(2));

    // 6. Verify callback structure - AFTER PUT callbacks wrap PUT_DONE
    Message putDoneCallback = callbacks.get(1);
    assertThat("PUT_DONE callback message should not be null", putDoneCallback, is(notNullValue()));
    assertThat(
        "PUT_DONE callback should be INTERCEPT_CALLBACK_REQUEST type",
        putDoneCallback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "PUT_DONE callback class should match",
        putDoneCallback.getInterceptCallbackRequest().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "PUT_DONE callback method should match",
        putDoneCallback.getInterceptCallbackRequest().getCallbackMethod(),
        is(callbackMethod));
    // AFTER PUT callback wraps the PUT_DONE operation
    assertThat(
        "Second callback should be InstanceFieldPutDone",
        putDoneCallback.getInterceptCallbackRequest().getExec().getInstanceFieldPutDone(),
        is(notNullValue()));

    logger.info("===== testSingleAfterCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }
}
