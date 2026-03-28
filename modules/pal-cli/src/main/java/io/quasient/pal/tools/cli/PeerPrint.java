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

import static io.quasient.pal.common.util.Strings.stringAfter;
import static io.quasient.pal.common.util.Strings.stringBefore;
import static picocli.CommandLine.Parameters;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.MessageStreamer;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.ColferUtils;
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
 * Streams and prints messages from a peer's socket connection.
 *
 * <p>This is the peer-specific print command for the {@code pal peer print} pattern. It accepts a
 * peer UUID or address as a positional argument and streams messages in various output formats with
 * optional filtering by type, peer UUID, thread name, and message ID.
 *
 * <p>Usage examples:
 *
 * <pre>
 *   pal peer print 123e4567-e89b-12d3-a456-426614174000
 *   pal peer print tcp://localhost:5555 --full
 *   pal peer print my-peer-uuid --types CONSTRUCTOR,INSTANCE_METHOD
 * </pre>
 *
 * @see AbstractPrintCommand
 */
@Command(name = "print", description = "Print messages from a peer")
@SuppressFBWarnings(
    value = {"SIC_INNER_SHOULD_BE_STATIC_ANON", "URF_UNREAD_FIELD"},
    justification =
        "Anonymous Thread subclass for shutdown hook; picocli fields read via reflection")
class PeerPrint extends AbstractPrintCommand {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(PeerPrint.class);

  /**
   * Latch used for coordinating shutdown in socket-based message streaming.
   *
   * <p>This latch is counted down during shutdown to signal the streaming thread to terminate.
   * Package-private for test access.
   */
  CountDownLatch socketShutdownLatch;

  /** Parent command that provides access to the main PalCommand context. */
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
  String peerIdentifier;

  /** Constructs a new {@code PeerPrint} instance. */
  PeerPrint() {}

  /**
   * {@inheritDoc}
   *
   * <p>Validates that a peer identifier has been provided.
   *
   * @throws RuntimeException if the peer identifier is not provided
   */
  @Override
  protected void validateInput() {
    if (peerIdentifier == null || peerIdentifier.isEmpty()) {
      throw new RuntimeException(
          "Peer identifier is required. Usage: pal peer print <PEER_UUID_OR_ADDRESS>");
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Initializes the directory connection provider using the connection string from the parent
   * command.
   */
  @Override
  protected void initialize() {
    if (palCommand != null && palCommand.getPalDirectoryConnectionString() != null) {
      initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Streams and prints messages from the specified peer's PUB socket.
   *
   * @return 0 if the operation is successful, 1 if an uncaught error occurs
   * @throws Exception if an error occurs while consuming messages
   */
  @Override
  protected int runCommand() throws Exception {
    return printSocketMessageStream();
  }

  /**
   * Initiates the printing of messages by subscribing to a peer's message stream via a socket
   * connection.
   *
   * <p>Resolves the peer address from the positional identifier (UUID or direct address), connects
   * to the ZMQ PUB socket, applies configured filters, and prints messages in the selected output
   * format.
   *
   * @return 0 if the operation is successful, 1 if an uncaught error occurs
   * @throws Exception if an error occurs while consuming messages
   */
  private Integer printSocketMessageStream() throws Exception {
    ExecutorService executor =
        Executors.newFixedThreadPool(
            1,
            r -> {
              Thread thread = new Thread(r);
              thread.setUncaughtExceptionHandler((t, e) -> logger.error("Uncaught exception", e));
              return thread;
            });

    String peerAddress = resolvePeerAddress();

    // parse address
    String hostAndPort =
        peerAddress.contains("://") ? stringAfter(peerAddress, "://") : peerAddress;
    String host = stringBefore(hostAndPort, ":");
    int port = Integer.parseInt(stringAfter(hostAndPort, ":"));

    final MessageStreamer streamer = new MessageStreamer(host, port).connect();
    logger.info("Connected printer to {}:{}", host, port);

    Runnable streamerThread =
        () -> {
          Stream<Message> stream = streamer.getStream();

          // stream: apply filter: message types
          if (msgTypes != null) {
            stream =
                stream.filter(
                    m -> {
                      String messageTypeName = getMessageTypeName(m);
                      // remove the "EXEC_" prefix
                      messageTypeName = messageTypeName.substring(5);
                      return msgTypes.contains(messageTypeName);
                    });
          }
          // stream: apply filter: from peer (uuid)
          if (fromPeer != null) {
            stream = stream.filter(m -> fromPeer.equalsIgnoreCase(getPeerUuid(m)));
          }

          // stream: apply filter: from thread name
          if (threadName != null) {
            stream =
                stream.filter(
                    m -> {
                      ExecMessage execMessage = m.getExecMessage();
                      return execMessage != null
                          && threadName.equalsIgnoreCase(execMessage.getThreadName());
                    });
          }

          // stream: apply filter: msg ID
          if (id != null) {
            stream = stream.filter(m -> id.equalsIgnoreCase(getMessageId(m)));
          }

          // stream: print
          stream.forEach(
              msg -> {
                switch (getFormat()) {
                  case FULL -> System.out.printf("%s%n", msg.toString());
                  case JSON -> System.out.printf("%s%n", ColferUtils.toJson(msg, true));
                  case COMPACT ->
                      System.out.printf(
                          "uuid=%s type=%s%n", getMessageTypeName(msg), getMessageTypeName(msg));
                  default -> throw new IllegalStateException("Unexpected value: " + getFormat());
                }
              });
        };

    // latch to wait for termination
    socketShutdownLatch = new CountDownLatch(1);

    // attach shutdown handler to catch control-c
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread("streams-shutdown-hook") {
              @Override
              public void run() {
                performShutdown();
              }
            });

    // start consuming and printing
    try {
      executor.execute(streamerThread);
      logger.info("Stream started");
      socketShutdownLatch.await();
      logger.info("Shutting down");
      executor.shutdownNow();
      streamer.close();
    } catch (Throwable e) {
      logger.error("Uncaught error", e);
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
      PeerInfo peerInfo = palDirectory.getPeer(peerUuid);
      return peerInfo.getPubAddress();
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
   * terminate. It is called by the shutdown hook when the application receives a termination signal
   * (e.g., Ctrl+C).
   *
   * <p>Package-private for test access.
   */
  void performShutdown() {
    socketShutdownLatch.countDown();
  }
}
