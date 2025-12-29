/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.mechanism.instancefield;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
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
 *
 * <p>Tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Intercepts triggered via AspectJ weaving at call-site (getter/setter calls
 *       field access)
 *   <li><b>INCOMING_RPC</b>: Intercepts triggered via direct RPC message dispatch
 * </ul>
 *
 * <p><b>Note:</b> Instance field PUT tests have path-dependent callback counts. HOT_PATH triggers
 * the field initializer when creating the instance, resulting in an extra callback.
 */
@RunWith(Parameterized.class)
public class InstanceFieldAsyncCallbackIT extends AbstractInterceptIT {

  /** ThinPeer for receiving async callbacks via ROUTER socket. */
  private ThinPeer asyncCallbackPeer;

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7895";

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /** UUID for the async callback receiver peer (registered in directory). */
  private final UUID asyncCallbackPeerUuid = UUID.randomUUID();

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public InstanceFieldAsyncCallbackIT(InvocationPath path) {
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
   * Invokes a field GET operation on the counter field through the specified invocation path.
   *
   * @param appInstance the target object (only used for HOT_PATH)
   * @return the response ExecMessage
   */
  private ExecMessage invokeFieldGet(ObjectRef appInstance) {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use getter method that accesses the field (triggers intercept via call-site)
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "getCounter",
              appInstance,
              new String[] {},
              new Object[] {}));
    } else {
      // INCOMING_RPC: Call field get directly
      return invoke(
          messageBuilder.buildGetObject(
              myPeerUuid, InterceptableApp.class.getName(), "counter", appInstance));
    }
  }

  /**
   * Invokes a field PUT operation on the counter field through the specified invocation path.
   *
   * @param appInstance the target object (only used for HOT_PATH)
   * @param value the value to set
   * @return the response ExecMessage
   */
  private ExecMessage invokeFieldPut(ObjectRef appInstance, int value) {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use setter method that accesses the field (triggers intercept via call-site)
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "setCounter",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {value}));
    } else {
      // INCOMING_RPC: Call field put directly
      return invoke(
          messageBuilder.buildPutObject(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "counter",
              appInstance,
              "java.lang.Integer",
              value));
    }
  }

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

  /** Tests single BEFORE_ASYNC callback on instance field GET operation. */
  @Test
  public void testSingleBeforeAsyncCallbackOnGet() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallbackOnGet [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // Invoke field GET through the specified path
    logger.info("Invoking field GET via {} path which should trigger 1 callback", path);
    invokeFieldGet(appInstance);

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
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    // Verify the intercepted operation is an instance field GET
    assertThat(
        "Intercepted operation should be InstanceFieldGet",
        callback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldGet(),
        is(notNullValue()));

    logger.info(
        "===== testSingleBeforeAsyncCallbackOnGet [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /** Tests single AFTER_ASYNC callback on instance field GET operation. */
  @Test
  public void testSingleAfterAsyncCallbackOnGet() throws Exception {
    logger.info("===== testSingleAfterAsyncCallbackOnGet [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // Invoke field GET through the specified path
    logger.info("Invoking field GET via {} path which should trigger 1 callback", path);
    invokeFieldGet(appInstance);

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
        callback.getInterceptCallbackRequestMessage().getCallbackClass(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getInterceptCallbackRequestMessage().getCallbackMethod(),
        is(callbackMethod));
    // AFTER GET callbacks wrap the ReturnValue (the field value that was read)
    assertThat(
        "Intercepted operation should have ReturnValue",
        callback.getInterceptCallbackRequestMessage().getExec().getReturnValue(),
        is(notNullValue()));

    logger.info(
        "===== testSingleAfterAsyncCallbackOnGet [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests BEFORE_ASYNC callback on instance field PUT operation.
   *
   * <p>Registers a BEFORE_ASYNC intercept on counter, creates an app instance, calls a setter once.
   *
   * <p>For HOT_PATH: Verifies 2 callbacks are received (one from field initializer, one from
   * setter). For INCOMING_RPC: Verifies 1 callback is received (only from the direct PUT call). To
   * achieve this, we create the instance before registering the intercept for INCOMING_RPC.
   */
  @Test
  public void testBeforeAsyncCallbackOnPut() throws Exception {
    logger.info("===== testBeforeAsyncCallbackOnPut [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // For INCOMING_RPC, create instance BEFORE registering intercept to avoid
    // intercepting the field initializer PUT
    ObjectRef appInstance = null;
    if (path == InvocationPath.INCOMING_RPC) {
      logger.info("Creating InterceptableApp instance before intercept registration");
      appInstance =
          ObjectRef.from(
              invoke(
                      messageBuilder.buildEmptyConstructor(
                          myPeerUuid, InterceptableApp.class.getName()))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      logger.info("InterceptableApp instance created with ref: {}", appInstance);
    }

    // 1. Register a BEFORE intercept on counter field PUT
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    // Wait for intercept registration to propagate
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. For HOT_PATH, create InterceptableApp instance after registering intercept
    // (field initializer will be intercepted)
    if (path == InvocationPath.HOT_PATH) {
      logger.info("Creating InterceptableApp instance (will trigger field initializer callback)");
      appInstance =
          ObjectRef.from(
              invoke(
                      messageBuilder.buildEmptyConstructor(
                          myPeerUuid, InterceptableApp.class.getName()))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      logger.info("InterceptableApp instance created with ref: {}", appInstance);
    }

    // 3. Invoke field PUT through the specified path
    logger.info("Invoking field PUT via {} path", path);
    ExecMessage response = invokeFieldPut(appInstance, newValue);
    logger.info("Field PUT invocation completed");

    // 4. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Retrieve and verify callbacks based on path
    // HOT_PATH: 2 callbacks (field initializer + setter call)
    // INCOMING_RPC: 1 callback (only direct PUT call)
    final int expectedCallbacks = (path == InvocationPath.HOT_PATH) ? 2 : 1;
    logger.info("Waiting for {} callback(s) to be received", expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("Callbacks received successfully");

    assertThat(
        "Should receive exactly " + expectedCallbacks + " callback(s)",
        callbacks.size(),
        is(expectedCallbacks));

    if (path == InvocationPath.HOT_PATH) {
      // 6a. Verify callback structure for HOT_PATH (2 callbacks)
      Message initCallback = callbacks.get(0);
      assertThat("PUT callback message should not be null", initCallback, is(notNullValue()));
      assertThat(
          "PUT callback should be INTERCEPT_CALLBACK_REQUEST type",
          initCallback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "First callback should be InstanceFieldPut",
          initCallback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPut(),
          is(notNullValue()));

      // Verify value passed to fieldput is the initializer value (triggered by ctor)
      Obj putValueObj =
          initCallback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getInstanceFieldPut()
              .getValueObject();
      Object value = Unwrapper.unwrapObject(putValueObj);
      assertThat("First callback put value should be 1 (initializer value in class)", value, is(1));

      // The second callback is from the field put triggered by the setter we invoke
      Message setterCallback = callbacks.get(1);
      assertThat("Callback message should not be null", setterCallback, is(notNullValue()));
      assertThat(
          "Second callback should be InstanceFieldPut",
          setterCallback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPut(),
          is(notNullValue()));

      // Verify value passed to fieldput is the value we call the setter with
      putValueObj =
          setterCallback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getInstanceFieldPut()
              .getValueObject();
      value = Unwrapper.unwrapObject(putValueObj);
      assertThat("Second callback put value should be value passed to setter", value, is(newValue));
    } else {
      // 6b. Verify callback structure for INCOMING_RPC (1 callback)
      Message callback = callbacks.get(0);
      assertThat("PUT callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "PUT callback should be INTERCEPT_CALLBACK_REQUEST type",
          callback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "Callback class should match",
          callback.getInterceptCallbackRequestMessage().getCallbackClass(),
          is(callbackClass));
      assertThat(
          "Callback should be InstanceFieldPut",
          callback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPut(),
          is(notNullValue()));

      // Verify value passed to fieldput is the value we pass directly
      Obj putValueObj =
          callback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getInstanceFieldPut()
              .getValueObject();
      Object value = Unwrapper.unwrapObject(putValueObj);
      assertThat("Callback put value should be value passed to PUT", value, is(newValue));
    }

    logger.info("===== testBeforeAsyncCallbackOnPut [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests AFTER_ASYNC callback on instance field PUT operation.
   *
   * <p>Registers an AFTER_ASYNC intercept on counter, creates an app instance, calls a setter.
   *
   * <p>For HOT_PATH: Verifies 2 callbacks are received (one from field initializer, one from
   * setter). For INCOMING_RPC: Verifies 1 callback is received (only from the direct PUT call). To
   * achieve this, we create the instance before registering the intercept for INCOMING_RPC.
   */
  @Test
  public void testAfterAsyncCallbackOnPut() throws Exception {
    logger.info("===== testAfterAsyncCallbackOnPut [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "aFakeMethod";
    final int newValue = 200;

    // For INCOMING_RPC, create instance BEFORE registering intercept to avoid
    // intercepting the field initializer PUT
    ObjectRef appInstance = null;
    if (path == InvocationPath.INCOMING_RPC) {
      logger.info("Creating InterceptableApp instance before intercept registration");
      appInstance =
          ObjectRef.from(
              invoke(
                      messageBuilder.buildEmptyConstructor(
                          myPeerUuid, InterceptableApp.class.getName()))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      logger.info("InterceptableApp instance created with ref: {}", appInstance);
    }

    // 1. Register an AFTER intercept on counter field PUT
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            asyncCallbackPeerUuid,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. For HOT_PATH, create InterceptableApp instance after registering intercept
    // (field initializer will be intercepted)
    if (path == InvocationPath.HOT_PATH) {
      logger.info("Creating InterceptableApp instance (will trigger field initializer callback)");
      appInstance =
          ObjectRef.from(
              invoke(
                      messageBuilder.buildEmptyConstructor(
                          myPeerUuid, InterceptableApp.class.getName()))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      logger.info("InterceptableApp instance created with ref: {}", appInstance);
    }

    // 3. Invoke field PUT through the specified path
    logger.info("Invoking field PUT via {} path", path);
    invokeFieldPut(appInstance, newValue);

    // 4. Retrieve and verify callbacks based on path
    // HOT_PATH: 2 callbacks (field initializer + setter call)
    // INCOMING_RPC: 1 callback (only direct PUT call)
    final int expectedCallbacks = (path == InvocationPath.HOT_PATH) ? 2 : 1;
    logger.info("Waiting for {} callback(s) to be received", expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("Callbacks received successfully");

    assertThat(
        "Should receive exactly " + expectedCallbacks + " callback(s)",
        callbacks.size(),
        is(expectedCallbacks));

    if (path == InvocationPath.HOT_PATH) {
      // 5a. Verify callback structure for HOT_PATH (2 callbacks)

      // First callback from field initializer during ctor
      Message initCallback = callbacks.get(0);
      assertThat("PUT_DONE callback message should not be null", initCallback, is(notNullValue()));
      assertThat(
          "PUT_DONE callback should be INTERCEPT_CALLBACK_REQUEST type",
          initCallback.getMessageType(),
          is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));
      assertThat(
          "First callback should be InstanceFieldPutDone",
          initCallback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPutDone(),
          is(notNullValue()));

      // Second callback from the setter we invoke
      Message setterCallback = callbacks.get(1);
      assertThat(
          "PUT_DONE callback message should not be null", setterCallback, is(notNullValue()));
      assertThat(
          "Callback should be InstanceFieldPutDone",
          setterCallback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPutDone(),
          is(notNullValue()));
    } else {
      // 5b. Verify callback structure for INCOMING_RPC (1 callback)
      Message callback = callbacks.get(0);
      assertThat("PUT_DONE callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "PUT_DONE callback should be INTERCEPT_CALLBACK_REQUEST type",
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
      assertThat(
          "Callback should be InstanceFieldPutDone",
          callback.getInterceptCallbackRequestMessage().getExec().getInstanceFieldPutDone(),
          is(notNullValue()));
      assertThat(
          "InstanceFieldPutDone should have field info",
          callback
              .getInterceptCallbackRequestMessage()
              .getExec()
              .getInstanceFieldPutDone()
              .getField(),
          is(notNullValue()));
    }

    logger.info("===== testAfterAsyncCallbackOnPut [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }
}
