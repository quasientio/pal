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

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import com.google.gson.Gson;
import com.quasient.pal.common.cli.PalCommand;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.util.Base62UuidGenerator;
import com.quasient.pal.common.util.IdGenerator;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.messages.colfer.RaisedThrowable;
import com.quasient.pal.messages.colfer.ReturnValue;
import com.quasient.pal.messages.jsonrpc.Argument;
import com.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import com.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import com.quasient.pal.messages.jsonrpc.Params;
import com.quasient.pal.messages.types.RpcType;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import com.quasient.pal.serdes.jsonrpc.JsonSerializationException;
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

  /** Prefix used for log topics. */
  private String logPrefix;

  /** URL for connecting to the PAL directory service. */
  private String palDirectoryUrl;

  /** Builder for constructing main method call messages. */
  private MainMethodCallBuilder mainMethodCallBuilder;

  /** UUID of the target peer. */
  private UUID peerUuid;

  /** Address of the target peer. */
  private String peerAddress;

  /** Gson instance for JSON processing. */
  private final Gson gson = new Gson();

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
      names = {"-i", "--from-log"},
      paramLabel = "name",
      description = "read from given log")
  private String inLogName;

  /** Specifies the log to write to. */
  @Option(
      names = {"-o", "--to-log"},
      paramLabel = "name",
      description = "write to given log")
  private String outLogName;

  /** Identifies the peer to communicate with, either by UUID or RPC address. */
  @Option(
      names = {"-p", "--to-peer"},
      paramLabel = "uuid|HOST:PORT",
      description = "talk to peer with given UUID or RPC address")
  private String peerIdentifier;

  /** Specifies the type of RPC to use for communication. */
  @Option(
      names = {"-r", "--rpc-type"},
      paramLabel = "BIN_RPC|JSON_RPC",
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
      description = "print response messages (default: false)")
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

    if (Stream.of(peerIdentifier, logName, outLogName).noneMatch(Caller::optionGiven)) {
      throw new RuntimeException("Nowhere to call. Please specify --peer, --log or --out-log.");
    }

    if (optionGiven(outLogName) && !optionGiven(inLogName) && !sendAndForget) {
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
      // not a valid UUID, must be an address then
      if (peerUuid == null) {
        if (!peerIdentifier.startsWith("tcp://") && !peerIdentifier.startsWith("ws://")) {
          throw new RuntimeException(
              "Peer address must start with tcp:// (for ZMQ-RPC) or ws:// (for JSON-RPC)");
        }
        peerAddress = peerIdentifier;
      }
    }

    // --forget-response only works with Log, not with peer
    if (optionGiven(peerIdentifier) && sendAndForget) {
      throw new RuntimeException(
          "Direct p2p talk (-p) is not compatible with -f (--forget-response) option");
    }

    // validate RPC type and endpoint
    if (optionGiven(rpcType)) {
      if (!rpcType.equals(RpcType.BIN_RPC.name()) && !rpcType.equals(RpcType.JSON_RPC.name())) {
        throw new RuntimeException("Invalid RPC type. Must be RPC or JSONRPC.");
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
    final String logPrefixProp = properties.getProperty("kafkaTopicPrefix");
    if (pollDurationProp != null && !pollDurationProp.trim().isEmpty()) {
      pollDuration = Long.parseLong(pollDurationProp.trim());
    }
    if (logPrefixProp != null && !logPrefixProp.trim().isEmpty()) {
      logPrefix = logPrefixProp.trim();
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
    final boolean sendToPeer = Stream.of(peerAddress, peerUuid).anyMatch(Objects::nonNull);

    RpcType inferredRpcType = optionGiven(rpcType) ? RpcType.valueOf(rpcType) : null;

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
      } else {
        peerInfo = new PeerInfo();
        if (peerAddress.startsWith("tcp://")) {
          peerInfo.setZmqRpcAddress(peerAddress);
          inferredRpcType = RpcType.BIN_RPC;
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
              .withSelfRegistration(true)
              .withInitialPeer(peerInfo)
              .withOutboundRpcType(inferredRpcType)
              .init();
    } else { // send to Log
      if (logName != null) {
        inLogName = outLogName = logName;
      }
      LogInfo inLog = inLogName == null ? null : new LogInfo(inLogName);
      LogInfo outLog = outLogName == null ? null : new LogInfo(outLogName);
      thinPeer =
          new ThinPeer()
              .withUuid(thinPeerUuid)
              .withDirectoryUrl(palDirectoryUrl)
              .withSelfRegistration(true)
              .withInLog(inLog)
              .withOutLog(outLog)
              .withConsumerProperties(consumerProperties)
              .withProducerProperties(producerProperties);
      if (pollDuration != null) {
        thinPeer.withPollingDuration(pollDuration);
      }
      if (logPrefix != null) {
        thinPeer.withLogPrefix(logPrefix);
      }
      thinPeer.init();
    }

    // init call builder
    if (className != null) {
      mainMethodCallBuilder =
          new MainMethodCallBuilder(thinPeerUuid, className, methodName, argList);
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
        responseFuture = thinPeer.sendJsonRpcRequestToPeer(mainMethodCallBuilder.buildJsonRpc());
        CompletableFuture<Void> callback =
            responseFuture.thenAcceptAsync(this::printIfRequired, onResponseExecutor);
        callbacks.add(callback);
        requestsSent++;
      } else {
        // send N JSON-RPC request(s) read from stdin
        for (String jsonRpc : stdinRequests) {
          if (autoIds) { // generate missing JSON-RPC request IDs
            JsonRpcRequest request = gson.fromJson(jsonRpc, JsonRpcRequest.class);
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
        printIfRequired(thinPeer.sendToPeer(mainMethodCallBuilder.buildExecMessage()));
      } else {
        LogMessage<Message> responseLogMessage =
            thinPeer.sendExecMessageToLogAndReceive(mainMethodCallBuilder.buildExecMessage());
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
      inLogName = outLogName = logName;
    }
    LogInfo inLog = inLogName == null ? null : new LogInfo(inLogName);
    LogInfo outLog = outLogName == null ? null : new LogInfo(outLogName);

    // create ThinPeer
    final UUID thinPeerUuid = UUID.randomUUID();
    try (ThinPeer thinPeer =
        new ThinPeer()
            .withUuid(thinPeerUuid)
            .withDirectoryUrl(palDirectoryUrl)
            .withSelfRegistration(true)
            .withInLog(inLog)
            .withOutLog(outLog)
            .withConsumerProperties(consumerProperties)
            .withProducerProperties(producerProperties)) {
      if (pollDuration != null) {
        thinPeer.withPollingDuration(pollDuration);
      }
      if (logPrefix != null) {
        thinPeer.withLogPrefix(logPrefix);
      }
      thinPeer.init();

      // send message(s)
      long start = System.currentTimeMillis();
      var unused = thinPeer.sendExecMessageToLog(mainMethodCallBuilder.buildExecMessage());
      int requestsSent = 1;

      if (verbose) {
        err.printf(
            "sent and received %s requests in %s ms%n",
            requestsSent, (System.currentTimeMillis() - start));
      }

      return requestsSent;
    }
  }

  /**
   * Sends requests using multiple clients in parallel threads. This method leverages multiple
   * threads to send requests concurrently, either synchronously or asynchronously based on
   * configuration.
   *
   * @throws Exception if an error occurs during the sending of requests.
   */
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
            "Peer listens to both RPC and JSON-RPC. "
                + "Please specify the RPC type with -t or --rpc-type");
      } else {
        return RpcType.valueOf(rpcType);
      }
    }
    if (listensToRpc) {
      return RpcType.BIN_RPC;
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
   * Builder class for constructing execution messages and JSON-RPC requests for the main method
   * call.
   */
  private class MainMethodCallBuilder {
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
     * Constructs a new {@code MainMethodCallBuilder} with the specified parameters.
     *
     * @param thinPeerUuid the UUID of the ThinPeer.
     * @param className the name of the class whose method is to be called.
     * @param methodName the name of the method to call.
     * @param argList the list of arguments to pass to the method.
     */
    public MainMethodCallBuilder(
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
      return messageBuilder.buildClassMethod(
          thinPeerUuid,
          className,
          methodName,
          parameterTypesNamesArray,
          Caller.this,
          null,
          parameters,
          argObjRefs);
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

      return new JsonRpcRequest.Builder()
          .withId(UUID.randomUUID().toString())
          .withParams(paramsBuilder.build())
          .build();
    }
  }
}
