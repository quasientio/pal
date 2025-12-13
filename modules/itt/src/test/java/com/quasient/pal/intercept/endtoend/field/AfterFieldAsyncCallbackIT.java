/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.endtoend.field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.InterceptEndToEndTestSuite;
import com.quasient.pal.apps.callbacks.field.FieldHandlers;
import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.FieldOpType;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for AFTER_ASYNC field intercept callbacks.
 *
 * <p>These tests verify that AFTER_ASYNC callbacks on field operations:
 *
 * <ul>
 *   <li>Can read the GET return value but cannot override it
 *   <li>Are fire-and-forget (caller receives original value)
 *   <li>Throw UnsupportedOperationException when attempting to override return value
 * </ul>
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and FieldHandlers
 * callback handlers (both in itt-apps module).
 */
@RunWith(Parameterized.class)
public class AfterFieldAsyncCallbackIT extends AbstractInterceptIT {

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
  public AfterFieldAsyncCallbackIT(InvocationPath invocationPath) {
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

  /**
   * Tests that AFTER_ASYNC callback can read GET return value without override on instance field.
   *
   * <p>Registers an AFTER_ASYNC intercept on instance field GET that only logs the return value.
   * Verifies that the caller receives the original value.
   */
  @Test
  public void testInstanceFieldGetAsyncCanReadValue() throws Exception {
    logger.info(
        "===== testInstanceFieldGetAsyncCanReadValue ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "logReturnValue";
    final int initialValue = 42;

    // 1. Create InterceptableApp instance with initial value
    logger.info("Creating InterceptableApp instance with initial value {}", initialValue);
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

    // 2. Register an AFTER_ASYNC intercept on counter field GET
    logger.info("Creating AFTER_ASYNC intercept request for counter GET");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter to trigger the callback
    logger.info("Invoking getCounter() with AFTER_ASYNC callback via {}", invocationPath);
    ExecMessage response =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance);

    // 4. Verify no exception and value is unchanged
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat(
        "Counter value should be unchanged (ASYNC cannot override)",
        counterValue,
        is(initialValue));

    // Verify callback logged the return value in application log
    assertTrue(
        "Expected logReturnValue callback to log the return value",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "logReturnValue.*AFTER_ASYNC.*returnValue.*" + initialValue));

    logger.info(
        "===== testInstanceFieldGetAsyncCanReadValue ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that AFTER_ASYNC callback throws UnsupportedOperationException on override attempt.
   *
   * <p>Registers an AFTER_ASYNC intercept that attempts to override the GET return value. Verifies
   * that the caller still receives the original value.
   */
  @Test
  public void testInstanceFieldGetAsyncCannotOverride() throws Exception {
    logger.info(
        "===== testInstanceFieldGetAsyncCannotOverride ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "attemptReturnOverride";
    final int initialValue = 25;

    // 1. Create InterceptableApp instance with initial value
    logger.info("Creating InterceptableApp instance with initial value {}", initialValue);
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

    // 2. Register an AFTER_ASYNC intercept that attempts override
    logger.info("Creating AFTER_ASYNC intercept request with override attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter - callback will throw but ASYNC is fire-and-forget
    logger.info(
        "Invoking getCounter() via {} - callback should throw but caller gets original value",
        invocationPath);
    ExecMessage response =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance);

    // 4. Verify caller receives original value (ASYNC exception doesn't affect caller)
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat(
        "Counter value should be unchanged (ASYNC callback exception doesn't affect caller)",
        counterValue,
        is(initialValue));

    // Verify callback logged the override attempt in application log
    assertTrue(
        "Expected attemptReturnOverride callback to log override attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptReturnOverride.*AFTER_ASYNC.*attempting to override return value"));

    logger.info(
        "===== testInstanceFieldGetAsyncCannotOverride ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests AFTER_ASYNC callback on static field GET.
   *
   * <p>Verifies that ASYNC callbacks work correctly for static field GET operations.
   */
  @Test
  public void testStaticFieldGetAsyncNoOp() throws Exception {
    logger.info(
        "===== testStaticFieldGetAsyncNoOp ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "logReturnValue";
    final int initialValue = 200;

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

    // 2. Register an AFTER_ASYNC intercept on staticCounter field GET
    logger.info("Creating AFTER_ASYNC intercept request for staticCounter GET");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getStaticCounter to trigger the callback
    logger.info("Invoking getStaticCounter() with AFTER_ASYNC callback via {}", invocationPath);
    ExecMessage response =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), STATIC_COUNTER, null);

    // 4. Verify no exception and value is unchanged
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Static counter value: {}", counterValue);

    assertThat("Static counter value should be unchanged", counterValue, is(initialValue));

    // Verify callback logged the return value in application log
    assertTrue(
        "Expected logReturnValue callback to log the return value",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "logReturnValue.*AFTER_ASYNC.*returnValue.*" + initialValue));

    logger.info(
        "===== testStaticFieldGetAsyncNoOp ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that AFTER_ASYNC callback cannot throw exceptions via setExceptionToThrow.
   *
   * <p>Registers an AFTER_ASYNC intercept on instance field GET that attempts to call
   * setExceptionToThrow. Verifies that:
   *
   * <ol>
   *   <li>The callback throws UnsupportedOperationException when calling setExceptionToThrow
   *   <li>The caller receives the original field value (ASYNC is fire-and-forget)
   * </ol>
   */
  @Test
  public void testAsyncCallbackCannotThrowException() throws Exception {
    logger.info(
        "===== testAsyncCallbackCannotThrowException ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "attemptThrowException";
    final int initialValue = 42;

    // 1. Create InterceptableApp instance with initial value
    logger.info("Creating InterceptableApp instance with initial value {}", initialValue);
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

    // 2. Register an AFTER_ASYNC intercept that attempts to throw exception
    logger.info("Creating AFTER_ASYNC intercept request with exception throw attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getCounter to trigger the callback
    logger.info(
        "Invoking getCounter() via {} - callback should throw but caller should get original value",
        invocationPath);
    ExecMessage response =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance);

    // 4. Verify no exception and value is unchanged
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat(
        "Counter value should be unchanged (ASYNC callback exception doesn't affect caller)",
        counterValue,
        is(initialValue));

    // Verify callback logged the exception throw attempt in application log
    assertTrue(
        "Expected attemptThrowException callback to log exception throw attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptThrowException.*AFTER_ASYNC.*attempting to set exception"));

    logger.info(
        "===== testAsyncCallbackCannotThrowException ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
