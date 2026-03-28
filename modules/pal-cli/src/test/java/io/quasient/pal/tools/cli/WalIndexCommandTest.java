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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ClassMethodCall;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.ReturnValue;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Unit tests for the {@link WalIndexCommand} CLI command.
 *
 * <p>Tests cover argument parsing, input validation, summary output formatting, verbose output
 * formatting, and the static signature formatting method.
 */
public class WalIndexCommandTest {

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
  private static Field findField(java.lang.Class<?> clazz, String name)
      throws NoSuchFieldException {
    java.lang.Class<?> current = clazz;
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
   * Creates a WalIndexCommand instance with fields populated via picocli parsing.
   *
   * @param args the command-line arguments to parse
   * @return a configured WalIndexCommand instance
   */
  private static WalIndexCommand parseCommand(String... args) {
    WalIndexCommand cmd = new WalIndexCommand();
    new CommandLine(cmd).parseArgs(args);
    return cmd;
  }

  /**
   * Creates a synthetic OPERATION {@link WalEntry} using an {@link InstanceMethodCall}-based {@link
   * ExecMessage}.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new operation entry
   */
  private static WalEntry makeOperation(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    InstanceMethodCall imc = new InstanceMethodCall();
    imc.setName("doWork");
    Class clazz = new Class();
    clazz.setName("com.example.Worker");
    imc.setClazz(clazz);
    msg.setInstanceMethodCall(imc);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a synthetic OPERATION {@link WalEntry} using a {@link ClassMethodCall} with parameters.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @param className the class name
   * @param methodName the method name
   * @param paramTypes the parameter types
   * @return a new operation entry with parameters
   */
  private static WalEntry makeOperationWithParams(
      long offset,
      String threadName,
      int builderSeq,
      String className,
      String methodName,
      String... paramTypes) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    ClassMethodCall cmc = new ClassMethodCall();
    cmc.setName(methodName);
    Class clazz = new Class();
    clazz.setName(className);
    cmc.setClazz(clazz);
    Obj[] args = new Obj[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      args[i] = new Obj();
      Class paramClass = new Class();
      paramClass.setName(paramTypes[i]);
      args[i].setClazz(paramClass);
    }
    cmc.setArgs(args);
    msg.setClassMethodCall(cmc);
    return WalEntry.fromExecMessage(offset, msg);
  }

  /**
   * Creates a synthetic COMPLETION {@link WalEntry} using a {@link ReturnValue}-based {@link
   * ExecMessage}.
   *
   * @param offset the WAL offset
   * @param threadName the thread name
   * @param builderSeq the builder sequence number
   * @return a new completion entry
   */
  private static WalEntry makeCompletion(long offset, String threadName, int builderSeq) {
    ExecMessage msg = new ExecMessage();
    msg.setThreadName(threadName);
    msg.setBuilderSeq(builderSeq);
    ReturnValue rv = new ReturnValue();
    rv.setIsVoid(true);
    msg.setReturnValue(rv);
    return WalEntry.fromExecMessage(offset, msg);
  }

  // ===========================================================================
  // Argument parsing tests
  // ===========================================================================

  /** Verifies that the log path positional parameter is correctly parsed. */
  @Test
  public void testParseLogPath() throws Exception {
    WalIndexCommand cmd = parseCommand("file:/tmp/my-wal");
    assertThat(getField(cmd, "logPath"), is("file:/tmp/my-wal"));
  }

  /** Verifies that the --verbose flag is parsed correctly. */
  @Test
  public void testParseVerboseFlag() throws Exception {
    WalIndexCommand cmd = parseCommand("--verbose", "file:/tmp/my-wal");
    assertThat(getField(cmd, "verbose"), is(true));
  }

  /** Verifies that the short -v flag is parsed correctly. */
  @Test
  public void testParseShortVerboseFlag() throws Exception {
    WalIndexCommand cmd = parseCommand("-v", "file:/tmp/my-wal");
    assertThat(getField(cmd, "verbose"), is(true));
  }

  /** Verifies that verbose defaults to false when not specified. */
  @Test
  public void testVerboseDefaultsFalse() throws Exception {
    WalIndexCommand cmd = parseCommand("file:/tmp/my-wal");
    assertThat(getField(cmd, "verbose"), is(false));
  }

  // ===========================================================================
  // Validation tests
  // ===========================================================================

  /** Verifies that validateInput accepts paths with the file: prefix. */
  @Test
  public void testValidateInputAcceptsFilePrefix() throws Exception {
    WalIndexCommand cmd = parseCommand("file:/tmp/my-wal");
    cmd.validateInput(); // should not throw
  }

  /** Verifies that validateInput rejects paths without the file: prefix. */
  @Test
  public void testValidateInputRejectsNoFilePrefix() {
    WalIndexCommand cmd = parseCommand("kafka-topic-name");
    RuntimeException e = assertThrows(RuntimeException.class, cmd::validateInput);
    assertThat(e.getMessage(), containsString("file:"));
  }

  /** Verifies that validateInput rejects paths with an incorrect prefix. */
  @Test
  public void testValidateInputRejectsWrongPrefix() {
    WalIndexCommand cmd = parseCommand("/tmp/my-wal");
    RuntimeException e = assertThrows(RuntimeException.class, cmd::validateInput);
    assertThat(e.getMessage(), containsString("file:"));
  }

  // ===========================================================================
  // printSummary tests
  // ===========================================================================

  /** Verifies the summary output for a simple paired WAL. */
  @Test
  public void testPrintSummarySimplePaired() throws Exception {
    // Given
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeCompletion(1, "self-caller", 2),
            makeOperation(2, "self-caller", 3),
            makeCompletion(3, "self-caller", 4));
    WalIndex index = WalIndex.build(entries);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos, true, StandardCharsets.UTF_8);

