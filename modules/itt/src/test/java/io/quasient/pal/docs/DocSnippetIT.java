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
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.core.service.Main;
import io.quasient.pal.core.service.PeerException;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.docs.CommandTransformer.TransformedCommand;
import io.quasient.pal.tools.cli.AbstractPalSubcommand;
import io.quasient.pal.tools.cli.Replay;
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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
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
 * <p><b>NOTE:</b> The expected number of non-success exit codes is hard-coded in {@link
 * #EXPECTED_NON_SUCCESS_COUNT}. When user documentation is modified in a way that adds or removes
 * CLI snippets producing non-success exit codes (e.g., new streaming or replay commands), the
 * meta-test {@link #zz_testNonSuccessExitCodeCount()} will fail. Review the test log for the
 * breakdown and update the constant accordingly.
 *
 * @see DocSnippetScanner
 * @see CommandTransformer
 * @see DocCommand
 * @see DocCommandType
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class DocSnippetIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(DocSnippetIT.class);

  /** Main class for generic doc examples (short-lived, exits after running). */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Duration in seconds to let streaming commands run before killing. */
  private static final int STREAMING_DURATION_SECONDS = 3;

  /** Timeout in seconds for short-lived peer run commands. */
  private static final int SHORT_LIVED_TIMEOUT_SECONDS = 30;

  /**
   * Expected number of documentation snippets that complete with a non-success exit code but are
   * still accepted by the test (e.g., interrupted streaming commands exiting 130, replay commands
   * exiting 2 for expected divergences). Verified by {@link #zz_testNonSuccessExitCodeCount()}.
   *
   * <p><b>Update this constant</b> when user documentation changes add or remove CLI snippets that
   * produce non-success exit codes. See the test log's "Non-success accepted exit codes" report for
   * the breakdown.
   */
  private static final int EXPECTED_NON_SUCCESS_COUNT = 23;

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

  /** Per-type count of commands skipped by the transformer. */
  private static final Map<DocCommandType, Integer> skippedCountByType =
      new EnumMap<>(DocCommandType.class);

  /**
   * Per-type count of commands handled (tested or skipped). Used by the coverage check to
   * distinguish "no test method exists for this type" from "all commands of this type were skipped
   * by the transformer."
   */
  private static final Map<DocCommandType, Integer> handledCountByType =
      new EnumMap<>(DocCommandType.class);

  /** Per-type count of commands discovered. */
  private static final Map<DocCommandType, Integer> discoveredCountByType =
      new EnumMap<>(DocCommandType.class);

  /** Accumulated skip descriptions for the coverage report. */
  private static final List<String> skipReasons = new ArrayList<>();

  /**
   * Snippets that completed with a non-zero exit code that was still accepted by the test. Grouped
   * by command type for the end-of-suite report.
   */
  private static final Map<DocCommandType, List<String>> nonSuccessAccepted =
      new EnumMap<>(DocCommandType.class);

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
    int palDiscovered =
        totalDiscovered - discoveredCountByType.getOrDefault(DocCommandType.NON_PAL, 0);
    logger.info(
        "Doc snippet coverage: {} of {} pal commands tested, {} skipped",
        totalTested,
        palDiscovered,
        totalSkipped);
    for (String reason : skipReasons) {
      logger.info("SKIPPED: {}", reason);
    }
    for (DocCommandType type : DocCommandType.values()) {
      if (type == DocCommandType.NON_PAL || type == DocCommandType.SKIPPED) {
        continue;
      }
      int tested = testedCountByType.getOrDefault(type, 0);
      int skipped = skippedCountByType.getOrDefault(type, 0);
      int discovered = discoveredCountByType.getOrDefault(type, 0);
      if (discovered > 0) {
        logger.info(
            "  {}: {} tested / {} skipped / {} discovered", type, tested, skipped, discovered);
      }
    }
    int nonPal = discoveredCountByType.getOrDefault(DocCommandType.NON_PAL, 0);
    if (nonPal > 0) {
      logger.info("  NON_PAL (not tested): {}", nonPal);
    }

    // Report all snippets accepted with non-success exit codes
    int totalNonSuccess = nonSuccessAccepted.values().stream().mapToInt(List::size).sum();
    if (totalNonSuccess > 0) {
      logger.info("Non-success accepted exit codes ({} total):", totalNonSuccess);
      for (DocCommandType type : DocCommandType.values()) {
        List<String> entries = nonSuccessAccepted.get(type);
        if (entries != null && !entries.isEmpty()) {
          logger.info("  {} ({}):", type, entries.size());
          for (String entry : entries) {
            logger.info("    {}", entry);
          }
        }
      }
    } else {
      logger.info("All tested snippets exited with success (exit code 0)");
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
      if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      }
      trackTested(DocCommandType.HELP);
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all listing commands ({@link DocCommandType#PEER_LS}, {@link DocCommandType#LOG_LS},
   * {@link DocCommandType#INTERCEPT_LS}) and peer remove commands execute successfully.
   *
   * <p>Listing commands run against the live directory and accept empty results (exit 0).
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
            DocCommandType.INTERCEPT_LS);
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
      if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      } else if (result.exitCode() != 0) {
        trackNonSuccessExit(cmd, result.exitCode());
      }
      trackTested(cmd.getType());
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all {@link DocCommandType#PEER_RM} commands successfully remove peers.
   *
   * <p>For each remove command, the peer identifiers (names or UUIDs) referenced in the command are
   * pre-registered in the PAL directory so that the remove operation can find and delete them. For
   * commands using {@code -s} (starting-with), a peer with a matching name prefix is registered.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPeerRemoveCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.PEER_RM);
    if (cmds.isEmpty()) {
      logger.info("No PEER_RM commands found in docs");
      return;
    }

    PalDirectory dir = new PalDirectory(getPalDirectoryUrl(), true);
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

        // Register peers that this command will try to remove
        registerPeersForRmCommand(dir, tc.getArgs());

        CliProcessResult result =
            runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), tc.getArgs());
        if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        } else if (result.exitCode() != 0) {
          trackNonSuccessExit(cmd, result.exitCode());
        }
        trackTested(cmd.getType());
      }
    } finally {
      dir.close();
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
      if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
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
   * Each command is executed with real itt-apps classes and must exit with code 0.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testShortLivedRunCommands() throws Exception {
    List<DocCommand> cmds = filterByType(allCommands, DocCommandType.RUN);

    // Pre-create a Chronicle WAL so --source-log file: paths have something to read
    Path chronicleDir = Files.createTempDirectory("pal-doc-test-run-");
    tempDirectories.add(chronicleDir);
    Path chronicleWalPath = chronicleDir.resolve("shared-run-wal");
    runPeer(
        SHORT_LIVED_TIMEOUT_SECONDS,
        "--wal",
        "file:" + chronicleWalPath,
        "-cp",
        getIttAppsClasspath(),
        METHODS_CLASS);
    logger.info("Created Chronicle WAL for short-lived run tests: {}", chronicleWalPath);

    // Pre-create a Kafka WAL so --source-log <name> has something to read
    String kafkaWalName = "doc-test-wal-run-" + generateId();
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
    logger.info("Created Kafka WAL for short-lived run tests: {}", kafkaWalName);

    // Create a temp properties file so --properties paths resolve
    Path tempPropertiesFile = chronicleDir.resolve("tuning.properties");
    Files.writeString(tempPropertiesFile, "# doc-test placeholder properties\n");
    logger.info("Created temp properties file: {}", tempPropertiesFile);

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
        String[] args = overrideLogRefs(tc.getArgs(), kafkaWalName, "file:" + chronicleWalPath);
        args = overridePropertiesPath(args, tempPropertiesFile.toString());
        ProcessResult result = runPeer(SHORT_LIVED_TIMEOUT_SECONDS, args);
        if (isCommandFailure(result.exitCode(), Main.EXIT_SUCCESS)) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        } else if (result.exitCode() != Main.EXIT_SUCCESS) {
          trackNonSuccessExit(cmd, result.exitCode());
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
   * peer name references overridden to match the actual running peer. Each call must complete and
   * exit with code 0. Teardown stops the peer.
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
            "--as-service",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);
    launchedPeers.add(callPeer.getProcess());
    createdKafkaTopics.add(walName);

    // Look up the peer's RPC addresses to replace ws:// and tcp:// URLs in doc commands
    String jsonRpcAddr = getPeerJsonRpcAddress(peerId);
    String zmqRpcAddr = getPeerZmqRpcAddress(peerId);
    logger.info("Peer JSON-RPC address for call tests: {}", jsonRpcAddr);
    logger.info("Peer ZMQ-RPC address for call tests: {}", zmqRpcAddr);

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
        String[] args = overridePeerCallTarget(tc.getArgs(), peerName);
        if (jsonRpcAddr != null) {
          args = overrideWsUrls(args, jsonRpcAddr);
        }
        if (zmqRpcAddr != null) {
          args = overrideTcpUrls(args, zmqRpcAddr);
        }
        // Inject -r ZMQ_RPC for directory-based calls that don't already specify an RPC type,
        // are not ws:// direct calls, and have no stdin JSON-RPC data (which implies JSON-RPC)
        if (tc.getStdinData() == null) {
          args = injectZmqRpcTypeIfNeeded(args);
        } else {
          // Stdin commands imply JSON-RPC; inject -r JSON_RPC for directory-based calls
          args = injectJsonRpcTypeIfNeeded(args);
        }
        // Use stdin-aware execution for JSON-RPC commands, duration-based for the rest
        CliProcessResult result;
        if (tc.getStdinData() != null) {
          result = runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
        } else {
          result =
              runCliSubcommandForDuration(
                  tc.getSubcommandParts(), SHORT_LIVED_TIMEOUT_SECONDS, args);
        }
        if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        } else if (result.exitCode() != 0) {
          trackNonSuccessExit(cmd, result.exitCode());
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
            "--tcp-pub",
            "auto",
            "--zmq-rpc",
            "auto",
            "--json-rpc",
            "auto",
            "--as-service",
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
        String[] args = overridePeerRef(tc.getArgs(), peerId.toString());
        CliProcessResult result =
            runCliSubcommandForDuration(tc.getSubcommandParts(), STREAMING_DURATION_SECONDS, args);
        // Accept 0 (natural exit) or 130 (interrupted after streaming duration)
        if (isCommandFailure(
            result.exitCode(),
            AbstractPalSubcommand.EXIT_SUCCESS,
            AbstractPalSubcommand.EXIT_INTERRUPTED)) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        } else if (result.exitCode() != 0) {
          trackNonSuccessExit(cmd, result.exitCode());
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
   * point to the real WAL. {@code log print} commands with {@code -f/--follow} are streaming and
   * run for a limited duration; all other commands must run and exit with code 0. Teardown removes
   * the Kafka topic and deletes the Chronicle directory.
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
      args = ensureDirectoryAndKafkaFlags(args);
      // Only "log print -f/--follow" is streaming; log index, log stats, and
      // log print without --follow all run and exit.
      boolean streaming = cmd.getType() == DocCommandType.LOG_PRINT && isFollowMode(tc.getArgs());
      if (streaming) {
        CliProcessResult result =
            runCliSubcommandForDuration(tc.getSubcommandParts(), STREAMING_DURATION_SECONDS, args);
        // Accept 0 (completed) or 130 (interrupted after streaming duration)
        if (isCommandFailure(
            result.exitCode(),
            AbstractPalSubcommand.EXIT_SUCCESS,
            AbstractPalSubcommand.EXIT_INTERRUPTED)) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        } else if (result.exitCode() != 0) {
          trackNonSuccessExit(cmd, result.exitCode());
        }
      } else {
        CliProcessResult result =
            runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
        if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
          failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
        }
      }
      trackTested(cmd.getType());
    }
    assertNoFailures(failures);
  }

  /**
   * Tests that all {@link DocCommandType#LOG_CALL} commands execute against existing WALs.
   *
   * <p>Setup creates both a Kafka WAL and a Chronicle WAL by running short peers. Each log call
   * command's log references are overridden to use the real WAL names. Commands that lack {@code
   * --forget-response} are augmented with it, because the single-log doc snippet syntax does not
   * support the separate source-log/WAL topology required for the response path (a circularity
   * guard prevents a peer from writing responses to the same log it reads from). The full
   * request/response flow through separate logs is covered by {@code CallerIT}. Teardown removes
   * the Kafka topic.
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

    // Create a Chronicle WAL for commands that use file: paths
    Path chronicleDir = Files.createTempDirectory("pal-doc-test-logcall-");
    tempDirectories.add(chronicleDir);
    Path chronicleWalPath = chronicleDir.resolve("shared-logcall-wal");
    runPeer(
        SHORT_LIVED_TIMEOUT_SECONDS,
        "--wal",
        "file:" + chronicleWalPath,
        "-cp",
        getIttAppsClasspath(),
        METHODS_CLASS);
    logger.info("Created Chronicle WAL for log call tests: {}", chronicleWalPath);

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
      args = ensureDirectoryAndKafkaFlags(args);
      args = ensureForgetResponse(args);
      CliProcessResult result =
          runCliSubcommandForDuration(tc.getSubcommandParts(), SHORT_LIVED_TIMEOUT_SECONDS, args);
      if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      } else if (result.exitCode() != 0) {
        trackNonSuccessExit(cmd, result.exitCode());
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
      args = ensureDirectoryAndKafkaFlags(args);
      CliProcessResult result = runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
      // LogRemove.runCommand() returns an error count: each log that cannot be resolved or
      // whose backend deletion fails increments the counter (see LogRemove.java:363). When
      // multiple placeholder log names map to the same test WAL, the first removal succeeds
      // but subsequent names fail resolution — this is expected. Any non-negative exit code
      // is acceptable.
      if (result.exitCode() < 0) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      } else if (result.exitCode() != 0) {
        trackNonSuccessExit(cmd, result.exitCode());
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
      args = ensureJdkTimeReExecuteForStubAllElse(args);
      CliProcessResult result = runCliSubcommand(tc.getSubcommandParts(), tc.getStdinData(), args);
      // Accept 0 (clean replay) or 2 (divergence detected). Doc snippets with --scope flags
      // reference placeholder classes that differ from the recorded WAL data, so divergence
      // is expected for those commands.
      if (isCommandFailure(result.exitCode(), Main.EXIT_SUCCESS, Replay.EXIT_CODE_DIVERGENCES)) {
        failures.add(formatFailure(cmd, result.exitCode(), result.stderr()));
      } else if (result.exitCode() != Main.EXIT_SUCCESS) {
        trackNonSuccessExit(cmd, result.exitCode());
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
        if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
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
        if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
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
        if (isCommandFailure(result.exitCode(), AbstractPalSubcommand.EXIT_SUCCESS)) {
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
  public void zz_testAllDocCommandsAreCovered() throws Exception {
    Set<DocCommandType> uncoveredTypes = EnumSet.noneOf(DocCommandType.class);
    for (DocCommandType type : DocCommandType.values()) {
      if (type == DocCommandType.SKIPPED || type == DocCommandType.NON_PAL) {
        continue;
      }
      int discovered = discoveredCountByType.getOrDefault(type, 0);
      int handled = handledCountByType.getOrDefault(type, 0);
      if (discovered > 0 && handled == 0) {
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

  /**
   * Meta-test that verifies the number of accepted non-success exit codes matches {@link
   * #EXPECTED_NON_SUCCESS_COUNT}.
   *
   * <p>This acts as a tripwire: if user documentation changes add or remove CLI snippets that
   * produce non-success exit codes (e.g., streaming commands that exit 130 when interrupted, or
   * replay commands that exit 2 for expected divergences), this test fails with a descriptive
   * message showing the actual vs. expected count and the full breakdown by category.
   *
   * <p>When this test fails, review the breakdown in the failure message, verify the new count is
   * correct, and update {@link #EXPECTED_NON_SUCCESS_COUNT}.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void zz_testNonSuccessExitCodeCount() throws Exception {
    int actual = nonSuccessAccepted.values().stream().mapToInt(List::size).sum();
    if (actual != EXPECTED_NON_SUCCESS_COUNT) {
      StringBuilder msg = new StringBuilder();
      msg.append("Expected ")
          .append(EXPECTED_NON_SUCCESS_COUNT)
          .append(" non-success accepted exit codes, but found ")
          .append(actual)
          .append(".\n");
      msg.append("If user documentation was modified, review the breakdown below ")
          .append("and update EXPECTED_NON_SUCCESS_COUNT in DocSnippetIT.\n\n");
      for (DocCommandType type : DocCommandType.values()) {
        List<String> entries = nonSuccessAccepted.get(type);
        if (entries != null && !entries.isEmpty()) {
          msg.append(type).append(" (").append(entries.size()).append("):\n");
          for (String entry : entries) {
            msg.append("  ").append(entry).append("\n");
          }
        }
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
   * Checks whether a CLI result indicates a command failure, given a set of acceptable exit codes.
   *
   * @param exitCode the process exit code
   * @param acceptableExitCodes the set of exit codes that are considered non-failures
   * @return true if the exit code is not in the acceptable set
   */
  private static boolean isCommandFailure(int exitCode, int... acceptableExitCodes) {
    for (int acceptable : acceptableExitCodes) {
      if (exitCode == acceptable) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks whether a command's arguments include {@code -f} or {@code --follow}, indicating a
   * streaming command that runs indefinitely until killed.
   *
   * @param args the command arguments
   * @return true if follow mode is present
   */
  private static boolean isFollowMode(String[] args) {
    for (String arg : args) {
      if ("-f".equals(arg) || "--follow".equals(arg)) {
        return true;
      }
    }
    return false;
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
    skipReasons.add(
        String.format(
            "%s:%d — %s\n    snippet: %s",
            cmd.getSourceFile(), cmd.getLineNumber(), reason, cmd.getNormalizedText()));
    skippedCountByType.merge(cmd.getType(), 1, Integer::sum);
    handledCountByType.merge(cmd.getType(), 1, Integer::sum);
  }

  /**
   * Records a tested command type for the coverage report.
   *
   * @param type the command type that was tested
   */
  private static void trackTested(DocCommandType type) {
    totalTested++;
    testedCountByType.merge(type, 1, Integer::sum);
    handledCountByType.merge(type, 1, Integer::sum);
  }

  /**
   * Records a command that was accepted with a non-zero exit code.
   *
   * @param cmd the command
   * @param exitCode the non-zero exit code
   */
  private static void trackNonSuccessExit(DocCommand cmd, int exitCode) {
    String cmdText = cmd.getNormalizedText() != null ? cmd.getNormalizedText() : cmd.getRawText();
    String label = describeExitCode(exitCode, cmd.getType());
    String entry =
        String.format(
            "%s:%d — exit=%d (%s)  cmd: %s",
            cmd.getSourceFile(), cmd.getLineNumber(), exitCode, label, cmdText);
    nonSuccessAccepted.computeIfAbsent(cmd.getType(), k -> new ArrayList<>()).add(entry);
  }

  /**
   * Returns a human-readable constant name for an exit code, using the command type to determine
   * which exit-code domain applies.
   *
   * @param exitCode the exit code
   * @param type the command type (determines which constants are relevant)
   * @return the constant name or a plain integer string if unknown
   */
  @SuppressWarnings("EnumOrdinal") // FatalCode.getCode() is the semantic value, not ordinal
  private static String describeExitCode(int exitCode, DocCommandType type) {
    if (exitCode == EXIT_CODE_KILLED) {
      return "EXIT_CODE_KILLED";
    }
    if (exitCode == EXIT_CODE_SIGTERM) {
      return "EXIT_CODE_SIGTERM";
    }
    if (type == DocCommandType.RUN) {
      if (exitCode == Main.EXIT_CLASS_NOT_FOUND) {
        return "Main.EXIT_CLASS_NOT_FOUND";
      }
      if (exitCode == Main.EXIT_REPLAY_DIVERGENCES) {
        return "Main.EXIT_REPLAY_DIVERGENCES";
      }
      for (PeerException.FatalCode fc : PeerException.FatalCode.values()) {
        if (fc.getCode() == exitCode) {
          return "PeerException.FatalCode." + fc.name();
        }
      }
      return String.valueOf(exitCode);
    }
    if (type == DocCommandType.REPLAY) {
      if (exitCode == Main.EXIT_CLASS_NOT_FOUND) {
        return "Main.EXIT_CLASS_NOT_FOUND";
      }
      if (exitCode == Replay.EXIT_CODE_DIVERGENCES) {
        return "Replay.EXIT_CODE_DIVERGENCES";
      }
      return String.valueOf(exitCode);
    }
    if (type == DocCommandType.PEER_RM) {
      return exitCode + " not-found peer(s)";
    }
    if (type == DocCommandType.LOG_RM) {
      return exitCode + " removal error(s)";
    }
    // CLI subcommands (peer call, peer print, log call, etc.)
    if (exitCode == AbstractPalSubcommand.EXIT_ERROR) {
      return "AbstractPalSubcommand.EXIT_ERROR";
    }
    if (exitCode == AbstractPalSubcommand.EXIT_INTERRUPTED) {
      return "AbstractPalSubcommand.EXIT_INTERRUPTED";
    }
    return String.valueOf(exitCode);
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
    for (int i = 0; i < result.length; i++) {
      // Flag-based peer references: -p <peer> or --peer <peer>
      if (("-p".equals(result[i]) || "--peer".equals(result[i])) && i + 1 < result.length) {
        result[i + 1] = peerName;
        i++; // skip value
        continue;
      }
      // Positional UUID args (e.g., 550e8400-e29b-41d4-a716-446655440000)
      if (UUID_PATTERN.matcher(result[i]).matches()) {
        result[i] = peerName;
        continue;
      }
      // Positional peer name placeholders: bare names like "my-peer", "peer-uuid"
      // that look like peer identifiers (not flags, not FQNs, not file: paths)
      if (isPeerNamePlaceholder(result[i])) {
        result[i] = peerName;
      }
    }
    return result;
  }

  /** Pattern matching a full UUID. */
  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  /**
   * Checks whether an arg looks like a placeholder peer name (not a flag, FQN, file: path, or
   * numeric value). Peer name placeholders are bare alphanumeric-with-hyphens names like "my-peer"
   * or "peer-uuid" that appear as positional args in peer print/stats commands.
   */
  private static boolean isPeerNamePlaceholder(String arg) {
    if (arg.startsWith("-") || arg.startsWith("file:") || arg.contains(".")) {
      return false;
    }
    // Must contain "peer" to avoid false positives on other positional args
    return arg.toLowerCase(Locale.ROOT).contains("peer");
  }

  /**
   * Flags used by {@code peer rm} that consume the next token as their value.
   *
   * <p>Used by {@link #registerPeersForRmCommand} to skip flag-value pairs when extracting
   * positional peer identifiers.
   */
  private static final Set<String> RM_FLAGS_WITH_VALUE = Set.of("-d", "--directory");

  /**
   * Registers placeholder peers in the PAL directory so that a {@code peer rm} command can find and
   * remove them.
   *
   * <p>Parses the transformed args to extract positional peer identifiers (names or UUIDs),
   * skipping known flags. For each name, a peer with that name is registered. For each UUID, a peer
   * with that exact UUID is registered. For commands using {@code -s} (starting-with), a peer whose
   * name starts with the given prefix is registered so the prefix match finds a result.
   *
   * @param dir the PAL directory to register peers in
   * @param args the transformed command args (after address substitution by the transformer)
   * @throws Exception if peer registration fails
   */
  private void registerPeersForRmCommand(PalDirectory dir, String[] args) throws Exception {
    boolean startingWith = false;
    List<String> positionalArgs = new ArrayList<>();

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if ("-s".equals(arg) || "--starting-with".equals(arg)) {
        startingWith = true;
        continue;
      }
      if (RM_FLAGS_WITH_VALUE.contains(arg) && i + 1 < args.length) {
        i++; // skip value
        continue;
      }
      if (arg.startsWith("-")) {
        continue; // boolean flag (--force, --all, etc.)
      }
      positionalArgs.add(arg);
    }

    for (String id : positionalArgs) {
      UUID uuid;
      String name;
      if (UUID_PATTERN.matcher(id).matches()) {
        uuid = UUID.fromString(id);
        name = "doc-test-rm-" + uuid.toString().substring(0, 8);
      } else if (startingWith) {
        uuid = UUID.randomUUID();
        name = id + "-doc-test";
      } else {
        uuid = UUID.randomUUID();
        name = id;
      }
      PeerInfo peer = new PeerInfo(uuid, name);
      dir.createPeer(peer);
      logger.info("Registered peer '{}' ({}) for rm command", name, uuid);
    }
  }

  /**
   * Pattern matching bare WAL/log name placeholders in args: alphabetic start, then alphanumeric
   * with hyphens/underscores. Excludes flags, file: paths, FQNs (contain dots), UUIDs, numbers.
   */
  private static final Pattern LOG_NAME_PLACEHOLDER_PATTERN =
      Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]*$");

  /**
   * Overrides WAL/log name args with the given WAL name. Replaces args starting with {@code
   * doc-test-wal-} (transformer-uniquified names) as well as any positional arg that looks like a
   * placeholder log name. Stops replacing after encountering a fully-qualified class name to avoid
   * replacing method names or arguments in commands like {@code pal log call}.
   *
   * @param args the original args array
   * @param walName the actual WAL name to substitute
   * @return a new args array with WAL names overridden
   */
  private static String[] overrideWalNames(String[] args, String walName) {
    String[] result = args.clone();
    boolean seenFqn = false;
    for (int i = 0; i < result.length; i++) {
      // Already uniquified WAL names — always replace
      if (result[i].startsWith("doc-test-wal-")) {
        result[i] = walName;
        continue;
      }
      // Note: -i/-o flags are NOT handled here because -o means --offset in log print.
      // Callers that need -i/-o replacement (e.g., testLogCallCommands) should handle
      // them separately.
      // Skip flags and their values
      if (result[i].startsWith("-")) {
        continue;
      }
      // Skip file: Chronicle paths (handled separately by overrideChroniclePaths)
      if (result[i].startsWith("file:")) {
        continue;
      }
      // Detect FQN class names (contain dots like com.example.App) — stop replacing after this
      if (result[i].contains(".")) {
        seenFqn = true;
        continue;
      }
      // After a FQN, remaining positional args are method names/arguments, not log names
      if (seenFqn) {
        continue;
      }
      // Skip all-uppercase tokens — likely enum/constant values like CLASS_METHOD, THROWABLE
      if (result[i].equals(result[i].toUpperCase(Locale.ROOT)) && result[i].length() > 1) {
        continue;
      }
      // Replace positional args that look like placeholder log names
      if (LOG_NAME_PLACEHOLDER_PATTERN.matcher(result[i]).matches()) {
        result[i] = walName;
      }
    }
    return result;
  }

  /**
   * Overrides any arg starting with {@code file:} with the given Chronicle path. This catches both
   * transformer-redirected paths (containing {@code pal-doc-test-}) and placeholder paths that were
   * not redirected (e.g., {@code file:./logs/my-log}).
   *
   * @param args the original args array
   * @param chroniclePath the actual Chronicle path (e.g., {@code file:/tmp/...})
   * @return a new args array with Chronicle paths overridden
   */
  private static String[] overrideChroniclePaths(String[] args, String chroniclePath) {
    String[] result = args.clone();
    for (int i = 0; i < result.length; i++) {
      if (result[i].startsWith("file:")) {
        result[i] = chroniclePath;
      }
    }
    return result;
  }

  /**
   * Replaces any {@code ws://} or {@code wss://} URL in args with the given JSON-RPC address. Doc
   * snippets use placeholder URLs like {@code ws://localhost:9001}; this substitutes the real
   * address of the test peer's JSON-RPC endpoint.
   *
   * @param args the original args array
   * @param jsonRpcAddr the actual WebSocket address (e.g., {@code ws://localhost:54321})
   * @return a new args array with WebSocket URLs overridden
   */
  private static String[] overrideWsUrls(String[] args, String jsonRpcAddr) {
    String[] result = args.clone();
    for (int i = 0; i < result.length; i++) {
      if (result[i].startsWith("ws://") || result[i].startsWith("wss://")) {
        result[i] = jsonRpcAddr;
      }
    }
    return result;
  }

  /**
   * Replaces any {@code tcp://} URL in args with the given ZMQ-RPC address. Doc snippets use
   * placeholder URLs like {@code tcp://localhost:5555}; this substitutes the real address of the
   * test peer's ZMQ-RPC endpoint.
   *
   * @param args the original args array
   * @param zmqAddr the actual ZMQ address (e.g., {@code tcp://localhost:54321})
   * @return a new args array with TCP URLs overridden
   */
  private static String[] overrideTcpUrls(String[] args, String zmqAddr) {
    String[] result = args.clone();
    for (int i = 0; i < result.length; i++) {
      if (result[i].startsWith("tcp://")) {
        result[i] = zmqAddr;
      }
    }
    return result;
  }

  /**
   * Injects {@code -r ZMQ_RPC} into args if no RPC type is already specified and the command is not
   * a direct ws:// call. This is needed because the test peer exposes both ZMQ-RPC and JSON-RPC,
   * and PeerCall requires disambiguation when both are available.
   *
   * @param args the original args array
   * @return a new args array with {@code -r ZMQ_RPC} prepended if needed, or the original array
   */
  private static String[] injectZmqRpcTypeIfNeeded(String[] args) {
    boolean hasRpcType = false;
    boolean isWsDirect = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-r") || args[i].equals("--rpc-type")) {
        hasRpcType = true;
        break;
      }
      if (args[i].startsWith("ws://") || args[i].startsWith("wss://")) {
        isWsDirect = true;
      }
    }
    if (hasRpcType || isWsDirect) {
      return args;
    }
    // Insert -r ZMQ_RPC after the -d <url> pair (if present) to preserve the expected
    // arg layout for runCliSubcommandForDuration which extracts -d from position 0.
    int insertAt = 0;
    if (args.length >= 2 && args[0].equals("-d")) {
      insertAt = 2;
    }
    String[] result = new String[args.length + 2];
    System.arraycopy(args, 0, result, 0, insertAt);
    result[insertAt] = "-r";
    result[insertAt + 1] = "ZMQ_RPC";
    System.arraycopy(args, insertAt, result, insertAt + 2, args.length - insertAt);
    return result;
  }

  /**
   * Injects {@code -r JSON_RPC} into argument arrays for stdin-based JSON-RPC commands that go
   * through the directory (no ws:// URL). This disambiguates the RPC type for peers that listen on
   * both ZMQ-RPC and JSON-RPC.
   */
  private static String[] injectJsonRpcTypeIfNeeded(String[] args) {
    boolean hasRpcType = false;
    boolean isWsDirect = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-r") || args[i].equals("--rpc-type")) {
        hasRpcType = true;
        break;
      }
      if (args[i].startsWith("ws://") || args[i].startsWith("wss://")) {
        isWsDirect = true;
      }
    }
    if (hasRpcType || isWsDirect) {
      return args;
    }
    // Insert -r JSON_RPC after the -d <url> pair (if present)
    int insertAt = 0;
    if (args.length >= 2 && args[0].equals("-d")) {
      insertAt = 2;
    }
    String[] result = new String[args.length + 2];
    System.arraycopy(args, 0, result, 0, insertAt);
    result[insertAt] = "-r";
    result[insertAt + 1] = "JSON_RPC";
    System.arraycopy(args, insertAt, result, insertAt + 2, args.length - insertAt);
    return result;
  }

  /**
   * PeerCall option flags that take a separate value argument. Used by {@link
   * #overridePeerCallTarget} to skip flag-value pairs when searching for the first positional arg.
   */
  private static final Set<String> PEER_CALL_FLAGS_WITH_VALUE =
      Set.of(
          "-d",
          "--directory",
          "-k",
          "--kafka-servers",
          "-r",
          "--rpc-type",
          "-m",
          "--method",
          "-t",
          "--num-threads",
          "--thread-affinity");

  /**
   * Overrides the peer reference in a PEER_CALL command's args. Unlike {@link #overridePeerRef}
   * which relies on heuristics (name must contain "peer"), this method uses the known PeerCall
   * positional structure: the first non-flag, non-URL, non-FQN arg is always the peer.
   *
   * @param args the original args array
   * @param peerRef the peer identifier to substitute (UUID string or name)
   * @return a new args array with the peer reference overridden
   */
  private static String[] overridePeerCallTarget(String[] args, String peerRef) {
    String[] result = args.clone();
    boolean skipNext = false;
    for (int i = 0; i < result.length; i++) {
      if (skipNext) {
        skipNext = false;
        continue;
      }
      if (result[i].startsWith("-")) {
        // Flag with attached value (--key=value): no next arg to skip
        if (!result[i].contains("=") && PEER_CALL_FLAGS_WITH_VALUE.contains(result[i])) {
          skipNext = true;
        }
        continue;
      }
      // Skip URLs (tcp://, ws://, wss://) — handled separately by overrideWsUrls/overrideTcpUrls
      if (result[i].contains("://")) {
        continue;
      }
      // Skip FQN class names (contain dots, e.g., io.quasient.foobar.apps.quantized.rpc.Methods)
      if (result[i].contains(".")) {
        continue;
      }
      // First remaining positional arg is the peer reference
      result[i] = peerRef;
      return result;
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
   * Replaces the file path following {@code --properties} with the given temp path. Doc snippets
   * reference paths like {@code /etc/pal/tuning.properties} that do not exist in the test
   * environment; this substitutes a real temp file so the peer can start.
   *
   * @param args the original args array
   * @param tempPath the path to the temporary properties file
   * @return a new array with the properties path replaced, or the original if no flag found
   */
  private static String[] overridePropertiesPath(String[] args, String tempPath) {
    for (int i = 0; i < args.length - 1; i++) {
      if ("--properties".equals(args[i])) {
        String[] result = args.clone();
        result[i + 1] = tempPath;
        return result;
      }
    }
    return args;
  }

  /**
   * Ensures the args include {@code -d} and {@code -k} flags for commands that need them but don't
   * have them. Many doc snippets omit these flags (relying on environment variables), but the test
   * environment may not have them propagated to CLI subprocesses.
   *
   * @param args the original args array
   * @return a new args array with directory/kafka flags ensured
   */
  private String[] ensureDirectoryAndKafkaFlags(String[] args) {
    boolean hasD = false;
    boolean hasK = false;
    boolean hasFile = false;
    for (String arg : args) {
      if ("-d".equals(arg) || "--directory".equals(arg)) {
        hasD = true;
      }
      if ("-k".equals(arg) || "--kafka-servers".equals(arg)) {
        hasK = true;
      }
      if (arg.startsWith("file:")) {
        hasFile = true;
      }
    }
    List<String> result = new ArrayList<>(Arrays.asList(args));
    if (!hasD) {
      // Prepend -d at index 0: runCliSubcommand only moves -d before the subcommand
      // when it is the first arg (see AbstractCliIT.runCliSubcommand line 617).
      result.add(0, "-d");
      result.add(1, getPalDirectoryUrl());
    }
    if (!hasK && !hasFile) {
      result.add("-k");
      result.add(getKafkaServers());
    }
    return result.toArray(new String[0]);
  }

  /**
   * Ensures the args contain {@code --forget-response}. Log call doc snippets use a single log for
   * both input and output, but the runtime's circularity guard prevents a peer from writing
   * responses to the same log it reads from ({@code --source-log} == {@code --wal}). Adding {@code
   * --forget-response} converts the command to fire-and-forget mode so it writes the request and
   * exits. The full request/response flow is tested in {@code CallerIT} with separate logs.
   *
   * @param args the original args array
   * @return the same array if already present, or a new array with {@code --forget-response}
   *     appended
   */
  private static String[] ensureForgetResponse(String[] args) {
    for (String arg : args) {
      if ("-f".equals(arg) || "--forget-response".equals(arg)) {
        return args;
      }
    }
    String[] result = new String[args.length + 1];
    System.arraycopy(args, 0, result, 0, args.length);
    result[args.length] = "--forget-response";
    return result;
  }

  /**
   * Ensures JDK time helper methods are re-executed (not stubbed) when {@code --stub-all-else} is
   * used. Without this, {@code ZoneId.of()} returns null from WAL stub because {@code ZoneRegion}
   * (a JDK-internal subtype of {@code ZoneId}) is stored as reference-only in the WAL and cannot be
   * deserialized. The null propagates to {@code LocalDate.now(null)}, causing a param-type mismatch
   * ({@code ZoneId} declared type vs {@code ZoneRegion} in WAL) and a {@code NullPointerException}.
   *
   * <p>The fix appends {@code java.time.**} to the {@code --re-execute} patterns so that
   * deterministic JDK time helpers like {@code ZoneId.of} run live and produce valid objects.
   * Non-deterministic operations like {@code LocalDate.now} are still handled by {@code
   * --shield-io} rules which have higher priority in the replay policy.
   *
   * @param args the original args array
   * @return the same array if not applicable, or a modified array with {@code java.time.**} added
   *     to {@code --re-execute}
   */
  private static String[] ensureJdkTimeReExecuteForStubAllElse(String[] args) {
    boolean hasStubAllElse = false;
    int reExecuteValueIdx = -1;
    for (int i = 0; i < args.length; i++) {
      if ("--stub-all-else".equals(args[i])) {
        hasStubAllElse = true;
      }
      if ("--re-execute".equals(args[i]) && i + 1 < args.length) {
        reExecuteValueIdx = i + 1;
      }
    }
    if (!hasStubAllElse) {
      return args;
    }
    String[] result = args.clone();
    if (reExecuteValueIdx >= 0) {
      // Append java.time.** to existing --re-execute pattern
      result[reExecuteValueIdx] = result[reExecuteValueIdx] + ",java.time.**";
    } else {
      // No --re-execute flag — add one before --stub-all-else
      List<String> list = new ArrayList<>(Arrays.asList(args));
      int stubIdx = list.indexOf("--stub-all-else");
      list.add(stubIdx, "java.time.**");
      list.add(stubIdx, "--re-execute");
      result = list.toArray(new String[0]);
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
