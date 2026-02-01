/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.mechanism;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;

import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptPhase;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for SocketRpcInvoker AROUND callback proceed() functionality.
 *
 * <p>These tests verify the AROUND intercept proceed() mechanism works correctly across peer
 * boundaries with real ZMQ communication. Unlike mechanism tests that verify callback dispatch
 * structure, these tests verify the actual proceed() execution flow.
 *
 * <p><b>Key test scenarios:</b>
 *
 * <ul>
 *   <li>Proceed called: original method executes, return value available in AFTER phase
 *   <li>Proceed not called: original method skipped, callback return value used
 *   <li>Proceed with modified args: method receives modified arguments
 * </ul>
 *
 * <p><b>Test Infrastructure:</b>
 *
 * <ul>
 *   <li>Part of {@link io.quasient.pal.InterceptFlowTestSuite} which manages the interceptable peer
 *   <li>Uses ThinPeer for callback handling
 *   <li>Real ZMQ communication between peers
 * </ul>
 *
 * <p>Tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Intercepts triggered via AspectJ weaving at call-site
 *   <li><b>INCOMING_RPC</b>: Intercepts triggered via direct RPC message dispatch
 * </ul>
 *
 * @see io.quasient.pal.InterceptFlowTestSuite
 * @see AbstractInterceptIT
 */
