/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.quasient.pal.AbstractIntegrationTest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for Main-related integration tests.
 *
 * <p>Provides common infrastructure for testing the Main class by launching actual peer processes
 * via pal.sh and capturing their exit codes and output. Tests extending this class can focus on
 * specific scenarios without duplicating process execution logic.
 */
public abstract class AbstractMainIT extends AbstractIntegrationTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  protected static final int PROCESS_TIMEOUT_SECONDS =
      15; // Increased to allow for Kafka health check timeout

  /**
   * Runs a pal command with the given arguments and returns the process result. Uses different
   * ports than peer4itts.sh to avoid conflicts.
   *
   * @param args the command-line arguments to pass to pal.sh run
   * @return ProcessResult containing exit code, stdout, and stderr
   * @throws IOException if process execution fails
   * @throws InterruptedException if the process is interrupted
   */
  protected ProcessResult runPalCommand(String... args) throws IOException, InterruptedException {
    return runPalCommandWithEnv(null, args);
  }

  /**
   * Runs a pal command with custom environment variables.
   *
   * @param palDirectory the PAL_DIRECTORY value to set, or null to remove it
   * @param args the command-line arguments to pass to pal.sh run
   * @return ProcessResult containing exit code, stdout, and stderr
   * @throws IOException if process execution fails
   * @throws InterruptedException if the process is interrupted
   */
  protected ProcessResult runPalCommandWithEnv(String palDirectory, String... args)
      throws IOException, InterruptedException {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    List<String> command = new ArrayList<>();
    command.add(Paths.get(palHome, "bin", "pal.sh").toString());
    command.add("run");

    // Add the test arguments
    command.addAll(Arrays.asList(args));

    logger.info("Running command: {}", String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.environment().put("PAL_HOME", palHome);

    // Set or remove PAL_DIRECTORY based on parameter
    if (palDirectory != null) {
      pb.environment().put("PAL_DIRECTORY", palDirectory);
    } else {
      pb.environment().remove("PAL_DIRECTORY");
    }

    // Remove other environment variables that would interfere with tests
    pb.environment().remove("KAFKA_SERVERS");
    pb.environment().remove("PAL_JMX_HOST");
    pb.environment().remove("PAL_JMX_PORT");

    Process process = pb.start();

    // Capture stdout and stderr
    StringBuilder stdout = new StringBuilder();
    StringBuilder stderr = new StringBuilder();

    Thread stdoutReader =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getInputStream(), UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  stdout.append(line).append("\n");
                }
              } catch (IOException e) {
                logger.warn("Error reading stdout", e);
              }
            });

    Thread stderrReader =
        new Thread(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(process.getErrorStream(), UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                  stderr.append(line).append("\n");
                }
              } catch (IOException e) {
                logger.warn("Error reading stderr", e);
              }
            });

    stdoutReader.start();
    stderrReader.start();

    boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new RuntimeException("Process timed out after " + PROCESS_TIMEOUT_SECONDS + " seconds");
    }

    stdoutReader.join(1000);
    stderrReader.join(1000);

    int exitCode = process.exitValue();
    String stdoutStr = stdout.toString();
    String stderrStr = stderr.toString();

    logger.info("Process exited with code: {}", exitCode);
    if (!stdoutStr.isEmpty()) {
      logger.debug("Process stdout: {}", stdoutStr);
    }
    if (!stderrStr.isEmpty()) {
      logger.debug("Process stderr: {}", stderrStr);
    }

    return new ProcessResult(exitCode, stdoutStr, stderrStr);
  }

  /** Container for process execution results. */
  protected record ProcessResult(int exitCode, String stdout, String stderr) {}
}
