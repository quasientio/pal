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
 * Integration tests for BEFORE method intercept callbacks with argument mutation.
 *
 * <p>These tests verify the end-to-end callback mechanism for BEFORE method intercepts, including
 * argument mutation via static callback methods invoked using reflection.
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
public class BeforeMethodCallbackIT extends AbstractInterceptIT {

  /** Method invocation descriptors for parameterized tests. */
  private static final MethodInvocation ECHO = new MethodInvocation("callEcho", "echo");

  private static final MethodInvocation CONCATENATE =
      new MethodInvocation("callConcatenate", "concatenate");
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
  public BeforeMethodCallbackIT(InvocationPath invocationPath) {
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
   * Tests single-argument mutation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept on StringMethods.echo() that converts the argument to
   * uppercase. Verifies that the method receives the mutated argument by checking the return value.
   */
  @Test
  public void testSingleArgumentMutation() throws Exception {
    logger.info(
        "===== testSingleArgumentMutation [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "uppercaseFirstArg";
    final String inputValue = "hello";
    final String expectedValue = "HELLO";

    // 1. Register a BEFORE intercept on echo method
    logger.info("Creating BEFORE intercept request for echo method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Callback to interceptor peer
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke method via the parameterized path
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
    logger.info("Invocation completed");

    // 4. Verify the return value is uppercase (proving arg was mutated before method execution)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be uppercase (argument was mutated by BEFORE callback)",
        returnValue,
        is(expectedValue));

    // Verify callback logged the mutation in application log
    assertTrue(
        "Expected uppercaseFirstArg callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("uppercaseFirstArg: hello -> HELLO"));

    logger.info(
        "===== testSingleArgumentMutation [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests multi-argument mutation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept on StringMethods.concatenate() that converts both arguments to
   * uppercase. Verifies that both arguments were mutated by checking the concatenated result.
   */
  @Test
  public void testMultiArgumentMutation() throws Exception {
    logger.info(
        "===== testMultiArgumentMutation [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "uppercaseBothArgs";
    final String inputA = "hello";
    final String inputB = "world";
    final String expectedResult = "HELLOWORLD";

    // 1. Register a BEFORE intercept on concatenate method
    logger.info("Creating BEFORE intercept request for concatenate method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "concatenate", Arrays.asList("java.lang.String", "java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke method via the parameterized path
    logger.info(
        "Invoking {} via {} path - expecting mutated args",
        CONCATENATE.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            CONCATENATE,
            stringMethodsInstance,
            new String[] {"java.lang.String", "java.lang.String"},
            new Object[] {inputA, inputB});
    logger.info("Invocation completed");

    // 4. Verify the return value is all uppercase
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be all uppercase (both arguments were mutated)",
        returnValue,
        is(expectedResult));

    // Verify callback logged both argument mutations in application log
    assertTrue(
        "Expected uppercaseBothArgs callback to log mutations",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "uppercaseBothArgs:.*hello.*world.*HELLO.*WORLD"));

    logger.info(
        "===== testMultiArgumentMutation [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests primitive argument mutation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept on StringMethods.multiply() that doubles the first integer
   * argument. Verifies the mutation by checking the multiplication result.
   */
  @Test
  public void testPrimitiveArgumentMutation() throws Exception {
    logger.info(
        "===== testPrimitiveArgumentMutation [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "doubleFirstIntArg";
    final int inputValue = 5;
    final int factor = 3;
    final int expectedResult = 30; // (5 * 2) * 3 = 30

    // 1. Register a BEFORE intercept on multiply method
    logger.info("Creating BEFORE intercept request for multiply method");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("multiply", Arrays.asList("int", "int")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke method via the parameterized path
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
    logger.info("Invocation completed");

    // 4. Verify the return value reflects the doubled first argument
    int returnValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be 30 (first arg was doubled by callback: (5*2)*3=30)",
        returnValue,
        is(expectedResult));

    // Verify callback logged the primitive mutation in application log
    assertTrue(
        "Expected doubleFirstIntArg callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("doubleFirstIntArg: 5 -> 10"));

    logger.info(
        "===== testPrimitiveArgumentMutation [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests exception propagation via BEFORE callback.
   *
   * <p>Registers a BEFORE intercept that throws a SecurityException. Verifies that the exception is
   * propagated back to the caller and the method is never executed.
   */
  @Test
  public void testCallbackThrowsException() throws Exception {
    logger.info(
        "===== testCallbackThrowsException [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "throwException";

    // 1. Register a BEFORE intercept on echo method that throws exception
    logger.info("Creating BEFORE intercept request for echo method with throwing callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke method - should throw SecurityException from callback
    logger.info(
        "Invoking {} via {} - should throw SecurityException", ECHO.targetMethod(), invocationPath);
    try {
      ExecMessage response =
          invokeMethod(
              invocationPath,
              StringMethods.class.getName(),
              ECHO,
              stringMethodsInstance,
              new String[] {"java.lang.String"},
              new Object[] {"test"});

      // Check if response contains a raised exception
      if (response.getRaisedThrowable() != null) {
        String exceptionClass = response.getRaisedThrowable().getThrowable().getType();
        String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();

        logger.info("Received exception: {} with message: {}", exceptionClass, exceptionMessage);

        assertThat(
            "Exception should be SecurityException",
            exceptionClass,
            is("java.lang.SecurityException"));
        assertThat(
            "Exception message should mention callback",
            exceptionMessage,
            containsString("Access denied by intercept callback"));

        // Verify callback logged that it was throwing an exception
        assertTrue(
            "Expected throwException callback to log",
            InterceptEndToEndTestSuite.waitForAppLogLine(
                "throwException: throwing SecurityException"));

        logger.info(
            "===== testCallbackThrowsException [{}]: TEST COMPLETED SUCCESSFULLY =====",
            invocationPath.getDescription());
      } else {
        fail("Expected SecurityException to be thrown by callback, but no exception was raised");
      }
    } catch (Exception e) {
      logger.error("Unexpected exception during test", e);
      throw e;
    }
  }

  /**
   * Tests no-op callback behavior.
   *
   * <p>Registers a BEFORE intercept with a no-op callback that doesn't mutate arguments. Verifies
   * that the method receives the original arguments unchanged.
   */
  @Test
  public void testNoOpCallback() throws Exception {
    logger.info("===== testNoOpCallback [{}]: TEST STARTED =====", invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "noOp";
    final String inputValue = "hello";

    // 1. Register a BEFORE intercept on echo method with no-op callback
    logger.info("Creating BEFORE intercept request for echo method with no-op callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke method via the parameterized path
    logger.info("Invoking {} via {} with no-op callback", ECHO.targetMethod(), invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});
    logger.info("Invocation completed");

    // 4. Verify the return value is unchanged
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (no-op callback doesn't mutate)",
        returnValue,
        is(inputValue));

    // Verify callback logged no mutations in application log
    assertTrue(
        "Expected noOp callback to log no mutations",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations"));

    logger.info(
        "===== testNoOpCallback [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  // ========================================================================
  // Phase/Type Restriction Tests - BEFORE intercepts
  // ========================================================================

  /**
   * Tests that getReturnValue() throws UnsupportedOperationException in BEFORE intercept.
   *
   * <p>Registers a BEFORE intercept with a callback that attempts to call getReturnValue(). The
   * callback verifies that UnsupportedOperationException is thrown, then returns normally. If the
   * exception is not thrown, the callback throws AssertionError and the test fails.
   */
  @Test
  public void testGetReturnValueThrowsInBefore() throws Exception {
    logger.info(
        "===== testGetReturnValueThrowsInBefore [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptGetReturnValueInBefore";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
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

    // Invoke method - callback should verify exception and return normally
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    // If callback threw AssertionError, propagate it
    if (response.getRaisedThrowable() != null) {
      fail(
          "Callback failed: "
              + response.getRaisedThrowable().getThrowable().getType()
              + " - "
              + response.getRaisedThrowable().getThrowable().getMessage());
    }

    assertTrue(
        "Expected callback to log UnsupportedOperationException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptGetReturnValueInBefore: correctly threw UnsupportedOperationException"));

    logger.info(
        "===== testGetReturnValueThrowsInBefore [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that getThrownException() throws UnsupportedOperationException in BEFORE intercept.
   *
   * <p>Registers a BEFORE intercept with a callback that attempts to call getThrownException(). The
   * callback verifies that UnsupportedOperationException is thrown, then returns normally.
   */
  @Test
  public void testGetThrownExceptionThrowsInBefore() throws Exception {
    logger.info(
        "===== testGetThrownExceptionThrowsInBefore [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptGetThrownExceptionInBefore";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
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
        "Expected callback to log UnsupportedOperationException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptGetThrownExceptionInBefore: correctly threw UnsupportedOperationException"));

    logger.info(
        "===== testGetThrownExceptionThrowsInBefore [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that setReturnValue() throws UnsupportedOperationException in BEFORE intercept.
   *
   * <p>Registers a BEFORE intercept with a callback that attempts to call setReturnValue(). The
   * callback verifies that UnsupportedOperationException is thrown, then returns normally.
   */
  @Test
  public void testSetReturnValueThrowsInBefore() throws Exception {
    logger.info(
        "===== testSetReturnValueThrowsInBefore [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptSetReturnValueInBefore";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
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
        "Expected callback to log UnsupportedOperationException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptSetReturnValueInBefore: correctly threw UnsupportedOperationException"));

    logger.info(
        "===== testSetReturnValueThrowsInBefore [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that setExceptionToThrow() works in BEFORE intercept via InterceptContext.
   *
   * <p>Registers a BEFORE intercept with a callback that calls ctx.setExceptionToThrow(). Verifies
   * that the SecurityException is propagated to the caller, preventing method execution.
   */
  @Test
  public void testSetExceptionToThrowWorksInBefore() throws Exception {
    logger.info(
        "===== testSetExceptionToThrowWorksInBefore [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "setExceptionViaContextInBefore";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
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

    // Invoke method - should throw SecurityException from BEFORE callback
    logger.info(
        "Invoking {} via {} - should throw SecurityException from BEFORE callback",
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

    // Verify SecurityException was thrown
    assertNotNull(
        "Expected SecurityException to be thrown by BEFORE callback",
        response.getRaisedThrowable());
    assertThat(
        "Expected SecurityException type",
        response.getRaisedThrowable().getThrowable().getType(),
        is(SecurityException.class.getName()));
    assertThat(
        "Expected exception message to indicate BEFORE intercept via context",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("BEFORE intercept via context"));

    // Verify callback logged that it set the exception
    assertTrue(
        "Expected callback to log exception setting",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "setExceptionViaContextInBefore: exception set successfully"));

    logger.info(
        "===== testSetExceptionToThrowWorksInBefore [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that proceed() throws UnsupportedOperationException in BEFORE intercept.
   *
   * <p>Registers a BEFORE intercept with a callback that attempts to call proceed(). The callback
   * verifies that UnsupportedOperationException is thrown, then returns normally.
   */
  @Test
  public void testProceedThrowsInBefore() throws Exception {
    logger.info(
        "===== testProceedThrowsInBefore [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "attemptProceedInBefore";

    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
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
        "Expected callback to log UnsupportedOperationException",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptProceedInBefore: correctly threw UnsupportedOperationException"));

    logger.info(
        "===== testProceedThrowsInBefore [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
