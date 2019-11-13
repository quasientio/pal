package com.ittera.cometa.core;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.primitives.Longs;
import com.ittera.cometa.common.util.UUIDUtils;
import com.ittera.cometa.common.znodes.LogInfo;
import com.ittera.cometa.common.znodes.LogRequest;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import java.lang.reflect.AccessibleObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.curator.test.TestingServer;
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

  private static final int TEST_PORT = 2182;
  private static final String CONNECTION_STR = String.format("localhost:%d", TEST_PORT);

  private static final Set<String> createdLogs = new HashSet<>();

  private final MessageBuilder messageBuilder = new ProtobufMessageBuilder();
  private static final UUID peerUuid = UUID.randomUUID();
  private PALDirectory palDirectory;
  private TestingServer testingServer;
  private MockProducer<String, byte[]> producer;
  private ZMQ.Socket offsetPublisher;
  private ZContext zmqContext;
  private final String offsetPubAddress = "inproc://offsets";

  @Before
  public void setup() throws Exception {
    testingServer = new TestingServer(TEST_PORT, true);
    palDirectory = new PALDirectory(CONNECTION_STR);
    zmqContext = this.createContext();
    producer = new MockProducer<>(Cluster.empty(), true, null, null, null);
    offsetPublisher = zmqContext.createSocket(SocketType.PUB);
    offsetPublisher.bind(offsetPubAddress);
  }

  @After
  public void cleanup() throws Exception {
    for (String log : createdLogs) {
      palDirectory.unregisterLog(log);
      logger.debug("Cleaned up created log: {}", log);
    }
    if (offsetPublisher != null) {
      offsetPublisher.close();
    }
    producer.close();
    palDirectory.close();
    testingServer.close();
    zmqContext.close();
  }

  /**
   * Tests MessageOffsetInformer's CuratorInterface -- i.e. process()
   *
   * @throws Exception
   */
  @Test
  public void writeReplyNodes_existingReqNode() throws Exception {
    boolean publishOffsets = false;
    boolean writeReplyNodes = true;
    offsetPublisher = null;

    // register log
    LogInfo log = new LogInfo("test.log");
    palDirectory.registerLog(log.getName());
    createdLogs.add(log.getName());

    // fake req UUID and LogRequest
    UUID requestUuid = UUID.randomUUID();
    LogRequest logRequest = new LogRequest(requestUuid);

    // create a reply message
    AccessibleObject from =
        this.getClass().getDeclaredMethod("writeReplyNodes_existingReqNode", null);
    ExecMessage replyMessage =
        messageBuilder.buildReturnValue(peerUuid, null, from, null, false, requestUuid.toString());

    // add log request to directory
    CountDownLatch latch = new CountDownLatch(1);
    palDirectory.addLogRequestAsync(
        log.getName(), logRequest, (curatorFramework, curatorEvent) -> latch.countDown());
    // wait to make sure request node exists
    if (!latch.await(2, TimeUnit.SECONDS)) {
      fail("Timeout awaiting latch downcount - node not created?");
    }

    // create and send ProducerRecord w/ MessageOffsetInformer callback
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(log.getName(), 0, peerUuid.toString(), replyMessage.toByteArray());
    MessageOffsetInformer offsetInformer =
        new MessageOffsetInformer(
            UUID.fromString(replyMessage.getMessageUuid()),
            requestUuid,
            publishOffsets,
            writeReplyNodes,
            offsetPublisher,
            palDirectory,
            log,
            peerUuid);
    producer.send(newRecord, offsetInformer);

    while (!offsetInformer.isDone()) {
      Thread.sleep(100);
    }
    // verify
    assertThat(producer.history().size(), is(1));
    assertThat(producer.history().get(0), is(newRecord));
    assertThat(offsetInformer.getLastError(), nullValue());
    assertThat(palDirectory.getRepliesTo(log.getName(), logRequest).size(), is(1));
  }

  @Test
  public void writeReplyNodes_reqNodeInitiallyMissing() throws Exception {
    boolean publishOffsets = false;
    boolean writeReplyNodes = true;
    offsetPublisher = null;

    // register log
    LogInfo log = new LogInfo("test.log");
    palDirectory.registerLog(log.getName());
    createdLogs.add(log.getName());

    // fake req UUID and LogRequest
    UUID requestUuid = UUID.randomUUID();
    LogRequest logRequest = new LogRequest(requestUuid);

    // create fake reply message
    AccessibleObject from =
        this.getClass().getDeclaredMethod("writeReplyNodes_reqNodeInitiallyMissing", null);
    ExecMessage replyMessage =
        messageBuilder.buildReturnValue(peerUuid, null, from, null, false, requestUuid.toString());

    // DON'T ADD log request to directory just yet

    // create and send ProducerRecord w/ MessageOffsetInformer callback
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(log.getName(), 0, peerUuid.toString(), replyMessage.toByteArray());
    MessageOffsetInformer offsetInformer =
        new MessageOffsetInformer(
            UUID.fromString(replyMessage.getMessageUuid()),
            requestUuid,
            publishOffsets,
            writeReplyNodes,
            offsetPublisher,
            palDirectory,
            log,
            peerUuid);
    producer.send(newRecord, offsetInformer);

    // NOW, wait a bit and then add log request to directory
    Thread.sleep(300);
    CountDownLatch latch = new CountDownLatch(1);
    palDirectory.addLogRequestAsync(
        log.getName(), logRequest, (curatorFramework, curatorEvent) -> latch.countDown());
    if (!latch.await(2, TimeUnit.SECONDS)) {
      fail("Timeout awaiting latch downcount - node not created?");
    }

    while (!offsetInformer.isDone()) {
      Thread.sleep(100);
    }
    // verify
    assertThat(producer.history().size(), is(1));
    assertThat(producer.history().get(0), is(newRecord));
    assertThat(offsetInformer.getLastError(), nullValue());
    assertThat(palDirectory.getRepliesTo(log.getName(), logRequest).size(), is(1));
  }

  @Test
  public void publishOffsetsAndwriteReplyNodes() throws Exception {
    boolean publishOffsets = true;
    boolean writeReplyNodes = true;

    // subscribe to published offsets
    ZMQ.Socket offsetSubscriber = zmqContext.createSocket(SocketType.SUB);
    offsetSubscriber.connect(offsetPubAddress);
    offsetSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);

    // register log
    LogInfo log = new LogInfo("test.log");
    palDirectory.registerLog(log.getName());
    createdLogs.add(log.getName());

    // fake req UUID and LogRequest
    UUID requestUuid = UUID.randomUUID();
    LogRequest logRequest = new LogRequest(requestUuid);

    // create a reply message
    AccessibleObject from =
        this.getClass().getDeclaredMethod("writeReplyNodes_existingReqNode", null);
    ExecMessage replyMessage =
        messageBuilder.buildReturnValue(peerUuid, null, from, null, false, requestUuid.toString());

    // add log request to directory
    CountDownLatch latch = new CountDownLatch(1);
    palDirectory.addLogRequestAsync(
        log.getName(), logRequest, (curatorFramework, curatorEvent) -> latch.countDown());
    // wait to make sure request node exists
    if (!latch.await(2, TimeUnit.SECONDS)) {
      fail("Timeout awaiting latch downcount - node not created?");
    }

    // create and send ProducerRecord w/ MessageOffsetInformer callback
    ProducerRecord<String, byte[]> newRecord =
        new ProducerRecord<>(log.getName(), 0, peerUuid.toString(), replyMessage.toByteArray());
    MessageOffsetInformer offsetInformer =
        new MessageOffsetInformer(
            UUID.fromString(replyMessage.getMessageUuid()),
            requestUuid,
            publishOffsets,
            writeReplyNodes,
            offsetPublisher,
            palDirectory,
            log,
            peerUuid);
    Future<RecordMetadata> recordMetadataFuture = producer.send(newRecord, offsetInformer);

    while (!offsetInformer.isDone()) {
      Thread.sleep(100);
    }
    // verify
    assertThat(producer.history().size(), is(1));
    assertThat(producer.history().get(0), is(newRecord));
    assertThat(offsetInformer.getLastError(), nullValue());

    // get and verify published offsets
    // multi-part msg: 1) offset as byte[], 2) uuid as byte[]
    byte[] offsetBuff = offsetSubscriber.recv();
    byte[] uuidBuff = offsetSubscriber.recv();
    assertThat(Longs.fromByteArray(offsetBuff), is(recordMetadataFuture.get().offset()));
    assertThat(UUIDUtils.fromBytes(uuidBuff).toString(), is(replyMessage.getMessageUuid()));
    offsetSubscriber.close();
  }
}
