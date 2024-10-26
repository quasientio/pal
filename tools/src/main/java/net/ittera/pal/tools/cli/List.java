/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.tools.cli;

import static java.lang.String.format;

import com.google.common.base.Splitter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
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
import javax.management.ObjectName;
import net.ittera.pal.common.cli.PalCommand;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.util.Strings;
import net.ittera.pal.cxn.JmxClient;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "ls",
    customSynopsis = "pal ls [OPTIONS]%n",
    description = "List peers and logs in directory",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n",
    commandListHeading = "%nCommands:%n")
public class List extends AbstractPalSubcommand {

  @ParentCommand PalCommand palCommand;

  @Option(
      names = {"-L", "--logs"},
      description = "list logs")
  private boolean listLogs;

  @Option(
      names = {"-P", "--peers"},
      description = "list peers")
  private boolean listPeers;

  @Option(
      names = {"-l", "--long"},
      description = "use long listing format")
  private boolean longListing;

  @Option(
      names = {"-S", "--sort-by-size"},
      description = "sort logs by size, largest first")
  private boolean sortBySize;

  @Option(
      names = {"-c", "--sort-by-ctime"},
      description = "sort by creation/up time, newest first")
  private boolean sortByCTime;

  @Option(
      names = {"-r", "--reverse"},
      description = "reverse order while sorting")
  private boolean reverseOrder;

  @Option(
      names = "--kafka-jmx-port",
      paramLabel = "port",
      defaultValue = "10121",
      description = "JMX port used by kafka servers (default: ${DEFAULT-VALUE})")
  private Integer kafkaJmxPort;

  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  private final Logger logger = LoggerFactory.getLogger(List.class);
  private static final UUID KAFKA_CLIENT_ID = UUID.randomUUID();
  private final Map<String, JmxClient> jmxClientsPerServer = new HashMap<>();
  private final Map<String, Admin> adminClientsPerServer = new HashMap<>();

  /* Column widths for variable-length fields.
   NOTE: Adjust these values, not the format strings below.
  */
  private static final short MAX_LOG_NAME_LEN = 20;
  private static final short MAX_LOG_SIZE_LEN = 10;
  private static final short MAX_LOG_IDX_LEN = 8;
  private static final short MAX_PEER_NAME_LEN = 15;
  private static final short MAX_ENDPOINT_LEN = 20;

  // name uuid size start --> end CTime
  private static final String LOGS_LONG_FORMAT =
      format(
          "%%-%ds %%-36s  %%-%ds %%-%ds --> %%-%ds %%-8s",
          MAX_LOG_NAME_LEN, MAX_LOG_SIZE_LEN, MAX_LOG_IDX_LEN, MAX_LOG_IDX_LEN);

  // uuid name rpc jsonrpc pub jmx CTime
  private static final String PEERS_LONG_FORMAT =
      format(
          "%%-36s %%-%ds %%-%ds %%-%ds %%-%ds %%-%ds %%-8s",
          MAX_PEER_NAME_LEN,
          MAX_ENDPOINT_LEN,
          MAX_ENDPOINT_LEN,
          MAX_ENDPOINT_LEN,
          MAX_ENDPOINT_LEN);

  @Override
  protected void initialize() {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    if (directoryConnectionProvider.get().isEmpty()) {
      err.println(
          "A PalDirectory is required. Run with -d (--dir) or set the ENV variable PAL_DIRECTORY.");
      System.exit(1);
    }
  }

  @Override
  public void validateInput() {
    if (!(listLogs || listPeers)) {
      throw new RuntimeException("Use -L (--logs) to list logs, or -P (--peers) to list peers.");
    }
    if (listLogs && listPeers) {
      throw new RuntimeException("Use either -L (--logs) or -P (--peers), but not both.");
    }
  }

  private Admin getAdminClientForServers(String bootstrapServers) {
    if (!adminClientsPerServer.containsKey(bootstrapServers)) {
      Properties props = new Properties();
      props.setProperty("bootstrap.servers", bootstrapServers);
      props.setProperty("client.id", KAFKA_CLIENT_ID.toString());
      adminClientsPerServer.put(bootstrapServers, Admin.create(props));
    }
    return adminClientsPerServer.get(bootstrapServers);
  }

