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
package io.quasient.pal.docs;

import static org.junit.Assert.fail;

import io.quasient.pal.cli.AbstractCliIT;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests that execute all discovered documentation CLI commands against real
 * infrastructure (etcd + Kafka).
 *
 * <p>This test class dynamically discovers {@code pal} CLI commands from user-facing documentation
 * markdown files via {@link DocSnippetScanner}, transforms them for the test environment using
 * {@link CommandTransformer}, and executes them against running etcd and Kafka containers.
 *
 * <p>Test methods are organized by command category, with each method handling a group of commands
 * that share the same prerequisites, setup/teardown, and assertion patterns.
 *
 * <p><b>Infrastructure requirements:</b>
 *
 * <ul>
 *   <li>etcd running (Docker container)
 *   <li>Kafka running (Docker container)
 *   <li>{@code PAL_HOME}, {@code PAL_DIRECTORY}, {@code PAL_KAFKA_SERVERS} environment variables
 *       set
 *   <li>itt-apps compiled ({@code modules/itt-apps/target/classes} exists)
 * </ul>
 *
 * <p><b>Assertion strategy:</b>
 *
 * <ul>
 *   <li>Exit code 2 (PicoCLI usage error) always fails
 *   <li>Stderr containing "Unknown option" or "Unmatched argument" always fails
 *   <li>Exit code 0 always passes
 *   <li>Exit code 1 passes only when the main method itself throws (e.g., wrong application-level
 *       arguments); infrastructure or classpath errors (class not found, method not found) are
 *       failures because {@link CommandTransformer} substitutes real itt-apps classes
 *   <li>Failures are collected per test method and reported together
 * </ul>
 *
 * @see DocSnippetScanner
 * @see CommandTransformer
 * @see DocCommand
 * @see DocCommandType
 */