    WalIndexCommand cmd = new WalIndexCommand();
    setField(cmd, "out", printStream);

    // When
    cmd.printSummary(index);

    // Then
    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("WAL Index Summary"));
    assertThat(output, containsString("Entries:       4"));
    assertThat(output, containsString("Operations:    2"));
    assertThat(output, containsString("Completions:   2"));
    assertThat(output, containsString("Entry points:  0"));
    assertThat(output, containsString("Pairs:         2"));
    assertThat(output, containsString("self-caller"));
    assertThat(output, containsString("Issues:        0"));
    assertThat(output, not(containsString("Structural Issues")));
  }

  /** Verifies the summary output includes structural issues when present. */
  @Test
  public void testPrintSummaryWithIssues() throws Exception {
    // Given - orphaned completion
    List<WalEntry> entries = Collections.singletonList(makeCompletion(0, "self-caller", 1));
    WalIndex index = WalIndex.build(entries);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos, true, StandardCharsets.UTF_8);

    WalIndexCommand cmd = new WalIndexCommand();
    setField(cmd, "out", printStream);

    // When
    cmd.printSummary(index);

    // Then
    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("Issues:        1"));
    assertThat(output, containsString("Structural Issues"));
    assertThat(output, containsString("Orphaned completion at offset 0"));
  }

  /** Verifies the summary output for an empty WAL. */
  @Test
  public void testPrintSummaryEmptyWal() throws Exception {
    // Given
    WalIndex index = WalIndex.build(Collections.emptyList());

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos, true, StandardCharsets.UTF_8);

    WalIndexCommand cmd = new WalIndexCommand();
    setField(cmd, "out", printStream);

    // When
    cmd.printSummary(index);

    // Then
    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("Entries:       0"));
    assertThat(output, containsString("Operations:    0"));
    assertThat(output, containsString("Completions:   0"));
    assertThat(output, containsString("Entry points:  0"));
    assertThat(output, containsString("Pairs:         0"));
    assertThat(output, containsString("Issues:        0"));
  }

  /** Verifies the summary lists multiple threads. */
  @Test
  public void testPrintSummaryMultipleThreads() throws Exception {
    // Given
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "thread-a", 1),
            makeCompletion(1, "thread-a", 2),
            makeOperation(2, "thread-b", 3),
            makeCompletion(3, "thread-b", 4));
    WalIndex index = WalIndex.build(entries);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos, true, StandardCharsets.UTF_8);

    WalIndexCommand cmd = new WalIndexCommand();
    setField(cmd, "out", printStream);

    // When
    cmd.printSummary(index);

    // Then
    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("thread-a"));
    assertThat(output, containsString("thread-b"));
  }

  // ===========================================================================
  // printVerboseEntries tests
  // ===========================================================================

  /** Verifies the verbose entry listing output format. */
  @Test
  public void testPrintVerboseEntries() throws Exception {
    // Given
    List<WalEntry> entries =
        Arrays.asList(makeOperation(0, "self-caller", 1), makeCompletion(1, "self-caller", 2));
    WalIndex index = WalIndex.build(entries);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos, true, StandardCharsets.UTF_8);

    WalIndexCommand cmd = new WalIndexCommand();
    setField(cmd, "out", printStream);

    // When
    cmd.printVerboseEntries(index);

    // Then
    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("[0] OPERATION self-caller"));
    assertThat(output, containsString("[1] COMPLETION self-caller"));
    assertThat(output, containsString("com.example.Worker.doWork"));
  }

  // ===========================================================================
  // formatSignature tests
  // ===========================================================================

  /** Verifies that an operation entry with params formats as className.method(params). */
  @Test
  public void testFormatSignatureWithParams() {
    WalEntry entry = makeOperationWithParams(0, "t", 1, "com.example.Calc", "add", "int", "int");
    String sig = WalIndexCommand.formatSignature(entry);
    assertThat(sig, is("com.example.Calc.add(int,int)"));
  }

  /** Verifies that an operation entry with no params formats as className.method. */
  @Test
  public void testFormatSignatureNoParams() {
    WalEntry entry = makeOperationWithParams(0, "t", 1, "com.example.Calc", "reset");
    String sig = WalIndexCommand.formatSignature(entry);
    assertThat(sig, is("com.example.Calc.reset()"));
  }

  /** Verifies that a completion entry with no executable name formats as just className. */
  @Test
  public void testFormatSignatureCompletion() {
    WalEntry entry = makeCompletion(0, "t", 1);
    String sig = WalIndexCommand.formatSignature(entry);
    // Completion entries have no executable name in most cases, just class name
    // ReturnValue-based completions have getFromExecutableName which may return null
    assertThat(sig.isEmpty(), is(false));
  }

  /** Verifies that closeResources does not throw (no-op). */
  @Test
  public void testCloseResourcesNoOp() throws Exception {
    WalIndexCommand cmd = new WalIndexCommand();
    cmd.closeResources(); // should not throw
  }

  /** Verifies that initialize does not throw (no-op). */
  @Test
  public void testInitializeNoOp() throws Exception {
    WalIndexCommand cmd = new WalIndexCommand();
    cmd.initialize(); // should not throw
  }

  /** Verifies that the CHRONICLE_FILE_PREFIX constant has the expected value. */
  @Test
  public void testChronicleFilePrefixConstant() {
    assertThat(WalIndexCommand.CHRONICLE_FILE_PREFIX, is("file:"));
  }

  /**
   * Verifies operation count and completion count in the summary reflect correct categorization.
   */
  @Test
  public void testPrintSummaryCounts() throws Exception {
    // Given - 3 operations, 3 completions
    List<WalEntry> entries =
        Arrays.asList(
            makeOperation(0, "self-caller", 1),
            makeOperation(1, "self-caller", 2),
            makeOperation(2, "self-caller", 3),
            makeCompletion(3, "self-caller", 4),
            makeCompletion(4, "self-caller", 5),
            makeCompletion(5, "self-caller", 6));
    WalIndex index = WalIndex.build(entries);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos, true, StandardCharsets.UTF_8);

    WalIndexCommand cmd = new WalIndexCommand();
    setField(cmd, "out", printStream);

    // When
    cmd.printSummary(index);

    // Then
    String output = baos.toString(StandardCharsets.UTF_8);
    assertThat(output, containsString("Entries:       6"));
    assertThat(output, containsString("Operations:    3"));
    assertThat(output, containsString("Completions:   3"));
    assertThat(output, containsString("Entry points:  0"));
    assertThat(output, containsString("Pairs:         3"));
  }

  // ===========================================================================
  // Kafka and PalDirectory support tests
  // ===========================================================================

  /** Verifies that the --kafka-servers option is correctly parsed via picocli. */
  @Test
  public void testParseKafkaServersOption() throws Exception {
    WalIndexCommand cmd = parseCommand("--kafka-servers", "localhost:29092", "file:/tmp/wal");
    assertThat(getField(cmd, "kafkaServers"), is("localhost:29092"));
    assertThat(getField(cmd, "logPath"), is("file:/tmp/wal"));
  }

  /** Verifies that the short -k form of the Kafka servers option is correctly parsed. */
  @Test
  public void testParseShortKafkaServersOption() throws Exception {
    WalIndexCommand cmd = parseCommand("-k", "localhost:29092", "my-topic");
    assertThat(getField(cmd, "kafkaServers"), is("localhost:29092"));
    assertThat(getField(cmd, "logPath"), is("my-topic"));
  }

  /** Verifies that validateInput accepts a Kafka topic name when --kafka-servers is provided. */
  @Test
  public void testValidateInputAcceptsKafkaTopicWithServers() {
    WalIndexCommand cmd = parseCommand("-k", "localhost:29092", "my-topic");
    cmd.validateInput(); // should not throw
  }

  /**
   * Verifies that validateInput still accepts Chronicle paths without Kafka servers (backward
   * compatibility).
   */
  @Test
  public void testValidateInputAcceptsChroniclePathWithoutServers() {
    WalIndexCommand cmd = parseCommand("file:/tmp/my-wal");
    cmd.validateInput(); // should not throw
  }

  /**
   * Verifies that validateInput rejects a Kafka topic name when neither --kafka-servers nor
   * PalDirectory is configured.
   */
  @Test
  public void testValidateInputRejectsKafkaTopicWithoutServersAndWithoutDirectory() {
    WalIndexCommand cmd = parseCommand("my-topic");
    RuntimeException e = assertThrows(RuntimeException.class, cmd::validateInput);
    assertThat(e.getMessage(), containsString("--kafka-servers"));
    assertThat(e.getMessage(), containsString("-d"));
  }

  /**
   * Verifies that validateInput rejects paths with an incorrect prefix when no Kafka servers or
   * PalDirectory is configured.
   */
  @Test
  public void testValidateInputRejectsWrongPrefixWithoutServers() {
    WalIndexCommand cmd = parseCommand("http://something");
    RuntimeException e = assertThrows(RuntimeException.class, cmd::validateInput);
    assertThat(e.getMessage(), containsString("--kafka-servers"));
  }

  /**
   * Verifies that validateInput accepts a Kafka topic name when PalDirectory is available (via
   * {@code @ParentCommand} mock), even without explicit --kafka-servers. The topic will be resolved
   * via PalDirectory at runtime.
   */
  @Test
  public void testValidateInputAcceptsTopicWithPalDirectory() throws Exception {
    WalIndexCommand cmd = parseCommand("my-topic");
    // Simulate a parent command with a PalDirectory URL
    setField(cmd, "palCommand", (PalCommand) () -> "localhost:2379");
    cmd.validateInput(); // should not throw
  }
}
