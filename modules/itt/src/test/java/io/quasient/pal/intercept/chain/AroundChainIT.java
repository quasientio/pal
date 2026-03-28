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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for AROUND intercept chain behavior (onion model).
 *
 * <p>These tests verify the proper chaining of local and remote AROUND intercepts, where:
 *
 * <ul>
 *   <li>Local AROUND intercepts are the outermost layers
 *   <li>Remote AROUND intercepts are inner layers
 *   <li>Method execution is the innermost layer
 *   <li>Each proceed() invokes the next layer, not the method directly
 *   <li>Return values propagate outward through the chain
 *   <li>Skipping proceed() skips the entire inner chain (including remote AROUND)
 * </ul>
 *
 * <p>Chain structure:
 *
 * <pre>
 * Local AROUND-1 BEFORE (outermost)
 *   → Local AROUND-2 BEFORE
 *     → Remote AROUND-1 BEFORE
 *       → [METHOD EXECUTION]
 *     ← Remote AROUND-1 AFTER
 *   ← Local AROUND-2 AFTER
 * ← Local AROUND-1 AFTER
 * </pre>
 */
@RunWith(Parameterized.class)
public class AroundChainIT extends AbstractInterceptIT {

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
  public AroundChainIT(InvocationPath path) {
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
   * Creates a local AROUND intercept request.
   *
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalAroundIntercept(
      String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID, // local: callback peer = interceptable peer
        InterceptType.AROUND,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("getCounter", Collections.emptyList()));
  }

