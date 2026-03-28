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
package io.quasient.pal.intercept.endtoend.field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import io.quasient.foobar.apps.callbacks.field.FieldHandlers;
import io.quasient.foobar.apps.quantized.intercept.InterceptableApp;
import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.intercept.AbstractInterceptIT;
import io.quasient.pal.intercept.InvocationPath;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.serdes.Unwrapper;
import java.util.Collection;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for BEFORE_ASYNC field intercept callbacks.
 *
 * <p>These tests verify that BEFORE_ASYNC callbacks on field operations:
 *
 * <ul>
 *   <li>Can read the PUT value but cannot mutate it
 *   <li>Are fire-and-forget (field operation does not wait for callback response)
 *   <li>Throw InterceptApiMisuseException when attempting to mutate arguments
 * </ul>
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and FieldHandlers
 * callback handlers (both in itt-apps module).
 *
 * <p>Tests are parameterized to run through both HOT_PATH and INCOMING_RPC invocation paths.
 */
@RunWith(Parameterized.class)
public class BeforeFieldAsyncCallbackIT extends AbstractInterceptIT {

  /** Field invocation descriptors for parameterized tests. */
  private static final FieldInvocation COUNTER =
      new FieldInvocation("getCounter", "setCounter", "counter", "java.lang.Integer", false);

  private static final FieldInvocation STATIC_COUNTER =
      new FieldInvocation(
          "getStaticCounter", "setStaticCounter", "staticCounter", "java.lang.Integer", true);

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** The invocation path for this parameterized test run. */
  private final InvocationPath invocationPath;

