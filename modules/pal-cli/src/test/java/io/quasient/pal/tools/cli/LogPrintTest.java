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
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.WireType;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Unit tests for {@code LogPrint}.
 *
 * <p>LogPrint is the log-specific print command extracted from {@code MessageStreamPrinter} to
 * follow the entity-operation pattern ({@code pal log print}). It handles printing messages from
 * Kafka topics or Chronicle Queue logs, including offset-based starting, follow mode, return value
 * printing, and type/peer filtering. The log identifier is a positional argument (replacing the
 * former {@code -l/--log} option).
 *
 * @see AbstractPrintCommand
 * @see LogPrint
 */
public class LogPrintTest {

  // ==================== runCommand() Tests ====================

  /**
   * Tests that a positional log name is parsed and stored correctly.
   *
   * <p>Verifies that providing a Kafka topic name as the positional argument causes it to be stored
   * in the logIdentifier field.
   */
  @Test
  public void runCommand_withPositionalLogName_printsMessages() {
    // Given: positional log name argument "my-log"
    LogPrint cmd = new LogPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("my-log");

    // Then: logIdentifier is set correctly
    assertThat(cmd.logIdentifier, is("my-log"));
  }

  /**
   * Tests that the -o/--offset option is parsed and stored correctly.
   *
   * <p>Verifies that providing a positional log name and {@code -o 10} causes the offset field to
   * be set to 10.
   */
  @Test
  public void runCommand_withOffset_startsAtOffset() throws Exception {
    // Given: positional log name and -o 10 option
    LogPrint cmd = new LogPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("my-log", "-o", "10");

    // Then: offset is set to 10
    assertThat(cmd.logIdentifier, is("my-log"));
    assertThat(cmd.offset, is(10L));
  }

  /**
   * Tests that the -f/--follow flag is parsed correctly.
   *
   * <p>Verifies that when the {@code -f} flag is set, the follow field is true.
   */
  @Test
  public void runCommand_withFollow_streamsMessages() throws Exception {
    // Given: positional log name and -f flag
    LogPrint cmd = new LogPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("my-log", "-f");

    // Then: follow is true
    assertThat(cmd.logIdentifier, is("my-log"));
    assertThat(cmd.follow, is(true));
  }

  /**
   * Tests that the --with-return flag is parsed correctly.
   *
   * <p>Verifies that when the {@code --with-return} option is specified, the withReturn field is
   * true.
   */
  @Test
  public void runCommand_withReturn_includesReturnValues() throws Exception {
    // Given: positional log name and --with-return option
    LogPrint cmd = new LogPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("my-log", "--with-return");

    // Then: withReturn is true
    assertThat(cmd.logIdentifier, is("my-log"));
    assertThat(cmd.withReturn, is(true));
  }

  /**
   * Tests that the --types filter is parsed correctly.
   *
   * <p>Verifies that when the {@code --types CONSTRUCTOR} filter is provided, the msgTypes field
   * contains the correct value.
   */
  @Test
  public void runCommand_withTypeFilter_filtersTypes() {
    // Given: positional log name and --types CONSTRUCTOR filter
    LogPrint cmd = new LogPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("my-log", "--types", "CONSTRUCTOR");

    // Then: msgTypes contains CONSTRUCTOR
    assertThat(cmd.logIdentifier, is("my-log"));
    assertThat(cmd.msgTypes, is(notNullValue()));
    assertThat(cmd.msgTypes, is(List.of("CONSTRUCTOR")));
  }

  /**
   * Tests that the -fp/--from-peer filter is parsed correctly.
   *
   * <p>Verifies that when the {@code -fp UUID} filter is provided, the fromPeer field is set
   * correctly.
   */
  @Test
  public void runCommand_withPeerFilter_filtersByPeer() {
    // Given: positional log name and -fp <specific-UUID> filter
    String peerUuid = "550e8400-e29b-41d4-a716-446655440000";
    LogPrint cmd = new LogPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("my-log", "-fp", peerUuid);

    // Then: fromPeer is set to the specific UUID
    assertThat(cmd.logIdentifier, is("my-log"));
    assertThat(cmd.fromPeer, is(peerUuid));
  }

