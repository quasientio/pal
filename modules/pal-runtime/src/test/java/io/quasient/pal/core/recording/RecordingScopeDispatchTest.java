/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.recording;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.common.runtime.Dispatcher;
import io.quasient.pal.core.execution.java.GetInstanceVariableDispatcher;
import io.quasient.pal.core.execution.java.InstanceMethodDispatcher;
import io.quasient.pal.core.execution.java.PjpBuilder;
import io.quasient.pal.core.execution.java.reflect.ReflectionHelper;
import io.quasient.pal.core.intercept.InterceptCheckResult;
import io.quasient.pal.core.intercept.InterceptChecker;
import io.quasient.pal.core.replay.DivergenceDetector;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayGate;
import io.quasient.pal.core.replay.ReplayObjectStore;
import io.quasient.pal.core.replay.ReplayPolicy;
import io.quasient.pal.core.runtime.objects.ConcurrentHashMapObjectLookupStore;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.Test;

/**
 * Tests verifying that {@link io.quasient.pal.core.execution.java.BaseExecMessageDispatcher}
 * correctly uses {@link RecordingScope} to gate WAL/PUB writes in {@code dispatchInternal()} and
 * bypass WAL matching in {@code dispatchReplay()}.
 *
 * <p>These tests exercise the boolean logic of the scope integration in the dispatch hot path.
 * Since {@code BaseExecMessageDispatcher} is package-private, tests use public concrete dispatchers
 * ({@link InstanceMethodDispatcher}, {@link GetInstanceVariableDispatcher}) with reflection-based
 * injection of the {@code recordingScope} field and mock {@link ProceedingJoinPoint} instances
 * built via {@link PjpBuilder}.
 *
 * @see RecordingScope
 * @see io.quasient.pal.core.execution.java.BaseExecMessageDispatcher
 */
public class RecordingScopeDispatchTest {

  /** A real method to use for building PJP mocks with proper signatures. */
  private static final Method SAMPLE_METHOD;

  /** A real field to use for building field-get PJP mocks with proper signatures. */
  private static final Field SAMPLE_FIELD;

