package net.ittera.pal.tools.paldir;

import static java.lang.String.format;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.management.ObjectName;
import net.ittera.pal.common.util.Strings;
import net.ittera.pal.common.znodes.LogInfo;
import net.ittera.pal.common.znodes.PeerInfo;
import net.ittera.pal.cxn.JmxClient;
import net.ittera.pal.cxn.PALDirectory;
import org.apache.kafka.clients.admin.AdminClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "ls", description = "List logs and peers")
public class List extends AbstractPALDirSubcommand {

  @Option(
      names = {"-L", "--logs"},
      description = "list logs in directory")
  private boolean listLogs;

  @Option(
      names = {"-P", "--peers"},
      description = "list peers in directory")
  private boolean listPeers;

  @Option(
      names = {"-l", "--long"},
      description = "use long listing format")
  private boolean longListing;

  @Option(
      names = "--kafka-jmx-port",
      defaultValue = "10121",
      description = "JMX port used by kafka servers (default: ${DEFAULT-VALUE})")
  private Integer kafkaJmxPort;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  private static final Logger logger = LoggerFactory.getLogger(List.class);
  private static final UUID KAFKA_CLIENT_ID = UUID.randomUUID();
  private final Map<String, JmxClient> jmxClientsPerServer = new HashMap<>();
  private final Map<String, AdminClient> adminClientsPerServer = new HashMap<>();

  /** name uuid size start --> end ctime mtime */
  private static final String LOGS_LONG_FORMAT = "%-20s %36s  %10s %8s --> %-8s %12s";

  /** uuid name req pub jmx ctime mtime */
  private static final String PEERS_LONG_FORMAT = "%-36s %10s %20s %20s %20s %12s";

  public List(PALDirectory palDirectory) {
    super(palDirectory);
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

  private AdminClient getAdminClientForServers(String bootstrapServers) {
    if (!adminClientsPerServer.containsKey(bootstrapServers)) {
      Properties props = new Properties();
      props.setProperty("bootstrap.servers", bootstrapServers);
      props.setProperty("client.id", KAFKA_CLIENT_ID.toString());
      adminClientsPerServer.put(bootstrapServers, AdminClient.create(props));
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

  /*  TODO fillMbeanInfo with JmxClient is a temporary hack.
   *  All Log Info should be retrieved from PALDirectory, maintained by the running peers.
   *  This approach, however, has the advantage of getting live log info as maintained by kafka
   */
  public void fillMbeanInfo(LogInfo logInfo) {
    if (logger.isDebugEnabled()) {
      logger.debug("Attempting to get mbean info for log: {}", logInfo);
    }
    final JmxClient jmxCli = getJMXClientForServer(logInfo.getBootstrapServers());
    if (jmxCli == null) {
      logger.warn("No JMX client available for log '{}'", logInfo.getName());
    }
    try {
      // start offset
      String query =
          String.format("kafka.log:type=Log,name=LogStartOffset,topic=%s,*", logInfo.getName());
      Set<ObjectName> objNames = jmxCli.query(query);
      logInfo.setStartOffset((long) jmxCli.getValue(objNames.toArray(new ObjectName[] {})[0]));

      // end offset
      query = String.format("kafka.log:type=Log,name=LogEndOffset,topic=%s,*", logInfo.getName());
      objNames = jmxCli.query(query);
      logInfo.setEndOffset((long) jmxCli.getValue(objNames.toArray(new ObjectName[] {})[0]));

      // size (bytes)
      query = String.format("kafka.log:type=Log,name=Size,topic=%s,*", logInfo.getName());
      objNames = jmxCli.query(query);
      logInfo.setBytes((long) jmxCli.getValue(objNames.toArray(new ObjectName[] {})[0]));
    } catch (Exception e) {
      logger.error("Error retrieving log information from kafka via jmx", e);
      if (e instanceof ArrayIndexOutOfBoundsException) {
        logger.error(
            "Log named '{}' with uuid '{}' probably doesn't exist in kafka",
            logInfo.getName(),
            logInfo.getUuid(),
            e);
      }
    }
  }

  private JmxClient getJMXClientForServer(String server) {
    if (!jmxClientsPerServer.containsKey(server)) {
      String host = server.split(":")[0];
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
              logInfo.getName(),
              logInfo.getUuid(),
              logInfo.getHumanReadableByteSize() == null
                  ? "??"
                  : logInfo.getHumanReadableByteSize(),
              logInfo.getStartOffset() == null ? "?" : logInfo.getStartOffset(),
              logInfo.getEndOffset() == null ? "?" : logInfo.getEndOffset(),
              getFormattedDate(logInfo.getCTime()));
    } else {
      logInfoLine = format("%s", logInfo.getName());
    }
    out.println(logInfoLine);
  }

  private void print(PeerInfo peerInfo) {
    if (longListing) {
      out.println(
          format(
              PEERS_LONG_FORMAT,
              peerInfo.getUuid(),
              peerInfo.getName() == null ? "" : peerInfo.getName(),
              peerInfo.getReqAddress() == null
                  ? ""
                  : Strings.stringAfter(peerInfo.getReqAddress(), "tcp://"),
              peerInfo.getPubAddress() == null
                  ? ""
                  : Strings.stringAfter(peerInfo.getPubAddress(), "tcp://"),
              peerInfo.getJmxAddress() == null ? "" : peerInfo.getJmxAddress(),
              getFormattedDate(peerInfo.getCTime())));
    } else {
      out.println(format("%s", peerInfo.getUuid()));
    }
  }

  @Override
  protected void closeResources() throws IOException {
    // close jmx clients
    for (JmxClient jmxClient : jmxClientsPerServer.values()) {
      jmxClient.close();
    }
    // close kafka admin clients
    adminClientsPerServer.values().forEach(AdminClient::close);
  }

  @Override
  protected int runCommand() throws Exception {
    if (listLogs) {
      // get all logs in directory
      Set<LogInfo> logsInDirectory = palDirectory.getAllLogs();

      // get logs from all different kafka servers
      Set<LogInfo> allLogsInKafka = new HashSet<>();
      logsInDirectory.stream()
          .map(LogInfo::getBootstrapServers)
          .distinct()
          .forEach(
              s -> {
                allLogsInKafka.addAll(this.getLogsInKafkaServers(s));
              });

      // fill mbean info of all logs found in kafka (assumes JMX connection)
      logsInDirectory.stream().filter(allLogsInKafka::contains).forEach(this::fillMbeanInfo);
      if (longListing) {
        if (!logsInDirectory.isEmpty()) {
          // print total and header lines
          out.println(format("total %d", logsInDirectory.size()));
          out.println(
              (format(LOGS_LONG_FORMAT, "Name", "UUID", "Size", "Start", "End", "Created")));
        }
      }
      logsInDirectory.forEach(this::print);
    }

    if (listPeers) {
      Set<PeerInfo> peers = palDirectory.getAllPeers();
      if (longListing) {
        if (!peers.isEmpty()) {
          // print total and header lines
          out.println(format("total %d", peers.size()));
          out.println((format(PEERS_LONG_FORMAT, "UUID", "Name", "REQ", "PUB", "JMX", "Created")));
        }
      }
      peers.forEach(this::print);
    }
    return 0;
  }
}
