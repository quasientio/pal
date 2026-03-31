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

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
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
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.Test;

/**
 * Unit tests for {@link LogPrune}.
 *
 * <p>LogPrune removes stale log entries (those whose backing store no longer exists) from the PAL
 * directory. These tests verify that only stale logs are pruned while existing logs are left
 * untouched, and that unreachable Kafka clusters produce warnings without affecting Chronicle log
 * processing.
 */
public class LogPruneTest {

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
   * Creates a LogPrune instance with a mock PalDirectory, mock Kafka Admin, and wired output
   * streams.
   *
   * @param mockDir the mock PalDirectory to inject
   * @param mockAdmin the mock Kafka Admin client to use
   * @param bout the output stream to capture standard output
   * @param berr the output stream to capture error output
   * @return a configured LogPrune instance
   */
  private static LogPrune createWithMocks(
      PalDirectory mockDir, Admin mockAdmin, ByteArrayOutputStream bout, ByteArrayOutputStream berr)
      throws Exception {
    LogPrune cmd = new LogPrune();
    DirectoryConnectionProvider dcp = mock(DirectoryConnectionProvider.class);
    when(dcp.get()).thenReturn(Optional.of(mockDir));
    setField(cmd, "directoryConnectionProvider", dcp);
    setField(cmd, "out", new PrintStream(bout));
    setField(cmd, "err", new PrintStream(berr));
    KafkaAdminHelper kafkaHelper = new KafkaAdminHelper(props -> mockAdmin);
    setField(cmd, "kafkaAdminHelper", kafkaHelper);
    return cmd;
  }

  /**
   * Creates a mock Kafka Admin that returns the given topic names from listTopics().
   *
   * @param topicNames the set of topic names to return
   * @return a mock Admin client
   */
  private static Admin createMockKafkaAdmin(Set<String> topicNames) {
    Admin mockAdmin = mock(Admin.class);
    ListTopicsResult mockResult = mock(ListTopicsResult.class);
    KafkaFutureImpl<Set<String>> future = new KafkaFutureImpl<>();
    future.complete(topicNames);
    when(mockResult.names()).thenReturn(future);
    when(mockAdmin.listTopics()).thenReturn(mockResult);
    return mockAdmin;
  }

  /**
   * Creates a mock Kafka Admin whose listTopics() throws an ExecutionException.
   *
   * @return a mock Admin client that simulates an unreachable Kafka cluster
   */
  private static Admin createUnreachableKafkaAdmin() {
    Admin mockAdmin = mock(Admin.class);
    ListTopicsResult mockResult = mock(ListTopicsResult.class);
    KafkaFutureImpl<Set<String>> future = new KafkaFutureImpl<>();
    future.completeExceptionally(new RuntimeException("Connection refused"));
    when(mockResult.names()).thenReturn(future);
    when(mockAdmin.listTopics()).thenReturn(mockResult);
    return mockAdmin;
  }

  /**
   * Invokes the runCommand() method on a LogPrune instance via reflection.
   *
   * @param cmd the LogPrune instance
   * @return the exit code
   */
  private static int invokeRunCommand(LogPrune cmd) throws Exception {
    Method runCommand = LogPrune.class.getDeclaredMethod("runCommand");
    runCommand.setAccessible(true);
    return (int) runCommand.invoke(cmd);
  }

  // ==================== Tests ====================

