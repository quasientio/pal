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
package io.quasient.pal.core.transport.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
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
 * End-to-end integration tests for Kafka source log reader offset skipping.
 *
 * <p>When source log and WAL are the same Kafka topic ({@code --log <topic>}), the source log
 * reader must skip self-produced messages. These tests launch real peers, verify that offset-skip
 * log messages appear in the peer log, and that message count stays bounded.
 *
 * <p>Test Infrastructure Requirements:
 *
 * <ul>
 *   <li>etcd container running (for peer registration)
 *   <li>Kafka container running (for log topics)
 * </ul>
 */
public class KafkaOffsetSkipIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(KafkaOffsetSkipIT.class);

  /** Test application class whose execution generates hot-path WAL entries. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Producer peer process, or null if not launched. */
  private PeerProcess producerPeer;

  /** Consumer peer process, or null if not launched. */
  private PeerProcess consumerPeer;

  /** Sets up test environment before each test. */
  @Before
  public void setUp() {
    producerPeer = null;
    consumerPeer = null;
  }

  /**
   * Cleans up resources after each test.
   *
   * @throws Exception if cleanup fails
   */
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
  }

  /**
   * Verifies that a consumer peer with same source and WAL Kafka topic skips its own messages.
   *
   * <p>Scenario:
   *
   * <ol>
   *   <li>Producer peer writes messages to Kafka topic T via {@code --wal T}
   *   <li>Consumer peer reads from T and writes back to T via {@code --log T}
   *   <li>Consumer replays the producer's messages, generating hot-path WAL entries back to T
   *   <li>Source log reader skips those self-produced entries via offset publisher
   * </ol>
   *
   * <p>Asserts:
   *
   * <ul>
   *   <li>Consumer peer log contains "Jumping from offset" messages (offset skipping active)
   *   <li>Message count in the topic stays bounded (no infinite re-write loop)
   * </ul>
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void sameSourceAndWal_skipsOwnMessages() throws Exception {
    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String logName = "test-skip-kafka-" + generateId();

    // Step 1: Producer writes messages to Kafka topic
    UUID producerId = UUID.randomUUID();
    producerPeer =
        launchPeer(
            producerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--wal",
            logName,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);

    int producerExitCode = joinPeer(producerPeer, 15);
    assertEquals("Producer should exit successfully", 0, producerExitCode);
    producerPeer = null;

    // Verify producer wrote messages
    CliProcessResult originalPrint = runLogPrint("-d", palDirectory, logName);
    assertEquals("Expected successful print", 0, originalPrint.exitCode());
    long originalCount = originalPrint.stdout().lines().filter(l -> !l.isBlank()).count();
    assertThat("Expected messages in log", originalCount, greaterThan(0L));
    logger.info("Producer wrote {} messages to Kafka topic '{}'", originalCount, logName);

    // Step 2: Consumer reads from same topic (--log = same source and WAL, no main class)
    UUID consumerId = UUID.randomUUID();
    consumerPeer =
        launchPeer(
            consumerId,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "--log",
            logName,
            "-cp",
            getIttAppsClasspath());

    // Let consumer process messages and generate skip events
    Thread.sleep(8000);

    // Step 3: Verify offset-skip messages in consumer's peer log
    assertTrue(
        "Consumer peer log should contain offset-skip messages",
        consumerPeer.containsLogLine("Jumping from offset.*to"));

    // Stop consumer to flush WAL
    stopPeer(consumerPeer);
    consumerPeer = null;

    Thread.sleep(1000);

    // Step 4: Verify bounded message growth
    CliProcessResult afterPrint = runLogPrint("-d", palDirectory, logName);
    assertEquals("Expected successful print after consumer", 0, afterPrint.exitCode());
    long afterCount = afterPrint.stdout().lines().filter(l -> !l.isBlank()).count();

    logger.info("Message count after consumer: {} (original: {})", afterCount, originalCount);

    assertThat(
        "Message count should not have grown unboundedly (offset skipping active)",
        afterCount < originalCount * 3);
  }
}
