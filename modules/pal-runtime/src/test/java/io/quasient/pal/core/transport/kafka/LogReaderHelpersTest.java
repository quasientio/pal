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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.types.MessageFormatType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.AbstractQueue;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

public class LogReaderHelpersTest extends ZmqEnabledTest {

  private ZContext ctx;
  private KafkaSourceLogReader reader;
  private UUID peerId;

  @Before
  public void setUp() {
    ctx = createContext();
    peerId = UUID.randomUUID();
    @SuppressWarnings("unchecked")
    Consumer<String, byte[]> consumer = (Consumer<String, byte[]>) mock(Consumer.class);
    DirectoryConnectionProvider dcp = new DirectoryConnectionProvider(PalDirectory.NO_URL);
    reader =
        new KafkaSourceLogReader(
            peerId,
            ctx,
            SYNC_SOCKET_ADDRESS,
            new ThreadGroup("svc"),
            "LogReaderHelpers",
            "inproc://dealer.ignore",
            "inproc://offs.ignore",
            dcp,
            consumer,
            /* autoCommit */ true,
            /* pollDurationMs */ 10);
  }

  @After
  public void tearDown() throws Exception {
    closeContext(ctx);
  }

  @Test
  public void getMessageFormatFromHeader_binary() throws Exception {
    RecordHeaders headers = new RecordHeaders();
    headers.add("message-format", new byte[] {MessageFormatType.BINARY.toByte()});
    Method m =
        KafkaSourceLogReader.class.getDeclaredMethod("getMessageFormatFromHeader", Headers.class);
    m.setAccessible(true);
    MessageFormatType fmt = (MessageFormatType) m.invoke(reader, headers);
    assertThat(fmt, is(MessageFormatType.BINARY));
  }

  @Test
  public void getMessageFormatFromHeader_missing_isNull() throws Exception {
    RecordHeaders headers = new RecordHeaders();
    Method m =
        KafkaSourceLogReader.class.getDeclaredMethod("getMessageFormatFromHeader", Headers.class);
    m.setAccessible(true);
    Object fmt = m.invoke(reader, headers);
    assertThat(fmt == null, is(true));
  }

  @Test
  public void recordProducedBySelf_trueAndFalse() throws Exception {
    RecordHeaders headers = new RecordHeaders();
    headers.add("producer-id", UuidUtils.toBytes(peerId));
    Method m = KafkaSourceLogReader.class.getDeclaredMethod("recordProducedBySelf", Headers.class);
    m.setAccessible(true);
    boolean isSelf = (boolean) m.invoke(reader, headers);
    assertThat(isSelf, is(true));

    RecordHeaders other = new RecordHeaders();
    other.add("producer-id", UuidUtils.toBytes(UUID.randomUUID()));
    boolean notSelf = (boolean) m.invoke(reader, other);
    assertThat(notSelf, is(false));
  }

  @Test
  public void recordProducedBySelf_multipleHeaders_trueWhenAnyMatches() throws Exception {
    RecordHeaders headers = new RecordHeaders();
    headers.add("producer-id", UuidUtils.toBytes(UUID.randomUUID()));
    headers.add("producer-id", UuidUtils.toBytes(peerId));
    Method m = KafkaSourceLogReader.class.getDeclaredMethod("recordProducedBySelf", Headers.class);
    m.setAccessible(true);
    boolean isSelf = (boolean) m.invoke(reader, headers);
    assertThat(isSelf, is(true));
  }

  @Test
  public void nextOffset_skipsQueuedOffsets() throws Exception {
    // set lastOffsetRead = 4
    Field fLast = KafkaSourceLogReader.class.getDeclaredField("lastOffsetRead");
    fLast.setAccessible(true);
    fLast.setLong(reader, 4L);

    // fill skipOffsets with [2, 4, 5, 7]
    Field fQueue = KafkaSourceLogReader.class.getDeclaredField("skipOffsets");
    fQueue.setAccessible(true);
    @SuppressWarnings("unchecked")
    AbstractQueue<Long> q = (AbstractQueue<Long>) fQueue.get(reader);
    q.clear();
    q.add(2L);
    q.add(4L);
    q.add(5L);
    q.add(7L);

    Method m = KafkaSourceLogReader.class.getDeclaredMethod("nextOffset");
    m.setAccessible(true);
    Long next = (Long) m.invoke(reader);
    // 4 -> candidate 5; 5 is queued, so skip to 6
    assertThat(next, is(6L));
  }

  @Test
  public void closeConnections_closesConsumer() {
    // Construct a reader with autoCommit=true and injected mock consumer
    @SuppressWarnings("unchecked")
    Consumer<String, byte[]> consumer = (Consumer<String, byte[]>) mock(Consumer.class);
    DirectoryConnectionProvider dcp = new DirectoryConnectionProvider(PalDirectory.NO_URL);
    KafkaSourceLogReader r =
        new KafkaSourceLogReader(
            UUID.randomUUID(),
            ctx,
            SYNC_SOCKET_ADDRESS,
            new ThreadGroup("svc"),
            "LogReaderHelpersClose",
            "inproc://dealer.ignore",
            "inproc://offs.ignore",
            dcp,
            consumer,
            /* autoCommit */ true,
            /* pollDurationMs */ 10);
    r.closeConnections();
    verify(consumer, atLeastOnce()).close(any(Duration.class));
  }
}
