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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Removes dead peers from the PAL directory.
 *
 * <p>A dead peer is one whose lease has expired (no {@code /state} key in etcd), typically because
 * the peer process crashed or was killed without graceful shutdown. The {@code /info} and {@code
 * /by-name} keys remain as stale entries that this command cleans up.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal peer prune
 *   pal peer prune -d localhost:2379
 * </pre>
 */
@Command(
    name = "prune",
    description = "Remove dead peers from directory",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "helpRequested is read by picocli framework via reflection")
public class PeerPrune extends AbstractPalSubcommand {

  /** Logger instance for logging operations. */
  private final Logger logger = LoggerFactory.getLogger(PeerPrune.class);

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

  /** Constructs a new {@code PeerPrune} instance. */
  public PeerPrune() {}

  /** No validation needed; this command takes no positional arguments. */
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
   * Removes all dead peers from the PAL directory.
   *
   * <p>Lists all registered peers, checks each for liveness, and deletes those whose lease has
   * expired. Prints each pruned peer and a final summary.
   *
   * @return the number of errors encountered during execution
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    Set<PeerInfo> peers = getPalDirectory().listPeers();
    int pruned = 0;

    for (PeerInfo peer : peers) {
      try {
        if (!getPalDirectory().isPeerAlive(peer.getUuid())) {
          getPalDirectory().deletePeer(peer.getUuid());
          String display =
              (peer.getName() != null && !peer.getName().isEmpty())
                  ? peer.getName()
                  : peer.getUuid().toString();
          out.printf("Pruned %s (%s)%n", display, peer.getUuid());
          pruned++;
        }
      } catch (RuntimeException | ExecutionException | InterruptedException e) {
        logger.error("Error pruning peer '{}'", peer.getUuid(), e);
        errors++;
      }
    }

    if (pruned == 0 && errors == 0) {
      out.println("No dead peers found");
    } else if (pruned > 0) {
      out.printf("Pruned %d dead peer(s)%n", pruned);
    }

    return errors;
  }
}
