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

import static java.lang.String.format;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeLogDirsResult;
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.ReplicaInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Lists logs registered in the PAL directory.
 *
 * <p>This is the log-specific list command for the {@code pal log ls} pattern. It displays logs in
 * short or long format with optional sorting by size or creation time, reversal, and trimming
 * control. Supports both Kafka and Chronicle Queue logs.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal log ls
 *   pal log ls -l
 *   pal log ls -l -S
 *   pal log ls -l -c -r
 *   pal log ls --no-trim
 * </pre>
 */
@Command(
    name = "ls",
    description = "List logs",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "helpRequested is read by picocli framework via reflection")
public class LogList extends AbstractPalSubcommand {

  /** Logger instance. */
  private final Logger logger = LoggerFactory.getLogger(LogList.class);

  /** The parent command to which this subcommand belongs. */
  @ParentCommand PalCommand palCommand;

  /** Flag indicating whether to use long listing format. */
  @Option(
      names = {"-l", "--long"},
      description = "use long listing format")
  private boolean longListing;

  /** Flag indicating whether to sort logs by size in descending order. */
  @Option(
      names = {"-S", "--sort-by-size"},
      description = "sort logs by size, largest first")
  private boolean sortBySize;

  /** Flag indicating whether to sort by creation time, newest first. */
  @Option(
      names = {"-c", "--sort-by-ctime"},
      description = "sort by creation time, newest first")
  private boolean sortByCTime;

  /** Flag indicating whether to reverse the order while sorting. */
  @Option(
      names = {"-r", "--reverse"},
      description = "reverse order while sorting")
  private boolean reverseOrder;

  /** Flag indicating whether to disable trimming of long field values. */
  @Option(
      names = {"--no-trim"},
      description = "disable trimming of long field values")
  private boolean noTrimming;

  /** Flag indicating whether the help message is requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Maximum allowed length (in characters) for the log name representation. */
  private static final short MAX_LOG_NAME_LEN = 20;

  /** Maximum allowed length (in characters) for the log size representation. */
  private static final short MAX_LOG_SIZE_LEN = 10;

  /** Maximum allowed length (in characters) for the log index representation. */
  private static final short MAX_LOG_IDX_LEN = 8;

  /**
   * Format string for long listing of logs.
   *
   * <p>name uuid size start --> end CTime
   */
  private static final String LOGS_LONG_FORMAT =
      format(
          "%%-%ds %%-36s  %%-%ds %%-%ds --> %%-%ds %%-8s",
          MAX_LOG_NAME_LEN, MAX_LOG_SIZE_LEN, MAX_LOG_IDX_LEN, MAX_LOG_IDX_LEN);

  /** Helper for managing Kafka Admin client connections. */
  private KafkaAdminHelper kafkaAdminHelper;

  /** Constructs a new {@code LogList} instance. */
  public LogList() {}

  /** No validation needed; no mutual exclusion flags. */
  @Override
  public void validateInput() {}

  /**
   * Initializes the subcommand by setting up the directory connection and Kafka admin helper.
   *
   * @throws Exception if initialization fails
   */
  @Override
  protected void initialize() throws Exception {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    kafkaAdminHelper = new KafkaAdminHelper();
  }

