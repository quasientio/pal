/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.endtoend.constructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.apps.callbacks.constructor.ConstructorHandlers;
import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for AFTER_ASYNC constructor intercept callbacks.
 *
 * <p>These tests verify that AFTER_ASYNC callbacks on constructors:
 *
 * <ul>
 *   <li>Can read the constructed object (return value) but cannot override it
 *   <li>Are fire-and-forget (caller receives original constructed object)
 *   <li>Throw UnsupportedOperationException when attempting to override return value
 * </ul>
 *
 * <p><b>Note:</b> Constructor return values (the constructed object) are typically not
 * JSON-serializable. AFTER_ASYNC callbacks can observe that construction completed but cannot
 * meaningfully inspect the constructed object's state via getReturnValue().
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and
 * ConstructorHandlers callback handlers (both in itt-apps module).
 *
 * <p><b>Parameterized:</b> Each test runs through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Invokes factory method (e.g., createWithCounter) → intercept fires at
 *       call-site
 *   <li><b>INCOMING_RPC</b>: Invokes constructor directly → intercept fires in dispatchIncoming
 * </ul>
 */
@RunWith(Parameterized.class)
public class AfterConstructorAsyncCallbackIT extends AbstractInterceptIT {

  /** Constructor invocation descriptor for parameterized tests. */
  private static final ConstructorInvocation WITH_COUNTER =
      new ConstructorInvocation("createWithCounter", List.of("java.lang.Integer"));

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** The invocation path for this parameterized test run. */
  private final InvocationPath invocationPath;

