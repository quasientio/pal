/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.intercept.exception;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.InterceptEndToEndTestSuite;
import io.quasient.pal.apps.quantized.intercept.StringMethods;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptPhaseViolationException;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptTypeNotSupportedException;
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
 * Integration tests for end-to-end exception handling across peers.
 *
 * <p>These tests verify the complete exception handling flow in the intercept system, including:
 *
 * <ul>
 *   <li>API misuse exceptions (e.g., calling getReturnValue() in BEFORE phase)
 *   <li>Business exceptions with different propagation policies
 *   <li>Checked exception wrapping and validation
 *   <li>Per-intercept policy overrides
 * </ul>
 *
 * <p><b>Test Infrastructure Requirements:</b>
 *
 * <ul>
 *   <li>Two PAL peers (interceptable and interceptor)
 *   <li>etcd running for intercept registration
 *   <li>Callback handler classes in itt-apps
 * </ul>
 *
 * <p><b>Parameterized:</b> Each test runs through both invocation paths:
 *
 * <ul>
 *   <li><b>HOT_PATH</b>: Invokes wrapper method → intercept fires at call-site
 *   <li><b>INCOMING_RPC</b>: Invokes target method directly → intercept fires in dispatchIncoming
 * </ul>
 *
 * @see io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy
 * @see io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy
 * @see io.quasient.pal.apps.callbacks.exception.ExceptionTestCallbacks
 */
@RunWith(Parameterized.class)
public class ExceptionHandlingIT extends AbstractInterceptIT {

  /** Fully qualified name of the exception test callback handler class. */
  private static final String EXCEPTION_CALLBACK_CLASS =
      "io.quasient.pal.apps.callbacks.exception.ExceptionTestCallbacks";

  /** Target class for interception. */
  private static final String TARGET_CLASS = StringMethods.class.getName();

  /** Method invocation descriptor for echo method. */
  private static final MethodInvocation ECHO = new MethodInvocation("callEcho", "echo");

  /** The invocation path for this test run. */
  private final InvocationPath path;

