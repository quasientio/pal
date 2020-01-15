package net.ittera.pal.tools.paldir;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import net.ittera.pal.cxn.PALDirectory;

public abstract class AbstractPALDirSubcommand implements Callable<Integer> {
  protected final PALDirectory palDirectory;
  protected PrintStream out;
  protected PrintStream err;

  protected AbstractPALDirSubcommand(PALDirectory palDirectory) {
    this.palDirectory = palDirectory;
    out = System.out;
    err = System.err;
  }

  public abstract void validateInput();

  protected abstract void closeResources() throws IOException;

  protected abstract int runCommand() throws Exception;

  public Integer call() throws Exception {
    try {
      validateInput();
    } catch (RuntimeException e) {
      err.println(e.getMessage());
      return 1;
    }
    int result = runCommand();
    closeResources();
    return result;
  }
}
