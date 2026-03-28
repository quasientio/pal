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
package io.quasient.pal.core.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.ReturnValue;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@code ReplayContext} — the central coordination object that holds {@code
 * WalIndex}, {@code ReplayPolicy}, {@code ReplayObjectStore}, {@code DivergenceDetector}, and
 * manages per-thread {@code ReplayCursor} instances.
 *
 * <p>Tests verify correct delegation to sub-components and lazy cursor creation with caching.
 * ReplayContext is constructed with a simple WalIndex built from synthetic WalEntry lists.
 */
public class ReplayContextTest {

  /**
   * Verifies that getCursor creates a new cursor on first access for a thread whose entries exist
   * in the WalIndex.
   */
  @Test
  public void getCursorCreatesOnFirstAccess() {
    // Given: ReplayContext with WalIndex containing entries for 'self-caller' thread
    List<WalEntry> entries =
        Arrays.asList(makeOperation(0L, "self-caller", 1), makeCompletion(1L, "self-caller", 2));
    WalIndex walIndex = WalIndex.build(entries);
    ReplayContext ctx = createContext(walIndex);

    // When: getCursor('self-caller')
    ReplayCursor cursor = ctx.getCursor("self-caller");

    // Then: returns non-null ReplayCursor that is not exhausted
    assertThat(cursor, is(notNullValue()));
    assertThat(cursor.isExhausted(), is(false));
    assertThat(cursor.getThreadName(), is("self-caller"));
  }

  /** Verifies that getCursor returns the same cached instance on subsequent calls. */
  @Test
  public void getCursorReturnsSameInstance() {
    // Given: ReplayContext with WalIndex containing entries for 'self-caller' thread
    List<WalEntry> entries =
        Arrays.asList(makeOperation(0L, "self-caller", 1), makeCompletion(1L, "self-caller", 2));
    WalIndex walIndex = WalIndex.build(entries);
    ReplayContext ctx = createContext(walIndex);

    // When: getCursor('self-caller') called twice
    ReplayCursor first = ctx.getCursor("self-caller");
    ReplayCursor second = ctx.getCursor("self-caller");

    // Then: returns same ReplayCursor instance (cached)
    assertThat(second, is(sameInstance(first)));
  }

  /**
   * Verifies that getCursor for an unknown thread returns a cursor that is immediately exhausted.
   */
  @Test
  public void getCursorForUnknownThread() {
    // Given: ReplayContext with WalIndex that has no entries for 'unknown-thread'
    List<WalEntry> entries =
        Arrays.asList(makeOperation(0L, "self-caller", 1), makeCompletion(1L, "self-caller", 2));
    WalIndex walIndex = WalIndex.build(entries);
    ReplayContext ctx = createContext(walIndex);

    // When: getCursor('unknown-thread')
    ReplayCursor cursor = ctx.getCursor("unknown-thread");

    // Then: returns ReplayCursor that is immediately exhausted (empty entry list)
    assertThat(cursor, is(notNullValue()));
    assertThat(cursor.isExhausted(), is(true));
    assertThat(cursor.getThreadName(), is("unknown-thread"));
  }

  /** Verifies that getDivergenceDetector returns the instance passed at construction. */
  @Test
  public void delegatesToDivergenceDetector() {
    // Given: ReplayContext constructed with a specific DivergenceDetector instance
    DivergenceDetector detector = new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN);
    ReplayContext ctx =
        new ReplayContext(
            WalIndex.build(Arrays.asList()),
            new ReplayPolicy(),
            new ReplayObjectStore(),
            detector,
            new ReplayGate(true));

