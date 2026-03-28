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
package io.quasient.pal.common.replay;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.ReturnValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Unit tests for {@code WalIndex} — the indexed WAL structure that pairs operations with
 * completions, computes spans, groups entries by thread, and indexes by builderSeq.
 *
 * <p>Tests exercise the core pairing algorithm (balanced-parentheses stack), span computation, and
 * structural validation. Test inputs are synthetic {@code WalEntry} lists built with controlled
 * offset, kind, threadName, and builderSeq values.
 *
 * <p>Depends on {@code WalEntry} and {@code WalEntryKind} for constructing test inputs.
 */
public class WalIndexTest {

  /**
   * Verifies that a simple linear sequence of one operation followed by its completion is paired
   * correctly, with the correct span and zero structural issues.
   *
   * <p>Input: [A_OP(offset=0), A_RET(offset=1)]. Expected: pairs={0↔1}, spans={0→(0,1)}, zero
   * structural issues.
   */
  @Test
  public void pairsLinearSequence() {
    // Given
    List<WalEntry> entries =
        Arrays.asList(makeOperation(0, "self-caller", 1), makeCompletion(1, "self-caller", 2));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getPairs().get(0L), is(1L));
    assertThat(index.getPairs().get(1L), is(0L));
    assertThat(index.getSpans().get(0L), is(new Span(0, 1)));
    assertThat(index.getStructuralIssues().isEmpty(), is(true));
  }

  /**
   * Verifies that a nested sequence (A calls B, B returns, then A returns) is paired correctly with
   * both pairs and spans reflecting the nesting structure.
   *
   * <p>Input: [A_OP(0), B_OP(1), B_RET(2), A_RET(3)]. Expected: pairs={0↔3, 1↔2}, spans={0→(0,3),
   * 1→(1,2)}.
   */
  @Test
  public void pairsNestedSequence() {
    // Given
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeOperation(1, "self-caller", 2),
            makeCompletion(2, "self-caller", 3),
            makeCompletion(3, "self-caller", 4));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getPairs().get(0L), is(3L));
    assertThat(index.getPairs().get(3L), is(0L));
    assertThat(index.getPairs().get(1L), is(2L));
    assertThat(index.getPairs().get(2L), is(1L));
    assertThat(index.getSpans().get(0L), is(new Span(0, 3)));
    assertThat(index.getSpans().get(1L), is(new Span(1, 2)));
    assertThat(index.getStructuralIssues().isEmpty(), is(true));
  }

  /**
   * Verifies that a three-level deeply nested sequence is paired correctly.
   *
   * <p>Input: [A_OP(0), B_OP(1), C_OP(2), C_RET(3), B_RET(4), A_RET(5)]. Expected: pairs={0↔5, 1↔4,
   * 2↔3}.
   */
  @Test
  public void pairsDeeplyNested() {
    // Given
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeOperation(1, "self-caller", 2),
            makeOperation(2, "self-caller", 3),
            makeCompletion(3, "self-caller", 4),
            makeCompletion(4, "self-caller", 5),
            makeCompletion(5, "self-caller", 6));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getPairs().get(0L), is(5L));
    assertThat(index.getPairs().get(1L), is(4L));
    assertThat(index.getPairs().get(2L), is(3L));
    assertThat(index.getStructuralIssues().isEmpty(), is(true));
  }

  /**
   * Verifies that sequential sibling operations (A completes, then B starts and completes) are
   * paired independently.
   *
   * <p>Input: [A_OP(0), A_RET(1), B_OP(2), B_RET(3)]. Expected: pairs={0↔1, 2↔3}.
   */
  @Test
  public void pairsSequentialSiblings() {
    // Given
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeCompletion(1, "self-caller", 2),
            makeOperation(2, "self-caller", 3),
            makeCompletion(3, "self-caller", 4));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getPairs().get(0L), is(1L));
    assertThat(index.getPairs().get(2L), is(3L));
    assertThat(index.getSpans().get(0L), is(new Span(0, 1)));
    assertThat(index.getSpans().get(2L), is(new Span(2, 3)));
    assertThat(index.getStructuralIssues().isEmpty(), is(true));
  }

  /**
   * Verifies that entries are grouped by thread name, with each thread's entries accessible
   * separately.
   *
   * <p>Input: entries with threadName 'thread-1' and 'thread-2'. Expected: byThread has two keys,
   * each mapping to the correct subset of entries.
   */
  @Test
  public void groupsByThread() {
    // Given
    WalEntry t1Op = makeOperation(0, "thread-1", 1);
    WalEntry t2Op = makeOperation(1, "thread-2", 2);
    WalEntry t1Ret = makeCompletion(2, "thread-1", 3);
    WalEntry t2Ret = makeCompletion(3, "thread-2", 4);
    List<WalEntry> entries = Arrays.asList(t1Op, t2Op, t1Ret, t2Ret);

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    List<WalEntry> thread1Entries = index.getEntriesForThread("thread-1");
    List<WalEntry> thread2Entries = index.getEntriesForThread("thread-2");
    assertThat(thread1Entries, is(notNullValue()));
    assertThat(thread2Entries, is(notNullValue()));
    assertThat(thread1Entries.size(), is(2));
    assertThat(thread2Entries.size(), is(2));
    assertThat(thread1Entries.get(0).getOffset(), is(0L));
    assertThat(thread1Entries.get(1).getOffset(), is(2L));
    assertThat(thread2Entries.get(0).getOffset(), is(1L));
    assertThat(thread2Entries.get(1).getOffset(), is(3L));
  }

  /**
   * Verifies that entries are indexed by builderSeq, allowing direct lookup by sequence number.
   *
   * <p>Input: entries with builderSeq values 1, 2, 3. Expected: byBuilderSeq maps each seq to the
   * corresponding entry.
   */
  @Test
  public void indexesByBuilderSeq() {
    // Given
    WalEntry entry1 = makeOperation(0, "self-caller", 1);
    WalEntry entry2 = makeOperation(1, "self-caller", 2);
    WalEntry entry3 = makeCompletion(2, "self-caller", 3);
    List<WalEntry> entries = Arrays.asList(entry1, entry2, entry3);

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getEntryByBuilderSeq(1).getOffset(), is(0L));
    assertThat(index.getEntryByBuilderSeq(2).getOffset(), is(1L));
    assertThat(index.getEntryByBuilderSeq(3).getOffset(), is(2L));
  }

  /**
   * Verifies that an orphaned completion (a completion with no preceding operation on the stack)
   * produces a structural issue.
   *
   * <p>Input: [RET(0)] — a completion entry with no matching operation. Expected: structuralIssues
   * is non-empty.
   */
  @Test
  public void reportsOrphanedCompletion() {
    // Given
    List<WalEntry> entries = Collections.singletonList(makeCompletion(0, "self-caller", 1));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getStructuralIssues().isEmpty(), is(false));
    assertThat(index.getStructuralIssues(), hasItem("Orphaned completion at offset 0"));
    assertThat(index.getPairs().isEmpty(), is(true));
  }

  /**
   * Verifies that an unmatched operation (an operation with no corresponding completion) produces a
   * structural issue.
   *
   * <p>Input: [A_OP(0)] — an operation entry with no matching completion. Expected:
   * structuralIssues contains an unmatched operation warning.
   */
  @Test
  public void reportsUnmatchedOperation() {
    // Given
    List<WalEntry> entries = Collections.singletonList(makeOperation(0, "self-caller", 1));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getStructuralIssues().isEmpty(), is(false));
    assertThat(index.getStructuralIssues(), hasItem("Unmatched operation at offset 0"));
    assertThat(index.getPairs().isEmpty(), is(true));
    assertThat(index.getSpans().isEmpty(), is(true));
  }

  /**
   * Verifies that building a WalIndex from an empty list produces an empty index with no pairs, no
   * spans, and zero structural issues.
   */
  @Test
  public void handlesEmptyWal() {
    // Given
    List<WalEntry> entries = Collections.emptyList();

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getEntries().isEmpty(), is(true));
    assertThat(index.getPairs().isEmpty(), is(true));
    assertThat(index.getSpans().isEmpty(), is(true));
    assertThat(index.getStructuralIssues().isEmpty(), is(true));
  }

  /**
   * Verifies that a single-entry WAL (one operation, no completion) is handled gracefully and
   * reports the unmatched entry as a structural issue.
   */
  @Test
  public void handlesSingleEntry() {
    // Given
    List<WalEntry> entries = Collections.singletonList(makeOperation(0, "self-caller", 1));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getEntries().size(), is(1));
    assertThat(index.getPairs().isEmpty(), is(true));
    assertThat(index.getStructuralIssues().isEmpty(), is(false));
    assertThat(index.getStructuralIssues(), hasItem("Unmatched operation at offset 0"));
  }

  // ================================
  // Entry-point classification tests
  // ================================

  /**
   * Verifies that {@code getInputThreadNames()} returns threads that have at least one entry-point
   * operation, excluding threads (like the self-caller) that have no entry-point markers.
   *
   * <p>This is used by {@code ReplayInputInjector} to determine which threads need WAL-driven input
   * injection during multi-threaded replay.
   */
  @Test
  public void getInputThreadNames_returnsThreadsWithEntryPoints() {
    // Given: WAL with entries on threads 'self-caller', 'rpc-worker-1', 'rpc-worker-2';
    //        entry points marked on rpc-worker threads only
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeEntryPointOperation(1, "rpc-worker-1", 2),
            makeEntryPointOperation(2, "rpc-worker-2", 3),
            makeCompletion(3, "rpc-worker-2", 4),
            makeCompletion(4, "rpc-worker-1", 5),
            makeCompletion(5, "self-caller", 6));

    // When
    WalIndex index = WalIndex.build(entries);
    Set<String> inputThreads = index.getInputThreadNames();

    // Then
    assertThat(inputThreads.size(), is(2));
    assertThat(inputThreads.contains("rpc-worker-1"), is(true));
    assertThat(inputThreads.contains("rpc-worker-2"), is(true));
    assertThat(inputThreads.contains("self-caller"), is(false));
  }

  /**
   * Verifies that {@code getInputThreadNames()} returns an empty set when no entries in the WAL
   * have the entry-point marker set (e.g., a single-threaded application with only self-caller
   * operations).
   */
  @Test
  public void getInputThreadNames_emptyWhenNoEntryPoints() {
    // Given: WAL with all entries having entryPoint = false (single-threaded app)
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeOperation(1, "self-caller", 2),
            makeCompletion(2, "self-caller", 3),
            makeCompletion(3, "self-caller", 4));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getInputThreadNames().isEmpty(), is(true));
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} returns only the entries that are both marked
   * as entry points and have {@code kind == OPERATION}, excluding nested (non-entry-point)
   * operations on the same thread.
   */
  @Test
  public void getEntryPointsForThread_returnsOnlyEntryPointOperations() {
    // Given: WAL with thread 'rpc-worker-1' having 3 entry-point operations and
    //        nested operations (entryPoint = false)
    List<WalEntry> entries =
        Arrays.asList(
            makeEntryPointOperation(0, "rpc-worker-1", 1),
            makeOperation(1, "rpc-worker-1", 2),
            makeCompletion(2, "rpc-worker-1", 3),
            makeCompletion(3, "rpc-worker-1", 4),
            makeEntryPointOperation(4, "rpc-worker-1", 5),
            makeOperation(5, "rpc-worker-1", 6),
            makeCompletion(6, "rpc-worker-1", 7),
            makeCompletion(7, "rpc-worker-1", 8),
            makeEntryPointOperation(8, "rpc-worker-1", 9),
            makeCompletion(9, "rpc-worker-1", 10));

    // When
    WalIndex index = WalIndex.build(entries);
    List<WalEntry> entryPoints = index.getEntryPointsForThread("rpc-worker-1");

    // Then
    assertThat(entryPoints.size(), is(3));
    for (WalEntry ep : entryPoints) {
      assertThat(ep.isEntryPoint(), is(true));
      assertThat(ep.getKind(), is(WalEntryKind.OPERATION));
    }
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} excludes COMPLETION entries even when they are
   * marked with {@code entryPoint = true}. Only OPERATION entries should be returned, since
   * completions are not injected during replay — they are the result of executing the operation.
   */
  @Test
  public void getEntryPointsForThread_excludesCompletions() {
    // Given: WAL with entry-point operation and its completion both marked entryPoint = true
    List<WalEntry> entries =
        Arrays.asList(
            makeEntryPointOperation(0, "rpc-worker-1", 1),
            makeEntryPointCompletion(1, "rpc-worker-1", 2));

    // When
    WalIndex index = WalIndex.build(entries);
    List<WalEntry> entryPoints = index.getEntryPointsForThread("rpc-worker-1");

    // Then: only the OPERATION, not the completion
    assertThat(entryPoints.size(), is(1));
    assertThat(entryPoints.get(0).getKind(), is(WalEntryKind.OPERATION));
    assertThat(entryPoints.get(0).getOffset(), is(0L));
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} returns an empty list for a thread that exists
   * in the WAL but has no entry-point markers (e.g., the self-caller thread in a typical
   * application).
   */
  @Test
  public void getEntryPointsForThread_emptyForThreadWithoutEntryPoints() {
    // Given: WAL with self-caller thread having no entry-point markers
    List<WalEntry> entries =
        Arrays.asList(makeOperation(0, "self-caller", 1), makeCompletion(1, "self-caller", 2));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getEntryPointsForThread("self-caller").isEmpty(), is(true));
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} returns an empty list when called with a thread
   * name that does not appear in the WAL at all.
   */
  @Test
  public void getEntryPointsForThread_emptyForUnknownThread() {
    // Given: Normal WAL index (no thread named "nonexistent-thread")
    List<WalEntry> entries =
        Arrays.asList(makeOperation(0, "self-caller", 1), makeCompletion(1, "self-caller", 2));

    // When
    WalIndex index = WalIndex.build(entries);

    // Then
    assertThat(index.getEntryPointsForThread("nonexistent-thread").isEmpty(), is(true));
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} returns entry-point operations in WAL offset
   * order. This ordering is important because {@code ReplayInputInjector} processes entry points
   * sequentially and uses WAL offsets for the ordering barrier.
   */
  @Test
  public void getEntryPointsForThread_preservesOffsetOrder() {
    // Given: WAL with thread 'rpc-worker-1' having entry points at offsets 10, 50, 100
    List<WalEntry> entries =
        Arrays.asList(
            makeEntryPointOperation(10, "rpc-worker-1", 1),
            makeCompletion(20, "rpc-worker-1", 2),
            makeEntryPointOperation(50, "rpc-worker-1", 3),
            makeCompletion(60, "rpc-worker-1", 4),
            makeEntryPointOperation(100, "rpc-worker-1", 5),
            makeCompletion(110, "rpc-worker-1", 6));

    // When
    WalIndex index = WalIndex.build(entries);
    List<WalEntry> entryPoints = index.getEntryPointsForThread("rpc-worker-1");

    // Then: entries in offset order (10, 50, 100)
    assertThat(entryPoints.size(), is(3));
    assertThat(entryPoints.get(0).getOffset(), is(10L));
    assertThat(entryPoints.get(1).getOffset(), is(50L));
    assertThat(entryPoints.get(2).getOffset(), is(100L));
  }

  // ===========================================================================================
  // Offset lookup and span entry query tests
  // ===========================================================================================

  /**
   * Verifies that {@code getEntryAtOffset()} returns the correct entry when the requested offset
   * exists in the index.
   */
  @Test
  public void getEntryAtOffsetReturnsCorrectEntry() {
    // Given
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(10, "self-caller", 1),
            makeOperation(20, "self-caller", 2),
            makeCompletion(30, "self-caller", 3),
            makeCompletion(40, "self-caller", 4));
    WalIndex index = WalIndex.build(entries);

    // When
    WalEntry result = index.getEntryAtOffset(20);

    // Then
    assertThat(result, is(notNullValue()));
    assertThat(result.getOffset(), is(20L));
  }

  /**
   * Verifies that {@code getEntryAtOffset()} returns null when the requested offset does not exist
   * in the index.
   */
  @Test
  public void getEntryAtOffsetReturnsNullForMissingOffset() {
    // Given
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(10, "self-caller", 1),
            makeOperation(20, "self-caller", 2),
            makeCompletion(30, "self-caller", 3));
    WalIndex index = WalIndex.build(entries);

    // When
    WalEntry result = index.getEntryAtOffset(15);

    // Then
    assertThat(result, is(nullValue()));
  }

  /**
   * Verifies that {@code getEntriesInSpan()} returns only the entries strictly inside the span
   * boundaries (exclusive of the span's operation and completion offsets).
   */
  @Test
  public void getEntriesInSpanReturnsInnerEntries() {
    // Given: outer span(10, 40) with inner entries at offsets 20 and 30
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(10, "self-caller", 1),
            makeOperation(20, "self-caller", 2),
            makeCompletion(30, "self-caller", 3),
            makeCompletion(40, "self-caller", 4));
    WalIndex index = WalIndex.build(entries);
    Span span = new Span(10, 40);

    // When
    List<WalEntry> inner = index.getEntriesInSpan(span);

    // Then
    assertThat(inner.size(), is(2));
    assertThat(inner.get(0).getOffset(), is(20L));
    assertThat(inner.get(1).getOffset(), is(30L));
  }

  /**
   * Verifies that {@code getEntriesInSpan()} returns an empty list when the span contains no
   * entries between its operation and completion offsets (adjacent offsets with nothing in
   * between).
   */
  @Test
  public void getEntriesInSpanReturnsEmptyForEmptySpan() {
    // Given: adjacent span(10, 20) with no entries between boundaries
    List<WalEntry> entries =
        Arrays.asList(makeOperation(10, "self-caller", 1), makeCompletion(20, "self-caller", 2));
    WalIndex index = WalIndex.build(entries);
    Span span = new Span(10, 20);

    // When
    List<WalEntry> inner = index.getEntriesInSpan(span);

    // Then
    assertThat(inner.isEmpty(), is(true));
  }

  /**
   * Creates a synthetic OPERATION {@link WalEntry} using an {@link InstanceMethodCall}-based {@link
   * ExecMessage}.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new operation entry
   */
  private static WalEntry makeOperation(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("op" + offset);
    Class clazz = new Class();
    clazz.setName("com.example.Test");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a synthetic COMPLETION {@link WalEntry} using a {@link ReturnValue}-based {@link
   * ExecMessage}.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new completion entry
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
   * Creates a synthetic entry-point OPERATION {@link WalEntry} with the {@code entryPoint} flag
   * set.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new entry-point operation entry
   */
  private static WalEntry makeEntryPointOperation(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    msg.setEntryPoint(true);
    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("entryOp" + offset);
    Class clazz = new Class();
    clazz.setName("com.example.Test");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a synthetic entry-point COMPLETION {@link WalEntry} with the {@code entryPoint} flag
   * set.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new entry-point completion entry
   */
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
}
