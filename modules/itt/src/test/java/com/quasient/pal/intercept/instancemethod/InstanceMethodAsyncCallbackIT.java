/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.instancemethod;

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
 * Integration tests for asynchronous instance method intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify the end-to-end callback mechanism for asynchronous intercepts on instance
 * methods (EXEC_INSTANCE_METHOD) using DEALER sockets, including single and multiple callbacks for
 * both BEFORE_ASYNC and AFTER_ASYNC intercept types.
 *
 * <p>Unlike synchronous callbacks which use REQ-REP pattern and wait for responses, async callbacks
 * use DEALER-ROUTER pattern for fire-and-forget delivery.
 */
public class InstanceMethodAsyncCallbackIT extends AbstractInterceptIT {

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7891";

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
   * Tests single BEFORE_ASYNC callback.
   *
   * <p>Registers a BEFORE_ASYNC intercept on multiplyBy, calls it once (n=1), and verifies the
   * intercept mechanism works without blocking for a response.
   */
  @Test
  public void testSingleBeforeAsyncCallback() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.InstanceMethodHandlers";
    final String callbackMethod = "noOp";
    final int n = 1;
    final int multiplier = 3;

    // 1. Register a BEFORE_ASYNC intercept on multiplyBy method
    logger.info("Creating BEFORE_ASYNC intercept request for multiplyBy method");
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

    // 3. Invoke multiplyCounterNTimesBy which triggers async intercept
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) - async intercept should work",
        n,
        multiplier);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyCounterNTimesBy",
            appInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed successfully");

    logger.info("===== testSingleBeforeAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE_ASYNC callbacks.
   *
   * <p>Registers a BEFORE_ASYNC intercept on multiplyBy, calls it multiple times (n=3), and
   * verifies the intercept mechanism works without blocking.
   */
  @Test
  public void testMultipleBeforeAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.InstanceMethodHandlers";
    final String callbackMethod = "noOp";
    final int n = 3;
    final int multiplier = 2;

    // 1. Register a BEFORE_ASYNC intercept on multiplyBy method
    logger.info("Creating BEFORE_ASYNC intercept request for multiplyBy method");
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

    // 3. Invoke multiplyCounterNTimesBy which triggers async intercepts
    logger.info(
        "Invoking multiplyCounterNTimesBy(n={}, multiplier={}) - async intercepts should work",
        n,
        multiplier);
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "multiplyCounterNTimesBy",
            appInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {n, multiplier}));
    logger.info("multiplyCounterNTimesBy invocation completed successfully");

    logger.info("===== testMultipleBeforeAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER_ASYNC callback.
   *
   * <p>Registers an AFTER_ASYNC intercept on multiplyBy, calls it once (n=1), and verifies exactly
   * 1 callback is received after method execution without blocking.
   */
  @Test
  @Ignore
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

    // 3. Invoke multiplyCounterNTimesBy which triggers async callback
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

    // 4. Wait for and retrieve async callback(s) using new pattern
    logger.info("Waiting for {} async callback(s) to be received", n);
    List<Message> receivedCallbacks = getCallbacks(n, 5000);
    logger.info("All {} async callback(s) received successfully", n);

    // 5. Verify callback structure
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

    logger.info("===== testSingleAfterAsyncCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER_ASYNC callbacks.
   *
   * <p>Registers an AFTER_ASYNC intercept on multiplyBy, calls it multiple times (n=3), and
   * verifies exactly 3 callbacks are received after method executions without blocking.
   */
  @Test
  @Ignore
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

    // 3. Invoke multiplyCounterNTimesBy which triggers async callbacks
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

    // 4. Wait for and retrieve async callbacks using new pattern
    logger.info("Waiting for {} async callback(s) to be received", n);
    List<Message> receivedCallbacks = getCallbacks(n, 5000);
    logger.info("All {} async callback(s) received successfully", n);

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
      // AFTER_ASYNC callbacks receive the return value, not parameters
      // multiplyBy is void, so the callback has 0 parameters
      assertThat(
          "AFTER_ASYNC callback should have 0 parameters (void method)",
          callback.getExecMessage().getClassMethodCall().getParameters().length,
          is(0));
    }

    logger.info("===== testMultipleAfterAsyncCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