  static {
    try {
      SAMPLE_METHOD = SampleTarget.class.getMethod("doWork");
      SAMPLE_FIELD = SampleTarget.class.getField("counter");
    } catch (NoSuchMethodException | NoSuchFieldException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /** Target class providing real Method and Field objects for PJP mock signatures. */
  @SuppressWarnings("unused")
  public static class SampleTarget {
    public int counter;

    public String doWork() {
      return "ok";
    }
  }

  // ──────────────────── Reflection helpers ────────────────────

  /**
   * Finds a declared field in the class hierarchy by walking up from the given class. This avoids
   * needing to reference package-private superclasses like {@code AbstractDispatcher} directly.
   */
  @SuppressWarnings("PMD.UnnecessaryFullyQualifiedName") // Class clashes with colfer.Class
  private static java.lang.reflect.Field findDeclaredField(
      java.lang.Class<?> clazz, String fieldName) {
    java.lang.Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(fieldName);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new RuntimeException("Field not found: " + fieldName + " in hierarchy of " + clazz);
  }

  @SuppressWarnings("PMD.UnnecessaryFullyQualifiedName") // Class clashes with colfer.Class
  private static void setFieldValue(Dispatcher d, String fieldName, Object value) throws Exception {
    java.lang.reflect.Field f = findDeclaredField(d.getClass(), fieldName);
    f.setAccessible(true);
    f.set(d, value);
  }

  // ──────────────────── Factory helpers ────────────────────

  /** Creates a mock OutboundMessageGateway that returns the wrapped ExecMessage. */
  private static OutboundMessageGateway mockGateway() {
    OutboundMessageGateway gw = mock(OutboundMessageGateway.class);
    when(gw.sendExecMessage(any(), any()))
        .thenAnswer(
            invocation -> {
              Object arg = invocation.getArgument(0);
              if (arg instanceof Message messageArg) {
                return messageArg.getExecMessage();
              }
              throw new IllegalArgumentException("Expected Message, got " + arg.getClass());
            });
    return gw;
  }

  /** Creates an InstanceMethodDispatcher wired with the given gateway and run options. */
  private static InstanceMethodDispatcher createMethodDispatcher(
      OutboundMessageGateway gw, EnumSet<RunOptions> options) {
    return new InstanceMethodDispatcher(
        UUID.randomUUID(),
        options,
        new MessageBuilder(),
        gw,
        new ReflectionHelper(),
        ConcurrentHashMapObjectLookupStore.createSyncManaged());
  }

  /** Creates a GetInstanceVariableDispatcher wired with the given gateway and run options. */
  private static GetInstanceVariableDispatcher createFieldGetDispatcher(
      OutboundMessageGateway gw, EnumSet<RunOptions> options) {
    return new GetInstanceVariableDispatcher(
        UUID.randomUUID(),
        options,
        new MessageBuilder(),
        gw,
        ConcurrentHashMapObjectLookupStore.createSyncManaged());
  }

  // ──────────────────── PJP builders ────────────────────

  /**
   * Builds a PJP for a method call using PjpBuilder with a real Method signature. The recording
   * scope check extracts class/method name from the signature, so a real Method ensures correct
   * type resolution through ContextFactory.
   */
  private static ProceedingJoinPoint buildMethodPjp(Object returnValue) throws Throwable {
    return PjpBuilder.create()
        .kindMethodCall()
        .methodExecutionSignature(SAMPLE_METHOD)
        .source("SampleTarget.java", 1, SampleTarget.class)
        .sender(new SampleTarget())
        .target(new SampleTarget())
        .args(new Object[] {})
        .proceedBehavior(() -> returnValue)
        .build();
  }

  /**
   * Builds a PJP for a field-get with a real Field signature. The recording scope check extracts
   * class/field name from the FieldSignature.
   */
  private static ProceedingJoinPoint buildFieldGetPjp(Object returnValue) throws Throwable {
    return PjpBuilder.create()
        .kindFieldGet()
        .fieldExecutionSignature(SAMPLE_FIELD)
        .source("SampleTarget.java", 1, SampleTarget.class)
        .sender(new SampleTarget())
        .target(new SampleTarget())
        .args(new Object[0])
        .proceedBehavior(() -> returnValue)
        .build();
  }

  /**
   * Builds a mock PJP for replay tests. Uses mock signatures (not PjpBuilder) because the replay
   * path does not call ContextFactory.
   */
  @SuppressWarnings("PMD.UnnecessaryFullyQualifiedName") // Class clashes with colfer.Class
  private static ProceedingJoinPoint buildReplayPjp(
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

  // ──────────────────── Tests: dispatchInternal (WAL/PUB gating) ────────────────────

  /**
   * Verifies that when {@code recordingScope.isInScope()} returns true and {@code WITH_WAL} is set,
   * the operation produces ExecMessages that are written to the WAL via the {@code
   * OutboundMessageGateway}.
   */
  @Test
  public void inScopeOperationRecordedToWal() throws Throwable {
    // Given: dispatcher with scope that includes everything (default RECORD)
    OutboundMessageGateway gw = mockGateway();
    InstanceMethodDispatcher d = createMethodDispatcher(gw, EnumSet.of(RunOptions.WITH_WAL));
    setFieldValue(d, "recordingScope", new RecordingScope(List.of(), RecordingScopeAction.RECORD));

    ProceedingJoinPoint pjp = buildMethodPjp("ok");

    // When
    Object result = d.dispatch(pjp);

    // Then: operation recorded to WAL (BEFORE + AFTER messages sent)
    assertThat(result, is("ok"));
    verify(gw, times(2)).sendExecMessage(any(), any());
  }

  /**
   * Verifies that when {@code recordingScope.isInScope()} returns false, {@code withPubOrWal}
   * becomes false, preventing ExecMessage creation and WAL write. The underlying method should
   * still be invoked normally (via {@code pjp.proceed()}).
   */
  @Test
  public void outOfScopeOperationSkipsWalWrite() throws Throwable {
    // Given: dispatcher with scope that skips everything (default SKIP)
    OutboundMessageGateway gw = mockGateway();
    InstanceMethodDispatcher d = createMethodDispatcher(gw, EnumSet.of(RunOptions.WITH_WAL));
    setFieldValue(d, "recordingScope", new RecordingScope(List.of(), RecordingScopeAction.SKIP));

    ProceedingJoinPoint pjp = buildMethodPjp("ok");

    // When
    Object result = d.dispatch(pjp);

    // Then: method still executes but no messages sent to gateway
    assertThat(result, is("ok"));
    verify(gw, times(0)).sendExecMessage(any(), any());
  }

  /**
   * Verifies that when {@code recordingScope.isInScope()} returns false but intercepts are
   * configured for the operation, the intercept still fires. Intercepts gate independently from
   * recording scope — the intercept OR condition in {@code needsBeforeMessages}/{@code
   * needsAfterMessages} is separate from {@code withPubOrWal}.
   */
  @Test
  public void outOfScopeOperationStillFiresIntercepts() throws Throwable {
    // Given: dispatcher with out-of-scope recording AND WITH_INTERCEPTS
    OutboundMessageGateway gw = mockGateway();
    InstanceMethodDispatcher d =
        createMethodDispatcher(gw, EnumSet.of(RunOptions.WITH_WAL, RunOptions.WITH_INTERCEPTS));
    setFieldValue(d, "recordingScope", new RecordingScope(List.of(), RecordingScopeAction.SKIP));

    // Mock InterceptChecker to return a result indicating intercepts are registered
    InterceptChecker checker = mock(InterceptChecker.class);
    InterceptCheckResult checkResult =
        new InterceptCheckResult(Collections.emptyList(), Collections.emptyList());
    when(checker.checkIntercepts(any(ProceedingJoinPoint.class), any(MessageType.class), any()))
        .thenReturn(checkResult);
    setFieldValue(d, "interceptChecker", checker);

    ProceedingJoinPoint pjp = buildMethodPjp("ok");

    // When
    Object result = d.dispatch(pjp);

    // Then: intercept checker was consulted even though recording scope is SKIP
    assertThat(result, is("ok"));
    verify(checker, times(2)).checkIntercepts(any(ProceedingJoinPoint.class), any(), any());
  }

  /**
   * Verifies that a FIELD_GET operation that is out of scope produces no ExecMessage and no WAL
   * write. This confirms that field dispatchers (which inherit from {@code
   * BaseExecMessageDispatcher} via {@code FieldOpDispatcher}) also respect the recording scope.
   */
  @Test
  public void outOfScopeFieldGetSkipsWal() throws Throwable {
    // Given: field-get dispatcher with out-of-scope recording
    OutboundMessageGateway gw = mockGateway();
    GetInstanceVariableDispatcher d = createFieldGetDispatcher(gw, EnumSet.of(RunOptions.WITH_WAL));
    setFieldValue(d, "recordingScope", new RecordingScope(List.of(), RecordingScopeAction.SKIP));

    ProceedingJoinPoint pjp = buildFieldGetPjp(42);

    // When
    Object result = d.dispatch(pjp);

    // Then: field value returned correctly but no messages sent to gateway
    assertThat(result, is(42));
    verify(gw, times(0)).sendExecMessage(any(), any());
  }

  // ──────────────────── Tests: dispatchReplay (scope bypass) ────────────────────

  /** Thread name used in WAL entries, matching the self-caller convention. */
  private static final String THREAD_NAME = "self-caller";

  /**
   * Verifies that in {@code dispatchReplay()}, when {@code recordingScope.isInScope()} returns
   * false, the operation invokes directly via {@code pjp.proceed()} without consuming WAL cursor
   * entries. Since out-of-scope operations produce no WAL entries during recording, they should not
   * attempt to match against the WAL during replay.
   */
  @Test
  public void replayBypassesOutOfScopeOperation() throws Throwable {
    String originalThreadName = Thread.currentThread().getName();
    Thread.currentThread().setName(THREAD_NAME);
    try {
      // Given: WAL with one in-scope entry; dispatcher with out-of-scope recording
      List<WalEntry> entries = new ArrayList<>();
      entries.add(makeOperation(0L, THREAD_NAME, 0, "com.example.Foo", "bar"));
      entries.add(makeReturnValue(1L, THREAD_NAME, 1, "42", "java.lang.String"));
      WalIndex index = WalIndex.build(entries);
      DivergenceDetector detector =
          new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
      ReplayContext ctx =
          new ReplayContext(
              index, new ReplayPolicy(), new ReplayObjectStore(), detector, new ReplayGate(true));

      OutboundMessageGateway gw = mockGateway();
      InstanceMethodDispatcher d = createMethodDispatcher(gw, EnumSet.of(RunOptions.WITH_REPLAY));
      setFieldValue(d, "replayContext", ctx);
      setFieldValue(d, "recordingScope", new RecordingScope(List.of(), RecordingScopeAction.SKIP));

      ProceedingJoinPoint pjp =
          buildReplayPjp("com.example.Foo", "bar", new Object[] {}, "live-value");

      // When: dispatch an out-of-scope operation during replay
      Object result = d.dispatch(pjp);

      // Then: operation invoked directly via pjp.proceed(), WAL cursor NOT consumed
      assertThat(result, is("live-value"));
      verify(pjp, times(1)).proceed(any(Object[].class));
      // Cursor should still have entries (not consumed)
      assertThat(ctx.getCursor(THREAD_NAME).isExhausted(), is(false));
      assertThat(detector.hasDivergences(), is(false));
    } finally {
      Thread.currentThread().setName(originalThreadName);
    }
  }

  /**
   * Verifies that in {@code dispatchReplay()}, when {@code recordingScope.isInScope()} returns
   * true, the normal replay logic proceeds — the WAL cursor is consulted, signature matching
   * occurs, and the recorded return value is used.
   */
  @Test
  public void replayProcessesInScopeOperation() throws Throwable {
    String originalThreadName = Thread.currentThread().getName();
    Thread.currentThread().setName(THREAD_NAME);
    try {
      // Given: WAL with one entry; dispatcher with in-scope recording
      List<WalEntry> entries = new ArrayList<>();
      entries.add(makeOperation(0L, THREAD_NAME, 0, "com.example.Foo", "bar"));
      entries.add(makeReturnValue(1L, THREAD_NAME, 1, "42", "java.lang.String"));
      WalIndex index = WalIndex.build(entries);
      DivergenceDetector detector =
          new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
      ReplayContext ctx =
          new ReplayContext(
              index, new ReplayPolicy(), new ReplayObjectStore(), detector, new ReplayGate(true));

      OutboundMessageGateway gw = mockGateway();
      InstanceMethodDispatcher d = createMethodDispatcher(gw, EnumSet.of(RunOptions.WITH_REPLAY));
      setFieldValue(d, "replayContext", ctx);
      setFieldValue(
          d, "recordingScope", new RecordingScope(List.of(), RecordingScopeAction.RECORD));

      ProceedingJoinPoint pjp = buildReplayPjp("com.example.Foo", "bar", new Object[] {}, "42");

      // When: dispatch an in-scope operation during replay
      Object result = d.dispatch(pjp);

      // Then: normal replay: WAL cursor consumed, return value matches
      assertThat(result, is("42"));
      verify(pjp, times(1)).proceed(any(Object[].class));
      assertThat(ctx.getCursor(THREAD_NAME).isExhausted(), is(true));
      assertThat(detector.hasDivergences(), is(false));
    } finally {
      Thread.currentThread().setName(originalThreadName);
    }
  }

  // ──────────────────── WAL entry helpers (from ReplayDispatchTest) ────────────────────

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

  /** Creates a COMPLETION (return value) WalEntry. */
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
}
