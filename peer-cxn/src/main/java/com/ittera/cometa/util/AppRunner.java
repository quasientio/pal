package com.ittera.cometa.util;

import com.ittera.cometa.cxn.ThinPeer;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.DataMessageBuilder;

import java.util.Queue;
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

// Provides a Thread for ThinPeer that gets initialized/connected in the constructor, not in run()
class ThinPeerThread extends Thread {
  ThinPeer thinPeer;

  ThinPeerThread() {
    try {
      thinPeer = new ThinPeer("/runner.properties");
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }
}

public class AppRunner {

  protected static DataMessageBuilder dataMessageBuilder = new ProtobufDataMessageBuilder();
  protected boolean verbose;

  AppRunner(boolean verbose) {
    this.verbose = verbose;
  }

  protected void runReqsWithOneClient(String className, String methodName, final int requests, boolean async, boolean sendAndForget) throws Exception {
    ThinPeer thinPeer = new ThinPeer("/runner.properties");

    long start = System.currentTimeMillis();
    int reqsSent = 0;
    DataMessage replyMsg;
    Future<DataMessage> messageFuture;

    // a queue to store futures (async mode)
    final Queue<Future<DataMessage>> messageFutureQueue = new ConcurrentLinkedQueue<>();
    Thread replyProcessorThread = null;
    if (async) {
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
            System.out.println(String.format("processed %s records, total so far: %s, size of queue: %s",
              processed, totalProcessed, messageFutureQueue.size()));
            try {
              Thread.sleep(10);
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
    for (int i = 0; i < requests; i++) {
      DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
      if (sendAndForget) {
        // send to log and forget
        thinPeer.sendToLogAndForget(requestMsg);
      } else if (async) {
        // send async, store future reply
        messageFuture = thinPeer.sendToLogAsync(requestMsg);
        messageFutureQueue.add(messageFuture);
      } else {
        // send and wait for reply
        replyMsg = thinPeer.sendAndReceive(requestMsg);
      }
      reqsSent++;
    }

    // wait for background reply processor to be done
    if (async) {
      replyProcessorThread.join();
    }

    thinPeer.close();

    if (verbose) {
      System.out.println(String.format("sent and received %s requests in %s ms", reqsSent, (System.currentTimeMillis() - start)));
    }
  }

  protected void runAsyncReqsWithNClients(final String className, final String methodName, int clients, final int requests, final boolean sendAndForget) throws Exception {


    // we can reuse this. so better just once
    final Class[] parameterTypes = new Class[]{String[].class};
    final String[] parameterTypesNamesArray = new String[parameterTypes.length];
    for (int i = 0; i < parameterTypes.length; i++) {
      parameterTypesNamesArray[i] = parameterTypes[i].getName();
    }
    final Object[] parameters = new Object[]{new String[]{}};
    Thread[] clientList = new Thread[clients];

    final AtomicInteger finishedThreads = new AtomicInteger(0);
    final AtomicInteger reqsToSend = new AtomicInteger(requests);
    final AtomicInteger reqsSent = new AtomicInteger(0);

    // start timing
    long start = System.currentTimeMillis();

    // create all threads
    for (int i = 0; i < clients; i++) {
      ThinPeerThread client = new ThinPeerThread() {
        @Override
        public void run() {
          while (reqsToSend.getAndDecrement() > 0) {
            DataMessage requestMsg = dataMessageBuilder.buildClassMethod(thinPeer.getPeerUuid(), className, methodName, parameterTypesNamesArray, parameters, new String[parameterTypes.length]);
            if (sendAndForget) {
              // send async to log and forget
//              thinPeer.sendToLogAndForget(requestMsg);
              thinPeer.sendToLogAsync(requestMsg);
            } else {
              // send and wait for reply
              DataMessage replyMsg = thinPeer.sendAndReceive(requestMsg);
            }
            reqsSent.getAndIncrement();
          }
          finishedThreads.getAndIncrement();
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

    //TODO close thinPeers!!!

    if (verbose) {
      System.out.println(String.format("sent %s requests with %s client(s) in %s ms", reqsSent.get(), clients, (System.currentTimeMillis() - start)));
    }
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
    boolean sendAndForget = line.hasOption("forget");
    boolean async = line.hasOption("async");
    String className = line.getArgs()[0];

    AppRunner appRunner = new AppRunner(verbose);
    if (requests == 1 || clients == 1) {
      appRunner.runReqsWithOneClient(className, "main", requests, async, sendAndForget);
    } else {
      appRunner.runAsyncReqsWithNClients(className, "main", clients, requests, sendAndForget);
    }
  }
}
