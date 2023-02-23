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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.ittera.pal.common.cli.PALCommand;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.colfer.ExecMessage;
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
  private static final long REPLY_PROCESSOR_SLEEP_MS = 100;
  private final MessageBuilder messageBuilder;
  private final Properties properties = new Properties();
  private final Properties consumerProperties = new Properties();
  private final Properties producerProperties = new Properties();
  private Long pollDuration;
  private String logPrefix;
  private String methodName = "main";
  private String palDirectoryURL;

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
      description = "talk to peer with given UUID or REQ address")
  private String peerIdentifier;

  @Option(
      names = {"-f", "--forget-reply"},
      description = "do not wait for replies")
  private boolean sendAndForget;

  @Option(
      names = {"-r", "--num-requests"},
      defaultValue = "1",
      paramLabel = "NUM_REQUESTS",
      description = "number of requests to send per client (default: 1)")
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

  private UUID peerUuid;
  private String peerAddress;

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
  }

  @Override
  protected void initialize() throws Exception {
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
   * Serially sends all requests in a single (ThinPeer) thread. With log IO 1st req to log and waits
   * for Future reply, then sends all other directly to peer.
   *
   * <p>In p2p mode (-p), it sends all directly to peer.
   */
  private int runReqsWithSingleClient() throws Exception {

    // prepare arrays for message construction
    // TODO: generalize this to other methods (non-varargs)
    Class[] parameterTypes = new Class[] {String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    IntStream.range(0, parameterTypes.length)
        .forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
    Object[] parameters = new Object[] {new String[] {}};
    ObjectRef[] argObjRefs = new ObjectRef[parameterTypes.length];
    if ("main".equals(methodName) && argList != null) {
      parameters[0] = argList.toArray(new String[0]);
    }

    ThinPeer thinPeer;
    ExecMessage requestMsg;
    int reqsSent;
    long start;

    final boolean sendToPeer = Stream.of(peerAddress, peerUuid).anyMatch(Objects::nonNull);
    // talk directly to given peer
    if (sendToPeer) {
      PeerInfo peerInfo;
      if (peerUuid != null) {
        peerInfo = new PeerInfo(peerUuid);
      } else {
        String peerReqAddress =
            peerAddress.startsWith("tcp://") ? peerAddress : "tcp://" + peerAddress;
        peerInfo = new PeerInfo(peerReqAddress);
      }
      // create ThinPeer
      thinPeer =
          new ThinPeer()
              .withUUID(uuid == null ? UUID.randomUUID() : uuid)
              .withDirectoryURL(palDirectoryURL)
              .withInitialPeer(peerInfo)
              .init();
      start = System.currentTimeMillis();
      for (reqsSent = 0; reqsSent < requests; reqsSent++) {
        requestMsg =
            messageBuilder.buildClassMethod(
                thinPeer.getPeerUuid(),
                className,
                methodName,
                parameterTypesNamesArray,
                this,
                null,
                parameters,
                argObjRefs);
        thinPeer.sendAndReceive(requestMsg);
      }
    }

    // 1st goes to log, ThinPeer switches then to direct talk with replying Peer
    else {
      if (logName != null) {
        inLogName = outLogName = logName;
      }
      LogInfo inLog = inLogName == null ? null : new LogInfo(inLogName);
      LogInfo outLog = outLogName == null ? null : new LogInfo(outLogName);

      // create ThinPeer
      thinPeer =
          new ThinPeer()
              .withUUID(uuid == null ? UUID.randomUUID() : uuid)
              .withDirectoryURL(palDirectoryURL)
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

      start = System.currentTimeMillis();
      for (reqsSent = 0; reqsSent < requests; reqsSent++) {
        requestMsg =
            messageBuilder.buildClassMethod(
                thinPeer.getPeerUuid(),
                className,
                methodName,
                parameterTypesNamesArray,
                this,
                null,
                parameters,
                argObjRefs);
        thinPeer.sendAndReceive(requestMsg);
      }
    }

    thinPeer.close();

    if (verbose) {
      System.out.println(
          String.format(
              "sent and received %s requests in %s ms",
              reqsSent, (System.currentTimeMillis() - start)));
    }

    return reqsSent;
  }

  /**
   * Use this method when no direct peer-to-peer talk is available or desirable. Sends all requests
   * to log, and doesn't wait for replies, useful for void methods or any other type of call where
   * we don't care about the returned value or thrown exceptions. The 'async' word in the method
   * name simply refers to the fact that ThinPeer won't wait for a reply to the message sent, as
   * when calling sendAndReceive().
   */
  private int runReqsWithSingleClientAsync() throws Exception {

    if (peerAddress != null || peerUuid != null) {
      throw new RuntimeException(
          "Direct p2p talk (-p) is not compatible with -f (--forget-reply) option");
    }

    // load properties and init ThinPeer
    ThinPeer thinPeer;
    if (logName != null) {
      inLogName = outLogName = logName;
    }
    LogInfo inLog = inLogName == null ? null : new LogInfo(inLogName);
    LogInfo outLog = outLogName == null ? null : new LogInfo(outLogName);
    // create ThinPeer
    thinPeer =
        new ThinPeer()
            .withUUID(uuid == null ? UUID.randomUUID() : uuid)
            .withDirectoryURL(palDirectoryURL)
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

    long start = System.currentTimeMillis();
    int reqsSent = 0;

    // prepare arrays for message construction
    Class[] parameterTypes = new Class[] {String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    IntStream.range(0, parameterTypes.length)
        .forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
    Object[] parameters = new Object[] {new String[] {}};
    ObjectRef[] argObjRefs = new ObjectRef[parameterTypes.length];
    // TODO: generalize this to other methods (non-varargs)
    if ("main".equals(methodName) && argList != null) {
      parameters[0] = argList.toArray(new String[0]);
    }

    // send all requests
    while (reqsSent < requests) {
      ExecMessage requestMsg =
          messageBuilder.buildClassMethod(
              thinPeer.getPeerUuid(),
              className,
              methodName,
              parameterTypesNamesArray,
              this,
              null,
              parameters,
              argObjRefs);

      // send to log and forget
      thinPeer.sendToLogAndForget(requestMsg);
      reqsSent++;
    }

    thinPeer.close();

    if (verbose) {
      System.out.println(
          String.format(
              "sent and received %s requests in %s ms",
              reqsSent, (System.currentTimeMillis() - start)));
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
    final AtomicInteger finishedThreads = new AtomicInteger(0);
    final AtomicInteger reqsSent = new AtomicInteger(0);

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
                          finishedThreads.getAndIncrement();
                          reqsSent.getAndAdd(sent);
                        } catch (Exception e) {
                          logger.error("Caught error running requests", e);
                        }
                      });
              clientList[i] = client;
            });

    // then start all clients at once
    Arrays.stream(clientList).forEach(Thread::start);

    // wait for threads to finish
    while (finishedThreads.get() < numberOfClients) {
      Thread.sleep(10);
    }

    if (verbose) {
      System.out.println(
          String.format(
              "sent %s requests with %s client(s) in %s ms",
              reqsSent.get(), numberOfClients, (System.currentTimeMillis() - start)));
    }
  }

  @Override
  protected int runCommand() throws Exception {
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
}
