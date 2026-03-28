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
package io.quasient.pal.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;

import org.junit.Test;

/**
 * Integration tests for CLI help text rendering in the new entity-operation command structure.
 *
 * <p>Tests that help text is correctly rendered for entity groups (peer, log, intercept),
 * subcommand nesting, and alias resolution.
 *
 * <p>Requires running etcd infrastructure as described in modules/itt/README.md.
 */
public class CliHelpIT extends AbstractCliIT {

  /**
   * Tests that {@code pal --help} output contains entity group commands (peer, log, intercept).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPalHelp_showsEntityGroups() throws Exception {
    CliProcessResult result = runCliSubcommand(new String[] {"--help"}, null);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString("peer"));
    assertThat(result.stdout(), containsString("log"));
    assertThat(result.stdout(), containsString("intercept"));
    assertThat(result.stdout(), containsString("run"));
  }

  /**
   * Tests that {@code pal peer --help} shows available peer subcommands (ls, rm, print, call).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerHelp_showsSubcommands() throws Exception {
    CliProcessResult result = runCliSubcommand(new String[] {"peer", "--help"}, null);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString("ls"));
    assertThat(result.stdout(), containsString("rm"));
    assertThat(result.stdout(), containsString("print"));
    assertThat(result.stdout(), containsString("call"));
  }

  /**
   * Tests that {@code pal log --help} shows available log subcommands (ls, rm, print, call).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogHelp_showsSubcommands() throws Exception {
    CliProcessResult result = runCliSubcommand(new String[] {"log", "--help"}, null);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString("ls"));
    assertThat(result.stdout(), containsString("rm"));
    assertThat(result.stdout(), containsString("print"));
    assertThat(result.stdout(), containsString("call"));
  }

  /**
   * Tests that {@code pal intercept --help} shows available intercept subcommands (ls).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testInterceptHelp_showsSubcommands() throws Exception {
    CliProcessResult result = runCliSubcommand(new String[] {"intercept", "--help"}, null);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString("ls"));
  }

  /**
   * Tests that {@code pal peers --help} shows peer listing help (alias for {@code pal peer ls}).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testAliasHelp_showsPeersAlias() throws Exception {
    CliProcessResult result = runCliSubcommand(new String[] {"peers", "--help"}, null);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), is(not(emptyString())));
  }

  /**
   * Tests that {@code pal logs --help} shows log listing help (alias for {@code pal log ls}).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testAliasHelp_showsLogsAlias() throws Exception {
    CliProcessResult result = runCliSubcommand(new String[] {"logs", "--help"}, null);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), is(not(emptyString())));
  }

  /**
   * Tests that {@code pal intercepts --help} shows intercept listing help (alias for {@code pal
   * intercept ls}).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testAliasHelp_showsInterceptsAlias() throws Exception {
    CliProcessResult result = runCliSubcommand(new String[] {"intercepts", "--help"}, null);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), is(not(emptyString())));
  }

  /**
   * Tests that {@code pal peer ls --help} shows options specific to peer listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testNestedHelp_showsCommandHelp() throws Exception {
    CliProcessResult result = runPeerLs("--help");

    assertThat(result.exitCode(), is(0));
    String stdout = result.stdout();
    // Should contain long-format option
    assertThat(
        "Expected --long or -l option in peer ls help",
        stdout.contains("--long") || stdout.contains("-l"),
        is(true));
    // Should NOT contain the old entity-selection flag -P
    assertThat("Should not contain old -P entity flag", stdout, not(containsString("-P")));
  }
}
