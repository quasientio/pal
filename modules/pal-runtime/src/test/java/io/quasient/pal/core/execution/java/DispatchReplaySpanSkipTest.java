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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalEntryKind;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.core.replay.DivergenceDetector;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayCursor;
import io.quasient.pal.core.replay.ReplayGate;
import io.quasient.pal.core.replay.ReplayObjectStore;
import io.quasient.pal.core.replay.ReplayPolicy;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import java.lang.reflect.AccessibleObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the conditional span-skip logic in {@code
 * BaseExecMessageDispatcher.dispatchReplay()}.
 *
 * <p>These tests verify that:
 *
 * <ul>
 *   <li>When an entry point's thread has a registered {@code ReplayInputInjector} (via {@code
 *       ReplayContext.registerInjectorThread()}), the full span is skipped via {@code
 *       advancePast(completionOffset)}
 *   <li>When an entry point's thread does NOT have a registered injector (e.g., self-caller
 *       main()), only the OPERATION entry is skipped via {@code cursor.advance()}
 *   <li>The COMPLETION skip loop is active only when span-skip was NOT used
 * </ul>
 */
public class DispatchReplaySpanSkipTest {

  /** Thread name used for entry-point tests with injector. */
  private static final String FX_THREAD = "fx-thread";

  /** Thread name used for entry-point tests without injector. */
  private static final String SELF_CALLER = "self-caller";

  /** Original thread name, restored after each test. */
  private String originalThreadName;

  /** Saves the original thread name before each test. */
  @Before
  public void saveThreadName() {
    originalThreadName = Thread.currentThread().getName();
  }

  /** Restores the original thread name after each test. */
  @After
  public void restoreThreadName() {
    Thread.currentThread().setName(originalThreadName);
  }