  private Set<LogInfo> getLogsInKafkaServers(String bootstrapServers) {
    Set<LogInfo> logsInServers = null;
    try {
      logsInServers =
          getAdminClientForServers(bootstrapServers).listTopics().names().get().stream()
              .map(LogInfo::new)
              .collect(Collectors.toSet());
    } catch (InterruptedException | ExecutionException e) {
      logger.error("Error listing logs in kafka", e);
    }
    return logsInServers;
  }

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
              logInfo.setEndOffset(endOffsets.get(logInfo.getName()));
            }
          } catch (Exception e) {
            logger.error("Error setting offset properties for List of LogInfo's", e);
          }
        });
  }

  /*  TODO fillMbeanInfo with JmxClient is a temporary hack.
   *  All Log Info should be retrieved from PalDirectory, maintained by the running peers.
   *  This approach, however, has the advantage of getting live log info as maintained by kafka
   */
  public void fillLogInfoSize(LogInfo logInfo) {
    if (logger.isDebugEnabled()) {
      logger.debug("Attempting to get mbean info for log: {}", logInfo);
    }
    // use a JmxClient to fetch the byte size
    String query = String.format("kafka.log:type=Log,name=Size,topic=%s,*", logInfo.getName());
    JmxClient jmxCli = getJmxClientForServer(logInfo.getBootstrapServers());
    if (jmxCli == null) {
      logger.error("Cannot fetch bytes for logInfo: {} - no JMX client", logInfo);
      return;
    }
    try {
      Set<ObjectName> objNames = jmxCli.query(query);
      logInfo.setBytes((long) jmxCli.getValue(objNames.toArray(new ObjectName[] {})[0]));
    } catch (Exception e) {
      logger.error("Error setting bytes property for logInfo: {}", logInfo, e);
    }
  }

  private JmxClient getJmxClientForServer(String server) {
    if (!jmxClientsPerServer.containsKey(server)) {
      String host = Splitter.on(':').splitToList(server).get(0);
      JmxClient jmxClient = null;
      try {
        jmxClient = new JmxClient(host, kafkaJmxPort);
      } catch (IOException e) {
        logger.error("Error connecting jmx client to host", e);
      }
      if (jmxClient != null) {
        jmxClientsPerServer.put(server, jmxClient);
      }
    }
    return jmxClientsPerServer.get(server);
  }

  private static String getFormattedUptime(OffsetDateTime startDateTime) {
    final OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    return DurationFormatUtils.formatDuration(
        Duration.between(startDateTime, now).toMillis(), "H:mm:ss");
  }

  private static String trimTo(String astring, int maxLength) {
    if (astring.length() <= maxLength) {
      return astring;
    }
    return astring.substring(0, maxLength - 2) + "..";
  }

  private static String getFormattedDate(OffsetDateTime dateTime) {
    return format(
        "%s %02d %02d:%02d",
        dateTime.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault()),
        dateTime.getDayOfMonth(),
        dateTime.getHour(),
        dateTime.getMinute());
  }

  private void print(LogInfo logInfo) {
    final String logInfoLine;
    if (longListing) {
      logInfoLine =
          format(
              LOGS_LONG_FORMAT,
              trimTo(logInfo.getName(), MAX_LOG_NAME_LEN),
              logInfo.getUuid(),
              logInfo.getHumanReadableByteSize() == null
                  ? "??"
                  : trimTo(logInfo.getHumanReadableByteSize(), MAX_LOG_SIZE_LEN),
              logInfo.getStartOffset() == null
                  ? "?"
                  : trimTo(String.valueOf(logInfo.getStartOffset()), MAX_LOG_IDX_LEN),
              logInfo.getEndOffset() == null
                  ? "?"
                  : trimTo(String.valueOf(logInfo.getEndOffset()), MAX_LOG_IDX_LEN),
              getFormattedDate(logInfo.getCTime()));
    } else {
      logInfoLine = format("%s", logInfo.getName());
    }
    out.println(logInfoLine);
  }

  private void print(PeerInfo peerInfo) {
    if (longListing) {
      out.printf(
          PEERS_LONG_FORMAT + "%n",
          peerInfo.getUuid(),
          peerInfo.getName() == null ? "" : trimTo(peerInfo.getName(), MAX_PEER_NAME_LEN),
          peerInfo.getRpcAddress() == null
              ? ""
              : trimTo(Strings.stringAfter(peerInfo.getRpcAddress(), "tcp://"), MAX_ENDPOINT_LEN),
          peerInfo.getJsonrpcAddress() == null
              ? ""
              : trimTo(
                  Strings.stringAfter(peerInfo.getJsonrpcAddress(), "ws://"), MAX_ENDPOINT_LEN),
          peerInfo.getPubAddress() == null
              ? ""
              : trimTo(Strings.stringAfter(peerInfo.getPubAddress(), "tcp://"), MAX_ENDPOINT_LEN),
          peerInfo.getJmxAddress() == null
              ? ""
              : trimTo(peerInfo.getJmxAddress(), MAX_ENDPOINT_LEN),
          getFormattedUptime(peerInfo.getCTime()));
    } else {
      out.printf("%s%n", peerInfo.getUuid());
    }
  }

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

  private void printPeers(Set<PeerInfo> peers) {
    final Comparator<PeerInfo> comparator;
    if (sortByCTime) {
      final Comparator<PeerInfo> cTimeComparator = Comparator.comparing(PeerInfo::getCTime);
      // Comparator<OffsetDateTime> orders old -> new, so that is actually our reverse
      comparator = reverseOrder ? cTimeComparator : cTimeComparator.reversed();
    } else {
      final Comparator<PeerInfo> peerNameComparator =
          (o1, o2) -> // no need to check for null PeerInfo's here since it can't happen
          Objects.compare(
                  o1.getName(), o2.getName(), Comparator.nullsLast(Comparator.naturalOrder()));
      comparator = reverseOrder ? peerNameComparator.reversed() : peerNameComparator;
    }

    Stream<PeerInfo> sortedPeers = peers.stream().sorted(comparator);
    sortedPeers.forEach(this::print);
  }

  @Override
  protected void closeResources() throws IOException {
    // close jmx clients
    for (JmxClient jmxClient : jmxClientsPerServer.values()) {
      jmxClient.close();
    }
    // close kafka admin clients
    adminClientsPerServer.values().forEach(Admin::close);

    super.closeResources();
  }

  @Override
  protected int runCommand() throws Exception {
    if (listLogs) {
      // get all logs in directory
      Set<LogInfo> logsInDirectory = getPalDirectory().getAllLogs();

      // get logs from all different kafka servers
      Set<LogInfo> logsInKafka = new HashSet<>();
      logsInDirectory.stream()
          .map(LogInfo::getBootstrapServers)
          .distinct()
          .forEach(s -> logsInKafka.addAll(this.getLogsInKafkaServers(s)));

      // filter out logs that are not in kafka
      logsInDirectory.retainAll(logsInKafka);

      // fill offsets of all logs using kafka admin client
      fillLogInfosWithOffsets(logsInDirectory);

      // fill byte size of all logs found in kafka using MBeans (assumes JMX connection)
      logsInDirectory.forEach(this::fillLogInfoSize);

      if (longListing) {
        out.printf("total %d%n", logsInDirectory.size());
        if (!logsInDirectory.isEmpty()) {
          out.printf(LOGS_LONG_FORMAT + "%n", "Name", "UUID", "Size", "Start", "End", "Created");
        }
      }
      printLogs(logsInDirectory);
    }

    if (listPeers) {
      Set<PeerInfo> peers = getPalDirectory().getAllPeers();
      logger.debug("{} peers found in directory", peers.size());
      if (longListing) {
        out.printf("total %d%n", peers.size());
        if (!peers.isEmpty()) {
          out.printf(
              PEERS_LONG_FORMAT + "%n", "UUID", "Name", "RPC", "JSON-RPC", "PUB", "JMX", "Uptime");
        }
      }
      printPeers(peers);
    }
    return 0;
  }

  static class KafkaOffsetFetcher {

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
