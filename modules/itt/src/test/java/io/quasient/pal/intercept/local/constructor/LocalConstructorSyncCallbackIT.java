/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.local.constructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.LocalInterceptTestSuite;
import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
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
 * Integration tests for synchronous local constructor intercept callbacks (BEFORE and AFTER).
 *
 * <p>These tests verify local intercepts on constructors where the callback runs in the same peer
 * as the intercepted constructor. Local intercepts use {@code LocalInterceptCallbackDispatcher}
 * instead of sending RPC messages to a remote peer.
 *
 * <p>Key differences from remote intercepts:
 *
 * <ul>
 *   <li>Callback peer UUID equals interceptable peer UUID
 *   <li>Callback is invoked directly via reflection, no ZMQ message passing
 *   <li>Constructor arguments are direct Java objects (not serialized copies)
 * </ul>
 *
 * <p><b>Constructor Interception Pattern:</b> Constructors are intercepted using method name "new"
 * and the constructor parameter types as the signature.
 */
@RunWith(Parameterized.class)
public class LocalConstructorSyncCallbackIT extends AbstractInterceptIT {

  private static final String CALLBACK_CLASS =
      "io.quasient.pal.apps.callbacks.local.LocalInterceptCallbacks";
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
  public LocalConstructorSyncCallbackIT(InvocationPath path) {
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
    // For local intercepts, callback peer UUID = interceptable peer UUID
    // Constructors use method name "new"
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
   * Tests that a local BEFORE callback is invoked for a constructor.
   *
   * <p>Registers a local BEFORE intercept on InterceptableApp(Integer) constructor, invokes it, and
   * verifies the callback was invoked by checking the application log.
   */
  @Test
  public void testLocalBeforeConstructorCallback() throws Exception {
    logger.info("===== testLocalBeforeConstructorCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local BEFORE intercept on parameterized constructor
    logger.info("Creating local BEFORE intercept request for constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(InterceptType.BEFORE, "java.lang.Integer", "onBefore");

    logger.info("Registering intercept request with callback peer = interceptable peer");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor
    logger.info("Invoking constructor via {} path", path);
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});

    // 3. Verify invocation succeeded
    assertThat(
        "Constructor should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify local BEFORE callback was invoked (via log output)
    assertTrue(
        "Local BEFORE callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*method=(new|<init>)"));

    logger.info("===== testLocalBeforeConstructorCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER callback is invoked for a constructor.
   *
   * <p>Registers a local AFTER intercept on InterceptableApp(Integer) constructor, invokes it, and
   * verifies the callback was invoked by checking the application log.
   */
  @Test
  public void testLocalAfterConstructorCallback() throws Exception {
    logger.info("===== testLocalAfterConstructorCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local AFTER intercept on parameterized constructor
    logger.info("Creating local AFTER intercept request for constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(InterceptType.AFTER, "java.lang.Integer", "onAfter");

    logger.info("Registering intercept request with callback peer = interceptable peer");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor
    logger.info("Invoking constructor via {} path", path);
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});

    // 3. Verify invocation succeeded
    assertThat(
        "Constructor should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify local AFTER callback was invoked (via log output)
    assertTrue(
        "Local AFTER callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER:.*method=(new|<init>)"));

    logger.info("===== testLocalAfterConstructorCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that both local BEFORE and AFTER callbacks are invoked for a constructor.
   *
   * <p>Registers both BEFORE and AFTER intercepts and verifies both are called.
   */
  @Test
  public void testLocalBeforeAndAfterConstructorCallbacks() throws Exception {
    logger.info("===== testLocalBeforeAndAfterConstructorCallbacks [{}]: TEST STARTED =====", path);

    // 1. Register both BEFORE and AFTER intercepts
    InterceptRequest<InterceptableMethodCall> beforeIntercept =
        createLocalConstructorIntercept(InterceptType.BEFORE, "java.lang.Integer", "onBefore");
    InterceptRequest<InterceptableMethodCall> afterIntercept =
        createLocalConstructorIntercept(InterceptType.AFTER, "java.lang.Integer", "onAfter");

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
        "Local BEFORE callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE:.*method=(new|<init>)"));
    assertTrue(
        "Local AFTER callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AFTER:.*method=(new|<init>)"));

    logger.info(
        "===== testLocalBeforeAndAfterConstructorCallbacks [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AROUND callback is invoked for a constructor.
   *
   * <p>Registers a local AROUND intercept that calls proceed(), verifies the constructor is
   * executed and the callback was invoked.
   */
  @Test
  public void testLocalAroundConstructorCallback() throws Exception {
    logger.info("===== testLocalAroundConstructorCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local AROUND intercept on parameterized constructor
    logger.info("Creating local AROUND intercept request for constructor");
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(InterceptType.AROUND, "java.lang.Integer", "onAround");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Invoke constructor
    logger.info("Invoking constructor via {} path", path);
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {42});

    // 3. Verify invocation succeeded
    assertThat(
        "Constructor should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Verify local AROUND callback was invoked (via log output)
    assertTrue(
        "Local AROUND callback should have been invoked",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AROUND:.*"));

    logger.info("===== testLocalAroundConstructorCallback [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Argument Mutation Tests
  // ===========================================================================

  /**
   * Tests that a BEFORE callback can mutate constructor arguments.
   *
   * <p>Registers a BEFORE intercept that doubles the constructor argument, then verifies the object
   * was created with the mutated value.
   */
  @Test
  public void testBeforeArgMutation() throws Exception {
    logger.info("===== testBeforeArgMutation [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that doubles the constructor argument
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.BEFORE, "java.lang.Integer", "onBeforeMutateArg");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp with counter=10 - callback should double to 20
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});
    assertThat(response.getRaisedThrowable(), is(nullValue()));
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());

    // 3. Verify arg was mutated (via log)
    assertTrue(
        "BEFORE callback should have mutated arg 10 -> 20",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_BEFORE_MUTATE_ARG: 10 -> 20"));

    // 4. Verify the counter value is 20 (not 10)
    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid, TARGET_CLASS, "getCounter", appInstance, new String[] {}, null));
    int counterValue =
        (Integer) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    assertThat("Counter should be 20 due to arg mutation", counterValue, is(20));

    logger.info("===== testBeforeArgMutation [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AROUND callback can mutate constructor arguments before proceeding.
   *
   * <p>Registers an AROUND intercept that doubles the argument before calling proceed().
   */
  @Test
  public void testAroundMutateArgBeforeProceed() throws Exception {
    logger.info("===== testAroundMutateArgBeforeProceed [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that doubles arg before proceed
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AROUND, "java.lang.Integer", "onAroundMutateArgBeforeProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp with counter=10 - callback should double to 20
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});
    assertThat(response.getRaisedThrowable(), is(nullValue()));
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());

    // 3. Verify arg was mutated (via log)
    assertTrue(
        "AROUND callback should have mutated arg 10 -> 20",
        LocalInterceptTestSuite.waitForAppLogLine("LOCAL_AROUND_MUTATE_ARG: 10 -> 20"));

    // 4. Verify the counter value is 20 (not 10)
    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid, TARGET_CLASS, "getCounter", appInstance, new String[] {}, null));
    int counterValue =
        (Integer) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    assertThat("Counter should be 20 due to arg mutation", counterValue, is(20));

    logger.info("===== testAroundMutateArgBeforeProceed [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Exception Throwing Tests
  // ===========================================================================

  /**
   * Tests that a BEFORE callback can throw an exception.
   *
   * <p>Registers a BEFORE intercept that throws SecurityException.
   */
  @Test
  public void testBeforeThrowsException() throws Exception {
    logger.info("===== testBeforeThrowsException [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that throws exception
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.BEFORE, "java.lang.Integer", "onBeforeThrowException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp - callback should throw exception
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});

    // 3. Verify exception was raised
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

    // 4. Verify exception was logged
    assertTrue(
        "BEFORE callback should have logged exception",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_BEFORE_THROW_EXCEPTION: setting SecurityException via ctx"));

    logger.info("===== testBeforeThrowsException [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AFTER callback can throw an exception.
   *
   * <p>Registers an AFTER intercept that throws SecurityException.
   */
  @Test
  public void testAfterThrowsException() throws Exception {
    logger.info("===== testAfterThrowsException [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept that throws exception
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AFTER, "java.lang.Integer", "onAfterThrowException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp - constructor runs but AFTER throws exception
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});

    // 3. Verify exception was raised
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

    // 4. Verify exception was logged
    assertTrue(
        "AFTER callback should have logged exception",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_THROW_EXCEPTION: setting SecurityException via ctx"));

    logger.info("===== testAfterThrowsException [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that an AROUND callback can skip proceed and throw an exception.
   *
   * <p>Registers an AROUND intercept that skips proceed and throws SecurityException.
   */
  @Test
  public void testAroundSkipWithException() throws Exception {
    logger.info("===== testAroundSkipWithException [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that skips and throws exception
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AROUND, "java.lang.Integer", "onAroundSkipWithException");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp - callback should skip and throw exception
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});

    // 3. Verify exception was raised
    assertThat(
        "Exception should have been raised", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be SecurityException",
        response.getRaisedThrowable().getThrowable().getType(),
        is(SecurityException.class.getName()));
    assertThat(
        "Exception message should match",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Access denied by AROUND skip"));

    // 4. Verify skip was logged
    assertTrue(
        "AROUND callback should have logged skip with exception",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_SKIP_WITH_EXCEPTION: throwing SecurityException"));

    logger.info("===== testAroundSkipWithException [{}]: TEST COMPLETED =====", path);
  }

  // ===========================================================================
  // Illegal Operation Tests (BEFORE)
  // ===========================================================================

  /** Tests that getReturnValue() throws UnsupportedOperationException in BEFORE callback. */
  @Test
  public void testBeforeGetReturnValueThrowsUnsupported() throws Exception {
    logger.info("===== testBeforeGetReturnValueThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register BEFORE intercept that attempts getReturnValue()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.BEFORE, "java.lang.Integer", "onBeforeAttemptGetReturnValue");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});
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
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.BEFORE, "java.lang.Integer", "onBeforeAttemptProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});
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
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AFTER, "java.lang.Integer", "onAfterAttemptSetArg");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "AFTER callback should have caught UnsupportedOperationException for setArg()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_ILLEGAL_SET_ARG: correctly threw UnsupportedOperationException"));

    logger.info("===== testAfterSetArgThrowsUnsupported [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that proceed() throws UnsupportedOperationException in AFTER callback. */
  @Test
  public void testAfterProceedThrowsUnsupported() throws Exception {
    logger.info("===== testAfterProceedThrowsUnsupported [{}]: TEST STARTED =====", path);

    // 1. Register AFTER intercept that attempts proceed()
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AFTER, "java.lang.Integer", "onAfterAttemptProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught UnsupportedOperationException
    assertTrue(
        "AFTER callback should have caught UnsupportedOperationException for proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AFTER_ILLEGAL_PROCEED: correctly threw UnsupportedOperationException"));

    logger.info("===== testAfterProceedThrowsUnsupported [{}]: TEST COMPLETED =====", path);
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
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AROUND, "java.lang.Integer", "onAroundAttemptGetReturnBeforeProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});
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
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AROUND, "java.lang.Integer", "onAroundAttemptSetArgAfterProceed");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});
    assertThat(response.getRaisedThrowable(), is(nullValue()));

    // 3. Verify the callback correctly caught IllegalStateException
    assertTrue(
        "AROUND callback should have caught IllegalStateException for setArg() after proceed()",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_ILLEGAL_SET_ARG_AFTER_PROCEED: correctly threw IllegalStateException"));

    logger.info(
        "===== testAroundSetArgAfterProceedThrowsIllegalState [{}]: TEST COMPLETED =====", path);
  }

  /** Tests that skipProceed() without setReturnValue() throws IllegalStateException. */
  @Test
  public void testAroundSkipWithoutReturnValueThrowsIllegalState() throws Exception {
    logger.info(
        "===== testAroundSkipWithoutReturnValueThrowsIllegalState [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that attempts skipProceed without setReturnValue
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AROUND, "java.lang.Integer", "onAroundSkipWithoutReturnValue");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp - should fail with IllegalStateException
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});

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

    // 4. Verify the callback logged the attempt
    assertTrue(
        "AROUND callback should have logged skip without return value attempt",
        LocalInterceptTestSuite.waitForAppLogLine(
            "LOCAL_AROUND_SKIP_WITHOUT_RETURN: attempting skipProceed without setReturnValue"));

    logger.info(
        "===== testAroundSkipWithoutReturnValueThrowsIllegalState [{}]: TEST COMPLETED =====",
        path);
  }

  /**
   * Tests that skipProceed() with null return value succeeds for constructors.
   *
   * <p>Unlike methods, constructors can return null when skipped.
   */
  @Test
  public void testAroundSkipWithNullReturnValueSucceeds() throws Exception {
    logger.info("===== testAroundSkipWithNullReturnValueSucceeds [{}]: TEST STARTED =====", path);

    // 1. Register AROUND intercept that skips and returns null
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        createLocalConstructorIntercept(
            InterceptType.AROUND, "java.lang.Integer", "onAroundSkipWithNullReturn");

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp - callback should skip and return null
    ExecMessage response = invokeConstructor(path, TARGET_CLASS, WITH_COUNTER, new Object[] {10});

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