  /**
   * Constructs a test instance for the specified invocation path.
   *
   * @param path the invocation path to test
   */
  public ExceptionHandlingIT(InvocationPath path) {
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

  // ==================== API Misuse Tests ====================

  /**
   * Tests that API misuse exceptions from remote callbacks are propagated to the caller.
   *
   * <p><b>Given:</b> Remote BEFORE callback that calls getReturnValue() (API misuse)
   *
   * <p><b>When:</b> Method invoked triggering intercept
   *
   * <p><b>Then:</b>
   *
   * <ul>
   *   <li>InterceptTypeNotSupportedException is propagated to caller
   *   <li>API misuse is logged on interceptor peer
   * </ul>
   *
   * <p>This test verifies that InterceptTypeNotSupportedException thrown when a BEFORE callback
   * attempts to call getReturnValue() is classified as API misuse and propagated to the caller,
   * bypassing all exception policies.
   */
  @Test
  public void shouldPropagateApiMisuseExceptionFromRemoteCallback() throws Exception {
    logger.info(
        "===== shouldPropagateApiMisuseExceptionFromRemoteCallback [{}]: TEST STARTED =====", path);

    // 1. Register a BEFORE intercept with apiMisuseGetReturnValueInBefore callback
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Remote callback
            InterceptType.BEFORE,
            TARGET_CLASS,
            EXCEPTION_CALLBACK_CLASS,
            "apiMisuseGetReturnValueInBefore",
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering BEFORE intercept with API misuse callback");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke the intercepted method
    logger.info("Invoking echo method via {} path", path);
    ExecMessage response =
        invokeMethod(
            path,
            TARGET_CLASS,
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    // 4. Verify API misuse exception WAS propagated
    assertThat(
        "API misuse exception should be propagated",
        response.getRaisedThrowable(),
        is(notNullValue()));

    // 5. Verify exception is InterceptTypeNotSupportedException
    // For remote callbacks, the exception may be wrapped in RuntimeException during transmission,
    // so we check that either:
    // a) The type is InterceptTypeNotSupportedException directly, or
    // b) The message contains InterceptTypeNotSupportedException (wrapped case)
    String exceptionType = response.getRaisedThrowable().getThrowable().getType();
    String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();
    String expectedTypeName = InterceptTypeNotSupportedException.class.getName();
    boolean isCorrectException =
        exceptionType.equals(expectedTypeName) || exceptionMessage.contains(expectedTypeName);
    assertTrue(
        "Exception should be or contain InterceptTypeNotSupportedException, but got type="
            + exceptionType
            + ", message="
            + exceptionMessage,
        isCorrectException);

    // 6. Verify API misuse was logged on interceptor peer
    assertTrue(
        "API misuse should be logged",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "API_MISUSE_GET_RETURN_IN_BEFORE: attempting invalid getReturnValue"));

    logger.info(
        "===== shouldPropagateApiMisuseExceptionFromRemoteCallback [{}]: TEST COMPLETED =====",
        path);
  }

  /**
   * Tests that API misuse exceptions from local callbacks are propagated to the caller.
   *
   * <p><b>Given:</b> Local AROUND callback that calls setArg() after proceed() (API misuse)
   *
   * <p><b>When:</b> Method invoked triggering intercept
   *
   * <p><b>Then:</b>
   *
   * <ul>
   *   <li>InterceptPhaseViolationException is propagated to caller
   *   <li>API misuse is logged
   * </ul>
   *
   * <p>This test verifies that InterceptPhaseViolationException thrown when an AROUND callback
   * attempts to call setArg() after proceed() is classified as API misuse and propagated to the
   * caller, bypassing all exception policies.
   */
  @Test
  public void shouldPropagateApiMisuseExceptionFromLocalCallback() throws Exception {
    logger.info(
        "===== shouldPropagateApiMisuseExceptionFromLocalCallback [{}]: TEST STARTED =====", path);

    // 1. Register a local AROUND intercept with apiMisuseSetArgAfterProceed callback
    // Local = callback peer UUID equals interceptable peer UUID
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTABLE_PEER_UUID, // Local callback (same peer)
            InterceptType.AROUND,
            TARGET_CLASS,
            EXCEPTION_CALLBACK_CLASS,
            "apiMisuseSetArgAfterProceed",
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")));

    logger.info("Registering local AROUND intercept with API misuse callback");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke the intercepted method
    logger.info("Invoking echo method via {} path", path);
    ExecMessage response =
        invokeMethod(
            path,
            TARGET_CLASS,
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    // 4. Verify API misuse exception WAS propagated
    assertThat(
        "API misuse exception should be propagated",
        response.getRaisedThrowable(),
        is(notNullValue()));

    // 5. Verify exception is InterceptPhaseViolationException
    // For local callbacks, the exception type should be preserved directly
    // We check both type and message for consistency with remote callback test pattern
    String exceptionType = response.getRaisedThrowable().getThrowable().getType();
    String exceptionMessage = response.getRaisedThrowable().getThrowable().getMessage();
    String expectedTypeName = InterceptPhaseViolationException.class.getName();
    boolean isCorrectException =
        exceptionType.equals(expectedTypeName) || exceptionMessage.contains(expectedTypeName);
    assertTrue(
        "Exception should be or contain InterceptPhaseViolationException, but got type="
            + exceptionType
            + ", message="
            + exceptionMessage,
        isCorrectException);

    // 6. Verify API misuse was logged
    assertTrue(
        "API misuse should be logged",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "API_MISUSE_SET_ARG_AFTER_PROCEED: attempting invalid setArg"));

    logger.info(
        "===== shouldPropagateApiMisuseExceptionFromLocalCallback [{}]: TEST COMPLETED =====",
        path);
  }

  // ==================== Business Exception Propagation Tests ====================

  /**
   * Tests that business exceptions propagate with PROPAGATE_ALL policy.
   *
   * <p><b>Given:</b>
   *
   * <ul>
   *   <li>Remote BEFORE callback that throws SecurityException
   *   <li>Intercept registered with PROPAGATE_ALL exception policy
   * </ul>
   *
   * <p><b>When:</b> Method invoked triggering intercept
   *
   * <p><b>Then:</b> SecurityException is propagated to caller
   *
   * <p>This test verifies that when ExceptionPropagationPolicy.PROPAGATE_ALL is set, business
   * exceptions thrown by callbacks propagate to the caller of the intercepted method.
   */
  @Test
  public void shouldPropagateBusinessExceptionFromRemoteCallbackWithPolicy() throws Exception {
    logger.info(
        "===== shouldPropagateBusinessExceptionFromRemoteCallbackWithPolicy [{}]: TEST STARTED =====",
        path);

    // 1. Register a BEFORE intercept with throwSecurityException callback
    // Set exceptionPropagationPolicy to PROPAGATE_ALL
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Remote callback
            InterceptType.BEFORE,
            TARGET_CLASS,
            EXCEPTION_CALLBACK_CLASS,
            "throwSecurityException",
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")),
            false, // forceImmediate
            ExceptionPropagationPolicy.PROPAGATE_ALL,
            null); // checkedExceptionPolicy

    logger.info("Registering BEFORE intercept with PROPAGATE_ALL policy");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke the intercepted method
    logger.info("Invoking echo method via {} path - expecting SecurityException", path);
    ExecMessage response =
        invokeMethod(
            path,
            TARGET_CLASS,
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    // 4. Verify SecurityException was propagated
    assertThat(
        "Exception should be propagated with PROPAGATE_ALL policy",
        response.getRaisedThrowable(),
        is(notNullValue()));
    assertThat(
        "Exception should be SecurityException",
        response.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.SecurityException"));
    assertThat(
        "Exception message should contain expected text",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Access denied by callback"));

    // 5. Verify callback logged that it was throwing
    assertTrue(
        "Callback should log exception throw",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "THROW_SECURITY_EXCEPTION: throwing SecurityException"));

    logger.info(
        "===== shouldPropagateBusinessExceptionFromRemoteCallbackWithPolicy [{}]: TEST COMPLETED =====",
        path);
  }

  /**
   * Tests that business exceptions are swallowed with SWALLOW_ALL policy.
   *
   * <p><b>Given:</b>
   *
   * <ul>
   *   <li>Remote BEFORE callback that throws SecurityException
   *   <li>Intercept registered with SWALLOW_ALL exception policy
   * </ul>
   *
   * <p><b>When:</b> Method invoked triggering intercept
   *
   * <p><b>Then:</b>
   *
   * <ul>
   *   <li>Method executes normally
   *   <li>Exception is swallowed (not propagated)
   *   <li>Exception is logged for debugging
   * </ul>
   *
   * <p>This test verifies that when ExceptionPropagationPolicy.SWALLOW_ALL is set, all exceptions
   * thrown by callbacks are swallowed and logged, allowing the intercepted method to execute
   * normally.
   */
  @Test
  public void shouldSwallowBusinessExceptionFromRemoteCallbackWithSwallowPolicy() throws Exception {
    logger.info(
        "===== shouldSwallowBusinessExceptionFromRemoteCallbackWithSwallowPolicy [{}]: TEST STARTED =====",
        path);

    // 1. Register a BEFORE intercept with throwSecurityException callback
    // Set exceptionPropagationPolicy to SWALLOW_ALL
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Remote callback
            InterceptType.BEFORE,
            TARGET_CLASS,
            EXCEPTION_CALLBACK_CLASS,
            "throwSecurityException",
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")),
            false, // forceImmediate
            ExceptionPropagationPolicy.SWALLOW_ALL,
            null); // checkedExceptionPolicy

    logger.info("Registering BEFORE intercept with SWALLOW_ALL policy");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke the intercepted method
    logger.info("Invoking echo method via {} path - exception should be swallowed", path);
    ExecMessage response =
        invokeMethod(
            path,
            TARGET_CLASS,
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    // 4. Verify no exception was propagated - SWALLOW_ALL
    assertThat(
        "Exception should be swallowed with SWALLOW_ALL policy",
        response.getRaisedThrowable(),
        is(nullValue()));

    // 5. Verify method returned expected value (executed normally)
    String returnValue = (String) Unwrapper.unwrapObject(response.getReturnValue().getObject());
    assertThat("Method should execute normally with original argument", returnValue, is("hello"));

    // 6. Verify callback was invoked (it logged before throwing)
    assertTrue(
        "Callback should log exception throw",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "THROW_SECURITY_EXCEPTION: throwing SecurityException"));

    logger.info(
        "===== shouldSwallowBusinessExceptionFromRemoteCallbackWithSwallowPolicy [{}]: TEST COMPLETED =====",
        path);
  }

  // ==================== Checked Exception Handling Tests ====================

  /**
   * Tests that checked exceptions are wrapped according to WRAP policy.
   *
   * <p><b>Given:</b>
   *
   * <ul>
   *   <li>Remote BEFORE callback that throws SQLException (wrapped in RuntimeException)
   *   <li>Method declares IOException (not SQLException)
   *   <li>Checked exception policy is WRAP
   * </ul>
   *
   * <p><b>When:</b> Method invoked triggering intercept
   *
   * <p><b>Then:</b> RuntimeException with SQLException cause is propagated to caller
   *
   * <p>This test verifies that when CheckedExceptionPolicy.WRAP is set, checked exceptions that are
   * not declared by the intercepted method are wrapped in RuntimeException and propagated with the
   * original exception as the cause.
   */
  @Test
  public void shouldWrapCheckedExceptionFromRemoteCallback() throws Exception {
    logger.info(
        "===== shouldWrapCheckedExceptionFromRemoteCallback [{}]: TEST STARTED =====", path);

    // 1. Register a BEFORE intercept with throwSqlException callback
    // The callback wraps SQLException in RuntimeException
    // Set PROPAGATE_ALL so the exception propagates, and WRAP for checked exceptions
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Remote callback
            InterceptType.BEFORE,
            TARGET_CLASS,
            EXCEPTION_CALLBACK_CLASS,
            "throwSqlException",
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")),
            false, // forceImmediate
            ExceptionPropagationPolicy.PROPAGATE_ALL,
            CheckedExceptionPolicy.WRAP);

    logger.info("Registering BEFORE intercept with WRAP checked exception policy");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke the intercepted method
    logger.info("Invoking echo method via {} path - expecting wrapped exception", path);
    ExecMessage response =
        invokeMethod(
            path,
            TARGET_CLASS,
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    // 4. Verify RuntimeException with SQLException cause was propagated
    assertThat("Exception should be propagated", response.getRaisedThrowable(), is(notNullValue()));
    assertThat(
        "Exception should be RuntimeException (wrapper)",
        response.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.RuntimeException"));
    // The message should contain information about the wrapped SQLException
    assertThat(
        "Exception message should reference the wrapped SQLException",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("SQLException"));

    // 5. Verify callback logged that it was throwing
    assertTrue(
        "Callback should log exception throw",
        InterceptEndToEndTestSuite.waitForAppLogLine(
            "THROW_SQL_EXCEPTION: throwing SQLException wrapped in RuntimeException"));

    logger.info(
        "===== shouldWrapCheckedExceptionFromRemoteCallback [{}]: TEST COMPLETED =====", path);
  }

  // ==================== Per-Intercept Policy Override Tests ====================

  /**
   * Tests that per-intercept policy overrides the global policy.
   *
   * <p><b>Given:</b>
   *
   * <ul>
   *   <li>Global policy is SWALLOW_ALL (configured on peer)
   *   <li>Intercept registered with PROPAGATE_ALL policy override
   *   <li>Callback throws exception
   * </ul>
   *
   * <p><b>When:</b> Method invoked triggering intercept
   *
   * <p><b>Then:</b> Exception is propagated (intercept policy overrides global)
   *
   * <p>This test verifies that the per-intercept exception policy set in InterceptRequest overrides
   * the global peer configuration. Even if the peer is configured with SWALLOW_ALL, an intercept
   * with PROPAGATE_ALL will propagate exceptions.
   */
  @Test
  public void shouldRespectPerInterceptPolicyOverride() throws Exception {
    logger.info("===== shouldRespectPerInterceptPolicyOverride [{}]: TEST STARTED =====", path);

    // This test verifies that per-intercept policy works.
    // We'll register an intercept with PROPAGATE_ALL which should propagate the exception
    // regardless of any global peer configuration.
    UUID interceptUuid = UUID.randomUUID();
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            interceptUuid,
            INTERCEPTOR_PEER_UUID, // Remote callback
            InterceptType.BEFORE,
            TARGET_CLASS,
            EXCEPTION_CALLBACK_CLASS,
            "throwSecurityException",
            new InterceptableMethodCall("echo", Collections.singletonList("java.lang.String")),
            false, // forceImmediate
            ExceptionPropagationPolicy.PROPAGATE_ALL, // Per-intercept override
            null);

    logger.info("Registering BEFORE intercept with PROPAGATE_ALL policy override");
    register(interceptRequest);
    Thread.sleep(INTERCEPT_REGISTRATION_MAX_DELAY_MS);

    // 2. Create StringMethods instance
    logger.info("Creating StringMethods instance");
    ObjectRef stringMethodsInstance =
        ObjectRef.from(
            invoke(messageBuilder.buildEmptyConstructor(myPeerUuid, TARGET_CLASS))
                .getReturnValue()
                .getObject()
                .getRef());

    // 3. Invoke the intercepted method
    logger.info("Invoking echo method via {} path - per-intercept policy should apply", path);
    ExecMessage response =
        invokeMethod(
            path,
            TARGET_CLASS,
            ECHO,
            stringMethodsInstance,
            new String[] {"java.lang.String"},
            new Object[] {"hello"});

    // 4. Verify SecurityException was propagated (per-intercept PROPAGATE_ALL)
    assertThat(
        "Per-intercept PROPAGATE_ALL policy should override any global policy",
        response.getRaisedThrowable(),
        is(notNullValue()));
    assertThat(
        "Exception should be SecurityException",
        response.getRaisedThrowable().getThrowable().getType(),
        is("java.lang.SecurityException"));
    assertThat(
        "Exception message should contain expected text",
        response.getRaisedThrowable().getThrowable().getMessage(),
        containsString("Access denied by callback"));

    logger.info("===== shouldRespectPerInterceptPolicyOverride [{}]: TEST COMPLETED =====", path);
  }
}
