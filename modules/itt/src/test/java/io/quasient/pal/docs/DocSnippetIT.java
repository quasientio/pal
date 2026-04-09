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

import static org.junit.Assert.assertTrue;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.cli.AbstractCliIT;
import io.quasient.pal.docs.CommandTransformer.TransformedCommand;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
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

  /** Main class for generic doc examples (short-lived, exits after running). */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Duration in seconds to let streaming commands run before killing. */
  private static final int STREAMING_DURATION_SECONDS = 3;

  /** Timeout in seconds for short-lived peer run commands. */
  private static final int SHORT_LIVED_TIMEOUT_SECONDS = 30;

  // ---------------------------------------------------------------------------
  // Static state (set once in @BeforeClass, shared across all test methods)
  // ---------------------------------------------------------------------------

  /** All discovered documentation commands. */
  private static List<DocCommand> allCommands;

  /** Transformer for adapting doc commands to the test environment. */
  private static CommandTransformer transformer;

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
   * Scans documentation files, discovers all PAL CLI commands, and initializes the transformer.
   *
   * <p>Resolves the docs root from the {@code PAL_HOME} environment variable, runs {@link
   * DocSnippetScanner#scan(Path)}, asserts at least 50 pal commands are found (sanity check), and
   * creates a {@link CommandTransformer} with the test environment's etcd URL, Kafka servers, and
   * itt-apps classpath. Logs per-type discovery counts at INFO level.
   */
  @BeforeClass
  public static void scanDocs() {
    String palHome = System.getenv("PAL_HOME");
    assertTrue("PAL_HOME environment variable must be set", palHome != null && !palHome.isEmpty());

    Path docsRoot = Paths.get(palHome, "docs", "user", "docs");
    allCommands = DocSnippetScanner.scan(docsRoot);

    for (DocCommand cmd : allCommands) {
      discoveredCountByType.merge(cmd.getType(), 1, Integer::sum);
    }
    totalDiscovered = allCommands.size();

    long palCommandCount =
        allCommands.stream().filter(c -> c.getType() != DocCommandType.NON_PAL).count();
    assertTrue(
        "Scanner must find at least 50 pal commands, found " + palCommandCount,
        palCommandCount >= 50);

    String ittAppsClasspath =
        Paths.get(palHome, "modules", "itt-apps", "target", "classes").toAbsolutePath().toString();
    transformer = new CommandTransformer(getPalDirectoryUrl(), getKafkaServers(), ittAppsClasspath);

    logger.info(
        "Discovered {} total commands ({} pal commands)", allCommands.size(), palCommandCount);
    for (DocCommandType type : DocCommandType.values()) {
      int count = discoveredCountByType.getOrDefault(type, 0);
      if (count > 0) {
        logger.info("  {}: {}", type, count);
      }
    }
  }

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
  public void testHelpCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.HELP);
    List<String> failures = new ArrayList<>();
    for (DocCommand cmd : cmds) {
      TransformedCommand tc = transformer.transform(cmd);
      if (tc.isSkipped()) {
        trackSkip(cmd, tc.getSkipReason());
        continue;
      }
      logger.info("Testing: {}", cmd);
      logSubstitutions(tc);
      CliProcessResult result =
          runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), tc.getArgs());
      if (isCliSyntaxError(result.exitCode(), result.stderr())) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      }
      trackTested(DocCommandType.HELP);
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all listing commands ({@link DocCommandType#PEER_LS}, {@link DocCommandType#LOG_LS},
   * {@link DocCommandType#INTERCEPT_LS}) and peer remove commands execute with valid CLI syntax.
   *
   * <p>Listing commands run against the live directory and accept empty results. Peer remove
   * commands may return exit code 1 (peer not found) but must not return exit code 2 (CLI syntax
   * error).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListingCommands() throws Exception {
    List<DocCommand> cmds =
        filterByType(
            allCommands,
            DocCommandType.PEER_LS,
            DocCommandType.LOG_LS,
            DocCommandType.INTERCEPT_LS,
            DocCommandType.PEER_RM);
    List<String> failures = new ArrayList<>();
    for (DocCommand cmd : cmds) {
      TransformedCommand tc = transformer.transform(cmd);
      if (tc.isSkipped()) {
        trackSkip(cmd, tc.getSkipReason());
        continue;
      }
      logger.info("Testing: {}", cmd);
      logSubstitutions(tc);
      CliProcessResult result =
          runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), tc.getArgs());
      if (isCliSyntaxError(result.exitCode(), result.stderr())) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      }
      trackTested(cmd.getType());
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all {@link DocCommandType#INIT} commands execute with exit code 0.
   *
   * <p>All init commands are run with {@code --dry-run} and {@code -y} appended (by the
   * transformer), using a temporary working directory that is cleaned up after the test.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testInitCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.INIT);
    List<String> failures = new ArrayList<>();
    for (DocCommand cmd : cmds) {
      TransformedCommand tc = transformer.transform(cmd);
      if (tc.isSkipped()) {
        trackSkip(cmd, tc.getSkipReason());
        continue;
      }
      logger.info("Testing: {}", cmd);
      logSubstitutions(tc);
      Path tempDir = Files.createTempDirectory("doc-test-init-");
      tempDirectories.add(tempDir);
      CliProcessResult result =
          runCliSubcommand(
              tc.getSubcommandParts(), tc.getStdinData(), tempDir.toFile(), tc.getArgs());
      if (isCliSyntaxError(result.exitCode(), result.stderr())) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      }
      trackTested(DocCommandType.INIT);
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that short-lived {@link DocCommandType#RUN} commands execute successfully against real
   * infrastructure.
   *
   * <p>Short-lived commands are those where {@link TransformedCommand#isLongRunning()} returns
   * false. They are expected to run and exit within {@value #SHORT_LIVED_TIMEOUT_SECONDS} seconds.
   * Each command is executed with real itt-apps classes and validated for correct CLI syntax.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testShortLivedRunCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.RUN);
    List<String> failures = new ArrayList<>();
    for (DocCommand cmd : cmds) {
      TransformedCommand tc = transformer.transform(cmd);
      if (tc.isSkipped()) {
        trackSkip(cmd, tc.getSkipReason());
        continue;
      }
      if (tc.isLongRunning()) {
        continue; // Handled by testLongRunningRunCommands
      }
      logger.info("Testing short-lived run: {}", cmd);
      logSubstitutions(tc);
      trackRunResources(tc);
      try {
        ProcessResult result = runPeer(SHORT_LIVED_TIMEOUT_SECONDS, tc.getArgs());
        if (isCliSyntaxError(result.exitCode(), result.stderr())) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        }
      } catch (RuntimeException e) {
        if (e.getMessage() != null && e.getMessage().contains("timed out")) {
          logger.warn("Short-lived run command timed out (acceptable): {}", cmd);
        } else {
          failures.add(formatExceptionFailure(cmd, e));
        }
      }
      trackTested(DocCommandType.RUN);
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that long-running {@link DocCommandType#RUN} commands start and stop cleanly.
   *
   * <p>Long-running commands are those that include flags like {@code --json-rpc}, {@code
   * --zmq-rpc}, or {@code --as-service}, indicating they are intended to run as persistent peers.
   * Each command is launched via {@link #launchPeer(UUID, String...)}, verified to start
   * successfully, and then stopped via {@link #stopPeer(PeerProcess)}.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLongRunningRunCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.RUN);
    List<String> failures = new ArrayList<>();
    for (DocCommand cmd : cmds) {
      TransformedCommand tc = transformer.transform(cmd);
      if (tc.isSkipped()) {
        trackSkip(cmd, tc.getSkipReason());
        continue;
      }
      if (!tc.isLongRunning()) {
        continue; // Handled by testShortLivedRunCommands
      }
      logger.info("Testing long-running run: {}", cmd);
      logSubstitutions(tc);
      trackRunResources(tc);
      UUID peerId = UUID.randomUUID();
      try {
        PeerProcess peer = launchPeer(peerId, tc.getArgs());
        launchedPeers.add(peer.getProcess());
        logger.info("Long-running peer started successfully: {}", peer.getPeerName());
        stopPeer(peer);
        launchedPeers.remove(peer.getProcess());
      } catch (Exception e) {
        failures.add(formatExceptionFailure(cmd, e));
      }
      trackTested(DocCommandType.RUN);
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all {@link DocCommandType#PEER_CALL} commands execute against a live peer.
   *
   * <p>Setup launches one peer with ZMQ-RPC + JSON-RPC, {@code --interceptable}, and a known
   * classpath. All PEER_CALL commands are transformed and executed against this shared peer, with
   * peer name references overridden to match the actual running peer. Teardown stops the peer.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerCallCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.PEER_CALL);
    if (cmds.isEmpty()) {
      logger.info("No PEER_CALL commands found in docs");
      return;
    }

    // Launch a shared peer for all call commands
    UUID peerId = UUID.randomUUID();
    String peerName = "doc-test-call-peer-" + generateId();
    String walName = "doc-test-call-wal-" + generateId();
    PeerProcess callPeer =
        launchPeer(
            peerId,
            "-d",
            getPalDirectoryUrl(),
            "-k",
            getKafkaServers(),
            "--wal",
            walName,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "--interceptable",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);
    launchedPeers.add(callPeer.getProcess());
    createdKafkaTopics.add(walName);

    List<String> failures = new ArrayList<>();
    try {
      for (DocCommand cmd : cmds) {
        TransformedCommand tc = transformer.transform(cmd);
        if (tc.isSkipped()) {
          trackSkip(cmd, tc.getSkipReason());
          continue;
        }
        logger.info("Testing: {}", cmd);
        logSubstitutions(tc);
        String[] args = overridePeerRef(tc.getArgs(), peerName);
        CliProcessResult result =
            runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
        if (isCliSyntaxError(result.exitCode(), result.stderr())) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        }
        trackTested(DocCommandType.PEER_CALL);
      }
    } finally {
      stopPeer(callPeer);
      launchedPeers.remove(callPeer.getProcess());
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all {@link DocCommandType#PEER_PRINT} and {@link DocCommandType#PEER_STATS} commands
   * execute against a live peer.
   *
   * <p>Setup launches one peer with RPC endpoints. Print and stats commands are streaming, so they
   * are run for a limited duration ({@value #STREAMING_DURATION_SECONDS} seconds) and then killed.
   * Exit codes of 0 (natural exit) or -1 (killed after duration) are acceptable.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerPrintCommands() throws Exception {
    List<DocCommand> cmds =
        filterByType(allCommands, DocCommandType.PEER_PRINT, DocCommandType.PEER_STATS);
    if (cmds.isEmpty()) {
      logger.info("No PEER_PRINT or PEER_STATS commands found in docs");
      return;
    }

    // Launch a shared peer
    UUID peerId = UUID.randomUUID();
    String peerName = "doc-test-print-peer-" + generateId();
    String walName = "doc-test-print-wal-" + generateId();
    PeerProcess printPeer =
        launchPeer(
            peerId,
            "-d",
            getPalDirectoryUrl(),
            "-k",
            getKafkaServers(),
            "--wal",
            walName,
            "-n",
            peerName,
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);
    launchedPeers.add(printPeer.getProcess());
    createdKafkaTopics.add(walName);

    List<String> failures = new ArrayList<>();
    try {
      for (DocCommand cmd : cmds) {
        TransformedCommand tc = transformer.transform(cmd);
        if (tc.isSkipped()) {
          trackSkip(cmd, tc.getSkipReason());
          continue;
        }
        logger.info("Testing: {}", cmd);
        logSubstitutions(tc);
        String[] args = overridePeerRef(tc.getArgs(), peerName);
        CliProcessResult result =
            runCliSubcommandForDuration(tc.getSubcommandParts(), STREAMING_DURATION_SECONDS, args);
        // Accept 0 (natural exit) or -1 (killed after duration)
        if (result.exitCode() != 0
            && result.exitCode() != -1
            && isCliSyntaxError(result.exitCode(), result.stderr())) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        }
        trackTested(cmd.getType());
      }
    } finally {
      stopPeer(printPeer);
      launchedPeers.remove(printPeer.getProcess());
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that log print, index, and stats commands execute against existing logs.
   *
   * <p>Setup runs a peer briefly to create a Kafka WAL with some messages and also creates a
   * Chronicle WAL under {@code /tmp}. Log references in each command's arguments are overridden to
   * point to the real WAL. Teardown removes the Kafka topic and deletes the Chronicle directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogPrintAndStatsCommands() throws Exception {
    List<DocCommand> cmds =
        filterByType(
            allCommands,
            DocCommandType.LOG_PRINT,
            DocCommandType.LOG_INDEX,
            DocCommandType.LOG_STATS);
    if (cmds.isEmpty()) {
      logger.info("No LOG_PRINT, LOG_INDEX, or LOG_STATS commands found in docs");
      return;
    }

    // Create a Kafka WAL with messages
    String kafkaWalName = "doc-test-wal-shared-" + generateId();
    ProcessResult peerResult =
        runPeer(
            SHORT_LIVED_TIMEOUT_SECONDS,
            "-d",
            getPalDirectoryUrl(),
            "-k",
            getKafkaServers(),
            "--wal",
            kafkaWalName,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);
    createdKafkaTopics.add(kafkaWalName);
    logger.info("Created Kafka WAL {} (peer exit code: {})", kafkaWalName, peerResult.exitCode());

    // Create a Chronicle WAL with messages
    Path chronicleDir = Files.createTempDirectory("pal-doc-test-log-");
    tempDirectories.add(chronicleDir);
    Path chronicleWalPath = chronicleDir.resolve("shared-wal");
    ProcessResult chronicleResult =
        runPeer(
            SHORT_LIVED_TIMEOUT_SECONDS,
            "--wal",
            "file:" + chronicleWalPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);
    logger.info(
        "Created Chronicle WAL at {} (peer exit code: {})",
        chronicleWalPath,
        chronicleResult.exitCode());

    List<String> failures = new ArrayList<>();
    for (DocCommand cmd : cmds) {
      TransformedCommand tc = transformer.transform(cmd);
      if (tc.isSkipped()) {
        trackSkip(cmd, tc.getSkipReason());
        continue;
      }
      logger.info("Testing: {}", cmd);
      logSubstitutions(tc);
      String[] args = overrideLogRefs(tc.getArgs(), kafkaWalName, "file:" + chronicleWalPath);
      CliProcessResult result = runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
      if (isCliSyntaxError(result.exitCode(), result.stderr())) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      }
      trackTested(cmd.getType());
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all {@link DocCommandType#LOG_CALL} commands execute against an existing WAL.
   *
   * <p>Setup creates a Kafka WAL by running a short peer. Each log call command's log references
   * are overridden to use the real WAL name. Teardown removes the Kafka topic.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogCallCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.LOG_CALL);
    if (cmds.isEmpty()) {
      logger.info("No LOG_CALL commands found in docs");
      return;
    }

    // Create a Kafka WAL
    String kafkaWalName = "doc-test-wal-logcall-" + generateId();
    runPeer(
        SHORT_LIVED_TIMEOUT_SECONDS,
        "-d",
        getPalDirectoryUrl(),
        "-k",
        getKafkaServers(),
        "--wal",
        kafkaWalName,
        "-cp",
        getIttAppsClasspath(),
        METHODS_CLASS);
    createdKafkaTopics.add(kafkaWalName);
    logger.info("Created Kafka WAL for log call tests: {}", kafkaWalName);

    List<String> failures = new ArrayList<>();
    for (DocCommand cmd : cmds) {
      TransformedCommand tc = transformer.transform(cmd);
      if (tc.isSkipped()) {
        trackSkip(cmd, tc.getSkipReason());
        continue;
      }
      logger.info("Testing: {}", cmd);
      logSubstitutions(tc);
      String[] args = overrideLogRefs(tc.getArgs(), kafkaWalName, null);
      CliProcessResult result = runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
      if (isCliSyntaxError(result.exitCode(), result.stderr())) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      }
      trackTested(DocCommandType.LOG_CALL);
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all {@link DocCommandType#LOG_RM} commands successfully remove temporary logs.
   *
   * <p>For each remove command, a dedicated temporary Kafka WAL is created first so the remove has
   * something to operate on. Each WAL is created by running a short peer. If the remove command
   * fails, the WAL is tracked for cleanup by {@link #cleanUp()}.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testLogRemoveCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.LOG_RM);
    if (cmds.isEmpty()) {
      logger.info("No LOG_RM commands found in docs");
      return;
    }

    List<String> failures = new ArrayList<>();
    for (DocCommand cmd : cmds) {
      TransformedCommand tc = transformer.transform(cmd);
      if (tc.isSkipped()) {
        trackSkip(cmd, tc.getSkipReason());
        continue;
      }
      logger.info("Testing: {}", cmd);
      logSubstitutions(tc);

      // Create a temporary WAL for this rm command
      String tempWalName = "doc-test-wal-rm-" + generateId();
      runPeer(
          SHORT_LIVED_TIMEOUT_SECONDS,
          "-d",
          getPalDirectoryUrl(),
          "-k",
          getKafkaServers(),
          "--wal",
          tempWalName,
          "-cp",
          getIttAppsClasspath(),
          METHODS_CLASS);

      String[] args = overrideLogRefs(tc.getArgs(), tempWalName, null);
      CliProcessResult result = runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
      if (isCliSyntaxError(result.exitCode(), result.stderr())) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      }
      // Track as backup cleanup in case rm command failed
      if (result.exitCode() != 0) {
        createdKafkaTopics.add(tempWalName);
      }
      trackTested(DocCommandType.LOG_RM);
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all {@link DocCommandType#REPLAY} commands execute against a shared Chronicle WAL.
   *
   * <p>Setup runs a short peer to record a Chronicle WAL under {@code /tmp}. This WAL is shared
   * across all replay tests with references overridden in each command's arguments. Teardown
   * deletes the shared Chronicle WAL directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testReplayCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.REPLAY);
    if (cmds.isEmpty()) {
      logger.info("No REPLAY commands found in docs");
      return;
    }

    // Create a shared Chronicle WAL for replay
    Path chronicleDir = Files.createTempDirectory("pal-doc-test-replay-");
    tempDirectories.add(chronicleDir);
    Path chronicleWalPath = chronicleDir.resolve("shared-replay-wal");
    ProcessResult walResult =
        runPeer(
            SHORT_LIVED_TIMEOUT_SECONDS,
            "--wal",
            "file:" + chronicleWalPath,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);
    logger.info(
        "Created shared Chronicle WAL at {} (exit code: {})",
        chronicleWalPath,
        walResult.exitCode());

    List<String> failures = new ArrayList<>();
    for (DocCommand cmd : cmds) {
      TransformedCommand tc = transformer.transform(cmd);
      if (tc.isSkipped()) {
        trackSkip(cmd, tc.getSkipReason());
        continue;
      }
      logger.info("Testing: {}", cmd);
      logSubstitutions(tc);
      // Override WAL path references to use our shared WAL
      String[] args = overrideChroniclePaths(tc.getArgs(), "file:" + chronicleWalPath);
      args = overrideWalNames(args, "file:" + chronicleWalPath);
      CliProcessResult result = runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
      if (isCliSyntaxError(result.exitCode(), result.stderr())) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      }
      trackTested(DocCommandType.REPLAY);
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that intercept apply and diff commands execute with temporary YAML bundles.
   *
   * <p>Setup creates a temporary YAML intercept bundle file and launches a peer with {@code
   * --interceptable}. File path and peer name references in each command are overridden to match
   * the actual resources. Teardown stops the peer and deletes the YAML file.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testInterceptApplyAndDiffCommands() throws Exception {
    List<DocCommand> cmds =
        filterByType(allCommands, DocCommandType.INTERCEPT_APPLY, DocCommandType.INTERCEPT_DIFF);
    if (cmds.isEmpty()) {
      logger.info("No INTERCEPT_APPLY or INTERCEPT_DIFF commands found in docs");
      return;
    }

    // Launch a peer with --interceptable
    UUID peerId = UUID.randomUUID();
    String peerName = "doc-test-intercept-peer-" + generateId();
    String bundleName = "doc-test-bundle-" + generateId();
    PeerProcess interceptPeer =
        launchPeer(
            peerId, "-d", getPalDirectoryUrl(), "-n", peerName, "--interceptable", "--as-service");
    launchedPeers.add(interceptPeer.getProcess());

    // Create temp YAML bundle
    File yamlFile = createTempInterceptYaml(bundleName, peerName);
    tempFiles.add(yamlFile.toPath());

    List<String> failures = new ArrayList<>();
    try {
      for (DocCommand cmd : cmds) {
        TransformedCommand tc = transformer.transform(cmd);
        if (tc.isSkipped()) {
          trackSkip(cmd, tc.getSkipReason());
          continue;
        }
        logger.info("Testing: {}", cmd);
        logSubstitutions(tc);
        String[] args = overrideInterceptFileRef(tc.getArgs(), yamlFile.getAbsolutePath());
        args = overridePeerRef(args, peerName);
        CliProcessResult result =
            runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
        if (isCliSyntaxError(result.exitCode(), result.stderr())) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        }
        trackTested(cmd.getType());
      }
    } finally {
      stopPeer(interceptPeer);
      launchedPeers.remove(interceptPeer.getProcess());
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that intercept remove and status commands execute after applying an intercept bundle.
   *
   * <p>Setup launches a peer with {@code --interceptable} and applies an intercept bundle first.
   * Status commands (read-only) are run before remove commands. Between remove commands, the bundle
   * is re-applied so each remove has intercepts to operate on. Teardown stops the peer.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testInterceptRemoveAndStatusCommands() throws Exception {
    List<DocCommand> cmds =
        filterByType(allCommands, DocCommandType.INTERCEPT_RM, DocCommandType.INTERCEPT_STATUS);
    if (cmds.isEmpty()) {
      logger.info("No INTERCEPT_RM or INTERCEPT_STATUS commands found in docs");
      return;
    }

    // Launch a peer with --interceptable
    UUID peerId = UUID.randomUUID();
    String peerName = "doc-test-irm-peer-" + generateId();
    String bundleName = "doc-test-irm-bundle-" + generateId();
    PeerProcess interceptPeer =
        launchPeer(
            peerId, "-d", getPalDirectoryUrl(), "-n", peerName, "--interceptable", "--as-service");
    launchedPeers.add(interceptPeer.getProcess());

    // Create and apply temp YAML bundle
    File yamlFile = createTempInterceptYaml(bundleName, peerName);
    tempFiles.add(yamlFile.toPath());
    CliProcessResult applyResult =
        runInterceptApply("-d", getPalDirectoryUrl(), yamlFile.getAbsolutePath());
    logger.info("Applied intercept bundle (exit code: {})", applyResult.exitCode());

    // Run status commands first (read-only), then rm commands
    List<DocCommand> statusCmds =
        cmds.stream()
            .filter(c -> c.getType() == DocCommandType.INTERCEPT_STATUS)
            .collect(Collectors.toList());
    List<DocCommand> rmCmds =
        cmds.stream()
            .filter(c -> c.getType() == DocCommandType.INTERCEPT_RM)
            .collect(Collectors.toList());

    List<String> failures = new ArrayList<>();
    try {
      for (DocCommand cmd : statusCmds) {
        TransformedCommand tc = transformer.transform(cmd);
        if (tc.isSkipped()) {
          trackSkip(cmd, tc.getSkipReason());
          continue;
        }
        logger.info("Testing: {}", cmd);
        logSubstitutions(tc);
        String[] args = overrideInterceptFileRef(tc.getArgs(), yamlFile.getAbsolutePath());
        args = overridePeerRef(args, peerName);
        args = overrideBundleRef(args, bundleName);
        CliProcessResult result =
            runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
        if (isCliSyntaxError(result.exitCode(), result.stderr())) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        }
        trackTested(DocCommandType.INTERCEPT_STATUS);
      }

      for (int i = 0; i < rmCmds.size(); i++) {
        DocCommand cmd = rmCmds.get(i);
        TransformedCommand tc = transformer.transform(cmd);
        if (tc.isSkipped()) {
          trackSkip(cmd, tc.getSkipReason());
          continue;
        }
        logger.info("Testing: {}", cmd);
        logSubstitutions(tc);
        String[] args = overrideInterceptFileRef(tc.getArgs(), yamlFile.getAbsolutePath());
        args = overridePeerRef(args, peerName);
        args = overrideBundleRef(args, bundleName);
        CliProcessResult result =
            runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
        if (isCliSyntaxError(result.exitCode(), result.stderr())) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        }
        trackTested(DocCommandType.INTERCEPT_RM);

        // Re-apply bundle for subsequent rm commands
        if (i < rmCmds.size() - 1) {
          runInterceptApply("-d", getPalDirectoryUrl(), yamlFile.getAbsolutePath());
        }
      }
    } finally {
      stopPeer(interceptPeer);
      launchedPeers.remove(interceptPeer.getProcess());
    }
    assertNoFailures(failures);
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
  public void testAllDocCommandsAreCovered() throws Exception {
    Set<DocCommandType> uncoveredTypes = EnumSet.noneOf(DocCommandType.class);
    for (DocCommandType type : DocCommandType.values()) {
      if (type == DocCommandType.SKIPPED || type == DocCommandType.NON_PAL) {
        continue;
      }
      int discovered = discoveredCountByType.getOrDefault(type, 0);
      int tested = testedCountByType.getOrDefault(type, 0);
      if (discovered > 0 && tested == 0) {
        uncoveredTypes.add(type);
      }
    }

    if (!uncoveredTypes.isEmpty()) {
      StringBuilder msg = new StringBuilder("Uncovered command types:\n");
      for (DocCommandType type : uncoveredTypes) {
        msg.append("  ")
            .append(type)
            .append(": ")
            .append(discoveredCountByType.getOrDefault(type, 0))
            .append(" commands discovered\n");
        allCommands.stream()
            .filter(c -> c.getType() == type)
            .forEach(c -> msg.append("    ").append(c).append("\n"));
      }
      assertTrue(msg.toString(), false);
    }
  }

  // ---------------------------------------------------------------------------
  // Helper methods
  // ---------------------------------------------------------------------------

  /**
   * Filters commands by one or more types.
   *
   * @param commands the commands to filter
   * @param types the types to include
   * @return a new list containing only commands with matching types
   */
  private static List<DocCommand> filterByType(List<DocCommand> commands, DocCommandType... types) {
    Set<DocCommandType> typeSet = EnumSet.noneOf(DocCommandType.class);
    for (DocCommandType t : types) {
      typeSet.add(t);
    }
    return commands.stream()
        .filter(c -> typeSet.contains(c.getType()))
        .collect(Collectors.toList());
  }

  /**
   * Checks whether a CLI result indicates a PicoCLI syntax error.
   *
   * @param exitCode the process exit code
   * @param stderr the stderr output
   * @return true if the result indicates a CLI syntax error
   */
  private static boolean isCliSyntaxError(int exitCode, String stderr) {
    if (exitCode == 2) {
      return true;
    }
    if (stderr == null) {
      return false;
    }
    return stderr.contains("Unknown option")
        || stderr.contains("Unmatched argument")
        || stderr.contains("Missing required");
  }

  /**
   * Formats a CLI failure as a human-readable string.
   *
   * @param cmd the source command
   * @param exitCode the exit code
   * @param stderr the stderr output
   * @return formatted failure description
   */
  private static String formatFailure(DocCommand cmd, int exitCode, String stderr) {
    String cmdText = cmd.getNormalizedText() != null ? cmd.getNormalizedText() : cmd.getRawText();
    return String.format(
        "%s:%d — exit=%d\n  cmd: %s\n  stderr: %s",
        cmd.getSourceFile(),
        cmd.getLineNumber(),
        exitCode,
        cmdText,
        stderr != null ? stderr.trim() : "");
  }

  /**
   * Formats an exception-based failure as a human-readable string.
   *
   * @param cmd the source command
   * @param e the exception
   * @return formatted failure description
   */
  private static String formatExceptionFailure(DocCommand cmd, Exception e) {
    String cmdText = cmd.getNormalizedText() != null ? cmd.getNormalizedText() : cmd.getRawText();
    return String.format(
        "%s:%d — exception: %s\n  cmd: %s",
        cmd.getSourceFile(), cmd.getLineNumber(), e.getMessage(), cmdText);
  }

  /**
   * Asserts that no CLI syntax failures were collected.
   *
   * @param failures the list of failure descriptions
   */
  private static void assertNoFailures(List<String> failures) {
    assertTrue(
        "Documentation snippet failures:\n" + String.join("\n\n", failures), failures.isEmpty());
  }

  /**
   * Records a skipped command for the coverage report.
   *
   * @param cmd the skipped command
   * @param reason the reason for skipping
   */
  private static void trackSkip(DocCommand cmd, String reason) {
    totalSkipped++;
    skipReasons.add(String.format("%s:%d — %s", cmd.getSourceFile(), cmd.getLineNumber(), reason));
  }

  /**
   * Records a tested command type for the coverage report.
   *
   * @param type the command type that was tested
   */
  private static void trackTested(DocCommandType type) {
    totalTested++;
    testedCountByType.merge(type, 1, Integer::sum);
  }

  /**
   * Logs all substitutions applied by the transformer for debugging.
   *
   * @param tc the transformed command
   */
  private static void logSubstitutions(TransformedCommand tc) {
    for (String sub : tc.getSubstitutions()) {
      logger.info("  substitution: {}", sub);
    }
  }

  /**
   * Tracks Kafka WAL names and Chronicle paths from a run command for cleanup.
   *
   * @param tc the transformed run command
   */
  private void trackRunResources(TransformedCommand tc) {
    if (tc.getUniqueWalName() != null) {
      createdKafkaTopics.add(tc.getUniqueWalName());
    }
    if (tc.getChroniclePath() != null) {
      Path parent = tc.getChroniclePath().getParent();
      if (parent != null) {
        tempDirectories.add(parent);
      }
    }
  }

  /**
   * Overrides {@code -p} or {@code --peer} flag values in args with the given peer name.
   *
   * @param args the original args array
   * @param peerName the actual peer name to substitute
   * @return a new args array with peer references overridden
   */
  private static String[] overridePeerRef(String[] args, String peerName) {
    String[] result = args.clone();
    for (int i = 0; i < result.length - 1; i++) {
      if ("-p".equals(result[i]) || "--peer".equals(result[i])) {
        result[i + 1] = peerName;
      }
    }
    return result;
  }

  /**
   * Overrides any arg starting with {@code doc-test-wal-} with the given WAL name.
   *
   * @param args the original args array
   * @param walName the actual WAL name to substitute
   * @return a new args array with WAL names overridden
   */
  private static String[] overrideWalNames(String[] args, String walName) {
    String[] result = args.clone();
    for (int i = 0; i < result.length; i++) {
      if (result[i].startsWith("doc-test-wal-")) {
        result[i] = walName;
      }
    }
    return result;
  }

  /**
   * Overrides any arg containing {@code pal-doc-test-} with the given Chronicle path.
   *
   * @param args the original args array
   * @param chroniclePath the actual Chronicle path (e.g., {@code file:/tmp/...})
   * @return a new args array with Chronicle paths overridden
   */
  private static String[] overrideChroniclePaths(String[] args, String chroniclePath) {
    String[] result = args.clone();
    for (int i = 0; i < result.length; i++) {
      if (result[i].contains("pal-doc-test-")) {
        result[i] = chroniclePath;
      }
    }
    return result;
  }

  /**
   * Overrides both Kafka WAL names and Chronicle paths in args.
   *
   * @param args the original args array
   * @param kafkaWalName the actual Kafka WAL name
   * @param chroniclePath the actual Chronicle path, or null to skip Chronicle overrides
   * @return a new args array with log references overridden
   */
  private static String[] overrideLogRefs(
      String[] args, String kafkaWalName, String chroniclePath) {
    String[] result = overrideWalNames(args, kafkaWalName);
    if (chroniclePath != null) {
      result = overrideChroniclePaths(result, chroniclePath);
    }
    return result;
  }

  /**
   * Overrides YAML file path references in args (both positional and {@code -f} flag).
   *
   * @param args the original args array
   * @param yamlPath the actual YAML file path
   * @return a new args array with file references overridden
   */
  private static String[] overrideInterceptFileRef(String[] args, String yamlPath) {
    String[] result = args.clone();
    for (int i = 0; i < result.length; i++) {
      if (result[i].endsWith(".yaml") || result[i].endsWith(".yml")) {
        result[i] = yamlPath;
      }
      if (("-f".equals(result[i]) || "--file".equals(result[i])) && i + 1 < result.length) {
        result[i + 1] = yamlPath;
      }
    }
    return result;
  }

  /**
   * Overrides {@code --bundle} flag values in args with the given bundle name.
   *
   * @param args the original args array
   * @param bundleName the actual bundle name
   * @return a new args array with bundle references overridden
   */
  private static String[] overrideBundleRef(String[] args, String bundleName) {
    String[] result = args.clone();
    for (int i = 0; i < result.length - 1; i++) {
      if ("--bundle".equals(result[i])) {
        result[i + 1] = bundleName;
      }
    }
    return result;
  }

  /**
   * Creates a temporary YAML intercept bundle file for testing.
   *
   * @param bundleName the bundle name
   * @param peerName the peer name for the defaults section
   * @return the temp YAML file
   * @throws IOException if file creation fails
   */
  private File createTempInterceptYaml(String bundleName, String peerName) throws IOException {
    String yaml =
        "bundle: \""
            + bundleName
            + "\"\n"
            + "defaults:\n"
            + "  peer: \""
            + peerName
            + "\"\n"
            + "intercepts:\n"
            + "  - target: com.acme.OrderService.placeOrder\n"
            + "    type: BEFORE\n"
            + "    callback:\n"
            + "      class: com.acme.FraudChecker\n"
            + "      method: verify\n"
            + "  - target: com.acme.OrderService.refund\n"
            + "    type: AROUND\n"
            + "    callback:\n"
            + "      class: com.acme.FraudChecker\n"
            + "      method: wrapRefund\n";
    File tempFile = File.createTempFile("doc-test-intercept-", ".yaml");
    Files.writeString(tempFile.toPath(), yaml, StandardCharsets.UTF_8);
    return tempFile;
  }
}
