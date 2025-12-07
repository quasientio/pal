/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.mechanism.staticfield;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.Unwrapper;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;

/**
 * Integration tests for asynchronous static field intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify the end-to-end callback mechanism for asynchronous intercepts on static
 * field operations (EXEC_GET_STATIC and EXEC_PUT_STATIC) using DEALER sockets, including single and
 * multiple callbacks for both BEFORE_ASYNC and AFTER_ASYNC intercept types.
 *
 * <p>Unlike synchronous callbacks which use REQ-REP pattern and wait for responses, async callbacks
 * use DEALER-ROUTER pattern for fire-and-forget delivery.
 *
 * <p><b>NOTE:</b>These tests verify intercepts at the hot-path (via quantization, which happens at
 * the call-site), and so, we need to invoke via RPC a method/ctor that triggers the actual
 * interception target.
 */
public class StaticFieldAsyncCallbackIT extends AbstractInterceptIT {

  /** ThinPeer for receiving async callbacks via ROUTER socket. */
  private ThinPeer asyncCallbackPeer;

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7894";

  /** UUID for the async callback receiver peer (registered in directory). */
  private final UUID asyncCallbackPeerUuid = UUID.randomUUID();

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
    if (asyncCallbackPeer != null) {
      asyncCallbackPeer.close();
      logger.info("Async callback peer closed");
    }
  }

  /** Tests single BEFORE_ASYNC callback on static field GET operation. */
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
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "getStaticCounter",
            new String[] {},
            null,
            null,
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
    // Verify the intercepted operation is a static field GET
    assertThat(
        "Intercepted operation should be StaticFieldGet",
        callback.getInterceptCallbackRequest().getExec().getStaticFieldGet(),
        is(notNullValue()));

    logger.info("===== testSingleBeforeAsyncCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests single AFTER_ASYNC callback on static field GET operation. */
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
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "getStaticCounter",
            new String[] {},
            null,
            null,
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
    assertThat(
        "ReturnValue should have field info",
        callback.getInterceptCallbackRequest().getExec().getReturnValue().getFrom().getField(),
        is(notNullValue()));

    logger.info("===== testSingleAfterAsyncCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests single BEFORE_ASYNC callback on static field PUT operation. */
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
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {newValue}));

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
    // BEFORE PUT callback wraps the PUT operation
    assertThat(
        "Callback should be StaticFieldPut",
        callback.getInterceptCallbackRequest().getExec().getStaticFieldPut(),
        is(notNullValue()));

    // Verify the value being PUT matches what we passed to the setter
    Obj putValueObj =
        callback.getInterceptCallbackRequest().getExec().getStaticFieldPut().getValueObject();
    Object value = Unwrapper.unwrapObject(putValueObj);
    assertThat("PUT value should match the value passed to setter", value, is(newValue));

    logger.info("===== testSingleBeforeAsyncCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests single AFTER_ASYNC callback on static field PUT operation. */
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
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {newValue}));

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
    assertThat(
        "Callback should have StaticFieldPutDone",
        callback.getInterceptCallbackRequest().getExec().getStaticFieldPutDone(),
        is(notNullValue()));
    assertThat(
        "StaticFieldPutDone should have field info",
        callback.getInterceptCallbackRequest().getExec().getStaticFieldPutDone().getField(),
        is(notNullValue()));

    logger.info("===== testSingleAfterAsyncCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }
}
