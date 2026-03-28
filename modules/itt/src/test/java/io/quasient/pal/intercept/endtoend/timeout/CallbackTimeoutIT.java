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
package io.quasient.pal.intercept.endtoend.timeout;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import io.quasient.foobar.apps.callbacks.method.MethodHandlers;
import io.quasient.foobar.apps.quantized.intercept.StringMethods;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Integration tests for callback timeout configuration in the intercept system.
 *
 * <p>These tests verify that per-intercept callback timeout settings ({@code callbackTimeoutMs})
 * interact correctly with the global {@code --callback-timeout-ms} peer configuration. The
 * interceptable peer in this suite uses the default global timeout of 3000ms.
 *
 * <p><b>Timeout semantics:</b>
 *
 * <ul>
 *   <li>{@code null}: Defer to global peer configuration (3000ms default)
 *   <li>{@code 0}: No timeout (infinite wait)
 *   <li>Positive value: Per-intercept override in milliseconds
 * </ul>
 *
 * <p><b>Parameterized:</b> Each test runs through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Invokes wrapper method (e.g., callEcho) - intercept fires at call-site
 *   <li><b>INCOMING_RPC</b>: Invokes target method directly (e.g., echo) - intercept fires in
 *       dispatchIncoming
 * </ul>
 */
@RunWith(Parameterized.class)
public class CallbackTimeoutIT extends AbstractInterceptIT {

  /** Method invocation descriptor for the echo method used in timeout tests. */
  private static final MethodInvocation ECHO = new MethodInvocation("callEcho", "echo");

  /** UUID for the intercept registration. */
  private UUID interceptUuid;

  /** The invocation path for this parameterized test run. */
  private final InvocationPath invocationPath;

