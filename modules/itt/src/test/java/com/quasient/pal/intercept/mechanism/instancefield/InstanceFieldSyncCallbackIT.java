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
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for synchronous instance field intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the callback mechanism for synchronous intercepts on instance field
 * operations (EXEC_GET_FIELD and EXEC_PUT_FIELD), including single callbacks for both BEFORE and
 * AFTER intercept types.
 *
 * <p>Tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Intercepts triggered via AspectJ weaving at call-site (getter/setter
 *       method accesses field)
 *   <li><b>INCOMING_RPC</b>: Intercepts triggered via direct RPC message dispatch
 * </ul>
 */
@RunWith(Parameterized.class)
public class InstanceFieldSyncCallbackIT extends AbstractInterceptIT {

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public InstanceFieldSyncCallbackIT(InvocationPath path) {
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
   * Invokes a GET on the counter field through the specified invocation path.
   *
   * @param appInstance the target object
   * @return the response ExecMessage
   */
  private ExecMessage invokeFieldGet(ObjectRef appInstance) {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use getter method that accesses field
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "getCounter",
              appInstance,
              new String[] {},
              new Object[] {}));
    } else {
      // INCOMING_RPC: Access field directly
      return invoke(
          messageBuilder.buildGetObject(
              myPeerUuid, InterceptableApp.class.getName(), "counter", appInstance));
    }
  }

  /**
   * Invokes a PUT on the counter field through the specified invocation path.
   *
   * @param appInstance the target object
   * @param value the value to set
   * @return the response ExecMessage
   */
  private ExecMessage invokeFieldPut(ObjectRef appInstance, int value) {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use setter method that accesses field
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "setCounter",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {value}));
    } else {
      // INCOMING_RPC: Access field directly
      return invoke(
          messageBuilder.buildPutObject(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "counter",
              appInstance,
              "java.lang.Integer",
              value));
    }
  }

  /**
   * Tests single BEFORE callback on instance field GET operation.
   *
   * <p>Registers a BEFORE intercept on counter, creates an app instance, invokes a field GET, and
   * verifies exactly 1 callback is received with correct structure.
   */
  @Test
  public void testSingleBeforeCallbackOnGet() throws Exception {
    logger.info("===== testSingleBeforeCallbackOnGet [{}]: TEST STARTED =====", path);

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

    // 3. Invoke field GET through the specified path
    logger.info("Invoking counter GET via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeFieldGet(appInstance);
    logger.info("counter GET invocation completed");

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
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    // Verify the intercepted operation is an instance field GET
    assertThat(
        "Intercepted operation should be InstanceFieldGet",
        callback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldGet(),
        is(notNullValue()));

    logger.info(
        "===== testSingleBeforeCallbackOnGet [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests single AFTER callback on instance field GET operation.
   *
   * <p>Registers an AFTER intercept on counter, creates an app instance, invokes a field GET, and
   * verifies exactly 1 callback is received after the field get.
   */
  @Test
  public void testSingleAfterCallbackOnGet() throws Exception {
    logger.info("===== testSingleAfterCallbackOnGet [{}]: TEST STARTED =====", path);

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

    // 3. Invoke field GET through the specified path
    logger.info("Invoking counter GET via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeFieldGet(appInstance);
    logger.info("counter GET invocation completed");

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
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    // AFTER GET callbacks wrap the ReturnValue (the field value that was read)
    assertThat(
        "Intercepted operation should have ReturnValue",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue(),
        is(notNullValue()));
    assertThat(
        "The return value should not be void",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().isVoid,
        is(false));
    assertThat(
        "ReturnValue should have field info",
        callback
            .getInterceptCallbackRequestMessage()
            .getExec()
            .getReturnValue()
            .getFrom()
            .getField(),
        is(notNullValue()));

    logger.info("===== testSingleAfterCallbackOnGet [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests BEFORE callback on instance field PUT operation.
   *
   * <p>Registers a BEFORE intercept on counter, creates an app instance, invokes a field PUT, and
   * verifies callbacks are received.
   *
   * <p>For HOT_PATH: The field initializer also triggers a callback, so we expect 2 callbacks. For
   * INCOMING_RPC: Only the direct PUT triggers a callback, so we expect 1 callback. To achieve
   * this, we create the instance before registering the intercept for INCOMING_RPC.
   */
  @Test
  public void testBeforeCallbackOnPut() throws Exception {
    logger.info("===== testBeforeCallbackOnPut [{}]: TEST STARTED =====", path);

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
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
    logger.info("Invoking counter PUT via {} path", path);
    ExecMessage response = invokeFieldPut(appInstance, newValue);
    logger.info("counter PUT invocation completed");

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Retrieve and verify callbacks
    // HOT_PATH: 2 callbacks (1 from field initializer in ctor, 1 from setter)
    // INCOMING_RPC: 1 callback (only the direct PUT)
    int expectedCallbacks = (path == InvocationPath.HOT_PATH) ? 2 : 1;
    logger.info("Waiting for {} callback(s) to be received", expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("Callbacks received successfully");

    assertThat(
        "Should receive exactly " + expectedCallbacks + " callback(s)",
        callbacks.size(),
        is(expectedCallbacks));

    // 6. Verify callback structure
    if (path == InvocationPath.HOT_PATH) {
      // The first callback is from the field put triggered by the field initializer
      // when we created the app instance
      Message initCallback = callbacks.get(0);
      assertThat("Callback message should not be null", initCallback, is(notNullValue()));
      assertThat(
          "PUT callback should be INTERCEPT_CALLBACK_REQUEST type",
          initCallback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "PUT callback class should match",
          initCallback.getInterceptCallbackRequestMessage().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "PUT callback method should match",
          initCallback.getInterceptCallbackRequestMessage().getCallbackMethod(),
          is(callbackMethod));
      assertThat(
          "First callback should be InstanceFieldPut",
          initCallback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPut(),
          is(notNullValue()));

      // verify value passed to fieldput is the initializer value in the app
      Obj putValueObj =
          initCallback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getInstanceFieldPut()
              .getValueObject();
      Object value = Unwrapper.unwrapObject(putValueObj);
      assertThat("First callback put value should be 1 (initializer value in class)", value, is(1));

      // The second callback is from the field put triggered by the setter we invoke
      Message setterCallback = callbacks.get(1);
      assertThat("Callback message should not be null", setterCallback, is(notNullValue()));
      assertThat(
          "Second callback should be InstanceFieldPut",
          setterCallback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPut(),
          is(notNullValue()));

      // verify value passed to fieldput is the value we call the setter with
      putValueObj =
          setterCallback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getInstanceFieldPut()
              .getValueObject();
      value = Unwrapper.unwrapObject(putValueObj);
      assertThat("Second callback put value should be value passed to setter", value, is(newValue));
    } else {
      // INCOMING_RPC: Only one callback from the direct PUT
      Message callback = callbacks.get(0);
      assertThat("Callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "PUT callback should be INTERCEPT_CALLBACK_REQUEST type",
          callback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "PUT callback class should match",
          callback.getInterceptCallbackRequestMessage().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "PUT callback method should match",
          callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
          is(callbackMethod));
      assertThat(
          "Callback should be InstanceFieldPut",
          callback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPut(),
          is(notNullValue()));

      // verify value passed to fieldput is the value we set
      Obj putValueObj =
          callback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getInstanceFieldPut()
              .getValueObject();
      Object value = Unwrapper.unwrapObject(putValueObj);
      assertThat("Callback put value should be value we set", value, is(newValue));
    }

    logger.info("===== testBeforeCallbackOnPut [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests AFTER callback on instance field PUT operation.
   *
   * <p>Registers an AFTER intercept on counter, creates an app instance, invokes a field PUT, and
   * verifies callbacks are received after the field put.
   *
   * <p>For HOT_PATH: The field initializer also triggers a callback, so we expect 2 callbacks. For
   * INCOMING_RPC: Only the direct PUT triggers a callback, so we expect 1 callback. To achieve
   * this, we create the instance before registering the intercept for INCOMING_RPC.
   */
  @Test
  public void testAfterCallbackOnPut() throws Exception {
    logger.info("===== testAfterCallbackOnPut [{}]: TEST STARTED =====", path);

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
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
    logger.info("Invoking counter PUT via {} path", path);
    ExecMessage response = invokeFieldPut(appInstance, newValue);
    logger.info("counter PUT invocation completed");

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Retrieve and verify callbacks
    // HOT_PATH: 2 callbacks (1 from field initializer in ctor, 1 from setter)
    // INCOMING_RPC: 1 callback (only the direct PUT)
    int expectedCallbacks = (path == InvocationPath.HOT_PATH) ? 2 : 1;
    logger.info("Waiting for {} callback(s) to be received", expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("Callbacks received successfully");

    assertThat(
        "Should receive exactly " + expectedCallbacks + " callback(s)",
        callbacks.size(),
        is(expectedCallbacks));

    // 6. Verify callback structure
    if (path == InvocationPath.HOT_PATH) {
      // The first callback is from the field put triggered by the field initializer
      Message initCallback = callbacks.get(0);
      assertThat("PUT_DONE callback message should not be null", initCallback, is(notNullValue()));
      assertThat(
          "PUT_DONE callback should be INTERCEPT_CALLBACK_REQUEST type",
          initCallback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "PUT_DONE callback class should match",
          initCallback.getInterceptCallbackRequestMessage().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "PUT_DONE callback method should match",
          initCallback.getInterceptCallbackRequestMessage().getCallbackMethod(),
          is(callbackMethod));
      // AFTER PUT callback wraps the PUT_DONE operation
      assertThat(
          "Callback should be InstanceFieldPutDone",
          initCallback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPutDone(),
          is(notNullValue()));
      assertThat(
          "InstanceFieldPutDone should have field info",
          initCallback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getInstanceFieldPutDone()
              .getField(),
          is(notNullValue()));

      // The second callback is from the field put triggered by the setter we invoke
      Message setterCallback = callbacks.get(1);
      assertThat(
          "PUT_DONE callback message should not be null", setterCallback, is(notNullValue()));
      assertThat(
          "Callback should be InstanceFieldPutDone",
          setterCallback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPutDone(),
          is(notNullValue()));
    } else {
      // INCOMING_RPC: Only one callback from the direct PUT
      Message callback = callbacks.get(0);
      assertThat("PUT_DONE callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "PUT_DONE callback should be INTERCEPT_CALLBACK_REQUEST type",
          callback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "PUT_DONE callback class should match",
          callback.getInterceptCallbackRequestMessage().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "PUT_DONE callback method should match",
          callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
          is(callbackMethod));
      // AFTER PUT callback wraps the PUT_DONE operation
      assertThat(
          "Callback should be InstanceFieldPutDone",
          callback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPutDone(),
          is(notNullValue()));
      assertThat(
          "InstanceFieldPutDone should have field info",
          callback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getInstanceFieldPutDone()
              .getField(),
          is(notNullValue()));
    }

    logger.info("===== testAfterCallbackOnPut [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }
}