  /**
   * Verifies that when an entry point is encountered on a thread that has a corresponding {@code
   * ReplayInputInjector}, the entire span (OPERATION through COMPLETION) is skipped via {@code
   * cursor.advancePast(completionOffset)}, so nested operations are not consumed by the current
   * cursor.
   */
  @Test
  public void entryPointWithInjector_skipsEntireSpan() throws Throwable {
    // Given: WAL for fx-thread with:
    //   offset 0: entry-point OP (start, entryPoint=true) — will mismatch live op
    //   offset 1: nested OP (doSomething)
    //   offset 2: nested RET (completion of doSomething)
    //   offset 3: entry-point COMPLETION (entryPoint=true, pairs with offset 0)
    //   offset 4: OP (bar) — matches the live operation
    //   offset 5: RET (completion of bar)
    Thread.currentThread().setName(FX_THREAD);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(0L, FX_THREAD, 0, "com.example.App", "start"));
    entries.add(makeOperation(1L, FX_THREAD, 1, "com.example.App", "doSomething"));
    entries.add(makeReturnValue(2L, FX_THREAD, 2, "void", "void"));
    entries.add(makeEntryPointCompletion(3L, FX_THREAD, 3));
    entries.add(makeOperation(4L, FX_THREAD, 4, "com.example.App", "bar"));
    entries.add(makeReturnValue(5L, FX_THREAD, 5, "result", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(index, new ReplayPolicy(), new ReplayObjectStore(), detector, gate);

    // Register fx-thread as having an injector (simulates Main.startReplayInputInjectors)
    ctx.registerInjectorThread(FX_THREAD);
    assertThat(ctx.hasInjectorForThread(FX_THREAD), is(true));

    MinimalDispatcher dispatcher = new MinimalDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    // Live operation is "bar" — mismatches entry point "start"
    ProceedingJoinPoint pjp = buildPjp("com.example.App", "bar", new Object[] {}, "result");

    // When: dispatchReplay encounters entry point mismatch
    Object result = dispatcher.dispatch(pjp);

    // Then: span-skip occurred; nested ops (offsets 1-3) were skipped;
    //       matched at offset 4 (bar); cursor exhausted after processing
    assertThat(result, is("result"));
    assertThat(ctx.getCursor(FX_THREAD).isExhausted(), is(true));
    assertThat(detector.hasDivergences(), is(false));
    // Entry point at offset 0 was marked handled
    assertThat(ctx.isEntryPointHandled(0L), is(true));
  }

  /**
   * Verifies that when an entry point is encountered on a thread that does NOT have a corresponding
   * {@code ReplayInputInjector} (e.g., self-caller thread running main()), only the OPERATION entry
   * is skipped via {@code cursor.advance()}, leaving nested operations available in the cursor for
   * matching.
   */
  @Test
  public void entryPointWithoutInjector_skipsOnlyOperation() throws Throwable {
    // Given: WAL for self-caller with:
    //   offset 0: entry-point OP (main, entryPoint=true) — will mismatch live op
    //   offset 1: nested OP (doWork) — should match the live operation after skip
    //   offset 2: nested RET (completion of doWork)
    //   offset 3: entry-point COMPLETION (entryPoint=true, pairs with offset 0)
    // The self-caller thread has entry points in the WAL but no registered injector
    // (Main.java skips creating an injector for self-caller; SelfBootstrapInvoker
    // handles it instead).
    Thread.currentThread().setName(SELF_CALLER);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(0L, SELF_CALLER, 0, "com.example.App", "main"));
    entries.add(makeOperation(1L, SELF_CALLER, 1, "com.example.App", "doWork"));
    entries.add(makeReturnValue(2L, SELF_CALLER, 2, "done", "java.lang.String"));
    entries.add(makeEntryPointCompletion(3L, SELF_CALLER, 3));
    WalIndex index = WalIndex.build(entries);

    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(index, new ReplayPolicy(), new ReplayObjectStore(), detector, gate);
    // self-caller is NOT registered as an injector thread (mirrors Main.java behavior)

    MinimalDispatcher dispatcher = new MinimalDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    // Live operation is "doWork" — mismatches entry point "main"
    ProceedingJoinPoint pjp = buildPjp("com.example.App", "doWork", new Object[] {}, "done");

    // When: dispatchReplay encounters entry point mismatch
    Object result = dispatcher.dispatch(pjp);

    // Then: operation-only skip; nested ops remain in cursor and "doWork" matches
    assertThat(result, is("done"));
    assertThat(detector.hasDivergences(), is(false));
    // Entry point at offset 0 was marked handled
    assertThat(ctx.isEntryPointHandled(0L), is(true));
    // Entry-point COMPLETION at offset 3 is still in the cursor (it will be skipped
    // at the start of the next dispatchReplay call by the top-level COMPLETION skip loop)
    ReplayCursor cursor = ctx.getCursor(SELF_CALLER);
    assertThat(cursor.isExhausted(), is(false));
    WalEntry remaining = cursor.peekNext();
    assertThat(remaining.isEntryPoint(), is(true));
    assertThat(remaining.getKind(), is(WalEntryKind.COMPLETION));
  }

