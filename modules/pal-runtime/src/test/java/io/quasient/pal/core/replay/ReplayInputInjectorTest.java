/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ClassMethodCall;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ReplayInputInjector} — the component that drives WAL-based input injection
 * on non-self-caller threads during deterministic replay.
 *
 * <p>ReplayInputInjector reads entry-point operations from the {@code WalIndex} for a specific
 * thread and injects them via {@code IncomingMessageDispatcher.incomingCall()}, causing the
 * replayed peer to re-execute incoming RPC calls in the correct order.
 *
 * <p>Tests cover injection of entry points, message type correctness, ordering gate interaction,
 * completion semantics, empty entry-point handling, raw message pass-through, and ready-latch
 * gating.
 */
public class ReplayInputInjectorTest {

  /**
   * Verifies that all entry-point operations for a given thread are injected via the incoming
   * message dispatcher.
   */
  @Test
  public void injectsAllEntryPointsForThread() {
    // Given: 3 entry-point operations on thread 'rpc-worker-1'; mock IncomingMessageDispatcher
    IncomingMessageDispatcher dispatcher = mock(IncomingMessageDispatcher.class);
    ReplayGate gate = new ReplayGate(false);
    ReplayContext replayContext = mock(ReplayContext.class);
    when(replayContext.isEntryPointHandled(anyLong())).thenReturn(false);
    CountDownLatch readyLatch = new CountDownLatch(0);

    List<WalEntry> entryPoints = new ArrayList<>();
    entryPoints.add(makeEntryPoint(10L, "rpc-worker-1", 1, "methodA"));
    entryPoints.add(makeEntryPoint(20L, "rpc-worker-1", 2, "methodB"));
    entryPoints.add(makeEntryPoint(30L, "rpc-worker-1", 3, "methodC"));

    WalIndex walIndex = buildWalIndex(entryPoints);
    ReplayInputInjector injector =
        new ReplayInputInjector(
            "rpc-worker-1", entryPoints, dispatcher, gate, replayContext, readyLatch, walIndex);

    // When: ReplayInputInjector runs to completion
    injector.run();

    // Then: incomingMessageDispatcher.incomingCall() invoked exactly 3 times
    verify(dispatcher, times(3))
        .incomingCall(
            any(ExecMessage.class),
            any(MessageType.class),
            eq(MessageChannelType.REPLAY_INJECTION));
  }

  /**
   * Verifies that each injected entry point uses the correct {@code MessageType} from the
   * corresponding {@code WalEntry}.
   */
  @Test
  public void usesCorrectMessageType() {
    // Given: entry points of types EXEC_INSTANCE_METHOD and EXEC_CLASS_METHOD
    IncomingMessageDispatcher dispatcher = mock(IncomingMessageDispatcher.class);
    ReplayGate gate = new ReplayGate(false);
    ReplayContext replayContext = mock(ReplayContext.class);
    when(replayContext.isEntryPointHandled(anyLong())).thenReturn(false);
    CountDownLatch readyLatch = new CountDownLatch(0);

    List<WalEntry> entryPoints = new ArrayList<>();
    entryPoints.add(makeEntryPoint(10L, "rpc-worker-1", 1, "instanceMethod"));
    entryPoints.add(makeClassMethodEntryPoint(20L, "rpc-worker-1", 2, "staticMethod"));

    WalIndex walIndex = buildWalIndex(entryPoints);
    ReplayInputInjector injector =
        new ReplayInputInjector(
            "rpc-worker-1", entryPoints, dispatcher, gate, replayContext, readyLatch, walIndex);

    // When: ReplayInputInjector runs
    injector.run();

    // Then: Each incomingCall() invocation uses the correct MessageType from the WalEntry
    ArgumentCaptor<MessageType> typeCaptor = ArgumentCaptor.forClass(MessageType.class);
    verify(dispatcher, times(2))
        .incomingCall(
            any(ExecMessage.class), typeCaptor.capture(), eq(MessageChannelType.REPLAY_INJECTION));

    List<MessageType> capturedTypes = typeCaptor.getAllValues();
    assertThat(capturedTypes.get(0), is(MessageType.EXEC_INSTANCE_METHOD));
    assertThat(capturedTypes.get(1), is(MessageType.EXEC_CLASS_METHOD));
  }

