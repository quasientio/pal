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

import static org.mockito.Mockito.*;

import io.etcd.jetcd.Lease;
import java.util.concurrent.ScheduledFuture;
import org.junit.Test;

public class PeerLeaseTest {

  @SuppressWarnings("unchecked")
  @Test
  public void close_cancelsKeepAlive_andRevokesLease() throws Exception {
    ScheduledFuture<?> ka = mock(ScheduledFuture.class);
    Lease lease = mock(Lease.class);

    PeerLease pl = new PeerLease(123L, ka, lease);
    pl.close();

    verify(ka, times(1)).cancel(true);
    verify(lease, times(1)).revoke(123L);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void close_ignoresExceptionsFromRevoke() throws Exception {
    ScheduledFuture<?> ka = mock(ScheduledFuture.class);
    Lease lease = mock(Lease.class);
    when(lease.revoke(anyLong())).thenThrow(new RuntimeException("boom"));

    PeerLease pl = new PeerLease(321L, ka, lease);
    pl.close(); // should not throw

    verify(ka, times(1)).cancel(true);
    verify(lease, times(1)).revoke(321L);
  }
}
