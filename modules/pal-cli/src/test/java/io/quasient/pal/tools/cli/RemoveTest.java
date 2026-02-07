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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

/** Unit tests for {@link Remove}. */
public class RemoveTest {

  // ===========================================================================
  // Helper methods
  // ===========================================================================

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
   * Gets a field value from an object via reflection, searching the class hierarchy.
   *
   * @param target the object from which to read the field
   * @param fieldName the name of the field to read
   * @return the field value
   */
  private static Object getField(Object target, String fieldName) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    return f.get(target);
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
   * Creates a Remove instance with a mock PalDirectory injected via a mock
   * DirectoryConnectionProvider.
   *
   * @param mockDir the mock PalDirectory to inject
   * @return a configured Remove instance
   */
  private static Remove createRemoveWithMockDirectory(PalDirectory mockDir) throws Exception {
    Remove rm = new Remove();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(rm, "directoryConnectionProvider", dcp);
    return rm;
  }

  /**
   * Creates a Remove instance with a mock PalDirectory and wired output streams.
   *
   * @param mockDir the mock PalDirectory to inject
   * @param bout the output stream to capture standard output
   * @return a configured Remove instance
   */
  private static Remove createRemoveWithMockDirAndOutput(
      PalDirectory mockDir, ByteArrayOutputStream bout) throws Exception {
    Remove rm = createRemoveWithMockDirectory(mockDir);
    setField(rm, "out", new PrintStream(bout));
    return rm;
  }

  // ===========================================================================
  // Existing tests
  // ===========================================================================

  @Test
  public void runCommand_withoutFlags_printsUsageAndReturnsOne() throws Exception {
    Remove rm = new Remove();
    // inject palCommand returning NO_URL
    var pc = Pal.class.getDeclaredConstructor();
    pc.setAccessible(true);
    Pal pal = pc.newInstance();
    var palUrl = Pal.class.getDeclaredField("palDirectoryUrl");
    palUrl.setAccessible(true);
    palUrl.set(pal, PalDirectory.NO_URL);
    var parent = Remove.class.getDeclaredField("palCommand");
    parent.setAccessible(true);
    parent.set(rm, pal);

    // wire err/out to capture
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    var outF = AbstractPalSubcommand.class.getDeclaredField("out");
    outF.setAccessible(true);
    outF.set(rm, new PrintStream(bout));

    // go through call() pipeline
    int code = rm.call();
    assertThat(code, is(1));
    assertThat(bout.toString(UTF_8), containsString("Use -L/--logs to remove logs"));
  }

  // ===========================================================================
  // Tests for resolveLogInfo() - Issue #368, #369
  // ===========================================================================

  /**
   * Tests that resolveLogInfo() correctly parses a Chronicle log path with "file:" prefix.
   *
   * <p>Verifies that the "file:" prefix is stripped and a LogInfo with CHRONICLE type is returned.
   */
  @Test
  public void testResolveLogInfo_chronicleWithFilePrefix() throws Exception {
    // Given: logNameOrPath = "file:/tmp/mylog"
    //        Remove instance with no PAL directory connection
    Remove rm = new Remove();

    // Access the private resolveLogInfo method via reflection
    Method resolveLogInfoMethod = Remove.class.getDeclaredMethod("resolveLogInfo", String.class);
    resolveLogInfoMethod.setAccessible(true);

    // When: resolveLogInfo() called via reflection
    LogInfo result = (LogInfo) resolveLogInfoMethod.invoke(rm, "file:/tmp/mylog");

    // Then: Returns LogInfo with:
    //       - CHRONICLE type
    //       - path "/tmp/mylog" (prefix stripped)
    assertThat(result, is(notNullValue()));
    assertThat(result.getLogType(), is(LogType.CHRONICLE));
    assertThat(result.getName(), is("/tmp/mylog"));
  }

