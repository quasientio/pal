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
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Unit tests for {@code PeerPrint}.
 *
 * <p>PeerPrint is the peer-specific print command extracted from {@code MessageStreamPrinter} to
 * follow the entity-operation pattern ({@code pal peer print}). It handles streaming messages from
 * a peer's ZMQ PUB socket, accepting either a peer UUID (resolved via the PAL directory) or a
 * direct {@code tcp://} address as a positional argument.
 *
 * @see AbstractPrintCommand
 * @see PeerPrint
 */
public class PeerPrintTest {

  // ==================== runCommand() Tests ====================

  /**
   * Tests that a positional peer UUID is parsed and stored correctly.
   *
   * <p>Verifies that providing a peer UUID as the positional argument causes it to be stored in the
   * peerIdentifier field.
   */
  @Test
  public void runCommand_withPositionalPeerUuid_streamsMessages() {
    // Given: positional peer UUID argument
    String peerUuid = "550e8400-e29b-41d4-a716-446655440000";
    PeerPrint cmd = new PeerPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs(peerUuid);

    // Then: peerIdentifier is set correctly
    assertThat(cmd.peerIdentifier, is(peerUuid));
  }

  /**
   * Tests that a positional peer address is parsed and stored correctly.
   *
   * <p>Verifies that providing a {@code tcp://host:port} address as the positional argument causes
   * it to be stored in the peerIdentifier field.
   */
  @Test
  public void runCommand_withPositionalPeerAddress_streamsMessages() {
    // Given: positional peer address argument
    PeerPrint cmd = new PeerPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("tcp://localhost:5555");

    // Then: peerIdentifier is set to the address
    assertThat(cmd.peerIdentifier, is("tcp://localhost:5555"));
  }

  /**
   * Tests that the --types filter is parsed correctly for peer streaming.
   *
   * <p>Verifies that when the {@code --types CONSTRUCTOR} filter is provided, the msgTypes field
   * contains the correct value.
   */
  @Test
  public void runCommand_withTypeFilter_filtersTypes() {
    // Given: positional peer identifier and --types CONSTRUCTOR filter
    PeerPrint cmd = new PeerPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("tcp://localhost:5555", "--types", "CONSTRUCTOR");

    // Then: msgTypes contains CONSTRUCTOR
    assertThat(cmd.peerIdentifier, is("tcp://localhost:5555"));
    assertThat(cmd.msgTypes, is(notNullValue()));
    assertThat(cmd.msgTypes, is(List.of("CONSTRUCTOR")));
  }

  /**
   * Tests that the -fp/--from-peer filter is parsed correctly for peer streaming.
   *
   * <p>Verifies that when the {@code -fp UUID} filter is provided, the fromPeer field is set
   * correctly.
   */
  @Test
  public void runCommand_withPeerFilter_filtersByPeer() {
    // Given: positional peer identifier and -fp <specific-UUID> filter
    String filterUuid = "550e8400-e29b-41d4-a716-446655440000";
    PeerPrint cmd = new PeerPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("tcp://localhost:5555", "-fp", filterUuid);

    // Then: fromPeer is set to the specific UUID
    assertThat(cmd.peerIdentifier, is("tcp://localhost:5555"));
    assertThat(cmd.fromPeer, is(filterUuid));
  }

  // ==================== validateInput() Tests ====================

  /**
   * Tests that validation fails when no peer identifier is provided.
   *
   * <p>Verifies that invoking the command without a positional peer identifier argument (neither
   * UUID nor address) results in a validation error.
   */
  @Test
  public void validateInput_peerIdentifierRequired() {
    // Given: no positional peer identifier argument (no UUID, no address)
    PeerPrint cmd = new PeerPrint();

    // When: validateInput() is called
    // Then: RuntimeException is thrown indicating peer identifier is required
    try {
      cmd.validateInput();
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertThat(e.getMessage().contains("Peer identifier is required"), is(true));
    }
  }

  // ==================== performShutdown() Tests ====================

  /**
   * Tests that performShutdown counts down the socket shutdown latch.
   *
   * <p>Verifies that calling performShutdown() decrements the shutdown latch count to 0, allowing
   * the main thread to unblock and complete.
   */
  @Test
  public void performShutdown_countsDownLatch() {
    // Given: PeerPrint instance with socketShutdownLatch count of 1
    PeerPrint cmd = new PeerPrint();
    cmd.socketShutdownLatch = new CountDownLatch(1);

    // Assert socketShutdownLatch.getCount() == 1 before call
    assertThat(cmd.socketShutdownLatch.getCount(), is(1L));

    // When: performShutdown() is called
    cmd.performShutdown();

    // Then: socketShutdownLatch.getCount() returns 0
    assertThat(cmd.socketShutdownLatch.getCount(), is(0L));
  }
}
