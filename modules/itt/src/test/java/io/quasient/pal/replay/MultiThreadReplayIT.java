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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.cli.AbstractCliIT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for end-to-end multi-threaded WAL replay.
 *
 * <p>Validates the complete multi-threaded replay pipeline: recording a WAL from a peer with
 * multiple RPC worker threads, indexing the WAL to verify entry-point markers and thread
 * distribution, and replaying the WAL with zero divergences.
 *
 * <p>Parameterized over WAL backend type ("chronicle" or "kafka"). Chronicle uses {@code file:}
 * prefix WAL paths; Kafka uses topic names with {@code -k} bootstrap servers.
 *
 * <p>Uses the {@code RpcCalculator} test application from {@code itt-apps}, which provides
 * deterministic {@code factorial} and {@code sum} methods. The ZMQ DEALER pattern distributes
 * sequential RPC calls across worker threads in round-robin fashion, ensuring multi-thread coverage
 * without requiring concurrent call submission.
 *
 * <p><b>Recording strategy:</b> The peer is launched with {@code --as-service --wal-incoming-rpc
 * --rpc-threads 3}. Compute RPC calls are issued sequentially via {@code pal call} (the DEALER
 * round-robins them to different worker threads). A {@code shutdown()} RPC call is recorded last.
 * During replay, the shutdown call releases the latch in {@code RpcCalculator.main()}, allowing the
 * replay to complete cleanly.
 *
 * <p><b>Test Infrastructure Requirements:</b>
 *
 * <ul>
 *   <li>etcd running (Docker)
 *   <li>Kafka running (Docker) — for Kafka backend variant
 *   <li>{@link AbstractCliIT} base class for process management
 *   <li>{@code RpcCalculator} test app
 * </ul>
 *
 * @see io.quasient.pal.apps.quantized.replay.RpcCalculator
 */
