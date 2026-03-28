/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.intercept.order;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.foobar.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for verifying intercept callback execution order.
 *
 * <p>These tests verify the documented execution order from {@code
 * docs/developer/docs/architecture/interception-system.md}:
 *
 * <ul>
 *   <li>Local intercepts execute before remote intercepts within each phase
 *   <li>Registration order is preserved within each category (local/remote)
 *   <li>Mixed registration doesn't affect local-before-remote rule
 * </ul>
 *
 * <p><b>Documented Order:</b>
 *
 * <pre>
 * BEFORE phase:
 *   1. Local BEFORE callbacks (in registration order)
 *   2. Local BEFORE_ASYNC callbacks (fire-and-forget)
 *   3. Remote BEFORE callbacks (in registration order)
 *   4. Remote BEFORE_ASYNC callbacks (fire-and-forget)
 *
 * AROUND phase:
 *   5. Local AROUND callbacks (in registration order)
 *   6. Remote AROUND callbacks (in registration order)
 *
 * [Method Execution]
 *
 * AFTER phase:
 *   7. Local AFTER callbacks (in registration order)
 *   8. Local AFTER_ASYNC callbacks (fire-and-forget)
 *   9. Remote AFTER callbacks (in registration order)
 *   10. Remote AFTER_ASYNC callbacks (fire-and-forget)
 * </pre>
 */
@RunWith(Parameterized.class)
public class InterceptExecutionOrderIT extends AbstractInterceptIT {

  /** Fully qualified name of the local callback handler class. */
  private static final String LOCAL_CALLBACK_CLASS =
      "io.quasient.foobar.apps.callbacks.order.OrderTrackingCallbacks";

  /** Fully qualified name of the remote callback handler class. */
  private static final String REMOTE_CALLBACK_CLASS =
      "io.quasient.foobar.apps.callbacks.order.RemoteOrderCallbacks";

