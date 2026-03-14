/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.endtoend.field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.foobar.apps.callbacks.field.FieldHandlers;
import io.quasient.foobar.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for AROUND intercept callbacks on field operations.
 *
 * <p>These tests verify the end-to-end callback mechanism for AROUND intercepts on field GET and
 * PUT operations, including:
 *
 * <ul>
 *   <li>PUT value mutation before proceed
 *   <li>GET value override after proceed
 *   <li>Field value caching (skip-proceed on GET)
 *   <li>No-op callback behavior
 * </ul>
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and FieldHandlers
 * callback handlers (both in itt-apps module).
 *
 * <p>Tests are parameterized to run through both HOT_PATH (direct local invocation) and
 * INCOMING_RPC (remote invocation) paths.
 */
@RunWith(Parameterized.class)
public class AroundFieldCallbackIT extends AbstractInterceptIT {

  /** Field invocation descriptors for parameterized tests. */
  private static final FieldInvocation COUNTER =
      new FieldInvocation("getCounter", "setCounter", "counter", "java.lang.Integer", false);

  private static final FieldInvocation STATIC_COUNTER =
      new FieldInvocation(
          "getStaticCounter", "setStaticCounter", "staticCounter", "java.lang.Integer", true);

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** The invocation path for this parameterized test run. */
  private final InvocationPath invocationPath;

  /**
   * Constructs a parameterized test instance.
   *
   * @param invocationPath the invocation path (HOT_PATH or INCOMING_RPC)
   */
  public AroundFieldCallbackIT(InvocationPath invocationPath) {
    this.invocationPath = invocationPath;
  }

  /**
   * Provides parameters for the test: both invocation paths.
   *
   * @return collection of invocation paths to test
   */
  @Parameterized.Parameters(name = "{index}: path={0}")
  public static Collection<Object[]> data() {
    return invocationPathParameters();
  }

  // ===========================================================================
  // Instance Field PUT Tests
  // ===========================================================================

