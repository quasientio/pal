package net.ittera.pal.tools.paldir;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.ittera.pal.common.znodes.LogInfo;
import net.ittera.pal.common.znodes.PeerInfo;
import net.ittera.pal.cxn.NoLogInfoNodeException;
import net.ittera.pal.cxn.PALDirectory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "rm", description = "Remove logs and peers")
public class Remove extends AbstractPALDirSubcommand {

  @Parameters(index = "0..*", description = "where arg = (log name|UUID OR peer name|UUID)")
  private java.util.List<String> argList;

  @Option(
      names = {"-P", "--delete-peers"},
      description = "delete peers")
  private boolean deletePeers = false;

  @Option(
      names = {"-L", "--delete-logs"},
      description = "delete logs")
  private boolean deleteLogs;

  @Option(
      names = {"-s", "--starting-with"},
      description = "delete all logs/peers starting with given name(s)")
  private boolean startingWith;

  @Option(
      names = {"--all", "-a"},
      description = "delete all")
  private boolean deleteAll;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  private static final UUID KAFKA_CLIENT_ID = UUID.randomUUID();
  private static final int DELETE_TOPIC_TIMEOUT_MS = 250;
  private static final int ADMIN_CLIENT_CLOSE_TIMEOUT_SECS = 2;
  private static final Logger logger = LoggerFactory.getLogger(Remove.class);
  private final Map<String, AdminClient> adminClientsPerServer = new HashMap<>();
  private int errors = 0;

  public Remove(PALDirectory palDirectory) {
    super(palDirectory);
  }

  @Override
  public void validateInput() {}

  private AdminClient getAdminClientForServers(String bootstrapServers) {
    if (!adminClientsPerServer.containsKey(bootstrapServers)) {
      Properties props = new Properties();
      props.setProperty("bootstrap.servers", bootstrapServers);
      props.setProperty("client.id", KAFKA_CLIENT_ID.toString());
      adminClientsPerServer.put(bootstrapServers, AdminClient.create(props));
    }
    return adminClientsPerServer.get(bootstrapServers);
  }

  private void removeFromKafka(LogInfo logInfo) {
    if (logger.isDebugEnabled()) {
      logger.debug("Attempting to remove log '{}' from kafka", logInfo.getName());
    }
    AdminClient adminClient = getAdminClientForServers(logInfo.getBootstrapServers());
    adminClient.deleteTopics(
        Collections.singleton(logInfo.getName()),
        new DeleteTopicsOptions().timeoutMs(DELETE_TOPIC_TIMEOUT_MS));
  }

  private void removeFromKafka(Set<LogInfo> logInfos, String bootstrapServers) {
    AdminClient adminClient = getAdminClientForServers(bootstrapServers);
    adminClient.deleteTopics(
        logInfos.stream().map(LogInfo::getName).collect(Collectors.toList()),
        new DeleteTopicsOptions().timeoutMs(DELETE_TOPIC_TIMEOUT_MS));
  }

  // remove from PAL directory
  private void deleteLog(LogInfo logInfo) {
    try {
      palDirectory.unregisterLog(logInfo.getName());
    } catch (Exception e) {
      logger.error("Error unregistering log '{}' from directory", logInfo.getName(), e);
      errors++;
    }

    // remove from kafka
    removeFromKafka(logInfo);

    logger.info("Log '{}' (UUID: {}) removed", logInfo.getName(), logInfo.getUuid());
  }

  private void deleteLogsWithUUID(UUID uuid) {
    final Set<LogInfo> matchingLogs;
    try {
      matchingLogs =
          palDirectory.getAllLogs().stream()
              .filter(l -> l.getUuid().equals(uuid))
              .collect(Collectors.toSet());
    } catch (Exception e) {
      logger.error("Error fetching logs from directory matching UUID '{}'", uuid, e);
      errors++;
      return;
    }
    // request confirmation
    if (matchingLogs.size() > 1) {
      String answer = null;
      while (answer == null || !(answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("n"))) {
        out.printf(
            "There are %d logs with UUID '%s'. Delete all? (y/n): ", matchingLogs.size(), uuid);
        try (Scanner scanner = new Scanner(System.in)) {
          answer = scanner.next();
        }
      }
      if (answer.equalsIgnoreCase("n")) {
        return;
      }
    }

    // delete
    for (LogInfo log : matchingLogs) {
      deleteLog(log);
    }
  }

