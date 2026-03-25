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
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.util.Base62UuidGenerator;
import io.quasient.pal.common.util.IdGenerator;
import io.quasient.pal.cxn.ThinPeer;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.Params;
import io.quasient.pal.messages.types.RpcType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * Sends RPC calls to a specific peer.
 *
 * <p>This is the peer-specific call command for the {@code pal peer call} pattern. It accepts a
 * peer identifier (UUID, address, or name) as a positional argument and sends method invocation
 * requests via ZMQ or JSON-RPC. Supports synchronous operations, multithreaded request handling,
 * configurable RPC types, and thread affinity hints.
 *
 * <p>Alternatively, raw JSON-RPC requests can be piped via standard input (one per line) instead of
 * specifying a class name. The two modes are mutually exclusive.
 *
 * <p>Usage examples:
 *
 * <pre>
 *   pal peer call 550e8400-e29b-41d4-a716-446655440000 com.example.Main arg1 arg2
 *   pal peer call tcp://localhost:5555 com.example.Main
 *   pal peer call my-peer com.example.Main -r ZMQ_RPC --thread-affinity fx-thread
 *   echo '{"jsonrpc":"2.0","method":"com.example.Main","id":1}' | pal peer call ws://localhost:8080
 * </pre>
 */
@Command(
    name = "call",
    customSynopsis =
        "pal peer call [OPTIONS] PEER [class args...]%n"
            + "  or: <json-rpc-requests> | pal peer call [OPTIONS] PEER%n",
    description = "Send RPC calls to a peer (via class name or JSON-RPC on stdin)",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = {"DLS_DEAD_LOCAL_STORE", "URF_UNREAD_FIELD"},
    justification = "Unused field from picocli")
class PeerCall extends AbstractCallCommand {

  /** Logger instance. */
  private final Logger logger = LoggerFactory.getLogger(PeerCall.class);

  /** URL for connecting to the PAL directory service. */
  private String palDirectoryUrl;

  /** Builder for constructing static method call messages. */
  private StaticMethodCallBuilder staticMethodCallBuilder;

  /** UUID of the target peer (parsed from peerIdentifier). */
  private UUID peerUuid;

  /** Address of the target peer (parsed from peerIdentifier). */
  private String peerAddress;

  /** Name of the target peer (parsed from peerIdentifier). */
  private String peerName;

  /** List of JSON-RPC requests read from standard input. */
  private List<String> stdinRequests = new ArrayList<>();

  /** Generator for creating unique identifiers. */
  private final IdGenerator idGenerator = new Base62UuidGenerator();

  /** Parent command instance for accessing shared configurations. */
  @ParentCommand PalCommand palCommand;

  // Positional arguments

