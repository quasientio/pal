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
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DeleteTopicsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Removes logs from the PAL directory and their backing stores (Kafka topics or Chronicle queues).
 *
 * <p>This is the log-specific remove command for the {@code pal log rm} pattern. It supports
 * removal by name, UUID, or {@code file:}-prefixed Chronicle path as positional arguments, prefix
 * matching with {@code -s/--starting-with}, bulk deletion with {@code --all}, and optional Kafka
 * bootstrap servers for direct mode operation.
 *
 * <p>Uses {@link LogResolver} for log identifier resolution and {@link KafkaAdminHelper} for Kafka
 * topic deletion.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal log rm my-log --force
 *   pal log rm file:/tmp/wal
 *   pal log rm -k localhost:29092 my-topic
 *   pal log rm -s wal- --force
 *   pal log rm --all --force
 * </pre>
 */
@Command(
    name = "rm",
    description = "Remove logs from directory and backing stores",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "helpRequested is read by picocli framework via reflection")
public class LogRemove extends AbstractPalSubcommand {

  /** Logger instance for logging operations. */
  private final Logger logger = LoggerFactory.getLogger(LogRemove.class);

  /** Reference to the parent PalCommand for directory connection string propagation. */
  @ParentCommand PalCommand palCommand;

  /** The command specification provided by picocli for accessing usage information. */
  @Spec CommandSpec spec;

  /** Positional arguments specifying the names, UUIDs, or {@code file:} paths of logs to remove. */
  @Parameters(
      index = "0..*",
      paramLabel = "LOG",
      description = "Log names, UUIDs, or file: paths to remove")
  List<String> logIdentifiers;

  /**
   * Kafka bootstrap servers for direct access to Kafka logs without PAL_DIRECTORY.
   *
   * <p>When provided, allows deleting Kafka logs directly without connecting to the PAL directory.
   * Takes precedence over the PAL_KAFKA_SERVERS environment variable.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      paramLabel = "host:port[,host:port...]",
      description = "Kafka bootstrap servers (for direct Kafka access without -d)")
  String kafkaServers;

  /** Flag indicating that only logs starting with the specified prefix should be deleted. */
  @Option(
      names = {"-s", "--starting-with"},
      description = "delete logs starting with given prefix")
  boolean startingWith;

  /** Flag indicating that all logs should be deleted. */
  @Option(
      names = {"--all", "-a"},
      description = "delete all logs")
  boolean deleteAll;

  /** Flag indicating whether to skip confirmation prompts. */
  @Option(
      names = {"--force", "-f"},
      description = "skip confirmation prompts")
  boolean force;

  /** Flag indicating whether the help message was requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Timeout in milliseconds for deleting Kafka topics. */
  private static final int DELETE_TOPIC_TIMEOUT_MS = 250;

  /** Counter for the number of errors encountered during command execution. */
  private int errors = 0;

  /** Helper for managing cached Kafka Admin clients. */
  private KafkaAdminHelper kafkaAdminHelper;

  /** Resolver for log identifiers. */
  private LogResolver logResolver;

  /** Constructs a new {@code LogRemove} instance. */
  public LogRemove() {}

  /** Validates input. (Currently no validation is performed.) */
  @Override
  public void validateInput() {}

  /**
   * Initializes the directory connection and shared utilities.
   *
   * @throws Exception if initialization fails
   */
  @Override
  protected void initialize() throws Exception {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    if (kafkaAdminHelper == null) {
      kafkaAdminHelper = new KafkaAdminHelper();
    }
    String effectiveKafkaServers = kafkaServers != null ? kafkaServers : getKafkaServers();
    if (logResolver == null) {
      logResolver = new LogResolver(directoryConnectionProvider, effectiveKafkaServers);
    }
  }

  /**
   * Deletes the Kafka topic associated with the given LogInfo.
   *
   * @param logInfo the LogInfo representing the log to remove from Kafka
   */
  private void removeFromKafka(LogInfo logInfo) {
    if (logger.isDebugEnabled()) {
      logger.debug("Attempting to remove log '{}' from kafka", logInfo.getName());
    }
    Admin adminClient = kafkaAdminHelper.getAdminClientForServers(logInfo.getBootstrapServers());
    adminClient.deleteTopics(
        Collections.singleton(logInfo.getName()),
        new DeleteTopicsOptions().timeoutMs(DELETE_TOPIC_TIMEOUT_MS));
  }

  /**
   * Deletes a Chronicle queue by removing all files in the queue directory.
   *
   * @param logInfo the LogInfo representing the Chronicle log to delete
   */
  private void removeChronicleLog(LogInfo logInfo) {
    if (logger.isDebugEnabled()) {
      logger.debug("Attempting to remove Chronicle log '{}'", logInfo.getName());
    }

    Path queuePath = Path.of(logInfo.getName());

    if (!ChronicleLogUtil.queueExists(queuePath)) {
      logger.warn("Chronicle log '{}' does not exist at path: {}", logInfo.getName(), queuePath);
      return;
    }

    boolean deleted = ChronicleLogUtil.deleteQueue(queuePath);
    if (!deleted) {
      logger.error("Failed to delete Chronicle log '{}' at path: {}", logInfo.getName(), queuePath);
      errors++;
    } else {
      logger.debug("Successfully deleted Chronicle log files at: {}", queuePath);
    }
  }

