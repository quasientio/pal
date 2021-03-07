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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.tools.AbstractTool;
import org.slf4j.LoggerFactory;

public abstract class AbstractPALSubcommand extends AbstractTool implements Callable<Integer> {
  private static final String LOGGING_CONFIG = "/cli-logging.xml";
  private DirectoryConnectionProvider directoryConnectionProvider;
  protected PrintStream out;
  protected PrintStream err;

  protected AbstractPALSubcommand() {
    out = System.out;
    err = System.err;
  }

  protected final void initializeDirectoryConnectionProvider(String paldirConnectionString) {
    this.directoryConnectionProvider =
        new DirectoryConnectionProvider(paldirConnectionString, null, false);
  }

  protected static boolean optionGiven(String option) {
    return !(option == null || option.isEmpty());
  }

  protected abstract void validateInput();

  /**
   * Logging is configured here and not in the Pal parent command, since we can't configure logback
   * when launching the 'run' subcommand, ie. a peer.
   */
  private void configureLogging() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    context.reset();
    try (final InputStream stream =
        AbstractPALSubcommand.class.getResourceAsStream(LOGGING_CONFIG)) {
      configurator.doConfigure(stream);
    } catch (Exception ie) {
      System.err.printf("Error loading logging configuration from %s%n", LOGGING_CONFIG);
      // for more info: StatusPrinter.printInCaseOfErrorsOrWarnings(context);
      ie.printStackTrace();
    }
  }

  protected void closeResources() throws IOException {
    directoryConnectionProvider
        .get()
        .ifPresent(
            c -> {
              Optional<PALDirectory> palDirectory = directoryConnectionProvider.get();
              palDirectory.ifPresent(PALDirectory::close);
            });
  }

  protected abstract int runCommand() throws Exception;

  protected abstract void initialize() throws Exception;

  protected PALDirectory getPalDirectory() {
    Optional<PALDirectory> palDirectory = directoryConnectionProvider.get();
    return palDirectory.orElseThrow(
        () ->
            new RuntimeException(
                "A PALDirectory is required. Run with -d (--dir) or set the ENV variable PAL_DIRECTORY."));
  }

  public Integer call() throws Exception {
    configureLogging();
    try {
      validateInput();
    } catch (RuntimeException e) {
      err.println(e.getMessage());
      return 1;
    }
    initialize();
    int result = runCommand();
    closeResources();
    return result;
  }
}
