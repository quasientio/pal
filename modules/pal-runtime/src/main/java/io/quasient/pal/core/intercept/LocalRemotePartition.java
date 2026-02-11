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
import io.quasient.pal.messages.colfer.InterceptMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * Single-pass partitioner that separates intercept messages into local and remote lists based on
 * peer UUID comparison.
 *
 * <p>This replaces the two stream-based filter methods in {@link InterceptChecker} ({@code
 * filterLocalIntercepts()} and {@code filterRemoteIntercepts()}) with a single iteration. Instead
 * of iterating the matches list twice (once per filter), this class partitions all messages in one
 * pass.
 *
 * <p>Usage pattern with ThreadLocal:
 *
 * <pre>{@code
 * private static final ThreadLocal<LocalRemotePartition> TL_LR_PARTITION =
 *     ThreadLocal.withInitial(LocalRemotePartition::new);
 *
 * // In checkIntercepts method:
 * LocalRemotePartition lrPartition = TL_LR_PARTITION.get();
 * lrPartition.partition(matches, peerUuidString);
 * // Use lrPartition.local(), lrPartition.remote()
 * }</pre>
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP",
    justification = "Intentional: mutable lists returned for zero-allocation thread-local reuse")
public class LocalRemotePartition {

  /** Intercepts whose callback peer UUID matches the local peer. */
  private final List<InterceptMessage> local = new ArrayList<>();

  /** Intercepts whose callback peer UUID differs from the local peer. */
  private final List<InterceptMessage> remote = new ArrayList<>();

  /**
   * Clears both partition lists, preparing this instance for reuse.
   *
   * <p>This method is called automatically by {@link #partition(List, String)}.
   */
  public void clear() {
    local.clear();
    remote.clear();
  }

  /**
   * Partitions the given list of intercept messages into local and remote lists in a single pass.
   *
   * <p>An intercept is considered local when its callback peer UUID matches the provided peer UUID
   * string. All other intercepts are considered remote.
   *
   * <p><strong>Thread safety:</strong> This method is not thread-safe. Each thread should use its
   * own instance (typically via ThreadLocal).
   *
   * @param matches the list of matched intercept messages to partition; must not be null
   * @param thisPeerUuidString the UUID string of the local peer
   */
  public void partition(List<InterceptMessage> matches, String thisPeerUuidString) {
    clear();
    for (int i = 0; i < matches.size(); i++) {
      InterceptMessage im = matches.get(i);
      if (thisPeerUuidString.equals(im.getPeerUuid())) {
        local.add(im);
      } else {
        remote.add(im);
      }
    }
  }

  /**
   * Returns the list of local intercepts from the last {@link #partition(List, String)} call.
   *
   * @return mutable list of local intercepts; never null
   */
  public List<InterceptMessage> local() {
    return local;
  }

  /**
   * Returns the list of remote intercepts from the last {@link #partition(List, String)} call.
   *
   * @return mutable list of remote intercepts; never null
   */
  public List<InterceptMessage> remote() {
    return remote;
  }
}
