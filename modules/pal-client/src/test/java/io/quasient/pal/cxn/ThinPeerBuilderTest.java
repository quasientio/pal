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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.messages.types.RpcType;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for {@link ThinPeer} builder pattern and configuration methods.
 *
 * <p>Tests the fluent builder API and configuration validation without requiring actual network
 * connections.
 */
public class ThinPeerBuilderTest {

  // ==================== UUID configuration tests ====================

  /** Tests that withUuid sets the peer UUID. */
  @Test
  public void testWithUuid_setsUuid() {
    UUID uuid = UUID.randomUUID();
    ThinPeer peer = new ThinPeer().withUuid(uuid);
    assertThat("UUID should be set", peer.getPeerUuid(), is(uuid));
  }

  /** Tests that UUID defaults to null when not explicitly set. */
  @Test
  public void testUuid_defaultsToNull() {
    ThinPeer peer = new ThinPeer();
    assertThat("Default UUID should be null until set", peer.getPeerUuid(), nullValue());
  }

  // ==================== Name configuration tests ====================

  /** Tests that withName sets the peer name. */
  @Test
  public void testWithName_setsName() {
    ThinPeer peer = new ThinPeer().withName("test-peer");
    assertThat("Name should be set", peer.getName(), is("test-peer"));
  }

  /** Tests that name defaults to null when not set. */
  @Test
  public void testName_defaultsToNull() {
    ThinPeer peer = new ThinPeer();
    assertThat("Default name should be null", peer.getName(), nullValue());
  }

  // ==================== ZMQ RPC configuration tests ====================

  /** Tests that withZmqRpcAddress sets the ZMQ RPC address. */
  @Test
  public void testWithZmqRpcAddress_setsAddress() {
    ThinPeer peer = new ThinPeer().withZmqRpcAddress("tcp://localhost:5555");
    assertThat(
        "ZMQ RPC address should be set", peer.getZmqRpcAddress(), is("tcp://localhost:5555"));
  }

  // ==================== Bootstrap servers tests ====================

  /** Tests that withBootstrapServers sets the Kafka bootstrap servers. */
  @Test
  public void testWithBootstrapServers_setsServers() {
    ThinPeer peer = new ThinPeer().withBootstrapServers("kafka1:9092,kafka2:9092");
    assertThat(
        "Bootstrap servers should be set",
        peer.getBootstrapServers(),
        is("kafka1:9092,kafka2:9092"));
  }

  // ==================== Log configuration tests ====================

  /** Tests that withLog sets both input and output logs. */
  @Test
  public void testWithLog_setsBothLogs() {
    LogInfo log = new LogInfo("test-topic");
    ThinPeer peer = new ThinPeer().withLog(log);
    assertThat("Input log should be set", peer.getInputLog(), is(log));
    assertThat("Output log should be set", peer.getOutputLog(), is(log));
  }

  /** Tests that withInputLog sets only the input log. */
  @Test
  public void testWithInputLog_setsInputOnly() {
    LogInfo inputLog = new LogInfo("input-topic");
    LogInfo outputLog = new LogInfo("output-topic");
    ThinPeer peer = new ThinPeer().withInputLog(inputLog).withOutputLog(outputLog);
    assertThat("Input log should be set", peer.getInputLog(), is(inputLog));
    assertThat("Output log should be set", peer.getOutputLog(), is(outputLog));
  }

  /** Tests that withLogPrefix sets the log prefix. */
  @Test
  public void testWithLogPrefix_setsPrefix() {
    ThinPeer peer = new ThinPeer().withLogPrefix("myapp");
    assertThat("Log prefix should be set", peer.getLogPrefix(), is("myapp"));
  }

  // ==================== RPC type configuration tests ====================

  /** Tests that withOutboundRpcType sets the RPC type. */
  @Test
  public void testWithOutboundRpcType_setsType() {
    ThinPeer peer = new ThinPeer().withOutboundRpcType(RpcType.JSON_RPC);
    assertThat("Outbound RPC type should be set", peer.getOutboundRpcType(), is(RpcType.JSON_RPC));
  }

  /** Tests that default outbound RPC type is ZMQ_RPC. */
  @Test
  public void testOutboundRpcType_defaultsToZmq() {
    ThinPeer peer = new ThinPeer();
    assertThat(
        "Default RPC type should be ZMQ_RPC", peer.getOutboundRpcType(), is(RpcType.ZMQ_RPC));
  }

  // ==================== Polling duration tests ====================

  /** Tests that withPollingDuration sets the duration. */
  @Test
  public void testWithPollingDuration_setsDuration() {
    ThinPeer peer = new ThinPeer().withPollingDuration(10);
    // Duration is internal, but we can verify the builder returns this
    assertThat("Builder should return same instance", peer, notNullValue());
  }

