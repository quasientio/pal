/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.lang.intercept.AfterPhaseData;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.common.runtime.ThreadAffinity;
import io.quasient.pal.core.execution.ThreadAffinityDispatcher;
import io.quasient.pal.core.intercept.AroundInterceptChain;
import io.quasient.pal.core.intercept.AroundInterceptChainBuilder;
import io.quasient.pal.core.intercept.InterceptCheckResult;
import io.quasient.pal.core.intercept.InterceptChecker;
import io.quasient.pal.core.intercept.LocalInterceptCallbackDispatcher;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for verifying that BaseExecMessageDispatcher correctly uses ThreadAffinityDispatcher
 * when processing incoming RPC calls.
 *
 * <p>These tests verify the two call sites in {@code dispatchIncoming()} where thread affinity
 * routing must occur:
 *
 * <ol>
 *   <li>The direct invocation path (no AROUND intercepts) — wraps {@code invokeIncoming()} in a
 *       callable passed to {@code ThreadAffinityDispatcher.execute()}.
 *   <li>The AROUND intercept chain path — the {@code MethodInvoker} lambda wraps {@code
 *       invokeIncoming()} through the dispatcher.
 * </ol>
 *
 * <p>Uses the MinimalOk pattern established in {@link BaseExecMessageDispatcherDispatchTest} to
 * create a minimal concrete implementation with a mock ThreadAffinityDispatcher injected via
 * reflection.
 *
 * @see BaseExecMessageDispatcherDispatchTest
 */
public class BaseExecMessageDispatcherThreadAffinityTest {

  /**
   * Minimal concrete implementation of BaseExecMessageDispatcher for testing thread affinity
   * integration.
   *
   * <p>Follows the MinimalOk pattern from {@link BaseExecMessageDispatcherDispatchTest}.
   */
  static class MinimalOkForAffinity extends BaseExecMessageDispatcher {

    @Override
    protected ExecMessage createBeforeExecMessage(
        Context ctxt,
        Object sender,
        Object target,
        Object[] args,
        boolean includeDeclaredExceptions) {
      return new ExecMessage();
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {
      return new ExecMessage();
    }

    @Override
    protected ExecMessage createAfterExecMessage(
        ExecMessage execMessage,
        Object valueObject,
        ObjectRef valueObjRef,
        AccessibleObject accessibleObject,
        Throwable exceptionWhileLoading,
        Throwable exceptionWhileInvoking) {
      ExecMessage msg = new ExecMessage();
      ReturnValue rv = new ReturnValue();
      rv.isVoid = true;
      msg.returnValue = rv;
      return msg;
    }

    @Override
    protected Object invokeIncoming(
        AccessibleObject accessibleObject,
        Object target,
        List<MessageArgument> args,
        Object value) {
      return null;
    }

    @Override
    protected boolean returnsVoid(AccessibleObject accessibleObject) {
      return false;
    }

    @Override
    protected boolean returnsVoid(ProceedingJoinPoint pjp) {
      return false;
    }

    @Override
    protected MessageType getBeforeExecMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }

    @Override
    protected List<Parameter> getParameterList(ExecMessage execMessage) {
      return Collections.emptyList();
    }

    @Override
    protected AccessibleObject loadAccessibleObject(
        ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args) {
      return null;
    }

