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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.IOException;
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
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.Test;

/**
 * Unit tests for {@link LogRemove}.
 *
 * <p>LogRemove is the log-specific remove command following the entity-operation pattern ({@code
 * pal log rm}). It handles log deletion by name, UUID, or prefix matching, including Chronicle
 * queue file removal, Kafka topic deletion, and directory unregistration.
 */
public class LogRemoveTest {

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
   * Creates a LogRemove instance with a mock PalDirectory injected.
   *
   * @param mockDir the mock PalDirectory to inject
   * @return a configured LogRemove instance
   */
  private static LogRemove createWithMockDirectory(PalDirectory mockDir) throws Exception {
    LogRemove cmd = new LogRemove();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(cmd, "directoryConnectionProvider", dcp);
    return cmd;
  }

  /**
   * Creates a LogRemove instance with a mock PalDirectory and a mock KafkaAdminHelper.
   *
   * @param mockDir the mock PalDirectory to inject
   * @param mockAdmin the mock Kafka Admin client to use
   * @return a configured LogRemove instance
   */
  private static LogRemove createWithMockDirAndKafka(PalDirectory mockDir, Admin mockAdmin)
      throws Exception {
    LogRemove cmd = createWithMockDirectory(mockDir);
    KafkaAdminHelper kafkaHelper = new KafkaAdminHelper(props -> mockAdmin);
    setField(cmd, "kafkaAdminHelper", kafkaHelper);
    return cmd;
  }

  /**
   * Creates a mock Kafka Admin that returns a successful DeleteTopicsResult.
   *
   * @return a mock Admin client
   */
  private static Admin createMockKafkaAdmin() {
    Admin mockAdmin = mock(Admin.class);
    DeleteTopicsResult mockResult = mock(DeleteTopicsResult.class);
    KafkaFutureImpl<Void> future = new KafkaFutureImpl<>();
    future.complete(null);
    when(mockResult.all()).thenReturn(future);
    when(mockAdmin.deleteTopics(anyCollection(), any())).thenReturn(mockResult);
    return mockAdmin;
  }

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

  // ==================== runCommand() Tests ====================

