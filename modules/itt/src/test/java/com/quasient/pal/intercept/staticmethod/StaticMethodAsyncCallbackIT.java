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
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;

/**
 * Integration tests for asynchronous static method intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify the end-to-end callback mechanism for asynchronous intercepts on static
 * methods (INTERCEPT_CALLBACK_REQUEST) using DEALER sockets, including single and multiple
 * callbacks for both BEFORE_ASYNC and AFTER_ASYNC intercept types.
 *
 * <p>Unlike synchronous callbacks which use REQ-REP pattern and wait for responses, async callbacks
 * use DEALER-ROUTER pattern for fire-and-forget delivery.
 *
 * <p><b>NOTE:</b>These tests verify intercepts at the hot-path (via quantization, which happens at
 * the call-site), and so, we need to invoke via RPC a method/ctor that triggers the actual
 * interception target.
 */
public class StaticMethodAsyncCallbackIT extends AbstractInterceptIT {

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7892";

  /** UUID for the async callback receiver peer (registered in directory). */
  private final UUID asyncCallbackPeerUuid = UUID.randomUUID();

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** ThinPeer for receiving async callbacks via ROUTER socket. */
  private ThinPeer asyncCallbackPeer;

  /**
   * Sets up ThinPeer with ROUTER socket for receiving async callbacks.
   *
   * <p>ROUTER socket is needed because async callbacks use DEALER sockets which cannot connect to
   * REP sockets. The ThinPeer registers itself in the directory so the InterceptCallbackDispatcher
   * can look up the address.
   */
  @Before
  public void setUpAsyncReceiver() throws Exception {
    // Create DirectoryConnectionProvider for the async callback peer
    DirectoryConnectionProvider directoryConnectionProvider =
        new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);

    // Create ThinPeer with ROUTER socket to receive async callbacks
    asyncCallbackPeer =
        new ThinPeer()
            .withUuid(asyncCallbackPeerUuid)
            .withName("AsyncCallbackReceiver")
            .withSelfRegistration(true)
            .withZmqRpcAddress(ASYNC_CALLBACK_ADDRESS, SocketType.ROUTER)
            .withDirectoryProvider(directoryConnectionProvider)
            .init();

    // Register this test class as a listener for incoming async callback messages
    asyncCallbackPeer.addMessageListener(this);

    logger.info(
        "Async callback peer {} initialized with ROUTER socket at {}",
        asyncCallbackPeerUuid,
        ASYNC_CALLBACK_ADDRESS);
  }

  /** Closes the async callback ThinPeer and cleans up intercepts after tests complete. */
  @After
  public void tearDownAsyncReceiver() {
    if (interceptUuid != null) {
      logger.info("Cleaning up intercept registration: {}", interceptUuid);
    }
    if (asyncCallbackPeer != null) {
      asyncCallbackPeer.close();
      logger.info("Async callback peer closed");
    }
  }

  /**
   * Tests single BEFORE_ASYNC callback on static method.
   *
   * <p>Registers a BEFORE_ASYNC intercept on multiplyStaticBy, calls a wrapper than invokes it
   * once, and verifies exactly 1 callback is received without blocking for a response.
   */
  @Test
  public void testSingleBeforeAsyncCallback() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int multiplier = 3;

    // 1. Register a BEFORE_ASYNC intercept on multiplyStaticBy method
    logger.info("Creating BEFORE_ASYNC intercept request for multiplyStaticBy method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.BEFORE_ASYNC,
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

    // 3. Invoke callMultiplyStaticBy which triggers async intercept via call-site
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

    logger.info("===== testSingleBeforeAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE_ASYNC callbacks on static method.
   *
   * <p>Registers a BEFORE_ASYNC intercept on incrementStaticCounter, invokes a wrapper method that
   * calls it n=3 times, and verifies exactly 3 callbacks are received without blocking.
   */
  @Test
  public void testMultipleBeforeAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;

    // 1. Register a BEFORE_ASYNC intercept on incrementStaticCounter method
    logger.info("Creating BEFORE_ASYNC intercept request for incrementStaticCounter method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.BEFORE_ASYNC,
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

    // 3. Invoke callIncrementStaticCounterNTimes which internally calls incrementStaticCounter n
    // times
    logger.info(
        "Invoking callIncrementStaticCounterNTimes(n={}) which should trigger {} callback(s)",
        n,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "callIncrementStaticCounterNTimes",
                appInstance,
                new String[] {"java.lang.Integer"},
                new Object[] {n}));
    logger.info("callIncrementStaticCounterNTimes invocation completed");

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
    }

    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER_ASYNC callback on static method.
   *
   * <p>Registers an AFTER_ASYNC intercept on multiplyStaticBy, calls a wrapper that invokes it
   * once, and verifies exactly 1 callback is received after method execution without blocking.
   */
  @Test
  public void testSingleAfterAsyncCallback() throws Exception {
    logger.info("===== testSingleAfterAsyncCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int multiplier = 3;

    // 1. Register an AFTER_ASYNC intercept on multiplyStaticBy method
    logger.info("Creating AFTER_ASYNC intercept request for multiplyStaticBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.AFTER_ASYNC,
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

    // 3. Invoke callMultiplyStaticBy wrapper which triggers async intercept
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

    logger.info("===== testSingleAfterAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER_ASYNC callbacks on static method.
   *
   * <p>Registers an AFTER_ASYNC intercept on incrementStaticCounter, invokes a wrapper method that
   * calls it n=3 times, and verifies exactly 3 callbacks are received after method executions
   * without blocking.
   */
  @Test
  public void testMultipleAfterAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleAfterAsyncCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;

    // 1. Register an AFTER_ASYNC intercept on incrementStaticCounter method
    logger.info("Creating AFTER_ASYNC intercept request for incrementStaticCounter method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.AFTER_ASYNC,
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

    // 3. Invoke callIncrementStaticCounterNTimes which internally calls incrementStaticCounter n
    // times
    logger.info(
        "Invoking callIncrementStaticCounterNTimes(n={}) which should trigger {} callback(s)",
        n,
        n);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "callIncrementStaticCounterNTimes",
                appInstance,
                new String[] {"java.lang.Integer"},
                new Object[] {n}));
    logger.info("callIncrementStaticCounterNTimes invocation completed");

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
          "incrementStaticCounter returns Integer, so isVoid should be false",
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

    logger.info("===== testMultipleAfterAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
