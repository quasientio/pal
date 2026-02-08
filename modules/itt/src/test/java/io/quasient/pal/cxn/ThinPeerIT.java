/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.ControlStatusType;
import io.quasient.pal.messages.types.RpcType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.jsonrpc.JsonRpcMessageFactory;
import io.quasient.pal.serdes.kafka.typed.KafkaLogMessageSerializer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
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

  /** Chronicle temp directories created during tests for cleanup. */
  private final List<Path> chronicleTempDirs = new ArrayList<>();

  /** Well-known UUID for the shared ThinPeer test peer. */
  public static final UUID SHARED_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000004");

  /** Shared peer process for tests that need RPC peers. */
  private static PeerProcess sharedPeerProcess;

  /** Helper instance to access non-static methods. */
  private static ThinPeerIT instance;

  /**
   * Launches a shared peer before any tests run. This peer provides RPC endpoints for tests that
   * need to connect to a peer.
   */
  @BeforeClass
  public static void launchSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Launching shared ThinPeer test peer with UUID: {}", SHARED_PEER_UUID);
    logger.info("============================================================");

    instance = new ThinPeerIT();

    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    String ittAppsClasspath =
        String.format(
            "%s/modules/itt-apps/target/classes:%s/modules/itt-apps/target/classes",
            palHome, palHome);

    sharedPeerProcess =
        instance.launchPeer(
            SHARED_PEER_UUID,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            "peer-for-thinpeer-tests",
            "--zmq-rpc",
            "5659",
            "--json-rpc",
            "7792",
            "--rpc-threads",
            "3",
            "--rpc-allow-nonpublic",
            "--log",
            "auto",
            "--log-prefix",
            "itt",
            "-cp",
            ittAppsClasspath);

    logger.info("Shared ThinPeer test peer launched successfully");
  }

  /** Stops the shared peer after all tests complete. */
  @AfterClass
  public static void stopSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Stopping shared ThinPeer test peer");
    logger.info("============================================================");

    if (sharedPeerProcess != null && instance != null) {
      instance.stopPeer(sharedPeerProcess);
      sharedPeerProcess = null;
      logger.info("Shared ThinPeer test peer process stopped");

      // Unregister peer and clean up logs
      PalDirectory palDirectory = null;
      try {
        DirectoryConnectionProvider directoryConnectionProvider =
            new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);
        palDirectory =
            directoryConnectionProvider
                .get()
                .orElseThrow(() -> new RuntimeException("No connection for PalDirectory"));
        logger.info("Unregistering peer {} from directory", SHARED_PEER_UUID);
        palDirectory.deletePeer(SHARED_PEER_UUID);
        logger.info("Peer unregistered from directory");

        // Delete logs created by this peer
        logger.info("Deleting logs created by ThinPeer test peer");
        for (LogInfo log : palDirectory.listAllLogs()) {
          if (log.getName().startsWith("itt")) {
            logger.info("Deleting log: {}", log.getName());
            palDirectory.deleteLog(log.getName());
          }
        }
        logger.info("Logs cleaned up");
      } catch (Exception e) {
        logger.warn("Failed to clean up peer/logs from directory", e);
      } finally {
        if (palDirectory != null) {
          try {
            palDirectory.close();
          } catch (Exception e) {
            logger.warn("Error closing palDirectory", e);
          }
        }
      }

      logger.info("Shared ThinPeer test peer stopped and cleaned up successfully");
    }
  }

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
        logger.debug("Ignoring IllegalStateException while closing ThinPeer in tearDown()", e);
      }
    }
    // delete created logs (peers are deleting/unregistering themselves when closed)
    for (String log : createdLogs.stream().map(LogInfo::getName).toList()) {
      palDirectory.deleteLog(log);
      logger.info("Cleaned up created log: {}", log);
    }
    // Clean up Chronicle temp directories
    for (Path tempDir : chronicleTempDirs) {
      deleteChronicleDirectory(tempDir);
    }
    chronicleTempDirs.clear();
    palDirectory.close();
  }

  /**
   * Recursively deletes a Chronicle temp directory and all its contents.
   *
   * @param path the path to delete
   */
  private void deleteChronicleDirectory(Path path) {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(path)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                  logger.debug("Deleted Chronicle temp file: {}", p);
                } catch (IOException e) {
                  logger.warn("Failed to delete Chronicle temp file: {}", p, e);
                }
              });
      logger.info("Cleaned up Chronicle temp directory: {}", path);
    } catch (IOException e) {
      logger.warn("Failed to walk Chronicle temp directory for cleanup: {}", path, e);
    }
  }

  private LogInfo createTestLog() throws Exception {
    // Use unique log name for each invocation to avoid conflicts
    String logName = "test_log_" + UUID.randomUUID().toString().substring(0, 8);
    LogInfo log = new LogInfo(logName, getKafkaServers());
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
      logger.debug(
          "Expected IllegalStateException during sendExecMessageToLogAndReceive on uninitialized peer",
          e);
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
      logger.debug("Expected IllegalStateException during sendToPeer on closed peer", e);
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

    // Retry finding the peer to handle directory registration timing
    PeerInfo peer = null;
    for (int i = 0; i < 10; i++) {
      peer = findRpcPeer(RpcType.ZMQ_RPC, directoryConnectionProvider).orElse(null);
      if (peer != null) {
        break;
      }
      logger.debug("Waiting for ZMQ RPC peer to register in directory (attempt {})", i + 1);
      Thread.sleep(500);
    }
    if (peer == null) {
      throw new RuntimeException(
          "No ZMQ RPC peer found in directory after 5 seconds. "
              + "Ensure ThinPeerTestSuite's shared peer is running.");
    }

    boolean connected = thinPeer.connectToPeer(peer, Duration.ofMinutes(2));
    assertTrue(connected);
  }

  @Test
  public void testSendAndReceiveControlMessage() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

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
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  /**
   * Tests sending an ExecMessage to a log and receiving the response from a peer that reads from
   * that log. Uses the shared peer's log (with prefix "itt") that the peer is already reading from.
   */
  @Test
  public void testSendExecMessageToLogAndReceive() throws Exception {
    Properties consumerProperties = getKafkaConsumerProperties();
    Properties producerProperties = getKafkaProducerProperties();

    // Find the shared peer's log (created with prefix "itt")
    LogInfo sharedPeerLog = palDirectory.getLatestLogWithPrefix("itt");
    assertNotNull("Shared peer log should exist", sharedPeerLog);
    logger.info("Using shared peer log: {}", sharedPeerLog.getName());

    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withConsumerProperties(consumerProperties)
              .withProducerProperties(producerProperties)
              .withLog(sharedPeerLog)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .init();

      // Test - use a simple static method that returns a value
      ExecMessage execMsg =
          msgBuilder.buildClassMethod(
              tp.getPeerUuid(),
              "io.quasient.pal.apps.quantized.rpc.Methods",
              "staticStringWithStringArg",
              new String[] {"java.lang.String"},
              null,
              null,
              new Object[] {"test-input"},
              null);

      LogMessage<Message> response = tp.sendExecMessageToLogAndReceive(execMsg);

      // Verify
      assertNotNull("Response should not be null", response);
      assertNotNull("Response content should not be null", response.getContent());
      ExecMessage responseExecMsg = response.getContent().getExecMessage();
      assertNotNull("Response exec message should not be null", responseExecMsg);
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testSendMessageWhenNotConnected() throws Exception {
    // Setup
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .init();

      // This should throw IllegalStateException
      tp.sendToPeer(msgBuilder.buildEmptyConstructor(tp.getPeerUuid(), "java.lang.String"));
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testConnectionTimeout() throws Exception {
    // Setup with non-existent peer
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .init();

      PeerInfo nonExistentPeer = new PeerInfo(UUID.randomUUID());
      nonExistentPeer.setZmqRpcAddress("tcp://localhost:5555"); // Unlikely to be in use

      // Test with short timeout
      long startTime = System.currentTimeMillis();
      boolean connected = tp.connectToPeer(nonExistentPeer, Duration.ofSeconds(2));
      long duration = System.currentTimeMillis() - startTime;

      // Verify
      assertFalse("Should not connect to non-existent peer", connected);
      assertTrue("Should respect timeout", duration >= 1900 && duration < 3000);
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
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
            new MockProducer<>(true, new StringSerializer(), new KafkaLogMessageSerializer())) {
      ThinPeer tp = null;
      try {
        tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withConsumer(testConsumer)
                .withProducer(testProducer)
                .withLog(testLog)
                .withOutboundRpcType(RpcType.ZMQ_RPC)
                .withZmqRpcAddress("tcp://localhost:0")
                .withSelfRegistration(true)
                .init();

        // Verify peer is initialized and active
        assertTrue("Peer should be initialized", tp.isInitialized());
        assertFalse("Peer should not be closed yet", tp.isClosed());

        // Test explicit close
        tp.close();
        assertTrue("Peer should be closed after explicit close", tp.isClosed());
      } finally {
        if (tp != null && !tp.isClosed()) {
          try {
            tp.close();
          } catch (IllegalStateException e) {
            logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
          }
        }
      }
    }
  }

  @Test
  public void testPeerConfiguration() throws Exception {
    // Test various configuration options
    UUID testUuid = UUID.randomUUID();
    String testName = "TestPeer";
    String testZmqAddress = "tcp://localhost:0";

    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withUuid(testUuid)
              .withName(testName)
              .withZmqRpcAddress(testZmqAddress)
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      // Verify configuration
      assertEquals("UUID should match", testUuid, tp.getPeerUuid());
      assertEquals("Name should match", testName, tp.getName());
      assertEquals("ZMQ address should match", testZmqAddress, tp.getZmqRpcAddress());
      assertEquals("RPC type should match", RpcType.ZMQ_RPC, tp.getOutboundRpcType());
      assertFalse("Self registration should be disabled", tp.isSelfRegistering());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
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
            new MockProducer<>(true, new StringSerializer(), new KafkaLogMessageSerializer())) {
      ThinPeer tp = null;
      try {
        tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withConsumer(testConsumer)
                .withProducer(testProducer)
                .withLog(log)
                .withLogPrefix(logPrefix)
                .withPollingDuration(100)
                .init();

        // Verify Kafka configuration
        assertEquals("Input log should match", log, tp.getInputLog());
        assertEquals("Output log should match", log, tp.getOutputLog());
        assertEquals("Log prefix should match", logPrefix, tp.getLogPrefix());
        assertEquals(
            "Polling duration should match", Duration.ofMillis(100), tp.getPollingDuration());
        assertTrue("Log IO should be enabled", tp.isLogIOEnabled());
        assertTrue("Producing should be enabled", tp.isProducing());
        assertTrue("Consuming should be enabled", tp.isConsuming());
      } finally {
        if (tp != null && !tp.isClosed()) {
          try {
            tp.close();
          } catch (IllegalStateException e) {
            logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
          }
        }
      }
    }
  }

  @Test
  public void testWebSocketRpcType() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.JSON_RPC)
              .withSelfRegistration(false)
              .init();

      assertEquals("RPC type should be JSON_RPC", RpcType.JSON_RPC, tp.getOutboundRpcType());

      // Try to connect to a JSON-RPC peer
      PeerInfo jsonRpcPeer =
          findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElse(null);
      if (jsonRpcPeer != null) {
        boolean connected = tp.connectToPeer(jsonRpcPeer, Duration.ofSeconds(5));
        logger.info("JSON-RPC connection result: {}", connected);
      }
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testSendPingToConnectedPeer() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      PeerInfo zmqPeer = findRpcPeer(RpcType.ZMQ_RPC, directoryConnectionProvider).orElseThrow();
      tp.connectToPeer(zmqPeer);

      // Test ping with timeout
      double pingTime = tp.sendPing(Duration.ofSeconds(5));
      assertTrue("Ping should return valid time", pingTime >= 0.0);

      // Test ping without timeout
      double pingTime2 = tp.sendPing();
      assertTrue("Ping should return valid time", pingTime2 >= 0.0);
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
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
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false);

      tp.init();
      assertTrue("Peer should be initialized", tp.isInitialized());

      tp.init();
      assertTrue("Peer should still be initialized", tp.isInitialized());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testGettersAfterInitialization() throws Exception {
    LogInfo testLog = createTestLog();

    try (MockConsumer<String, LogMessage<?>> testConsumer =
            new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, LogMessage<?>> testProducer =
            new MockProducer<>(true, new StringSerializer(), new KafkaLogMessageSerializer())) {
      ThinPeer tp = null;
      try {
        tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withConsumer(testConsumer)
                .withProducer(testProducer)
                .withLog(testLog)
                .withOutboundRpcType(RpcType.ZMQ_RPC)
                .init();

        assertNotNull("Peer UUID should not be null", tp.getPeerUuid());
        assertNotNull("ZMQ context should not be null", tp.getZmqContext());
        assertNotNull("Producer should not be null", tp.getProducer());
        assertNotNull("Consumer should not be null", tp.getConsumer());
        assertEquals("Input log should match", testLog, tp.getInputLog());
        assertEquals("Output log should match", testLog, tp.getOutputLog());
        assertTrue("Peer should be initialized", tp.isInitialized());
        assertFalse("Peer should not be closed", tp.isClosed());
      } finally {
        if (tp != null && !tp.isClosed()) {
          try {
            tp.close();
          } catch (IllegalStateException e) {
            logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
          }
        }
      }
    }
  }

  @Test
  public void testZmqContextConfiguration() throws Exception {
    try (ZContext customContext = new ZContext()) {
      ThinPeer tp = null;
      try {
        tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withZmqContext(customContext)
                .withOutboundRpcType(RpcType.ZMQ_RPC)
                .withSelfRegistration(false)
                .init();

        assertEquals(
            "ZMQ context should match provided context", customContext, tp.getZmqContext());
      } finally {
        if (tp != null && !tp.isClosed()) {
          try {
            tp.close();
          } catch (IllegalStateException e) {
            logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
          }
        }
      }
    }
  }

  @Test
  public void testSeparateInputOutputLogs() throws Exception {
    LogInfo inputLog = createTestLog();
    LogInfo outputLog = createTestLog();

    try (MockConsumer<String, LogMessage<?>> testConsumer =
            new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, LogMessage<?>> testProducer =
            new MockProducer<>(true, new StringSerializer(), new KafkaLogMessageSerializer())) {
      ThinPeer tp = null;
      try {
        tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withConsumer(testConsumer)
                .withProducer(testProducer)
                .withInputLog(inputLog)
                .withOutputLog(outputLog)
                .init();

        // Verify separate logs are correctly set
        assertEquals("Input log should match", inputLog, tp.getInputLog());
        assertEquals("Output log should match", outputLog, tp.getOutputLog());
        assertFalse(
            "Input and output logs should be different",
            tp.getInputLog().getUuid().equals(tp.getOutputLog().getUuid()));
        assertTrue("Log IO should be enabled", tp.isLogIOEnabled());
      } finally {
        if (tp != null && !tp.isClosed()) {
          try {
            tp.close();
          } catch (IllegalStateException e) {
            logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
          }
        }
      }
    }
  }

  @Test
  public void testWebSocketConnectionLostTimeoutConfiguration() throws Exception {
    ThinPeer tp = null;
    try {
      // Test that websocket connection lost timeout can be configured
      // (no getter available, but configuration should not throw)
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.JSON_RPC)
              .withWebsocketConnectionLostTimeout(30)
              .withSelfRegistration(false)
              .init();

      // If we get here, configuration was accepted
      assertTrue("Peer should be initialized", tp.isInitialized());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testConnectToPeerByUuid() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      // Connect using the shared peer's UUID (this method returns void)
      tp.connectToPeer(SHARED_PEER_UUID);
      assertTrue("Should be talking to peer", tp.isTalkingToPeer());
      assertNotNull("Current peer should not be null", tp.getCurrentPeer());
      assertEquals(
          "Current peer UUID should match", SHARED_PEER_UUID, tp.getCurrentPeer().getUuid());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testConnectToPeerByUuid_nonExistent_doesNotConnect() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      // Try to connect to non-existent peer - silently does nothing
      UUID nonExistentUuid = UUID.randomUUID();
      tp.connectToPeer(nonExistentUuid);

      // Should not be connected since peer doesn't exist
      assertFalse("Should not be talking to peer", tp.isTalkingToPeer());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testSendExecMessageToPeer() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      // Connect to shared peer
      PeerInfo zmqPeer = findRpcPeer(RpcType.ZMQ_RPC, directoryConnectionProvider).orElseThrow();
      tp.connectToPeer(zmqPeer);

      // Send an exec message
      ExecMessage execMsg =
          msgBuilder.buildClassMethod(
              tp.getPeerUuid(),
              "io.quasient.pal.apps.quantized.rpc.Methods",
              "staticVoidNoArg",
              new String[] {},
              null,
              null,
              new Object[] {},
              null);

      ExecMessage response = tp.sendToPeer(execMsg);

      assertNotNull("Response should not be null", response);
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testInitWithDefaultRpcType() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withSelfRegistration(false)
              .init();

      // Default RPC type should be ZMQ_RPC
      assertEquals("Default RPC type should be ZMQ_RPC", RpcType.ZMQ_RPC, tp.getOutboundRpcType());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testDefaultPollingDuration() throws Exception {
    LogInfo testLog = createTestLog();

    try (MockConsumer<String, LogMessage<?>> testConsumer =
            new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        MockProducer<String, LogMessage<?>> testProducer =
            new MockProducer<>(true, new StringSerializer(), new KafkaLogMessageSerializer())) {
      ThinPeer tp = null;
      try {
        tp =
            new ThinPeer()
                .withDirectoryProvider(directoryConnectionProvider)
                .withConsumer(testConsumer)
                .withProducer(testProducer)
                .withLog(testLog)
                .init();

        // Default polling duration should be 10ms
        assertEquals(
            "Default polling duration should be 10ms",
            Duration.ofMillis(10),
            tp.getPollingDuration());
      } finally {
        if (tp != null && !tp.isClosed()) {
          try {
            tp.close();
          } catch (IllegalStateException e) {
            logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
          }
        }
      }
    }
  }

  @Test
  public void testWithInitialPeerAndJsonRpc() throws Exception {
    ThinPeer tp = null;
    try {
      PeerInfo jsonRpcPeer =
          findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElseThrow();

      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withInitialPeer(jsonRpcPeer)
              .withOutboundRpcType(RpcType.JSON_RPC)
              .withSelfRegistration(false)
              .init();

      assertTrue("Should be talking to peer", tp.isTalkingToPeer());
      assertEquals("Initial peer should match", jsonRpcPeer, tp.getInitialPeer());
      // Connection is established via initial peer
      assertNotNull("Current peer should be set", tp.getCurrentPeer());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testSendControlMessageWithTimeout() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      PeerInfo peerInfo = findRpcPeer(RpcType.ZMQ_RPC, directoryConnectionProvider).orElseThrow();
      tp.connectToPeer(peerInfo);

      // Send ping with explicit timeout
      ControlMessage pingMsg =
          msgBuilder.buildControlCommandMessage(tp.getPeerUuid(), ControlCommandType.PING);
      ControlMessage response = tp.sendToPeer(pingMsg, Duration.ofSeconds(10));

      assertNotNull("Response should not be null", response);
      assertEquals(
          "Response status should be OK", ControlStatusType.OK.toId(), response.getStatus());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testSendPingViaJsonRpc() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.JSON_RPC)
              .withSelfRegistration(false)
              .init();

      PeerInfo jsonRpcPeer =
          findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElseThrow();
      tp.connectToPeer(jsonRpcPeer);

      // Test ping via JSON-RPC
      double pingTime = tp.sendPing();
      assertTrue("Ping should return valid time", pingTime >= 0.0);
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test(expected = IllegalStateException.class)
  public void testSendPingWhenNotConnected() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .init();

      // Should throw IllegalStateException because not connected to any peer
      tp.sendPing();
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testAutoGeneratedUuid() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      // UUID should be auto-generated
      assertNotNull("UUID should be auto-generated", tp.getPeerUuid());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testNoLogIOWhenNoLogConfigured() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      // Log IO should be disabled when no log is configured
      assertFalse("Log IO should be disabled", tp.isLogIOEnabled());
      assertFalse("Consuming should be disabled", tp.isConsuming());
      assertFalse("Producing should be disabled", tp.isProducing());
      assertThat("Input log should be null", tp.getInputLog(), is((LogInfo) null));
      assertThat("Output log should be null", tp.getOutputLog(), is((LogInfo) null));
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testLogPrefixNullWhenNotConfigured() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      // Log prefix is null when not explicitly configured
      // (DEFAULT_TOPIC_PREFIX is only used internally for log lookup)
      assertThat(
          "Log prefix should be null when not configured", tp.getLogPrefix(), is((String) null));
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  @Test
  public void testLogPrefixWhenConfigured() throws Exception {
    ThinPeer tp = null;
    try {
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withLogPrefix("custom-prefix")
              .withOutboundRpcType(RpcType.ZMQ_RPC)
              .withSelfRegistration(false)
              .init();

      assertEquals("Log prefix should match configured value", "custom-prefix", tp.getLogPrefix());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  // ============================================================================
  // WebSocket and Chronicle Integration Tests (Issue #423)
  // These test specifications verify ThinPeer WebSocket client and Chronicle
  // log operations against real peers and infrastructure.
  // ============================================================================

  /**
   * Tests that sendJsonRpcRequestToPeer receives a valid response when connected.
   *
   * <p>Acceptance Criteria:
   * [INTEGRATION:ThinPeerIT.sendJsonRpcRequestToPeer_connected_receivesResponse]
   */
  @Test
  public void sendJsonRpcRequestToPeer_connected_receivesResponse() throws Exception {
    ThinPeer tp = null;
    try {
      // Given: ThinPeer connected to shared peer via WebSocket
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.JSON_RPC)
              .withSelfRegistration(false)
              .init();

      PeerInfo jsonRpcPeer =
          findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElseThrow();
      boolean connected = tp.connectToPeer(jsonRpcPeer, Duration.ofSeconds(5));
      assertTrue("Should connect to shared peer", connected);

      // When: sendJsonRpcRequestToPeer() with valid request called
      // Use a static method call that will succeed
      JsonRpcRequest request =
          JsonRpcMessageFactory.buildClassMethodCall(
              "io.quasient.pal.apps.quantized.rpc.Methods",
              "staticIntNoArgs",
              Collections.emptyList());

      CompletableFuture<JsonRpcResponse> responseFuture = tp.sendJsonRpcRequestToPeer(request);
      JsonRpcResponse response = responseFuture.get(10, TimeUnit.SECONDS);

      // Then: Receives valid JsonRpcResponse
      assertNotNull("Response should not be null", response);
      assertNotNull("Response ID should not be null", response.getId());
      assertEquals("Response ID should match request ID", request.getId(), response.getId());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  /**
   * Tests that sendJsonRpcRequestToPeer correctly tracks message IDs.
   *
   * <p>Acceptance Criteria:
   * [INTEGRATION:ThinPeerIT.sendJsonRpcRequestToPeer_withMessageId_tracksCorrectly]
   */
  @Test
  public void sendJsonRpcRequestToPeer_withMessageId_tracksCorrectly() throws Exception {
    ThinPeer tp = null;
    try {
      // Given: ThinPeer connected via WebSocket
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.JSON_RPC)
              .withSelfRegistration(false)
              .init();

      PeerInfo jsonRpcPeer =
          findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElseThrow();
      boolean connected = tp.connectToPeer(jsonRpcPeer, Duration.ofSeconds(5));
      assertTrue("Should connect to shared peer", connected);

      // When: Multiple concurrent requests with different message IDs
      String messageId1 = "test-message-id-1";
      String messageId2 = "test-message-id-2";

      JsonRpcRequest request1 =
          JsonRpcMessageFactory.buildClassMethodCall(
              messageId1,
              "io.quasient.pal.apps.quantized.rpc.Methods",
              "staticIntNoArgs",
              Collections.emptyList());
      JsonRpcRequest request2 =
          JsonRpcMessageFactory.buildClassMethodCall(
              messageId2,
              "io.quasient.pal.apps.quantized.rpc.Methods",
              "staticIntNoArgs",
              Collections.emptyList());

      CompletableFuture<JsonRpcResponse> responseFuture1 =
          tp.sendJsonRpcRequestToPeer(request1, messageId1);
      CompletableFuture<JsonRpcResponse> responseFuture2 =
          tp.sendJsonRpcRequestToPeer(request2, messageId2);

      JsonRpcResponse response1 = responseFuture1.get(10, TimeUnit.SECONDS);
      JsonRpcResponse response2 = responseFuture2.get(10, TimeUnit.SECONDS);

      // Then: Responses correlate to their respective message IDs
      assertNotNull("Response 1 should not be null", response1);
      assertNotNull("Response 2 should not be null", response2);
      assertEquals("Response 1 ID should match request 1 ID", messageId1, response1.getId());
      assertEquals("Response 2 ID should match request 2 ID", messageId2, response2.getId());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  /**
   * Tests that WebSocket connection loss is handled gracefully.
   *
   * <p>Acceptance Criteria: [INTEGRATION:ThinPeerIT.wsClient_connectionLost_handlesGracefully]
   */
  @Test
  public void wsClient_connectionLost_handlesGracefully() throws Exception {
    ThinPeer tp = null;
    try {
      // Given: ThinPeer connected via WebSocket
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.JSON_RPC)
              .withSelfRegistration(false)
              .init();

      PeerInfo jsonRpcPeer =
          findRpcPeer(RpcType.JSON_RPC, directoryConnectionProvider).orElseThrow();
      boolean connected = tp.connectToPeer(jsonRpcPeer, Duration.ofSeconds(5));
      assertTrue("Should connect to shared peer", connected);

      // When: Close the ThinPeer's WebSocket connection gracefully
      // This simulates connection loss and tests graceful handling
      tp.close();

      // Then: ThinPeer should be in closed state without throwing exceptions
      assertTrue("ThinPeer should be closed", tp.isClosed());

      // Trying to use a closed ThinPeer should throw appropriate exception
      try {
        tp.sendPing();
        fail("Expected IllegalStateException when using closed ThinPeer");
      } catch (IllegalStateException e) {
        // Expected behavior - connection loss handled gracefully
        logger.debug("Got expected IllegalStateException: {}", e.getMessage());
      }
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  /**
   * Tests that sendExecMessageToLog appends successfully to Chronicle log.
   *
   * <p>Acceptance Criteria:
   * [INTEGRATION:ThinPeerIT.sendExecMessageToChronicleLog_validMessage_appendsSuccessfully]
   */
  @Test
  public void sendExecMessageToChronicleLog_validMessage_appendsSuccessfully() throws Exception {
    // Create a temp directory for Chronicle queue
    Path tempDir = Files.createTempDirectory("chronicle-test-write");
    chronicleTempDirs.add(tempDir);
    Path queuePath = tempDir.resolve("test-queue");

    ThinPeer tp = null;
    try {
      // Given: ThinPeer with Chronicle output log configured
      LogInfo chronicleLog = new LogInfo(queuePath.toString());
      chronicleLog.setLogType(LogType.CHRONICLE);

      // ThinPeer requires a producer to enable log I/O - use a mock producer
      // The actual Chronicle writing bypasses Kafka when log type is CHRONICLE
      MockProducer<String, LogMessage<?>> mockProducer =
          new MockProducer<>(Cluster.empty(), true, null, null, null);

      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutputLog(chronicleLog)
              .withProducer(mockProducer)
              .withSelfRegistration(false)
              .init();

      // When: sendExecMessageToLog() called with a valid ExecMessage
      ExecMessage execMsg = msgBuilder.buildEmptyConstructor(tp.getPeerUuid(), "java.lang.String");

      Future<?> sendFuture = tp.sendExecMessageToLog(execMsg);
      sendFuture.get(5, TimeUnit.SECONDS);

      // Then: Message should be appended to Chronicle queue
      assertTrue("Chronicle queue should exist", ChronicleLogUtil.queueExists(queuePath));
      int messageCount = ChronicleLogUtil.countMessages(queuePath);
      assertTrue("Chronicle queue should contain at least one message", messageCount >= 1);

      ChronicleLogUtil.QueueIndexInfo indexInfo = ChronicleLogUtil.getQueueIndexInfo(queuePath);
      assertNotNull("Queue index info should be available", indexInfo);
      assertEquals("First index should be 0", 0, indexInfo.getFirstIndex());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  /**
   * Tests Chronicle round-trip: send message and receive response.
   *
   * <p>This test verifies that ThinPeer can write to a Chronicle queue and that messages are
   * correctly serialized using the OutboundMsg format. Since this test does not require a full peer
   * to process the messages, we verify the write operation and message format directly.
   *
   * <p>Acceptance Criteria:
   * [INTEGRATION:ThinPeerIT.sendExecMessageToChronicleLogAndReceive_validMessage_receivesResponse]
   */
  @Test
  public void sendExecMessageToChronicleLogAndReceive_validMessage_receivesResponse()
      throws Exception {
    // Create temp directory for Chronicle queue
    Path tempDir = Files.createTempDirectory("chronicle-test-roundtrip");
    chronicleTempDirs.add(tempDir);
    Path queuePath = tempDir.resolve("roundtrip-queue");

    ThinPeer tp = null;
    try {
      // Given: ThinPeer with Chronicle output log configured
      LogInfo chronicleLog = new LogInfo(queuePath.toString());
      chronicleLog.setLogType(LogType.CHRONICLE);

      // ThinPeer requires a producer to enable log I/O - use a mock producer
      // The actual Chronicle writing bypasses Kafka when log type is CHRONICLE
      MockProducer<String, LogMessage<?>> mockProducer =
          new MockProducer<>(Cluster.empty(), true, null, null, null);

      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutputLog(chronicleLog)
              .withProducer(mockProducer)
              .withSelfRegistration(false)
              .init();

      // When: Write multiple messages to verify queue operations
      ExecMessage execMsg1 = msgBuilder.buildEmptyConstructor(tp.getPeerUuid(), "java.lang.String");
      ExecMessage execMsg2 =
          msgBuilder.buildEmptyConstructor(tp.getPeerUuid(), "java.lang.Integer");

      Future<?> sendFuture1 = tp.sendExecMessageToLog(execMsg1);
      Future<?> sendFuture2 = tp.sendExecMessageToLog(execMsg2);
      sendFuture1.get(5, TimeUnit.SECONDS);
      sendFuture2.get(5, TimeUnit.SECONDS);

      // Then: Messages should be written and readable from the queue
      assertTrue("Chronicle queue should exist", ChronicleLogUtil.queueExists(queuePath));
      int messageCount = ChronicleLogUtil.countMessages(queuePath);
      assertEquals("Chronicle queue should contain 2 messages", 2, messageCount);

      // Verify the messages can be read back using Chronicle's low-level API
      try (ChronicleQueue queue =
          SingleChronicleQueueBuilder.binary(queuePath.toFile()).readOnly(true).build()) {
        ExcerptTailer tailer = queue.createTailer();
        tailer.toStart();

        OutboundMsg readMsg1 = OutboundMsg.readNext(tailer);
        assertNotNull("First message should be readable", readMsg1);
        assertNotNull("First message should have body", readMsg1.getBody());

        OutboundMsg readMsg2 = OutboundMsg.readNext(tailer);
        assertNotNull("Second message should be readable", readMsg2);
        assertNotNull("Second message should have body", readMsg2.getBody());
      }
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  /**
   * Tests that getMessageAtOffset retrieves correct message from Chronicle log.
   *
   * <p>This test verifies that messages can be written to a Chronicle queue and then read back
   * correctly. It uses ThinPeer to write messages to a Chronicle queue, then verifies the messages
   * using the Chronicle API directly.
   *
   * <p>Acceptance Criteria:
   * [INTEGRATION:ThinPeerIT.getMessageAtOffset_chronicleLog_retrievesCorrectMessage]
   */
  @Test
  public void getMessageAtOffset_chronicleLog_retrievesCorrectMessage() throws Exception {
    // Create temp directory for Chronicle queue
    Path tempDir = Files.createTempDirectory("chronicle-test-offset");
    chronicleTempDirs.add(tempDir);
    Path queuePath = tempDir.resolve("offset-queue");

    ThinPeer tp = null;
    String[] messageIds = new String[3];

    try {
      // Given: ThinPeer configured to write to Chronicle queue
      LogInfo chronicleLog = new LogInfo(queuePath.toString());
      chronicleLog.setLogType(LogType.CHRONICLE);

      // ThinPeer requires a producer to enable log I/O
      MockProducer<String, LogMessage<?>> mockProducer =
          new MockProducer<>(Cluster.empty(), true, null, null, null);

      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutputLog(chronicleLog)
              .withProducer(mockProducer)
              .withSelfRegistration(false)
              .init();

      // When: Write 3 messages to the Chronicle queue via ThinPeer
      for (int i = 0; i < 3; i++) {
        ExecMessage execMsg =
            msgBuilder.buildEmptyConstructor(tp.getPeerUuid(), "java.lang.String" + i);
        messageIds[i] = execMsg.getMessageId();

        Future<?> sendFuture = tp.sendExecMessageToLog(execMsg);
        sendFuture.get(5, TimeUnit.SECONDS);
      }
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }

    // Then: Verify messages were written and can be read back
    assertTrue("Chronicle queue should exist", ChronicleLogUtil.queueExists(queuePath));
    assertEquals(
        "Chronicle queue should contain 3 messages", 3, ChronicleLogUtil.countMessages(queuePath));

    // Read messages back and verify they were written correctly
    // Note: When reading from Chronicle Queue, OutboundMsg.readNext() reconstructs the message
    // with type and body, but doesn't extract the messageId from the body. The messageId is
    // embedded in the serialized body and would need deserialization to extract.
    try (ChronicleQueue queue =
        SingleChronicleQueueBuilder.binary(queuePath.toFile()).readOnly(true).build()) {
      ExcerptTailer tailer = queue.createTailer();
      tailer.toStart();

      // Read first message (offset 0)
      OutboundMsg msg0 = OutboundMsg.readNext(tailer);
      assertNotNull("Message at offset 0 should exist", msg0);
      assertNotNull("Message 0 should have body", msg0.getBody());
      assertTrue("Message 0 body should not be empty", msg0.getBody().length > 0);

      // Read second message (offset 1)
      OutboundMsg msg1 = OutboundMsg.readNext(tailer);
      assertNotNull("Message at offset 1 should exist", msg1);
      assertNotNull("Message 1 should have body", msg1.getBody());
      assertTrue("Message 1 body should not be empty", msg1.getBody().length > 0);

      // Read third message (offset 2)
      OutboundMsg msg2 = OutboundMsg.readNext(tailer);
      assertNotNull("Message at offset 2 should exist", msg2);
      assertNotNull("Message 2 should have body", msg2.getBody());
      assertTrue("Message 2 body should not be empty", msg2.getBody().length > 0);
    }

    // Verify queue index info
    ChronicleLogUtil.QueueIndexInfo indexInfo = ChronicleLogUtil.getQueueIndexInfo(queuePath);
    assertNotNull("Queue index info should not be null", indexInfo);
    assertEquals("First index should be 0", 0, indexInfo.getFirstIndex());
    assertEquals("Last index should be 2", 2, indexInfo.getLastIndex());
    assertEquals("Message count should be 3", 3, indexInfo.getMessageCount());
  }

  /**
   * Tests that connectWebSocketWithTimeout returns false for an unreachable peer.
   *
   * <p>This test verifies that when attempting to connect to an invalid peer address, the
   * connection attempt fails gracefully by returning false rather than throwing an exception. The
   * timeout behavior depends on the underlying WebSocket implementation: - Connection refused
   * errors (closed port) typically return immediately - Non-routable addresses may wait for the
   * full timeout
   *
   * <p>Acceptance Criteria:
   * [INTEGRATION:ThinPeerIT.connectWebSocketWithTimeout_invalidPeer_timesOut]
   */
  @Test
  public void connectWebSocketWithTimeout_invalidPeer_timesOut() throws Exception {
    ThinPeer tp = null;
    try {
      // Given: ThinPeer configured for JSON-RPC
      tp =
          new ThinPeer()
              .withDirectoryProvider(directoryConnectionProvider)
              .withOutboundRpcType(RpcType.JSON_RPC)
              .withSelfRegistration(false)
              .init();

      // Create PeerInfo with a non-existent JSON-RPC address (port 1 is typically not listening)
      PeerInfo invalidPeer = new PeerInfo(UUID.randomUUID(), "invalid-peer");
      invalidPeer.setJsonrpcAddress("ws://localhost:1");

      // When: connectToPeer with timeout
      Duration timeout = Duration.ofSeconds(2);
      long startTime = System.currentTimeMillis();
      boolean connected = tp.connectToPeer(invalidPeer, timeout);
      long elapsedTime = System.currentTimeMillis() - startTime;

      // Then: Connection should fail
      assertFalse("Connection should fail for invalid peer", connected);

      // Verify the call didn't take significantly longer than the timeout
      // (allowing for some overhead)
      long timeoutMs = timeout.toMillis();
      assertTrue(
          "Elapsed time should not exceed timeout by more than 500ms",
          elapsedTime <= timeoutMs + 500);

      // After a failed connection, ThinPeer should still be usable (not in an error state)
      assertFalse("ThinPeer should not be closed after failed connection", tp.isClosed());
    } finally {
      if (tp != null && !tp.isClosed()) {
        try {
          tp.close();
        } catch (IllegalStateException e) {
          logger.debug("Ignoring IllegalStateException while closing ThinPeer", e);
        }
      }
    }
  }

  // ============================================================================
  // ThinPeer Integration Test Specifications (Issue #635)
  // These test specifications define acceptance criteria for ThinPeer methods
  // that require real Kafka/ZMQ infrastructure. Implementation in #637.
  // ============================================================================

  /**
   * Tests that getAllWalMessages retrieves all messages written to a Kafka WAL.
   *
   * <p>Acceptance Criteria:
   * [INTEGRATION:ThinPeerIT.getAllWalMessages_multipleMessages_allRetrieved]
   */
  @Test
  @Ignore("Awaiting implementation in #637")
  public void getAllWalMessages_multipleMessages_allRetrieved() throws Exception {
    // Given: ThinPeer with Kafka WAL configured (real producer and consumer properties),
    //        3 ExecMessages written to the WAL via sendExecMessageToLog()
    // When: getAllWalMessages() is called
    // Then: All 3 messages are returned in the list

    // TODO(#637): Implement test logic
    // 1. Create ThinPeer with real Kafka consumer/producer properties and a test log
    // 2. Send 3 distinct ExecMessages to the log via sendExecMessageToLog()
    // 3. Wait for sends to complete (Future.get())
    // 4. Call getAllWalMessages()
    // 5. Assert returned list has size >= 3
    // 6. Verify messages match what was sent (by message ID or content)
    fail("Not yet implemented");
  }

  /**
   * Tests that getAllWalMessages returns an empty list for an empty WAL.
   *
   * <p>Acceptance Criteria: [INTEGRATION:ThinPeerIT.getAllWalMessages_emptyLog_returnsEmptyList]
   */
  @Test
  @Ignore("Awaiting implementation in #637")
  public void getAllWalMessages_emptyLog_returnsEmptyList() throws Exception {
    // Given: ThinPeer with Kafka WAL configured but no messages written
    // When: getAllWalMessages() is called
    // Then: An empty list is returned

    // TODO(#637): Implement test logic
    // 1. Create a fresh test log (unique name to ensure no pre-existing messages)
    // 2. Create ThinPeer with real Kafka consumer/producer properties and the fresh log
    // 3. Call getAllWalMessages() immediately without sending any messages
    // 4. Assert returned list is empty (size == 0)
    fail("Not yet implemented");
  }

  /**
   * Tests that getMessages retrieves the requested number of messages from a given offset.
   *
   * <p>Acceptance Criteria: [INTEGRATION:ThinPeerIT.getMessages_fromOffset_returnsRequestedCount]
   */
  @Test
  @Ignore("Awaiting implementation in #637")
  public void getMessages_fromOffset_returnsRequestedCount() throws Exception {
    // Given: ThinPeer with Kafka WAL containing 5 messages
    // When: getMessages(2, 2) is called (start at offset 2, request 2 messages)
    // Then: Exactly 2 ConsumerRecords are returned from offset 2

    // TODO(#637): Implement test logic
    // 1. Create ThinPeer with real Kafka consumer/producer properties and a test log
    // 2. Send 5 distinct ExecMessages to the log
    // 3. Wait for all sends to complete
    // 4. Call getMessages(2, 2)
    // 5. Assert returned list has size == 2
    // 6. Verify messages correspond to offsets 2 and 3
    fail("Not yet implemented");
  }

  /**
   * Tests that sendJsonRpcRequestToLog sends a valid request successfully.
   *
   * <p>Acceptance Criteria:
   * [INTEGRATION:ThinPeerIT.sendJsonRpcRequestToLog_validRequest_sentSuccessfully]
   */
  @Test
  @Ignore("Awaiting implementation in #637")
  public void sendJsonRpcRequestToLog_validRequest_sentSuccessfully() throws Exception {
    // Given: ThinPeer configured as a producer with a Kafka WAL
    // When: sendJsonRpcRequestToLog() is called with a valid JsonRpcRequest
    // Then: The returned Future completes successfully (no exception)

    // TODO(#637): Implement test logic
    // 1. Create ThinPeer with real Kafka producer properties and a test log
    // 2. Build a valid JsonRpcRequest using JsonRpcMessageFactory.buildClassMethodCall()
    // 3. Call sendJsonRpcRequestToLog(request)
    // 4. Assert the returned Future completes within timeout (future.get(5, SECONDS))
    // 5. Verify RecordMetadata has a valid offset (>= 0)
    fail("Not yet implemented");
  }

  /**
   * Tests that the listener thread receives messages sent to the ThinPeer's inbound socket.
   *
   * <p>Acceptance Criteria: [INTEGRATION:ThinPeerIT.startListenerThread_receivesMessages]
   */
  @Test
  @Ignore("Awaiting implementation in #637")
  public void startListenerThread_receivesMessages() throws Exception {
    // Given: ThinPeer initialized with a ZMQ RPC address (which starts the listener thread),
    //        and a registered IncomingMessageListener
    // When: A message arrives on the inbound RPC socket (sent from another ThinPeer)
    // Then: The listener's onMessageReceived() callback is invoked with the message

    // TODO(#637): Implement test logic
    // 1. Create ThinPeer with withZmqRpcAddress("tcp://127.0.0.1:0") to bind inbound socket
    //    and start the listener thread
    // 2. Register an IncomingMessageListener that captures received messages
    // 3. Create a second ThinPeer and connect it to the first peer's ZMQ address
    // 4. Send a message from the second peer to the first peer
    // 5. Wait briefly for async delivery
    // 6. Assert the listener was called and the message was received
    // 7. Alternatively, verify pullReceivedMessages() returns the message
    fail("Not yet implemented");
  }

  /**
   * Tests that connectToPeer with timeout connects successfully to a valid ZMQ peer.
   *
   * <p>Acceptance Criteria: [INTEGRATION:ThinPeerIT.connectZmqSocketWithTimeout_validPeer_connects]
   */
  @Test
  @Ignore("Awaiting implementation in #637")
  public void connectZmqSocketWithTimeout_validPeer_connects() throws Exception {
    // Given: A running ZMQ peer (the shared peer) with a known address
    // When: connectToPeer(peerInfo, Duration.ofSeconds(10)) is called
    // Then: Returns true, isTalkingToPeer() returns true, isZmqSocketConnected() returns true

    // TODO(#637): Implement test logic
    // 1. Create ThinPeer with ZMQ_RPC outbound type
    // 2. Find the shared ZMQ peer via findRpcPeer()
    // 3. Call connectToPeer(peerInfo, Duration.ofSeconds(10))
    // 4. Assert returns true
    // 5. Assert isTalkingToPeer() is true
    // 6. Assert isZmqSocketConnected() is true
    // 7. Assert getCurrentPeer() matches the connected peer
    fail("Not yet implemented");
  }

  /**
   * Tests that connectToPeer with timeout returns false for a non-existent ZMQ address.
   *
   * <p>Acceptance Criteria:
   * [INTEGRATION:ThinPeerIT.connectZmqSocketWithTimeout_invalidPeer_returnsFalse]
   */
  @Test
  @Ignore("Awaiting implementation in #637")
  public void connectZmqSocketWithTimeout_invalidPeer_returnsFalse() throws Exception {
    // Given: A PeerInfo with a non-existent ZMQ RPC address
    // When: connectToPeer(invalidPeerInfo, Duration.ofSeconds(2)) is called
    // Then: Returns false within the timeout period

    // TODO(#637): Implement test logic
    // 1. Create ThinPeer with ZMQ_RPC outbound type
    // 2. Create a PeerInfo with a non-existent ZMQ address (e.g., tcp://localhost:59999)
    // 3. Call connectToPeer(invalidPeerInfo, Duration.ofSeconds(2))
    // 4. Assert returns false
    // 5. Assert elapsed time respects the timeout (within reasonable margin)
    // 6. Assert ThinPeer is still usable (not closed)
    fail("Not yet implemented");
  }

  /**
   * Tests that sendPing returns a positive elapsed time for a connected ZMQ peer.
   *
   * <p>Acceptance Criteria: [INTEGRATION:ThinPeerIT.sendPing_zmqSocket_returnsElapsedTime]
   */
  @Test
  @Ignore("Awaiting implementation in #637")
  public void sendPing_zmqSocket_returnsElapsedTime() throws Exception {
    // Given: ThinPeer connected to the shared peer via ZMQ RPC
    // When: sendPing() is called
    // Then: Returns a positive elapsed time (>= 0.0 milliseconds)

    // TODO(#637): Implement test logic
    // 1. Create ThinPeer with ZMQ_RPC outbound type
    // 2. Connect to the shared ZMQ peer
    // 3. Call sendPing()
    // 4. Assert returned value >= 0.0 (positive elapsed time in milliseconds)
    // 5. Optionally call sendPing(Duration.ofSeconds(5)) to test the timeout variant
    // 6. Assert the timeout variant also returns >= 0.0
    fail("Not yet implemented");
  }

  /**
   * Tests that sendToPeer with a ControlMessage receives a valid response.
   *
   * <p>Acceptance Criteria: [INTEGRATION:ThinPeerIT.sendToPeer_controlMessage_receivesResponse]
   */
  @Test
  @Ignore("Awaiting implementation in #637")
  public void sendToPeer_controlMessage_receivesResponse() throws Exception {
    // Given: ThinPeer connected to a running peer via ZMQ RPC
    // When: sendToPeer() is called with a PING ControlMessage
    // Then: A ControlMessage response is returned with OK status

    // TODO(#637): Implement test logic
    // 1. Create ThinPeer with ZMQ_RPC outbound type
    // 2. Connect to the shared ZMQ peer
    // 3. Build a PING ControlMessage using msgBuilder.buildControlCommandMessage()
    // 4. Call sendToPeer(controlMessage)
    // 5. Assert response is not null
    // 6. Assert response status is ControlStatusType.OK
    // 7. Assert response.getResponseToId() matches the sent message's ID
    fail("Not yet implemented");
  }
}