  /**
   * Lists logs from the PAL directory.
   *
   * @return 0 on success, 1 on error
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    try {
      if (directoryConnectionProvider == null || directoryConnectionProvider.get().isEmpty()) {
        err.println(
            """
            Error: pal log ls requires a PAL directory.
            Specify with --directory/-d option or PAL_DIRECTORY environment variable.
            Example: pal log ls -d localhost:2379""");
        return 1;
      }
    } catch (RuntimeException e) {
      err.println(
          """
          Error: Cannot connect to PAL directory.
          Ensure etcd is running and accessible, then specify the directory:
            pal log ls -d localhost:2379
          Or set PAL_DIRECTORY environment variable.""");
      return 1;
    }

    Set<LogInfo> logsInDirectory = getPalDirectory().listAllLogs();

    // Separate Kafka and Chronicle logs
    Set<LogInfo> kafkaLogs =
        logsInDirectory.stream()
            .filter(log -> log.getLogType() == LogType.KAFKA)
            .collect(Collectors.toSet());

    Set<LogInfo> chronicleLogs =
        logsInDirectory.stream()
            .filter(log -> log.getLogType() == LogType.CHRONICLE)
            .collect(Collectors.toSet());

    // Process Kafka logs
    Set<LogInfo> existingKafkaLogs = new HashSet<>();
    if (!kafkaLogs.isEmpty()) {
      Set<LogInfo> logsInKafka = new HashSet<>();
      kafkaLogs.stream()
          .map(LogInfo::getBootstrapServers)
          .distinct()
          .forEach(s -> logsInKafka.addAll(getLogsInKafkaServers(s)));

      kafkaLogs.retainAll(logsInKafka);
      existingKafkaLogs.addAll(kafkaLogs);

      fillLogInfosWithOffsets(existingKafkaLogs);
      existingKafkaLogs.forEach(this::fillLogInfoSize);
    }

    // Process Chronicle logs
    Set<LogInfo> existingChronicleLogs = new HashSet<>();
    if (!chronicleLogs.isEmpty()) {
      chronicleLogs.stream()
          .filter(this::chronicleLogExists)
          .forEach(
              log -> {
                existingChronicleLogs.add(log);
                fillChronicleLogOffsets(log);
                fillChronicleLogSize(log);
              });
    }

    // Combine all existing logs
    Set<LogInfo> allExistingLogs = new HashSet<>();
    allExistingLogs.addAll(existingKafkaLogs);
    allExistingLogs.addAll(existingChronicleLogs);

    if (longListing) {
      out.printf("total %d%n", allExistingLogs.size());
      if (!allExistingLogs.isEmpty()) {
        out.printf(LOGS_LONG_FORMAT + "%n", "Name", "UUID", "Size", "Start", "End", "Created");
      }
    }
    printLogs(allExistingLogs);
    return 0;
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

  /**
   * Retrieves the set of logs present in the specified Kafka servers.
   *
   * @param bootstrapServers the Kafka bootstrap servers
   * @return a set of {@link LogInfo} representing the logs in the servers, or empty set on error
   */
  private Set<LogInfo> getLogsInKafkaServers(String bootstrapServers) {
    try {
      return kafkaAdminHelper
          .getAdminClientForServers(bootstrapServers)
          .listTopics()
          .names()
          .get()
          .stream()
          .map(name -> new LogInfo(name, bootstrapServers))
          .collect(Collectors.toSet());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted while listing logs in kafka", e);
    } catch (ExecutionException e) {
      logger.error("Error listing logs in kafka", e);
    }
    return Collections.emptySet();
  }

  /**
   * Checks if the Chronicle log exists on disk.
   *
   * @param logInfo the LogInfo representing the Chronicle log
   * @return true if the Chronicle queue exists, false otherwise
   */
  private boolean chronicleLogExists(LogInfo logInfo) {
    return ChronicleLogUtil.queueExists(Path.of(logInfo.getName()));
  }

  /**
   * Fills in the offsets (indices) for a Chronicle log.
   *
   * @param logInfo the LogInfo to populate with offset information
   */
  private void fillChronicleLogOffsets(LogInfo logInfo) {
    Path queuePath = Path.of(logInfo.getName());
    ChronicleLogUtil.QueueIndexInfo indexInfo = ChronicleLogUtil.getQueueIndexInfo(queuePath);

    if (indexInfo != null) {
      logInfo.setStartOffset(indexInfo.getFirstIndex());
      logInfo.setEndOffset(indexInfo.getLastIndex());
    }
  }

  /**
   * Fills in the size information for a Chronicle log.
   *
   * @param logInfo the LogInfo to populate with size information
   */
  private void fillChronicleLogSize(LogInfo logInfo) {
    Path queuePath = Path.of(logInfo.getName());
    long sizeInBytes = ChronicleLogUtil.getQueueSizeInBytes(queuePath);
    logInfo.setBytes(sizeInBytes);
  }

