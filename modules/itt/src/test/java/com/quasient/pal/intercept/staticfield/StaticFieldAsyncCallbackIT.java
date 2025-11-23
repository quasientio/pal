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

import com.quasient.pal.apps.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.intercept.AbstractInterceptIT;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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
 */
public class StaticFieldAsyncCallbackIT extends AbstractInterceptIT {

  /** ThinPeer for receiving async callbacks via ROUTER socket. */
  private ThinPeer asyncCallbackPeer;

  /** Address for the async callback receiver. */
  private static final String ASYNC_CALLBACK_ADDRESS = "tcp://localhost:7894";

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

  /**
   * Creates an InterceptRequest for a field operation.
   *
   * @param uuid unique identifier for the intercept request
   * @param type intercept type (BEFORE_ASYNC, AFTER_ASYNC, etc.)
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
        uuid,
        asyncCallbackPeerUuid, // Use async callback peer UUID
        type,
        classname,
        callbackClass,
        callbackMethod,
        interceptableFieldOp);
  }

  // Test methods for GET operation - 4 tests total (single/multiple BEFORE_ASYNC/AFTER_ASYNC)

  /** Tests single BEFORE_ASYNC callback on static field GET operation. */
  @Test
  public void testSingleBeforeAsyncCallbackOnGet() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.FieldHandlers";
    final String callbackMethod = "noOp";

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
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

    logger.info("===== testSingleBeforeAsyncCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests multiple BEFORE_ASYNC callbacks on static field GET operation. */
  @Test
  public void testMultipleBeforeAsyncCallbacksOnGet() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacksOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.FieldHandlers";
    final String callbackMethod = "noOp";
    final int n = 3;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

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

    logger.info("===== testMultipleBeforeAsyncCallbacksOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests single AFTER_ASYNC callback on static field GET operation. */
  @Test
  @Ignore
  public void testSingleAfterAsyncCallbackOnGet() throws Exception {
    logger.info("===== testSingleAfterAsyncCallbackOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.FieldHandlers";
    final String callbackMethod = "noOp";

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
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

    logger.info("===== testSingleAfterAsyncCallbackOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests multiple AFTER_ASYNC callbacks on static field GET operation. */
  @Test
  @Ignore
  public void testMultipleAfterAsyncCallbacksOnGet() throws Exception {
    logger.info("===== testMultipleAfterAsyncCallbacksOnGet: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.FieldHandlers";
    final String callbackMethod = "noOp";
    final int n = 3;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

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

    logger.info("===== testMultipleAfterAsyncCallbacksOnGet: TEST COMPLETED SUCCESSFULLY =====");
  }

  // Test methods for PUT operation - 4 tests total (single/multiple BEFORE_ASYNC/AFTER_ASYNC)

  /** Tests single BEFORE_ASYNC callback on static field PUT operation. */
  @Test
  public void testSingleBeforeAsyncCallbackOnPut() throws Exception {
    logger.info("===== testSingleBeforeAsyncCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.FieldHandlers";
    final String callbackMethod = "noOp";
    final int newValue = 200;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
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

    logger.info("===== testSingleBeforeAsyncCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests multiple BEFORE_ASYNC callbacks on static field PUT operation. */
  @Test
  public void testMultipleBeforeAsyncCallbacksOnPut() throws Exception {
    logger.info("===== testMultipleBeforeAsyncCallbacksOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.FieldHandlers";
    final String callbackMethod = "noOp";
    final int n = 3;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

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

    logger.info("===== testMultipleBeforeAsyncCallbacksOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests single AFTER_ASYNC callback on static field PUT operation. */
  @Test
  @Ignore
  public void testSingleAfterAsyncCallbackOnPut() throws Exception {
    logger.info("===== testSingleAfterAsyncCallbackOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.FieldHandlers";
    final String callbackMethod = "noOp";
    final int newValue = 200;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
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

    logger.info("===== testSingleAfterAsyncCallbackOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }

  /** Tests multiple AFTER_ASYNC callbacks on static field PUT operation. */
  @Test
  @Ignore
  public void testMultipleAfterAsyncCallbacksOnPut() throws Exception {
    logger.info("===== testMultipleAfterAsyncCallbacksOnPut: TEST STARTED =====");

    final String callbackClass = "com.quasient.pal.apps.intercept.FieldHandlers";
    final String callbackMethod = "noOp";
    final int n = 3;

    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            UUID.randomUUID(),
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

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

    logger.info("===== testMultipleAfterAsyncCallbacksOnPut: TEST COMPLETED SUCCESSFULLY =====");
  }
}
