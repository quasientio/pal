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

import static org.junit.Assert.fail;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.types.MessageType;
import java.lang.reflect.AccessibleObject;
import java.util.Collections;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.Ignore;
import org.junit.Test;

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
      return new ExecMessage();
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

  // ===== Direct invocation path tests =====

  /**
   * Verifies that the direct invocation path (no AROUND intercepts) routes through
   * ThreadAffinityDispatcher with the thread affinity from the ExecMessage.
   */
  @Test
  @Ignore("Awaiting implementation in #741")
  public void directInvocationPathUsesThreadAffinityDispatcher() {
    // Given: BaseExecMessageDispatcher with mock ThreadAffinityDispatcher;
    //        ExecMessage with threadAffinity="fx-thread";
    //        no AROUND intercepts configured
    //
    // When: dispatchIncoming() processes the message
    //
    // Then: ThreadAffinityDispatcher.execute() is called with "fx-thread"
    //       and a callable that wraps invokeIncoming()

    // TODO(#741): Implement test logic
    // 1. Create MinimalOkForAffinity dispatcher
    // 2. Inject mock ThreadAffinityDispatcher via reflection
    // 3. Inject required dependencies (runOptions, interceptChecker, etc.)
    // 4. Create ExecMessage with threadAffinity="fx-thread" and
    //    InstanceMethodCall populated for EXEC_INSTANCE_METHOD type
    // 5. Call dispatchIncoming(execMessage, MessageChannelType.ZMQ_RPC)
    // 6. Verify mock ThreadAffinityDispatcher.execute() was called
    //    with "fx-thread" as the affinity argument
    // 7. Verify the callable passed to execute() wraps invokeIncoming()
    fail("Not yet implemented");
  }

  // ===== Null affinity tests =====

  /**
   * Verifies that when ExecMessage has null threadAffinity, the dispatcher still routes through
   * ThreadAffinityDispatcher with null affinity (which falls back to direct execution).
   */
  @Test
  @Ignore("Awaiting implementation in #741")
  public void nullAffinityPassedToDispatcher() {
    // Given: BaseExecMessageDispatcher with mock ThreadAffinityDispatcher;
    //        ExecMessage with null threadAffinity
    //
    // When: dispatchIncoming() processes the message
    //
    // Then: ThreadAffinityDispatcher.execute() is called with null affinity
    //       (which triggers direct execution via DirectInvocationExecutor)

    // TODO(#741): Implement test logic
    // 1. Create MinimalOkForAffinity dispatcher
    // 2. Inject mock ThreadAffinityDispatcher via reflection
    // 3. Inject required dependencies (runOptions, interceptChecker, etc.)
    // 4. Create ExecMessage with null/default threadAffinity and
    //    InstanceMethodCall populated for EXEC_INSTANCE_METHOD type
    // 5. Call dispatchIncoming(execMessage, MessageChannelType.ZMQ_RPC)
    // 6. Verify mock ThreadAffinityDispatcher.execute() was called
    //    with null as the affinity argument
    fail("Not yet implemented");
  }

  // ===== AROUND chain path tests =====

  /**
   * Verifies that when AROUND intercepts are configured, the MethodInvoker lambda inside the AROUND
   * chain routes through ThreadAffinityDispatcher with the correct thread affinity.
   */
  @Test
  @Ignore("Awaiting implementation in #741")
  public void aroundChainPathUsesThreadAffinityDispatcher() {
    // Given: BaseExecMessageDispatcher with mock ThreadAffinityDispatcher;
    //        ExecMessage with threadAffinity="fx-thread";
    //        AROUND intercept chain configured (so invocation goes through
    //        the MethodInvoker lambda rather than the direct path)
    //
    // When: The MethodInvoker (inside AROUND chain) is invoked during
    //        dispatchIncoming() processing
    //
    // Then: ThreadAffinityDispatcher.execute() is called with "fx-thread"
    //       wrapping the invokeIncoming() call inside the chain

    // TODO(#741): Implement test logic
    // 1. Create MinimalOkForAffinity dispatcher
    // 2. Inject mock ThreadAffinityDispatcher via reflection
    // 3. Inject required dependencies including:
    //    - runOptions with WITH_INTERCEPTS enabled
    //    - mock InterceptChecker that returns AROUND intercepts
    //    - mock AroundInterceptChainBuilder that captures the MethodInvoker
    //    - mock LocalInterceptCallbackDispatcher
    //    - mock InterceptCallbackDispatcher
    // 4. Create ExecMessage with threadAffinity="fx-thread" and
    //    InstanceMethodCall populated for EXEC_INSTANCE_METHOD type
    // 5. Call dispatchIncoming(execMessage, MessageChannelType.ZMQ_RPC)
    // 6. Verify that when the captured MethodInvoker is invoked,
    //    it calls ThreadAffinityDispatcher.execute() with "fx-thread"
    //
    // Note: If mocking the full AROUND chain path is impractical,
    // an alternative approach is to verify the ThreadAffinityDispatcher
    // field is properly injectable and that the MethodInvoker lambda
    // references it, deferring full integration verification to task #741.
    fail("Not yet implemented");
  }
}
