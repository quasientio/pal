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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.core.replay.DivergenceDetector;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayGate;
import io.quasient.pal.core.replay.ReplayObjectStore;
import io.quasient.pal.core.replay.ReplayPolicy;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
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
 * Unit tests for the dispatch replay path in {@link BaseExecMessageDispatcher}. These tests verify
 * the core replay loop: match WAL entry, execute via ProceedingJoinPoint, verify return value
 * against the WAL oracle, and advance the cursor.
 *
 * <p>Tests use a minimal dispatcher subclass combined with mock {@code ProceedingJoinPoint} and
 * controlled {@code ReplayContext} inputs to exercise {@code dispatchReplay()} in isolation.
 */
public class ReplayDispatchTest {

  /** Thread name used in WAL entries, matching the self-caller convention. */
  private static final String THREAD_NAME = "self-caller";

  /** Original thread name, restored after each test. */
  private String originalThreadName;

  /** Sets the current thread name to match the WAL entries' thread name. */
  @Before
  public void setThreadName() {
    originalThreadName = Thread.currentThread().getName();
    Thread.currentThread().setName(THREAD_NAME);
  }

  /** Restores the original thread name after each test. */
  @After
  public void restoreThreadName() {
    Thread.currentThread().setName(originalThreadName);
  }

