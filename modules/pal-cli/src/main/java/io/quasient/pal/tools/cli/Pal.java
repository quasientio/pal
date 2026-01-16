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

import static picocli.CommandLine.Option;

import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.core.service.Main;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.Arrays;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;
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
    commandListHeading = "%nCommands:%n",
    usageHelpWidth = 90,
    footer = "%nRun 'pal COMMAND --help' or 'pal help COMMAND' for more information on a command.",
    sortOptions = false,
    versionProvider = ManifestVersionProvider.class)
public class Pal implements Callable<Integer>, PalCommand {

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
      description = "PAL directory")
  private String palDirectoryUrl;

  /**
   * Constructs a new instance of the Pal command.
   *
   * <p>This constructor is private to enforce usage through the CLI framework.
   */
  private Pal() {}

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
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public static void main(String[] args) {
    System.setProperty("picocli.ansi", "false");
    Pal pal = new Pal();
    CommandLine commandLine = new CommandLine(pal);

    // run (i.e. peer) command goes first
    commandLine.addSubcommand("run", new Main());

    // subcommands other than 'run' must be subclasses of AbstractPalSubcommand
    java.util.List<AbstractPalSubcommand> subcommands =
        Arrays.asList(new MessageStreamPrinter(), new Caller(), new List(), new Remove());
    subcommands.forEach(commandLine::addSubcommand);

    // at last Help
    commandLine.addSubcommand(HelpCommand.class);

    int exitCode = commandLine.setExecutionStrategy(pal::executionStrategy).execute(args);
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
}