public class DocSnippetIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(DocSnippetIT.class);

  // ---------------------------------------------------------------------------
  // Coverage tracking (static — accumulated across all test methods)
  // ---------------------------------------------------------------------------

  /** Total number of pal commands discovered by the scanner. */
  private static int totalDiscovered;

  /** Number of commands actually executed (not skipped). */
  private static int totalTested;

  /** Number of commands skipped with reasons. */
  private static int totalSkipped;

  /** Per-type count of commands tested. */
  private static final Map<DocCommandType, Integer> testedCountByType =
      new EnumMap<>(DocCommandType.class);

  /** Per-type count of commands discovered. */
  private static final Map<DocCommandType, Integer> discoveredCountByType =
      new EnumMap<>(DocCommandType.class);

  /** Accumulated skip reasons for the coverage report: "{file}:{line} — {reason}". */
  private static final List<String> skipReasons = new ArrayList<>();

  // ---------------------------------------------------------------------------
  // Per-test resource tracking (instance — reset and drained by @After)
  // ---------------------------------------------------------------------------

  /** Peer processes launched during the current test method, stopped in {@link #cleanUp()}. */
  private final List<Process> launchedPeers = new ArrayList<>();

  /**
   * Kafka WAL/topic names created during the current test method, deleted in {@link #cleanUp()}.
   */
  private final List<String> createdKafkaTopics = new ArrayList<>();

  /**
   * Temporary directories created during the current test method, deleted in {@link #cleanUp()}.
   */
  private final List<Path> tempDirectories = new ArrayList<>();

  /** Temporary files (e.g., YAML bundles) created during the current test, deleted in cleanup. */
  private final List<Path> tempFiles = new ArrayList<>();

  // ---------------------------------------------------------------------------
  // Lifecycle
  // ---------------------------------------------------------------------------

  /**
   * Per-test cleanup that complements {@link AbstractCliIT#tearDownCLITest()}.
   *
   * <p>Stops all peers launched during the test (in reverse order), removes Kafka WAL topics
   * created during the test, deletes temporary files and directories, and cleans etcd of any
   * registered peers, logs, or intercepts. This method runs even when the test fails, preventing
   * state leakage between test methods.
   *
   * <p>Chronicle log directory cleanup is handled by the inherited {@code tearDownCLITest()} via
   * the {@link AbstractCliIT#chronicleLogsToCleanup} list.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void cleanUp() throws Exception {
    // 1. Stop all launched peers (reverse order)
    for (int i = launchedPeers.size() - 1; i >= 0; i--) {
      Process peer = launchedPeers.get(i);
      logger.info("Stopping peer process (pid {})", peer.pid());
      peer.destroyForcibly();
    }
    launchedPeers.clear();

    // 2. Delete Kafka WAL topics created during the test
    for (String topic : createdKafkaTopics) {
      try {
        logger.info("Removing Kafka WAL topic: {}", topic);
        runLogRm("-d", getPalDirectoryUrl(), topic, "--force");
      } catch (Exception e) {
        logger.warn("Failed to remove Kafka WAL topic {}: {}", topic, e.getMessage());
      }
    }
    createdKafkaTopics.clear();

    // 3. Delete temporary files (YAML bundles, etc.)
    for (Path file : tempFiles) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException e) {
        logger.warn("Failed to delete temp file {}: {}", file, e.getMessage());
      }
    }
    tempFiles.clear();

    // 4. Delete temporary directories recursively
    for (Path dir : tempDirectories) {
      if (Files.exists(dir)) {
        try {
          Files.walkFileTree(
              dir,
              new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                  Files.delete(file);
                  return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc)
                    throws IOException {
                  Files.delete(d);
                  return FileVisitResult.CONTINUE;
                }
              });
        } catch (IOException e) {
          logger.warn("Failed to delete temp directory {}: {}", dir, e.getMessage());
        }
      }
    }
    tempDirectories.clear();

    // 5. Clean etcd: handled by inherited tearDownCLITest() which calls
    //    deleteStaleEntriesInPalDirectory() and cleanUpChronicleLogDirectories()
  }

  /**
   * Logs a coverage report summarizing how many documentation commands were tested vs. skipped.
   *
   * <p>Produces an INFO-level log summary in the format: "Doc snippet coverage: X of Y pal commands
   * tested, Z skipped". For each skipped command, logs: "SKIPPED: {file}:{line} — {reason}". For
   * each command type, logs count tested vs. total discovered.
   */
  @AfterClass
  public static void logCoverageReport() {
    // TODO(#1435): Implement coverage reporting:
    // 1. Log "Doc snippet coverage: X of Y pal commands tested, Z skipped"
    // 2. For each skipped command, log "SKIPPED: <file>:<line> — <reason>"
    // 3. For each DocCommandType, log count tested vs. total discovered
    logger.info(
        "Doc snippet coverage: {} of {} pal commands tested, {} skipped",
        totalTested,
        totalDiscovered,
        totalSkipped);
    for (String reason : skipReasons) {
      logger.info("SKIPPED: {}", reason);
    }
    for (DocCommandType type : DocCommandType.values()) {
      int tested = testedCountByType.getOrDefault(type, 0);
      int discovered = discoveredCountByType.getOrDefault(type, 0);
      if (discovered > 0) {
        logger.info("  {}: {} tested / {} discovered", type, tested, discovered);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Test methods
  // ---------------------------------------------------------------------------

  /**
   * Tests that all {@link DocCommandType#HELP} commands execute with exit code 0.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testHelpCommands() throws Exception {
    // Given: All DocCommands with type HELP, transformed
    // When: Each executed via runCliSubcommand()
    // Then: All return exit code 0. Stderr does not contain
    //       "Unknown option" or "Unmatched argument"

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that all listing commands ({@link DocCommandType#PEER_LS}, {@link DocCommandType#LOG_LS},
   * {@link DocCommandType#INTERCEPT_LS}) execute with exit code 0.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testListingCommands() throws Exception {
    // Given: All DocCommands with type PEER_LS, LOG_LS, INTERCEPT_LS,
    //        transformed (address substitution applied)
    // When: Each executed via runPeerLs(), runLogLs(), runInterceptLs()
    // Then: All return exit code 0 (empty listings are acceptable)

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that all {@link DocCommandType#INIT} commands execute with exit code 0.
   *
   * <p>All init commands are run with {@code --dry-run} and {@code -y} appended, using a temporary
   * working directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testInitCommands() throws Exception {
    // Given: All DocCommands with type INIT, transformed
    //        (--dry-run, -y appended, temp working directory)
    // When: Each executed via runCliSubcommand() with temp dir as workingDir
    // Then: All return exit code 0. Temp directories cleaned up

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that short-lived {@link DocCommandType#RUN} commands execute successfully against real
   * infrastructure.
   *
   * <p>Short-lived commands are those where {@code longRunning=false}. They are expected to run and
   * exit within 30 seconds. Each command is executed with real itt-apps classes and validated for
   * correct execution, not just CLI syntax.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testShortLivedRunCommands() throws Exception {
    // Given: All DocCommands with type RUN where longRunning=false,
    //        transformed (classpath, main class, addresses, WAL names substituted)
    // When: Each executed via runPeer() with 30s timeout
    // Then: Exit code is 0 or 1 (main method may throw due to app-level args).
    //       Exit code 1 with class/method not found is a FAILURE (transformer
    //       substitutes real itt-apps classes, so classpath errors indicate a bug).
    //       NOT exit code 2 (CLI syntax error).
    //       Stderr does not contain PicoCLI usage errors.
    //       WALs and Chronicle paths cleaned up

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that long-running {@link DocCommandType#RUN} commands start and stop cleanly.
   *
   * <p>Long-running commands are those that include flags like {@code --json-rpc}, {@code
   * --zmq-rpc}, or {@code --interceptable}, indicating they are intended to run as persistent
   * peers.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testLongRunningRunCommands() throws Exception {
    // Given: All DocCommands with type RUN where longRunning=true, transformed
    // When: Each executed via launchPeer(), wait for ready, then stopPeer()
    // Then: Peer starts successfully (waitForLogLine for startup complete).
    //       Peer stops cleanly.
    //       All created resources cleaned up

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that all {@link DocCommandType#PEER_CALL} commands execute against a live peer.
   *
   * <p>Setup launches one peer with ZMQ-RPC + JSON-RPC, {@code --interceptable}, {@code
   * --rpc-default-action ALLOW}, and a known classpath. Teardown stops the peer.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testPeerCallCommands() throws Exception {
    // Given: One running peer with ZMQ-RPC + JSON-RPC, --interceptable,
    //        --rpc-default-action ALLOW, known classpath.
    //        All DocCommands with type PEER_CALL, transformed
    //        (substitute peer name/UUID and main class)
    // When: Each executed via runPeerCall() or runPeerCallWithStdin()
    // Then: Exit code is 0. Stderr clean
    // Teardown: Stop peer

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that all {@link DocCommandType#PEER_PRINT} commands execute against a live peer.
   *
   * <p>Setup launches one peer briefly to generate some messages. Print commands may stream output,
   * so they are run for a limited duration.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testPeerPrintCommands() throws Exception {
    // Given: One peer launched briefly to generate some messages.
    //        All DocCommands with type PEER_PRINT, transformed
    // When: Each executed via runCliSubcommandForDuration()
    //       (print commands may stream, so run for limited duration)
    // Then: Exit code is 0 or 143 (killed after duration).
    //       No CLI syntax errors
    // Teardown: Stop peer if running

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that log print, index, and stats commands execute against existing logs.
   *
   * <p>Setup runs a peer briefly to create a Kafka WAL with some messages and also creates a
   * Chronicle WAL under {@code /tmp}. Teardown removes the Kafka topic and deletes the Chronicle
   * directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testLogPrintAndStatsCommands() throws Exception {
    // Given: A peer run briefly to create a Kafka WAL with messages.
    //        A Chronicle WAL created under /tmp.
    //        All DocCommands with type LOG_PRINT, LOG_INDEX, LOG_STATS, transformed
    // When: Each executed via runLogPrint(), runLogIndex(), runLogStats()
    // Then: Exit code is 0. Commands against existing logs return data
    // Teardown: Remove Kafka topic, delete Chronicle dir

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that all {@link DocCommandType#LOG_CALL} commands execute against an existing WAL.
   *
   * <p>Setup ensures at least one Kafka WAL exists. Teardown cleans up the WAL.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testLogCallCommands() throws Exception {
    // Given: At least one Kafka WAL exists.
    //        All DocCommands with type LOG_CALL, transformed
    // When: Each executed via runLogCall() or runLogCallWithStdin()
    // Then: Exit code is 0
    // Teardown: Clean up WAL

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that all {@link DocCommandType#LOG_RM} commands successfully remove temporary logs.
   *
   * <p>Setup creates temporary WALs/logs for each remove command. Each remove command runs against
   * its own dedicated temporary log.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testLogRemoveCommands() throws Exception {
    // Given: Temporary WALs/logs created for each remove command.
    //        All DocCommands with type LOG_RM, transformed
    // When: Each executed via runLogRm() against a dedicated temporary log
    // Then: Exit code is 0. Log no longer appears in directory
    // Teardown: (logs already removed by the commands under test)

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that all {@link DocCommandType#REPLAY} commands execute against a shared Chronicle WAL.
   *
   * <p>Setup runs a short peer to record a Chronicle WAL under {@code /tmp/pal-doc-test/}. This WAL
   * is shared across all replay tests. Teardown deletes the shared Chronicle WAL directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testReplayCommands() throws Exception {
    // Given: A short peer run to record a Chronicle WAL under /tmp/pal-doc-test/.
    //        Shared WAL across replay tests.
    //        All DocCommands with type REPLAY, transformed
    // When: Each executed via runPeer() (replay runs and exits)
    // Then: Exit code is 0 or acceptable (divergences OK). Not exit code 2
    // Teardown: Delete shared Chronicle WAL dir

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that intercept apply and diff commands execute with temporary YAML bundles.
   *
   * <p>Setup creates temporary YAML intercept bundle files matching doc patterns and launches a
   * peer with {@code --interceptable}. Teardown removes intercepts, stops the peer, and deletes
   * temp YAML files.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testInterceptApplyAndDiffCommands() throws Exception {
    // Given: Temporary YAML intercept bundle files created matching doc patterns.
    //        A peer launched with --interceptable.
    //        All DocCommands with type INTERCEPT_APPLY, INTERCEPT_DIFF, transformed
    //        (file paths point to temp YAML)
    // When: Each executed via runInterceptApply(), runInterceptDiff()
    // Then: Exit code is 0. Intercepts visible in directory
    // Teardown: Remove intercepts, stop peer, delete temp YAML files

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that intercept remove and status commands execute after applying an intercept bundle.
   *
   * <p>Setup applies an intercept bundle first and launches a peer with {@code --interceptable}.
   * Teardown stops the peer and cleans the directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testInterceptRemoveAndStatusCommands() throws Exception {
    // Given: An intercept bundle applied first.
    //        A peer launched with --interceptable.
    //        All DocCommands with type INTERCEPT_RM, INTERCEPT_STATUS, transformed
    // When: Each executed via runInterceptRm(), runInterceptStatus()
    // Then: Exit code is 0
    // Teardown: Stop peer, clean directory

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Meta-test that verifies every non-skipped command type has a corresponding test method.
   *
   * <p>Collects all {@link DocCommandType} values tested by the above methods and asserts that
   * every non-{@link DocCommandType#SKIPPED} and non-{@link DocCommandType#NON_PAL} command
   * discovered by the scanner is covered by at least one test method. If a new {@code
   * DocCommandType} appears in docs without a handler, this test fails with a descriptive message
   * listing the uncovered types and their source locations.
   *
   * @throws Exception if test execution fails
   */
  @Test
  @Ignore("Awaiting implementation in #1435")
  public void testAllDocCommandsAreCovered() throws Exception {
    // Given: All DocCommandType values tested across the above test methods
    // When: Compared against every non-SKIPPED, non-NON_PAL command discovered
    //       by the scanner
    // Then: Every discovered command type is covered by at least one test method.
    //       If a new DocCommandType appears in docs without a handler,
    //       this test fails with a descriptive message listing
    //       the uncovered types and their source locations

    // TODO(#1435): Implement test logic
    fail("Not yet implemented");
  }
}