  /**
   * Verifies that the COMPLETION skip loop is active after an operation-only skip (i.e., when
   * span-skip was NOT used). When only the OPERATION entry was skipped for an entry point, the
   * COMPLETION entry remains in the cursor and must be skipped by the re-match loop.
   */
  @Test
  public void completionSkipLoop_activeAfterOperationOnlySkip() throws Throwable {
    // Given: WAL for self-caller with:
    //   offset 0: entry-point OP (main, entryPoint=true) — will mismatch
    //   offset 1: entry-point COMPLETION (entryPoint=true, pairs with offset 0)
    //   offset 2: OP (realWork) — matches the live operation
    //   offset 3: RET (completion of realWork)
    // After operation-only skip of offset 0, the COMPLETION at offset 1 should be
    // skipped by the loop, and matching should proceed to offset 2.
    Thread.currentThread().setName(SELF_CALLER);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(0L, SELF_CALLER, 0, "com.example.App", "main"));
    entries.add(makeEntryPointCompletion(1L, SELF_CALLER, 1));
    entries.add(makeOperation(2L, SELF_CALLER, 2, "com.example.App", "realWork"));
    entries.add(makeReturnValue(3L, SELF_CALLER, 3, "ok", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(index, new ReplayPolicy(), new ReplayObjectStore(), detector, gate);
    // self-caller is NOT registered as an injector thread

    MinimalDispatcher dispatcher = new MinimalDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    // Live operation is "realWork" — mismatches entry point "main"
    ProceedingJoinPoint pjp = buildPjp("com.example.App", "realWork", new Object[] {}, "ok");

    // When: dispatchReplay encounters entry point mismatch, skips OP only,
    //       then COMPLETION skip loop skips the entry-point COMPLETION
    Object result = dispatcher.dispatch(pjp);

    // Then: COMPLETION at offset 1 was skipped; matched realWork at offset 2
    assertThat(result, is("ok"));
    assertThat(ctx.getCursor(SELF_CALLER).isExhausted(), is(true));
    assertThat(detector.hasDivergences(), is(false));
    // Gate advanced to at least offset 1 (the COMPLETION that was skipped)
    assertThat(gate.getCompletedOffset() >= 1L, is(true));
  }

  // ──────────────────── Helpers ────────────────────

  /** Sets {@code runOptions} on an AbstractDispatcher via reflection. */
  private static void setRunOptions(AbstractDispatcher d, Set<RunOptions> ro) throws Exception {
    var f = AbstractDispatcher.class.getDeclaredField("runOptions");
    f.setAccessible(true);
    f.set(d, ro);
  }

  /** Sets {@code replayContext} on an AbstractDispatcher via reflection. */
  private static void setReplayContext(AbstractDispatcher d, ReplayContext ctx) throws Exception {
    var f = AbstractDispatcher.class.getDeclaredField("replayContext");
    f.setAccessible(true);
    f.set(d, ctx);
  }

  /**
   * Creates a mock {@link ProceedingJoinPoint} for a method with the given class/method name that
   * returns the specified value from {@code proceed(Object[])}.
   */
  private static ProceedingJoinPoint buildPjp(
      String className, String methodName, Object[] args, Object returnValue) throws Throwable {
    ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
    JoinPoint.StaticPart sp = mock(JoinPoint.StaticPart.class);
    MethodSignature ms = mock(MethodSignature.class);

    when(ms.getDeclaringTypeName()).thenReturn(className);
    when(ms.getName()).thenReturn(methodName);
    when(ms.getParameterTypes()).thenReturn(new java.lang.Class<?>[0]);
    when(sp.getSignature()).thenReturn(ms);
    when(pjp.getSignature()).thenReturn(ms);
    when(pjp.getStaticPart()).thenReturn(sp);
    when(pjp.getThis()).thenReturn(new Object());
    when(pjp.getTarget()).thenReturn(new Object());
    when(pjp.getArgs()).thenReturn(args);
    when(pjp.proceed(any(Object[].class))).thenReturn(returnValue);

    return pjp;
  }

  /** Creates an OPERATION WalEntry for an instance method call. */
  private static WalEntry makeOperation(
      long offset, String threadName, int builderSeq, String className, String methodName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    InstanceMethodCall imc = new InstanceMethodCall();
    Class clazz = new Class();
    clazz.setName(className);
    imc.setClazz(clazz);
    imc.setName(methodName);
    imc.setArgs(new Obj[0]);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates an entry-point OPERATION WalEntry. */
  private static WalEntry makeEntryPointOperation(
      long offset, String threadName, int builderSeq, String className, String methodName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    msg.setEntryPoint(true);

    InstanceMethodCall imc = new InstanceMethodCall();
    Class clazz = new Class();
    clazz.setName(className);
    imc.setClazz(clazz);
    imc.setName(methodName);
    imc.setArgs(new Obj[0]);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates a COMPLETION WalEntry with a string return value. */
  private static WalEntry makeReturnValue(
      long offset, String threadName, int builderSeq, String value, String typeName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    ReturnValue rv = new ReturnValue();
    Obj obj = new Obj();
    obj.setValue(value);
    Class clazz = new Class();
    clazz.setName(typeName);
    obj.setClazz(clazz);
    rv.setObject(obj);
    msg.setReturnValue(rv);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates an entry-point COMPLETION WalEntry (void return). */
  private static WalEntry makeEntryPointCompletion(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    msg.setEntryPoint(true);

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Minimal concrete dispatcher subclass for testing the dispatch replay path in isolation. Only
   * the replay-relevant methods need real behavior; all other abstract methods return stubs.
   */
  static class MinimalDispatcher extends BaseExecMessageDispatcher {

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
    protected List<Obj> getArgsList(ExecMessage execMessage) {
      return Collections.emptyList();
    }

    @Override
    protected AccessibleObject loadAccessibleObject(
        ExecMessage execMessage, List<java.lang.Class<?>> parameterTypes, List<Object> args) {
      return null;
    }

    @Override
    public MessageType getSupportedMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }
  }
}