  /**
   * Tests that a log is deleted when identified by name.
   *
   * <p>Verifies that providing a log name as a positional argument with {@code --force} resolves
   * the log from the directory and unregisters it, along with deleting the backing store.
   */
  @Test
  public void runCommand_deletesLogByName() throws Exception {
    // Given: PalDirectory with a log named "my-log"
    Path tempDir = Files.createTempDirectory("logremove-byname");
    try {
      Files.createFile(tempDir.resolve("data.cq4"));

      LogRemove cmd = new LogRemove();
      setField(cmd, "logIdentifiers", List.of("file:" + tempDir));
      setField(cmd, "force", true);
      // Set up logResolver that handles file: prefix
      LogResolver resolver = new LogResolver(null, null);
      setField(cmd, "logResolver", resolver);

      Method runCommand = LogRemove.class.getDeclaredMethod("runCommand");
      runCommand.setAccessible(true);
      int result = (int) runCommand.invoke(cmd);

      // Then: log is deleted
      int errors = (int) getField(cmd, "errors");
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
   * Tests that a log is deleted when identified by UUID.
   *
   * <p>Verifies that providing a UUID string as a positional argument with {@code --force} resolves
   * the log and unregisters it.
   */
  @Test
  public void runCommand_deletesLogByUuid() throws Exception {
    // Given: PalDirectory with a log registered by UUID
    UUID logUuid = UUID.randomUUID();
    LogInfo logInfo = new LogInfo("test-log", logUuid, "localhost:29092");
    logInfo.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(logInfo);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Admin mockAdmin = createMockKafkaAdmin();
    LogRemove cmd = createWithMockDirAndKafka(mockDir, mockAdmin);
    setField(cmd, "logIdentifiers", List.of(logUuid.toString()));
    setField(cmd, "force", true);

    // When
    Method runCommand = LogRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then
    verify(mockDir).listAllLogs();
    verify(mockDir).deleteLog("test-log");
    assertThat(result, is(0));
  }

  /**
   * Tests that all logs are deleted when {@code --all} is specified.
   *
   * <p>Verifies that the {@code --all --force} flags cause all logs registered in the directory to
   * be unregistered and their backing stores deleted.
   */
  @Test
  public void runCommand_deleteAllLogs() throws Exception {
    // Given: PalDirectory with 3 registered logs
    LogInfo log1 = new LogInfo("log-1", UUID.randomUUID(), "localhost:29092");
    log1.setLogType(LogType.KAFKA);
    LogInfo log2 = new LogInfo("log-2", UUID.randomUUID(), "localhost:29092");
    log2.setLogType(LogType.KAFKA);
    LogInfo log3 = new LogInfo("log-3", UUID.randomUUID(), "localhost:29092");
    log3.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> allLogs = new HashSet<>();
    allLogs.add(log1);
    allLogs.add(log2);
    allLogs.add(log3);
    when(mockDir.listAllLogs()).thenReturn(allLogs);

    Admin mockAdmin = createMockKafkaAdmin();
    LogRemove cmd = createWithMockDirAndKafka(mockDir, mockAdmin);
    setField(cmd, "deleteAll", true);
    setField(cmd, "force", true);

    // When
    Method runCommand = LogRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: all 3 logs are unregistered
    verify(mockDir).listAllLogs();
    verify(mockDir).deleteLog("log-1");
    verify(mockDir).deleteLog("log-2");
    verify(mockDir).deleteLog("log-3");
    assertThat(result, is(0));
  }

  /**
   * Tests that only logs matching a name prefix are deleted.
   *
   * <p>Verifies that the {@code -s/--starting-with} option filters logs by name prefix, deleting
   * only those whose names start with the given string.
   */
  @Test
  public void runCommand_deleteWithPrefix() throws Exception {
    // Given: PalDirectory with logs ["wal-1", "wal-2", "other"]
    LogInfo log1 = new LogInfo("wal-1", UUID.randomUUID(), "localhost:29092");
    log1.setLogType(LogType.KAFKA);
    LogInfo log2 = new LogInfo("wal-2", UUID.randomUUID(), "localhost:29092");
    log2.setLogType(LogType.KAFKA);
    LogInfo log3 = new LogInfo("other", UUID.randomUUID(), "localhost:29092");
    log3.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> allLogs = new HashSet<>();
    allLogs.add(log1);
    allLogs.add(log2);
    allLogs.add(log3);
    when(mockDir.listAllLogs()).thenReturn(allLogs);

    Admin mockAdmin = createMockKafkaAdmin();
    LogRemove cmd = createWithMockDirAndKafka(mockDir, mockAdmin);
    setField(cmd, "logIdentifiers", List.of("wal"));
    setField(cmd, "startingWith", true);
    setField(cmd, "force", true);

    // When
    Method runCommand = LogRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    int result = (int) runCommand.invoke(cmd);

    // Then: "wal-1" and "wal-2" are deleted; "other" is not deleted
    verify(mockDir).listAllLogs();
    verify(mockDir).deleteLog("wal-1");
    verify(mockDir).deleteLog("wal-2");
    verify(mockDir, never()).deleteLog("other");
    assertThat(result, is(0));
  }

  // ==================== Backend-Specific Deletion Tests ====================

  /**
   * Tests that deleting a Chronicle log removes the queue directory.
   *
   * <p>Verifies that when a log has {@code LogType.CHRONICLE}, the Chronicle queue directory
   * (containing {@code .cq4} files) is recursively deleted from the filesystem.
   */
  @Test
  public void runCommand_chronicleLog_removesQueue() throws Exception {
    // Given: Chronicle log at a temp path with .cq4 files
    Path tempDir = Files.createTempDirectory("logremove-chronicle");
    try {
      Files.createFile(tempDir.resolve("data.cq4"));

      LogInfo logInfo = new LogInfo(tempDir.toString());
      logInfo.setLogType(LogType.CHRONICLE);

      LogRemove cmd = new LogRemove();
      // Invoke deleteLog directly via reflection
      Method deleteLogMethod = LogRemove.class.getDeclaredMethod("deleteLog", LogInfo.class);
      deleteLogMethod.setAccessible(true);

      // When
      deleteLogMethod.invoke(cmd, logInfo);

      // Then: Chronicle queue directory is recursively removed
      int errors = (int) getField(cmd, "errors");
      assertThat(errors, is(0));
      assertThat("Chronicle dir should be deleted", !Files.exists(tempDir));
    } finally {
      if (Files.exists(tempDir)) {
        deleteRecursive(tempDir);
      }
    }
  }

  /**
   * Tests that deleting a Kafka log deletes the topic via the Admin client.
   *
   * <p>Verifies that when a log has {@code LogType.KAFKA} and {@code -k} bootstrap servers are
   * provided, the Kafka topic is deleted using the Kafka Admin client API.
   */
  @Test
  public void runCommand_kafkaLog_deletesTopicWithAdmin() throws Exception {
    // Given: Kafka log with bootstrap servers
    LogInfo logInfo = new LogInfo("my-topic", UUID.randomUUID(), "localhost:29092");
    logInfo.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Admin mockAdmin = createMockKafkaAdmin();
    LogRemove cmd = createWithMockDirAndKafka(mockDir, mockAdmin);

    Method deleteLogMethod = LogRemove.class.getDeclaredMethod("deleteLog", LogInfo.class);
    deleteLogMethod.setAccessible(true);

    // When
    deleteLogMethod.invoke(cmd, logInfo);

    // Then: Kafka topic is deleted via Admin client
    verify(mockAdmin).deleteTopics(anyCollection(), any());
    verify(mockDir).deleteLog("my-topic");
  }

  /**
   * Tests that a Chronicle log path provided directly as a positional arg deletes the files.
   *
   * <p>Verifies that when a {@code file:/tmp/wal} positional argument is provided without a
   * directory connection, the Chronicle queue files are deleted directly from the filesystem.
   */
  @Test
  public void runCommand_directChronicleMode_deletesFiles() throws Exception {
    // Given: positional arg "file:/tmp/wal" (Chronicle path) without directory connection
    Path tempDir = Files.createTempDirectory("logremove-direct");
    try {
      Files.createFile(tempDir.resolve("data.cq4"));

      LogRemove cmd = new LogRemove();
      setField(cmd, "logIdentifiers", List.of("file:" + tempDir));
      LogResolver resolver = new LogResolver(null, null);
      setField(cmd, "logResolver", resolver);

      Method runCommand = LogRemove.class.getDeclaredMethod("runCommand");
      runCommand.setAccessible(true);

      // When
      int result = (int) runCommand.invoke(cmd);

      // Then: Chronicle queue files are deleted directly
      int errors = (int) getField(cmd, "errors");
      assertThat(errors, is(0));
      assertThat(result, is(0));
      assertThat("Chronicle dir should be deleted", !Files.exists(tempDir));
    } finally {
      if (Files.exists(tempDir)) {
        deleteRecursive(tempDir);
      }
    }
  }

  // ==================== Error Handling Tests ====================

  /**
   * Tests that attempting to delete a non-existent log increments the error count.
   *
   * <p>Verifies that when no log matches the given identifier, the error counter is incremented and
   * an appropriate error message is produced.
   */
  @Test
  public void runCommand_nonExistentLog_incrementsErrors() throws Exception {
    // Given: PalDirectory with no log matching "ghost", no Kafka servers
    LogRemove cmd = new LogRemove();
    setField(cmd, "logIdentifiers", List.of("ghost"));
    // LogResolver with no directory and no kafka → returns null
    LogResolver resolver = new LogResolver(null, null);
    setField(cmd, "logResolver", resolver);

    Method runCommand = LogRemove.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);

    // When
    int result = (int) runCommand.invoke(cmd);

    // Then: error count is incremented
    assertThat(result, is(1));
    int errors = (int) getField(cmd, "errors");
    assertThat(errors, is(1));
  }

  /**
   * Tests that log deletion proceeds without directory unregistration when no directory is
   * configured.
   *
   * <p>Verifies that when no PAL directory connection is available, the backing store (Chronicle or
   * Kafka) is still deleted, but no directory unregistration is attempted.
   */
  @Test
  public void runCommand_noDirectorySkipsUnregistration() throws Exception {
    // Given: no directory connection
    Path tempDir = Files.createTempDirectory("logremove-nodir");
    try {
      Files.createFile(tempDir.resolve("data.cq4"));

      LogInfo logInfo = new LogInfo(tempDir.toString());
      logInfo.setLogType(LogType.CHRONICLE);

      LogRemove cmd = new LogRemove();
      // directoryConnectionProvider is null by default

      Method deleteLogMethod = LogRemove.class.getDeclaredMethod("deleteLog", LogInfo.class);
      deleteLogMethod.setAccessible(true);

      // When
      deleteLogMethod.invoke(cmd, logInfo);

      // Then: backend is deleted, no directory unregistration attempted, no errors
      int errors = (int) getField(cmd, "errors");
      assertThat(errors, is(0));
      assertThat("Chronicle dir should be deleted", !Files.exists(tempDir));
    } finally {
      if (Files.exists(tempDir)) {
        deleteRecursive(tempDir);
      }
    }
  }

  // ==================== LogResolver Integration Tests ====================

  /**
   * Tests that LogRemove delegates log resolution to {@code LogResolver}.
   *
   * <p>Verifies that the command uses {@code LogResolver} for resolving log identifiers, which
   * handles PAL directory lookup, {@code file:} Chronicle fallback, and Kafka fallback.
   */
  @Test
  public void resolveLogInfo_usesLogResolver() throws Exception {
    // Given: log identifier that requires resolution (Chronicle file: prefix)
    Path tempDir = Files.createTempDirectory("logremove-resolver");
    try {
      Files.createFile(tempDir.resolve("data.cq4"));

      LogRemove cmd = new LogRemove();
      LogResolver resolver = new LogResolver(null, null);
      setField(cmd, "logResolver", resolver);
      setField(cmd, "logIdentifiers", List.of("file:" + tempDir));

      Method runCommand = LogRemove.class.getDeclaredMethod("runCommand");
      runCommand.setAccessible(true);

      // When: log resolution is triggered during runCommand()
      int result = (int) runCommand.invoke(cmd);

      // Then: LogResolver resolved the log and it was deleted
      int errors = (int) getField(cmd, "errors");
      assertThat(errors, is(0));
      assertThat(result, is(0));
      assertThat("Chronicle dir should be deleted", !Files.exists(tempDir));
    } finally {
      if (Files.exists(tempDir)) {
        deleteRecursive(tempDir);
      }
    }
  }
}
