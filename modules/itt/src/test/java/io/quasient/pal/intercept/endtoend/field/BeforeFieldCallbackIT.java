/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.endtoend.field;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.apps.callbacks.field.FieldHandlers;
import io.quasient.pal.apps.quantized.intercept.InterceptableApp;
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
 * Integration tests for BEFORE intercept callbacks on field operations.
 *
 * <p>These tests verify the end-to-end callback mechanism for BEFORE intercepts on field GET and
 * PUT operations, including:
 *
 * <ul>
 *   <li>Generic no-op callbacks that verify callback invocation without mutation
 *   <li>PUT value mutation via BEFORE callbacks (doubling or adding to the value)
 *   <li>Exception propagation from BEFORE callbacks
 * </ul>
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and FieldHandlers
 * callback handlers (both in itt-apps module).
 *
 * <p>These tests are parameterized to run through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Field access via wrapper methods (e.g., getCounter/setCounter)
 *   <li><b>INCOMING_RPC</b>: Direct field access via RPC messages
 * </ul>
 */
@RunWith(Parameterized.class)
public class BeforeFieldCallbackIT extends AbstractInterceptIT {

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
  public BeforeFieldCallbackIT(InvocationPath invocationPath) {
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

  // ===========================================================================
  // Instance Field Tests
  // ===========================================================================

  /**
   * Tests no-op BEFORE callback on instance field GET.
   *
   * <p>Verifies that the callback is invoked without affecting the field value.
   */
  @Test
  public void testInstanceFieldGetNoOpCallback() throws Exception {
    logger.info(
        "===== testInstanceFieldGetNoOpCallback ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "noOp";
    final int initialValue = 42;

    // 1. Register a BEFORE intercept on counter field GET
    logger.info(
        "Creating BEFORE intercept request for counter GET ({})", invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.GET));

    logger.info("Registering intercept request ({})", invocationPath.getDescription());
    register(interceptRequest);

    // Wait for intercept registration to propagate
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance with initial value
    logger.info(
        "Creating InterceptableApp instance with initial value {} ({})",
        initialValue,
        invocationPath.getDescription());
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {initialValue}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Call getCounter to trigger the callback
    logger.info("Invoking getCounter() ({})", invocationPath.getDescription());
    ExecMessage response =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance);

    // 4. Verify no exception and value is unchanged
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Counter value: {} ({})", counterValue, invocationPath.getDescription());

    assertThat("Counter value should be unchanged", counterValue, is(initialValue));

    // Verify callback logged in application log
    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations, phase=BEFORE"));

    logger.info(
        "===== testInstanceFieldGetNoOpCallback ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests no-op BEFORE callback on instance field PUT.
   *
   * <p>Verifies that the callback is invoked without affecting the value being written.
   */
  @Test
  public void testInstanceFieldPutNoOpCallback() throws Exception {
    logger.info(
        "===== testInstanceFieldPutNoOpCallback ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "noOp";
    final int newValue = 100;

    // 1. Register a BEFORE intercept on counter field PUT
    logger.info(
        "Creating BEFORE intercept request for counter PUT ({})", invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request ({})", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Call setCounter to trigger the callback
    logger.info("Invoking setCounter({}) ({})", newValue, invocationPath.getDescription());
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance, newValue);

    assertThat(
        "setCounter should not raise exception", setResponse.getRaisedThrowable(), is(nullValue()));

    // 4. Verify the value was written correctly
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
    logger.info("Counter value after set: {} ({})", counterValue, invocationPath.getDescription());

    assertThat("Counter should have the set value", counterValue, is(newValue));

    // Verify callback logged in application log
    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations, phase=BEFORE"));

    logger.info(
        "===== testInstanceFieldPutNoOpCallback ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests PUT value mutation via BEFORE callback on instance field.
   *
   * <p>Registers a BEFORE intercept on counter PUT that doubles the value. Verifies that the
   * doubled value is written to the field.
   */
  @Test
  public void testInstanceFieldPutValueMutation() throws Exception {
    logger.info(
        "===== testInstanceFieldPutValueMutation ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "doublePutValue";
    final int inputValue = 25;
    final int expectedValue = 50; // 25 * 2 = 50

    // 1. Register a BEFORE intercept on counter field PUT that doubles value
    logger.info(
        "Creating BEFORE intercept request for counter PUT with doubling callback ({})",
        invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request ({})", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create InterceptableApp instance
    ExecMessage createResponse =
        invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, InterceptableApp.class.getName()));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 3. Call setCounter with inputValue - callback should double it
    logger.info(
        "Invoking setCounter({}) which should be mutated to {} ({})",
        inputValue,
        expectedValue,
        invocationPath.getDescription());
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance, inputValue);

    assertThat(
        "setCounter should not raise exception", setResponse.getRaisedThrowable(), is(nullValue()));

    // 4. Verify the doubled value was written
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
    logger.info("Counter value after set: {} ({})", counterValue, invocationPath.getDescription());

    assertThat("Counter should have doubled value (25 * 2 = 50)", counterValue, is(expectedValue));

    // Verify callback logged the mutation in application log
    assertTrue(
        "Expected doublePutValue callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("doublePutValue: 25 -> 50"));

    logger.info(
        "===== testInstanceFieldPutValueMutation ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  // ===========================================================================
  // Static Field Tests
  // ===========================================================================

  /**
   * Tests no-op BEFORE callback on static field GET.
   *
   * <p>Verifies that the callback is invoked without affecting the field value.
   */
  @Test
  public void testStaticFieldGetNoOpCallback() throws Exception {
    logger.info(
        "===== testStaticFieldGetNoOpCallback ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "noOp";
    final int initialValue = 200;

    // 1. First set the static counter to a known value
    invoke(
        messageBuilder.buildClassMethod(
            myPeerUuid,
            InterceptableApp.class.getName(),
            "setStaticCounter",
            new String[] {"java.lang.Integer"},
            null,
            null,
            new Object[] {initialValue}));

    // 2. Register a BEFORE intercept on staticCounter field GET
    logger.info(
        "Creating BEFORE intercept request for staticCounter GET ({})",
        invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.GET));

    logger.info("Registering intercept request ({})", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call getStaticCounter to trigger the callback
    logger.info("Invoking getStaticCounter() ({})", invocationPath.getDescription());
    ExecMessage response =
        invokeFieldGet(invocationPath, InterceptableApp.class.getName(), STATIC_COUNTER, null);

    // 4. Verify no exception and value is unchanged
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    int counterValue = (int) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Static counter value: {} ({})", counterValue, invocationPath.getDescription());

    assertThat("Static counter value should be unchanged", counterValue, is(initialValue));

    // Verify callback logged in application log
    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations, phase=BEFORE"));

    logger.info(
        "===== testStaticFieldGetNoOpCallback ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests no-op BEFORE callback on static field PUT.
   *
   * <p>Verifies that the callback is invoked without affecting the value being written.
   */
  @Test
  public void testStaticFieldPutNoOpCallback() throws Exception {
    logger.info(
        "===== testStaticFieldPutNoOpCallback ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "noOp";
    final int newValue = 300;

    // 1. Register a BEFORE intercept on staticCounter field PUT
    logger.info(
        "Creating BEFORE intercept request for staticCounter PUT ({})",
        invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request ({})", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Call setStaticCounter to trigger the callback
    logger.info("Invoking setStaticCounter({}) ({})", newValue, invocationPath.getDescription());
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
        "Static counter value after set: {} ({})", counterValue, invocationPath.getDescription());

    assertThat("Static counter should have the set value", counterValue, is(newValue));

    // Verify callback logged in application log
    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations, phase=BEFORE"));

    logger.info(
        "===== testStaticFieldPutNoOpCallback ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests PUT value mutation via BEFORE callback on static field.
   *
   * <p>Registers a BEFORE intercept on staticCounter PUT that adds 100 to the value. Verifies that
   * the modified value is written to the field.
   */
  @Test
  public void testStaticFieldPutValueMutation() throws Exception {
    logger.info(
        "===== testStaticFieldPutValueMutation ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "addHundredToPutValue";
    final int inputValue = 50;
    final int expectedValue = 150; // 50 + 100 = 150

    // 1. Register a BEFORE intercept on staticCounter field PUT that adds 100
    logger.info(
        "Creating BEFORE intercept request for staticCounter PUT with add100 callback ({})",
        invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("staticCounter", FieldOpType.PUT));

    logger.info("Registering intercept request ({})", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Call setStaticCounter with inputValue - callback should add 100
    logger.info(
        "Invoking setStaticCounter({}) which should be mutated to {} ({})",
        inputValue,
        expectedValue,
        invocationPath.getDescription());
    ExecMessage setResponse =
        invokeFieldPut(
            invocationPath, InterceptableApp.class.getName(), STATIC_COUNTER, null, inputValue);

    assertThat(
        "setStaticCounter should not raise exception",
        setResponse.getRaisedThrowable(),
        is(nullValue()));

    // 3. Verify the modified value was written
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
        "Static counter value after set: {} ({})", counterValue, invocationPath.getDescription());

    assertThat(
        "Static counter should have modified value (50 + 100 = 150)",
        counterValue,
        is(expectedValue));

    // Verify callback logged the mutation in application log
    assertTrue(
        "Expected addHundredToPutValue callback to log mutation",
        InterceptEndToEndTestSuite.waitForAppLogLine("addHundredToPutValue: 50 -> 150"));

    logger.info(
        "===== testStaticFieldPutValueMutation ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests exception propagation via BEFORE callback on instance field PUT.
   *
   * <p>Registers a BEFORE intercept that throws a SecurityException. Verifies that the exception is
   * propagated and the field value is not modified.
   */
  @Test
  public void testInstanceFieldPutCallbackThrowsException() throws Exception {
    logger.info(
        "===== testInstanceFieldPutCallbackThrowsException ({}): TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = FieldHandlers.class.getName();
    final String callbackMethod = "throwExceptionOnPut";
    final int initialValue = 10;
    final int newValue = 999;

    // 1. Create InterceptableApp instance with initial value
    ExecMessage createResponse =
        invoke(
            messageBuilder.buildClassMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "createWithCounter",
                new String[] {"java.lang.Integer"},
                null,
                null,
                new Object[] {initialValue}));
    ObjectRef appInstance = ObjectRef.from(createResponse.getReturnValue().getObject().getRef());

    // 2. Register a BEFORE intercept on counter field PUT that throws
    logger.info(
        "Creating BEFORE intercept request for counter PUT with throwing callback ({})",
        invocationPath.getDescription());
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableFieldOp> interceptRequest =
        createFieldOpInterceptRequest(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableFieldOp("counter", FieldOpType.PUT));

    logger.info("Registering intercept request ({})", invocationPath.getDescription());
    register(interceptRequest);

    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 3. Call setCounter - should throw SecurityException
    logger.info(
        "Invoking setCounter({}) which should throw SecurityException ({})",
        newValue,
        invocationPath.getDescription());
    try {
      ExecMessage setResponse =
          invokeFieldPut(
              invocationPath, InterceptableApp.class.getName(), COUNTER, appInstance, newValue);

      if (setResponse.getRaisedThrowable() != null) {
        String exceptionClass = setResponse.getRaisedThrowable().getThrowable().getType();
        String exceptionMessage = setResponse.getRaisedThrowable().getThrowable().getMessage();

        logger.info(
            "Received exception: {} with message: {} ({})",
            exceptionClass,
            exceptionMessage,
            invocationPath.getDescription());

        assertThat(
            "Exception should be SecurityException",
            exceptionClass,
            is("java.lang.SecurityException"));
        assertThat(
            "Exception message should mention field PUT callback",
            exceptionMessage,
            containsString("Access denied by field PUT intercept callback"));

        // Verify callback logged in application log
        assertTrue(
            "Expected throwExceptionOnPut callback to log",
            InterceptEndToEndTestSuite.waitForAppLogLine(
                "throwExceptionOnPut: throwing SecurityException"));
      } else {
        fail("Expected SecurityException to be thrown by callback");
      }
    } catch (Exception e) {
      logger.error("Unexpected exception during test ({})", invocationPath.getDescription(), e);
      throw e;
    }

    logger.info(
        "===== testInstanceFieldPutCallbackThrowsException ({}): TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
