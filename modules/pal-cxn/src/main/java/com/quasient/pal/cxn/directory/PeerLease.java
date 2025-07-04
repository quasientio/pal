/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn.directory;

import io.etcd.jetcd.Lease;
import java.util.concurrent.ScheduledFuture;

/**
 * Represents a lease-holder that maintains a keep-alive for an etcd lease and can revoke it when
 * closed.
 *
 * <p>Upon closing, the scheduled keep-alive task is canceled and the etcd lease is revoked.
 */
public class PeerLease implements AutoCloseable {

  /** The identifier of the etcd lease. */
  public final long leaseId;

  /** The scheduled task responsible for sending periodic keep-alive messages. */
  private final ScheduledFuture<?> ka;

  /** The etcd lease client used to revoke the lease. */
  private final Lease leaseClient;

  /**
   * Constructs a PeerLease.
   *
   * @param leaseId the etcd lease identifier
   * @param ka the scheduled future controlling the keep-alive loop
   * @param leaseClient the client used to revoke the etcd lease
   */
  PeerLease(long leaseId, ScheduledFuture<?> ka, Lease leaseClient) {
    this.leaseId = leaseId;
    this.ka = ka;
    this.leaseClient = leaseClient;
  }

  /**
   * Cancels the keep-alive task and revokes the etcd lease.
   *
   * <p>This will stop the scheduled keep-alive loop by canceling {@code ka}, then attempt to revoke
   * the lease via {@code leaseClient}. Any exceptions thrown during revocation are caught and
   * ignored.
   */
  @Override
  public void close() {
    // Stop the keep-alive loop
    ka.cancel(true);

    // Attempt to revoke the lease, ignoring any failures
    try {
      var unused = leaseClient.revoke(leaseId);
    } catch (Exception ignored) {
      // Intentionally ignored
    }
  }
}
