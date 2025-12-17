/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.local.field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.LocalInterceptTestSuite;
import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.util.Collection;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for synchronous local field intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify local intercepts on field GET and PUT operations where the callback runs in
 * the same peer as the intercepted field access. Local intercepts use {@code
 * LocalInterceptCallbackDispatcher} instead of sending RPC messages to a remote peer.
 *
 * <p>Key differences from remote intercepts:
 *
 * <ul>
 *   <li>Callback peer UUID equals interceptable peer UUID
 *   <li>Callback is invoked directly via reflection, no ZMQ message passing
 *   <li>Field values are direct Java objects (not serialized copies)
 * </ul>
 */
@RunWith(Parameterized.class)
public class LocalFieldSyncCallbackIT extends AbstractInterceptIT {

  private static final String CALLBACK_CLASS =
      "com.quasient.pal.apps.callbacks.local.LocalInterceptCallbacks";
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** Field invocation descriptors for parameterized tests. */
  private static final FieldInvocation COUNTER =
      new FieldInvocation("getCounter", "setCounter", "counter", "java.lang.Integer", false);

  private static final FieldInvocation STATIC_COUNTER =
      new FieldInvocation(
          "getStaticCounter", "setStaticCounter", "staticCounter", "java.lang.Integer", true);

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public LocalFieldSyncCallbackIT(InvocationPath path) {
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
   * Creates a local intercept request for a field op where callback peer = interceptable peer.
   *
   * @param type the intercept type
   * @param fieldName the field name
   * @param fieldOpType the field operation type (GET or PUT)
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableFieldOp> createLocalFieldIntercept(
      InterceptType type, String fieldName, FieldOpType fieldOpType, String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID, // callback peer = interceptable peer
        type,
        TARGET_CLASS,
        CALLBACK_CLASS,
        callbackMethod,
        new InterceptableFieldOp(fieldName, fieldOpType));
  }

  // ===========================================================================
  // Instance Field GET Tests
  // ===========================================================================

