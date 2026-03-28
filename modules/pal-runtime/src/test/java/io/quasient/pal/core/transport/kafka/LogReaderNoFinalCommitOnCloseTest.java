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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Complements LogReaderCloseConsumerTest by ensuring that when committed >= processed, close does
 * not attempt a final commitSync.
 */
public class LogReaderNoFinalCommitOnCloseTest {

  @Test
  public void noFinalCommitSyncOnClose_whenCommittedUpToProcessed() throws Exception {
    @SuppressWarnings("unchecked")
    Consumer<String, byte[]> consumer = mock(Consumer.class);
    DirectoryConnectionProvider dcp = new DirectoryConnectionProvider(PalDirectory.NO_URL);
    KafkaSourceLogReader r =
        new KafkaSourceLogReader(
            UUID.randomUUID(),
            new ZContext(1),
            "inproc://sync",
            new ThreadGroup("svc"),
            "LogReaderNoFinalCommit",
            "inproc://dealer",
            "inproc://offs",
            dcp,
            consumer,
            /* autoCommit */ false,
            /* pollDurationMs */ 10);

    // Simulate processed offsets <= committed so close will NOT attempt commitSync
    Field fProcessed = KafkaSourceLogReader.class.getDeclaredField("lastOffsetRead");
    fProcessed.setAccessible(true);
    fProcessed.setLong(r, 10L);
    Field fCommitted = KafkaSourceLogReader.class.getDeclaredField("lastCommittedOffset");
    fCommitted.setAccessible(true);
    ((AtomicLong) fCommitted.get(r)).set(10L);

    r.closeConnections();
    verify(consumer, never()).commitSync();
    verify(consumer).close(any(Duration.class));
  }
}
