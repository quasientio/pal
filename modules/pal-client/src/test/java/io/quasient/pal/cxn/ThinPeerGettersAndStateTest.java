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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.Message;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;

/**
 * Unit tests for {@link ThinPeer} getter and state methods.
 *
 * <p>Tests all uncovered getter/boolean methods by setting fields via reflection and verifying
 * return values. Extends coverage from existing {@link ThinPeerBuilderTest} and {@link
 * ThinPeerGuardedMethodsTest}.
 *
 * <p>Tests use reflection to set internal flags without requiring real infrastructure connections.
 * This follows the established pattern in {@link ThinPeerValidationTest}.
 */
public class ThinPeerGettersAndStateTest {

  // ==================== Default state tests ====================

  /**
   * Tests that a new ThinPeer has consuming=false by default.
   *
   * <p>Given: A freshly constructed ThinPeer (no builder calls)
   *
   * <p>When: isConsuming() is called
   *
   * <p>Then: Returns false (the {@code consuming} field defaults to false)
   */
  @Test
  public void isConsuming_defaultFalse() {
    // Given: A freshly constructed ThinPeer
    ThinPeer peer = new ThinPeer();

    // When/Then: isConsuming() returns false
    assertFalse(peer.isConsuming());
  }

  /**
   * Tests that a new ThinPeer has producing=false by default.
   *
   * <p>Given: A freshly constructed ThinPeer (no builder calls)
   *
   * <p>When: isProducing() is called
   *
   * <p>Then: Returns false (the {@code producing} field defaults to false)
   */
  @Test
  public void isProducing_defaultFalse() {
    // Given: A freshly constructed ThinPeer
    ThinPeer peer = new ThinPeer();

    // When/Then: isProducing() returns false
    assertFalse(peer.isProducing());
  }

  /**
   * Tests that a new ThinPeer has registerSelf=false by default.
   *
   * <p>Given: A freshly constructed ThinPeer (no builder calls)
   *
   * <p>When: isSelfRegistering() is called
   *
   * <p>Then: Returns false (the {@code registerSelf} field defaults to false)
   */
  @Test
  public void isSelfRegistering_defaultFalse() {
    // Given: A freshly constructed ThinPeer
    ThinPeer peer = new ThinPeer();

    // When/Then: isSelfRegistering() returns false
    assertFalse(peer.isSelfRegistering());
  }

  /**
   * Tests that a new ThinPeer has talkingToPeer=false by default.
   *
   * <p>Given: A freshly constructed ThinPeer (no builder calls)
   *
   * <p>When: isTalkingToPeer() is called
   *
   * <p>Then: Returns false (the {@code talkingToPeer} field defaults to false)
   */
  @Test
  public void isTalkingToPeer_defaultFalse() {
    // Given: A freshly constructed ThinPeer
    ThinPeer peer = new ThinPeer();

    // When/Then: isTalkingToPeer() returns false
    assertFalse(peer.isTalkingToPeer());
  }

  /**
   * Tests that a new ThinPeer has isZmqSocketConnected=false by default.
   *
   * <p>Given: A freshly constructed ThinPeer (no builder calls)
   *
   * <p>When: isZmqSocketConnected() is called
   *
   * <p>Then: Returns false (the {@code isZmqSocketConnected} field defaults to false)
   */
  @Test
  public void isZmqSocketConnected_defaultFalse() {
    // Given: A freshly constructed ThinPeer
    ThinPeer peer = new ThinPeer();

    // When/Then: isZmqSocketConnected() returns false
    assertFalse(peer.isZmqSocketConnected());
  }

  /**
   * Tests that a new ThinPeer has logIOEnabled=false by default.
   *
   * <p>Given: A freshly constructed ThinPeer (no builder calls)
   *
   * <p>When: isLogIOEnabled() is called
   *
   * <p>Then: Returns false (the {@code logIOEnabled} field defaults to false)
   */
  @Test
  public void isLogIOEnabled_defaultFalse() {
    // Given: A freshly constructed ThinPeer
    ThinPeer peer = new ThinPeer();

    // When/Then: isLogIOEnabled() returns false
    assertFalse(peer.isLogIOEnabled());
  }

