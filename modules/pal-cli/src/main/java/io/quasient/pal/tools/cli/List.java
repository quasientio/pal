/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static java.lang.String.format;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.lang.intercept.Interceptable;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.util.Strings;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeLogDirsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.LogDirDescription;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.ReplicaInfo;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Represents the 'List' subcommand in the PAL CLI tool.
 *
 * <p>This command is used to list peers and logs within the directory. It supports various options
 * for formatting and sorting the output.
 */
@Command(
    name = "ls",
    customSynopsis = "pal ls [OPTIONS]%n",
    description = "List peers and logs in directory",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n",
    commandListHeading = "%nCommands:%n")
@SuppressFBWarnings(
    value = {"DM_EXIT", "URF_UNREAD_FIELD"},
    justification = "CLI command - System.exit intentional; helpRequested field set by picocli")
public class List extends AbstractPalSubcommand {

  /** The parent command to which this subcommand belongs. */
  @ParentCommand PalCommand palCommand;

  /** Flag indicating whether to list logs. */
  @Option(
      names = {"-L", "--logs"},
      description = "list logs")
  private boolean listLogs;

  /** Flag indicating whether to list peers. */
  @Option(
      names = {"-P", "--peers"},
      description = "list peers")
  private boolean listPeers;

  /** Flag indicating whether to list intercepts. */
  @Option(
      names = {"-I", "--intercepts"},
      description = "list intercepts")
  private boolean listIntercepts;

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

  /** Flag indicating whether to sort by creation or uptime, newest first. */
  @Option(
      names = {"-c", "--sort-by-ctime"},
      description = "sort by creation/up time, newest first")
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

  /** Logger instance. */
  private final Logger logger = LoggerFactory.getLogger(List.class);

  /** Unique identifier for Kafka clients. */
  private static final UUID KAFKA_CLIENT_ID = UUID.randomUUID();

  /** Mapping of server addresses to their respective Kafka admin clients. */
  private final Map<String, Admin> adminClientsPerServer = new HashMap<>();

  /*---------------------------------------------------------------------------------------------------
   * Column widths for variable-length fields. Adjust these values, not the format strings that follow.
   *--------------------------------------------------------------------------------------------------*/

  /** Maximum allowed length (in characters) for the log name representation. */
  private static final short MAX_LOG_NAME_LEN = 20;

  /** Maximum allowed length (in characters) for the log size representation. */
  private static final short MAX_LOG_SIZE_LEN = 10;

  /** Maximum allowed length (in characters) for the log index representation. */
  private static final short MAX_LOG_IDX_LEN = 8;

  /** Maximum allowed length (in characters) for peer names. */
  private static final short MAX_PEER_NAME_LEN = 15;

  /** Maximum allowed length (in characters) for endpoint addresses. */
  private static final short MAX_ENDPOINT_LEN = 20;

  /** Maximum allowed length (in characters) for intercept class names. */
  private static final short MAX_INTERCEPT_CLASS_LEN = 30;

  /** Maximum allowed length (in characters) for intercept target (method/field) names. */
  private static final short MAX_INTERCEPT_TARGET_LEN = 25;

  /** Maximum allowed length (in characters) for intercept callback display. */
  private static final short MAX_INTERCEPT_CALLBACK_LEN = 30;

  /**
   * Format string for long listing of logs.
   *
   * <p>name uuid size start --> end CTime
   */
  private static final String LOGS_LONG_FORMAT =
      format(
          "%%-%ds %%-36s  %%-%ds %%-%ds --> %%-%ds %%-8s",
          MAX_LOG_NAME_LEN, MAX_LOG_SIZE_LEN, MAX_LOG_IDX_LEN, MAX_LOG_IDX_LEN);

  /**
   * Format string for long listing of peers.
   *
   * <p>uuid name rpc jsonrpc pub jmx CTime
   */
  private static final String PEERS_LONG_FORMAT =
      format(
          "%%-36s %%-%ds %%-%ds %%-%ds %%-%ds %%-%ds %%-8s",
          MAX_PEER_NAME_LEN,
          MAX_ENDPOINT_LEN,
          MAX_ENDPOINT_LEN,
          MAX_ENDPOINT_LEN,
          MAX_ENDPOINT_LEN);