  /** Peer identifier: UUID, address ({@code tcp://} or {@code ws://}), or name. */
  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "PEER",
      description = "peer UUID, address (tcp:// or ws://), or name")
  private String peerIdentifier;

  /** The class whose method is to be called. */
  @SuppressWarnings("unused")
  @Parameters(index = "1", arity = "0..1", hidden = true)
  private String className;

  /** Arguments to pass to the target class method. */
  @SuppressWarnings("unused")
  @Parameters(index = "2..*", arity = "0..*", hidden = true)
  private List<String> argList;

  // Options

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

  /** Indicates whether to automatically add missing JSON-RPC request IDs. */
  @Option(
      names = {"-a", "--add-ids"},
      description = "add missing JSON-RPC request IDs (default: false)")
  private boolean autoIds;

  /** Specifies whether to print response messages received from the peer. */
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

  /**
   * Thread affinity hint for the target peer. When set, the receiving peer routes execution to a
   * matching executor thread (e.g., {@code "fx-thread"} for the JavaFX Application Thread).
   */
  @Option(
      names = {"--thread-affinity"},
      paramLabel = "affinity",
      description = "thread affinity hint for the target peer (e.g., 'fx-thread')")
  private String threadAffinity;

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

  /** Constructs a new {@code PeerCall} instance. */
  PeerCall() {}

  /** {@inheritDoc} */
  @Override
  protected boolean isPrintResponses() {
    return printResponses;
  }

  /**
   * Validates the user input options and arguments for peer-specific RPC calls.
   *
   * @throws RuntimeException if input validation fails due to missing or conflicting options
   */
  @Override
  protected final void validateInput() {
    if (!optionGiven(peerIdentifier)) {
      throw new RuntimeException(
          "Peer identifier is required. Usage: pal peer call <PEER> [class args...]");
    }

    // resolve peer identifier: UUID, address, or name
    UUID parsedUuid = null;
    try {
      parsedUuid = UUID.fromString(peerIdentifier);
    } catch (IllegalArgumentException iae) {
      // not a UUID
    } finally {
      peerUuid = parsedUuid;
    }
    if (peerUuid == null) {
      if (peerIdentifier.startsWith("tcp://") || peerIdentifier.startsWith("ws://")) {
        peerAddress = peerIdentifier;
      } else {
        peerName = peerIdentifier;
      }
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
        if (peerAddress == null || !peerAddress.startsWith("ws://")) {
          throw new RuntimeException("Peer address must start with ws:// when using JSON-RPC.");
        }
      }
    }

    // read stdin for raw JSON-RPC requests (1 per line), if any
    stdinRequests = readStdinRequests();

    // validate mutual exclusivity of className and stdinRequests
    validateClassNameOrStdin(className, stdinRequests, "JSON-RPC requests");

    if (peerAddress != null && peerAddress.startsWith("tcp://") && !stdinRequests.isEmpty()) {
      throw new RuntimeException(
          "JSON-RPC requests given through STDIN, but peer address does not"
              + " start with ws:// (for JSON-RPC)");
    }
  }

  /**
   * Initializes the PeerCall by setting up directory connections.
   *
   * @throws Exception if initialization fails due to issues with directory connections
   */
  @Override
  protected void initialize() throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Initializing PeerCall...");
      if (optionGiven(className)) {
        logger.debug("Will call class: {} and method: {}", className, methodName);
      } else {
        logger.debug("Will send given JSON-RPC requests");
      }
    }
    palDirectoryUrl = palCommand.getPalDirectoryConnectionString();
    initializeDirectoryConnectionProvider(palDirectoryUrl);
  }

  /**
   * Serially sends requests to a peer using a single client.
   *
   * @return the number of requests successfully sent
   * @throws Exception if an error occurs during the sending of requests
   */
  private int sendRequestsWithSingleClient() throws Exception {
    long start;
    RpcType inferredRpcType = optionGiven(rpcType) ? RpcType.valueOf(rpcType) : null;
    boolean gotPalDir = !palDirectoryUrl.equals(PalDirectory.NO_URL);

    // resolve peer and create ThinPeer
    final UUID thinPeerUuid = UUID.randomUUID();
    PeerInfo peerInfo;
    if (peerUuid != null) {
      peerInfo = new PeerInfo(peerUuid);
      if (inferredRpcType == null) {
        inferredRpcType = getRpcTypeForPeer(peerUuid);
      }
    } else if (peerName != null) {
      UUID resolvedUuid = lookupPeerByName(peerName);
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

    final ThinPeer thinPeer =
        new ThinPeer()
            .withUuid(thinPeerUuid)
            .withDirectoryUrl(palDirectoryUrl)
            .withSelfRegistration(gotPalDir)
            .withInitialPeer(peerInfo)
            .withOutboundRpcType(inferredRpcType)
            .init();

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
            responseFuture.thenAcceptAsync(this::printJsonRpcResponse, onResponseExecutor);
        callbacks.add(callback);
        requestsSent++;
      } else {
        // send N JSON-RPC request(s) read from stdin
        for (String jsonRpc : stdinRequests) {
          logger.debug("will now parse json-rpc request:{BEGIN}{}{END}", jsonRpc);
          if (autoIds) {
            JsonRpcRequest request = JsonRpcSerializer.fromJson(jsonRpc, JsonRpcRequest.class);
            if (request.getId() == null || request.getId().isEmpty()) {
              request.setId(idGenerator.nextId());
            }
            responseFuture = thinPeer.sendJsonRpcRequestToPeer(request);
          } else {
            responseFuture = thinPeer.sendJsonRpcRequestToPeer(jsonRpc);
          }
          CompletableFuture<Void> callback =
              responseFuture.thenAcceptAsync(this::printJsonRpcResponse, onResponseExecutor);
          callbacks.add(callback);
          requestsSent++;
        }
        if (requestsSent > 1 && logger.isDebugEnabled()) {
          logger.debug("sent {} JSON-RPC request(s) to peer", requestsSent);
        }
      }
    } else {
      // build and send 1 ExecMessage from cmd line args
      printExecMessage(thinPeer.sendToPeer(staticMethodCallBuilder.buildExecMessage()));
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
   * Executes the PeerCall command based on the provided configuration.
   *
   * @return {@code 0} upon successful execution
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Running PeerCall command...");
    }
    if (numberOfThreads == 1) {
      logger.info("running sendRequestsWithSingleClient()");
      sendRequestsWithSingleClient();
    } else {
      logger.info("running sendRequestsWithManyClients()");
      runManyClients(numberOfThreads, verbose, logger, this::sendRequestsWithSingleClient);
    }
    return 0;
  }

  /**
   * Looks up a peer UUID by its name in the directory.
   *
   * @param name the name of the peer to look up
   * @return the UUID of the peer with the given name
   * @throws RuntimeException if no peer with the given name exists
   * @throws ExecutionException if an error occurs while fetching peer information
   * @throws InterruptedException if the operation is interrupted
   */
  private UUID lookupPeerByName(String name) throws ExecutionException, InterruptedException {
    var peers = getPalDirectory().listPeers();
    for (PeerInfo peer : peers) {
      if (name.equals(peer.getName())) {
        return peer.getUuid();
      }
    }
    throw new RuntimeException("No peer found with name: " + name);
  }

  /**
   * Retrieves the RPC type supported by the specified peer.
   *
   * @param uuid the UUID of the peer
   * @return the {@link RpcType} supported by the peer
   * @throws ExecutionException if an error occurs while fetching peer information
   * @throws InterruptedException if the operation is interrupted
   */
  RpcType getRpcTypeForPeer(UUID uuid) throws ExecutionException, InterruptedException {
    PeerInfo peerInfo = getPalDirectory().getPeer(uuid);
    boolean listensToRpc = peerInfo.getZmqRpcAddress() != null;
    boolean listensToJsonRpc = peerInfo.getJsonrpcAddress() != null;
    if (listensToRpc && listensToJsonRpc) {
      if (!optionGiven(rpcType)) {
        throw new RuntimeException(
            "Peer listens to both ZMQ-RPC and JSON-RPC. "
                + "Please specify the RPC type with -r or --rpc-type");
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
   * Builder class for constructing execution messages and JSON-RPC requests for static method
   * calls.
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
      ExecMessage msg =
          messageBuilder.buildClassMethod(
              thinPeerUuid,
              className,
              methodName,
              parameterTypesNamesArray,
              PeerCall.this,
              null,
              parameters,
              argObjRefs);
      MessageBuilder.withThreadAffinity(msg, threadAffinity);
      return msg;
    }

    /**
     * Builds a {@link JsonRpcRequest} representing the method call.
     *
     * @return the constructed {@link JsonRpcRequest}
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
