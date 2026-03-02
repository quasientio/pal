/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.replay;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.ReturnValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Ignore;
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

  // ===========================================================================================
  // Entry-point classification tests (Issue #899 — specs for #900 implementation)
  // ===========================================================================================

  /**
   * Verifies that {@code getInputThreadNames()} returns threads that have at least one entry-point
   * operation, excluding threads (like the self-caller) that have no entry-point markers.
   *
   * <p>This is used by {@code ReplayInputInjector} to determine which threads need WAL-driven input
   * injection during multi-threaded replay.
   */
  @Test
  @Ignore("Awaiting implementation in #900")
  public void getInputThreadNames_returnsThreadsWithEntryPoints() {
    // Given: WAL with entries on threads 'self-caller', 'rpc-worker-1', 'rpc-worker-2';
    //        entry points marked on rpc-worker threads only
    // When: walIndex.getInputThreadNames()
    // Then: Returns set containing 'rpc-worker-1' and 'rpc-worker-2'

    // TODO(#900): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@code getInputThreadNames()} returns an empty set when no entries in the WAL
   * have the entry-point marker set (e.g., a single-threaded application with only self-caller
   * operations).
   */
  @Test
  @Ignore("Awaiting implementation in #900")
  public void getInputThreadNames_emptyWhenNoEntryPoints() {
    // Given: WAL with all entries having entryPoint = false (single-threaded app)
    // When: walIndex.getInputThreadNames()
    // Then: Returns empty set

    // TODO(#900): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} returns only the entries that are both marked
   * as entry points and have {@code kind == OPERATION}, excluding nested (non-entry-point)
   * operations on the same thread.
   */
  @Test
  @Ignore("Awaiting implementation in #900")
  public void getEntryPointsForThread_returnsOnlyEntryPointOperations() {
    // Given: WAL with thread 'rpc-worker-1' having 3 entry-point operations and 5 nested
    //        operations (entryPoint = false)
    // When: walIndex.getEntryPointsForThread("rpc-worker-1")
    // Then: Returns list of 3 entries, all with isEntryPoint() == true and
    //       getKind() == OPERATION

    // TODO(#900): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} excludes COMPLETION entries even when they are
   * marked with {@code entryPoint = true}. Only OPERATION entries should be returned, since
   * completions are not injected during replay — they are the result of executing the operation.
   */
  @Test
  @Ignore("Awaiting implementation in #900")
  public void getEntryPointsForThread_excludesCompletions() {
    // Given: WAL with entry-point operation and its completion both marked entryPoint = true
    // When: walIndex.getEntryPointsForThread(threadName)
    // Then: Returns only the OPERATION entries, not completions

    // TODO(#900): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} returns an empty list for a thread that exists
   * in the WAL but has no entry-point markers (e.g., the self-caller thread in a typical
   * application).
   */
  @Test
  @Ignore("Awaiting implementation in #900")
  public void getEntryPointsForThread_emptyForThreadWithoutEntryPoints() {
    // Given: WAL with self-caller thread having no entry-point markers
    // When: walIndex.getEntryPointsForThread("self-caller")
    // Then: Returns empty list

    // TODO(#900): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} returns an empty list when called with a thread
   * name that does not appear in the WAL at all.
   */
  @Test
  @Ignore("Awaiting implementation in #900")
  public void getEntryPointsForThread_emptyForUnknownThread() {
    // Given: Normal WAL index (no thread named "nonexistent-thread")
    // When: walIndex.getEntryPointsForThread("nonexistent-thread")
    // Then: Returns empty list

    // TODO(#900): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that {@code getEntryPointsForThread()} returns entry-point operations in WAL offset
   * order. This ordering is important because {@code ReplayInputInjector} processes entry points
   * sequentially and uses WAL offsets for the ordering barrier.
   */
  @Test
  @Ignore("Awaiting implementation in #900")
  public void getEntryPointsForThread_preservesOffsetOrder() {
    // Given: WAL with thread 'rpc-worker-1' having entry points at offsets 10, 50, 100
    // When: walIndex.getEntryPointsForThread("rpc-worker-1")
    // Then: Returns entries in offset order (10, 50, 100)

    // TODO(#900): Implement test logic
    fail("Not yet implemented");
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
}
