/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.instancefield;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.SocketType;

/**
 * Integration tests for asynchronous instance field intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify the end-to-end callback mechanism for asynchronous intercepts on static
 * field operations (EXEC_GET_FIELD and EXEC_PUT_FIELD) using DEALER sockets, including single and
 * multiple callbacks for both BEFORE_ASYNC and AFTER_ASYNC intercept types.
 *
 * <p>Unlike synchronous callbacks which use REQ-REP pattern and wait for responses, async callbacks
 * use DEALER-ROUTER pattern for fire-and-forget delivery.
 */
public class InstanceFieldAsyncCallbackIT extends AbstractInterceptIT {

  /** ThinPeer for receiving async callbacks via ROUTER socket. */
  private ThinPeer asyncCallbackPeer;

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7895";

  /** UUID for the async callback receiver peer (registered in directory). */
  private final UUID asyncCallbackPeerUuid = UUID.randomUUID();

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

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

  /** Closes the ThinPeer and unregisters from directory after tests complete. */
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

  // Test methods for GET operation - 4 tests total (single/multiple BEFORE_ASYNC/AFTER_ASYNC)

  /** Tests single BEFORE_ASYNC callback on instance field GET operation. */
  @Test
  public void testSingleBeforeAsyncCallbackOnGet() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "getCounter",
            appInstance,
            new String[] {},
            new Object[] {}));

    // Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // Verify callback structure
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
    // Verify the intercepted operation is an instance field GET
    assertThat(
        "Intercepted operation should be InstanceFieldGet",
        callback.getInterceptCallbackRequest().getExec().getInstanceFieldGet(),
        is(notNullValue()));

    logger.info("===== testSingleBeforeAsyncCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests multiple BEFORE_ASYNC callbacks on instance field GET operation. */
  @Test
  public void testMultipleBeforeAsyncCallbacksOnGet() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacksOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "getCounter",
              appInstance,
              new String[] {},
              new Object[] {}));
    }

    // Retrieve and verify callbacks
    logger.info("Waiting for {} callbacks to be received", n);
    List<Message> callbacks = getCallbacks(n, 5000);
    logger.info("All {} callbacks received successfully", n);

    assertThat("Should receive exactly " + n + " callbacks", callbacks.size(), is(n));

    // Verify each callback structure
    for (int i = 0; i < n; i++) {
      Message callback = callbacks.get(i);
      assertThat("Callback " + i + " should not be null", callback, is(notNullValue()));
      assertThat(
          "Callback " + i + " should be INTERCEPT_CALLBACK_REQUEST type",
          callback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "Callback " + i + " class should match",
          callback.getInterceptCallbackRequest().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "Callback " + i + " method should match",
          callback.getInterceptCallbackRequest().getCallbackMethod(),
          is(callbackMethod));
      assertThat(
          "Callback " + i + " should be InstanceFieldGet",
          callback.getInterceptCallbackRequest().getExec().getInstanceFieldGet(),
          is(notNullValue()));
    }

    logger.info("===== testMultipleBeforeAsyncCallbacksOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests single AFTER_ASYNC callback on instance field GET operation. */
  @Test
  public void testSingleAfterAsyncCallbackOnGet() throws Exception {
    logger.info("===== testSingleAfterAsyncCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "getCounter",
            appInstance,
            new String[] {},
            new Object[] {}));

    // Retrieve and verify callbacks
    logger.info("Waiting for 1 callback to be received");
    List<Message> callbacks = getCallbacks(1, 5000);
    logger.info("Callback received successfully");

    assertThat("Should receive exactly 1 callback", callbacks.size(), is(1));

    // Verify callback structure
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
    // AFTER GET callbacks wrap the ReturnValue (the field value that was read)
    assertThat(
        "Intercepted operation should have ReturnValue",
        callback.getInterceptCallbackRequest().getExec().getReturnValue(),
        is(notNullValue()));

    logger.info("===== testSingleAfterAsyncCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests multiple AFTER_ASYNC callbacks on instance field GET operation. */
  @Test
  public void testMultipleAfterAsyncCallbacksOnGet() throws Exception {
    logger.info("===== testMultipleAfterAsyncCallbacksOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "getCounter",
              appInstance,
              new String[] {},
              new Object[] {}));
    }

    // Retrieve and verify callbacks
    logger.info("Waiting for {} callbacks to be received", n);
    List<Message> callbacks = getCallbacks(n, 5000);
    logger.info("All {} callbacks received successfully", n);

    assertThat("Should receive exactly " + n + " callbacks", callbacks.size(), is(n));

    // Verify each callback structure
    for (int i = 0; i < n; i++) {
      Message callback = callbacks.get(i);
      assertThat("Callback " + i + " should not be null", callback, is(notNullValue()));
      assertThat(
          "Callback " + i + " should be INTERCEPT_CALLBACK_REQUEST type",
          callback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "Callback " + i + " class should match",
          callback.getInterceptCallbackRequest().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "Callback " + i + " method should match",
          callback.getInterceptCallbackRequest().getCallbackMethod(),
          is(callbackMethod));
      // AFTER GET callbacks wrap the ReturnValue
      assertThat(
          "Callback " + i + " should have ReturnValue",
          callback.getInterceptCallbackRequest().getExec().getReturnValue(),
          is(notNullValue()));
    }

    logger.info("===== testMultipleAfterAsyncCallbacksOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  // Test methods for PUT operation - 4 tests total (single/multiple BEFORE_ASYNC/AFTER_ASYNC)

  /** Tests single BEFORE_ASYNC callback on instance field PUT operation. */
  @Test
  public void testSingleBeforeAsyncCallbackOnPut() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setCounter",
            appInstance,
            new String[] {"java.lang.Integer"},
            new Object[] {newValue}));

    // Retrieve and verify callbacks - PUT generates 2 messages (PUT + PUT_DONE)
    logger.info("Waiting for 2 callbacks to be received (PUT + PUT_DONE)");
    List<Message> callbacks = getCallbacks(2, 5000);
    logger.info("Callbacks received successfully");

    assertThat("Should receive exactly 2 callbacks (PUT + PUT_DONE)", callbacks.size(), is(2));

    // Verify first callback (PUT) structure
    Message putCallback = callbacks.get(0);
    assertThat("PUT callback message should not be null", putCallback, is(notNullValue()));
    assertThat(
        "PUT callback should be INTERCEPT_CALLBACK_REQUEST type",
        putCallback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "PUT callback class should match",
        putCallback.getInterceptCallbackRequest().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "PUT callback method should match",
        putCallback.getInterceptCallbackRequest().getCallbackMethod(),
        is(callbackMethod));
    // BEFORE PUT callback wraps the PUT operation
    assertThat(
        "First callback should be InstanceFieldPut",
        putCallback.getInterceptCallbackRequest().getExec().getInstanceFieldPut(),
        is(notNullValue()));

    logger.info("===== testSingleBeforeAsyncCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests multiple BEFORE_ASYNC callbacks on instance field PUT operation. */
  @Test
  @Ignore
  public void testMultipleBeforeAsyncCallbacksOnPut() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacksOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "setCounter",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {200 + i}));
    }

    // Retrieve and verify callbacks - each PUT generates 2 messages (PUT + PUT_DONE)
    final int expectedCallbacks = n * 2;
    logger.info("Waiting for {} callbacks to be received ({} PUTs * 2)", expectedCallbacks, n);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("All {} callbacks received successfully", expectedCallbacks);

    assertThat(
        "Should receive exactly " + expectedCallbacks + " callbacks",
        callbacks.size(),
        is(expectedCallbacks));

    // Verify each callback structure (alternating PUT and PUT_DONE)
    for (int i = 0; i < expectedCallbacks; i++) {
      Message callback = callbacks.get(i);
      assertThat("Callback " + i + " should not be null", callback, is(notNullValue()));
      assertThat(
          "Callback " + i + " should be INTERCEPT_CALLBACK_REQUEST type",
          callback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "Callback " + i + " class should match",
          callback.getInterceptCallbackRequest().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "Callback " + i + " method should match",
          callback.getInterceptCallbackRequest().getCallbackMethod(),
          is(callbackMethod));
      // BEFORE PUT callbacks wrap either PUT or PUT_DONE
      assertThat(
          "Callback " + i + " should be InstanceFieldPut or InstanceFieldPutDone",
          callback.getInterceptCallbackRequest().getExec().getInstanceFieldPut() != null
              || callback.getInterceptCallbackRequest().getExec().getInstanceFieldPutDone() != null,
          is(true));
    }

    logger.info("===== testMultipleBeforeAsyncCallbacksOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests single AFTER_ASYNC callback on instance field PUT operation. */
  @Test
  public void testSingleAfterAsyncCallbackOnPut() throws Exception {
    logger.info("===== testSingleAfterAsyncCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setCounter",
            appInstance,
            new String[] {"java.lang.Integer"},
            new Object[] {newValue}));

    // Retrieve and verify callbacks - PUT generates 2 messages (PUT + PUT_DONE)
    logger.info("Waiting for 2 callbacks to be received (PUT + PUT_DONE)");
    List<Message> callbacks = getCallbacks(2, 5000);
    logger.info("Callbacks received successfully");

    assertThat("Should receive exactly 2 callbacks (PUT + PUT_DONE)", callbacks.size(), is(2));

    // Verify second callback (PUT_DONE) structure - AFTER callbacks focus on PUT_DONE
    Message putDoneCallback = callbacks.get(1);
    assertThat("PUT_DONE callback message should not be null", putDoneCallback, is(notNullValue()));
    assertThat(
        "PUT_DONE callback should be INTERCEPT_CALLBACK_REQUEST type",
        putDoneCallback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
    assertThat(
        "PUT_DONE callback class should match",
        putDoneCallback.getInterceptCallbackRequest().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "PUT_DONE callback method should match",
        putDoneCallback.getInterceptCallbackRequest().getCallbackMethod(),
        is(callbackMethod));
    // AFTER PUT callback wraps the PUT_DONE operation
    assertThat(
        "Second callback should be InstanceFieldPutDone",
        putDoneCallback.getInterceptCallbackRequest().getExec().getInstanceFieldPutDone(),
        is(notNullValue()));

    logger.info("===== testSingleAfterAsyncCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests multiple AFTER_ASYNC callbacks on instance field PUT operation. */
  @Test
  @Ignore
  public void testMultipleAfterAsyncCallbacksOnPut() throws Exception {
    logger.info("===== testMultipleAfterAsyncCallbacksOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int n = 3;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create InterceptableApp instance
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "setCounter",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {200 + i}));
    }

    // Retrieve and verify callbacks - each PUT generates 2 messages (PUT + PUT_DONE)
    final int expectedCallbacks = n * 2;
    logger.info("Waiting for {} callbacks to be received ({} PUTs * 2)", expectedCallbacks, n);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("All {} callbacks received successfully", expectedCallbacks);

    assertThat(
        "Should receive exactly " + expectedCallbacks + " callbacks",
        callbacks.size(),
        is(expectedCallbacks));

    // Verify each callback structure (alternating PUT and PUT_DONE)
    for (int i = 0; i < expectedCallbacks; i++) {
      Message callback = callbacks.get(i);
      assertThat("Callback " + i + " should not be null", callback, is(notNullValue()));
      assertThat(
          "Callback " + i + " should be INTERCEPT_CALLBACK_REQUEST type",
          callback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "Callback " + i + " class should match",
          callback.getInterceptCallbackRequest().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "Callback " + i + " method should match",
          callback.getInterceptCallbackRequest().getCallbackMethod(),
          is(callbackMethod));
      // AFTER PUT callbacks wrap either PUT or PUT_DONE
      assertThat(
          "Callback " + i + " should be InstanceFieldPut or InstanceFieldPutDone",
          callback.getInterceptCallbackRequest().getExec().getInstanceFieldPut() != null
              || callback.getInterceptCallbackRequest().getExec().getInstanceFieldPutDone() != null,
          is(true));
    }

    logger.info("===== testMultipleAfterAsyncCallbacksOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }
}
