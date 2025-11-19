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

import com.quasient.pal.apps.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.SocketType;

/**
 * Integration tests for asynchronous static method intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify the end-to-end callback mechanism for asynchronous intercepts on static
 * methods (EXEC_CLASS_METHOD) using DEALER sockets, including single and multiple callbacks for
 * both BEFORE_ASYNC and AFTER_ASYNC intercept types.
 *
 * <p>Unlike synchronous callbacks which use REQ-REP pattern and wait for responses, async callbacks
 * use DEALER-ROUTER pattern for fire-and-forget delivery.
 */
public class StaticMethodAsyncCallbackIT extends AbstractInterceptIT {

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7892";

  /** UUID for the async callback receiver peer (registered in directory). */
  private final UUID asyncCallbackPeerUuid = UUID.randomUUID();

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

  /** Closes the async callback ThinPeer after tests complete. */
  @After
  public void tearDownAsyncReceiver() {
    if (asyncCallbackPeer != null) {
      asyncCallbackPeer.close();
      logger.info("Async callback peer closed");
    }
  }

  /**
   * Tests single BEFORE_ASYNC callback on static method.
   *
   * <p>Registers a BEFORE_ASYNC intercept on multiplyStaticBy, calls it once, and verifies exactly
   * 1 callback is received without blocking for a response.
   */
  @Test
  @Ignore
  public void testSingleBeforeAsyncCallback() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallback: TEST STARTED =====");

    final String callbackClass = "com.example.AsyncCallbackHandler";
    final String callbackMethod = "handleAsyncCallback";
    final int multiplier = 3;

    // 1. Register a BEFORE_ASYNC intercept on multiplyStaticBy method
    logger.info("Creating BEFORE_ASYNC intercept request for multiplyStaticBy method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
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

    // 3. Create InterceptableApp instance for calling wrapper method
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

    // 4. Invoke callMultiplyStaticBy which triggers async callback via call-site
    logger.info(
        "Invoking callMultiplyStaticBy wrapper(multiplier={}) which should trigger async callback",
        multiplier);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "callMultiplyStaticBy",
            appInstance,
            new String[] {"java.lang.Integer"},
            new Object[] {multiplier}));
    logger.info("callMultiplyStaticBy invocation completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving callbacks using getCallbacks()");
    List<Message> receivedCallbacks = getCallbacks(1, 5000);
    logger.info("Callbacks received successfully");

    // 5. Verify callback structure
    logger.info("Verifying async callback message structure");
    assertThat("Should have received exactly 1 async callback", receivedCallbacks.size(), is(1));

    Message callback = receivedCallbacks.get(0);
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

    logger.info("===== testSingleBeforeAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE_ASYNC callbacks on static method.
   *
   * <p>Registers a BEFORE_ASYNC intercept on incrementStaticCounter, calls it multiple times (n=3),
   * and verifies exactly 3 callbacks are received without blocking.
   */
  @Test
  @Ignore
  public void testMultipleBeforeAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST STARTED =====");

    final String callbackClass = "com.example.AsyncCallbackHandler";
    final String callbackMethod = "handleAsyncCallback";
    final int n = 3;

    // 1. Register a BEFORE_ASYNC intercept on incrementStaticCounter method
    logger.info("Creating BEFORE_ASYNC intercept request for incrementStaticCounter method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
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

    // 3. Create InterceptableApp instance for calling wrapper method
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

    // 4. Invoke callIncrementStaticCounter multiple times (triggers incrementStaticCounter via
    // call-site)
    logger.info(
        "Invoking callIncrementStaticCounter wrapper {} times which should trigger {} async callbacks",
        n,
        n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "callIncrementStaticCounter",
              appInstance,
              new String[] {},
              new Object[] {}));
    }
    logger.info("callIncrementStaticCounter invocations completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving {} callback(s) using getCallbacks()", n);
    List<Message> receivedCallbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    // 5. Verify we received exactly n callbacks
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

    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER_ASYNC callback on static method.
   *
   * <p>Registers an AFTER_ASYNC intercept on multiplyStaticBy, calls it once, and verifies exactly
   * 1 callback is received after method execution without blocking.
   */
  @Test
  @Ignore
  public void testSingleAfterAsyncCallback() throws Exception {
    logger.info("===== testSingleAfterAsyncCallback: TEST STARTED =====");

    final String callbackClass = "com.example.AsyncCallbackHandler";
    final String callbackMethod = "handleAsyncCallback";
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

    // 3. Create InterceptableApp instance for calling wrapper method
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

    // 4. Invoke callMultiplyStaticBy which triggers async callback via call-site
    logger.info(
        "Invoking callMultiplyStaticBy wrapper(multiplier={}) which should trigger async callback",
        multiplier);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "callMultiplyStaticBy",
            appInstance,
            new String[] {"java.lang.Integer"},
            new Object[] {multiplier}));
    logger.info("callMultiplyStaticBy invocation completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving callbacks using getCallbacks()");
    List<Message> receivedCallbacks = getCallbacks(1, 5000);
    logger.info("Callbacks received successfully");

    // 5. Verify callback structure
    logger.info("Verifying async callback message structure");
    assertThat("Should have received exactly 1 async callback", receivedCallbacks.size(), is(1));

    Message callback = receivedCallbacks.get(0);
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
    // AFTER_ASYNC callbacks receive the return value as parameter
    // multiplyStaticBy returns Integer, so the callback has 1 parameter
    assertThat(
        "AFTER_ASYNC callback should have 1 parameter (Integer return value)",
        callback.getExecMessage().getClassMethodCall().getParameters().length,
        is(1));

    logger.info("===== testSingleAfterAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER_ASYNC callbacks on static method.
   *
   * <p>Registers an AFTER_ASYNC intercept on incrementStaticCounter, calls it multiple times (n=3),
   * and verifies exactly 3 callbacks are received after method executions without blocking.
   */
  @Test
  @Ignore
  public void testMultipleAfterAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleAfterAsyncCallbacks: TEST STARTED =====");

    final String callbackClass = "com.example.AsyncCallbackHandler";
    final String callbackMethod = "handleAsyncCallback";
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

    // 3. Create InterceptableApp instance for calling wrapper method
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

    // 4. Invoke callIncrementStaticCounter multiple times (triggers incrementStaticCounter via
    // call-site)
    logger.info(
        "Invoking callIncrementStaticCounter wrapper {} times which should trigger {} async callbacks",
        n,
        n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "callIncrementStaticCounter",
              appInstance,
              new String[] {},
              new Object[] {}));
    }
    logger.info("callIncrementStaticCounter invocations completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving {} callback(s) using getCallbacks()", n);
    List<Message> receivedCallbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    // 5. Verify we received exactly n callbacks
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
      // AFTER_ASYNC callbacks receive the return value as parameter
      // incrementStaticCounter returns Integer, so the callback has 1 parameter
      assertThat(
          "AFTER_ASYNC callback should have 1 parameter (Integer return value)",
          callback.getExecMessage().getClassMethodCall().getParameters().length,
          is(1));
    }

    logger.info("===== testMultipleAfterAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
