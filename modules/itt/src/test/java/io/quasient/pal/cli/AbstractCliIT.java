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
package io.quasient.pal.cli;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
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

  /**
   * Exit code returned by {@link #runCliSubcommandForDuration} when the process is killed after the
   * timeout expires rather than exiting on its own.
   */
  protected static final int EXIT_CODE_KILLED = -1;

  /** Counter for generating unique coverage file names for CLI processes. */
  private static final AtomicInteger cliInvocationCounter = new AtomicInteger(0);

  /** List of Chronicle-queue Logs (i.e. directories) created during tests that need cleanup. */
  protected List<Path> chronicleLogsToCleanup;

  /** Sets up test environment before each test. */
  @Before
  public void setUpCLITest() {
    chronicleLogsToCleanup = new ArrayList<>();
  }

  /**
   * Cleans up resources after each test.
   *
   * @throws ExecutionException if etcd lookup fails
   * @throws InterruptedException if the thread is interrupted
   */
  @After
  public void tearDownCLITest() throws ExecutionException, InterruptedException {
    cleanUpChronicleLogDirectories();
    deleteStaleEntriesInPalDirectory();
  }

  /**
   * Deletes any peers and logs remaining in the PAL directory after a test.
   *
   * <p>CLI tests launch peers via {@code launchPeer()} which registers them in etcd with a 60s TTL
   * lease. When the peer process is killed, the lease may keep the registration alive for up to 60
   * seconds. This method cleans up those stale registrations so they don't interfere with
   * subsequent test runs.
   */
  private void deleteStaleEntriesInPalDirectory() {
    PalDirectory palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    try {
      // Delete stale peer registrations left behind by killed peer processes
      palDirectory.deletePeers();
    } catch (Exception e) {
      logger.error("Error cleaning up peers", e);
    }
    try {
      Set<LogInfo> allLogs = palDirectory.listAllLogs();
      if (allLogs != null && !allLogs.isEmpty()) {
        allLogs.forEach(
            l -> {
              try {
                palDirectory.deleteLog(l.getUuid());
              } catch (Exception e) {
                logger.error("Error cleaning up log", e);
              }
            });
      }
    } catch (Exception e) {
      logger.error("Error listing logs for cleanup", e);
    }
    palDirectory.close();
  }

  /** Cleans up Chronicle log directories created during the test. */
  private void cleanUpChronicleLogDirectories() throws ExecutionException, InterruptedException {
    // Clean up Chronicle queue directories created during the test
    logger.info("Cleaning up {} Chronicle queue directories", chronicleLogsToCleanup.size());

    // initialize PalDirectory to look up absolute path of Logs passed with relative path
    PalDirectory palDirectory = new PalDirectory(getPalDirectoryUrl(), true);

    // loop through all chronicle directories to cleanup
    for (Path chronicleDir : chronicleLogsToCleanup) {
      Path absChronicleDirPath = null;
      // if chronicle directory is absolute, use it as is
      if (chronicleDir.isAbsolute()) {
        absChronicleDirPath = chronicleDir;
      } else { // if chronicle directory is relative, look up absolute path in PalDirectory
        LogInfo logToDelete = palDirectory.getLogInfo(chronicleDir.toString());
        if (logToDelete != null) {
          absChronicleDirPath = Path.of(palDirectory.getLogInfo(chronicleDir.toString()).getName());
        } else {
          // Fallback: resolve against PAL_HOME (peer process working directory)
          String palHome = System.getenv("PAL_HOME");
          if (palHome != null) {
            absChronicleDirPath = Path.of(palHome).resolve(chronicleDir);
          }
        }
      }

      // delete chronicle directory if it exists
      if (absChronicleDirPath != null && Files.exists(absChronicleDirPath)) {
        logger.info("Deleting Chronicle queue directory: {}", absChronicleDirPath);
        try (Stream<Path> files = Files.walk(absChronicleDirPath)) {
          files
              .sorted(Comparator.reverseOrder())
              .forEach(
                  path -> {
                    try {
                      Files.delete(path);
                    } catch (IOException e) {
                      logger.warn("Failed to delete Chronicle queue file: {}", path, e);
                    }
                  });
          logger.info("Successfully deleted Chronicle queue directory: {}", absChronicleDirPath);
        } catch (IOException e) {
          logger.warn("Failed to clean up Chronicle queue directory: {}", absChronicleDirPath, e);
        }
      } else {
        logger.debug("Chronicle queue directory does not exist: {}", absChronicleDirPath);
      }
    }
    palDirectory.close();
    chronicleLogsToCleanup.clear();
  }

  /**
   * Executes a `pal replay` command with the given arguments.
   *
   * <p>Retained for replay tests outside the CLI package.
   *
   * @param args command-line arguments to pass to `pal replay`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runReplay(String... args) throws Exception {
    return runCliSubcommand("replay", null, args);
  }

  /**
   * Executes a `pal replay` command with the given working directory and arguments.
   *
   * <p>The working directory controls where relative {@code file:} WAL paths are resolved against.
   *
   * @param workingDir the working directory for the replay process
   * @param args command-line arguments to pass to `pal replay`
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runReplayFromDir(File workingDir, String... args) throws Exception {
    return runCliSubcommand(new String[] {"replay"}, null, workingDir, args);
  }

  /**
   * Executes a {@code pal log print} command with the given working directory and arguments.
   *
   * <p>The working directory controls where relative {@code file:} log paths are resolved against.
   *
   * @param workingDir the working directory for the process
   * @param args command-line arguments to pass to {@code pal log print}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogPrintFromDir(File workingDir, String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "print"}, null, workingDir, args);
  }

  /**
   * Executes a {@code pal log rm} command with the given working directory and arguments.
   *
   * <p>The working directory controls where relative {@code file:} log paths are resolved against.
   *
   * @param workingDir the working directory for the process
   * @param args command-line arguments to pass to {@code pal log rm}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogRmFromDir(File workingDir, String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "rm"}, null, workingDir, args);
  }

  /**
   * Executes a {@code pal log index} command with the given working directory and arguments.
   *
   * <p>The working directory controls where relative {@code file:} log paths are resolved against.
   *
   * @param workingDir the working directory for the process
   * @param args command-line arguments to pass to {@code pal log index}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogIndexFromDir(File workingDir, String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "index"}, null, workingDir, args);
  }

  // ==========================================================================
  // Entity-operation helpers for the CLI structure.
  // These use multi-part subcommand paths (e.g., {"peer", "ls"}).
  // ==========================================================================

  /**
   * Executes a {@code pal peer ls} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal peer ls}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runPeerLs(String... args) throws Exception {
    return runCliSubcommand(new String[] {"peer", "ls"}, null, args);
  }

  /**
   * Executes a {@code pal peer rm} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal peer rm}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runPeerRm(String... args) throws Exception {
    return runCliSubcommand(new String[] {"peer", "rm"}, null, args);
  }

  /**
   * Executes a {@code pal peer prune} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal peer prune}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runPeerPrune(String... args) throws Exception {
    return runCliSubcommand(new String[] {"peer", "prune"}, null, args);
  }

  /**
   * Executes a {@code pal peer print} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal peer print}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runPeerPrint(String... args) throws Exception {
    return runCliSubcommand(new String[] {"peer", "print"}, null, args);
  }

  /**
   * Executes a {@code pal peer call} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal peer call}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runPeerCall(String... args) throws Exception {
    return runCliSubcommand(new String[] {"peer", "call"}, null, args);
  }

  /**
   * Executes a {@code pal peer call} command with JSON-RPC requests sent via stdin.
   *
   * @param stdinData the data to send to stdin (one JSON-RPC request per line)
   * @param args command-line arguments to pass to {@code pal peer call}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runPeerCallWithStdin(String stdinData, String... args)
      throws Exception {
    return runCliSubcommand(new String[] {"peer", "call"}, stdinData, args);
  }

  /**
   * Executes a {@code pal log prune} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal log prune}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogPrune(String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "prune"}, null, args);
  }

  /**
   * Executes a {@code pal log ls} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal log ls}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogLs(String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "ls"}, null, args);
  }

  /**
   * Executes a {@code pal log rm} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal log rm}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogRm(String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "rm"}, null, args);
  }

  /**
   * Executes a {@code pal log print} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal log print}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogPrint(String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "print"}, null, args);
  }

  /**
   * Executes a {@code pal log call} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal log call}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogCall(String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "call"}, null, args);
  }

  /**
   * Executes a {@code pal log call} command with JSON-RPC requests sent via stdin.
   *
   * @param stdinData the data to send to stdin (one JSON-RPC request per line)
   * @param args command-line arguments to pass to {@code pal log call}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogCallWithStdin(String stdinData, String... args)
      throws Exception {
    return runCliSubcommand(new String[] {"log", "call"}, stdinData, args);
  }

  /**
   * Executes a {@code pal intercept ls} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal intercept ls}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runInterceptLs(String... args) throws Exception {
    return runCliSubcommand(new String[] {"intercept", "ls"}, null, args);
  }

  /**
   * Executes a {@code pal intercept apply} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal intercept apply}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runInterceptApply(String... args) throws Exception {
    return runCliSubcommand(new String[] {"intercept", "apply"}, null, args);
  }

  /**
   * Executes a {@code pal intercept rm} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal intercept rm}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runInterceptRm(String... args) throws Exception {
    return runCliSubcommand(new String[] {"intercept", "rm"}, null, args);
  }

  /**
   * Executes a {@code pal intercept diff} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal intercept diff}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runInterceptDiff(String... args) throws Exception {
    return runCliSubcommand(new String[] {"intercept", "diff"}, null, args);
  }

  /**
   * Executes a {@code pal intercept status} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal intercept status}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runInterceptStatus(String... args) throws Exception {
    return runCliSubcommand(new String[] {"intercept", "status"}, null, args);
  }

  /**
   * Executes a {@code pal log stats} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal log stats}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogStats(String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "stats"}, null, args);
  }

  /**
   * Executes a {@code pal peer stats} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal peer stats}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runPeerStats(String... args) throws Exception {
    return runCliSubcommand(new String[] {"peer", "stats"}, null, args);
  }

  /**
   * Executes a {@code pal log index} command with the given arguments.
   *
   * @param args command-line arguments to pass to {@code pal log index}
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runLogIndex(String... args) throws Exception {
    return runCliSubcommand(new String[] {"log", "index"}, null, args);
  }

  /**
   * Runs a CLI subcommand for a specified duration, then terminates the process.
   *
   * <p>Used for streaming commands (e.g., {@code pal peer print}, {@code pal peer stats}) that run
   * indefinitely until killed. The process is started, allowed to run for the specified duration,
   * then destroyed. Whatever output was captured during that time is returned.
   *
   * @param subcommandParts the subcommand path parts (e.g., {"peer", "print"})
   * @param durationSeconds how long to let the process run before killing it
   * @param args command-line arguments
   * @return CliProcessResult containing exit code (-1 if killed), stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runCliSubcommandForDuration(
      String[] subcommandParts, int durationSeconds, String... args) throws Exception {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null || palHome.isEmpty()) {
      throw new IllegalStateException("PAL_HOME environment variable not set");
    }

    List<String> command = new ArrayList<>();
    command.add(Paths.get(palHome, "bin", "pal").toAbsolutePath().toString());

    int startIdx = 0;
    if (args.length >= 2 && args[0].equals("-d")) {
      command.add(args[0]);
      command.add(args[1]);
      startIdx = 2;
    }

    command.addAll(Arrays.asList(subcommandParts));
    command.addAll(Arrays.asList(args).subList(startIdx, args.length));

    logger.info("Executing CLI command for {}s: {}", durationSeconds, String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(new File(palHome));
    pb.environment()
        .put("PAL_CLI_LOGGING_CONFIG", Paths.get(palHome, "config", "cli-logging.xml").toString());
    pb.environment().remove("PAL_DIRECTORY");
    pb.environment().remove("PAL_KAFKA_SERVERS");
    pb.environment().remove("PAL_CHRONICLE_BASE_DIR");
    pb.environment().remove("PAL_JMX_HOST");
    pb.environment().remove("PAL_JMX_PORT");

    Process process = pb.start();

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
                // Process was destroyed, expected
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
                // Process was destroyed, expected
              }
            });

    stdoutThread.start();
    stderrThread.start();

    boolean completed = process.waitFor(durationSeconds, TimeUnit.SECONDS);
    int exitCode;
    if (!completed) {
      process.destroy();
      process.waitFor(2, TimeUnit.SECONDS);
      exitCode = EXIT_CODE_KILLED;
    } else {
      exitCode = process.exitValue();
    }

    stdoutThread.join(2000);
    stderrThread.join(2000);

    logger.info(
        "CLI command {} after {}s, exit code {}, stdout length: {}, stderr length: {}",
        completed ? "completed" : "killed",
        durationSeconds,
        exitCode,
        stdout.length(),
        stderr.length());

    return new CliProcessResult(exitCode, stdout.toString(), stderr.toString());
  }

  /**
   * Executes a PAL CLI subcommand with multi-part subcommand path and optional stdin data.
   *
   * <p>This method supports the new entity-operation command structure where subcommands consist of
   * multiple parts (e.g., {@code {"peer", "ls"}} for {@code pal peer ls}).
   *
   * @param subcommandParts the subcommand path parts (e.g., {"peer", "ls"}, {"log", "print"})
   * @param stdinData optional data to send to stdin, or null for no stdin input
   * @param args command-line arguments; if first arg is "-d", it and the next arg are moved before
   *     subcommand
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runCliSubcommand(
      String[] subcommandParts, String stdinData, String... args) throws Exception {
    return runCliSubcommand(subcommandParts, stdinData, null, args);
  }

  /**
   * Executes a PAL CLI subcommand with multi-part subcommand path, optional stdin data, and an
   * optional working directory override.
   *
   * @param subcommandParts the subcommand path parts (e.g., {"peer", "ls"}, {"log", "print"})
   * @param stdinData optional data to send to stdin, or null for no stdin input
   * @param workingDir optional working directory for the subprocess, or null to use PAL_HOME
   * @param args command-line arguments; if first arg is "-d", it and the next arg are moved before
   *     subcommand
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   */
  protected CliProcessResult runCliSubcommand(
      String[] subcommandParts, String stdinData, File workingDir, String... args)
      throws Exception {
    String palHome = System.getenv("PAL_HOME");
    if (palHome == null || palHome.isEmpty()) {
      throw new IllegalStateException("PAL_HOME environment variable not set");
    }

    // Build command: pal [global-opts] <subcommand-parts...> <args>
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

    // Add all subcommand parts
    command.addAll(Arrays.asList(subcommandParts));

    // Add remaining args
    command.addAll(Arrays.asList(args).subList(startIdx, args.length));

    String subcommandStr = String.join(" ", subcommandParts);
    logger.info("Executing CLI command: {}", String.join(" ", command));

    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(workingDir != null ? workingDir : new File(palHome));

    // Configure logging
    pb.environment()
        .put("PAL_CLI_LOGGING_CONFIG", Paths.get(palHome, "config", "cli-logging.xml").toString());

    // Configure JaCoCo agent for CLI process coverage collection
    String jacocoAgentJar = System.getProperty("jacoco.agent.jar");
    String jacocoDestFileDir = System.getProperty("jacoco.destfile.dir");
    if (jacocoAgentJar != null && jacocoDestFileDir != null) {
      File agentFile = new File(jacocoAgentJar);
      if (agentFile.exists()) {
        // Create unique coverage file for this CLI invocation
        int invocationId = cliInvocationCounter.getAndIncrement();
        String coverageFile =
            Paths.get(
                    jacocoDestFileDir,
                    "jacoco-cli-" + subcommandStr.replace(' ', '-') + "-" + invocationId + ".exec")
                .toString();
        // Note: bin/pal script adds '-javaagent:' prefix automatically, so only provide
        // path+options
        String javaAgent =
            String.format(
                "%s=destfile=%s,append=true,dumponexit=true", jacocoAgentJar, coverageFile);
        pb.environment().put("JAVA_AGENT", javaAgent);
        logger.debug("Enabled JaCoCo agent for CLI process: {}", coverageFile);
      } else {
        logger.warn("JaCoCo agent JAR not found at: {}", jacocoAgentJar);
      }
    }

    // Remove environment variables that would interfere with tests
    // Tests must explicitly pass configuration via CLI args (e.g., -d, -k)
    pb.environment().remove("PAL_DIRECTORY");
    pb.environment().remove("PAL_KAFKA_SERVERS");
    pb.environment().remove("PAL_CHRONICLE_BASE_DIR");
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
   * Executes a PAL CLI subcommand with the given arguments and optional stdin data.
   *
   * <p>This method:
   *
   * <ul>
   *   <li>Handles global options (like -d) that must appear before the subcommand name
   *   <li>Cleans environment variables (PAL_DIRECTORY, PAL_KAFKA_SERVERS, etc.) to ensure tests are
   *       explicit about configuration
   *   <li>Optionally sends data to stdin if provided
   * </ul>
   *
   * <p><b>Important:</b> This method removes PAL_DIRECTORY and PAL_KAFKA_SERVERS from the
   * environment. Tests must explicitly pass these via command-line arguments (-d, -k) or the
   * peer/log will not be able to connect.
   *
   * @param subcommand the subcommand name (e.g., "ls", "rm", "print", "call")
   * @param stdinData optional data to send to stdin, or null for no stdin input
   * @param args command-line arguments; if first arg is "-d", it and the next arg are moved before
   *     subcommand
   * @return CliProcessResult containing exit code, stdout, and stderr
   * @throws Exception if command execution fails
   * @deprecated Use {@link #runCliSubcommand(String[], String, String...)} for multi-part
   *     subcommand paths
   */
  @Deprecated
  private CliProcessResult runCliSubcommand(String subcommand, String stdinData, String... args)
      throws Exception {
    return runCliSubcommand(new String[] {subcommand}, stdinData, args);
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
   * Tracks a Chronicle Log for cleanup after the test.
   *
   * @param queueName the name of the Chronicle Log
   */
  protected void trackChronicleLog(String queueName) {
    chronicleLogsToCleanup.add(Paths.get(queueName));
  }

  /** Container for CLI process execution results. */
  protected record CliProcessResult(int exitCode, String stdout, String stderr) {}
}
