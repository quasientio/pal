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

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.tools.stats.ContinuousPrinter;
import io.quasient.pal.tools.stats.Counters;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Collects and displays message stream statistics from a Kafka log.
 *
 * <p>This is the log-specific stats command for the {@code pal log stats} pattern. It accepts a log
 * name as a positional argument and aggregates statistics via Kafka Streams. Messages are filtered
 * by type, peer UUID, or thread name, and counters are continuously printed in plain text or JSON
 * format.
 *
 * <p>Usage examples:
 *
 * <pre>
 *   pal log stats my-log-topic -k localhost:9092
 *   pal log stats my-log-topic -k localhost:9092 -t EXEC_INSTANCE_METHOD -j
 * </pre>
 *
 * @see Counters
 * @see ContinuousPrinter
 */
@Command(name = "stats", description = "Show log message statistics")
@SuppressFBWarnings(
    value = {
      "EI_EXPOSE_REP2",
      "SIC_INNER_SHOULD_BE_STATIC_ANON",
      "URF_UNREAD_FIELD",
      "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"
    },
    justification =
        "CLI stats command - shared state for thread operations; format strings intentional")
public class LogStats extends AbstractStatsCommand {

  /** Logger for logging information and errors. */
  private final Logger logger = LoggerFactory.getLogger(LogStats.class);

  /**
   * Latch used to coordinate shutdown signal handling for Kafka streams.
   *
   * <p>Package-private for test access.
   */
  final CountDownLatch shutdownLatch = new CountDownLatch(1);

  /**
   * Kafka streams instance for log-based message processing.
   *
   * <p>Package-private for test access.
   */
  KafkaStreams kafkaStreams;

  /** Handles continuous printing of aggregated statistics. */
  ContinuousPrinter continuousPrinter;

  /** Indicates whether statistics are being printed externally. */
  private boolean externalPrinting = false;

  /** Parent command providing access to the PAL directory connection string. */
  @ParentCommand PalCommand palCommand;

  /** Positional log name argument specifying the Kafka topic to read from. */
  @Parameters(index = "0", arity = "0..1", paramLabel = "LOG_NAME", description = "log name")
  private String logName;

  /**
   * Kafka bootstrap server addresses.
   *
   * <p>Specifies the Kafka bootstrap servers to connect to. Defaults to "localhost:9092" if not
   * provided.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      required = true,
      paramLabel = "BOOTSTRAP_SERVERS",
      defaultValue = "localhost:9092",
      description = "kafka bootstrap servers (default: ${DEFAULT-VALUE})")
  private String bootstrapServers;

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

  /** Flag to enable verbose output. */
  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

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
   * Constructs a LogStats instance for log-based streams, intended for use from another class
   * rather than the command line (e.g., from a Web/GUI application).
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param logName Kafka topic name
   */
  public LogStats(String bootstrapServers, String logName) {
    this(bootstrapServers, logName, null, null, null);
  }

  /**
   * Constructs a LogStats instance for log-based streams with additional filtering options.
   * Intended for use from another class rather than the command line.
   *
   * @param bootstrapServers Kafka bootstrap servers
   * @param logName Kafka topic name
   * @param msgTypes message types to filter by
   * @param fromPeer peer UUID to filter by
   * @param threadName thread name to filter by
   */
  public LogStats(
      String bootstrapServers,
      String logName,
      List<String> msgTypes,
      String fromPeer,
      String threadName) {
    this.bootstrapServers = bootstrapServers;
    this.logName = logName;
    this.msgTypes = msgTypes;
    this.fromPeer = fromPeer;
    this.threadName = threadName;
    this.externalPrinting = true;
  }

  /** Default constructor for running as a Picocli command (i.e., from the command line). */
  LogStats() {}

  /**
   * Validates the input provided to the command.
   *
   * @throws RuntimeException if the log name is not provided
   */
  @Override
  protected void validateInput() {
    if (logName == null || logName.isEmpty()) {
      throw new RuntimeException("Log name is required. Usage: pal log stats <LOG_NAME>");
    }
  }

  /**
   * Performs initialization steps required before running the command.
   *
   * <p>No initialization is required for log stats since it connects directly to Kafka.
   */
  @Override
  protected void initialize() {
    // No directory connection needed for log stats - connects directly to Kafka
  }

