/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.endtoend.method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.apps.callbacks.method.MethodHandlers;
import io.quasient.pal.apps.quantized.intercept.StringMethods;
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
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for BEFORE_ASYNC method intercept callbacks.
 *
 * <p>These tests verify that BEFORE_ASYNC callbacks:
 *
 * <ul>
 *   <li>Can read arguments but cannot mutate them
 *   <li>Are fire-and-forget (method execution does not wait for callback response)
 *   <li>Throw UnsupportedOperationException when attempting to mutate arguments
 * </ul>
 *
 * <p>Tests use the shared intercept peer with StringMethods application class and MethodHandlers
 * callback handlers (both in itt-apps module).
 *
 * <p><b>Parameterized:</b> Each test runs through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Invokes wrapper method (e.g., callEcho) → intercept fires at call-site
 *   <li><b>INCOMING_RPC</b>: Invokes target method directly (e.g., echo) → intercept fires in
 *       dispatchIncoming
 * </ul>
 */
@RunWith(Parameterized.class)
public class BeforeMethodAsyncCallbackIT extends AbstractInterceptIT {

  /** Method invocation descriptor for parameterized tests. */
  private static final MethodInvocation ECHO = new MethodInvocation("callEcho", "echo");

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** The invocation path for this parameterized test run. */
  private final InvocationPath invocationPath;

  /**
   * Constructs a parameterized test instance.
   *
   * @param invocationPath the invocation path (HOT_PATH or INCOMING_RPC)
   */
  public BeforeMethodAsyncCallbackIT(InvocationPath invocationPath) {
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
   * Tests that BEFORE_ASYNC callback can read arguments without mutation.
   *
   * <p>Registers a BEFORE_ASYNC intercept that only logs arguments. Verifies that the method
   * executes normally with unchanged arguments.
   */
  @Test
  public void testAsyncCallbackCanReadArgs() throws Exception {
    logger.info(
        "===== testAsyncCallbackCanReadArgs [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "logArgs";
    final String inputValue = "hello";

    // 1. Register a BEFORE_ASYNC intercept on echo method
    logger.info("Creating BEFORE_ASYNC intercept request for echo method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method via parameterized path - ASYNC callback should log args but not mutate them
    logger.info(
        "Invoking {} via {} path with BEFORE_ASYNC callback", ECHO.targetMethod(), invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    // 4. Verify the return value is unchanged (ASYNC cannot mutate)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (ASYNC callbacks cannot mutate)",
        returnValue,
        is(inputValue));

    // 5. Verify the callback logged the args in the application log
    assertTrue(
        "Expected logArgs callback to log the args in application log",
        InterceptEndToEndTestSuite.waitForAppLogLine("logArgs.*args.*" + inputValue));

    logger.info(
        "===== testAsyncCallbackCanReadArgs [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that BEFORE_ASYNC callback throws UnsupportedOperationException on mutation attempt.
   *
   * <p>Registers a BEFORE_ASYNC intercept that attempts to mutate arguments. Verifies that:
   *
   * <ul>
   *   <li>The callback throws UnsupportedOperationException
   *   <li>The original method still executes (ASYNC is fire-and-forget)
   *   <li>Arguments are not mutated
   * </ul>
   */
  @Test
  public void testAsyncCallbackCannotMutateArgs() throws Exception {
    logger.info(
        "===== testAsyncCallbackCannotMutateArgs [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptArgMutation";
    final String inputValue = "hello";

    // 1. Register a BEFORE_ASYNC intercept that attempts mutation
    logger.info("Creating BEFORE_ASYNC intercept request with mutation attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method - callback will throw but ASYNC is fire-and-forget so method still runs
    logger.info(
        "Invoking {} via {} path - callback should throw but method should still execute",
        ECHO.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    // 4. Verify method executed with unchanged args (ASYNC exception doesn't stop execution)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (ASYNC callback exception doesn't stop execution)",
        returnValue,
        is(inputValue));

    // Verify callback caught UnsupportedOperationException
    assertTrue(
        "Expected attemptArgMutation callback to log UnsupportedOperationException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptArgMutation: correctly threw UnsupportedOperationException"));

    logger.info(
        "===== testAsyncCallbackCannotMutateArgs [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that BEFORE_ASYNC callback receives correct argument values.
   *
   * <p>Registers a BEFORE_ASYNC intercept that verifies the first argument equals "hello". If not,
   * the callback throws AssertionError. This tests that arguments are correctly deserialized and
   * passed to ASYNC callbacks.
   */
  @Test
  public void testAsyncCallbackReceivesCorrectArgs() throws Exception {
    logger.info(
        "===== testAsyncCallbackReceivesCorrectArgs [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "verifyFirstArgIsHello";
    final String inputValue = "hello";

    // 1. Register a BEFORE_ASYNC intercept that verifies args
    logger.info("Creating BEFORE_ASYNC intercept request with arg verification");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method via parameterized path - callback will verify and log success
    logger.info(
        "Invoking {} via {} path - callback should verify arg is 'hello'",
        ECHO.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    // 4. Method should execute normally
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Method should execute and return input", returnValue, is(inputValue));

    // 5. Verify the callback verified and logged success in the application log
    assertTrue(
        "Expected verifyFirstArgIsHello callback to log success in application log",
        InterceptEndToEndTestSuite.waitForAppLogLine("verifyFirstArgIsHello.*verified.*hello"));

    logger.info(
        "===== testAsyncCallbackReceivesCorrectArgs [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that BEFORE_ASYNC callback cannot throw exceptions via setExceptionToThrow.
   *
   * <p>Registers a BEFORE_ASYNC intercept that attempts to call setExceptionToThrow. Verifies that:
   *
   * <ol>
   *   <li>The callback throws UnsupportedOperationException when calling setExceptionToThrow
   *   <li>The original method still executes (ASYNC is fire-and-forget)
   *   <li>The method returns normally with unchanged result
   * </ol>
   */
  @Test
  public void testAsyncCallbackCannotThrowException() throws Exception {
    logger.info(
        "===== testAsyncCallbackCannotThrowException [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptThrowException";
    final String inputValue = "hello";

    // 1. Register a BEFORE_ASYNC intercept that attempts to throw exception
    logger.info("Creating BEFORE_ASYNC intercept request with exception throw attempt");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method - callback will throw UnsupportedOperationException but method still runs
    logger.info(
        "Invoking {} via {} path - callback should throw but method should still execute",
        ECHO.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    // 4. Verify method executed normally (ASYNC exception doesn't affect caller)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (ASYNC callback exception doesn't affect caller)",
        returnValue,
        is(inputValue));

    // Verify callback logged that UnsupportedOperationException was correctly thrown
    assertTrue(
        "Expected attemptThrowException callback to log UnsupportedOperationException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptThrowException: correctly threw UnsupportedOperationException"));

    logger.info(
        "===== testAsyncCallbackCannotThrowException [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
