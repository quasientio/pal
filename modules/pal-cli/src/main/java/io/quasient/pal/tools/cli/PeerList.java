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
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.util.Strings;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Lists peers registered in the PAL directory.
 *
 * <p>This is the peer-specific list command for the {@code pal peer ls} pattern. It displays peers
 * in short or long format with optional sorting by creation time, reversal, and trimming control.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal peer ls
 *   pal peer ls -l
 *   pal peer ls -l -c -r
 *   pal peer ls --no-trim
 * </pre>
 */
@Command(
    name = "ls",
    description = "List peers",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "helpRequested is read by picocli framework via reflection")
public class PeerList extends AbstractPalSubcommand {

  /** Logger instance. */
  private final Logger logger = LoggerFactory.getLogger(PeerList.class);

  /** The parent command to which this subcommand belongs. */
  @ParentCommand PalCommand palCommand;

  /** Flag indicating whether to use long listing format. */
  @Option(
      names = {"-l", "--long"},
      description = "use long listing format")
  private boolean longListing;

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

  /** Maximum allowed length (in characters) for peer names. */
  private static final short MAX_PEER_NAME_LEN = 15;

  /** Maximum allowed length (in characters) for endpoint addresses. */
  private static final short MAX_ENDPOINT_LEN = 20;

  /**
   * Format string for long listing of peers.
   *
   * <p>uuid name rpc jsonrpc pub jmx Uptime
   */
  private static final String PEERS_LONG_FORMAT =
      format(
          "%%-36s %%-%ds %%-%ds %%-%ds %%-%ds %%-%ds %%-8s",
          MAX_PEER_NAME_LEN,
          MAX_ENDPOINT_LEN,
          MAX_ENDPOINT_LEN,
          MAX_ENDPOINT_LEN,
          MAX_ENDPOINT_LEN);

  /** Constructs a new {@code PeerList} instance. */
  public PeerList() {}

  /** No validation needed; no mutual exclusion flags. */
  @Override
  public void validateInput() {}

  /**
   * Initializes the subcommand by setting up the directory connection.
   *
   * @throws Exception if initialization fails
   */
  @Override
  protected void initialize() throws Exception {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
  }

  /**
   * Lists peers from the PAL directory.
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
            Error: pal peer ls requires a PAL directory.
            Specify with --directory/-d option or PAL_DIRECTORY environment variable.
            Example: pal peer ls -d localhost:2379""");
        return 1;
      }
    } catch (RuntimeException e) {
      err.println(
          """
          Error: Cannot connect to PAL directory.
          Ensure etcd is running and accessible, then specify the directory:
            pal peer ls -d localhost:2379
          Or set PAL_DIRECTORY environment variable.""");
      return 1;
    }

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
    return 0;
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
      String displayValue =
          (peerInfo.getName() != null && !peerInfo.getName().isEmpty())
              ? peerInfo.getName()
              : peerInfo.getUuid().toString();
      out.printf("%s%n", displayValue);
    }
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
   * Formats the uptime based on the start time.
   *
   * @param startDateTime the start time of the peer
   * @return a formatted uptime string in "H:mm:ss" format
   */
  static String getFormattedUptime(OffsetDateTime startDateTime) {
    final OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
    return DurationFormatUtils.formatDuration(
        Duration.between(startDateTime, now).toMillis(), "H:mm:ss");
  }

  /**
   * Formats the given date and time.
   *
   * @param dateTime the date and time to format
   * @return a formatted date string in "MMM dd HH:mm" format
   */
  static String getFormattedDate(OffsetDateTime dateTime) {
    return ListFormatUtils.getFormattedDate(dateTime);
  }
}