  /**
   * Tests that a {@code file:} path as positional argument is parsed correctly.
   *
   * <p>Verifies that providing a {@code file:/tmp/wal} path as the log identifier causes the field
   * to be set correctly for direct Chronicle mode.
   */
  @Test
  public void runCommand_directChronicleMode_worksWithFilePath() {
    // Given: positional log identifier "file:/tmp/wal"
    LogPrint cmd = new LogPrint();
    CommandLine commandLine = new CommandLine(cmd);
    commandLine.parseArgs("file:/tmp/wal");

    // Then: logIdentifier is set to the file path
    assertThat(cmd.logIdentifier, is("file:/tmp/wal"));
  }

  // ==================== validateInput() Tests ====================

  /**
   * Tests that validation fails when no log identifier is provided.
   *
   * <p>Verifies that invoking the command without a positional log identifier argument results in a
   * validation error.
   */
  @Test
  public void validateInput_logIdentifierRequired() {
    // Given: no positional log identifier argument
    LogPrint cmd = new LogPrint();

    // When: validateInput() is called
    // Then: RuntimeException is thrown indicating log identifier is required
    try {
      cmd.validateInput();
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      assertThat(e.getMessage().contains("Log identifier is required"), is(true));
    }
  }

  // ==================== --with-return Behavior Tests ====================

  /**
   * Tests that {@code --with-return} stops after printing the requested offset when that offset is
   * itself a return/done message.
   *
   * <p>The forward-scan only makes sense when the requested offset is an operation: it walks
   * subsequent messages tracking nesting depth until the matching return is found. When the offset
   * is already a return value, there is no operation to find a return for; the command must print
   * just that record. Without this guard, the scan would incorrectly print the very next message
   * after the requested offset (which may be a wholly unrelated return from a different operation).
   */
  @Test
  public void runCommand_withReturn_offsetAtReturnValue_printsOnlyThatRecord() throws Exception {
    // Given: a Chronicle queue with two consecutive return values (offsets 0 and 1)
    Path queueDir = Files.createTempDirectory("logprint-with-return-at-return");
    PrintStream originalOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try {
      UUID peerId = UUID.randomUUID();
      writeReturnValueMessage(queueDir, peerId);
      writeReturnValueMessage(queueDir, peerId);

      LogPrint cmd = new LogPrint();
      cmd.logIdentifier = "file:" + queueDir;
      cmd.offset = 0L;
      cmd.withReturn = true;

      // When: runCommand() is invoked with --with-return at a return-value offset
      System.setOut(new PrintStream(captured, true, UTF_8));
      int result = cmd.runCommand();

      // Then: exit code 0 and exactly the requested record is printed (offset 1 is not bled in)
      assertThat(result, is(0));
      String output = captured.toString(UTF_8);
      long recordCount =
          Stream.of(output.split("\n")).filter(line -> line.startsWith("offset=")).count();
      assertThat(recordCount, is(1L));
      assertThat(output, containsString("offset=0 "));
      assertThat(output, not(containsString("offset=1 ")));
    } finally {
      System.setOut(originalOut);
      deleteDirectory(queueDir);
    }
  }