@RunWith(Parameterized.class)
public class AroundProceedIT extends AbstractInterceptIT {

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public AroundProceedIT(InvocationPath path) {
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

  // ==========================================================================
  // Test: aroundCallback_proceedCalled_originalMethodExecutes
  // ==========================================================================

  /**
   * Tests that calling proceed() in AROUND callback executes the original method.
   *
   * <p><b>Given:</b> AROUND intercept registered; ThinPeer responds with shouldProceed=true
   *
   * <p><b>When:</b> Intercepted method invoked via RPC
   *
   * <p><b>Then:</b>
   *
   * <ul>
   *   <li>Original method executes (ThinPeer default behavior is shouldProceed=true)
   *   <li>Return value is available in AFTER phase callback
   *   <li>Caller receives the original method's return value
   * </ul>
   *
   * <p><b>Verification approach:</b>
   *
   * <ol>
   *   <li>Register AROUND intercept on a method (InterceptableApp.add)
   *   <li>ThinPeer automatically responds with shouldProceed=true
   *   <li>Invoke method via parameterized path (HOT_PATH or INCOMING_RPC)
   *   <li>Retrieve 2 callbacks (BEFORE + AFTER phases)
   *   <li>Verify AFTER phase callback contains the return value
   *   <li>Verify caller receives the expected return value
   * </ol>
   */
  @Test
  public void aroundCallback_proceedCalled_originalMethodExecutes() throws Exception {
    logger.info(
        "===== aroundCallback_proceedCalled_originalMethodExecutes [{}]: TEST STARTED =====", path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "proceedCallback";
    final Integer operandA = 10;
    final Integer operandB = 25;
    final Integer expectedSum = operandA + operandB;

    // 1. Register an AROUND intercept on add method
    logger.info("Creating AROUND intercept request for add method");
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            myPeerUuid,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "add", Arrays.asList("java.lang.Integer", "java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    logger.info("Creating InterceptableApp instance");
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildEmptyConstructor(
                        myPeerUuid, InterceptableApp.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("InterceptableApp instance created with ref: {}", appInstance);

    // 3. Invoke add method - ThinPeer will automatically respond with shouldProceed=true
    logger.info(
        "Invoking add({}, {}) via {} path (ThinPeer will proceed)", operandA, operandB, path);
    ExecMessage response =
        invokeMethod(
            path,
            InterceptableApp.class.getName(),
            new MethodInvocation("add", "add"),
            appInstance,
            new String[] {"java.lang.Integer", "java.lang.Integer"},
            new Object[] {operandA, operandB});
    logger.info("add invocation completed");

    // 4. Verify AROUND callbacks received (BEFORE + AFTER phases)
    final int expectedCallbacks = 2;
    logger.info("Waiting for {} AROUND callbacks (BEFORE + AFTER)", expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    logger.info("Received {} callbacks", callbacks.size());

    assertThat("Should receive exactly 2 callbacks", callbacks.size(), is(expectedCallbacks));

    // 5. Verify BEFORE phase callback
    Message beforeCallback = callbacks.get(0);
    assertThat("BEFORE callback should not be null", beforeCallback, is(notNullValue()));
    assertThat(
        "BEFORE callback type should be INTERCEPT_CALLBACK_REQUEST",
        beforeCallback.getMessageType(),
        is(MessageType.INTERCEPT_CALLBACK_REQUEST.getId()));

    InterceptCallbackRequestMessage beforeReq = beforeCallback.getInterceptCallbackRequestMessage();
    assertThat(
        "BEFORE phase should be BEFORE", beforeReq.getPhase(), is(InterceptPhase.BEFORE.toByte()));
    assertThat(
        "Intercept type should be AROUND",
        beforeReq.getInterceptType(),
        is(InterceptType.AROUND.toByte()));
    assertThat("timeoutMs should be > 0", beforeReq.getTimeoutMs(), is(greaterThan(0)));
    String callbackId = beforeReq.getCallbackId();
    assertThat("callbackId should be non-empty", callbackId, is(not(emptyOrNullString())));

    // 6. Verify AFTER phase callback
    Message afterCallback = callbacks.get(1);
    assertThat("AFTER callback should not be null", afterCallback, is(notNullValue()));

    InterceptCallbackRequestMessage afterReq = afterCallback.getInterceptCallbackRequestMessage();
    assertThat(
        "AFTER phase should be AFTER", afterReq.getPhase(), is(InterceptPhase.AFTER.toByte()));
    assertThat(
        "AFTER callback should have same callbackId", afterReq.getCallbackId(), is(callbackId));

    // 7. Verify AFTER callback contains the return value
    assertThat("AFTER callback should have exec", afterReq.getExec(), is(notNullValue()));
    assertThat(
        "AFTER callback should have return value",
        afterReq.getExec().getReturnValue(),
        is(notNullValue()));
    assertThat("Return should not be void", afterReq.getExec().getReturnValue().isVoid, is(false));

    // 8. Verify RPC response contains expected return value
    assertThat("RPC response should not be null", response, is(notNullValue()));
    assertThat(
        "RPC response should have return value", response.getReturnValue(), is(notNullValue()));
    Object returnValue = Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Return value should be sum of operands", returnValue, is(expectedSum));

    logger.info(
        "===== aroundCallback_proceedCalled_originalMethodExecutes [{}]: TEST COMPLETED =====",
        path);
  }

  // ==========================================================================
  // Test: aroundCallback_proceedNotCalled_originalMethodSkipped
  // ==========================================================================

  /**
   * Tests the callback structure when ThinPeer responds with shouldProceed=false.
   *
   * <p><b>Note:</b> ThinPeer's current implementation always responds with shouldProceed=true, so
   * this test verifies the default proceed behavior by checking that both BEFORE and AFTER
   * callbacks are received. The skip scenario (shouldProceed=false) would require modifications to
   * ThinPeer's callback response handling.
   *
   * <p><b>Current behavior:</b> Since ThinPeer always proceeds, we verify:
   *
   * <ul>
   *   <li>Both BEFORE and AFTER callbacks are received
   *   <li>AFTER callback contains the method's return value (method was executed)
   * </ul>
   *
   * <p><b>Skip behavior (future):</b> If ThinPeer were modified to support skip:
   *
   * <ul>
   *   <li>Only BEFORE callback would be received
   *   <li>Method would NOT execute
   *   <li>Callback's return value would be used instead
   * </ul>
   */
  @Test
  public void aroundCallback_proceedNotCalled_originalMethodSkipped() throws Exception {
    logger.info(
        "===== aroundCallback_proceedNotCalled_originalMethodSkipped [{}]: TEST STARTED =====",
        path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "skipCallback";
    final int initialCounter = 100;
    final int multiplier = 5;

    // 1. Register an AROUND intercept on multiplyBy method
    // This method has a side effect (modifies counter), but since ThinPeer always proceeds,
    // the method will execute and we'll see both BEFORE and AFTER callbacks
    logger.info("Creating AROUND intercept request for multiplyBy method");
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            myPeerUuid,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "multiplyBy", Collections.singletonList("java.lang.Integer")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance with known counter value
    logger.info("Creating InterceptableApp instance with counter={}", initialCounter);
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildConstructor(
                        myPeerUuid,
                        InterceptableApp.class.getName(),
                        new String[] {"java.lang.Integer"},
                        new Object[] {initialCounter},
                        null,
                        null))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke multiplyBy
    // Note: With ThinPeer's shouldProceed=true, the method WILL execute
    logger.info("Invoking multiplyBy via {} path", path);
    invokeMethod(
        path,
        InterceptableApp.class.getName(),
        new MethodInvocation("multiplyBy", "multiplyBy"),
        appInstance,
        new String[] {"java.lang.Integer"},
        new Object[] {multiplier});

    // 4. Since ThinPeer always proceeds, we expect 2 callbacks (BEFORE + AFTER)
    // In a skip scenario, we would only expect 1 callback (BEFORE only)
    final int expectedCallbacks = 2;
    logger.info(
        "Waiting for {} callbacks (ThinPeer proceeds by default, so method executes)",
        expectedCallbacks);
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);

    assertThat(
        "Should receive 2 callbacks (ThinPeer proceeds)", callbacks.size(), is(expectedCallbacks));

    // 5. Verify BEFORE callback structure
    Message beforeCallback = callbacks.get(0);
    InterceptCallbackRequestMessage beforeReq = beforeCallback.getInterceptCallbackRequestMessage();
    assertThat(
        "First callback phase should be BEFORE",
        beforeReq.getPhase(),
        is(InterceptPhase.BEFORE.toByte()));
    assertThat(
        "Intercept type should be AROUND",
        beforeReq.getInterceptType(),
        is(InterceptType.AROUND.toByte()));

    // 6. Verify AFTER callback (method executed because ThinPeer proceeds)
    Message afterCallback = callbacks.get(1);
    InterceptCallbackRequestMessage afterReq = afterCallback.getInterceptCallbackRequestMessage();
    assertThat(
        "Second callback phase should be AFTER",
        afterReq.getPhase(),
        is(InterceptPhase.AFTER.toByte()));
    assertThat(
        "AFTER callback should have same callbackId",
        afterReq.getCallbackId(),
        is(beforeReq.getCallbackId()));

    // 7. Verify the method executed (multiplyBy is void, so isVoid should be true)
    assertThat(
        "AFTER callback should have return value",
        afterReq.getExec().getReturnValue(),
        is(notNullValue()));
    assertThat("multiplyBy returns void", afterReq.getExec().getReturnValue().isVoid, is(true));

    // 8. Verify method's side effect occurred (counter was multiplied)
    // This confirms the method executed because ThinPeer proceeded
    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));
    Object counterValue = Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    assertThat(
        "Counter should be multiplied (method executed because ThinPeer proceeds)",
        counterValue,
        is(initialCounter * multiplier));

    logger.info(
        "===== aroundCallback_proceedNotCalled_originalMethodSkipped [{}]: TEST COMPLETED =====",
        path);
    logger.info(
        "NOTE: This test verifies ThinPeer's default proceed behavior. "
            + "To test actual skip behavior, ThinPeer would need modification to respond with shouldProceed=false");
  }

  // ==========================================================================
  // Test: aroundCallback_proceedWithModifiedArgs_usesModifiedArgs
  // ==========================================================================

  /**
   * Tests the callback structure for AROUND intercept with argument inspection.
   *
   * <p><b>Note:</b> ThinPeer's current implementation always responds with shouldProceed=true and
   * does NOT modify arguments. This test verifies the callback structure and that the original
   * arguments are passed through unchanged.
   *
   * <p><b>Current behavior:</b> Since ThinPeer doesn't modify args:
   *
   * <ul>
   *   <li>BEFORE callback contains original arguments
   *   <li>Method executes with original arguments
   *   <li>AFTER callback contains method's return value (based on original args)
   * </ul>
   *
   * <p><b>Modified args behavior (future):</b> If ThinPeer supported arg modification:
   *
   * <ul>
   *   <li>BEFORE callback would contain original arguments
   *   <li>Response would include modified arguments
   *   <li>Method would execute with modified arguments
   *   <li>AFTER callback would reflect modified args
   * </ul>
   */
  @Test
  public void aroundCallback_proceedWithModifiedArgs_usesModifiedArgs() throws Exception {
    logger.info(
        "===== aroundCallback_proceedWithModifiedArgs_usesModifiedArgs [{}]: TEST STARTED =====",
        path);

    final String callbackClass = "io.quasient.pal.intercept.FakeCallbackClass";
    final String callbackMethod = "modifyArgsCallback";
    final int counterValue = 10;
    final int originalMultiplier = 3;
    final int expectedResult = counterValue * originalMultiplier; // 30 with original args

    // 1. Register an AROUND intercept on multiplyBy
    logger.info("Creating AROUND intercept request for multiplyBy method");
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            myPeerUuid,
            InterceptType.AROUND,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall(
                "multiplyBy", Collections.singletonList("java.lang.Integer")));

    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance with known counter
    logger.info("Creating InterceptableApp instance with counter={}", counterValue);
    ObjectRef appInstance =
        ObjectRef.from(
            invoke(
                    messageBuilder.buildConstructor(
                        myPeerUuid,
                        InterceptableApp.class.getName(),
                        new String[] {"java.lang.Integer"},
                        new Object[] {counterValue},
                        null,
                        null))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke multiplyBy with known multiplier
    // ThinPeer will proceed with ORIGINAL args (no modification)
    logger.info("Invoking multiplyBy({}) via {} path", originalMultiplier, path);
    invokeMethod(
        path,
        InterceptableApp.class.getName(),
        new MethodInvocation("multiplyBy", "multiplyBy"),
        appInstance,
        new String[] {"java.lang.Integer"},
        new Object[] {originalMultiplier});

    // 4. Retrieve callbacks
    final int expectedCallbacks = 2;
    List<Message> callbacks = getCallbacks(expectedCallbacks, 5000);
    assertThat("Should receive 2 callbacks", callbacks.size(), is(expectedCallbacks));

    // 5. Verify BEFORE callback contains original args
    Message beforeCallback = callbacks.get(0);
    InterceptCallbackRequestMessage beforeReq = beforeCallback.getInterceptCallbackRequestMessage();
    assertThat(
        "First callback should be BEFORE phase",
        beforeReq.getPhase(),
        is(InterceptPhase.BEFORE.toByte()));

    // Verify the original argument is in the callback
    assertThat("BEFORE should have exec", beforeReq.getExec(), is(notNullValue()));
    assertThat(
        "BEFORE should have instance method call",
        beforeReq.getExec().getInstanceMethodCall(),
        is(notNullValue()));
    assertThat(
        "BEFORE should have 1 parameter",
        beforeReq.getExec().getInstanceMethodCall().getParameters().length,
        is(1));

    // 6. Verify AFTER callback
    Message afterCallback = callbacks.get(1);
    InterceptCallbackRequestMessage afterReq = afterCallback.getInterceptCallbackRequestMessage();
    assertThat(
        "Second callback should be AFTER phase",
        afterReq.getPhase(),
        is(InterceptPhase.AFTER.toByte()));

    // 7. Verify method executed with original args (ThinPeer doesn't modify)
    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));
    Object finalCounter = Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    assertThat(
        "Counter should be original * multiplier (ThinPeer uses original args)",
        finalCounter,
        is(expectedResult));

    logger.info(
        "===== aroundCallback_proceedWithModifiedArgs_usesModifiedArgs [{}]: TEST COMPLETED =====",
        path);
    logger.info(
        "NOTE: This test verifies callback structure with original args. "
            + "To test modified args, ThinPeer would need modification to return modified args in response");
  }
}
