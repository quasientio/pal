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
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.util.Base62UuidGenerator;
import io.quasient.pal.common.util.IdGenerator;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.Params;
import io.quasient.pal.messages.types.RpcType;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import io.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * The {@code Caller} class serves as a command-line interface for sending messages to peers or logs
 * within the PAL runtime system. It parses and validates user inputs, constructs appropriate
 * messages, and manages communication with peers, either directly or through designated logs, using
 * RPC. The class supports synchronous and asynchronous operations, multithreaded request handling,
 * and configurable RPC types.
 */
@Command(
    name = "call",
    customSynopsis = "pal call [OPTIONS] [class args...]%n",
    description = "Send messages to peers or logs",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = {"DLS_DEAD_LOCAL_STORE", "URF_UNREAD_FIELD"},
    justification = "Unused field from picocli")
public class Caller extends AbstractPalSubcommand {

  /** Logger instance. */
  private final Logger logger = LoggerFactory.getLogger(Caller.class);

  /** Path to the caller properties file. */
  private static final String CALLER_PROPERTIES_PATH = "/caller.properties";

  /** Path to the consumer properties file. */
  private static final String CONSUMER_PROPERTIES_PATH = "/consumer.properties";

  /** Path to the producer properties file. */
  private static final String PRODUCER_PROPERTIES_PATH = "/producer.properties";

  /** Builder for constructing messages to be sent. */
  private final MessageBuilder messageBuilder;

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

  /** Builder for constructing main method call messages. */
  private StaticMethodCallBuilder staticMethodCallBuilder;

  /** UUID of the target peer. */
  private UUID peerUuid;

  /** Address of the target peer. */
  private String peerAddress;

  /** Name of the target peer. */
  private String peerName;

  /** List of JSON-RPC requests read from standard input. */
  private List<String> stdinRequests = new ArrayList<>();

  /** Generator for creating unique identifiers. */
  private final IdGenerator idGenerator = new Base62UuidGenerator();

  /** Parent command instance for accessing shared configurations. */
  @ParentCommand PalCommand palCommand;

  // Options

  /** Specifies the log to read from and write to. */
  @Option(
      names = {"-l", "--log"},
      paramLabel = "name",
      description = "read from and write to given log")
  private String logName;

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

  /**
   * Kafka bootstrap servers for direct access to Kafka logs without PAL_DIRECTORY.
   *
   * <p>When provided, allows accessing Kafka logs directly without connecting to the PAL directory.
   * Takes precedence over the KAFKA_SERVERS environment variable.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      paramLabel = "host:port[,host:port...]",
      description = "Kafka bootstrap servers (for direct Kafka access without -d)")
  private String kafkaServers;

  /** Identifies the peer to communicate with, either by UUID or RPC address. */
  @Option(
      names = {"-p", "--to-peer"},
      paramLabel = "uuid|HOST:PORT",
      description = "talk to peer with given UUID or RPC address")
  private String peerIdentifier;

  /** Specifies the type of RPC to use for communication. */
  @Option(
      names = {"-r", "--rpc-type"},
      paramLabel = "ZMQ_RPC|JSON_RPC",
      description = "the RPC type to use")
  private String rpcType;

  /** Specifies the method to call on the target class. */
  @Option(
      names = {"-m", "--method"},
      paramLabel = "method",
      defaultValue = "main",
      description = "method to call on the class (default: main)")
  private String methodName;

  /** Determines whether to send requests without waiting for responses. */
  @Option(
      names = {"-f", "--forget-response"},
      description = "do not wait for responses (default: false)")
  private boolean sendAndForget;

  /** Indicates whether to automatically add missing JSON-RPC request IDs. */
  @Option(
      names = {"-a", "--add-ids"},
      description = "add missing JSON-RPC request IDs (default: false)")
  private boolean autoIds;

  /** Specifies whether to print response messages received from peers or logs. */
  @Option(
      names = {"--print-responses"},
      defaultValue = "true",
      description = "print response messages (default: true)")
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