  /**
   * Processes and aggregates statistics from Kafka message streams.
   *
   * <p>Sets up Kafka Streams with the specified configurations, applies necessary filters based on
   * message types and peer UUID, and updates the counters accordingly. This method initializes the
   * stream processing topology and starts the Kafka Streams application.
   *
   * @return exit code indicating success (0) or failure (1)
   */
  @Override
  protected int runCommand() {
    Objects.requireNonNull(bootstrapServers, "bootstrap servers required");

    Properties props = new Properties();
    String consumerId = "printer-" + UUID.randomUUID();
    if (verbose) {
      System.out.println("CONFIG:");
      System.out.println("=======");
      System.out.printf(
          "Kafka config: topic=%s bootstrap_servers=%s app_id=%s%n",
          logName, bootstrapServers, consumerId);
    }
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, consumerId);
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    final StreamsBuilder builder = new StreamsBuilder();

    KStream<String, Message> stream =
        builder.<String, byte[]>stream(logName)
            .map(
                (k, v) -> {
                  Message message = new Message();
                  message.unmarshal(v, 0);
                  return new KeyValue<>(k, message);
                });

    if (msgTypes != null) {
      stream =
          stream.filter(
              (k, m) -> {
                ExecMessage execMessage = m.getExecMessage();
                MessageType messageType = MessageType.fromId(m.getMessageType());
                return execMessage != null && msgTypes.contains(messageType.name());
              });
    }

    if (fromPeer != null) {
      stream = stream.filter((k, m) -> fromPeer.equalsIgnoreCase(getPeerUuid(m)));
    }

    stream.foreach((key, message) -> updateCounters(message));

    final Topology topology = builder.build();
    if (verbose) {
      System.out.println(topology.describe());
    }

    kafkaStreams = new KafkaStreams(topology, props);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread("streams-shutdown-hook") {
              @Override
              public void run() {
                performKafkaShutdown();
              }
            });

    startStreams(kafkaStreams, getCounters());
    try {
      shutdownLatch.await();
    } catch (Throwable e) {
      logger.error("Uncaught error processing stream", e);
      if (!externalPrinting) {
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
      return 1;
    }
    return 0;
  }

  /**
   * Performs shutdown for Kafka-based message streaming.
   *
   * <p>This method closes the Kafka streams, signals the continuous printer to stop, and counts
   * down the shutdown latch.
   *
   * <p>Package-private for test access.
   */
  void performKafkaShutdown() {
    kafkaStreams.close();
    if (continuousPrinter != null) {
      continuousPrinter.setDone(true);
    }
    shutdownLatch.countDown();
  }

  /**
   * Stops the ongoing stream processing by triggering a shutdown.
   *
   * <p>Signals the stream processing threads to terminate gracefully.
   */
  @SuppressWarnings("unused")
  public void stopStreams() {
    shutdownLatch.countDown();
  }

  /**
   * Initializes and starts the Kafka Streams processing.
   *
   * <p>Sets up state listeners and exception handlers, starts the Kafka Streams instance, and
   * optionally starts the continuous statistics printer if external printing is not enabled.
   *
   * @param streams the {@link KafkaStreams} instance to start
   * @param counters the {@link Counters} instance to update with stream data
   */
  private void startStreams(KafkaStreams streams, Counters counters) {
    CompletableFuture<KafkaStreams.State> stateFuture = new CompletableFuture<>();
    streams.setStateListener(
        (newState, oldState) -> {
          if (stateFuture.isDone()) {
            return;
          }

          if (newState == KafkaStreams.State.RUNNING || newState == KafkaStreams.State.ERROR) {
            stateFuture.complete(newState);
          }
        });

    streams.setUncaughtExceptionHandler(
        throwable -> {
          logger.error("Uncaught exception in stream. Will shutdown client.", throwable);
          return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
        });

    streams.start();
    try {
      KafkaStreams.State finalState = stateFuture.get();
      if (finalState == KafkaStreams.State.RUNNING) {
        if (!externalPrinting) {
          this.continuousPrinter = new ContinuousPrinter(counters, jsonOutput);
          new Thread(continuousPrinter).start();
        }
      }
    } catch (InterruptedException | ExecutionException ex) {
      logger.error("Error processing stream", ex);
      if (!externalPrinting) {
        //noinspection CallToPrintStackTrace
        ex.printStackTrace();
      }
    }
  }
}