  /** Target class for interception. */
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** Pattern to extract callback IDs from log lines. */
  private static final Pattern CALLBACK_ID_PATTERN =
      Pattern.compile("ORDER_CALLBACK: id=([A-Z_]+),");

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public InterceptExecutionOrderIT(InvocationPath path) {
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

  /**
   * Clears state before each test.
   *
   * @throws Exception if cleanup fails
   */
  @Before
  public void clearStateBeforeTest() throws Exception {
    // Clear the app log
    InterceptEndToEndTestSuite.clearAppLog();

    // Reset OrderTrackingCallbacks state via RPC
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid, LOCAL_CALLBACK_CLASS, "reset", null, null, null, null));
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a local intercept request (callback peer = interceptable peer).
   *
   * @param type the intercept type
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalIntercept(
      InterceptType type, String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID, // local: callback peer = interceptable peer
        type,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("multiplyBy", Collections.singletonList("java.lang.Integer")));
  }

  /**
   * Creates a remote intercept request (callback peer = interceptor peer).
   *
   * @param type the intercept type
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createRemoteIntercept(
      InterceptType type, String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTOR_PEER_UUID, // remote: callback peer = interceptor peer
        type,
        TARGET_CLASS,
        REMOTE_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("multiplyBy", Collections.singletonList("java.lang.Integer")));
  }

  /**
   * Creates an InterceptableApp instance on the interceptable peer.
   *
   * @return ObjectRef to the created instance
   */
  private ObjectRef createInterceptableApp() {
    ExecMessage response = invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    return ObjectRef.from(response.getReturnValue().getObject().getRef());
  }

  /**
   * Invokes multiplyBy on the given app instance.
   *
   * @param appInstance the target object
   * @param multiplier the multiplier argument
   * @return the response ExecMessage
   */
  private ExecMessage invokeMultiplyBy(ObjectRef appInstance, int multiplier) {
    if (path == InvocationPath.HOT_PATH) {
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              TARGET_CLASS,
              "multiplyCounterNTimesBy",
              appInstance,
              new String[] {"java.lang.Integer", "java.lang.Integer"},
              new Object[] {1, multiplier}));
    } else {
      return invoke(
          messageBuilder.buildInstanceMethod(
              myPeerUuid,
              TARGET_CLASS,
              "multiplyBy",
              appInstance,
              new String[] {"java.lang.Integer"},
              new Object[] {multiplier}));
    }
  }

  /**
   * Parses log lines and extracts callback IDs in execution order.
   *
   * @return list of callback IDs in the order they appeared in the log
   * @throws IOException if log file cannot be read
   */
  private List<String> extractOrderFromLog() throws IOException {
    List<String> order = new ArrayList<>();
    Path logPath = InterceptEndToEndTestSuite.getAppLogPath();
    if (Files.exists(logPath)) {
      for (String line : Files.readAllLines(logPath)) {
        Matcher matcher = CALLBACK_ID_PATTERN.matcher(line);
        if (matcher.find()) {
          order.add(matcher.group(1));
        }
      }
    }
    return order;
  }

  /**
   * Waits for expected number of callbacks to appear in log.
   *
   * @param expectedCount expected number of callback entries
   * @param timeoutSeconds maximum wait time in seconds
   * @return true if expected count reached, false on timeout
   */
  private boolean waitForCallbackCount(int expectedCount, int timeoutSeconds) {
    long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
    while (System.currentTimeMillis() < deadline) {
      try {
        List<String> order = extractOrderFromLog();
        if (order.size() >= expectedCount) {
          return true;
        }
        Thread.sleep(100);
      } catch (Exception e) {
        return false;
      }
    }
    return false;
  }

  // ==================== Tests: Local vs Remote Priority ====================

  /**
   * Tests that local BEFORE callbacks execute before remote BEFORE callbacks.
   *
   * <p>Registers a local BEFORE and a remote BEFORE (local registered first). Verifies local
   * executes before remote.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testLocalBeforeExecutesBeforeRemoteBefore() throws Exception {
    logger.info("===== testLocalBeforeExecutesBeforeRemoteBefore [{}] =====", path);

    // 1. Register local BEFORE, then remote BEFORE
    InterceptRequest<?> localBefore = createLocalIntercept(InterceptType.BEFORE, "localBeforeA");
    InterceptRequest<?> remoteBefore = createRemoteIntercept(InterceptType.BEFORE, "remoteBeforeA");

    register(localBefore);
    register(remoteBefore);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Wait for callbacks and verify order
    waitForCallbackCount(2, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 2 callbacks", order.size(), is(2));
    assertThat("First callback should be local", order.get(0), is("LOCAL_BEFORE_A"));
    assertThat("Second callback should be remote", order.get(1), is("REMOTE_BEFORE_A"));
  }

  /**
   * Tests that local AFTER callbacks execute before remote AFTER callbacks.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testLocalAfterExecutesBeforeRemoteAfter() throws Exception {
    logger.info("===== testLocalAfterExecutesBeforeRemoteAfter [{}] =====", path);

    // 1. Register local AFTER and remote AFTER
    InterceptRequest<?> localAfter = createLocalIntercept(InterceptType.AFTER, "localAfterA");
    InterceptRequest<?> remoteAfter = createRemoteIntercept(InterceptType.AFTER, "remoteAfterA");

    register(localAfter);
    register(remoteAfter);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Wait for callbacks and verify order
    waitForCallbackCount(2, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 2 callbacks", order.size(), is(2));
    assertThat("First callback should be local AFTER", order.get(0), is("LOCAL_AFTER_A"));
    assertThat("Second callback should be remote AFTER", order.get(1), is("REMOTE_AFTER_A"));
  }

  // NOTE: testLocalAroundExecutesBeforeRemoteAround is not included because
  // multiple AROUND intercepts have complex chaining behavior where local proceed()
  // triggers remote AROUND. Testing AROUND ordering requires deeper investigation
  // of the AROUND callback chain mechanism.

  // ==================== Tests: Registration Order Doesn't Override Local-First
  // ====================

  /**
   * Tests that even when remote is registered first, local still executes first.
   *
   * <p>This is a critical test: registration order does NOT affect the local-before-remote rule.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testLocalExecutesFirstEvenWhenRemoteRegisteredFirst() throws Exception {
    logger.info("===== testLocalExecutesFirstEvenWhenRemoteRegisteredFirst [{}] =====", path);

    // 1. Register REMOTE first, then local (opposite of natural order)
    InterceptRequest<?> remoteBefore = createRemoteIntercept(InterceptType.BEFORE, "remoteBeforeA");
    InterceptRequest<?> localBefore = createLocalIntercept(InterceptType.BEFORE, "localBeforeA");

    register(remoteBefore); // Remote registered FIRST
    register(localBefore); // Local registered SECOND
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Wait for callbacks and verify local still executed first
    waitForCallbackCount(2, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 2 callbacks", order.size(), is(2));
    assertThat(
        "Local should execute first despite being registered second",
        order.get(0),
        is("LOCAL_BEFORE_A"));
    assertThat(
        "Remote should execute second despite being registered first",
        order.get(1),
        is("REMOTE_BEFORE_A"));
  }

  // ==================== Tests: Registration Order Within Category ====================

  /**
   * Tests that multiple local BEFORE callbacks execute in registration order.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testLocalBeforeRegistrationOrder() throws Exception {
    logger.info("===== testLocalBeforeRegistrationOrder [{}] =====", path);

    // 1. Register three local BEFORE callbacks in order: A, B, C
    InterceptRequest<?> localA = createLocalIntercept(InterceptType.BEFORE, "localBeforeA");
    InterceptRequest<?> localB = createLocalIntercept(InterceptType.BEFORE, "localBeforeB");
    InterceptRequest<?> localC = createLocalIntercept(InterceptType.BEFORE, "localBeforeC");

    register(localA);
    register(localB);
    register(localC);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Wait for callbacks and verify order matches registration order
    waitForCallbackCount(3, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 3 callbacks", order.size(), is(3));
    assertThat("First callback should be A", order.get(0), is("LOCAL_BEFORE_A"));
    assertThat("Second callback should be B", order.get(1), is("LOCAL_BEFORE_B"));
    assertThat("Third callback should be C", order.get(2), is("LOCAL_BEFORE_C"));
  }

  /**
   * Tests that multiple remote BEFORE callbacks execute in registration order.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testRemoteBeforeRegistrationOrder() throws Exception {
    logger.info("===== testRemoteBeforeRegistrationOrder [{}] =====", path);

    // 1. Register two remote BEFORE callbacks in order: A, B
    InterceptRequest<?> remoteA = createRemoteIntercept(InterceptType.BEFORE, "remoteBeforeA");
    InterceptRequest<?> remoteB = createRemoteIntercept(InterceptType.BEFORE, "remoteBeforeB");

    register(remoteA);
    register(remoteB);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Wait for callbacks and verify order matches registration order
    waitForCallbackCount(2, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 2 callbacks", order.size(), is(2));
    assertThat("First callback should be A", order.get(0), is("REMOTE_BEFORE_A"));
    assertThat("Second callback should be B", order.get(1), is("REMOTE_BEFORE_B"));
  }

  // ==================== Tests: Full Phase Ordering ====================

  /**
   * Tests the complete documented execution order with BEFORE and AFTER intercept types.
   *
   * <p>Registers: Local BEFORE, Remote BEFORE, Local AFTER, Remote AFTER. Verifies the full
   * documented order for these types.
   *
   * <p>Note: AROUND intercepts are not included in this test because multiple AROUND intercepts
   * have complex chaining behavior that requires separate investigation.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testFullPhaseOrdering() throws Exception {
    logger.info("===== testFullPhaseOrdering [{}] =====", path);

    // 1. Register BEFORE and AFTER types (registration order shouldn't matter for local-vs-remote)
    InterceptRequest<?> localBefore = createLocalIntercept(InterceptType.BEFORE, "localBeforeA");
    InterceptRequest<?> remoteBefore = createRemoteIntercept(InterceptType.BEFORE, "remoteBeforeA");
    InterceptRequest<?> localAfter = createLocalIntercept(InterceptType.AFTER, "localAfterA");
    InterceptRequest<?> remoteAfter = createRemoteIntercept(InterceptType.AFTER, "remoteAfterA");

    register(localBefore);
    register(remoteBefore);
    register(localAfter);
    register(remoteAfter);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    // 3. Verify invocation succeeded
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Wait for all callbacks and verify full order
    waitForCallbackCount(4, 10);
    List<String> order = extractOrderFromLog();

    // Expected order:
    // 1. LOCAL_BEFORE_A (local BEFORE)
    // 2. REMOTE_BEFORE_A (remote BEFORE)
    // [Method executes]
    // 3. LOCAL_AFTER_A (local AFTER)
    // 4. REMOTE_AFTER_A (remote AFTER)

    assertThat("Should have 4 callbacks", order.size(), is(4));
    assertThat("Position 0: Local BEFORE", order.get(0), is("LOCAL_BEFORE_A"));
    assertThat("Position 1: Remote BEFORE", order.get(1), is("REMOTE_BEFORE_A"));
    assertThat("Position 2: Local AFTER", order.get(2), is("LOCAL_AFTER_A"));
    assertThat("Position 3: Remote AFTER", order.get(3), is("REMOTE_AFTER_A"));
  }

  // ==================== Helper Methods: Priority ====================

  /**
   * Creates a local intercept request with an explicit priority.
   *
   * @param type the intercept type
   * @param callbackMethod the callback method name
   * @param priority the execution priority (lower executes first)
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalInterceptWithPriority(
      InterceptType type, String callbackMethod, int priority) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID,
        type,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("multiplyBy", Collections.singletonList("java.lang.Integer")),
        false,
        null,
        null,
        priority);
  }

  /**
   * Creates a remote intercept request with an explicit priority.
   *
   * @param type the intercept type
   * @param callbackMethod the callback method name
   * @param priority the execution priority (lower executes first)
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createRemoteInterceptWithPriority(
      InterceptType type, String callbackMethod, int priority) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTOR_PEER_UUID,
        type,
        TARGET_CLASS,
        REMOTE_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("multiplyBy", Collections.singletonList("java.lang.Integer")),
        false,
        null,
        null,
        priority);
  }

  // ==================== Tests: Priority-Based Ordering ====================

  /**
   * Tests that local BEFORE callbacks are sorted by ascending priority, overriding registration
   * order.
   *
   * <p>Registers three local BEFORE callbacks with priorities 2, 1, 0 (in that order). Verifies
   * execution order is C(p=0), B(p=1), A(p=2) — ascending priority.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testLocalBeforePriorityOverridesRegistrationOrder() throws Exception {
    logger.info("===== testLocalBeforePriorityOverridesRegistrationOrder [{}] =====", path);

    // Register local BEFORE A(p=2), B(p=1), C(p=0)
    InterceptRequest<?> localA =
        createLocalInterceptWithPriority(InterceptType.BEFORE, "localBeforeA", 2);
    InterceptRequest<?> localB =
        createLocalInterceptWithPriority(InterceptType.BEFORE, "localBeforeB", 1);
    InterceptRequest<?> localC =
        createLocalInterceptWithPriority(InterceptType.BEFORE, "localBeforeC", 0);

    register(localA);
    register(localB);
    register(localC);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify order: C(p=0), B(p=1), A(p=2)
    waitForCallbackCount(3, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 3 callbacks", order.size(), is(3));
    assertThat("First callback should be C (p=0)", order.get(0), is("LOCAL_BEFORE_C"));
    assertThat("Second callback should be B (p=1)", order.get(1), is("LOCAL_BEFORE_B"));
    assertThat("Third callback should be A (p=2)", order.get(2), is("LOCAL_BEFORE_A"));
  }

  /**
   * Tests that remote BEFORE callbacks are sorted by ascending priority, overriding registration
   * order.
   *
   * <p>Registers two remote BEFORE callbacks with priorities 10, 5 (in that order). Verifies
   * execution order is B(p=5), A(p=10) — ascending priority.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testRemoteBeforePriorityOverridesRegistrationOrder() throws Exception {
    logger.info("===== testRemoteBeforePriorityOverridesRegistrationOrder [{}] =====", path);

    // Register remote BEFORE A(p=10), B(p=5)
    InterceptRequest<?> remoteA =
        createRemoteInterceptWithPriority(InterceptType.BEFORE, "remoteBeforeA", 10);
    InterceptRequest<?> remoteB =
        createRemoteInterceptWithPriority(InterceptType.BEFORE, "remoteBeforeB", 5);

    register(remoteA);
    register(remoteB);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify order: B(p=5), A(p=10)
    waitForCallbackCount(2, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 2 callbacks", order.size(), is(2));
    assertThat("First callback should be B (p=5)", order.get(0), is("REMOTE_BEFORE_B"));
    assertThat("Second callback should be A (p=10)", order.get(1), is("REMOTE_BEFORE_A"));
  }

  /**
   * Tests that local AFTER callbacks are sorted by ascending priority.
   *
   * <p>Registers two local AFTER callbacks with priorities 3, 1 (in that order). Verifies execution
   * order is B(p=1), A(p=3) — ascending priority.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testLocalAfterPriorityOrder() throws Exception {
    logger.info("===== testLocalAfterPriorityOrder [{}] =====", path);

    // Register local AFTER A(p=3), B(p=1)
    InterceptRequest<?> localA =
        createLocalInterceptWithPriority(InterceptType.AFTER, "localAfterA", 3);
    InterceptRequest<?> localB =
        createLocalInterceptWithPriority(InterceptType.AFTER, "localAfterB", 1);

    register(localA);
    register(localB);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify order: B(p=1), A(p=3)
    waitForCallbackCount(2, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 2 callbacks", order.size(), is(2));
    assertThat("First callback should be B (p=1)", order.get(0), is("LOCAL_AFTER_B"));
    assertThat("Second callback should be A (p=3)", order.get(1), is("LOCAL_AFTER_A"));
  }

  /**
   * Tests mixed priority values with stable tie-breaking on registration order.
   *
   * <p>Registers three local BEFORE callbacks: A(p=0), B(p=-1), C(p=0). Verifies execution order is
   * B(p=-1), A(p=0), C(p=0) — B first due to lower priority, then A before C due to stable sort
   * preserving registration order among equal priorities.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testMixedPriorityAndDefaultRegistrationOrder() throws Exception {
    logger.info("===== testMixedPriorityAndDefaultRegistrationOrder [{}] =====", path);

    // Register local BEFORE A(p=0), B(p=-1), C(p=0)
    InterceptRequest<?> localA =
        createLocalInterceptWithPriority(InterceptType.BEFORE, "localBeforeA", 0);
    InterceptRequest<?> localB =
        createLocalInterceptWithPriority(InterceptType.BEFORE, "localBeforeB", -1);
    InterceptRequest<?> localC =
        createLocalInterceptWithPriority(InterceptType.BEFORE, "localBeforeC", 0);

    register(localA);
    register(localB);
    register(localC);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify order: B(p=-1), A(p=0), C(p=0)
    waitForCallbackCount(3, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 3 callbacks", order.size(), is(3));
    assertThat("First callback should be B (p=-1)", order.get(0), is("LOCAL_BEFORE_B"));
    assertThat("Second callback should be A (p=0)", order.get(1), is("LOCAL_BEFORE_A"));
    assertThat("Third callback should be C (p=0)", order.get(2), is("LOCAL_BEFORE_C"));
  }

  /**
   * Tests that the local-before-remote invariant is preserved regardless of priority values.
   *
   * <p>Registers a remote BEFORE with very low priority (-100) and a local BEFORE with very high
   * priority (100). Verifies that the local callback still executes before the remote callback,
   * because the local-before-remote invariant takes precedence over priority ordering.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testPriorityDoesNotAffectLocalBeforeRemoteInvariant() throws Exception {
    logger.info("===== testPriorityDoesNotAffectLocalBeforeRemoteInvariant [{}] =====", path);

    // Register remote BEFORE A(p=-100), local BEFORE B(p=100)
    InterceptRequest<?> remoteA =
        createRemoteInterceptWithPriority(InterceptType.BEFORE, "remoteBeforeA", -100);
    InterceptRequest<?> localB =
        createLocalInterceptWithPriority(InterceptType.BEFORE, "localBeforeA", 100);

    register(remoteA);
    register(localB);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create instance and invoke method
    ObjectRef appInstance = createInterceptableApp();
    ExecMessage response = invokeMultiplyBy(appInstance, 3);

    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify local executes before remote despite priority values
    waitForCallbackCount(2, 5);
    List<String> order = extractOrderFromLog();

    assertThat("Should have exactly 2 callbacks", order.size(), is(2));
    assertThat(
        "Local should execute first despite higher priority value",
        order.get(0),
        is("LOCAL_BEFORE_A"));
    assertThat(
        "Remote should execute second despite lower priority value",
        order.get(1),
        is("REMOTE_BEFORE_A"));
  }
}