  /**
   * Thread affinity hint for the target peer. When set, the receiving peer routes execution to a
   * matching executor thread (e.g., {@code "fx-thread"} for the JavaFX Application Thread).
   */
  @Option(
      names = {"--thread-affinity"},
      paramLabel = "affinity",
      description = "thread affinity hint for the target peer (e.g., 'fx-thread')")
  private String threadAffinity;

  /** Displays the help message when requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  // Arguments

  /** Specifies the class to call. */
  @SuppressWarnings("unused")
  @Parameters(index = "0", arity = "0..1", hidden = true)
  private String className;

  /** Specifies the arguments to pass to the target class method. */
  @SuppressWarnings("unused")
  @Parameters(index = "1..*", arity = "0..*", hidden = true)
  private List<String> argList;

  /** Constructs a new {@code Caller} instance and initializes the message builder. */
  Caller() {
    this.messageBuilder = new MessageBuilder();
  }

  /**
   * Validates the user input options and arguments, ensuring that required parameters are provided
   * and configuration options are consistent.
   *
   * @throws RuntimeException if input validation fails due to missing or conflicting options.
   */
  @Override
  protected final void validateInput() {

    if (Stream.of(peerIdentifier, logName, outputLogName).noneMatch(Caller::optionGiven)) {
      throw new RuntimeException("Nowhere to call. Please specify --peer, --log or --output-log.");
    }

    if (optionGiven(outputLogName) && !optionGiven(inputLogName) && !sendAndForget) {
      throw new RuntimeException(
          "You must specify a log to read from, or else use --forget-response.");
    }

    // resolve peer identifier
    if (optionGiven(peerIdentifier)) {
      UUID parsedUuid = null;
      try {
        parsedUuid = UUID.fromString(peerIdentifier);
      } catch (IllegalArgumentException iae) {
        // never mind
      } finally {
        peerUuid = parsedUuid;
      }
      // not a valid UUID, check if it's an address or name
      if (peerUuid == null) {
        if (peerIdentifier.startsWith("tcp://") || peerIdentifier.startsWith("ws://")) {
          // It's an address
          peerAddress = peerIdentifier;
        } else {
          // It's a peer name - will be resolved later via directory lookup
          peerName = peerIdentifier;
        }
      }
    }

    // --forget-response only works with Log, not with peer
    if (optionGiven(peerIdentifier) && sendAndForget) {
      throw new RuntimeException(
          "Direct p2p talk (-p) is not compatible with -f (--forget-response) option");
    }

    // validate RPC type and endpoint
    if (optionGiven(rpcType)) {
      if (!rpcType.equals(RpcType.ZMQ_RPC.name()) && !rpcType.equals(RpcType.JSON_RPC.name())) {
        throw new RuntimeException(
            String.format(
                "Invalid RPC type. Must be %s or %s.",
                RpcType.ZMQ_RPC.name(), RpcType.JSON_RPC.name()));
      }
      if (rpcType.equals(RpcType.JSON_RPC.name())) {
        if (!optionGiven(peerIdentifier)) {
          throw new RuntimeException("You must specify a peer to talk to when using JSON-RPC.");
        }
        if (!peerIdentifier.startsWith("ws://")) {
          throw new RuntimeException("Peer address must start with ws:// when using JSON-RPC.");
        }
      }
    }

    // read stdin for raw JSON-RPC requests (1 per line), if any
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()))) {
      if (reader.ready()) {
        stdinRequests = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
          stdinRequests.add(line);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // requests are given through STDIN or through command line parameters,
    // either or
    if (!optionGiven(className) && stdinRequests.isEmpty()) {
      throw new RuntimeException(
          "You must specify a class to call or provide JSON-RPC requests through STDIN.");
    }

    // but not both
    if (optionGiven(className) && !stdinRequests.isEmpty()) {
      throw new RuntimeException(
          "Either specify a class or provide JSON-RPC requests through STDIN, but not both.");
    }

    if (peerAddress != null && peerAddress.startsWith("tcp://") && !stdinRequests.isEmpty()) {
      throw new RuntimeException(
          "JSON-RPC requests given through STDIN, but peer address does not"
              + " start with ws:// (for JSON-RPC)");
    }
  }

  /**
   * Initializes the Caller by setting up directory connections and loading necessary properties.
   *
   * @throws Exception if initialization fails due to issues with directory connections or property
   *     loading.
   */
  @Override
  protected void initialize() throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Initializing...");
    }

    if (optionGiven(className)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Will call class: {} and method: {}", className, methodName);
      }
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Will send given JSON-RPC requests");
      }
    }

    palDirectoryUrl = palCommand.getPalDirectoryConnectionString();
    initializeDirectoryConnectionProvider(palDirectoryUrl);

    loadProperties();
  }

  /**
   * Loads required configuration values from properties files.
   *
   * @throws IOException if an error occurs while reading properties files.
   */
  private void loadProperties() throws IOException {
    // caller properties
    try (final InputStream stream = Caller.class.getResourceAsStream(CALLER_PROPERTIES_PATH)) {
      properties.load(stream);
    }
    final String pollDurationProp = properties.getProperty("pollDuration");
    if (pollDurationProp != null && !pollDurationProp.trim().isEmpty()) {
      pollDuration = Long.parseLong(pollDurationProp.trim());
    }

    // load consumer properties
    try (final InputStream stream = Caller.class.getResourceAsStream(CONSUMER_PROPERTIES_PATH)) {
      consumerProperties.load(stream);
    }
    // load producer properties
    try (final InputStream stream = Caller.class.getResourceAsStream(PRODUCER_PROPERTIES_PATH)) {
      producerProperties.load(stream);
    }
  }

  /**
   * Serially sends requests using a single client.
   *
   * @return the number of requests successfully sent.
   * @throws Exception if an error occurs during the sending of requests.
   */
  private int sendRequestsWithSingleClient() throws Exception {

    long start;
    final boolean sendToPeer =
        Stream.of(peerAddress, peerUuid, peerName).anyMatch(Objects::nonNull);

    RpcType inferredRpcType = optionGiven(rpcType) ? RpcType.valueOf(rpcType) : null;

    // see if we have a PalDirectory
    boolean gotPalDir = !palDirectoryUrl.equals(PalDirectory.NO_URL);

    // create ThinPeer
    final ThinPeer thinPeer;
    final UUID thinPeerUuid = UUID.randomUUID();
    if (sendToPeer) {
      PeerInfo peerInfo;
      if (peerUuid != null) {
        peerInfo = new PeerInfo(peerUuid);
        if (inferredRpcType == null) {
          inferredRpcType = getRpcTypeForPeer(peerUuid);
        }
      } else if (peerName != null) {
        // Resolve peer name to UUID via directory lookup
        UUID resolvedUuid = lookupPeerByName(peerName);
        // Fetch full PeerInfo from directory to get RPC addresses
        peerInfo = getPalDirectory().getPeer(resolvedUuid);
        if (inferredRpcType == null) {
          inferredRpcType = getRpcTypeForPeer(resolvedUuid);
        }
      } else {
        peerInfo = new PeerInfo();
        if (peerAddress.startsWith("tcp://")) {
          peerInfo.setZmqRpcAddress(peerAddress);
          inferredRpcType = RpcType.ZMQ_RPC;
        } else if (peerAddress.startsWith("ws://")) {
          peerInfo.setJsonrpcAddress(peerAddress);
          inferredRpcType = RpcType.JSON_RPC;
        } else {
          throw new RuntimeException(
              "Peer address must start with tcp:// (for ZMQ-RPC) or ws:// (for JSON-RPC)");
        }
      }
      thinPeer =
          new ThinPeer()
              .withUuid(thinPeerUuid)
              .withDirectoryUrl(palDirectoryUrl)
              .withSelfRegistration(gotPalDir)
              .withInitialPeer(peerInfo)
              .withOutboundRpcType(inferredRpcType)
              .init();
    } else { // send to Log
      if (logName != null) {
        inputLogName = outputLogName = logName;
      }

      // Fetch log information from directory if available, or create minimal LogInfo for direct
      // mode
      LogInfo inputLog = null;
      LogInfo outputLog = null;

      if (inputLogName != null) {
        inputLog = resolveLogInfo(inputLogName);
      }

      if (outputLogName != null) {
        outputLog = resolveLogInfo(outputLogName);
      }

      thinPeer =
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
    }

    // init call builder
    if (className != null) {
      staticMethodCallBuilder =
          new StaticMethodCallBuilder(thinPeerUuid, className, methodName, argList);
    }

    // callback list and executor for async response completion
    CompletableFuture<JsonRpcResponse> responseFuture;
    ExecutorService onResponseExecutor = Executors.newFixedThreadPool(2);
    List<CompletableFuture<Void>> callbacks = new ArrayList<>();

    // send message(s)
    int requestsSent = 0;
    start = System.currentTimeMillis();
    if (inferredRpcType == RpcType.JSON_RPC) {
      if (stdinRequests == null || stdinRequests.isEmpty()) {
        // build and send 1 JSON-RPC request from cmd line args
        responseFuture = thinPeer.sendJsonRpcRequestToPeer(staticMethodCallBuilder.buildJsonRpc());
        CompletableFuture<Void> callback =
            responseFuture.thenAcceptAsync(this::printIfRequired, onResponseExecutor);
        callbacks.add(callback);
        requestsSent++;
      } else {
        // send N JSON-RPC request(s) read from stdin
        for (String jsonRpc : stdinRequests) {
          logger.debug("will now parse json-rpc request:{BEGIN}{}{END}", jsonRpc);
          if (autoIds) { // generate missing JSON-RPC request IDs
            JsonRpcRequest request = JsonRpcSerializer.fromJson(jsonRpc, JsonRpcRequest.class);
            if (request.getId() == null || request.getId().isEmpty()) {
              request.setId(idGenerator.nextId());
            }
            responseFuture = thinPeer.sendJsonRpcRequestToPeer(request);
          } else { // send raw JSON-RPC request
            responseFuture = thinPeer.sendJsonRpcRequestToPeer(jsonRpc);
          }
          CompletableFuture<Void> callback =
              responseFuture.thenAcceptAsync(this::printIfRequired, onResponseExecutor);
          callbacks.add(callback);
          requestsSent++;
        }
        if (requestsSent > 1 && logger.isDebugEnabled()) {
          logger.debug("sent {} JSON-RPC request(s) to peer", requestsSent);
        }
      }
    } else {
      // build and send 1 ExecMessage from cmd line args
      if (sendToPeer) {
        printIfRequired(thinPeer.sendToPeer(staticMethodCallBuilder.buildExecMessage()));
      } else {
        LogMessage<Message> responseLogMessage =
            thinPeer.sendExecMessageToLogAndReceive(staticMethodCallBuilder.buildExecMessage());
        logger.debug("got response: {}", getMessageContentAsPrettyJson(responseLogMessage));
        printIfRequired(responseLogMessage.getContent().getExecMessage());
      }
      requestsSent++;
    }

    // wait for all response futures to be done
    CompletableFuture.allOf(callbacks.toArray(new CompletableFuture[0])).join();

    thinPeer.close();
    onResponseExecutor.shutdownNow();

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
   * Sends requests asynchronously without waiting for responses. This method is suitable for
   * scenarios where responses are not required, such as invoking void methods. All requests are
   * sent to a log.
   *
   * @return the number of requests successfully sent.
   * @throws Exception if an error occurs during the sending of requests.
   */
  private int sendRequestsWithSingleClientAsync() throws Exception {

    if (logName != null) {
      inputLogName = outputLogName = logName;
    }

    // Fetch log information from directory if available, or create minimal LogInfo for direct mode
    LogInfo inputLog = null;
    LogInfo outputLog = null;

    if (inputLogName != null) {
      inputLog = resolveLogInfo(inputLogName);
    }

    if (outputLogName != null) {
      outputLog = resolveLogInfo(outputLogName);
    }

    // see if we have a PalDirectory
    boolean gotPalDir = !palDirectoryUrl.equals(PalDirectory.NO_URL);

    // create ThinPeer
    final UUID thinPeerUuid = UUID.randomUUID();
    ThinPeer thinPeer =
        new ThinPeer()
            .withUuid(thinPeerUuid)
            .withDirectoryUrl(palDirectoryUrl)
            .withSelfRegistration(gotPalDir)
            .withInputLog(inputLog)
            .withOutputLog(outputLog)
            .withConsumerProperties(consumerProperties)
            .withProducerProperties(producerProperties);
    try {
      if (pollDuration != null) {
        thinPeer.withPollingDuration(pollDuration);
      }
      thinPeer.init();

      // init call builder
      if (className != null) {
        staticMethodCallBuilder =
            new StaticMethodCallBuilder(thinPeerUuid, className, methodName, argList);
      }

      // send message(s)
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
   * Sends requests using multiple clients in parallel threads. This method leverages multiple
   * threads to send requests concurrently, either synchronously or asynchronously based on
   * configuration.
   *
   * @throws Exception if an error occurs during the sending of requests.
   */
  @SuppressFBWarnings(
      value = "REC_CATCH_EXCEPTION",
      justification =
          "Catches exceptions from sendRequestsWithSingleClient methods that throw Exception")
  private void sendRequestsWithManyClients() throws Exception {

    if (numberOfThreads <= 1) {
      throw new IllegalArgumentException(
          "Method must be called with clients > 1. clients = " + numberOfThreads);
    }

    Thread[] clientList = new Thread[numberOfThreads];
    final AtomicInteger requestsSent = new AtomicInteger(0);
    final CountDownLatch latch = new CountDownLatch(numberOfThreads);

    // start timing
    long start = System.currentTimeMillis();

    // create all threads
    IntStream.range(0, numberOfThreads)
        .forEach(
            i -> {
              Thread client =
                  new Thread(
                      () -> {
                        try {
                          int sent;
                          if (sendAndForget) {
                            sent = sendRequestsWithSingleClientAsync();
                          } else {
                            sent = sendRequestsWithSingleClient();
                          }
                          requestsSent.getAndAdd(sent);
                        } catch (Exception e) {
                          logger.error("Caught error running requests", e);
                        } finally {
                          latch.countDown();
                        }
                      });
              clientList[i] = client;
            });

    // then start all clients at once
    Arrays.stream(clientList).forEach(Thread::start);

    // wait for threads to finish
    latch.await();

    if (verbose) {
      err.printf(
          "sent %s requests with %s client(s) in %s ms%n",
          requestsSent.get(), numberOfThreads, (System.currentTimeMillis() - start));
    }
  }

  /**
   * Executes the Caller command based on the provided configuration and user input.
   *
   * @return {@code 0} upon successful execution.
   * @throws Exception if an error occurs during command execution.
   */
  @Override
  protected int runCommand() throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Running command...");
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
      sendRequestsWithManyClients();
    }
    return 0;
  }

  /**
   * Looks up a peer UUID by its name in the directory.
   *
   * @param peerName the name of the peer to look up.
   * @return the UUID of the peer with the given name.
   * @throws RuntimeException if no peer with the given name exists.
   * @throws ExecutionException if an error occurs while fetching peer information.
   * @throws InterruptedException if the operation is interrupted.
   */
  private UUID lookupPeerByName(String peerName) throws ExecutionException, InterruptedException {
    var peers = getPalDirectory().listPeers();
    for (PeerInfo peer : peers) {
      if (peerName.equals(peer.getName())) {
        return peer.getUuid();
      }
    }
    throw new RuntimeException("No peer found with name: " + peerName);
  }

  /**
   * Retrieves the RPC type supported by the specified peer.
   *
   * @param peerUuid the UUID of the peer.
   * @return the {@link RpcType} supported by the peer.
   * @throws ExecutionException if an error occurs while fetching peer information.
   * @throws InterruptedException if the operation is interrupted.
   */
  private RpcType getRpcTypeForPeer(UUID peerUuid) throws ExecutionException, InterruptedException {
    PeerInfo peerInfo = getPalDirectory().getPeer(peerUuid);
    boolean listensToRpc = peerInfo.getZmqRpcAddress() != null;
    boolean listensToJsonRpc = peerInfo.getJsonrpcAddress() != null;
    if (listensToRpc && listensToJsonRpc) {
      if (!optionGiven(rpcType)) {
        throw new RuntimeException(
            "Peer listens to both ZMQ-RPC and JSON-RPC. "
                + "Please specify the RPC type with -t or --rpc-type");
      } else {
        return RpcType.valueOf(rpcType);
      }
    }
    if (listensToRpc) {
      return RpcType.ZMQ_RPC;
    }
    if (listensToJsonRpc) {
      return RpcType.JSON_RPC;
    }
    throw new RuntimeException("Peer does not have any RPC address");
  }

  /**
   * Prints the JSON-RPC response if the {@code printResponses} flag is enabled.
   *
   * @param response the {@link JsonRpcResponse} to print.
   */
  private void printIfRequired(JsonRpcResponse response) {
    if (!printResponses) {
      return;
    }
    try {
      out.println(JsonRpcSerializer.toJson(response));
    } catch (JsonSerializationException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Prints the execution message response if the {@code printResponses} flag is enabled.
   *
   * @param response the {@link ExecMessage} to print.
   */
  private void printIfRequired(ExecMessage response) {
    if (!printResponses) {
      return;
    }
    if (response.getReturnValue() != null) {
      print(response.getReturnValue());
    } else if (response.getRaisedThrowable() != null) {
      print(response.getRaisedThrowable());
    }
  }

  /**
   * Prints the return value of an executed method if the {@code printResponses} flag is enabled and
   * the return value is not void.
   *
   * @param returnValue the {@link ReturnValue} to print.
   */
  private void print(ReturnValue returnValue) {
    if (!printResponses) {
      return;
    }
    if (returnValue.getIsVoid()) {
      return;
    }
    Obj object = returnValue.getObject();
    if (object != null) {
      out.println(object.getValue());
    }
  }

  /**
   * Prints the raised throwable if the {@code printResponses} flag is enabled.
   *
   * @param raisedThrowable the {@link RaisedThrowable} to print.
   */
  private void print(RaisedThrowable raisedThrowable) {
    if (!printResponses) {
      return;
    }
    out.println(ColferUtils.format(raisedThrowable));
  }

  /**
   * Resolves log information by name, attempting to use PAL directory if available, or creating a
   * minimal LogInfo for direct mode.
   *
   * @param logNameOrPath the log name or path to resolve
   * @return LogInfo for the specified log, or null if resolution fails
   */
  private LogInfo resolveLogInfo(String logNameOrPath) {
    // Try to get PalDirectory if available
    try {
      if (directoryConnectionProvider != null) {
        var palDirOpt = directoryConnectionProvider.get();
        if (palDirOpt.isPresent()) {
          // Registry mode: lookup log by name
          LogInfo logInfo = palDirOpt.get().getLogInfo(logNameOrPath);
          if (logInfo != null) {
            return logInfo;
          }
          // Not found in directory, fall through to direct mode
        }
      }
    } catch (RuntimeException | ExecutionException | InterruptedException e) {
      logger.debug("PalDirectory not available: {}", e.getMessage());
    }

    // Direct mode: create minimal LogInfo
    String chronicleFilePrefix = "file:";
    boolean isChronicleLog = logNameOrPath.startsWith(chronicleFilePrefix);

    if (isChronicleLog) {
      // Chronicle log: strip prefix and create LogInfo
      String path = logNameOrPath.substring(chronicleFilePrefix.length());
      LogInfo logInfo = new LogInfo(path);
      logInfo.setLogType(LogInfo.LogType.CHRONICLE);
      logger.info("Using Chronicle log in direct mode: {}", path);
      return logInfo;
    } else {
      // Kafka log: need bootstrap servers
      String kafkaServersToUse = kafkaServers != null ? kafkaServers : getKafkaServers();
      if (kafkaServersToUse != null) {
        LogInfo logInfo = new LogInfo(logNameOrPath, kafkaServersToUse);
        logInfo.setLogType(LogInfo.LogType.KAFKA);
        logger.info(
            "Using Kafka log in direct mode: topic={}, servers={}",
            logNameOrPath,
            kafkaServersToUse);
        return logInfo;
      } else {
        // Cannot determine log type without Kafka servers
        // Create a basic LogInfo and let it be resolved later
        logger.warn(
            "Log name '{}' does not have 'file:' prefix and no Kafka servers provided. "
                + "Treating as bare log name.",
            logNameOrPath);
        return new LogInfo(logNameOrPath);
      }
    }
  }

  /**
   * Builder class for constructing execution messages and JSON-RPC requests for the main method
   * call.
   */
  private class StaticMethodCallBuilder {
    /** The UUID of the ThinPeer initiating the call. */
    private final UUID thinPeerUuid;

    /** The array of parameter types for the target method (always String[]). */
    final Class<?>[] parameterTypes = new Class[] {String[].class};

    /** The simple name of the method to invoke. */
    private final String methodName;

    /** The fully qualified name of the class containing the method. */
    private final String className;

    /** The array of parameter type names, derived from {@code parameterTypes}. */
    private final String[] parameterTypesNamesArray;

    /** The actual parameter values to pass to the method (wrapped in an Object[]). */
    private final Object[] parameters;

    /** References for any object parameters, used in JSON-RPC argument handling. */
    private final ObjectRef[] argObjRefs;

    /**
     * Constructs a new {@code StaticMethodCallBuilder} with the specified parameters.
     *
     * @param thinPeerUuid the UUID of the ThinPeer.
     * @param className the name of the class whose method is to be called.
     * @param methodName the name of the method to call.
     * @param argList the list of arguments to pass to the method.
     */
    public StaticMethodCallBuilder(
        UUID thinPeerUuid, String className, String methodName, List<String> argList) {
      // create reusable arrays for message construction
      parameterTypesNamesArray = new String[parameterTypes.length];
      IntStream.range(0, parameterTypes.length)
          .forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
      parameters = new Object[] {new String[] {}};
      argObjRefs = new ObjectRef[parameterTypes.length];
      this.thinPeerUuid = thinPeerUuid;
      this.className = className;
      this.methodName = methodName;
      if (argList != null) {
        parameters[0] = argList.toArray(new String[0]);
      }
    }

    /**
     * Builds an {@link ExecMessage} representing the method call.
     *
     * @return the constructed {@link ExecMessage}.
     */
    public ExecMessage buildExecMessage() {
      ExecMessage msg =
          messageBuilder.buildClassMethod(
              thinPeerUuid,
              className,
              methodName,
              parameterTypesNamesArray,
              Caller.this,
              null,
              parameters,
              argObjRefs);
      MessageBuilder.withThreadAffinity(msg, threadAffinity);
      return msg;
    }

    /**
     * Builds a {@link JsonRpcRequest} representing the method call.
     *
     * @return the constructed {@link JsonRpcRequest}.
     */
    public JsonRpcRequest buildJsonRpc() {
      Params.Builder paramsBuilder =
          new Params.Builder().withMethod(methodName).withType(className);

      if (argList != null) {
        paramsBuilder.addArg(
            new Argument.Builder()
                .withType("[Ljava.lang.String;")
                .withValue(argList.toArray(new String[0]))
                .build());
      }

      if (threadAffinity != null) {
        paramsBuilder.withThreadAffinity(threadAffinity);
      }

      return new JsonRpcRequest.Builder()
          .withId(UUID.randomUUID().toString())
          .withParams(paramsBuilder.build())
          .build();
    }
  }
}
