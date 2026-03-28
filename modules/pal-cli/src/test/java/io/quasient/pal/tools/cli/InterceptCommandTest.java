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
 * Unit test specifications for {@code InterceptCommand}.
 *
 * <p>InterceptCommand is the entity group container command for intercept-related sub-subcommands
 * ({@code pal intercept ls}). It implements {@link io.quasient.pal.common.cli.PalCommand} to
 * propagate the directory connection string from the root {@link Pal} command down to its
 * sub-subcommands via {@code @ParentCommand} delegation.
 *
 * @see io.quasient.pal.common.cli.PalCommand
 * @see Pal
 */
public class InterceptCommandTest {

  /**
   * Tests that InterceptCommand implements the PalCommand interface.
   *
   * <p>Verifies that an InterceptCommand instance is assignable to PalCommand, which is required
   * for sub-subcommands to use {@code @ParentCommand PalCommand} uniformly.
   */
  @Test
  public void implementsPalCommand() {
    // Given: an InterceptCommand instance
    InterceptCommand interceptCommand = new InterceptCommand();

    // When: checked for PalCommand assignability
    // Then: instance is assignable to PalCommand
    assertTrue(PalCommand.class.isAssignableFrom(interceptCommand.getClass()));
  }

  /**
   * Tests that getPalDirectoryConnectionString delegates to the parent command.
   *
   * <p>Verifies that when the parent PalCommand returns "localhost:2379", InterceptCommand's {@code
   * getPalDirectoryConnectionString()} returns the same value, confirming proper
   * {@code @ParentCommand} delegation.
   */
  @Test
  public void getPalDirectoryConnectionString_delegatesToParent() {
    // Given: InterceptCommand with mock parent PalCommand returning "localhost:2379"
    InterceptCommand interceptCommand = new InterceptCommand();
    interceptCommand.parent = () -> "localhost:2379";

    // When: getPalDirectoryConnectionString() called
    String result = interceptCommand.getPalDirectoryConnectionString();

    // Then: returns "localhost:2379"
    assertThat(result, is("localhost:2379"));
  }

  /**
   * Tests that running InterceptCommand with no subcommand prints usage.
   *
   * <p>Verifies that when InterceptCommand is wired into a CommandLine and executed without a
   * subcommand, it prints usage information to stdout and exits with code 0.
   */
  @Test
  public void run_withNoSubcommand_printsUsage() {
    // Given: InterceptCommand wired into CommandLine
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(outputStream);
    PrintStream originalOut = System.out;
    System.setOut(printStream);

    try {
      CommandLine commandLine = new CommandLine(new InterceptCommand());

      // When: execute with no subcommand
      int exitCode = commandLine.execute();

      // Then: usage is printed to stdout (exit code 0)
      assertThat(exitCode, is(0));
      String output = outputStream.toString(UTF_8);
      assertThat(output, containsString("intercept"));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that all expected subcommands are registered.
   *
   * <p>Verifies that a CommandLine wrapping InterceptCommand has subcommands registered with the
   * key "ls".
   */
  @Test
  public void subcommands_areRegistered() {
    // Given: CommandLine with InterceptCommand
    CommandLine commandLine = new CommandLine(new InterceptCommand());

    // When: getSubcommands() called
    Map<String, CommandLine> subcommands = commandLine.getSubcommands();

    // Then: contains key "ls"
    assertTrue(subcommands.containsKey("ls"));
  }
}
