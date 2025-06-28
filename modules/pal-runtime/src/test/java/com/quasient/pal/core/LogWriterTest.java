/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.messages.OutboundMsg;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.colfer.InternalHeader;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.kafka.KafkaKeySerializer;
import com.quasient.pal.serdes.kafka.KafkaMessageSerializer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Cluster;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class LogWriterTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private ZContext zmqContext;
  private LogWriter logWriter;
  private final UUID peerUuid = UUID.randomUUID();
  private ServiceManager manager;
  private MockProducer<String, byte[]> producer;
  private ZMQ.Socket pubSocket;
  private static final String OUT_PUB_ADDRESS = "inproc://pub";
  private static final String OFFSET_PUB_ADDRESS = "inproc://offsets";
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");

  @After
  public void cleanup() throws Exception {
    closeContext(zmqContext);
    manager.stopAsync().awaitStopped();
    logger.trace("services stopped");
    logger.trace("exec service shut down");
  }

  @Before
  public void setup() throws Exception {
    zmqContext = this.createContext();
    producer =
        new MockProducer<>(
            Cluster.empty(), true, null, new KafkaKeySerializer(), new KafkaMessageSerializer());
    logWriter =
        new LogWriter(
            UUID.randomUUID(),
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "LogWriterTest-Service",
            OUT_PUB_ADDRESS,
            OFFSET_PUB_ADDRESS,
            producer);
    // configure log
    LogInfo log = new LogInfo("test_app", "localhost:9092");
    logWriter.writeToLog(log, false);
    // start services
    final Set<Service> services = new HashSet<>(Collections.singletonList(this.logWriter));
    manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
  }

  private String getMessageId(Message msg) throws IllegalArgumentException {
    ExecMessage execMessage = msg.getExecMessage();
    if (execMessage != null) {
      return execMessage.getMessageId();
    }
    InterceptMessage interceptMessage = msg.getInterceptMessage();
    if (interceptMessage != null) {
      return interceptMessage.getMessageId();
    }
    throw new IllegalArgumentException(format("Unsupported message type: %s", msg));
  }

  private String getResponseToId(Message msg) {
    ExecMessage execMessage = msg.getExecMessage();
    if (execMessage != null) {
      String responseToId = execMessage.getResponseToId();
      if (responseToId != null && !responseToId.isEmpty()) {
        return responseToId;
      }
    }
    return null;
  }

  @Test
  public void noPublishedMessages() {
    assertThat(logWriter.isRunning(), is(true));

    // we don't publish any messages

    // assert NO published message is produced to the log
    assertThat(producer.history().isEmpty(), is(true));
  }

  @Test
  public void publishedMixedMessages() throws Exception {
    // we create outPub socket and publish some messages
    pubSocket = zmqContext.createSocket(SocketType.PUB);
    pubSocket.bind(OUT_PUB_ADDRESS);

    List<Message> messagesCreated = new ArrayList<>();
    // create ExecMessage's
    int execMessagesToSend = 15;
    for (int i = 0; i < execMessagesToSend; i++) {
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      messagesCreated.add(msgBuilder.wrap(msg));
    }
    // create InterceptMessages
    int interceptMessagesToSend = 5;
    for (int i = 0; i < interceptMessagesToSend; i++) {
      InterceptMessage msg =
          msgBuilder.buildInterceptMessage(
              peerUuid,
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "println",
              Collections.emptyList(),
              this.getClass().getName(),
              "someCallbackMethod");
      messagesCreated.add(msgBuilder.wrap(msg));
    }

    // PUB them
    messagesCreated.forEach(
        msg -> {
          boolean hasExecMessage = msg.getExecMessage() != null;
          MessageType msgType =
              hasExecMessage ? MessageType.EXEC_CONSTRUCTOR : MessageType.INTERCEPT_MESSAGE;
          ExecPhase execPhase = hasExecMessage ? ExecPhase.BEFORE : ExecPhase.UNDEFINED;
          OutboundMsg outMsg =
              new OutboundMsg(
                  msgType, execPhase, null, getMessageId(msg), getResponseToId(msg), msg);
          outMsg.send(pubSocket);
        });

    // give it some time
    Thread.sleep(300);

    // assert published messages are produced to the log
    List<String> producedMsgUuids = new ArrayList<>();
    for (ProducerRecord<String, byte[]> record : producer.history()) {
      Message msg = new Message();
      msg.unmarshal(record.value(), 0);
      producedMsgUuids.add(getMessageId(msg));
    }
    List<String> sentMsgUuids =
        messagesCreated.stream().map(this::getMessageId).collect(Collectors.toList());
    assertThat(producer.history().size(), is(execMessagesToSend + interceptMessagesToSend));
    assertThat(producedMsgUuids, is(sentMsgUuids));
  }

  @Test
  public void publishedMessagesWithHeader() throws Exception {
    assertThat(logWriter.isRunning(), is(true));

    // we create outPub socket and publish some messages with header
    pubSocket = zmqContext.createSocket(SocketType.PUB);
    pubSocket.bind(OUT_PUB_ADDRESS);

    int messagesToSend = 5;
    List<Message> messagesCreated = new ArrayList<>();
    for (int i = 0; i < messagesToSend; i++) {
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      messagesCreated.add(msgBuilder.wrap(msg));
    }

    // PUB them
    List<InternalHeader> headers =
        Collections.singletonList(msgBuilder.buildWriteAheadHeader(peerUuid));
    messagesCreated.forEach(
        msg -> {
          OutboundMsg outMsg =
              new OutboundMsg(
                  MessageType.EXEC_CONSTRUCTOR,
                  ExecPhase.BEFORE,
                  headers,
                  getMessageId(msg),
                  getResponseToId(msg),
                  msg);
          outMsg.send(pubSocket);
        });

    // give it some time
    Thread.sleep(300);

    // assert published messages are produced to the log
    List<String> producedMsgUuids = new ArrayList<>();
    for (ProducerRecord<String, byte[]> record : producer.history()) {
      Message msg = new Message();
      msg.unmarshal(record.value(), 0);
      producedMsgUuids.add(getMessageId(msg));
    }
    List<String> sentMsgUuids =
        messagesCreated.stream().map(this::getMessageId).collect(Collectors.toList());
    assertThat(producer.history().size(), is(messagesToSend));
    assertThat(producedMsgUuids, is(sentMsgUuids));
  }
}
