package net.ittera.pal.core;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.common.znodes.LogInfo;
import net.ittera.pal.core.messages.InboundLogMsg;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Intercepts;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptMessage;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.apache.curator.test.TestingServer;
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

/** CAVEAT: this test doesn't cover Headers as they're not supported by MockConsumer */
public class LogReaderTest extends ZmqEnabledTest {

  /*
  class for Workers (which REPly to Dealer) IRL: LogMessageInvoker's
  */
  class Worker implements Runnable {

    private ZMQ.Socket socket;
    private ZContext context;
    private String dealerAddress;
    private Set<String> rcvdMsgUuids = new TreeSet<>();

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
        InboundLogMsg logMsg = null;
        try {
          logMsg = InboundLogMsg.recvMsg(socket, true);
          Message wrapper = Message.parseFrom(logMsg.getBody());
          if (wrapper.hasExecMessage()) {
            ExecMessage msg = wrapper.getExecMessage();
            logger.debug("ExecMessage received = {}", msg);
            rcvdMsgUuids.add(msg.getMessageUuid());
          } else {
            InterceptMessage msg = wrapper.getInterceptMessage();
            logger.debug("InterceptMessage msg received = {}", msg);
            rcvdMsgUuids.add(msg.getMessageUuid());
          }
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            logger.warn("context terminated");
            break;
          } else if (errorCode == ZError.EINTR) {
            logger.warn("interrupted during recv()");
            break;
          } else {
            logger.error("unexpected error during recv()", ex);
            throw (ex);
          }
        } catch (Exception e) {
          logger.error("error parsing received message", e);
        }
      }

      this.socket.close();
    }

    Set<String> getReceivedMessages() {
      return rcvdMsgUuids;
    }
  }

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private ExecutorService execService;
  private ZContext zmqContext;
  private LogReader logReader;
  private UUID peerUuid = UUID.randomUUID();
  private PALDirectory palDirectory;
  private TestingServer testingServer;
  private ServiceManager manager;
  private MockConsumer<String, byte[]> consumer;
  private LogInfo log;
  private final int partition = 0;
  private static Set<String> createdLogs = new HashSet<>();
  private final String DEALER_ADDR = "inproc://inlog_tests";
  private final String OFFSET_PUB_ADDR = "inproc://offsets_tests";
  private static final int TEST_PORT = 2182;
  private static final String CONNECTION_STR = String.format("localhost:%d", TEST_PORT);
  private ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private Set<Service> services;

  private void deleteCreatedLogs() throws Exception {
    for (String log : createdLogs) {
      palDirectory.unregisterLog(log);
      logger.debug("Cleaned up left over log: {}", log);
    }
  }

  @After
  public void cleanup() throws Exception {
    execService.shutdown();
    execService.awaitTermination(2, TimeUnit.SECONDS);
    zmqContext.close();
    deleteCreatedLogs();
    palDirectory.close();
    testingServer.close();
  }

  @Before
  public void setup() throws Exception {
    execService = Executors.newSingleThreadExecutor();
    testingServer = new TestingServer(TEST_PORT, true);
    palDirectory = new PALDirectory(CONNECTION_STR);
    zmqContext = this.createContext();
    consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
    logReader =
        new LogReader(
            UUID.randomUUID(),
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "LogReaderTest-Service",
            DEALER_ADDR,
            OFFSET_PUB_ADDR,
            palDirectory,
            consumer,
            10);
    log = palDirectory.newLog("testapp");
    createdLogs.add(this.log.getName());
    TopicPartition topicPartition = new TopicPartition(log.getName(), 0);
    final List<TopicPartition> topicPartitionList = Collections.singletonList(topicPartition);
    consumer.assign(topicPartitionList);
    consumer.seek(topicPartition, 0);
    services = new HashSet<>(Arrays.asList(this.logReader));
    this.manager = new ServiceManager(services);
  }

  @Test
  public void dontAcceptRequests() throws Exception {
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));

    // DON'T START ACCEPTING REQUESTS
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, this.DEALER_ADDR);
    execService.submit(logMsgInvoker);

    // send 1 message
    MessageBuilder msgBuilder = new ProtobufMessageBuilder();
    String key = peerUuid.toString();
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>(
            this.log.getName(), partition, 0, key, msgBuilder.wrap(msg).toByteArray());
    this.consumer.addRecord(record);

    // assert received = 0
    assertThat(logMsgInvoker.getReceivedMessages().size(), is(0));

    // shut down
    manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
  }

  @Test
  public void startRunNoMessages() throws Exception {
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));
    logReader.acceptRequests(true);
    assertThat(logReader.isAcceptingRequests(), is(true));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, this.DEALER_ADDR);
    execService.submit(logMsgInvoker);

    // send no messages

    Thread.sleep(300);

    // assert received = 0
    assertThat(logMsgInvoker.getReceivedMessages().size(), is(0));

    // shut down
    manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));
  }

  @Test
  public void consumeExecMessage() throws Exception {
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));

    logReader.acceptRequests(true);
    assertThat(logReader.isAcceptingRequests(), is(true));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, this.DEALER_ADDR);
    execService.submit(logMsgInvoker);

    // send 1 message
    MessageBuilder msgBuilder = new ProtobufMessageBuilder();
    String key = peerUuid.toString();
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>(
            this.log.getName(), partition, 0, key, msgBuilder.wrap(msg).toByteArray());
    this.consumer.addRecord(record);

    Thread.sleep(300);
    // assert received
    logger.debug("received: {}", String.join(",", logMsgInvoker.getReceivedMessages()));
    assertThat(logMsgInvoker.getReceivedMessages().size(), is(1));
    assertThat(
        logMsgInvoker.getReceivedMessages().stream().anyMatch(u -> u.equals(msg.getMessageUuid())),
        is(true));

    // shut down
    manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));
  }

  @Test
  public void consumeInterceptMessage() throws Exception {
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));

    logReader.acceptRequests(true);
    assertThat(logReader.isAcceptingRequests(), is(true));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, this.DEALER_ADDR);
    execService.submit(logMsgInvoker);

    // send 1 message
    MessageBuilder msgBuilder = new ProtobufMessageBuilder();
    String key = peerUuid.toString();
    Intercepts.InterceptMessage msg =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            Intercepts.InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.EMPTY_LIST,
            this.getClass().getName(),
            "someCallbackMethod");
    ConsumerRecord<String, byte[]> record =
        new ConsumerRecord<>(
            this.log.getName(), partition, 0, key, msgBuilder.wrap(msg).toByteArray());
    this.consumer.addRecord(record);

    Thread.sleep(300);
    // assert received
    logger.debug("received: {}", String.join(",", logMsgInvoker.getReceivedMessages()));
    assertThat(logMsgInvoker.getReceivedMessages().size(), is(1));
    assertThat(
        logMsgInvoker.getReceivedMessages().stream().anyMatch(u -> u.equals(msg.getMessageUuid())),
        is(true));

    // shut down
    manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));
  }

  @Test
  public void consumeManyMessages() throws Exception {
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));

    // start services
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), zmqContext);
    assertThat(logReader.isRunning(), is(true));

    logReader.acceptRequests(true);
    assertThat(logReader.isAcceptingRequests(), is(true));

    // start worker(s)
    Worker logMsgInvoker = new Worker(this.zmqContext, this.DEALER_ADDR);
    execService.submit(logMsgInvoker);

    // send many messages
    MessageBuilder msgBuilder = new ProtobufMessageBuilder();
    String key = peerUuid.toString();
    Set<String> sentUuids = new TreeSet<>();

    int msgsToSend = 30;
    for (int i = 0; i < msgsToSend; i++) {
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      long offset = i;
      ConsumerRecord<String, byte[]> record =
          new ConsumerRecord<>(
              this.log.getName(), partition, offset, key, msgBuilder.wrap(msg).toByteArray());
      this.consumer.addRecord(record);
      sentUuids.add(msg.getMessageUuid());
    }

    Thread.sleep(300);
    // assert received
    logger.debug("received: {}", String.join(",", logMsgInvoker.getReceivedMessages()));
    assertThat(logMsgInvoker.getReceivedMessages(), is(sentUuids));

    // shut down
    manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
    assertThat(logReader.isRunning(), is(false));
    assertThat(logReader.isAcceptingRequests(), is(false));
  }
}