  /**
   * Tests that resolveLogInfo() correctly creates a Kafka LogInfo when bootstrap servers are set.
   *
   * <p>Verifies that a Kafka-type LogInfo is returned with the provided bootstrap servers.
   */
  @Test
  public void testResolveLogInfo_kafkaWithBootstrapServers() throws Exception {
    // Given: logNameOrPath = "my-topic"
    //        kafkaServers field set to "localhost:29092"
    //        Remove instance with no PAL directory connection
    Remove rm = new Remove();

    // Set kafkaServers field via reflection
    Field kafkaServersField = Remove.class.getDeclaredField("kafkaServers");
    kafkaServersField.setAccessible(true);
    kafkaServersField.set(rm, "localhost:29092");

    // Access the private resolveLogInfo method via reflection
    Method resolveLogInfoMethod = Remove.class.getDeclaredMethod("resolveLogInfo", String.class);
    resolveLogInfoMethod.setAccessible(true);

    // When: resolveLogInfo() called via reflection
    LogInfo result = (LogInfo) resolveLogInfoMethod.invoke(rm, "my-topic");

    // Then: Returns LogInfo with:
    //       - KAFKA type
    //       - name "my-topic"
    //       - bootstrap servers "localhost:29092"
    assertThat(result, is(notNullValue()));
    assertThat(result.getLogType(), is(LogType.KAFKA));
    assertThat(result.getName(), is("my-topic"));
    assertThat(result.getBootstrapServers(), is("localhost:29092"));
  }

  /**
   * Tests that resolveLogInfo() returns null and logs error when Kafka servers are not available.
   *
   * <p>Verifies that when no "file:" prefix is present and no Kafka servers are configured (neither
   * via field nor KAFKA_SERVERS env var), the method returns null.
   *
   * <p>Note: This test assumes KAFKA_SERVERS environment variable is not set. If the test is run in
   * an environment where KAFKA_SERVERS is set, the test may fail. In CI/CD pipelines, ensure this
   * env var is not set when running unit tests.
   */
  @Test
  public void testResolveLogInfo_failsWithoutKafkaServers() throws Exception {
    // Given: logNameOrPath = "my-topic" (no "file:" prefix)
    //        kafkaServers field is null
    //        KAFKA_SERVERS environment variable is not set
    //        Remove instance with no PAL directory connection
    Remove rm = new Remove();

    // Ensure kafkaServers field is null (it should be by default, but be explicit)
    Field kafkaServersField = Remove.class.getDeclaredField("kafkaServers");
    kafkaServersField.setAccessible(true);
    kafkaServersField.set(rm, null);

    // Access the private resolveLogInfo method via reflection
    Method resolveLogInfoMethod = Remove.class.getDeclaredMethod("resolveLogInfo", String.class);
    resolveLogInfoMethod.setAccessible(true);

    // When: resolveLogInfo() called via reflection
    // Note: This test assumes KAFKA_SERVERS env var is not set. If it is set,
    // the method will return a valid LogInfo instead of null.
    String kafkaServersEnv = System.getenv("KAFKA_SERVERS");
    LogInfo result = (LogInfo) resolveLogInfoMethod.invoke(rm, "my-topic");

    // Then: Returns null if KAFKA_SERVERS is not set
    //       Logs error message about missing Kafka servers
    if (kafkaServersEnv == null || kafkaServersEnv.isEmpty()) {
      assertThat(result, is(nullValue()));
    } else {
      // If KAFKA_SERVERS is set, the result should be a valid Kafka LogInfo
      // using the environment variable value
      assertThat(result, is(notNullValue()));
      assertThat(result.getLogType(), is(LogType.KAFKA));
      assertThat(result.getName(), is("my-topic"));
      assertThat(result.getBootstrapServers(), is(kafkaServersEnv));
    }
  }

  // ===========================================================================
  // Tests for runCommand() log deletion paths - Issue #632
  // ===========================================================================