  /**
   * Constructs a parameterized test instance.
   *
   * @param invocationPath the invocation path (HOT_PATH or INCOMING_RPC)
   */
  public CallbackTimeoutIT(InvocationPath invocationPath) {
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
   * Tests that a callback responding within the global timeout succeeds.
   *
   * <p>Registers a BEFORE intercept with no per-intercept timeout (null, defers to global 3000ms).
   * The callback (noOp) responds immediately, well within the global timeout. Verifies that the
   * callback was invoked and the method proceeds normally.
   */
  @Test
  public void globalTimeout_callbackRespondsInTime_succeeds() throws Exception {
    logger.info(
        "===== globalTimeout_callbackRespondsInTime_succeeds [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "noOp";
    final String inputValue = "hello";

    // 1. Register a BEFORE intercept with no per-intercept timeout (defers to global)
    logger.info("Creating BEFORE intercept request with null callbackTimeoutMs (defer to global)");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke method via the parameterized path
    logger.info(
        "Invoking {} via {} path - callback should respond within global timeout",
        ECHO.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});
    logger.info("Invocation completed");

    // 4. Verify the return value is unchanged (noOp callback doesn't mutate)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (noOp callback with global timeout)",
        returnValue,
        is(inputValue));

    // Verify callback was invoked
    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations"));

    logger.info(
        "===== globalTimeout_callbackRespondsInTime_succeeds [{}]: TEST COMPLETED SUCCESSFULLY"
            + " =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that a callback with no timeout (callbackTimeoutMs=0) succeeds even with a delay.
   *
   * <p>Registers a BEFORE intercept with {@code callbackTimeoutMs=0L} (no timeout, overriding the
   * global 3000ms). The callback (delayedNoOp) sleeps for 100ms before responding. Verifies that
   * the method still proceeds successfully because no timeout is enforced.
   */
  @Test
  public void noTimeout_callbackTakesLong_succeeds() throws Exception {
    logger.info(
        "===== noTimeout_callbackTakesLong_succeeds [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "delayedNoOp";
    final String inputValue = "hello";

    // 1. Register a BEFORE intercept with callbackTimeoutMs=0 (no timeout)
    logger.info("Creating BEFORE intercept request with callbackTimeoutMs=0 (no timeout)");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")),
            false,
            null,
            null,
            0,
            0,
            0L);

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke method via the parameterized path
    logger.info(
        "Invoking {} via {} path - callback has 100ms delay, no timeout enforced",
        ECHO.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});
    logger.info("Invocation completed");

    // 4. Verify the return value is unchanged (delayedNoOp doesn't mutate)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (delayedNoOp callback with no timeout)",
        returnValue,
        is(inputValue));

    // Verify callback completed after delay
    assertTrue(
        "Expected delayedNoOp callback to log completion",
        InterceptEndToEndTestSuite.waitForAppLogLine("delayedNoOp: completed after delay"));

    logger.info(
        "===== noTimeout_callbackTakesLong_succeeds [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that a per-intercept timeout overrides the global timeout.
   *
   * <p>Registers a BEFORE intercept with {@code callbackTimeoutMs=5000L} (per-intercept override).
   * The callback (noOp) responds immediately, well within the 5000ms per-intercept timeout.
   * Verifies that the callback was invoked and the method proceeds normally.
   */
  @Test
  public void perInterceptTimeout_overridesGlobal() throws Exception {
    logger.info(
        "===== perInterceptTimeout_overridesGlobal [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "noOp";
    final String inputValue = "hello";

    // 1. Register a BEFORE intercept with callbackTimeoutMs=5000 (per-intercept override)
    logger.info(
        "Creating BEFORE intercept request with callbackTimeoutMs=5000 (per-intercept override)");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")),
            false,
            null,
            null,
            0,
            0,
            5000L);

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke method via the parameterized path
    logger.info(
        "Invoking {} via {} path - callback should respond within 5000ms per-intercept timeout",
        ECHO.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});
    logger.info("Invocation completed");

    // 4. Verify the return value is unchanged (noOp callback doesn't mutate)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (noOp callback with per-intercept 5000ms timeout)",
        returnValue,
        is(inputValue));

    // Verify callback was invoked
    assertTrue(
        "Expected noOp callback to log",
        InterceptEndToEndTestSuite.waitForAppLogLine("noOp: no mutations"));

    logger.info(
        "===== perInterceptTimeout_overridesGlobal [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }

  /**
   * Tests that a per-intercept no-timeout setting overrides the global timeout.
   *
   * <p>The global timeout is 3000ms, but this intercept uses {@code callbackTimeoutMs=0L} (no
   * timeout). The callback (delayedNoOp) sleeps for 100ms before responding. Verifies that the
   * method still proceeds successfully because the per-intercept no-timeout overrides the global.
   */
  @Test
  public void perInterceptNoTimeout_overridesGlobal() throws Exception {
    logger.info(
        "===== perInterceptNoTimeout_overridesGlobal [{}]: TEST STARTED =====",
        invocationPath.getDescription());

    final String callbackClass = MethodHandlers.class.getName();
    final String callbackMethod = "delayedNoOp";
    final String inputValue = "hello";

    // 1. Register a BEFORE intercept with callbackTimeoutMs=0 (no timeout, overrides global)
    logger.info(
        "Creating BEFORE intercept request with callbackTimeoutMs=0 (no timeout, overrides"
            + " global 3000ms)");
    interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID,
            InterceptType.BEFORE,
            StringMethods.class.getName(),
            callbackClass,
            callbackMethod,
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")),
            false,
            null,
            null,
            0,
            0,
            0L);

    logger.info("Registering intercept request");
    register(interceptRequest);

    // Wait for intercept registration to propagate
    logger.info(
        "Sleeping {}ms to allow intercept registration", INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);
    logger.info("Intercept registration delay completed");

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, StringMethods.class.getName()))
                .getReturnValue()
                .getObject()
                .getRef());
    logger.info("StringMethods instance created with ref: {}", stringMethodsInstance);

    // 3. Invoke method via the parameterized path
    logger.info(
        "Invoking {} via {} path - callback has 100ms delay, per-intercept no-timeout overrides"
            + " global",
        ECHO.targetMethod(),
        invocationPath);
    ExecMessage response =
        invokeMethod(
            invocationPath,
            StringMethods.class.getName(),
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {inputValue});
    logger.info("Invocation completed");

    // 4. Verify the return value is unchanged (delayedNoOp doesn't mutate)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    logger.info("Return value: {}", returnValue);

    assertThat(
        "Return value should be unchanged (delayedNoOp with per-intercept no-timeout override)",
        returnValue,
        is(inputValue));

    // Verify callback completed after delay
    assertTrue(
        "Expected delayedNoOp callback to log completion",
        InterceptEndToEndTestSuite.waitForAppLogLine("delayedNoOp: completed after delay"));

    logger.info(
        "===== perInterceptNoTimeout_overridesGlobal [{}]: TEST COMPLETED SUCCESSFULLY =====",
        invocationPath.getDescription());
  }
}
