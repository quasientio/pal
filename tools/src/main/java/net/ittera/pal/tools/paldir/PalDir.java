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

package net.ittera.pal.tools.paldir;

import static picocli.CommandLine.Option;

import java.util.concurrent.Callable;
import net.ittera.pal.cxn.PALDirectory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "paldir")
public class PalDir implements Callable<Integer> {

  private static PALDirectory palDirectory;

  /** Options */
  @Option(
      names = {"-d", "--dir"},
      description = "PAL directory URL")
  private static String palDirectoryURL;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  private PalDir() {}

  private static void validateInput() {
    // directory
    if (palDirectoryURL == null || palDirectoryURL.isEmpty()) {
      palDirectoryURL = System.getenv("PAL_DIRECTORY");
    }
    if (palDirectoryURL == null) {
      throw new RuntimeException(
          "Please provide -d/--dir, or set the ENV variable PAL_DIRECTORY (eg. PAL_DIRECTORY=localhost:2181)");
    }
  }

  private static void initResources() {
    palDirectory = new PALDirectory(palDirectoryURL);
  }

  private void closeResources() {
    if (palDirectory != null) {
      palDirectory.close();
    }
  }

  public static void main(String[] args) {
    validateInput();
    initResources();
    PalDir palDir = new PalDir();
    int exitCode =
        new CommandLine(palDir)
            .addSubcommand(new List(palDirectory))
            .addSubcommand(new Remove(palDirectory))
            .execute(args);
    palDir.closeResources();
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    // run command here
    return 0;
  }
}
