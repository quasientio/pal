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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

import io.quasient.pal.core.transport.WalWriterStats;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;
import org.zeromq.ZContext;

/** Pure unit test for KafkaWalWriter.SendCb callback without sockets/brokers. */
@SuppressWarnings("deprecation")
public class KafkaWalWriterSendCbTest {

  private KafkaWalWriter newWriter() throws Exception {
    return new KafkaWalWriter(
        UUID.randomUUID(),
        new ZContext(1),
        "inproc://sync",
        new ThreadGroup("svc"),
        "KafkaWalWriterTest",
        /* walQueue */ null,
        new AtomicBoolean(false),
        "inproc://offs",
        /* flushOnClose */ null,
        /* lingerMs */ null,
        /* batchSize */ null,
        /* compressionType */ null,
        /* bufferMemory */ null,
        (Properties props) -> null);
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
    // set mid field for logging path
    Field fMid = inner.getDeclaredField("mid");
    fMid.setAccessible(true);
    fMid.set(cb, "mid-1");
    return cb;
  }

  @Test
  public void onCompletion_success_incrementsWritten() throws Exception {
    KafkaWalWriter writer = newWriter();
    Object cb = newSendCb(writer);
    // Construct a minimal RecordMetadata with offset 5
    RecordMetadata md =
        new RecordMetadata(new TopicPartition("t", 0), 0L, 5L, 0L, Long.valueOf(0L), 0, 0);
    // invoke callback
    var m = cb.getClass().getDeclaredMethod("onCompletion", RecordMetadata.class, Exception.class);
    m.setAccessible(true);
    m.invoke(cb, md, null);
    WalWriterStats stats = writer.getLiveStats();
    assertThat(stats.messagesWritten(), greaterThan(0L));
  }

  @Test
  public void onCompletion_error_incrementsDropped() throws Exception {
    KafkaWalWriter writer = newWriter();
    Object cb = newSendCb(writer);
    var m = cb.getClass().getDeclaredMethod("onCompletion", RecordMetadata.class, Exception.class);
    m.setAccessible(true);
    m.invoke(cb, null, new RuntimeException("boom"));
    WalWriterStats stats = writer.getLiveStats();
    assertThat(stats.messagesDroppedError(), greaterThan(0L));
  }
}
