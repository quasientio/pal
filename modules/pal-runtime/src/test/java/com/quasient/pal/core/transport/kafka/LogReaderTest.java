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

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.core.internal.messages.InboundLogMsg;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.MessageFormatType;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

// CAVEAT: this test doesn't cover Headers as they're not supported by MockConsumer
public class LogReaderTest extends ZmqEnabledTest {

  /*
  class for Workers (which REPly to Dealer) IRL: LogMessageInvoker
  */
  static class Worker implements Runnable {

    private final ZMQ.Socket socket;
    private final ZContext context;
    private final String dealerAddress;
    private final Set<String> receivedMessageIds = new TreeSet<>();

    Worker(ZContext context, String dealerAddress) {
      this.context = context;
      this.dealerAddress = dealerAddress;
      this.socket = this.context.createSocket(SocketType.REP);
    }

    @Override
    public void run() {
      // connect to dealer
      this.socket.connect(this.dealerAddress);

      // process requests
      while (!Thread.interrupted()) {
        InboundLogMsg logMsg;
        try {
          logMsg = InboundLogMsg.receive(socket, true);
          assert logMsg != null;
          Message wrapper = new Message();
          wrapper.unmarshal(logMsg.getBody(), 0);
          if (wrapper.getExecMessage() != null) {
            ExecMessage msg = wrapper.getExecMessage();
            logger.debug("ExecMessage received = {}", ColferUtils.format(msg));
            receivedMessageIds.add(msg.getMessageId());
          } else {
            InterceptMessage msg = wrapper.getInterceptMessage();
            logger.debug("InterceptMessage msg received = {}", ColferUtils.format(msg));
            receivedMessageIds.add(msg.getMessageId());
          }
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            logger.warn("context terminated");
            break;
          } else if (errorCode == ZError.EINTR) {
            logger.warn("interrupted during receive()");
            break;
          } else {
            logger.error("unexpected error during receive()", ex);
            throw ex;
          }
        } catch (Exception e) {
          logger.error("error parsing received message", e);
        }
      }

      this.socket.close();
      this.context.close();
    }

