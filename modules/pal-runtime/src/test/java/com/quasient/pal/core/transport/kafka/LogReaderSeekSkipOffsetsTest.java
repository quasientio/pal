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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ServiceManager;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Verifies that when skipWrittenOffsets is enabled and nextOffset jumps ahead, LogReader seeks the
 * consumer to the computed next offset.
 */
public class LogReaderSeekSkipOffsetsTest extends ZmqEnabledTest {

  private ZContext ctx;
  private LogReader reader;
  private ServiceManager manager;
  private Consumer<String, byte[]> mockConsumer;
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services");

  @Before
  public void setUp() throws Exception {
    ctx = createContext();
    @SuppressWarnings("unchecked")
    Consumer<String, byte[]> consumer = mock(Consumer.class);
    mockConsumer = consumer;
    // poll returns empty to trigger the post-loop seek check
    when(consumer.poll(any(Duration.class))).thenReturn(ConsumerRecords.empty());

    DirectoryConnectionProvider dcp = new DirectoryConnectionProvider(PalDirectory.NO_URL);
    reader =
        new LogReader(
            UUID.randomUUID(),
            ctx,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "LogReaderSeekSkip",
            "inproc://dealer.seek",
            "inproc://offs.seek",
            dcp,
            consumer,
            /* autoCommit */ true,
            /* pollDurationMs */ 10);

    // prepare ServiceManager
    java.util.Set<com.google.common.util.concurrent.Service> services = new HashSet<>();
    services.add(reader);
    manager = new ServiceManager(services);
  }

  @After
  public void tearDown() throws Exception {
    closeContext(ctx);
  }

  @Test
  public void seekCalledWhenNextOffsetSkipsAhead() throws Exception {
    // Start service and wait until openConnections finished
    manager.startAsync().awaitHealthy();
    collectGoSignals(1, ctx);

    // Configure internal fields via reflection before accepting requests
    TopicPartition tp = new TopicPartition(new LogInfo("seek_app").getName(), 0);
    Field fTp = LogReader.class.getDeclaredField("topicPartition");
    fTp.setAccessible(true);
    fTp.set(reader, tp);

    // Enable skip and set lastOffsetRead=4
    Field fSkip = LogReader.class.getDeclaredField("skipWrittenOffsets");
    fSkip.setAccessible(true);
    fSkip.setBoolean(reader, true);

    Field fLast = LogReader.class.getDeclaredField("lastOffsetRead");
    fLast.setAccessible(true);
    fLast.setLong(reader, 4L);

    // Populate skipOffsets queue so nextOffset() returns 10 (simulate big jump)
    Field fQueue = LogReader.class.getDeclaredField("skipOffsets");
    fQueue.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.AbstractQueue<Long> q = (java.util.AbstractQueue<Long>) fQueue.get(reader);
    q.clear();
    // nextToRead starts at 5, so add 5..9 to cause jump to 10
    for (long off = 5; off < 10; off++) {
      q.add(off);
    }

    // Allow run loop to proceed once
    reader.acceptRequests(true);

    // Give a short time slice to process the empty poll and perform seek
    Thread.sleep(150);

    // Stop and verify
    manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);

    verify(mockConsumer, times(1)).seek(tp, 10L);
  }
}