  /**
   * Verifies that the injector waits for the ordering gate to reach the required offset before
   * injecting each entry point.
   */
  @Test
  public void waitsForOrderingGateBeforeInjection() throws Exception {
    // Given: ReplayGate at offset -1 (ordered mode); 2 entry points at offsets 10 and 50
    IncomingMessageDispatcher dispatcher = mock(IncomingMessageDispatcher.class);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext replayContext = mock(ReplayContext.class);
    when(replayContext.isEntryPointHandled(anyLong())).thenReturn(false);
    CountDownLatch readyLatch = new CountDownLatch(0);

    List<WalEntry> entryPoints = new ArrayList<>();
    entryPoints.add(makeEntryPoint(10L, "rpc-worker-1", 1, "methodA"));
    entryPoints.add(makeEntryPoint(50L, "rpc-worker-1", 2, "methodB"));

    WalIndex walIndex = buildWalIndex(entryPoints);
    ReplayInputInjector injector =
        new ReplayInputInjector(
            "rpc-worker-1", entryPoints, dispatcher, gate, replayContext, readyLatch, walIndex);

    AtomicBoolean firstInjected = new AtomicBoolean(false);
    AtomicBoolean secondInjected = new AtomicBoolean(false);
    CountDownLatch done = new CountDownLatch(1);

    // Track when injections happen via mock side effects
    doAnswer(
            invocation -> {
              if (!firstInjected.get()) {
                firstInjected.set(true);
              } else {
                secondInjected.set(true);
              }
              return null;
            })
        .when(dispatcher)
        .incomingCall(
            any(ExecMessage.class), any(MessageType.class), any(MessageChannelType.class));

    // When: ReplayInputInjector starts (gate blocks first entry point)
    Thread injectorThread =
        new Thread(
            () -> {
              injector.run();
              done.countDown();
            });
    injectorThread.start();

    // Then: First injection doesn't happen until gate reaches offset 9
    Thread.sleep(50);
    assertThat("First injection should be blocked", firstInjected.get(), is(false));

    gate.advanceTo(9);
    Thread.sleep(50);
    assertThat("First injection should have happened", firstInjected.get(), is(true));

    // Second doesn't happen until first entry point's completion (offset 11) + 1 is reached
    // AND the gate reaches offset 49 (entry point 2 is at offset 50)
    assertThat("Second injection should be blocked", secondInjected.get(), is(false));

    // Advance past first entry point's completion (offset 11) + 1 = 12
    gate.advanceTo(12);
    Thread.sleep(50);
    assertThat(
        "Second injection should still be blocked (waiting for offset 49)",
        secondInjected.get(),
        is(false));

    // Advance to allow second entry point (at offset 50)
    gate.advanceTo(49);
    Thread.sleep(50);
    assertThat("Second injection should have happened", secondInjected.get(), is(true));

    // Advance past second entry point's completion (offset 51) + 1 = 52 for injector to complete
    gate.advanceTo(52);
    boolean completed = done.await(2, TimeUnit.SECONDS);
    assertThat("Injector should complete", completed, is(true));
  }