  /**
   * Populates the start and end offsets for each log in the provided set.
   *
   * @param logInfos the set of {@link LogInfo} objects to be updated with offsets
   */
  void fillLogInfosWithOffsets(Set<LogInfo> logInfos) {
    Map<String, Set<LogInfo>> logInfosByServer =
        logInfos.stream()
            .collect(Collectors.groupingBy(LogInfo::getBootstrapServers, Collectors.toSet()));

    logInfosByServer.forEach(
        (server, logInfosSet) -> {
          try {
            Admin adminClient = kafkaAdminHelper.getAdminClientForServers(server);
            Map<String, Long> startOffsets =
                KafkaOffsetFetcher.getStartOffsets(
                    logInfosSet.stream().map(LogInfo::getName).collect(Collectors.toSet()),
                    adminClient);
            Map<String, Long> endOffsets =
                KafkaOffsetFetcher.getEndOffsets(
                    logInfosSet.stream().map(LogInfo::getName).collect(Collectors.toSet()),
                    adminClient);

            for (LogInfo logInfo : logInfosSet) {
              logInfo.setStartOffset(startOffsets.get(logInfo.getName()));
              Long endOffset = endOffsets.get(logInfo.getName());
              if (endOffset != null) {
                if (endOffset > 0) {
                  logInfo.setEndOffset(endOffset - 1);
                } else {
                  logInfo.setEndOffset(0);
                }
              }
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while setting offset properties for LogInfos", e);
          } catch (ExecutionException e) {
            logger.error("Error setting offset properties for LogInfos", e);
          }
        });
  }

  /**
   * Retrieves the on-disk size (in bytes) of a topic/partition from the broker's
   * <em>DescribeLogDirs</em> RPC and stores it in the supplied {@link LogInfo}.
   *
   * @param logInfo the {@link LogInfo} to update
   */
  void fillLogInfoSize(LogInfo logInfo) {
    final int brokerId = 1;

    if (logger.isDebugEnabled()) {
      logger.debug("Fetching log size via DescribeLogDirs for {}", logInfo);
    }

    Admin adminClient;
    try {
      adminClient = kafkaAdminHelper.getAdminClientForServers(logInfo.getBootstrapServers());
      if (adminClient == null) {
        logger.error("Cannot create AdminClient for {}", logInfo.getBootstrapServers());
        return;
      }

      DescribeLogDirsResult result =
          adminClient.describeLogDirs(Collections.singletonList(brokerId));

      Map<Integer, Map<String, LogDirDescription>> brokers = result.allDescriptions().get();

      Map<String, LogDirDescription> logDirs = brokers.get(brokerId);
      if (logDirs == null) {
        logger.error("Broker {} absent in DescribeLogDirs response", brokerId);
        return;
      }

      for (LogDirDescription dir : logDirs.values()) {
        if (dir.error() != null) {
          logger.warn("Log dir {} on broker {} is offline.", dir, brokerId, dir.error());
          continue;
        }

        for (Map.Entry<TopicPartition, ReplicaInfo> e : dir.replicaInfos().entrySet()) {
          TopicPartition tp = e.getKey();

          if (tp.topic().equals(logInfo.getName()) && tp.partition() == 0) {
            long sizeBytes = e.getValue().size();
            logInfo.setBytes(sizeBytes);
            logger.debug("Set size for {} to {} bytes", logInfo, sizeBytes);
            return;
          }
        }
      }

      logger.warn("Topic {} partition 0 not present on broker {}", logInfo.getName(), brokerId);

    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted while waiting for describeLogDirs", ie);
    } catch (ExecutionException ee) {
      logger.error("describeLogDirs failed", ee.getCause());
    }
  }

  /**
   * Prints the information of a log in the appropriate format.
   *
   * @param logInfo the {@link LogInfo} object to print
   */
  private void print(LogInfo logInfo) {
    final String logInfoLine;
    String logName = logInfo.getName();
    if (longListing) {
      logInfoLine =
          format(
              LOGS_LONG_FORMAT,
              optionallyTrim(logName, MAX_LOG_NAME_LEN),
              logInfo.getUuid(),
              logInfo.getHumanReadableByteSize() == null
                  ? "??"
                  : optionallyTrim(logInfo.getHumanReadableByteSize(), MAX_LOG_SIZE_LEN),
              logInfo.getStartOffset() == null
                  ? "?"
                  : optionallyTrim(String.valueOf(logInfo.getStartOffset()), MAX_LOG_IDX_LEN),
              logInfo.getEndOffset() == null
                  ? "?"
                  : optionallyTrim(String.valueOf(logInfo.getEndOffset()), MAX_LOG_IDX_LEN),
              getFormattedDate(logInfo.getCTime()));
    } else {
      logInfoLine = format("%s", logName);
    }
    out.println(logInfoLine);
  }

  /**
   * Prints the set of logs in the specified order.
   *
   * @param logs the set of {@link LogInfo} objects to print
   */
  private void printLogs(Set<LogInfo> logs) {
    final Comparator<LogInfo> comparator;

    if (sortBySize) {
      final Comparator<LogInfo> logSizeComparator = Comparator.comparing(LogInfo::getBytes);
      comparator = reverseOrder ? logSizeComparator : logSizeComparator.reversed();
    } else if (sortByCTime) {
      final Comparator<LogInfo> cTimeComparator = Comparator.comparing(LogInfo::getCTime);
      comparator = reverseOrder ? cTimeComparator : cTimeComparator.reversed();
    } else {
      final Comparator<LogInfo> logNaturalComparator = Comparator.naturalOrder();
      comparator = reverseOrder ? logNaturalComparator.reversed() : logNaturalComparator;
    }

    Stream<LogInfo> sortedLogs = logs.stream().sorted(comparator);
    sortedLogs.forEach(this::print);
  }

  /**
   * Optionally trims the given string to the specified maximum length, appending ".." if trimmed.
   *
   * @param astring the string to trim
   * @param maxLength the maximum allowed length
   * @return the trimmed string if necessary, otherwise the original string
   */
  private String optionallyTrim(String astring, int maxLength) {
    return ListFormatUtils.optionallyTrim(astring, maxLength, noTrimming);
  }

  /**
   * Formats the given date and time.
   *
   * @param dateTime the date and time to format
   * @return a formatted date string in "MMM dd HH:mm" format
   */
  private static String getFormattedDate(OffsetDateTime dateTime) {
    return ListFormatUtils.getFormattedDate(dateTime);
  }

  /** Utility class for fetching Kafka offsets. */
  static class KafkaOffsetFetcher {

    /**
     * Retrieves the 'earliest' offsets for the specified log names.
     *
     * @param logNames the set of log names
     * @param adminClient the Kafka admin client
     * @return a map of log names to their earliest offsets
     * @throws ExecutionException if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted
     */
    @SuppressWarnings("PMD.NoFullyQualifiedTypes")
    public static Map<String, Long> getStartOffsets(Set<String> logNames, Admin adminClient)
        throws ExecutionException, InterruptedException {
      java.util.List<TopicPartition> topicPartitions =
          logNames.stream().map(logName -> new TopicPartition(logName, 0)).toList();
      Map<TopicPartition, org.apache.kafka.clients.admin.OffsetSpec> request =
          topicPartitions.stream()
              .collect(
                  Collectors.toMap(
                      tp -> tp, tp -> org.apache.kafka.clients.admin.OffsetSpec.earliest()));
      Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo>
          result = adminClient.listOffsets(request).all().get();
      return result.entrySet().stream()
          .collect(
              Collectors.toMap(
                  entry -> entry.getKey().topic(), entry -> entry.getValue().offset()));
    }

    /**
     * Retrieves the 'latest' offsets for the specified log names.
     *
     * @param logNames the set of log names
     * @param adminClient the Kafka admin client
     * @return a map of log names to their latest offsets
     * @throws ExecutionException if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted
     */
    @SuppressWarnings("PMD.NoFullyQualifiedTypes")
    public static Map<String, Long> getEndOffsets(Set<String> logNames, Admin adminClient)
        throws ExecutionException, InterruptedException {
      java.util.List<TopicPartition> topicPartitions =
          logNames.stream().map(logName -> new TopicPartition(logName, 0)).toList();
      Map<TopicPartition, org.apache.kafka.clients.admin.OffsetSpec> request =
          topicPartitions.stream()
              .collect(
                  Collectors.toMap(
                      tp -> tp, tp -> org.apache.kafka.clients.admin.OffsetSpec.latest()));
      Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo>
          result = adminClient.listOffsets(request).all().get();
      return result.entrySet().stream()
          .collect(
              Collectors.toMap(
                  entry -> entry.getKey().topic(), entry -> entry.getValue().offset()));
    }
  }
}
