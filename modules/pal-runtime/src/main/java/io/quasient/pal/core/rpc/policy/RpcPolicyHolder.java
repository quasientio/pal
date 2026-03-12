/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.rpc.policy;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Volatile holder for a swappable {@link RpcPolicy} reference, enabling hot-reload of RPC policy
 * rules at runtime without restarting the peer.
 *
 * <p>This class follows the single-writer, lock-free-reader pattern established by {@code
 * InterceptRequests}: a single writer thread (the file-watcher) calls {@link #updatePolicy}, while
 * multiple reader threads on the dispatch hot path call {@link #getPolicy} concurrently. The {@code
 * volatile} keyword guarantees that readers always see the most recently published policy
 * reference.
 *
 * <p>The held {@link RpcPolicy} is itself immutable, so readers always observe a consistent,
 * complete policy object.
 */
@Singleton
public class RpcPolicyHolder {

  /** The current policy reference, swapped atomically by a single writer thread. */
  private volatile RpcPolicy policy;

  /**
   * Creates a holder initialized with the given policy.
   *
   * @param initialPolicy the initial RPC policy to hold (typically built at startup from YAML,
   *     presets, and the default action)
   */
  @Inject
  public RpcPolicyHolder(RpcPolicy initialPolicy) {
    this.policy = initialPolicy;
  }

  /**
   * Returns the current {@link RpcPolicy}. This is a lock-free volatile read, safe for use on the
   * dispatch hot path from any thread.
   *
   * @return the current policy (never {@code null})
   */
  public RpcPolicy getPolicy() {
    return policy;
  }

  /**
   * Swaps the current policy with the given new policy. This method is intended to be called by a
   * single writer thread (the file-watcher); concurrent writers are not supported.
   *
   * @param newPolicy the new policy to install
   */
  @SuppressWarnings("NonAtomicOperationOnVolatileField")
  public void updatePolicy(RpcPolicy newPolicy) {
    this.policy = newPolicy;
  }

  /**
   * Returns whether the current policy contains any visibility-based rules.
   *
   * <p>Delegates to {@link RpcPolicy#hasVisibilityRules()} on the currently held policy. When
   * {@code false}, callers on the hot path can skip modifiers extraction.
   *
   * @return {@code true} if at least one rule in the current policy filters by visibility
   */
  public boolean hasVisibilityRules() {
    return policy.hasVisibilityRules();
  }
}
