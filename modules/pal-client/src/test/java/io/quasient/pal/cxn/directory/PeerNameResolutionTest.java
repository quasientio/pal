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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.PeerInfo;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for peer-by-name resolution logic.
 *
 * <p>These tests verify the peer name resolution that {@code InterceptManager} will use when
 * resolving peer name strings (from YAML bundles) to {@link PeerInfo} instances. The resolution
 * delegates to {@link PalDirectory#listPeers()} and filters by name.
 *
 * <p>Since {@code PalDirectory} requires an etcd connection, these tests mock the underlying
 * directory to test the resolution logic in isolation.
 */
public class PeerNameResolutionTest {

  /**
   * Verifies that resolving a peer by name returns the correct {@code PeerInfo} when exactly one
   * peer matches.
   */
  @Test
  public void resolvePeer_findsMatchByName() throws Exception {
    // Given: A mock PalDirectory whose listPeers() returns 3 peers with distinct names
    UUID alphaUuid = UUID.randomUUID();
    UUID betaUuid = UUID.randomUUID();
    UUID gammaUuid = UUID.randomUUID();

    PeerInfo alpha = new PeerInfo(alphaUuid);
    alpha.setName("alpha");
    PeerInfo beta = new PeerInfo(betaUuid);
    beta.setName("beta");
    PeerInfo gamma = new PeerInfo(gammaUuid);
    gamma.setName("gamma");

    Set<PeerInfo> peers = new LinkedHashSet<>();
    peers.add(alpha);
    peers.add(beta);
    peers.add(gamma);

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listPeers()).thenReturn(peers);
    when(mockDir.getPeerByName("beta")).thenCallRealMethod();

    // When
    PeerInfo result = mockDir.getPeerByName("beta");

    // Then
    assertThat(result.getName(), is("beta"));
    assertThat(result.getUuid(), is(betaUuid));
  }

  /** Verifies that resolving a name that matches no peer returns null. */
  @Test
  public void resolvePeer_returnsNullForUnknownName() throws Exception {
    // Given: A mock PalDirectory whose listPeers() returns 3 peers (none named "unknown-peer")
    PeerInfo alpha = new PeerInfo(UUID.randomUUID());
    alpha.setName("alpha");
    PeerInfo beta = new PeerInfo(UUID.randomUUID());
    beta.setName("beta");
    PeerInfo gamma = new PeerInfo(UUID.randomUUID());
    gamma.setName("gamma");

    Set<PeerInfo> peers = new LinkedHashSet<>();
    peers.add(alpha);
    peers.add(beta);
    peers.add(gamma);

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listPeers()).thenReturn(peers);
    when(mockDir.getPeerByName("unknown-peer")).thenCallRealMethod();

    // When
    PeerInfo result = mockDir.getPeerByName("unknown-peer");

    // Then
    assertThat(result, is(nullValue()));
  }

  /**
   * Verifies that resolving a name that matches multiple peers throws an {@link
   * IllegalStateException} with a descriptive error message listing both peer UUIDs.
   *
   * <p>This is the Risk 1 mitigation: peer names are not unique-enforced in etcd, so the resolution
   * must fail explicitly when ambiguous.
   */
  @Test
  public void resolvePeer_throwsOnDuplicateNames() throws Exception {
    // Given: A mock PalDirectory whose listPeers() returns 2 peers with the same name
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();

    PeerInfo peer1 = new PeerInfo(uuid1);
    peer1.setName("duplicate-peer");
    PeerInfo peer2 = new PeerInfo(uuid2);
    peer2.setName("duplicate-peer");

    Set<PeerInfo> peers = new LinkedHashSet<>();
    peers.add(peer1);
    peers.add(peer2);

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listPeers()).thenReturn(peers);
    when(mockDir.getPeerByName("duplicate-peer")).thenCallRealMethod();

    // When / Then
    try {
      mockDir.getPeerByName("duplicate-peer");
      fail("Expected IllegalStateException for duplicate peer names");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), containsString(uuid1.toString()));
      assertThat(e.getMessage(), containsString(uuid2.toString()));
      assertThat(e.getMessage(), containsString("duplicate-peer"));
    }
  }

  /**
   * Verifies that when the name string is a valid UUID format, resolution attempts to match by UUID
   * directly rather than (or in addition to) matching by name.
   */
  @Test
  public void resolvePeer_acceptsUuidString() throws Exception {
    // Given: A mock PalDirectory whose listPeers() returns a peer with a known UUID
    UUID knownUuid = UUID.randomUUID();
    PeerInfo peer = new PeerInfo(knownUuid);
    peer.setName("some-peer");

    Set<PeerInfo> peers = new LinkedHashSet<>();
    peers.add(peer);

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listPeers()).thenReturn(peers);
    when(mockDir.getPeerByName(knownUuid.toString())).thenCallRealMethod();

    // When: Peer resolution is performed using the UUID's string representation as the name
    PeerInfo result = mockDir.getPeerByName(knownUuid.toString());

    // Then
    assertThat(result.getUuid(), is(knownUuid));
    assertThat(result.getName(), is("some-peer"));
  }
}
