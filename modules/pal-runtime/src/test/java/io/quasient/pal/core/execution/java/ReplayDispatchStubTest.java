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
 * Unit tests for the STUB_FROM_WAL dispatch path in {@code
 * BaseExecMessageDispatcher.dispatchReplay} — verifying return value reconstruction, phantom
 * cascading, span skipping, and replay coordination.
 *
 * <p>These tests construct WalIndex/ReplayContext manually with known WAL entries and verify
 * correct stubbing behavior at a unit level.
 */
public class ReplayDispatchStubTest {

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
   * Verifies that STUB_FROM_WAL correctly reconstructs a primitive int return value from the WAL
   * completion entry without invoking the actual method.
   */
  @Test
  public void stubFromWalReturnsPrimitiveValue() throws Throwable {
    // Given: WAL with operation at offset 10 and completion at offset 20 returning int 42;
    //        policy returns STUB_FROM_WAL for this operation
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(10L, THREAD_NAME, 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(20L, THREAD_NAME, 1, "42", "java.lang.Integer"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(index, policy, new ReplayObjectStore(), detector, new ReplayGate(true));

    StubDispatcher dispatcher = new StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "bar", new Object[] {}, "live-value");

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then: returns 42 (from WAL), method not invoked, cursor exhausted
    assertThat(result, is(42));
    verify(pjp, never()).proceed(any(Object[].class));
    assertThat(ctx.getCursor(THREAD_NAME).isExhausted(), is(true));
  }

  /**
   * Verifies that STUB_FROM_WAL correctly reconstructs a String return value from the WAL
   * completion entry.
   */
  @Test
  public void stubFromWalReturnsStringValue() throws Throwable {
    // Given: WAL with method returning String "hello"; policy returns STUB_FROM_WAL
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(10L, THREAD_NAME, 0, "com.example.Foo", "greet"));
    entries.add(makeReturnValue(20L, THREAD_NAME, 1, "\"hello\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(index, policy, new ReplayObjectStore(), detector, new ReplayGate(true));

    StubDispatcher dispatcher = new StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "greet", new Object[] {}, "live");

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then: returns "hello" from WAL; method not invoked
    assertThat(result, is("hello"));
    verify(pjp, never()).proceed(any(Object[].class));
  }

  /**
   * Verifies that STUB_FROM_WAL returns null for a void method and advances the cursor past the
   * completion entry.
   */
  @Test
  public void stubFromWalReturnsNullForVoidMethod() throws Throwable {
    // Given: WAL with void method; policy returns STUB_FROM_WAL
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(10L, THREAD_NAME, 0, "com.example.Foo", "doSomething"));
    entries.add(makeVoidReturnValue(20L, THREAD_NAME, 1));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(index, policy, new ReplayObjectStore(), detector, new ReplayGate(true));

    StubDispatcher dispatcher = new StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "doSomething", new Object[] {}, null);

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then: returns null; method not invoked; cursor advanced past completion
    assertThat(result, is(nullValue()));
    verify(pjp, never()).proceed(any(Object[].class));
    assertThat(ctx.getCursor(THREAD_NAME).isExhausted(), is(true));
  }

  /**
   * Verifies that stubbing a constructor or method returning a reference-only object (no serialized
   * value) registers the WAL ref as a phantom in the object store.
   */
  @Test
  public void stubFromWalRegistersPhantomForUnreconstructableObject() throws Throwable {
    // Given: WAL with method returning reference-only object (ref=99, no value);
    //        policy returns STUB_FROM_WAL
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(10L, THREAD_NAME, 0, "com.example.Foo", "create"));
    entries.add(makeReturnValueWithRef(20L, THREAD_NAME, 1, 99));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayObjectStore objectStore = new ReplayObjectStore();
    ReplayContext ctx =
        new ReplayContext(index, policy, objectStore, detector, new ReplayGate(true));

    StubDispatcher dispatcher = new StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "create", new Object[] {}, null);

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then: returns null; objectStore.isPhantom(99) is true
    assertThat(result, is(nullValue()));
    assertThat(objectStore.isPhantom(99), is(true));
    verify(pjp, never()).proceed(any(Object[].class));
  }

  /**
   * Verifies that phantom cascading overrides the policy: when the target object of a method call
   * is a phantom, the call is auto-stubbed from WAL regardless of the policy returning RE_EXECUTE.
   */
  @Test
  public void phantomTargetAutoStubsRegardlessOfPolicy() throws Throwable {
    // Given: WAL with method call on object ref 99; objectStore has ref 99 as phantom;
    //        policy returns RE_EXECUTE (default)
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperationWithTarget(10L, THREAD_NAME, 0, "com.example.Svc", "process", 99));
    entries.add(makeReturnValue(20L, THREAD_NAME, 1, "\"stubbed\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = new ReplayPolicy(); // default: RE_EXECUTE
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayObjectStore objectStore = new ReplayObjectStore();
    objectStore.registerPhantom(99);
    ReplayContext ctx =
        new ReplayContext(index, policy, objectStore, detector, new ReplayGate(true));

    StubDispatcher dispatcher = new StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Svc", "process", new Object[] {}, "live");

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then: returns WAL value (auto-stubbed); method not invoked (phantom cascading overrides
    // policy)
    assertThat(result, is("stubbed"));
    verify(pjp, never()).proceed(any(Object[].class));
  }

  /**
   * Verifies that stubbing an operation skips its entire span, including all nested operations
   * within the span boundaries.
   */
  @Test
  public void stubFromWalSkipsEntireSpan() throws Throwable {
    // Given: WAL with span from offset 10 to 40, containing nested operations at 20, 30;
    //        policy stubs offset 10
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(10L, THREAD_NAME, 0, "com.example.Outer", "outer"));
    entries.add(makeOperation(20L, THREAD_NAME, 1, "com.example.Inner", "inner1"));
    entries.add(makeReturnValue(30L, THREAD_NAME, 2, "\"x\"", "java.lang.String"));
    entries.add(makeReturnValue(40L, THREAD_NAME, 3, "\"result\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(index, policy, new ReplayObjectStore(), detector, new ReplayGate(true));

    StubDispatcher dispatcher = new StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Outer", "outer", new Object[] {}, "live");

    // When
    Object result = dispatcher.dispatch(pjp);

    // Then: cursor advanced past offset 40; nested operations at 20, 30 skipped
    assertThat(result, is("result"));
    assertThat(ctx.getCursor(THREAD_NAME).isExhausted(), is(true));
    verify(pjp, never()).proceed(any(Object[].class));
  }

  /**
   * Verifies that stubbing an operation advances the replay gate to the span's completion offset,
   * unblocking other threads that may be waiting.
   */
  @Test
  public void stubFromWalAdvancesGateToCompletionOffset() throws Throwable {
    // Given: WAL with span (10, 40); policy stubs
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeOperation(10L, THREAD_NAME, 0, "com.example.Foo", "bar"));
    entries.add(makeReturnValue(40L, THREAD_NAME, 1, "\"val\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx = new ReplayContext(index, policy, new ReplayObjectStore(), detector, gate);

    StubDispatcher dispatcher = new StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "bar", new Object[] {}, "live");

    // When
    dispatcher.dispatch(pjp);

    // Then: replay gate advanced to offset 40
    assertThat(gate.getCompletedOffset(), is(40L));
  }

  /**
   * Verifies that stubbing an entry-point operation marks it as handled in the replay context,
   * preventing the replay input injector from re-processing it.
   */
  @Test
  public void stubFromWalMarksEntryPointHandled() throws Throwable {
    // Given: WAL entry at offset 10 is an entry point; policy stubs
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(10L, THREAD_NAME, 0, "com.example.Foo", "init"));
    entries.add(makeReturnValue(20L, THREAD_NAME, 1, "\"done\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(index, policy, new ReplayObjectStore(), detector, new ReplayGate(true));

    StubDispatcher dispatcher = new StubDispatcher();
    setRunOptions(dispatcher, EnumSet.of(RunOptions.WITH_REPLAY));
    setReplayContext(dispatcher, ctx);

    ProceedingJoinPoint pjp = buildPjp("com.example.Foo", "init", new Object[] {}, "live");

    // When
    dispatcher.dispatch(pjp);

    // Then: replayContext.isEntryPointHandled(10) returns true
    assertThat(ctx.isEntryPointHandled(10L), is(true));
  }

  // ──────────────────── Helpers ────────────────────

  /** Creates a {@link ReplayPolicy} that stubs all operations from WAL. */
  private static ReplayPolicy stubAllPolicy() {
    return new ReplayPolicy(Collections.emptyList(), ReplayPolicy.ReplayAction.STUB_FROM_WAL);
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
    imc.setParameters(new Parameter[0]);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates an OPERATION WalEntry for an instance method call with a target object ref. */
  private static WalEntry makeOperationWithTarget(
      long offset,
      String threadName,
      int builderSeq,
      String className,
      String methodName,
      int objectRef) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    InstanceMethodCall imc = new InstanceMethodCall();
    Class clazz = new Class();
    clazz.setName(className);
    imc.setClazz(clazz);
    imc.setName(methodName);
    imc.setObjectRef(objectRef);
    imc.setParameters(new Parameter[0]);
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

  /** Creates a COMPLETION WalEntry with an object ref only (reference-only, no value). */
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
   * Minimal concrete dispatcher subclass for testing the stub dispatch path. All abstract methods
   * return stubs; only the replay-relevant methods (inherited from {@code
   * BaseExecMessageDispatcher}) have real behavior.
   */
  static class StubDispatcher extends BaseExecMessageDispatcher {

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
