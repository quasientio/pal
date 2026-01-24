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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for AROUND method intercept callbacks.
 *
 * <p>These tests verify the end-to-end callback mechanism for AROUND method intercepts, including:
 *
 * <ul>
 *   <li>Skip-proceed with cached return value
 *   <li>Proceed and cache result
 *   <li>Argument mutation before proceed
 *   <li>Return value override after proceed
 *   <li>Exception handling after proceed
 *   <li>No-op callback behavior
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
public class AroundMethodCallbackIT extends AbstractInterceptIT {

  /** Method invocation descriptors for parameterized tests. */
  private static final MethodInvocation ECHO = new MethodInvocation("callEcho", "echo");

  private static final MethodInvocation MULTIPLY = new MethodInvocation("callMultiply", "multiply");

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** The invocation path for this parameterized test run. */
  private final InvocationPath invocationPath;

  /**
   * Constructs a parameterized test instance.
   *
   * @param invocationPath the invocation path (HOT_PATH or INCOMING_RPC)
   */
  public AroundMethodCallbackIT(InvocationPath invocationPath) {
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
  // Skip-Proceed Tests
  // ===========================================================================

  /**
   * Tests that AROUND callback can skip execution and return a hardcoded value.
   *
   * <p>Registers an AROUND intercept that always skips execution and returns 42. Verifies that the
   * method is not called and the hardcoded value is returned.
   */
  @Test
  public void testSkipProceedWithHardcodedValue() throws Exception {
    logger.info(
        "===== testSkipProceedWithHardcodedValue [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "skipAndReturnHardcodedValue";

    // 1. Register an AROUND intercept on multiply method
    logger.info("Creating AROUND intercept request for multiply method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("multiply", Arrays.asList("int", "int")));

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

    // 3. Invoke method via parameterized path - should return 42 regardless of input
    logger.info(
        "Invoking {} via {} path - should return 42 (skipped)",
        MULTIPLY.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            MULTIPLY,
            stringMethodsInstance,
            new String[] {"int", "int"},
            new Object[] {5, 3});

    // 4. Verify the return value is 42 (not 5*3=15)
    int returnValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat("Return value should be 42 (hardcoded by skip callback)", returnValue, is(42));

    assertTrue(
        "Expected skipAndReturnHardcodedValue callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "skipAndReturnHardcodedValue: skipping execution, returning 42"));

    logger.info(
        "===== testSkipProceedWithHardcodedValue [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that AROUND callback can skip execution and throw an exception.
   *
   * <p>Registers an AROUND intercept that skips execution and throws SecurityException.
   */
  @Test
  public void testSkipProceedWithException() throws Exception {
    logger.info(
        "===== testSkipProceedWithException [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "skipAndThrowException";

    // 1. Register an AROUND intercept on echo method
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method via parameterized path - should throw SecurityException
    logger.info(
        "Invoking {} via {} path - should throw SecurityException",
        ECHO.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"test"});

    // 4. Verify exception was thrown
    if (response.getRaisedThrowable() != null) {
      String exceptionClass = response.getRaisedThrowable().getThrowable().getType();
      String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();

      assertThat(
          "Exception should be SecurityException",
          exceptionClass,
          is("java.lang.SecurityException"));
      assertThat(
          "Exception message should mention AROUND callback",
          exceptionMessage,
          containsString("Access denied by AROUND intercept callback"));

      assertTrue(
          "Expected skipAndThrowException callback to log",
          InterceptEndToEndTestSuite.waitForAppLogLine(
              "skipAndThrowException: skipping execution, throwing SecurityException"));
    } else {
      fail("Expected SecurityException to be thrown by callback");
    }

    logger.info(
        "===== testSkipProceedWithException [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // Caching Tests
  // ===========================================================================

  /**
   * Tests cache miss behavior: first call proceeds and caches the result.
   *
   * <p>Registers an AROUND intercept with caching callback. On first call (cache miss), the method
   * executes and the result is cached.
   */
  @Test
  public void testCachingCallbackCacheMiss() throws Exception {
    logger.info(
        "===== testCachingCallbackCacheMiss [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "cachingCallback";
    final String inputValue = "test-key-" + invocationPath.getDescription();

    // 1. Register an AROUND intercept on echo method with caching callback
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. First call - cache miss, should proceed and cache result
    logger.info(
        "Invoking {} via {} path - expecting cache MISS", ECHO.targetMethod(), invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    // 4. Verify the return value is the echoed input
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat("Return value should be the input (method executed)", returnValue, is(inputValue));

    assertTrue(
        "Expected cachingCallback to log cache MISS",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "cachingCallback: cache MISS for key=" + inputValue));

    assertTrue(
        "Expected cachingCallback to log caching the result",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "cachingCallback: cached result for key=" + inputValue));

    logger.info(
        "===== testCachingCallbackCacheMiss [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests cache hit behavior: second call returns cached value without proceeding.
   *
   * <p>Makes two calls with the same key. First call (cache miss) executes the method and caches.
   * Second call (cache hit) returns cached value without executing.
   */
  @Test
  public void testCachingCallbackCacheHit() throws Exception {
    logger.info(
        "===== testCachingCallbackCacheHit [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "cachingCallback";
    final String cacheKey = "cache-hit-test-key-" + invocationPath.getDescription();

    // 1. Register an AROUND intercept on echo method with caching callback
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. First call - cache miss, populates the cache
    logger.info(
        "First call: {} via {} path - expecting cache MISS", ECHO.targetMethod(), invocationPath);
    ExecMessage firstResponse =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {cacheKey});

    String firstReturnValue =
        (String) Unwrapper.unwrapObject(firstResponse.getReturnValue().getObject());
    logger.info("First call return value: {}", firstReturnValue);

    assertThat("First call should return input (cache miss)", firstReturnValue, is(cacheKey));

    assertTrue(
        "Expected first call to log cache MISS",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "cachingCallback: cache MISS for key=" + cacheKey));

    // 4. Second call with same key - cache hit, should return cached value without proceeding
    logger.info(
        "Second call: {} via {} path - expecting cache HIT", ECHO.targetMethod(), invocationPath);
    ExecMessage secondResponse =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {cacheKey});

    String secondReturnValue =
        (String) Unwrapper.unwrapObject(secondResponse.getReturnValue().getObject());
    logger.info("Second call return value: {}", secondReturnValue);

    // The cached value is the same as the key for echo method
    assertThat("Second call should return cached value", secondReturnValue, is(cacheKey));

    assertTrue(
        "Expected second call to log cache HIT",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "cachingCallback: cache HIT for key=" + cacheKey));

    logger.info(
        "===== testCachingCallbackCacheHit [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // Argument Mutation Tests
  // ===========================================================================

  /**
   * Tests argument mutation before proceeding.
   *
   * <p>Registers an AROUND intercept that converts the first string argument to uppercase before
   * proceeding. Verifies that the method receives the mutated argument.
   */
  @Test
  public void testMutateArgsBeforeProceed() throws Exception {
    logger.info(
        "===== testMutateArgsBeforeProceed [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "uppercaseFirstArgBeforeProceed";
    final String inputValue = "hello";
    final String expectedValue = "HELLO";

    // 1. Register an AROUND intercept on echo method
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method via parameterized path with lowercase input
    logger.info(
        "Invoking {} via {} path - expecting mutated arg \"{}\"",
        ECHO.targetMethod(),
        invocationPath,
        expectedValue);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    // 4. Verify the return value is uppercase
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be uppercase (argument was mutated before proceed)",
        returnValue,
        is(expectedValue));

    assertTrue(
        "Expected uppercaseFirstArgBeforeProceed callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "uppercaseFirstArgBeforeProceed: hello -> HELLO"));

    logger.info(
        "===== testMutateArgsBeforeProceed [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests integer argument mutation before proceeding.
   *
   * <p>Registers an AROUND intercept that doubles the first integer argument before proceeding.
   */
  @Test
  public void testMutateIntArgBeforeProceed() throws Exception {
    logger.info(
        "===== testMutateIntArgBeforeProceed [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "doubleFirstIntArgBeforeProceed";
    final int inputValue = 5;
    final int factor = 3;
    final int expectedResult = 30; // (5 * 2) * 3 = 30

    // 1. Register an AROUND intercept on multiply method
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("multiply", Arrays.asList("int", "int")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method via parameterized path with value=5, factor=3
    logger.info(
        "Invoking {} via {} path - expecting doubled first arg",
        MULTIPLY.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            MULTIPLY,
            stringMethodsInstance,
            new String[] {"int", "int"},
            new Object[] {inputValue, factor});

    // 4. Verify the return value reflects the doubled first argument
    int returnValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be 30 (first arg doubled: (5*2)*3=30)",
        returnValue,
        is(expectedResult));

    assertTrue(
        "Expected doubleFirstIntArgBeforeProceed callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleFirstIntArgBeforeProceed: 5 -> 10"));

    logger.info(
        "===== testMutateIntArgBeforeProceed [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // Return Value Override Tests
  // ===========================================================================

  /**
   * Tests return value override after proceeding.
   *
   * <p>Registers an AROUND intercept that converts the return value to uppercase after proceeding.
   */
  @Test
  public void testOverrideReturnAfterProceed() throws Exception {
    logger.info(
        "===== testOverrideReturnAfterProceed [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "uppercaseReturnAfterProceed";
    final String inputValue = "hello";
    final String expectedValue = "HELLO";

    // 1. Register an AROUND intercept on echo method
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method via parameterized path - method returns lowercase, callback overrides
    logger.info(
        "Invoking {} via {} path - return should be overridden to uppercase",
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

    // 4. Verify the return value is uppercase (overridden after proceed)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be uppercase (overridden after proceed)",
        returnValue,
        is(expectedValue));

    assertTrue(
        "Expected uppercaseReturnAfterProceed callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "uppercaseReturnAfterProceed: hello -> HELLO"));

    logger.info(
        "===== testOverrideReturnAfterProceed [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests integer return value override after proceeding.
   *
   * <p>Registers an AROUND intercept that doubles the return value after proceeding.
   */
  @Test
  public void testDoubleReturnAfterProceed() throws Exception {
    logger.info(
        "===== testDoubleReturnAfterProceed [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "doubleReturnAfterProceed";
    final int a = 5;
    final int b = 3;
    final int expectedResult = 30; // (5 * 3) * 2 = 30

    // 1. Register an AROUND intercept on multiply method
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("multiply", Arrays.asList("int", "int")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method via parameterized path - method returns 15, callback doubles to 30
    logger.info(
        "Invoking {} via {} path - return should be doubled",
        MULTIPLY.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            MULTIPLY,
            stringMethodsInstance,
            new String[] {"int", "int"},
            new Object[] {a, b});

    // 4. Verify the return value is doubled
    int returnValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be 30 (15 doubled after proceed)", returnValue, is(expectedResult));

    assertTrue(
        "Expected doubleReturnAfterProceed callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleReturnAfterProceed: 15 -> 30"));

    logger.info(
        "===== testDoubleReturnAfterProceed [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // No-Op Callback Test
  // ===========================================================================

  /**
   * Tests no-op AROUND callback behavior.
   *
   * <p>Registers an AROUND intercept that simply proceeds with no modifications. Verifies that the
   * method executes normally and returns the expected value.
   */
  @Test
  public void testNoOpCallback() throws Exception {
    logger.info("===== testNoOpCallback [{}]: TEST STARTED =====", invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "noOpAround";
    final String inputValue = "hello";

    // 1. Register an AROUND intercept on echo method with no-op callback
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke method via parameterized path
    logger.info(
        "Invoking {} via {} path with no-op AROUND callback", ECHO.targetMethod(), invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});

    // 4. Verify the return value is unchanged
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat("Return value should be unchanged (no-op callback)", returnValue, is(inputValue));

    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: proceeding with no modifications"));

    logger.info(
        "===== testNoOpCallback [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  // ========================================================================
  // Phase/Type Restriction Tests - AROUND intercepts
  // ========================================================================

  /**
   * Tests that getReturnValue() throws IllegalStateException before proceed() in AROUND intercept.
   *
   * <p>Registers an AROUND intercept with a callback that attempts to call getReturnValue() before
   * proceeding. The callback verifies that IllegalStateException is thrown, then proceeds normally.
   */
  @Test
  public void testGetReturnValueThrowsBeforeProceed() throws Exception {
    logger.info(
        "===== testGetReturnValueThrowsBeforeProceed [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptGetReturnValueBeforeProceed";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    if (response.getRaisedThrowable() != null) {
      fail(
          "Callback failed: "
              + response.getRaisedThrowable().getThrowable().getType()
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    assertTrue(
        "Expected callback to log IllegalStateException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptGetReturnValueBeforeProceed: correctly threw InterceptApiMisuseException"));

    logger.info(
        "===== testGetReturnValueThrowsBeforeProceed [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that getThrownException() throws IllegalStateException before proceed() in AROUND
   * intercept.
   *
   * <p>Registers an AROUND intercept with a callback that attempts to call getThrownException()
   * before proceeding. The callback verifies that IllegalStateException is thrown, then proceeds
   * normally.
   */
  @Test
  public void testGetThrownExceptionThrowsBeforeProceed() throws Exception {
    logger.info(
        "===== testGetThrownExceptionThrowsBeforeProceed [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptGetThrownExceptionBeforeProceed";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    if (response.getRaisedThrowable() != null) {
      fail(
          "Callback failed: "
              + response.getRaisedThrowable().getThrowable().getType()
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    assertTrue(
        "Expected callback to log IllegalStateException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptGetThrownExceptionBeforeProceed: correctly threw InterceptApiMisuseException"));

    logger.info(
        "===== testGetThrownExceptionThrowsBeforeProceed [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that setArg() throws IllegalStateException after proceed() in AROUND intercept.
   *
   * <p>Registers an AROUND intercept with a callback that proceeds first, then attempts to call
   * setArg(). The callback verifies that IllegalStateException is thrown.
   */
  @Test
  public void testSetArgThrowsAfterProceed() throws Exception {
    logger.info(
        "===== testSetArgThrowsAfterProceed [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptSetArgAfterProceed";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    if (response.getRaisedThrowable() != null) {
      fail(
          "Callback failed: "
              + response.getRaisedThrowable().getThrowable().getType()
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    assertTrue(
        "Expected callback to log IllegalStateException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptSetArgAfterProceed: correctly threw InterceptApiMisuseException"));

    logger.info(
        "===== testSetArgThrowsAfterProceed [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // Skip-Proceed Validation Tests
  // ===========================================================================

  /**
   * Tests that skipProceed() without setReturnValue() or setExceptionToThrow() throws
   * IllegalStateException.
   *
   * <p>Registers an AROUND intercept that calls skipProceed() without providing a return value.
   * Verifies that the server throws IllegalStateException, which is propagated to the caller.
   */
  @Test
  public void testSkipProceedWithoutReturnValueThrows() throws Exception {
    logger.info(
        "===== testSkipProceedWithoutReturnValueThrows [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "skipWithoutReturnValue";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // Invoke method via parameterized path - should throw IllegalStateException
    logger.info(
        "Invoking {} via {} path - should throw IllegalStateException",
        ECHO.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

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
        "===== testSkipProceedWithoutReturnValueThrows [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that skipProceed() with explicit null return value is accepted.
   *
   * <p>Registers an AROUND intercept that calls setReturnValue(null) then skipProceed(). Verifies
   * that null is properly returned to the caller (not an exception).
   */
  @Test
  public void testSkipProceedWithNullReturnValueSucceeds() throws Exception {
    logger.info(
        "===== testSkipProceedWithNullReturnValueSucceeds [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "skipWithNullReturnValue";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AROUND,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());

    // Invoke method via parameterized path - should return null (not throw)
    logger.info(
        "Invoking {} via {} path - should return null", ECHO.targetMethod(), invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

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
        "===== testSkipProceedWithNullReturnValueSucceeds [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