    @Override
    public MessageType getSupportedMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }
  }

  /**
   * Sets a field on {@link AbstractDispatcher} via reflection.
   *
   * @param dispatcher the dispatcher instance
   * @param fieldName the field name
   * @param value the value to set
   */
  private static void setField(AbstractDispatcher dispatcher, String fieldName, Object value)
      throws Exception {
    Field f = AbstractDispatcher.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(dispatcher, value);
  }

  /**
   * Creates an ExecMessage with an InstanceMethodCall set (so getMessageTypeOf returns
   * EXEC_INSTANCE_METHOD) and the given thread affinity.
   *
   * @param threadAffinity the thread affinity to set, or null for default
   * @return a configured ExecMessage
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes") // Class conflicts with java.lang.Class
  private static ExecMessage createInstanceMethodExecMessage(String threadAffinity) {
    ExecMessage msg = new ExecMessage();
    InstanceMethodCall call = new InstanceMethodCall();
    call.name = "testMethod";
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.name = "test.TestClass";
    call.clazz = clazz;
    msg.instanceMethodCall = call;
    if (threadAffinity != null) {
      msg.setThreadAffinity(threadAffinity);
    }
    return msg;
  }

  /**
   * Creates a MinimalOkForAffinity dispatcher with required dependencies injected.
   *
   * @param mockDispatcher the mock ThreadAffinityDispatcher
   * @param runOptions the run options to use
   * @return a configured dispatcher ready for testing
   */
  private static MinimalOkForAffinity createDispatcher(
      ThreadAffinityDispatcher mockDispatcher, Set<RunOptions> runOptions) throws Exception {
    MinimalOkForAffinity d = new MinimalOkForAffinity();
    setField(d, "runOptions", runOptions);
    setField(d, "threadAffinityDispatcher", mockDispatcher);

    // Mock messageGateway (called at end of dispatchIncoming to send the after exec message)
    OutboundMessageGateway mockGateway = mock(OutboundMessageGateway.class);
    when(mockGateway.sendExecMessage(any(), any())).thenReturn(new ExecMessage());
    setField(d, "messageGateway", mockGateway);

    // Real MessageBuilder (used by messageGateway.sendExecMessage via wrap())
    MessageBuilder messageBuilder =
        new MessageBuilder(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    setField(d, "messageBuilder", messageBuilder);

    return d;
  }

  // ===== Direct invocation path tests =====

  /**
   * Verifies that the direct invocation path (no AROUND intercepts) routes through
   * ThreadAffinityDispatcher with the thread affinity from the ExecMessage.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void directInvocationPathUsesThreadAffinityDispatcher() throws Exception {
    // Given: mock ThreadAffinityDispatcher; no AROUND intercepts
    ThreadAffinityDispatcher mockDispatcher = mock(ThreadAffinityDispatcher.class);
    when(mockDispatcher.execute(any(), any(Callable.class))).thenReturn(null);

    MinimalOkForAffinity d = createDispatcher(mockDispatcher, EnumSet.noneOf(RunOptions.class));

    // Create ExecMessage with threadAffinity="fx-thread"
    ExecMessage msg = createInstanceMethodExecMessage(ThreadAffinity.FX_THREAD);

    // When: dispatchIncoming processes the message
    d.dispatchIncoming(msg, MessageChannelType.ZMQ_SOCKET_RPC);

    // Then: ThreadAffinityDispatcher.execute() is called with "fx-thread"
    verify(mockDispatcher).execute(eq(ThreadAffinity.FX_THREAD), any(Callable.class));
  }

  // ===== Null affinity tests =====

  /**
   * Verifies that when ExecMessage has null threadAffinity, the dispatcher still routes through
   * ThreadAffinityDispatcher with the default (empty) affinity from ExecMessage.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void nullAffinityPassedToDispatcher() throws Exception {
    // Given: mock ThreadAffinityDispatcher; default threadAffinity (empty string)
    ThreadAffinityDispatcher mockDispatcher = mock(ThreadAffinityDispatcher.class);
    when(mockDispatcher.execute(any(), any(Callable.class))).thenReturn(null);

    MinimalOkForAffinity d = createDispatcher(mockDispatcher, EnumSet.noneOf(RunOptions.class));

    // Create ExecMessage with default (empty) threadAffinity
    ExecMessage msg = createInstanceMethodExecMessage(null);

    // When: dispatchIncoming processes the message
    d.dispatchIncoming(msg, MessageChannelType.ZMQ_SOCKET_RPC);

    // Then: ThreadAffinityDispatcher.execute() is called with empty string (default)
    // ExecMessage defaults threadAffinity to "" (empty string), not null
    verify(mockDispatcher).execute(eq(""), any(Callable.class));
  }

  // ===== AROUND chain path tests =====

  /**
   * Verifies that when AROUND intercepts are configured, the MethodInvoker lambda inside the AROUND
   * chain routes through ThreadAffinityDispatcher with the correct thread affinity.
   */
  @Test
  @SuppressWarnings("unchecked")
  public void aroundChainPathUsesThreadAffinityDispatcher() throws Exception {
    // Given: mock ThreadAffinityDispatcher
    ThreadAffinityDispatcher mockDispatcher = mock(ThreadAffinityDispatcher.class);
    when(mockDispatcher.execute(any(), any(Callable.class))).thenReturn(null);

    MinimalOkForAffinity d =
        createDispatcher(mockDispatcher, EnumSet.of(RunOptions.WITH_INTERCEPTS));

    // Mock InterceptChecker to return local AROUND intercepts
    InterceptMessage aroundIntercept = new InterceptMessage();
    aroundIntercept.setInterceptType(InterceptType.AROUND.toByte());
    aroundIntercept.setCallbackClass("test.Callback");
    aroundIntercept.setCallbackMethod("onAround");

    InterceptCheckResult checkResult =
        new InterceptCheckResult(
            Collections.emptyList(), Collections.singletonList(aroundIntercept));

    InterceptChecker mockChecker = mock(InterceptChecker.class);
    when(mockChecker.checkIntercepts(any(), any(), any(), any(), any())).thenReturn(checkResult);
    setField(d, "interceptChecker", mockChecker);

    // Mock LocalInterceptCallbackDispatcher (needed for BEFORE local intercept handling)
    LocalInterceptCallbackDispatcher mockLocalDispatcher =
        mock(LocalInterceptCallbackDispatcher.class);
    setField(d, "localInterceptCallbackDispatcher", mockLocalDispatcher);

    // Mock AroundInterceptChainBuilder — capture the MethodInvoker and invoke it
    AroundInterceptChainBuilder mockChainBuilder = mock(AroundInterceptChainBuilder.class);
    ArgumentCaptor<AroundInterceptChain.MethodInvoker> invokerCaptor =
        ArgumentCaptor.forClass(AroundInterceptChain.MethodInvoker.class);

    // Create a mock AroundInterceptChain whose invoke() calls the captured MethodInvoker
    AroundInterceptChain mockChain = mock(AroundInterceptChain.class);
    when(mockChain.isEmpty()).thenReturn(false);
    when(mockChain.invoke(any(), any()))
        .thenAnswer(
            invocation -> {
              // Get the MethodInvoker that was captured during build()
              AroundInterceptChain.MethodInvoker methodInvoker = invokerCaptor.getValue();
              Object[] args = invocation.getArgument(0);
              AfterPhaseData result = methodInvoker.invoke(args);
              return new AroundInterceptChain.ChainResult(
                  result.returnValue(), result.thrownException(), result.isVoid(), true);
            });

    when(mockChainBuilder.build(
            any(List.class),
            any(List.class),
            any(),
            any(),
            any(List.class),
            invokerCaptor.capture()))
        .thenReturn(mockChain);
    setField(d, "aroundChainBuilder", mockChainBuilder);

    // Create ExecMessage with threadAffinity="fx-thread"
    ExecMessage msg = createInstanceMethodExecMessage(ThreadAffinity.FX_THREAD);

    // When: dispatchIncoming processes the message (which triggers the AROUND chain)
    d.dispatchIncoming(msg, MessageChannelType.ZMQ_SOCKET_RPC);

    // Then: ThreadAffinityDispatcher.execute() is called with "fx-thread"
    // from within the MethodInvoker lambda in the AROUND chain
    verify(mockDispatcher).execute(eq(ThreadAffinity.FX_THREAD), any(Callable.class));
  }
}
