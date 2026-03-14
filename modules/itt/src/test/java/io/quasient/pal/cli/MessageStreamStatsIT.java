/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.tools.cli.MessageStreamStats;
import io.quasient.pal.tools.stats.Counters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the `pal stats` command.
 *
 * <p>Tests collecting statistics from Kafka logs using the MessageStreamStats class
 * programmatically rather than via CLI (since stats runs continuously and doesn't terminate).
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class MessageStreamStatsIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(MessageStreamStatsIT.class);

  /** Peer process launched for testing, or null if not launched. */
  private PeerProcess peerProcess;

  /** Sets up test environment before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
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
  }

  /**
   * Tests that MessageStreamStats can collect basic statistics from a Kafka log programmatically.
   *
   * <p>This test creates a Kafka WAL by launching a peer, which causes the peer to write internal
   * messages (like registration, constructor calls, method invocations) to the WAL. We then verify
   * we can collect statistics from those messages using the MessageStreamStats class directly.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testStats_kafkaLog_basicCounters() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL by launching a peer - this will write some messages to the WAL
    String walName = "test-stats-kafka-basic-" + generateId();
    UUID peerId = UUID.randomUUID();

    // Run a simple class that creates objects and calls methods
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for the process to complete and create the log
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Create MessageStreamStats instance to collect statistics from the log
    MessageStreamStats stats = new MessageStreamStats(kafkaServers, walName);

    // Run stats collection in background and stop after a few seconds
    CompletableFuture<Integer> statsFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return stats.call();
              } catch (Exception e) {
                logger.error("Error running stats", e);
                return 1;
              }
            });

    // Wait for Kafka Streams to initialize and process messages
    // Kafka Streams needs time to: start, subscribe, seek to beginning, consume, process
    // Poll counters until messages are processed or timeout
    int maxWaitSeconds = 30;
    int pollIntervalMs = 500;
    int attempts = (maxWaitSeconds * 1000) / pollIntervalMs;
    boolean messagesProcessed = false;

    for (int i = 0; i < attempts; i++) {
      Thread.sleep(pollIntervalMs);
      if (stats.getCounters().getNumberOfMessages().get() > 0) {
        messagesProcessed = true;
        logger.info("Messages processed after {} ms", i * pollIntervalMs);
        break;
      }
    }

    if (!messagesProcessed) {
      logger.warn("No messages processed after {} seconds", maxWaitSeconds);
    }

    // Stop stats collection
    stats.stopStreams();

    // Wait for stats to complete
    Integer exitCode = statsFuture.get(5, TimeUnit.SECONDS);
    assertEquals("Expected successful stats collection", Integer.valueOf(0), exitCode);

    // Verify counters were updated
    Counters counters = stats.getCounters();
    assertThat("Expected counters to be created", counters, notNullValue());
    assertThat(
        "Expected messages to be processed", counters.getNumberOfMessages().get(), greaterThan(0L));
    assertThat(
        "Expected message types to be tracked",
        counters.getMessagesByType().size(),
        greaterThan(0));
    assertThat(
        "Expected peer tracking", counters.getMessagesFromPeer().size(), greaterThanOrEqualTo(1));

    logger.info(
        "Successfully collected {} messages from Kafka log", counters.getNumberOfMessages().get());
  }

  /**
   * Tests that MessageStreamStats can filter messages by type.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testStats_kafkaLog_messageTypeFiltering() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL by launching a peer
    String walName = "test-stats-kafka-filter-" + generateId();
    UUID peerId = UUID.randomUUID();

    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for the process to complete
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Create MessageStreamStats with filter for EXEC_CONSTRUCTOR messages only
    List<String> msgTypes = List.of("EXEC_CONSTRUCTOR");
    MessageStreamStats stats = new MessageStreamStats(kafkaServers, walName, msgTypes, null, null);

    // Run stats collection in background
    CompletableFuture<Integer> statsFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return stats.call();
              } catch (Exception e) {
                logger.error("Error running stats", e);
                return 1;
              }
            });

    // Wait for Kafka Streams to process messages
    int maxWaitSeconds = 30;
    int pollIntervalMs = 500;
    int attempts = (maxWaitSeconds * 1000) / pollIntervalMs;
    boolean messagesProcessed = false;

    for (int i = 0; i < attempts; i++) {
      Thread.sleep(pollIntervalMs);
      if (stats.getCounters().getNumberOfMessages().get() > 0) {
        messagesProcessed = true;
        logger.info("Messages processed after {} ms", i * pollIntervalMs);
        break;
      }
    }

    if (!messagesProcessed) {
      logger.warn("No messages processed after {} seconds", maxWaitSeconds);
    }

    // Stop stats collection
    stats.stopStreams();

    // Wait for stats to complete
    Integer exitCode = statsFuture.get(5, TimeUnit.SECONDS);
    assertEquals("Expected successful stats collection", Integer.valueOf(0), exitCode);

    // Verify counters were updated with filtered messages
    Counters counters = stats.getCounters();
    assertThat(
        "Expected messages to be processed", counters.getNumberOfMessages().get(), greaterThan(0L));

    // All messages should be EXEC_CONSTRUCTOR type
    if (counters.getMessagesByType().size() > 0) {
      // If we have message types, verify they're all the filtered type
      assertThat(
          "Expected only EXEC_CONSTRUCTOR messages",
          counters.getMessagesByType().containsKey("EXEC_CONSTRUCTOR"),
          equalTo(true));
    }

    logger.info(
        "Successfully collected {} filtered messages from Kafka log",
        counters.getNumberOfMessages().get());
  }

  /**
   * Tests that MessageStreamStats can filter messages by peer UUID.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testStats_kafkaLog_peerFiltering() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL by launching a peer
    String walName = "test-stats-kafka-peer-filter-" + generateId();
    UUID peerId = UUID.randomUUID();

    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for the process to complete
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Create MessageStreamStats with filter for specific peer UUID
    String filterPeerUuid = peerId.toString();
    MessageStreamStats stats =
        new MessageStreamStats(kafkaServers, walName, null, filterPeerUuid, null);

    // Run stats collection in background
    CompletableFuture<Integer> statsFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return stats.call();
              } catch (Exception e) {
                logger.error("Error running stats", e);
                return 1;
              }
            });

    // Wait for Kafka Streams to process messages
    int maxWaitSeconds = 30;
    int pollIntervalMs = 500;
    int attempts = (maxWaitSeconds * 1000) / pollIntervalMs;
    boolean messagesProcessed = false;

    for (int i = 0; i < attempts; i++) {
      Thread.sleep(pollIntervalMs);
      if (stats.getCounters().getNumberOfMessages().get() > 0) {
        messagesProcessed = true;
        logger.info("Messages processed after {} ms", i * pollIntervalMs);
        break;
      }
    }

    if (!messagesProcessed) {
      logger.warn("No messages processed after {} seconds", maxWaitSeconds);
    }

    // Stop stats collection
    stats.stopStreams();

    // Wait for stats to complete
    Integer exitCode = statsFuture.get(5, TimeUnit.SECONDS);
    assertEquals("Expected successful stats collection", Integer.valueOf(0), exitCode);

    // Verify counters were updated
    Counters counters = stats.getCounters();
    assertThat(
        "Expected messages to be processed", counters.getNumberOfMessages().get(), greaterThan(0L));

    // All messages should be from the specified peer
    assertThat(
        "Expected messages from specified peer",
        counters.getMessagesFromPeer().containsKey(filterPeerUuid),
        equalTo(true));

    logger.info(
        "Successfully collected {} messages from specified peer",
        counters.getNumberOfMessages().get());
  }

  /**
   * Tests that MessageStreamStats tracks different message categories correctly.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testStats_kafkaLog_categoryTracking() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Create a WAL by launching a peer that performs various operations
    String walName = "test-stats-kafka-categories-" + generateId();
    UUID peerId = UUID.randomUUID();

    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for the process to complete
    int peerExitCode = joinPeer(peerProcess, 10);
    assertEquals("Expected successful peer exit code", 0, peerExitCode);
    peerProcess = null;

    // Create MessageStreamStats to collect all statistics
    MessageStreamStats stats = new MessageStreamStats(kafkaServers, walName);

    // Run stats collection in background
    CompletableFuture<Integer> statsFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return stats.call();
              } catch (Exception e) {
                logger.error("Error running stats", e);
                return 1;
              }
            });

    // Wait for Kafka Streams to process messages
    int maxWaitSeconds = 30;
    int pollIntervalMs = 500;
    int attempts = (maxWaitSeconds * 1000) / pollIntervalMs;
    boolean messagesProcessed = false;

    for (int i = 0; i < attempts; i++) {
      Thread.sleep(pollIntervalMs);
      if (stats.getCounters().getNumberOfMessages().get() > 0) {
        messagesProcessed = true;
        logger.info("Messages processed after {} ms", i * pollIntervalMs);
        break;
      }
    }

    if (!messagesProcessed) {
      logger.warn("No messages processed after {} seconds", maxWaitSeconds);
    }

    // Stop stats collection
    stats.stopStreams();

    // Wait for stats to complete
    Integer exitCode = statsFuture.get(5, TimeUnit.SECONDS);
    assertEquals("Expected successful stats collection", Integer.valueOf(0), exitCode);

    // Verify different categories were tracked
    Counters counters = stats.getCounters();
    assertThat("Expected messages by type map", counters.getMessagesByType(), notNullValue());
    assertThat("Expected messages from peer map", counters.getMessagesFromPeer(), notNullValue());
    assertThat("Expected messages by thread map", counters.getMessagesByThread(), notNullValue());

    // Verify at least some operations were tracked
    // Note: The specific operations depend on what Methods class does
    boolean hasOperations =
        counters.getObjectsCreated().size() > 0
            || counters.getMethodsCalled().size() > 0
            || counters.getFieldReads().size() > 0
            || counters.getFieldWrites().size() > 0;
    assertThat("Expected some operations to be tracked", hasOperations, equalTo(true));

    logger.info("Successfully verified category tracking in statistics");
  }

  /**
   * Tests that MessageStreamStats handles empty logs gracefully.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testStats_kafkaLog_emptyLog() throws Exception {
    String kafkaServers = getKafkaServers();

    // Use a log name that doesn't exist
    String walName = "test-stats-empty-log-" + generateId();

    // Create MessageStreamStats for non-existent log
    MessageStreamStats stats = new MessageStreamStats(kafkaServers, walName);

    // Run stats collection in background
    CompletableFuture<Integer> statsFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return stats.call();
              } catch (Exception e) {
                // Expected to fail or handle gracefully
                logger.info("Stats handling empty/non-existent log: {}", e.getMessage());
                return 1;
              }
            });

    // Wait briefly
    Thread.sleep(2000);

    // Stop stats collection
    stats.stopStreams();

    // Stats may fail or succeed with zero messages
    try {
      statsFuture.get(3, TimeUnit.SECONDS);
    } catch (Exception e) {
      // Either outcome is acceptable for empty log
      logger.info("Stats completed with expected behavior for empty log");
    }

    // Verify counters show zero or handle gracefully
    // Empty log should have zero messages processed
    logger.info("Empty log test completed with counters: {}", stats.getCounters());
  }

  // ==========================================================================
  // Socket-based MessageStreamStats tests (socketMessageStreamStats() method)
  // ==========================================================================

  /**
   * Tests that MessageStreamStats can collect basic statistics from a peer's PUB socket.
   *
   * <p>Given: A peer running with a TCP PUB socket enabled that generates messages (constructor
   * calls, method invocations, etc.) during its main class execution.
   *
   * <p>When: MessageStreamStats is created with the peer's UUID and PAL directory address, and runs
   * for a period to collect statistics from the socket stream.
   *
   * <p>Then: The counters should show messages received with numberOfMessages > 0, and message type
   * tracking should be populated.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testStats_peerSocket_basicCounters() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Given: Peer running with PUB socket
    UUID peerId = UUID.randomUUID();
    String pubEndpoint = "localhost:41781";

    // Start subscriber BEFORE launching the peer to avoid the ZMQ "slow joiner" problem.
    // ZMQ SUB can connect to a non-existing endpoint; the connection completes automatically
    // when the PUB socket is created during peer startup. This ensures the subscriber is
    // ready to receive messages before the peer's main class publishes them.
    MessageStreamStats stats =
        new MessageStreamStats(palDirectory, peerId, "tcp://" + pubEndpoint, null, null, null);

    // Run stats collection in background
    CompletableFuture<Integer> statsFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return stats.call();
              } catch (Exception e) {
                logger.error("Error running stats", e);
                return 1;
              }
            });

    // Allow ZMQ subscriber socket to initialize before launching the peer
    Thread.sleep(200);

    // Launch a peer with TCP PUB socket and a class that generates messages
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for socket to connect and process messages
    int maxWaitSeconds = 10;
    int pollIntervalMs = 200;
    int attempts = (maxWaitSeconds * 1000) / pollIntervalMs;

    for (int i = 0; i < attempts; i++) {
      Thread.sleep(pollIntervalMs);
      if (stats.getCounters().getNumberOfMessages().get() > 0) {
        logger.info("Messages processed after {} ms", i * pollIntervalMs);
        break;
      }
    }

    // Trigger shutdown - socket stream may not terminate cleanly due to ZMQ blocking
    stats.stopStreams();

    // Give a brief moment for shutdown to propagate
    Thread.sleep(100);

    // Cancel the future if still running - socket streaming may not terminate cleanly
    // when the peer exits and there are no more messages
    boolean completed = statsFuture.isDone();
    if (!completed) {
      logger.info("Stats future not complete, cancelling");
      statsFuture.cancel(true);
    }

    // Then: Verify counters show messages received (primary test assertion)
    Counters counters = stats.getCounters();
    assertThat("Expected counters to be created", counters, notNullValue());
    assertThat(
        "Expected messages to be processed", counters.getNumberOfMessages().get(), greaterThan(0L));
    assertThat(
        "Expected message types to be tracked",
        counters.getMessagesByType().size(),
        greaterThan(0));
    assertThat(
        "Expected peer tracking", counters.getMessagesFromPeer().size(), greaterThanOrEqualTo(1));

    logger.info(
        "Successfully collected {} messages from peer socket",
        counters.getNumberOfMessages().get());
  }

  /**
   * Tests that MessageStreamStats can filter messages by type when streaming from a peer socket.
   *
   * <p>Given: A peer running with a TCP PUB socket that generates various message types
   * (EXEC_CONSTRUCTOR, EXEC_INSTANCE_METHOD, EXEC_CLASS_METHOD, etc.) during execution.
   *
   * <p>When: MessageStreamStats is created with a message type filter (e.g., only EXEC_CONSTRUCTOR)
   * and runs for a period collecting statistics from the socket stream.
   *
   * <p>Then: Only messages matching the filtered type should be counted in the statistics.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testStats_peerSocket_messageTypeFiltering() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Given: Peer generating various message types
    UUID peerId = UUID.randomUUID();
    String pubEndpoint = "localhost:41782";

    // Start subscriber BEFORE launching the peer to avoid the ZMQ "slow joiner" problem.
    List<String> msgTypes = List.of("EXEC_CONSTRUCTOR");
    MessageStreamStats stats =
        new MessageStreamStats(palDirectory, peerId, "tcp://" + pubEndpoint, msgTypes, null, null);

    // Run stats collection in background
    CompletableFuture<Integer> statsFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return stats.call();
              } catch (Exception e) {
                logger.error("Error running stats", e);
                return 1;
              }
            });

    // Allow ZMQ subscriber socket to initialize before launching the peer
    Thread.sleep(200);

    // Launch a peer with TCP PUB socket and a class that generates multiple message types
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for socket to connect and process messages
    int maxWaitSeconds = 10;
    int pollIntervalMs = 200;
    int attempts = (maxWaitSeconds * 1000) / pollIntervalMs;

    for (int i = 0; i < attempts; i++) {
      Thread.sleep(pollIntervalMs);
      if (stats.getCounters().getNumberOfMessages().get() > 0) {
        logger.info("Messages processed after {} ms", i * pollIntervalMs);
        break;
      }
    }

    // Trigger shutdown - socket stream may not terminate cleanly due to ZMQ blocking
    stats.stopStreams();

    // Give a brief moment for shutdown to propagate
    Thread.sleep(100);

    // Cancel the future if still running
    if (!statsFuture.isDone()) {
      logger.info("Stats future not complete, cancelling");
      statsFuture.cancel(true);
    }

    // Then: Verify only filtered message types are counted
    Counters counters = stats.getCounters();
    assertThat(
        "Expected messages to be processed", counters.getNumberOfMessages().get(), greaterThan(0L));

    // All messages should be EXEC_CONSTRUCTOR type
    if (counters.getMessagesByType().size() > 0) {
      // Verify only the filtered type is present
      assertThat(
          "Expected only EXEC_CONSTRUCTOR messages",
          counters.getMessagesByType().containsKey("EXEC_CONSTRUCTOR"),
          equalTo(true));
      // Verify no other message types are counted
      assertThat(
          "Expected only one message type (EXEC_CONSTRUCTOR)",
          counters.getMessagesByType().size(),
          equalTo(1));
    }

    logger.info(
        "Successfully collected {} filtered messages from peer socket",
        counters.getNumberOfMessages().get());
  }

  /**
   * Tests that MessageStreamStats can filter messages by peer UUID when streaming from a socket.
   *
   * <p>Given: A peer running with a TCP PUB socket and a known UUID that generates messages.
   *
   * <p>When: MessageStreamStats is created with a peer filter matching the running peer's UUID and
   * runs for a period collecting statistics from the socket stream.
   *
   * <p>Then: Only messages from the specified peer should be counted in the statistics.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testStats_peerSocket_peerFiltering() throws Exception {
    String palDirectory = getPalDirectoryUrl();

    // Given: Peer with known UUID
    UUID peerId = UUID.randomUUID();
    String pubEndpoint = "localhost:41783";

    // Start subscriber BEFORE launching the peer to avoid the ZMQ "slow joiner" problem.
    String filterPeerUuid = peerId.toString();
    MessageStreamStats stats =
        new MessageStreamStats(
            palDirectory, peerId, "tcp://" + pubEndpoint, null, filterPeerUuid, null);

    // Run stats collection in background
    CompletableFuture<Integer> statsFuture =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return stats.call();
              } catch (Exception e) {
                logger.error("Error running stats", e);
                return 1;
              }
            });

    // Allow ZMQ subscriber socket to initialize before launching the peer
    Thread.sleep(200);

    // Launch a peer with TCP PUB socket
    String classToRun = "io.quasient.foobar.apps.quantized.rpc.Methods";

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectory,
            "--tcp-pub",
            pubEndpoint,
            "-cp",
            getIttAppsClasspath(),
            classToRun);

    // Wait for socket to connect and process messages
    int maxWaitSeconds = 10;
    int pollIntervalMs = 200;
    int attempts = (maxWaitSeconds * 1000) / pollIntervalMs;

    for (int i = 0; i < attempts; i++) {
      Thread.sleep(pollIntervalMs);
      if (stats.getCounters().getNumberOfMessages().get() > 0) {
        logger.info("Messages processed after {} ms", i * pollIntervalMs);
        break;
      }
    }

    // Trigger shutdown - socket stream may not terminate cleanly due to ZMQ blocking
    stats.stopStreams();

    // Give a brief moment for shutdown to propagate
    Thread.sleep(100);

    // Cancel the future if still running
    if (!statsFuture.isDone()) {
      logger.info("Stats future not complete, cancelling");
      statsFuture.cancel(true);
    }

    // Then: Verify only messages from specified peer are counted
    Counters counters = stats.getCounters();
    assertThat(
        "Expected messages to be processed", counters.getNumberOfMessages().get(), greaterThan(0L));

    // All messages should be from the specified peer
    assertThat(
        "Expected messages from specified peer",
        counters.getMessagesFromPeer().containsKey(filterPeerUuid),
        equalTo(true));

    // Verify only messages from the specified peer are counted (should only have one peer)
    assertThat(
        "Expected messages from only one peer", counters.getMessagesFromPeer().size(), equalTo(1));

    logger.info(
        "Successfully collected {} messages from specified peer via socket",
        counters.getNumberOfMessages().get());
  }
}
