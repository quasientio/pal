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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import picocli.CommandLine;

/**
 * Unit test specifications for {@code PeersAlias}.
 *
 * <p>PeersAlias is a shortcut command ({@code pal peers}) that extends {@link PeerList} so that
 * running {@code pal peers} is equivalent to {@code pal peer ls}. It is registered as a direct
 * child of the root {@link Pal} command, so its {@code @ParentCommand PalCommand} resolves to
 * {@link Pal} directly.
 *
 * @see PeerList
 * @see Pal
 */
public class PeersAliasTest {

  /**
   * Tests that PeersAlias extends PeerList.
   *
   * <p>Verifies that PeersAlias is a subclass of PeerList, which means it inherits all peer listing
   * behavior and only overrides the {@code @Command(name = "peers")} annotation.
   */
  @Test
  public void extendsFromPeerList() {
    assertTrue(PeerList.class.isAssignableFrom(PeersAlias.class));
  }

  /**
   * Tests that the picocli command name for PeersAlias is "peers".
   *
   * <p>Verifies that when PeersAlias is wrapped in a CommandLine, the command name resolves to
   * "peers" (not "ls" from the parent PeerList).
   */
  @Test
  public void commandNameIsPeers() {
    CommandLine commandLine = new CommandLine(new PeersAlias());
    assertThat(commandLine.getCommandName(), is("peers"));
  }

  /**
   * Tests that PeersAlias works as a direct child of Pal.
   *
   * <p>Verifies that when PeersAlias is wired under the root Pal command (as a direct child), the
   * {@code @ParentCommand PalCommand} delegation resolves correctly, allowing {@code
   * getPalDirectoryConnectionString()} to propagate from Pal through the alias.
   */
  @Test
  public void worksAsDirectChildOfPal() {
    CommandLine palCmd = Pal.createCommandLine();

    // Verify "peers" is registered as a direct child
    assertTrue(palCmd.getSubcommands().containsKey("peers"));
    assertTrue(palCmd.getSubcommands().get("peers").getCommand() instanceof PeersAlias);

    // Parse args to trigger @ParentCommand injection
    palCmd.parseArgs("-d", "test-host:2379", "peers");
    PeersAlias alias = palCmd.getSubcommands().get("peers").getCommand();
    assertThat(alias.palCommand.getPalDirectoryConnectionString(), is("test-host:2379"));
  }
}
