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
package io.quasient.pal.tools.cli;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Removes stale log entries from the PAL directory.
 *
 * <p>A stale log entry is one whose backing store no longer exists: a Kafka topic that has been
 * deleted, or a Chronicle Queue directory that has been removed from disk. The directory entry
 * persists because log registrations in etcd have no lease or TTL.
 *
 * <p>For Kafka logs, the command connects to the bootstrap servers recorded in each log entry and
 * checks whether the topic still exists. If a Kafka cluster is unreachable, those logs are skipped
 * with a warning. Chronicle logs are checked by verifying the queue directory exists on the local
 * filesystem.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal log prune
 *   pal log prune -d localhost:2379
 * </pre>
 */
@Command(
    name = "prune",
    description = "Remove stale log entries from directory",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "helpRequested is read by picocli framework via reflection")
public class LogPrune extends AbstractPalSubcommand {

  /** Logger instance for logging operations. */
  private final Logger logger = LoggerFactory.getLogger(LogPrune.class);

  /** Reference to the parent PalCommand for directory connection string propagation. */
  @ParentCommand PalCommand palCommand;

  /** Flag indicating whether the help message was requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Counter for the number of errors encountered during command execution. */
  private int errors = 0;

  /** Helper for managing Kafka Admin client connections. */
  private KafkaAdminHelper kafkaAdminHelper;

  /** Constructs a new {@code LogPrune} instance. */
  public LogPrune() {}

  /** No validation needed; this command takes no positional arguments. */
  @Override
  public void validateInput() {}

  /**
   * Initializes the directory connection and Kafka admin helper.
   *
   * @throws Exception if initialization fails
   */
  @Override
  protected void initialize() throws Exception {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    kafkaAdminHelper = new KafkaAdminHelper();
  }

  /**
   * Removes stale log entries from the PAL directory.
   *
   * <p>Lists all registered logs, checks each for existence in its backing store (Kafka or
   * Chronicle), and deletes directory entries for those whose backing store no longer exists. Kafka
   * logs are grouped by bootstrap server to minimize admin client connections. If a Kafka cluster
   * is unreachable, those logs are skipped with a warning.
   *
   * @return the number of errors encountered during execution
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    Set<LogInfo> logsInDirectory = getPalDirectory().listAllLogs();

    Set<LogInfo> kafkaLogs =
        logsInDirectory.stream()
            .filter(log -> log.getLogType() == LogType.KAFKA)
            .collect(Collectors.toSet());

    Set<LogInfo> chronicleLogs =
        logsInDirectory.stream()
            .filter(log -> log.getLogType() == LogType.CHRONICLE)
            .collect(Collectors.toSet());

    int pruned = 0;

    // Process Kafka logs grouped by bootstrap server
    if (!kafkaLogs.isEmpty()) {
      Map<String, Set<LogInfo>> kafkaLogsByServer =
          kafkaLogs.stream()
              .collect(Collectors.groupingBy(LogInfo::getBootstrapServers, Collectors.toSet()));

      for (Map.Entry<String, Set<LogInfo>> entry : kafkaLogsByServer.entrySet()) {
        String servers = entry.getKey();
        Set<LogInfo> logsForServer = entry.getValue();

        Set<String> existingTopics;
        try {
          existingTopics =
              kafkaAdminHelper.getAdminClientForServers(servers).listTopics().names().get();
        } catch (ExecutionException | RuntimeException e) {
          err.printf(
              "Warning: Cannot connect to Kafka at %s. Skipping %d Kafka log(s).%n"
                  + "Ensure the Kafka cluster is accessible and retry.%n",
              servers, logsForServer.size());
          logger.debug("Kafka unreachable at {}", servers, e);
          continue;
        }

        for (LogInfo log : logsForServer) {
          if (!existingTopics.contains(log.getName())) {
            pruned += pruneLog(log);
          }
        }
      }
    }

    // Process Chronicle logs
    for (LogInfo log : chronicleLogs) {
      if (!ChronicleLogUtil.queueExists(Path.of(log.getName()))) {
        pruned += pruneLog(log);
      }
    }

    if (pruned == 0 && errors == 0) {
      out.println("No stale logs found");
    } else if (pruned > 0) {
      out.printf("Pruned %d stale log(s)%n", pruned);
    }

    return errors;
  }

  /**
   * Deletes a single stale log entry from the directory.
   *
   * @param log the log entry to prune
   * @return 1 if pruned successfully, 0 if skipped or failed
   */
  private int pruneLog(LogInfo log) {
    try {
      getPalDirectory().deleteLog(log.getUuid());
      out.printf("Pruned %s (%s)%n", log.getName(), log.getUuid());
      return 1;
    } catch (IllegalArgumentException e) {
      // Log is still referenced by a peer — skip it
      logger.warn("Skipping log '{}': {}", log.getName(), e.getMessage());
      return 0;
    } catch (RuntimeException | ExecutionException | InterruptedException e) {
      logger.error("Error pruning log '{}'", log.getName(), e);
      errors++;
      return 0;
    }
  }

  /**
   * Closes all resources associated with the subcommand.
   *
   * @throws IOException if an I/O error occurs while closing resources
   */
  @Override
  protected void closeResources() throws IOException {
    if (kafkaAdminHelper != null) {
      kafkaAdminHelper.closeResources();
    }
    super.closeResources();
  }
}
