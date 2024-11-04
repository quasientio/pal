/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.tools.cli;

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import com.google.gson.Gson;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.ittera.pal.common.cli.PalCommand;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.RaisedThrowable;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.RpcType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "call",
    customSynopsis = "pal call [OPTIONS] [class args...]%n",
    description = "Send messages to peers or logs",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
public class Caller extends AbstractPalSubcommand {

  private final Logger logger = LoggerFactory.getLogger(Caller.class);
  private static final String CALLER_PROPERTIES_PATH = "/caller.properties";
  private static final String CONSUMER_PROPERTIES_PATH = "/consumer.properties";
  private static final String PRODUCER_PROPERTIES_PATH = "/producer.properties";
  private final MessageBuilder messageBuilder;
  private final Properties properties = new Properties();
  private final Properties consumerProperties = new Properties();
  private final Properties producerProperties = new Properties();
  private Long pollDuration;
  private String logPrefix;
  private String palDirectoryUrl;
  private MainMethodCallBuilder mainMethodCallBuilder;
  private UUID peerUuid;
  private String peerAddress;
  private final Gson gson = new Gson();
  private List<String> stdinRequests = new ArrayList<>();

  @ParentCommand PalCommand palCommand;

  // Options
  @Option(
      names = {"-l", "--log"},
      paramLabel = "name",
      description = "read from and write to given log")
  private String logName;

  @Option(
      names = {"-i", "--from-log"},
      paramLabel = "name",
      description = "read from given log")
  private String inLogName;

  @Option(
      names = {"-o", "--to-log"},
      paramLabel = "name",
      description = "write to given log")
  private String outLogName;

  @Option(
      names = {"-p", "--to-peer"},
      paramLabel = "uuid|HOST:PORT",
      description = "talk to peer with given UUID or RPC address")
  private String peerIdentifier;

  @Option(
      names = {"-r", "--rpc-type"},
      paramLabel = "RPC|JSONRPC",
      description = "the RPC type to use")
  private String rpcType;

  @Option(
      names = {"-m", "--method"},
      paramLabel = "method",
      defaultValue = "main",
      description = "method to call on the class (default: main)")
  private String methodName;

  @Option(
      names = {"-f", "--forget-reply"},
      description = "do not wait for replies (default: false)")
  private boolean sendAndForget;

  @Option(
      names = {"-a", "--add-ids"},
      description = "add missing JSON-RPC request IDs (default: false)")
  private boolean autoIds;

  @Option(
      names = {"-t", "--num-threads"},
      defaultValue = "1",
      paramLabel = "NUM_THREADS",
      description = "number of threads, i.e. clients to use (default: 1)")
  private int numberOfThreads;

  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  // Arguments
  @SuppressWarnings("unused")
  @Parameters(index = "0", arity = "0..1", hidden = true)
  private String className;

  @SuppressWarnings("unused")
  @Parameters(index = "1..*", arity = "0..*", hidden = true)
  private List<String> argList;

  Caller() {
    this.messageBuilder = new MessageBuilder();
  }

