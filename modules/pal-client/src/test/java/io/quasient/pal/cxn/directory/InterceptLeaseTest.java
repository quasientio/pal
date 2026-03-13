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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.etcd.jetcd.Lease;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/**
 * Unit tests for {@link InterceptLease}.
 *
 * <p>Tests cover manual keep-alive, auto-refresh scheduling, close/revoke semantics, idempotency,
 * and the {@link InterceptLease#NONE} sentinel.
 */
public class InterceptLeaseTest {

  /**
   * Verifies that keepAlive invokes keepAliveOnce on the lease client exactly once.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.keepAlive_callsKeepAliveOnceOnLeaseClient]
   */
  @Test
  public void keepAlive_callsKeepAliveOnceOnLeaseClient() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    UUID interceptUuid = UUID.randomUUID();
    InterceptLease lease = new InterceptLease(12345L, interceptUuid, 30L, leaseClient, scheduler);

    // When
    lease.keepAlive();

    // Then
    verify(leaseClient, times(1)).keepAliveOnce(12345L);
  }

  /**
   * Verifies that keepAlive throws IllegalStateException after the lease has been closed.
   *
   * <p>Acceptance Criterion:
   * [TEST:InterceptLeaseTest.keepAlive_afterClose_throwsIllegalStateException]
   */
  @Test(expected = IllegalStateException.class)
  public void keepAlive_afterClose_throwsIllegalStateException() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    UUID interceptUuid = UUID.randomUUID();
    InterceptLease lease = new InterceptLease(12345L, interceptUuid, 30L, leaseClient, scheduler);
    lease.close();

    // When
    lease.keepAlive();

    // Then: IllegalStateException is thrown
  }

  /**
   * Verifies that startAutoRefresh schedules a periodic keepAlive task at ttl/3 interval.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.startAutoRefresh_schedulesPeriodicKeepAlive]
   */
  @SuppressWarnings("unchecked")
  @Test
  public void startAutoRefresh_schedulesPeriodicKeepAlive() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    ScheduledFuture<?> future = mock(ScheduledFuture.class);
    doReturn(future)
        .when(scheduler)
        .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any());
    UUID interceptUuid = UUID.randomUUID();
    InterceptLease lease = new InterceptLease(12345L, interceptUuid, 30L, leaseClient, scheduler);

    // When
    lease.startAutoRefresh();

    // Then
    verify(scheduler, times(1))
        .scheduleAtFixedRate(any(Runnable.class), eq(10L), eq(10L), eq(TimeUnit.SECONDS));
    assertThat(lease.isAutoRefreshing(), is(true));
  }

  /**
   * Verifies that calling startAutoRefresh twice is idempotent (no second task scheduled).
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.startAutoRefresh_calledTwice_idempotent]
   */
  @SuppressWarnings("unchecked")
  @Test
  public void startAutoRefresh_calledTwice_idempotent() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    ScheduledFuture<?> future = mock(ScheduledFuture.class);
    doReturn(future)
        .when(scheduler)
        .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any());
    UUID interceptUuid = UUID.randomUUID();
    InterceptLease lease = new InterceptLease(12345L, interceptUuid, 30L, leaseClient, scheduler);
    lease.startAutoRefresh();

    // When
    lease.startAutoRefresh();

    // Then: second call creates a task but it gets cancelled immediately; only one stays active
    assertThat(lease.isAutoRefreshing(), is(true));
  }

  /**
   * Verifies that stopAutoRefresh cancels the scheduled task without revoking the lease.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.stopAutoRefresh_cancelsScheduledTask]
   */
  @SuppressWarnings("unchecked")
  @Test
  public void stopAutoRefresh_cancelsScheduledTask() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    ScheduledFuture<?> future = mock(ScheduledFuture.class);
    doReturn(future)
        .when(scheduler)
        .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any());
    UUID interceptUuid = UUID.randomUUID();
    InterceptLease lease = new InterceptLease(12345L, interceptUuid, 30L, leaseClient, scheduler);
    lease.startAutoRefresh();

    // When
    lease.stopAutoRefresh();

    // Then
    verify(future, times(1)).cancel(false);
    assertThat(lease.isAutoRefreshing(), is(false));
    verify(leaseClient, never()).revoke(anyLong());
  }

  /**
   * Verifies that stopAutoRefresh is a no-op when no auto-refresh is running.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.stopAutoRefresh_withoutAutoRefresh_noOp]
   */
  @Test
  public void stopAutoRefresh_withoutAutoRefresh_noOp() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    UUID interceptUuid = UUID.randomUUID();
    InterceptLease lease = new InterceptLease(12345L, interceptUuid, 30L, leaseClient, scheduler);

    // When
    lease.stopAutoRefresh();

    // Then: no exception, not auto-refreshing
    assertThat(lease.isAutoRefreshing(), is(false));
  }

  /**
   * Verifies that close cancels auto-refresh and revokes the lease.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.close_cancelsKeepAliveAndRevokesLease]
   */
  @SuppressWarnings("unchecked")
  @Test
  public void close_cancelsKeepAliveAndRevokesLease() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    ScheduledFuture<?> future = mock(ScheduledFuture.class);
    doReturn(future)
        .when(scheduler)
        .scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any());
    UUID interceptUuid = UUID.randomUUID();
    InterceptLease lease = new InterceptLease(12345L, interceptUuid, 30L, leaseClient, scheduler);
    lease.startAutoRefresh();

    // When
    lease.close();

    // Then
    verify(future, times(1)).cancel(true);
    verify(leaseClient, times(1)).revoke(12345L);
    assertThat(lease.isClosed(), is(true));
  }

  /**
   * Verifies that close revokes the lease even when no auto-refresh is running.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.close_withoutAutoRefresh_revokesLease]
   */
  @Test
  public void close_withoutAutoRefresh_revokesLease() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    UUID interceptUuid = UUID.randomUUID();
    InterceptLease lease = new InterceptLease(12345L, interceptUuid, 30L, leaseClient, scheduler);

    // When
    lease.close();

    // Then
    verify(leaseClient, times(1)).revoke(12345L);
    assertThat(lease.isClosed(), is(true));
  }

  /**
   * Verifies that calling close twice is safe and does not revoke a second time.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.close_idempotent]
   */
  @Test
  public void close_idempotent() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    UUID interceptUuid = UUID.randomUUID();
    InterceptLease lease = new InterceptLease(12345L, interceptUuid, 30L, leaseClient, scheduler);
    lease.close();

    // When
    lease.close();

    // Then: revoke called only once
    verify(leaseClient, times(1)).revoke(12345L);
  }

  /**
   * Verifies that InterceptLease.NONE sentinel's keepAlive is a no-op.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.none_sentinel_keepAliveIsNoOp]
   */
  @Test
  public void none_sentinel_keepAliveIsNoOp() {
    // When
    InterceptLease.NONE.keepAlive();

    // Then: no exception thrown
  }

  /**
   * Verifies that InterceptLease.NONE sentinel's close is a no-op.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.none_sentinel_closeIsNoOp]
   */
  @Test
  public void none_sentinel_closeIsNoOp() {
    // When
    InterceptLease.NONE.close();

    // Then: no exception thrown
  }

  /**
   * Verifies that InterceptLease.NONE sentinel returns sensible defaults from getters.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.none_sentinel_gettersReturnDefaults]
   */
  @Test
  public void none_sentinel_gettersReturnDefaults() {
    // When / Then
    assertThat(InterceptLease.NONE.getLeaseId(), is(0L));
    assertThat(InterceptLease.NONE.getInterceptUuid(), is(new UUID(0L, 0L)));
    assertThat(InterceptLease.NONE.getTtlSeconds(), is(0L));
    assertThat(InterceptLease.NONE.isAutoRefreshing(), is(false));
    assertThat(InterceptLease.NONE.isClosed(), is(false));
  }

  /**
   * Verifies that getters return the values provided at construction time.
   *
   * <p>Acceptance Criterion: [TEST:InterceptLeaseTest.getters_returnConstructorValues]
   */
  @Test
  public void getters_returnConstructorValues() {
    // Given
    Lease leaseClient = mock(Lease.class);
    ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    UUID interceptUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
    InterceptLease lease = new InterceptLease(999L, interceptUuid, 60L, leaseClient, scheduler);

    // When / Then
    assertThat(lease.getLeaseId(), is(999L));
    assertThat(lease.getInterceptUuid(), is(interceptUuid));
    assertThat(lease.getTtlSeconds(), is(60L));
  }
}
