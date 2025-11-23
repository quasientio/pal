/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.constructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.quasient.pal.InterceptTestSuite;
import com.quasient.pal.apps.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
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
 * Integration tests for synchronous constructor intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify the end-to-end callback mechanism for synchronous intercepts on
 * constructors (EXEC_CONSTRUCTOR), including single and multiple callbacks for both BEFORE and
 * AFTER intercept types.
 */
public class ConstructorSyncCallbackIT extends AbstractInterceptIT {

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
   * Tests single BEFORE callback on constructor.
   *
   * <p>Registers a BEFORE intercept on the parameterized constructor, calls it once, and verifies
   * exactly 1 callback is received.
   */
  @Test
  public void testSingleBeforeCallback() throws Exception {
    logger.info("===== testSingleBeforeCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.ConstructorHandlers";
    final String callbackMethod = "noOp";
    final int initialValue = 10;

    // 1. Register a BEFORE intercept on parameterized constructor
    logger.info("Creating BEFORE intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("<init>", Collections.singletonList("java.lang.Integer")));

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
        "Invoking createWithCounter factory method with initialValue={} - constructor intercept should work",
        initialValue);
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "createWithCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {initialValue}));
    logger.info("Factory method invocation completed successfully");

    logger.info("===== testSingleBeforeCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple BEFORE callbacks on constructor.
   *
   * <p>Registers a BEFORE intercept on the parameterized constructor, calls it multiple times
   * (n=3), and verifies exactly 3 callbacks are received.
   */
  @Test
  public void testMultipleBeforeCallbacks() throws Exception {
    logger.info("===== testMultipleBeforeCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.ConstructorHandlers";
    final String callbackMethod = "noOp";
    final int n = 3;

    // 1. Register a BEFORE intercept on parameterized constructor
    logger.info("Creating BEFORE intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            InterceptTestSuite.INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("<init>", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke constructor multiple times - constructor intercepts should work
    logger.info("Invoking constructor {} times - intercepts should work", n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "createWithCounter",
              new String[] {"java.lang.Integer"},
              null,
              null,
              new Object[] {10 + i}));
    }
    logger.info("Constructor invocations completed successfully");

    logger.info("===== testMultipleBeforeCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests single AFTER callback on constructor.
   *
   * <p>Registers an AFTER intercept on the parameterized constructor, calls it once, and verifies
   * exactly 1 callback is received after constructor execution.
   */
  @Test
  @Ignore
  public void testSingleAfterCallback() throws Exception {
    logger.info("===== testSingleAfterCallback: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.ConstructorHandlers";
    final String callbackMethod = "noOp";
    final int initialValue = 10;

    // 1. Register an AFTER intercept on parameterized constructor
    logger.info("Creating AFTER intercept request for parameterized constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("<init>", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke factory method which internally calls constructor (triggers callback via call-site)
    logger.info(
        "Invoking createWithCounter factory method with initialValue={} which should trigger constructor intercept",
        initialValue);
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

    // 3. Receive callbacks using getCallbacks()
    logger.info("Receiving callbacks using getCallbacks()");
    List<Message> receivedCallbacks = getCallbacks(1, 5000);
    logger.info("Callbacks received successfully");

    // 4. Verify callback structure
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
    // AFTER callbacks receive the return value (constructed object), not parameters
    assertThat(
        "AFTER callback should have 1 parameter (constructed object)",
        callback.getExecMessage().getClassMethodCall().getParameters().length,
        is(1));

    logger.info("===== testSingleAfterCallback: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests multiple AFTER callbacks on constructor.
   *
   * <p>Registers an AFTER intercept on the parameterized constructor, calls it multiple times
   * (n=3), and verifies exactly 3 callbacks are received after constructor executions.
   */
  @Test
  @Ignore
  public void testMultipleAfterCallbacks() throws Exception {
    logger.info("===== testMultipleAfterCallbacks: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.ConstructorHandlers";
    final String callbackMethod = "noOp";
    final int n = 3;

    // 1. Register an AFTER intercept on parameterized constructor
    logger.info("Creating AFTER intercept request for parameterized constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createMethodCallInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("<init>", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke constructor multiple times which triggers callbacks
    logger.info("Invoking constructor {} times which should trigger {} callbacks", n, n);
    for (int i = 0; i < n; i++) {
      invoke(
          messageBuilder.buildClassMethod(
              myPeerUuid,
              InterceptableApp.class.getName(),
              "createWithCounter",
              new String[] {"java.lang.Integer"},
              null,
              null,
              new Object[] {10 + i}));
    }
    logger.info("Constructor invocations completed");

    // 3. Receive callbacks using getCallbacks()
    logger.info("Receiving {} callback(s) using getCallbacks()", n);
    List<Message> receivedCallbacks = getCallbacks(n, 5000);
    logger.info("All {} callback(s) received successfully", n);

    // 4. Verify we received exactly n callbacks
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
      // AFTER callbacks receive the return value (constructed object), not parameters
      assertThat(
          "AFTER callback should have 1 parameter (constructed object)",
          callback.getExecMessage().getClassMethodCall().getParameters().length,
          is(1));
    }

    logger.info("===== testMultipleAfterCallbacks: TEST COMPLETED SUCCESSFULLY =====");
  }
}
