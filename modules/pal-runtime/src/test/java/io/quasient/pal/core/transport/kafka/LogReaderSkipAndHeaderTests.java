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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.core.internal.messages.InboundLogMsg;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageFormatType;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.Collections;
import java.util.HashSet;
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
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * Additional KafkaSourceLogReader tests for header-driven branches: missing message-format and
 * self-produced record suppression.
 */
public class LogReaderSkipAndHeaderTests extends ZmqEnabledTest {

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
      this.socket.connect(this.dealerAddress);
      while (!Thread.interrupted()) {
        try {
          InboundLogMsg logMsg = InboundLogMsg.receive(socket, true);
          assert logMsg != null;
          Message wrapper = new Message();
          wrapper.unmarshal(logMsg.getBody(), 0);
          ExecMessage msg = wrapper.getExecMessage();
          if (msg != null) {
            receivedMessageIds.add(msg.getMessageId());
          }
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM || errorCode == ZError.EINTR) {
            break;
          } else {
            throw ex;
          }
        } catch (Exception e) {
          // ignore parse errors
        }
      }
      this.socket.close();
      this.context.close();
    }

    Set<String> getReceivedMessages() {
      return receivedMessageIds;
    }
  }

  private ExecutorService execService;
  private ZContext zmqContext;
  private KafkaSourceLogReader logReader;
  private final UUID peerUuid = UUID.randomUUID();
  private ServiceManager manager;
  private MockConsumer<String, byte[]> consumer;
  private LogInfo log;
  private final int partition = 0;
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private static final String DEALER_ADDRESS = "inproc://source_log_hdr";
  private static final String OFFSET_PUB_ADDRESS = "inproc://offsets_hdr";
  private Set<Service> services;

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
            peerUuid,
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "LogReaderHeaderTests",
            DEALER_ADDRESS,
            OFFSET_PUB_ADDRESS,
            directoryConnectionProvider,
            consumer,
            autoCommit,
            10);
    this.log = new LogInfo("test_app_hdr");
    TopicPartition topicPartition = new TopicPartition(log.getName(), 0);
    consumer.assign(Collections.singletonList(topicPartition));
    consumer.seek(topicPartition, 0);
    services = new HashSet<>(Collections.singletonList(this.logReader));
    this.manager = new ServiceManager(services);
  }

  @After
  public void cleanup() throws Exception {
    closeContext(zmqContext);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
  }

  @Test
  public void missingMessageFormat_isSkipped() throws Exception {
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    logReader.acceptRequests(true);

    Worker worker = new Worker(this.zmqContext, DEALER_ADDRESS);
    execService.execute(worker);

    // record without message-format header → should be skipped
    MessageBuilder msgBuilder = new MessageBuilder();
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    String key = peerUuid.toString();
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>(
            this.log.getName(), partition, 0, key, ColferUtils.toBytes(msgBuilder.wrap(msg)));
    // No header added here
    this.consumer.addRecord(record);

    Thread.sleep(300);
    assertThat(worker.getReceivedMessages().size(), is(0));
    manager.stopAsync().awaitStopped();
  }

  @Test
  public void producedBySelf_isSkipped() throws Exception {
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    logReader.acceptRequests(true);

    Worker worker = new Worker(this.zmqContext, DEALER_ADDRESS);
    execService.execute(worker);

    // record with self producer-id header → should be skipped
    MessageBuilder msgBuilder = new MessageBuilder();
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    String key = peerUuid.toString();
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>(
            this.log.getName(), partition, 0, key, ColferUtils.toBytes(msgBuilder.wrap(msg)));
    RecordHeaders headers = (RecordHeaders) record.headers();
    headers.add("message-format", new byte[] {MessageFormatType.BINARY.toByte()});
    headers.add("producer-id", UuidUtils.toBytes(peerUuid));
    this.consumer.addRecord(record);

    Thread.sleep(300);
    assertThat(worker.getReceivedMessages().size(), is(0));
    manager.stopAsync().awaitStopped();
  }
}
