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

import static io.quasient.pal.common.util.Strings.stringAfter;
import static io.quasient.pal.common.util.Strings.stringBefore;
import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.MessageStreamer;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.tools.stats.ContinuousPrinter;
import io.quasient.pal.tools.stats.Counters;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Collects and displays message stream statistics from a peer's socket connection.
 *
 * <p>This is the peer-specific stats command for the {@code pal peer stats} pattern. It accepts a
 * peer identifier (UUID or address) as a positional argument and streams statistics via ZMQ PUB/SUB
 * socket connection. Messages are filtered by type, peer UUID, or thread name, and counters are
 * continuously printed in plain text or JSON format.
 *
 * <p>Usage examples:
 *
 * <pre>
 *   pal peer stats 123e4567-e89b-12d3-a456-426614174000
 *   pal peer stats tcp://localhost:5555 -t EXEC_INSTANCE_METHOD -j
 * </pre>
 *
 * @see Counters
 * @see ContinuousPrinter
 */
@Command(name = "stats", description = "Show peer message statistics")
@SuppressFBWarnings(
    value = {
      "EI_EXPOSE_REP2",
      "SIC_INNER_SHOULD_BE_STATIC_ANON",
      "URF_UNREAD_FIELD",
      "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"
    },
    justification =
        "CLI stats command - shared state for thread operations; format strings intentional")
public class PeerStats extends AbstractStatsCommand {

  /** Logger for logging information and errors. */
  private final Logger logger = LoggerFactory.getLogger(PeerStats.class);

  /**
   * Latch used to coordinate shutdown signal handling for socket streams.
   *
   * <p>Package-private for test access.
   */
  CountDownLatch socketShutdownLatch;

  /** Handles continuous printing of aggregated statistics. */
  private ContinuousPrinter continuousPrinter;

  /** Indicates whether statistics are being printed externally. */
  private boolean externalPrinting = false;

  /** Parent command providing access to the PAL directory connection string. */
  @ParentCommand PalCommand palCommand;

