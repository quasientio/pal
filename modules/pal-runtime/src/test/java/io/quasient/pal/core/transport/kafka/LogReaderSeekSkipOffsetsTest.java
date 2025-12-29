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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.AbstractQueue;
import java.util.HashSet;
import java.util.Set;
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
 * Verifies that when skipWrittenOffsets is enabled and nextOffset jumps ahead, KafkaSourceLogReader
 * seeks the consumer to the computed next offset.
 */
public class LogReaderSeekSkipOffsetsTest extends ZmqEnabledTest {

  private ZContext ctx;
  private KafkaSourceLogReader reader;
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
        new KafkaSourceLogReader(
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
    Set<Service> services = new HashSet<>();
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
    Field fTp = KafkaSourceLogReader.class.getDeclaredField("topicPartition");
    fTp.setAccessible(true);
    fTp.set(reader, tp);

    // Enable skip and set lastOffsetRead=4
    Field fSkip = KafkaSourceLogReader.class.getSuperclass().getDeclaredField("skipWrittenOffsets");
    fSkip.setAccessible(true);
    fSkip.setBoolean(reader, true);

    Field fLast = KafkaSourceLogReader.class.getDeclaredField("lastOffsetRead");
    fLast.setAccessible(true);
    fLast.setLong(reader, 4L);

    // Populate skipOffsets queue so nextOffset() returns 10 (simulate big jump)
    Field fQueue = KafkaSourceLogReader.class.getDeclaredField("skipOffsets");
    fQueue.setAccessible(true);
    @SuppressWarnings("unchecked")
    AbstractQueue<Long> q = (AbstractQueue<Long>) fQueue.get(reader);
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