@RunWith(Parameterized.class)
public class MultiThreadReplayIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(MultiThreadReplayIT.class);

  /** Fully qualified name of the RpcCalculator test application. */
  private static final String RPC_CALCULATOR_CLASS =
      "io.quasient.pal.apps.quantized.replay.RpcCalculator";

  /** Number of RPC worker threads to use when launching the peer. */
  private static final int RPC_THREADS = 3;

  /**
   * Number of compute RPC calls to issue per test. Should be >= RPC_THREADS to ensure the DEALER
   * round-robin distributes at least one call to each worker thread.
   */
  private static final int RPC_CALL_COUNT = 3;

  /**
   * Regex pattern matching the executor thread identifier in WAL index verbose output. Captures
   * identifiers like "Executor 0", "Executor 1", etc.
   */
  private static final Pattern EXECUTOR_PATTERN = Pattern.compile("Executor \\d+");

  /** The WAL backend type for this test run ("chronicle" or "kafka"). */
  private final String backend;

  /** Peer process launched for the current test, or null if not launched. */
  private PeerProcess peerProcess;

  /**
   * Creates a parameterized test instance for the given backend.
   *
   * @param backend the WAL backend type ("chronicle" or "kafka")
   */
  public MultiThreadReplayIT(String backend) {
    this.backend = backend;
  }

  /**
   * Returns the parameterized backend types.
   *
   * @return collection of parameters: "chronicle" and "kafka"
   */
  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[] {"chronicle"}, new Object[] {"kafka"});
  }

  /** Initializes per-test state. */
  @Before
  public void setUp() {
    peerProcess = null;
  }

  /**
   * Cleans up peer process after each test.
   *
   * @throws InterruptedException if interrupted while stopping the peer
   */
  @After
  public void tearDown() throws InterruptedException {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Records a multi-threaded RPC WAL and replays it with zero divergences.
   *
   * <p>This is the primary end-to-end test for Phase 2 multi-threaded replay. It exercises the full
   * pipeline: peer startup with multiple RPC threads, sequential RPC calls that distribute across
   * threads via DEALER round-robin, WAL recording with entry-point markers, peer shutdown, and
   * deterministic replay.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void recordAndReplayMultiThreadRpc_zeroDivergence() throws Exception {
    String walSpec = recordMultiThreadWal("mt-zero-div");

    CliProcessResult replayResult = doReplay(walSpec, List.of());

    logger.info("Replay exit code: {}", replayResult.exitCode());
    logger.info("Replay stdout: {}", replayResult.stdout());
    logger.info("Replay stderr: {}", replayResult.stderr());

    assertEquals("Replay should succeed with zero divergences", 0, replayResult.exitCode());
    assertThat(
        "Replay stderr should not contain DIVERGENCE",
        replayResult.stderr(),
        not(containsString("DIVERGENCE")));
    assertThat(
        "Replay stderr should not contain MISMATCH",
        replayResult.stderr(),
        not(containsString("MISMATCH")));
  }

  /**
   * Verifies that the WAL index shows entry-point markers distributed across multiple threads.
   *
   * <p>After recording a WAL with multi-threaded RPC, the {@code pal wal-index} command should
   * report entries on at least 2 different threads (confirming round-robin distribution) and show
   * entry-point markers on those entries.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void walIndexShowsMultipleThreads() throws Exception {
    String walSpec = recordMultiThreadWal("mt-wal-idx");

    CliProcessResult indexResult = indexWal(walSpec);

    logger.info("WAL index exit code: {}", indexResult.exitCode());
    logger.info("WAL index stdout:\n{}", indexResult.stdout());

    assertEquals("WAL index should succeed", 0, indexResult.exitCode());

    String output = indexResult.stdout();

    // Verify the summary shows input threads
    assertThat("Summary should show input threads", output, containsString("Input threads:"));

    // Parse verbose lines to count unique RPC executor thread identifiers.
    // Thread names contain spaces (e.g., "ZMQ_SOCKET_RPC Executor 0"), so we use a
    // regex pattern to extract the "Executor N" portion as a unique identifier.
    Set<String> rpcThreadNames = new HashSet<>();
    output
        .lines()
        .filter(line -> line.contains("Executor") && line.contains("OPERATION"))
        .forEach(
            line -> {
              Matcher matcher = EXECUTOR_PATTERN.matcher(line);
              if (matcher.find()) {
                rpcThreadNames.add(matcher.group());
              }
            });

    logger.info(
        "Found {} RPC thread names in WAL index: {}", rpcThreadNames.size(), rpcThreadNames);

    assertThat(
        "WAL should contain entries on at least 2 RPC worker threads",
        rpcThreadNames.size(),
        greaterThanOrEqualTo(2));

    // Verify entry-point markers are present in verbose output
    assertThat(
        "Verbose output should show ENTRY_POINT markers on RPC entries",
        output,
        containsString("ENTRY_POINT"));
  }

  /**
   * Replays a multi-threaded WAL with unordered threading and verifies zero divergences.
   *
   * <p>The {@code --replay-threading unordered} flag disables the WAL-offset ordering barrier,
   * allowing replay threads to proceed without cross-thread synchronization. For deterministic
   * applications like RpcCalculator, this should still produce zero divergences.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayWithUnorderedThreading_zeroDivergence() throws Exception {
    String walSpec = recordMultiThreadWal("mt-unord");

    CliProcessResult replayResult = doReplay(walSpec, List.of("--threading", "unordered"));

    logger.info("Unordered replay exit code: {}", replayResult.exitCode());
    logger.info("Unordered replay stdout: {}", replayResult.stdout());
    logger.info("Unordered replay stderr: {}", replayResult.stderr());

    assertEquals(
        "Unordered replay should succeed with zero divergences", 0, replayResult.exitCode());
    assertThat(
        "Unordered replay stderr should not contain DIVERGENCE",
        replayResult.stderr(),
        not(containsString("DIVERGENCE")));
    assertThat(
        "Unordered replay stderr should not contain MISMATCH",
        replayResult.stderr(),
        not(containsString("MISMATCH")));
  }

  /**
   * Verifies that cross-divergence is detected during multi-threaded replay with thread context in
   * the divergence report.
   *
   * <p>Records a WAL with normal RpcCalculator behavior, then replays with broken mode (passing
   * {@code "broken"} as an argument to main). The broken mode causes factorial and sum to return
   * incorrect results, triggering VALUE_MISMATCH divergences. The divergence report should include
   * the thread name(s) where mismatches occurred.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void replayMultiThreadRpc_crossDivergence() throws Exception {
    // Record with normal behavior (no broken mode)
    String walSpec = recordMultiThreadWal("mt-cross-div");

    // Replay with broken mode: pass "broken" as the app arg, which makes
    // RpcCalculator produce deliberately wrong results
    CliProcessResult replayResult = doReplay(walSpec, List.of(), "broken");

    logger.info("Cross-divergence replay exit code: {}", replayResult.exitCode());
    logger.info("Cross-divergence replay stdout: {}", replayResult.stdout());
    logger.info("Cross-divergence replay stderr: {}", replayResult.stderr());

    assertEquals(
        "Replay with different results should exit with code 2", 2, replayResult.exitCode());
    assertThat(
        "Replay stderr should contain VALUE_MISMATCH",
        replayResult.stderr(),
        containsString("VALUE_MISMATCH"));

    // Verify thread context is present in divergence report
    assertThat(
        "Divergence report should include RPC thread name",
        replayResult.stderr(),
        containsString("Executor"));
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  /**
   * Creates a WAL spec appropriate for the current backend.
   *
   * <p>For Chronicle, returns a {@code file:}-prefixed path and registers it for cleanup. For
   * Kafka, returns a unique topic name.
   *
   * @param prefix a descriptive prefix for the WAL name
   * @return the WAL spec string
   */
  private String createWalSpec(String prefix) {
    String id = generateId();
    if ("chronicle".equals(backend)) {
      String path = "/tmp/pal-" + prefix + "-" + id;
      trackChronicleLog(path);
      return "file:" + path;
    } else {
      return "test-" + prefix + "-" + id;
    }
  }

  /**
   * Records a multi-threaded WAL by starting a peer with RPC threads and issuing sequential calls.
   *
   * <p>Starts a peer with {@code --rpc-threads 3}, issues {@link #RPC_CALL_COUNT} sequential RPC
   * calls (the ZMQ DEALER pattern distributes them to different worker threads in round-robin
   * fashion), then calls {@code shutdown()} via RPC to record a clean shutdown in the WAL. The
   * shutdown entry ensures that replay can complete (releasing the latch in {@code
   * RpcCalculator.main()}).
   *
   * @param prefix descriptive prefix for the WAL name
   * @return the WAL spec string
   * @throws Exception if recording fails
   */
  private String recordMultiThreadWal(String prefix) throws Exception {
    String walSpec = createWalSpec(prefix);
    String peerName = "test-mt-" + generateId();

    peerProcess = startRpcPeer(walSpec, peerName);

    // Issue sequential compute calls. The ZMQ DEALER distributes them to different
    // worker threads in round-robin fashion regardless of call timing.
    issueRpcCalls(peerName, RPC_CALL_COUNT);

    // Call shutdown() via RPC so it's recorded in the WAL.
    // During replay, this releases the SHUTDOWN_LATCH so main() can exit.
    CliProcessResult shutdownResult =
        runCall(
            "-d",
            getPalDirectoryUrl(),
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "shutdown",
            RPC_CALCULATOR_CLASS);
    logger.info("Shutdown RPC exit code: {}", shutdownResult.exitCode());
    if (shutdownResult.exitCode() != 0) {
      logger.warn("Shutdown RPC failed: {}", shutdownResult.stderr());
    }

    // Stop the peer process
    stopPeer(peerProcess);
    peerProcess = null;

    // Allow time for WAL flush, especially for Kafka
    Thread.sleep("kafka".equals(backend) ? 2000 : 500);

    return walSpec;
  }

  /**
   * Starts an RPC-enabled peer with multiple worker threads and WAL recording.
   *
   * <p>Launches a peer with {@code --zmq-rpc auto}, {@code --rpc-threads 3}, {@code
   * --wal-incoming-rpc}, and the given WAL spec. Waits for the peer to become ready.
   *
   * @param walSpec the WAL spec (Chronicle path or Kafka topic)
   * @param peerName human-readable name for the peer
   * @return the launched peer process
   * @throws Exception if peer launch fails
   */
  private PeerProcess startRpcPeer(String walSpec, String peerName) throws Exception {
    UUID peerId = UUID.randomUUID();
    String palDirectory = getPalDirectoryUrl();

    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(palDirectory);
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.add("-n");
    args.add(peerName);
    args.add("--wal");
    args.add(walSpec);
    args.add("--zmq-rpc");
    args.add("auto");
    args.add("--rpc-threads");
    args.add(String.valueOf(RPC_THREADS));
    args.add("--wal-incoming-rpc");
    args.add("--as-service");
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(RPC_CALCULATOR_CLASS);

    return launchPeer(peerId, args.toArray(new String[0]));
  }

  /**
   * Issues sequential compute RPC calls to the peer.
   *
   * <p>Calls are issued one at a time. The ZMQ DEALER distributes them to different RPC worker
   * threads in round-robin fashion, ensuring multi-thread WAL entries without requiring concurrent
   * call submission. Even-indexed calls invoke {@code factorial}, odd-indexed calls invoke {@code
   * sum}.
   *
   * @param peerName the peer name to target via {@code pal call}
   * @param totalCalls total number of RPC calls to issue
   * @throws Exception if any call fails
   */
  private void issueRpcCalls(String peerName, int totalCalls) throws Exception {
    String palDirectory = getPalDirectoryUrl();

    for (int i = 0; i < totalCalls; i++) {
      CliProcessResult result;
      if (i % 2 == 0) {
        result =
            runCall(
                "-d",
                palDirectory,
                "-p",
                peerName,
                "--rpc-type",
                "ZMQ_RPC",
                "-m",
                "factorial",
                RPC_CALCULATOR_CLASS,
                String.valueOf(i + 1));
      } else {
        result =
            runCall(
                "-d",
                palDirectory,
                "-p",
                peerName,
                "--rpc-type",
                "ZMQ_RPC",
                "-m",
                "sum",
                RPC_CALCULATOR_CLASS,
                String.valueOf(i),
                String.valueOf(i + 1));
      }

      logger.info("RPC call {} exit code: {}", i, result.exitCode());
      if (result.exitCode() != 0) {
        logger.warn("RPC call {} stderr: {}", i, result.stderr());
        throw new RuntimeException("RPC call " + i + " failed with exit code " + result.exitCode());
      }
    }
  }

  /**
   * Runs a replay against the given WAL spec with optional replay options and application
   * arguments.
   *
   * @param walSpec the WAL spec (Chronicle path or Kafka topic)
   * @param replayOptions additional replay options (e.g., {@code --threading unordered})
   * @param appArgs application arguments passed to the main class (e.g., {@code "broken"})
   * @return the CLI process result
   * @throws Exception if replay fails
   */
  private CliProcessResult doReplay(String walSpec, List<String> replayOptions, String... appArgs)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("--wal");
    args.add(walSpec);
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.addAll(replayOptions);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(RPC_CALCULATOR_CLASS);
    Collections.addAll(args, appArgs);
    return runReplay(args.toArray(new String[0]));
  }

  /**
   * Runs {@code pal wal-index} on the given WAL spec with verbose output.
   *
   * @param walSpec the WAL spec (Chronicle path or Kafka topic)
   * @return the CLI process result
   * @throws Exception if indexing fails
   */
  private CliProcessResult indexWal(String walSpec) throws Exception {
    List<String> args = new ArrayList<>();
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.add("-v");
    args.add(walSpec);
    return runWalIndex(args.toArray(new String[0]));
  }
}
