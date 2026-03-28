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
package io.quasient.pal.intercept.chain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import io.quasient.foobar.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.InterceptEndToEndTestSuite;
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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for combining AROUND intercepts with BEFORE and AFTER intercepts.
 *
 * <p>These tests verify the proper interaction between different intercept types when used
 * together:
 *
 * <ul>
 *   <li>BEFORE intercepts execute before AROUND's BEFORE phase
 *   <li>AFTER intercepts execute after AROUND's AFTER phase
 *   <li>Argument mutations in BEFORE are visible to AROUND and the method
 *   <li>Return value modifications in AROUND are visible to AFTER
 *   <li>Exception thrown in BEFORE prevents AROUND and method execution
 *   <li>Exception thrown in AROUND prevents method execution but AFTER still runs
 * </ul>
 *
 * <p>Execution order:
 *
 * <pre>
 * BEFORE callbacks (in registration order)
 *   → AROUND BEFORE phase
 *     → [METHOD EXECUTION]
 *   ← AROUND AFTER phase
 * ← AFTER callbacks (in registration order)
 * </pre>
 */
@RunWith(Parameterized.class)
public class AroundWithBeforeAfterIT extends AbstractInterceptIT {

  /** Fully qualified name of the local callback handler class. */
  private static final String LOCAL_CALLBACK_CLASS =
      "io.quasient.foobar.apps.callbacks.chain.AroundChainCallbacks";

  /** Fully qualified name of the remote callback handler class. */
  private static final String REMOTE_CALLBACK_CLASS =
      "io.quasient.foobar.apps.callbacks.chain.RemoteAroundChainCallbacks";

