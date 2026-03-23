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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;
import org.junit.Ignore;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Unit test specifications for the overall {@link Pal} command wiring.
 *
 * <p>These tests validate that the entire CLI command hierarchy is correctly assembled in {@link
 * Pal#main(String[])}, including top-level commands, entity group subcommands, alias shortcuts, and
 * Docker-style help formatting. The tests verify structural correctness of the command tree rather
 * than execution behavior of individual commands.
 *
 * @see Pal
 * @see PeerCommand
 * @see LogCommand
 * @see InterceptCommand
 */
public class PalCommandWiringTest {

  /**
   * Tests that all expected top-level commands are registered under Pal.
   *
   * <p>Verifies that the full Pal CommandLine has subcommands registered with keys: run, peer, log,
   * intercept, replay, peers, logs, intercepts, help.
   */
  @Test
  public void topLevelCommands_registered() {
    CommandLine palCmd = Pal.createCommandLine();
    Map<String, CommandLine> subs = palCmd.getSubcommands();

    assertTrue(subs.containsKey("run"));
    assertTrue(subs.containsKey("peer"));
    assertTrue(subs.containsKey("log"));
    assertTrue(subs.containsKey("intercept"));
    assertTrue(subs.containsKey("replay"));
    assertTrue(subs.containsKey("peers"));
    assertTrue(subs.containsKey("logs"));
    assertTrue(subs.containsKey("intercepts"));
    assertTrue(subs.containsKey("help"));
  }

  /**
   * Tests that the peer entity group has all expected subcommands.
   *
   * <p>Verifies that the "peer" subcommand contains nested subcommands: ls, rm, print, call, stats.
   */
  @Test
  public void peerSubcommands_registered() {
    CommandLine palCmd = Pal.createCommandLine();
    CommandLine peerCmd = palCmd.getSubcommands().get("peer");
    Map<String, CommandLine> subs = peerCmd.getSubcommands();

    assertTrue(subs.containsKey("ls"));
    assertTrue(subs.containsKey("rm"));
    assertTrue(subs.containsKey("print"));
    assertTrue(subs.containsKey("call"));
    assertTrue(subs.containsKey("stats"));
  }

  /**
   * Tests that the log entity group has all expected subcommands.
   *
   * <p>Verifies that the "log" subcommand contains nested subcommands: ls, rm, print, call, index,
   * stats.
   */
  @Test
  public void logSubcommands_registered() {
    CommandLine palCmd = Pal.createCommandLine();
    CommandLine logCmd = palCmd.getSubcommands().get("log");
    Map<String, CommandLine> subs = logCmd.getSubcommands();

    assertTrue(subs.containsKey("ls"));
    assertTrue(subs.containsKey("rm"));
    assertTrue(subs.containsKey("print"));
    assertTrue(subs.containsKey("call"));
    assertTrue(subs.containsKey("index"));
    assertTrue(subs.containsKey("stats"));
  }

  /**
   * Tests that the intercept entity group has all expected subcommands.
   *
   * <p>Verifies that the "intercept" subcommand contains nested subcommand: ls.
   */
  @Test
  public void interceptSubcommands_registered() {
    CommandLine palCmd = Pal.createCommandLine();
    CommandLine interceptCmd = palCmd.getSubcommands().get("intercept");
    Map<String, CommandLine> subs = interceptCmd.getSubcommands();

    assertTrue(subs.containsKey("ls"));
  }

  /**
   * Tests that the root help text includes entity group references.
   *
   * <p>Verifies that calling getUsageMessage() on the Pal CommandLine produces help text that
   * mentions "peer", "log", and "intercept" entity groups.
   */
  @Test
  public void helpText_includesEntityGroups() {
    CommandLine palCmd = Pal.createCommandLine();
    String help = palCmd.getUsageMessage();

    assertThat(help, containsString("peer"));
    assertThat(help, containsString("log"));
    assertThat(help, containsString("intercept"));
  }

  /**
   * Tests that the root help text uses Docker-style grouping format.
   *
   * <p>Verifies that the help output organizes commands into sections such as "Management Commands"
   * (entity groups: peer, log, intercept), "Commands" (run, replay), and "Shortcuts" (peers, logs,
   * intercepts), similar to Docker CLI help formatting.
   */
  @Test
  public void helpText_dockerStyleFormat() {
    CommandLine palCmd = Pal.createCommandLine();
    String help = palCmd.getUsageMessage();

    assertThat(help, containsString("Management Commands:"));
    assertThat(help, containsString("Commands:"));
    assertThat(help, containsString("Shortcuts:"));
  }

  /**
   * Tests that the peer help text shows its subcommands.
   *
   * <p>Verifies that calling getUsageMessage() on the peer CommandLine produces help text that
   * lists ls, rm, print, call subcommands.
   */
  @Test
  public void peerHelp_showsSubcommands() {
    CommandLine palCmd = Pal.createCommandLine();
    CommandLine peerCmd = palCmd.getSubcommands().get("peer");
    String help = peerCmd.getUsageMessage();

    assertThat(help, containsString("ls"));
    assertThat(help, containsString("rm"));
    assertThat(help, containsString("print"));
    assertThat(help, containsString("call"));
  }

  /**
   * Tests that alias commands resolve to the correct underlying command class.
   *
   * <p>Verifies that when "peers" is parsed via the full CommandLine, it resolves to a PeersAlias
   * instance, which extends PeerList (i.e., is functionally equivalent to {@code pal peer ls}).
   */
  @Test
  public void aliasesAreFunctional() {
    CommandLine palCmd = Pal.createCommandLine();

    // Parse "peers" and verify it resolves to PeersAlias
    CommandLine.ParseResult result = palCmd.parseArgs("peers");
    CommandLine.ParseResult sub = result.subcommand();
    assertTrue(sub.commandSpec().userObject() instanceof PeersAlias);
    assertTrue(sub.commandSpec().userObject() instanceof PeerList);
  }

  /**
   * Tests that the "init" command is registered as a top-level subcommand.
   *
   * <p>Verifies that {@code Pal.createCommandLine()} includes "init" in its subcommands map.
   */
  @Test
  @Ignore("Awaiting implementation in #1347")
  public void testInitCommandRegistered() {
    // Given: Pal.createCommandLine()
    // When: subcommands queried
    // Then: "init" present in subcommands map

    // TODO(#1347): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that the root help output includes the "init" command.
   *
   * <p>Verifies that the Pal help text mentions "init" in the "Commands" group with its
   * description.
   */
  @Test
  @Ignore("Awaiting implementation in #1347")
  public void testHelpIncludesInitCommand() {
    // Given: Pal help output rendered
    // When: content inspected
    // Then: "init" appears in "Commands" group with description

    // TODO(#1347): Implement test logic
    fail("Not yet implemented");
  }
}
