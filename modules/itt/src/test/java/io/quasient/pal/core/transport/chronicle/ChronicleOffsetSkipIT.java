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
package io.quasient.pal.core.transport.chronicle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.PeerProcess;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for Chronicle queue offset-skipping behavior.
 *
 * <p>When source log and WAL are the same Chronicle queue ({@code --log file:/path}), the source
 * log reader must skip self-produced messages to prevent infinite re-processing. These tests verify
 * that the offset-skipping mechanism works correctly end-to-end.
 *
 * <p>Mirrors the Kafka same-source/WAL tests in {@code IncomingWalIT} but for Chronicle queues.
 */
public class ChronicleOffsetSkipIT extends AbstractIntegrationTest {

  /** Test application classpath. */
  private static final String ITT_APPS_CP = "modules/itt-apps/target/classes";

  /** Test application class whose execution generates hot-path WAL entries. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  private Path tempDir;
  private Path walPath;

  /** Producer peer process, or null if not launched. */
  private PeerProcess producerPeer;

  /** Consumer peer process, or null if not launched. */
  private PeerProcess consumerPeer;

  /** Sets up test fixtures with a temporary directory for Chronicle queues. */
  @Before
  public void setUp() throws IOException {
    tempDir = Files.createTempDirectory("chronicle-skip-itt");
    walPath = tempDir.resolve("test-wal");
    producerPeer = null;
    consumerPeer = null;
    logger.info("Created temp directory for Chronicle offset-skip tests: {}", tempDir);
  }

