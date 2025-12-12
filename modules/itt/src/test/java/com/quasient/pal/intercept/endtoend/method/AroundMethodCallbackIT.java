/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.endtoend.method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.InterceptEndToEndTestSuite;
import com.quasient.pal.apps.callbacks.method.MethodHandlers;
import com.quasient.pal.apps.quantized.intercept.StringMethods;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.Unwrapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

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
 */
public class AroundMethodCallbackIT extends AbstractInterceptIT {

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

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
    logger.info("===== testSkipProceedWithHardcodedValue: TEST STARTED =====");

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

    // 3. Invoke callMultiply - should return 42 regardless of input
    logger.info("Invoking callMultiply(5, 3) which should return 42 (skipped)");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callMultiply",
                stringMethodsInstance,
                new String[] {"int", "int"},
                new Object[] {5, 3}));

    // 4. Verify the return value is 42 (not 5*3=15)
    int returnValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat("Return value should be 42 (hardcoded by skip callback)", returnValue, is(42));

    assertTrue(
        "Expected skipAndReturnHardcodedValue callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "skipAndReturnHardcodedValue: skipping execution, returning 42"));

    logger.info("===== testSkipProceedWithHardcodedValue: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that AROUND callback can skip execution and throw an exception.
   *
   * <p>Registers an AROUND intercept that skips execution and throws SecurityException.
   */
  @Test
  public void testSkipProceedWithException() throws Exception {
    logger.info("===== testSkipProceedWithException: TEST STARTED =====");

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

    // 3. Invoke callEcho - should throw SecurityException
    logger.info("Invoking callEcho which should throw SecurityException");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {"test"}));

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

    logger.info("===== testSkipProceedWithException: TEST COMPLETED SUCCESSFULLY =====");
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
    logger.info("===== testCachingCallbackCacheMiss: TEST STARTED =====");

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "cachingCallback";
    final String inputValue = "test-key";

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
    logger.info("Invoking callEcho(\"{}\") - expecting cache MISS", inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

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

    logger.info("===== testCachingCallbackCacheMiss: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests cache hit behavior: second call returns cached value without proceeding.
   *
   * <p>Makes two calls with the same key. First call (cache miss) executes the method and caches.
   * Second call (cache hit) returns cached value without executing.
   */
  @Test
  public void testCachingCallbackCacheHit() throws Exception {
    logger.info("===== testCachingCallbackCacheHit: TEST STARTED =====");

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "cachingCallback";
    final String cacheKey = "cache-hit-test-key";

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
    logger.info("First call: callEcho(\"{}\") - expecting cache MISS", cacheKey);
    ExecMessage firstResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {cacheKey}));

    String firstReturnValue =
        (String) Unwrapper.unwrapObject(firstResponse.getReturnValue().getObject());
    logger.info("First call return value: {}", firstReturnValue);

    assertThat("First call should return input (cache miss)", firstReturnValue, is(cacheKey));

    assertTrue(
        "Expected first call to log cache MISS",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "cachingCallback: cache MISS for key=" + cacheKey));

    // 4. Second call with same key - cache hit, should return cached value without proceeding
    logger.info("Second call: callEcho(\"{}\") - expecting cache HIT", cacheKey);
    ExecMessage secondResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {cacheKey}));

    String secondReturnValue =
        (String) Unwrapper.unwrapObject(secondResponse.getReturnValue().getObject());
    logger.info("Second call return value: {}", secondReturnValue);

    // The cached value is the same as the key for echo method
    assertThat("Second call should return cached value", secondReturnValue, is(cacheKey));

    assertTrue(
        "Expected second call to log cache HIT",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "cachingCallback: cache HIT for key=" + cacheKey));

    logger.info("===== testCachingCallbackCacheHit: TEST COMPLETED SUCCESSFULLY =====");
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
    logger.info("===== testMutateArgsBeforeProceed: TEST STARTED =====");

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

    // 3. Invoke callEcho with lowercase input
    logger.info(
        "Invoking callEcho(\"{}\") which should receive mutated arg \"{}\"",
        inputValue,
        expectedValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

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

    logger.info("===== testMutateArgsBeforeProceed: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests integer argument mutation before proceeding.
   *
   * <p>Registers an AROUND intercept that doubles the first integer argument before proceeding.
   */
  @Test
  public void testMutateIntArgBeforeProceed() throws Exception {
    logger.info("===== testMutateIntArgBeforeProceed: TEST STARTED =====");

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

    // 3. Invoke callMultiply with value=5, factor=3
    logger.info(
        "Invoking callMultiply({}, {}) which should receive doubled first arg", inputValue, factor);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callMultiply",
                stringMethodsInstance,
                new String[] {"int", "int"},
                new Object[] {inputValue, factor}));

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

    logger.info("===== testMutateIntArgBeforeProceed: TEST COMPLETED SUCCESSFULLY =====");
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
    logger.info("===== testOverrideReturnAfterProceed: TEST STARTED =====");

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

    // 3. Invoke callEcho - method returns lowercase, callback overrides to uppercase
    logger.info("Invoking callEcho(\"{}\") - return should be overridden to uppercase", inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

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

    logger.info("===== testOverrideReturnAfterProceed: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests integer return value override after proceeding.
   *
   * <p>Registers an AROUND intercept that doubles the return value after proceeding.
   */
  @Test
  public void testDoubleReturnAfterProceed() throws Exception {
    logger.info("===== testDoubleReturnAfterProceed: TEST STARTED =====");

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

    // 3. Invoke callMultiply - method returns 15, callback doubles to 30
    logger.info("Invoking callMultiply({}, {}) - return should be doubled", a, b);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callMultiply",
                stringMethodsInstance,
                new String[] {"int", "int"},
                new Object[] {a, b}));

    // 4. Verify the return value is doubled
    int returnValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be 30 (15 doubled after proceed)", returnValue, is(expectedResult));

    assertTrue(
        "Expected doubleReturnAfterProceed callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleReturnAfterProceed: 15 -> 30"));

    logger.info("===== testDoubleReturnAfterProceed: TEST COMPLETED SUCCESSFULLY =====");
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
    logger.info("===== testNoOpCallback: TEST STARTED =====");

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

    // 3. Invoke callEcho
    logger.info("Invoking callEcho(\"{}\") with no-op AROUND callback", inputValue);
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {inputValue}));

    // 4. Verify the return value is unchanged
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat("Return value should be unchanged (no-op callback)", returnValue, is(inputValue));

    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: proceeding with no modifications"));

    logger.info("===== testNoOpCallback: TEST COMPLETED SUCCESSFULLY =====");
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
    logger.info("===== testGetReturnValueThrowsBeforeProceed: TEST STARTED =====");

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
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {"hello"}));

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
            "attemptGetReturnValueBeforeProceed: correctly threw IllegalStateException"));

    logger.info("===== testGetReturnValueThrowsBeforeProceed: TEST COMPLETED SUCCESSFULLY =====");
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
    logger.info("===== testGetThrownExceptionThrowsBeforeProceed: TEST STARTED =====");

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
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {"hello"}));

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
            "attemptGetThrownExceptionBeforeProceed: correctly threw IllegalStateException"));

    logger.info(
        "===== testGetThrownExceptionThrowsBeforeProceed: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that setArg() throws IllegalStateException after proceed() in AROUND intercept.
   *
   * <p>Registers an AROUND intercept with a callback that proceeds first, then attempts to call
   * setArg(). The callback verifies that IllegalStateException is thrown.
   */
  @Test
  public void testSetArgThrowsAfterProceed() throws Exception {
    logger.info("===== testSetArgThrowsAfterProceed: TEST STARTED =====");

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
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {"hello"}));

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
            "attemptSetArgAfterProceed: correctly threw IllegalStateException"));

    logger.info("===== testSetArgThrowsAfterProceed: TEST COMPLETED SUCCESSFULLY =====");
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
    logger.info("===== testSkipProceedWithoutReturnValueThrows: TEST STARTED =====");

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

    // Invoke callEcho - should throw IllegalStateException due to missing return value
    logger.info("Invoking callEcho which should throw IllegalStateException");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {"hello"}));

    // Verify IllegalStateException was thrown
    assertTrue(
        "Expected IllegalStateException to be thrown due to missing return value",
        response.getRaisedThrowable() != null);
    assertThat(
        "Expected IllegalStateException type",
        response.getRaisedThrowable().getThrowable().getType(),
        is(IllegalStateException.class.getName()));
    assertThat(
        "Expected exception message to mention return value or setReturnValue",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("setReturnValue"));

    logger.info("===== testSkipProceedWithoutReturnValueThrows: TEST COMPLETED SUCCESSFULLY =====");
  }

  /**
   * Tests that skipProceed() with explicit null return value is accepted.
   *
   * <p>Registers an AROUND intercept that calls setReturnValue(null) then skipProceed(). Verifies
   * that null is properly returned to the caller (not an exception).
   */
  @Test
  public void testSkipProceedWithNullReturnValueSucceeds() throws Exception {
    logger.info("===== testSkipProceedWithNullReturnValueSucceeds: TEST STARTED =====");

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

    // Invoke callEcho - should return null (not throw)
    logger.info("Invoking callEcho which should return null");
    ExecMessage response =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                StringMethods.class.getName(),
                "callEcho",
                stringMethodsInstance,
                new String[] {"java.lang.String"},
                new Object[] {"hello"}));

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
        "===== testSkipProceedWithNullReturnValueSucceeds: TEST COMPLETED SUCCESSFULLY =====");
  }
}
