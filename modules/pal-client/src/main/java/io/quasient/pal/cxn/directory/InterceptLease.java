/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.directory;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.etcd.jetcd.Lease;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifecycle of an etcd lease backing an intercept registration.
 *
 * <p>An {@code InterceptLease} wraps an etcd lease ID and provides manual keep-alive, scheduled
 * auto-refresh, and close/revoke semantics. It is returned by {@link PalDirectory#createIntercept}
 * when a TTL is specified and enables callers to extend an intercept's lifetime programmatically.
 *
 * <p>For zero-TTL intercepts (no expiration), the static {@link #NONE} sentinel is returned. All
 * methods on the sentinel are safe no-ops.
 *
 * <p>This class is thread-safe. The {@code closed} flag is volatile, and the auto-refresh task
 * reference is managed via {@link AtomicReference} to ensure safe concurrent access.
 *
 * @see PeerLease
 */
@SuppressFBWarnings(
    value = {"DE_MIGHT_IGNORE", "DLS_DEAD_LOCAL_STORE"},
    justification = "close() intentionally ignores revoke failures - best-effort cleanup")
public class InterceptLease implements AutoCloseable {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptLease.class);

  /**
   * Sentinel instance for intercepts with no TTL. All methods are safe no-ops; {@link #close()}
   * does nothing, {@link #keepAlive()} does nothing, and getters return default values.
   */
  public static final InterceptLease NONE =
      new InterceptLease(0L, new UUID(0L, 0L), 0L, null, null) {

        @Override
        public void keepAlive() {
          // no-op for zero-TTL intercepts
        }

        @Override
        public void startAutoRefresh() {
          // no-op for zero-TTL intercepts
        }

        @Override
        public void stopAutoRefresh() {
          // no-op for zero-TTL intercepts
        }

        @Override
        public void close() {
          // no-op for zero-TTL intercepts
        }

        @Override
        public boolean isAutoRefreshing() {
          return false;
        }

        @Override
        public boolean isClosed() {
          return false;
        }
      };

  /** The etcd lease identifier. */
  private final long leaseId;

  /** The UUID of the intercept this lease backs. */
  private final UUID interceptUuid;

  /** The TTL of the lease in seconds. */
  private final long ttlSeconds;

  /** The etcd lease client used for keep-alive and revoke operations. */
  private final Lease leaseClient;

  /** The shared scheduler used for auto-refresh tasks. */
  private final ScheduledExecutorService scheduler;

  /** The currently scheduled auto-refresh task, or {@code null} if none is active. */
  private final AtomicReference<ScheduledFuture<?>> keepAliveTask = new AtomicReference<>();

  /** Whether this lease has been closed. */
  private volatile boolean closed;

  /**
   * Constructs a new {@code InterceptLease}.
   *
   * <p>Package-private: instances are created by {@link PalDirectory}.
   *
   * @param leaseId the etcd lease identifier
   * @param interceptUuid the UUID of the intercept this lease backs
   * @param ttlSeconds the TTL of the lease in seconds
   * @param leaseClient the etcd lease client for keep-alive and revoke operations
   * @param scheduler the shared scheduled executor for auto-refresh tasks
   */
  InterceptLease(
      long leaseId,
      UUID interceptUuid,
      long ttlSeconds,
      Lease leaseClient,
      ScheduledExecutorService scheduler) {
    this.leaseId = leaseId;
    this.interceptUuid = interceptUuid;
    this.ttlSeconds = ttlSeconds;
    this.leaseClient = leaseClient;
    this.scheduler = scheduler;
    this.closed = false;
  }

  /**
   * Sends a single keep-alive request to etcd to refresh this lease.
   *
   * @throws IllegalStateException if this lease has been closed
   */
  public void keepAlive() {
    if (closed) {
      throw new IllegalStateException(
          "Cannot keep alive a closed InterceptLease (intercept=" + interceptUuid + ")");
    }
    try {
      @SuppressWarnings("unused")
      var unused = leaseClient.keepAliveOnce(leaseId);
    } catch (Exception e) {
      logger.warn("Lease keep-alive failed for intercept {}", interceptUuid, e);
    }
  }

  /**
   * Starts automatic periodic refresh of this lease at an interval of {@code ttlSeconds / 3}.
   *
   * <p>This method is idempotent: calling it when auto-refresh is already running has no effect.
   * The scheduled task calls {@link Lease#keepAliveOnce(long)} on each tick.
   *
   * @throws IllegalStateException if this lease has been closed
   */
  public void startAutoRefresh() {
    if (closed) {
      throw new IllegalStateException(
          "Cannot start auto-refresh on a closed InterceptLease (intercept=" + interceptUuid + ")");
    }
    long intervalSeconds = ttlSeconds / 3;
    ScheduledFuture<?> task =
        scheduler.scheduleAtFixedRate(
            () -> {
              try {
                @SuppressWarnings("unused")
                var unused = leaseClient.keepAliveOnce(leaseId);
              } catch (Exception e) {
                logger.warn("Auto-refresh keep-alive failed for intercept {}", interceptUuid, e);
              }
            },
            intervalSeconds,
            intervalSeconds,
            TimeUnit.SECONDS);
    if (!keepAliveTask.compareAndSet(null, task)) {
      // Another call already set the task; cancel the one we just created
      task.cancel(false);
    }
  }

  /**
   * Stops the auto-refresh task if one is running.
   *
   * <p>This does <em>not</em> revoke the lease; the intercept remains active until the lease
   * expires naturally or {@link #close()} is called.
   */
  public void stopAutoRefresh() {
    ScheduledFuture<?> task = keepAliveTask.getAndSet(null);
    if (task != null) {
      task.cancel(false);
    }
  }

  /**
   * Cancels any auto-refresh task and revokes the etcd lease.
   *
   * <p>This method is idempotent: calling it on an already-closed lease has no effect. Revoke
   * failures are caught and ignored (best-effort cleanup), following the same pattern as {@link
   * PeerLease#close()}.
   */
  @Override
  public void close() {
    if (closed) {
      return;
    }
    closed = true;

    // Cancel auto-refresh if running
    ScheduledFuture<?> task = keepAliveTask.getAndSet(null);
    if (task != null) {
      task.cancel(true);
    }

    // Revoke the lease (best-effort)
    try {
      @SuppressWarnings("unused")
      var unused = leaseClient.revoke(leaseId);
    } catch (Exception ignored) {
      // Intentionally ignored — same pattern as PeerLease
    }
  }

  /**
   * Returns whether auto-refresh is currently active.
   *
   * @return {@code true} if an auto-refresh task is scheduled and has not been cancelled
   */
  public boolean isAutoRefreshing() {
    ScheduledFuture<?> task = keepAliveTask.get();
    return task != null && !task.isCancelled();
  }

  /**
   * Returns whether this lease has been closed.
   *
   * @return {@code true} if {@link #close()} has been called
   */
  public boolean isClosed() {
    return closed;
  }

  /**
   * Returns the etcd lease identifier.
   *
   * @return the lease ID
   */
  public long getLeaseId() {
    return leaseId;
  }

  /**
   * Returns the UUID of the intercept this lease backs.
   *
   * @return the intercept UUID
   */
  public UUID getInterceptUuid() {
    return interceptUuid;
  }

  /**
   * Returns the TTL of the lease in seconds.
   *
   * @return the TTL in seconds
   */
  public long getTtlSeconds() {
    return ttlSeconds;
  }
}
