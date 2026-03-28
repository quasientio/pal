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
