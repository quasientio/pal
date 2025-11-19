/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.staticfield;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.quasient.pal.apps.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.List;
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Integration tests for synchronous static field intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the end-to-end callback mechanism for synchronous intercepts on static
 * field operations (EXEC_GET_STATIC and EXEC_PUT_STATIC), including single and multiple callbacks
 * for both BEFORE and AFTER intercept types.
 */
public class StaticFieldSyncCallbackIT extends AbstractInterceptIT {

  /**
   * Creates an InterceptRequest for a field operation.
   *
   * @param uuid unique identifier for the intercept request
   * @param type intercept type (BEFORE, AFTER, etc.)
   * @param classname target class name
   * @param callbackClass callback class name
   * @param callbackMethod callback method name
   * @param interceptableFieldOp field operation to intercept
   * @return an InterceptRequest for the field operation
   */
  private InterceptRequest<InterceptableFieldOp> createFieldOpInterceptRequest(
      UUID uuid,
      InterceptType type,
      String classname,
      String callbackClass,
      String callbackMethod,
      InterceptableFieldOp interceptableFieldOp) {
    return new InterceptRequest<>(
        uuid, myPeerUuid, type, classname, callbackClass, callbackMethod, interceptableFieldOp);
  }

  /**
   * Tests single BEFORE callback on static field GET operation.
   *
   * <p>Registers a BEFORE intercept on getStaticCounter (which triggers EXEC_GET_STATIC), calls it
   * once, and verifies exactly 1 callback is received.
   */
  @Test
  @Ignore
  public void testSingleBeforeCallbackOnGet() throws Exception {
    logger.info("===== testSingleBeforeCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";

    // 1. Register a BEFORE intercept on staticCounter field GET
    logger.info("Creating BEFORE intercept request for staticCounter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 3. Invoke getStaticCounter which triggers GET_STATIC and callback
    logger.info("Invoking getStaticCounter() which should trigger callback");
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "getStaticCounter",
            new String[] {},
            null,
            null,
            new Object[] {}));
    logger.info("getStaticCounter invocation completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving callbacks using getCallbacks()");
    List<Message> receivedCallbacks = getCallbacks(1, 5000);
    logger.info("Callbacks received successfully");

    // 5. Verify callback structure
    logger.info("Verifying callback message structure");
    assertThat("Should have received exactly 1 callback", receivedCallbacks.size(), is(1));

    Message callback = receivedCallbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be CLASS_METHOD type",
        callback.getMessageType(),
        is(MessageType.EXEC_CLASS_METHOD.getId()));
    assertThat(
        "Callback class should match",
        callback.getExecMessage().getClassMethodCall().getClazz().getName(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getExecMessage().getClassMethodCall().getName(),
        is(callbackMethod));

    logger.info("===== testSingleBeforeCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE callbacks on static field GET operation.
   *
   * <p>Registers a BEFORE intercept on getStaticCounter, calls it multiple times (n=3), and
   * verifies exactly 3 callbacks are received.
   */
  @Test
  @Ignore
  public void testMultipleBeforeCallbacksOnGet() throws Exception {
    logger.info("===== testMultipleBeforeCallbacksOnGet: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";
    final int n = 3;

    // 1. Register a BEFORE intercept on staticCounter field GET
    logger.info("Creating BEFORE intercept request for staticCounter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 3. Invoke getStaticCounter multiple times which triggers callbacks
    logger.info("Invoking getStaticCounter {} times which should trigger {} callbacks", n, n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "getStaticCounter",
              new String[] {},
              null,
              null,
              new Object[] {}));
    }
    logger.info("getStaticCounter invocations completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving {} callback(s) using getCallbacks()", n);
    List<Message> receivedCallbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    // 5. Verify we received exactly n callbacks
    logger.info("Verifying exactly {} callbacks were received", n);
    assertThat("Should have received exactly " + n + " callbacks", receivedCallbacks.size(), is(n));

    for (int i = 0; i < n; i++) {
      Message callback = receivedCallbacks.get(i);
      assertThat("Callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "Callback should be CLASS_METHOD type",
          callback.getMessageType(),
          is(MessageType.EXEC_CLASS_METHOD.getId()));
      assertThat(
          "Callback class should match",
          callback.getExecMessage().getClassMethodCall().getClazz().getName(),
          is(callbackClass));
      assertThat(
          "Callback method should match",
          callback.getExecMessage().getClassMethodCall().getName(),
          is(callbackMethod));
    }

    logger.info("===== testMultipleBeforeCallbacksOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on static field GET operation.
   *
   * <p>Registers an AFTER intercept on getStaticCounter, calls it once, and verifies exactly 1
   * callback is received after the field get.
   */
  @Test
  @Ignore
  public void testSingleAfterCallbackOnGet() throws Exception {
    logger.info("===== testSingleAfterCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";

    // 1. Register an AFTER intercept on staticCounter field GET
    logger.info("Creating AFTER intercept request for staticCounter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 3. Invoke getStaticCounter which triggers GET_STATIC and callback
    logger.info("Invoking getStaticCounter() which should trigger callback");
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "getStaticCounter",
            new String[] {},
            null,
            null,
            new Object[] {}));
    logger.info("getStaticCounter invocation completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving callbacks using getCallbacks()");
    List<Message> receivedCallbacks = getCallbacks(1, 5000);
    logger.info("Callbacks received successfully");

    // 5. Verify callback structure
    logger.info("Verifying callback message structure");
    assertThat("Should have received exactly 1 callback", receivedCallbacks.size(), is(1));

    Message callback = receivedCallbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be CLASS_METHOD type",
        callback.getMessageType(),
        is(MessageType.EXEC_CLASS_METHOD.getId()));
    assertThat(
        "Callback class should match",
        callback.getExecMessage().getClassMethodCall().getClazz().getName(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getExecMessage().getClassMethodCall().getName(),
        is(callbackMethod));
    // AFTER callbacks for GET operations receive the field value as parameter
    assertThat(
        "AFTER callback should have 1 parameter (field value)",
        callback.getExecMessage().getClassMethodCall().getParameters().length,
        is(1));

    logger.info("===== testSingleAfterCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER callbacks on static field GET operation.
   *
   * <p>Registers an AFTER intercept on getStaticCounter, calls it multiple times (n=3), and
   * verifies exactly 3 callbacks are received after the field gets.
   */
  @Test
  @Ignore
  public void testMultipleAfterCallbacksOnGet() throws Exception {
    logger.info("===== testMultipleAfterCallbacksOnGet: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";
    final int n = 3;

    // 1. Register an AFTER intercept on staticCounter field GET
    logger.info("Creating AFTER intercept request for staticCounter GET");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 3. Invoke getStaticCounter multiple times which triggers callbacks
    logger.info("Invoking getStaticCounter {} times which should trigger {} callbacks", n, n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "getStaticCounter",
              new String[] {},
              null,
              null,
              new Object[] {}));
    }
    logger.info("getStaticCounter invocations completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving {} callback(s) using getCallbacks()", n);
    List<Message> receivedCallbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    // 5. Verify we received exactly n callbacks
    logger.info("Verifying exactly {} callbacks were received", n);
    assertThat("Should have received exactly " + n + " callbacks", receivedCallbacks.size(), is(n));

    for (int i = 0; i < n; i++) {
      Message callback = receivedCallbacks.get(i);
      assertThat("Callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "Callback should be CLASS_METHOD type",
          callback.getMessageType(),
          is(MessageType.EXEC_CLASS_METHOD.getId()));
      assertThat(
          "Callback class should match",
          callback.getExecMessage().getClassMethodCall().getClazz().getName(),
          is(callbackClass));
      assertThat(
          "Callback method should match",
          callback.getExecMessage().getClassMethodCall().getName(),
          is(callbackMethod));
      // AFTER callbacks for GET operations receive the field value as parameter
      assertThat(
          "AFTER callback should have 1 parameter (field value)",
          callback.getExecMessage().getClassMethodCall().getParameters().length,
          is(1));
    }

    logger.info("===== testMultipleAfterCallbacksOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single BEFORE callback on static field PUT operation.
   *
   * <p>Registers a BEFORE intercept on setStaticCounter (which triggers EXEC_PUT_STATIC), calls it
   * once, and verifies exactly 1 callback is received.
   */
  @Test
  @Ignore
  public void testSingleBeforeCallbackOnPut() throws Exception {
    logger.info("===== testSingleBeforeCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";
    final int newValue = 200;

    // 1. Register a BEFORE intercept on staticCounter field PUT
    logger.info("Creating BEFORE intercept request for staticCounter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 3. Invoke setStaticCounter which triggers PUT_STATIC and callback
    logger.info("Invoking setStaticCounter({}) which should trigger callback", newValue);
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {newValue}));
    logger.info("setStaticCounter invocation completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving callbacks using getCallbacks()");
    List<Message> receivedCallbacks = getCallbacks(1, 5000);
    logger.info("Callbacks received successfully");

    // 5. Verify callback structure
    logger.info("Verifying callback message structure");
    assertThat("Should have received exactly 1 callback", receivedCallbacks.size(), is(1));

    Message callback = receivedCallbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be CLASS_METHOD type",
        callback.getMessageType(),
        is(MessageType.EXEC_CLASS_METHOD.getId()));
    assertThat(
        "Callback class should match",
        callback.getExecMessage().getClassMethodCall().getClazz().getName(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getExecMessage().getClassMethodCall().getName(),
        is(callbackMethod));
    // BEFORE callbacks for PUT operations receive the new value as parameter
    assertThat(
        "BEFORE callback should have 1 parameter (new value)",
        callback.getExecMessage().getClassMethodCall().getParameters().length,
        is(1));

    logger.info("===== testSingleBeforeCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE callbacks on static field PUT operation.
   *
   * <p>Registers a BEFORE intercept on setStaticCounter, calls it multiple times (n=3), and
   * verifies exactly 3 callbacks are received.
   */
  @Test
  @Ignore
  public void testMultipleBeforeCallbacksOnPut() throws Exception {
    logger.info("===== testMultipleBeforeCallbacksOnPut: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";
    final int n = 3;

    // 1. Register a BEFORE intercept on staticCounter field PUT
    logger.info("Creating BEFORE intercept request for staticCounter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 3. Invoke setStaticCounter multiple times which triggers callbacks
    logger.info("Invoking setStaticCounter {} times which should trigger {} callbacks", n, n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "setStaticCounter",
              new String[] {"java.lang.Integer"},
              null,
              null,
              new Object[] {200 + i}));
    }
    logger.info("setStaticCounter invocations completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving {} callback(s) using getCallbacks()", n);
    List<Message> receivedCallbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    // 5. Verify we received exactly n callbacks
    logger.info("Verifying exactly {} callbacks were received", n);
    assertThat("Should have received exactly " + n + " callbacks", receivedCallbacks.size(), is(n));

    for (int i = 0; i < n; i++) {
      Message callback = receivedCallbacks.get(i);
      assertThat("Callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "Callback should be CLASS_METHOD type",
          callback.getMessageType(),
          is(MessageType.EXEC_CLASS_METHOD.getId()));
      assertThat(
          "Callback class should match",
          callback.getExecMessage().getClassMethodCall().getClazz().getName(),
          is(callbackClass));
      assertThat(
          "Callback method should match",
          callback.getExecMessage().getClassMethodCall().getName(),
          is(callbackMethod));
    }

    logger.info("===== testMultipleBeforeCallbacksOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on static field PUT operation.
   *
   * <p>Registers an AFTER intercept on setStaticCounter, calls it once, and verifies exactly 1
   * callback is received after the field put.
   */
  @Test
  @Ignore
  public void testSingleAfterCallbackOnPut() throws Exception {
    logger.info("===== testSingleAfterCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";
    final int newValue = 200;

    // 1. Register an AFTER intercept on staticCounter field PUT
    logger.info("Creating AFTER intercept request for staticCounter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 3. Invoke setStaticCounter which triggers PUT_STATIC and callback
    logger.info("Invoking setStaticCounter({}) which should trigger callback", newValue);
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {newValue}));
    logger.info("setStaticCounter invocation completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving callbacks using getCallbacks()");
    List<Message> receivedCallbacks = getCallbacks(1, 5000);
    logger.info("Callbacks received successfully");

    // 5. Verify callback structure
    logger.info("Verifying callback message structure");
    assertThat("Should have received exactly 1 callback", receivedCallbacks.size(), is(1));

    Message callback = receivedCallbacks.get(0);
    assertThat("Callback message should not be null", callback, is(notNullValue()));
    assertThat(
        "Callback should be CLASS_METHOD type",
        callback.getMessageType(),
        is(MessageType.EXEC_CLASS_METHOD.getId()));
    assertThat(
        "Callback class should match",
        callback.getExecMessage().getClassMethodCall().getClazz().getName(),
        is(callbackClass));
    assertThat(
        "Callback method should match",
        callback.getExecMessage().getClassMethodCall().getName(),
        is(callbackMethod));
    // AFTER callbacks for PUT operations receive no parameters (void return)
    assertThat(
        "AFTER callback should have 0 parameters (void)",
        callback.getExecMessage().getClassMethodCall().getParameters().length,
        is(0));

    logger.info("===== testSingleAfterCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER callbacks on static field PUT operation.
   *
   * <p>Registers an AFTER intercept on setStaticCounter, calls it multiple times (n=3), and
   * verifies exactly 3 callbacks are received after the field puts.
   */
  @Test
  @Ignore
  public void testMultipleAfterCallbacksOnPut() throws Exception {
    logger.info("===== testMultipleAfterCallbacksOnPut: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";
    final int n = 3;

    // 1. Register an AFTER intercept on staticCounter field PUT
    logger.info("Creating AFTER intercept request for staticCounter PUT");
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 3. Invoke setStaticCounter multiple times which triggers callbacks
    logger.info("Invoking setStaticCounter {} times which should trigger {} callbacks", n, n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "setStaticCounter",
              new String[] {"java.lang.Integer"},
              null,
              null,
              new Object[] {200 + i}));
    }
    logger.info("setStaticCounter invocations completed");

    // 4. Receive callbacks using getCallbacks()
    logger.info("Receiving {} callback(s) using getCallbacks()", n);
    List<Message> receivedCallbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    // 5. Verify we received exactly n callbacks
    logger.info("Verifying exactly {} callbacks were received", n);
    assertThat("Should have received exactly " + n + " callbacks", receivedCallbacks.size(), is(n));

    for (int i = 0; i < n; i++) {
      Message callback = receivedCallbacks.get(i);
      assertThat("Callback message should not be null", callback, is(notNullValue()));
      assertThat(
          "Callback should be CLASS_METHOD type",
          callback.getMessageType(),
          is(MessageType.EXEC_CLASS_METHOD.getId()));
      assertThat(
          "Callback class should match",
          callback.getExecMessage().getClassMethodCall().getClazz().getName(),
          is(callbackClass));
      assertThat(
          "Callback method should match",
          callback.getExecMessage().getClassMethodCall().getName(),
          is(callbackMethod));
      // AFTER callbacks for PUT operations receive no parameters (void return)
      assertThat(
          "AFTER callback should have 0 parameters (void)",
          callback.getExecMessage().getClassMethodCall().getParameters().length,
          is(0));
    }

    logger.info("===== testMultipleAfterCallbacksOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }
}
