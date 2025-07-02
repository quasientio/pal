/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.tools.cli;

import com.quasient.pal.common.cli.PalCommand;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import java.io.IOException;
import java.nio.charset.Charset;
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
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * A command-line subcommand that removes peers or logs from the PAL directory. It interfaces with
 * Kafka to delete corresponding topics and manages the unregistration of peers or logs from the
 * directory.
 */
@Command(
    name = "rm",
    customSynopsis = "pal rm [OPTIONS] [-L LOG, ...] [-P PEER, ...]%n",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n",
    description = "Remove peers or logs from directory")
public class Remove extends AbstractPalSubcommand {

  /** List of positional arguments specifying the names or UUIDs of peers or logs to remove. */
  @Parameters(index = "0..*", hidden = true)
  @SuppressWarnings("unused")
  private java.util.List<String> argList;

  /** Reference to the parent PalCommand. */
  @ParentCommand PalCommand palCommand;

  /** Flag indicating whether logs should be deleted. */
  @Option(
      names = {"-L", "--delete-logs"},
      description = "delete logs")
  private boolean deleteLogs;

  /** Flag indicating whether peers should be deleted. */
  @Option(
      names = {"-P", "--delete-peers"},
      description = "delete peers")
  private boolean deletePeers = false;

  /**
   * Flag indicating that only peers or logs starting with the specified prefix should be deleted.
   */
  @Option(
      names = {"-s", "--starting-with"},
      description = "delete peers or logs starting with given prefix")
  private boolean startingWith;

  /** Flag indicating that all peers or logs should be deleted. */
  @Option(
      names = {"--all", "-a"},
      description = "delete all")
  private boolean deleteAll;

  /** Flag indicating whether the help message was requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Unique identifier for Kafka clients used by this command. */
  private static final UUID KAFKA_CLIENT_ID = UUID.randomUUID();

  /** Timeout in milliseconds for deleting Kafka topics. */
  private static final int DELETE_TOPIC_TIMEOUT_MS = 250;

  /** Timeout in seconds for closing Kafka AdminClient instances. */
  private static final int ADMIN_CLIENT_CLOSE_TIMEOUT_SECS = 2;

  /** Logger instance for logging operations. */
  private final Logger logger = LoggerFactory.getLogger(Remove.class);

  /** Map storing AdminClient instances for each Kafka bootstrap server. */
  private final Map<String, AdminClient> adminClientsPerServer = new HashMap<>();

  /** Counter for the number of errors encountered during command execution. */
  private int errors = 0;

  /** Validates user input. (Currently no validation is performed.) */
  @Override
  public void validateInput() {}

  /**
   * Initializes the directory connection using the connection string provided by the parent
   * PalCommand.
   */
  @Override
  protected void initialize() {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
  }

  /**
   * Retrieves the AdminClient for the specified bootstrap servers, creating a new instance if one
   * does not already exist.
   *
   * @param bootstrapServers the Kafka bootstrap servers to connect to
   * @return the AdminClient for the given bootstrap servers
   */
  private AdminClient getAdminClientForServers(String bootstrapServers) {
    if (!adminClientsPerServer.containsKey(bootstrapServers)) {
      Properties props = new Properties();
      props.setProperty("bootstrap.servers", bootstrapServers);
      props.setProperty("client.id", KAFKA_CLIENT_ID.toString());
      adminClientsPerServer.put(bootstrapServers, AdminClient.create(props));
    }
    return adminClientsPerServer.get(bootstrapServers);
  }

  /**
   * Deletes the Kafka topic associated with the given LogInfo.
   *
   * @param logInfo the LogInfo representing the log to remove
   */
  private void removeFromKafka(LogInfo logInfo) {
    if (logger.isDebugEnabled()) {
      logger.debug("Attempting to remove log '{}' from kafka", logInfo.getName());
    }
    AdminClient adminClient = getAdminClientForServers(logInfo.getBootstrapServers());
    adminClient.deleteTopics(
        Collections.singleton(logInfo.getName()),
        new DeleteTopicsOptions().timeoutMs(DELETE_TOPIC_TIMEOUT_MS));
  }

  /**
   * Deletes multiple Kafka topics associated with the provided LogInfo set.
   *
   * @param logInfos the set of LogInfo representing logs to remove
   * @param bootstrapServers the Kafka bootstrap servers to connect to
   */
  @SuppressWarnings("unused")
  private void removeFromKafka(Set<LogInfo> logInfos, String bootstrapServers) {
    AdminClient adminClient = getAdminClientForServers(bootstrapServers);
    adminClient.deleteTopics(
        logInfos.stream().map(LogInfo::getName).collect(Collectors.toList()),
        new DeleteTopicsOptions().timeoutMs(DELETE_TOPIC_TIMEOUT_MS));
  }

