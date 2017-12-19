package com.ittera.cometa.util;

import com.ittera.cometa.PeerInfo;
import com.ittera.cometa.cxn.ThinPeer;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.DataMessageBuilder;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppRunner {

  protected final static Logger logger = LoggerFactory.getLogger(AppRunner.class);
  protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();
  protected boolean verbose;
  protected static final long REPLY_PROCESSOR_SLEEP_MS = 100;

  AppRunner(boolean verbose) {
    this.verbose = verbose;
  }

  /**
   * Serially sends all requests in a single (ThinPeer) thread.
   * Sends 1st req to log and waits for Future reply, then sends all other directly to peer
   */
  protected int runReqsWithSingleClient(String className, String methodName, final int requests) throws Exception {
    ThinPeer thinPeer = new ThinPeer("/runner.properties");
    long start = System.currentTimeMillis();
    int reqsSent = 0;
    DataMessage replyMsg;
    Future<DataMessage> messageFuture;

     // prepare arrays for message construction
    Class[] parameterTypes = new Class[]{String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }
    Object[] parameters = new Object[]{new String[]{}};

    // send 1st request
    DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName,
      parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
    messageFuture = thinPeer.sendToLogAsync(requestMsg);
    reqsSent++;

    // wait for reply (blocking)
    replyMsg = messageFuture.get();

    // switch to direct p2p talk
    String concentratorUuid = replyMsg.getConcentratorUuid();
    PeerInfo newPeer = null;
    thinPeer.connectToPeer(UUID.fromString(concentratorUuid));

    // send rest of requests
    for (; reqsSent < requests; reqsSent++) {
      requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName,
        parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
      replyMsg = thinPeer.sendAndReceive(requestMsg);
    }

    thinPeer.close();

    if (verbose) {
      System.out.println(String.format("sent and received %s requests in %s ms", reqsSent,
        (System.currentTimeMillis() - start)));
    }

    return reqsSent;
  }

  /**
   * Use this method when no direct peer-to-peer talk is available or desirable.
   * Sends all requests asynchronously to log, waits for reply offsets in directory, then fetches them from log.
   * If sendAndForget=true, it doesn't wait for replies, useful for void methods or any other type of call where
   * we don't care about the returned value or thrown exceptions.
   */
  protected int runReqsWithSingleClientAsync(String className, String methodName, final int requests,
                                              boolean sendAndForget) throws Exception {
    ThinPeer thinPeer = new ThinPeer("/runner.properties");

    long start = System.currentTimeMillis();
    int reqsSent = 0;
    Future<DataMessage> messageFuture;

    // a queue to store futures (async mode)
    final Queue<Future<DataMessage>> messageFutureQueue = new ConcurrentLinkedQueue<>();
    Thread replyProcessorThread = null;
    if (!sendAndForget) {
      replyProcessorThread = new Thread() {
        @Override
        public void run() {
          int totalProcessed = 0;
          int processed;
          while (totalProcessed < requests) {
            processed = 0;
            for (Future<DataMessage> futureReply: messageFutureQueue) {
              if (futureReply.isDone()) {
                messageFutureQueue.remove(futureReply);
                processed++;
              }
            }
            totalProcessed+=processed;
            if (logger.isDebugEnabled()) {
              int queueSize = messageFutureQueue.size();
              logger.debug("processed {} records, total so far: {}, size of queue: {}", processed, totalProcessed, queueSize);
              if (logger.isTraceEnabled() && queueSize>0) {
                logger.trace("PENDING:");
                for (Future<DataMessage> futureReply : messageFutureQueue) {
                  logger.trace(futureReply.toString());
                }
              }
            }
            try {
              Thread.sleep(REPLY_PROCESSOR_SLEEP_MS);
            } catch (InterruptedException e) {
              // what to do
            }
          }
        }
      };

      // start background reply processor
      replyProcessorThread.setDaemon(true);
      replyProcessorThread.start();
    }

    // prepare arrays for message construction
    Class[] parameterTypes = new Class[]{String[].class};
    String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }
    Object[] parameters = new Object[]{new String[]{}};

    // send all requests
    for (; reqsSent < requests; reqsSent++) {
      DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName,
        parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
      if (sendAndForget) {
        // send to log and forget
        thinPeer.sendToLogAndForget(requestMsg);
      } else {
        // send async, store future reply
        messageFuture = thinPeer.sendToLogAsync(requestMsg);
        messageFutureQueue.add(messageFuture);
      }
    }

    // wait for background reply processor to be done
    if (!sendAndForget) {
      replyProcessorThread.join();
    }

    thinPeer.close();

    if (verbose) {
      System.out.println(String.format("sent and received %s requests in %s ms", reqsSent,
        (System.currentTimeMillis() - start)));
    }

    return reqsSent;
  }

  /**
   * Use this method to send requests in parallel with separate client (ThinPeer) threads
   * NOTE that this method calls either the runReqsWithSingleClient() or runReqsWithSingleClientAsync()
   * methods in parallel threads
   */
  protected int runReqsWithNClients(final String className, final String methodName, int clients, final int requests,
                                    final boolean sendAndForget, final boolean async) throws Exception {

    assert requests > 1;
    assert clients > 1;

    Thread[] clientList = new Thread[clients];
    final AtomicInteger finishedThreads = new AtomicInteger(0);
    final AtomicInteger reqsSent = new AtomicInteger(0);

    // start timing
    long start = System.currentTimeMillis();

    // create all threads
    for (int i = 0; i < clients; i++) {
      Thread client = new Thread() {
        @Override
        public void run() {
          try {
            int sent = 0;
            if (async || sendAndForget) {
              sent = runReqsWithSingleClientAsync(className, methodName, requests, sendAndForget);
            } else {
              sent = runReqsWithSingleClient(className, methodName, requests);
            }
            finishedThreads.getAndIncrement();
            reqsSent.getAndAdd(sent);
          } catch (Exception e) {
            logger.error("Caught error running requests", e);
          }
        }
      };
      clientList[i] = client;
    }

    // then start all clients at once
    for (int i = 0; i < clients; i++) {
      clientList[i].start();
    }

    // wait for threads to finish
    while (finishedThreads.get() < clients) {
      Thread.sleep(10);
    }

     if (verbose) {
      System.out.println(String.format("sent %s requests with %s client(s) in %s ms", reqsSent.get(), clients,
        (System.currentTimeMillis() - start)));
    }

    return reqsSent.get();
  }

  public static void main(String[] args) throws Exception {

    CommandLineParser parser = new DefaultParser();
    Options options = new Options();
    options.addOption(Option.builder("r").required(false).longOpt("num-requests").desc("number of requests to send").hasArg().build());
    options.addOption(Option.builder("c").required(false).longOpt("num-clients").desc("number of clients to use").hasArg().build());
    options.addOption(Option.builder("f").required(false).longOpt("forget-reply").desc("do not wait for replies").build());
    options.addOption(Option.builder("a").required(false).longOpt("async").desc("send to log in async mode").build());
    options.addOption(Option.builder("v").required(false).longOpt("verbose").desc("print useful info").build());
    options.addOption(Option.builder("h").required(false).longOpt("help").desc("print usage").build());

    CommandLine line = null;
    try {
      line = parser.parse(options, args);
    } catch (ParseException exp) {
      System.err.println(exp.getMessage());
      System.exit(1);
    }

    if (line.hasOption("help")) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("runner", options);
      System.exit(0);
    }

    int requests = Integer.parseInt(line.getOptionValue("r", "1"));
    int clients = Integer.parseInt(line.getOptionValue("c", "1"));
    boolean verbose = line.hasOption("v");
    boolean sendAndForget = line.hasOption("forget-reply");
    boolean async = line.hasOption("async");

    if (async && sendAndForget) {
      System.err.println("async (-a) and forget-reply (-f) options are mutually-exclusive");
      System.exit(1);
    }

    String className = line.getArgs()[0];

    AppRunner appRunner = new AppRunner(verbose);
    if (requests == 1 || clients == 1) {
      if (async || sendAndForget) {
        appRunner.runReqsWithSingleClientAsync(className, "main", requests, sendAndForget);
      } else {
        appRunner.runReqsWithSingleClient(className, "main", requests);
      }
    } else {
        appRunner.runReqsWithNClients(className, "main", clients, requests, sendAndForget, async);
    }
  }
}