  /**
   * Tests that a new ThinPeer has isClosed=false by default.
   *
   * <p>Given: A freshly constructed ThinPeer (no builder calls)
   *
   * <p>When: isClosed() is called
   *
   * <p>Then: Returns false (the {@code closed} field defaults to false)
   */
  @Test
  public void isClosed_defaultFalse() {
    // Given: A freshly constructed ThinPeer
    ThinPeer peer = new ThinPeer();

    // When/Then: isClosed() returns false
    assertFalse(peer.isClosed());
  }

  /**
   * Tests that a new ThinPeer has isInitialized=false by default.
   *
   * <p>Given: A freshly constructed ThinPeer (no builder calls)
   *
   * <p>When: isInitialized() is called
   *
   * <p>Then: Returns false (the {@code initialized} field defaults to false)
   */
  @Test
  public void isInitialized_defaultFalse() {
    // Given: A freshly constructed ThinPeer
    ThinPeer peer = new ThinPeer();

    // When/Then: isInitialized() returns false
    assertFalse(peer.isInitialized());
  }

  // ==================== Reflection-based getter tests ====================

  /**
   * Tests that isConsuming() reflects the value of the consuming field set via reflection.
   *
   * <p>Given: A ThinPeer with {@code consuming} set to true via reflection
   *
   * <p>When: isConsuming() is called
   *
   * <p>Then: Returns true
   *
   * <p>Implementation note: Use {@code ThinPeer.class.getDeclaredField("consuming")} with {@code
   * setAccessible(true)} and {@code setBoolean(peer, true)}.
   */
  @Test
  public void isConsuming_reflectsField() throws Exception {
    // Given: A ThinPeer with consuming=true (set via reflection on field "consuming")
    ThinPeer peer = new ThinPeer();
    Field consumingField = ThinPeer.class.getDeclaredField("consuming");
    consumingField.setAccessible(true);
    consumingField.setBoolean(peer, true);

    // When/Then: isConsuming() returns true
    assertTrue(peer.isConsuming());
  }

  /**
   * Tests that isProducing() reflects the value of the producing field set via reflection.
   *
   * <p>Given: A ThinPeer with {@code producing} set to true via reflection
   *
   * <p>When: isProducing() is called
   *
   * <p>Then: Returns true
   *
   * <p>Implementation note: Use {@code ThinPeer.class.getDeclaredField("producing")} with {@code
   * setAccessible(true)} and {@code setBoolean(peer, true)}.
   */
  @Test
  public void isProducing_reflectsField() throws Exception {
    // Given: A ThinPeer with producing=true (set via reflection on field "producing")
    ThinPeer peer = new ThinPeer();
    Field producingField = ThinPeer.class.getDeclaredField("producing");
    producingField.setAccessible(true);
    producingField.setBoolean(peer, true);

    // When/Then: isProducing() returns true
    assertTrue(peer.isProducing());
  }

  /**
   * Tests that isSelfRegistering() reflects the value of the registerSelf field set via reflection.
   *
   * <p>Given: A ThinPeer with {@code registerSelf} set to true via reflection
   *
   * <p>When: isSelfRegistering() is called
   *
   * <p>Then: Returns true
   *
   * <p>Implementation note: Use {@code ThinPeer.class.getDeclaredField("registerSelf")} with {@code
   * setAccessible(true)} and {@code setBoolean(peer, true)}.
   */
  @Test
  public void isSelfRegistering_reflectsField() throws Exception {
    // Given: A ThinPeer with registerSelf=true (set via reflection on field "registerSelf")
    ThinPeer peer = new ThinPeer();
    Field registerSelfField = ThinPeer.class.getDeclaredField("registerSelf");
    registerSelfField.setAccessible(true);
    registerSelfField.setBoolean(peer, true);

    // When/Then: isSelfRegistering() returns true
    assertTrue(peer.isSelfRegistering());
  }

  // ==================== Cache and message tests ====================

