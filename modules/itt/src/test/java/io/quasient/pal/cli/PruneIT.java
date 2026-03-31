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
package io.quasient.pal.cli;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code pal peer prune} command.
 *
 * <p>Tests that dead peers (those with no active lease) are removed from the directory while alive
 * peers are left untouched.
 *
 * <p>Dead peers are created directly via {@link PalDirectory#createPeer(PeerInfo)} without
 * attaching a lease, simulating the state left behind after a peer process crashes and its lease
 * expires.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class PruneIT extends AbstractCliIT {

  /** PalDirectory instance for creating stale peer entries directly. */
  private PalDirectory palDirectory;

  /** UUIDs of peers created directly (without lease) that need cleanup. */
  private final Set<UUID> directlyCreatedPeers = new HashSet<>();

  /** A peer process handle for tests that launch live peers, or null if none launched. */
  private PeerProcess peerProcess;

  /** Initializes test state and PalDirectory before each test method. */
  @Before
  public void setUp() {
    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    peerProcess = null;
  }

  /**
   * Tears down test state after each test method.
   *
   * <p>Cleans up any directly-created peers that were not pruned by the test, and stops any
   * launched peer processes.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
    for (UUID peerUuid : directlyCreatedPeers) {
      try {
        if (palDirectory.peerExists(peerUuid)) {
          palDirectory.deletePeer(peerUuid);
        }
      } catch (Exception e) {
        // best-effort cleanup
      }
    }
    directlyCreatedPeers.clear();
    palDirectory.close();
  }

  /**
   * Creates a dead peer entry directly in etcd without a lease.
   *
   * <p>This simulates the state left behind after a peer crashes and its lease expires: the {@code
   * /info} and {@code /by-name} keys persist, but the {@code /state} key (which was leased) has
   * been deleted by etcd.
   *
   * @param name the peer name
   * @return the created PeerInfo
   * @throws Exception if peer creation fails
   */
  private PeerInfo createDeadPeer(String name) throws Exception {
    PeerInfo peer = new PeerInfo(UUID.randomUUID(), name);
    palDirectory.createPeer(peer);
    directlyCreatedPeers.add(peer.getUuid());
    return peer;
  }

  /**
   * Tests that {@code pal peer prune} removes dead peers from the directory.
   *
   * <p>Creates two dead peer entries (no lease), runs prune, and verifies they no longer appear in
   * the peer listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrune_removesDeadPeers() throws Exception {
    String palDir = getPalDirectoryUrl();
    String peerName1 = "prune-dead-" + generateId() + "-a";
    String peerName2 = "prune-dead-" + generateId() + "-b";

    createDeadPeer(peerName1);
    createDeadPeer(peerName2);

    // Prune dead peers
    CliProcessResult pruneResult = runPeerPrune("-d", palDir);
    assertThat("Expected exit code 0 for peer prune", pruneResult.exitCode(), is(0));
    assertThat(pruneResult.stdout(), containsString("Pruned"));

    // Verify neither peer appears in listing
    CliProcessResult lsResult = runPeerLs("-d", palDir);
    assertThat(
        "First peer should not appear after prune",
        lsResult.stdout(),
        not(containsString(peerName1)));
    assertThat(
        "Second peer should not appear after prune",
        lsResult.stdout(),
        not(containsString(peerName2)));
  }

  /**
   * Tests that {@code pal peer prune} succeeds on an empty directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrune_emptyDirectory_succeeds() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult pruneResult = runPeerPrune("-d", palDir);
    assertThat("Expected exit code 0 for prune on empty directory", pruneResult.exitCode(), is(0));
    assertThat(pruneResult.stdout(), containsString("No dead peers found"));
  }

  /**
   * Tests that {@code pal peer prune} leaves alive peers untouched.
   *
   * <p>Launches a live peer (which has an active lease) and creates a dead peer entry (no lease).
   * After pruning, the alive peer should still be listed and the dead peer should be gone.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrune_leavesAlivePeersUntouched() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String aliveName = "prune-alive-" + generateId();
    String deadName = "prune-dead-" + generateId();
    UUID aliveId = UUID.randomUUID();

    // Launch a live peer (has active lease)
    peerProcess =
        launchPeer(
            aliveId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            aliveName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // Create a dead peer entry (no lease)
    createDeadPeer(deadName);

    // Prune
    CliProcessResult pruneResult = runPeerPrune("-d", palDir);
    assertThat("Expected exit code 0 for peer prune", pruneResult.exitCode(), is(0));

    // Verify alive peer is still listed, dead peer is gone
    CliProcessResult lsResult = runPeerLs("-d", palDir);
    assertThat(
        "Alive peer should still appear after prune", lsResult.stdout(), containsString(aliveName));
    assertThat(
        "Dead peer should not appear after prune",
        lsResult.stdout(),
        not(containsString(deadName)));

    // Cleanup
    stopPeer(peerProcess);
    peerProcess = null;
  }
}