  /**
   * Format string for long listing of intercepts.
   *
   * <p>uuid peer type class target callback CTime
   */
  private static final String INTERCEPTS_LONG_FORMAT =
      format(
          "%%-36s %%-36s %%-12s %%-%ds %%-%ds %%-%ds %%-12s",
          MAX_INTERCEPT_CLASS_LEN, MAX_INTERCEPT_TARGET_LEN, MAX_INTERCEPT_CALLBACK_LEN);

  /**
   * Initializes the subcommand by setting up the directory connection.
   *
   * <p>Establishes a connection to the PAL directory using the provided connection string. Exits
   * the program if the connection string is not provided.
   */
  @Override
  protected void initialize() {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    if (directoryConnectionProvider.get().isEmpty()) {
      err.println(
          "A PalDirectory is required. Run with -d (--dir) or set the ENV variable PAL_DIRECTORY.");
      System.exit(1);
    }
  }

  /**
   * Validates the input options for the subcommand.
   *
   * <p>When neither -L nor -P is specified, both logs and peers will be listed. When both flags are
   * specified, this is an error.
   *
   * @throws RuntimeException if the input options are invalid
   */
  @Override
  public void validateInput() {
    // Count how many filter flags are set
    int flagCount = (listLogs ? 1 : 0) + (listPeers ? 1 : 0) + (listIntercepts ? 1 : 0);
    if (flagCount > 1) {
      throw new RuntimeException(
          "Use only one of -L (--logs), -P (--peers), or -I (--intercepts).");
    }
  }

  /**
   * Retrieves or creates a Kafka Admin client for the specified bootstrap servers.
   *
   * @param bootstrapServers the Kafka bootstrap servers
   * @return the Kafka Admin client associated with the bootstrap servers
   */
  private Admin getAdminClientForServers(String bootstrapServers) {
    if (!adminClientsPerServer.containsKey(bootstrapServers)) {
      Properties props = new Properties();
      props.setProperty("bootstrap.servers", bootstrapServers);
      props.setProperty("client.id", KAFKA_CLIENT_ID.toString());
      props.setProperty("request.timeout.ms", "5000");
      props.setProperty("default.api.timeout.ms", "10000");
      adminClientsPerServer.put(bootstrapServers, Admin.create(props));
    }
    return adminClientsPerServer.get(bootstrapServers);
  }

