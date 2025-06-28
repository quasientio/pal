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

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZContext;

public class ConnectedServiceTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private final ThreadGroup threadGroup = new ThreadGroup("service-thread-group");

  private ConnectedService createService(UUID peerUuid, ZContext zmqContext, String serviceName) {
    return new ConnectedService(
        peerUuid, zmqContext, SYNC_SOCKET_ADDRESS, threadGroup, serviceName) {
      @Override
      @SuppressWarnings("BusyWait")
      protected void run() {
        while (!shutdownRequested) {
          // do something
          logger.debug("{} printing and sleeping", serviceName);
          try {
            Thread.sleep(300);
          } catch (InterruptedException e) {
            logger.error("Interrupted", e);
            Thread.currentThread().interrupt();
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
  public void test() throws InterruptedException {
    final ZContext zmqContext = createContext();

    // define services
    final Set<Service> services = new HashSet<>();
    services.add(createService(UUID.randomUUID(), zmqContext, "Test-Service-One"));
    services.add(createService(UUID.randomUUID(), zmqContext, "Test-Service-Two"));
    services.add(createService(UUID.randomUUID(), zmqContext, "Test-Service-Three"));
    final ServiceManager manager = new ServiceManager(services);

    manager.addListener(
        new ServiceManager.Listener() {
          @Override
          public void stopped() {
            logger.debug("Service manager stopped.");
          }

          @Override
          public void healthy() {
            // start accepting requests
            logger.debug("Managed services ready");
          }

          @Override
          public void failure(@Nonnull Service service) {
            logger.error("failed service: {} ", service, service.failureCause());
          }
        },
        Executors.newFixedThreadPool(1));

    manager.startAsync().awaitHealthy(); // we could skip awaiting here
    collectGoSignals(services.size(), zmqContext);
    // now we know all services have sync-ed (awaitHealthy() is no guarantee of all being running)
    manager.stopAsync();
    manager.awaitStopped();
    closeContext(zmqContext);
  }
}
