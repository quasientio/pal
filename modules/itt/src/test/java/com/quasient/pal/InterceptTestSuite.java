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
import com.quasient.pal.intercept.BeforeInterceptCallbackIT;
import com.quasient.pal.intercept.constructor.ConstructorAsyncCallbackIT;
import com.quasient.pal.intercept.constructor.ConstructorSyncCallbackIT;
import com.quasient.pal.intercept.instancefield.InstanceFieldAsyncCallbackIT;
import com.quasient.pal.intercept.instancefield.InstanceFieldSyncCallbackIT;
import com.quasient.pal.intercept.instancemethod.InstanceMethodAsyncCallbackIT;
import com.quasient.pal.intercept.instancemethod.InstanceMethodSyncCallbackIT;
import com.quasient.pal.intercept.staticfield.StaticFieldAsyncCallbackIT;
import com.quasient.pal.intercept.staticfield.StaticFieldSyncCallbackIT;
import com.quasient.pal.intercept.staticmethod.StaticMethodAsyncCallbackIT;
import com.quasient.pal.intercept.staticmethod.StaticMethodSyncCallbackIT;
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
  // New-style BEFORE intercept tests
  BeforeInterceptCallbackIT.class,
  // Instance method intercept tests
  InstanceMethodSyncCallbackIT.class,
  InstanceMethodAsyncCallbackIT.class,
  // Static method intercept tests
  StaticMethodSyncCallbackIT.class,
  StaticMethodAsyncCallbackIT.class,
  // Constructor intercept tests
  ConstructorSyncCallbackIT.class,
  ConstructorAsyncCallbackIT.class,
  // Static field intercept tests
  StaticFieldSyncCallbackIT.class,
  StaticFieldAsyncCallbackIT.class,
  // Instance field intercept tests
  InstanceFieldSyncCallbackIT.class,
  InstanceFieldAsyncCallbackIT.class
})
public class InterceptTestSuite extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(InterceptTestSuite.class);

  /**
   * Well-known UUID for the shared interceptable peer (the peer being intercepted).
   *
   * <p>This UUID is hardcoded to ensure tests can reliably find and connect to the peer. Uses a
   * different UUID than RpcTestSuite to avoid conflicts.
   */
  public static final UUID INTERCEPTABLE_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  /**
   * Well-known UUID for the interceptor peer (receives and handles intercept callbacks).
   *
   * <p>This peer runs the callback handlers and receives InterceptCallbackRequest messages from the
   * interceptable peer.
   */
  public static final UUID INTERCEPTOR_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000003");

  /** Interceptable peer process (the one being intercepted). */
  private static Process interceptablePeerProcess;

  /** Interceptor peer process (handles callbacks). */
  private static Process interceptorPeerProcess;

  /**
   * Backward compatibility alias for INTERCEPTABLE_PEER_UUID.
   *
   * @deprecated Use {@link #INTERCEPTABLE_PEER_UUID} instead
   */
  @Deprecated public static final UUID SHARED_PEER_UUID = INTERCEPTABLE_PEER_UUID;

  /** Helper instance to access non-static methods from AbstractIntegrationTest. */
  private static InterceptTestSuite instance;

  /**
   * Launches the shared intercept test peers before any tests run.
   *
   * <p>Two peers are launched:
   *
   * <ul>
   *   <li><b>Interceptable peer</b> ({@link #INTERCEPTABLE_PEER_UUID}): Runs the test applications
   *       (StringMethods, InterceptableApp, etc.). Configured with --interceptable flag.
   *   <li><b>Interceptor peer</b> ({@link #INTERCEPTOR_PEER_UUID}): Handles intercept callbacks.
   *       Receives InterceptCallbackRequest messages and invokes callback handlers.
   * </ul>
   *
   * @throws Exception if peer launch fails
   */
  @BeforeClass
  public static void launchSharedPeers() throws Exception {
    logger.info("============================================================");
    logger.info("Launching shared intercept test peers");
    logger.info("  Interceptable peer UUID: {}", INTERCEPTABLE_PEER_UUID);
    logger.info("  Interceptor peer UUID:   {}", INTERCEPTOR_PEER_UUID);
    logger.info("============================================================");

    // Create helper instance to access non-static methods
    instance = new InterceptTestSuite();

    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Build classpath for itt-apps (both peers need access to test application classes)
    String ittAppsClasspath = String.format("%s/modules/itt-apps/target/classes", palHome);

    // Launch interceptable peer (the peer being intercepted)
    logger.info("Launching interceptable peer...");
    interceptablePeerProcess =
        instance.launchPeer(
            INTERCEPTABLE_PEER_UUID,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            "interceptable-peer",
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
            "itt-interceptable",
            "-cp",
            ittAppsClasspath);
    logger.info("Interceptable peer launched successfully");

    // Launch interceptor peer (handles callbacks)
    logger.info("Launching interceptor peer...");
    interceptorPeerProcess =
        instance.launchPeer(
            INTERCEPTOR_PEER_UUID,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            "interceptor-peer",
            "--zmq-rpc",
            "5658",
            "--json-rpc",
            "7791",
            "--rpc-threads",
            "2",
            "--rpc-allow-nonpublic",
            "--log",
            "auto",
            "--log-prefix",
            "itt-interceptor",
            "-cp",
            ittAppsClasspath);
    logger.info("Interceptor peer launched successfully");

    logger.info("All intercept test peers launched successfully");
  }

  /**
   * Stops the shared intercept test peers after all tests complete.
   *
   * @throws Exception if peer stop fails
   */
  @AfterClass
  public static void stopSharedPeers() throws Exception {
    logger.info("============================================================");
    logger.info("Stopping shared intercept test peers");
    logger.info("============================================================");

    if (instance != null) {
      // Stop both peer processes
      if (interceptablePeerProcess != null) {
        logger.info("Stopping interceptable peer process...");
        instance.stopPeer(interceptablePeerProcess);
        interceptablePeerProcess = null;
        logger.info("Interceptable peer process stopped");
      }

      if (interceptorPeerProcess != null) {
        logger.info("Stopping interceptor peer process...");
        instance.stopPeer(interceptorPeerProcess);
        interceptorPeerProcess = null;
        logger.info("Interceptor peer process stopped");
      }

      // Now unregister peers from the directory (after processes are stopped)
      PalDirectory palDirectory = null;
      try {
        DirectoryConnectionProvider directoryConnectionProvider =
            new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);
        palDirectory =
            directoryConnectionProvider
                .get()
                .orElseThrow(() -> new RuntimeException("No connection for PalDirectory"));

        logger.info("Unregistering interceptable peer {} from directory", INTERCEPTABLE_PEER_UUID);
        palDirectory.deletePeer(INTERCEPTABLE_PEER_UUID);

        logger.info("Unregistering interceptor peer {} from directory", INTERCEPTOR_PEER_UUID);
        palDirectory.deletePeer(INTERCEPTOR_PEER_UUID);

        logger.info("Peers unregistered from directory");

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
        logger.warn("Failed to unregister peers from directory (may already be gone)", e);
      } finally {
        if (palDirectory != null) {
          try {
            palDirectory.close();
          } catch (Exception e) {
            logger.warn("Error closing palDirectory", e);
          }
        }
      }

      logger.info("Shared intercept test peers stopped and cleaned up successfully");
    }
  }
}