  /**
   * Creates a remote AROUND intercept request.
   *
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createRemoteAroundIntercept(
      String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTOR_PEER_UUID, // remote: callback peer = interceptor peer
        InterceptType.AROUND,
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

  // ==================== Tests ====================

  /**
   * Tests mixed local and remote AROUND chain with return value modification.
   *
   * <p>Chain:
   *
   * <ul>
   *   <li>Local (1) outerDoubler: doubles return value after proceed
   *   <li>Local (2) middleLogger: logs but doesn't modify
   *   <li>Remote (1) innerAdder: adds 10 to return value after proceed
   * </ul>
   *
   * <p>Method getCounter() returns 5.
   *
   * <p>Expected flow:
   *
   * <ol>
   *   <li>outerDoubler BEFORE
   *   <li>middleLogger BEFORE
   *   <li>innerAdder BEFORE (remote)
   *   <li>Method returns 5
   *   <li>innerAdder AFTER: 5 + 10 = 15
   *   <li>middleLogger AFTER: sees 15, returns 15
   *   <li>outerDoubler AFTER: 15 * 2 = 30
   * </ol>
   *
   * <p>Final result: 30
   *
   * @throws Exception if test fails
   */
  @Test
  public void testMixedLocalRemoteAroundChain() throws Exception {
    logger.info("===== testMixedLocalRemoteAroundChain [{}] =====", path);

    // 1. Register AROUND intercepts in order: local-1, local-2, remote-1
    // Registration order determines execution order within each category
    InterceptRequest<?> outerDoubler = createLocalAroundIntercept("outerDoubler");
    InterceptRequest<?> middleLogger = createLocalAroundIntercept("middleLogger");
    InterceptRequest<?> innerAdder = createRemoteAroundIntercept("innerAdder");

    register(outerDoubler);
    register(middleLogger);
    register(innerAdder);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 5
    ObjectRef appInstance = createAppWithCounter(5);

    // 3. Invoke getCounter
    logger.info("Invoking getCounter via {} path", path);
    ExecMessage response = invokeGetCounter(appInstance);

    // 4. Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 5. Verify return value: (5 + 10) * 2 = 30
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be (5 + 10) * 2 = 30 after chain processing", returnValue, is(30));

    // 6. Verify log shows proper execution order
    assertTrue(
        "outerDoubler BEFORE should execute first",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: outerDoubler BEFORE"));
    assertTrue(
        "middleLogger BEFORE should execute second",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: middleLogger BEFORE"));
    assertTrue(
        "innerAdder BEFORE should execute third (remote)",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: innerAdder BEFORE"));
    assertTrue(
        "innerAdder AFTER should show 5 + 10 = 15",
        InterceptEndToEndTestSuite.waitForAppLogLine("innerAdder AFTER.*5 \\+ 10 = 15"));
    assertTrue(
        "middleLogger AFTER should see 15",
        InterceptEndToEndTestSuite.waitForAppLogLine("middleLogger AFTER.*return=15"));
    assertTrue(
        "outerDoubler AFTER should show 15 -> 30",
        InterceptEndToEndTestSuite.waitForAppLogLine("outerDoubler AFTER.*15 -> 30"));

    logger.info("===== testMixedLocalRemoteAroundChain [{}]: PASSED =====", path);
  }

  /**
   * Tests that skipping proceed() in a local AROUND skips the entire inner chain.
   *
   * <p>Chain:
   *
   * <ul>
   *   <li>Local (1) cachingCallback: caches results, skips proceed on cache hit
   *   <li>Remote (1) innerLogger: logs invocations
   * </ul>
   *
   * <p>Test flow:
   *
   * <ol>
   *   <li>First call: cache miss, proceed() called, remote AROUND fires, method executes
   *   <li>Second call: cache hit, proceed() NOT called, remote AROUND does NOT fire
   * </ol>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testAroundChainWithSkip() throws Exception {
    logger.info("===== testAroundChainWithSkip [{}] =====", path);

    // 1. Register AROUND intercepts: local caching + remote logger
    InterceptRequest<?> cachingIntercept = createLocalAroundIntercept("cachingCallback");
    InterceptRequest<?> remoteLogger = createRemoteAroundIntercept("innerLogger");

    register(cachingIntercept);
    register(remoteLogger);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create app with counter = 42
    ObjectRef appInstance = createAppWithCounter(42);

    // 3. First call - cache miss
    logger.info("First call - expecting cache miss");
    ExecMessage response1 = invokeGetCounter(appInstance);

    assertThat(
        "First call should not raise exception", response1.getRaisedThrowable(), is(nullValue()));

    int returnValue1 = (Integer) Unwrapper.unwrapObject(response1.getReturnValue().getObject());
    assertThat("First call should return 42", returnValue1, is(42));

    // Verify cache miss and remote callback invoked
    assertTrue(
        "First call should be cache miss",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: cachingCallback CACHE_MISS"));
    assertTrue(
        "Remote innerLogger should be invoked on cache miss",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: innerLogger BEFORE"));

    // Clear log for second call
    InterceptEndToEndTestSuite.clearAppLog();

    // 4. Second call - cache hit (proceed should be skipped)
    logger.info("Second call - expecting cache hit");
    ExecMessage response2 = invokeGetCounter(appInstance);

    assertThat(
        "Second call should not raise exception", response2.getRaisedThrowable(), is(nullValue()));

    int returnValue2 = (Integer) Unwrapper.unwrapObject(response2.getReturnValue().getObject());
    assertThat("Second call should return cached 42", returnValue2, is(42));

    // Verify cache hit
    assertTrue(
        "Second call should be cache hit",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: cachingCallback CACHE_HIT"));

    // Verify remote callback was NOT invoked (since proceed was skipped)
    // Wait a bit and check that innerLogger was NOT called
    Thread.sleep(500);
    boolean remoteLoggerInvoked =
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN: innerLogger", 1);
    assertThat(
        "Remote innerLogger should NOT be invoked on cache hit (proceed skipped)",
        remoteLoggerInvoked,
        is(false));

    logger.info("===== testAroundChainWithSkip [{}]: PASSED =====", path);
  }

  // ==================== Chain Order Tests ====================

  /**
   * Tests that local and remote AROUND intercepts execute in correct order.
   *
   * <p>Registration order: Local-A, Local-C (both local to verify they run in order)
   *
   * <p>Expected execution order (onion model):
   *
   * <ul>
   *   <li>Both locals execute in registration order: A, then C
   *   <li>Then method executes
   *   <li>AFTER phases unwind in reverse: C, A
   * </ul>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testMixedLocalAndRemoteAroundChainOrder() throws Exception {
    logger.info("===== testMixedLocalAndRemoteAroundChainOrder [{}] =====", path);

    // Register two local intercepts to verify ordering
    InterceptRequest<?> localA = createLocalAroundIntercept("localAroundA");
    InterceptRequest<?> localC = createLocalAroundIntercept("localAroundC");

    register(localA);
    register(localC);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app
    ObjectRef appInstance = createAppWithCounter(5);

    // Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify BEFORE phases execute in registration order: A, then C
    assertTrue(
        "LOCAL_AROUND_A_BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_BEFORE"));
    assertTrue(
        "LOCAL_AROUND_C_BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_C_BEFORE"));

    // Verify AFTER phases execute in reverse order: C, A
    assertTrue(
        "LOCAL_AROUND_C_AFTER should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_C_AFTER"));
    assertTrue(
        "LOCAL_AROUND_A_AFTER should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_AFTER"));

    logger.info("===== testMixedLocalAndRemoteAroundChainOrder [{}]: PASSED =====", path);
  }

  // ==================== Skip in Middle of Chain Tests ====================

  /**
   * Tests that skipping in the middle of the chain prevents inner layers.
   *
   * <p>Chain: Local-A (outer) → Local-Skip (skips) → Local-C (should NOT execute) → Method
   *
   * <p>Expected: Local-A executes, Local-Skip returns cached value, Local-C does NOT execute,
   * Method does NOT execute.
   *
   * <p><b>Fixes Required:</b> This test initially failed due to how {@link
   * io.quasient.pal.core.intercept.AroundInterceptChain.SkipExecutionException} was handled in
   * multi-layer AROUND intercept chains. Two fixes were needed in {@code AroundInterceptChain}:
   *
   * <ol>
   *   <li><b>Unwrap InvocationTargetException</b> ({@code AroundInterceptChain.invokeLocal()}):
   *       Callbacks are invoked via reflection, which wraps exceptions in {@code
   *       InvocationTargetException}. The code needed to unwrap this and check if the cause is a
   *       {@code SkipExecutionException}, then re-throw it to propagate the skip signal through the
   *       chain.
   *   <li><b>Convert skip to normal return in accessor</b> ({@code
   *       AroundInterceptChain.invokeLocal()} accessor lambda): When an inner layer skips, the
   *       {@code proceed()} call should return normally (not throw), allowing the outer callback to
   *       complete its AFTER logic. The accessor now converts {@code SkipExecutionException} into a
   *       normal {@code AfterPhaseData} return with the skip value, rather than re-throwing the
   *       exception.
   * </ol>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testAroundSkipInMiddleOfChain() throws Exception {
    logger.info("===== testAroundSkipInMiddleOfChain [{}] =====", path);

    // Register: Local-A, Local-Skip, Local-C
    InterceptRequest<?> localA = createLocalAroundIntercept("localAroundA");
    InterceptRequest<?> localSkip = createLocalAroundIntercept("alwaysSkipWithCachedValue");
    InterceptRequest<?> localC = createLocalAroundIntercept("localAroundC");

    register(localA);
    register(localSkip);
    register(localC);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app with counter = 42
    ObjectRef appInstance = createAppWithCounter(42);

    // Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify return value is the cached value (999) from alwaysSkipWithCachedValue
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be cached value 999", returnValue, is(999));

    // Verify Local-A executed
    assertTrue(
        "LOCAL_AROUND_A_BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_BEFORE"));
    assertTrue(
        "LOCAL_AROUND_A_AFTER should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_AFTER"));

    // Verify Local-Skip executed and skipped
    assertTrue(
        "alwaysSkipWithCachedValue should log SKIPPING",
        InterceptEndToEndTestSuite.waitForAppLogLine("alwaysSkipWithCachedValue SKIPPING"));

    // Verify Local-C did NOT execute (wait briefly and check)
    Thread.sleep(500);
    boolean localCExecuted =
        InterceptEndToEndTestSuite.waitForAppLogLine("LOCAL_AROUND_C_BEFORE", 1);
    assertThat("Local-C should NOT execute (skip bypassed it)", localCExecuted, is(false));

    logger.info("===== testAroundSkipInMiddleOfChain [{}]: PASSED =====", path);
  }

  /**
   * Tests that skipping in a local intercept prevents downstream remote intercepts.
   *
   * <p>Chain: Local-A (outer) → Local-Skip (skips) → Remote-D (should NOT execute) → Method
   *
   * <p>Expected: Local-A executes, Local-Skip returns cached value, Remote-D does NOT execute,
   * Method does NOT execute.
   *
   * <p>This test verifies that the skip mechanism works correctly across the local/remote boundary,
   * ensuring that when a local intercept skips, it prevents both downstream local intercepts AND
   * remote intercepts from executing.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testAroundSkipInMiddleOfChainWithRemote() throws Exception {
    logger.info("===== testAroundSkipInMiddleOfChainWithRemote [{}] =====", path);

    // Register: Local-A, Local-Skip, Remote-D
    InterceptRequest<?> localA = createLocalAroundIntercept("localAroundA");
    InterceptRequest<?> localSkip = createLocalAroundIntercept("alwaysSkipWithCachedValue");
    InterceptRequest<?> remoteD = createRemoteAroundIntercept("remoteAroundD");

    register(localA);
    register(localSkip);
    register(remoteD);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app with counter = 42
    ObjectRef appInstance = createAppWithCounter(42);

    // Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify return value is the cached value (999) from alwaysSkipWithCachedValue
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be cached value 999", returnValue, is(999));

    // Verify Local-A executed
    assertTrue(
        "LOCAL_AROUND_A_BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_BEFORE"));
    assertTrue(
        "LOCAL_AROUND_A_AFTER should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_AFTER"));

    // Verify Local-Skip executed and skipped
    assertTrue(
        "alwaysSkipWithCachedValue should log SKIPPING",
        InterceptEndToEndTestSuite.waitForAppLogLine("alwaysSkipWithCachedValue SKIPPING"));

    // Verify Remote-D did NOT execute (wait briefly and check)
    Thread.sleep(500);
    boolean remoteDExecuted =
        InterceptEndToEndTestSuite.waitForAppLogLine("REMOTE_AROUND_D_BEFORE", 1);
    assertThat(
        "Remote-D should NOT execute (skip bypassed it across local/remote boundary)",
        remoteDExecuted,
        is(false));

    logger.info("===== testAroundSkipInMiddleOfChainWithRemote [{}]: PASSED =====", path);
  }

  /**
   * Tests that skipping in a remote intercept prevents method execution but not outer locals.
   *
   * <p>Chain: Local-A (outer) → Local-C (middle) → Remote-Skip (inner, skips) → Method
   *
   * <p>Expected: Local-A executes fully, Local-C executes fully, Remote-Skip returns cached value,
   * Method does NOT execute.
   *
   * <p>This test verifies that when a remote intercept skips, it prevents method execution but does
   * NOT prevent outer local intercepts from executing. This is because in the AROUND chain model,
   * ALL local intercepts are outer layers relative to ALL remote intercepts, regardless of
   * registration order. The skip value (888) propagates back through the outer layers.
   *
   * @throws Exception if test fails
   */
  @Test
  public void testAroundSkipInRemoteIntercept() throws Exception {
    logger.info("===== testAroundSkipInRemoteIntercept [{}] =====", path);

    // Register: Local-A, Remote-Skip, Local-C
    // NOTE: Even though Remote-Skip is registered between Local-A and Local-C,
    // the actual chain is: Local-A → Local-C → Remote-Skip → Method
    // because all local intercepts execute before any remote intercepts.
    InterceptRequest<?> localA = createLocalAroundIntercept("localAroundA");
    InterceptRequest<?> remoteSkip = createRemoteAroundIntercept("remoteAlwaysSkip");
    InterceptRequest<?> localC = createLocalAroundIntercept("localAroundC");

    register(localA);
    register(remoteSkip);
    register(localC);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app with counter = 42
    ObjectRef appInstance = createAppWithCounter(42);

    // Invoke getCounter
    ExecMessage response = invokeGetCounter(appInstance);

    // Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify return value is the cached value (888) from remoteAlwaysSkip
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be cached value 888 from remote skip", returnValue, is(888));

    // Verify Local-A executed
    assertTrue(
        "LOCAL_AROUND_A_BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_BEFORE"));
    assertTrue(
        "LOCAL_AROUND_A_AFTER should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_A_AFTER"));

    // Verify Local-C executed (it's an outer layer, so it always executes)
    assertTrue(
        "LOCAL_AROUND_C_BEFORE should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_C_BEFORE"));
    assertTrue(
        "LOCAL_AROUND_C_AFTER should execute",
        InterceptEndToEndTestSuite.waitForAppLogLine("AROUND_CHAIN_ORDER: LOCAL_AROUND_C_AFTER"));

    // Verify Remote-Skip executed and skipped
    assertTrue(
        "remoteAlwaysSkip should log SKIPPING",
        InterceptEndToEndTestSuite.waitForAppLogLine("remoteAlwaysSkip SKIPPING"));

    logger.info("===== testAroundSkipInRemoteIntercept [{}]: PASSED =====", path);
  }