  /**
   * Retrieves the set of logs present in the specified Kafka servers.
   *
   * @param bootstrapServers the Kafka bootstrap servers
   * @return a set of {@link LogInfo} representing the logs in the servers, or empty set on error
   */
  private Set<LogInfo> getLogsInKafkaServers(String bootstrapServers) {
    try {
      return getAdminClientForServers(bootstrapServers).listTopics().names().get().stream()
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
   * Checks if the Chronicle log exists on disk and returns true if it does.
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
  public void fillLogInfosWithOffsets(Set<LogInfo> logInfos) {
    // Group logInfos by their bootstrap servers
    Map<String, Set<LogInfo>> logInfosByServer =
        logInfos.stream()
            .collect(Collectors.groupingBy(LogInfo::getBootstrapServers, Collectors.toSet()));

    // Fetch offsets for each group of logInfos
    logInfosByServer.forEach(
        (server, logInfosSet) -> {
          try {
            Admin adminClient = getAdminClientForServers(server);
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
              // Kafka returns end offset as last+1, but we want to display the last message offset
              Long endOffset = endOffsets.get(logInfo.getName());
              if (endOffset != null) {
                if (endOffset > 0) {
                  logInfo.setEndOffset(endOffset - 1);
                } else {
                  logInfo.setEndOffset(0);
                }
              }
              // If endOffset is null, leave it unset
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
   * Retrieves the on-disk size (in bytes) of a topic/partition from the broker’s
   * <em>DescribeLogDirs</em> RPC and stores it in the supplied {@link LogInfo}.
   *
   * <p><strong>Prerequisites</strong>
   *
   * <ul>
   *   <li>You are running Kafka&nbsp;2.4 + (the Admin API must expose <code>describeLogDirs</code>
   *       ).
   *   <li>The cluster is running in KRaft mode and the single broker/controller has <code>
   *       KAFKA_NODE_ID=1</code>.
   *   <li>Each topic has a single partition (<code>partition&nbsp;0</code>).
   * </ul>
   *
   * @param logInfo the {@link LogInfo} to update
   */
  public void fillLogInfoSize(LogInfo logInfo) {
    final Logger logger = LoggerFactory.getLogger(getClass());
    final int BROKER_ID = 1; // KAFKA_NODE_ID=1

    if (logger.isDebugEnabled()) {
      logger.debug("Fetching log size via DescribeLogDirs for {}", logInfo);
    }

    Admin adminClient;
    try {
      adminClient = getAdminClientForServers(logInfo.getBootstrapServers());
      if (adminClient == null) {
        logger.error("Cannot create AdminClient for {}", logInfo.getBootstrapServers());
        return;
      }

      DescribeLogDirsResult result =
          adminClient.describeLogDirs(Collections.singletonList(BROKER_ID));

      // New, non-deprecated accessor:
      Map<Integer, Map<String, LogDirDescription>> brokers =
          result.allDescriptions().get(); // Map<brokerId, logDir -> desc>

      Map<String, LogDirDescription> logDirs = brokers.get(BROKER_ID);
      if (logDirs == null) {
        logger.error("Broker {} absent in DescribeLogDirs response", BROKER_ID);
        return;
      }

      for (LogDirDescription dir : logDirs.values()) {
        if (dir.error() != null) { // log directory offline?
          logger.warn("Log dir {} on broker {} is offline.", dir, BROKER_ID, dir.error());
          continue;
        }

        // dir.replicaInfos() -> Map<TopicPartition, ReplicaInfo>
        for (Map.Entry<TopicPartition, ReplicaInfo> e : dir.replicaInfos().entrySet()) {
          TopicPartition tp = e.getKey();

          if (tp.topic().equals(logInfo.getName()) && tp.partition() == 0) {
            long sizeBytes = e.getValue().size();
            logInfo.setBytes(sizeBytes);
            logger.debug("Set size for {} to {} bytes", logInfo, sizeBytes);
            return; // done
          }
        }
      }

      logger.warn("Topic {} partition 0 not present on broker {}", logInfo.getName(), BROKER_ID);

    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      logger.error("Interrupted while waiting for describeLogDirs", ie);
    } catch (ExecutionException ee) {
      logger.error("describeLogDirs failed", ee.getCause());
    }
  }

  /**
   * Formats the uptime based on the start time.
   *
   * @param startDateTime the start time of the peer
   * @return a formatted uptime string in "H:mm:ss" format
   */
  private static String getFormattedUptime(OffsetDateTime startDateTime) {
    final OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    return DurationFormatUtils.formatDuration(
        Duration.between(startDateTime, now).toMillis(), "H:mm:ss");
  }

  /**
   * Optionally trims the given string to the specified maximum length, appending ".." if trimmed.
   * If the --no-trimming flag is set, returns the string unchanged.
   *
   * @param astring the string to trim
   * @param maxLength the maximum allowed length
   * @return the trimmed string if necessary, otherwise the original string
   */
  private String optionallyTrim(String astring, int maxLength) {
    if (noTrimming || astring.length() <= maxLength) {
      return astring;
    }
    return astring.substring(0, maxLength - 2) + "..";
  }

  /**
   * Formats the given date and time.
   *
   * @param dateTime the date and time to format
   * @return a formatted date string in "MMM dd HH:mm" format
   */
  private static String getFormattedDate(OffsetDateTime dateTime) {
    return format(
        "%s %02d %02d:%02d",
        dateTime.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()),
        dateTime.getDayOfMonth(),
        dateTime.getHour(),
        dateTime.getMinute());
  }

  /**
   * Prints the information of a log in the appropriate format.
   *
   * @param logInfo the {@link LogInfo} object to print
   */
  private void print(LogInfo logInfo) {
    final String logInfoLine;
    String logName;
    if (logInfo.getLogType() == LogType.CHRONICLE) {
      Path fileName = Paths.get(logInfo.getName()).getFileName();
      logName = fileName != null ? fileName.toString() : logInfo.getName();
    } else {
      logName = logInfo.getName();
    }
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
   * Prints the information of a peer in the appropriate format.
   *
   * @param peerInfo the {@link PeerInfo} object to print
   */
  private void print(PeerInfo peerInfo) {
    if (longListing) {
      out.printf(
          PEERS_LONG_FORMAT + "%n",
          peerInfo.getUuid(),
          peerInfo.getName() == null ? "" : optionallyTrim(peerInfo.getName(), MAX_PEER_NAME_LEN),
          peerInfo.getZmqRpcAddress() == null
              ? ""
              : optionallyTrim(
                  Strings.stringAfter(peerInfo.getZmqRpcAddress(), "tcp://"), MAX_ENDPOINT_LEN),
          peerInfo.getJsonrpcAddress() == null
              ? ""
              : optionallyTrim(
                  Strings.stringAfter(peerInfo.getJsonrpcAddress(), "ws://"), MAX_ENDPOINT_LEN),
          peerInfo.getPubAddress() == null
              ? ""
              : optionallyTrim(
                  Strings.stringAfter(peerInfo.getPubAddress(), "tcp://"), MAX_ENDPOINT_LEN),
          peerInfo.getJmxAddress() == null
              ? ""
              : optionallyTrim(peerInfo.getJmxAddress(), MAX_ENDPOINT_LEN),
          getFormattedUptime(peerInfo.getCTime()));
    } else {
      // Short format: show name if present, otherwise UUID
      String displayValue =
          (peerInfo.getName() != null && !peerInfo.getName().isEmpty())
              ? peerInfo.getName()
              : peerInfo.getUuid().toString();
      out.printf("%s%n", displayValue);
    }
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
      // Comparator<long> orders small -> large, so that is actually our reverse
      comparator = reverseOrder ? logSizeComparator : logSizeComparator.reversed();
    } else if (sortByCTime) {
      final Comparator<LogInfo> cTimeComparator = Comparator.comparing(LogInfo::getCTime);
      // Comparator<OffsetDateTime> orders old -> new, so that is actually our reverse
      comparator = reverseOrder ? cTimeComparator : cTimeComparator.reversed();
    } else {
      final Comparator<LogInfo> logNaturalComparator = Comparator.naturalOrder();
      comparator = reverseOrder ? logNaturalComparator.reversed() : logNaturalComparator;
    }

    Stream<LogInfo> sortedLogs = logs.stream().sorted(comparator);
    sortedLogs.forEach(this::print);
  }

  /**
   * Prints the set of peers in the specified order.
   *
   * @param peers the set of {@link PeerInfo} objects to print
   */
  private void printPeers(Set<PeerInfo> peers) {
    final Comparator<PeerInfo> comparator;
    if (sortByCTime) {
      final Comparator<PeerInfo> cTimeComparator = Comparator.comparing(PeerInfo::getCTime);
      // Comparator<OffsetDateTime> orders old -> new, so that is actually our reverse
      comparator = reverseOrder ? cTimeComparator : cTimeComparator.reversed();
    } else {
      final Comparator<PeerInfo> peerNameComparator =
          (o1, o2) ->
              Objects.compare(
                  o1.getName(), o2.getName(), Comparator.nullsLast(Comparator.naturalOrder()));
      comparator = reverseOrder ? peerNameComparator.reversed() : peerNameComparator;
    }

    Stream<PeerInfo> sortedPeers = peers.stream().sorted(comparator);
    sortedPeers.forEach(this::print);
  }

  /**
   * Formats the interceptable target for display.
   *
   * <p>For method calls, shows the method name and parameter types. For field operations, shows the
   * field name and operation type (GET/PUT).
   *
   * @param interceptable the interceptable to format
   * @return a human-readable target description
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  static String formatInterceptTarget(Interceptable interceptable) {
    if (interceptable instanceof InterceptableMethodCall methodCall) {
      java.util.List<String> paramTypes = methodCall.getParameterTypes();
      if (paramTypes.isEmpty()) {
        return methodCall.getName() + "()";
      }
      return methodCall.getName()
          + "("
          + paramTypes.stream()
              .map(t -> t.contains(".") ? t.substring(t.lastIndexOf('.') + 1) : t)
              .collect(Collectors.joining(", "))
          + ")";
    } else if (interceptable instanceof InterceptableFieldOp fieldOp) {
      return fieldOp.getName() + " [" + fieldOp.getFieldOpType() + "]";
    }
    return interceptable.getName();
  }

  /**
   * Prints the information of an intercept in the appropriate format.
   *
   * @param intercept the {@link InterceptRequest} object to print
   */
  private void print(InterceptRequest<?> intercept) {
    if (longListing) {
      String callbackDisplay =
          intercept.getCallbackClass().contains(".")
              ? intercept
                      .getCallbackClass()
                      .substring(intercept.getCallbackClass().lastIndexOf('.') + 1)
                  + "."
                  + intercept.getCallbackMethod()
              : intercept.getCallbackClass() + "." + intercept.getCallbackMethod();
      String classDisplay =
          intercept.getClazz().contains(".")
              ? intercept.getClazz().substring(intercept.getClazz().lastIndexOf('.') + 1)
              : intercept.getClazz();

      out.printf(
          INTERCEPTS_LONG_FORMAT + "%n",
          intercept.getUuid(),
          intercept.getPeer(),
          intercept.getType(),
          optionallyTrim(classDisplay, MAX_INTERCEPT_CLASS_LEN),
          optionallyTrim(
              formatInterceptTarget(intercept.getInterceptable()), MAX_INTERCEPT_TARGET_LEN),
          optionallyTrim(callbackDisplay, MAX_INTERCEPT_CALLBACK_LEN),
          getFormattedDate(intercept.getCTime()));
    } else {
      out.printf("%s%n", intercept.getUuid());
    }
  }

  /**
   * Prints the set of intercepts in the specified order.
   *
   * @param intercepts the set of {@link InterceptRequest} objects to print
   */
  private void printIntercepts(Set<InterceptRequest<?>> intercepts) {
    final Comparator<InterceptRequest<?>> comparator;
    if (sortByCTime) {
      final Comparator<InterceptRequest<?>> cTimeComparator =
          Comparator.comparing(InterceptRequest::getCTime);
      comparator = reverseOrder ? cTimeComparator : cTimeComparator.reversed();
    } else {
      final Comparator<InterceptRequest<?>> classComparator =
          Comparator.comparing(InterceptRequest::getClazz);
      comparator = reverseOrder ? classComparator.reversed() : classComparator;
    }

    intercepts.stream().sorted(comparator).forEach(this::print);
  }

  /**
   * Closes all resources associated with the subcommand.
   *
   * <p>Closes Kafka admin clients before delegating to the superclass method.
   *
   * @throws IOException if an I/O error occurs while closing resources
   */
  @Override
  protected void closeResources() throws IOException {
    // close kafka admin clients
    adminClientsPerServer.values().forEach(Admin::close);

    super.closeResources();
  }

  /**
   * Executes the command based on the provided options.
   *
   * <p>If listing logs is requested, it retrieves logs from the directory and Kafka servers, fills
   * in their offsets and sizes, and prints them. If listing peers is requested, it retrieves peers
   * from the directory and prints them. If neither flag is specified, both logs and peers are
   * listed.
   *
   * @return the exit code of the command execution
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    // Verify PAL_DIRECTORY is available (required for listing)
    try {
      if (directoryConnectionProvider == null || directoryConnectionProvider.get().isEmpty()) {
        err.println(
            """
                        Error: pal ls requires a PAL directory to list registered resources.
                        Specify with --directory/-d option or PAL_DIRECTORY environment variable.
                        Example: pal ls -d localhost:2379""");
        return 1;
      }
    } catch (RuntimeException e) {
      err.println(
          """
                      Error: Cannot connect to PAL directory.
                      Ensure etcd is running and accessible, then specify the directory:
                        pal ls -d localhost:2379
                      Or set PAL_DIRECTORY environment variable.""");
      return 1;
    }

    // When no filter flag is specified, list logs and peers (not intercepts)
    boolean noFilterSpecified = !listLogs && !listPeers && !listIntercepts;
    boolean shouldListLogs = listLogs || noFilterSpecified;
    boolean shouldListPeers = listPeers || noFilterSpecified;
    boolean shouldListIntercepts = listIntercepts;

    if (shouldListLogs) {
      // get all logs in directory
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
        // get logs from all different kafka servers
        Set<LogInfo> logsInKafka = new HashSet<>();
        kafkaLogs.stream()
            .map(LogInfo::getBootstrapServers)
            .distinct()
            .forEach(s -> logsInKafka.addAll(this.getLogsInKafkaServers(s)));

        // filter out logs that are not in kafka
        kafkaLogs.retainAll(logsInKafka);
        existingKafkaLogs.addAll(kafkaLogs);

        // fill offsets of all Kafka logs using kafka admin client
        fillLogInfosWithOffsets(existingKafkaLogs);

        // fill byte size of all Kafka logs using kafka admin client
        existingKafkaLogs.forEach(this::fillLogInfoSize);
      }

      // Process Chronicle logs
      Set<LogInfo> existingChronicleLogs = new HashSet<>();
      if (!chronicleLogs.isEmpty()) {
        // filter out Chronicle logs that don't exist on disk
        chronicleLogs.stream()
            .filter(this::chronicleLogExists)
            .forEach(
                log -> {
                  existingChronicleLogs.add(log);
                  // fill offsets and size for Chronicle logs
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
    }

    if (shouldListPeers) {
      Set<PeerInfo> peers = getPalDirectory().listPeers();
      logger.debug("{} peers found in directory", peers.size());
      if (longListing) {
        out.printf("total %d%n", peers.size());
        if (!peers.isEmpty()) {
          out.printf(
              PEERS_LONG_FORMAT + "%n",
              "UUID",
              "Name",
              "ZMQ-RPC",
              "JSON-RPC",
              "PUB",
              "JMX",
              "Uptime");
        }
      }
      printPeers(peers);
    }

    if (shouldListIntercepts) {
      Set<InterceptRequest<?>> intercepts = getPalDirectory().listAllIntercepts();
      logger.debug("{} intercepts found in directory", intercepts.size());
      if (longListing) {
        out.printf("total %d%n", intercepts.size());
        if (!intercepts.isEmpty()) {
          out.printf(
              INTERCEPTS_LONG_FORMAT + "%n",
              "UUID",
              "Peer",
              "Type",
              "Class",
              "Target",
              "Callback",
              "Created");
        }
      }
      printIntercepts(intercepts);
    }
    return 0;
  }

  /** Utility class for fetching Kafka offsets. */
  static class KafkaOffsetFetcher {

    /**
     * Retrieves the 'earliest' offsets for the specified log names using the provided admin client.
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
      Map<TopicPartition, OffsetSpec> request =
          topicPartitions.stream().collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.earliest()));
      Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> result =
          adminClient.listOffsets(request).all().get();
      return result.entrySet().stream()
          .collect(
              Collectors.toMap(
                  entry -> entry.getKey().topic(), entry -> entry.getValue().offset()));
    }

    /**
     * Retrieves the 'latest' offsets for the specified log names using the provided admin client.
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
      Map<TopicPartition, OffsetSpec> request =
          topicPartitions.stream().collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
      Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> result =
          adminClient.listOffsets(request).all().get();
      return result.entrySet().stream()
          .collect(
              Collectors.toMap(
                  entry -> entry.getKey().topic(), entry -> entry.getValue().offset()));
    }
  }
}
