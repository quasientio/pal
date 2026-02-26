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
import java.util.concurrent.ConcurrentHashMap;

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

  /** Lazily created and cached per-thread cursors. Thread-safe via {@link ConcurrentHashMap}. */
  private final Map<String, ReplayCursor> cursors = new ConcurrentHashMap<>();

  /**
   * Constructs a new {@code ReplayContext} with all required sub-components.
   *
   * @param walIndex the indexed WAL providing the oracle for replay
   * @param policy the policy determining replay actions
   * @param objectStore the bidirectional WAL ref ↔ live object mapping
   * @param divergenceDetector the verification engine for comparing actual vs WAL values
   */
  public ReplayContext(
      WalIndex walIndex,
      ReplayPolicy policy,
      ReplayObjectStore objectStore,
      DivergenceDetector divergenceDetector) {
    this.walIndex = walIndex;
    this.policy = policy;
    this.objectStore = objectStore;
    this.divergenceDetector = divergenceDetector;
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
}