  // ==================== Arg Mutation Tests ====================

  /**
   * Tests that argument mutations propagate through the chain.
   *
   * <p>Chain: Local mutateFirstArgTo10 → Method add(a, b)
   *
   * <p>Expected flow:
   *
   * <ol>
   *   <li>Original call: add(5, 3)
   *   <li>Local mutates arg[0] to 10: add(10, 3)
   *   <li>Method executes: 10 + 3 = 13
   * </ol>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testAroundArgMutationPropagation() throws Exception {
    logger.info("===== testAroundArgMutationPropagation [{}] =====", path);

    // Register arg mutation intercept for add method
    InterceptRequest<?> localMutator = createLocalAroundInterceptForAdd("mutateFirstArgTo10");

    register(localMutator);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app
    ObjectRef appInstance = createAppWithCounter(0);

    // Invoke add(5, 3) - original should be 8, but mutation changes arg[0] to 10
    ExecMessage response = invokeAdd(appInstance, 5, 3);

    // Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify return value: local mutates 5→10, so 10+3=13
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be 10+3=13 after arg mutation", returnValue, is(13));

    // Verify mutation log
    assertTrue(
        "Local should see original arg[0]=5",
        InterceptEndToEndTestSuite.waitForAppLogLine("mutateFirstArgTo10 BEFORE.*arg\\[0\\]=5"));

    logger.info("===== testAroundArgMutationPropagation [{}]: PASSED =====", path);
  }

  // ==================== Return Value Override Tests ====================

  /**
   * Tests that return value modifications propagate outward through the chain.
   *
   * <p>Chain: Local addOneToReturn → Method returnHundred()
   *
   * <p>Expected flow:
   *
   * <ol>
   *   <li>Method returns 100
   *   <li>Local adds 1: 100 + 1 = 101
   * </ol>
   *
   * @throws Exception if test fails
   */
  @Test
  public void testAroundReturnValueOverride() throws Exception {
    logger.info("===== testAroundReturnValueOverride [{}] =====", path);

    // Register return value modification intercept for returnHundred method
    InterceptRequest<?> localAdder = createLocalAroundInterceptForReturnHundred("addOneToReturn");

    register(localAdder);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // Create app
    ObjectRef appInstance = createAppWithCounter(0);

    // Invoke returnHundred()
    ExecMessage response = invokeReturnHundred(appInstance);

    // Verify no exception
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // Verify return value: method returns 100, local +1 = 101
    int returnValue = (Integer) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return should be 100+1=101", returnValue, is(101));

    // Verify modification log
    assertTrue(
        "Local should add 100 + 1 = 101",
        InterceptEndToEndTestSuite.waitForAppLogLine("addOneToReturn AFTER.*100 \\+ 1 = 101"));

    logger.info("===== testAroundReturnValueOverride [{}]: PASSED =====", path);
  }