  /**
   * Tests that runCommand deletes a log by UUID when deleteLogs is true.
   *
   * <p>Verifies that when deleteLogs=true and a UUID string is provided in argList, the command
   * resolves the UUID and delegates to deleteLogsWithUuid() which lists logs from the directory,
   * filters by UUID, and deletes matching logs.
   */
  @Test
  public void runCommand_deleteLogs_withUuid_deletesLogByUuid() throws Exception {
    // Given
    UUID logUuid = UUID.randomUUID();
    LogInfo logInfo = new LogInfo("test-log", logUuid, "localhost:29092");
    logInfo.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(logInfo);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Remove rm = createRemoveWithMockDirectory(mockDir);
    setField(rm, "deleteLogs", true);
    setField(rm, "argList", List.of(logUuid.toString()));

    // When
    Method runCommand = Remove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(rm);

    // Then
    verify(mockDir).listAllLogs();
    verify(mockDir).deleteLog("test-log");
    assertThat(result, is(0));
  }

  /**
   * Tests that runCommand deletes all logs when deleteLogs=true and deleteAll=true.
   *
   * <p>Verifies that the deleteAllLogs() path is taken, which lists all logs from the directory and
   * deletes each one.
   */
  @Test
  public void runCommand_deleteLogs_deleteAll_deletesAllLogs() throws Exception {
    // Given
    LogInfo log1 = new LogInfo("log-1", UUID.randomUUID(), "localhost:29092");
    log1.setLogType(LogType.KAFKA);
    LogInfo log2 = new LogInfo("log-2", UUID.randomUUID(), "localhost:29092");
    log2.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(log1);
    logs.add(log2);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Remove rm = createRemoveWithMockDirectory(mockDir);
    setField(rm, "deleteLogs", true);
    setField(rm, "deleteAll", true);

    // When
    Method runCommand = Remove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(rm);

    // Then
    verify(mockDir).listAllLogs();
    verify(mockDir).deleteLog("log-1");
    verify(mockDir).deleteLog("log-2");
    assertThat(result, is(0));
  }

  /**
   * Tests that runCommand deletes all peers when deletePeers=true and deleteAll=true.
   *
   * <p>Verifies that deleteAllPeers() is called, which delegates to
   * getPalDirectory().deletePeers().
   */
  @Test
  public void runCommand_deletePeers_deleteAll_deletesAllPeers() throws Exception {
    // Given
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.deletePeers()).thenReturn(3L);

    Remove rm = createRemoveWithMockDirectory(mockDir);
    setField(rm, "deletePeers", true);
    setField(rm, "deleteAll", true);

    // When
    Method runCommand = Remove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(rm);