  /** Tests that stale Kafka logs (topic no longer exists) are pruned. */
  @Test
  public void runCommand_prunesStaleKafkaLogs() throws Exception {
    UUID staleUuid = UUID.randomUUID();
    UUID existingUuid = UUID.randomUUID();
    LogInfo staleLog = new LogInfo("deleted-topic", staleUuid, "localhost:29092");
    staleLog.setLogType(LogType.KAFKA);
    LogInfo existingLog = new LogInfo("existing-topic", existingUuid, "localhost:29092");
    existingLog.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(staleLog);
    logs.add(existingLog);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Admin mockAdmin = createMockKafkaAdmin(Set.of("existing-topic"));

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    LogPrune cmd = createWithMocks(mockDir, mockAdmin, bout, berr);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    verify(mockDir).deleteLog(staleUuid);
    verify(mockDir, never()).deleteLog(existingUuid);
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("deleted-topic"));
    assertThat(output, containsString("Pruned 1 stale log(s)"));
    assertThat(output, not(containsString("existing-topic")));
  }

  /** Tests that stale Chronicle logs (queue directory no longer exists) are pruned. */
  @Test
  public void runCommand_prunesStaleChronicleLog() throws Exception {
    UUID staleUuid = UUID.randomUUID();
    LogInfo staleLog = new LogInfo("/tmp/nonexistent-chronicle-queue-" + staleUuid);
    staleLog.setUuid(staleUuid);
    staleLog.setLogType(LogType.CHRONICLE);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(staleLog);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Admin mockAdmin = createMockKafkaAdmin(Set.of());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    LogPrune cmd = createWithMocks(mockDir, mockAdmin, bout, berr);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    verify(mockDir).deleteLog(staleUuid);
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("Pruned 1 stale log(s)"));
  }

  /** Tests that no logs are deleted when all logs exist in their backing stores. */
  @Test
  public void runCommand_noStaleLogs_noPruning() throws Exception {
    UUID kafkaUuid = UUID.randomUUID();
    LogInfo kafkaLog = new LogInfo("active-topic", kafkaUuid, "localhost:29092");
    kafkaLog.setLogType(LogType.KAFKA);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(kafkaLog);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Admin mockAdmin = createMockKafkaAdmin(Set.of("active-topic"));

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    LogPrune cmd = createWithMocks(mockDir, mockAdmin, bout, berr);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    verify(mockDir, never()).deleteLog(any(UUID.class));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("No stale logs found"));
  }

  /** Tests that an empty directory produces no deletions and a clean message. */
  @Test
  public void runCommand_emptyDirectory_succeeds() throws Exception {
    PalDirectory mockDir = mock(PalDirectory.class);
    when(mockDir.listAllLogs()).thenReturn(new HashSet<>());

    Admin mockAdmin = createMockKafkaAdmin(Set.of());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    LogPrune cmd = createWithMocks(mockDir, mockAdmin, bout, berr);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    verify(mockDir, never()).deleteLog(any(UUID.class));
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("No stale logs found"));
  }

  /**
   * Tests that when Kafka is unreachable, a warning is printed and Chronicle logs are still
   * processed.
   */
  @Test
  public void runCommand_kafkaUnreachable_warnsAndProcessesChronicle() throws Exception {
    UUID kafkaUuid = UUID.randomUUID();
    LogInfo kafkaLog = new LogInfo("some-topic", kafkaUuid, "unreachable:29092");
    kafkaLog.setLogType(LogType.KAFKA);

    UUID chronicleUuid = UUID.randomUUID();
    LogInfo chronicleLog = new LogInfo("/tmp/nonexistent-chronicle-queue-" + chronicleUuid);
    chronicleLog.setUuid(chronicleUuid);
    chronicleLog.setLogType(LogType.CHRONICLE);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(kafkaLog);
    logs.add(chronicleLog);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Admin mockAdmin = createUnreachableKafkaAdmin();

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    LogPrune cmd = createWithMocks(mockDir, mockAdmin, bout, berr);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    // Kafka log skipped, Chronicle log pruned
    verify(mockDir, never()).deleteLog(kafkaUuid);
    verify(mockDir).deleteLog(chronicleUuid);
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("Pruned 1 stale log(s)"));
    String errOutput = berr.toString(UTF_8);
    assertThat(errOutput, containsString("Warning: Cannot connect to Kafka"));
    assertThat(errOutput, containsString("unreachable:29092"));
    assertThat(errOutput, containsString("Skipping 1 Kafka log(s)"));
  }

  /** Tests that both Kafka and Chronicle stale logs are pruned in a single run. */
  @Test
  public void runCommand_prunesMixedStaleKafkaAndChronicle() throws Exception {
    UUID kafkaUuid = UUID.randomUUID();
    LogInfo kafkaLog = new LogInfo("gone-topic", kafkaUuid, "localhost:29092");
    kafkaLog.setLogType(LogType.KAFKA);

    UUID chronicleUuid = UUID.randomUUID();
    LogInfo chronicleLog = new LogInfo("/tmp/nonexistent-chronicle-queue-" + chronicleUuid);
    chronicleLog.setUuid(chronicleUuid);
    chronicleLog.setLogType(LogType.CHRONICLE);

    PalDirectory mockDir = mock(PalDirectory.class);
    Set<LogInfo> logs = new HashSet<>();
    logs.add(kafkaLog);
    logs.add(chronicleLog);
    when(mockDir.listAllLogs()).thenReturn(logs);

    Admin mockAdmin = createMockKafkaAdmin(Set.of());

    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ByteArrayOutputStream berr = new ByteArrayOutputStream();
    LogPrune cmd = createWithMocks(mockDir, mockAdmin, bout, berr);

    int result = invokeRunCommand(cmd);

    assertThat(result, is(0));
    verify(mockDir).deleteLog(kafkaUuid);
    verify(mockDir).deleteLog(chronicleUuid);
    String output = bout.toString(UTF_8);
    assertThat(output, containsString("Pruned 2 stale log(s)"));
  }
}