  /**
   * Verifies that the injector completes after all entry points have been injected, and that {@code
   * isComplete()} returns true.
   */
  @Test
  public void completesWhenAllEntryPointsInjected() {
    // Given: 2 entry points; ReplayGate that never blocks (unordered)
    IncomingMessageDispatcher dispatcher = mock(IncomingMessageDispatcher.class);
    ReplayGate gate = new ReplayGate(false);
    ReplayContext replayContext = mock(ReplayContext.class);
    when(replayContext.isEntryPointHandled(anyLong())).thenReturn(false);
    CountDownLatch readyLatch = new CountDownLatch(0);

    List<WalEntry> entryPoints = new ArrayList<>();
    entryPoints.add(makeEntryPoint(10L, "rpc-worker-1", 1, "methodA"));
    entryPoints.add(makeEntryPoint(20L, "rpc-worker-1", 2, "methodB"));

    WalIndex walIndex = buildWalIndex(entryPoints);
    ReplayInputInjector injector =
        new ReplayInputInjector(
            "rpc-worker-1", entryPoints, dispatcher, gate, replayContext, readyLatch, walIndex);

    // Before run: not complete
    assertThat(injector.isComplete(), is(false));

    // When: ReplayInputInjector.run() called
    injector.run();

    // Then: isComplete() returns true
    assertThat(injector.isComplete(), is(true));
  }

  /**
   * Verifies that the injector handles the case where there are no entry points for the given
   * thread gracefully.
   */
  @Test
  public void handlesEmptyEntryPointList() {
    // Given: empty entry points list
    IncomingMessageDispatcher dispatcher = mock(IncomingMessageDispatcher.class);
    ReplayGate gate = new ReplayGate(false);
    ReplayContext replayContext = mock(ReplayContext.class);
    when(replayContext.isEntryPointHandled(anyLong())).thenReturn(false);
    CountDownLatch readyLatch = new CountDownLatch(0);

    List<WalEntry> emptyList = Collections.emptyList();
    WalIndex walIndex = buildWalIndex(emptyList);
    ReplayInputInjector injector =
        new ReplayInputInjector(
            "rpc-worker-3", emptyList, dispatcher, gate, replayContext, readyLatch, walIndex);

    // When: ReplayInputInjector.run() called
    injector.run();

    // Then: Returns immediately; no calls to incomingMessageDispatcher; marked complete
    verify(dispatcher, never())
        .incomingCall(
            any(ExecMessage.class), any(MessageType.class), any(MessageChannelType.class));
    assertThat(injector.isComplete(), is(true));
  }

  /**
   * Verifies that the raw {@code ExecMessage} from the {@code WalEntry} is passed directly to the
   * dispatcher without modification.
   */
  @Test
  public void passesRawMessageFromWalEntry() {
    // Given: WalEntry with specific ExecMessage
    IncomingMessageDispatcher dispatcher = mock(IncomingMessageDispatcher.class);
    ReplayGate gate = new ReplayGate(false);
    ReplayContext replayContext = mock(ReplayContext.class);
    when(replayContext.isEntryPointHandled(anyLong())).thenReturn(false);
    CountDownLatch readyLatch = new CountDownLatch(0);

    WalEntry entryPoint = makeEntryPoint(10L, "rpc-worker-1", 1, "targetMethod");
    ExecMessage expectedMsg = entryPoint.getRawMessage();

    List<WalEntry> entryPointList = List.of(entryPoint);
    WalIndex walIndex = buildWalIndex(entryPointList);
    ReplayInputInjector injector =
        new ReplayInputInjector(
            "rpc-worker-1", entryPointList, dispatcher, gate, replayContext, readyLatch, walIndex);

    // When: ReplayInputInjector injects this entry point
    injector.run();

    // Then: The ExecMessage passed to incomingCall() is the exact rawMessage from the WalEntry
    ArgumentCaptor<ExecMessage> msgCaptor = ArgumentCaptor.forClass(ExecMessage.class);
    verify(dispatcher)
        .incomingCall(
            msgCaptor.capture(),
            eq(MessageType.EXEC_INSTANCE_METHOD),
            eq(MessageChannelType.REPLAY_INJECTION));

    assertThat(
        "Should pass the exact same ExecMessage object from WalEntry",
        msgCaptor.getValue(),
        is(sameInstance(expectedMsg)));
  }

