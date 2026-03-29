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
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.core.replay.DivergenceDetector;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayGate;
import io.quasient.pal.core.replay.ReplayObjectStore;
import io.quasient.pal.core.replay.ReplayPolicy;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceFieldPut;
import io.quasient.pal.messages.colfer.InstanceFieldPutDone;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.ReturnValue;
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
 * Unit tests for the STUB_WITH_SIDE_EFFECTS dispatch path in {@code BaseExecMessageDispatcher}.
 * This action combines WAL-based stubbing (returning recorded return values without executing the
 * method) with field mutation replay (applying PUT_FIELD / PUT_STATIC operations from within the
 * stubbed span).
 */
public class ReplayDispatchStubWithSideEffectsTest {

  /** Thread name used in WAL entries. */
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
   * Verifies that STUB_WITH_SIDE_EFFECTS returns the WAL-recorded value and applies field mutations
   * from within the span, without invoking the original method.
   */
  @Test
  public void stubWithSideEffectsReturnsValueAndAppliesMutations() throws Throwable {
    // Given: WAL with span (10, 40) containing PUT_FIELD at offset 25
    //        (sets obj.name = "mutated"); policy returns STUB_WITH_SIDE_EFFECTS;
    //        completion at 40 has return value "result"
    MutationTarget target = new MutationTarget();
    target.name = "original";

    ReplayObjectStore objectStore = new ReplayObjectStore();
    objectStore.register(1, target);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(10L, THREAD_NAME, 0, "com.example.Foo", "enrich"));
    entries.add(
        makeInstanceFieldPutEntry(
            25L, THREAD_NAME, MutationTarget.class.getName(), "name", "mutated", 1));
    entries.add(makeInstanceFieldPutDoneEntry(26L, THREAD_NAME));
    entries.add(makeReturnValue(40L, THREAD_NAME, 3, "\"result\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubWithSideEffectsPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(index, policy, objectStore, detector, new ReplayGate(true));

    ReplayDispatchStubTest.StubDispatcher dispatcher = new ReplayDispatchStubTest.StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "enrich", new Object[] {}, "live-value");

    // When: dispatchReplay called
    Object result = dispatcher.dispatch(pjp);

    // Then: Returns "result"; obj.name is "mutated"; method not invoked
    assertThat(result, is("result"));
    assertThat(target.name, is("mutated"));
    verify(pjp, never()).proceed(any(Object[].class));
  }

  /**
   * Verifies that STUB_WITH_SIDE_EFFECTS advances the cursor past the span's completion offset and
   * advances the replay gate, matching STUB_FROM_WAL span-skipping behavior.
   */
  @Test
  public void stubWithSideEffectsSkipsSpanLikeStubFromWal() throws Throwable {
    // Given: WAL with span (10, 40); policy returns STUB_WITH_SIDE_EFFECTS
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(10L, THREAD_NAME, 0, "com.example.Foo", "process"));
    entries.add(makeReturnValue(40L, THREAD_NAME, 1, "\"val\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubWithSideEffectsPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx = new ReplayContext(index, policy, new ReplayObjectStore(), detector, gate);

    ReplayDispatchStubTest.StubDispatcher dispatcher = new ReplayDispatchStubTest.StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "process", new Object[] {}, "live");

    // When: dispatchReplay called
    dispatcher.dispatch(pjp);

    // Then: Cursor advanced past 40; gate advanced to 40
    assertThat(ctx.getCursor(THREAD_NAME).isExhausted(), is(true));
    assertThat(gate.getCompletedOffset(), is(40L));
  }

  /**
   * Verifies that STUB_WITH_SIDE_EFFECTS handles void methods correctly — returns null while still
   * applying field mutations from the span.
   */
  @Test
  public void stubWithSideEffectsHandlesVoidMethodWithMutations() throws Throwable {
    // Given: Void method span with PUT_FIELD mutations; policy returns STUB_WITH_SIDE_EFFECTS
    MutationTarget target = new MutationTarget();
    target.name = "before";

    ReplayObjectStore objectStore = new ReplayObjectStore();
    objectStore.register(1, target);

    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(10L, THREAD_NAME, 0, "com.example.Foo", "mutate"));
    entries.add(
        makeInstanceFieldPutEntry(
            15L, THREAD_NAME, MutationTarget.class.getName(), "name", "after", 1));
    entries.add(makeInstanceFieldPutDoneEntry(16L, THREAD_NAME));
    entries.add(makeVoidReturnValue(20L, THREAD_NAME, 2));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubWithSideEffectsPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(index, policy, objectStore, detector, new ReplayGate(true));

    ReplayDispatchStubTest.StubDispatcher dispatcher = new ReplayDispatchStubTest.StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "mutate", new Object[] {}, null);

    // When: dispatchReplay called
    Object result = dispatcher.dispatch(pjp);

    // Then: Returns null; field mutations applied
    assertThat(result, is(nullValue()));
    assertThat(target.name, is("after"));
    verify(pjp, never()).proceed(any(Object[].class));
  }

  // ──────────────────── Helpers ────────────────────

  /** Creates a {@link ReplayPolicy} that uses STUB_WITH_SIDE_EFFECTS for all operations. */
  private static ReplayPolicy stubWithSideEffectsPolicy() {
    return new ReplayPolicy(
        Collections.emptyList(), ReplayPolicy.ReplayAction.STUB_WITH_SIDE_EFFECTS);
  }

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

  /** Creates an EXEC_PUT_FIELD entry with a string value. */
  private static WalEntry makeInstanceFieldPutEntry(
      long offset,
      String threadName,
      String className,
      String fieldName,
      String value,
      int objectRef) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    InstanceFieldPut put = new InstanceFieldPut();
    Class clazz = new Class();
    clazz.setName(className);
    put.setClazz(clazz);
    put.setObjectRef(objectRef);

    Field field = new Field();
    field.setName(fieldName);
    put.setField(field);

    Obj valueObj = new Obj();
    Class valueClazz = new Class();
    valueClazz.setName("java.lang.String");
    valueObj.setClazz(valueClazz);
    valueObj.setValue("\"" + value + "\"");
    put.setValueObject(valueObj);

    msg.setInstanceFieldPut(put);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates an EXEC_PUT_FIELD_DONE completion entry. */
  private static WalEntry makeInstanceFieldPutDoneEntry(long offset, String threadName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq((int) offset);

    InstanceFieldPutDone done = new InstanceFieldPutDone();
    Class clazz = new Class();
    clazz.setName("com.example.Test");
    Field doneField = new Field();
    doneField.setName("done");
    doneField.setClazz(clazz);
    done.setField(doneField);
    msg.setInstanceFieldPutDone(done);

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

  /** Creates a COMPLETION WalEntry with a void return value. */
  private static WalEntry makeVoidReturnValue(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);

    return WalEntry.fromExecMessage(offset, msg);
  }

  // ──────────────────── Test target class ────────────────────

  /** Test target for field mutation verification. */
  static class MutationTarget {

    /** A string field for testing mutations. */
    String name;
  }
}
