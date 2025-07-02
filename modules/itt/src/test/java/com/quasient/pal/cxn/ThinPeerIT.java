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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.AbstractIntegrationTest;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.types.RpcType;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.time.Duration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.Cluster;
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
    directoryConnectionProvider = new DirectoryConnectionProvider(getPalDirectoryUrl());
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
    LogInfo inAndOutLog = createTestLog();
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
            .withLog(inAndOutLog)
            .withUuid(peerUuid)
            .withProducerProperties(producerProperties)
            .withConsumerProperties(consumerProperties)
            .withRpcAddress("tcp://localhost:1234")
            .withOutboundRpcType(RpcType.BIN_RPC)
            .withInitialPeer(initialPeer)
            .withSelfRegistration(false)
            .init();

    assertThat(thinPeer.getName(), is("test_peer"));
    assertThat(thinPeer.getPalDirectoryUrl(), is(getPalDirectoryUrl()));
    assertThat(thinPeer.getConsumer(), is(consumer));
    assertThat(thinPeer.getProducer(), is(producer));
    assertThat(thinPeer.getInLog(), is(inAndOutLog));
    assertThat(thinPeer.getOutLog(), is(inAndOutLog));
    assertThat(thinPeer.getPeerUuid(), is(peerUuid));
    assertThat(thinPeer.getProducerProperties(), is(producerProperties));
    assertThat(thinPeer.getConsumerProperties(), is(consumerProperties));
    assertThat(thinPeer.isLogIOEnabled(), is(true));
    assertThat(thinPeer.isConsuming(), is(true));
    assertThat(thinPeer.isProducing(), is(true));
    assertThat(thinPeer.getRpcAddress(), is("tcp://localhost:1234"));
    assertThat(thinPeer.getOutboundRpcType(), is(RpcType.BIN_RPC));
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
            .withOutboundRpcType(RpcType.BIN_RPC)
            .withSelfRegistration(false)
            .init();

    PeerInfo peer = findRpcPeer(RpcType.BIN_RPC, directoryConnectionProvider).orElseThrow();
    boolean connected = thinPeer.connectToPeer(peer, Duration.ofMinutes(2));
    assertTrue(connected);
  }
}