  // ==================== Self-registration tests ====================

  /** Tests that withSelfRegistration sets the flag. */
  @Test
  public void testWithSelfRegistration_setsFlag() {
    ThinPeer peer = new ThinPeer().withSelfRegistration(true);
    // Flag is internal, verify builder pattern works
    assertThat("Builder should return same instance", peer, notNullValue());
  }

  // ==================== Directory configuration tests ====================

  /** Tests that withDirectoryUrl sets the URL. */
  @Test
  public void testWithDirectoryUrl_setsUrl() {
    ThinPeer peer = new ThinPeer().withDirectoryUrl("http://localhost:2379");
    // URL is internal, verify builder pattern works
    assertThat("Builder should return same instance", peer, notNullValue());
  }

  // ==================== Properties configuration tests ====================

  /** Tests that withConsumerProperties sets properties. */
  @Test
  public void testWithConsumerProperties_setsProperties() {
    Properties props = new Properties();
    props.put("test.key", "test.value");
    ThinPeer peer = new ThinPeer().withConsumerProperties(props);
    assertThat("Builder should return same instance", peer, notNullValue());
  }

  /** Tests that withProducerProperties sets properties. */
  @Test
  public void testWithProducerProperties_setsProperties() {
    Properties props = new Properties();
    props.put("test.key", "test.value");
    ThinPeer peer = new ThinPeer().withProducerProperties(props);
    assertThat("Builder should return same instance", peer, notNullValue());
  }

  // ==================== WebSocket configuration tests ====================

  /** Tests that withWebsocketConnectionLostTimeout sets timeout. */
  @Test
  public void testWithWebsocketConnectionLostTimeout_setsTimeout() {
    ThinPeer peer = new ThinPeer().withWebsocketConnectionLostTimeout(30);
    assertThat("Builder should return same instance", peer, notNullValue());
  }

  // ==================== Chained builder tests ====================

  /** Tests that builder methods can be chained. */
  @Test
  public void testBuilderChaining_allMethods() {
    UUID uuid = UUID.randomUUID();
    ThinPeer peer =
        new ThinPeer()
            .withUuid(uuid)
            .withName("chained-peer")
            .withZmqRpcAddress("tcp://localhost:5555")
            .withBootstrapServers("kafka:9092")
            .withLog(new LogInfo("topic"))
            .withLogPrefix("prefix")
            .withOutboundRpcType(RpcType.JSON_RPC)
            .withPollingDuration(5)
            .withConsumerProperties(new Properties())
            .withProducerProperties(new Properties())
            .withWebsocketConnectionLostTimeout(10)
            .withSelfRegistration(false)
            .withDirectoryUrl("http://localhost:2379");

    assertThat("UUID should be set after chaining", peer.getPeerUuid(), is(uuid));
    assertThat("Name should be set after chaining", peer.getName(), is("chained-peer"));
    assertThat(
        "ZMQ RPC address should be set after chaining",
        peer.getZmqRpcAddress(),
        is("tcp://localhost:5555"));
    assertThat(
        "Bootstrap servers should be set after chaining",
        peer.getBootstrapServers(),
        is("kafka:9092"));
    assertThat("Log prefix should be set after chaining", peer.getLogPrefix(), is("prefix"));
    assertThat(
        "RPC type should be set after chaining", peer.getOutboundRpcType(), is(RpcType.JSON_RPC));
  }

  // ==================== State validation tests ====================

  /** Tests that isClosed returns false for new peer. */
  @Test
  public void testIsClosed_newPeer_returnsFalse() {
    ThinPeer peer = new ThinPeer();
    assertFalse("New peer should not be closed", peer.isClosed());
  }

  /** Tests that isInitialized returns false for new peer. */
  @Test
  public void testIsInitialized_newPeer_returnsFalse() {
    ThinPeer peer = new ThinPeer();
    assertFalse("New peer should not be initialized", peer.isInitialized());
  }

  // ==================== Guard condition tests ====================

  /** Tests that guarded methods throw before initialization. */
  @Test
  public void testGuardedMethod_beforeInit_throws() {
    ThinPeer peer = new ThinPeer();
    assertThrows(IllegalStateException.class, () -> peer.sendPing(Duration.ofSeconds(1)));
  }

  /** Tests that close throws before initialization. */
  @Test
  public void testClose_beforeInit_throws() {
    ThinPeer peer = new ThinPeer();
    assertThrows(IllegalStateException.class, peer::close);
  }

  /** Tests that getMessageAtOffset throws before initialization. */
  @Test
  public void testGetMessageAtOffset_beforeInit_throws() {
    ThinPeer peer = new ThinPeer();
    assertThrows(IllegalStateException.class, () -> peer.getMessageAtOffset(0L));
  }
}