  /**
   * Verifies that the injector waits for the ready latch to be counted down before starting any
   * injections.
   */
  @Test
  public void waitsForReadyLatchBeforeStarting() throws Exception {
    // Given: ReplayInputInjector with a readyLatch that is not yet counted down
    IncomingMessageDispatcher dispatcher = mock(IncomingMessageDispatcher.class);
    ReplayGate gate = new ReplayGate(false);
    ReplayContext replayContext = mock(ReplayContext.class);
    when(replayContext.isEntryPointHandled(anyLong())).thenReturn(false);
    CountDownLatch readyLatch = new CountDownLatch(1);

    List<WalEntry> entryPoints = List.of(makeEntryPoint(10L, "rpc-worker-1", 1, "methodA"));

    WalIndex walIndex = buildWalIndex(entryPoints);
    ReplayInputInjector injector =
        new ReplayInputInjector(
            "rpc-worker-1", entryPoints, dispatcher, gate, replayContext, readyLatch, walIndex);

    CountDownLatch done = new CountDownLatch(1);

    // When: ReplayInputInjector.run() starts on a separate thread
    Thread injectorThread =
        new Thread(
            () -> {
              injector.run();
              done.countDown();
            });
    injectorThread.start();

    // Then: No injections happen until latch is counted down
    Thread.sleep(50);
    verify(dispatcher, never())
        .incomingCall(
            any(ExecMessage.class), any(MessageType.class), any(MessageChannelType.class));
    assertThat("Should not be complete before latch countdown", injector.isComplete(), is(false));

    // Injections proceed after countdown
    readyLatch.countDown();
    boolean completed = done.await(2, TimeUnit.SECONDS);
    assertThat("Injector thread should complete", completed, is(true));
    verify(dispatcher, times(1))
        .incomingCall(
            any(ExecMessage.class),
            any(MessageType.class),
            eq(MessageChannelType.REPLAY_INJECTION));
    assertThat(injector.isComplete(), is(true));
  }

  /**
   * Creates an entry-point {@link WalEntry} with an {@code InstanceMethodCall} (message type {@code
   * EXEC_INSTANCE_METHOD}).
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param methodName the method name
   * @return a new entry-point WalEntry
   */
  private static WalEntry makeEntryPoint(
      long offset, String threadName, int builderSeq, String methodName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    msg.setEntryPoint(true);

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName(methodName);
    imc.setObjectRef(1);
    Class clazz = new Class();
    clazz.setName("com.example.TestService");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates an entry-point {@link WalEntry} with a {@code ClassMethodCall} (message type {@code
   * EXEC_CLASS_METHOD}).
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param methodName the method name
   * @return a new entry-point WalEntry
   */
  private static WalEntry makeClassMethodEntryPoint(
      long offset, String threadName, int builderSeq, String methodName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    msg.setEntryPoint(true);

    ClassMethodCall cmc = new ClassMethodCall();
    cmc.setName(methodName);
    Class clazz = new Class();
    clazz.setName("com.example.TestService");
    cmc.setClazz(clazz);
    msg.setClassMethodCall(cmc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a completion {@link WalEntry} for an operation at the given offset.
   *
   * @param completionOffset the offset of this completion entry
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new completion WalEntry
   */
  private static WalEntry makeCompletion(long completionOffset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);
    return WalEntry.fromExecMessage(completionOffset, msg);
  }

  /**
   * Builds a {@link WalIndex} from the given entry points with completion entries.
   *
   * <p>For each entry point at offset O, a completion entry is created at offset O+1.
   *
   * @param entryPoints the entry points to include
   * @return a WalIndex with proper operation/completion pairing
   */
  private static WalIndex buildWalIndex(List<WalEntry> entryPoints) {
    List<WalEntry> allEntries = new ArrayList<>();
    int builderSeq = 100; // Start after any entry point sequences
    for (WalEntry ep : entryPoints) {
      allEntries.add(ep);
      allEntries.add(makeCompletion(ep.getOffset() + 1, ep.getThreadName(), builderSeq++));
    }
    return WalIndex.build(allEntries);
  }
}
