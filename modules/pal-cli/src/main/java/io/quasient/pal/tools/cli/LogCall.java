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

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Sends method invocation messages to a log.
 *
 * <p>This is the log-specific call command for the {@code pal log call} pattern. It accepts a log
 * name or path as a positional argument and writes method invocation messages to the log,
 * optionally reading responses from an input log. Supports synchronous and fire-and-forget modes,
 * multithreaded request handling, and uses {@link LogResolver} for log resolution.
 *
 * <p>Usage examples:
 *
 * <pre>
 *   pal log call my-log com.example.Worker arg1
 *   pal log call file:/tmp/wal com.example.Worker
 *   pal log call -i input-log -o output-log com.example.Worker
 *   pal log call my-log --forget-response com.example.Worker
 * </pre>
 */
@Command(
    name = "call",
    customSynopsis = "pal log call [OPTIONS] [LOG] [class args...]%n",
    description = "Send method calls via a log",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = {"DLS_DEAD_LOCAL_STORE", "URF_UNREAD_FIELD"},
    justification = "Unused field from picocli")
class LogCall extends AbstractCallCommand {

  /** Logger instance. */
  private final Logger logger = LoggerFactory.getLogger(LogCall.class);

  /** Path to the caller properties file. */
  private static final String CALLER_PROPERTIES_PATH = "/caller.properties";

  /** Path to the consumer properties file. */
  private static final String CONSUMER_PROPERTIES_PATH = "/consumer.properties";

  /** Path to the producer properties file. */
  private static final String PRODUCER_PROPERTIES_PATH = "/producer.properties";

  /** Properties loaded from the caller properties file. */
  private final Properties properties = new Properties();

  /** Properties loaded from the consumer properties file. */
  private final Properties consumerProperties = new Properties();

  /** Properties loaded from the producer properties file. */
  private final Properties producerProperties = new Properties();

  /** Duration to poll for messages. */
  private Long pollDuration;

  /** URL for connecting to the PAL directory service. */
  private String palDirectoryUrl;

  /** Builder for constructing static method call messages. */
  private StaticMethodCallBuilder staticMethodCallBuilder;

  /** List of JSON-RPC requests read from standard input. */
  private List<String> stdinRequests = new ArrayList<>();

  /**
   * Resolver for converting log names/paths to {@link LogInfo} objects.
   *
   * <p>Package-private for test access.
   */
  LogResolver logResolver;

  /** Parent command instance for accessing shared configurations. */
  @ParentCommand PalCommand palCommand;

  // Positional arguments

