/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.AbstractIntegrationTest;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.colfer.ControlMessage;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.types.ControlCommandType;
import com.quasient.pal.messages.types.ControlStatusType;
import com.quasient.pal.messages.types.RpcType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.kafka.typed.KafkaLogMessageSerializer;
import java.time.Duration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

public class ThinPeerIT extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  private final MessageBuilder msgBuilder = new MessageBuilder();

  // PAL directory
  private PalDirectory palDirectory;
  private DirectoryConnectionProvider directoryConnectionProvider;

  // mock Kafka producer & consumer
  private MockProducer<String, LogMessage<?>> producer;
  private MockConsumer<String, LogMessage<?>> consumer;

  private static final Set<LogInfo> createdLogs = new HashSet<>();
  private ThinPeer thinPeer;

  @Before
  public void setUp() {
    directoryConnectionProvider = new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);
    palDirectory = directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
    producer = new MockProducer<>(Cluster.empty(), true, null, null, null);
    consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
  }

  @After
  public void tearDown() throws Exception {
    // close peer
    if (thinPeer != null && !thinPeer.isClosed()) {
      try {
        thinPeer.close();
        thinPeer = null;
      } catch (IllegalStateException e) {
        // we may close it after testing an uninitialized thin peer, so it's fine
      }
    }
    // delete created logs (peers are deleting/unregistering themselves when closed)
    for (String log : createdLogs.stream().map(LogInfo::getName).toList()) {
      palDirectory.deleteLog(log);
      logger.info("Cleaned up created log: {}", log);
    }
    palDirectory.close();
  }

  private LogInfo createTestLog() throws Exception {
    LogInfo log = new LogInfo("test_log", getKafkaServers());
    palDirectory.createLog(log);
    createdLogs.add(log);
    return log;
  }

  @Test
  public void initOk() throws Exception {
    thinPeer =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumer(consumer)
            .withProducer(producer)
            .withLog(createTestLog())
            .init();
    assertThat(thinPeer.isInitialized(), is(true));
    assertThat(thinPeer.isConsuming(), is(true));
    assertThat(thinPeer.isProducing(), is(true));
    assertThat(thinPeer.isLogIOEnabled(), is(true));
  }

  @Test
  public void initAndClose() throws Exception {
    thinPeer =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumer(consumer)
            .withProducer(producer)
            .withLog(createTestLog())
            .init();
    assertThat(thinPeer.isInitialized(), is(true));
    assertThat(thinPeer.isLogIOEnabled(), is(true));
    thinPeer.close();
    assertThat(thinPeer.isClosed(), is(true));
  }

  @Test
  public void initWithMissingDirectoryOk() throws Exception {
    thinPeer =
        new ThinPeer()
            .withConsumer(consumer)
            .withProducer(producer)
            .withLog(createTestLog())
            .init();
    assertThat(thinPeer.isLogIOEnabled(), is(true));
  }

  @Test(expected = IllegalArgumentException.class)
  public void initWithSelfRegisterButMissingDirectory_IllegalArgumentException() throws Exception {
    thinPeer =
        new ThinPeer()
            .withConsumer(consumer)
            .withProducer(producer)
            .withLog(createTestLog())
            .withSelfRegistration(true)
            .init();
  }

  @Test
  public void initWithMissingConsumer() throws Exception {
    // no Consumer nor Consumer Properties
    thinPeer =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withProducer(producer)
            .withLog(createTestLog())
            .init();
    assertThat(thinPeer.isLogIOEnabled(), is(true));
    assertThat(thinPeer.isConsuming(), is(false));
    assertThat(thinPeer.isProducing(), is(true));

    // try to receive a message from the log
    try {
      thinPeer.getMessageAtOffset(33L);
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage().contains("ThinPeer log consumer not configured."), is(true));
    }
  }

  @Test
  public void initWithMissingProducer() throws Exception {
    // no Producer nor Producer Properties
    thinPeer =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumer(consumer)
            .withLog(createTestLog())
            .init();
    assertThat(thinPeer.isLogIOEnabled(), is(true));
    assertThat(thinPeer.isConsuming(), is(true));
    assertThat(thinPeer.isProducing(), is(false));

    // try to send a message to the log
    try {
      thinPeer.sendExecMessageToLog(
          msgBuilder.buildEmptyConstructor(thinPeer.getPeerUuid(), "java.lang.String"));
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage().contains("ThinPeer log producer not configured"), is(true));
    }
  }

  @Test
  public void initWithMiscFlags() throws Exception {
    ZContext zmqContext = createZmqContext();
    thinPeer =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumer(consumer)
            .withLog(createTestLog())
            .withBootstrapServers(getKafkaServers())
            .withLogPrefix("test-prefix")
            .withPollingDuration(1000)
            .withZmqContext(zmqContext)
            .init();
    assertEquals(getKafkaServers(), thinPeer.getBootstrapServers());
    assertEquals("test-prefix", thinPeer.getLogPrefix());
    assertEquals(1000, thinPeer.getPollingDuration().toMillis());
    assertEquals(zmqContext, thinPeer.getZmqContext());
  }

  @Test
  public void sendAndReceive_Uninitialized_IllegalState() throws Exception {
    thinPeer =
        new ThinPeer()
            .withUuid(UUID.randomUUID())
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumer(consumer)
            .withProducer(producer)
            .withLog(createTestLog());

    assertFalse(thinPeer.isInitialized());
    ExecMessage msg = msgBuilder.buildEmptyConstructor(thinPeer.getPeerUuid(), "java.lang.String");
    try {
      thinPeer.sendExecMessageToLogAndReceive(msg);
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      // ok
    }
  }

  @Test
  public void sendAndReceive_Closed_IllegalState() throws Exception {
    thinPeer =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumer(consumer)
            .withProducer(producer)
            .withLog(createTestLog())
            .init();

    assertTrue(thinPeer.isInitialized());

    assertFalse(thinPeer.isClosed());
    thinPeer.close();
    assertTrue(thinPeer.isClosed());
    ExecMessage msg = msgBuilder.buildEmptyConstructor(thinPeer.getPeerUuid(), "java.lang.String");
    try {
      thinPeer.sendToPeer(msg);
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      // ok
    }
  }

  @Test
  public void initFullyConnectedAndTestGetters() throws Exception {
    LogInfo inputAndOutputLog = createTestLog();
    PeerInfo initialPeer = findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElseThrow();
    Properties producerProperties = getKafkaProducerProperties();
    Properties consumerProperties = getKafkaConsumerProperties();
    UUID peerUuid = UUID.randomUUID();
    thinPeer =
        new ThinPeer()
            .withName("test_peer")
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumer(consumer)
            .withProducer(producer)
            .withLog(inputAndOutputLog)
            .withUuid(peerUuid)
            .withProducerProperties(producerProperties)
            .withConsumerProperties(consumerProperties)
            .withZmqRpcAddress("tcp://localhost:1234")
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .withInitialPeer(initialPeer)
            .withSelfRegistration(false)
            .init();

    assertThat(thinPeer.getName(), is("test_peer"));
    assertThat(thinPeer.getPalDirectoryUrl(), is(getPalDirectoryUrl()));
    assertThat(thinPeer.getConsumer(), is(consumer));
    assertThat(thinPeer.getProducer(), is(producer));
    assertThat(thinPeer.getInputLog(), is(inputAndOutputLog));
    assertThat(thinPeer.getOutputLog(), is(inputAndOutputLog));
    assertThat(thinPeer.getPeerUuid(), is(peerUuid));
    assertThat(thinPeer.getProducerProperties(), is(producerProperties));
    assertThat(thinPeer.getConsumerProperties(), is(consumerProperties));
    assertThat(thinPeer.isLogIOEnabled(), is(true));
    assertThat(thinPeer.isConsuming(), is(true));
    assertThat(thinPeer.isProducing(), is(true));
    assertThat(thinPeer.getZmqRpcAddress(), is("tcp://localhost:1234"));
    assertThat(thinPeer.getOutboundRpcType(), is(RpcType.ZMQ_RPC));
    assertThat(thinPeer.getInitialPeer(), is(initialPeer));
    assertThat(thinPeer.isSelfRegistering(), is(false));
    assertThat(thinPeer.isTalkingToPeer(), is(true));
    assertThat(thinPeer.getCurrentPeer(), is(initialPeer));
    assertThat(thinPeer.isZmqSocketConnected(), is(true));
  }

  @Test
  public void initAndPing() throws Exception {
    PeerInfo initialPeer = findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElseThrow();
    thinPeer =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withInitialPeer(initialPeer)
            .withOutboundRpcType(RpcType.JSON_RPC)
            .withSelfRegistration(false)
            .init();

    double took = thinPeer.sendPing();
    logger.debug("Ping reply took {} ms", (long) took);
  }

  @Test
  public void initAndConnectWithTimeoutToJsonRpcPeer() throws Exception {
    thinPeer =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.JSON_RPC)
            .withSelfRegistration(false)
            .init();

    PeerInfo peer = findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElseThrow();
    boolean connected = thinPeer.connectToPeer(peer, Duration.ofMinutes(2));
    assertTrue(connected);
  }

  @Test
  public void initAndConnectWithTimeoutToZmqPeer() throws Exception {
    thinPeer =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .withSelfRegistration(false)
            .init();

    PeerInfo peer = findRpcPeer(RpcType.ZMQ_RPC, directoryConnectionProvider).orElseThrow();
    boolean connected = thinPeer.connectToPeer(peer, Duration.ofMinutes(2));
    assertTrue(connected);
  }

  @Test
  public void testSendAndReceiveControlMessage() throws Exception {
    try (ThinPeer tp =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .withSelfRegistration(false)
            .init()) {

      PeerInfo peerInfo = findRpcPeer(RpcType.ZMQ_RPC, directoryConnectionProvider).orElseThrow();
      tp.connectToPeer(peerInfo);

      // Test
      ControlMessage pingMsg =
          msgBuilder.buildControlCommandMessage(tp.getPeerUuid(), ControlCommandType.PING);
      ControlMessage response = tp.sendToPeer(pingMsg);

      // Verify
      assertNotNull("Response should not be null", response);
      assertEquals(
          "Response status should be OK", ControlStatusType.OK.toId(), response.getStatus());
    }
  }

  @Test
  public void testSendExecMessageToLogAndReceive() throws Exception {
    Properties consumerProperties = getKafkaConsumerProperties();
    Properties producerProperties = getKafkaProducerProperties();

    try (ThinPeer tp =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withConsumerProperties(consumerProperties)
            .withProducerProperties(producerProperties)
            .withLogPrefix("itt")
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .init()) {

      // Test - use a simple method call without parameters
      ExecMessage execMsg =
          msgBuilder.buildClassMethod(
              tp.getPeerUuid(),
              "com.quasient.pal.apps.rpc.Methods",
              "testVoidMethod",
              new String[] {},
              this,
              null,
              new Object[] {},
              null);

      LogMessage<Message> response = tp.sendExecMessageToLogAndReceive(execMsg);
      // Verify
      assertNotNull("Response should not be null", response);
      assertNotNull("Response content should not be null", response.getContent());
      ExecMessage responseExecMsg = response.getContent().getExecMessage();
      assertNotNull("Response exec message should not be null", responseExecMsg);
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testSendMessageWhenNotConnected() throws Exception {
    // Setup
    try (ThinPeer tp =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .init()) {

      // This should throw IllegalStateException
      tp.sendToPeer(msgBuilder.buildEmptyConstructor(tp.getPeerUuid(), "java.lang.String"));
    }
  }

  @Test
  public void testConnectionTimeout() throws Exception {
    // Setup with non-existent peer
    try (ThinPeer tp =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .init()) {

      PeerInfo nonExistentPeer = new PeerInfo(UUID.randomUUID());
      nonExistentPeer.setZmqRpcAddress("tcp://localhost:5555"); // Unlikely to be in use

      // Test with short timeout
      long startTime = System.currentTimeMillis();
      boolean connected = tp.connectToPeer(nonExistentPeer, Duration.ofSeconds(2));
      long duration = System.currentTimeMillis() - startTime;

      // Verify
      assertFalse("Should not connect to non-existent peer", connected);
      assertTrue("Should respect timeout", duration >= 1900 && duration < 3000);
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testCloseWhenNotInitialized() {
    // Setup - create peer but don't initialize
    ThinPeer tp = new ThinPeer();
    // Should throw IllegalStateException when closing uninitialized peer
    tp.close();
  }

  @Test
  public void testSimplePeerCreation() {
    // Very simple test - just create a peer and check its state
    ThinPeer tp = new ThinPeer();
    assertNotNull("Peer should be created", tp);
    assertFalse("Peer should not be initialized", tp.isInitialized());
    assertFalse("Peer should not be closed initially", tp.isClosed());
  }

  @Test
  public void testCloseWithResources() throws Exception {
    // Setup
    LogInfo testLog = createTestLog();
    try (MockConsumer<String, LogMessage<?>> testConsumer =
            new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, LogMessage<?>> testProducer =
            new MockProducer<>(true, new StringSerializer(), new KafkaLogMessageSerializer());
        ThinPeer tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withConsumer(testConsumer)
                .withProducer(testProducer)
                .withLog(testLog)
                .withOutboundRpcType(RpcType.ZMQ_RPC)
                .withZmqRpcAddress("tcp://localhost:0")
                .withSelfRegistration(true)
                .init()) {

      // Verify peer is initialized and active
      assertTrue("Peer should be initialized", tp.isInitialized());
      assertFalse("Peer should not be closed yet", tp.isClosed());

      // Test explicit close
      tp.close();
      assertTrue("Peer should be closed after explicit close", tp.isClosed());
    }
  }

  @Test
  public void testPeerConfiguration() throws Exception {
    // Test various configuration options
    UUID testUuid = UUID.randomUUID();
    String testName = "TestPeer";
    String testZmqAddress = "tcp://localhost:0";

    try (ThinPeer tp =
        new ThinPeer()
            .withUuid(testUuid)
            .withName(testName)
            .withZmqRpcAddress(testZmqAddress)
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .withSelfRegistration(false)
            .init()) {

      // Verify configuration
      assertEquals("UUID should match", testUuid, tp.getPeerUuid());
      assertEquals("Name should match", testName, tp.getName());
      assertEquals("ZMQ address should match", testZmqAddress, tp.getZmqRpcAddress());
      assertEquals("RPC type should match", RpcType.ZMQ_RPC, tp.getOutboundRpcType());
      assertFalse("Self registration should be disabled", tp.isSelfRegistering());
    }
  }

  @Test
  public void testKafkaLogConfiguration() throws Exception {
    // Setup
    LogInfo log = createTestLog();
    String logPrefix = "test-prefix";

    try (MockConsumer<String, LogMessage<?>> testConsumer =
            new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, LogMessage<?>> testProducer =
            new MockProducer<>(true, new StringSerializer(), new KafkaLogMessageSerializer());
        ThinPeer tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withConsumer(testConsumer)
                .withProducer(testProducer)
                .withLog(log)
                .withLogPrefix(logPrefix)
                .withPollingDuration(100)
                .init()) {

      // Verify Kafka configuration
      assertEquals("Input log should match", log, tp.getInputLog());
      assertEquals("Output log should match", log, tp.getOutputLog());
      assertEquals("Log prefix should match", logPrefix, tp.getLogPrefix());
      assertEquals(
          "Polling duration should match", Duration.ofMillis(100), tp.getPollingDuration());
      assertTrue("Log IO should be enabled", tp.isLogIOEnabled());
      assertTrue("Producing should be enabled", tp.isProducing());
      assertTrue("Consuming should be enabled", tp.isConsuming());
    }
  }

  @Test
  public void testWebSocketRpcType() throws Exception {
    try (ThinPeer tp =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.JSON_RPC)
            .withSelfRegistration(false)
            .init()) {

      assertEquals("RPC type should be JSON_RPC", RpcType.JSON_RPC, tp.getOutboundRpcType());

      // Try to connect to a JSON-RPC peer
      PeerInfo jsonRpcPeer =
          findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElse(null);
      if (jsonRpcPeer != null) {
        boolean connected = tp.connectToPeer(jsonRpcPeer, Duration.ofSeconds(5));
        // Connection may or may not succeed depending on test environment
        logger.info("JSON-RPC connection result: {}", connected);
      }
    }
  }

  @Test
  public void testSendPingToConnectedPeer() throws Exception {
    try (ThinPeer tp =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .withSelfRegistration(false)
            .init()) {

      PeerInfo zmqPeer = findRpcPeer(RpcType.ZMQ_RPC, directoryConnectionProvider).orElseThrow();
      tp.connectToPeer(zmqPeer);

      // Test ping with timeout
      double pingTime = tp.sendPing(Duration.ofSeconds(5));
      assertTrue("Ping should return valid time", pingTime >= 0.0);

      // Test ping without timeout
      double pingTime2 = tp.sendPing();
      assertTrue("Ping should return valid time", pingTime2 >= 0.0);
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testOperationOnClosedPeer() throws Exception {
    ThinPeer tp =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .init();

    tp.close();

    // This should throw IllegalStateException
    tp.sendPing();
  }

  @Test
  public void testMultipleInitializationCalls() throws Exception {
    try (ThinPeer tp =
        new ThinPeer()
            .withDirectoryProvider(directoryConnectionProvider)
            .withOutboundRpcType(RpcType.ZMQ_RPC)
            .withSelfRegistration(false)) {

      // First initialization
      tp.init();
      assertTrue("Peer should be initialized", tp.isInitialized());

      // Second initialization should not cause issues
      tp.init();
      assertTrue("Peer should still be initialized", tp.isInitialized());
    }
  }

  @Test
  public void testGettersAfterInitialization() throws Exception {
    LogInfo testLog = createTestLog();

    try (MockConsumer<String, LogMessage<?>> testConsumer =
            new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, LogMessage<?>> testProducer =
            new MockProducer<>(true, new StringSerializer(), new KafkaLogMessageSerializer());
        ThinPeer tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withConsumer(testConsumer)
                .withProducer(testProducer)
                .withLog(testLog)
                .withOutboundRpcType(RpcType.ZMQ_RPC)
                .init()) {

      // Test all getters
      assertNotNull("Peer UUID should not be null", tp.getPeerUuid());
      // Note: getPalDirectory() is private, so we can't test it directly
      // assertNotNull("PAL directory should not be null", peer.getPalDirectory());
      assertNotNull("ZMQ context should not be null", tp.getZmqContext());
      assertNotNull("Producer should not be null", tp.getProducer());
      assertNotNull("Consumer should not be null", tp.getConsumer());
      assertEquals("Input log should match", testLog, tp.getInputLog());
      assertEquals("Output log should match", testLog, tp.getOutputLog());
      assertTrue("Peer should be initialized", tp.isInitialized());
      assertFalse("Peer should not be closed", tp.isClosed());
    }
  }

  @Test
  public void testZmqContextConfiguration() throws Exception {
    try (ZContext customContext = new ZContext();
        ThinPeer tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withZmqContext(customContext)
                .withOutboundRpcType(RpcType.ZMQ_RPC)
                .withSelfRegistration(false)
                .init()) {

      assertEquals("ZMQ context should match provided context", customContext, tp.getZmqContext());
    }
  }
}