  /**
   * Verifies that a matching WAL operation executes and advances the cursor past both OP and RET
   * entries.
   */
  @Test
  public void matchingOperationExecutesAndAdvances() throws Throwable {
    // Given: WAL with [OP(Foo.bar), RET(value="42")]
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(0L, "self-caller", 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(1L, "self-caller", 1, "42", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(
            index, new ReplayPolicy(), new ReplayObjectStore(), detector, new ReplayGate(true));

    MinimalDispatcher dispatcher = new MinimalDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "bar", new Object[] {}, "42");

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then
    assertThat(result, is("42"));
    verify(pjp, times(1)).proceed(any(Object[].class));
    assertThat(detector.hasDivergences(), is(false));
    assertThat(ctx.getCursor(THREAD_NAME).isExhausted(), is(true));
  }

  /**
   * Verifies that a return value mismatch between WAL and live execution is detected and recorded
   * as a divergence.
   */
  @Test
  public void returnValueMismatchRecordsDivergence() throws Throwable {
    // Given: WAL expects "42" but live returns "99"
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(0L, "self-caller", 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(1L, "self-caller", 1, "42", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(
            index, new ReplayPolicy(), new ReplayObjectStore(), detector, new ReplayGate(true));

    MinimalDispatcher dispatcher = new MinimalDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "bar", new Object[] {}, "99");

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then
    assertThat(result, is("99"));
    verify(pjp, times(1)).proceed(any(Object[].class));
    assertThat(detector.hasDivergences(), is(true));
    assertThat(
        detector.getReport().getDivergences().get(0).type(),
        is(DivergenceDetector.DivergenceType.VALUE_MISMATCH));
  }

  /**
   * Verifies that an operation signature mismatch between WAL and live execution is detected and
   * recorded, but execution still proceeds (best-effort).
   */
  @Test
  public void operationMismatchRecordsDivergence() throws Throwable {
    // Given: WAL expects Foo.bar but live calls Foo.baz
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(0L, "self-caller", 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(1L, "self-caller", 1, "42", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(
            index, new ReplayPolicy(), new ReplayObjectStore(), detector, new ReplayGate(true));

    MinimalDispatcher dispatcher = new MinimalDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    // PJP for Foo.baz (different method name)
    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "baz", new Object[] {}, "99");

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then: mismatch recorded, pjp still called
    assertThat(result, is("99"));
    verify(pjp, times(1)).proceed(any(Object[].class));
    assertThat(detector.hasDivergences(), is(true));
    assertThat(
        detector.getReport().getDivergences().get(0).type(),
        is(DivergenceDetector.DivergenceType.OPERATION_MISMATCH));
  }

  /**
   * Verifies that an extra live operation (when the WAL cursor is exhausted) is detected and
   * recorded, but execution still proceeds.
   */
  @Test
  public void extraOperationWhenCursorExhausted() throws Throwable {
    // Given: empty WAL (cursor immediately exhausted)
    List<WalEntry> entries = Collections.emptyList();
    WalIndex index = WalIndex.build(entries);
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(
            index, new ReplayPolicy(), new ReplayObjectStore(), detector, new ReplayGate(true));

    MinimalDispatcher dispatcher = new MinimalDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "bar", new Object[] {}, "result");

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then
    assertThat(result, is("result"));
    verify(pjp, times(1)).proceed(any(Object[].class));
    assertThat(detector.hasDivergences(), is(true));
    assertThat(
        detector.getReport().getDivergences().get(0).type(),
        is(DivergenceDetector.DivergenceType.EXTRA_OPERATION));
  }

  /**
   * Verifies that nested operations advance the cursor correctly through all entries in order,
   * matching the balanced parentheses model [A_OP, B_OP, B_RET, A_RET].
   */
  @Test
  public void nestedOperationsAdvanceCursorCorrectly() throws Throwable {
    // Given: WAL [A_OP, B_OP, B_RET, A_RET]
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(0L, "self-caller", 0, "com.example.Outer", "a"));
    entries.add(makeOperation(1L, "self-caller", 1, "com.example.Inner", "b"));
    entries.add(makeReturnValue(2L, "self-caller", 2, "10", "java.lang.String"));
    entries.add(makeReturnValue(3L, "self-caller", 3, "20", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(
            index, new ReplayPolicy(), new ReplayObjectStore(), detector, new ReplayGate(true));

    // Outer dispatcher — when pjp.proceed() is called, it simulates the nested dispatch of B
    MinimalDispatcher outerDispatcher = new MinimalDispatcher();
    setRunOptions(outerDispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(outerDispatcher, ctx);

    // Inner dispatcher for nested B call
    MinimalDispatcher innerDispatcher = new MinimalDispatcher();
    setRunOptions(innerDispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(innerDispatcher, ctx);

    // Inner PJP (B)
    ProceedingJoinPoint innerPjp = buildPjp("com.example.Inner", "b", new Object[] {}, "10");

    // Outer PJP (A) — proceed triggers nested dispatch of B
    ProceedingJoinPoint outerPjp =
        buildPjpWithNestedDispatch(
            "com.example.Outer", "a", new Object[] {}, innerDispatcher, innerPjp, "20");

    // When: dispatch A (which internally triggers dispatch B)
    Object result = outerDispatcher.dispatch(outerPjp);

    // Then: cursor advanced through all 4 entries, no divergences
    assertThat(result, is("20"));
    assertThat(detector.hasDivergences(), is(false));
    assertThat(ctx.getCursor(THREAD_NAME).isExhausted(), is(true));
  }

  /**
   * Verifies that after a constructor replay, the newly created object is registered in the {@code
   * ReplayObjectStore} with its WAL ref.
   */
  @Test
  public void objectRefRegisteredAfterConstructor() throws Throwable {
    // Given: WAL with [OP(Foo.bar), RET(ref=7)]
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(0L, "self-caller", 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValueWithRef(1L, "self-caller", 1, 7));
    WalIndex index = WalIndex.build(entries);
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayObjectStore objectStore = new ReplayObjectStore();
    ReplayContext ctx =
        new ReplayContext(index, new ReplayPolicy(), objectStore, detector, new ReplayGate(true));

    MinimalDispatcher dispatcher = new MinimalDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    Object newObj = new Object();
    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "bar", new Object[] {}, newObj);

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then: object registered in store with WAL ref 7
    assertThat(result, is(newObj));
    assertThat(objectStore.resolveOrNull(7), is(newObj));
    assertThat(objectStore.getWalRef(newObj), is(7));
  }

  /**
   * Verifies that the {@link ReplayGate} is advanced to the completion offset after each
   * operation+completion pair in {@code dispatchReplay()}.
   */
  @Test
  public void replayGateAdvancedAfterCompletion() throws Throwable {
    // Given: WAL with [OP(Foo.bar)@0, RET(value="42")@1]
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(0L, "self-caller", 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(1L, "self-caller", 1, "42", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(index, new ReplayPolicy(), new ReplayObjectStore(), detector, gate);

    MinimalDispatcher dispatcher = new MinimalDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "bar", new Object[] {}, "42");

    // When
    dispatcher.dispatch(pjp);

    // Then: gate is advanced to the completion offset (1)
    assertThat(gate.getCompletedOffset(), is(1L));
  }

  /**
   * Verifies that the {@link ReplayGate} is advanced after nested operations, tracking the highest
   * completion offset processed.
   */
  @Test
  public void replayGateAdvancedThroughNestedOperations() throws Throwable {
    // Given: WAL [A_OP@0, B_OP@1, B_RET@2, A_RET@3]
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(0L, "self-caller", 0, "com.example.Outer", "a"));
    entries.add(makeOperation(1L, "self-caller", 1, "com.example.Inner", "b"));
    entries.add(makeReturnValue(2L, "self-caller", 2, "10", "java.lang.String"));
    entries.add(makeReturnValue(3L, "self-caller", 3, "20", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(index, new ReplayPolicy(), new ReplayObjectStore(), detector, gate);

    MinimalDispatcher outerDispatcher = new MinimalDispatcher();
    setRunOptions(outerDispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(outerDispatcher, ctx);

    MinimalDispatcher innerDispatcher = new MinimalDispatcher();
    setRunOptions(innerDispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(innerDispatcher, ctx);

    ProceedingJoinPoint innerPjp = buildPjp("com.example.Inner", "b", new Object[] {}, "10");
    ProceedingJoinPoint outerPjp =
        buildPjpWithNestedDispatch(
            "com.example.Outer", "a", new Object[] {}, innerDispatcher, innerPjp, "20");

    // When
    outerDispatcher.dispatch(outerPjp);

    // Then: gate advanced through both completions, ending at offset 3
    assertThat(gate.getCompletedOffset(), is(3L));
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

  /**
   * Creates a mock {@link ProceedingJoinPoint} where proceed() triggers a nested dispatch through
   * the given inner dispatcher.
   */
  private static ProceedingJoinPoint buildPjpWithNestedDispatch(
      String className,
      String methodName,
      Object[] args,
      BaseExecMessageDispatcher innerDispatcher,
      ProceedingJoinPoint innerPjp,
      Object outerReturnValue)
      throws Throwable {
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
    when(pjp.proceed(any(Object[].class)))
        .thenAnswer(
            inv -> {
              // Simulate nested dispatch
              innerDispatcher.dispatch(innerPjp);
              return outerReturnValue;
            });

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
    imc.setParameters(new Parameter[0]);
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

  /** Creates a COMPLETION WalEntry with an object ref (for constructor results). */
  private static WalEntry makeReturnValueWithRef(
      long offset, String threadName, int builderSeq, int ref) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    ReturnValue rv = new ReturnValue();
    Obj obj = new Obj();
    obj.setRef(ref);
    Class clazz = new Class();
    clazz.setName("java.lang.Object");
    obj.setClazz(clazz);
    rv.setObject(obj);
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
    protected List<Parameter> getParameterList(ExecMessage execMessage) {
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
