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
import io.quasient.pal.common.replay.WalIndex;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

/**
 * Central coordination object for deterministic WAL replay.
 *
 * <p>Holds the WAL oracle ({@link WalIndex}), per-thread cursors ({@link ReplayCursor}), the object
 * mapping ({@link ReplayObjectStore}), the verification engine ({@link DivergenceDetector}), and
 * the action policy ({@link ReplayPolicy}).
 *
 * <p>Cursors are lazily created and cached per thread name using {@link
 * ConcurrentHashMap#computeIfAbsent}. For unknown threads (no entries in the WalIndex), an empty
 * cursor is returned that is immediately exhausted.
 *
 * <p>This class is injected into the dispatcher via Guice when the peer is running in replay mode.
 */
public class ReplayContext {

  /** The indexed WAL providing the oracle for replay verification. */
  private final WalIndex walIndex;

  /** The policy determining what replay action to take for each operation. */
  private final ReplayPolicy policy;

  /** Bidirectional mapping between WAL object references and live JVM objects. */
  private final ReplayObjectStore objectStore;

  /** Verification engine that compares actual values against WAL-recorded values. */
  private final DivergenceDetector divergenceDetector;

  /** WAL-offset-based ordering barrier for cross-thread replay coordination. */
  private final ReplayGate replayGate;

  /**
   * Delay in milliseconds to wait before processing each OPERATION entry. Used for slow-motion
   * replay visualization. A value of {@code 0} disables the delay.
   */
  private final long operationDelayMs;

  /** Lazily created and cached per-thread cursors. Thread-safe via {@link ConcurrentHashMap}. */
  private final Map<String, ReplayCursor> cursors = new ConcurrentHashMap<>();

  /**
   * Tracks entry-point WAL offsets that have been handled via {@code dispatchReplay()} (the real
   * runtime path, e.g., JavaFX calling start() which calls constructor/buildUI). These entry points
   * should not be re-injected by {@link ReplayInputInjector} to avoid duplicate execution.
   */
  private final Set<Long> handledEntryPoints = ConcurrentHashMap.newKeySet();

  /**
   * Per-thread queue of pending injection offsets. The {@link ReplayInputInjector} pushes the entry
   * point offset before calling {@code incomingCall()}, and the callback in {@code
   * dispatchIncoming()} pops it to know the exact offset being injected. This avoids race
   * conditions where the cursor has advanced by the time the callback runs.
   */
  private final Map<String, Queue<Long>> pendingInjectionOffsets = new ConcurrentHashMap<>();

  /**
   * Latch that gates replay input injector threads until the self-caller has loaded and initialized
   * the target application class. This prevents class-loading race conditions where an injector
   * thread could trigger static initialization on the wrong thread, causing cursor misalignment.
   * Set via {@link #setInjectorReadyLatch} and counted down via {@link #countDownInjectorLatch}.
   */
  private volatile CountDownLatch injectorReadyLatch;

  /**
   * Constructs a new {@code ReplayContext} with all required sub-components.
   *
   * @param walIndex the indexed WAL providing the oracle for replay
   * @param policy the policy determining replay actions
   * @param objectStore the bidirectional WAL ref ↔ live object mapping
   * @param divergenceDetector the verification engine for comparing actual vs WAL values
   * @param replayGate the WAL-offset ordering barrier for cross-thread coordination
   */
  public ReplayContext(
      WalIndex walIndex,
      ReplayPolicy policy,
      ReplayObjectStore objectStore,
      DivergenceDetector divergenceDetector,
      ReplayGate replayGate) {
    this(walIndex, policy, objectStore, divergenceDetector, replayGate, 0L);
  }

