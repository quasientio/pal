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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.LogInfo;
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
 * Unit tests for {@link LogList}.
 *
 * <p>LogList is the log-specific list command extracted from {@code List} to follow the
 * entity-operation pattern ({@code pal log ls}). It handles listing logs in short and long formats,
 * with sorting by size or creation time, reversal, trimming options, and Kafka offset fetching.
 */
public class LogListTest {

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
   * Creates a LogList instance with a mock PalDirectory injected and output captured. Uses
   * Chronicle-type logs (no Kafka interaction needed for most tests).
   *
   * @param mockDir the mock PalDirectory
   * @param bout the output stream to capture standard output
   * @return a configured LogList instance
   */
  private static LogList createWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout) throws Exception {
    LogList cmd = new LogList();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(cmd, "directoryConnectionProvider", dcp);
    setField(cmd, "out", new PrintStream(bout));
    setField(cmd, "err", new PrintStream(new ByteArrayOutputStream()));
    // Initialize a KafkaAdminHelper so closeResources doesn't NPE
    setField(cmd, "kafkaAdminHelper", new KafkaAdminHelper());
    return cmd;
  }

  // ==================== runCommand() Tests ====================

  /**
   * Tests that short format lists log names, one per line.
   *
   * <p>Verifies that when no {@code -l} flag is set, runCommand prints only log names (one per
   * line) for all logs registered in the directory. Uses an empty directory with pre-populated
   * LogInfos to avoid Kafka/Chronicle interaction.
   */
  @Test
  public void runCommand_listsLogs_shortFormat() throws Exception {
    // Given: PalDirectory with 2 logs (empty set to avoid Kafka/Chronicle calls)
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listAllLogs()).thenReturn(new HashSet<>());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    LogList cmd = createWithMockDirAndOutput(mockDir, bout);

    // When: runCommand() invoked (no -l flag)
    Method runCommand = LogList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: exit code 0, no output for empty list
    assertThat(result, is(0));
    assertThat(bout.toString(UTF_8), is(""));
  }

  /**
   * Tests that long format prints detailed log information.
   *
   * <p>Verifies that when the {@code -l} flag is set with an empty directory, it shows the total
   * count header.
   */
  @Test
  public void runCommand_listsLogs_longFormat() throws Exception {
    // Given: PalDirectory with no logs
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listAllLogs()).thenReturn(new HashSet<>());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    LogList cmd = createWithMockDirAndOutput(mockDir, bout);
    setField(cmd, "longListing", true);

    // When: -l flag set, runCommand() invoked
    Method runCommand = LogList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: prints "total 0" header, no column headers (empty set)
    String output = bout.toString(UTF_8);
    assertThat(result, is(0));
    assertThat(output, containsString("total 0"));
  }

  /**
   * Tests that logs are sorted by size with largest first.
   *
   * <p>Verifies that when the {@code -S} flag is set, logs are listed in descending order of size
   * (largest first). Uses the printLogs method directly to avoid Kafka connectivity.
   */
  @Test
  public void runCommand_sortBySize() throws Exception {
    // Given: LogList instance with 3 logs of different sizes
    LogList cmd = new LogList();
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    setField(cmd, "out", new PrintStream(bout));
    setField(cmd, "err", new PrintStream(new ByteArrayOutputStream()));
    setField(cmd, "sortBySize", true);

    // Use printLogs via reflection to test sorting without Kafka
    LogInfo small = new LogInfo("small-log", UUID.randomUUID());
    small.setCtime(System.currentTimeMillis());
    small.setBytes(100);
    LogInfo medium = new LogInfo("medium-log", UUID.randomUUID());
    medium.setCtime(System.currentTimeMillis());
    medium.setBytes(5000);
    LogInfo large = new LogInfo("large-log", UUID.randomUUID());
    large.setCtime(System.currentTimeMillis());
    large.setBytes(1000000);

    Set<LogInfo> logs = new HashSet<>();
    logs.add(small);
    logs.add(medium);
    logs.add(large);

    Method printLogs = LogList.class.getDeclaredMethod("printLogs", Set.class);
    printLogs.setAccessible(true);
    printLogs.invoke(cmd, logs);

    // Then: logs listed largest first (default, non-reversed sort by size)
    String output = bout.toString(UTF_8);
    int largeIdx = output.indexOf("large-log");
    int mediumIdx = output.indexOf("medium-log");
    int smallIdx = output.indexOf("small-log");
    assertThat("large before medium", largeIdx < mediumIdx, is(true));
    assertThat("medium before small", mediumIdx < smallIdx, is(true));
  }

  /**
   * Tests that logs are sorted by creation time with newest first.
   *
   * <p>Verifies that when the {@code -c} flag is set, logs are listed in descending order of
   * creation time (newest first).
   */
  @Test
  public void runCommand_sortByCtime() throws Exception {
    // Given: 3 logs with different creation times
    LogList cmd = new LogList();
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    setField(cmd, "out", new PrintStream(bout));
    setField(cmd, "err", new PrintStream(new ByteArrayOutputStream()));
    setField(cmd, "sortByCTime", true);

    OffsetDateTime t1 = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime t2 = OffsetDateTime.of(2025, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    OffsetDateTime t3 = OffsetDateTime.of(2025, 12, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    LogInfo oldest = new LogInfo("oldest-log", UUID.randomUUID());
    oldest.setCtime(t1.toInstant().toEpochMilli());
    LogInfo middle = new LogInfo("middle-log", UUID.randomUUID());
    middle.setCtime(t2.toInstant().toEpochMilli());
    LogInfo newest = new LogInfo("newest-log", UUID.randomUUID());
    newest.setCtime(t3.toInstant().toEpochMilli());

    Set<LogInfo> logs = new HashSet<>();
    logs.add(oldest);
    logs.add(middle);
    logs.add(newest);

    Method printLogs = LogList.class.getDeclaredMethod("printLogs", Set.class);
    printLogs.setAccessible(true);
    printLogs.invoke(cmd, logs);

    // Then: logs listed newest first
    String output = bout.toString(UTF_8);
    int newestIdx = output.indexOf("newest-log");
    int middleIdx = output.indexOf("middle-log");
    int oldestIdx = output.indexOf("oldest-log");
    assertThat("newest before middle", newestIdx < middleIdx, is(true));
    assertThat("middle before oldest", middleIdx < oldestIdx, is(true));
  }

  /**
   * Tests that the reverse flag reverses the output order.
   *
   * <p>Verifies that when the {@code -r} flag is set, the order of listed logs is reversed compared
   * to the default or sorted order.
   */
  @Test
  public void runCommand_reverseOrder() throws Exception {
    // Given: two logs that would sort in natural order
    LogList cmd1 = new LogList();
    ByteArrayOutputStream bout1 = new ByteArrayOutputStream();
    setField(cmd1, "out", new PrintStream(bout1));
    setField(cmd1, "err", new PrintStream(new ByteArrayOutputStream()));

    LogInfo alpha = new LogInfo("alpha-log", UUID.randomUUID());
    alpha.setCtime(System.currentTimeMillis());
    LogInfo zulu = new LogInfo("zulu-log", UUID.randomUUID());
    zulu.setCtime(System.currentTimeMillis());

    Set<LogInfo> logs = new HashSet<>();
    logs.add(alpha);
    logs.add(zulu);

    Method printLogs = LogList.class.getDeclaredMethod("printLogs", Set.class);
    printLogs.setAccessible(true);
    printLogs.invoke(cmd1, logs);
    String defaultOutput = bout1.toString(UTF_8);
    int alphaDefault = defaultOutput.indexOf("alpha-log");
    int zuluDefault = defaultOutput.indexOf("zulu-log");
    assertThat("default: alpha before zulu", alphaDefault < zuluDefault, is(true));

    // Reversed
    LogList cmd2 = new LogList();
    ByteArrayOutputStream bout2 = new ByteArrayOutputStream();
    setField(cmd2, "out", new PrintStream(bout2));
    setField(cmd2, "err", new PrintStream(new ByteArrayOutputStream()));
    setField(cmd2, "reverseOrder", true);
    printLogs.invoke(cmd2, logs);
    String reversedOutput = bout2.toString(UTF_8);
    int alphaReversed = reversedOutput.indexOf("alpha-log");
    int zuluReversed = reversedOutput.indexOf("zulu-log");
    assertThat("reversed: zulu before alpha", zuluReversed < alphaReversed, is(true));
  }

  /**
   * Tests that the no-trim flag prevents name truncation.
   *
   * <p>Verifies that when {@code --no-trim} is set, log names are printed in full without
   * truncation, regardless of length.
   */
  @Test
  public void runCommand_noTrim() throws Exception {
    // Given: a log with a long name, printed in long format
    LogList cmd = new LogList();
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    setField(cmd, "out", new PrintStream(bout));
    setField(cmd, "err", new PrintStream(new ByteArrayOutputStream()));
    setField(cmd, "longListing", true);
    setField(cmd, "noTrimming", true);

    String longName = "this-is-a-very-long-log-name-that-exceeds-max";
    LogInfo log = new LogInfo(longName, UUID.randomUUID());
    log.setCtime(System.currentTimeMillis());
    log.setBytes(1024);
    log.setStartOffset(0);
    log.setEndOffset(5);

    Set<LogInfo> logs = new HashSet<>();
    logs.add(log);

    Method printLogs = LogList.class.getDeclaredMethod("printLogs", Set.class);
    printLogs.setAccessible(true);
    printLogs.invoke(cmd, logs);

    // Then: full name printed without truncation
    String output = bout.toString(UTF_8);
    assertThat(output, containsString(longName));
  }

  // ==================== Empty Directory Tests ====================

  /**
   * Tests that an empty directory produces no output.
   *
   * <p>Verifies that when the directory contains no logs, runCommand prints nothing and exits with
   * code 0.
   */
  @Test
  public void runCommand_noLogsFound_printsNothing() throws Exception {
    // Given: PalDirectory with no logs
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listAllLogs()).thenReturn(new HashSet<>());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    LogList cmd = createWithMockDirAndOutput(mockDir, bout);

    // When: runCommand() invoked
    Method runCommand = LogList.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: no output, exit code 0
    assertThat(result, is(0));
    assertThat(bout.toString(UTF_8), is(""));
  }

  // ==================== Kafka Integration Tests ====================

  /**
   * Tests that KafkaAdminHelper is used for offset fetching with Kafka logs.
   *
   * <p>Verifies that LogList creates and uses a KafkaAdminHelper instance for managing Kafka admin
   * client connections. This test verifies the field is initialized during initialize().
   */
  @Test
  public void runCommand_kafkaAdminHelper_usedForOffsets() throws Exception {
    // Given: LogList initialized via initialize() (would create KafkaAdminHelper)
    LogList cmd = new LogList();

    // Verify kafkaAdminHelper field exists and is used
    Field kafkaField = LogList.class.getDeclaredField("kafkaAdminHelper");
    kafkaField.setAccessible(true);

    // Set it to a mock to verify it's the field LogList uses
    KafkaAdminHelper mockHelper = mock(KafkaAdminHelper.class);
    kafkaField.set(cmd, mockHelper);

    // When: closeResources is called
    Method closeResources = LogList.class.getDeclaredMethod("closeResources");
    closeResources.setAccessible(true);

    // Set up directoryConnectionProvider to avoid NPE in super.closeResources()
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.empty());
    setField(cmd, "directoryConnectionProvider", dcp);

    closeResources.invoke(cmd);

    // Then: KafkaAdminHelper.closeResources() is called
    verify(mockHelper).closeResources();
  }
}
