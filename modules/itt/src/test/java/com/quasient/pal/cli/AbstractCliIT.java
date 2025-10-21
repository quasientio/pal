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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for CLI integration tests.
 *
 * <p>Provides methods for launching transient peers, waiting for them to be ready, and executing
 * CLI subcommands against running infrastructure (etcd, Kafka) and peers.
 *
 * <p>Extends {@link AbstractIntegrationTest} to inherit methods for running pal commands and
 * accessing directory/Kafka configuration.
 */
public abstract class AbstractCliIT extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractCliIT.class);

  /**
   * Stops a running peer gracefully.
   *
   * <p>Destroys the process and waits up to 5 seconds for it to terminate. If still alive, force
   * kills it.
   *
   * @param process the peer process to stop
   * @throws InterruptedException if interrupted while waiting for process termination
   */
  protected void stopPeer(Process process) throws InterruptedException {
    if (process == null || !process.isAlive()) {
      return;
    }

    logger.info("Stopping peer process");
    process.destroy();
    boolean exited = process.waitFor(5, TimeUnit.SECONDS);

    if (!exited) {
      logger.warn("Peer did not exit gracefully, force killing");
      process.destroyForcibly();
      process.waitFor(2, TimeUnit.SECONDS);
    }

    logger.info("Peer stopped");
  }

  /**
   * Executes a `pal ls` command with the given arguments.
   *
   * @param args command-line arguments to pass to `pal ls`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLs(String... args) throws Exception {
    return runCliSubcommand("ls", args);
  }

  /**
   * Executes a `pal rm` command with the given arguments.
   *
   * @param args command-line arguments to pass to `pal rm`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runRm(String... args) throws Exception {
    return runCliSubcommand("rm", args);
  }

  /**
   * Executes a `pal print` command with the given arguments.
   *
   * @param args command-line arguments to pass to `pal print`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runPrint(String... args) throws Exception {
    return runCliSubcommand("print", args);
  }

  /**
   * Executes a `pal call` command with the given arguments.
   *
   * @param args command-line arguments to pass to `pal call`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runCall(String... args) throws Exception {
    return runCliSubcommand("call", args);
  }

  /**
   * Executes a PAL CLI subcommand with the given arguments.
   *
   * <p>This method handles global options (like -d) that must appear before the subcommand name.
   *
   * @param subcommand the subcommand name (e.g., "ls", "rm", "print", "call")
   * @param args command-line arguments; if first arg is "-d", it and the next arg are moved before
   *     subcommand
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  private CliProcessResult runCliSubcommand(String subcommand, String... args) throws Exception {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null || palHome.isEmpty()) {
      throw new IllegalStateException("PAL_HOME environment variable not set");
    }

    // Build command: pal [global-opts] <subcommand> <args>
    // Global options like -d must come BEFORE the subcommand
    List<String> command = new ArrayList<>();
    command.add(palHome + "/bin/pal");

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
    for (int i = startIdx; i < args.length; i++) {
      command.add(args[i]);
    }

    logger.info("Executing CLI command: {}", String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(new File(palHome));

    Process process = pb.start();

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

    return new CliProcessResult(exitCode, stdout.toString(), stderr.toString());
  }

  /** Container for CLI process execution results. */
  protected record CliProcessResult(int exitCode, String stdout, String stderr) {}
}
