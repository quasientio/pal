/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.mechanism.staticmethod;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.foobar.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
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
 * Integration tests for synchronous static method intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the callback mechanism for synchronous intercepts on static methods
 * (EXEC_CLASS_METHOD), including single and multiple callbacks for both BEFORE and AFTER intercept
 * types.
 *
 * <p>Tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Intercepts triggered via AspectJ weaving at call-site (wrapper method
 *       calls target static method)
 *   <li><b>INCOMING_RPC</b>: Intercepts triggered via direct RPC message dispatch
 * </ul>
 */
@RunWith(Parameterized.class)
public class StaticMethodSyncCallbackIT extends AbstractInterceptIT {

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public StaticMethodSyncCallbackIT(InvocationPath path) {
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
   * Invokes incrementStaticCounter once through the specified invocation path.
   *
   * @param appInstance the target object (for HOT_PATH wrapper invocation)
   * @return the response ExecMessage
   */
  private ExecMessage invokeIncrementStaticCounterOnce(ObjectRef appInstance) {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use wrapper method that calls incrementStaticCounter once
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "callIncrementStaticCounter",
              appInstance,
              new String[] {},
              new Object[] {}));
    } else {
      // INCOMING_RPC: Call incrementStaticCounter directly
      return invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "incrementStaticCounter",
              new String[] {},
              null,
              null,
              new Object[] {}));
    }
  }

  /**
   * Invokes multiplyStaticBy once through the specified invocation path.
   *
   * @param appInstance the target object (for HOT_PATH wrapper invocation)
   * @param multiplier the multiplier argument
   * @return the response ExecMessage
   */
  private ExecMessage invokeMultiplyStaticByOnce(ObjectRef appInstance, int multiplier) {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use wrapper method that calls multiplyStaticBy once
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "callMultiplyStaticBy",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {multiplier}));
    } else {
      // INCOMING_RPC: Call multiplyStaticBy directly
      return invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "multiplyStaticBy",
              new String[] {"java.lang.Integer"},
              null,
              null,
              new Object[] {multiplier}));
    }
  }

  /**
   * Tests single BEFORE callback on static method.
   *
   * <p>Registers a BEFORE intercept on incrementStaticCounter, invokes it once, and verifies
   * exactly 1 callback is received with correct structure.
   */
  @Test
  public void testSingleBeforeCallback() throws Exception {
    logger.info("===== testSingleBeforeCallback [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    // 1. Register a BEFORE intercept on incrementStaticCounter static method
    logger.info("Creating BEFORE intercept request for incrementStaticCounter static method");
    UUID interceptUuid = UUID.randomUUID();
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

    // 2. Create InterceptableApp instance (for HOT_PATH wrapper invocation)
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

    // 3. Invoke incrementStaticCounter through the specified path
    logger.info(
        "Invoking incrementStaticCounter via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeIncrementStaticCounterOnce(appInstance);
    logger.info("incrementStaticCounter invocation completed");

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
    // BEFORE callbacks receive method parameters (incrementStaticCounter has no params)
    assertThat(
        "BEFORE callback should have 0 parameters",
        callback
            .getInterceptCallbackRequestMessage()
            .getExec()
            .getClassMethodCall()
            .getParameters()
            .length,
        is(0));

    logger.info("===== testSingleBeforeCallback [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests multiple BEFORE callbacks on static method.
   *
   * <p>Registers a BEFORE intercept on multiplyStaticBy, invokes it multiple times, and verifies
   * the correct number of callbacks are received.
   *
   * <p>For HOT_PATH: Uses wrapper method that calls target n times. For INCOMING_RPC: Calls target
   * directly n times.
   */
  @Test
  public void testMultipleBeforeCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeCallbacks [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;
    final int multiplier = 2;

    // 1. Register a BEFORE intercept on multiplyStaticBy static method
    logger.info("Creating BEFORE intercept request for multiplyStaticBy static method");
    UUID interceptUuid = UUID.randomUUID();
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

    // 2. Create InterceptableApp instance (for HOT_PATH wrapper invocation)
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

    // 3. Invoke multiplyStaticBy n times through the specified path
    logger.info(
        "Invoking multiplyStaticBy {} times via {} path which should trigger {} callback(s)",
        n,
        path,
        n);

    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use wrapper method that calls target n times
      ExecMessage response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  InterceptableApp.class.getName(),
                  "callMultiplyStaticByNTimes",
                  appInstance,
                  new String[] {"java.lang.Integer", "java.lang.Integer"},
                  new Object[] {n, multiplier}));
      assertThat(
          "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));
    } else {
      // INCOMING_RPC: Call target method directly n times
      for (int i = 0; i < n; i++) {
        ExecMessage response =
            invoke(
                messageBuilder.buildClassMethod(
                    myPeerUuid,
                    InterceptableApp.class.getName(),
                    "multiplyStaticBy",
                    new String[] {"java.lang.Integer"},
                    null,
                    null,
                    new Object[] {multiplier}));
        assertThat(
            "Invocation should not raise exception",
            response.getRaisedThrowable(),
            is(nullValue()));
      }
    }
    logger.info("multiplyStaticBy invocations completed");

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
          callback.getInterceptCallbackRequestMessage().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "Callback method should match",
          callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
          is(callbackMethod));
      // BEFORE callbacks receive method parameters (multiplyStaticBy has 1 Integer parameter)
      assertThat(
          "BEFORE callback should have 1 parameter (the Integer argument)",
          callback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getClassMethodCall()
              .getParameters()
              .length,
          is(1));
    }

    logger.info("===== testMultipleBeforeCallbacks [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests single AFTER callback on static method.
   *
   * <p>Registers an AFTER intercept on multiplyStaticBy, invokes it once, and verifies exactly 1
   * callback is received after method execution.
   */
  @Test
  public void testSingleAfterCallback() throws Exception {
    logger.info("===== testSingleAfterCallback [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // 2. Create InterceptableApp instance (for HOT_PATH wrapper invocation)
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

    // 3. Invoke multiplyStaticBy through the specified path
    logger.info("Invoking multiplyStaticBy via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeMultiplyStaticByOnce(appInstance, multiplier);
    logger.info("multiplyStaticBy invocation completed");

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
    // AFTER callbacks wrap ReturnValue, not ClassMethodCall
    // Verify the return value structure for non-void method (returns Integer)
    assertThat(
        "AFTER callback should have ReturnValue in exec",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue(),
        is(notNullValue()));
    assertThat(
        "multiplyStaticBy returns Integer, so isVoid should be false",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().isVoid,
        is(false));
    assertThat(
        "ReturnValue should have the return object",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().getObject(),
        is(notNullValue()));
    assertThat(
        "ReturnValue should have method info",
        callback
            .getInterceptCallbackRequestMessage()
            .getExec()
            .getReturnValue()
            .getFrom()
            .getMethod(),
        is(notNullValue()));

    logger.info("===== testSingleAfterCallback [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests multiple AFTER callbacks on static method.
   *
   * <p>Registers an AFTER intercept on multiplyStaticBy, invokes it multiple times, and verifies
   * the correct number of callbacks are received.
   *
   * <p>For HOT_PATH: Uses wrapper method that calls target n times. For INCOMING_RPC: Calls target
   * directly n times.
   */
  @Test
  public void testMultipleAfterCallbacks() throws Exception {
    logger.info("===== testMultipleAfterCallbacks [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // 2. Create InterceptableApp instance (for HOT_PATH wrapper invocation)
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

    // 3. Invoke multiplyStaticBy n times through the specified path
    logger.info(
        "Invoking multiplyStaticBy {} times via {} path which should trigger {} callback(s)",
        n,
        path,
        n);

    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use wrapper method that calls target n times
      ExecMessage response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  InterceptableApp.class.getName(),
                  "callMultiplyStaticByNTimes",
                  appInstance,
                  new String[] {"java.lang.Integer", "java.lang.Integer"},
                  new Object[] {n, multiplier}));
      assertThat(
          "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));
    } else {
      // INCOMING_RPC: Call target method directly n times
      for (int i = 0; i < n; i++) {
        ExecMessage response =
            invoke(
                messageBuilder.buildClassMethod(
                    myPeerUuid,
                    InterceptableApp.class.getName(),
                    "multiplyStaticBy",
                    new String[] {"java.lang.Integer"},
                    null,
                    null,
                    new Object[] {multiplier}));
        assertThat(
            "Invocation should not raise exception",
            response.getRaisedThrowable(),
            is(nullValue()));
      }
    }
    logger.info("multiplyStaticBy invocations completed");

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
          callback.getInterceptCallbackRequestMessage().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "Callback method should match",
          callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
          is(callbackMethod));
      // AFTER callbacks wrap ReturnValue, not ClassMethodCall
      // Verify the return value structure for non-void method (returns Integer)
      assertThat(
          "AFTER callback should have ReturnValue in exec",
          callback.getInterceptCallbackRequestMessage().getExec().getReturnValue(),
          is(notNullValue()));
      assertThat(
          "multiplyStaticBy returns Integer, so isVoid should be false",
          callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().isVoid,
          is(false));
      assertThat(
          "ReturnValue should have the return object",
          callback.getInterceptCallbackRequestMessage().getExec().getReturnValue().getObject(),
          is(notNullValue()));
      assertThat(
          "ReturnValue should have method info",
          callback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getReturnValue()
              .getFrom()
              .getMethod(),
          is(notNullValue()));
    }

    logger.info("===== testMultipleAfterCallbacks [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }
}
