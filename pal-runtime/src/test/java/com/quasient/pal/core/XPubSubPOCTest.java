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

package com.quasient.pal.core;

import static java.lang.String.format;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

@SuppressWarnings({"unused", "checkstyle:AbbreviationAsWordInName"})
public class XPubSubPOCTest {

  /*
  a class for Publishers
  */
  private static class Publisher implements Runnable {

    private final UUID peerUuid = UUID.randomUUID();
    private final Socket socket;
    private final String xsubAddress;
    private final List<String> messagesToSend;

    Publisher(ZContext context, String xsubAddress, List<String> messagesToSend) {
      this.xsubAddress = xsubAddress;
      this.socket = context.createSocket(SocketType.PUB);
      this.messagesToSend = messagesToSend;
    }

    @Override
    public void run() {
      // connect to xpub endpoint
      this.socket.connect(this.xsubAddress);

      // process requests
      int messagesSent = 0;
      while (!Thread.interrupted() && messagesSent < messagesToSend.size()) {
        try {
          final String nextMsg = messagesToSend.get(messagesSent);
          socket.send(nextMsg);
          System.out.printf("publisher sent message: %s%n", nextMsg);
          messagesSent++;
        } catch (Exception ex) {
          socket.send("ERROR");
          System.err.println("publisher error: " + ex.getMessage());
        }
      }
      this.socket.close();
      System.out.printf("publisher w/uuid <%s> is finished%n", peerUuid);
    }
  }

  /*
  a class for Subscriber
  */
  private static class Subscriber implements Callable<List<String>> {
    private final UUID peerUuid = UUID.randomUUID();
    private final int id;
    private final Socket socket;
    private final String xpubAddress;
    private final int expectedMessages;
    private final CountDownLatch readyLatch;
    private final CountDownLatch shutdownLatch;

    Subscriber(
        int id,
        ZContext context,
        String xpubAddress,
        int expectedMessages,
        CountDownLatch readyLatch,
        CountDownLatch shutdownLatch) {
      this.id = id;
      this.xpubAddress = xpubAddress;
      this.socket = context.createSocket(SocketType.SUB);
      this.expectedMessages = expectedMessages;
      this.readyLatch = readyLatch;
      this.shutdownLatch = shutdownLatch;
    }

    @Override
    public List<String> call() {
      System.out.printf("new subscriber (id: %d) with identity: %s%n", id, peerUuid);
      this.socket.setIdentity(peerUuid.toString().getBytes(ZMQ.CHARSET));
      this.socket.connect(this.xpubAddress);
      this.socket.subscribe(ZMQ.SUBSCRIPTION_ALL);

      final List<String> received = new ArrayList<>();

      readyLatch.countDown();
      while (!Thread.interrupted() && received.size() < expectedMessages) {
        System.out.printf("subscriber (id=%d) waiting for msg%n", id);
        String receivedString = this.socket.recvStr();
        received.add(receivedString);
        System.out.printf(
            "subscriber (id=%d) got msg: %s (got=%d, total expected=%d) %n",
            id, receivedString, received.size(), expectedMessages);
      }

      this.socket.close();
      System.out.printf("subscriber (id=%d) w/uuid <%s> is finished%n", id, peerUuid);
      shutdownLatch.countDown();

      return received;
    }
  }

  private final String xpubAddress = "inproc://xpub";
  private final String xsubAddress = "inproc://xsub";
  private final String ctrlProxyAddress = "inproc://ctrl-proxy";
  private ZContext context;
  private Socket xpub;
  private Socket xsub;
  private final List<Publisher> publishers = new ArrayList<>();
  private final List<Subscriber> subscribers = new ArrayList<>();
  private final ExecutorService execService = Executors.newCachedThreadPool();

  private ZContext createContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(10000);
    ctxt.setSndHWM(10000);
    return ctxt;
  }

  //  @Test
  public void testReqRep() {
    this.context = createContext();
    Socket req = context.createSocket(SocketType.REQ);
    Socket rep = context.createSocket(SocketType.REP);
    final String address = "inproc://request_flow";
    rep.bind(address);
    req.connect(address);

    // send
    int messagesToSend = 10;
    execService.execute(
        () -> {
          for (int i = 0; i < messagesToSend; i++) {
            String msg = format("Hello <%d>", i);
            System.out.println("sending message: " + msg);
            req.send(msg, 0);
            //        req.receive();
          }
        });
    // receive
    for (int i = 0; i < messagesToSend; i++) {
      String received = rep.recvStr();
      //      rep.send("OK");
      System.out.printf("received msg: %s%n", received);
    }

    req.close();
    rep.close();
    context.close();
  }

  private void initXpubXsubProxy() {
    execService.execute(
        () -> {
          this.xpub = context.createSocket(SocketType.XPUB);
          xpub.bind(xpubAddress);

          this.xsub = context.createSocket(SocketType.XSUB);
          xsub.bind(xsubAddress);

          Socket ctrl = context.createSocket(SocketType.PAIR);
          ctrl.bind(this.ctrlProxyAddress);

          System.out.println("starting xpub-xsub proxy");
          ZMQ.proxy(xpub, xsub, null, ctrl);
          System.out.println("exited xpub-xsub proxy");
        });
  }

  private void initPublishers(int numberOfPublishers, List<String> messagesToSend) {
    for (int i = 0; i < numberOfPublishers; i++) {
      Publisher publisher = new Publisher(this.context, xsubAddress, messagesToSend);
      publishers.add(publisher);
    }
    publishers.forEach(execService::execute);
  }

  //  @Test
  public void testXPubSub() throws InterruptedException {

    int numberOfPublishers = 3;
    int numberOfSubscribers = 2;
    this.context = createContext();
    initXpubXsubProxy();

    List<String> messages = Arrays.asList("Hello", "world!", "what is going on", "?");

    // init subscribers
    int expectedMessages = numberOfPublishers * messages.size();
    final CountDownLatch shutdownLatch = new CountDownLatch(numberOfSubscribers);
    final CountDownLatch readyLatch = new CountDownLatch(numberOfSubscribers);
    for (int i = 0; i < numberOfSubscribers; i++) {
      subscribers.add(
          new Subscriber(
              i, this.context, xpubAddress, expectedMessages, readyLatch, shutdownLatch));
    }

    // run subscriber threads and store Future replies
    Map<Subscriber, Future<List<String>>> futureReplies = new HashMap<>();
    subscribers.forEach(
        c -> {
          var cliReplies = execService.submit(c);
          futureReplies.put(c, cliReplies);
        });

    // IMPORTANT to start publishers after subscribers are started and ready
    readyLatch.await();
    System.out.printf("all subscribers ready, starting publishers%n");
    initPublishers(numberOfPublishers, messages);

    // wait for all subscribers to be finished
    shutdownLatch.await();

    // assert Future replies contain the client (i.e. sender) UUID as returned by the publisher
    assertThat(futureReplies.values().size(), is(subscribers.size()));
    futureReplies
        .values()
        .forEach(
            v -> {
              try {
                assertThat(v.get().size(), is(expectedMessages));
              } catch (Exception e) {
                System.err.println("error getting response future: " + e.getMessage());
                fail();
              }
            });
    // stop proxy
    Socket ctrl = context.createSocket(SocketType.PAIR);
    ctrl.connect(this.ctrlProxyAddress);
    ctrl.send(ZMQ.PROXY_TERMINATE);
    ctrl.close();

    // stop executor
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    System.out.println("executor shut down");

    // close context
    this.context.close();
    System.out.println("local ctxt closed");
  }
}
