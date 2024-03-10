/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.core;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import com.google.common.primitives.Longs;
import java.lang.reflect.AccessibleObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Future;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.util.UUIDUtils;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.KafkaKeySerializer;
import net.ittera.pal.messages.colfer.KafkaSerializer;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Cluster;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class MessageOffsetInformerTest extends ZmqEnabledTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  private static final Set<String> createdLogs = new HashSet<>();
  private final MessageBuilder messageBuilder = new MessageBuilder();
  private static final UUID peerUuid = UUID.randomUUID();
  private MockProducer<String, byte[]> producer;
  private ZMQ.Socket offsetPublisher;
  private ZContext zmqContext;
  private final String offsetPubAddress = "inproc://offsets";

  @Before
  public void setup() throws Exception {
    zmqContext = this.createContext();
    producer =
        new MockProducer<>(
            Cluster.empty(), true, null, new KafkaKeySerializer(), new KafkaSerializer());
    offsetPublisher = zmqContext.createSocket(SocketType.PUB);
    offsetPublisher.bind(offsetPubAddress);
  }

  @After
  public void cleanup() throws Exception {
    if (offsetPublisher != null) {
      offsetPublisher.close();
    }
    logger.trace("publisher closed");
    producer.close();
    logger.trace("producer closed");
    closeContext(zmqContext);
  }

  @Test
  public void publishOffsets() throws Exception {

    // subscribe to published offsets
    ZMQ.Socket offsetSubscriber = zmqContext.createSocket(SocketType.SUB);
    offsetSubscriber.connect(offsetPubAddress);
    offsetSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

    // register log
    LogInfo log = new LogInfo("test.log");
    createdLogs.add(log.getName());

    AccessibleObject from = this.getClass().getDeclaredMethod("publishOffsets", (Class<?>[]) null);
    ExecMessage replyMessage =
        messageBuilder.buildReturnValue(
            peerUuid, null, from, null, false, UUID.randomUUID().toString());

    // create and send ProducerRecord w/ MessageOffsetInformer callback
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(
            log.getName(), 0, peerUuid.toString(), ColferUtils.toBytes(replyMessage));
    MessageOffsetInformer offsetInformer =
        new MessageOffsetInformer(UUID.fromString(replyMessage.getMessageUuid()), offsetPublisher);
    assertNotNull(newRecord);
    Future<RecordMetadata> recordMetadataFuture = producer.send(newRecord, offsetInformer);

    // wait for offset to be published
    offsetInformer.get();

    // verify
    assertThat(producer.history().size(), is(1));
    assertThat(producer.history().get(0), is(newRecord));

    // get and verify published offsets
    // multi-part msg: 1) offset as byte[], 2) uuid as byte[]
    byte[] offsetBuff = offsetSubscriber.recv();
    byte[] uuidBuff = offsetSubscriber.recv();
    assertThat(Longs.fromByteArray(offsetBuff), is(recordMetadataFuture.get().offset()));
    assertThat(UUIDUtils.fromBytes(uuidBuff).toString(), is(replyMessage.getMessageUuid()));
    offsetSubscriber.close();
  }
}
