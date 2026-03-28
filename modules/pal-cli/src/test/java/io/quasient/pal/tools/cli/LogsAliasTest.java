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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import picocli.CommandLine;

/**
 * Unit test specifications for {@code LogsAlias}.
 *
 * <p>LogsAlias is a shortcut command ({@code pal logs}) that extends {@link LogList} so that
 * running {@code pal logs} is equivalent to {@code pal log ls}. It is registered as a direct child
 * of the root {@link Pal} command, so its {@code @ParentCommand PalCommand} resolves to {@link Pal}
 * directly.
 *
 * @see LogList
 * @see Pal
 */
public class LogsAliasTest {

  /**
   * Tests that LogsAlias extends LogList.
   *
   * <p>Verifies that LogsAlias is a subclass of LogList, which means it inherits all log listing
   * behavior and only overrides the {@code @Command(name = "logs")} annotation.
   */
  @Test
  public void extendsFromLogList() {
    assertTrue(LogList.class.isAssignableFrom(LogsAlias.class));
  }

  /**
   * Tests that the picocli command name for LogsAlias is "logs".
   *
   * <p>Verifies that when LogsAlias is wrapped in a CommandLine, the command name resolves to
   * "logs" (not "ls" from the parent LogList).
   */
  @Test
  public void commandNameIsLogs() {
    CommandLine commandLine = new CommandLine(new LogsAlias());
    assertThat(commandLine.getCommandName(), is("logs"));
  }

  /**
   * Tests that LogsAlias works as a direct child of Pal.
   *
   * <p>Verifies that when LogsAlias is wired under the root Pal command (as a direct child), the
   * {@code @ParentCommand PalCommand} delegation resolves correctly, allowing {@code
   * getPalDirectoryConnectionString()} to propagate from Pal through the alias.
   */
  @Test
  public void worksAsDirectChildOfPal() {
    CommandLine palCmd = Pal.createCommandLine();

    // Verify "logs" is registered as a direct child
    assertTrue(palCmd.getSubcommands().containsKey("logs"));
    assertTrue(palCmd.getSubcommands().get("logs").getCommand() instanceof LogsAlias);

    // Parse args to trigger @ParentCommand injection
    palCmd.parseArgs("-d", "test-host:2379", "logs");
    LogsAlias alias = palCmd.getSubcommands().get("logs").getCommand();
    assertThat(alias.palCommand.getPalDirectoryConnectionString(), is("test-host:2379"));
  }
}
