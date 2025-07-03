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

/** Lease-holder returned to the caller so it can stop KA & revoke the etcd lease. */
public class PeerLease implements AutoCloseable {
  public final long leaseId;
  private final ScheduledFuture<?> ka;
  private final Lease leaseClient;

  PeerLease(long leaseId, ScheduledFuture<?> ka, Lease leaseClient) {
    this.leaseId = leaseId;
    this.ka = ka;
    this.leaseClient = leaseClient;
  }

  @Override
  public void close() {
    ka.cancel(true); // stop keep-alive loop
    try {
      var unused = leaseClient.revoke(leaseId);
    } catch (Exception ignored) {
      // ignore
    }
  }
}