  /** Log identifier: log name, Kafka topic, or {@code file:}-prefixed Chronicle path. */
  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "LOG",
      description = "log name, topic, or file:/path")
  private String logIdentifier;

  /** The class whose method is to be called. */
  @SuppressWarnings("unused")
  @Parameters(index = "1", arity = "0..1", hidden = true)
  private String className;

  /** Arguments to pass to the target class method. */
  @SuppressWarnings("unused")
  @Parameters(index = "2..*", arity = "0..*", hidden = true)
  private List<String> argList;

  // Options

  /**
   * Kafka bootstrap servers for direct access to Kafka logs without PAL_DIRECTORY.
   *
   * <p>When provided, allows accessing Kafka logs directly without connecting to the PAL directory.
   * Takes precedence over the PAL_KAFKA_SERVERS environment variable.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      paramLabel = "host:port[,host:port...]",
      description = "Kafka bootstrap servers (for direct Kafka access without -d)")
  private String kafkaServers;

  /** Specifies the log to read from. */
  @Option(
      names = {"-i", "--input-log"},
      paramLabel = "name",
      description = "read from given log")
  private String inputLogName;

  /** Specifies the log to write to. */
  @Option(
      names = {"-o", "--output-log"},
      paramLabel = "name",
      description = "write to given log")
  private String outputLogName;

  /** Determines whether to send requests without waiting for responses. */
  @Option(
      names = {"-f", "--forget-response"},
      description = "do not wait for responses (default: false)")
  private boolean sendAndForget;

  /** Specifies the method to call on the target class. */
  @Option(
      names = {"-m", "--method"},
      paramLabel = "method",
      defaultValue = "main",
      description = "method to call on the class (default: main)")
  private String methodName;

  /** Specifies whether to print response messages received from the log. */
  @Option(
      names = {"--print-responses"},
      negatable = true,
      defaultValue = "true",
      fallbackValue = "true",
      description = "print response messages (default: ${DEFAULT-VALUE})")
  private boolean printResponses;

  /** Specifies the number of threads (clients) to use for sending requests. */
  @Option(
      names = {"-t", "--num-threads"},
      defaultValue = "1",
      paramLabel = "NUM_THREADS",
      description = "number of threads, i.e. clients to use (default: 1)")
  private int numberOfThreads;

  /** Enables verbose output during execution. */
  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  /** Displays the help message when requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Constructs a new {@code LogCall} instance. */
  LogCall() {}

  /** {@inheritDoc} */
  @Override
  protected boolean isPrintResponses() {
    return printResponses;
  }

  /**
   * Validates the user input options and arguments for log-specific message dispatch.
   *
   * @throws RuntimeException if input validation fails due to missing or conflicting options
   */
  @Override
  protected final void validateInput() {
    // Map positional logIdentifier to input/output log names (like old -l flag)
    if (optionGiven(logIdentifier)) {
      if (!optionGiven(inputLogName)) {
        inputLogName = logIdentifier;
      }
      if (!optionGiven(outputLogName)) {
        outputLogName = logIdentifier;
      }
    }

    if (!optionGiven(inputLogName) && !optionGiven(outputLogName)) {
      throw new RuntimeException(
          "Log identifier is required. Usage: pal log call <LOG> [class args...]");
    }

    if (optionGiven(outputLogName) && !optionGiven(inputLogName) && !sendAndForget) {
      throw new RuntimeException(
          "You must specify a log to read from, or else use --forget-response.");
    }

    // read stdin for requests (1 per line), if any
    stdinRequests = readStdinRequests();

    // validate mutual exclusivity of className and stdinRequests
    validateClassNameOrStdin(className, stdinRequests, "requests");
  }

  /**
   * Initializes the LogCall by setting up directory connections, loading properties, and creating
   * the log resolver.
   *
   * @throws Exception if initialization fails
   */
  @Override
  protected void initialize() throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Initializing LogCall...");
      if (optionGiven(className)) {
        logger.debug("Will call class: {} and method: {}", className, methodName);
      } else {
        logger.debug("Will send given requests");
      }
    }
    palDirectoryUrl = palCommand.getPalDirectoryConnectionString();
    initializeDirectoryConnectionProvider(palDirectoryUrl);
    loadProperties();

    String effectiveKafkaServers = kafkaServers != null ? kafkaServers : getKafkaServers();
    logResolver = new LogResolver(directoryConnectionProvider, effectiveKafkaServers);
  }

  /**
   * Loads required configuration values from properties files.
   *
   * @throws IOException if an error occurs while reading properties files
   */
  private void loadProperties() throws IOException {
    try (InputStream stream = LogCall.class.getResourceAsStream(CALLER_PROPERTIES_PATH)) {
      properties.load(stream);
    }
    final String pollDurationProp = properties.getProperty("pollDuration");
    if (pollDurationProp != null && !pollDurationProp.trim().isEmpty()) {
      pollDuration = Long.parseLong(pollDurationProp.trim());
    }
    try (InputStream stream = LogCall.class.getResourceAsStream(CONSUMER_PROPERTIES_PATH)) {
      consumerProperties.load(stream);
    }
    try (InputStream stream = LogCall.class.getResourceAsStream(PRODUCER_PROPERTIES_PATH)) {
      producerProperties.load(stream);
    }
  }

  /**
   * Creates and initializes a ThinPeer configured for log communication.
   *
   * @param thinPeerUuid the UUID for the new ThinPeer
   * @return the initialized ThinPeer
   * @throws Exception if ThinPeer initialization fails
   */
  private ThinPeer createLogThinPeer(UUID thinPeerUuid) throws Exception {
    boolean gotPalDir = !palDirectoryUrl.equals(PalDirectory.NO_URL);

    LogInfo inputLog = null;
    LogInfo outputLog = null;
    if (inputLogName != null) {
      inputLog = logResolver.resolveLogInfo(inputLogName);
    }
    if (outputLogName != null) {
      outputLog = logResolver.resolveLogInfo(outputLogName);
    }

    ThinPeer thinPeer =
        new ThinPeer()
            .withUuid(thinPeerUuid)
            .withDirectoryUrl(palDirectoryUrl)
            .withSelfRegistration(gotPalDir)
            .withInputLog(inputLog)
            .withOutputLog(outputLog)
            .withConsumerProperties(consumerProperties)
            .withProducerProperties(producerProperties);
    if (pollDuration != null) {
      thinPeer.withPollingDuration(pollDuration);
    }
    thinPeer.init();
    return thinPeer;
  }

  /**
   * Serially sends requests to a log using a single client.
   *
   * @return the number of requests successfully sent
   * @throws Exception if an error occurs during the sending of requests
   */
  private int sendRequestsWithSingleClient() throws Exception {
    final UUID thinPeerUuid = UUID.randomUUID();
    ThinPeer thinPeer = createLogThinPeer(thinPeerUuid);

    // init call builder
    if (className != null) {
      staticMethodCallBuilder =
          new StaticMethodCallBuilder(thinPeerUuid, className, methodName, argList);
    }

    // send message and receive response
    int requestsSent = 0;
    long start = System.currentTimeMillis();

    LogMessage<Message> responseLogMessage =
        thinPeer.sendExecMessageToLogAndReceive(staticMethodCallBuilder.buildExecMessage());
    logger.debug("got response: {}", getMessageContentAsPrettyJson(responseLogMessage));
    printExecMessage(responseLogMessage.getContent().getExecMessage());
    requestsSent++;

    thinPeer.close();

    long spent = System.currentTimeMillis() - start;
    if (logger.isInfoEnabled()) {
      logger.info("sent and received {} requests in {} ms", requestsSent, spent);
    }
    if (verbose) {
      err.printf("sent and received %s requests in %s ms%n", requestsSent, spent);
    }
    return requestsSent;
  }

  /**
   * Sends requests asynchronously without waiting for responses. Suitable for fire-and-forget
   * scenarios.
   *
   * @return the number of requests successfully sent
   * @throws Exception if an error occurs during the sending of requests
   */
  private int sendRequestsWithSingleClientAsync() throws Exception {
    final UUID thinPeerUuid = UUID.randomUUID();
    ThinPeer thinPeer = createLogThinPeer(thinPeerUuid);
    try {
      // init call builder
      if (className != null) {
        staticMethodCallBuilder =
            new StaticMethodCallBuilder(thinPeerUuid, className, methodName, argList);
      }

      long start = System.currentTimeMillis();
      @SuppressWarnings("unused")
      var unused = thinPeer.sendExecMessageToLog(staticMethodCallBuilder.buildExecMessage());
      int requestsSent = 1;

      if (verbose) {
        err.printf(
            "sent and received %s requests in %s ms%n",
            requestsSent, (System.currentTimeMillis() - start));
      }
      return requestsSent;
    } finally {
      thinPeer.close();
    }
  }

  /**
   * Executes the LogCall command based on the provided configuration.
   *
   * @return {@code 0} upon successful execution
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Running LogCall command...");
    }
    if (numberOfThreads == 1) {
      if (sendAndForget) {
        logger.info("running sendRequestsWithSingleClientAsync()");
        sendRequestsWithSingleClientAsync();
      } else {
        logger.info("running sendRequestsWithSingleClient()");
        sendRequestsWithSingleClient();
      }
    } else {
      logger.info("running sendRequestsWithManyClients()");
      runManyClients(
          numberOfThreads,
          verbose,
          logger,
          () ->
              sendAndForget ? sendRequestsWithSingleClientAsync() : sendRequestsWithSingleClient());
    }
    return 0;
  }

  /**
   * Prints the JSON-RPC response if the {@code printResponses} flag is enabled.
   *
   * <p>Package-private for test access.
   *
   * @param response the {@link JsonRpcResponse} to print
   */
  void printIfRequired(JsonRpcResponse response) {
    printJsonRpcResponse(response);
  }

  /**
   * Builder class for constructing execution messages for static method calls dispatched to a log.
   */
  private class StaticMethodCallBuilder extends BaseStaticMethodCallBuilder {

    /**
     * Constructs a new {@code StaticMethodCallBuilder}.
     *
     * @param thinPeerUuid the UUID of the ThinPeer
     * @param className the name of the class whose method is to be called
     * @param methodName the name of the method to call
     * @param argList the list of arguments to pass to the method
     */
    public StaticMethodCallBuilder(
        UUID thinPeerUuid, String className, String methodName, List<String> argList) {
      super(thinPeerUuid, className, methodName, argList);
    }

    /**
     * Builds an {@link ExecMessage} representing the method call.
     *
     * @return the constructed {@link ExecMessage}
     */
    public ExecMessage buildExecMessage() {
      return messageBuilder.buildClassMethod(
          thinPeerUuid,
          className,
          methodName,
          parameterTypesNamesArray,
          LogCall.this,
          null,
          parameters,
          argObjRefs);
    }
  }
}
