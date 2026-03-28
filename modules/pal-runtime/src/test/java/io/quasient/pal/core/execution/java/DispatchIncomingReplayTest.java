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
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.replay.DivergenceDetector;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayCursor;
import io.quasient.pal.core.replay.ReplayGate;
import io.quasient.pal.core.replay.ReplayObjectStore;
import io.quasient.pal.core.replay.ReplayPolicy;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the replay-mode integration in {@link BaseExecMessageDispatcher#dispatchIncoming}.
 *
 * <p>These tests verify that when {@code WITH_REPLAY} is active:
 *
 * <ul>
 *   <li>No WAL writing occurs (BEFORE and AFTER messages are not written)
 *   <li>The replay cursor is advanced past entry-point OPERATION and COMPLETION entries
 *   <li>The {@link ReplayGate} is advanced to the completion offset after each entry-point
 *       operation completes
 * </ul>
 *
 * <p>Uses the MinimalOk pattern to create a minimal concrete implementation with mocked gateway and
 * builder dependencies.
 */
public class DispatchIncomingReplayTest {

  /** Thread name matching the WAL thread name for entry-point operations. */
  private static final String RPC_THREAD_NAME = "rpc-worker-1";

  /** Original thread name, restored after each test. */
  private String originalThreadName;

  /** Sets the current thread name to match the WAL entries' thread name. */
  @Before
  public void setThreadName() {
    originalThreadName = Thread.currentThread().getName();
    Thread.currentThread().setName(RPC_THREAD_NAME);
  }

  /** Restores the original thread name after each test. */
  @After
  public void restoreThreadName() {
    Thread.currentThread().setName(originalThreadName);
  }

  /**
   * Verifies that during replay mode, the outbound message gateway is never called to write
   * messages to WAL/PUB.
   */
  @Test
  public void noWalWritingDuringReplay() throws Exception {
    // Given: WAL with [EP_OP@0, EP_RET@1]
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(0L, RPC_THREAD_NAME, 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(1L, RPC_THREAD_NAME, 1));
    WalIndex index = WalIndex.build(entries);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            new ReplayPolicy(),
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    OutboundMessageGateway mockGateway = mock(OutboundMessageGateway.class);
    MinimalOkForReplay dispatcher =
        createReplayDispatcher(
            EnumSet.of(
                RunOptions.WITH_REPLAY, RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC),
            ctx,
            mockGateway);

    ExecMessage msg = createInstanceMethodExecMessage("com.example.Foo", "bar", true);

    // Simulate what ReplayInputInjector does: push the entry point offset before incomingCall
    ctx.pushPendingInjection(RPC_THREAD_NAME, 0L);

    // When
    dispatcher.dispatchIncoming(msg, MessageChannelType.CLI_RPC);

    // Then: gateway should never be called to send exec messages (neither BEFORE nor AFTER)
    verify(mockGateway, never()).sendExecMessage(any(), any());
  }

  /**
   * Verifies that during replay mode, the cursor is advanced past the entry-point OPERATION and
   * COMPLETION entries.
   */
  @Test
  public void cursorAdvancedPastEntryPointEntries() throws Exception {
    // Given: WAL with [EP_OP@0, EP_RET@1]
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(0L, RPC_THREAD_NAME, 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(1L, RPC_THREAD_NAME, 1));
    WalIndex index = WalIndex.build(entries);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            new ReplayPolicy(),
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    MinimalOkForReplay dispatcher =
        createReplayDispatcher(EnumSet.of(RunOptions.WITH_REPLAY), ctx, null);

    ExecMessage msg = createInstanceMethodExecMessage("com.example.Foo", "bar", true);

    // Simulate what ReplayInputInjector does: push the entry point offset before incomingCall
    ctx.pushPendingInjection(RPC_THREAD_NAME, 0L);

    // When
    dispatcher.dispatchIncoming(msg, MessageChannelType.CLI_RPC);

    // Then: cursor should be exhausted (advanced past both OP and RET entries)
    ReplayCursor cursor = ctx.getCursor(RPC_THREAD_NAME);
    assertThat("Cursor should be exhausted after replay", cursor.isExhausted(), is(true));
  }

  /**
   * Verifies that the {@link ReplayGate} is advanced to the completion offset after the entry-point
   * operation completes in {@code dispatchIncoming()}.
   */
  @Test
  public void replayGateAdvancedToCompletionOffset() throws Exception {
    // Given: WAL with [EP_OP@10, EP_RET@20]
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(10L, RPC_THREAD_NAME, 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(20L, RPC_THREAD_NAME, 1));
    WalIndex index = WalIndex.build(entries);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            new ReplayPolicy(),
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    MinimalOkForReplay dispatcher =
        createReplayDispatcher(EnumSet.of(RunOptions.WITH_REPLAY), ctx, null);

    ExecMessage msg = createInstanceMethodExecMessage("com.example.Foo", "bar", true);

    // Simulate what ReplayInputInjector does: push the entry point offset before incomingCall
    ctx.pushPendingInjection(RPC_THREAD_NAME, 10L);

    // When
    dispatcher.dispatchIncoming(msg, MessageChannelType.CLI_RPC);

    // Then: gate should be advanced to the completion offset (20)
    assertThat(gate.getCompletedOffset(), is(20L));
  }

  /**
   * Verifies that during replay with multiple entry-point operations on the same thread, the cursor
   * and gate are correctly advanced after each one.
   */
  @Test
  public void multipleEntryPointsAdvanceCursorAndGateCorrectly() throws Exception {
    // Given: WAL with [EP_OP@0, EP_RET@1, EP_OP@2, EP_RET@3]
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(0L, RPC_THREAD_NAME, 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(1L, RPC_THREAD_NAME, 1));
    entries.add(makeEntryPointOperation(2L, RPC_THREAD_NAME, 2, "com.example.Foo", "baz"));
    entries.add(makeReturnValue(3L, RPC_THREAD_NAME, 3));
    WalIndex index = WalIndex.build(entries);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            new ReplayPolicy(),
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    MinimalOkForReplay dispatcher =
        createReplayDispatcher(EnumSet.of(RunOptions.WITH_REPLAY), ctx, null);

    // When: inject first entry point
    // Simulate what ReplayInputInjector does: push the entry point offset before incomingCall
    ctx.pushPendingInjection(RPC_THREAD_NAME, 0L);
    ExecMessage msg1 = createInstanceMethodExecMessage("com.example.Foo", "bar", true);
    dispatcher.dispatchIncoming(msg1, MessageChannelType.CLI_RPC);

    // Then: gate at offset 1, cursor at position 2
    assertThat(gate.getCompletedOffset(), is(1L));

    // When: inject second entry point
    // Simulate what ReplayInputInjector does: push the entry point offset before incomingCall
    ctx.pushPendingInjection(RPC_THREAD_NAME, 2L);
    ExecMessage msg2 = createInstanceMethodExecMessage("com.example.Foo", "baz", true);
    dispatcher.dispatchIncoming(msg2, MessageChannelType.CLI_RPC);

    // Then: gate at offset 3, cursor exhausted
    assertThat(gate.getCompletedOffset(), is(3L));
    assertThat(ctx.getCursor(RPC_THREAD_NAME).isExhausted(), is(true));
  }

  /**
   * Verifies that during replay when there is no entry-point operation at the current cursor
   * position, the cursor is not incorrectly advanced.
   */
  @Test
  public void cursorNotAdvancedWhenNoEntryPointAtPosition() throws Exception {
    // Given: WAL with [regular_OP@0, regular_RET@1] (not entry points)
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(0L, RPC_THREAD_NAME, 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(1L, RPC_THREAD_NAME, 1));
    WalIndex index = WalIndex.build(entries);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            new ReplayPolicy(),
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    MinimalOkForReplay dispatcher =
        createReplayDispatcher(EnumSet.of(RunOptions.WITH_REPLAY), ctx, null);

    ExecMessage msg = createInstanceMethodExecMessage("com.example.Foo", "bar", false);

    // When
    dispatcher.dispatchIncoming(msg, MessageChannelType.CLI_RPC);

    // Then: cursor should NOT have been advanced (entry at position 0 is not an entry point)
    ReplayCursor cursor = ctx.getCursor(RPC_THREAD_NAME);
    assertThat(
        "Cursor should not have advanced past non-entry-point OP", cursor.getPosition(), is(0));
    // Gate should still be at initial value
    assertThat(gate.getCompletedOffset(), is(-1L));
  }

  /**
   * Verifies that replay-specific behavior is not triggered when replay mode is not active, even
   * when replayContext is null.
   */
  @Test
  public void noReplayBehaviorWithoutReplayMode() throws Exception {
    // Given: no replay context, standard WAL writing options
    OutboundMessageGateway mockGateway = mock(OutboundMessageGateway.class);
    when(mockGateway.sendExecMessage(any(), any())).thenReturn(new ExecMessage());
    MinimalOkForReplay dispatcher =
        createReplayDispatcher(
            EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC), null, mockGateway);

    ExecMessage msg = createInstanceMethodExecMessage("com.example.Foo", "bar", false);

    // When
    dispatcher.dispatchIncoming(msg, MessageChannelType.ZMQ_SOCKET_RPC);

    // Then: gateway should be called to write BEFORE and AFTER exec messages
    verify(mockGateway).sendExecMessage(any(), eq(ExecPhase.BEFORE));
    verify(mockGateway).sendExecMessage(any(), eq(ExecPhase.AFTER));
  }

  // ──────────────────── Helpers ────────────────────

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
   * Creates a {@link MinimalOkForReplay} dispatcher with required dependencies injected.
   *
   * @param runOptions the run options to use
   * @param replayCtx the replay context, or null if not in replay mode
   * @param mockGateway the mock gateway, or null to create a default mock
   * @return a configured dispatcher ready for testing
   */
  private static MinimalOkForReplay createReplayDispatcher(
      Set<RunOptions> runOptions, ReplayContext replayCtx, OutboundMessageGateway mockGateway)
      throws Exception {
    MinimalOkForReplay d = new MinimalOkForReplay();
    setField(d, "runOptions", runOptions);

    if (replayCtx != null) {
      setField(d, "replayContext", replayCtx);
    }

    if (mockGateway == null) {
      mockGateway = mock(OutboundMessageGateway.class);
      when(mockGateway.sendExecMessage(any(), any())).thenReturn(new ExecMessage());
    }
    setField(d, "messageGateway", mockGateway);

    MessageBuilder messageBuilder =
        new MessageBuilder(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    setField(d, "messageBuilder", messageBuilder);

    // Peer UUID
    setField(d, "peerUuid", UUID.fromString("00000000-0000-0000-0000-000000000002"));

    return d;
  }

  /**
   * Creates an {@link ExecMessage} with an {@link InstanceMethodCall} set and optionally marked as
   * an entry point.
   *
   * @param className the class name
   * @param methodName the method name
   * @param entryPoint whether to mark as entry point
   * @return a configured ExecMessage
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes") // Class conflicts with java.lang.Class
  private static ExecMessage createInstanceMethodExecMessage(
      String className, String methodName, boolean entryPoint) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(RPC_THREAD_NAME); // Required for cursor lookup in dispatchIncoming()
    InstanceMethodCall call = new InstanceMethodCall();
    call.setName(methodName);
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.setName(className);
    call.setClazz(clazz);
    call.setArgs(new Obj[0]);
    msg.setInstanceMethodCall(call);
    msg.setEntryPoint(entryPoint);
    return msg;
  }

  /**
   * Creates an OPERATION {@link WalEntry} for an instance method call marked as entry point.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param className the class name
   * @param methodName the method name
   * @return a WalEntry marked as entry point
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static WalEntry makeEntryPointOperation(
      long offset, String threadName, int builderSeq, String className, String methodName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    msg.setEntryPoint(true);

    InstanceMethodCall imc = new InstanceMethodCall();
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.setName(className);
    imc.setClazz(clazz);
    imc.setName(methodName);
    imc.setArgs(new Obj[0]);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a regular OPERATION {@link WalEntry} (not an entry point).
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param className the class name
   * @param methodName the method name
   * @return a WalEntry not marked as entry point
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static WalEntry makeOperation(
      long offset, String threadName, int builderSeq, String className, String methodName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    InstanceMethodCall imc = new InstanceMethodCall();
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.setName(className);
    imc.setClazz(clazz);
    imc.setName(methodName);
    imc.setArgs(new Obj[0]);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a COMPLETION {@link WalEntry} with a void return value.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a COMPLETION WalEntry
   */
  private static WalEntry makeReturnValue(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Minimal concrete subclass of {@link BaseExecMessageDispatcher} for testing replay integration
   * in {@code dispatchIncoming()}. The loading and invocation phases produce errors (since no real
   * classes/methods are loaded), but the replay cursor/gate logic executes regardless.
   */
  static class MinimalOkForReplay extends BaseExecMessageDispatcher {

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
      rv.setIsVoid(true);
      msg.setReturnValue(rv);
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
      return true;
    }

    @Override
    protected boolean returnsVoid(ProceedingJoinPoint pjp) {
      return true;
    }

    @Override
    protected MessageType getBeforeExecMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }

    @Override
    protected List<Obj> getArgsList(ExecMessage execMessage) {
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
}
