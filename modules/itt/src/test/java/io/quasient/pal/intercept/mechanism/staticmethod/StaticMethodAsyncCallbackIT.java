/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.mechanism.staticmethod;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
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
 * <p>Tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Intercepts triggered via AspectJ weaving at call-site (wrapper method
 *       calls target)
 *   <li><b>INCOMING_RPC</b>: Intercepts triggered via direct RPC message dispatch
 * </ul>
 */
@RunWith(Parameterized.class)
public class StaticMethodAsyncCallbackIT extends AbstractInterceptIT {

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7892";

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /** UUID for the async callback receiver peer (registered in directory). */
  private final UUID asyncCallbackPeerUuid = UUID.randomUUID();

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** ThinPeer for receiving async callbacks via ROUTER socket. */
  private ThinPeer asyncCallbackPeer;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public StaticMethodAsyncCallbackIT(InvocationPath path) {
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
   * Invokes multiplyStaticBy once through the specified invocation path.
   *
   * @param multiplier the multiplier argument
   * @return the response ExecMessage
   */
  private ExecMessage invokeMultiplyStaticByOnce(int multiplier) {
    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use wrapper method that calls multiplyStaticBy once
      // Need an instance to call the wrapper
      ObjectRef appInstance =
          ObjectRef.from(
              invoke(
                      messageBuilder.buildEmptyConstructor(
                          myPeerUuid, InterceptableApp.class.getName()))
                  .getReturnValue()
                  .getObject()
                  .getRef());
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
   * <p>Registers a BEFORE_ASYNC intercept on multiplyStaticBy, invokes it once through the
   * specified path, and verifies exactly 1 callback is received without blocking for a response.
   */
  @Test
  public void testSingleBeforeAsyncCallback() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallback [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // 2. Invoke multiplyStaticBy through the specified path
    logger.info("Invoking multiplyStaticBy via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeMultiplyStaticByOnce(multiplier);
    logger.info("multiplyStaticBy invocation completed");

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

    logger.info(
        "===== testSingleBeforeAsyncCallback [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests multiple BEFORE_ASYNC callbacks on static method.
   *
   * <p>Registers a BEFORE_ASYNC intercept on incrementStaticCounter, invokes it n times through the
   * specified path, and verifies exactly n callbacks are received without blocking.
   *
   * <p>For HOT_PATH: Uses wrapper method that calls target n times. For INCOMING_RPC: Calls target
   * directly n times.
   */
  @Test
  public void testMultipleBeforeAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacks [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // 2. Invoke incrementStaticCounter n times through the specified path
    logger.info(
        "Invoking incrementStaticCounter {} times via {} path which should trigger {} callback(s)",
        n,
        path,
        n);

    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use wrapper method that calls target n times
      ObjectRef appInstance =
          ObjectRef.from(
              invoke(
                      messageBuilder.buildEmptyConstructor(
                          myPeerUuid, InterceptableApp.class.getName()))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      ExecMessage response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  InterceptableApp.class.getName(),
                  "callIncrementStaticCounterNTimes",
                  appInstance,
                  new String[] {"java.lang.Integer"},
                  new Object[] {n}));
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
                    "incrementStaticCounter",
                    new String[] {},
                    null,
                    null,
                    new Object[] {}));
        assertThat(
            "Invocation should not raise exception",
            response.getRaisedThrowable(),
            is(nullValue()));
      }
    }
    logger.info("incrementStaticCounter invocations completed");

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
    }

    logger.info(
        "===== testMultipleBeforeAsyncCallbacks [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests single AFTER_ASYNC callback on static method.
   *
   * <p>Registers an AFTER_ASYNC intercept on multiplyStaticBy, invokes it once through the
   * specified path, and verifies exactly 1 callback is received after method execution without
   * blocking.
   */
  @Test
  public void testSingleAfterAsyncCallback() throws Exception {
    logger.info("===== testSingleAfterAsyncCallback [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // 2. Invoke multiplyStaticBy through the specified path
    logger.info("Invoking multiplyStaticBy via {} path which should trigger 1 callback", path);
    ExecMessage response = invokeMultiplyStaticByOnce(multiplier);
    logger.info("multiplyStaticBy invocation completed");

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

    logger.info("===== testSingleAfterAsyncCallback [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }

  /**
   * Tests multiple AFTER_ASYNC callbacks on static method.
   *
   * <p>Registers an AFTER_ASYNC intercept on incrementStaticCounter, invokes it n times through the
   * specified path, and verifies exactly n callbacks are received after method executions without
   * blocking.
   *
   * <p>For HOT_PATH: Uses wrapper method that calls target n times. For INCOMING_RPC: Calls target
   * directly n times.
   */
  @Test
  public void testMultipleAfterAsyncCallbacks() throws Exception {
    logger.info("===== testMultipleAfterAsyncCallbacks [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
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

    // 2. Invoke incrementStaticCounter n times through the specified path
    logger.info(
        "Invoking incrementStaticCounter {} times via {} path which should trigger {} callback(s)",
        n,
        path,
        n);

    if (path == InvocationPath.HOT_PATH) {
      // HOT_PATH: Use wrapper method that calls target n times
      ObjectRef appInstance =
          ObjectRef.from(
              invoke(
                      messageBuilder.buildEmptyConstructor(
                          myPeerUuid, InterceptableApp.class.getName()))
                  .getReturnValue()
                  .getObject()
                  .getRef());
      ExecMessage response =
          invoke(
              messageBuilder.buildInstanceMethod(
                  myPeerUuid,
                  InterceptableApp.class.getName(),
                  "callIncrementStaticCounterNTimes",
                  appInstance,
                  new String[] {"java.lang.Integer"},
                  new Object[] {n}));
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
                    "incrementStaticCounter",
                    new String[] {},
                    null,
                    null,
                    new Object[] {}));
        assertThat(
            "Invocation should not raise exception",
            response.getRaisedThrowable(),
            is(nullValue()));
      }
    }
    logger.info("incrementStaticCounter invocations completed");

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
      // AFTER callbacks wrap ReturnValue, not ClassMethodCall
      // Verify the return value structure for non-void method (returns Integer)
      assertThat(
          "AFTER callback should have ReturnValue in exec",
          callback.getInterceptCallbackRequestMessage().getExec().getReturnValue(),
          is(notNullValue()));
      assertThat(
          "incrementStaticCounter returns Integer, so isVoid should be false",
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

    logger.info(
        "===== testMultipleAfterAsyncCallbacks [{}]: TEST COMPLETED SUCCESSFULLY =====", path);
  }
}
