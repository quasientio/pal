/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal;

import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.intercept.MethodInterceptIT;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test suite for intercept integration tests.
 *
 * <p>This suite automatically launches a shared peer process with interception enabled before
 * running any tests and stops it afterwards.
 *
 * <p>The shared peer is configured with:
 *
 * <ul>
 *   <li>ZMQ RPC on port 5657 (different from RPC suite to avoid conflicts)
 *   <li>JSON-RPC on port 7790 (different from RPC suite to avoid conflicts)
 *   <li>3 RPC threads
 *   <li>Allow non-public method invocation
 *   <li><b>Interception enabled (--interceptable)</b>
 *   <li>ITT apps classes on classpath
 * </ul>
 *
 * <p><b>Note:</b> This peer is shared across all tests in the suite for performance. Tests must not
 * rely on mutable peer state or interfere with each other.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  // Intercept tests
  MethodInterceptIT.class
})
public class InterceptTestSuite extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(InterceptTestSuite.class);

  /**
   * Well-known UUID for the shared intercept test peer.
   *
   * <p>This UUID is hardcoded to ensure tests can reliably find and connect to the peer. Uses a
   * different UUID than RpcTestSuite to avoid conflicts.
   */
  public static final UUID SHARED_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  /** Shared peer process, launched once for all tests in the suite. */
  private static Process sharedPeerProcess;

  /** Helper instance to access non-static methods from AbstractIntegrationTest. */
  private static InterceptTestSuite instance;

  /**
   * Launches the shared intercept peer before any tests run.
   *
   * <p>The peer is configured with minimal flags needed for intercept tests, including the
   * --interceptable flag.
   *
   * @throws Exception if peer launch fails
   */
  @BeforeClass
  public static void launchSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Launching shared intercept test peer with UUID: {}", SHARED_PEER_UUID);
    logger.info("============================================================");

    // Create helper instance to access non-static methods
    instance = new InterceptTestSuite();

    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Build classpath for itt-apps
    // NOTE: Use the actual JAR file, not a glob pattern, as globs are not expanded in the -cp
    // argument
    String ittAppsClasspath =
        String.format(
            "%s/modules/itt-apps/target/classes:%s/modules/itt-apps/target/classes",
            palHome, palHome);

    sharedPeerProcess =
        instance.launchTransientPeer(
            SHARED_PEER_UUID,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            "peer-for-intercept-tests",
            "--zmq-rpc",
            "5657",
            "--json-rpc",
            "7790",
            "--rpc-threads",
            "3",
            "--rpc-allow-nonpublic",
            "--interceptable",
            "--log",
            "auto",
            "--log-prefix",
            "itt",
            "-cp",
            ittAppsClasspath);

    logger.info("Shared intercept test peer launched successfully");
  }

  /**
   * Stops the shared intercept peer after all tests complete.
   *
   * @throws Exception if peer stop fails
   */
  @AfterClass
  public static void stopSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Stopping shared intercept test peer");
    logger.info("============================================================");

    if (sharedPeerProcess != null && instance != null) {
      // IMPORTANT: Stop the peer process FIRST to stop the keep-alive thread
      // If we delete from directory while process is running, it may re-register
      instance.stopPeer(sharedPeerProcess);
      sharedPeerProcess = null;
      logger.info("Shared intercept test peer process stopped");

      // Now unregister the peer from the directory (after process is stopped)
      PalDirectory palDirectory = null;
      try {
        DirectoryConnectionProvider directoryConnectionProvider =
            new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);
        palDirectory =
            directoryConnectionProvider
                .get()
                .orElseThrow(() -> new RuntimeException("No connection for PalDirectory"));
        logger.info("Unregistering peer {} from directory", SHARED_PEER_UUID);
        palDirectory.deletePeer(SHARED_PEER_UUID);
        logger.info("Peer unregistered from directory");

        // Delete logs created by this suite (with prefix "itt")
        logger.info("Deleting logs created by InterceptTestSuite");
        for (com.quasient.pal.common.directory.nodes.LogInfo log : palDirectory.listAllLogs()) {
          if (log.getName().startsWith("itt")) {
            logger.info("Deleting log: {}", log.getName());
            palDirectory.deleteLog(log.getName());
          }
        }
        logger.info("Logs cleaned up");
      } catch (Exception e) {
        logger.warn("Failed to unregister peer from directory (may already be gone)", e);
      } finally {
        if (palDirectory != null) {
          try {
            palDirectory.close();
          } catch (Exception e) {
            logger.warn("Error closing palDirectory", e);
          }
        }
      }

      logger.info("Shared intercept test peer stopped and cleaned up successfully");
    }
  }
}
