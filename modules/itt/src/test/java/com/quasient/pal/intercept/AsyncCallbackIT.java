/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.quasient.pal.apps.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZMQ.Socket;

/**
 * Integration tests for asynchronous intercept callbacks (BEFORE_ASYNC and AFTER_ASYNC).
 *
 * <p>These tests verify the end-to-end callback mechanism for asynchronous intercepts using DEALER
 * sockets, including single and multiple callbacks for both BEFORE_ASYNC and AFTER_ASYNC intercept
 * types.
 *
 * <p>Unlike synchronous callbacks which use REQ-REP pattern and wait for responses, async callbacks
 * use DEALER-ROUTER pattern for fire-and-forget delivery.
 */
public class AsyncCallbackIT extends AbstractInterceptIT {

  /** ZContext for creating ZeroMQ sockets. */
  private org.zeromq.ZContext asyncZmqContext;

  /** ROUTER socket for receiving async callbacks (DEALER clients connect to this). */
  private Socket routerSocket;

  /** Timeout in milliseconds for polling Router socket for callbacks */
  private static final int ROUTER_SOCKET_RECV_TIMEOUT_MS = 1000;

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7891";

  /** UUID for the async callback receiver peer (registered in directory). */
  private final UUID asyncCallbackPeerUuid = UUID.randomUUID();

  /**
   * Sets up ROUTER socket for receiving async callbacks and registers it in the directory.
   *
   * <p>ROUTER socket is needed because async callbacks use DEALER sockets which cannot connect to
   * REP sockets. We register this peer in the directory so the InterceptCallbackDispatcher can look
   * up the address.
   */
  @Before
  public void setUpAsyncReceiver() throws Exception {
    // Create ZMQ context for async sockets
    asyncZmqContext = new org.zeromq.ZContext();

    // Create and bind ROUTER socket
    routerSocket = asyncZmqContext.createSocket(SocketType.ROUTER);
    routerSocket.bind(ASYNC_CALLBACK_ADDRESS);
    routerSocket.setReceiveTimeOut(ROUTER_SOCKET_RECV_TIMEOUT_MS);
    logger.info("ROUTER socket bound to {} for async callbacks", ASYNC_CALLBACK_ADDRESS);

    // Register this peer in the directory so InterceptCallbackDispatcher can find it
    PeerInfo asyncPeerInfo = new PeerInfo(asyncCallbackPeerUuid);
    asyncPeerInfo.setName("AsyncCallbackReceiver");
    asyncPeerInfo.setZmqRpcAddress(ASYNC_CALLBACK_ADDRESS);

    DirectoryConnectionProvider directoryProvider =
        new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);
    PalDirectory directory =
        directoryProvider
            .get()
            .orElseThrow(() -> new RuntimeException("No connection for PalDirectory"));

    directory.createPeer(asyncPeerInfo);
    logger.info(
        "Registered async callback peer {} in directory with address {}",
        asyncCallbackPeerUuid,
        ASYNC_CALLBACK_ADDRESS);

