/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-pass partitioner that replaces four stream-based filter methods in {@link
 * io.quasient.pal.core.execution.java.BaseExecMessageDispatcher} with a single iteration over the
 * intercept list.
 *
 * <p>Instead of calling {@code stream().filter().toList()} four times (once each for BEFORE,
 * BEFORE_ASYNC, AFTER, and AFTER_ASYNC), this class partitions all intercept messages into five
 * pre-allocated lists in a single pass. The lists are reusable via {@link #clear()}, making this
 * class suitable for thread-local storage to eliminate per-dispatch allocations.
 *
 * <p>Usage pattern with ThreadLocal:
 *
 * <pre>{@code
 * private static final ThreadLocal<InterceptPartition> TL_PARTITION =
 *     ThreadLocal.withInitial(InterceptPartition::new);
 *
 * // In dispatch method:
 * InterceptPartition partition = TL_PARTITION.get();
 * partition.partition(localIntercepts);
 * // Use partition.before(), partition.after(), etc.
 * }</pre>
 *
 * @see InterceptType
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "Intentional: mutable lists returned for zero-allocation thread-local reuse")
public class InterceptPartition {

  /** Intercepts of type {@link InterceptType#BEFORE}. */
  private final List<InterceptMessage> before = new ArrayList<>();

  /** Intercepts of type {@link InterceptType#BEFORE_ASYNC}. */
  private final List<InterceptMessage> beforeAsync = new ArrayList<>();

  /** Intercepts of type {@link InterceptType#AFTER}. */
  private final List<InterceptMessage> after = new ArrayList<>();

  /** Intercepts of type {@link InterceptType#AFTER_ASYNC}. */
  private final List<InterceptMessage> afterAsync = new ArrayList<>();

  /** Intercepts of type {@link InterceptType#AROUND}. */
  private final List<InterceptMessage> around = new ArrayList<>();

  /**
   * Clears all partition lists, preparing this instance for reuse.
   *
   * <p>This method must be called before each new partitioning operation to ensure no stale
   * elements remain from a previous call. The {@link #partition(List)} method calls this
   * automatically.
   */
  public void clear() {
    before.clear();
    beforeAsync.clear();
    after.clear();
    afterAsync.clear();
    around.clear();
  }

  /**
   * Partitions the given list of intercept messages into five sub-lists by intercept type in a
   * single pass.
   *
   * <p>This method first clears all sub-lists, then iterates through the input list exactly once,
   * routing each message to the appropriate sub-list based on its {@link InterceptType}. The
   * resulting sub-lists preserve insertion order within each partition.
   *
   * <p><strong>Thread safety:</strong> This method is not thread-safe. Each thread should use its
   * own instance (typically via ThreadLocal). The partition lists must be consumed before any
   * operation that could trigger a nested dispatch on the same thread.
   *
   * @param intercepts the list of intercept messages to partition; must not be null
   */
  public void partition(List<InterceptMessage> intercepts) {
    clear();
    for (int i = 0; i < intercepts.size(); i++) {
      InterceptMessage im = intercepts.get(i);
      switch (InterceptType.fromByte(im.getInterceptType())) {
        case BEFORE -> before.add(im);
        case BEFORE_ASYNC -> beforeAsync.add(im);
        case AFTER -> after.add(im);
        case AFTER_ASYNC -> afterAsync.add(im);
        case AROUND -> around.add(im);
      }
    }
  }

  /**
   * Returns the list of BEFORE intercepts from the last {@link #partition(List)} call.
   *
   * @return mutable list of BEFORE intercepts; never null
   */
  public List<InterceptMessage> before() {
    return before;
  }

  /**
   * Returns the list of BEFORE_ASYNC intercepts from the last {@link #partition(List)} call.
   *
   * @return mutable list of BEFORE_ASYNC intercepts; never null
   */
  public List<InterceptMessage> beforeAsync() {
    return beforeAsync;
  }

  /**
   * Returns the list of AFTER intercepts from the last {@link #partition(List)} call.
   *
   * @return mutable list of AFTER intercepts; never null
   */
  public List<InterceptMessage> after() {
    return after;
  }

  /**
   * Returns the list of AFTER_ASYNC intercepts from the last {@link #partition(List)} call.
   *
   * @return mutable list of AFTER_ASYNC intercepts; never null
   */
  public List<InterceptMessage> afterAsync() {
    return afterAsync;
  }

  /**
   * Returns the list of AROUND intercepts from the last {@link #partition(List)} call.
   *
   * @return mutable list of AROUND intercepts; never null
   */
  public List<InterceptMessage> around() {
    return around;
  }
}
