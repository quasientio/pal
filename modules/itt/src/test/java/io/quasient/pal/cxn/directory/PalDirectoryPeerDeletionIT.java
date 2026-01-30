/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.directory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for PalDirectory peer deletion methods.
 *
 * <p>These tests are in a standalone IT (not part of a test suite) because they call methods like
 * {@code deletePeers()} and {@code purgePeersExcept()} which delete peers globally. Running these
 * in a suite with shared peers would interfere with other tests.
 *
 * <p>This IT creates its own peers, tests deletion, and cleans up. It does not rely on any shared
 * peer infrastructure.
 */
public class PalDirectoryPeerDeletionIT extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(PalDirectoryPeerDeletionIT.class);

  private PalDirectory palDirectory;
  private Set<UUID> createdPeers;

  @Before
  public void setUp() {
    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    createdPeers = new HashSet<>();
  }

  @After
  public void tearDown() throws Exception {
    // Clean up any peers we created that weren't deleted by the test
    for (UUID peerUuid : createdPeers) {
      try {
        if (palDirectory.peerExists(peerUuid)) {
          palDirectory.deletePeer(peerUuid);
          logger.debug("Cleaned up peer: {}", peerUuid);
        }
      } catch (Exception e) {
        logger.warn("Failed to clean up peer {}: {}", peerUuid, e.getMessage());
      }
    }
    palDirectory.close();
  }

  /**
   * Creates a test peer with a random UUID.
   *
   * @param name the peer name
   * @return the created PeerInfo
   * @throws Exception if peer creation fails
   */
  private PeerInfo createTestPeer(String name) throws Exception {
    PeerInfo peer = new PeerInfo(UUID.randomUUID(), name);
    peer.setZmqRpcAddress("tcp://127.0.0.1:" + (5670 + createdPeers.size()));
    palDirectory.createPeer(peer);
    createdPeers.add(peer.getUuid());
    return peer;
  }

  @Test
  public void deletePeers_withExistingPeers_deletesAll() throws Exception {
    // Create some peers
    PeerInfo peer1 = createTestPeer("deletion-test-peer1");
    PeerInfo peer2 = createTestPeer("deletion-test-peer2");
    PeerInfo peer3 = createTestPeer("deletion-test-peer3");

    // Verify peers exist
    assertTrue(palDirectory.peerExists(peer1.getUuid()));
    assertTrue(palDirectory.peerExists(peer2.getUuid()));
    assertTrue(palDirectory.peerExists(peer3.getUuid()));

    // Delete all peers
    long deleted = palDirectory.deletePeers();

    // Verify deletion
    assertTrue("Should have deleted at least 3 peers", deleted >= 3);
    assertFalse(palDirectory.peerExists(peer1.getUuid()));
    assertFalse(palDirectory.peerExists(peer2.getUuid()));
    assertFalse(palDirectory.peerExists(peer3.getUuid()));

    // Clear tracking since peers are deleted
    createdPeers.clear();
  }

  @Test
  public void purgePeersExcept_withExclusions_excludedPeersRemain() throws Exception {
    // Create multiple peers
    PeerInfo peer1 = createTestPeer("purge-test-peer1");
    PeerInfo peer2 = createTestPeer("purge-test-peer2");
    PeerInfo peer3 = createTestPeer("purge-test-peer3");

    // Verify all peers exist
    assertTrue(palDirectory.peerExists(peer1.getUuid()));
    assertTrue(palDirectory.peerExists(peer2.getUuid()));
    assertTrue(palDirectory.peerExists(peer3.getUuid()));

    // Purge all except peer2
    Set<UUID> exclusions = new HashSet<>();
    exclusions.add(peer2.getUuid());
    long deleted = palDirectory.purgePeersExcept(exclusions);

    // Verify at least 2 were deleted (peer1 and peer3)
    assertTrue("Should have deleted at least 2 peers", deleted >= 2);

    // Verify peer2 remains and others are deleted
    assertTrue("peer2 should remain", palDirectory.peerExists(peer2.getUuid()));
    assertFalse("peer1 should be deleted", palDirectory.peerExists(peer1.getUuid()));
    assertFalse("peer3 should be deleted", palDirectory.peerExists(peer3.getUuid()));

    // Update tracking
    createdPeers.remove(peer1.getUuid());
    createdPeers.remove(peer3.getUuid());
  }

  @Test
  public void purgePeersExcept_emptyExclusions_deletesAll() throws Exception {
    // Create some peers
    PeerInfo peer1 = createTestPeer("purge-empty-test-peer1");
    PeerInfo peer2 = createTestPeer("purge-empty-test-peer2");

    // Verify peers exist
    assertTrue(palDirectory.peerExists(peer1.getUuid()));
    assertTrue(palDirectory.peerExists(peer2.getUuid()));

    // Purge with empty exclusions - should delete all
    long deleted = palDirectory.purgePeersExcept(new HashSet<>());

    // Verify all deleted
    assertTrue("Should have deleted at least 2 peers", deleted >= 2);
    assertFalse(palDirectory.peerExists(peer1.getUuid()));
    assertFalse(palDirectory.peerExists(peer2.getUuid()));

    // Clear tracking since peers are deleted
    createdPeers.clear();
  }

  @Test
  public void purgePeersExcept_nullExclusions_deletesAll() throws Exception {
    // Create some peers
    PeerInfo peer1 = createTestPeer("purge-null-test-peer1");
    PeerInfo peer2 = createTestPeer("purge-null-test-peer2");

    // Verify peers exist
    assertTrue(palDirectory.peerExists(peer1.getUuid()));
    assertTrue(palDirectory.peerExists(peer2.getUuid()));

    // Purge with null exclusions - should delete all (fast path)
    long deleted = palDirectory.purgePeersExcept(null);

    // Verify all deleted
    assertTrue("Should have deleted at least 2 peers", deleted >= 2);
    assertFalse(palDirectory.peerExists(peer1.getUuid()));
    assertFalse(palDirectory.peerExists(peer2.getUuid()));

    // Clear tracking since peers are deleted
    createdPeers.clear();
  }
}
