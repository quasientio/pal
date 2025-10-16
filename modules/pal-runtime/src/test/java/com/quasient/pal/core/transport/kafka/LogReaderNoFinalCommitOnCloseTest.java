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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
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
