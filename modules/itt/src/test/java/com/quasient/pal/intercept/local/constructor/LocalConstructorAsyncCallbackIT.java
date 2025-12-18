/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept.local.constructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import com.quasient.pal.LocalInterceptTestSuite;
import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.ExecMessage;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for asynchronous local constructor intercept callbacks (BEFORE_ASYNC and
 * AFTER_ASYNC).
 *
 * <p>These tests verify fire-and-forget async local intercepts on constructors. Async callbacks are
 * submitted to an executor and do not block the constructor execution.
 *
 * <p>Key characteristics of async local intercepts:
 *
 * <ul>
 *   <li>Callback runs asynchronously in a separate thread
 *   <li>Constructor does not wait for callback completion
 *   <li>Cannot mutate arguments or return values (fire-and-forget)
 *   <li>Used for logging, metrics, audit trails
 * </ul>
 */
@RunWith(Parameterized.class)
public class LocalConstructorAsyncCallbackIT extends AbstractInterceptIT {

  private static final String CALLBACK_CLASS =
      "com.quasient.pal.apps.callbacks.local.LocalInterceptCallbacks";
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** Constructor invocation descriptor for parameterized tests. */
  private static final ConstructorInvocation WITH_COUNTER =
      new ConstructorInvocation("createWithCounter", List.of("java.lang.Integer"));

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public LocalConstructorAsyncCallbackIT(InvocationPath path) {
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
   * Creates a local intercept request for a constructor where callback peer = interceptable peer.
   *
   * @param type the intercept type
   * @param paramTypes the constructor parameter types
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalConstructorIntercept(
      InterceptType type, String paramTypes, String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID, // callback peer = interceptable peer
        type,
        TARGET_CLASS,
        CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("new", Collections.singletonList(paramTypes)));
  }

