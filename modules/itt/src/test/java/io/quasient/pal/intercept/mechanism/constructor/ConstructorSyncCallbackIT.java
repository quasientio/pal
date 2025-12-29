/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.mechanism.constructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for synchronous constructor intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the callback mechanism for synchronous intercepts on constructors
 * (INTERCEPT_CALLBACK_REQUEST), including single and multiple callbacks for both BEFORE and AFTER
 * intercept types.
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
public class ConstructorSyncCallbackIT extends AbstractInterceptIT {

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public ConstructorSyncCallbackIT(InvocationPath path) {
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
   * Invokes the parameterized constructor once through the specified invocation path.
   *
   * @param initialValue the initial counter value
   * @return the response ExecMessage
   */
  private ExecMessage invokeConstructorOnce(int initialValue) {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use factory method that calls constructor once
      return invoke(
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
      return invoke(
          messageBuilder.buildConstructor(
              myPeerUuid,
              InterceptableApp.class.getName(),
              new String[] {"java.lang.Integer"},
              new Object[] {initialValue},
              null,
              null));
    }
  }

  /**
   * Tests single BEFORE callback on constructor.
   *
   * <p>Registers a BEFORE intercept on the parameterized constructor, invokes it once, and verifies
   * exactly 1 callback is received with correct structure.
   */
  @Test
  public void testSingleBeforeCallback() throws Exception {
    logger.info("===== testSingleBeforeCallback [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int initialValue = 10;

    // 1. Register a BEFORE intercept on parameterized constructor
    logger.info("Creating BEFORE intercept request for parameterized constructor");
    UUID interceptUuid = UUID.randomUUID();
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

    // 2. Invoke constructor through the specified path
    logger.info("Invoking constructor via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeConstructorOnce(initialValue);
    logger.info("Constructor invocation completed");

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
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    // BEFORE callbacks receive the constructor parameters
    // Constructor(Integer) has 1 parameter
    assertThat(
        "BEFORE callback should have 1 parameter (the Integer argument)",
        callback
            .getInterceptCallbackRequestMessage()
            .getExec()
            .getConstructorCall()
            .getParameters()
            .length,
        is(1));

    logger.info("===== testSingleBeforeCallback [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests multiple BEFORE callbacks on constructor.
   *
   * <p>Registers a BEFORE intercept on the parameterized constructor, invokes it multiple times,
   * and verifies the correct number of callbacks are received.
   *
   * <p>For HOT_PATH: Uses factory method that calls constructor n times. For INCOMING_RPC: Calls
   * constructor directly n times.
   */
  @Test
  public void testMultipleBeforeCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeCallbacks [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;
    final int initialValue = 10;

    // 1. Register a BEFORE intercept on parameterized constructor
    logger.info("Creating BEFORE intercept request for parameterized constructor");
    UUID interceptUuid = UUID.randomUUID();
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

    // 2. Invoke constructor n times through the specified path
    logger.info(
        "Invoking constructor {} times via {} path which should trigger {} callback(s)",
        n,
        path,
        n);

    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use factory method that calls constructor n times
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
      assertThat(
          "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));
    } else {
      // INCOMING_RPC: Call constructor directly n times
      for (int i = 0; i < n; i++) {
        ExecMessage response =
            invoke(
                messageBuilder.buildConstructor(
                    myPeerUuid,
                    InterceptableApp.class.getName(),
                    new String[] {"java.lang.Integer"},
                    new Object[] {initialValue + i},
                    null,
                    null));
        assertThat(
            "Invocation should not raise exception",
            response.getRaisedThrowable(),
            is(nullValue()));
      }
    }
    logger.info("Constructor invocations completed");

    // 3. Retrieve and verify callbacks
    logger.info("Waiting for {} callback(s) to be received", n);
    List<Message> callbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    assertThat("Should receive exactly " + n + " callback(s)", callbacks.size(), is(n));

    // 4. Verify callback structure
    for (int i = 0; i < n; i++) {
      Message callback = callbacks.get(i);
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
      // BEFORE callbacks receive the constructor parameters
      // Constructor(Integer) has 1 parameter
      assertThat(
          "BEFORE callback should have 1 parameter (the Integer argument)",
          callback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getConstructorCall()
              .getParameters()
              .length,
          is(1));
    }

    logger.info("===== testMultipleBeforeCallbacks [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests single AFTER callback on constructor.
   *
   * <p>Registers an AFTER intercept on the parameterized constructor, invokes it once, and verifies
   * exactly 1 callback is received after constructor execution.
   */
  @Test
  public void testSingleAfterCallback() throws Exception {
    logger.info("===== testSingleAfterCallback [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // 2. Invoke constructor through the specified path
    logger.info("Invoking constructor via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeConstructorOnce(initialValue);
    logger.info("Constructor invocation completed");

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
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    // AFTER callbacks wrap ReturnValue, not ConstructorCall
    // Verify the return value structure for constructor (returns the constructed object)
    assertThat(
        "AFTER callback should have ReturnValue in exec",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue(),
        is(notNullValue()));
    assertThat(
        "Constructor returns object, so isVoid should be false",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().isVoid,
        is(false));
    assertThat(
        "ReturnValue should have the constructed object",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().getObject(),
        is(notNullValue()));
    assertThat(
        "ReturnValue should have constructor info",
        callback
            .getInterceptCallbackRequestMessage()
            .getExec()
            .getReturnValue()
            .getFrom()
            .getConstructor(),
        is(notNullValue()));

    logger.info("===== testSingleAfterCallback [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests multiple AFTER callbacks on constructor.
   *
   * <p>Registers an AFTER intercept on the parameterized constructor, invokes it multiple times,
   * and verifies the correct number of callbacks are received.
   *
   * <p>For HOT_PATH: Uses factory method that calls constructor n times. For INCOMING_RPC: Calls
   * constructor directly n times.
   */
  @Test
  public void testMultipleAfterCallbacks() throws Exception {
    logger.info("===== testMultipleAfterCallbacks [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // 2. Invoke constructor n times through the specified path
    logger.info(
        "Invoking constructor {} times via {} path which should trigger {} callback(s)",
        n,
        path,
        n);

    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use factory method that calls constructor n times
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
      assertThat(
          "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));
    } else {
      // INCOMING_RPC: Call constructor directly n times
      for (int i = 0; i < n; i++) {
        ExecMessage response =
            invoke(
                messageBuilder.buildConstructor(
                    myPeerUuid,
                    InterceptableApp.class.getName(),
                    new String[] {"java.lang.Integer"},
                    new Object[] {initialValue + i},
                    null,
                    null));
        assertThat(
            "Invocation should not raise exception",
            response.getRaisedThrowable(),
            is(nullValue()));
      }
    }
    logger.info("Constructor invocations completed");

    // 3. Retrieve and verify callbacks
    logger.info("Waiting for {} callback(s) to be received", n);
    List<Message> callbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    assertThat("Should receive exactly " + n + " callback(s)", callbacks.size(), is(n));

    // 4. Verify callback structure
    for (int i = 0; i < n; i++) {
      Message callback = callbacks.get(i);
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
      // AFTER callbacks wrap ReturnValue, not ConstructorCall
      // Verify the return value structure for constructor (returns the constructed object)
      assertThat(
          "AFTER callback should have ReturnValue in exec",
          callback.getInterceptCallbackRequestMessage().getExec().getReturnValue(),
          is(notNullValue()));
      assertThat(
          "Constructor returns object, so isVoid should be false",
          callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().isVoid,
          is(false));
      assertThat(
          "ReturnValue should have the constructed object",
          callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().getObject(),
          is(notNullValue()));
      assertThat(
          "ReturnValue should have constructor info",
          callback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getReturnValue()
              .getFrom()
              .getConstructor(),
          is(notNullValue()));
    }

    logger.info("===== testMultipleAfterCallbacks [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }
}
