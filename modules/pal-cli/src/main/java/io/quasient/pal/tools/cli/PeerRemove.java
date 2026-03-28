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
import io.quasient.pal.common.directory.nodes.PeerInfo;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Removes peers from the PAL directory.
 *
 * <p>This is the peer-specific remove command for the {@code pal peer rm} pattern. It supports
 * removal by name or UUID as positional arguments, prefix matching with {@code -s/--starting-with},
 * bulk deletion with {@code --all}, and a force flag to override alive-peer safety checks.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal peer rm my-peer --force
 *   pal peer rm 550e8400-e29b-41d4-a716-446655440000
 *   pal peer rm -s app- --force
 *   pal peer rm --all --force
 * </pre>
 */
@Command(
    name = "rm",
    description = "Remove peers from directory",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "helpRequested is read by picocli framework via reflection")
public class PeerRemove extends AbstractPalSubcommand {

  /** Logger instance for logging operations. */
  private final Logger logger = LoggerFactory.getLogger(PeerRemove.class);

  /** Reference to the parent PalCommand for directory connection string propagation. */
  @ParentCommand PalCommand palCommand;

  /** The command specification provided by picocli for accessing usage information. */
  @Spec CommandSpec spec;

  /** Positional arguments specifying the names or UUIDs of peers to remove. */
  @Parameters(index = "0..*", paramLabel = "PEER", description = "Peer names or UUIDs to remove")
  List<String> peerIdentifiers;

  /** Flag indicating that only peers starting with the specified prefix should be deleted. */
  @Option(
      names = {"-s", "--starting-with"},
      description = "delete peers starting with given prefix")
  boolean startingWith;

  /** Flag indicating that all peers should be deleted. */
  @Option(
      names = {"--all", "-a"},
      description = "delete all peers")
  boolean deleteAll;

  /** Flag indicating whether to skip confirmation prompts and override alive-peer safety checks. */
  @Option(
      names = {"--force", "-f"},
      description = "skip confirmation prompts and force removal of alive peers")
  boolean force;

  /** Flag indicating whether the help message was requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Counter for the number of errors encountered during command execution. */
  private int errors = 0;

  /** Constructs a new {@code PeerRemove} instance. */
  public PeerRemove() {}

  /** Validates that either positional arguments or {@code --all} are provided. */
  @Override
  public void validateInput() {}

  /**
   * Initializes the directory connection using the connection string from the parent command.
   *
   * @throws Exception if initialization fails
   */
  @Override
  protected void initialize() throws Exception {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
  }

  /**
   * Deletes the peer with the specified UUID from the PAL directory.
   *
   * <p>Checks if the peer is alive (has an active lease) before deletion. If alive and {@code
   * --force} is not specified, the deletion is blocked and an error is printed.
   *
   * @param peerUuid the UUID of the peer to delete
   */
  private void deletePeer(UUID peerUuid) {
    try {
      if (getPalDirectory().getPeer(peerUuid) == null) {
        out.printf("No peer found with UUID '%s'%n", peerUuid);
        errors++;
        return;
      }
      boolean isAlive = getPalDirectory().isPeerAlive(peerUuid);
      if (isAlive && !force) {
        out.printf(
            "Cannot remove peer %s: peer is alive (has active lease). "
                + "Use --force to remove anyway.%n",
            peerUuid);
        errors++;
        return;
      }
      getPalDirectory().deletePeer(peerUuid);
    } catch (RuntimeException | ExecutionException | InterruptedException e) {
      errors++;
    }
  }

  /**
   * Deletes all peers with the specified name from the PAL directory.
   *
   * <p>If multiple peers share the same name and {@code --force} is not specified, the user is
   * prompted for confirmation before deletion.
   *
   * @param peerName the name of the peers to delete
   * @throws Exception if an error occurs while fetching or unregistering peers
   */
  private void deletePeersNamed(String peerName) throws Exception {
    final Set<PeerInfo> matchingPeers =
        getPalDirectory().listPeers().stream()
            .filter(p -> peerName.equals(p.getName()))
            .collect(Collectors.toSet());

    if (matchingPeers.isEmpty()) {
      out.printf("No peer found with name '%s'%n", peerName);
      errors++;
      return;
    }

    if (matchingPeers.size() > 1 && !force) {
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

    for (PeerInfo peer : matchingPeers) {
      try {
        boolean isAlive = getPalDirectory().isPeerAlive(peer.getUuid());
        if (isAlive && !force) {
          out.printf(
              "Cannot remove peer '%s' (%s): peer is alive (has active lease). "
                  + "Use --force to remove anyway.%n",
              peer.getName() != null ? peer.getName() : peer.getUuid(), peer.getUuid());
          errors++;
          continue;
        }
        getPalDirectory().deletePeer(peer.getUuid());
      } catch (RuntimeException | ExecutionException | InterruptedException e) {
        logger.error("Error unregistering peer UUID '{}' from directory", peer.getUuid(), e);
        errors++;
      }
    }
  }

  /**
   * Deletes all peers registered in the PAL directory.
   *
   * <p>Delegates to {@link io.quasient.pal.cxn.directory.PalDirectory#deletePeers()}.
   */
  private void deleteAllPeers() {
    try {
      long peersUnregistered = getPalDirectory().deletePeers();
      logger.debug("Unregistered {} peers", peersUnregistered);
    } catch (RuntimeException | ExecutionException | InterruptedException e) {
      errors++;
    }
  }

  /**
   * Executes the peer removal based on the specified options and positional arguments.
   *
   * @return the number of errors encountered during execution
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    if (!deleteAll && (peerIdentifiers == null || peerIdentifiers.isEmpty())) {
      spec.commandLine().usage(out);
      return 1;
    }

    if (deleteAll) {
      deleteAllPeers();
    } else {
      for (String arg : peerIdentifiers) {
        UUID peerUuid = null;
        try {
          peerUuid = UUID.fromString(arg);
        } catch (IllegalArgumentException e) {
          // not a UUID, treat as name
        }
        if (peerUuid != null) {
          deletePeer(peerUuid);
        } else {
          if (startingWith) {
            final Set<PeerInfo> allPeers = getPalDirectory().listPeers();
            allPeers.stream()
                .filter(p -> p.getName() != null && p.getName().startsWith(arg))
                .forEach(p -> deletePeer(p.getUuid()));
          } else {
            deletePeersNamed(arg);
          }
        }
      }
    }
    return errors;
  }
}
