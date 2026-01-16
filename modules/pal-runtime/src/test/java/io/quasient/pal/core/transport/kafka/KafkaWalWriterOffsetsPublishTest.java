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
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

import com.lmax.disruptor.dsl.Disruptor;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.core.transport.WalWriter;
import io.quasient.pal.core.transport.WalWriterStats;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Verifies that when offsets publishing is enabled, the SendCb publishes to the offsets ring
 * without errors (no subscriber required) and increments written counters.
 */
@SuppressWarnings("deprecation")
public class KafkaWalWriterOffsetsPublishTest {

  private KafkaWalWriter newWriter() {
    return new KafkaWalWriter(
        UUID.randomUUID(),
        new ZContext(1),
        "inproc://sync",
        new ThreadGroup("svc"),
        "KafkaWalWriterOffsets",
        /* walQueue */ null,
        new AtomicBoolean(false),
        "inproc://offs",
        /* flushOnClose */ null,
        /* lingerMs */ null,
        /* batchSize */ null,
        /* compressionType */ null,
        /* bufferMemory */ null,
        (Properties p) -> null);
  }

  private Object newSendCb(KafkaWalWriter writer) throws Exception {
    Class<?> inner = null;
    for (Class<?> c : KafkaWalWriter.class.getDeclaredClasses()) {
      if (c.getName().endsWith("SendCb")) {
        inner = c;
        break;
      }
    }
    assert inner != null;
    Constructor<?> ctor = inner.getDeclaredConstructor(writer.getClass());
    ctor.setAccessible(true);
    Object cb = ctor.newInstance(writer);
    Field fMid = inner.getDeclaredField("mid");
    fMid.setAccessible(true);
    fMid.set(cb, "m-1");
    return cb;
  }

  @Test
  public void sendCb_publishesOffsets_whenEnabled() throws Exception {
    KafkaWalWriter writer = newWriter();
    // configure publishOffsets and start ring/disruptor via openConnections
    LogInfo log = new LogInfo("topic-x", "k:9092");
    writer.writeToLog(log, true);
    // openConnections is protected; invoke reflectively
    Method oc = KafkaWalWriter.class.getDeclaredMethod("openConnections");
    oc.setAccessible(true);
    oc.invoke(writer);

    // offsetsRing should be initialized
    Field fRing = WalWriter.class.getDeclaredField("offsetsRing");
    fRing.setAccessible(true);
    Object ring = fRing.get(writer);
    assertThat(ring, notNullValue());

    // fire callback with a metadata (offset 7) to publish into ring
    Object cb = newSendCb(writer);
    RecordMetadata md =
        new RecordMetadata(new TopicPartition("topic-x", 0), 0L, 7L, 0L, Long.valueOf(0L), 0, 0);
    var onCompletion =
        cb.getClass().getDeclaredMethod("onCompletion", RecordMetadata.class, Exception.class);
    onCompletion.setAccessible(true);
    onCompletion.invoke(cb, md, null);

    WalWriterStats stats = writer.getLiveStats();
    // messagesWritten should have increased via callback
    assertThat(stats.messagesWritten(), greaterThan(0L));

    // stop disruptor safely without touching producer (which was never created)
    Field fDis = WalWriter.class.getDeclaredField("offsetsDisruptor");
    fDis.setAccessible(true);
    Disruptor<?> dis = (Disruptor<?>) fDis.get(writer);
    if (dis != null) {
      dis.shutdown();
    }
  }
}
