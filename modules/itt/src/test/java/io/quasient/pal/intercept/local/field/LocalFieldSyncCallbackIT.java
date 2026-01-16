/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.local.field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.LocalInterceptTestSuite;
import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import org.junit.Before;
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
      "io.quasient.pal.apps.callbacks.local.LocalInterceptCallbacks";
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
   * Clears the application log and resets callback counters before each test.
   *
   * @throws IOException if log file cannot be cleared
   */
  @Before
  public void clearAppLogBeforeTest() throws IOException {
    LocalInterceptTestSuite.clearAppLog();
    // Reset callback counters in the peer via RPC
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid, TARGET_CLASS, "resetLocalInterceptCallbacks", null, null, null, null));
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

  // ===========================================================================
  // Value Mutation Tests (PUT)
  // ===========================================================================

  /**
   * Tests that a BEFORE callback can mutate the value being set on a field PUT.
   *
   * <p>Registers a BEFORE intercept on field PUT that doubles the value, then verifies the field
   * received the mutated value.
   */
  @Test
  public void testBeforePutValueMutation() throws Exception {
    logger.info("===== testBeforePutValueMutation [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept on counter PUT that doubles the value
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.BEFORE, "counter", FieldOpType.PUT, "onBeforeMutateArg");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Set counter to 25 - callback should double to 50
    ExecMessage response = invokeFieldPut(path, TARGET_CLASS, COUNTER, appInstance, 25);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify value was mutated (via log)
    assertTrue(
        "BEFORE callback should have mutated value 25 -> 50",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE_MUTATE_ARG: 25 -> 50"));

    // 5. Verify the counter is 50 (not 25)
    ExecMessage getResponse = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
    int counterValue = (Integer) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    assertThat("Counter should be 50 due to value mutation", counterValue, is(50));

    logger.info("===== testBeforePutValueMutation [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AROUND callback can mutate the value being set on a field PUT before proceeding.
   */
  @Test
  public void testAroundPutValueMutationBeforeProceed() throws Exception {
    logger.info("===== testAroundPutValueMutationBeforeProceed [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept on counter PUT that doubles the value before proceed
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AROUND, "counter", FieldOpType.PUT, "onAroundMutateArgBeforeProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Set counter to 15 - callback should double to 30
    ExecMessage response = invokeFieldPut(path, TARGET_CLASS, COUNTER, appInstance, 15);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify value was mutated (via log)
    assertTrue(
        "AROUND callback should have mutated value 15 -> 30",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AROUND_MUTATE_ARG: 15 -> 30"));

    // 5. Verify the counter is 30 (not 15)
    ExecMessage getResponse = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
    int counterValue = (Integer) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    assertThat("Counter should be 30 due to value mutation", counterValue, is(30));

    logger.info("===== testAroundPutValueMutationBeforeProceed [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Return Override Tests (GET)
  // ===========================================================================

  /**
   * Tests that an AFTER callback can override the return value on a field GET.
   *
   * <p>Registers an AFTER intercept on field GET that doubles the value, then verifies the caller
   * received the overridden value.
   */
  @Test
  public void testAfterGetReturnOverride() throws Exception {
    logger.info("===== testAfterGetReturnOverride [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept on counter GET that doubles the return value
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AFTER, "counter", FieldOpType.GET, "onAfterOverrideReturn");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp with counter=30
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {30}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Get counter - field has 30, callback should double to 60
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify value was overridden (via log)
    assertTrue(
        "AFTER callback should have overridden return 30 -> 60",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER_OVERRIDE_RETURN: 30 -> 60"));

    // 5. Verify we received 60 (not 30)
    int counterValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be 60 due to override", counterValue, is(60));

    logger.info("===== testAfterGetReturnOverride [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that an AROUND callback can override return value on a field GET after proceeding. */
  @Test
  public void testAroundGetReturnOverrideAfterProceed() throws Exception {
    logger.info("===== testAroundGetReturnOverrideAfterProceed [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept on counter GET that doubles return after proceed
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AROUND, "counter", FieldOpType.GET, "onAroundOverrideReturnAfterProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp with counter=20
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                TARGET_CLASS,
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {20}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Get counter - field has 20, callback should double to 40
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify value was overridden (via log)
    assertTrue(
        "AROUND callback should have overridden return 20 -> 40",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AROUND_OVERRIDE_RETURN: 20 -> 40"));

    // 5. Verify we received 40 (not 20)
    int counterValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be 40 due to override", counterValue, is(40));

    logger.info("===== testAroundGetReturnOverrideAfterProceed [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Exception Throwing Tests
  // ===========================================================================

  /** Tests that a BEFORE callback can throw an exception on field PUT. */
  @Test
  public void testBeforePutThrowsException() throws Exception {
    logger.info("===== testBeforePutThrowsException [{}]: TEST STARTED =====", path);

    // 1. Create InterceptableApp instance BEFORE registering intercept
    //    (important: the field initialization `counter = 1` would trigger the intercept if
    //    registered first)
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Now register BEFORE intercept that throws exception
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.BEFORE, "counter", FieldOpType.PUT, "onBeforeThrowException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Set counter - callback should throw exception
    ExecMessage response = invokeFieldPut(path, TARGET_CLASS, COUNTER, appInstance, 100);

    // 4. Verify exception was raised
    assertThat(
        "Exception should have been raised", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be SecurityException",
        response.getRaisedThrowable().getThrowable().getType(),
        is(SecurityException.class.getName()));
    assertThat(
        "Exception message should match",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Access denied by BEFORE callback"));

    logger.info("===== testBeforePutThrowsException [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that an AFTER callback can throw an exception on field GET. */
  @Test
  public void testAfterGetThrowsException() throws Exception {
    logger.info("===== testAfterGetThrowsException [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept that throws exception
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AFTER, "counter", FieldOpType.GET, "onAfterThrowException");

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

    // 3. Get counter - callback should throw exception after reading
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);

    // 4. Verify exception was raised
    assertThat(
        "Exception should have been raised", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be SecurityException",
        response.getRaisedThrowable().getThrowable().getType(),
        is(SecurityException.class.getName()));
    assertThat(
        "Exception message should match",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Access denied by AFTER callback"));

    logger.info("===== testAfterGetThrowsException [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Illegal Operation Tests (BEFORE)
  // ===========================================================================

  /** Tests that getReturnValue() throws UnsupportedOperationException in BEFORE callback. */
  @Test
  public void testBeforeGetReturnValueThrowsUnsupported() throws Exception {
    logger.info("===== testBeforeGetReturnValueThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that attempts getReturnValue()
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.BEFORE, "counter", FieldOpType.GET, "onBeforeAttemptGetReturnValue");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke field GET
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
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "BEFORE callback should have caught UnsupportedOperationException for getReturnValue()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_BEFORE_ILLEGAL_GET_RETURN: correctly threw UnsupportedOperationException"));

    logger.info("===== testBeforeGetReturnValueThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that proceed() throws UnsupportedOperationException in BEFORE callback. */
  @Test
  public void testBeforeProceedThrowsUnsupported() throws Exception {
    logger.info("===== testBeforeProceedThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that attempts proceed()
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.BEFORE, "counter", FieldOpType.GET, "onBeforeAttemptProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke field GET
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
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "BEFORE callback should have caught UnsupportedOperationException for proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_BEFORE_ILLEGAL_PROCEED: correctly threw UnsupportedOperationException"));

    logger.info("===== testBeforeProceedThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Illegal Operation Tests (AFTER)
  // ===========================================================================

  /** Tests that setArg() throws UnsupportedOperationException in AFTER callback. */
  @Test
  public void testAfterSetArgThrowsUnsupported() throws Exception {
    logger.info("===== testAfterSetArgThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept that attempts setArg()
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AFTER, "counter", FieldOpType.GET, "onAfterAttemptSetArg");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke field GET
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
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "AFTER callback should have caught UnsupportedOperationException for setArg()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_ILLEGAL_SET_ARG: correctly threw UnsupportedOperationException"));

    logger.info("===== testAfterSetArgThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Illegal Operation Tests (AROUND)
  // ===========================================================================

  /**
   * Tests that getReturnValue() throws IllegalStateException before proceed() in AROUND callback.
   */
  @Test
  public void testAroundGetReturnValueBeforeProceedThrowsIllegalState() throws Exception {
    logger.info(
        "===== testAroundGetReturnValueBeforeProceedThrowsIllegalState [{}]: TEST STARTED =====",
        path);

    // 1. Register AROUND intercept that attempts getReturnValue() before proceed()
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AROUND,
            "counter",
            FieldOpType.GET,
            "onAroundAttemptGetReturnBeforeProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke field GET
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
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught IllegalStateException
    assertTrue(
        "AROUND callback should have caught IllegalStateException for getReturnValue() before proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_ILLEGAL_GET_RETURN_BEFORE_PROCEED: correctly threw IllegalStateException"));

    logger.info(
        "===== testAroundGetReturnValueBeforeProceedThrowsIllegalState [{}]: TEST COMPLETED =====",
        path);
  }

  /** Tests that setArg() throws IllegalStateException after proceed() in AROUND callback. */
  @Test
  public void testAroundSetArgAfterProceedThrowsIllegalState() throws Exception {
    logger.info(
        "===== testAroundSetArgAfterProceedThrowsIllegalState [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that attempts setArg() after proceed()
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AROUND, "counter", FieldOpType.PUT, "onAroundAttemptSetArgAfterProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke field PUT
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());
    ExecMessage response = invokeFieldPut(path, TARGET_CLASS, COUNTER, appInstance, 100);
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught IllegalStateException
    assertTrue(
        "AROUND callback should have caught IllegalStateException for setArg() after proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_ILLEGAL_SET_ARG_AFTER_PROCEED: correctly threw IllegalStateException"));

    logger.info(
        "===== testAroundSetArgAfterProceedThrowsIllegalState [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that skipProceed() without setReturnValue() throws IllegalStateException on field GET.
   */
  @Test
  public void testAroundSkipWithoutReturnValueThrowsIllegalState() throws Exception {
    logger.info(
        "===== testAroundSkipWithoutReturnValueThrowsIllegalState [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that attempts skipProceed without setReturnValue
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AROUND, "counter", FieldOpType.GET, "onAroundSkipWithoutReturnValue");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke field GET
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
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);

    // 3. Verify IllegalStateException was raised
    assertThat(
        "Exception should have been raised", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be IllegalStateException",
        response.getRaisedThrowable().getThrowable().getType(),
        is(IllegalStateException.class.getName()));
    assertThat(
        "Exception message should mention setReturnValue",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("setReturnValue"));

    logger.info(
        "===== testAroundSkipWithoutReturnValueThrowsIllegalState [{}]: TEST COMPLETED =====",
        path);
  }

  /** Tests that skipProceed() with null return value succeeds on field GET. */
  @Test
  public void testAroundSkipWithNullReturnValueSucceeds() throws Exception {
    logger.info("===== testAroundSkipWithNullReturnValueSucceeds [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that skips and returns null
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createLocalFieldIntercept(
            InterceptType.AROUND, "counter", FieldOpType.GET, "onAroundSkipWithNullReturn");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp and invoke field GET
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
    ExecMessage response = invokeFieldGet(path, TARGET_CLASS, COUNTER, appInstance);

    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify skip was logged
    assertTrue(
        "AROUND callback should have skipped with null return",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_SKIP_WITH_NULL_RETURN: returning null"));

    // 4. Verify return value is null
    Object returnValue = Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be null from skip", returnValue, is(nullValue()));

    logger.info("===== testAroundSkipWithNullReturnValueSucceeds [{}]: TEST COMPLETED =====", path);
  }
}
