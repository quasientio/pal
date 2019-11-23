package com.ittera.cometa.core;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

public class DirectRequestDispatcherTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  /*
  a class for Workers (which REPly to Dealer) IRL: PeerMessageInvoker's
  */
  private class Worker implements Runnable {

    private final UUID peerUuid = UUID.randomUUID();
    private Socket socket;
    private ZContext context;
    private String dealerAddress;

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
          socket.send("ERROR");
        }
      }

      logger.debug("worker '{}' exits", peerUuid);
      this.socket.close();
    }
  }

  /*
  a class for Clients (which REQuest to Router)
  */
  private class Client implements Callable {
    private final UUID peerUuid = UUID.randomUUID();
    private Socket socket;
    private ZContext context;
    private String routerAddress;
    private List<String> msgsToSend;
    private final CountDownLatch shutdownLatch;

    Client(
        ZContext context,
        String routerAddress,
        List<String> msgsToSend,
        CountDownLatch shutdownLatch) {
      this.context = context;
      this.routerAddress = routerAddress;
      this.msgsToSend = msgsToSend;
      this.socket = this.context.createSocket(SocketType.REQ);
      this.shutdownLatch = shutdownLatch;
    }

    @Override
    public Object call() {
      // connect to router
      logger.debug("new client with identity: {}", peerUuid.toString());
      this.socket.setIdentity(peerUuid.toString().getBytes(ZMQ.CHARSET));
      this.socket.connect(this.routerAddress);

      final List<String> replies = new ArrayList<>();

      // send requests
      msgsToSend.stream()
          .forEach(
              m -> {
                this.socket.send(peerUuid.toString(), ZMQ.SNDMORE);
                this.socket.send(m, 0);
                logger.debug("sent req: {}", m);
                String reply = this.socket.recvStr();
                logger.debug("got reply: {}", reply);
                replies.add(reply);
              });

      this.socket.close();
      logger.debug("client is done");
      shutdownLatch.countDown();

      return replies;
    }
  }

  private final String ROUTER_ADDR = "tcp://0.0.0.0:5671";
  private final String DEALER_ADDR = "inproc://deal";
  private ZContext context;
  private List<Worker> workers;
  private List<Client> clients;
  private ServiceManager manager;
  private ExecutorService execService;
  private ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private DirectRequestDispatcher directRequestDispatcher;

  @Before
  public void setup() {
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    this.directRequestDispatcher =
        new DirectRequestDispatcher(
            UUID.randomUUID(),
            context,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "DirectRequestTest-Service",
            ROUTER_ADDR,
            DEALER_ADDR);
    initWorkers(3);

    final Set<Service> services = new HashSet<>(Arrays.asList(this.directRequestDispatcher));
    this.manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), context);
  }

  @After
  public void cleanup() throws Exception {
    manager.stopAsync().awaitStopped();

    // close local context
    execService.submit(
        () -> {
          context.close();
          logger.debug("context terminated");
        });

    // stop executor
    execService.shutdown();
    execService.awaitTermination(3, TimeUnit.SECONDS);
    logger.debug("executor shut down");
  }

  private void initWorkers(int numberOfWorkers) {
    workers = new ArrayList<>();
    for (int i = 0; i < numberOfWorkers; i++) {
      Worker worker = new Worker(this.context, DEALER_ADDR);
      workers.add(worker);
    }
    workers.stream().forEach(w -> execService.submit(w));
  }

  @Test
  public void clientsSendReqsGetWorkersRep() throws Exception {
    assertThat(directRequestDispatcher.isRunning(), is(true));

    // init clients
    clients = new ArrayList<>();
    ZContext remoteCtxt = createContext();
    short numberOfClients = 3;
    final CountDownLatch shutdownLatch = new CountDownLatch(numberOfClients);
    for (int i = 0; i < numberOfClients; i++) {
      clients.add(
          new Client(remoteCtxt, ROUTER_ADDR, Arrays.asList("Hello", "World", "!"), shutdownLatch));
    }

    // run clients and store Future replies
    Map<Client, Future<List<String>>> futureReplies = new HashMap<>();
    clients.stream()
        .forEach(
            c -> {
              Future<List<String>> cliReplies = execService.submit(c);
              futureReplies.put(c, cliReplies);
            });

    // wait for all clients to be finished
    shutdownLatch.await();

    // assert Future replies contain the client (i.e. sender) UUID as returned by the worker
    futureReplies.entrySet().stream()
        .forEach(
            entry -> {
              Client cli = entry.getKey();
              List<String> replies = null;
              try {
                replies = entry.getValue().get();
              } catch (Exception e) {
                fail();
                logger.error("error getting future value", e);
              }
              replies.stream()
                  .forEach(
                      r -> assertThat(r, containsString("from peer: " + cli.peerUuid.toString())));
            });

    // close remote context
    remoteCtxt.close();
    logger.debug("remote ctxt closed");
  }
}
