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
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.common.cli.PalCommand;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Unit test specifications for {@code LogCommand}.
 *
 * <p>LogCommand is the entity group container command for log-related sub-subcommands ({@code pal
 * log ls}, {@code pal log rm}, {@code pal log print}, {@code pal log call}, {@code pal log index},
 * {@code pal log stats}). It implements {@link io.quasient.pal.common.cli.PalCommand} to propagate
 * the directory connection string from the root {@link Pal} command down to its sub-subcommands via
 * {@code @ParentCommand} delegation.
 *
 * @see io.quasient.pal.common.cli.PalCommand
 * @see Pal
 */
public class LogCommandTest {

  /**
   * Tests that LogCommand implements the PalCommand interface.
   *
   * <p>Verifies that a LogCommand instance is assignable to PalCommand, which is required for
   * sub-subcommands to use {@code @ParentCommand PalCommand} uniformly.
   */
  @Test
  public void implementsPalCommand() {
    // Given: a LogCommand instance
    LogCommand logCommand = new LogCommand();

    // When: checked for PalCommand assignability
    // Then: instance is assignable to PalCommand
    assertTrue(PalCommand.class.isAssignableFrom(logCommand.getClass()));
  }

  /**
   * Tests that getPalDirectoryConnectionString delegates to the parent command.
   *
   * <p>Verifies that when the parent PalCommand returns "localhost:2379", LogCommand's {@code
   * getPalDirectoryConnectionString()} returns the same value, confirming proper
   * {@code @ParentCommand} delegation.
   */
  @Test
  public void getPalDirectoryConnectionString_delegatesToParent() {
    // Given: LogCommand with mock parent PalCommand returning "localhost:2379"
    LogCommand logCommand = new LogCommand();
    logCommand.parent = () -> "localhost:2379";

    // When: getPalDirectoryConnectionString() called
    String result = logCommand.getPalDirectoryConnectionString();

    // Then: returns "localhost:2379"
    assertThat(result, is("localhost:2379"));
  }

  /**
   * Tests that running LogCommand with no subcommand prints usage.
   *
   * <p>Verifies that when LogCommand is wired into a CommandLine and executed without a subcommand,
   * it prints usage information to stdout and exits with code 0.
   */
  @Test
  public void run_withNoSubcommand_printsUsage() {
    // Given: LogCommand wired into CommandLine
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(outputStream);
    PrintStream originalOut = System.out;
    System.setOut(printStream);

    try {
      CommandLine commandLine = new CommandLine(new LogCommand());

      // When: execute with no subcommand
      int exitCode = commandLine.execute();

      // Then: usage is printed to stdout (exit code 0)
      assertThat(exitCode, is(0));
      String output = outputStream.toString(UTF_8);
      assertThat(output, containsString("log"));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that all expected subcommands are registered.
   *
   * <p>Verifies that a CommandLine wrapping LogCommand has subcommands registered with keys "ls",
   * "rm", "print", "call", "index", and "stats".
   */
  @Test
  public void subcommands_areRegistered() {
    // Given: CommandLine with LogCommand
    CommandLine commandLine = new CommandLine(new LogCommand());

    // When: getSubcommands() called
    Map<String, CommandLine> subcommands = commandLine.getSubcommands();

    // Then: contains keys "ls", "rm", "print", "call", "index", "stats"
    assertTrue(subcommands.containsKey("ls"));
    assertTrue(subcommands.containsKey("rm"));
    assertTrue(subcommands.containsKey("print"));
    assertTrue(subcommands.containsKey("call"));
    assertTrue(subcommands.containsKey("index"));
    assertTrue(subcommands.containsKey("stats"));
  }
}