  /**
   * Tests that a local BEFORE callback is invoked for instance field GET.
   *
   * <p>Registers a local BEFORE intercept on counter GET, invokes it, and verifies the callback was
   * invoked.
   */
  @Test
  public void testLocalBeforeFieldGetCallback() throws Exception {
    logger.info("===== testLocalBeforeFieldGetCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local BEFORE intercept on counter field GET
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(InterceptType.BEFORE, "counter", FieldOpType.GET, "onBefore");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance with initial value
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {42}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Invoke field GET
    logger.info("Invoking getCounter via {} path", path);
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);

    // 4. Verify invocation succeeded
    assertThat(
        "Field GET should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify local BEFORE callback was invoked (via log output)
    assertTrue(
        "Local BEFORE callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*counter.*count=1"));

    logger.info("===== testLocalBeforeFieldGetCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER callback is invoked for instance field GET.
   *
   * <p>Registers a local AFTER intercept on counter GET, invokes it, and verifies the callback was
   * invoked.
   */
  @Test
  public void testLocalAfterFieldGetCallback() throws Exception {
    logger.info("===== testLocalAfterFieldGetCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local AFTER intercept on counter field GET
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(InterceptType.AFTER, "counter", FieldOpType.GET, "onAfter");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance with initial value
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {42}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Invoke field GET
    logger.info("Invoking getCounter via {} path", path);
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);

    // 4. Verify invocation succeeded
    assertThat(
        "Field GET should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify local AFTER callback was invoked (via log output)
    assertTrue(
        "Local AFTER callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER:.*counter.*count=1"));

    logger.info("===== testLocalAfterFieldGetCallback [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Instance Field PUT Tests
  // ===========================================================================

  /**
   * Tests that a local BEFORE callback is invoked for instance field PUT.
   *
   * <p>Registers a local BEFORE intercept on counter PUT, invokes it, and verifies the callback was
   * invoked.
   */
  @Test
  public void testLocalBeforeFieldPutCallback() throws Exception {
    logger.info("===== testLocalBeforeFieldPutCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local BEFORE intercept on counter field PUT
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(InterceptType.BEFORE, "counter", FieldOpType.PUT, "onBefore");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Invoke field PUT
    logger.info("Invoking setCounter via {} path", path);
    ExecMessage response = invokeFieldPut(path, TARGET_CLASS, COUNTER, appInstance, 100);

    // 4. Verify invocation succeeded
    assertThat(
        "Field PUT should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify local BEFORE callback was invoked (via log output)
    assertTrue(
        "Local BEFORE callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*counter.*count=1"));

    logger.info("===== testLocalBeforeFieldPutCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER callback is invoked for instance field PUT.
   *
   * <p>Registers a local AFTER intercept on counter PUT, invokes it, and verifies the callback was
   * invoked.
   */
  @Test
  public void testLocalAfterFieldPutCallback() throws Exception {
    logger.info("===== testLocalAfterFieldPutCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local AFTER intercept on counter field PUT
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(InterceptType.AFTER, "counter", FieldOpType.PUT, "onAfter");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Invoke field PUT
    logger.info("Invoking setCounter via {} path", path);
    ExecMessage response = invokeFieldPut(path, TARGET_CLASS, COUNTER, appInstance, 100);

    // 4. Verify invocation succeeded
    assertThat(
        "Field PUT should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify local AFTER callback was invoked (via log output)
    assertTrue(
        "Local AFTER callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER:.*counter.*count=1"));

    logger.info("===== testLocalAfterFieldPutCallback [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Static Field Tests
  // ===========================================================================

  /**
   * Tests that a local BEFORE callback is invoked for static field GET.
   *
   * <p>Registers a local BEFORE intercept on staticCounter GET, invokes it, and verifies the
   * callback was invoked.
   */
  @Test
  public void testLocalBeforeStaticFieldGetCallback() throws Exception {
    logger.info("===== testLocalBeforeStaticFieldGetCallback [{}]: TEST STARTED =====", path);

    // 1. Set static counter to a known value
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {200}));

    // 2. Register a local BEFORE intercept on staticCounter field GET
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.BEFORE, "staticCounter", FieldOpType.GET, "onBefore");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Invoke static field GET
    logger.info("Invoking getStaticCounter via {} path", path);
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, STATIC_COUNTER, null);

    // 4. Verify invocation succeeded
    assertThat(
        "Static field GET should not raise exception",
        response.getRaisedThrowable(),
        is(nullValue()));

    // 5. Verify local BEFORE callback was invoked (via log output)
    assertTrue(
        "Local BEFORE callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*staticCounter.*count=1"));

    logger.info("===== testLocalBeforeStaticFieldGetCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local BEFORE callback is invoked for static field PUT.
   *
   * <p>Registers a local BEFORE intercept on staticCounter PUT, invokes it, and verifies the
   * callback was invoked.
   */
  @Test
  public void testLocalBeforeStaticFieldPutCallback() throws Exception {
    logger.info("===== testLocalBeforeStaticFieldPutCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local BEFORE intercept on staticCounter field PUT
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.BEFORE, "staticCounter", FieldOpType.PUT, "onBefore");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke static field PUT
    logger.info("Invoking setStaticCounter via {} path", path);
    ExecMessage response = invokeFieldPut(path, TARGET_CLASS, STATIC_COUNTER, null, 300);

    // 3. Verify invocation succeeded
    assertThat(
        "Static field PUT should not raise exception",
        response.getRaisedThrowable(),
        is(nullValue()));

    // 4. Verify local BEFORE callback was invoked (via log output)
    assertTrue(
        "Local BEFORE callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*staticCounter.*count=1"));

    logger.info("===== testLocalBeforeStaticFieldPutCallback [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Combined BEFORE and AFTER Tests
  // ===========================================================================

  /**
   * Tests that both local BEFORE and AFTER callbacks are invoked for field GET.
   *
   * <p>Registers both BEFORE and AFTER intercepts on counter GET and verifies both are called.
   */
  @Test
  public void testLocalBeforeAndAfterFieldGetCallbacks() throws Exception {
    logger.info("===== testLocalBeforeAndAfterFieldGetCallbacks [{}]: TEST STARTED =====", path);

    // 1. Register both BEFORE and AFTER intercepts on counter GET
    InterceptRequest<InterceptableFieldOp> beforeIntercept =
        createLocalFieldIntercept(InterceptType.BEFORE, "counter", FieldOpType.GET, "onBefore");
    InterceptRequest<InterceptableFieldOp> afterIntercept =
        createLocalFieldIntercept(InterceptType.AFTER, "counter", FieldOpType.GET, "onAfter");

    register(beforeIntercept);
    register(afterIntercept);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {42}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Invoke field GET
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify both callbacks were invoked (via log output)
    assertTrue(
        "Local BEFORE callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*counter.*count=1"));
    assertTrue(
        "Local AFTER callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER:.*counter.*count=1"));

    logger.info("===== testLocalBeforeAndAfterFieldGetCallbacks [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AROUND callback is invoked for field GET.
   *
   * <p>Registers a local AROUND intercept that calls proceed(), verifies the field is accessed and
   * the callback was invoked.
   */
  @Test
  public void testLocalAroundFieldGetCallback() throws Exception {
    logger.info("===== testLocalAroundFieldGetCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local AROUND intercept on counter field GET
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(InterceptType.AROUND, "counter", FieldOpType.GET, "onAround");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {42}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Invoke field GET
    logger.info("Invoking getCounter via {} path", path);
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);

    // 4. Verify invocation succeeded
    assertThat(
        "Field GET should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify local AROUND callback was invoked (via log output)
    assertTrue(
        "Local AROUND callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AROUND:.*count=1"));

    logger.info("===== testLocalAroundFieldGetCallback [{}]: TEST COMPLETED =====", path);
  }
}
