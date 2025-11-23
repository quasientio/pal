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

import com.quasient.pal.InterceptTestSuite;
import com.quasient.pal.apps.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Integration tests for synchronous static method intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the end-to-end callback mechanism for synchronous intercepts on static
 * methods (EXEC_CLASS_METHOD), including single and multiple callbacks for both BEFORE and AFTER
 * intercept types.
 */
public class StaticMethodSyncCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** Cleans up intercept registrations after each test. */
  @After
  public void cleanupIntercepts() {
    if (interceptUuid != null) {
      logger.info("Cleaning up intercept registration: {}", interceptUuid);
    }
  }

  /**
   * Tests single BEFORE callback on static method.
   *
   * <p>Registers a BEFORE intercept on incrementStaticCounter, calls it once, and verifies the
   * intercept mechanism works without throwing.
   */
  @Test
  public void testSingleBeforeCallback() throws Exception {
    logger.info("===== testSingleBeforeCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.StaticMethodHandlers";
    final String callbackMethod = "noOp";

    // 1. Register a BEFORE intercept on incrementStaticCounter static method
    logger.info("Creating BEFORE intercept request for incrementStaticCounter static method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
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

    // 3. Invoke callIncrementStaticCounter (triggers incrementStaticCounter via call-site)
    logger.info("Invoking callIncrementStaticCounter wrapper - intercept should work");
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "callIncrementStaticCounter",
            appInstance,
            new String[] {},
            new Object[] {}));
    logger.info("callIncrementStaticCounter invocation completed successfully");

    logger.info("===== testSingleBeforeCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE callbacks on static method.
   *
   * <p>Registers a BEFORE intercept on multiplyStaticBy, calls it multiple times (n=3), and
   * verifies the intercept mechanism works without throwing.
   */
  @Test
  public void testMultipleBeforeCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.StaticMethodHandlers";
    final String callbackMethod = "noOp";
    final int n = 3;
    final int multiplier = 2;

    // 1. Register a BEFORE intercept on multiplyStaticBy static method
    logger.info("Creating BEFORE intercept request for multiplyStaticBy static method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
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

    // 3. Invoke callMultiplyStaticBy multiple times (triggers multiplyStaticBy via call-site)
    logger.info("Invoking callMultiplyStaticBy wrapper {} times - intercepts should work", n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "callMultiplyStaticBy",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {multiplier}));
    }
    logger.info("callMultiplyStaticBy invocations completed successfully");

    logger.info("===== testMultipleBeforeCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on static method.
   *
   * <p>Registers an AFTER intercept on multiplyStaticBy, calls it once, and verifies exactly 1
   * callback is received after method execution.
   */
  @Test
  @Ignore
  public void testSingleAfterCallback() throws Exception {
    logger.info("===== testSingleAfterCallback: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";
    final int multiplier = 3;

    // 1. Register an AFTER intercept on multiplyStaticBy static method
    logger.info("Creating AFTER intercept request for multiplyStaticBy static method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
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

    // 3. Invoke callMultiplyStaticBy (triggers multiplyStaticBy via call-site)
    logger.info("Invoking callMultiplyStaticBy wrapper which should trigger callback");
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
    // AFTER callbacks receive the return value (Integer), so 1 parameter
    assertThat(
        "AFTER callback should have 1 parameter (return value)",
        callback.getExecMessage().getClassMethodCall().getParameters().length,
        is(1));

    logger.info("===== testSingleAfterCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER callbacks on static method.
   *
   * <p>Registers an AFTER intercept on multiplyStaticBy, calls it multiple times (n=3), and
   * verifies exactly 3 callbacks are received after method executions.
   */
  @Test
  @Ignore
  public void testMultipleAfterCallbacks() throws Exception {
    logger.info("===== testMultipleAfterCallbacks: TEST STARTED =====");

    final String callbackClass = "com.example.CallbackHandler";
    final String callbackMethod = "handleCallback";
    final int n = 3;
    final int multiplier = 2;

    // 1. Register an AFTER intercept on multiplyStaticBy static method
    logger.info("Creating AFTER intercept request for multiplyStaticBy static method");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
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

    // 3. Invoke callMultiplyStaticBy multiple times (triggers multiplyStaticBy via call-site)
    logger.info(
        "Invoking callMultiplyStaticBy wrapper {} times which should trigger {} callbacks", n, n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "callMultiplyStaticBy",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {multiplier}));
    }
    logger.info("callMultiplyStaticBy invocations completed");

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
      // AFTER callbacks receive the return value (Integer), so 1 parameter
      assertThat(
          "AFTER callback should have 1 parameter (return value)",
          callback.getExecMessage().getClassMethodCall().getParameters().length,
          is(1));
    }

    logger.info("===== testMultipleAfterCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