  // ==================== Helper Methods for New Tests ====================

  /**
   * Creates a local AROUND intercept request for the add method.
   *
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalAroundInterceptForAdd(
      String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID,
        InterceptType.AROUND,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall(
            "add", Arrays.asList("java.lang.Integer", "java.lang.Integer")));
  }

  /**
   * Creates a local AROUND intercept request for the returnHundred method.
   *
   * @param callbackMethod the callback method name
   * @return the intercept request
   */
  private InterceptRequest<InterceptableMethodCall> createLocalAroundInterceptForReturnHundred(
      String callbackMethod) {
    return new InterceptRequest<>(
        UUID.randomUUID(),
        INTERCEPTABLE_PEER_UUID,
        InterceptType.AROUND,
        TARGET_CLASS,
        LOCAL_CALLBACK_CLASS,
        callbackMethod,
        new InterceptableMethodCall("returnHundred", Collections.emptyList()));
  }

  /**
   * Invokes add on the given app instance.
   *
   * @param appInstance the target object
   * @param a first operand
   * @param b second operand
   * @return the response ExecMessage
   */
  private ExecMessage invokeAdd(ObjectRef appInstance, int a, int b) {
    return invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid,
            TARGET_CLASS,
            "add",
            appInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {a, b}));
  }

  /**
   * Invokes returnHundred on the given app instance.
   *
   * @param appInstance the target object
   * @return the response ExecMessage
   */
  private ExecMessage invokeReturnHundred(ObjectRef appInstance) {
    return invoke(
        messageBuilder.buildInstanceMethod(
            myPeerUuid, TARGET_CLASS, "returnHundred", appInstance, null, null));
  }
}
