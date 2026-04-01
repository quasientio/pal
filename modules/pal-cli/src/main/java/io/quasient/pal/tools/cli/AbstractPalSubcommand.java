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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import io.quasient.pal.core.service.Main;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.tools.AbstractTool;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves as the base class for PAL CLI subcommands, providing common functionalities such as
 * logging configuration, directory connection management, and resource handling. Subclasses should
 * implement specific command logic by overriding the {@link #validateInput()}, {@link
 * #initialize()}, and {@link #runCommand()} methods.
 */
public abstract class AbstractPalSubcommand extends AbstractTool implements Callable<Integer> {

  /** Class logger. */
  private static final Logger logger = LoggerFactory.getLogger(AbstractPalSubcommand.class);

  /** Path to the logging configuration file used by Logback. */
  private static final String LOGGING_CONFIG = "/cli-logging-fallback.xml";

  /** Provides connections to the PAL directory. */
  protected DirectoryConnectionProvider directoryConnectionProvider;

  /** The output stream used for standard output. */
  protected PrintStream out;

  /** The output stream used for error output. */
  protected PrintStream err;

  /**
   * Constructs a new AbstractPalSubcommand and initializes the output streams to 'System.out' and
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
        new DirectoryConnectionProvider(paldirConnectionString, null, true);
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
   * Retrieves the Kafka bootstrap servers from the PAL_KAFKA_SERVERS environment variable.
   *
   * <p>This method reads the {@code PAL_KAFKA_SERVERS} environment variable and returns its value.
   * If the environment variable is not set or is empty, {@code null} is returned. This allows CLI
   * commands to use Kafka without requiring a PAL directory connection.
   *
   * @return the Kafka bootstrap servers connection string (e.g., "localhost:9092"), or {@code null}
   *     if not configured
   */
  protected static String getKafkaServers() {
    final String kafkaServers = System.getenv("PAL_KAFKA_SERVERS");
    if (kafkaServers == null || kafkaServers.isEmpty()) {
      return null;
    }
    return kafkaServers;
  }

  /**
   * Retrieves the Chronicle base directory from the {@code PAL_CHRONICLE_BASE_DIR} environment
   * variable.
   *
   * <p>This allows CLI commands to resolve relative Chronicle log paths against the same base
   * directory used by {@code pal run --chronicle-base-dir}, ensuring consistent path resolution
   * between log creation and querying.
   *
   * @return the Chronicle base directory as a {@link Path}, or {@code null} if not configured
   */
  @Nullable
  protected static Path getChronicleBaseDir() {
    String baseDir = System.getenv("PAL_CHRONICLE_BASE_DIR");
    if (baseDir == null || baseDir.isEmpty()) {
      return null;
    }
    return Path.of(baseDir);
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
      } catch (InvalidPathException | SecurityException ex) {
        logger.error("Error checking logging configuration path: {}", palLogging, ex);
      }
      if (givenFileExists) {
        try {
          configurator.doConfigure(palLogging);
        } catch (JoranException ex) {
          logger.error("Error loading logging configuration from {}", palLogging, ex);
        }
        return;
      }
    }

    // fall back to our default logging configuration
    try (final InputStream stream = Main.class.getResourceAsStream(LOGGING_CONFIG)) {
      configurator.doConfigure(stream);
    } catch (JoranException | IOException ex) {
      logger.error("Error loading fallback logging configuration from {}", LOGGING_CONFIG, ex);
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
