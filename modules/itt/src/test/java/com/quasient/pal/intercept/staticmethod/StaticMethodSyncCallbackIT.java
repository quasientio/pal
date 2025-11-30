/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.staticmethod;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for synchronous static method intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the end-to-end callback mechanism for synchronous intercepts on static
 * methods (EXEC_CLASS_METHOD), including single and multiple callbacks for both BEFORE and AFTER
 * intercept types.
 *
 * <p><b>NOTE:</b>These tests verify intercepts at the hot-path (via quantization, which happens at
 * the call-site), and so, we need to invoke via RPC a method/ctor that triggers the actual
 * interception target.
 */
public class StaticMethodSyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests single BEFORE callback on static method.
   *
   * <p>Registers a BEFORE intercept on incrementStaticCounter, calls a wrapper that invokes it once
   * and verifies the intercept mechanism works without throwing.
   */
  @Test
  public void testSingleBeforeCallback() throws Exception {
    logger.info("===== testSingleBeforeCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    // 1. Register a BEFORE intercept on incrementStaticCounter static method
    logger.info("Creating BEFORE intercept request for incrementStaticCounter static method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            myPeerUuid,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("incrementStaticCounter", Collections.emptyList()));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create InterceptableApp instance for calling wrapper method
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

    // 3. Invoke callIncrementStaticCounter (triggers incrementStaticCounter via call-site)
    logger.info("Invoking callIncrementStaticCounter wrapper which should trigger 1 callback");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "callIncrementStaticCounter",
                appInstance,
                new String[] {},
                new Object[] {}));
    logger.info("callIncrementStaticCounter invocation completed");

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
    // BEFORE callbacks receive method parameters (incrementStaticCounter has no params)
    assertThat(
        "BEFORE callback should have 0 parameters",
        callback
            .getInterceptCallbackRequest()
            .getExec()
            .getClassMethodCall()
            .getParameters()
            .length,
        is(0));

    logger.info("===== testSingleBeforeCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE callbacks on static method.
   *
   * <p>Registers a BEFORE intercept on multiplyStaticBy, invokes a wrapper method that calls it n=3
   * times, and verifies exactly 3 callbacks are received.
   */
  @Test
  public void testMultipleBeforeCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;
    final int multiplier = 2;

    // 1. Register a BEFORE intercept on multiplyStaticBy static method
    logger.info("Creating BEFORE intercept request for multiplyStaticBy static method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            myPeerUuid,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "multiplyStaticBy", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create InterceptableApp instance for calling wrapper method
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

    // 3. Invoke callMultiplyStaticByNTimes which internally calls multiplyStaticBy n times
    logger.info(
        "Invoking callMultiplyStaticByNTimes(n={}, multiplier={}) which should trigger {} callback(s)",
        n,
        multiplier,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "callMultiplyStaticByNTimes",
                appInstance,
                new String[] {"java.lang.Integer", "java.lang.Integer"},
                new Object[] {n, multiplier}));
    logger.info("callMultiplyStaticByNTimes invocation completed");

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Retrieve and verify callbacks
    logger.info("Waiting for {} callback(s) to be received", n);
    List<Message> callbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    assertThat("Should receive exactly " + n + " callback(s)", callbacks.size(), is(n));

    // 6. Verify callback structure
    for (int i = 0; i < n; i++) {
      Message callback = callbacks.get(i);
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
      // BEFORE callbacks receive method parameters (multiplyStaticBy has 1 Integer parameter)
      assertThat(
          "BEFORE callback should have 1 parameter (the Integer argument)",
          callback
              .getInterceptCallbackRequest()
              .getExec()
              .getClassMethodCall()
              .getParameters()
              .length,
          is(1));
    }

    logger.info("===== testMultipleBeforeCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on static method.
   *
   * <p>Registers an AFTER intercept on multiplyStaticBy, calls a wrapper that invokes it once, and
   * verifies exactly 1 callback is received after method execution.
   */
  @Test
  public void testSingleAfterCallback() throws Exception {
    logger.info("===== testSingleAfterCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int multiplier = 3;

    // 1. Register an AFTER intercept on multiplyStaticBy static method
    logger.info("Creating AFTER intercept request for multiplyStaticBy static method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "multiplyStaticBy", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create InterceptableApp instance for calling wrapper method
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

    // 3. Invoke callMultiplyStaticBy which triggers multiplyStaticBy via call-site
    logger.info(
        "Invoking callMultiplyStaticBy wrapper(multiplier={}) which should trigger 1 callback",
        multiplier);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "callMultiplyStaticBy",
                appInstance,
                new String[] {"java.lang.Integer"},
                new Object[] {multiplier}));
    logger.info("callMultiplyStaticBy invocation completed");

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
    // AFTER callbacks wrap ReturnValue, not ClassMethodCall
    // Verify the return value structure for non-void method (returns Integer)
    assertThat(
        "AFTER callback should have ReturnValue in exec",
        callback.getInterceptCallbackRequest().getExec().getReturnValue(),
        is(notNullValue()));
    assertThat(
        "multiplyStaticBy returns Integer, so isVoid should be false",
        callback.getInterceptCallbackRequest().getExec().getReturnValue().isVoid,
        is(false));
    assertThat(
        "ReturnValue should have the return object",
        callback.getInterceptCallbackRequest().getExec().getReturnValue().getObject(),
        is(notNullValue()));
    assertThat(
        "ReturnValue should have method info",
        callback.getInterceptCallbackRequest().getExec().getReturnValue().getFrom().getMethod(),
        is(notNullValue()));

    logger.info("===== testSingleAfterCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER callbacks on static method.
   *
   * <p>Registers an AFTER intercept on multiplyStaticBy, invokes a wrapper method that calls it n=3
   * times, and verifies exactly 3 callbacks are received after method executions.
   */
  @Test
  public void testMultipleAfterCallbacks() throws Exception {
    logger.info("===== testMultipleAfterCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;
    final int multiplier = 2;

    // 1. Register an AFTER intercept on multiplyStaticBy static method
    logger.info("Creating AFTER intercept request for multiplyStaticBy static method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "multiplyStaticBy", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create InterceptableApp instance for calling wrapper method
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

    // 3. Invoke callMultiplyStaticByNTimes which internally calls multiplyStaticBy n times
    logger.info(
        "Invoking callMultiplyStaticByNTimes(n={}, multiplier={}) which should trigger {} callback(s)",
        n,
        multiplier,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "callMultiplyStaticByNTimes",
                appInstance,
                new String[] {"java.lang.Integer", "java.lang.Integer"},
                new Object[] {n, multiplier}));
    logger.info("callMultiplyStaticByNTimes invocation completed");

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Retrieve and verify callbacks
    logger.info("Waiting for {} callback(s) to be received", n);
    List<Message> callbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    assertThat("Should receive exactly " + n + " callback(s)", callbacks.size(), is(n));

    // 6. Verify callback structure
    for (int i = 0; i < n; i++) {
      Message callback = callbacks.get(i);
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
      // AFTER callbacks wrap ReturnValue, not ClassMethodCall
      // Verify the return value structure for non-void method (returns Integer)
      assertThat(
          "AFTER callback should have ReturnValue in exec",
          callback.getInterceptCallbackRequest().getExec().getReturnValue(),
          is(notNullValue()));
      assertThat(
          "multiplyStaticBy returns Integer, so isVoid should be false",
          callback.getInterceptCallbackRequest().getExec().getReturnValue().isVoid,
          is(false));
      assertThat(
          "ReturnValue should have the return object",
          callback.getInterceptCallbackRequest().getExec().getReturnValue().getObject(),
          is(notNullValue()));
      assertThat(
          "ReturnValue should have method info",
          callback.getInterceptCallbackRequest().getExec().getReturnValue().getFrom().getMethod(),
          is(notNullValue()));
    }

    logger.info("===== testMultipleAfterCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
