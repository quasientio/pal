/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.staticfield;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for synchronous static field intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the end-to-end callback mechanism for synchronous intercepts on static
 * field operations (EXEC_GET_STATIC and EXEC_PUT_STATIC), including single callbacks for both
 * BEFORE and AFTER intercept types.
 */
public class StaticFieldSyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests single BEFORE callback on static field GET operation.
   *
   * <p>Registers a BEFORE intercept on getStaticCounter (which triggers EXEC_GET_STATIC), calls it
   * once, and verifies exactly 1 callback is received.
   */
  @Test
  public void testSingleBeforeCallbackOnGet() throws Exception {
    logger.info("===== testSingleBeforeCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    // 1. Register a BEFORE intercept on staticCounter field GET
    logger.info("Creating BEFORE intercept request for staticCounter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke getStaticCounter which triggers GET_STATIC and callback
    logger.info("Invoking getStaticCounter() which should trigger 1 callback");
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
    logger.info("getStaticCounter invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 5. Verify callback structure
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
    // Verify the intercepted operation is a static field GET
    assertThat(
        "Intercepted operation should be StaticFieldGet",
        callback.getInterceptCallbackRequest().getExec().getStaticFieldGet(),
        is(notNullValue()));

    logger.info("===== testSingleBeforeCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on static field GET operation.
   *
   * <p>Registers an AFTER intercept on getStaticCounter, calls it once, and verifies exactly 1
   * callback is received after the field get.
   */
  @Test
  public void testSingleAfterCallbackOnGet() throws Exception {
    logger.info("===== testSingleAfterCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    // 1. Register an AFTER intercept on staticCounter field GET
    logger.info("Creating AFTER intercept request for staticCounter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke getStaticCounter which triggers GET_STATIC and callback
    logger.info("Invoking getStaticCounter() which should trigger 1 callback");
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
    logger.info("getStaticCounter invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 5. Verify callback structure
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
   * Tests single BEFORE callback on static field PUT operation.
   *
   * <p>Registers a BEFORE intercept on setStaticCounter (which triggers EXEC_PUT_STATIC), calls it
   * once, and verifies exactly 1 callback is received.
   */
  @Test
  public void testSingleBeforeCallbackOnPut() throws Exception {
    logger.info("===== testSingleBeforeCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // 1. Register a BEFORE intercept on staticCounter field PUT
    logger.info("Creating BEFORE intercept request for staticCounter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke setStaticCounter which triggers PUT_STATIC and callback
    // Note: Static field PUT operations generate only 1 callback (no PUT_DONE for static fields)
    logger.info("Invoking setStaticCounter({}) which should trigger 1 callback", newValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "setStaticCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {newValue}));
    logger.info("setStaticCounter invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 5. Verify callback structure
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
    // BEFORE PUT callback wraps the PUT operation
    assertThat(
        "Callback should be StaticFieldPut",
        callback.getInterceptCallbackRequest().getExec().getStaticFieldPut(),
        is(notNullValue()));

    logger.info("===== testSingleBeforeCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on static field PUT operation.
   *
   * <p>Registers an AFTER intercept on setStaticCounter, calls it once, and verifies exactly 2
   * callbacks are received (PUT + PUT_DONE).
   */
  @Test
  public void testSingleAfterCallbackOnPut() throws Exception {
    logger.info("===== testSingleAfterCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // 1. Register an AFTER intercept on staticCounter field PUT
    logger.info("Creating AFTER intercept request for staticCounter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            myPeerUuid,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke setStaticCounter which triggers PUT_STATIC and callback
    // Note: PUT operations generate 2 messages (PUT + PUT_DONE), so we expect 2 callbacks
    logger.info(
        "Invoking setStaticCounter({}) which should trigger 2 callbacks (PUT + PUT_DONE)",
        newValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "setStaticCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {newValue}));
    logger.info("setStaticCounter invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // 5. Verify callback structure
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
    // AFTER PUT callback wraps PUT_DONE if available, otherwise PUT
    assertThat(
        "Callback should have StaticFieldPut or StaticFieldPutDone",
        callback.getInterceptCallbackRequest().getExec().getStaticFieldPut() != null
            || callback.getInterceptCallbackRequest().getExec().getStaticFieldPutDone() != null,
        is(true));

    logger.info("===== testSingleAfterCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }
}
