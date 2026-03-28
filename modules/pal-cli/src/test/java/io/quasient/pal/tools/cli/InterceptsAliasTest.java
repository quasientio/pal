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
 * Unit test specifications for {@code InterceptsAlias}.
 *
 * <p>InterceptsAlias is a shortcut command ({@code pal intercepts}) that extends {@link
 * InterceptList} so that running {@code pal intercepts} is equivalent to {@code pal intercept ls}.
 * It is registered as a direct child of the root {@link Pal} command, so its {@code @ParentCommand
 * PalCommand} resolves to {@link Pal} directly.
 *
 * @see InterceptList
 * @see Pal
 */
public class InterceptsAliasTest {

  /**
   * Tests that InterceptsAlias extends InterceptList.
   *
   * <p>Verifies that InterceptsAlias is a subclass of InterceptList, which means it inherits all
   * intercept listing behavior and only overrides the {@code @Command(name = "intercepts")}
   * annotation.
   */
  @Test
  public void extendsFromInterceptList() {
    assertTrue(InterceptList.class.isAssignableFrom(InterceptsAlias.class));
  }

  /**
   * Tests that the picocli command name for InterceptsAlias is "intercepts".
   *
   * <p>Verifies that when InterceptsAlias is wrapped in a CommandLine, the command name resolves to
   * "intercepts" (not "ls" from the parent InterceptList).
   */
  @Test
  public void commandNameIsIntercepts() {
    CommandLine commandLine = new CommandLine(new InterceptsAlias());
    assertThat(commandLine.getCommandName(), is("intercepts"));
  }

  /**
   * Tests that InterceptsAlias works as a direct child of Pal.
   *
   * <p>Verifies that when InterceptsAlias is wired under the root Pal command (as a direct child),
   * the {@code @ParentCommand PalCommand} delegation resolves correctly, allowing {@code
   * getPalDirectoryConnectionString()} to propagate from Pal through the alias.
   */
  @Test
  public void worksAsDirectChildOfPal() {
    CommandLine palCmd = Pal.createCommandLine();

    // Verify "intercepts" is registered as a direct child
    assertTrue(palCmd.getSubcommands().containsKey("intercepts"));
    assertTrue(palCmd.getSubcommands().get("intercepts").getCommand() instanceof InterceptsAlias);

    // Parse args to trigger @ParentCommand injection
    palCmd.parseArgs("-d", "test-host:2379", "intercepts");
    InterceptsAlias alias = palCmd.getSubcommands().get("intercepts").getCommand();
    assertThat(alias.palCommand.getPalDirectoryConnectionString(), is("test-host:2379"));
  }
}
