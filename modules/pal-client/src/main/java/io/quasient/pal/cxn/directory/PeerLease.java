/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.cxn.directory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.etcd.jetcd.Lease;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a lease-holder that maintains a keep-alive for an etcd lease and can revoke it when
 * closed.
 *
 * <p>Upon closing, the scheduled keep-alive task is canceled and the etcd lease is revoked.
 */
@SuppressFBWarnings(
    value = {"DE_MIGHT_IGNORE", "DLS_DEAD_LOCAL_STORE"},
    justification = "close() intentionally ignores revoke failures - best-effort cleanup")
public class PeerLease implements AutoCloseable {

  /** Class logger. */
  private static final Logger logger = LoggerFactory.getLogger(PeerLease.class);

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
      @SuppressWarnings("unused")
      var unused = leaseClient.revoke(leaseId);
    } catch (Exception ex) {
      if (logger.isDebugEnabled()) {
        logger.debug("Error revoking peer lease {}", leaseId, ex);
      }
    }
  }
}
