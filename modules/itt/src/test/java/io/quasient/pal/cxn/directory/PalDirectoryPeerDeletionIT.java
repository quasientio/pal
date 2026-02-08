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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.util.Arrays;
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
  private Set<String> createdLogs;

  @Before
  public void setUp() {
    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    createdPeers = new HashSet<>();
    createdLogs = new HashSet<>();
  }

  @After
  public void tearDown() throws Exception {
    // Clean up any logs we created that weren't deleted by the test
    for (String log : createdLogs) {
      try {
        palDirectory.deleteLog(log);
        logger.debug("Cleaned up log: {}", log);
      } catch (Exception e) {
        logger.warn("Failed to clean up log {}: {}", log, e.getMessage());
      }
    }
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

  /**
   * Creates an intercept request for a peer.
   *
   * @param peerUuid the UUID of the peer
   * @param callbackMethod the callback method name
   * @throws Exception if intercept creation fails
   */
  private void createInterceptForPeer(UUID peerUuid, String callbackMethod) throws Exception {
    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerUuid,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            callbackMethod,
            new InterceptableMethodCall("println", Arrays.asList("java.lang.String")));
    palDirectory.createIntercept(req);
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
  public void purgePeersExcept_withInterceptsOnPeers_cleansUpIntercepts() throws Exception {
    // Given: 3 peers, each with an intercept registered
    PeerInfo peer1 = createTestPeer("intercept-purge-peer1");
    PeerInfo peer2 = createTestPeer("intercept-purge-peer2");
    PeerInfo peer3 = createTestPeer("intercept-purge-peer3");

    createInterceptForPeer(peer1.getUuid(), "method1");
    createInterceptForPeer(peer2.getUuid(), "method2");
    createInterceptForPeer(peer3.getUuid(), "method3");

    // Verify all intercepts exist
    assertEquals(1, palDirectory.listInterceptsForPeer(peer1.getUuid()).size());
    assertEquals(1, palDirectory.listInterceptsForPeer(peer2.getUuid()).size());
    assertEquals(1, palDirectory.listInterceptsForPeer(peer3.getUuid()).size());

    // When: purgePeersExcept({peer2.uuid})
    Set<UUID> exclusions = new HashSet<>();
    exclusions.add(peer2.getUuid());
    long deleted = palDirectory.purgePeersExcept(exclusions);

    // Then: peer1 and peer3 are deleted, peer2 remains
    assertTrue("Should have deleted at least 2 peers", deleted >= 2);
    assertFalse("peer1 should be deleted", palDirectory.peerExists(peer1.getUuid()));
    assertTrue("peer2 should remain", palDirectory.peerExists(peer2.getUuid()));
    assertFalse("peer3 should be deleted", palDirectory.peerExists(peer3.getUuid()));

    // Intercepts on deleted peers are cleaned up
    assertTrue(palDirectory.listInterceptsForPeer(peer1.getUuid()).isEmpty());
    assertTrue(palDirectory.listInterceptsForPeer(peer3.getUuid()).isEmpty());

    // Intercepts on excluded peer remain
    assertEquals(1, palDirectory.listInterceptsForPeer(peer2.getUuid()).size());

    // Update tracking
    createdPeers.remove(peer1.getUuid());
    createdPeers.remove(peer3.getUuid());
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
  public void purgePeersExcept_excludeSubset_deletesRemainder() throws Exception {
    // Given: 4 peers in directory
    PeerInfo peer1 = createTestPeer("subset-peer1");
    PeerInfo peer2 = createTestPeer("subset-peer2");
    PeerInfo peer3 = createTestPeer("subset-peer3");
    PeerInfo peer4 = createTestPeer("subset-peer4");

    // When: purgePeersExcept({peer1.uuid, peer3.uuid})
    Set<UUID> exclusions = new HashSet<>();
    exclusions.add(peer1.getUuid());
    exclusions.add(peer3.getUuid());
    long deleted = palDirectory.purgePeersExcept(exclusions);

    // Then: peer2 and peer4 deleted; peer1 and peer3 remain
    assertTrue("Should have deleted at least 2 peers", deleted >= 2);
    assertTrue("peer1 should remain", palDirectory.peerExists(peer1.getUuid()));
    assertFalse("peer2 should be deleted", palDirectory.peerExists(peer2.getUuid()));
    assertTrue("peer3 should remain", palDirectory.peerExists(peer3.getUuid()));
    assertFalse("peer4 should be deleted", palDirectory.peerExists(peer4.getUuid()));

    // Update tracking
    createdPeers.remove(peer2.getUuid());
    createdPeers.remove(peer4.getUuid());
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
  public void purgeLogsExcept_withExclusions_excludedLogsRemain() throws Exception {
    // Given: 3 logs
    LogInfo log1 = palDirectory.createAutoLog("purge-log-test", getKafkaServers());
    createdLogs.add(log1.getName());
    LogInfo log2 = palDirectory.createAutoLog("purge-log-test", getKafkaServers());
    createdLogs.add(log2.getName());
    LogInfo log3 = palDirectory.createAutoLog("purge-log-test", getKafkaServers());
    createdLogs.add(log3.getName());

    // When: purgeLogsExcept({log2.uuid})
    Set<UUID> exclusions = new HashSet<>();
    exclusions.add(log2.getUuid());
    long deleted = palDirectory.purgeLogsExcept(exclusions);

    // Then: log2 remains; log1 and log3 are deleted
    assertEquals(2, deleted);
    assertFalse("log1 should be deleted", palDirectory.logExists(log1.getUuid()));
    assertTrue("log2 should remain", palDirectory.logExists(log2.getUuid()));
    assertFalse("log3 should be deleted", palDirectory.logExists(log3.getUuid()));
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
  public void purgeLogsExcept_noLogs_returnsZero() throws Exception {
    // Given: Empty directory (no logs)
    assertTrue("Directory should have no logs", palDirectory.listAllLogs().isEmpty());

    // When: purgeLogsExcept(null)
    long deleted = palDirectory.purgeLogsExcept(null);

    // Then: Returns 0
    assertEquals(0, deleted);
  }
}
