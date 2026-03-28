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
