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
import net.ittera.pal.common.cli.PalCommand;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "rm",
    customSynopsis = "pal rm [OPTIONS] [-L LOG, ...] [-P PEER, ...]%n",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n",
    description = "Remove peers or logs from directory")
public class Remove extends AbstractPalSubcommand {

  @Parameters(index = "0..*", hidden = true)
  @SuppressWarnings("unused")
  private java.util.List<String> argList;

  @ParentCommand PalCommand palCommand;

  @Option(
      names = {"-L", "--delete-logs"},
      description = "delete logs")
  private boolean deleteLogs;

  @Option(
      names = {"-P", "--delete-peers"},
      description = "delete peers")
  private boolean deletePeers = false;

  @Option(
      names = {"-s", "--starting-with"},
      description = "delete peers or logs starting with given prefix")
  private boolean startingWith;

  @Option(
      names = {"--all", "-a"},
      description = "delete all")
  private boolean deleteAll;

  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  private static final UUID KAFKA_CLIENT_ID = UUID.randomUUID();
  private static final int DELETE_TOPIC_TIMEOUT_MS = 250;
  private static final int ADMIN_CLIENT_CLOSE_TIMEOUT_SECS = 2;
  private final Logger logger = LoggerFactory.getLogger(Remove.class);
  private final Map<String, AdminClient> adminClientsPerServer = new HashMap<>();
  private int errors = 0;

  @Override
  public void validateInput() {}

  @Override
  protected void initialize() {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
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

  private void removeFromKafka(LogInfo logInfo) {
    if (logger.isDebugEnabled()) {
      logger.debug("Attempting to remove log '{}' from kafka", logInfo.getName());
    }
    AdminClient adminClient = getAdminClientForServers(logInfo.getBootstrapServers());
    adminClient.deleteTopics(
        Collections.singleton(logInfo.getName()),
        new DeleteTopicsOptions().timeoutMs(DELETE_TOPIC_TIMEOUT_MS));
  }

  @SuppressWarnings("unused")
  private void removeFromKafka(Set<LogInfo> logInfos, String bootstrapServers) {
    AdminClient adminClient = getAdminClientForServers(bootstrapServers);
    adminClient.deleteTopics(
        logInfos.stream().map(LogInfo::getName).collect(Collectors.toList()),
        new DeleteTopicsOptions().timeoutMs(DELETE_TOPIC_TIMEOUT_MS));
  }

  // remove from PAL directory
  private void deleteLog(LogInfo logInfo) {
    try {
      getPalDirectory().unregisterLog(logInfo.getName());
    } catch (Exception e) {
      logger.error("Error unregistering log '{}' from directory", logInfo.getName(), e);
      errors++;
    }

    // remove from kafka
    removeFromKafka(logInfo);

    logger.info("Log '{}' (UUID: {}) removed", logInfo.getName(), logInfo.getUuid());
  }

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

  private void deletePeer(UUID peerUuid) {
    try {
      getPalDirectory().unregisterPeer(peerUuid);
    } catch (Exception e) {
      errors++;
    }
  }

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
        getPalDirectory().unregisterPeer(peer.getUuid());
      } catch (Exception e) {
        logger.error("Error unregistering peer UUID '{}' from directory", peer.getUuid(), e);
        errors++;
      }
    }
  }

  private void deleteAllPeers() {
    try {
      long peersUnregistered = getPalDirectory().unregisterAllPeers();
      logger.debug("Unregistered {} peers", peersUnregistered);
    } catch (Exception e) {
      errors++;
    }
  }

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
