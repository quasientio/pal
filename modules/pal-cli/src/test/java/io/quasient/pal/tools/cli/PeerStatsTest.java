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
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.tools.stats.Counters;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;

/**
 * Unit tests for {@link PeerStats}.
 *
 * <p>PeerStats is the peer-specific stats command extracted from {@code MessageStreamStats} to
 * follow the entity-operation pattern ({@code pal peer stats}). It handles socket-based peer
 * message statistics collection via ZMQ PUB/SUB streaming.
 */
public class PeerStatsTest {

  // ==================== runCommand() Tests ====================

  /**
   * Tests that a PeerStats instance configured with a peer UUID is properly set up.
   *
   * <p>Verifies that providing a positional peer UUID argument configures the PeerStats for
   * socket-based streaming. Actual socket connection requires a running peer, so this verifies the
   * construction path.
   */
  @Test
  public void runCommand_withPeerUuid_startsSocketStream() {
    // Given: positional peer UUID argument
    String peerUuid = UUID.randomUUID().toString();
    PeerStats stats = new PeerStats(peerUuid, null, null, null);

    // Then: instance is configured and counters are accessible
    assertNotNull(stats.getCounters());
    assertThat(stats.getCounters().getNumberOfMessages().get(), is(0L));
  }

  /**
   * Tests that a PeerStats instance configured with a peer address is properly set up.
   *
   * <p>Verifies that providing a positional peer address configures the PeerStats for socket-based
   * streaming.
   */
  @Test
  public void runCommand_withPeerAddress_startsSocketStream() {
    // Given: positional peer address argument
    PeerStats stats = new PeerStats("tcp://localhost:5555", null, null, null);

    // Then: instance is configured and counters are accessible
    assertNotNull(stats.getCounters());
    assertThat(stats.getCounters().getNumberOfMessages().get(), is(0L));
  }

  // ==================== updateCounters() Tests ====================

  /**
   * Tests that updateCounters increments the total message count.
   *
   * <p>Verifies that calling updateCounters with a valid message increments the
   * counters.getNumberOfMessages() value by 1.
   */
  @Test
  public void updateCounters_incrementsMessageCount() {
    // Given: PeerStats instance with a valid message
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    PeerStats stats = new PeerStats(peerId.toString(), null, null, null);

    ExecMessage execMessage = builder.buildEmptyConstructor(peerId, "java.lang.String");
    Message message = builder.wrap(execMessage);

    // When: updateCounters(message) called
    stats.updateCounters(message);

    // Then: counters.getNumberOfMessages() incremented by 1
    Counters counters = stats.getCounters();
    assertThat(counters.getNumberOfMessages().get(), is(1L));
  }

  /**
   * Tests that updateCounters tracks message types correctly.
   *
   * <p>Verifies that processing a message of a specific type results in the message type being
   * tracked in counters.getMessagesByType().
   */
  @Test
  public void updateCounters_tracksMessageTypes() {
    // Given: message of a specific type (e.g., EXEC_INSTANCE_METHOD)
    UUID peerId = UUID.randomUUID();
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    PeerStats stats = new PeerStats(peerId.toString(), null, null, null);

    ExecMessage execMessage =
        builder.buildInstanceMethod(
            peerId,
            "java.util.ArrayList",
            "add",
            ObjectRef.randomRef(),
            new String[] {"int"},
            new Object[] {1});
    Message message = builder.wrap(execMessage);

    // When: updateCounters(message) called
    stats.updateCounters(message);

    // Then: counters.getMessagesByType() contains the type entry with count 1
    Counters counters = stats.getCounters();
    assertNotNull(counters.getMessagesByType().get("EXEC_INSTANCE_METHOD"));
    assertThat(counters.getMessagesByType().get("EXEC_INSTANCE_METHOD").get(), is(1L));
  }

  // ==================== performSocketShutdown() Tests ====================

  /**
   * Tests that performSocketShutdown counts down the socket shutdown latch.
   *
   * <p>Verifies that calling performSocketShutdown() decrements the socketShutdownLatch count to 0.
   */
  @Test
  public void performSocketShutdown_countsDownLatch() {
    // Given: PeerStats instance with socketShutdownLatch count of 1
    PeerStats stats = new PeerStats(UUID.randomUUID().toString(), null, null, null);
    stats.socketShutdownLatch = new CountDownLatch(1);

    assertThat(stats.socketShutdownLatch.getCount(), is(1L));

    // When: performSocketShutdown() called
    stats.performSocketShutdown();

    // Then: socketShutdownLatch.getCount() returns 0
    assertThat(stats.socketShutdownLatch.getCount(), is(0L));
  }

  // ==================== validateInput() Tests ====================

  /**
   * Tests that validation fails when no peer identifier is provided.
   *
   * <p>Verifies that invoking the command without a positional peer UUID or address argument
   * results in an error.
   */
  @Test(expected = RuntimeException.class)
  public void validateInput_noPeer_throwsError() {
    // Given: no positional peer identifier argument (no UUID, no address)
    PeerStats stats = new PeerStats();

    // When: validateInput() called
    // Then: error is thrown indicating peer identifier is required
    stats.validateInput();
  }
}
