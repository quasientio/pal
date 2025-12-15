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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.quasient.pal.apps.quantized.intercept.InterceptableApp;
import com.quasient.pal.apps.quantized.intercept.callback.LocalInterceptCallbacks;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.intercept.AbstractInterceptIT;
import com.quasient.pal.intercept.InvocationPath;
import com.quasient.pal.messages.colfer.ExecMessage;
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
      "com.quasient.pal.apps.quantized.intercept.callback.LocalInterceptCallbacks";
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
   * Returns the parameterized test data for invocation paths.
   *
   * @return collection of invocation path parameters
   */
  @Parameterized.Parameters(name = "{index}: path={0}")
  public static Collection<Object[]> data() {
    return invocationPathParameters();
  }

  /** Resets callback state before each test. */
  @Before
  public void resetCallbacks() {
    LocalInterceptCallbacks.reset();
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
   * verifies the callback was invoked by checking the counter in LocalInterceptCallbacks.
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

    // 4. Verify local BEFORE callback was invoked
    Thread.sleep(50);
    assertThat(
        "Local BEFORE callback should have been invoked",
        LocalInterceptCallbacks.getBeforeCallCount(),
        is(greaterThan(0)));

    logger.info("Local BEFORE callback count: {}", LocalInterceptCallbacks.getBeforeCallCount());
    logger.info("===== testLocalBeforeConstructorCallback [{}]: TEST COMPLETED =====", path);
  }

  /**
   * Tests that a local AFTER callback is invoked for a constructor.
   *
   * <p>Registers a local AFTER intercept on InterceptableApp(Integer) constructor, invokes it, and
   * verifies the callback was invoked by checking the counter in LocalInterceptCallbacks.
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

    // 4. Verify local AFTER callback was invoked
    Thread.sleep(50);
    assertThat(
        "Local AFTER callback should have been invoked",
        LocalInterceptCallbacks.getAfterCallCount(),
        is(greaterThan(0)));

    logger.info("Local AFTER callback count: {}", LocalInterceptCallbacks.getAfterCallCount());
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

    // 4. Verify both callbacks were invoked
    Thread.sleep(50);
    assertThat(
        "Local BEFORE callback should have been invoked",
        LocalInterceptCallbacks.getBeforeCallCount(),
        is(greaterThan(0)));
    assertThat(
        "Local AFTER callback should have been invoked",
        LocalInterceptCallbacks.getAfterCallCount(),
        is(greaterThan(0)));

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

    // 4. Verify local AROUND callback was invoked
    Thread.sleep(50);
    assertThat(
        "Local AROUND callback should have been invoked",
        LocalInterceptCallbacks.getAroundCallCount(),
        is(greaterThan(0)));

    logger.info("Local AROUND callback count: {}", LocalInterceptCallbacks.getAroundCallCount());
    logger.info("===== testLocalAroundConstructorCallback [{}]: TEST COMPLETED =====", path);
  }
}
