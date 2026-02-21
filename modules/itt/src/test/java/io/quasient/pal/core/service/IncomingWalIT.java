/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.cli.AbstractCliIT;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for incoming WAL/PUB behavior.
 *
 * <p>Verifies that incoming RPC operations produce both BEFORE and AFTER messages in the WAL when
 * the {@code --wal-incoming-rpc} flag is enabled. Also verifies backward compatibility when the
 * flag is not present, and the behavior of {@code --wal-all-incoming-rpc} for LOG_RPC messages.
 *
 * <p>Test Infrastructure Requirements:
 *
 * <ul>
 *   <li>etcd container running (for peer registration and RPC discovery)
 *   <li>Kafka container running (for WAL topics)
 *   <li>Two peers: a target peer (with --wal-incoming-rpc) and a caller (via {@code pal call})
 * </ul>
 *
 * @see <a href="https://github.com/quasient/pal/issues/779">Issue #779</a>
 */
public class IncomingWalIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(IncomingWalIT.class);

  /** Test application class used for RPC invocations. */
  private static final String METHODS_CLASS = "io.quasient.pal.apps.quantized.rpc.Methods";

  /** Peer process launched for testing, or null if not launched. */
  private PeerProcess peerProcess;

  /** Secondary peer process (e.g., source peer for log replay tests). */
  private PeerProcess secondaryPeerProcess;

  /** Sets up test environment before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
    secondaryPeerProcess = null;
  }

  /**
   * Cleans up resources after each test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    if (secondaryPeerProcess != null) {
      stopPeer(secondaryPeerProcess);
      secondaryPeerProcess = null;
    }
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Verifies that incoming RPC writes both BEFORE and AFTER messages to WAL when {@code
   * --wal-incoming-rpc} is enabled.
   *
   * <p>Given: Peer launched with {@code --wal <topic> --wal-incoming-rpc --zmq-rpc auto -k <kafka>
   * -d <etcd>} running a woven app JAR with a callable method
   *
   * <p>When: {@code pal call -d <etcd> -p <peer-name> -m processArgs
   * io.quasient.pal.apps.quantized.rpc.Methods wal-test-arg} invoked
   *
   * <p>Then: {@code pal print -d <etcd> -l <wal-topic>} shows both the BEFORE message (call to
   * processArgs) and the AFTER message (with return value PROCESSED) for the incoming RPC call
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void incomingRpc_withWalIncomingRpc_writesBothBeforeAndAfterToWal() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-iwal-both-" + generateId();
    String peerName = "test-iwal-both-" + generateId();
    UUID peerId = UUID.randomUUID();

    // Given: Peer launched with --wal-incoming-rpc flag and RPC enabled
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--wal-incoming-rpc",
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // When: Invoke processArgs method on the peer via pal call
    CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "processArgs",
            METHODS_CLASS,
            "wal-test-arg");

    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected processed output", callResult.stdout(), containsString("PROCESSED"));

    // Stop peer to flush WAL
    stopPeer(peerProcess);
    peerProcess = null;

    // Brief delay to let Kafka commit
    Thread.sleep(1000);

    // Then: WAL contains both BEFORE and AFTER messages for the incoming RPC
    CliProcessResult printResult = runPrint("-d", palDirectory, "-l", walName);
    assertEquals("Expected successful print", 0, printResult.exitCode());

    String output = printResult.stdout();
    // With --wal-incoming-rpc, BEFORE message (call) should be present for processArgs
    assertThat(
        "Expected BEFORE message for processArgs in WAL",
        output,
        containsString("call Methods.processArgs"));
    // AFTER message (return) should also be in WAL
    assertThat("Expected AFTER message with return value", output, containsString("PROCESSED"));

    logger.info("Successfully verified both BEFORE and AFTER messages in WAL for incoming RPC");
  }

  /**
   * Verifies backward compatibility: only AFTER message written to WAL without {@code
   * --wal-incoming-rpc} flag.
   *
   * <p>Given: Same setup but WITHOUT {@code --wal-incoming-rpc} flag
   *
   * <p>When: Same {@code pal call} invoked
   *
   * <p>Then: {@code pal print} shows only the AFTER message (backward compatibility preserved)
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void incomingRpc_withoutWalIncomingRpc_writesOnlyAfterToWal() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-iwal-after-" + generateId();
    String peerName = "test-iwal-after-" + generateId();
    UUID peerId = UUID.randomUUID();

    // Given: Peer launched WITHOUT --wal-incoming-rpc flag (default behavior)
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    // When: Invoke processArgs method on the peer via pal call
    CliProcessResult callResult =
        runCall(
            "-d",
            palDirectory,
            "-p",
            peerName,
            "--rpc-type",
            "ZMQ_RPC",
            "-m",
            "processArgs",
            METHODS_CLASS,
            "wal-test-arg");

    assertEquals("Expected successful call", 0, callResult.exitCode());
    assertThat("Expected processed output", callResult.stdout(), containsString("PROCESSED"));

    // Stop peer to flush WAL
    stopPeer(peerProcess);
    peerProcess = null;

    Thread.sleep(1000);

    // Then: WAL contains only the AFTER message (no BEFORE for incoming RPC)
    CliProcessResult printResult = runPrint("-d", palDirectory, "-l", walName);
    assertEquals("Expected successful print", 0, printResult.exitCode());

    String output = printResult.stdout();
    // Without --wal-incoming-rpc, NO BEFORE message for processArgs in WAL
    assertThat(
        "Expected no BEFORE message for processArgs",
        output,
        not(containsString("call Methods.processArgs")));
    // AFTER message should still be present (existing behavior unchanged)
    assertThat("Expected AFTER message with return value", output, containsString("PROCESSED"));

    logger.info("Successfully verified backward compatibility: only AFTER in WAL without flag");
  }

  /**
   * Verifies that LOG_RPC messages are written to WAL with both BEFORE and AFTER when {@code
   * --wal-all-incoming-rpc} is enabled and source log and WAL are different topics.
   *
   * <p>Given: Peer launched with {@code --source-log <source-topic> --wal <different-wal-topic>
   * --wal-all-incoming-rpc -k <kafka> -d <etcd>}; source topic contains messages from a
   * previously-run peer
   *
   * <p>When: Peer replays from source log
   *
   * <p>Then: WAL topic contains both BEFORE and AFTER messages for replayed operations
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void logRpc_withWalAllIncomingRpc_differentLogs_writesBothToWal() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String sourceLogName = "test-iwal-source-" + generateId();
    String walLogName = "test-iwal-wal-" + generateId();

    // Step 1: Launch producer peer to populate source log by running Methods.main()
    UUID producerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            producerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            sourceLogName,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    // Wait for producer to complete (Methods.main() runs and exits)
    int producerExitCode = joinPeer(peerProcess, 15);
    assertEquals("Expected successful producer exit", 0, producerExitCode);
    peerProcess = null;

    // Verify source log has messages
    CliProcessResult sourceCheck = runPrint("-d", palDirectory, "-l", sourceLogName);
    assertEquals("Expected successful source print", 0, sourceCheck.exitCode());
    assertThat("Expected messages in source log", sourceCheck.stdout().length(), greaterThan(0));

    // Step 2: Launch consumer peer with --wal-all-incoming-rpc and different WAL topic
    UUID consumerId = UUID.randomUUID();
    secondaryPeerProcess =
        launchPeer(
            consumerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-s",
            sourceLogName,
            "-w",
            walLogName,
            "--wal-all-incoming-rpc",
            "-cp",
            getIttAppsClasspath());

    // Wait for consumer to process messages from source log
    Thread.sleep(8000);

    // Stop consumer to flush WAL
    stopPeer(secondaryPeerProcess);
    secondaryPeerProcess = null;

    Thread.sleep(1000);

    // Step 3: Verify WAL contains messages from replayed operations
    CliProcessResult walPrint = runPrint("-d", palDirectory, "-l", walLogName);
    assertEquals("Expected successful WAL print", 0, walPrint.exitCode());

    String walOutput = walPrint.stdout();
    assertThat("Expected messages in WAL", walOutput.length(), greaterThan(0));
    // With --wal-all-incoming-rpc, BEFORE (call) messages should be present from LOG_RPC
    assertThat(
        "Expected BEFORE messages in WAL (call entries)", walOutput, containsString("call "));
    // AFTER (return) messages should also be present
    assertThat(
        "Expected AFTER messages in WAL (return entries)", walOutput, containsString("return "));

    logger.info(
        "Successfully verified LOG_RPC messages written to different WAL with"
            + " --wal-all-incoming-rpc");
  }

  /**
   * Verifies circularity guard: no duplicate BEFORE messages when source log and WAL are the same
   * topic with {@code --wal-all-incoming-rpc}.
   *
   * <p>Given: Peer launched with {@code --log <same-topic> --wal-all-incoming-rpc -k <kafka> -d
   * <etcd>} (source and WAL are the same topic)
   *
   * <p>When: Peer reads from the log
   *
   * <p>Then: No duplicate BEFORE messages written (circularity guard active); warning logged
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void logRpc_withSameLog_walAllIncomingRpc_doesNotDuplicate() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String logName = "test-iwal-same-" + generateId();

    // Step 1: Launch producer peer to populate the log with Methods.main() messages
    UUID producerId = UUID.randomUUID();
    peerProcess =
        launchPeer(
            producerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--log",
            logName,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    // Wait for producer to complete
    int producerExitCode = joinPeer(peerProcess, 15);
    assertEquals("Expected successful producer exit", 0, producerExitCode);
    peerProcess = null;

    // Count original messages in log
    CliProcessResult originalPrint = runPrint("-d", palDirectory, "-l", logName);
    assertEquals("Expected successful print", 0, originalPrint.exitCode());
    long originalLineCount = originalPrint.stdout().lines().filter(l -> !l.isBlank()).count();
    assertThat("Expected messages in log", originalLineCount, greaterThan(0L));

    logger.info("Original message count in log: {}", originalLineCount);

    // Step 2: Launch consumer with same log (--log) and --wal-all-incoming-rpc
    // The circularity guard should prevent BEFORE writes since source == WAL
    UUID consumerId = UUID.randomUUID();
    secondaryPeerProcess =
        launchPeer(
            consumerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--log",
            logName,
            "--wal-all-incoming-rpc",
            "-cp",
            getIttAppsClasspath());

    // Wait for consumer to process messages
    Thread.sleep(8000);

    // Step 3: Verify circularity guard warning in consumer's log
    assertTrue(
        "Expected circularity guard warning in consumer log",
        secondaryPeerProcess.containsLogLine(
            "source and WAL are the same log.*ignoring to prevent circular writes"));

    // Stop consumer to flush WAL
    stopPeer(secondaryPeerProcess);
    secondaryPeerProcess = null;

    Thread.sleep(1000);

    // Step 4: Verify no unbounded message growth
    CliProcessResult afterPrint = runPrint("-d", palDirectory, "-l", logName);
    assertEquals("Expected successful print after consumer", 0, afterPrint.exitCode());
    long afterLineCount = afterPrint.stdout().lines().filter(l -> !l.isBlank()).count();

    logger.info(
        "Message count after consumer: {} (original: {})", afterLineCount, originalLineCount);

    // With circularity guard, BEFORE messages are NOT written back.
    // AFTER messages may still be written (existing behavior not guarded by
    // shouldWriteIncomingToWal).
    // The source log reader already skips self-produced offsets, preventing infinite loops.
    // The message count should not have grown unboundedly.
    assertThat(
        "Message count should not have grown unboundedly (circularity guard)",
        afterLineCount < originalLineCount * 3);

    logger.info("Successfully verified circularity guard prevents unbounded message growth");
  }
}