  /**
   * Tests that a local BEFORE_ASYNC callback is invoked for a constructor.
   *
   * <p>Registers a local BEFORE_ASYNC intercept, invokes the constructor, and verifies the async
   * callback was invoked by checking the application log.
   */
  @Test
  public void testLocalBeforeAsyncConstructorCallback() throws Exception {
    logger.info("===== testLocalBeforeAsyncConstructorCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local BEFORE_ASYNC intercept on parameterized constructor
    logger.info("Creating local BEFORE_ASYNC intercept request for constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.BEFORE_ASYNC, "java.lang.Integer", "onBeforeAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor
    logger.info("Invoking constructor via {} path", path);
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});

    // 3. Verify invocation succeeded
    assertThat(
        "Constructor should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify local BEFORE_ASYNC callback was invoked (via log output)
    assertTrue(
        "Local BEFORE_ASYNC callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE_ASYNC:.*method=(new|<init>)"));

    logger.info("===== testLocalBeforeAsyncConstructorCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER_ASYNC callback is invoked for a constructor.
   *
   * <p>Registers a local AFTER_ASYNC intercept, invokes the constructor, and verifies the async
   * callback was invoked by checking the application log.
   */
  @Test
  public void testLocalAfterAsyncConstructorCallback() throws Exception {
    logger.info("===== testLocalAfterAsyncConstructorCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local AFTER_ASYNC intercept on parameterized constructor
    logger.info("Creating local AFTER_ASYNC intercept request for constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AFTER_ASYNC, "java.lang.Integer", "onAfterAsync");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor
    logger.info("Invoking constructor via {} path", path);
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});

    // 3. Verify invocation succeeded
    assertThat(
        "Constructor should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify local AFTER_ASYNC callback was invoked (via log output)
    assertTrue(
        "Local AFTER_ASYNC callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER_ASYNC:.*method=(new|<init>)"));

    logger.info("===== testLocalAfterAsyncConstructorCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that both local BEFORE_ASYNC and AFTER_ASYNC callbacks are invoked for a constructor.
   *
   * <p>Registers both async intercepts and verifies both are called.
   */
  @Test
  public void testLocalBeforeAndAfterAsyncConstructorCallbacks() throws Exception {
    logger.info(
        "===== testLocalBeforeAndAfterAsyncConstructorCallbacks [{}]: TEST STARTED =====", path);

    // 1. Register both BEFORE_ASYNC and AFTER_ASYNC intercepts
    InterceptRequest<InterceptableMethodCall> beforeIntercept =
        createLocalConstructorIntercept(
            InterceptType.BEFORE_ASYNC, "java.lang.Integer", "onBeforeAsync");
    InterceptRequest<InterceptableMethodCall> afterIntercept =
        createLocalConstructorIntercept(
            InterceptType.AFTER_ASYNC, "java.lang.Integer", "onAfterAsync");

    register(beforeIntercept);
    register(afterIntercept);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor
    logger.info("Invoking constructor via {} path", path);
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});

    // 3. Verify invocation succeeded
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify both callbacks were invoked (via log output)
    assertTrue(
        "Local BEFORE_ASYNC callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE_ASYNC:.*method=(new|<init>)"));
    assertTrue(
        "Local AFTER_ASYNC callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER_ASYNC:.*method=(new|<init>)"));

    logger.info(
        "===== testLocalBeforeAndAfterAsyncConstructorCallbacks [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Illegal Operation Tests (BEFORE_ASYNC)
  // ===========================================================================

  /**
   * Tests that setArg() throws UnsupportedOperationException in BEFORE_ASYNC callback.
   *
   * <p>ASYNC callbacks cannot mutate arguments (fire-and-forget semantics).
   */
  @Test
  public void testBeforeAsyncSetArgThrowsUnsupported() throws Exception {
    logger.info("===== testBeforeAsyncSetArgThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE_ASYNC intercept that attempts setArg()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.BEFORE_ASYNC, "java.lang.Integer", "onBeforeAsyncAttemptSetArg");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "BEFORE_ASYNC callback should have caught UnsupportedOperationException for setArg()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_BEFORE_ASYNC_ILLEGAL_SET_ARG: correctly threw UnsupportedOperationException"));

    logger.info("===== testBeforeAsyncSetArgThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Illegal Operation Tests (AFTER_ASYNC)
  // ===========================================================================

  /**
   * Tests that setReturnValue() throws UnsupportedOperationException in AFTER_ASYNC callback.
   *
   * <p>ASYNC callbacks cannot override return values (fire-and-forget semantics).
   */
  @Test
  public void testAfterAsyncSetReturnValueThrowsUnsupported() throws Exception {
    logger.info(
        "===== testAfterAsyncSetReturnValueThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register AFTER_ASYNC intercept that attempts setReturnValue()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AFTER_ASYNC, "java.lang.Integer", "onAfterAsyncAttemptSetReturnValue");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "AFTER_ASYNC callback should have caught UnsupportedOperationException for setReturnValue()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_ASYNC_ILLEGAL_SET_RETURN: correctly threw UnsupportedOperationException"));

    logger.info(
        "===== testAfterAsyncSetReturnValueThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that setExceptionToThrow() throws UnsupportedOperationException in AFTER_ASYNC callback.
   *
   * <p>ASYNC callbacks cannot throw exceptions (fire-and-forget semantics).
   */
  @Test
  public void testAfterAsyncSetExceptionThrowsUnsupported() throws Exception {
    logger.info("===== testAfterAsyncSetExceptionThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register AFTER_ASYNC intercept that attempts setExceptionToThrow()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AFTER_ASYNC, "java.lang.Integer", "onAfterAsyncAttemptSetException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "AFTER_ASYNC callback should have caught UnsupportedOperationException for setExceptionToThrow()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_ASYNC_ILLEGAL_SET_EXCEPTION: correctly threw UnsupportedOperationException"));

    logger.info(
        "===== testAfterAsyncSetExceptionThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }
}