  /**
   * Deletes the specified log from the PAL directory and removes its corresponding Kafka topic.
   *
   * @param logInfo the LogInfo representing the log to delete
   */
  private void deleteLog(LogInfo logInfo) {
    try {
      getPalDirectory().deleteLog(logInfo.getName());
    } catch (Exception e) {
      logger.error("Error unregistering log '{}' from directory", logInfo.getName(), e);
      errors++;
    }

    // remove from kafka
    removeFromKafka(logInfo);

    logger.info("Log '{}' (UUID: {}) removed", logInfo.getName(), logInfo.getUuid());
  }

  /**
   * Deletes all logs with the specified UUID. If multiple logs are found, prompts the user for
   * confirmation before deletion.
   *
   * @param uuid the UUID of the logs to delete
   */
  private void deleteLogsWithUuid(UUID uuid) {
    final Set<LogInfo> matchingLogs;
    try {
      matchingLogs =
          getPalDirectory().getAllLogs().stream()
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
        try (Scanner scanner = new Scanner(System.in, Charset.defaultCharset())) {
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

  /** Deletes all logs registered in the PAL directory. */
  private void deleteAllLogs() {
    final Set<LogInfo> allLogs;
    try {
      allLogs = getPalDirectory().getAllLogs();
    } catch (Exception e) {
      errors++;
      return;
    }
    allLogs.forEach(this::deleteLog);
  }

  /**
   * Deletes the peer with the specified UUID from the PAL directory.
   *
   * @param peerUuid the UUID of the peer to delete
   */
  private void deletePeer(UUID peerUuid) {
    try {
      getPalDirectory().deletePeer(peerUuid);
    } catch (Exception e) {
      errors++;
    }
  }

  /**
   * Deletes all peers with the specified name from the PAL directory. If multiple peers are found
   * with the same name, prompts the user for confirmation before deletion.
   *
   * @param peerName the name of the peers to delete
   * @throws Exception if an error occurs while fetching or unregistering peers
   */
  private void deletePeersNamed(String peerName) throws Exception {
    final Set<PeerInfo> matchingPeers =
        getPalDirectory().getAllPeers().stream()
            .filter(p -> peerName.equals(p.getName()))
            .collect(Collectors.toSet());

    // request confirmation
    if (matchingPeers.size() > 1) {
      String answer = null;
      while (answer == null || !(answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("n"))) {
        out.printf(
            "There are %d peers named '%s'. Delete all? (y/n): ", matchingPeers.size(), peerName);
        try (Scanner scanner = new Scanner(System.in, Charset.defaultCharset())) {
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
        getPalDirectory().deletePeer(peer.getUuid());
      } catch (Exception e) {
        logger.error("Error unregistering peer UUID '{}' from directory", peer.getUuid(), e);
        errors++;
      }
    }
  }

  /** Deletes all peers registered in the PAL directory. */
  private void deleteAllPeers() {
    try {
      long peersUnregistered = getPalDirectory().deleteAllPeers();
      logger.debug("Unregistered {} peers", peersUnregistered);
    } catch (Exception e) {
      errors++;
    }
  }

  /**
   * Closes all AdminClient instances and releases associated resources.
   *
   * @throws IOException if an I/O error occurs during resource closure
   */
  @Override
  protected void closeResources() throws IOException {
    adminClientsPerServer
        .values()
        .forEach(
            adminClient ->
                adminClient.close(
                    Duration.of(ADMIN_CLIENT_CLOSE_TIMEOUT_SECS, ChronoUnit.SECONDS)));

    super.closeResources();
  }

  /**
   * Executes the removal of peers and/or logs based on the specified options and arguments.
   *
   * @return the number of errors encountered during execution
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    if (!(deletePeers || deleteLogs)) {
      out.println("Use -L/--logs to remove logs, or -P/--peers to remove peers.");
      return 1;
    }

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
              final Set<PeerInfo> allPeers = getPalDirectory().getAllPeers();
              allPeers.stream()
                  .filter(p -> p.getName() != null && p.getName().startsWith(arg))
                  .forEach(p -> deletePeer(p.getUuid()));
            } else {
              deletePeersNamed(arg);
            }
          }
        }
      }
    }

    if (deleteLogs) {
      if (deleteAll) {
        deleteAllLogs();
      } else if (argList != null && !argList.isEmpty()) {
        // TODO group all logs with same bootstrap servers and remove in batch
        for (String arg : argList) {
          // try to parse arg as UUID
          UUID logUuid = null;
          try {
            logUuid = UUID.fromString(arg);
          } catch (IllegalArgumentException e) {
            // fine, it's not a UUID
          }
          if (logUuid != null) {
            deleteLogsWithUuid(logUuid);
          } else {
            // if not a valid UUID we will consider it a name
            if (startingWith) {
              final Set<LogInfo> allLogs = getPalDirectory().getAllLogs();
              allLogs.stream().filter(l -> l.getName().startsWith(arg)).forEach(this::deleteLog);
            } else {
              final LogInfo log = getPalDirectory().getLogInfo(arg);
              if (log == null) {
                logger.error("Cannot find log named '{}' in directory", arg);
                errors++;
              } else {
                deleteLog(log);
              }
            }
          }
        }
      }
    }
    return errors;
  }
}
