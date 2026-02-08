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
import static org.junit.Assert.fail;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
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

  // ==========================================================================
  // Test Specifications for Issue #636
  // Awaiting implementation in #638
  // ==========================================================================

  /**
   * Tests that purgePeersExcept cleans up intercepts on deleted peers.
   *
   * <p>Specification #15 from Issue #636:
   *
   * <ul>
   *   <li>Given: Multiple peers with intercepts registered
   *   <li>When: purgePeersExcept(excludeSet) is called, excluding one peer
   *   <li>Then: Intercepts on deleted peers are cleaned up; intercepts on excluded peer remain
   * </ul>
   */
  @Test
  @Ignore("Awaiting implementation in #638")
  public void purgePeersExcept_withInterceptsOnPeers_cleansUpIntercepts() throws Exception {
    // Given: 3 peers, each with intercepts registered
    // When: purgePeersExcept({peer2.uuid}) - exclude peer2 only
    // Then: Intercepts on peer1 and peer3 are cleaned up,
    //       intercepts on peer2 remain intact

    // TODO(#638): Implement test logic
    // 1. Create 3 peers
    // 2. Create intercept(s) for each peer via palDirectory.createIntercept()
    // 3. Verify all intercepts exist via listInterceptsForPeer() / listAllIntercepts()
    // 4. Call purgePeersExcept with exclusion set containing only peer2
    // 5. Verify peer2 still exists and its intercepts remain
    // 6. Verify peer1 and peer3 are deleted
    // 7. Verify intercepts for peer1 and peer3 are removed
    //    (listInterceptsForPeer returns empty for deleted peers)
    fail("Not yet implemented");
  }

  /**
   * Tests that purgePeersExcept with a subset exclusion only deletes non-excluded peers.
   *
   * <p>Specification #16 from Issue #636:
   *
   * <ul>
   *   <li>Given: Multiple peers in the directory
   *   <li>When: purgePeersExcept(excludeSubset) is called with a subset of peer UUIDs
   *   <li>Then: Only non-excluded peers are deleted; excluded peers remain
   * </ul>
   */
  @Test
  @Ignore("Awaiting implementation in #638")
  public void purgePeersExcept_excludeSubset_deletesRemainder() throws Exception {
    // Given: 4 peers in directory
    // When: purgePeersExcept({peer1.uuid, peer3.uuid})
    // Then: peer2 and peer4 deleted; peer1 and peer3 remain

    // TODO(#638): Implement test logic
    // 1. Create 4 peers
    // 2. Build exclusion set with peer1 and peer3 UUIDs
    // 3. Call purgePeersExcept(exclusions)
    // 4. Verify return value >= 2
    // 5. Verify peer1 and peer3 still exist
    // 6. Verify peer2 and peer4 are deleted
    fail("Not yet implemented");
  }

  /**
   * Tests that purgeLogsExcept with exclusions preserves excluded logs.
   *
   * <p>Specification #17 from Issue #636:
   *
   * <ul>
   *   <li>Given: Multiple logs in the directory
   *   <li>When: purgeLogsExcept(excludeSet) is called with a subset of log UUIDs
   *   <li>Then: Excluded logs survive the purge; all other logs are deleted
   * </ul>
   */
  @Test
  @Ignore("Awaiting implementation in #638")
  public void purgeLogsExcept_withExclusions_excludedLogsRemain() throws Exception {
    // Given: 3 logs, one of which is in the exclusion set
    // When: purgeLogsExcept({log2.uuid})
    // Then: log2 remains; log1 and log3 are deleted

    // TODO(#638): Implement test logic
    // 1. Create 3 logs via createAutoLog()
    // 2. Build exclusion set with log2 UUID
    // 3. Call purgeLogsExcept(exclusions)
    // 4. Verify return value == 2
    // 5. Verify log2 still exists via logExists(log2.uuid)
    // 6. Verify log1 and log3 are deleted
    fail("Not yet implemented");
  }

  /**
   * Tests that purgeLogsExcept returns zero when no logs exist.
   *
   * <p>Specification #18 from Issue #636:
   *
   * <ul>
   *   <li>Given: An empty directory with no logs
   *   <li>When: purgeLogsExcept(null) is called
   *   <li>Then: Returns 0
   * </ul>
   */
  @Test
  @Ignore("Awaiting implementation in #638")
  public void purgeLogsExcept_noLogs_returnsZero() throws Exception {
    // Given: Empty directory (no logs)
    // When: purgeLogsExcept(null)
    // Then: Returns 0

    // TODO(#638): Implement test logic
    // 1. Verify directory has no logs (listAllLogs is empty)
    // 2. Call purgeLogsExcept(null)
    // 3. Assert return value == 0
    fail("Not yet implemented");
  }
}