  /** Target class for interception. */
  private static final String TARGET_CLASS = InterceptableApp.class.getName();

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public AroundWithBeforeAfterIT(InvocationPath path) {
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

    // Reset local callback state via RPC
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid, LOCAL_CALLBACK_CLASS, "reset", null, null, null, null));

    // Reset remote callback state via RPC
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid, REMOTE_CALLBACK_CLASS, "reset", null, null, null, null));
  }

  // ==================== Helper Methods ====================

  /**
   * Creates a local intercept request for getCounter.
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
        new InterceptableMethodCall("getCounter", Collections.emptyList()));
  }

  /**
   * Creates a remote intercept request for getCounter.
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
        new InterceptableMethodCall("getCounter", Collections.emptyList()));
  }

  /**
   * Creates an InterceptableApp instance and sets its counter to a specific value.
   *
   * @param initialValue the initial counter value
   * @return ObjectRef to the created instance
   */
  private ObjectRef createAppWithCounter(int initialValue) {
    // Create instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // Set counter to initial value
    invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            TARGET_CLASS,
            "setCounter",
            appInstance,
            new String[] {"int"},
            new Object[] {initialValue}));

    return appInstance;
  }

  /**
   * Invokes getCounter on the given app instance.
   *
   * @param appInstance the target object
   * @return the response ExecMessage
   */
  private ExecMessage invokeGetCounter(ObjectRef appInstance) {
    return invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid, TARGET_CLASS, "getCounter", appInstance, null, null));
  }

  // ==================== Tests: BEFORE + AROUND ====================

  /**
   * Tests that BEFORE executes before AROUND's BEFORE phase.
   *
   * <p>Chain: Local BEFORE (logger) → Local AROUND (doubler)
   *
   * <p>Expected: BEFORE logs first, AROUND executes, return value is doubled.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testBeforeExecutesBeforeAround() throws Exception {
    logger.info("===== testBeforeExecutesBeforeAround [{}] =====", path);

    // 1. Register BEFORE and AROUND intercepts
    InterceptRequest<?> beforeLogger = createLocalIntercept(InterceptType.BEFORE, "beforeLogger");
    InterceptRequest<?> aroundDoubler = createLocalIntercept(InterceptType.AROUND, "outerDoubler");

    register(beforeLogger);
    register(aroundDoubler);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 10
    ObjectRef appInstance = createAppWithCounter(10);

    // 3. Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // 4. Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify return value: 10 * 2 = 20 (AROUND doubles)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be 10 * 2 = 20 (AROUND doubles)", returnValue, is(20));

    // 6. Verify execution order in logs
    assertTrue(
        "BEFORE logger should execute first",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: beforeLogger executed"));
    assertTrue(
        "AROUND BEFORE should execute second",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: outerDoubler BEFORE"));
    assertTrue(
        "AROUND AFTER should execute third",
        InterceptEndToEndTestSuite.waitForAppLogLine("outerDoubler AFTER"));

    logger.info("===== testBeforeExecutesBeforeAround [{}]: PASSED =====", path);
  }

  // ==================== Tests: AROUND + AFTER ====================

  /**
   * Tests that AFTER executes after AROUND's AFTER phase.
   *
   * <p>Chain: Local AROUND (adder +10) → Local AFTER (logger)
   *
   * <p>Expected: AROUND modifies return value, AFTER sees modified value.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testAfterExecutesAfterAround() throws Exception {
    logger.info("===== testAfterExecutesAfterAround [{}] =====", path);

    // 1. Register AROUND and AFTER intercepts
    InterceptRequest<?> aroundAdder = createRemoteIntercept(InterceptType.AROUND, "innerAdder");
    InterceptRequest<?> afterLogger = createLocalIntercept(InterceptType.AFTER, "afterLogger");

    register(aroundAdder);
    register(afterLogger);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 5
    ObjectRef appInstance = createAppWithCounter(5);

    // 3. Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // 4. Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify return value: 5 + 10 = 15 (AROUND adds 10)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be 5 + 10 = 15 (AROUND adds 10)", returnValue, is(15));

    // 6. Verify AFTER sees the modified value
    assertTrue(
        "AROUND innerAdder BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: innerAdder BEFORE"));
    assertTrue(
        "AROUND innerAdder AFTER should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("innerAdder AFTER"));
    assertTrue(
        "AFTER logger should see return value 15",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: afterLogger.*15"));

    logger.info("===== testAfterExecutesAfterAround [{}]: PASSED =====", path);
  }

  // ==================== Tests: BEFORE + AROUND + AFTER ====================

  /**
   * Tests the complete chain: BEFORE → AROUND → METHOD → AROUND → AFTER.
   *
   * <p>Chain: BEFORE (log) → AROUND (double) → METHOD → AROUND → AFTER (log final)
   *
   * <p>Expected flow:
   *
   * <ol>
   *   <li>BEFORE logs invocation
   *   <li>AROUND BEFORE logs
   *   <li>Method returns 7
   *   <li>AROUND AFTER doubles: 7 * 2 = 14
   *   <li>AFTER logs final value 14
   * </ol>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testFullChainBeforeAroundAfter() throws Exception {
    logger.info("===== testFullChainBeforeAroundAfter [{}] =====", path);

    // 1. Register all three intercept types
    InterceptRequest<?> beforeLogger = createLocalIntercept(InterceptType.BEFORE, "beforeLogger");
    InterceptRequest<?> aroundDoubler = createLocalIntercept(InterceptType.AROUND, "outerDoubler");
    InterceptRequest<?> afterLogger = createLocalIntercept(InterceptType.AFTER, "afterLogger");

    register(beforeLogger);
    register(aroundDoubler);
    register(afterLogger);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 7
    ObjectRef appInstance = createAppWithCounter(7);

    // 3. Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // 4. Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify return value: 7 * 2 = 14 (AROUND doubles)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be 7 * 2 = 14 (AROUND doubles)", returnValue, is(14));

    // 6. Verify execution order
    assertTrue(
        "BEFORE should execute first",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: beforeLogger executed"));
    assertTrue(
        "AROUND BEFORE should execute second",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: outerDoubler BEFORE"));
    assertTrue(
        "AROUND AFTER should double 7 to 14",
        InterceptEndToEndTestSuite.waitForAppLogLine("outerDoubler AFTER.*7 -> 14"));
    assertTrue(
        "AFTER should see final value 14",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: afterLogger.*14"));

    logger.info("===== testFullChainBeforeAroundAfter [{}]: PASSED =====", path);
  }

  /**
   * Tests that multiple BEFORE/AFTER with single AROUND work correctly.
   *
   * <p>Chain: BEFORE_A → BEFORE_B → AROUND → AFTER_A → AFTER_B
   *
   * <p>Expected: BEFORE callbacks execute in order, then AROUND, then AFTER in order.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testMultipleBeforeAfterWithSingleAround() throws Exception {
    logger.info("===== testMultipleBeforeAfterWithSingleAround [{}] =====", path);

    // 1. Register multiple BEFORE, single AROUND, multiple AFTER
    InterceptRequest<?> beforeA = createLocalIntercept(InterceptType.BEFORE, "beforeLoggerA");
    InterceptRequest<?> beforeB = createLocalIntercept(InterceptType.BEFORE, "beforeLoggerB");
    InterceptRequest<?> around = createLocalIntercept(InterceptType.AROUND, "middleLogger");
    InterceptRequest<?> afterA = createLocalIntercept(InterceptType.AFTER, "afterLoggerA");
    InterceptRequest<?> afterB = createLocalIntercept(InterceptType.AFTER, "afterLoggerB");

    register(beforeA);
    register(beforeB);
    register(around);
    register(afterA);
    register(afterB);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 100
    ObjectRef appInstance = createAppWithCounter(100);

    // 3. Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // 4. Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify return value is 100 (middleLogger doesn't modify)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be 100 (middleLogger doesn't modify)", returnValue, is(100));

    // 6. Verify execution order
    assertTrue(
        "BEFORE_A should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: beforeLoggerA"));
    assertTrue(
        "BEFORE_B should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: beforeLoggerB"));
    assertTrue(
        "AROUND middleLogger should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: middleLogger BEFORE"));
    assertTrue(
        "AFTER_A should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: afterLoggerA"));
    assertTrue(
        "AFTER_B should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: afterLoggerB"));

    logger.info("===== testMultipleBeforeAfterWithSingleAround [{}]: PASSED =====", path);
  }

  // ==================== Tests: Mixed Local and Remote ====================

  /**
   * Tests mixed local and remote intercepts across all types.
   *
   * <p>Chain: Local BEFORE → Remote BEFORE → Local AROUND → Remote AROUND → Local AFTER → Remote
   * AFTER
   *
   * <p>This verifies the full interaction between local and remote callbacks.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testMixedLocalRemoteAllTypes() throws Exception {
    logger.info("===== testMixedLocalRemoteAllTypes [{}] =====", path);

    // 1. Register local and remote intercepts of each type
    InterceptRequest<?> localBefore = createLocalIntercept(InterceptType.BEFORE, "beforeLogger");
    InterceptRequest<?> remoteBefore =
        createRemoteIntercept(InterceptType.BEFORE, "remoteBeforeLogger");
    InterceptRequest<?> localAround = createLocalIntercept(InterceptType.AROUND, "outerDoubler");
    InterceptRequest<?> remoteAround = createRemoteIntercept(InterceptType.AROUND, "innerAdder");
    InterceptRequest<?> localAfter = createLocalIntercept(InterceptType.AFTER, "afterLogger");
    InterceptRequest<?> remoteAfter =
        createRemoteIntercept(InterceptType.AFTER, "remoteAfterLogger");

    register(localBefore);
    register(remoteBefore);
    register(localAround);
    register(remoteAround);
    register(localAfter);
    register(remoteAfter);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 3
    ObjectRef appInstance = createAppWithCounter(3);

    // 3. Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // 4. Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify return value: ((3 + 10) * 2) = 26
    // innerAdder adds 10 (remote AROUND), outerDoubler doubles (local AROUND)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be (3 + 10) * 2 = 26", returnValue, is(26));

    // 6. Verify all callbacks executed
    assertTrue(
        "Local BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: beforeLogger"));
    assertTrue(
        "Remote BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: remoteBeforeLogger"));
    assertTrue(
        "Local AROUND should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: outerDoubler"));
    assertTrue(
        "Remote AROUND should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: innerAdder"));
    assertTrue(
        "Local AFTER should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: afterLogger"));
    assertTrue(
        "Remote AFTER should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: remoteAfterLogger"));

    logger.info("===== testMixedLocalRemoteAllTypes [{}]: PASSED =====", path);
  }

  // ==================== Tests: AROUND Skip with BEFORE/AFTER ====================

  /**
   * Tests that BEFORE still executes when AROUND skips execution.
   *
   * <p>Chain: BEFORE (logger) → AROUND (skip with cached value) → AFTER (logger)
   *
   * <p>Expected: BEFORE executes, AROUND skips method (returns 999), AFTER sees 999.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testAroundSkipWithBeforeAfter() throws Exception {
    logger.info("===== testAroundSkipWithBeforeAfter [{}] =====", path);

    // 1. Register BEFORE, AROUND (skip), and AFTER
    InterceptRequest<?> beforeLogger = createLocalIntercept(InterceptType.BEFORE, "beforeLogger");
    InterceptRequest<?> aroundSkip =
        createLocalIntercept(InterceptType.AROUND, "alwaysSkipWithCachedValue");
    InterceptRequest<?> afterLogger = createLocalIntercept(InterceptType.AFTER, "afterLogger");

    register(beforeLogger);
    register(aroundSkip);
    register(afterLogger);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 42 (won't be returned due to skip)
    ObjectRef appInstance = createAppWithCounter(42);

    // 3. Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // 4. Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify return value is 999 (cached value from skip)
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be 999 (cached value from skip)", returnValue, is(999));

    // 6. Verify execution order and that AFTER sees the skipped value
    assertTrue(
        "BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: beforeLogger"));
    assertTrue(
        "AROUND should log SKIPPING",
        InterceptEndToEndTestSuite.waitForAppLogLine("alwaysSkipWithCachedValue SKIPPING"));
    assertTrue(
        "AFTER should see return value 999",
        InterceptEndToEndTestSuite.waitForAppLogLine("BEFORE_AFTER_CHAIN: afterLogger.*999"));

    logger.info("===== testAroundSkipWithBeforeAfter [{}]: PASSED =====", path);
  }
}
