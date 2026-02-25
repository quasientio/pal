/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import io.quasient.pal.common.replay.WalEntry;
import java.util.Collections;
import java.util.List;

/**
 * Thread-local WAL position tracker used by the dispatch replay path to walk through {@link
 * WalEntry} instances in order for a single thread.
 *
 * <p>Each cursor is bound to a named thread and its corresponding slice of the WAL (obtained via
 * {@link io.quasient.pal.common.replay.WalIndex#getEntriesForThread(String)}). The cursor supports
 * forward-only traversal: peeking at the next entry without advancing, advancing to the next entry,
 * and skipping past a given WAL offset.
 *
 * <p>For Phase 1 (single-threaded replay), there is exactly one cursor per replay session, bound to
 * the {@code "self-caller"} thread created by {@code SelfBootstrapInvoker}.
 */
public class ReplayCursor {

  /** The name of the thread whose WAL entries this cursor walks. */
  private final String threadName;

  /** The ordered WAL entries for this thread, obtained from the WalIndex. */
  private final List<WalEntry> threadEntries;

  /** The current index into {@link #threadEntries}. */
  private int position;

  /**
   * Constructs a new cursor for the given thread and its WAL entries.
   *
   * @param threadName the name of the thread whose entries this cursor walks
   * @param threadEntries the ordered WAL entries for this thread; must not be {@code null}
   */
  public ReplayCursor(String threadName, List<WalEntry> threadEntries) {
    this.threadName = threadName;
    this.threadEntries = Collections.unmodifiableList(threadEntries);
    this.position = 0;
  }

  /**
   * Returns the name of the thread whose WAL entries this cursor walks.
   *
   * @return the thread name
   */
  public String getThreadName() {
    return threadName;
  }

  /**
   * Returns the next entry without advancing the cursor position.
   *
   * @return the entry at the current position, or {@code null} if the cursor is exhausted
   */
  public WalEntry peekNext() {
    if (isExhausted()) {
      return null;
    }
    return threadEntries.get(position);
  }

  /**
   * Returns the current entry and advances the cursor to the next position.
   *
   * @return the entry at the current position, or {@code null} if the cursor is exhausted
   */
  public WalEntry advance() {
    if (isExhausted()) {
      return null;
    }
    WalEntry current = threadEntries.get(position);
    position++;
    return current;
  }

  /**
   * Advances the cursor past the entry with the given WAL offset.
   *
   * <p>After this call, the cursor position will be at the first entry whose offset is strictly
   * greater than {@code offset}. If no such entry exists, the cursor becomes exhausted.
   *
   * @param offset the WAL offset to skip past
   */
  public void advancePast(long offset) {
    while (!isExhausted() && threadEntries.get(position).getOffset() <= offset) {
      position++;
    }
  }

  /**
   * Returns whether the cursor has been fully consumed.
   *
   * @return {@code true} if there are no more entries to return
   */
  public boolean isExhausted() {
    return position >= threadEntries.size();
  }

  /**
   * Returns the current position index into the entry list, useful for diagnostics.
   *
   * @return the current zero-based position
   */
  public int getPosition() {
    return position;
  }
}