  /**
   * Constructs a parameterized test instance.
   *
   * @param invocationPath the invocation path (HOT_PATH or INCOMING_RPC)
   */
  public BeforeFieldAsyncCallbackIT(InvocationPath invocationPath) {
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
   * Tests that BEFORE_ASYNC callback can read PUT value without mutation on instance field.
   *
   * <p>Registers a BEFORE_ASYNC intercept on instance field PUT that only logs the value. Verifies
   * that the field receives the original value.
   */
  @Test
  public void testInstanceFieldPutAsyncCanReadValue() throws Exception {
    logger.info(
        "===== testInstanceFieldPutAsyncCanReadValue [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "logArgs";
    final int newValue = 100;

    // 1. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register a BEFORE_ASYNC intercept on counter field PUT
    logger.info(
        "Creating BEFORE_ASYNC intercept request for counter PUT [{}]",
        invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request [{}]", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call setCounter to trigger the callback
    logger.info(
        "Invoking setCounter({}) with BEFORE_ASYNC callback [{}]",
        newValue,
        invocationPath.getDescription());
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance, newValue);

    assertThat(
        "setCounter should not raise exception", setResponse.getRaisedThrowable(), is(nullValue()));

    // 4. Verify the value was written correctly (unchanged by ASYNC callback)
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Counter value after set: {} [{}]", counterValue, invocationPath.getDescription());

    assertThat(
        "Counter should have the original value (ASYNC cannot mutate)", counterValue, is(newValue));

    // Verify callback logged the args in application log
    assertTrue(
        "Expected logArgs callback to log the args",
        InterceptEndToEndTestSuite.waitForAppLogLine("logArgs.*BEFORE_ASYNC.*args.*" + newValue));

    logger.info(
        "===== testInstanceFieldPutAsyncCanReadValue [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that BEFORE_ASYNC callback throws InterceptApiMisuseException on mutation attempt.
   *
   * <p>Registers a BEFORE_ASYNC intercept that attempts to mutate the PUT value. Verifies that the
   * field operation still completes with the original value.
   */
  @Test
  public void testInstanceFieldPutAsyncCannotMutate() throws Exception {
    logger.info(
        "===== testInstanceFieldPutAsyncCannotMutate [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "attemptIntArgMutation";
    final int newValue = 50;

    // 1. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register a BEFORE_ASYNC intercept that attempts mutation
    logger.info(
        "Creating BEFORE_ASYNC intercept request with mutation attempt [{}]",
        invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request [{}]", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call setCounter - callback will throw but ASYNC is fire-and-forget
    logger.info(
        "Invoking setCounter({}) - callback should throw but PUT still completes [{}]",
        newValue,
        invocationPath.getDescription());
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance, newValue);

    assertThat(
        "setCounter should not raise exception", setResponse.getRaisedThrowable(), is(nullValue()));

    // 4. Verify original value was written (ASYNC exception doesn't affect operation)
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Counter value after set: {} [{}]", counterValue, invocationPath.getDescription());

    assertThat(
        "Counter should have original value (ASYNC callback exception doesn't stop operation)",
        counterValue,
        is(newValue));

    // Verify callback logged the mutation attempt in application log
    assertTrue(
        "Expected attemptIntArgMutation callback to log mutation attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptIntArgMutation.*BEFORE_ASYNC.*attempting to mutate"));

    logger.info(
        "===== testInstanceFieldPutAsyncCannotMutate [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests BEFORE_ASYNC callback on static field PUT.
   *
   * <p>Verifies that ASYNC callbacks work correctly for static field operations.
   */
  @Test
  public void testStaticFieldPutAsyncNoOp() throws Exception {
    logger.info(
        "===== testStaticFieldPutAsyncNoOp [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "logArgs";
    final int newValue = 300;

    // 1. Register a BEFORE_ASYNC intercept on staticCounter field PUT
    logger.info(
        "Creating BEFORE_ASYNC intercept request for staticCounter PUT [{}]",
        invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request [{}]", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Call setStaticCounter to trigger the callback
    logger.info(
        "Invoking setStaticCounter({}) with BEFORE_ASYNC callback [{}]",
        newValue,
        invocationPath.getDescription());
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), STATIC_COUNTER, null, newValue);

    assertThat(
        "setStaticCounter should not raise exception",
        setResponse.getRaisedThrowable(),
        is(nullValue()));

    // 3. Verify the value was written correctly
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getStaticCounter",
                new String[] {},
                null,
                null,
                new Object[] {}));

    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info(
        "Static counter value after set: {} [{}]", counterValue, invocationPath.getDescription());

    assertThat("Static counter should have the set value", counterValue, is(newValue));

    // Verify callback logged the args in application log
    assertTrue(
        "Expected logArgs callback to log the args",
        InterceptEndToEndTestSuite.waitForAppLogLine("logArgs.*BEFORE_ASYNC.*args.*" + newValue));

    logger.info(
        "===== testStaticFieldPutAsyncNoOp [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that BEFORE_ASYNC callback cannot throw exceptions via setExceptionToThrow.
   *
   * <p>Registers a BEFORE_ASYNC intercept on instance field PUT that attempts to call
   * setExceptionToThrow. Verifies that:
   *
   * <ol>
   *   <li>The callback throws InterceptApiMisuseException when calling setExceptionToThrow
   *   <li>The field operation still executes (ASYNC is fire-and-forget)
   *   <li>The field value is set normally
   * </ol>
   */
  @Test
  public void testAsyncCallbackCannotThrowException() throws Exception {
    logger.info(
        "===== testAsyncCallbackCannotThrowException [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "attemptThrowException";
    final int newValue = 99;

    // 1. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register a BEFORE_ASYNC intercept that attempts to throw exception
    logger.info(
        "Creating BEFORE_ASYNC intercept request with exception throw attempt [{}]",
        invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE_ASYNC,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request [{}]", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call setCounter to trigger the callback
    logger.info(
        "Invoking setCounter({}) - callback should throw but field op should still execute [{}]",
        newValue,
        invocationPath.getDescription());
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance, newValue);

    assertThat(
        "setCounter should not raise exception", setResponse.getRaisedThrowable(), is(nullValue()));

    // 4. Verify field was set (operation ran normally)
    ExecMessage getResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));
    int counterValue = (int) Unwrapper.unwrapObject(getResponse.getReturnValue().getObject());
    logger.info("Counter value after set: {} [{}]", counterValue, invocationPath.getDescription());

    assertThat("Counter should have the new value", counterValue, is(newValue));

    // Verify callback logged the exception throw attempt in application log
    assertTrue(
        "Expected attemptThrowException callback to log exception throw attempt",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "attemptThrowException.*BEFORE_ASYNC.*attempting to set exception"));

    logger.info(
        "===== testAsyncCallbackCannotThrowException [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
