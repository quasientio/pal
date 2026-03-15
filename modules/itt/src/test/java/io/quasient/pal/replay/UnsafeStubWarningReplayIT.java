/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.cli.AbstractCliIT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for unsafe stub detection and the {@code --force-stub} override.
 *
 * <p>Validates that the {@link io.quasient.pal.core.replay.SideEffectAnalyzer} detects unsafe stubs
 * (operations stubbed with {@code STUB_FROM_WAL} whose spans contain field mutations on
 * externally-referenced objects) and that the system fails fast with a warning unless {@code
 * --force-stub} is specified.
 *
 * <p>Parameterized over thread count (1 and 2 threads) to verify both single-threaded and
 * multi-threaded replay scenarios.
 */
@RunWith(Parameterized.class)
public class UnsafeStubWarningReplayIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(UnsafeStubWarningReplayIT.class);

  /** Package prefix shared by all replay test applications. */
  private static final String APP_PACKAGE = "io.quasient.foobar.apps.quantized.replay.";

  /** The number of threads for this parameterized test run. */
  private final int threadCount;

  /** Peer process launched for multi-threaded tests. */
  private PeerProcess peerProcess;

  /** Temporary directory for YAML policy files. */
  private Path tempDir;

  /**
   * Creates a parameterized test instance for the given thread count.
   *
   * @param threadCount the number of RPC worker threads (1 or 2)
   */
  public UnsafeStubWarningReplayIT(int threadCount) {
    this.threadCount = threadCount;
  }

  /**
   * Returns the parameterized thread counts.
   *
   * @return collection of parameters: 1-thread and 2-thread scenarios
   */
  @Parameters(name = "threads={0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[] {1}, new Object[] {2});
  }

  /** Initializes per-test state. */
  @Before
  public void setUp() throws IOException {
    peerProcess = null;
    tempDir = Files.createTempDirectory("pal-unsafe-stub-test");
  }

  /**
   * Cleans up resources after each test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
    if (tempDir != null) {
      try (var paths = Files.walk(tempDir)) {
        paths
            .sorted(Comparator.reverseOrder())
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException e) {
                    logger.warn("Failed to delete temp file: {}", p, e);
                  }
                });
      }
    }
  }

  /**
   * Verifies that replaying with a plain {@code STUB_FROM_WAL} for a mutating method exits with a
   * non-zero code and emits a warning about unsafe stub usage.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void unsafeStubWithoutForceExitsWithError() throws Exception {
    // Create a YAML policy that stubs the mutating Enricher.enrich() with plain STUB_FROM_WAL
    String policyContent =
        "defaultAction: RE_EXECUTE\n"
            + "rules:\n"
            + "  - class: \""
            + APP_PACKAGE
            + "MutatingApp$Enricher\"\n"
            + "    method: \"enrich\"\n"
            + "    action: STUB_FROM_WAL\n";
    Path policyFile = tempDir.resolve("unsafe-stub-policy.yaml");
    Files.writeString(policyFile, policyContent);

    if (threadCount == 1) {
      String walSpec = createChronicleWalSpec("unsafe-stub");

      // Record WAL with MutatingApp
      ProcessResult recordResult = recordWal(walSpec, APP_PACKAGE + "MutatingApp", "widget", "100");
      assertEquals("Recording should succeed", 0, recordResult.exitCode());

      logger.info("Recorded output: {}", recordResult.stdout().trim());

      // Replay with plain STUB_FROM_WAL — should fail due to unsafe stub detection
      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--policy", policyFile.toString()),
              APP_PACKAGE + "MutatingApp",
              "widget",
              "100");

      logReplayResult("unsafe-stub", replayResult);

      assertNotEquals(
          "Replay with unsafe stub should exit with non-zero code", 0, replayResult.exitCode());
      String output = replayResult.stdout() + replayResult.stderr();
      assertThat("Output should contain unsafe stub warning", output, containsString("unsafe"));
    } else {
      // Multi-threaded variant
      String walSpec =
          recordMultiThreadWal("unsafe-stub-mt", APP_PACKAGE + "MutatingApp", "enrichViaRpc", 2);

      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--policy", policyFile.toString()),
              APP_PACKAGE + "MutatingApp",
              "service");

      logReplayResult("unsafe-stub-mt", replayResult);

      assertNotEquals(
          "Multi-thread unsafe stub should exit with non-zero code", 0, replayResult.exitCode());
      String output = replayResult.stdout() + replayResult.stderr();
      assertThat("Output should contain unsafe stub warning", output, containsString("unsafe"));
    }
  }

  /**
   * Verifies that {@code --force-stub} allows replay to proceed despite unsafe stub warnings.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void unsafeStubWithForceStubProceedsWithWarning() throws Exception {
    // Create the same unsafe STUB_FROM_WAL policy
    String policyContent =
        "defaultAction: RE_EXECUTE\n"
            + "rules:\n"
            + "  - class: \""
            + APP_PACKAGE
            + "MutatingApp$Enricher\"\n"
            + "    method: \"enrich\"\n"
            + "    action: STUB_FROM_WAL\n";
    Path policyFile = tempDir.resolve("force-stub-policy.yaml");
    Files.writeString(policyFile, policyContent);

    if (threadCount == 1) {
      String walSpec = createChronicleWalSpec("force-stub");

      // Record WAL with MutatingApp
      ProcessResult recordResult = recordWal(walSpec, APP_PACKAGE + "MutatingApp", "widget", "100");
      assertEquals("Recording should succeed", 0, recordResult.exitCode());

      logger.info("Recorded output: {}", recordResult.stdout().trim());

      // Replay with --force-stub — should proceed despite unsafe stub
      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--policy", policyFile.toString(), "--force-stub"),
              APP_PACKAGE + "MutatingApp",
              "widget",
              "100");

      logReplayResult("force-stub", replayResult);

      // With --force-stub, replay proceeds. It may produce divergences because
      // field mutations are dropped, but it should not fail on the unsafe stub check.
      // Verify it did not exit with the side-effect analysis exception.
      String output = replayResult.stdout() + replayResult.stderr();
      assertThat(
          "Output should not contain the unsafe stub exception message",
          output,
          not(containsString("Use --force-stub to proceed anyway")));
    } else {
      // Multi-threaded variant
      String walSpec =
          recordMultiThreadWal("force-stub-mt", APP_PACKAGE + "MutatingApp", "enrichViaRpc", 2);

      CliProcessResult replayResult =
          doReplay(
              walSpec,
              List.of("--policy", policyFile.toString(), "--force-stub"),
              APP_PACKAGE + "MutatingApp",
              "service");

      logReplayResult("force-stub-mt", replayResult);

      // With --force-stub, replay should proceed (not fail on unsafe stub check)
      String output = replayResult.stdout() + replayResult.stderr();
      assertThat(
          "Output should not contain the unsafe stub exception message",
          output,
          not(containsString("Use --force-stub to proceed anyway")));
    }
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  /**
   * Creates a Chronicle WAL spec and registers it for cleanup.
   *
   * @param prefix descriptive prefix
   * @return the WAL spec string
   */
  private String createChronicleWalSpec(String prefix) {
    String id = generateId();
    String path = "/tmp/pal-" + prefix + "-" + id;
    trackChronicleLog(path);
    return "file:" + path;
  }

  /**
   * Records a WAL by running the peer.
   *
   * @param walSpec the WAL spec
   * @param mainClass the main class
   * @param appArgs application arguments
   * @return the process result
   * @throws Exception if recording fails
   */
  private ProcessResult recordWal(String walSpec, String mainClass, String... appArgs)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    args.add("--wal");
    args.add(walSpec);
    args.add("--no-wal-incoming-cli");
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(mainClass);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Runs a replay against the given WAL.
   *
   * @param walSpec the WAL spec
   * @param replayOptions additional replay options
   * @param mainClass the main class
   * @param appArgs application arguments
   * @return the CLI process result
   * @throws Exception if replay fails
   */
  private CliProcessResult doReplay(
      String walSpec, List<String> replayOptions, String mainClass, String... appArgs)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("--wal");
    args.add(walSpec);
    args.addAll(replayOptions);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(mainClass);
    Collections.addAll(args, appArgs);
    return runReplay(args.toArray(new String[0]));
  }

  /**
   * Records a multi-threaded WAL.
   *
   * @param prefix descriptive prefix
   * @param mainClass the main class
   * @param rpcMethod the RPC method
   * @param callCount number of calls
   * @return the WAL spec
   * @throws Exception if recording fails
   */
  private String recordMultiThreadWal(
      String prefix, String mainClass, String rpcMethod, int callCount) throws Exception {
    String walSpec = createChronicleWalSpec(prefix);
    String peerName = "test-unsafe-" + generateId();
    UUID peerId = UUID.randomUUID();

    List<String> launchArgs = new ArrayList<>();
    launchArgs.add("-d");
    launchArgs.add(getPalDirectoryUrl());
    launchArgs.add("-n");
    launchArgs.add(peerName);
    launchArgs.add("--wal");
    launchArgs.add(walSpec);
    launchArgs.add("--zmq-rpc");
    launchArgs.add("auto");
    launchArgs.add("--rpc-threads");
    launchArgs.add("2");
    launchArgs.add("--wal-incoming-rpc");
    launchArgs.add("--as-service");
    launchArgs.add("-cp");
    launchArgs.add(getIttAppsClasspath());
    launchArgs.add(mainClass);
    launchArgs.add("service");

    peerProcess = launchPeer(peerId, launchArgs.toArray(new String[0]));

    for (int i = 0; i < callCount; i++) {
      CliProcessResult result =
          runPeerCall(
              "-d",
              getPalDirectoryUrl(),
              peerName,
              "--rpc-type",
              "ZMQ_RPC",
              "-m",
              rpcMethod,
              mainClass,
              "item" + i,
              String.valueOf((i + 1) * 50));
      logger.info("RPC call {} exit code: {}", i, result.exitCode());
    }

    runPeerCall(
        "-d", getPalDirectoryUrl(), peerName, "--rpc-type", "ZMQ_RPC", "-m", "shutdown", mainClass);

    stopPeer(peerProcess);
    peerProcess = null;
    Thread.sleep(500);

    return walSpec;
  }

  /**
   * Logs the replay result for debugging.
   *
   * @param label a descriptive label
   * @param result the replay result
   */
  private void logReplayResult(String label, CliProcessResult result) {
    logger.info("[{}] Replay exit code: {}", label, result.exitCode());
    logger.info("[{}] Replay stdout: {}", label, result.stdout());
    logger.info("[{}] Replay stderr: {}", label, result.stderr());
  }
}