  /**
   * Deletes the specified log from the PAL directory (if available) and removes its backing store
   * (Kafka topic or Chronicle queue).
   *
   * <p>Supports direct mode: if PAL_DIRECTORY is not available, only deletes the physical log
   * storage.
   *
   * @param logInfo the LogInfo representing the log to delete
   */
  private void deleteLog(LogInfo logInfo) {
    // Try to unregister from PAL directory if available
    try {
      if (directoryConnectionProvider != null) {
        var palDirOpt = directoryConnectionProvider.get();
        if (palDirOpt.isPresent()) {
          palDirOpt.get().deleteLog(logInfo.getName());
          logger.debug("Unregistered log '{}' from PAL directory", logInfo.getName());
        } else {
          logger.debug("PAL directory not available, skipping directory unregistration");
        }
      }
    } catch (RuntimeException | ExecutionException | InterruptedException e) {
      logger.error("Error unregistering log '{}' from directory", logInfo.getName(), e);
      errors++;
    }

    // Remove from backing store based on log type
    if (logInfo.getLogType() == LogType.CHRONICLE) {
      removeChronicleLog(logInfo);
    } else {
      removeFromKafka(logInfo);
    }

    logger.info("Log '{}' removed", logInfo.getName());
  }

  /**
   * Deletes all logs with the specified UUID.
   *
   * <p>If multiple logs are found and {@code --force} is not specified, the user is prompted for
   * confirmation.
   *
   * @param uuid the UUID of the logs to delete
   */
  private void deleteLogsWithUuid(UUID uuid) {
    final Set<LogInfo> matchingLogs;
    try {
      matchingLogs =
          getPalDirectory().listAllLogs().stream()
              .filter(l -> l.getUuid().equals(uuid))
              .collect(Collectors.toSet());
    } catch (RuntimeException | ExecutionException | InterruptedException e) {
      logger.error("Error fetching logs from directory matching UUID '{}'", uuid, e);
      errors++;
      return;
    }

    if (matchingLogs.size() > 1 && !force) {
      String answer = null;
      while (answer == null || !(answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("n"))) {
        out.printf(
            "There are %d logs with UUID '%s'. Delete all? (y/n): ", matchingLogs.size(), uuid);
        try (Scanner scanner = new Scanner(System.in, Charset.defaultCharset())) {
          answer = scanner.next();
        }
      }
      if (answer.equalsIgnoreCase("n")) {
        return;
      }
    }

    for (LogInfo log : matchingLogs) {
      deleteLog(log);
    }
  }

  /**
   * Deletes all logs registered in the PAL directory.
   *
   * <p>Lists all logs and deletes each one individually, including both directory unregistration
   * and backing store removal.
   */
  private void deleteAllLogs() {
    final Set<LogInfo> allLogs;
    try {
      allLogs = getPalDirectory().listAllLogs();
    } catch (RuntimeException | ExecutionException | InterruptedException e) {
      errors++;
      return;
    }
    allLogs.forEach(this::deleteLog);
  }

  /**
   * Closes all Kafka Admin clients and the directory connection.
   *
   * @throws IOException if an I/O error occurs during resource closure
   */
  @Override
  protected void closeResources() throws IOException {
    if (kafkaAdminHelper != null) {
      kafkaAdminHelper.closeResources();
    }
    super.closeResources();
  }

  /**
   * Executes the log removal based on the specified options and positional arguments.
   *
   * @return the number of errors encountered during execution
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    if (!deleteAll && (logIdentifiers == null || logIdentifiers.isEmpty())) {
      spec.commandLine().usage(out);
      return 1;
    }

    if (deleteAll) {
      deleteAllLogs();
    } else {
      for (String arg : logIdentifiers) {
        UUID logUuid = null;
        try {
          logUuid = UUID.fromString(arg);
        } catch (IllegalArgumentException e) {
          // not a UUID, treat as name
        }
        if (logUuid != null) {
          deleteLogsWithUuid(logUuid);
        } else {
          if (startingWith) {
            try {
              final Set<LogInfo> allLogs = getPalDirectory().listAllLogs();
              allLogs.stream().filter(l -> l.getName().startsWith(arg)).forEach(this::deleteLog);
            } catch (RuntimeException e) {
              logger.error(
                  "Cannot list logs for prefix matching: "
                      + "PAL_DIRECTORY required for --starting-with");
              errors++;
            }
          } else {
            final LogInfo log = logResolver.resolveLogInfo(arg);
            if (log == null) {
              logger.error("Cannot resolve log: '{}'", arg);
              errors++;
            } else {
              deleteLog(log);
            }
          }
        }
      }
    }
    return errors;
  }
}
