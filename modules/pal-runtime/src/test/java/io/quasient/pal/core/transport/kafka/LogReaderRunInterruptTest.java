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
import static org.mockito.Mockito.mock;

import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
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
    Consumer<String, byte[]> consumer = (Consumer<String, byte[]>) mock(Consumer.class);

    DirectoryConnectionProvider dcp = new DirectoryConnectionProvider(PalDirectory.NO_URL);

    KafkaSourceLogReader lr =
        new KafkaSourceLogReader(
            UUID.randomUUID(),
            ctx,
            "inproc://sync",
            new ThreadGroup("svc"),
            "KafkaSourceLogReader.service",
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
