package com.ittera.cometa.core;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class ConnectedServiceTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private final String SYNC_SOCKET_ADDRESS = "inproc://sync_socket";
  private ZMQ.Socket syncSocket;
  private final ThreadGroup threadGroup = new ThreadGroup("service-thread-group");

  private ConnectedService createService(UUID peerUuid, ZContext zmqContext, String serviceName) {
    return new ConnectedService(
        peerUuid, zmqContext, SYNC_SOCKET_ADDRESS, threadGroup, serviceName) {
      @Override
      protected void run() {
        while (!shutdownRequested) {
          // do something
          logger.debug("{} printing and sleeping", serviceName);
          try {
            Thread.sleep(400);
          } catch (InterruptedException e) {
          }
        }
      }

      @Override
      protected void openConnections() {
        logger.debug("{} opening connections", serviceName);
      }

      @Override
      protected void closeConnections() {
        logger.debug("{} closing connections", serviceName);
      }
    };
  }

  @Test
  public void test() {
    final ZContext zmqContext = createContext();

    // start sync PULL socket
    syncSocket = zmqContext.createSocket(SocketType.PULL);
    syncSocket.bind(SYNC_SOCKET_ADDRESS);

    // define services
    final Set<Service> services = new HashSet<>();
    services.add(createService(UUID.randomUUID(), zmqContext, "Test-Service-One"));
    services.add(createService(UUID.randomUUID(), zmqContext, "Test-Service-Two"));
    services.add(createService(UUID.randomUUID(), zmqContext, "Test-Service-Three"));
    final ServiceManager manager = new ServiceManager(services);

    manager.addListener(
        new ServiceManager.Listener() {
          public void stopped() {
            logger.debug("Service manager stopped.");
          }

          public void healthy() {
            // start accepting requests
            logger.debug("Managed services ready");
          }

          public void failure(Service service) {
            logger.debug("failure: {} ", service.failureCause());
          }
        });

    manager.startAsync();
    manager.awaitHealthy(); // we could skip awaiting here
    // collect all READY signals before proceeding
    CountDownLatch latch = new CountDownLatch(services.size());
    int ready = 0;
    while (latch.getCount() > 0) {
      String rcvd = syncSocket.recvStr();
      assertThat(rcvd, is("go!"));
      logger.debug("got {} ready!", ++ready);
      latch.countDown();
    }
    syncSocket.close();
    // now we know all services have sync'ed (awaitHealthy() is no guarantee of all being running)
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    manager.stopAsync();
    manager.awaitStopped();
  }
}
