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

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assume;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Covers the direct-write path (queueless) in KafkaWalWriter.run(): it parks on shutdownMonitor
 * until interrupted or shutdownRequested becomes true.
 */
public class KafkaWalWriterDirectWriteWaitTest {

  private KafkaWalWriter newQueuelessWriter() {
    return new KafkaWalWriter(
        UUID.randomUUID(),
        new ZContext(1),
        "inproc://sync",
        new ThreadGroup("svc"),
        "KafkaWalWriterDirectWait",
        /* walQueue */ null, // queueless
        new AtomicBoolean(false),
        "inproc://offs",
        /* flushOnClose */ null,
        /* lingerMs */ null,
        /* batchSize */ null,
        /* compressionType */ null,
        /* bufferMemory */ null,
        (Properties p) -> null);
  }

  @Test
  public void directWrite_runParksThenInterruptsCleanly() throws Exception {
    final ZContext ctx;
    try {
      ctx = new ZContext(1);
    } catch (Throwable t) {
      Assume.assumeNoException("Skipping due to ZMQ sandbox", t);
      return;
    }
    KafkaWalWriter writer = newQueuelessWriter();
    Thread t = new Thread(writer::run, "wal-direct-run");
    t.start();
    Thread.sleep(30);
    t.interrupt();
    t.join(1000);
    assertTrue("Thread should have finished", !t.isAlive());
    ctx.close();
  }
}