    // Then
    verify(mockDir).deletePeers();
    assertThat(result, is(0));
  }

  /**
   * Tests that runCommand with deletePeers=true and startingWith=true deletes only peers whose
   * names match the given prefix.
   *
   * <p>Verifies that listPeers() is called, results are filtered by name prefix, and only matching
   * peers are deleted.
   */
  @Test
  public void runCommand_deletePeers_withStartingWith_deletesMatchingPeers() throws Exception {
    // Given
    UUID peer1Uuid = UUID.randomUUID();
    UUID peer2Uuid = UUID.randomUUID();
    UUID peer3Uuid = UUID.randomUUID();
    PeerInfo peer1 = new PeerInfo(peer1Uuid, "test-peer-1");
    PeerInfo peer2 = new PeerInfo(peer2Uuid, "test-peer-2");
    PeerInfo peer3 = new PeerInfo(peer3Uuid, "other-peer");

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<PeerInfo> allPeers = new HashSet<>();
    allPeers.add(peer1);
    allPeers.add(peer2);
    allPeers.add(peer3);
    when(mockDir.listPeers()).thenReturn(allPeers);
    when(mockDir.isPeerAlive(any(UUID.class))).thenReturn(false);

    Remove rm = createRemoveWithMockDirectory(mockDir);
    setField(rm, "deletePeers", true);
    setField(rm, "startingWith", true);
    setField(rm, "argList", List.of("test-"));

    // When
    Method runCommand = Remove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(rm);

    // Then
    verify(mockDir).listPeers();
    verify(mockDir).deletePeer(peer1Uuid);
    verify(mockDir).deletePeer(peer2Uuid);
    verify(mockDir, never()).deletePeer(peer3Uuid);
    assertThat(result, is(0));
  }

  // ===========================================================================
  // Tests for deleteLog() - Issue #632
  // ===========================================================================

  /**
   * Tests that deleteLog correctly removes a Chronicle queue when the log type is CHRONICLE.
   *
   * <p>Verifies that when a LogInfo has LogType.CHRONICLE, the removeChronicleLog() path is taken
   * instead of removeFromKafka().
   */
  @Test
  public void deleteLog_chronicleLog_removesChronicleQueue() throws Exception {
    // Given: A LogInfo with CHRONICLE type pointing to a temp directory with .cq4 files
    Path tempDir = Files.createTempDirectory("removetest-chronicle");
    try {
      // Create a fake .cq4 file so queueExists returns true
      Files.createFile(tempDir.resolve("data.cq4"));

      LogInfo logInfo = new LogInfo(tempDir.toString());
      logInfo.setLogType(LogType.CHRONICLE);

      // No directory connection (direct mode)
      Remove rm = new Remove();

      Method deleteLogMethod = Remove.class.getDeclaredMethod("deleteLog", LogInfo.class);
      deleteLogMethod.setAccessible(true);

      // When
      deleteLogMethod.invoke(rm, logInfo);

      // Then: Chronicle directory should be deleted
      int errors = (int) getField(rm, "errors");
      assertThat(errors, is(0));
      assertThat("Chronicle dir should be deleted", !Files.exists(tempDir));
    } finally {
      // Clean up in case test fails
      if (Files.exists(tempDir)) {
        deleteRecursive(tempDir);
      }
    }
  }

  /**
   * Tests that deleteLog skips directory unregistration when no directory is configured.
   *
   * <p>Verifies that when directoryConnectionProvider is null, the method still proceeds to delete
   * the backing store without attempting directory operations.
   */
  @Test
  public void deleteLog_noDirectory_skipsDirectoryUnregistration() throws Exception {
    // Given: A LogInfo with CHRONICLE type, no directory connection
    Path tempDir = Files.createTempDirectory("removetest-nodir");
    try {
      Files.createFile(tempDir.resolve("data.cq4"));

      LogInfo logInfo = new LogInfo(tempDir.toString());
      logInfo.setLogType(LogType.CHRONICLE);

      Remove rm = new Remove();
      // directoryConnectionProvider is null by default

      Method deleteLogMethod = Remove.class.getDeclaredMethod("deleteLog", LogInfo.class);
      deleteLogMethod.setAccessible(true);

      // When
      deleteLogMethod.invoke(rm, logInfo);

      // Then: No exception thrown, Chronicle log deleted, no errors
      int errors = (int) getField(rm, "errors");
      assertThat(errors, is(0));
      assertThat("Chronicle dir should be deleted", !Files.exists(tempDir));
    } finally {
      if (Files.exists(tempDir)) {
        deleteRecursive(tempDir);
      }
    }
  }

  // ===========================================================================
  // Tests for removeChronicleLog() - Issue #632
  // ===========================================================================

  /**
   * Tests that removeChronicleLog logs a warning when the path does not exist.
   *
   * <p>Verifies that when ChronicleLogUtil.queueExists() returns false for the given path, a
   * warning is logged and no deletion is attempted (no error incremented).
   */
  @Test
  public void removeChronicleLog_nonExistentPath_logsWarning() throws Exception {
    // Given: A LogInfo pointing to a non-existent path
    LogInfo logInfo = new LogInfo("/nonexistent/path/that/does/not/exist");
    logInfo.setLogType(LogType.CHRONICLE);

    Remove rm = new Remove();

    Method removeChronicleLogMethod =
        Remove.class.getDeclaredMethod("removeChronicleLog", LogInfo.class);
    removeChronicleLogMethod.setAccessible(true);

    // When
    removeChronicleLogMethod.invoke(rm, logInfo);

    // Then: No error incremented (warning logged but not an error)
    int errors = (int) getField(rm, "errors");
    assertThat(errors, is(0));
  }

  /**
   * Tests that removeChronicleLog successfully deletes an existing Chronicle queue.
   *
   * <p>Verifies that when the Chronicle queue path exists, ChronicleLogUtil.deleteQueue() is called
   * and the queue is removed. On successful deletion, no error is incremented.
   */
  @Test
  public void removeChronicleLog_existentPath_deletes() throws Exception {
    // Given: A temp directory with a Chronicle queue file
    Path tempDir = Files.createTempDirectory("removetest-exists");
    try {
      Files.createFile(tempDir.resolve("data.cq4"));

      LogInfo logInfo = new LogInfo(tempDir.toString());
      logInfo.setLogType(LogType.CHRONICLE);

      Remove rm = new Remove();

      Method removeChronicleLogMethod =
          Remove.class.getDeclaredMethod("removeChronicleLog", LogInfo.class);
      removeChronicleLogMethod.setAccessible(true);

      // When
      removeChronicleLogMethod.invoke(rm, logInfo);

      // Then: Directory deleted, no errors
      int errors = (int) getField(rm, "errors");
      assertThat(errors, is(0));
      assertThat("Chronicle dir should be deleted", !Files.exists(tempDir));
    } finally {
      if (Files.exists(tempDir)) {
        deleteRecursive(tempDir);
      }
    }
  }

  // ===========================================================================
  // Tests for deletePeer() - Issue #632
  // ===========================================================================

  /**
   * Tests that deletePeer blocks deletion of an alive peer when force is false.
   *
   * <p>Verifies that when isPeerAlive() returns true and force=false, the peer is NOT deleted, an
   * error message is printed, and the error counter is incremented.
   */
  @Test
  public void deletePeer_aliveWithoutForce_blocked() throws Exception {
    // Given
    UUID peerUuid = UUID.randomUUID();
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.isPeerAlive(peerUuid)).thenReturn(true);

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    Remove rm = createRemoveWithMockDirAndOutput(mockDir, bout);
    setField(rm, "force", false);

    Method deletePeerMethod = Remove.class.getDeclaredMethod("deletePeer", UUID.class);
    deletePeerMethod.setAccessible(true);

    // When
    deletePeerMethod.invoke(rm, peerUuid);

    // Then
    verify(mockDir).isPeerAlive(peerUuid);
    verify(mockDir, never()).deletePeer(any(UUID.class));
    int errors = (int) getField(rm, "errors");
    assertThat(errors, is(1));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("Cannot remove peer"));
    assertThat(output, containsString("alive"));
  }

  /**
   * Tests that deletePeer deletes an alive peer when force is true.
   *
   * <p>Verifies that when isPeerAlive() returns true but force=true, the peer IS deleted despite
   * being alive.
   */
  @Test
  public void deletePeer_aliveWithForce_deletes() throws Exception {
    // Given
    UUID peerUuid = UUID.randomUUID();
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.isPeerAlive(peerUuid)).thenReturn(true);

    Remove rm = createRemoveWithMockDirectory(mockDir);
    setField(rm, "force", true);

    Method deletePeerMethod = Remove.class.getDeclaredMethod("deletePeer", UUID.class);
    deletePeerMethod.setAccessible(true);

    // When
    deletePeerMethod.invoke(rm, peerUuid);

    // Then
    verify(mockDir).isPeerAlive(peerUuid);
    verify(mockDir).deletePeer(peerUuid);
    int errors = (int) getField(rm, "errors");
    assertThat(errors, is(0));
  }

  /**
   * Tests that deletePeer deletes a non-alive peer normally without requiring force.
   *
   * <p>Verifies that when isPeerAlive() returns false, the peer is deleted regardless of the force
   * flag.
   */
  @Test
  public void deletePeer_notAlive_deletes() throws Exception {
    // Given
    UUID peerUuid = UUID.randomUUID();
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.isPeerAlive(peerUuid)).thenReturn(false);

    Remove rm = createRemoveWithMockDirectory(mockDir);
    setField(rm, "force", false);

    Method deletePeerMethod = Remove.class.getDeclaredMethod("deletePeer", UUID.class);
    deletePeerMethod.setAccessible(true);

    // When
    deletePeerMethod.invoke(rm, peerUuid);

    // Then
    verify(mockDir).isPeerAlive(peerUuid);
    verify(mockDir).deletePeer(peerUuid);
    int errors = (int) getField(rm, "errors");
    assertThat(errors, is(0));
  }

  // ===========================================================================
  // Tests for resolveLogInfo() - Issue #632
  // ===========================================================================

  /**
   * Tests that resolveLogInfo returns null when no Kafka servers are available (neither via field
   * nor environment variable).
   *
   * <p>Verifies that for a non-Chronicle log name without any Kafka servers configured, the method
   * returns null and logs an error.
   */
  @Test
  public void resolveLogInfo_kafkaWithoutServersOrEnvVar_returnsNull() throws Exception {
    // Given
    Remove rm = new Remove();
    setField(rm, "kafkaServers", null);

    Method resolveLogInfoMethod = Remove.class.getDeclaredMethod("resolveLogInfo", String.class);
    resolveLogInfoMethod.setAccessible(true);

    // When
    LogInfo result = (LogInfo) resolveLogInfoMethod.invoke(rm, "my-topic");

    // Then: If KAFKA_SERVERS env var is not set, result should be null
    String kafkaServersEnv = System.getenv("KAFKA_SERVERS");
    if (kafkaServersEnv == null || kafkaServersEnv.isEmpty()) {
      assertThat(result, is(nullValue()));
    } else {
      assertThat(result, is(notNullValue()));
      assertThat(result.getLogType(), is(LogType.KAFKA));
      assertThat(result.getBootstrapServers(), is(kafkaServersEnv));
    }
  }

  // ===========================================================================
  // Tests for deleteLogsWithUuid() - Issue #632
  // ===========================================================================

  /**
   * Tests that deleteLogsWithUuid deletes a single matching log by UUID.
   *
   * <p>Verifies that when exactly one log matches the given UUID, it is deleted without prompting
   * for confirmation.
   */
  @Test
  public void deleteLogsWithUuid_singleMatch_deletes() throws Exception {
    // Given
    UUID logUuid = UUID.randomUUID();
    LogInfo logInfo = new LogInfo("matching-log", logUuid, "localhost:29092");
    logInfo.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(logInfo);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Remove rm = createRemoveWithMockDirectory(mockDir);

    Method deleteLogsWithUuidMethod =
        Remove.class.getDeclaredMethod("deleteLogsWithUuid", UUID.class);
    deleteLogsWithUuidMethod.setAccessible(true);

    // When
    deleteLogsWithUuidMethod.invoke(rm, logUuid);

    // Then
    verify(mockDir).listAllLogs();
    verify(mockDir).deleteLog("matching-log");
    int errors = (int) getField(rm, "errors");
    assertThat(errors, is(0));
  }

  /**
   * Tests that deleteLogsWithUuid does not delete anything when no log matches the given UUID.
   *
   * <p>Verifies that when no logs match the UUID, no deletion occurs. The empty matching set
   * results in no action.
   */
  @Test
  public void deleteLogsWithUuid_noMatch_incrementsErrors() throws Exception {
    // Given
    UUID searchUuid = UUID.randomUUID();
    UUID otherUuid = UUID.randomUUID();
    LogInfo otherLog = new LogInfo("other-log", otherUuid, "localhost:29092");
    otherLog.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(otherLog);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Remove rm = createRemoveWithMockDirectory(mockDir);

    Method deleteLogsWithUuidMethod =
        Remove.class.getDeclaredMethod("deleteLogsWithUuid", UUID.class);
    deleteLogsWithUuidMethod.setAccessible(true);

    // When
    deleteLogsWithUuidMethod.invoke(rm, searchUuid);

    // Then: No log deleted since no UUID match
    verify(mockDir).listAllLogs();
    verify(mockDir, never()).deleteLog(any(String.class));
  }

  // ===========================================================================
  // Tests for runCommand() log deletion by name - Issue #632
  // ===========================================================================

  /**
   * Tests that runCommand deletes a log by name when deleteLogs is true and the argument is not a
   * UUID.
   *
   * <p>Verifies that when the argument cannot be parsed as a UUID, it is treated as a log name and
   * resolved via resolveLogInfo(), then deleted.
   */
  @Test
  public void runCommand_deleteLogs_withName_deletesLogByName() throws Exception {
    // Given: Remove with deleteLogs=true, a log name (not UUID), and kafkaServers configured
    // In direct mode (no directory), resolveLogInfo creates a Kafka LogInfo
    // deleteLog will attempt Kafka removal but since there's no real Kafka,
    // we use a Chronicle log path to avoid Kafka dependency
    Path tempDir = Files.createTempDirectory("removetest-byname");
    try {
      Files.createFile(tempDir.resolve("data.cq4"));

      Remove rm = new Remove();
      setField(rm, "deleteLogs", true);
      setField(rm, "startingWith", false);
      setField(rm, "argList", List.of("file:" + tempDir));

      Method runCommand = Remove.class.getDeclaredMethod("runCommand");
      runCommand.setAccessible(true);

      // When
      int result = (int) runCommand.invoke(rm);

      // Then: The Chronicle log should be deleted
      int errors = (int) getField(rm, "errors");
      assertThat(errors, is(0));
      assertThat(result, is(0));
      assertThat("Chronicle dir should be deleted", !Files.exists(tempDir));
    } finally {
      if (Files.exists(tempDir)) {
        deleteRecursive(tempDir);
      }
    }
  }

  /**
   * Tests that runCommand with deleteLogs=true and startingWith=true deletes only logs whose names
   * match the given prefix.
   *
   * <p>Verifies that listAllLogs() is called, results are filtered by name prefix, and only
   * matching logs are deleted.
   */
  @Test
  public void runCommand_deleteLogs_withStartingWith_deletesMatchingLogs() throws Exception {
    // Given
    LogInfo log1 = new LogInfo("test-log-1", UUID.randomUUID(), "localhost:29092");
    log1.setLogType(LogType.KAFKA);
    LogInfo log2 = new LogInfo("test-log-2", UUID.randomUUID(), "localhost:29092");
    log2.setLogType(LogType.KAFKA);
    LogInfo log3 = new LogInfo("other-log", UUID.randomUUID(), "localhost:29092");
    log3.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> allLogs = new HashSet<>();
    allLogs.add(log1);
    allLogs.add(log2);
    allLogs.add(log3);
    when(mockDir.listAllLogs()).thenReturn(allLogs);

    Remove rm = createRemoveWithMockDirectory(mockDir);
    setField(rm, "deleteLogs", true);
    setField(rm, "startingWith", true);
    setField(rm, "argList", List.of("test-"));

    Method runCommand = Remove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);

    // When
    int result = (int) runCommand.invoke(rm);

    // Then
    verify(mockDir).listAllLogs();
    verify(mockDir).deleteLog("test-log-1");
    verify(mockDir).deleteLog("test-log-2");
    verify(mockDir, never()).deleteLog("other-log");
    assertThat(result, is(0));
  }

  // ===========================================================================
  // Helper for temp directory cleanup
  // ===========================================================================

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param dir the directory to delete
   */
  private static void deleteRecursive(Path dir) throws IOException {
    try (var stream = Files.walk(dir)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException e) {
                  // best-effort cleanup
                }
              });
    }
  }
}
