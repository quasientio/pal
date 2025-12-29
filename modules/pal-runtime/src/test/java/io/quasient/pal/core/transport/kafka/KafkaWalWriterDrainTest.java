/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.core.internal.concurrent.MpscKind;
import io.quasient.pal.core.transport.WalWriterStats;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.types.MessageType;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Drains a small queue through a KafkaWalWriter subclass that overrides writeMessage to avoid
 * touching Kafka. Exercises the run() drain loop and counters.
 */
public class KafkaWalWriterDrainTest {

  static class NoopKafkaWalWriter extends KafkaWalWriter {
    NoopKafkaWalWriter(
        UUID peerUuid,
        ZContext context,
        String sync,
        ThreadGroup tg,
        String name,
        HwmMessageQueue<OutboundMsg> walQueue) {
      super(
          peerUuid,
          context,
          sync,
          tg,
          name,
          walQueue,
          new AtomicBoolean(false),
          "inproc://offs",
          /* flushOnClose */ null,
          null,
          null,
          null,
          null,
          (Properties p) -> null);
    }

    @Override
    public void writeMessage(OutboundMsg msg) {
      if (!POISON_PILL.equals(msg)) {
        messagesReceived.incrementAndGet();
        messagesWritten.incrementAndGet();
      }
    }
  }

  private static OutboundMsg newMsg(String id) {
    return new OutboundMsg(
        MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, List.of(), id, null, new ConstructorCall());
  }

  @Test
  public void drainLoop_processesMessages_andUpdatesCounters() throws Exception {
    HwmMessageQueue<OutboundMsg> q = HwmMessageQueue.createQueue(MpscKind.FIXED, 16, 16);
    for (int i = 0; i < 5; i++) {
      q.offer(newMsg("m-" + i));
    }

    NoopKafkaWalWriter w =
        new NoopKafkaWalWriter(
            UUID.randomUUID(), new ZContext(1), "inproc://sync", new ThreadGroup("svc"), "noop", q);

    Thread t = new Thread(w::run, "wal-run");
    t.start();
    // Let the run loop drain at least once
    Thread.sleep(50);
    t.interrupt();
    t.join(1500);

    WalWriterStats stats = w.getLiveStats();
    assertThat(stats.messagesReceived(), greaterThanOrEqualTo(5L));
    assertThat(stats.messagesWritten(), greaterThanOrEqualTo(5L));
  }
}