    // When: getDivergenceDetector()
    // Then: returns the same DivergenceDetector instance passed at construction
    assertThat(ctx.getDivergenceDetector(), is(sameInstance(detector)));
  }

  /** Verifies that getObjectStore returns the instance passed at construction. */
  @Test
  public void delegatesToObjectStore() {
    // Given: ReplayContext constructed with a specific ReplayObjectStore instance
    ReplayObjectStore store = new ReplayObjectStore();
    ReplayContext ctx =
        new ReplayContext(
            WalIndex.build(Arrays.asList()),
            new ReplayPolicy(),
            store,
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            new ReplayGate(true));

    // When: getObjectStore()
    // Then: returns the same ReplayObjectStore instance passed at construction
    assertThat(ctx.getObjectStore(), is(sameInstance(store)));
  }

  /** Verifies that getPolicy returns the instance passed at construction. */
  @Test
  public void delegatesToPolicy() {
    // Given: ReplayContext constructed with a specific ReplayPolicy instance
    ReplayPolicy policy = new ReplayPolicy();
    ReplayContext ctx =
        new ReplayContext(
            WalIndex.build(Arrays.asList()),
            policy,
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            new ReplayGate(true));

    // When: getPolicy()
    // Then: returns the same ReplayPolicy instance passed at construction
    assertThat(ctx.getPolicy(), is(sameInstance(policy)));
  }

  /** Verifies that getWalIndex returns the instance passed at construction. */
  @Test
  public void walIndexAccessible() {
    // Given: ReplayContext constructed with a specific WalIndex instance
    WalIndex walIndex = WalIndex.build(Arrays.asList());
    ReplayContext ctx =
        new ReplayContext(
            walIndex,
            new ReplayPolicy(),
            new ReplayObjectStore(),
            new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
            new ReplayGate(true));

    // When: getWalIndex()
    // Then: returns the same WalIndex instance passed at construction
    assertThat(ctx.getWalIndex(), is(sameInstance(walIndex)));
  }

  // ===== isPendingInjection / pushPendingInjection / popPendingInjection tests =====

  /** Verifies that isPendingInjection returns false when no injections have been pushed. */
  @Test
  public void isPendingInjection_returnsFalseWhenEmpty() {
    // Given
    ReplayContext ctx = createContext(WalIndex.build(Arrays.asList()));

    // When / Then
    assertThat(ctx.isPendingInjection("thread-1", 100L), is(false));
  }

  /** Verifies that isPendingInjection returns true after a matching push. */
  @Test
  public void isPendingInjection_returnsTrueAfterPush() {
    // Given
    ReplayContext ctx = createContext(WalIndex.build(Arrays.asList()));
    ctx.pushPendingInjection("thread-1", 100L);

    // When / Then
    assertThat(ctx.isPendingInjection("thread-1", 100L), is(true));
  }

  /** Verifies that isPendingInjection returns false when the offset does not match. */
  @Test
  public void isPendingInjection_returnsFalseForDifferentOffset() {
    // Given
    ReplayContext ctx = createContext(WalIndex.build(Arrays.asList()));
    ctx.pushPendingInjection("thread-1", 100L);

    // When / Then
    assertThat(ctx.isPendingInjection("thread-1", 200L), is(false));
  }

  /** Verifies that isPendingInjection returns false when the thread name does not match. */
  @Test
  public void isPendingInjection_returnsFalseForDifferentThread() {
    // Given
    ReplayContext ctx = createContext(WalIndex.build(Arrays.asList()));
    ctx.pushPendingInjection("thread-1", 100L);

    // When / Then
    assertThat(ctx.isPendingInjection("thread-2", 100L), is(false));
  }

  /**
   * Verifies that popPendingInjection removes the entry and isPendingInjection returns false after.
   */
  @Test
  public void popPendingInjection_removesFromQueue() {
    // Given
    ReplayContext ctx = createContext(WalIndex.build(Arrays.asList()));
    ctx.pushPendingInjection("thread-1", 100L);

    // When
    long popped = ctx.popPendingInjection("thread-1");

    // Then
    assertThat(popped, is(100L));
    assertThat(ctx.isPendingInjection("thread-1", 100L), is(false));
  }

  /** Verifies that multiple offsets are queued per thread in FIFO order. */
  @Test
  public void pushPendingInjection_multipleOffsetsQueued() {
    // Given
    ReplayContext ctx = createContext(WalIndex.build(Arrays.asList()));
    ctx.pushPendingInjection("thread-1", 100L);
    ctx.pushPendingInjection("thread-1", 200L);

    // When / Then: both are pending
    assertThat(ctx.isPendingInjection("thread-1", 100L), is(true));
    assertThat(ctx.isPendingInjection("thread-1", 200L), is(true));

    // When / Then: FIFO order
    assertThat(ctx.popPendingInjection("thread-1"), is(100L));
    assertThat(ctx.popPendingInjection("thread-1"), is(200L));
  }

  // ===== hasInjectorForThread tests =====

  /**
   * Verifies that {@code hasInjectorForThread} returns true for a thread that has been explicitly
   * registered via {@code registerInjectorThread}.
   */
  @Test
  public void hasInjectorForThread_returnsTrueForRegisteredThread() {
    // Given: ReplayContext with a registered injector thread
    ReplayContext ctx = createContext(WalIndex.build(Arrays.asList()));
    ctx.registerInjectorThread("fx-thread");

    // When / Then
    assertThat(ctx.hasInjectorForThread("fx-thread"), is(true));
  }

  /**
   * Verifies that {@code hasInjectorForThread} returns false for the self-caller thread even when
   * self-caller has entry-point operations in the WAL. The self-caller is handled by
   * SelfBootstrapInvoker, not by a ReplayInputInjector, so it should never be registered.
   */
  @Test
  public void hasInjectorForThread_returnsFalseForSelfCaller() {
    // Given: WalIndex with self-caller entry-point operations (simulates --no-wal-incoming-cli
    // NOT being used), but self-caller is NOT registered as an injector thread
    List<WalEntry> entries =
        Arrays.asList(
            makeEntryPointOperation(0L, "self-caller", 1), makeCompletion(1L, "self-caller", 2));
    WalIndex walIndex = WalIndex.build(entries);
    ReplayContext ctx = createContext(walIndex);
    // Note: self-caller is NOT registered (Main.java skips it)

    // When / Then: returns false despite self-caller being in getInputThreadNames()
    assertThat(walIndex.getInputThreadNames().contains("self-caller"), is(true));
    assertThat(ctx.hasInjectorForThread("self-caller"), is(false));
  }

  /**
   * Verifies that {@code hasInjectorForThread} returns false for a thread name that has not been
   * registered, even if other threads are registered.
   */
  @Test
  public void hasInjectorForThread_returnsFalseForUnregisteredThread() {
    // Given: ReplayContext with a different thread registered
    ReplayContext ctx = createContext(WalIndex.build(Arrays.asList()));
    ctx.registerInjectorThread("fx-thread");

    // When / Then
    assertThat(ctx.hasInjectorForThread("unknown-thread"), is(false));
  }

  /**
   * Creates a {@link ReplayContext} with default sub-components and the given WalIndex.
   *
   * @param walIndex the WalIndex to use
   * @return a new ReplayContext
   */
  private static ReplayContext createContext(WalIndex walIndex) {
    return new ReplayContext(
        walIndex,
        new ReplayPolicy(),
        new ReplayObjectStore(),
        new DivergenceDetector(DivergenceDetector.DivergencePolicy.WARN),
        new ReplayGate(true));
  }

  /**
   * Creates an OPERATION {@link WalEntry} (instance method call).
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new WalEntry of kind OPERATION
   */
  private static WalEntry makeOperation(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("testMethod");
    imc.setObjectRef(1);
    Class clazz = new Class();
    clazz.setName("com.example.TestClass");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a COMPLETION {@link WalEntry} (void return value).
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new WalEntry of kind COMPLETION
   */
  private static WalEntry makeCompletion(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);

    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);

    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates an entry-point OPERATION {@link WalEntry}.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new entry-point WalEntry of kind OPERATION
   */
  private static WalEntry makeEntryPointOperation(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    msg.setEntryPoint(true);

    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("entryOp" + offset);
    imc.setObjectRef(1);
    Class clazz = new Class();
    clazz.setName("com.example.TestClass");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);

    return WalEntry.fromExecMessage(offset, msg);
  }
}
