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

package net.ittera.pal.tools;

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "runner")
public class AppRunner implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(AppRunner.class);
  private static final String RUNNER_PROPERTIES_PATH = "/runner.properties";
  private static final String CONSUMER_PROPERTIES_PATH = "/consumer.properties";
  private static final String PRODUCER_PROPERTIES_PATH = "/producer.properties";
  private static final long REPLY_PROCESSOR_SLEEP_MS = 100;
  private final ProtobufMessageBuilder messageBuilder;
  private final Properties properties = new Properties();
  private final Properties consumerProperties = new Properties();
  private final Properties producerProperties = new Properties();
  private Long pollDuration;
  private String logPrefix;

  /** Options */
  @Option(
      names = {"-u", "--uuid"},
      description = "uuid to use by this peer (default: random)")
  private UUID uuid;

  @Option(
      names = {"-d", "--dir"},
      description = "PAL directory URL")
  private String palDirectoryURL;

  @Option(
      names = {"-n", "--num-requests"},
      defaultValue = "1",
      paramLabel = "NUM_REQUESTS",
      description = "number of requests to send")
  private int requests;

  @Option(
      names = {"-c", "--num-clients"},
      defaultValue = "1",
      paramLabel = "NUM_CLIENTS",
      description = "number of clients to use")
  private int clients;

  @Option(
      names = {"-li", "--log-in"},
      paramLabel = "LOGNAME",
      description = "read from given log")
  private String inLogName;

  @Option(
      names = {"-lo", "--log-out"},
      paramLabel = "LOGNAME",
      description = "write to given log")
  private String outLogName;

  @Option(
      names = {"-l", "--log"},
      paramLabel = "LOGNAME",
      description = "read and write from/to given log")
  private String logName;

  @Option(
      names = {"-pu", "--peer-uuid"},
      paramLabel = "PEER_UUID",
      description = "talk to peer with given UUID")
  private UUID peerUuid;

  @Option(
      names = {"-pa", "--peer-address"},
      paramLabel = "PEER_ADDRESS",
      description = "talk to peer with given address")
  private String peerAddress;

  @Option(
      names = {"-a", "--async"},
      description = "send to log in async mode")
  private boolean async;

  @Option(
      names = {"-f", "--forget-reply"},
      description = "do not wait for replies")
  private boolean sendAndForget;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  @Option(names = "-v", description = "run verbosely")
  private boolean verbose;

  /** Params */
  @Parameters(index = "0")
  private String className;

  private String methodName = "main";

  @Parameters(index = "1..*")
  private List<String> argList;

  AppRunner() {
    this.messageBuilder = new ProtobufMessageBuilder();
  }

  private void loadProperties() throws IOException {
    // runner properties
    try (final InputStream stream = AppRunner.class.getResourceAsStream(RUNNER_PROPERTIES_PATH)) {
      properties.load(stream);
    }
    final String pollDurationProp = properties.getProperty("pollDuration");
    final String logPrefixProp = properties.getProperty("kafkaTopic");
    if (pollDurationProp != null && !pollDurationProp.trim().isEmpty()) {
      pollDuration = Long.parseLong(pollDurationProp.trim());
    }
    if (logPrefixProp != null && !logPrefixProp.trim().isEmpty()) {
      logPrefix = logPrefixProp.trim();
    }

    // load consumer properties
    try (final InputStream stream = AppRunner.class.getResourceAsStream(CONSUMER_PROPERTIES_PATH)) {
      consumerProperties.load(stream);
    }
    // load producer properties
    try (final InputStream stream = AppRunner.class.getResourceAsStream(PRODUCER_PROPERTIES_PATH)) {
      producerProperties.load(stream);
    }
  }

  private void validateInput() {
    // directory
    if (palDirectoryURL == null || palDirectoryURL.isEmpty()) {
      palDirectoryURL = System.getenv("PAL_DIRECTORY");
    }
    if (palDirectoryURL == null) {
      throw new RuntimeException(
          "Please provide -d/--dir, or set the ENV variable PAL_DIRECTORY (eg. PAL_DIRECTORY=localhost:2181)");
    }
  }

  /**
   * Serially sends all requests in a single (ThinPeer) thread. With log IO 1st req to log and waits
   * for Future reply, then sends all other directly to peer In logless mode (-pa / -pu), it sends
   * all directly to peer
   */
  private int runReqsWithSingleClient() throws Exception {

    // prepare arrays for message construction
    // TODO: generalize this to other methods (non-varargs)
    Class[] parameterTypes = new Class[] {String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    IntStream.range(0, parameterTypes.length)
        .forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
    Object[] parameters = new Object[] {new String[] {}};
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
                new ObjectRef[parameterTypes.length]);
        thinPeer.sendAndReceive(requestMsg, true);
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
                new ObjectRef[parameterTypes.length]);
        thinPeer.sendAndReceive(requestMsg, true);
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
   * asynchronously to log, waits for reply offsets in directory, then fetches them from log. If
   * sendAndForget=true, it doesn't wait for replies, useful for void methods or any other type of
   * call where we don't care about the returned value or thrown exceptions.
   */
  private int runReqsWithSingleClientAsync() throws Exception {

    if (peerAddress != null || peerUuid != null) {
      throw new RuntimeException(
          "Direct p2p talk (-pa / -pu) is not compatible with -a or -f modes");
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
    Future<ExecMessage> messageFuture;

    // a queue to store futures (async mode)
    final Queue<Future<ExecMessage>> messageFutureQueue = new ConcurrentLinkedQueue<>();
    Thread replyProcessorThread = null;
    if (!sendAndForget) {
      replyProcessorThread =
          new Thread(
              () -> {
                int totalProcessed = 0;
                int processed;
                while (totalProcessed < requests) {
                  processed = 0;
                  for (Future<ExecMessage> futureReply : messageFutureQueue) {
                    if (futureReply.isDone()) {
                      messageFutureQueue.remove(futureReply);
                      processed++;
                    }
                  }
                  totalProcessed += processed;
                  if (logger.isDebugEnabled()) {
                    int queueSize = messageFutureQueue.size();
                    logger.debug(
                        "processed {} records, total so far: {}, size of queue: {}",
                        processed,
                        totalProcessed,
                        queueSize);
                    if (logger.isTraceEnabled() && queueSize > 0) {
                      logger.trace("PENDING:");
                      messageFutureQueue.stream().forEach(f -> logger.trace(f.toString()));
                    }
                  }
                  try {
                    Thread.sleep(REPLY_PROCESSOR_SLEEP_MS);
                  } catch (InterruptedException e) {
                    // what to do
                  }
                }
              });

      // start background reply processor
      replyProcessorThread.setDaemon(true);
      replyProcessorThread.start();
    }

    // prepare arrays for message construction
    Class[] parameterTypes = new Class[] {String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    IntStream.range(0, parameterTypes.length)
        .forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
    Object[] parameters = new Object[] {new String[] {}};
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
              new ObjectRef[parameterTypes.length]);
      if (sendAndForget) {
        // send to log and forget
        thinPeer.sendToLogAndForget(requestMsg);
      } else {
        // send async, store future reply
        messageFuture = thinPeer.sendToLogAndAsyncProcessReqAndRepNodes(requestMsg);
        messageFutureQueue.add(messageFuture);
      }
      reqsSent++;
    }

    // wait for background reply processor to be done
    if (!sendAndForget) {
      replyProcessorThread.join();
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
    if (clients <= 1) {
      throw new IllegalArgumentException(
          "Method must be called with clients > 1. clients = " + clients);
    }

    Thread[] clientList = new Thread[clients];
    final AtomicInteger finishedThreads = new AtomicInteger(0);
    final AtomicInteger reqsSent = new AtomicInteger(0);

    // start timing
    long start = System.currentTimeMillis();

    // create all threads
    IntStream.range(0, clients)
        .forEach(
            i -> {
              Thread client =
                  new Thread(
                      () -> {
                        try {
                          int sent;
                          if (async || sendAndForget) {
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
    while (finishedThreads.get() < clients) {
      Thread.sleep(10);
    }

    if (verbose) {
      System.out.println(
          String.format(
              "sent %s requests with %s client(s) in %s ms",
              reqsSent.get(), clients, (System.currentTimeMillis() - start)));
    }
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new AppRunner()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() throws Exception {
    validateInput();
    loadProperties();
    if ((async || sendAndForget) && (peerAddress != null || peerUuid != null)) {
      throw new RuntimeException(
          "Direct p2p talk (-pa / -pu) is not compatible with -a or -f options");
    }
    if (requests == 1 || clients == 1) {
      if (async || sendAndForget) {
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