    directory.close();
  }

  /** Closes the ROUTER socket and unregisters from directory after tests complete. */
  @After
  public void tearDownAsyncReceiver() {
    if (routerSocket != null) {
      routerSocket.close();
      logger.info("ROUTER socket closed");
    }

    if (asyncZmqContext != null) {
      asyncZmqContext.close();
      logger.info("Async ZMQ context closed");
    }

    // Unregister peer from directory (intercept requests are automatically cleaned up)
    try {
      DirectoryConnectionProvider directoryProvider =
          new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);
      PalDirectory directory =
          directoryProvider
              .get()
              .orElseThrow(() -> new RuntimeException("No connection for PalDirectory"));

      directory.deletePeer(asyncCallbackPeerUuid);
      logger.info("Unregistered async callback peer {} from directory", asyncCallbackPeerUuid);

      directory.close();
    } catch (Exception e) {
      logger.warn("Failed to unregister async callback peer from directory", e);
    }
  }

  /**
   * Receives an async callback message without sending a response.
   *
   * <p>ROUTER receives: [identity, empty delimiter, payload]. Unlike sync callbacks, we don't send
   * a response back since async callbacks are fire-and-forget.
   *
   * @return the received callback message
   */
  private Message receiveAsyncCallback() {
    // ROUTER receives: [identity, empty delimiter, payload]
    byte[] identity = routerSocket.recv();
    if (identity == null) {
      throw new RuntimeException("Timeout waiting for async callback identity frame");
    }

    byte[] empty = routerSocket.recv();
    if (empty == null) {
      throw new RuntimeException("Timeout waiting for async callback empty delimiter frame");
    }

    byte[] payload = routerSocket.recv();
    if (payload == null) {
      throw new RuntimeException("Timeout waiting for async callback payload frame");
    }

    Message callbackMsg = new Message();
    try {
      callbackMsg.unmarshal(payload, 0);
      logger.debug("Received async callback: {}", colferToPrettyJson(callbackMsg));
      return callbackMsg;
    } catch (Exception e) {
      throw new RuntimeException("Failed to unmarshal async callback message", e);
    }
  }

  /**
   * Tests single BEFORE_ASYNC callback.
   *
   * <p>Registers a BEFORE_ASYNC intercept on multiplyBy, calls it once (n=1), and verifies exactly
   * 1 callback is received without blocking for a response.
   */
  @Test
  public void testSingleBeforeAsyncCallback() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallback: TEST STARTED =====");

    final String callbackClass = "com.example.AsyncCallbackHandler";
    final String callbackMethod = "handleAsyncCallback";
    final int n = 1;
    final int multiplier = 3;

    // 1. Register a BEFORE_ASYNC intercept on multiplyBy method
    logger.info("Creating BEFORE_ASYNC intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.BEFORE_ASYNC,
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

    // 3. Set up async callback receiver in separate thread
    CountDownLatch callbackReceived = new CountDownLatch(n);
    List<Message> receivedCallbacks = Collections.synchronizedList(new ArrayList<>());

    logger.info("Starting executor thread to receive {} async callback(s)", n);
    for (int i = 0; i < n; i++) {
      executor.execute(
          () -> {
            try {
              logger.info("Async receiver thread started, waiting for callback");

              // Receive async callback message (no response sent)
              Message callbackMsg = receiveAsyncCallback();
              logger.debug("Received async callback message: {}", colferToPrettyJson(callbackMsg));

              receivedCallbacks.add(callbackMsg);
              callbackReceived.countDown();
              logger.info("Async receiver thread completed, callback received");

            } catch (Exception e) {
              logger.error("Error in async callback receiver thread", e);
              assertionError =
                  new AssertionError("Async callback receiver failed: " + e.getMessage());
            }
          });
    }

    // 4. Invoke multiplyCounterNTimesBy which triggers async callback
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) which should trigger {} async callback(s)",
        n,
        multiplier,
        n);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyCounterNTimesBy",
            appInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed");

    // 5. Wait for async callback(s) to be received
    logger.info("Waiting for {} async callback(s) to be received", n);
    boolean received = callbackReceived.await(5, TimeUnit.SECONDS);
    assertThat("All async callbacks should be received within 5 seconds", received, is(true));
    logger.info("All {} async callback(s) received successfully", n);

    // 6. Verify callback structure
    logger.info("Verifying async callback message structure");
    assertThat(
        "Should have received exactly " + n + " async callback(s)",
        receivedCallbacks.size(),
        is(n));

    for (int i = 0; i < n; i++) {
      Message callback = receivedCallbacks.get(i);
      assertThat("Async callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "Async callback should be CLASS_METHOD type",
          callback.getMessageType(),
          is(MessageType.EXEC_CLASS_METHOD.getId()));
      assertThat(
          "Async callback class should match",
          callback.getExecMessage().getClassMethodCall().getClazz().getName(),
          is(callbackClass));
      assertThat(
          "Async callback method should match",
          callback.getExecMessage().getClassMethodCall().getName(),
          is(callbackMethod));
      assertThat(
          "BEFORE_ASYNC callback should have 1 parameter",
          callback.getExecMessage().getClassMethodCall().getParameters().length,
          is(1));
    }

    // Throw any assertion errors from callback thread
    if (assertionError != null) {
      logger.error("Test failed with assertion error: {}", assertionError.getMessage());
      throw assertionError;
    }

    logger.info("===== testSingleBeforeAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE_ASYNC callbacks.
   *
   * <p>Registers a BEFORE_ASYNC intercept on multiplyBy, calls it multiple times (n=3), and
   * verifies exactly 3 callbacks are received without blocking.
   */
  @Test
  public void testMultipleBeforeAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST STARTED =====");

    final String callbackClass = "com.example.AsyncCallbackHandler";
    final String callbackMethod = "handleAsyncCallback";
    final int n = 3;
    final int multiplier = 2;

    // 1. Register a BEFORE_ASYNC intercept on multiplyBy method
    logger.info("Creating BEFORE_ASYNC intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.BEFORE_ASYNC,
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

    // 3. Set up async callback receiver in separate thread
    CountDownLatch callbackReceived = new CountDownLatch(n);
    List<Message> receivedCallbacks = Collections.synchronizedList(new ArrayList<>());

    logger.info("Starting executor threads to receive {} async callback(s)", n);
    for (int i = 0; i < n; i++) {
      executor.execute(
          () -> {
            try {
              logger.info("Async receiver thread started, waiting for callback");

              // Receive async callback message (no response sent)
              Message callbackMsg = receiveAsyncCallback();
              logger.debug("Received async callback message: {}", colferToPrettyJson(callbackMsg));

              receivedCallbacks.add(callbackMsg);
              callbackReceived.countDown();
              logger.info("Async receiver thread completed, callback received");

            } catch (Exception e) {
              logger.error("Error in async callback receiver thread", e);
              assertionError =
                  new AssertionError("Async callback receiver failed: " + e.getMessage());
            }
          });
    }

    // 4. Invoke multiplyCounterNTimesBy which triggers async callbacks
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) which should trigger {} async callback(s)",
        n,
        multiplier,
        n);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyCounterNTimesBy",
            appInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed");

    // 5. Wait for all async callbacks to be received
    logger.info("Waiting for {} async callback(s) to be received", n);
    boolean received = callbackReceived.await(5, TimeUnit.SECONDS);
    assertThat("All async callbacks should be received within 5 seconds", received, is(true));
    logger.info("All {} async callback(s) received successfully", n);

    // 6. Verify we received exactly n callbacks
    logger.info("Verifying exactly {} async callbacks were received", n);
    assertThat(
        "Should have received exactly " + n + " async callbacks", receivedCallbacks.size(), is(n));

    for (int i = 0; i < n; i++) {
      Message callback = receivedCallbacks.get(i);
      assertThat("Async callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "Async callback should be CLASS_METHOD type",
          callback.getMessageType(),
          is(MessageType.EXEC_CLASS_METHOD.getId()));
      assertThat(
          "Async callback class should match",
          callback.getExecMessage().getClassMethodCall().getClazz().getName(),
          is(callbackClass));
      assertThat(
          "Async callback method should match",
          callback.getExecMessage().getClassMethodCall().getName(),
          is(callbackMethod));
    }

    // Throw any assertion errors from callback thread
    if (assertionError != null) {
      logger.error("Test failed with assertion error: {}", assertionError.getMessage());
      throw assertionError;
    }

    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER_ASYNC callback.
   *
   * <p>Registers an AFTER_ASYNC intercept on multiplyBy, calls it once (n=1), and verifies exactly
   * 1 callback is received after method execution without blocking.
   */
  @Test
  public void testSingleAfterAsyncCallback() throws Exception {
    logger.info("===== testSingleAfterAsyncCallback: TEST STARTED =====");

    final String callbackClass = "com.example.AsyncCallbackHandler";
    final String callbackMethod = "handleAsyncCallback";
    final int n = 1;
    final int multiplier = 3;

    // 1. Register an AFTER_ASYNC intercept on multiplyBy method
    logger.info("Creating AFTER_ASYNC intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.AFTER_ASYNC,
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

    // 3. Set up async callback receiver in separate thread
    CountDownLatch callbackReceived = new CountDownLatch(n);
    List<Message> receivedCallbacks = Collections.synchronizedList(new ArrayList<>());

    logger.info("Starting executor thread to receive {} async callback(s)", n);
    for (int i = 0; i < n; i++) {
      executor.execute(
          () -> {
            try {
              logger.info("Async receiver thread started, waiting for callback");

              // Receive async callback message (no response sent)
              Message callbackMsg = receiveAsyncCallback();
              logger.debug("Received async callback message: {}", colferToPrettyJson(callbackMsg));

              receivedCallbacks.add(callbackMsg);
              callbackReceived.countDown();
              logger.info("Async receiver thread completed, callback received");

            } catch (Exception e) {
              logger.error("Error in async callback receiver thread", e);
              assertionError =
                  new AssertionError("Async callback receiver failed: " + e.getMessage());
            }
          });
    }

    // 4. Invoke multiplyCounterNTimesBy which triggers async callback
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) which should trigger {} async callback(s)",
        n,
        multiplier,
        n);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyCounterNTimesBy",
            appInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed");

    // 5. Wait for async callback(s) to be received
    logger.info("Waiting for {} async callback(s) to be received", n);
    boolean received = callbackReceived.await(5, TimeUnit.SECONDS);
    assertThat("All async callbacks should be received within 5 seconds", received, is(true));
    logger.info("All {} async callback(s) received successfully", n);

    // 6. Verify callback structure
    logger.info("Verifying async callback message structure");
    assertThat(
        "Should have received exactly " + n + " async callback(s)",
        receivedCallbacks.size(),
        is(n));

    for (int i = 0; i < n; i++) {
      Message callback = receivedCallbacks.get(i);
      assertThat("Async callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "Async callback should be CLASS_METHOD type",
          callback.getMessageType(),
          is(MessageType.EXEC_CLASS_METHOD.getId()));
      assertThat(
          "Async callback class should match",
          callback.getExecMessage().getClassMethodCall().getClazz().getName(),
          is(callbackClass));
      assertThat(
          "Async callback method should match",
          callback.getExecMessage().getClassMethodCall().getName(),
          is(callbackMethod));
      // AFTER_ASYNC callbacks receive the return value, not parameters
      // multiplyBy is void, so the callback has 0 parameters
      assertThat(
          "AFTER_ASYNC callback should have 0 parameters (void method)",
          callback.getExecMessage().getClassMethodCall().getParameters().length,
          is(0));
    }

    // Throw any assertion errors from callback thread
    if (assertionError != null) {
      logger.error("Test failed with assertion error: {}", assertionError.getMessage());
      throw assertionError;
    }

    logger.info("===== testSingleAfterAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER_ASYNC callbacks.
   *
   * <p>Registers an AFTER_ASYNC intercept on multiplyBy, calls it multiple times (n=3), and
   * verifies exactly 3 callbacks are received after method executions without blocking.
   */
  @Test
  public void testMultipleAfterAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleAfterAsyncCallbacks: TEST STARTED =====");

    final String callbackClass = "com.example.AsyncCallbackHandler";
    final String callbackMethod = "handleAsyncCallback";
    final int n = 3;
    final int multiplier = 2;

    // 1. Register an AFTER_ASYNC intercept on multiplyBy method
    logger.info("Creating AFTER_ASYNC intercept request for multiplyBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            asyncCallbackPeerUuid, // Use our async callback peer UUID
            InterceptType.AFTER_ASYNC,
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

    // 3. Set up async callback receiver in separate thread
    CountDownLatch callbackReceived = new CountDownLatch(n);
    List<Message> receivedCallbacks = Collections.synchronizedList(new ArrayList<>());

    logger.info("Starting executor threads to receive {} async callback(s)", n);
    for (int i = 0; i < n; i++) {
      executor.execute(
          () -> {
            try {
              logger.info("Async receiver thread started, waiting for callback");

              // Receive async callback message (no response sent)
              Message callbackMsg = receiveAsyncCallback();
              logger.debug("Received async callback message: {}", colferToPrettyJson(callbackMsg));

              receivedCallbacks.add(callbackMsg);
              callbackReceived.countDown();
              logger.info("Async receiver thread completed, callback received");

            } catch (Exception e) {
              logger.error("Error in async callback receiver thread", e);
              assertionError =
                  new AssertionError("Async callback receiver failed: " + e.getMessage());
            }
          });
    }

    // 4. Invoke multiplyCounterNTimesBy which triggers async callbacks
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) which should trigger {} async callback(s)",
        n,
        multiplier,
        n);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyCounterNTimesBy",
            appInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed");

    // 5. Wait for all async callbacks to be received
    logger.info("Waiting for {} async callback(s) to be received", n);
    boolean received = callbackReceived.await(5, TimeUnit.SECONDS);
    assertThat("All async callbacks should be received within 5 seconds", received, is(true));
    logger.info("All {} async callback(s) received successfully", n);

    // 6. Verify we received exactly n callbacks
    logger.info("Verifying exactly {} async callbacks were received", n);
    assertThat(
        "Should have received exactly " + n + " async callbacks", receivedCallbacks.size(), is(n));

    for (int i = 0; i < n; i++) {
      Message callback = receivedCallbacks.get(i);
      assertThat("Async callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "Async callback should be CLASS_METHOD type",
          callback.getMessageType(),
          is(MessageType.EXEC_CLASS_METHOD.getId()));
      assertThat(
          "Async callback class should match",
          callback.getExecMessage().getClassMethodCall().getClazz().getName(),
          is(callbackClass));
      assertThat(
          "Async callback method should match",
          callback.getExecMessage().getClassMethodCall().getName(),
          is(callbackMethod));
      // AFTER_ASYNC callbacks receive the return value, not parameters
      // multiplyBy is void, so the callback has 0 parameters
      assertThat(
          "AFTER_ASYNC callback should have 0 parameters (void method)",
          callback.getExecMessage().getClassMethodCall().getParameters().length,
          is(0));
    }

    // Throw any assertion errors from callback thread
    if (assertionError != null) {
      logger.error("Test failed with assertion error: {}", assertionError.getMessage());
      throw assertionError;
    }

    logger.info("===== testMultipleAfterAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
