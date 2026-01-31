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
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.common.directory.nodes.LogInfo;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.Test;
import org.zeromq.SocketType;

/**
 * Unit tests for {@link ThinPeer} guard and validation methods.
 *
 * <p>These tests verify that:
 *
 * <ul>
 *   <li>Guard methods correctly throw exceptions for uninitialized or closed peers
 *   <li>Builder methods correctly configure socket types and log information
 * </ul>
 *
 * <p>This test class supplements integration tests with focused unit test coverage.
 */
public class ThinPeerValidationTest {

  // ==================== assertInitializedAndActive guard tests ====================

  /**
   * Tests that operations requiring initialization throw IllegalStateException on uninitialized
   * peer.
   *
   * <p>Given: An uninitialized ThinPeer (init() not called)
   *
   * <p>When: An operation requiring initialization is called
   *
   * <p>Then: IllegalStateException with appropriate message is thrown
   */
  @Test
  public void assertInitializedAndActive_uninitialized_throwsIllegalStateException() {
    // Given: An uninitialized ThinPeer
    ThinPeer peer = new ThinPeer();

    // When/Then: Operations requiring initialization throw IllegalStateException
    IllegalStateException ex1 =
        assertThrows(IllegalStateException.class, () -> peer.sendPing(Duration.ofMillis(100)));
    assertThat(ex1.getMessage(), is("ThinPeer is not initialized. Did you call init()?"));

    IllegalStateException ex2 =
        assertThrows(IllegalStateException.class, () -> peer.getMessageAtOffset(0L));
    assertThat(ex2.getMessage(), is("ThinPeer is not initialized. Did you call init()?"));

    IllegalStateException ex3 = assertThrows(IllegalStateException.class, peer::close);
    assertThat(ex3.getMessage(), is("ThinPeer is not initialized. Did you call init()?"));
  }

  /**
   * Tests that operations throw IllegalStateException after peer is closed.
   *
   * <p>Given: A ThinPeer that was initialized then closed
   *
   * <p>When: An operation requiring active state is called
   *
   * <p>Then: IllegalStateException with appropriate message is thrown
   */
  @Test
  public void assertInitializedAndActive_closed_throwsIllegalStateException() throws Exception {
    // Given: ThinPeer that was initialized then closed (simulated via reflection)
    ThinPeer peer = new ThinPeer();

    // Use reflection to set initialized=true and closed=true to simulate closed state
    Field initializedField = ThinPeer.class.getDeclaredField("initialized");
    initializedField.setAccessible(true);
    initializedField.setBoolean(peer, true);

    Field closedField = ThinPeer.class.getDeclaredField("closed");
    closedField.setAccessible(true);
    closedField.setBoolean(peer, true);

    // When/Then: Operations requiring active state throw IllegalStateException
    IllegalStateException ex1 =
        assertThrows(IllegalStateException.class, () -> peer.sendPing(Duration.ofMillis(100)));
    assertThat(ex1.getMessage(), is("ThinPeer is closed. Cannot perform operations."));

    IllegalStateException ex2 =
        assertThrows(IllegalStateException.class, () -> peer.getMessageAtOffset(0L));
    assertThat(ex2.getMessage(), is("ThinPeer is closed. Cannot perform operations."));
  }

  // ==================== sendDeleteSessionCommand tests ====================

  /**
   * Tests that sendDeleteSessionCommand throws when not connected to a peer.
   *
   * <p>Given: An initialized ThinPeer not connected to any peer (currentPeer is null)
   *
   * <p>When: sendDeleteSessionCommand() is called
   *
   * <p>Then: IllegalStateException is thrown indicating not connected to a peer
   *
   * <p>Note: The method requires an active peer connection. When currentPeer is null, the method
   * throws IllegalStateException with message "Not connected to a peer".
   */
  @Test
  public void sendDeleteSessionCommand_notConnected_noException() throws Exception {
    // Given: Initialized ThinPeer not connected to any peer (currentPeer is null)
    ThinPeer peer = new ThinPeer();

    // Use reflection to set initialized=true to bypass init() requirement
    Field initializedField = ThinPeer.class.getDeclaredField("initialized");
    initializedField.setAccessible(true);
    initializedField.setBoolean(peer, true);

    // When: sendDeleteSessionCommand() is called
    // Then: IllegalStateException is thrown (currentPeer is null)
    IllegalStateException ex =
        assertThrows(IllegalStateException.class, peer::sendDeleteSessionCommand);
    assertThat(ex.getMessage(), is("Not connected to a peer"));
  }

  // ==================== withZmqRpcAddress socket type configuration tests ====================

  /**
   * Tests that withZmqRpcAddress with different SocketTypes configures correctly.
   *
   * <p>Given: A ThinPeer builder
   *
   * <p>When: withZmqRpcAddress(addr, SocketType.ROUTER) is called
   *
   * <p>Then: Socket type is correctly configured (builder returns for chaining)
   *
   * <p>Note: inboundSocketType is a private field. This test verifies the builder pattern works
   * without exceptions for both ROUTER and REP socket types; actual socket type verification
   * requires integration testing or reflection.
   */
  @Test
  public void withZmqRpcAddress_differentSocketTypes_configuresCorrectly() throws Exception {
    // Given: ThinPeer builder
    String address = "tcp://localhost:5555";

    // When: withZmqRpcAddress(addr, SocketType.ROUTER) called
    ThinPeer peerWithRouter = new ThinPeer().withZmqRpcAddress(address, SocketType.ROUTER);

    // Then: Address is set correctly and builder pattern works
    assertThat(peerWithRouter.getZmqRpcAddress(), is(address));

    // Verify via reflection that socket type is ROUTER
    Field socketTypeField = ThinPeer.class.getDeclaredField("inboundSocketType");
    socketTypeField.setAccessible(true);
    assertThat(socketTypeField.get(peerWithRouter), is(SocketType.ROUTER));

    // When: withZmqRpcAddress(addr, SocketType.REP) called
    ThinPeer peerWithRep = new ThinPeer().withZmqRpcAddress(address, SocketType.REP);

    // Then: Socket type is REP
    assertThat(socketTypeField.get(peerWithRep), is(SocketType.REP));

    // When: withZmqRpcAddress(addr) called (default socket type)
    ThinPeer peerWithDefault = new ThinPeer().withZmqRpcAddress(address);

    // Then: Default socket type is REP
    assertThat(socketTypeField.get(peerWithDefault), is(SocketType.REP));
  }

  // ==================== withLog configuration tests ====================

  /**
   * Tests that withLog sets both inputLog and outputLog to the same LogInfo.
   *
   * <p>Given: A ThinPeer builder
   *
   * <p>When: withLog(logInfo) is called
   *
   * <p>Then: Both inputLog and outputLog are set to the same LogInfo
   */
  @Test
  public void withLog_setsInputAndOutput_bothConfigured() {
    // Given: ThinPeer builder and a LogInfo
    LogInfo logInfo = new LogInfo("test-topic");

    // When: withLog(logInfo) called
    ThinPeer peer = new ThinPeer().withLog(logInfo);

    // Then: Both inputLog and outputLog are set to same LogInfo instance
    assertThat(peer.getInputLog(), is(sameInstance(logInfo)));
    assertThat(peer.getOutputLog(), is(sameInstance(logInfo)));
    assertThat(peer.getInputLog().getName(), is("test-topic"));
    assertThat(peer.getOutputLog().getName(), is("test-topic"));
  }
}
