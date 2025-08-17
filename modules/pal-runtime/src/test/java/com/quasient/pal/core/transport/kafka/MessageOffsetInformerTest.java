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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;

import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.core.internal.messages.PublishedOffsetMsg;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.kafka.KafkaKeySerializer;
import com.quasient.pal.serdes.kafka.KafkaMessageSerializer;
import java.lang.reflect.AccessibleObject;
import java.util.UUID;
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
  private static final UUID peerUuid = UUID.randomUUID();
  private final MessageBuilder messageBuilder = new MessageBuilder(peerUuid);
  private MockProducer<String, byte[]> producer;
  private ZMQ.Socket offsetPublisher;
  private ZContext zmqContext;
  private final String offsetPubAddress = "inproc://offsets";

  @Before
  public void setup() throws Exception {
    zmqContext = this.createContext();
    producer =
        new MockProducer<>(
            Cluster.empty(), true, null, new KafkaKeySerializer(), new KafkaMessageSerializer());
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

    AccessibleObject from = this.getClass().getDeclaredMethod("publishOffsets", (Class<?>[]) null);
    ExecMessage responseMessage =
        messageBuilder.buildReturnValue(null, from, null, false, UUID.randomUUID().toString());

    // create and send ProducerRecord w/ MessageOffsetInformer callback
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(
            log.getName(), 0, peerUuid.toString(), ColferUtils.toBytes(responseMessage));
    MessageOffsetInformer offsetInformer =
        new MessageOffsetInformer(responseMessage.getMessageId(), offsetPublisher);
    assertNotNull(newRecord);
    final RecordMetadata recordMetadata = producer.send(newRecord, offsetInformer).get();

    // wait for offset to be published
    offsetInformer.get();

    // verify
    assertThat(producer.history().size(), is(1));
    assertThat(producer.history().get(0), is(newRecord));

    // get and verify published offsets
    PublishedOffsetMsg publishedOffsetMsg = PublishedOffsetMsg.receive(offsetSubscriber);
    assertThat(publishedOffsetMsg, is(notNullValue()));
    assertThat(publishedOffsetMsg.getOffset(), is(recordMetadata.offset()));
    assertThat(publishedOffsetMsg.getMessageId(), is(responseMessage.getMessageId()));
    offsetSubscriber.close();
  }
}
