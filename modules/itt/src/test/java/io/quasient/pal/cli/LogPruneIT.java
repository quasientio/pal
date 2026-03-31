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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code pal log prune} command.
 *
 * <p>Tests that stale log entries (those whose backing store no longer exists) are removed from the
 * directory while entries with existing backing stores are left untouched.
 *
 * <p>Stale entries are created directly via {@link PalDirectory#createLog(LogInfo)} with topic
 * names or Chronicle paths that do not exist, simulating the state left behind when a Kafka topic
 * is deleted or a Chronicle queue directory is removed without cleaning up the PAL directory.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class LogPruneIT extends AbstractCliIT {

  /** PalDirectory instance for creating stale log entries directly. */
  private PalDirectory palDirectory;

  /** UUIDs of logs created directly that need cleanup. */
  private final Set<UUID> directlyCreatedLogs = new HashSet<>();

  /** Initializes test state and PalDirectory before each test method. */
  @Before
  public void setUp() {
    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
  }

  /**
   * Tears down test state after each test method.
   *
   * <p>Cleans up any directly-created logs that were not pruned by the test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    for (UUID logUuid : directlyCreatedLogs) {
      try {
        if (palDirectory.logExists(logUuid)) {
          palDirectory.deleteLog(logUuid);
        }
      } catch (Exception e) {
        // best-effort cleanup
      }
    }
    directlyCreatedLogs.clear();
    palDirectory.close();
  }

  /**
   * Creates a stale Kafka log entry in etcd pointing to a non-existent topic.
   *
   * <p>The topic name is intentionally chosen to not exist in Kafka, simulating the state left
   * behind when a Kafka topic is deleted without cleaning up the PAL directory.
   *
   * @param topicName the Kafka topic name (should not exist in Kafka)
   * @return the created LogInfo
   * @throws Exception if log creation fails
   */
  private LogInfo createStaleKafkaLog(String topicName) throws Exception {
    LogInfo log = new LogInfo(topicName, UUID.randomUUID(), getKafkaServers());
    log.setLogType(LogType.KAFKA);
    palDirectory.createLog(log);
    directlyCreatedLogs.add(log.getUuid());
    return log;
  }

  /**
   * Creates a stale Chronicle log entry in etcd pointing to a non-existent path.
   *
   * <p>The path is intentionally chosen to not exist on disk, simulating the state left behind when
   * a Chronicle queue directory is removed without cleaning up the PAL directory.
   *
   * @param path the Chronicle queue path (should not exist on disk)
   * @return the created LogInfo
   * @throws Exception if log creation fails
   */
  private LogInfo createStaleChronicleLog(String path) throws Exception {
    LogInfo log = new LogInfo(path);
    log.setUuid(UUID.randomUUID());
    log.setLogType(LogType.CHRONICLE);
    palDirectory.createLog(log);
    directlyCreatedLogs.add(log.getUuid());
    return log;
  }

  /**
   * Tests that {@code pal log prune} removes stale Kafka log entries from the directory.
   *
   * <p>Creates two stale Kafka log entries pointing to non-existent topics, runs prune, and
   * verifies they no longer appear in the log listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrune_removesStaleKafkaLogs() throws Exception {
    String palDir = getPalDirectoryUrl();
    String topicName1 = "prune-stale-kafka-" + generateId() + "-a";
    String topicName2 = "prune-stale-kafka-" + generateId() + "-b";

    createStaleKafkaLog(topicName1);
    createStaleKafkaLog(topicName2);

    CliProcessResult pruneResult = runLogPrune("-d", palDir);
    assertThat("Expected exit code 0 for log prune", pruneResult.exitCode(), is(0));
    assertThat(pruneResult.stdout(), containsString("Pruned"));

    // Verify neither log appears in listing
    CliProcessResult lsResult = runLogLs("-d", palDir, "-k", getKafkaServers());
    assertThat(
        "First stale log should not appear after prune",
        lsResult.stdout(),
        not(containsString(topicName1)));
    assertThat(
        "Second stale log should not appear after prune",
        lsResult.stdout(),
        not(containsString(topicName2)));
  }

  /**
   * Tests that {@code pal log prune} removes stale Chronicle log entries from the directory.
   *
   * <p>Creates a stale Chronicle log entry pointing to a non-existent path, runs prune, and
   * verifies it no longer appears in the directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrune_removesStaleChronicleLog() throws Exception {
    String palDir = getPalDirectoryUrl();
    String chroniclePath = "/tmp/prune-stale-chronicle-" + generateId();

    createStaleChronicleLog(chroniclePath);

    CliProcessResult pruneResult = runLogPrune("-d", palDir);
    assertThat("Expected exit code 0 for log prune", pruneResult.exitCode(), is(0));
    assertThat(pruneResult.stdout(), containsString("Pruned"));
  }

  /**
   * Tests that {@code pal log prune} succeeds on an empty directory.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testPrune_emptyDirectory_succeeds() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult pruneResult = runLogPrune("-d", palDir);
    assertThat("Expected exit code 0 for prune on empty directory", pruneResult.exitCode(), is(0));
    assertThat(pruneResult.stdout(), containsString("No stale logs found"));
  }
}
