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
package io.quasient.pal.core.transport.kafka;

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
        /* offsetsRingSize */ null,
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
