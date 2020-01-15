package net.ittera.pal.tools;

import static picocli.CommandLine.Option;

import java.util.concurrent.Callable;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.tools.paldir.List;
import net.ittera.pal.tools.paldir.Remove;
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
