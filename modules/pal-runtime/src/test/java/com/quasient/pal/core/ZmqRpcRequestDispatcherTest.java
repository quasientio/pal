/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

public class ZmqRpcRequestDispatcherTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  /*
  a class for Workers (which REPly to Dealer) IRL: RPCMessageInvoker's
  */
  private static class Worker implements Runnable {

    private final UUID peerUuid = UUID.randomUUID();
    private final Socket socket;
    private final ZContext context;
    private final String dealerAddress;

    Worker(ZContext context, String dealerAddress) {
      this.context = context;
      this.dealerAddress = dealerAddress;
      this.socket = this.context.createSocket(SocketType.REP);
    }

    @Override
    public void run() {
      // connect to dealer
      this.socket.connect(this.dealerAddress);

      // process requests
      while (!Thread.interrupted()) {
        logger.debug("worker '{}' starts dispatching", peerUuid);
        try {
          String from = socket.recvStr();
          String msg = socket.recvStr();
          socket.send(
              String.format("OK - worker <%s> got msg: %s from peer: %s", peerUuid, msg, from));
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            break;
          } else if (errorCode == ZError.EINTR) {
            break;
          }
        } catch (Exception ex) {
          if (!context.isClosed()) {
            socket.send("ERROR");
          }
        }
      }

      logger.debug("worker '{}' exits", peerUuid);
      this.socket.close();
    }
  }

  /*
  a class for Clients (which REQuest to Router)
  */
  private static class Client implements Callable<List<String>> {
    private final UUID peerUuid = UUID.randomUUID();
    private final Socket socket;
    private final String rpcRouterAddress;
    private final List<String> messagesToSend;
    private final CountDownLatch shutdownLatch;

    Client(
        ZContext context,
        String rpcRouterAddress,
        List<String> messagesToSend,
        CountDownLatch shutdownLatch) {
      this.rpcRouterAddress = rpcRouterAddress;
      this.messagesToSend = messagesToSend;
      this.socket = context.createSocket(SocketType.REQ);
      this.shutdownLatch = shutdownLatch;
    }

    @Override
    public List<String> call() {
      // connect to router
      logger.debug("new client with identity: {}", peerUuid);
      this.socket.setIdentity(peerUuid.toString().getBytes(ZMQ.CHARSET));
      this.socket.connect(this.rpcRouterAddress);

      final List<String> replies = new ArrayList<>();

      // send requests
      messagesToSend.forEach(
          m -> {
            this.socket.send(peerUuid.toString(), ZMQ.SNDMORE);
            this.socket.send(m, 0);
            logger.debug("sent req: {}", m);
            String response = this.socket.recvStr();
            logger.debug("got response: {}", response);
            replies.add(response);
          });

      this.socket.close();
      logger.debug("client is done");
      shutdownLatch.countDown();

      return replies;
    }
  }

  private static final short NUMBER_OF_WORKERS = 3;
  private static final String RPC_ROUTER_ADDRESS = "tcp://0.0.0.0:5671";
  private static final String DEALER_ADDRESS = "inproc://deal";
  private ZContext context;
  private ServiceManager manager;
  private ExecutorService execService;
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private ZmqRpcRequestDispatcher zmqRpcRequestDispatcher;

  @Before
  public void setup() {
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    this.zmqRpcRequestDispatcher =
        new ZmqRpcRequestDispatcher(
            UUID.randomUUID(),
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "RPCRequestTest-Service",
            RPC_ROUTER_ADDRESS,
            DEALER_ADDRESS);
    initWorkers();

    final Set<Service> services =
        new HashSet<>(Collections.singletonList(this.zmqRpcRequestDispatcher));
    this.manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);
  }

  @After
  public void cleanup() throws Exception {
    manager.stopAsync().awaitStopped();
    closeContext(context);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    logger.debug("executor shut down");
  }

  private void initWorkers() {
    List<Worker> workers = new ArrayList<>();
    for (int i = 0; i < NUMBER_OF_WORKERS; i++) {
      Worker worker = new Worker(this.context, DEALER_ADDRESS);
      workers.add(worker);
    }
    workers.forEach(w -> execService.execute(w));
  }

  @Test
  public void clientsSendRequestsGetWorkersReplies() throws Exception {
    assertThat(zmqRpcRequestDispatcher.isRunning(), is(true));

    // init clients
    List<Client> clients = new ArrayList<>();
    ZContext remoteCtxt = createContext();
    short numberOfClients = 3;
    final CountDownLatch shutdownLatch = new CountDownLatch(numberOfClients);
    for (int i = 0; i < numberOfClients; i++) {
      clients.add(
          new Client(
              remoteCtxt, RPC_ROUTER_ADDRESS, Arrays.asList("Hello", "World", "!"), shutdownLatch));
    }

    // run clients and store Future replies
    Map<Client, Future<List<String>>> futureReplies = new HashMap<>();
    clients.forEach(
        c -> {
          Future<List<String>> cliReplies = execService.submit(c);
          futureReplies.put(c, cliReplies);
        });

    // wait for all clients to be finished
    shutdownLatch.await();

    // assert Future replies contain the client (i.e. sender) UUID as returned by the worker
    futureReplies.forEach(
        (cli, value) -> {
          List<String> replies = null;
          try {
            replies = value.get();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          replies.forEach(r -> assertThat(r, containsString("from peer: " + cli.peerUuid)));
        });

    // close remote context
    remoteCtxt.close();
    logger.debug("remote ctxt closed");
  }
}
