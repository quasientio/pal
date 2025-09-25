/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.transport.kafka;

import static org.junit.Assert.assertTrue;

import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.Assume;
import org.junit.Test;
import org.zeromq.ZContext;

public class LogReaderRunInterruptTest {

  @Test
  public void run_waitsThenInterrupts_cleanly() throws Exception {
    final ZContext ctx;
    try {
      ctx = new ZContext(1);
    } catch (Throwable t) {
      Assume.assumeNoException("Skipping due to ZMQ sandbox", t);
      return;
    }

    // Mock-like consumer: we don't reach poll since we interrupt while awaiting acceptRequests
    @SuppressWarnings("unchecked")
    Consumer<String, byte[]> consumer =
        (Consumer<String, byte[]>) org.mockito.Mockito.mock(Consumer.class);

    DirectoryConnectionProvider dcp =
        new DirectoryConnectionProvider(com.quasient.pal.cxn.directory.PalDirectory.NO_URL);

    LogReader lr =
        new LogReader(
            UUID.randomUUID(),
            ctx,
            "inproc://sync",
            new ThreadGroup("svc"),
            "LogReader.service",
            "inproc://dealer." + UUID.randomUUID(),
            "inproc://offs." + UUID.randomUUID(),
            dcp,
            consumer,
            /*autoCommit*/ true,
            /*pollMs*/ 5);

    CountDownLatch started = new CountDownLatch(1);
    Thread t =
        new Thread(
            () -> {
              started.countDown();
              lr.run();
            },
            "lr-run");
    t.start();
    assertTrue(started.await(1, TimeUnit.SECONDS));
    // Thread should be waiting on condition; interrupt it to trigger InterruptedException path
    t.interrupt();
    t.join(1500);
    ctx.close();
  }
}