  @Override
  protected final void validateInput() {

    if (Stream.of(peerIdentifier, logName, outLogName).noneMatch(Caller::optionGiven)) {
      throw new RuntimeException("Nowhere to call. Please specify --peer, --log or --out-log.");
    }

    if (optionGiven(outLogName) && !optionGiven(inLogName) && !sendAndForget) {
      throw new RuntimeException(
          "You must specify a log to read from, or else use --forget-reply.");
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

    // --forget-reply only works with Log, not with peer
    if (optionGiven(peerIdentifier) && sendAndForget) {
      throw new RuntimeException(
          "Direct p2p talk (-p) is not compatible with -f (--forget-reply) option");
    }

    // validate RPC type and endpoint
    if (optionGiven(rpcType)) {
      if (!rpcType.equals(RpcType.RPC.name()) && !rpcType.equals(RpcType.JSONRPC.name())) {
        throw new RuntimeException("Invalid RPC type. Must be RPC or JSONRPC.");
      }
      if (rpcType.equals(RpcType.JSONRPC.name())) {
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
   * Serially sends requests in a single (ThinPeer) thread. With log IO 1st req is sent to Log,
   * waits for Future reply, then sends all other directly to the peer which replied.
   *
   * <p>In p2p mode (-p), it sends all directly to peer.
   *
   * @return number of requests sent
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
          peerInfo.setRpcAddress(peerAddress);
          inferredRpcType = RpcType.RPC;
        } else if (peerAddress.startsWith("ws://")) {
          peerInfo.setJsonrpcAddress(peerAddress);
          inferredRpcType = RpcType.JSONRPC;
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
    // send message(s)
    int requestsSent = 0;
    start = System.currentTimeMillis();
    List<CompletableFuture<JsonRpcResponse>> jsonRpcResponseFutures = new ArrayList<>();
    if (inferredRpcType == RpcType.JSONRPC) {
      if (stdinRequests == null || stdinRequests.isEmpty()) {
        // build and send 1 JSON-RPC request from cmd line args
        jsonRpcResponseFutures.add(
            thinPeer.sendJsonRpcRequestToPeer(mainMethodCallBuilder.buildJsonRpc()));
        requestsSent++;
      } else {
        // send N JSON-RPC request(s) read from stdin
        for (String jsonRpc : stdinRequests) {
          if (autoIds) { // generate missing JSON-RPC request IDs
            JsonRpcRequest request = gson.fromJson(jsonRpc, JsonRpcRequest.class);
            if (request.getId() == null || request.getId().isEmpty()) {
              request.setId(UUID.randomUUID().toString());
            }
            jsonRpcResponseFutures.add(thinPeer.sendJsonRpcRequestToPeer(request));
          } else { // send raw JSON-RPC request
            jsonRpcResponseFutures.add(thinPeer.sendJsonRpcRequestToPeer(jsonRpc));
          }
          requestsSent++;
        }
        if (requestsSent > 1 && logger.isDebugEnabled()) {
          logger.debug("sent {} JSON-RPC request(s) to peer", requestsSent);
        }
      }
    } else {
      // build and send 1 ExecMessage from cmd line args
      print(thinPeer.sendAndReceive(mainMethodCallBuilder.buildExecMessage()));
      requestsSent++;
    }

    // wait for all replies
    for (CompletableFuture<JsonRpcResponse> future : jsonRpcResponseFutures) {
      print(future.get());
    }

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
   * Use this method when no direct peer-to-peer talk is available or desirable. Sends all requests
   * to log, and doesn't wait for replies, useful for void methods or any other type of call where
   * we don't care about the returned value or thrown exceptions. The 'async' word in the method
   * name simply refers to the fact that ThinPeer won't wait for a reply to the message sent, as
   * opposed to when calling ThinPeer.sendAndReceiveJsonRpcRequest().
   *
   * @return number of requests sent
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
   * Use this method to send requests in parallel with separate client (ThinPeer) threads NOTE that
   * this method calls either the sendRequestsWithSingleClient() or
   * sendRequestsWithSingleClientAsync() methods in parallel threads.
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

  private RpcType getRpcTypeForPeer(UUID peerUuid) throws ExecutionException, InterruptedException {
    PeerInfo peerInfo = getPalDirectory().getPeerInfo(peerUuid);
    boolean listensToRpc = peerInfo.getRpcAddress() != null;
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
      return RpcType.RPC;
    }
    if (listensToJsonRpc) {
      return RpcType.JSONRPC;
    }
    throw new RuntimeException("Peer does not have any RPC address");
  }

  private void print(JsonRpcResponse response) {
    if (response.getResult() != null) {
      out.println(response.getResult().getObject());
    } else if (response.getError() != null) {
      out.println(response.getError());
    }
  }

  private void print(ExecMessage response) {
    if (response.getReturnValue() != null) {
      print(response.getReturnValue());
    } else if (response.getRaisedThrowable() != null) {
      print(response.getRaisedThrowable());
    }
  }

  private void print(ReturnValue returnValue) {
    if (returnValue.getIsVoid()) {
      return;
    }
    Obj object = returnValue.getObject();
    if (object != null) {
      out.println(object.getValue());
    }
  }

  private void print(RaisedThrowable raisedThrowable) {
    out.println(ColferUtils.format(raisedThrowable));
  }

  private class MainMethodCallBuilder {
    private final UUID thinPeerUuid;
    final Class<?>[] parameterTypes = new Class[] {String[].class};
    private final String methodName;
    private final String className;
    private final String[] parameterTypesNamesArray;
    private final Object[] parameters;
    private final ObjectRef[] argObjRefs;

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

    public JsonRpcRequest buildJsonRpc() {
      JsonRpcRequest jsonRpc = new JsonRpcRequest();
      jsonRpc.setJsonrpc("2.0");
      jsonRpc.setId(UUID.randomUUID().toString());
      jsonRpc.setMethod(String.format("%s.%s", className, methodName));
      if (argList != null) {
        JsonRpcParameter parameter = new JsonRpcParameter();
        String[] argArray = argList.toArray(new String[0]);
        parameter.setValue(argArray);
        parameter.setType("[Ljava.lang.String;");
        List<JsonRpcParameter> parameterList = new ArrayList<>();
        parameterList.add(parameter);
        jsonRpc.setParams(parameterList);
      }
      return jsonRpc;
    }
  }
}
