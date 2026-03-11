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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.core.replay.DivergenceDetector;
import io.quasient.pal.core.replay.ReplayContext;
import io.quasient.pal.core.replay.ReplayCursor;
import io.quasient.pal.core.replay.ReplayGate;
import io.quasient.pal.core.replay.ReplayObjectStore;
import io.quasient.pal.core.replay.ReplayPolicy;
import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import io.quasient.pal.core.service.RunOptions;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.core.transport.gateway.OutboundMessageGateway;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for phantom stub handling in {@link BaseExecMessageDispatcher#dispatchIncoming}.
 *
 * <p>These tests verify that when a replay injection encounters an object not in {@link
 * ReplayObjectStore} (created by unweaved code), the system falls back to returning the
 * WAL-recorded value if the replay policy says to stub.
 *
 * <p>Covers the following paths:
 *
 * <ul>
 *   <li>Loading phase fails, policy is STUB_FROM_WAL → returns WAL value (phantom stub)
 *   <li>Loading phase fails, policy is RE_EXECUTE → skips entry point, returns empty response
 *   <li>Cursor and gate advancement via {@code advancePhantomStub()}
 *   <li>Loading succeeds but policy is STUB_FROM_WAL → returns WAL value instead of executing
 *   <li>Phantom cascading: stubbed return ref becomes phantom, subsequent ops auto-stub
 * </ul>
 */
public class DispatchIncomingPhantomStubTest {

  /** Thread name matching the WAL thread name for entry-point operations. */
  private static final String WAL_THREAD_NAME = "fx-thread-1";

  /** Original thread name, restored after each test. */
  private String originalThreadName;

  /** Sets the current thread name to match the WAL entries' thread name. */
  @Before
  public void setThreadName() {
    originalThreadName = Thread.currentThread().getName();
    Thread.currentThread().setName(WAL_THREAD_NAME);
  }

  /** Restores the original thread name after each test. */
  @After
  public void restoreThreadName() {
    Thread.currentThread().setName(originalThreadName);
  }

  /**
   * Verifies that a phantom stub returns the WAL-recorded value when the target object is not in
   * the store and the replay policy action is STUB_FROM_WAL.
   */
  @Test
  public void phantomStub_returnsWalValueWhenObjectNotInStore() throws Exception {
    // Given: WAL with entry-point operation at offset 10 returning "phantom-result" at offset 20;
    //        policy returns STUB_FROM_WAL; loading phase throws (target not in store)
    List<WalEntry> entries = new ArrayList<>();
    entries.add(
        makeEntryPointOperation(10L, WAL_THREAD_NAME, 0, "com.example.KeyEvent", "getCode"));
    entries.add(makeReturnValue(20L, WAL_THREAD_NAME, 1, "\"phantom-result\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    ReplayObjectStore objectStore = new ReplayObjectStore();
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            policy,
            objectStore,
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    // Push pending injection (simulates what ReplayInputInjector does)
    ctx.pushPendingInjection(WAL_THREAD_NAME, 10L);

    PhantomFailingDispatcher dispatcher = createPhantomDispatcher(ctx);

    ExecMessage msg = createInstanceMethodExecMessage("com.example.KeyEvent", "getCode", true);

    // When
    ExecMessage response = dispatcher.dispatchIncoming(msg, MessageChannelType.REPLAY_INJECTION);

    // Then: response contains the WAL-recorded return value
    assertThat(response, is(notNullValue()));
    assertThat(response.getReturnValue(), is(notNullValue()));
    assertThat(response.getReturnValue().getObject(), is(notNullValue()));
    assertThat(response.getReturnValue().getObject().getValue(), is("\"phantom-result\""));
  }

  /**
   * Verifies that phantom stub is skipped when the replay policy action is RE_EXECUTE, and the
   * entry point is still advanced so the injector doesn't wait forever.
   */
  @Test
  public void phantomStub_skippedWhenPolicyIsReExecute() throws Exception {
    // Given: WAL with entry-point operation at offset 10; policy returns RE_EXECUTE (default);
    //        loading phase throws (target not in store)
    List<WalEntry> entries = new ArrayList<>();
    entries.add(
        makeEntryPointOperation(10L, WAL_THREAD_NAME, 0, "com.example.KeyEvent", "getCode"));
    entries.add(makeVoidReturnValue(20L, WAL_THREAD_NAME, 1));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = new ReplayPolicy(); // default action: RE_EXECUTE
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            policy,
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    ctx.pushPendingInjection(WAL_THREAD_NAME, 10L);

    PhantomFailingDispatcher dispatcher = createPhantomDispatcher(ctx);

    ExecMessage msg = createInstanceMethodExecMessage("com.example.KeyEvent", "getCode", true);

    // When
    ExecMessage response = dispatcher.dispatchIncoming(msg, MessageChannelType.REPLAY_INJECTION);

    // Then: response is an empty skip response (no WAL value returned);
    //       cursor and gate are still advanced (so injector doesn't wait forever)
    assertThat(response, is(notNullValue()));
    assertThat(response.getReturnValue(), is(nullValue()));
    assertThat(gate.getCompletedOffset(), is(20L));
    assertThat(ctx.getCursor(WAL_THREAD_NAME).isExhausted(), is(true));
  }

  /**
   * Verifies that {@code advancePhantomStub} correctly advances both the cursor past the completion
   * offset and the gate to the completion offset.
   */
  @Test
  public void phantomStub_advancesCursorAndGate() throws Exception {
    // Given: WAL with entry-point operation at offset 100, completion at offset 200;
    //        policy returns STUB_FROM_WAL; loading phase throws
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(100L, WAL_THREAD_NAME, 0, "com.example.Stage", "show"));
    entries.add(makeVoidReturnValue(200L, WAL_THREAD_NAME, 1));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            policy,
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    ctx.pushPendingInjection(WAL_THREAD_NAME, 100L);

    PhantomFailingDispatcher dispatcher = createPhantomDispatcher(ctx);

    ExecMessage msg = createInstanceMethodExecMessage("com.example.Stage", "show", true);

    // When
    dispatcher.dispatchIncoming(msg, MessageChannelType.REPLAY_INJECTION);

    // Then: cursor advanced past completion offset 200 (exhausted); gate at 200
    ReplayCursor cursor = ctx.getCursor(WAL_THREAD_NAME);
    assertThat("Cursor should be exhausted", cursor.isExhausted(), is(true));
    assertThat("Gate should be at completion offset", gate.getCompletedOffset(), is(200L));
    assertThat("Entry point should be marked handled", ctx.isEntryPointHandled(100L), is(true));
  }

  /**
   * Verifies that when the target object loads successfully but the replay policy action is
   * STUB_FROM_WAL, the WAL-recorded value is returned instead of executing the operation.
   */
  @Test
  public void phantomStub_handlesAlreadyLoadedTargetWithStubPolicy() throws Exception {
    // Given: WAL with entry-point operation at offset 10 returning "42" at offset 20;
    //        policy is STUB_FROM_WAL; loading succeeds (target exists)
    List<WalEntry> entries = new ArrayList<>();
    entries.add(
        makeEntryPointOperation(10L, WAL_THREAD_NAME, 0, "com.example.TextField", "getText"));
    entries.add(makeReturnValue(20L, WAL_THREAD_NAME, 1, "\"recorded-text\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            policy,
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    ctx.pushPendingInjection(WAL_THREAD_NAME, 10L);

    // Use a dispatcher where loading succeeds (no exception thrown)
    LoadSuccessDispatcher dispatcher = createLoadSuccessDispatcher(ctx);

    ExecMessage msg = createInstanceMethodExecMessage("com.example.TextField", "getText", true);

    // When
    ExecMessage response = dispatcher.dispatchIncoming(msg, MessageChannelType.REPLAY_INJECTION);

    // Then: response contains the WAL-recorded value (not the live value)
    assertThat(response, is(notNullValue()));
    assertThat(response.getReturnValue(), is(notNullValue()));
    assertThat(response.getReturnValue().getObject(), is(notNullValue()));
    assertThat(response.getReturnValue().getObject().getValue(), is("\"recorded-text\""));
  }

  /**
   * Verifies that phantom stubs cascade: when a phantom stub returns a reference to another object
   * not in the store, that reference is registered as phantom, and subsequent operations on that
   * phantom object also trigger phantom stub handling.
   */
  @Test
  public void phantomStub_propagatesCascadingPhantomRefs() throws Exception {
    // Given: WAL with two entry points on the same thread:
    //   EP1: create() at offset 10 returns ref 99 (reference-only, no value → phantom)
    //   EP2: process() at offset 30 on ref 99 returns "cascaded-result"
    List<WalEntry> entries = new ArrayList<>();
    entries.add(makeEntryPointOperation(10L, WAL_THREAD_NAME, 0, "com.example.Factory", "create"));
    entries.add(makeReturnValueWithRef(20L, WAL_THREAD_NAME, 1, 99));
    entries.add(makeEntryPointOperation(30L, WAL_THREAD_NAME, 2, "com.example.Widget", "process"));
    entries.add(
        makeReturnValue(40L, WAL_THREAD_NAME, 3, "\"cascaded-result\"", "java.lang.String"));
    WalIndex index = WalIndex.build(entries);

    ReplayPolicy policy = stubAllPolicy();
    ReplayObjectStore objectStore = new ReplayObjectStore();
    ReplayGate gate = new ReplayGate(true);
    ReplayContext ctx =
        new ReplayContext(
            index,
            policy,
            objectStore,
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            gate);

    PhantomFailingDispatcher dispatcher = createPhantomDispatcher(ctx);

    // Step 1: first entry point creates a phantom ref
    ctx.pushPendingInjection(WAL_THREAD_NAME, 10L);
    ExecMessage msg1 = createInstanceMethodExecMessage("com.example.Factory", "create", true);
    dispatcher.dispatchIncoming(msg1, MessageChannelType.REPLAY_INJECTION);

    // Verify ref 99 is now phantom
    assertThat("Ref 99 should be phantom after first stub", objectStore.isPhantom(99), is(true));

    // Step 2: second entry point targets phantom ref 99 — should also be stubbed
    ctx.pushPendingInjection(WAL_THREAD_NAME, 30L);
    ExecMessage msg2 = createInstanceMethodExecMessage("com.example.Widget", "process", true);
    ExecMessage response = dispatcher.dispatchIncoming(msg2, MessageChannelType.REPLAY_INJECTION);

    // Then: second operation also returns WAL value via phantom stub cascading
    assertThat(response, is(notNullValue()));
    assertThat(response.getReturnValue(), is(notNullValue()));
    assertThat(response.getReturnValue().getObject(), is(notNullValue()));
    assertThat(response.getReturnValue().getObject().getValue(), is("\"cascaded-result\""));
    assertThat("Gate should reach last completion", gate.getCompletedOffset(), is(40L));
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
   * Creates a {@link PhantomFailingDispatcher} with required dependencies injected and replay mode
   * enabled.
   *
   * @param replayCtx the replay context
   * @return a configured dispatcher ready for testing phantom stub paths
   */
  private static PhantomFailingDispatcher createPhantomDispatcher(ReplayContext replayCtx)
      throws Exception {
    PhantomFailingDispatcher d = new PhantomFailingDispatcher();
    injectCommonFields(d, replayCtx);
    return d;
  }

  /**
   * Creates a {@link LoadSuccessDispatcher} with required dependencies injected and replay mode
   * enabled.
   *
   * @param replayCtx the replay context
   * @return a configured dispatcher where loading succeeds
   */
  private static LoadSuccessDispatcher createLoadSuccessDispatcher(ReplayContext replayCtx)
      throws Exception {
    LoadSuccessDispatcher d = new LoadSuccessDispatcher();
    injectCommonFields(d, replayCtx);
    return d;
  }

  /**
   * Injects common fields required by {@code dispatchIncoming} into any dispatcher subclass.
   *
   * @param d the dispatcher to configure
   * @param replayCtx the replay context
   */
  private static void injectCommonFields(AbstractDispatcher d, ReplayContext replayCtx)
      throws Exception {
    setField(
        d,
        "runOptions",
        EnumSet.of(RunOptions.WITH_REPLAY, RunOptions.WITH_WAL, RunOptions.WITH_WAL_INCOMING_RPC));
    setField(d, "replayContext", replayCtx);

    OutboundMessageGateway mockGateway = mock(OutboundMessageGateway.class);
    when(mockGateway.sendExecMessage(any(), any())).thenReturn(new ExecMessage());
    setField(d, "messageGateway", mockGateway);

    MessageBuilder messageBuilder =
        new MessageBuilder(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    setField(d, "messageBuilder", messageBuilder);
    setField(d, "peerUuid", UUID.fromString("00000000-0000-0000-0000-000000000002"));
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
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static ExecMessage createInstanceMethodExecMessage(
      String className, String methodName, boolean entryPoint) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(WAL_THREAD_NAME);
    InstanceMethodCall call = new InstanceMethodCall();
    call.setName(methodName);
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.setName(className);
    call.setClazz(clazz);
    call.setParameters(new Parameter[0]);
    msg.setInstanceMethodCall(call);
    msg.setEntryPoint(entryPoint);
    return msg;
  }

  /**
   * Creates an entry-point OPERATION {@link WalEntry} for an instance method call.
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
    imc.setParameters(new Parameter[0]);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a COMPLETION {@link WalEntry} with a string return value.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param value the serialized value (e.g., {@code "\"hello\""})
   * @param typeName the fully-qualified type name
   * @return a COMPLETION WalEntry with a typed return value
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static WalEntry makeReturnValue(
      long offset, String threadName, int builderSeq, String value, String typeName) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    ReturnValue rv = new ReturnValue();
    Obj obj = new Obj();
    obj.setValue(value);
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.setName(typeName);
    obj.setClazz(clazz);
    rv.setObject(obj);
    msg.setReturnValue(rv);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a COMPLETION {@link WalEntry} with a void return value.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a COMPLETION WalEntry for a void method
   */
  private static WalEntry makeVoidReturnValue(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a COMPLETION {@link WalEntry} with an object ref only (reference-only, no serialized
   * value). This triggers phantom registration because the value cannot be reconstructed.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param ref the object reference
   * @return a COMPLETION WalEntry with ref only
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private static WalEntry makeReturnValueWithRef(
      long offset, String threadName, int builderSeq, int ref) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    ReturnValue rv = new ReturnValue();
    Obj obj = new Obj();
    obj.setRef(ref);
    io.quasient.pal.messages.colfer.Class clazz = new io.quasient.pal.messages.colfer.Class();
    clazz.setName("java.lang.Object");
    obj.setClazz(clazz);
    rv.setObject(obj);
    msg.setReturnValue(rv);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /** Creates a {@link ReplayPolicy} that stubs all operations from WAL. */
  private static ReplayPolicy stubAllPolicy() {
    return new ReplayPolicy(Collections.emptyList(), ReplayAction.STUB_FROM_WAL);
  }

  /**
   * Minimal concrete subclass of {@link BaseExecMessageDispatcher} where the loading phase always
   * fails (throws {@link RuntimeException}). This simulates phantom objects: the target doesn't
   * exist in the {@link ReplayObjectStore}, so the dispatcher cannot resolve it.
   */
  static class PhantomFailingDispatcher extends BaseExecMessageDispatcher {

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
    protected List<Parameter> getParameterList(ExecMessage execMessage) {
      return Collections.emptyList();
    }

    @Override
    protected AccessibleObject loadAccessibleObject(
        ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args) {
      // Simulate failure to load: target class/method not found (phantom object scenario)
      throw new RuntimeException("Target object not in store (simulated phantom)");
    }

    @Override
    public MessageType getSupportedMessageType() {
      return MessageType.EXEC_INSTANCE_METHOD;
    }
  }

  /**
   * Minimal concrete subclass of {@link BaseExecMessageDispatcher} where the loading phase
   * succeeds. Used to test the path where the target object exists but the replay policy still says
   * to stub (e.g., {@code TextField.getText()} where the live value would be empty).
   */
  static class LoadSuccessDispatcher extends BaseExecMessageDispatcher {

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
      return "live-value-should-not-be-used";
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
}
