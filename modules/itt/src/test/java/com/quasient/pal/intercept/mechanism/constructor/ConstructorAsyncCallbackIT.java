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
 * Integration tests for asynchronous constructor intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify the end-to-end callback mechanism for asynchronous intercepts on
 * constructors (EXEC_CONSTRUCTOR) using DEALER sockets, including single and multiple callbacks for
 * both BEFORE_ASYNC and AFTER_ASYNC intercept types.
 *
 * <p>Unlike synchronous callbacks which use REQ-REP pattern and wait for responses, async callbacks
 * use DEALER-ROUTER pattern for fire-and-forget delivery.
 *
 * <p><b>NOTE:</b>These tests verify intercepts at the hot-path (via quantization, which happens at
 * the call-site), and so, we need to invoke via RPC a method/ctor that triggers the actual
 * interception target.
 */
public class ConstructorAsyncCallbackIT extends AbstractInterceptIT {

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7893";

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
   * Tests single BEFORE_ASYNC callback on constructor.
   *
   * <p>Registers a BEFORE_ASYNC intercept on the parameterized constructor, invokes factory method
   * which internally calls constructor (triggers intercept via call-site) once and verifies exactly
   * 1 callback is received without blocking for a response.
   */
  @Test
  public void testSingleBeforeAsyncCallback() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int initialValue = 10;

    // 1. Register a BEFORE_ASYNC intercept on parameterized constructor
    logger.info("Creating BEFORE_ASYNC intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.BEFORE_ASYNC,
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
    logger.debug("Callback message type: {}", MessageType.fromId(callback.getMessageType()));
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

    logger.info("===== testSingleBeforeAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE_ASYNC callbacks on constructor.
   *
   * <p>Registers a BEFORE_ASYNC intercept on the parameterized constructor, invokes a factory
   * method that creates n=3 instances, and verifies exactly 3 callbacks are received without
   * blocking.
   */
  @Test
  public void testMultipleBeforeAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;
    final int initialValue = 10;

    // 1. Register a BEFORE_ASYNC intercept on parameterized constructor
    logger.info("Creating BEFORE_ASYNC intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.BEFORE_ASYNC,
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
      logger.debug("Callback message type: {}", MessageType.fromId(callback.getMessageType()));
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

    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER_ASYNC callback on constructor.
   *
   * <p>Registers a AFTER_ASYNC intercept on the parameterized constructor, invokes factory method
   * which internally calls constructor (triggers intercept via call-site) once and verifies exactly
   * 1 callback is received without blocking for a response.
   */
  @Test
  public void testSingleAfterAsyncCallback() throws Exception {
    logger.info("===== testSingleAfterAsyncCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int initialValue = 10;

    // 1. Register an AFTER_ASYNC intercept on parameterized constructor
    logger.info("Creating AFTER_ASYNC intercept request for parameterized constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.AFTER_ASYNC,
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
    logger.debug("Callback message type: {}", MessageType.fromId(callback.getMessageType()));
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

    logger.info("===== testSingleAfterAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER_ASYNC callbacks on constructor.
   *
   * <p>Registers an AFTER_ASYNC intercept on the parameterized constructor, invokes a factory
   * method that creates n=3 instances, and verifies exactly 3 callbacks are received after
   * constructor executions without blocking.
   */
  @Test
  public void testMultipleAfterAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleAfterAsyncCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;
    final int initialValue = 10;

    // 1. Register an AFTER_ASYNC intercept on parameterized constructor
    logger.info("Creating AFTER_ASYNC intercept request for parameterized constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.AFTER_ASYNC,
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
      logger.debug("Callback message type: {}", MessageType.fromId(callback.getMessageType()));
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

    logger.info("===== testMultipleAfterAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