  /**
   * Tests that getCachedMessageAtOffset returns null when no message is cached at that offset.
   *
   * <p>Given: A ThinPeer with an empty {@code lastRecordsRead} cache (default state)
   *
   * <p>When: The private getCachedMessageAtOffset(42L) is invoked via reflection
   *
   * <p>Then: Returns null
   *
   * <p>Implementation note: getCachedMessageAtOffset is private. Use reflection to invoke: {@code
   * Method m = ThinPeer.class.getDeclaredMethod("getCachedMessageAtOffset", Long.class);
   * m.setAccessible(true); Object result = m.invoke(peer, 42L); assertThat(result, nullValue());}
   */
  @Test
  public void getCachedMessageAtOffset_nullWhenNotCached() throws Exception {
    // Given: A ThinPeer with an empty lastRecordsRead map (default)
    ThinPeer peer = new ThinPeer();

    // When: getCachedMessageAtOffset(42L) is called via reflection
    Method method = ThinPeer.class.getDeclaredMethod("getCachedMessageAtOffset", Long.class);
    method.setAccessible(true);
    Object result = method.invoke(peer, 42L);

    // Then: Returns null
    assertThat(result, nullValue());
  }

  /**
   * Tests that getCachedMessageAtOffset returns the cached LogMessage when present.
   *
   * <p>Given: A ThinPeer with a ConsumerRecord inserted into the {@code lastRecordsRead} map at
   * offset 42 via reflection
   *
   * <p>When: The private getCachedMessageAtOffset(42L) is invoked via reflection
   *
   * <p>Then: Returns the LogMessage from the cached ConsumerRecord, with offset set to 42
   *
   * <p>Implementation note: Set {@code lastRecordsRead} via reflection to a Map containing a
   * ConsumerRecord at key 42L. The ConsumerRecord can be constructed with {@code new
   * ConsumerRecord<>("topic", 0, 42L, "key", logMessage)}. Use reflection to invoke the private
   * method.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void getCachedMessageAtOffset_returnsCachedMessage() throws Exception {
    // Given: A ThinPeer with lastRecordsRead containing a ConsumerRecord at offset 42
    ThinPeer peer = new ThinPeer();
    Message wrappedMessage = new Message();
    LogMessage<Message> logMessage =
        new LogMessage<>("test-topic", 42L, new HashMap<>(), wrappedMessage);

    ConsumerRecord<String, LogMessage<?>> record =
        new ConsumerRecord<>("test-topic", 0, 42L, "key", logMessage);

    Field lastRecordsField = ThinPeer.class.getDeclaredField("lastRecordsRead");
    lastRecordsField.setAccessible(true);
    Map<Long, ConsumerRecord<String, LogMessage<?>>> cache =
        (Map<Long, ConsumerRecord<String, LogMessage<?>>>) lastRecordsField.get(peer);
    cache.put(42L, record);

    // When: getCachedMessageAtOffset(42L) is called via reflection
    Method method = ThinPeer.class.getDeclaredMethod("getCachedMessageAtOffset", Long.class);
    method.setAccessible(true);
    Object result = method.invoke(peer, 42L);

    // Then: Returns the LogMessage from the ConsumerRecord, with offset set to 42
    assertThat(result, notNullValue());
    LogMessage<?> returned = (LogMessage<?>) result;
    assertThat(returned.getOffset(), is(42L));
    assertThat(returned, sameInstance(logMessage));
  }

  /**
   * Tests that pullReceivedMessages returns accumulated messages and clears the internal list.
   *
   * <p>Given: A ThinPeer with messages added to the {@code receivedMessages} list via reflection
   *
   * <p>When: pullReceivedMessages() is called
   *
   * <p>Then: Returns a list containing the added messages, and a subsequent call returns an empty
   * list
   *
   * <p>Implementation note: Access the {@code receivedMessages} field via reflection, add Message
   * instances to it, then call pullReceivedMessages(). Verify the returned list has the expected
   * messages. Call pullReceivedMessages() again and verify it returns an empty list.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void pullReceivedMessages_returnsAndClears() throws Exception {
    // Given: A ThinPeer with messages added to receivedMessages (via reflection)
    ThinPeer peer = new ThinPeer();
    Message msg1 = new Message();
    Message msg2 = new Message();

    Field receivedField = ThinPeer.class.getDeclaredField("receivedMessages");
    receivedField.setAccessible(true);
    List<Message> receivedMessages = (List<Message>) receivedField.get(peer);
    receivedMessages.add(msg1);
    receivedMessages.add(msg2);

    // When: pullReceivedMessages() is called
    List<Message> pulled = peer.pullReceivedMessages();

    // Then: Returns the accumulated messages
    assertThat(pulled, hasSize(2));
    assertThat(pulled.get(0), sameInstance(msg1));
    assertThat(pulled.get(1), sameInstance(msg2));

    // And a second call returns an empty list
    List<Message> pulledAgain = peer.pullReceivedMessages();
    assertThat(pulledAgain, empty());
  }

  // ==================== Message listener tests ====================

  /**
   * Tests that addMessageListener and removeMessageListener update the listener set.
   *
   * <p>Given: A ThinPeer and an IncomingMessageListener implementation
   *
   * <p>When: addMessageListener(listener) is called, then removeMessageListener(listener) is called
   *
   * <p>Then: After add, the {@code messageListeners} set contains the listener. After remove, the
   * set no longer contains the listener.
   *
   * <p>Implementation note: Access the {@code messageListeners} field via reflection to verify set
   * contents. Create the listener as a no-op lambda implementing {@link IncomingMessageListener}.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void addAndRemoveMessageListener_updatesListeners() throws Exception {
    // Given: A ThinPeer and an IncomingMessageListener lambda
    ThinPeer peer = new ThinPeer();
    IncomingMessageListener listener = message -> {};

    Field listenersField = ThinPeer.class.getDeclaredField("messageListeners");
    listenersField.setAccessible(true);
    Set<IncomingMessageListener> listeners =
        (Set<IncomingMessageListener>) listenersField.get(peer);

    // When: addMessageListener(listener) is called
    peer.addMessageListener(listener);

    // Then: messageListeners set contains the listener
    assertThat(listeners.size(), is(1));
    assertTrue(listeners.contains(listener));

    // When: removeMessageListener(listener) is called
    peer.removeMessageListener(listener);

    // Then: messageListeners set no longer contains the listener
    assertThat(listeners.size(), is(0));
    assertFalse(listeners.contains(listener));
  }

  // ==================== Builder-configured getter tests ====================

  /**
   * Tests that getPollingDuration returns the value set via the builder.
   *
   * <p>Given: A ThinPeer configured with {@code withPollingDuration(500)}
   *
   * <p>When: getPollingDuration() is called
   *
   * <p>Then: Returns a Duration of 500 milliseconds
   *
   * <p>Implementation note: The {@code withPollingDuration(long)} method sets the internal {@code
   * pollingDuration} field to {@code Duration.ofMillis(millis)}. Verify using {@code
   * assertThat(peer.getPollingDuration(), is(Duration.ofMillis(500)))}.
   */
  @Test
  public void getPollingDuration_returnsSetValue() {
    // Given: A ThinPeer configured with withPollingDuration(500)
    ThinPeer peer = new ThinPeer().withPollingDuration(500);

    // When/Then: getPollingDuration() returns Duration.ofMillis(500)
    assertThat(peer.getPollingDuration(), is(Duration.ofMillis(500)));
  }

  /**
   * Tests that getPalDirectoryUrl returns the value set via the builder.
   *
   * <p>Given: A ThinPeer configured with {@code withDirectoryUrl("http://localhost:2379")}
   *
   * <p>When: getPalDirectoryUrl() is called
   *
   * <p>Then: Returns "http://localhost:2379"
   */
  @Test
  public void getPalDirectoryUrl_returnsSetValue() {
    // Given: A ThinPeer configured with withDirectoryUrl("http://localhost:2379")
    ThinPeer peer = new ThinPeer().withDirectoryUrl("http://localhost:2379");

    // When/Then: getPalDirectoryUrl() returns "http://localhost:2379"
    assertThat(peer.getPalDirectoryUrl(), is("http://localhost:2379"));
  }
}
