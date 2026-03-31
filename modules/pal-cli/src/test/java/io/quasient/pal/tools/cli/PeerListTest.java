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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for {@link PeerList}.
 *
 * <p>PeerList is the peer-specific list command extracted from {@code List} to follow the
 * entity-operation pattern ({@code pal peer ls}). It handles listing peers in short and long
 * formats, with sorting, reversal, and trimming options.
 */
public class PeerListTest {

  // ==================== Helper methods ====================

  /**
   * Sets a field value on an object via reflection, searching the class hierarchy.
   *
   * @param target the object to set the field on
   * @param fieldName the name of the field
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
   * @throws NoSuchFieldException if the field is not found
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
   * Creates a PeerList instance with a mock PalDirectory injected and output captured.
   *
   * @param mockDir the mock PalDirectory
   * @param bout the output stream to capture standard output
   * @return a configured PeerList instance
   */
  private static PeerList createWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout) throws Exception {
    PeerList cmd = new PeerList();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(cmd, "directoryConnectionProvider", dcp);
    setField(cmd, "out", new PrintStream(bout));
    setField(cmd, "err", new PrintStream(new ByteArrayOutputStream()));
    return cmd;
  }

  /**
   * Creates a PeerInfo with a creation time set.
   *
   * @param uuid the UUID
   * @param name the name
   * @param ctime the creation time
   * @return a configured PeerInfo
   */
  private static PeerInfo createPeerInfo(UUID uuid, String name, OffsetDateTime ctime) {
    PeerInfo peer = new PeerInfo(uuid, name);
    peer.setCtime(ctime.toInstant().toEpochMilli());
    return peer;
  }

  // ==================== runCommand() Tests ====================

  /**
   * Tests that short format lists peer names, one per line.
   *
   * <p>Verifies that when no {@code -l} flag is set, runCommand prints only peer names (one per
   * line) for all peers registered in the directory.
   */
  @Test
  public void runCommand_listsPeers_shortFormat() throws Exception {
    // Given: PalDirectory with 2 peers
    PalDirectory mockDir = mock(PalDirectory.class);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(createPeerInfo(UUID.randomUUID(), "alpha", now));
    peers.add(createPeerInfo(UUID.randomUUID(), "beta", now));
    when(mockDir.listPeers()).thenReturn(peers);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerList cmd = createWithMockDirAndOutput(mockDir, bout);

    // When: runCommand() invoked (no -l flag)
    Method runCommand = PeerList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: prints peer names, one per line
    String output = bout.toString(UTF_8);
    assertThat(result, is(0));
    assertThat(output, containsString("alpha"));
    assertThat(output, containsString("beta"));
  }

  /**
   * Tests that long format prints detailed peer information.
   *
   * <p>Verifies that when the {@code -l} flag is set, runCommand prints peer name, UUID, RPC
   * addresses, status, and uptime for each peer.
   */
  @Test
  public void runCommand_listsPeers_longFormat() throws Exception {
    // Given: PalDirectory with peers
    UUID peerUuid = UUID.randomUUID();
    PalDirectory mockDir = mock(PalDirectory.class);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    PeerInfo peer = createPeerInfo(peerUuid, "my-peer", now);
    peer.setZmqRpcAddress("tcp://192.168.1.1:5555");
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(peer);
    when(mockDir.listPeers()).thenReturn(peers);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerList cmd = createWithMockDirAndOutput(mockDir, bout);
    setField(cmd, "longListing", true);

    // When: -l flag set, runCommand() invoked
    Method runCommand = PeerList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: prints header and peer details
    String output = bout.toString(UTF_8);
    assertThat(result, is(0));
    assertThat(output, containsString("total 1"));
    assertThat(output, containsString("UUID"));
    String expectedUuid = peerUuid.toString().substring(0, 8) + "..";
    assertThat(output, containsString(expectedUuid));
    assertThat(output, not(containsString(peerUuid.toString())));
    assertThat(output, containsString("my-peer"));
    assertThat(output, containsString("192.168.1.1:5555"));
  }

  /**
   * Tests that peers are sorted by creation time with newest first.
   *
   * <p>Verifies that when the {@code -c} flag is set, peers are listed in descending order of
   * creation time (newest first).
   */
  @Test
  public void runCommand_sortByCtime() throws Exception {
    // Given: 3 peers with different creation times
    PalDirectory mockDir = mock(PalDirectory.class);
    OffsetDateTime t1 = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime t2 = OffsetDateTime.of(2025, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime t3 = OffsetDateTime.of(2025, 12, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    Set<PeerInfo> peers = new HashSet<>();
    peers.add(createPeerInfo(UUID.randomUUID(), "oldest", t1));
    peers.add(createPeerInfo(UUID.randomUUID(), "middle", t2));
    peers.add(createPeerInfo(UUID.randomUUID(), "newest", t3));
    when(mockDir.listPeers()).thenReturn(peers);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerList cmd = createWithMockDirAndOutput(mockDir, bout);
    setField(cmd, "sortByCTime", true);

    // When: -c flag set, runCommand() invoked
    Method runCommand = PeerList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: peers listed newest first
    String output = bout.toString(UTF_8);
    assertThat(result, is(0));
    int newestIdx = output.indexOf("newest");
    int middleIdx = output.indexOf("middle");
    int oldestIdx = output.indexOf("oldest");
    assertThat("newest should appear before middle", newestIdx < middleIdx, is(true));
    assertThat("middle should appear before oldest", middleIdx < oldestIdx, is(true));
  }

  /**
   * Tests that the reverse flag reverses the output order.
   *
   * <p>Verifies that when the {@code -r} flag is set, the order of listed peers is reversed
   * compared to the default or sorted order.
   */
  @Test
  public void runCommand_reverseOrder() throws Exception {
    // Given: sorted peers
    PalDirectory mockDir = mock(PalDirectory.class);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(createPeerInfo(UUID.randomUUID(), "alpha", now));
    peers.add(createPeerInfo(UUID.randomUUID(), "zulu", now));
    when(mockDir.listPeers()).thenReturn(peers);

    // Default order (by name): alpha before zulu
    ByteArrayOutputStream bout1 = new ByteArrayOutputStream();
    PeerList cmd1 = createWithMockDirAndOutput(mockDir, bout1);
    Method runCommand = PeerList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    runCommand.invoke(cmd1);
    String defaultOutput = bout1.toString(UTF_8);
    int alphaDefault = defaultOutput.indexOf("alpha");
    int zuluDefault = defaultOutput.indexOf("zulu");
    assertThat("default: alpha before zulu", alphaDefault < zuluDefault, is(true));

    // Reversed order: zulu before alpha
    ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
    PeerList cmd2 = createWithMockDirAndOutput(mockDir, bout2);
    setField(cmd2, "reverseOrder", true);
    runCommand.invoke(cmd2);
    String reversedOutput = bout2.toString(UTF_8);
    int alphaReversed = reversedOutput.indexOf("alpha");
    int zuluReversed = reversedOutput.indexOf("zulu");
    assertThat("reversed: zulu before alpha", zuluReversed < alphaReversed, is(true));
  }

  /**
   * Tests that the no-trim flag prevents name truncation.
   *
   * <p>Verifies that when {@code --no-trim} is set, peer names are printed in full without
   * truncation, regardless of length.
   */
  @Test
  public void runCommand_noTrim() throws Exception {
    // Given: peer with a long name
    PalDirectory mockDir = mock(PalDirectory.class);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    String longName = "this-is-a-very-long-peer-name-that-exceeds-max";
    UUID peerUuid = UUID.randomUUID();
    Set<PeerInfo> peers = new HashSet<>();
    peers.add(createPeerInfo(peerUuid, longName, now));
    when(mockDir.listPeers()).thenReturn(peers);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerList cmd = createWithMockDirAndOutput(mockDir, bout);
    setField(cmd, "longListing", true);
    setField(cmd, "noTrimming", true);

    // When: --no-trim flag set, runCommand() invoked
    Method runCommand = PeerList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    runCommand.invoke(cmd);

    // Then: full name and full UUID printed without truncation
    String output = bout.toString(UTF_8);
    assertThat(output, containsString(longName));
    assertThat(output, containsString(peerUuid.toString()));
  }

  // ==================== optionallyTrim() Tests ====================

  /**
   * Tests that trimming truncates strings exceeding the max length.
   *
   * <p>Verifies that with trimming enabled (default), strings longer than the specified length are
   * truncated with a ".." suffix, while shorter strings are returned unchanged.
   */
  @Test
  public void optionallyTrim_withTrimmingEnabled() throws Exception {
    // Given: PeerList instance with trimming enabled (default, noTrimming=false)
    PeerList cmd = new PeerList();
    setField(cmd, "noTrimming", false);

    // When: optionallyTrim called via reflection
    Method trim = PeerList.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);
    assertThat((String) trim.invoke(cmd, "abcdef", 4), is("ab.."));
    assertThat((String) trim.invoke(cmd, "abc", 4), is("abc"));
  }

  /**
   * Tests that trimming is disabled when the no-trim flag is set.
   *
   * <p>Verifies that with {@code --no-trim} enabled, strings are returned in full regardless of
   * length.
   */
  @Test
  public void optionallyTrim_withNoTrimmingEnabled() throws Exception {
    // Given: PeerList instance with noTrimming=true
    PeerList cmd = new PeerList();
    setField(cmd, "noTrimming", true);

    // When: optionallyTrim called via reflection
    Method trim = PeerList.class.getDeclaredMethod("optionallyTrim", String.class, int.class);
    trim.setAccessible(true);
    assertThat((String) trim.invoke(cmd, "abcdef", 4), is("abcdef"));
    assertThat((String) trim.invoke(cmd, "abc", 4), is("abc"));
  }

  // ==================== Date/Uptime Formatting Tests ====================

  /**
   * Tests that date and uptime formatting produces expected output.
   *
   * <p>Verifies that getFormattedDate returns a string containing the month abbreviation, and
   * getFormattedUptime returns a colon-separated time string.
   */
  @Test
  public void dateFormatters() throws Exception {
    // Given: a known OffsetDateTime
    // When: getFormattedDate() called
    String s = PeerList.getFormattedDate(OffsetDateTime.of(2025, 1, 2, 3, 4, 0, 0, ZoneOffset.UTC));
    // Then: result contains "Jan"
    assertThat(s, containsString("Jan"));

    // And: when getFormattedUptime() called with a time 1 hour ago
    String up = PeerList.getFormattedUptime(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
    // Then: result contains ":"
    assertThat(up, containsString(":"));
  }

  // ==================== Empty Directory Tests ====================

  /**
   * Tests that an empty directory produces no output.
   *
   * <p>Verifies that when the directory contains no peers, runCommand prints nothing and exits with
   * code 0.
   */
  @Test
  public void runCommand_noPeersFound_printsNothing() throws Exception {
    // Given: PalDirectory with no peers
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listPeers()).thenReturn(new HashSet<>());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    PeerList cmd = createWithMockDirAndOutput(mockDir, bout);

    // When: runCommand() invoked
    Method runCommand = PeerList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: no output, exit code 0
    assertThat(result, is(0));
    assertThat(bout.toString(UTF_8), is(""));
  }
}
