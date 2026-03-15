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
 * Unit test specifications for {@code PeerCommand}.
 *
 * <p>PeerCommand is the entity group container command for peer-related sub-subcommands ({@code pal
 * peer ls}, {@code pal peer rm}, {@code pal peer print}, {@code pal peer call}, {@code pal peer
 * stats}). It implements {@link io.quasient.pal.common.cli.PalCommand} to propagate the directory
 * connection string from the root {@link Pal} command down to its sub-subcommands via
 * {@code @ParentCommand} delegation.
 *
 * @see io.quasient.pal.common.cli.PalCommand
 * @see Pal
 */
public class PeerCommandTest {

  /**
   * Tests that PeerCommand implements the PalCommand interface.
   *
   * <p>Verifies that a PeerCommand instance is assignable to PalCommand, which is required for
   * sub-subcommands to use {@code @ParentCommand PalCommand} uniformly.
   */
  @Test
  public void implementsPalCommand() {
    // Given: a PeerCommand instance
    PeerCommand peerCommand = new PeerCommand();

    // When: checked for PalCommand assignability
    // Then: instance is assignable to PalCommand
    assertTrue(PalCommand.class.isAssignableFrom(peerCommand.getClass()));
  }

  /**
   * Tests that getPalDirectoryConnectionString delegates to the parent command.
   *
   * <p>Verifies that when the parent PalCommand returns "localhost:2379", PeerCommand's {@code
   * getPalDirectoryConnectionString()} returns the same value, confirming proper
   * {@code @ParentCommand} delegation.
   */
  @Test
  public void getPalDirectoryConnectionString_delegatesToParent() {
    // Given: PeerCommand with mock parent PalCommand returning "localhost:2379"
    PeerCommand peerCommand = new PeerCommand();
    peerCommand.parent = () -> "localhost:2379";

    // When: getPalDirectoryConnectionString() called
    String result = peerCommand.getPalDirectoryConnectionString();

    // Then: returns "localhost:2379"
    assertThat(result, is("localhost:2379"));
  }

  /**
   * Tests that running PeerCommand with no subcommand prints usage.
   *
   * <p>Verifies that when PeerCommand is wired into a CommandLine and executed without a
   * subcommand, it prints usage information to stdout and exits with code 0.
   */
  @Test
  public void run_withNoSubcommand_printsUsage() {
    // Given: PeerCommand wired into CommandLine
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(outputStream);
    PrintStream originalOut = System.out;
    System.setOut(printStream);

    try {
      CommandLine commandLine = new CommandLine(new PeerCommand());

      // When: execute with no subcommand
      int exitCode = commandLine.execute();

      // Then: usage is printed to stdout (exit code 0)
      assertThat(exitCode, is(0));
      String output = outputStream.toString(UTF_8);
      assertThat(output, containsString("peer"));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that all expected subcommands are registered.
   *
   * <p>Verifies that a CommandLine wrapping PeerCommand has subcommands registered with keys "ls",
   * "rm", "print", "call", and "stats".
   */
  @Test
  public void subcommands_areRegistered() {
    // Given: CommandLine with PeerCommand
    CommandLine commandLine = new CommandLine(new PeerCommand());

    // When: getSubcommands() called
    Map<String, CommandLine> subcommands = commandLine.getSubcommands();

    // Then: contains keys "ls", "rm", "print", "call", "stats"
    assertTrue(subcommands.containsKey("ls"));
    assertTrue(subcommands.containsKey("rm"));
    assertTrue(subcommands.containsKey("print"));
    assertTrue(subcommands.containsKey("call"));
    assertTrue(subcommands.containsKey("stats"));
  }
}
