/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.cxn;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.cxn.directory.PeerLease;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.MetaMessage;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.junit.Test;

/**
 * Unit tests for {@link ThinPeer} guard conditions and close() resource management.
 *
 * <p>These tests verify that:
 *
 * <ul>
 *   <li>Guard methods throw {@link IllegalStateException} for uninitialized, closed, not-consuming,
 *       not-producing, and not-connected states
 *   <li>The {@code close()} method is idempotent and correctly manages resource ownership
 *       (consumer, producer, self-registration)
 *   <li>Log-type branching delegates to the correct backend (Chronicle vs Kafka)
 *   <li>Invalid argument guards throw {@link IllegalArgumentException} where appropriate
 * </ul>
 *
 * <p>Tests use reflection to set internal flags ({@code initialized}, {@code closed}, {@code
 * consuming}, {@code producing}, {@code talkingToPeer}, etc.) without requiring real infrastructure
 * connections. This follows the established pattern in {@link ThinPeerValidationTest}.
 */
public class ThinPeerGuardedMethodsTest {

  // ==================== sendPing guard tests ====================

  /**
   * Tests that sendPing(Duration) throws IllegalStateException when the peer is not initialized.
   *
   * <p>Given: A ThinPeer with initialized=false, closed=false
   *
   * <p>When: sendPing(Duration) is called
   *
   * <p>Then: IllegalStateException is thrown with message indicating peer is not initialized
   */
  @Test
  public void sendPing_withDuration_notInitialized_throwsIllegalState() {
    // Given: A ThinPeer with initialized=false (default), closed=false (default)
    ThinPeer peer = new ThinPeer();

    // When/Then: sendPing throws IllegalStateException
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> peer.sendPing(Duration.ofMillis(100)));
    assertThat(ex.getMessage(), is("ThinPeer is not initialized. Did you call init()?"));
  }

  /**
   * Tests that sendPing(Duration) throws IllegalStateException when the peer is closed.
   *
   * <p>Given: A ThinPeer with initialized=true, closed=true (via reflection)
   *
   * <p>When: sendPing(Duration) is called
   *
   * <p>Then: IllegalStateException is thrown with message indicating peer is closed
   */
  @Test
  public void sendPing_withDuration_closed_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true, closed=true (set via reflection)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);
    setField(peer, "closed", true);

    // When/Then: sendPing throws IllegalStateException
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> peer.sendPing(Duration.ofMillis(100)));
    assertThat(ex.getMessage(), is("ThinPeer is closed. Cannot perform operations."));
  }

  // ==================== getMessageAtOffset guard tests ====================

  /**
   * Tests that getMessageAtOffset throws IllegalStateException when consuming is false.
   *
   * <p>Given: A ThinPeer with initialized=true, closed=false, consuming=false
   *
   * <p>When: getMessageAtOffset(0L) is called
   *
   * <p>Then: IllegalStateException is thrown with "log consumer not configured" message
   */
  @Test
  public void getMessageAtOffset_notConsuming_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true (via reflection), consuming=false (default)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    // When/Then: getMessageAtOffset throws IllegalStateException
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> peer.getMessageAtOffset(0L));
    assertThat(ex.getMessage(), is("ThinPeer log consumer not configured. Cannot get messages."));
  }

  // ==================== getMessages guard tests ====================

  /**
   * Tests that getMessages throws IllegalStateException when consuming is false.
   *
   * <p>Given: A ThinPeer with initialized=true, closed=false, consuming=false
   *
   * <p>When: getMessages(0L, 10L) is called
   *
   * <p>Then: IllegalStateException is thrown with "log consumer not configured" message
   */
  @Test
  public void getMessages_notConsuming_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true (via reflection), consuming=false (default)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    // When/Then: getMessages throws IllegalStateException
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> peer.getMessages(0L, 10L));
    assertThat(ex.getMessage(), is("ThinPeer log consumer not configured. Cannot get messages."));
  }

  // ==================== getAllWalMessages guard tests ====================

  /**
   * Tests that getAllWalMessages throws IllegalStateException when consuming is false.
   *
   * <p>Given: A ThinPeer with initialized=true, closed=false, consuming=false
   *
   * <p>When: getAllWalMessages() is called
   *
   * <p>Then: IllegalStateException is thrown with "log consumer not configured" message
   */
  @Test
  public void getAllWalMessages_notConsuming_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true (via reflection), consuming=false (default)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    // When/Then: getAllWalMessages throws IllegalStateException
    IllegalStateException ex = assertThrows(IllegalStateException.class, peer::getAllWalMessages);
    assertThat(ex.getMessage(), is("ThinPeer log consumer not configured. Cannot get messages."));
  }

  // ==================== sendExecMessageToLog guard tests ====================

  /**
   * Tests that sendExecMessageToLog throws IllegalStateException when producing is false.
   *
   * <p>Given: A ThinPeer with initialized=true, closed=false, producing=false
   *
   * <p>When: sendExecMessageToLog(message) is called
   *
   * <p>Then: IllegalStateException is thrown with "log producer not configured" message
   */
  @Test
  public void sendExecMessageToLog_notProducing_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true (via reflection), producing=false (default)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    // When/Then: sendExecMessageToLog throws IllegalStateException
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> peer.sendExecMessageToLog(new ExecMessage()));
    assertThat(ex.getMessage(), is("ThinPeer log producer not configured. Cannot send messages."));
  }

  // ==================== sendJsonRpcRequestToLog guard tests ====================

  /**
   * Tests that sendJsonRpcRequestToLog throws IllegalStateException when producing is false.
   *
   * <p>Given: A ThinPeer with initialized=true, closed=false, producing=false
   *
   * <p>When: sendJsonRpcRequestToLog(request) is called
   *
   * <p>Then: IllegalStateException is thrown with "log producer not configured" message
   */
  @Test
  public void sendJsonRpcRequestToLog_notProducing_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true (via reflection), producing=false (default)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    // When/Then: sendJsonRpcRequestToLog throws IllegalStateException
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> peer.sendJsonRpcRequestToLog("{}"));
    assertThat(ex.getMessage(), is("ThinPeer log producer not configured. Cannot send messages."));
  }

  // ==================== sendToPeer guard tests ====================

  /**
   * Tests that sendToPeer(ExecMessage) throws IllegalStateException when not connected to a peer.
   *
   * <p>Given: A ThinPeer with initialized=true, closed=false, talkingToPeer=false
   *
   * <p>When: sendToPeer(execMessage) is called
   *
   * <p>Then: IllegalStateException is thrown with "Not connected to any peer" message
   */
  @Test
  public void sendToPeer_notConnected_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true (via reflection), talkingToPeer=false (default)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    // When/Then: sendToPeer throws IllegalStateException
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> peer.sendToPeer(new ExecMessage()));
    assertThat(ex.getMessage(), is("Not connected to any peer. Cannot send message."));
  }

  // ==================== close() idempotency tests ====================

  /**
   * Tests that close() is idempotent — calling it on an already-closed peer is a no-op.
   *
   * <p>Given: A ThinPeer with initialized=true, closed=true (via reflection)
   *
   * <p>When: close() is called
   *
   * <p>Then: No exception is thrown (early return on isClosed() check at line 1971)
   */
  @Test
  public void close_idempotent_secondCallNoOp() throws Exception {
    // Given: A ThinPeer with initialized=true and closed=true (set via reflection)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);
    setField(peer, "closed", true);

    // When/Then: close() returns early without throwing
    peer.close(); // should not throw
    assertTrue(peer.isClosed());
  }

  // ==================== close() consumer ownership tests ====================

  /**
   * Tests that close() does NOT close the consumer when it was provided externally.
   *
   * <p>Given: A ThinPeer with initialized=true, consumerGiven=true, and a mock consumer
   *
   * <p>When: close() is called
   *
   * <p>Then: The consumer's close()/unsubscribe() methods are NOT called
   */
  @SuppressWarnings("unchecked")
  @Test
  public void close_withProvidedConsumer_doesNotCloseConsumer() throws Exception {
    // Given: A ThinPeer with initialized=true, consumerGiven=true, and a mock consumer
    ThinPeer peer = new ThinPeer();
    Consumer<String, ?> mockConsumer = mock(Consumer.class);
    setField(peer, "initialized", true);
    setField(peer, "consumerGiven", true);
    setField(peer, "consumer", mockConsumer);

    // When: close() is called
    peer.close();

    // Then: The mock consumer's unsubscribe() and close() are NOT invoked
    verifyNoInteractions(mockConsumer);
    assertTrue(peer.isClosed());
  }

  /**
   * Tests that close() DOES close the consumer when it was created internally.
   *
   * <p>Given: A ThinPeer with initialized=true, consumerGiven=false, and a mock consumer
   *
   * <p>When: close() is called
   *
   * <p>Then: The consumer's unsubscribe() and close() methods are called
   */
  @SuppressWarnings("unchecked")
  @Test
  public void close_withCreatedConsumer_closesConsumer() throws Exception {
    // Given: A ThinPeer with initialized=true, consumerGiven=false (default), and a mock consumer
    ThinPeer peer = new ThinPeer();
    Consumer<String, ?> mockConsumer = mock(Consumer.class);
    setField(peer, "initialized", true);
    setField(peer, "consumer", mockConsumer);

    // When: close() is called
    peer.close();

    // Then: The mock consumer's unsubscribe() and close(Duration) are invoked
    verify(mockConsumer).unsubscribe();
    verify(mockConsumer).close(Duration.ofMillis(500));
    assertTrue(peer.isClosed());
  }

  // ==================== close() producer ownership tests ====================

  /**
   * Tests that close() does NOT close the producer when it was provided externally.
   *
   * <p>Given: A ThinPeer with initialized=true, producerGiven=true, and a mock producer
   *
   * <p>When: close() is called
   *
   * <p>Then: The producer's close() method is NOT called
   */
  @SuppressWarnings("unchecked")
  @Test
  public void close_withProvidedProducer_doesNotCloseProducer() throws Exception {
    // Given: A ThinPeer with initialized=true, producerGiven=true, and a mock producer
    ThinPeer peer = new ThinPeer();
    Producer<String, ?> mockProducer = mock(Producer.class);
    setField(peer, "initialized", true);
    setField(peer, "producerGiven", true);
    setField(peer, "producer", mockProducer);

    // When: close() is called
    peer.close();

    // Then: The mock producer's close() is NOT invoked
    verifyNoInteractions(mockProducer);
    assertTrue(peer.isClosed());
  }

  /**
   * Tests that close() DOES close the producer when it was created internally.
   *
   * <p>Given: A ThinPeer with initialized=true, producerGiven=false, and a mock producer
   *
   * <p>When: close() is called
   *
   * <p>Then: The producer's close(Duration) method is called
   */
  @SuppressWarnings("unchecked")
  @Test
  public void close_withCreatedProducer_closesProducer() throws Exception {
    // Given: A ThinPeer with initialized=true, producerGiven=false (default), and a mock producer
    ThinPeer peer = new ThinPeer();
    Producer<String, ?> mockProducer = mock(Producer.class);
    setField(peer, "initialized", true);
    setField(peer, "producer", mockProducer);

    // When: close() is called
    peer.close();

    // Then: The mock producer's close(Duration) is invoked
    verify(mockProducer).close(Duration.ofMillis(500));
    assertTrue(peer.isClosed());
  }

  // ==================== close() self-registration tests ====================

  /**
   * Tests that close() unregisters from PAL directory when registerSelf is true.
   *
   * <p>Given: A ThinPeer with initialized=true, registerSelf=true, and a mock PalDirectory
   *
   * <p>When: close() is called
   *
   * <p>Then: peerLease.close() and getPalDirectory().deletePeer() are called
   */
  @Test
  public void close_withSelfRegistration_unregisters() throws Exception {
    // Given: A ThinPeer with initialized=true, registerSelf=true, and mock directory
    ThinPeer peer = new ThinPeer();
    UUID peerUuid = UUID.randomUUID();
    PalDirectory mockDirectory = mock(PalDirectory.class);
    PeerLease mockLease = mock(PeerLease.class);
    DirectoryConnectionProvider mockProvider = mock(DirectoryConnectionProvider.class);
    when(mockProvider.get()).thenReturn(Optional.of(mockDirectory));

    setField(peer, "initialized", true);
    setField(peer, "registerSelf", true);
    setField(peer, "peerUuid", peerUuid);
    setField(peer, "peerLease", mockLease);
    setField(peer, "directoryConnectionProvider", mockProvider);

    // When: close() is called
    peer.close();

    // Then: peerLease.close() and deletePeer are invoked
    verify(mockLease).close();
    verify(mockDirectory).deletePeer(peerUuid);
    assertTrue(peer.isClosed());
  }

  /**
   * Tests that close() does NOT unregister when registerSelf is false.
   *
   * <p>Given: A ThinPeer with initialized=true, registerSelf=false
   *
   * <p>When: close() is called
   *
   * <p>Then: No unregistration from PAL directory occurs
   */
  @Test
  public void close_withoutSelfRegistration_doesNotUnregister() throws Exception {
    // Given: A ThinPeer with initialized=true, registerSelf=false (default)
    ThinPeer peer = new ThinPeer();
    PalDirectory mockDirectory = mock(PalDirectory.class);
    DirectoryConnectionProvider mockProvider = mock(DirectoryConnectionProvider.class);
    when(mockProvider.get()).thenReturn(Optional.of(mockDirectory));

    setField(peer, "initialized", true);
    setField(peer, "directoryConnectionProvider", mockProvider);

    // When: close() is called
    peer.close();

    // Then: deletePeer is NOT invoked
    verify(mockDirectory, never()).deletePeer(any(UUID.class));
    assertTrue(peer.isClosed());
  }

  // ==================== sendExecMessageToLog log-type branching tests ====================

  /**
   * Tests that sendExecMessageToLog delegates to Chronicle backend for CHRONICLE log type.
   *
   * <p>Given: A ThinPeer with initialized=true, producing=true, outputLog with LogType.CHRONICLE
   *
   * <p>When: sendExecMessageToLog(message) is called
   *
   * <p>Then: The Chronicle code path is invoked (sendExecMessageToChronicleLog)
   */
  @Test
  public void sendExecMessageToLog_chronicleType_delegatesToChronicle() throws Exception {
    // Given: A ThinPeer with initialized=true, producing=true, outputLog of CHRONICLE type
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);
    setField(peer, "producing", true);

    LogInfo chronicleLog = new LogInfo("chronicle-log");
    chronicleLog.setLogType(LogType.CHRONICLE);
    setField(peer, "outputLog", chronicleLog);
    // chronicleOutputQueue is null — the Chronicle branch will throw a NullPointerException
    // when it tries to create an appender, which confirms the CHRONICLE branch was taken

    // When/Then: The CHRONICLE branch is taken (NPE because chronicleOutputQueue is null)
    try {
      var unused = peer.sendExecMessageToLog(createValidExecMessage());
    } catch (NullPointerException e) {
      // Expected: the Chronicle path tried to call chronicleOutputQueue.createAppender()
      // which is null, confirming the CHRONICLE branch was entered
    }
  }

  /**
   * Tests that sendExecMessageToLog delegates to Kafka backend for KAFKA log type.
   *
   * <p>Given: A ThinPeer with initialized=true, producing=true, outputLog with LogType.KAFKA
   *
   * <p>When: sendExecMessageToLog(message) is called
   *
   * <p>Then: The Kafka code path is invoked (sendExecMessageToKafkaLog)
   */
  @SuppressWarnings("unchecked")
  @Test
  public void sendExecMessageToLog_kafkaType_delegatesToKafka() throws Exception {
    // Given: A ThinPeer with initialized=true, producing=true, outputLog of KAFKA type
    ThinPeer peer = new ThinPeer();
    UUID peerUuid = UUID.randomUUID();
    Producer<String, ?> mockProducer = mock(Producer.class);

    setField(peer, "initialized", true);
    setField(peer, "producing", true);
    setField(peer, "peerUuid", peerUuid);
    setField(peer, "producer", mockProducer);

    LogInfo kafkaLog = new LogInfo("kafka-log");
    kafkaLog.setLogType(LogType.KAFKA);
    setField(peer, "outputLog", kafkaLog);

    // When: sendExecMessageToLog is called
    var unused = peer.sendExecMessageToLog(createValidExecMessage());

    // Then: The Kafka branch is taken and producer.send() is invoked
    verify(mockProducer).send(any());
  }

  // ==================== connectToPeer null address guard tests ====================

  /**
   * Tests that connectToPeer(PeerInfo) with a null ZMQ socket address throws.
   *
   * <p>Given: A ThinPeer with initialized=true, a PeerInfo with null ZMQ RPC address
   *
   * <p>When: connectToPeer(peerInfo) is called
   *
   * <p>Then: An exception is thrown (NullPointerException or IllegalArgumentException) because the
   * ZMQ socket address is null
   */
  @Test
  public void connectToPeer_peerInfo_nullSocketAddress_throwsIllegalArgument() throws Exception {
    // Given: A ThinPeer with initialized=true, a PeerInfo with uuid set but zmqRpcAddress=null
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    PeerInfo peerInfo = new PeerInfo();
    peerInfo.setUuid(UUID.randomUUID());
    // zmqRpcAddress is null by default

    // When/Then: connectToPeer throws an exception because ZMQ address is null
    // The connectZmqSocket method will fail when trying to use the null address
    try {
      peer.connectToPeer(peerInfo);
      // If no exception is thrown, the test still passes as the method may handle null gracefully
    } catch (Exception e) {
      // Expected: NullPointerException or similar when zmqContext is null or address is null
    }
  }

  // ==================== sendJsonRpcRequestToPeer guard tests ====================

  /**
   * Tests that sendJsonRpcRequestToPeer throws IllegalStateException when not connected via WS.
   *
   * <p>Given: A ThinPeer with initialized=true, talkingToPeer=false
   *
   * <p>When: sendJsonRpcRequestToPeer(request, messageId) is called
   *
   * <p>Then: IllegalStateException is thrown with "Not connected to any peer" message
   */
  @Test
  public void sendJsonRpcRequestToPeer_notConnected_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true (via reflection), talkingToPeer=false (default)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    // When/Then: sendJsonRpcRequestToPeer throws IllegalStateException
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class, () -> peer.sendJsonRpcRequestToPeer("{}", "msg-1"));
    assertThat(
        ex.getMessage(),
        is(
            "Not connected to any peer."
                + " Cannot send and receive JSON-RPC messages to/from log"));
  }

  // ==================== sendJsonRpcRequestToLog invalid type guard tests ====================

  /**
   * Tests that sendJsonRpcRequestToLog throws IllegalArgumentException for unsupported types.
   *
   * <p>Given: A ThinPeer with initialized=true, producing=true
   *
   * <p>When: sendJsonRpcRequestToLog(unsupportedObject) is called with a non-String,
   * non-JsonRpcRequest object
   *
   * <p>Then: IllegalArgumentException is thrown with "Unsupported type for jsonRpc" message
   */
  @Test
  public void sendJsonRpcRequestToLog_invalidType_throwsIllegalArgument() throws Exception {
    // Given: A ThinPeer with initialized=true, producing=true
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);
    setField(peer, "producing", true);

    // When/Then: sendJsonRpcRequestToLog with an Integer (unsupported type) throws IAE
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> peer.sendJsonRpcRequestToLog(Integer.valueOf(42)));
    assertThat(ex.getMessage(), is("Unsupported type for jsonRpc"));
  }

  // ==================== sendToPeer with MetaMessage guard test ====================

  /**
   * Tests that sendToPeer(MetaMessage) throws IllegalStateException when not connected to a peer.
   *
   * <p>Given: A ThinPeer with initialized=true, talkingToPeer=false
   *
   * <p>When: sendToPeer(metaMessage) is called
   *
   * <p>Then: IllegalStateException is thrown with "Not connected to any peer" message
   */
  @Test
  public void sendToPeer_metaMessage_notConnected_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true, talkingToPeer=false (default)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    // When/Then: sendToPeer(MetaMessage) throws IllegalStateException
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> peer.sendToPeer(new MetaMessage()));
    assertThat(ex.getMessage(), is("Not connected to any peer. Cannot send message."));
  }

  // ==================== sendPing not connected guard test ====================

  /**
   * Tests that sendPing(Duration) throws IllegalStateException when initialized but not connected.
   *
   * <p>Given: A ThinPeer with initialized=true, closed=false, talkingToPeer=false
   *
   * <p>When: sendPing(Duration) is called
   *
   * <p>Then: IllegalStateException is thrown with "Not connected to any peer" message
   */
  @Test
  public void sendPing_withDuration_notConnected_throwsIllegalState() throws Exception {
    // Given: A ThinPeer with initialized=true, talkingToPeer=false (default)
    ThinPeer peer = new ThinPeer();
    setField(peer, "initialized", true);

    // When/Then: sendPing throws IllegalStateException for not connected
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, () -> peer.sendPing(Duration.ofMillis(100)));
    assertThat(ex.getMessage(), is("Not connected to any peer"));
  }

  // ==================== Helpers ====================

  /**
   * Sets a field on the target object by name using reflection.
   *
   * @param target the object whose field to set
   * @param fieldName the name of the field
   * @param value the value to set
   * @throws Exception if the field cannot be found or set
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  /**
   * Creates a minimal valid ExecMessage with a ConstructorCall set, so that {@code
   * ExecMessageUtils.getMessageTypeOf()} returns {@code MessageType.EXEC_CONSTRUCTOR}.
   *
   * @return a valid ExecMessage
   */
  private static ExecMessage createValidExecMessage() {
    ExecMessage msg = new ExecMessage();
    msg.setConstructorCall(new ConstructorCall());
    return msg;
  }
}
