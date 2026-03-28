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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

import java.util.List;
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
}