  private void deleteAllLogs() {
    final Set<LogInfo> allLogs;
    try {
      allLogs = palDirectory.getAllLogs();
    } catch (Exception e) {
      errors++;
      return;
    }
    allLogs.forEach(this::deleteLog);
  }

  private void deletePeer(UUID peerUUID) {
    try {
      palDirectory.unregisterPeer(peerUUID);
    } catch (Exception e) {
      errors++;
    }
  }

  private void deletePeersNamed(String peerName) throws Exception {
    final Set<PeerInfo> matchingPeers =
        palDirectory.getAllPeers().stream()
            .filter(p -> peerName.equals(p.getName()))
            .collect(Collectors.toSet());

    // request confirmation
    if (matchingPeers.size() > 1) {
      String answer = null;
      while (answer == null || !(answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("n"))) {
        out.printf(
            "There are %d peers named '%s'. Delete all? (y/n): ", matchingPeers.size(), peerName);
        try (Scanner scanner = new Scanner(System.in)) {
          answer = scanner.next();
        }
      }
      if (answer.equalsIgnoreCase("n")) {
        return;
      }
    }

    // delete
    for (PeerInfo peer : matchingPeers) {
      try {
        palDirectory.unregisterPeer(peer.getUuid());
      } catch (Exception e) {
        logger.error("Error unregistering peer UUID '{}' from directory", peer.getUuid(), e);
        errors++;
      }
    }
  }

  private void deleteAllPeers() {
    try {
      palDirectory.unregisterAllPeers();
    } catch (Exception e) {
      errors++;
    }
  }

  @Override
  protected void closeResources() {
    adminClientsPerServer
        .values()
        .forEach(
            adminClient ->
                adminClient.close(
                    Duration.of(ADMIN_CLIENT_CLOSE_TIMEOUT_SECS, ChronoUnit.SECONDS)));
  }

  @Override
  protected int runCommand() throws Exception {
    if (deletePeers) {
      if (deleteAll) {
        deleteAllPeers();
      } else if (argList != null && !argList.isEmpty()) {
        for (String arg : argList) {
          // try to parse arg as UUID
          UUID peerUuid = null;
          try {
            peerUuid = UUID.fromString(arg);
          } catch (IllegalArgumentException e) {
            // fine, it's not a UUID
          }
          if (peerUuid != null) {
            deletePeer(peerUuid);
          } else {
            // if not a valid UUID we will consider it a name
            if (startingWith) {
              final Set<PeerInfo> allPeers = palDirectory.getAllPeers();
              allPeers.stream()
                  .filter(p -> p.getName() != null && p.getName().startsWith(arg))
                  .forEach(p -> deletePeer(p.getUuid()));
            } else {
              deletePeersNamed(arg);
            }
          }
        }
      }
    } else if (deleteLogs) {
      if (deleteAll) {
        deleteAllLogs();
      }
      // TODO group all logs with same bootstrap servers and remove in batch
      if (argList != null && !argList.isEmpty()) {
        for (String arg : argList) {
          // try to parse arg as UUID
          UUID logUuid = null;
          try {
            logUuid = UUID.fromString(arg);
          } catch (IllegalArgumentException e) {
            // fine, it's not a UUID
          }
          if (logUuid != null) {
            deleteLogsWithUUID(logUuid);
          } else {
            // if not a valid UUID we will consider it a name
            Set<LogInfo> logsToDelete;
            if (startingWith) {
              final Set<LogInfo> allLogs = palDirectory.getAllLogs();
              allLogs.stream().filter(l -> l.getName().startsWith(arg)).forEach(this::deleteLog);
            } else {
              final LogInfo log;
              try {
                log = palDirectory.getLogInfo(arg);
              } catch (NoLogInfoNodeException e) {
                logger.error("Cannot find log named '{}' in directory", arg, e);
                errors++;
                continue;
              }
              deleteLog(log);
            }
          }
        }
      }
    } else {
      out.println("Use -L/--logs to remove logs, or -P/--peers to remove peers.");
      return 1;
    }
    return errors;
  }
}