  /**
   * Positional peer identifier argument (UUID or address).
   *
   * <p>Accepts either a peer UUID (e.g., "123e4567-e89b-12d3-a456-426614174000") or a peer address
   * (e.g., "tcp://localhost:5555").
   */
  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "PEER",
      description = "peer UUID or address")
  private String peerIdentifier;

  /**
   * Types of messages to filter by.
   *
   * <p>Specifies one or more message types to include in the statistics.
   */
  @Option(
      names = {"-t", "--types"},
      arity = "0..*",
      description =
          "type(s) of messages to filter by ("
              + "STATIC_CONSTRUCTOR, RETURN_CLASS, CONSTRUCTOR, INSTANCE_METHOD,"
              + " CLASS_METHOD, GET_STATIC, GET_FIELD, PUT_STATIC, PUT_FIELD,"
              + " PUT_STATIC_DONE, PUT_FIELD_DONE, THROWABLE, RETURN_VALUE)")
  private List<String> msgTypes;

  /**
   * Peer UUID to filter messages by.
   *
   * <p>Includes only messages originating from the specified peer UUID.
   */
  @Option(
      names = {"-fp", "--from-peer"},
      paramLabel = "PEER_UUID",
      description = "filter by peer uuid")
  private String fromPeer;

  /**
   * Thread name to filter messages by.
   *
   * <p>Includes only messages originating from the specified thread name.
   */
  @SuppressWarnings("unused")
  @Option(
      names = {"-ft", "--from-thread"},
      paramLabel = "THREAD_NAME",
      description = "filter by thread name")
  private String threadName;

  /**
   * Flag to enable JSON output for statistics.
   *
   * <p>If set, the aggregated statistics will be printed in JSON format.
   */
  @Option(
      names = {"-j", "--json-output"},
      description = "print stats as JSON")
  private boolean jsonOutput;

  /**
   * Indicates whether help was requested.
   *
   * <p>If set, displays the help message and exits.
   */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /**
   * Constructs a PeerStats instance for socket-based streams with additional filtering options.
   * Intended for use from another class rather than the command line.
   *
   * @param peerIdentifier peer UUID or address
   * @param msgTypes message types to filter by
   * @param fromPeer peer UUID to filter by
   * @param threadName thread name to filter by
   */
  public PeerStats(
      String peerIdentifier, List<String> msgTypes, String fromPeer, String threadName) {
    this.peerIdentifier = peerIdentifier;
    this.msgTypes = msgTypes;
    this.fromPeer = fromPeer;
    this.threadName = threadName;
    this.externalPrinting = true;
  }

  /** Default constructor for running as a Picocli command (i.e., from the command line). */
  PeerStats() {}

  /**
   * Validates the input provided to the command.
   *
   * @throws RuntimeException if the peer identifier is not provided
   */
  @Override
  protected void validateInput() {
    if (peerIdentifier == null || peerIdentifier.isEmpty()) {
      throw new RuntimeException(
          "Peer identifier is required. Usage: pal peer stats <PEER_UUID_OR_ADDRESS>");
    }
  }

  /**
   * Performs initialization steps required before running the command.
   *
   * <p>Initializes the directory connection provider if a directory connection string is available.
   */
  @Override
  protected void initialize() {
    if (palCommand != null && palCommand.getPalDirectoryConnectionString() != null) {
      initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    }
  }

  /**
   * Processes and aggregates statistics from socket-based message streams.
   *
   * <p>Connects to a message streamer via socket, applies necessary filters, and updates the
   * counters accordingly.
   *
   * @return exit code indicating success (0) or failure (1)
   * @throws Exception if an error occurs during stream processing
   */
  @Override
  protected int runCommand() throws Exception {
    String peerAddress = resolvePeerAddress();

    String hostAndPort =
        peerAddress.contains("://") ? stringAfter(peerAddress, "://") : peerAddress;
    String host = stringBefore(hostAndPort, ":");
    int port = Integer.parseInt(stringAfter(hostAndPort, ":"));

    ExecutorService executor =
        Executors.newFixedThreadPool(
            2,
            r -> {
              Thread thread = new Thread(r);
              thread.setUncaughtExceptionHandler(
                  (t, e) -> {
                    logger.error("Uncaught exception", e);
                    if (!externalPrinting) {
                      //noinspection CallToPrintStackTrace
                      e.printStackTrace();
                    }
                  });
              return thread;
            });

    final MessageStreamer streamer = new MessageStreamer(host, port).connect();
    Runnable streamerThread =
        () -> {
          Stream<Message> stream = streamer.getStream();

          if (msgTypes != null) {
            stream =
                stream.filter(
                    m -> {
                      if (m == null || m.getExecMessage() == null) {
                        return false;
                      }
                      MessageType messageType = MessageType.fromId(m.getMessageType());
                      return msgTypes.contains(messageType.name());
                    });
          }

          if (fromPeer != null) {
            stream = stream.filter(m -> m != null && fromPeer.equalsIgnoreCase(getPeerUuid(m)));
          }

          stream = stream.filter(Objects::nonNull);
          stream.forEach(this::updateCounters);
        };

    socketShutdownLatch = new CountDownLatch(1);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread("streams-shutdown-hook") {
              @Override
              public void run() {
                performSocketShutdown();
              }
            });

    try {
      executor.execute(streamerThread);
      logger.info("Stream started");
      if (!externalPrinting) {
        continuousPrinter = new ContinuousPrinter(getCounters(), jsonOutput, 1);
        executor.execute(continuousPrinter);
        logger.info("Printer started");
      }
      socketShutdownLatch.await();
      logger.info("Shutting down");
      if (continuousPrinter != null) {
        continuousPrinter.setDone(true);
      }
      executor.shutdownNow();
      streamer.close();
    } catch (Throwable e) {
      logger.error("Uncaught error", e);
      if (!externalPrinting) {
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
      return 1;
    }
    return 0;
  }

  /**
   * Resolves the peer address from the positional peer identifier.
   *
   * <p>If the identifier looks like a UUID, it is resolved via the PAL directory to get the publish
   * address. Otherwise, it is treated as a direct address.
   *
   * @return the resolved peer address
   * @throws Exception if the peer cannot be resolved
   */
  private String resolvePeerAddress() throws Exception {
    if (isUuid(peerIdentifier)) {
      UUID peerUuid = UUID.fromString(peerIdentifier);
      PalDirectory palDirectory = getPalDirectory();
      String address = palDirectory.getPeer(peerUuid).getPubAddress();
      return address;
    }
    return peerIdentifier;
  }

  /**
   * Checks whether a string is a valid UUID.
   *
   * @param str the string to check
   * @return {@code true} if the string is a valid UUID, {@code false} otherwise
   */
  private static boolean isUuid(String str) {
    try {
      UUID.fromString(str);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Performs shutdown for socket-based message streaming.
   *
   * <p>This method counts down the socket shutdown latch to signal the streaming thread to
   * terminate.
   *
   * <p>Package-private for test access.
   */
  void performSocketShutdown() {
    socketShutdownLatch.countDown();
  }

  /**
   * Stops the ongoing stream processing by triggering a shutdown.
   *
   * <p>Signals the stream processing threads to terminate gracefully.
   */
  @SuppressWarnings("unused")
  public void stopStreams() {
    if (socketShutdownLatch != null) {
      socketShutdownLatch.countDown();
    }
  }
}
