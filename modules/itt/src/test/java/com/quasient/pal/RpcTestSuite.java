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

import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.rpc.binary.CallMessageIT;
import com.quasient.pal.rpc.binary.ComplexArgsMessageIT;
import com.quasient.pal.rpc.binary.ConstructorMessageIT;
import com.quasient.pal.rpc.binary.ControlMessageIT;
import com.quasient.pal.rpc.binary.GetArrayMessageIT;
import com.quasient.pal.rpc.binary.GetMessageIT;
import com.quasient.pal.rpc.binary.MetaMessageIT;
import com.quasient.pal.rpc.binary.PutMessageIT;
import com.quasient.pal.rpc.json.CallArrayMessageIT;
import com.quasient.pal.rpc.json.JsonRpcResponseErrorIT;
import com.quasient.pal.rpc.json.PutGetArrayMessageIT;
import com.quasient.pal.rpc.json.dsl.RpcChainIT;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test suite for RPC integration tests.
 *
 * <p>This suite automatically launches a shared peer process before running any tests and stops it
 * afterwards.
 *
 * <p>The shared peer is configured with:
 *
 * <ul>
 *   <li>ZMQ RPC on port 5656
 *   <li>JSON-RPC on port 7789
 *   <li>3 RPC threads
 *   <li>Allow non-public method invocation
 *   <li>ITT apps classes on classpath
 * </ul>
 *
 * <p><b>Note:</b> This peer is shared across all tests in the suite for performance. Tests must not
 * rely on mutable peer state or interfere with each other.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  // Binary RPC tests
  CallMessageIT.class,
  ComplexArgsMessageIT.class,
  ConstructorMessageIT.class,
  ControlMessageIT.class,
  GetArrayMessageIT.class,
  GetMessageIT.class,
  MetaMessageIT.class,
  PutMessageIT.class,

  // JSON RPC tests
  com.quasient.pal.rpc.json.CallMessageIT.class,
  CallArrayMessageIT.class,
  com.quasient.pal.rpc.json.ComplexArgsMessageIT.class,
  com.quasient.pal.rpc.json.ConstructorMessageIT.class,
  com.quasient.pal.rpc.json.ControlMessageIT.class,
  com.quasient.pal.rpc.json.GetArrayMessageIT.class,
  com.quasient.pal.rpc.json.GetMessageIT.class,
  JsonRpcResponseErrorIT.class,
  com.quasient.pal.rpc.json.MetaMessageIT.class,
  com.quasient.pal.rpc.json.PutMessageIT.class,
  PutGetArrayMessageIT.class,
  RpcChainIT.class
})
@SuppressWarnings("PMD.NoFullyQualifiedTypes")
public class RpcTestSuite extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(RpcTestSuite.class);

  /**
   * Well-known UUID for the shared RPC test peer.
   *
   * <p>This UUID is hardcoded to ensure tests can reliably find and connect to the peer.
   */
  public static final UUID SHARED_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000001");

  /** Shared peer process, launched once for all tests in the suite. */
  private static Process sharedPeerProcess;

  /** Helper instance to access non-static methods from AbstractIntegrationTest. */
  private static RpcTestSuite instance;

  /**
   * Launches the shared RPC peer before any tests run.
   *
   * <p>The peer is configured with minimal flags needed for RPC tests.
   *
   * @throws Exception if peer launch fails
   */
  @BeforeClass
  public static void launchSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Launching shared RPC test peer with UUID: {}", SHARED_PEER_UUID);
    logger.info("============================================================");

    // Create helper instance to access non-static methods
    instance = new RpcTestSuite();

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
        instance.launchPeer(
            SHARED_PEER_UUID,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            "peer-for-rpc-tests",
            "--zmq-rpc",
            "5656",
            "--json-rpc",
            "7789",
            "--rpc-threads",
            "3",
            "--rpc-allow-nonpublic",
            "--log",
            "auto",
            "--log-prefix",
            "itt",
            "-cp",
            ittAppsClasspath);

    logger.info("Shared RPC test peer launched successfully");
  }

  /**
   * Stops the shared RPC peer after all tests complete.
   *
   * @throws Exception if peer stop fails
   */
  @AfterClass
  public static void stopSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Stopping shared RPC test peer");
    logger.info("============================================================");

    if (sharedPeerProcess != null && instance != null) {
      // IMPORTANT: Stop the peer process FIRST to stop the keep-alive thread
      // If we delete from directory while process is running, it may re-register
      logger.info("About to call stopPeer on process, isAlive={}", sharedPeerProcess.isAlive());
      instance.stopPeer(sharedPeerProcess);
      logger.info("stopPeer returned successfully");
      sharedPeerProcess = null;
      logger.info("Shared RPC test peer process stopped");

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
        logger.info("Deleting logs created by RpcTestSuite");
        for (LogInfo log : palDirectory.listAllLogs()) {
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

      logger.info("Shared RPC test peer stopped and cleaned up successfully");
    }
  }
}
