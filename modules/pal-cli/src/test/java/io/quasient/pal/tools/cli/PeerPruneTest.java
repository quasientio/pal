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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for {@link PeerPrune}.
 *
 * <p>PeerPrune removes dead peers (those whose lease has expired) from the PAL directory. These
 * tests verify that only dead peers are pruned while alive peers are left untouched.
 */
public class PeerPruneTest {

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
   * Creates a PeerPrune instance with a mock PalDirectory and wired output streams.
   *
   * @param mockDir the mock PalDirectory to inject
   * @param bout the output stream to capture standard output
   * @return a configured PeerPrune instance
   */
  private static PeerPrune createWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout) throws Exception {
    PeerPrune cmd = new PeerPrune();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(cmd, "directoryConnectionProvider", dcp);
    setField(cmd, "out", new PrintStream(bout));
    return cmd;
  }

  /**
   * Invokes the runCommand() method on a PeerPrune instance via reflection.
   *
   * @param cmd the PeerPrune instance
   * @return the exit code
   */
  private static int invokeRunCommand(PeerPrune cmd) throws Exception {
    Method runCommand = PeerPrune.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    return (int) runCommand.invoke(cmd);
  }

  // ==================== Tests ====================

  /**
   * Tests that only dead peers are pruned when the directory contains a mix of alive and dead
   * peers.
   */
  @Test
  public void runCommand_prunesDeadPeers() throws Exception {
    UUID aliveUuid = UUID.randomUUID();
    UUID deadUuid = UUID.randomUUID();
    PeerInfo alivePeer = new PeerInfo(aliveUuid, "alive-peer");
    PeerInfo deadPeer = new PeerInfo(deadUuid, "dead-peer");

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(alivePeer);
    peers.add(deadPeer);
    when(mockDir.listPeers()).thenReturn(peers);
    when(mockDir.isPeerAlive(aliveUuid)).thenReturn(true);
    when(mockDir.isPeerAlive(deadUuid)).thenReturn(false);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerPrune cmd = createWithMockDirAndOutput(mockDir, bout);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    verify(mockDir).deletePeer(deadUuid);
    verify(mockDir, never()).deletePeer(aliveUuid);
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("dead-peer"));
    assertThat(output, containsString("Pruned 1 dead peer(s)"));
    assertThat(output, not(containsString("alive-peer")));
  }

  /** Tests that no peers are deleted when all peers are alive. */
  @Test
  public void runCommand_noDeadPeers_noDeletions() throws Exception {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    PeerInfo peer1 = new PeerInfo(uuid1, "peer-1");
    PeerInfo peer2 = new PeerInfo(uuid2, "peer-2");

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(peer1);
    peers.add(peer2);
    when(mockDir.listPeers()).thenReturn(peers);
    when(mockDir.isPeerAlive(uuid1)).thenReturn(true);
    when(mockDir.isPeerAlive(uuid2)).thenReturn(true);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerPrune cmd = createWithMockDirAndOutput(mockDir, bout);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    verify(mockDir, never()).deletePeer(any(UUID.class));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("No dead peers found"));
  }

  /** Tests that an empty directory produces no deletions and a clean message. */
  @Test
  public void runCommand_noPeers_noDeletions() throws Exception {
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listPeers()).thenReturn(new HashSet<>());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerPrune cmd = createWithMockDirAndOutput(mockDir, bout);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    verify(mockDir, never()).deletePeer(any(UUID.class));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("No dead peers found"));
  }

  /** Tests that all peers are pruned when all peers are dead. */
  @Test
  public void runCommand_allDead_prunesAll() throws Exception {
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();
    UUID uuid3 = UUID.randomUUID();
    PeerInfo peer1 = new PeerInfo(uuid1, "dead-1");
    PeerInfo peer2 = new PeerInfo(uuid2, "dead-2");
    PeerInfo peer3 = new PeerInfo(uuid3, "dead-3");

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(peer1);
    peers.add(peer2);
    peers.add(peer3);
    when(mockDir.listPeers()).thenReturn(peers);
    when(mockDir.isPeerAlive(any(UUID.class))).thenReturn(false);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerPrune cmd = createWithMockDirAndOutput(mockDir, bout);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    verify(mockDir).deletePeer(uuid1);
    verify(mockDir).deletePeer(uuid2);
    verify(mockDir).deletePeer(uuid3);
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("Pruned 3 dead peer(s)"));
  }
}
