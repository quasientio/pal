/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.messages.OutboundMsg;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.Cluster;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/** Verifies direct-write mode (queueless) and the flush-on-close behavior. */
public class KafkaWalWriterDirectWriteFlushTest extends ZmqEnabledTest {

  private static final UUID PEER_ID = UUID.randomUUID();
  private static final LogInfo WAL_INFO = new LogInfo("test_app", "localhost:9092");

  private ZContext ctx;
  private ServiceManager manager;

  private static final class CapturingProducer extends MockProducer<String, byte[]> {
    volatile boolean flushed;

    CapturingProducer() {
      super(Cluster.empty(), true, null, null, null);
    }

    @Override
    public synchronized void flush() {
      flushed = true;
      super.flush();
    }
  }

  @Before
  public void setup() {
    ctx = createContext();
  }

  @After
  public void cleanup() throws InterruptedException {
    closeContext(ctx);
  }

  private KafkaWalWriter newWriterDirect(boolean flushOnClose, CapturingProducer producer) {
    return new KafkaWalWriter(
        PEER_ID,
        ctx,
        SYNC_SOCKET_ADDRESS,
        new ThreadGroup("svc"),
        "KafkaWalWriterDirect",
        (HwmMessageQueue<OutboundMsg>) null, // queueless
        new AtomicBoolean(false),
        /* offset.pub */ "inproc://offsets",
        String.valueOf(flushOnClose),
        null,
        null,
        null,
        null,
        props -> producer);
  }

  @Test
  public void directWrite_flushOnClose_true_callsFlush() {
    CapturingProducer cap = new CapturingProducer();
    KafkaWalWriter w = newWriterDirect(true, cap);
    w.writeToLog(WAL_INFO, /* publishOffsets */ false);

    Set<Service> services = new HashSet<>(Collections.singletonList(w));
    manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), ctx);
    // Stop service → closeConnections runs (flush expected true)
    manager.stopAsync().awaitStopped();
    assertThat(cap.flushed, is(true));
  }

  @Test
  public void directWrite_flushOnClose_false_doesNotCallFlush() {
    CapturingProducer cap = new CapturingProducer();
    KafkaWalWriter w = newWriterDirect(false, cap);
    w.writeToLog(WAL_INFO, /* publishOffsets */ false);

    Set<Service> services = new HashSet<>(Collections.singletonList(w));
    manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), ctx);
    // Stop service → closeConnections runs (flush expected false)
    manager.stopAsync().awaitStopped();
    assertThat(cap.flushed, is(false));
  }
}
