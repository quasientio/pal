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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An indexed view of a WAL (Write-Ahead Log), providing bidirectional operation/completion pairing,
 * span computation, per-thread grouping, and builder-sequence indexing.
 *
 * <p>Instances are created via the {@link #build(List)} static factory, which implements a
 * stack-based balanced-parentheses pairing algorithm to match each {@link WalEntryKind#OPERATION}
 * entry with its corresponding {@link WalEntryKind#COMPLETION} entry. Any pairing anomalies
 * (orphaned completions or unmatched operations) are recorded as structural issues.
 *
 * <p>This class is the central data structure for WAL replay — it provides the oracle that the
 * replay system consults to match operations and verify return values.
 */
public final class WalIndex {

  /** All entries in offset order. */
  private final List<WalEntry> entries;

  /**
   * Bidirectional mapping between operation offsets and completion offsets. For a paired
   * operation/completion, both {@code pairs.get(opOffset) == completionOffset} and {@code
   * pairs.get(completionOffset) == opOffset} hold.
   */
  private final Map<Long, Long> pairs;

  /** Maps each paired operation offset to its {@link Span}. */
  private final Map<Long, Span> spans;

  /** Maps each thread name to the list of entries produced by that thread, in offset order. */
  private final Map<String, List<WalEntry>> byThread;

  /** Maps each builder sequence number to the corresponding entry. */
  private final Map<Integer, WalEntry> byBuilderSeq;

  /** Descriptions of pairing or balance problems detected during indexing. */
  private final List<String> structuralIssues;

  /**
   * Constructs a new {@code WalIndex} with all precomputed fields.
   *
   * @param entries all entries in offset order
   * @param pairs bidirectional operation ↔ completion offset map
   * @param spans operation offset → span map
   * @param byThread thread name → entries map
   * @param byBuilderSeq builder sequence → entry map
   * @param structuralIssues list of pairing/balance problem descriptions
   */
  private WalIndex(
      List<WalEntry> entries,
      Map<Long, Long> pairs,
      Map<Long, Span> spans,
      Map<String, List<WalEntry>> byThread,
      Map<Integer, WalEntry> byBuilderSeq,
      List<String> structuralIssues) {
    this.entries = Collections.unmodifiableList(entries);
    this.pairs = Collections.unmodifiableMap(pairs);
    this.spans = Collections.unmodifiableMap(spans);
    this.byThread = Collections.unmodifiableMap(byThread);
    this.byBuilderSeq = Collections.unmodifiableMap(byBuilderSeq);
    this.structuralIssues = Collections.unmodifiableList(structuralIssues);
  }

  /**
   * Builds a {@code WalIndex} from a list of {@link WalEntry} instances in offset order.
   *
   * <p>The build process performs four steps:
   *
   * <ol>
   *   <li><b>Pairing:</b> Uses an {@link ArrayDeque} as a stack. For each entry, if it is an {@link
   *       WalEntryKind#OPERATION}, its offset is pushed onto the stack. If it is a {@link
   *       WalEntryKind#COMPLETION}, the top of the stack is popped and a bidirectional pair is
   *       created. Orphaned completions (pop from empty stack) and unmatched operations (remaining
   *       on stack after processing) are reported as structural issues.
   *   <li><b>Span computation:</b> For each {@link WalEntryKind#OPERATION} entry that has a pair, a
   *       {@link Span} is created from the operation offset to the completion offset.
   *   <li><b>Thread grouping:</b> Entries are grouped by {@link WalEntry#getThreadName()} into a
   *       map of thread name to entry list.
   *   <li><b>BuilderSeq indexing:</b> Each entry is indexed by its {@link
   *       WalEntry#getBuilderSeq()}.
   * </ol>
   *
   * @param entries the WAL entries in offset order
   * @return a fully indexed {@code WalIndex}
   */
  public static WalIndex build(List<WalEntry> entries) {
    Deque<Long> stack = new ArrayDeque<>();
    Map<Long, Long> pairs = new HashMap<>();
    List<String> issues = new ArrayList<>();

    for (WalEntry entry : entries) {
      if (entry.getKind() == WalEntryKind.OPERATION) {
        stack.push(entry.getOffset());
      } else {
        if (stack.isEmpty()) {
          issues.add("Orphaned completion at offset " + entry.getOffset());
        } else {
          long opOffset = stack.pop();
          pairs.put(opOffset, entry.getOffset());
          pairs.put(entry.getOffset(), opOffset);
        }
      }
    }

    while (!stack.isEmpty()) {
      issues.add("Unmatched operation at offset " + stack.pop());
    }

    Map<Long, Span> spans = new HashMap<>();
    for (WalEntry entry : entries) {
      if (entry.getKind() == WalEntryKind.OPERATION && pairs.containsKey(entry.getOffset())) {
        spans.put(entry.getOffset(), new Span(entry.getOffset(), pairs.get(entry.getOffset())));
      }
    }

    Map<String, List<WalEntry>> byThread = new LinkedHashMap<>();
    for (WalEntry entry : entries) {
      byThread.computeIfAbsent(entry.getThreadName(), k -> new ArrayList<>()).add(entry);
    }

    Map<Integer, WalEntry> byBuilderSeq = new HashMap<>();
    for (WalEntry entry : entries) {
      byBuilderSeq.put(entry.getBuilderSeq(), entry);
    }

    return new WalIndex(new ArrayList<>(entries), pairs, spans, byThread, byBuilderSeq, issues);
  }

  /**
   * Returns all entries in offset order.
   *
   * @return an unmodifiable list of all entries
   */
  public List<WalEntry> getEntries() {
    return entries;
  }

  /**
   * Returns the bidirectional operation ↔ completion offset map.
   *
   * <p>For a paired operation at offset {@code op} with completion at offset {@code comp}, both
   * {@code getPairs().get(op) == comp} and {@code getPairs().get(comp) == op} hold.
   *
   * @return an unmodifiable map of paired offsets
   */
  public Map<Long, Long> getPairs() {
    return pairs;
  }

  /**
   * Returns the map of operation offsets to their spans.
   *
   * @return an unmodifiable map of operation offset → {@link Span}
   */
  public Map<Long, Span> getSpans() {
    return spans;
  }

  /**
   * Returns the entries produced by the given thread.
   *
   * @param threadName the thread name to look up
   * @return a list of entries for that thread, or {@code null} if no entries exist for that thread
   */
  public List<WalEntry> getEntriesForThread(String threadName) {
    return byThread.get(threadName);
  }

  /**
   * Returns the entry with the given builder sequence number.
   *
   * @param builderSeq the builder sequence to look up
   * @return the entry with that sequence number, or {@code null} if not found
   */
  public WalEntry getEntryByBuilderSeq(int builderSeq) {
    return byBuilderSeq.get(builderSeq);
  }

  /**
   * Returns the list of structural issues detected during indexing.
   *
   * <p>Issues include orphaned completions (a completion with no matching operation on the stack)
   * and unmatched operations (an operation left on the stack with no corresponding completion).
   *
   * @return an unmodifiable list of issue descriptions
   */
  public List<String> getStructuralIssues() {
    return structuralIssues;
  }

  /**
   * Returns the completion offset paired with the given operation offset.
   *
   * @param opOffset the operation offset
   * @return the completion offset, or {@code null} if the operation is unpaired
   */
  public Long getCompletionOffset(long opOffset) {
    return pairs.get(opOffset);
  }

  /**
   * Returns the operation offset paired with the given completion offset.
   *
   * @param completionOffset the completion offset
   * @return the operation offset, or {@code null} if the completion is unpaired
   */
  public Long getOperationOffset(long completionOffset) {
    return pairs.get(completionOffset);
  }
}