  /** Cleans up running peers and temporary Chronicle queue directories. */
  @After
  public void tearDown() throws Exception {
    if (consumerPeer != null) {
      stopPeer(consumerPeer);
      consumerPeer = null;
    }
    if (producerPeer != null) {
      stopPeer(producerPeer);
      producerPeer = null;
    }
    if (tempDir != null && Files.exists(tempDir)) {
      try (Stream<Path> files = Files.walk(tempDir)) {
        files
            .sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException e) {
                    logger.warn("Failed to delete {}", path, e);
                  }
                });
      } catch (IOException e) {
        logger.warn("Failed to clean up temp directory {}", tempDir, e);
      }
    }
  }

  /**
   * Tests that a peer using {@code --log file:/path} (same source and WAL) completes successfully.
   *
   * <p>Without offset skipping, the peer would re-read its own messages from the source log and
   * potentially loop forever. This test verifies the peer exits normally.
   */
  @Test
  public void chronicleSameSourceAndWal_completesSuccessfully()
      throws IOException, InterruptedException {
    logger.info("Testing Chronicle --log (same source and WAL) completes without hanging");

    ProcessResult result =
        runPeer(
            "--log",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Peer should exit with code 0", result.exitCode(), is(0));

    // Verify the Chronicle queue was created and contains messages
    assertThat("Chronicle queue should exist", Files.exists(walPath), is(true));
    int messageCount = ChronicleLogUtil.countMessages(walPath);
    assertThat("Queue should contain messages", messageCount > 0, is(true));
    logger.info("Peer completed with {} messages in Chronicle queue", messageCount);

    // Verify no errors
    String stderrLower = result.stderr().toLowerCase(Locale.ROOT);
    assertThat("Should not contain 'error'", stderrLower.contains("error"), is(false));
    assertThat("Should not contain 'exception'", stderrLower.contains("exception"), is(false));
  }

  /**
   * Tests that replay with same source and WAL does not cause unbounded message growth.
   *
   * <p>Scenario:
   *
   * <ol>
   *   <li>Peer A writes messages to Chronicle queue X via {@code --wal file:X}
   *   <li>Peer B reads from X and writes back to X via {@code --log file:X}
   *   <li>Offset skipping prevents Peer B from re-processing its own writes
   * </ol>
   *
   * <p>Without offset skipping, Peer B would write duplicates that it would then re-read and
   * re-write, causing the queue to grow without bound. This test verifies bounded growth.
   */
  @Test
  public void chronicleSameSourceAndWal_doesNotDuplicate()
      throws IOException, InterruptedException {
    logger.info("Testing Chronicle --log replay does not cause unbounded growth");

    // Step 1: Producer writes messages to the Chronicle queue
    ProcessResult writeResult =
        runPeer(
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Producer should exit with code 0", writeResult.exitCode(), is(0));

    int originalCount = ChronicleLogUtil.countMessages(walPath);
    assertThat("Queue should contain messages after producer", originalCount, greaterThan(0));
    logger.info("Producer wrote {} messages to Chronicle queue", originalCount);

    // Step 2: Consumer reads from AND writes to the same queue (--log)
    ProcessResult replayResult =
        runPeer(
            "--log",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Consumer should exit with code 0", replayResult.exitCode(), is(0));

    int afterCount = ChronicleLogUtil.countMessages(walPath);
    logger.info("Message count after replay: {} (original: {})", afterCount, originalCount);

    // The consumer writes its own execution messages to the queue, so afterCount > originalCount
    // is expected. The consumer generates messages from: (a) replaying the producer's messages,
    // and (b) executing its own main class. Both produce hot-path WAL entries. Without offset
    // skipping the consumer would re-process its own writes indefinitely, causing exponential
    // growth. With offset skipping, growth is bounded to a linear multiple.
    assertThat(
        "Message count should not have grown unboundedly (offset skipping active)",
        afterCount < originalCount * 10);

    // Verify no errors
    String stderrLower = replayResult.stderr().toLowerCase(Locale.ROOT);
    assertThat("Should not contain 'error'", stderrLower.contains("error"), is(false));
    assertThat("Should not contain 'exception'", stderrLower.contains("exception"), is(false));
  }

  /**
   * Control test: separate source and WAL do not use offset skipping.
   *
   * <p>When source and WAL are different queues ({@code --source-log file:X --wal file:Y}), offset
   * skipping is not needed because reads and writes go to different queues. This test verifies the
   * baseline behavior that messages are read and processed normally.
   */
  @Test
  public void chronicleSeparateSourceAndWal_processesAllMessages()
      throws IOException, InterruptedException {
    logger.info("Testing Chronicle separate source/WAL processes all messages");

    Path sourcePath = tempDir.resolve("source-queue");
    Path walSeparatePath = tempDir.resolve("separate-wal");

    // Step 1: Write messages to source queue
    ProcessResult writeResult =
        runPeer(
            "--wal",
            "file:" + sourcePath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Producer should exit with code 0", writeResult.exitCode(), is(0));

    int sourceCount = ChronicleLogUtil.countMessages(sourcePath);
    assertThat("Source queue should contain messages", sourceCount, greaterThan(0));
    logger.info("Source queue contains {} messages", sourceCount);

    // Step 2: Read from source, write to separate WAL
    ProcessResult readResult =
        runPeer(
            "--source-log",
            "file:" + sourcePath.toAbsolutePath(),
            "--wal",
            "file:" + walSeparatePath.toAbsolutePath(),
            "-cp",
            "modules/itt-apps/target/classes",
            "io.quasient.foobar.apps.quantized.rpc.Methods");

    assertThat("Reader should exit with code 0", readResult.exitCode(), is(0));

    // WAL should contain messages from both replay and own execution
    int walCount = ChronicleLogUtil.countMessages(walSeparatePath);
    assertThat("WAL should contain messages", walCount, greaterThan(0));

    // Source queue should be unchanged (reading doesn't modify)
    int sourceCountAfter = ChronicleLogUtil.countMessages(sourcePath);
    assertThat("Source queue should be unchanged", sourceCountAfter, is(sourceCount));

    // No errors
    String stderrLower = readResult.stderr().toLowerCase(Locale.ROOT);
    assertThat("Should not contain 'error'", stderrLower.contains("error"), is(false));
    assertThat("Should not contain 'exception'", stderrLower.contains("exception"), is(false));

    logger.info("Separate source/WAL: source={} messages, WAL={} messages", sourceCount, walCount);
  }

  /**
   * Verifies that offset-skip log messages appear in the peer log when source and WAL are the same
   * Chronicle queue.
   *
   * <p>Scenario:
   *
   * <ol>
   *   <li>Producer peer writes messages to Chronicle queue Q via {@code --wal file:Q}
   *   <li>Consumer peer reads from Q and writes to Q via {@code --log file:Q}, running the same
   *       main class
   *   <li>Consumer's execution generates hot-path messages written back to Q
   *   <li>Source log reader skips those self-produced messages via the offset publisher
   * </ol>
   *
   * <p>Asserts:
   *
   * <ul>
   *   <li>Consumer peer log contains "Skipped self-produced message at index" (skip active)
   *   <li>Message count in the queue stays bounded
   * </ul>
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void chronicleSameSourceAndWal_peerLogShowsSkippedMessages() throws Exception {
    logger.info("Testing Chronicle offset-skip evidence in peer log");

    // Step 1: Producer writes messages to Chronicle queue
    UUID producerId = UUID.randomUUID();
    producerPeer =
        launchPeer(
            producerId,
            "--wal",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            ITT_APPS_CP,
            METHODS_CLASS);

    int producerExitCode = joinPeer(producerPeer, 15);
    assertEquals("Producer should exit successfully", 0, producerExitCode);
    producerPeer = null;

    int originalCount = ChronicleLogUtil.countMessages(walPath);
    assertThat("Queue should contain messages after producer", originalCount, greaterThan(0));
    logger.info("Producer wrote {} messages to Chronicle queue", originalCount);

    // Step 2: Consumer reads from AND writes to the same queue (--log), running main class
    UUID consumerId = UUID.randomUUID();
    consumerPeer =
        launchPeer(
            consumerId,
            "--log",
            "file:" + walPath.toAbsolutePath(),
            "-cp",
            ITT_APPS_CP,
            METHODS_CLASS);

    int consumerExitCode = joinPeer(consumerPeer, 15);
    assertEquals("Consumer should exit successfully", 0, consumerExitCode);

    // Step 3: Verify offset-skip messages in consumer's peer log
    assertTrue(
        "Consumer peer log should contain offset-skip messages",
        consumerPeer.containsLogLine("Skipped self-produced message at index"));

    consumerPeer = null;

    // Step 4: Verify bounded message growth — the consumer generates messages from replaying
    // the producer's messages AND executing its own main class, both producing hot-path WAL
    // entries. Without offset skipping, growth would be exponential; with it, linear.
    int afterCount = ChronicleLogUtil.countMessages(walPath);
    logger.info("Message count after consumer: {} (original: {})", afterCount, originalCount);

    assertThat(
        "Message count should not have grown unboundedly (offset skipping active)",
        afterCount < originalCount * 10);
  }
}