    Set<String> getReceivedMessages() {
      return receivedMessageIds;
    }
  }

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private ExecutorService execService;
  private ZContext zmqContext;
  private KafkaSourceLogReader logReader;
  private final UUID peerUuid = UUID.randomUUID();
  private ServiceManager manager;
  private MockConsumer<String, byte[]> consumer;
  private LogInfo log;
  private final int partition = 0;
  private static final String DEALER_ADDRESS = "inproc://source_log_tests";
  private static final String OFFSET_PUB_ADDRESS = "inproc://offsets_tests";
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private Set<Service> services;

  @After
  public void cleanup() throws Exception {
    closeContext(zmqContext);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    logger.trace("exec service shut down");
  }

  @Before
  public void setup() throws Exception {
    execService = Executors.newSingleThreadExecutor();
    DirectoryConnectionProvider directoryConnectionProvider =
        new DirectoryConnectionProvider(PalDirectory.NO_URL);
    zmqContext = this.createContext();
    consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    boolean autoCommit = true;
    logReader =
        new KafkaSourceLogReader(
            UUID.randomUUID(),
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "LogReaderTest-Service",
            DEALER_ADDRESS,
            OFFSET_PUB_ADDRESS,
            directoryConnectionProvider,
            consumer,
            autoCommit,
            10);
    this.log = new LogInfo("test_app");
    TopicPartition topicPartition = new TopicPartition(log.getName(), 0);
    final List<TopicPartition> topicPartitionList = Collections.singletonList(topicPartition);
    consumer.assign(topicPartitionList);
    consumer.seek(topicPartition, 0);
    services = new HashSet<>(Collections.singletonList(this.logReader));
    this.manager = new ServiceManager(services);
  }

  @Test
  public void doNotAcceptRequests() {
    logger.trace("entering doNotAcceptRequests()");
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));

    // DON'T START ACCEPTING REQUESTS
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, DEALER_ADDRESS);
    execService.execute(logMsgInvoker);

    // send 1 message
    MessageBuilder msgBuilder = new MessageBuilder();
    String key = peerUuid.toString();
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>(
            this.log.getName(), partition, 0, key, ColferUtils.toBytes(msgBuilder.wrap(msg)));
    this.consumer.addRecord(record);

    // assert received = 0
    assertThat(logMsgInvoker.getReceivedMessages().size(), is(0));

    // shut down
    manager.stopAsync().awaitStopped();

    logger.trace("leaving doNotAcceptRequests()");
  }

  @Test
  public void startRunNoMessages() throws Exception {
    logger.trace("entering startRunNoMessages()");
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));
    logReader.acceptRequests(true);
    assertThat(logReader.isAcceptingRequests(), is(true));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, DEALER_ADDRESS);
    execService.execute(logMsgInvoker);

    // send no messages

    Thread.sleep(300);

    // assert received = 0
    assertThat(logMsgInvoker.getReceivedMessages().size(), is(0));

    // shut down
    manager.stopAsync().awaitStopped();
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));
    logger.trace("leaving startRunNoMessages()");
  }

  @Test
  public void consumeExecMessage() throws Exception {
    logger.trace("entering consumeExecMessage()");
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));

    logReader.acceptRequests(true);
    assertThat(logReader.isAcceptingRequests(), is(true));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, DEALER_ADDRESS);
    execService.execute(logMsgInvoker);

    // send 1 message
    MessageBuilder msgBuilder = new MessageBuilder();
    String key = peerUuid.toString();
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>(
            this.log.getName(), partition, 0, key, ColferUtils.toBytes(msgBuilder.wrap(msg)));
    record.headers().add("message-format", new byte[] {MessageFormatType.BINARY.toByte()});
    this.consumer.addRecord(record);

    Thread.sleep(300);
    // assert received
    assertThat(logMsgInvoker.getReceivedMessages().size(), is(1));
    assertThat(
        logMsgInvoker.getReceivedMessages().stream().anyMatch(u -> u.equals(msg.getMessageId())),
        is(true));

    // shut down
    manager.stopAsync().awaitStopped();
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));
    logger.trace("leaving consumeExecMessage()");
  }

  @Test
  public void consumeInterceptMessage() throws Exception {
    logger.trace("entering consumeInterceptMessage()");
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));

    logReader.acceptRequests(true);
    assertThat(logReader.isAcceptingRequests(), is(true));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, DEALER_ADDRESS);
    execService.execute(logMsgInvoker);

    // send 1 message
    MessageBuilder msgBuilder = new MessageBuilder();
    String key = peerUuid.toString();
    @SuppressWarnings("unchecked")
    InterceptMessage msg =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            (List<String>) Collections.EMPTY_LIST,
            this.getClass().getName(),
            "someCallbackMethod");
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>(
            this.log.getName(), partition, 0, key, ColferUtils.toBytes(msgBuilder.wrap(msg)));
    record.headers().add("message-format", new byte[] {MessageFormatType.BINARY.toByte()});
    this.consumer.addRecord(record);

    Thread.sleep(300);
    // assert received
    assertThat(logMsgInvoker.getReceivedMessages().size(), is(1));
    assertThat(
        logMsgInvoker.getReceivedMessages().stream().anyMatch(u -> u.equals(msg.getMessageId())),
        is(true));

    // shut down
    manager.stopAsync().awaitStopped();
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));
    logger.trace("leaving consumeInterceptMessage()");
  }

  @Test
  public void consumeManyMessages() throws Exception {
    logger.trace("entering consumeManyMessages()");
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));

    logReader.acceptRequests(true);
    assertThat(logReader.isAcceptingRequests(), is(true));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, DEALER_ADDRESS);
    execService.execute(logMsgInvoker);

    // send many messages
    MessageBuilder msgBuilder = new MessageBuilder();
    String key = peerUuid.toString();
    Set<String> sentUuids = new TreeSet<>();

    int messagesToSend = 20;
    for (int i = 0; i < messagesToSend; i++) {
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>(
              this.log.getName(), partition, i, key, ColferUtils.toBytes(msgBuilder.wrap(msg)));
      record.headers().add("message-format", new byte[] {MessageFormatType.BINARY.toByte()});
      this.consumer.addRecord(record);
      sentUuids.add(msg.getMessageId());
    }

    Thread.sleep(1000);
    // assert received
    logger.debug("received: {}", String.join(",", logMsgInvoker.getReceivedMessages()));
    assertThat(logMsgInvoker.getReceivedMessages(), is(sentUuids));

    // shut down
    manager.stopAsync().awaitStopped();
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));
    logger.trace("leaving consumeManyMessages()");
  }
}
