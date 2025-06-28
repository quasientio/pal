/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.tools.cli;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import com.quasient.pal.core.Main;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import com.quasient.pal.tools.AbstractTool;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.slf4j.LoggerFactory;

/**
 * Serves as the base class for PAL CLI subcommands, providing common functionalities such as
 * logging configuration, directory connection management, and resource handling. Subclasses should
 * implement specific command logic by overriding the {@link #validateInput()}, {@link
 * #initialize()}, and {@link #runCommand()} methods.
 */
public abstract class AbstractPalSubcommand extends AbstractTool implements Callable<Integer> {

  /** Path to the logging configuration file used by Logback. */
  private static final String LOGGING_CONFIG = "/cli-logging-fallback.xml";

  /** Provides connections to the PAL directory. */
  protected DirectoryConnectionProvider directoryConnectionProvider;

  /** The output stream used for standard output. */
  protected PrintStream out;

  /** The output stream used for error output. */
  protected PrintStream err;

  /**
   * Constructs a new AbstractPalSubcommand and initializes the output streams to System.out and
   * System.err.
   */
  protected AbstractPalSubcommand() {
    out = System.out;
    err = System.err;
  }

  /**
   * Initializes the DirectoryConnectionProvider with the given connection string.
   *
   * @param paldirConnectionString the connection string for the PAL directory, must not be null or
   *     empty
   */
  protected final void initializeDirectoryConnectionProvider(String paldirConnectionString) {
    this.directoryConnectionProvider =
        new DirectoryConnectionProvider(paldirConnectionString, null);
  }

  /**
   * Checks if a given option string is provided.
   *
   * @param option the option string to check
   * @return {@code true} if the option is non-null and not empty, {@code false} otherwise
   */
  protected static boolean optionGiven(String option) {
    return !(option == null || option.isEmpty());
  }

  /**
   * Validates the input provided to the subcommand.
   *
   * <p>This method should be implemented by subclasses to perform necessary input validations
   * before command execution.
   *
   * @throws RuntimeException if the input is invalid
   */
  protected abstract void validateInput();

  /**
   * Initializes and configures the logging system using Logback.
   *
   * <p>If a system property "cli.logging" is set and points to an existing file, that configuration
   * is used. Otherwise, the default logging configuration resource is loaded.
   */
  private void configureLogging() {
    // configure logging
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    context.reset();

    // look for a property named cli.logging in the System properties and use it as configuration if
    // the file exists
    final String palLogging = System.getProperty("cli.logging");
    if (palLogging != null && !palLogging.trim().isEmpty()) {
      boolean givenFileExists = false;
      try {
        if (Files.exists(Paths.get(palLogging))) {
          givenFileExists = true;
        }
      } catch (Exception ex) {
        ex.printStackTrace(System.err);
      }
      if (givenFileExists) {
        try {
          configurator.doConfigure(palLogging);
        } catch (Exception ex) {
          System.err.printf("Error loading logging configuration from %s%n", palLogging);
          // for more info: StatusPrinter.printInCaseOfErrorsOrWarnings(context);
          //noinspection CallToPrintStackTrace
          ex.printStackTrace();
        }
        return;
      }
    }

    // fall back to our default logging configuration
    try (final InputStream stream = Main.class.getResourceAsStream(LOGGING_CONFIG)) {
      configurator.doConfigure(stream);
    } catch (Exception ex) {
      System.err.printf("Error loading logging configuration from %s%n", LOGGING_CONFIG);
      // for more info: StatusPrinter.printInCaseOfErrorsOrWarnings(context);
      //noinspection CallToPrintStackTrace
      ex.printStackTrace();
    }
  }

  /**
   * Closes all open resources associated with the directory connection provider.
   *
   * @throws IOException if an I/O error occurs while closing resources
   */
  protected void closeResources() throws IOException {
    directoryConnectionProvider
        .get()
        .ifPresent(
            c -> {
              Optional<PalDirectory> palDirectory = directoryConnectionProvider.get();
              palDirectory.ifPresent(PalDirectory::close);
            });
  }

  /**
   * Executes the subcommand's main logic.
   *
   * @return an integer status code representing the result of the command execution
   * @throws Exception if an error occurs during command execution
   */
  protected abstract int runCommand() throws Exception;

  /**
   * Performs initialization steps required before running the command.
   *
   * @throws Exception if an error occurs during initialization
   */
  protected abstract void initialize() throws Exception;

  /**
   * Retrieves the PalDirectory instance from the directory connection provider.
   *
   * @return the PalDirectory instance
   * @throws RuntimeException if the PalDirectory is not available or not properly configured
   */
  protected PalDirectory getPalDirectory() {
    Optional<PalDirectory> palDirectory = directoryConnectionProvider.get();
    return palDirectory.orElseThrow(
        () ->
            new RuntimeException(
                "A PalDirectory is required. Run with -d (--dir)"
                    + " or set the ENV variable PAL_DIRECTORY."));
  }

  /**
   * Executes the subcommand by configuring logging, validating input, initializing, running the
   * command, and closing resources.
   *
   * @return the status code resulting from the command execution
   * @throws Exception if an error occurs during execution
   */
  @Override
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
