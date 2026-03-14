/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.endtoend.constructor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.foobar.apps.callbacks.constructor.ConstructorHandlers;
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
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for AFTER intercept callbacks on constructors.
 *
 * <p>These tests verify the end-to-end callback mechanism for AFTER intercepts on constructors.
 *
 * <p><b>Note on Return Value Override:</b> Constructor return values (the constructed object) are
 * typically not JSON-serializable (not "wrappable"), so AFTER callbacks on constructors cannot
 * override the return value in the same way that method AFTER callbacks can. These tests verify:
 *
 * <ul>
 *   <li>AFTER callbacks are invoked correctly after constructor execution
 *   <li>AFTER callbacks can throw exceptions that propagate to the caller
 *   <li>No-op AFTER callbacks don't affect the constructed object
 * </ul>
 *
 * <p>Tests use the shared intercept peer with InterceptableApp application class and
 * ConstructorHandlers callback handlers (both in itt-apps module).
 *
 * <p><b>Parameterized:</b> Each test runs through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Invokes factory method (e.g., createWithCounter) → intercept fires at
 *       call-site
 *   <li><b>INCOMING_RPC</b>: Invokes constructor directly → intercept fires in dispatchIncoming
 * </ul>
 */
@RunWith(Parameterized.class)
public class AfterConstructorCallbackIT extends AbstractInterceptIT {

  /** Constructor invocation descriptor for parameterized tests. */
  private static final ConstructorInvocation WITH_COUNTER =
      new ConstructorInvocation("createWithCounter", List.of("java.lang.Integer"));

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** The invocation path for this parameterized test run. */
  private final InvocationPath invocationPath;

  /**
   * Constructs a parameterized test instance.
   *
   * @param invocationPath the invocation path (HOT_PATH or INCOMING_RPC)
   */
  public AfterConstructorCallbackIT(InvocationPath invocationPath) {
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
   * Tests no-op AFTER callback on constructor.
   *
   * <p>Registers an AFTER intercept with a callback that logs but doesn't modify anything. Verifies
   * that the constructor executes normally and the object is created with the correct state.
   */
  @Test
  public void testAfterConstructorNoOpCallback() throws Exception {
    logger.info(
        "===== testAfterConstructorNoOpCallback [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "logConstructedObject";
    final int inputValue = 42;

    // 1. Register an AFTER intercept on parameterized constructor
    logger.info("Creating AFTER intercept request for parameterized constructor");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Callback to interceptor peer
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke constructor via parameterized path (triggers AFTER intercept)
    logger.info(
        "Invoking constructor via {} path with arg {} with AFTER callback",
        invocationPath,
        inputValue);
    ExecMessage response =
        invokeConstructor(
            invocationPath,
            InterceptableApp.class.getName(),
            WITH_COUNTER,
            new Object[] {inputValue});
    logger.info("Constructor invocation completed");

    // 3. Verify no exception was raised
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Get the created instance and verify the counter value
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());
    logger.info("InterceptableApp instance created with ref: {}", appInstance);

    // 5. Call getCounter to verify the constructor executed normally
    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat(
        "Counter should be unchanged (no-op AFTER callback doesn't modify)",
        counterValue,
        is(inputValue));

    // Verify callback logged the constructed object info in application log
    assertTrue(
        "Expected logConstructedObject callback to log isVoid status",
        InterceptEndToEndTestSuite.waitForAppLogLine("logConstructedObject: isVoid=false"));

    logger.info(
        "===== testAfterConstructorNoOpCallback [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests exception propagation via AFTER callback on constructor.
   *
   * <p>Registers an AFTER intercept on InterceptableApp(Integer) constructor that throws a
   * SecurityException after the constructor executes. Verifies that the exception is propagated
   * back to the caller.
   */
  @Test
  public void testAfterConstructorCallbackThrowsException() throws Exception {
    logger.info(
        "===== testAfterConstructorCallbackThrowsException [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "throwExceptionAfter";

    // 1. Register an AFTER intercept on parameterized constructor that throws exception
    logger.info(
        "Creating AFTER intercept request for parameterized constructor with throwing callback");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke constructor via parameterized path - should throw SecurityException
    logger.info(
        "Invoking constructor via {} path which should throw SecurityException from AFTER callback",
        invocationPath);
    try {
      ExecMessage response =
          invokeConstructor(
              invocationPath, InterceptableApp.class.getName(), WITH_COUNTER, new Object[] {10});

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
            "Exception message should mention AFTER constructor callback",
            exceptionMessage,
            containsString("Access denied by AFTER constructor intercept callback"));

        // Verify callback logged that it was throwing an exception
        assertTrue(
            "Expected throwExceptionAfter callback to log",
            InterceptEndToEndTestSuite.waitForAppLogLine(
                "throwExceptionAfter: throwing SecurityException from AFTER constructor callback"));

        logger.info(
            "===== testAfterConstructorCallbackThrowsException [{}]: TEST COMPLETED SUCCESSFULLY =====",
            invocationPath.getDescription());
      } else {
        fail(
            "Expected SecurityException to be thrown by AFTER callback, "
                + "but no exception was raised");
      }
    } catch (Exception e) {
      logger.error("Unexpected exception during test", e);
      throw e;
    }
  }

  /**
   * Tests simple no-op AFTER callback behavior.
   *
   * <p>Registers an AFTER intercept with a simple no-op callback. Verifies that the constructor
   * executes normally.
   */
  @Test
  public void testAfterConstructorSimpleNoOp() throws Exception {
    logger.info(
        "===== testAfterConstructorSimpleNoOp [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = ConstructorHandlers.class.getName();
    final String callbackMethod = "noOp";
    final int inputValue = 123;

    // 1. Register an AFTER intercept on parameterized constructor with simple no-op
    logger.info("Creating AFTER intercept request for parameterized constructor with noOp");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.AFTER,
            InterceptableApp.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("new", Collections.singletonList("java.lang.Integer")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Invoke constructor via parameterized path
    logger.info(
        "Invoking constructor via {} path with arg {} with simple noOp AFTER callback",
        invocationPath,
        inputValue);
    ExecMessage response =
        invokeConstructor(
            invocationPath,
            InterceptableApp.class.getName(),
            WITH_COUNTER,
            new Object[] {inputValue});
    logger.info("Constructor invocation completed");

    // 3. Verify no exception was raised
    assertThat(
        "Invocation should not raise exception", response.getRaisedThrowable(), is(nullValue()));

    // 4. Get the created instance
    ObjectRef appInstance = ObjectRef.from(response.getReturnValue().getObject().getRef());
    logger.info("InterceptableApp instance created with ref: {}", appInstance);

    // 5. Verify the counter value
    ExecMessage getCounterResponse =
        invoke(
            messageBuilder.buildInstanceMethod(
                myPeerUuid,
                InterceptableApp.class.getName(),
                "getCounter",
                appInstance,
                new String[] {},
                new Object[] {}));

    int counterValue =
        (int) Unwrapper.unwrapObject(getCounterResponse.getReturnValue().getObject());
    logger.info("Counter value: {}", counterValue);

    assertThat("Counter should be the input value", counterValue, is(inputValue));

    // Verify callback logged no mutations in application log
    assertTrue(
        "Expected noOp callback to log no mutations",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations"));

    logger.info(
        "===== testAfterConstructorSimpleNoOp [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