  /**
   * Tests that {@code --with-return} still scans forward for the matching return when the requested
   * offset points to an operation message.
   *
   * <p>This is the original/intended behavior of {@code --with-return}: when at a constructor (or
   * any operation), walk subsequent messages until depth reaches zero, then print that matching
   * return value too.
   */
  @Test
  public void runCommand_withReturn_offsetAtOperation_printsOperationAndReturn() throws Exception {
    // Given: a Chronicle queue with a constructor at offset 0 and its return at offset 1
    Path queueDir = Files.createTempDirectory("logprint-with-return-at-op");
    PrintStream originalOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try {
      UUID peerId = UUID.randomUUID();
      writeConstructorMessage(queueDir, peerId, "com.example.Foo");
      writeReturnValueMessage(queueDir, peerId);

      LogPrint cmd = new LogPrint();
      cmd.logIdentifier = "file:" + queueDir;
      cmd.offset = 0L;
      cmd.withReturn = true;

      // When: runCommand() is invoked with --with-return at an operation offset
      System.setOut(new PrintStream(captured, true, UTF_8));
      int result = cmd.runCommand();

      // Then: both the operation and its return value are printed
      assertThat(result, is(0));
      String output = captured.toString(UTF_8);
      long recordCount =
          Stream.of(output.split("\n")).filter(line -> line.startsWith("offset=")).count();
      assertThat(recordCount, is(2L));
      assertThat(output, containsString("offset=0 "));
      assertThat(output, containsString("offset=1 "));
    } finally {
      System.setOut(originalOut);
      deleteDirectory(queueDir);
    }
  }

  // ==================== Test Helpers ====================

  /**
   * Writes an EXEC_CONSTRUCTOR message to a Chronicle queue.
   *
   * @param queueDir the queue directory
   * @param peerId the peer UUID
   * @param className the class name for the constructor
   */
  private static void writeConstructorMessage(Path queueDir, UUID peerId, String className) {
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(queueDir.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender app = q.createAppender();

      ExecMessage execMsg = new ExecMessage();
      execMsg.setThreadName("main");
      execMsg.setPeerUuid(UuidUtils.toBytes(peerId));
      execMsg.setMessageId(UUID.randomUUID().toString());
      execMsg.setCurrentTime(System.currentTimeMillis() * 1_000_000L);

      ConstructorCall cc = new ConstructorCall();
      Class clazz = new Class();
      clazz.setName(className);
      cc.setClazz(clazz);
      execMsg.setConstructorCall(cc);

      Message wrapper =
          new Message()
              .withMessageType(MessageType.EXEC_CONSTRUCTOR.getId())
              .withExecMessage(execMsg);

      OutboundMsg outboundMsg =
          new OutboundMsg(
              MessageType.EXEC_CONSTRUCTOR,
              ExecPhase.BEFORE,
              null,
              execMsg.getMessageId(),
              null,
              wrapper);
      outboundMsg.appendTo(app);
    }
  }

  /**
   * Writes an EXEC_RETURN_VALUE message (void return) to a Chronicle queue.
   *
   * @param queueDir the queue directory
   * @param peerId the peer UUID
   */
  private static void writeReturnValueMessage(Path queueDir, UUID peerId) {
    try (ChronicleQueue q =
        SingleChronicleQueueBuilder.single(queueDir.toFile())
            .wireType(WireType.BINARY_LIGHT)
            .build()) {
      ExcerptAppender app = q.createAppender();

      ExecMessage execMsg = new ExecMessage();
      execMsg.setThreadName("main");
      execMsg.setPeerUuid(UuidUtils.toBytes(peerId));
      execMsg.setMessageId(UUID.randomUUID().toString());
      execMsg.setCurrentTime(System.currentTimeMillis() * 1_000_000L);
      execMsg.setReturnValue(new ReturnValue().withIsVoid(true));

      Message wrapper =
          new Message()
              .withMessageType(MessageType.EXEC_RETURN_VALUE.getId())
              .withExecMessage(execMsg);

      OutboundMsg outboundMsg =
          new OutboundMsg(
              MessageType.EXEC_RETURN_VALUE,
              ExecPhase.AFTER,
              null,
              execMsg.getMessageId(),
              null,
              wrapper);
      outboundMsg.appendTo(app);
    }
  }

  /**
   * Recursively deletes a directory and its contents (best-effort cleanup for tests).
   *
   * @param dir the directory to delete
   * @throws IOException if walking the directory fails
   */
  private static void deleteDirectory(Path dir) throws IOException {
    if (Files.exists(dir)) {
      try (Stream<Path> walk = Files.walk(dir)) {
        walk.sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException e) {
                    // best-effort cleanup
                  }
                });
      }
    }
  }
}
