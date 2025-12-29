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
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.Producer;
import org.junit.Test;
import org.zeromq.ZContext;

public class KafkaWalWriterPropsTest {

  @Test
  public void producerProperties_setFromNamedParameters() throws Exception {
    KafkaWalWriter writer =
        new KafkaWalWriter(
            UUID.randomUUID(),
            new ZContext(1),
            "inproc://sync",
            new ThreadGroup("svc"),
            "KafkaWalWriterProps",
            /* walQueue */ null,
            new AtomicBoolean(false),
            "inproc://offs",
            /* flushOnClose */ null,
            /* lingerMs */ "1",
            /* batchSize */ "32768",
            /* compressionType */ "gzip",
            /* bufferMemory */ "65536",
            (Properties p) -> (Producer<String, byte[]>) null);

    Field f = KafkaWalWriter.class.getDeclaredField("producerProperties");
    f.setAccessible(true);
    Properties props = (Properties) f.get(writer);
    // Values are stored as numeric types (Long/Integer), not Strings
    assertThat(props.get("linger.ms"), is(1L));
    assertThat(props.get("batch.size"), is(32768));
    assertThat(props.get("compression.type"), is("gzip"));
    assertThat(props.get("buffer.memory"), is(65536L));
  }
}