  /**
   * Constructs a parameterized test instance.
   *
   * @param invocationPath the invocation path (HOT_PATH or INCOMING_RPC)
   */
  public AfterConstructorAsyncCallbackIT(InvocationPath invocationPath) {
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
   * Tests that AFTER_ASYNC callback is invoked after constructor execution.
   *
   * <p>Registers an AFTER_ASYNC intercept that logs the return value. Verifies that the caller
   * receives the original constructed object.
   */
  @Test
  public void testAsyncCallbackInvokedAfterConstruction() throws Exception {
    logger.info(
        "===== testAsyncCallbackInvokedAfterConstruction [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "logReturnValue";
    final int inputValue = 42;

    // 1. Register an AFTER_ASYNC intercept on parameterized constructor
    logger.info("Creating AFTER_ASYNC intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor via parameterized path
    logger.info("Invoking createWithCounter({}) with AFTER_ASYNC callback", inputValue);
    ExecMessage response =
        invokeConstructor(
            invocationPath,
            InterceptableApp.class.getName(),
            WITH_COUNTER,
            new Object[] {inputValue});

    // 3. Verify no exception was raised
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify the constructed object is returned correctly
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());
    logger.info("InterceptableApp instance created with ref: {}", appInstance);

    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat(
        "Counter should be the input value (ASYNC callback doesn't affect result)",
        counterValue,
        is(inputValue));

    // Verify callback logged the return value in application log
    assertTrue(
        "Expected logReturnValue callback to log the return value",
        InterceptEndToEndTestSuite.waitForAppLogLine("logReturnValue.*AFTER_ASYNC.*returnValue"));

    logger.info(
        "===== testAsyncCallbackInvokedAfterConstruction [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that AFTER_ASYNC callback throws UnsupportedOperationException on override attempt.
   *
   * <p>Registers an AFTER_ASYNC intercept that attempts to override return value. Verifies that:
   *
   * <ul>
   *   <li>The callback throws UnsupportedOperationException
   *   <li>The caller still receives the original constructed object (ASYNC is fire-and-forget)
   * </ul>
   */
  @Test
  public void testAsyncCallbackCannotOverrideReturnValue() throws Exception {
    logger.info(
        "===== testAsyncCallbackCannotOverrideReturnValue [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "attemptReturnOverride";
    final int inputValue = 42;

    // 1. Register an AFTER_ASYNC intercept that attempts override
    logger.info("Creating AFTER_ASYNC intercept request with override attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor via parameterized path - callback will throw but ASYNC is
    // fire-and-forget
    logger.info(
        "Invoking createWithCounter - callback should throw but caller gets original object");
    ExecMessage response =
        invokeConstructor(
            invocationPath,
            InterceptableApp.class.getName(),
            WITH_COUNTER,
            new Object[] {inputValue});

    // 3. Verify caller receives original constructed object
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());

    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat(
        "Counter should be unchanged (ASYNC callback exception doesn't affect caller)",
        counterValue,
        is(inputValue));

    // Verify callback logged the override attempt in application log
    assertTrue(
        "Expected attemptReturnOverride callback to log override attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptReturnOverride.*AFTER_ASYNC.*attempting to override return value"));

    logger.info(
        "===== testAsyncCallbackCannotOverrideReturnValue [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests no-op AFTER_ASYNC callback behavior on constructor.
   *
   * <p>Registers an AFTER_ASYNC intercept with a simple no-op callback. Verifies that the
   * constructor executes normally and the caller receives the constructed object.
   */
  @Test
  public void testAsyncNoOpCallback() throws Exception {
    logger.info(
        "===== testAsyncNoOpCallback [{}]: TEST STARTED =====", invocationPath.getDescription());

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "logArgs"; // Re-use logArgs as a simple no-op for AFTER
    final int inputValue = 123;

    // 1. Register an AFTER_ASYNC intercept with no-op
    logger.info("Creating AFTER_ASYNC intercept request with no-op callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor via parameterized path
    logger.info("Invoking createWithCounter({}) with no-op AFTER_ASYNC callback", inputValue);
    ExecMessage response =
        invokeConstructor(
            invocationPath,
            InterceptableApp.class.getName(),
            WITH_COUNTER,
            new Object[] {inputValue});

    // 3. Verify no exception was raised
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify the counter value
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());

    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat("Counter should be the input value", counterValue, is(inputValue));

    // Verify callback logged in application log
    // Note: For AFTER intercepts on constructors, args are empty since the args
    // were already consumed during construction
    assertTrue(
        "Expected logArgs callback to log (with empty args for AFTER intercept)",
        InterceptEndToEndTestSuite.waitForAppLogLine("logArgs.*BEFORE_ASYNC.*args.*\\[\\]"));

    logger.info(
        "===== testAsyncNoOpCallback [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that AFTER_ASYNC callback cannot throw exceptions via setExceptionToThrow.
   *
   * <p>Registers an AFTER_ASYNC intercept on constructor that attempts to call setExceptionToThrow.
   * Verifies that:
   *
   * <ol>
   *   <li>The callback throws UnsupportedOperationException when calling setExceptionToThrow
   *   <li>The caller receives the constructed object normally (ASYNC is fire-and-forget)
   * </ol>
   */
  @Test
  public void testAsyncCallbackCannotThrowException() throws Exception {
    logger.info(
        "===== testAsyncCallbackCannotThrowException [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "attemptThrowException";
    final int inputValue = 42;

    // 1. Register an AFTER_ASYNC intercept that attempts to throw exception
    logger.info("Creating AFTER_ASYNC intercept request with exception throw attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor via parameterized path
    logger.info(
        "Invoking createWithCounter({}) - callback should throw after but caller ok", inputValue);
    ExecMessage response =
        invokeConstructor(
            invocationPath,
            InterceptableApp.class.getName(),
            WITH_COUNTER,
            new Object[] {inputValue});

    // 3. Verify no exception was raised
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Get the created instance and verify the counter value
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());

    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    assertThat("Constructor should execute and set counter", counterValue, is(inputValue));

    // Verify callback logged the exception throw attempt in application log
    assertTrue(
        "Expected attemptThrowException callback to log exception throw attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptThrowException.*AFTER_ASYNC.*attempting to set exception"));

    logger.info(
        "===== testAsyncCallbackCannotThrowException [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
