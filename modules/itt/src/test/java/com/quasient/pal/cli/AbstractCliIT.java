/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cli;

import com.quasient.pal.AbstractIntegrationTest;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for CLI integration tests.
 *
 * <p>Provides methods for launching required peers, waiting for them to be ready, and executing CLI
 * subcommands against running infrastructure (etcd, Kafka) and peers.
 *
 * <p>Extends {@link AbstractIntegrationTest} to inherit methods for running pal commands and
 * accessing directory/Kafka configuration.
 */
public abstract class AbstractCliIT extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractCliIT.class);

  /** List of Chronicle queue directories created during tests that need cleanup. */
  protected List<Path> chronicleDirectoriesToCleanup;

  /**
   * Executes a `pal ls` command with the given arguments.
   *
   * @param args command-line arguments to pass to `pal ls`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLs(String... args) throws Exception {
    return runCliSubcommand("ls", null, args);
  }

  /**
   * Executes a `pal rm` command with the given arguments.
   *
   * @param args command-line arguments to pass to `pal rm`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runRm(String... args) throws Exception {
    return runCliSubcommand("rm", null, args);
  }

  /**
   * Executes a `pal print` command with the given arguments.
   *
   * @param args command-line arguments to pass to `pal print`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runPrint(String... args) throws Exception {
    return runCliSubcommand("print", null, args);
  }

  /**
   * Executes a `pal call` command with the given arguments.
   *
   * @param args command-line arguments to pass to `pal call`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runCall(String... args) throws Exception {
    return runCliSubcommand("call", null, args);
  }

  /**
   * Executes a `pal call` command with JSON-RPC requests sent via stdin.
   *
   * <p>Each JSON-RPC request should be a complete JSON object on a single line.
   *
   * @param stdinData the data to send to stdin (one JSON-RPC request per line)
   * @param args command-line arguments to pass to `pal call`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runCallWithStdin(String stdinData, String... args) throws Exception {
    return runCliSubcommand("call", stdinData, args);
  }

  /**
   * Executes a PAL CLI subcommand with the given arguments and optional stdin data.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Handles global options (like -d) that must appear before the subcommand name
   *   <li>Cleans environment variables (PAL_DIRECTORY, KAFKA_SERVERS, etc.) to ensure tests are
   *       explicit about configuration
   *   <li>Optionally sends data to stdin if provided
   * </ul>
   *
   * <p><b>Important:</b> This method removes PAL_DIRECTORY and KAFKA_SERVERS from the environment.
   * Tests must explicitly pass these via command-line arguments (-d, -k) or the peer/log will not
   * be able to connect.
   *
   * @param subcommand the subcommand name (e.g., "ls", "rm", "print", "call")
   * @param stdinData optional data to send to stdin, or null for no stdin input
   * @param args command-line arguments; if first arg is "-d", it and the next arg are moved before
   *     subcommand
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  private CliProcessResult runCliSubcommand(String subcommand, String stdinData, String... args)
      throws Exception {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null || palHome.isEmpty()) {
      throw new IllegalStateException("PAL_HOME environment variable not set");
    }

    // Build command: pal [global-opts] <subcommand> <args>
    // Global options like -d must come BEFORE the subcommand
    List<String> command = new ArrayList<>();
    command.add(Paths.get(palHome, "bin", "pal").toAbsolutePath().toString());

    // Check if first arg is a global option (-d, -k, etc.)
    int startIdx = 0;
    if (args.length >= 2 && args[0].equals("-d")) {
      // Move -d and its value before subcommand
      command.add(args[0]); // -d
      command.add(args[1]); // directory value
      startIdx = 2;
    }

    // Add subcommand
    command.add(subcommand);

    // Add remaining args
    command.addAll(Arrays.asList(args).subList(startIdx, args.length));

    logger.info("Executing CLI command: {}", String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(new File(palHome));

    // Configure logging
    pb.environment()
        .put("PAL_CLI_LOGGING_CONFIG", Paths.get(palHome, "config", "cli-logging.xml").toString());

    // Remove environment variables that would interfere with tests
    // Tests must explicitly pass configuration via CLI args (e.g., -d, -k)
    pb.environment().remove("PAL_DIRECTORY");
    pb.environment().remove("KAFKA_SERVERS");
    pb.environment().remove("CHRONICLE_BASE_DIR");
    pb.environment().remove("PAL_JMX_HOST");
    pb.environment().remove("PAL_JMX_PORT");

    Process process = pb.start();

    // Write stdin data if provided
    if (stdinData != null) {
      try {
        process.getOutputStream().write(stdinData.getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();
        process.getOutputStream().close(); // Signal end of input
      } catch (IOException e) {
        logger.error("Error writing to stdin", e);
        throw e;
      }
    }

    // Capture stdout and stderr
    StringBuilder stdout = new StringBuilder();
    StringBuilder stderr = new StringBuilder();

    Thread stdoutThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(
                      new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  stdout.append(line).append("\n");
                }
              } catch (IOException e) {
                logger.error("Error reading stdout", e);
              }
            });

    Thread stderrThread =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(
                      new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  stderr.append(line).append("\n");
                }
              } catch (IOException e) {
                logger.error("Error reading stderr", e);
              }
            });

    stdoutThread.start();
    stderrThread.start();

    // Wait for process to complete with timeout
    boolean completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!completed) {
      process.destroy();
      throw new IllegalStateException(
          String.format("CLI command did not complete within %d seconds", PROCESS_TIMEOUT_SECONDS));
    }

    stdoutThread.join(2000);
    stderrThread.join(2000);

    int exitCode = process.exitValue();
    logger.info(
        "CLI command completed with exit code {}, stdout length: {}, stderr length: {}",
        exitCode,
        stdout.length(),
        stderr.length());

    if (logger.isDebugEnabled()) {
      logger.debug("-----CLI STDOUT-----\n{}", stdout);
      logger.debug("-----CLI STDERR-----\n{}", stderr);
    }

    return new CliProcessResult(exitCode, stdout.toString(), stderr.toString());
  }

  /**
   * Gets the classpath for itt-apps module.
   *
   * @return classpath string
   */
  protected String getIttAppsClasspath() {
    String palHome = System.getenv("PAL_HOME");
    return Paths.get(palHome + "/modules/itt-apps/target/classes").toAbsolutePath().toString();
  }

  /**
   * Tracks a Chronicle queue directory for cleanup after the test.
   *
   * <p>The Chronicle queue will be created in PAL_HOME (where the peer process runs), so we need to
   * construct the full path using PAL_HOME.
   *
   * @param queueName the name of the Chronicle queue directory (relative to or absolute path)
   */
  protected void trackChronicleDirectory(String queueName) {
    Path queuePath = Paths.get(queueName);
    if (queuePath.isAbsolute()) {
      chronicleDirectoriesToCleanup.add(queuePath);
    } else {
      String palHome = System.getenv("PAL_HOME");
      if (palHome != null) {
        chronicleDirectoriesToCleanup.add(Paths.get(palHome, queueName));
      } else {
        chronicleDirectoriesToCleanup.add(queuePath);
      }
    }
  }

  /** Container for CLI process execution results. */
  protected record CliProcessResult(int exitCode, String stdout, String stderr) {}
}
