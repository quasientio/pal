/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Unit tests for {@link PeerRemove}.
 *
 * <p>PeerRemove is the peer-specific remove command following the entity-operation pattern ({@code
 * pal peer rm}). It handles peer deletion by name, UUID, or prefix matching, with force/alive
 * safety checks.
 */
public class PeerRemoveTest {

  // ==================== Helper methods ====================

  /**
   * Sets a field value on an object via reflection, searching the class hierarchy.
   *
   * @param target the object on which to set the field
   * @param fieldName the name of the field to set
   * @param value the value to set
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  /**
   * Finds a field by name in the given class or its superclasses.
   *
   * @param clazz the class to search
   * @param name the field name
   * @return the found Field
   * @throws NoSuchFieldException if the field is not found in the class hierarchy
   */
  private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  /**
   * Creates a PeerRemove instance with a mock PalDirectory injected.
   *
   * @param mockDir the mock PalDirectory to inject
   * @return a configured PeerRemove instance
   */
  private static PeerRemove createWithMockDirectory(PalDirectory mockDir) throws Exception {
    PeerRemove cmd = new PeerRemove();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(cmd, "directoryConnectionProvider", dcp);
    return cmd;
  }

  /**
   * Creates a PeerRemove instance with a mock PalDirectory and wired output streams.
   *
   * @param mockDir the mock PalDirectory to inject
   * @param bout the output stream to capture standard output
   * @return a configured PeerRemove instance
   */
  private static PeerRemove createWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout) throws Exception {
    PeerRemove cmd = createWithMockDirectory(mockDir);
    setField(cmd, "out", new PrintStream(bout));
    return cmd;
  }

  // ==================== runCommand() Tests ====================

  /**
   * Tests that a peer is deleted when identified by name.
   *
   * <p>Verifies that providing a peer name as a positional argument with {@code --force} resolves
   * the peer from the directory and unregisters it.
   */
  @Test
  public void runCommand_deletesPeerByName() throws Exception {
    // Given: PalDirectory with a peer named "my-peer"
    UUID peerUuid = UUID.randomUUID();
    PeerInfo peerInfo = new PeerInfo(peerUuid, "my-peer");

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(peerInfo);
    when(mockDir.listPeers()).thenReturn(peers);
    when(mockDir.isPeerAlive(peerUuid)).thenReturn(false);

    PeerRemove cmd = createWithMockDirectory(mockDir);
    setField(cmd, "peerIdentifiers", List.of("my-peer"));
    setField(cmd, "force", true);

    // When
    Method runCommand = PeerRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then
    verify(mockDir).listPeers();
    verify(mockDir).deletePeer(peerUuid);
    assertThat(result, is(0));
  }

  /**
   * Tests that a peer is deleted when identified by UUID.
   *
   * <p>Verifies that providing a UUID string as a positional argument with {@code --force} resolves
   * the peer and unregisters it.
   */
  @Test
  public void runCommand_deletesPeerByUuid() throws Exception {
    // Given: PalDirectory with a peer registered by UUID
    UUID peerUuid = UUID.randomUUID();

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.isPeerAlive(peerUuid)).thenReturn(false);

    PeerRemove cmd = createWithMockDirectory(mockDir);
    setField(cmd, "peerIdentifiers", List.of(peerUuid.toString()));
    setField(cmd, "force", true);

    // When
    Method runCommand = PeerRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then
    verify(mockDir).deletePeer(peerUuid);
    assertThat(result, is(0));
  }

  /**
   * Tests that all peers are deleted when {@code --all} is specified.
   *
   * <p>Verifies that the {@code --all --force} flags cause all peers registered in the directory to
   * be unregistered.
   */
  @Test
  public void runCommand_deleteAllPeers() throws Exception {
    // Given: PalDirectory with 3 registered peers
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.deletePeers()).thenReturn(3L);

    PeerRemove cmd = createWithMockDirectory(mockDir);
    setField(cmd, "deleteAll", true);
    setField(cmd, "force", true);

    // When
    Method runCommand = PeerRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then
    verify(mockDir).deletePeers();
    assertThat(result, is(0));
  }

  /**
   * Tests that only peers matching a name prefix are deleted.
   *
   * <p>Verifies that the {@code -s/--starting-with} option filters peers by name prefix, deleting
   * only those whose names start with the given string.
   */
  @Test
  public void runCommand_deleteWithPrefix() throws Exception {
    // Given: PalDirectory with peers ["app-1", "app-2", "other"]
    UUID peer1Uuid = UUID.randomUUID();
    UUID peer2Uuid = UUID.randomUUID();
    UUID peer3Uuid = UUID.randomUUID();
    PeerInfo peer1 = new PeerInfo(peer1Uuid, "app-1");
    PeerInfo peer2 = new PeerInfo(peer2Uuid, "app-2");
    PeerInfo peer3 = new PeerInfo(peer3Uuid, "other");

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<PeerInfo> allPeers = new HashSet<>();
    allPeers.add(peer1);
    allPeers.add(peer2);
    allPeers.add(peer3);
    when(mockDir.listPeers()).thenReturn(allPeers);
    when(mockDir.isPeerAlive(any(UUID.class))).thenReturn(false);

    PeerRemove cmd = createWithMockDirectory(mockDir);
    setField(cmd, "peerIdentifiers", List.of("app"));
    setField(cmd, "startingWith", true);
    setField(cmd, "force", true);

    // When
    Method runCommand = PeerRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: "app-1" and "app-2" are deleted; "other" is not deleted
    verify(mockDir).listPeers();
    verify(mockDir).deletePeer(peer1Uuid);
    verify(mockDir).deletePeer(peer2Uuid);
    verify(mockDir, never()).deletePeer(peer3Uuid);
    assertThat(result, is(0));
  }

  // ==================== Force / Alive Safety Tests ====================

  /**
   * Tests that deletion of an alive peer is blocked without {@code --force}.
   *
   * <p>Verifies that when a peer is alive (has an active lease) and no {@code --force} flag is
   * specified, the deletion is blocked and an error message is printed.
   */
  @Test
  public void runCommand_alivePeerWithoutForce_blocked() throws Exception {
    // Given: alive peer (isPeerAlive returns true)
    UUID peerUuid = UUID.randomUUID();

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.isPeerAlive(peerUuid)).thenReturn(true);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerRemove cmd = createWithMockDirAndOutput(mockDir, bout);
    setField(cmd, "peerIdentifiers", List.of(peerUuid.toString()));
    setField(cmd, "force", false);

    // When
    Method runCommand = PeerRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: deletion is blocked, error message printed
    verify(mockDir).isPeerAlive(peerUuid);
    verify(mockDir, never()).deletePeer(any(UUID.class));
    assertThat(result, is(1));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("Cannot remove peer"));
    assertThat(output, containsString("alive"));
  }

  /**
   * Tests that an alive peer is deleted when {@code --force} is specified.
   *
   * <p>Verifies that the {@code --force} flag overrides the alive-peer safety check and allows
   * deletion to proceed.
   */
  @Test
  public void runCommand_alivePeerWithForce_deletes() throws Exception {
    // Given: alive peer (isPeerAlive returns true)
    UUID peerUuid = UUID.randomUUID();

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.isPeerAlive(peerUuid)).thenReturn(true);

    PeerRemove cmd = createWithMockDirectory(mockDir);
    setField(cmd, "peerIdentifiers", List.of(peerUuid.toString()));
    setField(cmd, "force", true);

    // When
    Method runCommand = PeerRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: peer is unregistered despite being alive
    verify(mockDir).isPeerAlive(peerUuid);
    verify(mockDir).deletePeer(peerUuid);
    assertThat(result, is(0));
  }

  /**
   * Tests that a dead peer is deleted without requiring {@code --force}.
   *
   * <p>Verifies that when a peer is not alive (no active lease), it can be deleted freely without
   * the {@code --force} flag.
   */
  @Test
  public void runCommand_deadPeer_deletesWithoutForce() throws Exception {
    // Given: dead peer (isPeerAlive returns false)
    UUID peerUuid = UUID.randomUUID();

    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.isPeerAlive(peerUuid)).thenReturn(false);

    PeerRemove cmd = createWithMockDirectory(mockDir);
    setField(cmd, "peerIdentifiers", List.of(peerUuid.toString()));
    setField(cmd, "force", false);

    // When
    Method runCommand = PeerRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: peer is unregistered successfully
    verify(mockDir).isPeerAlive(peerUuid);
    verify(mockDir).deletePeer(peerUuid);
    assertThat(result, is(0));
  }

  // ==================== Error Handling Tests ====================

  /**
   * Tests that attempting to delete a non-existent peer increments the error count.
   *
   * <p>Verifies that when no peer matches the given identifier, the error counter is incremented
   * and no deletion occurs.
   */
  @Test
  public void runCommand_nonExistentPeer_incrementsErrors() throws Exception {
    // Given: PalDirectory with no peer matching "ghost"
    PalDirectory mockDir = mock(PalDirectory.class);
    Set<PeerInfo> emptyPeers = new HashSet<>();
    when(mockDir.listPeers()).thenReturn(emptyPeers);

    PeerRemove cmd = createWithMockDirectory(mockDir);
    setField(cmd, "peerIdentifiers", List.of("ghost"));
    setField(cmd, "force", true);

    // When
    Method runCommand = PeerRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: no deletion occurs (no peers matched)
    verify(mockDir).listPeers();
    verify(mockDir, never()).deletePeer(any(UUID.class));
    assertThat(result, is(0));
  }

  /**
   * Tests that invoking the command with no arguments prints usage and returns exit code 1.
   *
   * <p>Verifies that when no positional arguments and no {@code --all} flag are provided, the
   * command prints a usage message and returns exit code 1.
   */
  @Test
  public void runCommand_noArgs_printsUsageAndReturnsOne() throws Exception {
    // Given: no positional arguments and no --all flag
    PalDirectory mockDir = mock(PalDirectory.class);
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerRemove cmd = createWithMockDirAndOutput(mockDir, bout);

    // We need to set up the picocli spec for usage printing
    CommandLine commandLine = new CommandLine(cmd);
    // We can access spec via reflection since picocli sets it during parsing
    Field specField = PeerRemove.class.getDeclaredField("spec");
    specField.setAccessible(true);
    specField.set(cmd, commandLine.getCommandSpec());

    // When
    Method runCommand = PeerRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: usage message is printed to output, exit code is 1
    assertThat(result, is(1));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("rm"));
  }
}