  /**
   * Constructs a new {@code ReplayContext} with all required sub-components and an operation delay.
   *
   * @param walIndex the indexed WAL providing the oracle for replay
   * @param policy the policy determining replay actions
   * @param objectStore the bidirectional WAL ref ↔ live object mapping
   * @param divergenceDetector the verification engine for comparing actual vs WAL values
   * @param replayGate the WAL-offset ordering barrier for cross-thread coordination
   * @param operationDelayMs delay in milliseconds before each operation entry (0 to disable)
   */
  public ReplayContext(
      WalIndex walIndex,
      ReplayPolicy policy,
      ReplayObjectStore objectStore,
      DivergenceDetector divergenceDetector,
      ReplayGate replayGate,
      long operationDelayMs) {
    this.walIndex = walIndex;
    this.policy = policy;
    this.objectStore = objectStore;
    this.divergenceDetector = divergenceDetector;
    this.replayGate = replayGate;
    this.operationDelayMs = operationDelayMs;
  }

  /**
   * Returns the {@link ReplayCursor} for the given thread name, creating one if it does not yet
   * exist.
   *
   * <p>Cursors are lazily created on first access and cached for subsequent calls. If the thread
   * has no entries in the {@link WalIndex}, an empty cursor (immediately exhausted) is returned.
   *
   * @param threadName the name of the thread whose cursor to retrieve
   * @return the cached or newly created cursor for the given thread
   */
  public ReplayCursor getCursor(String threadName) {
    return cursors.computeIfAbsent(
        threadName,
        name -> {
          List<WalEntry> entries = walIndex.getEntriesForThread(name);
          return new ReplayCursor(name, entries != null ? entries : Collections.emptyList());
        });
  }

  /**
   * Returns the indexed WAL providing the oracle for replay verification.
   *
   * @return the WAL index
   */
  public WalIndex getWalIndex() {
    return walIndex;
  }

  /**
   * Returns the policy determining what replay action to take for each operation.
   *
   * @return the replay policy
   */
  public ReplayPolicy getPolicy() {
    return policy;
  }

  /**
   * Returns the bidirectional mapping between WAL object references and live JVM objects.
   *
   * @return the replay object store
   */
  public ReplayObjectStore getObjectStore() {
    return objectStore;
  }

  /**
   * Returns the verification engine for comparing actual values against WAL-recorded values.
   *
   * @return the divergence detector
   */
  public DivergenceDetector getDivergenceDetector() {
    return divergenceDetector;
  }

  /**
   * Returns the WAL-offset ordering barrier for cross-thread replay coordination.
   *
   * @return the replay gate
   */
  public ReplayGate getReplayGate() {
    return replayGate;
  }

  /**
   * Returns the delay in milliseconds to wait before processing each OPERATION entry.
   *
   * <p>Used for slow-motion replay visualization. A value of {@code 0} means no delay.
   *
   * @return the operation delay in milliseconds
   */
  public long getOperationDelayMs() {
    return operationDelayMs;
  }

  /**
   * Sets the latch that gates replay input injector threads. The latch will be counted down by
   * {@link #countDownInjectorLatch()} after the self-caller thread has loaded the target
   * application class (triggering static initialization on the correct thread).
   *
   * @param latch the latch to set, or {@code null} to clear
   */
  public void setInjectorReadyLatch(CountDownLatch latch) {
    this.injectorReadyLatch = latch;
  }

  /**
   * Counts down the injector ready latch if one has been set. This is called from {@code
   * dispatchIncoming()} after the class-loading phase completes, ensuring that static
   * initialization has run on the self-caller thread before any injector thread can trigger class
   * loading.
   *
   * <p>Safe to call multiple times; after the first countdown, subsequent calls are no-ops.
   */
  public void countDownInjectorLatch() {
    CountDownLatch latch = this.injectorReadyLatch;
    if (latch != null) {
      latch.countDown();
    }
  }

  /**
   * Marks an entry-point WAL offset as having been handled via {@code dispatchReplay()}.
   *
   * <p>This is called when an entry-point operation is executed via the real runtime path (e.g.,
   * JavaFX calling start() which calls constructor/buildUI). The {@link ReplayInputInjector} checks
   * this before injecting to avoid duplicate execution.
   *
   * @param offset the WAL offset of the handled entry point
   */
  public void markEntryPointHandled(long offset) {
    handledEntryPoints.add(offset);
  }

