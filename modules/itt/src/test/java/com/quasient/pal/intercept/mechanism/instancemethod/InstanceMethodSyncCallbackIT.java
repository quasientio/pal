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
 * Integration tests for synchronous instance method intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the end-to-end callback mechanism for synchronous intercepts on instance
 * methods (EXEC_INSTANCE_METHOD), including single and multiple callbacks for both BEFORE and AFTER
 * intercept types.
 *
 * <p><b>NOTE:</b>These tests verify intercepts at the hot-path (via quantization, which happens at
 * the call-site), and so, we need to invoke via RPC a method/ctor that triggers the actual
 * interception target.
 */
public class InstanceMethodSyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests single BEFORE callback.
   *
   * <p>Registers a BEFORE intercept on multiplyBy, calls a wrapper that invokes it once (n=1), and
   * verifies the intercept mechanism works without throwing, the number of expected callbacks are
   * received, and their types and parameters if any, are as expected.
   */
  @Test
  public void testSingleBeforeCallback() throws Exception {
    logger.info("===== testSingleBeforeCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 1; // Number of times to call multiplyBy
    final int multiplier = 3;

    // 1. Register a BEFORE intercept on multiplyBy method
    logger.info("Creating BEFORE intercept request for multiplyBy method");
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
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) which should trigger {} callback(s)",
        n,
        multiplier,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "multiplyCounterNTimesBy",
                appInstance,
                new String[] {"java.lang.Integer", "java.lang.Integer"},
                new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed");

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
          String.format(
              "Callback should be %s type", MessageType.INTERCEPT_CALLBACK_REQUEST.name()),
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
      // BEFORE callbacks receive the method parameters
      // multiplyBy(Integer) has 1 parameter
      assertThat(
          "BEFORE callback should have 1 parameter (the Integer argument)",
          callback
              .getInterceptCallbackRequest()
              .getExec()
              .getInstanceMethodCall()
              .getParameters()
              .length,
          is(1));
    }

    logger.info("===== testSingleBeforeCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE callbacks.
   *
   * <p>Registers a BEFORE intercept on multiplyBy, invokes a wrapper method that internally calls
   * multiplyBy n=3 times, and verifies exactly 3 callbacks are received.
   */
  @Test
  public void testMultipleBeforeCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3; // Number of times to call multiplyBy
    final int multiplier = 2;

    // 1. Register a BEFORE intercept on multiplyBy method
    logger.info("Creating BEFORE intercept request for multiplyBy method");
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

    // 3. Invoke multiplyCounterNTimesBy which internally calls multiplyBy n times
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) which should trigger {} callback(s)",
        n,
        multiplier,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "multiplyCounterNTimesBy",
                appInstance,
                new String[] {"java.lang.Integer", "java.lang.Integer"},
                new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed");

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
          String.format(
              "Callback should be %s type", MessageType.INTERCEPT_CALLBACK_REQUEST.name()),
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
      // BEFORE callbacks receive the method parameters
      // multiplyBy(Integer) has 1 parameter
      assertThat(
          "BEFORE callback should have 1 parameter (the Integer argument)",
          callback
              .getInterceptCallbackRequest()
              .getExec()
              .getInstanceMethodCall()
              .getParameters()
              .length,
          is(1));
    }

    logger.info("===== testMultipleBeforeCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback.
   *
   * <p>Registers an AFTER intercept on multiplyBy, invokes a wrapper method that calls it once
   * (n=1), and verifies exactly 1 callback is received after method execution.
   */
  @Test
  public void testSingleAfterCallback() throws Exception {
    logger.info("===== testSingleAfterCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 1; // Number of times to call multiplyBy
    final int multiplier = 3;

    // 1. Register an AFTER intercept on multiplyBy method
    logger.info("Creating AFTER intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
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

    // 3. Invoke multiplyCounterNTimesBy which internally calls multiplyBy and triggers callback
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) which should trigger {} callback(s)",
        n,
        multiplier,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "multiplyCounterNTimesBy",
                appInstance,
                new String[] {"java.lang.Integer", "java.lang.Integer"},
                new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed");

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
      // AFTER callbacks wrap ReturnValue, not InstanceMethodCall
      // Verify the return value structure for void method
      assertThat(
          "AFTER callback should have ReturnValue in exec",
          callback.getInterceptCallbackRequest().getExec().getReturnValue(),
          is(notNullValue()));
      assertThat(
          "multiplyBy returns void, so isVoid should be true",
          callback.getInterceptCallbackRequest().getExec().getReturnValue().isVoid,
          is(true));
      assertThat(
          "ReturnValue should have method info",
          callback.getInterceptCallbackRequest().getExec().getReturnValue().getFrom().getMethod(),
          is(notNullValue()));
    }

    logger.info("===== testSingleAfterCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER callbacks.
   *
   * <p>Registers an AFTER intercept on multiplyBy, invokes a wrapper method that internally calls
   * multiplyBy n=3 times, and verifies exactly 3 callbacks are received.
   */
  @Test
  public void testMultipleAfterCallbacks() throws Exception {
    logger.info("===== testMultipleAfterCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3; // Number of times to call multiplyBy
    final int multiplier = 2;

    // 1. Register an AFTER intercept on multiplyBy method
    logger.info("Creating AFTER intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
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

    // 3. Invoke multiplyCounterNTimesBy which internally calls multiplyBy n times
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) which should trigger {} callback(s)",
        n,
        multiplier,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "multiplyCounterNTimesBy",
                appInstance,
                new String[] {"java.lang.Integer", "java.lang.Integer"},
                new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed");

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
      // AFTER callbacks wrap ReturnValue, not InstanceMethodCall
      // Verify the return value structure for void method
      assertThat(
          "AFTER callback should have ReturnValue in exec",
          callback.getInterceptCallbackRequest().getExec().getReturnValue(),
          is(notNullValue()));
      assertThat(
          "multiplyBy returns void, so isVoid should be true",
          callback.getInterceptCallbackRequest().getExec().getReturnValue().isVoid,
          is(true));
      assertThat(
          "ReturnValue should have method info",
          callback.getInterceptCallbackRequest().getExec().getReturnValue().getFrom().getMethod(),
          is(notNullValue()));
    }

    logger.info("===== testMultipleAfterCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
