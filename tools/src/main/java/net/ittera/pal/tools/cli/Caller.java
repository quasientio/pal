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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.ittera.pal.common.cli.PALCommand;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.jsonrpc.JsonRpcParameter;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.types.RPCType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "call",
    customSynopsis = "pal call [OPTIONS] class [args...]%n",
    description = "Send messages to peers or logs",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
public class Caller extends AbstractPALSubcommand {

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
  private String methodName = "main";
  private String palDirectoryURL;
  private UUID peerUuid;
  private String peerAddress;
  private List<String> stdinReqs;

  @ParentCommand PALCommand palCommand;

  /** Options */
  @Option(
      names = {"-u", "--uuid"},
      paramLabel = "uuid",
      description = "uuid to use by this peer (default: <random>)")
  private UUID uuid;

  @Option(
      names = {"-l", "--log"},
      paramLabel = "name",
      description = "read from and write to given log")
  private String logName;

  @Option(
      names = {"-i", "--in-log"},
      paramLabel = "name",
      description = "read from given log")
  private String inLogName;

  @Option(
      names = {"-o", "--out-log"},
      paramLabel = "name",
      description = "write to given log")
  private String outLogName;

  @Option(
      names = {"-p", "--peer"},
      paramLabel = "uuid|HOST:PORT",
      description = "talk to peer with given UUID or RPC address")
  private String peerIdentifier;

  @Option(
      names = {"-t", "--rpc-type"},
      paramLabel = "RPC|JSONRPC",
      description = "specifies the RPC type for the peer")
  private String rpcType;

  @Option(
      names = {"-f", "--forget-reply"},
      description = "do not wait for replies")
  private boolean sendAndForget;

  @Option(
      names = {"-r", "--num-requests"},
      defaultValue = "1",
      paramLabel = "NUM_REQUESTS",
      description = "number of times to send each request per client (default: 1)")
  private int requests;

  @Option(
      names = {"-c", "--num-clients"},
      defaultValue = "1",
      paramLabel = "NUM_CLIENTS",
      description = "number of clients to use (default: 1)")
  private int numberOfClients;

  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Params */
  @Parameters(index = "0", hidden = true)
  private String className;

  @Parameters(index = "1..*", hidden = true)
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

