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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

/**
 * Integration tests for synchronous constructor intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the end-to-end callback mechanism for synchronous intercepts on
 * constructors (INTERCEPT_CALLBACK_REQUEST), including single and multiple callbacks for both
 * BEFORE and AFTER intercept types.
 *
 * <p><b>NOTE:</b>These tests verify intercepts at the hot-path (via quantization, which happens at
 * the call-site), and so, we need to invoke via RPC a method/ctor that triggers the actual
 * interception target.
 */
public class ConstructorSyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /**
   * Tests single BEFORE callback on constructor.
   *
   * <p>Registers a BEFORE intercept on the parameterized constructor, invokes a factory method that
   * calls it once, and verifies exactly 1 callback is received.
   */
  @Test
  public void testSingleBeforeCallback() throws Exception {
    logger.info("===== testSingleBeforeCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int initialValue = 10;

    // 1. Register a BEFORE intercept on parameterized constructor
    logger.info("Creating BEFORE intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            myPeerUuid,
            InterceptType.BEFORE,
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

    // 2. Invoke factory method which internally calls constructor (triggers intercept via
    // call-site)
    logger.info(
        "Invoking createWithCounter factory method with initialValue={} which should trigger 1 callback",
        initialValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {initialValue}));
    logger.info("Factory method invocation completed");

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
    // BEFORE callbacks receive the constructor parameters
    // Constructor(Integer) has 1 parameter
    assertThat(
        "BEFORE callback should have 1 parameter (the Integer argument)",
        callback
            .getInterceptCallbackRequest()
            .getExec()
            .getConstructorCall()
            .getParameters()
            .length,
        is(1));

    logger.info("===== testSingleBeforeCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE callbacks on constructor.
   *
   * <p>Registers a BEFORE intercept on the parameterized constructor, invokes a factory method that
   * creates n=3 instances, and verifies exactly 3 callbacks are received.
   */
  @Test
  public void testMultipleBeforeCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;
    final int initialValue = 10;

    // 1. Register a BEFORE intercept on parameterized constructor
    logger.info("Creating BEFORE intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            myPeerUuid,
            InterceptType.BEFORE,
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

    // 2. Invoke createNInstances which internally calls constructor n times
    logger.info(
        "Invoking createNInstances(n={}, initialValue={}) which should trigger {} callback(s)",
        n,
        initialValue,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createNInstances",
                new String[] {"java.lang.Integer", "java.lang.Integer"},
                null,
                null,
                new Object[] {n, initialValue}));
    logger.info("createNInstances invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for {} callback(s) to be received", n);
    List<Message> callbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    assertThat("Should receive exactly " + n + " callback(s)", callbacks.size(), is(n));

    // 5. Verify callback structure
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
      // BEFORE callbacks receive the constructor parameters
      // Constructor(Integer) has 1 parameter
      assertThat(
          "BEFORE callback should have 1 parameter (the Integer argument)",
          callback
              .getInterceptCallbackRequest()
              .getExec()
              .getConstructorCall()
              .getParameters()
              .length,
          is(1));
    }

    logger.info("===== testMultipleBeforeCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on constructor.
   *
   * <p>Registers a AFTER intercept on the parameterized constructor, invokes a factory method that
   * calls it once, and verifies exactly 1 callback is received after constructor execution.
   */
  @Test
  public void testSingleAfterCallback() throws Exception {
    logger.info("===== testSingleAfterCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int initialValue = 10;

    // 1. Register an AFTER intercept on parameterized constructor
    logger.info("Creating AFTER intercept request for parameterized constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
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

    // 2. Invoke factory method which internally calls constructor (triggers intercept via
    // call-site)
    logger.info(
        "Invoking createWithCounter factory method with initialValue={} which should trigger 1 callback",
        initialValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {initialValue}));
    logger.info("Factory method invocation completed");

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
    // AFTER callbacks wrap ReturnValue, not ConstructorCall
    // Verify the return value structure for constructor (returns the constructed object)
    assertThat(
        "AFTER callback should have ReturnValue in exec",
        callback.getInterceptCallbackRequest().getExec().getReturnValue(),
        is(notNullValue()));
    assertThat(
        "Constructor returns object, so isVoid should be false",
        callback.getInterceptCallbackRequest().getExec().getReturnValue().isVoid,
        is(false));
    assertThat(
        "ReturnValue should have the constructed object",
        callback.getInterceptCallbackRequest().getExec().getReturnValue().getObject(),
        is(notNullValue()));
    assertThat(
        "ReturnValue should have constructor info",
        callback
            .getInterceptCallbackRequest()
            .getExec()
            .getReturnValue()
            .getFrom()
            .getConstructor(),
        is(notNullValue()));

    logger.info("===== testSingleAfterCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER callbacks on constructor.
   *
   * <p>Registers an AFTER intercept on the parameterized constructor, invokes a factory method that
   * creates n=3 instances, and verifies exactly 3 callbacks are received.
   */
  @Test
  public void testMultipleAfterCallbacks() throws Exception {
    logger.info("===== testMultipleAfterCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;
    final int initialValue = 10;

    // 1. Register an AFTER intercept on parameterized constructor
    logger.info("Creating AFTER intercept request for parameterized constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
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

    // 2. Invoke createNInstances which internally calls constructor n times
    logger.info(
        "Invoking createNInstances(n={}, initialValue={}) which should trigger {} callback(s)",
        n,
        initialValue,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createNInstances",
                new String[] {"java.lang.Integer", "java.lang.Integer"},
                null,
                null,
                new Object[] {n, initialValue}));
    logger.info("createNInstances invocation completed");

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Retrieve and verify callbacks
    logger.info("Waiting for {} callback(s) to be received", n);
    List<Message> callbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    assertThat("Should receive exactly " + n + " callback(s)", callbacks.size(), is(n));

    // 5. Verify callback structure
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
      // AFTER callbacks wrap ReturnValue, not ConstructorCall
      // Verify the return value structure for constructor (returns the constructed object)
      assertThat(
          "AFTER callback should have ReturnValue in exec",
          callback.getInterceptCallbackRequest().getExec().getReturnValue(),
          is(notNullValue()));
      assertThat(
          "Constructor returns object, so isVoid should be false",
          callback.getInterceptCallbackRequest().getExec().getReturnValue().isVoid,
          is(false));
      assertThat(
          "ReturnValue should have the constructed object",
          callback.getInterceptCallbackRequest().getExec().getReturnValue().getObject(),
          is(notNullValue()));
      assertThat(
          "ReturnValue should have constructor info",
          callback
              .getInterceptCallbackRequest()
              .getExec()
              .getReturnValue()
              .getFrom()
              .getConstructor(),
          is(notNullValue()));
    }

    logger.info("===== testMultipleAfterCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