  /**
   * Checks whether an entry-point WAL offset has already been handled via {@code dispatchReplay()}.
   *
   * <p>The {@link ReplayInputInjector} should call this before injecting an entry point. If it
   * returns {@code true}, the entry point was already executed by the real runtime and should not
   * be re-injected.
   *
   * @param offset the WAL offset of the entry point to check
   * @return {@code true} if the entry point was already handled, {@code false} otherwise
   */
  public boolean isEntryPointHandled(long offset) {
    return handledEntryPoints.contains(offset);
  }

  /**
   * Pushes an entry-point offset to the pending injection queue for the given thread.
   *
   * <p>This is called by {@link ReplayInputInjector} immediately before calling {@code
   * incomingCall()} to record the exact offset being injected. The callback in {@code
   * dispatchIncoming()} will pop this offset to verify it hasn't already been handled.
   *
   * @param threadName the name of the target thread for the injection
   * @param offset the WAL offset of the entry point being injected
   */
  public void pushPendingInjection(String threadName, long offset) {
    boolean added =
        pendingInjectionOffsets
            .computeIfAbsent(threadName, k -> new ConcurrentLinkedQueue<>())
            .offer(offset);
    // ConcurrentLinkedQueue.offer() always returns true (unbounded queue), but check defensively
    if (!added) {
      throw new IllegalStateException(
          "Failed to add pending injection offset " + offset + " for thread " + threadName);
    }
  }

  /**
   * Pops the next pending injection offset for the given thread.
   *
   * <p>This is called by the callback in {@code dispatchIncoming()} to retrieve the exact offset
   * that was pushed by the injector. This offset can then be used to check {@link
   * #isEntryPointHandled} for race detection.
   *
   * @param threadName the name of the thread to pop from
   * @return the next pending offset, or {@code -1} if no pending injections exist
   */
  public long popPendingInjection(String threadName) {
    Queue<Long> queue = pendingInjectionOffsets.get(threadName);
    if (queue == null) {
      return -1L;
    }
    Long offset = queue.poll();
    return offset != null ? offset : -1L;
  }

  /**
   * Checks whether the given thread has a corresponding {@link ReplayInputInjector}.
   *
   * <p>A thread has an injector if its name appears in {@link WalIndex#getInputThreadNames()},
   * meaning it has at least one entry-point OPERATION in the WAL. Threads without injectors (e.g.,
   * the self-caller thread) have their entry points handled by {@code SelfBootstrapInvoker} rather
   * than by injection, so their nested operations should remain in the cursor for matching.
   *
   * <p>This is used by {@code dispatchReplay()} to decide the skip strategy when an entry point is
   * encountered: if an injector exists, the entire span is skipped (nested ops will be handled by
   * the injector); otherwise, only the OPERATION entry is skipped and nested ops remain available.
   *
   * @param threadName the name of the thread to check
   * @return {@code true} if the thread has a corresponding injector, {@code false} otherwise
   */
  public boolean hasInjectorForThread(String threadName) {
    return walIndex.getInputThreadNames().contains(threadName);
  }

  /**
   * Checks whether an offset is pending injection for the given thread.
   *
   * <p>This is used by {@code dispatchReplay()} to avoid skipping entry points that are about to be
   * injected. Without this check, there's a race condition: the injector pushes an offset and calls
   * {@code incomingCall()}, which queues a callback via {@code Platform.runLater()}. Meanwhile, a
   * live operation on the same thread triggers {@code dispatchReplay()}, which sees a mismatch with
   * the pending entry point and skips it. This causes the injection callback to find the entry
   * point already "handled" and skip, leading to missed injections.
   *
   * @param threadName the name of the thread to check
   * @param offset the WAL offset to check
   * @return {@code true} if the offset is pending injection, {@code false} otherwise
   */
  public boolean isPendingInjection(String threadName, long offset) {
    Queue<Long> queue = pendingInjectionOffsets.get(threadName);
    if (queue == null) {
      return false;
    }
    return queue.contains(offset);
  }
}