    if (optionGiven(rpcType)) {
      if (!rpcType.equals(RPCType.RPC.name()) && !rpcType.equals(RPCType.JSONRPC.name())) {
        throw new RuntimeException("Invalid RPC type. Must be RPC or JSONRPC.");
      }
      if (rpcType.equals(RPCType.JSONRPC.name())) {
        if (!optionGiven(peerIdentifier)) {
          throw new RuntimeException("You must specify a peer to talk to when using JSON-RPC.");
        }
        if (!peerIdentifier.startsWith("ws://")) {
          throw new RuntimeException("Peer address must start with ws:// when using JSON-RPC.");
        }
      }
    }
  }

  @Override
  protected void initialize() throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Initializing...");
    }

    // read stdin for raw JSON-RPC requests (1 per line), if any
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      if (reader.ready()) {
        stdinReqs = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
          stdinReqs.add(line);
        }
      }
    }

    palDirectoryURL = palCommand.getPalDirectoryConnectionString();
    initializeDirectoryConnectionProvider(palDirectoryURL);

    loadProperties();

    // resolve peer identifier
    if (peerIdentifier != null && !peerIdentifier.isEmpty()) {
      UUID parsedUuid = null;
      try {
        parsedUuid = UUID.fromString(peerIdentifier);
      } catch (IllegalArgumentException iae) {
        // nevermind
      } finally {
        peerUuid = parsedUuid;
      }
      // not a valid UUID, must be an address then
      if (peerUuid == null) {
        peerAddress = peerIdentifier;
      }
    }
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
   * Serially sends all requests in a single (ThinPeer) thread. With log IO 1st req is sent to Log,
   * waits for Future reply, then sends all other directly to the peer which replied.
   *
   * <p>In p2p mode (-p), it sends all directly to peer.
   */
  private int runReqsWithSingleClient() throws Exception {

    int reqsSent;
    long start;

    boolean uuidGiven = uuid != null;
    if (!uuidGiven) {
      uuid = UUID.randomUUID();
    }
    MethodCallBuilder methodCallBuilder = new MethodCallBuilder(uuid, className, argList);

    final boolean sendToPeer = Stream.of(peerAddress, peerUuid).anyMatch(Objects::nonNull);

    RPCType inferredRpcType = optionGiven(rpcType) ? RPCType.valueOf(rpcType) : null;

    // create ThinPeer
    final ThinPeer thinPeer;
    if (sendToPeer) {
      PeerInfo peerInfo;
      if (peerUuid != null) {
        peerInfo = new PeerInfo(peerUuid);
        if (inferredRpcType == null) {
          inferredRpcType = getRPCTypeForPeer(peerUuid);
        }
      } else {
        peerInfo = new PeerInfo();
        if (peerAddress.startsWith("tcp://")) {
          peerInfo.setRpcAddress(peerAddress);
          inferredRpcType = RPCType.RPC;
        } else if (peerAddress.startsWith("ws://")) {
          peerInfo.setJsonrpcAddress(peerAddress);
          inferredRpcType = RPCType.JSONRPC;
        } else {
          throw new RuntimeException(
              "Peer address must start with tcp:// (for ZMQ-RPC) or ws:// (for JSON-RPC)");
        }
      }
      thinPeer =
          new ThinPeer()
              .withUUID(uuid)
              .withDirectoryURL(palDirectoryURL)
              .withSelfRegistration(uuidGiven)
              .withInitialPeer(peerInfo)
              .withOutboundRPCType(inferredRpcType)
              .init();
    } else { // send to Log
      if (logName != null) {
        inLogName = outLogName = logName;
      }
      LogInfo inLog = inLogName == null ? null : new LogInfo(inLogName);
      LogInfo outLog = outLogName == null ? null : new LogInfo(outLogName);
      thinPeer =
          new ThinPeer()
              .withUUID(uuid)
              .withDirectoryURL(palDirectoryURL)
              .withSelfRegistration(uuidGiven)
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

    // send message(s)
    start = System.currentTimeMillis();
    for (reqsSent = 0; reqsSent < requests; reqsSent++) {
      if (inferredRpcType == RPCType.JSONRPC) {
        if (stdinReqs == null || stdinReqs.isEmpty()) {
          // build and send JSON-RPC request built from cmd line args
          print(thinPeer.sendAndReceive(methodCallBuilder.buildJsonRpc(), JsonRpcRequest.class));
        } else {
          // send raw JSON-RPC request(s) read from stdin
          for (String jsonRpc : stdinReqs) {
            print(thinPeer.sendAndReceive(jsonRpc, String.class));
          }
        }
      } else {
        print(thinPeer.sendAndReceive(methodCallBuilder.buildExecMessage()));
      }
    }
    thinPeer.close();

    if (verbose) {
      System.out.printf(
          "sent and received %s requests in %s ms%n",
          reqsSent, (System.currentTimeMillis() - start));
    }

    return reqsSent;
  }

  /**
   * Use this method when no direct peer-to-peer talk is available or desirable. Sends all requests
   * to log, and doesn't wait for replies, useful for void methods or any other type of call where
   * we don't care about the returned value or thrown exceptions. The 'async' word in the method
   * name simply refers to the fact that ThinPeer won't wait for a reply to the message sent, as
   * opposed to when calling ThinPeer.sendAndReceive().
   */
  private int runReqsWithSingleClientAsync() throws Exception {

    if (peerAddress != null || peerUuid != null) {
      throw new RuntimeException(
          "Direct p2p talk (-p) is not compatible with -f (--forget-reply) option");
    }

    boolean uuidGiven = uuid != null;
    if (!uuidGiven) {
      uuid = UUID.randomUUID();
    }
    MethodCallBuilder methodCallBuilder = new MethodCallBuilder(uuid, className, argList);

    if (logName != null) {
      inLogName = outLogName = logName;
    }
    LogInfo inLog = inLogName == null ? null : new LogInfo(inLogName);
    LogInfo outLog = outLogName == null ? null : new LogInfo(outLogName);

    // create ThinPeer
    final ThinPeer thinPeer =
        new ThinPeer()
            .withUUID(uuid)
            .withDirectoryURL(palDirectoryURL)
            .withSelfRegistration(uuidGiven)
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

    // send message(s)
    long start = System.currentTimeMillis();
    int reqsSent = 0;
    while (reqsSent < requests) {
      // send to log and forget
      thinPeer.sendToLogAndForget(methodCallBuilder.buildExecMessage());
      reqsSent++;
    }
    thinPeer.close();

    if (verbose) {
      System.out.printf(
          "sent and received %s requests in %s ms%n",
          reqsSent, (System.currentTimeMillis() - start));
    }

    return reqsSent;
  }

  /**
   * Use this method to send requests in parallel with separate client (ThinPeer) threads NOTE that
   * this method calls either the runReqsWithSingleClient() or runReqsWithSingleClientAsync()
   * methods in parallel threads
   */
  private void runReqsWithNClients() throws Exception {

    if (requests <= 1) {
      throw new IllegalArgumentException(
          "Method must be called with requests > 1. requests = " + requests);
    }
    if (numberOfClients <= 1) {
      throw new IllegalArgumentException(
          "Method must be called with clients > 1. clients = " + numberOfClients);
    }

    Thread[] clientList = new Thread[numberOfClients];
    final AtomicInteger reqsSent = new AtomicInteger(0);
    final CountDownLatch latch = new CountDownLatch(numberOfClients);

    // start timing
    long start = System.currentTimeMillis();

    // create all threads
    IntStream.range(0, numberOfClients)
        .forEach(
            i -> {
              Thread client =
                  new Thread(
                      () -> {
                        try {
                          int sent;
                          if (sendAndForget) {
                            sent = runReqsWithSingleClientAsync();
                          } else {
                            sent = runReqsWithSingleClient();
                          }
                          reqsSent.getAndAdd(sent);
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
      System.out.printf(
          "sent %s requests with %s client(s) in %s ms%n",
          reqsSent.get(), numberOfClients, (System.currentTimeMillis() - start));
    }
  }

  @Override
  protected int runCommand() throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Running command...");
    }

    if (sendAndForget && (peerAddress != null || peerUuid != null)) {
      throw new RuntimeException(
          "Direct p2p talk (-p) is not compatible with -f (--forget-reply) option");
    }
    if (requests == 1 || numberOfClients == 1) {
      if (sendAndForget) {
        logger.info("running runReqsWithSingleClientAsync()");
        runReqsWithSingleClientAsync();
      } else {
        logger.info("running runReqsWithSingleClient()");
        runReqsWithSingleClient();
      }
    } else {
      logger.info("running runReqsWithNClients()");
      runReqsWithNClients();
    }
    return 0;
  }

  private RPCType getRPCTypeForPeer(UUID peerUuid) throws ExecutionException, InterruptedException {
    PeerInfo peerInfo = getPalDirectory().getPeerInfo(peerUuid);
    boolean listensToRPC = peerInfo.getRpcAddress() != null;
    boolean listensToJSONRPC = peerInfo.getJsonrpcAddress() != null;
    if (listensToRPC && listensToJSONRPC) {
      if (!optionGiven(rpcType)) {
        throw new RuntimeException(
            "Peer listens to both RPC and JSON-RPC. Please specify the RPC type with -t or --rpc-type");
      } else {
        return RPCType.valueOf(rpcType);
      }
    }
    if (listensToRPC) {
      return RPCType.RPC;
    }
    if (listensToJSONRPC) {
      return RPCType.JSONRPC;
    }
    throw new RuntimeException("Peer does not have any RPC address");
  }

  private void print(JsonRpcResponse response) {
    if (response.getResult() != null) {
      System.out.println(response.getResult());
    } else if (response.getError() != null) {
      System.out.println(response.getError());
    }
  }

  private void print(ExecMessage response) {
    if (response.getReturnValue() != null) {
      System.out.println(ColferUtils.format(response.getReturnValue()));
    } else if (response.getRaisedThrowable() != null) {
      System.out.println(ColferUtils.format(response.getRaisedThrowable()));
    }
  }

  private class MethodCallBuilder {
    private final UUID thinPeerUuid;
    final Class<?>[] parameterTypes = new Class[] {String[].class};
    private final String methodName = "main";
    private final String className;
    private final String[] parameterTypesNamesArray;
    private final Object[] parameters;
    private final ObjectRef[] argObjRefs;

    public MethodCallBuilder(UUID thinPeerUuid, String className, List<String> argList) {
      // create reusable arrays for message construction
      parameterTypesNamesArray = new String[parameterTypes.length];
      IntStream.range(0, parameterTypes.length)
          .forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
      parameters = new Object[] {new String[] {}};
      argObjRefs = new ObjectRef[parameterTypes.length];
      this.thinPeerUuid = thinPeerUuid;
      this.className = className;
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
      List<JsonRpcParameter> parameterList = new ArrayList<>();
      if (argList != null) {
        for (String arg : argList) {
          JsonRpcParameter parameter = new JsonRpcParameter();
          parameter.setValue(arg);
          parameterList.add(parameter);
        }
        jsonRpc.setParams(parameterList);
      }
      return jsonRpc;
    }
  }
}
