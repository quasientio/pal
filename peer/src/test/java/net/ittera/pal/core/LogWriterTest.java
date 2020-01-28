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

import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.ittera.pal.common.ExecPhase;
import net.ittera.pal.common.znodes.LogInfo;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.MessageType;
import net.ittera.pal.messages.OutboundMsg;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Headers.InternalHeader;
import net.ittera.pal.messages.protobuf.Intercepts;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptMessage;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.apache.curator.test.TestingServer;
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
  private UUID peerUuid = UUID.randomUUID();
  private PALDirectory palDirectory;
  private TestingServer testingServer;
  private ServiceManager manager;
  private MockProducer<String, byte[]> producer;
  private LogInfo log;
  private ZMQ.Socket pubSocket;
  private final String OUT_PUB_ADDR = "inproc://pub";
  private final String OFFSET_PUB_ADDR = "inproc://offsets";
  private static final Set<String> createdLogs = new HashSet<>();
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
  private ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");

  private static final int TEST_PORT = 2182;
  private static final String CONNECTION_STR = String.format("localhost:%d", TEST_PORT);

  private void deleteCreatedLogs() throws Exception {
    for (String log : createdLogs) {
      palDirectory.unregisterLog(log);
      logger.debug("Cleaned up left over log: {}", log);
    }
  }

  @After
  public void cleanup() throws Exception {
    closeContext(zmqContext);
    manager.stopAsync().awaitStopped();
    logger.trace("services stopped");
    logger.trace("exec service shut down");
    deleteCreatedLogs();
    palDirectory.close();
    logger.trace("PAL dir closed");
    testingServer.close();
    logger.trace("testing zk server closed");
  }

  @Before
  public void setup() throws Exception {
    testingServer = new TestingServer(TEST_PORT, true);
    palDirectory = new PALDirectory(CONNECTION_STR);
    zmqContext = this.createContext();
    producer = new MockProducer<>(Cluster.empty(), true, null, null, null);
    logWriter =
        new LogWriter(
            UUID.randomUUID(),
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "LogWriterTest-Service",
            OUT_PUB_ADDR,
            OFFSET_PUB_ADDR,
            true,
            producer,
            palDirectory);
    // configure log
    log = this.palDirectory.newLog("testapp");
    createdLogs.add(log.getName());
    logWriter.writeToLog(log, log, false);
    // start services
    final Set<Service> services = new HashSet<>(Arrays.asList(this.logWriter));
    manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
  }

  private String getMessageUuid(Message msg) throws IllegalArgumentException {
    if (msg.hasExecMessage()) {
      return msg.getExecMessage().getMessageUuid();
    } else if (msg.hasInterceptMessage()) {
      return msg.getInterceptMessage().getMessageUuid();
    } else {
      throw new IllegalArgumentException(format("Unsupported message type: %s", msg));
    }
  }

  private UUID getFollowingUuid(Message msg) {
    if (msg.hasExecMessage() && msg.getExecMessage().hasFollowingUuid()) {
      return UUID.fromString(msg.getExecMessage().getFollowingUuid());
    }
    return null;
  }

  @Test
  public void noPublishedMsgs() throws Exception {
    assertThat(logWriter.isRunning(), is(true));

    // we PUBlish no messages

    // assert NO published message is produced to the log
    assertThat(producer.history().isEmpty(), is(true));
  }

  @Test
  public void publishedMixedMessages() throws Exception {
    // we create outPub socket and PUBlish some messages
    pubSocket = zmqContext.createSocket(SocketType.PUB);
    pubSocket.bind(OUT_PUB_ADDR);

    List<Message> msgsCreated = new ArrayList<>();
    // create ExecMessage's
    int execMessagesToSend = 15;
    for (int i = 0; i < execMessagesToSend; i++) {
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      msgsCreated.add(msgBuilder.wrap(msg));
    }
    // create InterceptMessages
    int interceptMessagesToSend = 5;
    for (int i = 0; i < interceptMessagesToSend; i++) {
      InterceptMessage msg =
          msgBuilder.buildInterceptMessage(
              peerUuid,
              Intercepts.InterceptType.BEFORE,
              "java.io.PrintStream",
              "println",
              Collections.EMPTY_LIST,
              this.getClass().getName(),
              "someCallbackMethod");
      msgsCreated.add(msgBuilder.wrap(msg));
    }

    // PUB them
    msgsCreated.forEach(
        msg -> {
          MessageType msgType =
              msg.hasExecMessage() ? MessageType.ExecMessage : MessageType.InterceptMessage;
          ExecPhase execPhase = msg.hasExecMessage() ? ExecPhase.BEFORE : ExecPhase.UNDEFINED;
          OutboundMsg outMsg =
              new OutboundMsg(
                  msgType,
                  execPhase,
                  null,
                  UUID.fromString(getMessageUuid(msg)),
                  getFollowingUuid(msg),
                  msg.toByteArray());
          outMsg.send(pubSocket);
        });

    // give it some time
    Thread.sleep(300);

    // assert published messages are produced to the log
    List<String> producedMsgUuids = new ArrayList<>();
    for (ProducerRecord<String, byte[]> record : producer.history()) {
      Message msg = Message.parseFrom(record.value());
      producedMsgUuids.add(getMessageUuid(msg));
    }
    List<String> sentMsgUuids =
        msgsCreated.stream().map(this::getMessageUuid).collect(Collectors.toList());
    assertThat(producer.history().size(), is(execMessagesToSend + interceptMessagesToSend));
    assertThat(producedMsgUuids, is(sentMsgUuids));
  }

  @Test
  public void publishedMessagesWithHeader() throws Exception {
    assertThat(logWriter.isRunning(), is(true));

    // we create outPub socket and PUBlish some messages with header
    pubSocket = zmqContext.createSocket(SocketType.PUB);
    pubSocket.bind(OUT_PUB_ADDR);

    int messagesToSend = 5;
    List<Message> msgsCreated = new ArrayList<>();
    for (int i = 0; i < messagesToSend; i++) {
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      msgsCreated.add(msgBuilder.wrap(msg));
    }

    // PUB them
    List<InternalHeader> headers = Arrays.asList(msgBuilder.buildWriteAheadHeader(peerUuid));
    msgsCreated.stream()
        .forEach(
            msg -> {
              OutboundMsg outMsg =
                  new OutboundMsg(
                      MessageType.ExecMessage,
                      ExecPhase.BEFORE,
                      headers,
                      UUID.fromString(getMessageUuid(msg)),
                      getFollowingUuid(msg),
                      msg.toByteArray());
              outMsg.send(pubSocket);
            });

    // give it some time
    Thread.sleep(300);

    // assert published messages are produced to the log
    List<String> producedMsgUuids = new ArrayList<>();
    for (ProducerRecord<String, byte[]> record : producer.history()) {
      producedMsgUuids.add(getMessageUuid(Message.parseFrom(record.value())));
    }
    List<String> sentMsgUuids =
        msgsCreated.stream().map(this::getMessageUuid).collect(Collectors.toList());
    assertThat(producer.history().size(), is(messagesToSend));
    assertThat(producedMsgUuids, is(sentMsgUuids));
  }
}