  /**
   * Tests PUT value mutation via AROUND callback on instance field.
   *
   * <p>Registers an AROUND intercept on counter PUT that doubles the value before proceeding.
   * Verifies that the doubled value is written to the field.
   */
  @Test
  public void testInstanceFieldPutValueMutation() throws Exception {
    logger.info(
        "===== testInstanceFieldPutValueMutation: TEST STARTED ({}) =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "doublePutValueAndProceed";
    final int inputValue = 25;
    final int expectedValue = 50; // 25 * 2 = 50

    // 1. Register an AROUND intercept on counter field PUT
    logger.info("Creating AROUND intercept request for counter PUT");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Call setCounter with inputValue - callback should double it
    logger.info("Invoking setCounter({}) which should be mutated to {}", inputValue, expectedValue);
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance, inputValue);

    assertThat(
        "setCounter should not raise exception", setResponse.getRaisedThrowable(), is(nullValue()));

    // 4. Verify the doubled value was written
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Counter value after set: {}", counterValue);

    assertThat("Counter should have doubled value (25 * 2 = 50)", counterValue, is(expectedValue));

    assertTrue(
        "Expected doublePutValueAndProceed callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("doublePutValueAndProceed: 25 -> 50"));

    logger.info(
        "===== testInstanceFieldPutValueMutation: TEST COMPLETED SUCCESSFULLY ({}) =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // Instance Field GET Tests
  // ===========================================================================

  /**
   * Tests GET value override via AROUND callback on instance field.
   *
   * <p>Registers an AROUND intercept on counter GET that doubles the returned value after
   * proceeding.
   */
  @Test
  public void testInstanceFieldGetValueOverride() throws Exception {
    logger.info(
        "===== testInstanceFieldGetValueOverride: TEST STARTED ({}) =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "doubleGetValueAfterProceed";
    final int initialValue = 30;
    final int expectedValue = 60; // 30 * 2 = 60

    // 1. Create InterceptableApp instance with initial value
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {initialValue}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register an AROUND intercept on counter field GET
    logger.info("Creating AROUND intercept request for counter GET with value doubling");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter - callback should double the returned value
    logger.info("Invoking getCounter() - expected to return {} (doubled)", expectedValue);
    ExecMessage getResponse =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance);

    assertThat(
        "getCounter should not raise exception", getResponse.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat(
        "Counter should be doubled after GET (30 * 2 = 60)", counterValue, is(expectedValue));

    assertTrue(
        "Expected doubleGetValueAfterProceed callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleGetValueAfterProceed: 30 -> 60"));

    logger.info(
        "===== testInstanceFieldGetValueOverride: TEST COMPLETED SUCCESSFULLY ({}) =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // Static Field PUT Tests
  // ===========================================================================

  /**
   * Tests PUT value mutation via AROUND callback on static field.
   *
   * <p>Registers an AROUND intercept on staticCounter PUT that doubles the value before proceeding.
   */
  @Test
  public void testStaticFieldPutValueMutation() throws Exception {
    logger.info(
        "===== testStaticFieldPutValueMutation: TEST STARTED ({}) =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "doublePutValueAndProceed";
    final int inputValue = 15;
    final int expectedValue = 30; // 15 * 2 = 30

    // 1. Register an AROUND intercept on staticCounter field PUT
    logger.info("Creating AROUND intercept request for staticCounter PUT");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Call setStaticCounter with inputValue - callback should double it
    logger.info(
        "Invoking setStaticCounter({}) which should be mutated to {}", inputValue, expectedValue);
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), STATIC_COUNTER, null, inputValue);

    assertThat(
        "setStaticCounter should not raise exception",
        setResponse.getRaisedThrowable(),
        is(nullValue()));

    // 3. Verify the doubled value was written
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getStaticCounter",
                new String[] {},
                null,
                null,
                new Object[] {}));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Static counter value after set: {}", counterValue);

    assertThat(
        "Static counter should have doubled value (15 * 2 = 30)", counterValue, is(expectedValue));

    assertTrue(
        "Expected doublePutValueAndProceed callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("doublePutValueAndProceed: 15 -> 30"));

    logger.info(
        "===== testStaticFieldPutValueMutation: TEST COMPLETED SUCCESSFULLY ({}) =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // Static Field GET Tests
  // ===========================================================================

  /**
   * Tests GET value override via AROUND callback on static field.
   *
   * <p>Registers an AROUND intercept on staticCounter GET that doubles the returned value after
   * proceeding.
   */
  @Test
  public void testStaticFieldGetValueOverride() throws Exception {
    logger.info(
        "===== testStaticFieldGetValueOverride: TEST STARTED ({}) =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "doubleGetValueAfterProceed";
    final int initialValue = 40;
    final int expectedValue = 80; // 40 * 2 = 80

    // 1. First set the static counter to a known value
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {initialValue}));

    // 2. Register an AROUND intercept on staticCounter field GET
    logger.info("Creating AROUND intercept request for staticCounter GET with value doubling");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getStaticCounter - callback should double the returned value
    logger.info("Invoking getStaticCounter() - expected to return {} (doubled)", expectedValue);
    ExecMessage getResponse =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), STATIC_COUNTER, null);

    assertThat(
        "getStaticCounter should not raise exception",
        getResponse.getRaisedThrowable(),
        is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Static counter value: {}", counterValue);

    assertThat(
        "Static counter should be doubled after GET (40 * 2 = 80)",
        counterValue,
        is(expectedValue));

    assertTrue(
        "Expected doubleGetValueAfterProceed callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleGetValueAfterProceed: 40 -> 80"));

    logger.info(
        "===== testStaticFieldGetValueOverride: TEST COMPLETED SUCCESSFULLY ({}) =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // No-Op Callback Tests
  // ===========================================================================

  /**
   * Tests no-op AROUND callback on instance field PUT.
   *
   * <p>Registers an AROUND intercept with a no-op callback that simply proceeds. Verifies that the
   * field value is written unchanged.
   */
  @Test
  public void testInstanceFieldPutNoOpCallback() throws Exception {
    logger.info(
        "===== testInstanceFieldPutNoOpCallback: TEST STARTED ({}) =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "noOpAround";
    final int newValue = 88;

    // 1. Register an AROUND intercept on counter field PUT
    logger.info("Creating AROUND intercept request for counter PUT with no-op callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Call setCounter
    logger.info("Invoking setCounter({}) with no-op AROUND callback", newValue);
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance, newValue);

    assertThat(
        "setCounter should not raise exception", setResponse.getRaisedThrowable(), is(nullValue()));

    // 4. Verify the value was written unchanged
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Counter value after set: {}", counterValue);

    assertThat(
        "Counter should have the set value (no-op callback doesn't mutate)",
        counterValue,
        is(newValue));

    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: proceeding with no modifications"));

    logger.info(
        "===== testInstanceFieldPutNoOpCallback: TEST COMPLETED SUCCESSFULLY ({}) =====",
        invocationPath.getDescription());
  }

  /**
   * Tests no-op AROUND callback on instance field GET.
   *
   * <p>Registers an AROUND intercept with a no-op callback that simply proceeds. Verifies that the
   * field value is returned unchanged.
   */
  @Test
  public void testInstanceFieldGetNoOpCallback() throws Exception {
    logger.info(
        "===== testInstanceFieldGetNoOpCallback: TEST STARTED ({}) =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "noOpAround";
    final int initialValue = 55;

    // 1. Create InterceptableApp instance with initial value
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {initialValue}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register an AROUND intercept on counter field GET
    logger.info("Creating AROUND intercept request for counter GET with no-op callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter
    logger.info("Invoking getCounter() with no-op AROUND callback");
    ExecMessage getResponse =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance);

    assertThat(
        "getCounter should not raise exception", getResponse.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat("Counter should be unchanged (no-op callback)", counterValue, is(initialValue));

    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: proceeding with no modifications"));

    logger.info(
        "===== testInstanceFieldGetNoOpCallback: TEST COMPLETED SUCCESSFULLY ({}) =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // skipProceed() Validation Tests
  // ===========================================================================

  /**
   * Tests that skipProceed() without setting a return value throws IllegalStateException.
   *
   * <p>Registers an AROUND intercept on field GET that calls skipProceed() without providing a
   * return value. Verifies that the server throws IllegalStateException, which is propagated to the
   * caller.
   */
  @Test
  public void testSkipProceedWithoutReturnValueThrows() throws Exception {
    logger.info(
        "===== testSkipProceedWithoutReturnValueThrows: TEST STARTED ({}) =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "skipWithoutReturnValue";

    // 1. Create InterceptableApp instance with initial value
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {100}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register an AROUND intercept on counter field GET
    logger.info("Creating AROUND intercept request for counter GET with invalid skipProceed");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter - should throw IllegalStateException due to missing return value
    logger.info("Invoking getCounter which should throw IllegalStateException");
    ExecMessage response =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance);

    // Verify IllegalStateException was thrown
    assertNotNull(
        "Expected IllegalStateException to be thrown due to missing return value",
        response.getRaisedThrowable());
    assertThat(
        "Expected IllegalStateException type",
        response.getRaisedThrowable().getThrowable().getType(),
        is(IllegalStateException.class.getName()));
    assertThat(
        "Expected exception message to mention return value or setReturnValue",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("setReturnValue"));

    logger.info(
        "===== testSkipProceedWithoutReturnValueThrows: TEST COMPLETED SUCCESSFULLY ({}) =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that skipProceed() with explicit null return value is accepted.
   *
   * <p>Registers an AROUND intercept on field GET that calls setReturnValue(null) then
   * skipProceed(). Verifies that null is properly returned to the caller (not an exception).
   */
  @Test
  public void testSkipProceedWithNullReturnValueSucceeds() throws Exception {
    logger.info(
        "===== testSkipProceedWithNullReturnValueSucceeds: TEST STARTED ({}) =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "skipWithNullReturnValue";

    // 1. Create InterceptableApp instance with initial value
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {100}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register an AROUND intercept on counter field GET
    logger.info("Creating AROUND intercept request for counter GET with null return value");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter - should return null (not throw)
    logger.info("Invoking getCounter which should return null");
    ExecMessage response =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance);

    // Verify no exception was thrown
    if (response.getRaisedThrowable() != null) {
      fail(
          "Unexpected exception: "
              + response.getRaisedThrowable().getThrowable().getType()
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    // Verify return value is null
    Object returnValue = Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be null", returnValue, is(nullValue()));

    assertTrue(
        "Expected callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "skipWithNullReturnValue: skipping execution with explicit null return value"));

    logger.info(
        "===== testSkipProceedWithNullReturnValueSucceeds: TEST COMPLETED SUCCESSFULLY ({}) =====",
        invocationPath.getDescription());
  }
}
