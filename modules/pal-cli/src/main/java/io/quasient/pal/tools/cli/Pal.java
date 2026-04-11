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

import static picocli.CommandLine.Option;

import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.core.service.GroupedOptionListRenderer;
import io.quasient.pal.core.service.Main;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.UsageMessageSpec;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.ScopeType;
import picocli.CommandLine.Spec;

/**
 * The main command class for the PAL command-line interface.
 *
 * <p>It configures and initializes the CLI application, manages subcommands, and handles the
 * execution strategy for processing user input.
 */
@Command(
    name = "pal",
    customSynopsis = "pal [OPTIONS] COMMAND",
    description = "%nThe friendly java runtime",
    separator = " ",
    mixinStandardHelpOptions = true,
    optionListHeading = "%nOptions:%n",
    usageHelpWidth = 90,
    footer = "%nRun 'pal COMMAND --help' or 'pal COMMAND SUBCOMMAND --help' for more information.",
    sortOptions = false,
    versionProvider = ManifestVersionProvider.class)
public class Pal implements Callable<Integer>, PalCommand {

  /** Column width for command names in the Docker-style help output. */
  private static final int COMMAND_NAME_WIDTH = 14;

  /** The command specification provided by Picocli for the current command. */
  @SuppressWarnings("unused")
  @Spec
  private CommandSpec spec;

  /**
   * The URL of the PAL directory, specified via command-line options or environment variables.
   *
   * <p>Accepts values in the format {@code HOST:PORT}.
   */
  @Option(
      names = {"-d", "--dir"},
      paramLabel = "HOST:PORT",
      description = "PAL directory [env: PAL_DIRECTORY]",
      scope = ScopeType.INHERIT)
  private String palDirectoryUrl;

  /**
   * Constructs a new instance of the Pal command.
   *
   * <p>This constructor is private to enforce usage through the CLI framework.
   */
  private Pal() {}

  /**
   * Creates and configures the full command-line hierarchy.
   *
   * <p>Assembles the entire CLI command tree including entity group commands, alias shortcuts, and
   * Docker-style help formatting. Package-private to support testing.
   *
   * @return a fully configured {@link CommandLine} instance
   */
  static CommandLine createCommandLine() {
    Pal pal = new Pal();
    CommandLine commandLine = new CommandLine(pal);

    // top-level commands
    commandLine.addSubcommand("run", new Main());
    commandLine
        .getSubcommands()
        .get("run")
        .getHelpSectionMap()
        .put(UsageMessageSpec.SECTION_KEY_OPTION_LIST, new GroupedOptionListRenderer());
    commandLine.addSubcommand(new Replay());
    commandLine.addSubcommand(new Init());

    // entity group commands (with nested subcommands declared via @Command(subcommands=...))
    commandLine.addSubcommand("peer", new PeerCommand());
    commandLine.addSubcommand("log", new LogCommand());
    commandLine.addSubcommand("intercept", new InterceptCommand());

    // alias shortcuts
    commandLine.addSubcommand("peers", new PeersAlias());
    commandLine.addSubcommand("logs", new LogsAlias());
    commandLine.addSubcommand("intercepts", new InterceptsAlias());

    // help
    commandLine.addSubcommand(HelpCommand.class);

    setupDockerStyleHelp(commandLine);
    commandLine.setExecutionStrategy(pal::executionStrategy);
    return commandLine;
  }

  /**
   * Defines the execution strategy for processing parsed command-line input.
   *
   * @param parseResult the result of parsing the command-line arguments
   * @return the exit code resulting from command execution
   */
  private int executionStrategy(ParseResult parseResult) {
    init();
    return new CommandLine.RunLast().execute(parseResult);
  }

  /**
   * Initializes the PAL directory connection string based on command-line options, environment
   * variables, or defaults.
   *
   * <p>This method ensures that the PAL directory URL is set before executing any command.
   */
  private void init() {
    if (palDirectoryUrl == null || palDirectoryUrl.trim().isEmpty()) {
      String palDirectoryEnvVar = System.getenv("PAL_DIRECTORY");
      palDirectoryUrl = palDirectoryEnvVar != null ? palDirectoryEnvVar.trim() : null;
    }
    if (palDirectoryUrl == null || palDirectoryUrl.isEmpty()) {
      palDirectoryUrl = PalDirectory.NO_URL;
    }
  }

  /**
   * The entry point of the PAL CLI application.
   *
   * <p>It configures the command hierarchy, adds subcommands, and executes the appropriate command
   * based on user input.
   *
   * @param args the command-line arguments provided by the user
   */
  public static void main(String[] args) {
    System.setProperty("picocli.ansi", "false");
    CommandLine commandLine = createCommandLine();
    int exitCode = commandLine.execute(args);
    System.exit(exitCode);
  }

  /**
   * Executes the command when no subcommand is provided.
   *
   * <p>It displays the usage information to guide the user.
   *
   * @return {@code 0} as the exit code indicating the usage information was displayed successfully
   */
  @Override
  public Integer call() {
    // no sub-command given, let's help with that
    spec.commandLine().usage(System.out);
    return 0;
  }

  /**
   * Retrieves the connection string for the PAL directory.
   *
   * @return the PAL directory connection string
   */
  @Override
  public String getPalDirectoryConnectionString() {
    return this.palDirectoryUrl;
  }

  /**
   * Configures Docker-style help grouping on the given command line.
   *
   * <p>Replaces the default command list section with a grouped rendering that organizes commands
   * into Management Commands, Commands, and Shortcuts sections.
   *
   * @param commandLine the root command line to configure
   */
  private static void setupDockerStyleHelp(CommandLine commandLine) {
    commandLine.getHelpSectionMap().put("commandListHeading", help -> "");
    commandLine.getHelpSectionMap().put("commandList", Pal::renderDockerStyleCommandList);
  }

  /**
   * Renders the command list in Docker-style grouped format.
   *
   * @param help the picocli Help object providing access to command metadata
   * @return the formatted command list string
   */
  private static String renderDockerStyleCommandList(CommandLine.Help help) {
    Map<String, CommandLine> subs = help.commandSpec().subcommands();

    StringBuilder sb = new StringBuilder();
    appendGroup(sb, "Management Commands", subs, "peer", "log", "intercept");
    appendGroup(sb, "Commands", subs, "run", "replay", "init");
    appendGroup(sb, "Shortcuts", subs, "peers", "logs", "intercepts");
    return sb.toString();
  }

  /**
   * Appends a named group of commands to the help output.
   *
   * @param sb the string builder to append to
   * @param heading the group heading
   * @param subs the map of registered subcommands
   * @param names the command names to include in this group
   */
  private static void appendGroup(
      StringBuilder sb, String heading, Map<String, CommandLine> subs, String... names) {
    sb.append(String.format("%n%s:%n", heading));
    for (String name : names) {
      CommandLine sub = subs.get(name);
      if (sub != null) {
        String[] desc = sub.getCommandSpec().usageMessage().description();
        String description = desc.length > 0 ? desc[0] : "";
        sb.append(String.format("  %-" + COMMAND_NAME_WIDTH + "s%s%n", name, description));
      }
    }
  }
}
