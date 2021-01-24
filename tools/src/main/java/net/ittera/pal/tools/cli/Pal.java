/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.tools.cli;

import static picocli.CommandLine.Option;

import java.util.Arrays;
import java.util.concurrent.Callable;
import net.ittera.pal.common.cli.PALCommand;
import net.ittera.pal.core.Main;
import net.ittera.pal.cxn.PALDirectory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

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
public class Pal implements Callable<Integer>, PALCommand {

  @Spec private CommandSpec spec;

  /** Options */
  @Option(
      names = {"-d", "--dir"},
      paramLabel = "HOST:PORT",
      description = "PAL directory")
  private String palDirectoryUrl;

  private Pal() {}

  private int executionStrategy(ParseResult parseResult) {
    init();
    return new CommandLine.RunLast().execute(parseResult);
  }

  /** Custom initialization to be done before executing any command or subcommand */
  private void init() {
    if (palDirectoryUrl == null || palDirectoryUrl.trim().isEmpty()) {
      String palDirectoryEnvVar = System.getenv("PAL_DIRECTORY");
      palDirectoryUrl = palDirectoryEnvVar != null ? palDirectoryEnvVar.trim() : null;
    }
    if (palDirectoryUrl == null || palDirectoryUrl.isEmpty()) {
      palDirectoryUrl = PALDirectory.NO_URL;
    }
  }

  public static void main(String[] args) {
    Pal pal = new Pal();
    CommandLine commandLine = new CommandLine(pal);

    // run (i.e. peer) command goes first
    commandLine.addSubcommand("run", new Main());

    // subcommands other than 'run' must be subclasses of AbstractPALSubcommand
    java.util.List<AbstractPALSubcommand> subcommands =
        Arrays.asList(new MessageStreamPrinter(), new Caller(), new List(), new Remove());
    subcommands.forEach(commandLine::addSubcommand);

    // at last Help
    commandLine.addSubcommand(HelpCommand.class);

    int exitCode = commandLine.setExecutionStrategy(pal::executionStrategy).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    // no sub-command given, let's help with that
    spec.commandLine().usage(System.out);
    return 0;
  }

  /** PALCommand INTERFACE */
  @Override
  public String getPalDirectoryConnectionString() {
    return this.palDirectoryUrl;
  }
}
