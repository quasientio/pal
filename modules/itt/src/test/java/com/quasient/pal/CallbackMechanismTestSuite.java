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
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.intercept.MethodInterceptIT;
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
import com.quasient.pal.messages.LogMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.messages.jsonrpc.JsonRpcMessage;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import java.util.List;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test suite for intercept callback mechanism tests.
 *
 * <p>This suite tests that intercept callbacks are correctly dispatched and structured. These tests
 * verify that callbacks are <b>sent to the right peer with the right content</b>, but do NOT
 * execute callback handlers.
 *
 * <p><b>Testing Pattern - Callback Dispatch Mechanism:</b>
 *
 * <ol>
 *   <li>Test creates ThinPeer (accessed via {@code myPeerUuid}) to accumulate callbacks
 *   <li>Test registers intercept with <b>{@code myPeerUuid}</b> as callback peer
 *   <li>Test invokes intercepted method/field/constructor on interceptable peer
 *   <li>Interceptable peer matches intercept and sends {@code InterceptCallbackRequest} to ThinPeer
 *   <li><b>ThinPeer accumulates callback (does NOT execute handler)</b>
 *   <li>Test retrieves callbacks via {@code getCallbacks(n, timeout)}
 *   <li>Test verifies callback structure (message type, class, method, parameters, etc.)
 * </ol>
 *
 * <p><b>Key Difference from InterceptTestSuite:</b> This suite tests that callbacks are <b>sent and
 * structured correctly</b>. For testing that handlers <b>execute and produce effects</b>, see
 * {@link InterceptTestSuite}.
 *
 * <p><b>Why No INTERCEPTOR_PEER_UUID Constant:</b> This suite intentionally does NOT define {@code
 * INTERCEPTOR_PEER_UUID} to provide <b>compile-time safety</b>. Tests in this suite should never
 * use that UUID - they register with {@code myPeerUuid} (the ThinPeer). If a test tries to use
 * {@code INTERCEPTOR_PEER_UUID}, compilation will fail, preventing the bug.
 *
 * <p><b>Architecture:</b> This suite launches only the interceptable peer:
 *
 * <ul>
 *   <li><b>Interceptable peer</b> ({@link #INTERCEPTABLE_PEER_UUID}): Runs the test applications
 *       (StringMethods, InterceptableApp, etc.) with {@code --interceptable} flag. Sends callback
 *       requests when intercepts match.
 *   <li><b>ThinPeer (per test)</b>: Each test creates its own ThinPeer via {@code
 *       AbstractInterceptIT.setUp()}. Receives and accumulates {@code InterceptCallbackRequest}
 *       messages without executing handlers. Accessed via {@code myPeerUuid}.
 * </ul>
 *
 * <p>The shared interceptable peer is configured with:
 *
 * <ul>
 *   <li>ZMQ RPC on port 5657
 *   <li>JSON-RPC on port 7790
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
  // Method intercept mechanism tests
  MethodInterceptIT.class,

  // Instance method callback tests
  InstanceMethodSyncCallbackIT.class,
  InstanceMethodAsyncCallbackIT.class,

  // Static method callback tests
  StaticMethodSyncCallbackIT.class,
  StaticMethodAsyncCallbackIT.class,

  // Constructor callback tests
  ConstructorSyncCallbackIT.class,
  ConstructorAsyncCallbackIT.class,

  // Static field callback tests
  StaticFieldSyncCallbackIT.class,
  StaticFieldAsyncCallbackIT.class,

  // Instance field callback tests
  InstanceFieldSyncCallbackIT.class,
  InstanceFieldAsyncCallbackIT.class
})
public class CallbackMechanismTestSuite extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(CallbackMechanismTestSuite.class);

  /**
   * Well-known UUID for the shared interceptable peer (the peer being intercepted).
   *
   * <p>This UUID is hardcoded to ensure tests can reliably find and connect to the peer.
   */
  public static final UUID INTERCEPTABLE_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  /** Interceptable peer process (the one being intercepted). */
  private static Process interceptablePeerProcess;

  /** ThinPeer for reading the interceptable peer's WAL for debugging. */
  private static ThinPeer walReaderThinPeer;

  /** Helper instance to access non-static methods from AbstractIntegrationTest. */
  private static CallbackMechanismTestSuite instance;

  /**
   * Launches the shared interceptable peer before any tests run.
   *
   * <p>Only one peer is launched:
   *
   * <ul>
   *   <li><b>Interceptable peer</b> ({@link #INTERCEPTABLE_PEER_UUID}): Runs the test applications
   *       (StringMethods, InterceptableApp, etc.). Configured with --interceptable flag.
   * </ul>
   *
   * <p>Each test creates its own ThinPeer to receive callbacks (accessed via {@code myPeerUuid}).
   *
   * @throws Exception if peer launch fails
   */
  @BeforeClass
  public static void launchSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Launching shared interceptable peer for callback mechanism tests");
    logger.info("  Interceptable peer UUID: {}", INTERCEPTABLE_PEER_UUID);
    logger.info("============================================================");

    // Create helper instance to access non-static methods
    instance = new CallbackMechanismTestSuite();

    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Build classpath for itt-apps
    String userHome = System.getProperty("user.home");
    String ittAppsClasses = String.format("%s/modules/itt-apps/target/classes", palHome);
    String slf4jApi =
        String.format("%s/.m2/repository/org/slf4j/slf4j-api/2.0.7/slf4j-api-2.0.7.jar", userHome);
    String logbackClassic =
        String.format(
            "%s/.m2/repository/ch/qos/logback/logback-classic/1.5.13/logback-classic-1.5.13.jar",
            userHome);
    String logbackCore =
        String.format(
            "%s/.m2/repository/ch/qos/logback/logback-core/1.5.13/logback-core-1.5.13.jar",
            userHome);
    String ittAppsClasspath =
        String.join(":", ittAppsClasses, slf4jApi, logbackClassic, logbackCore);

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
            "--rpc-threads",
            "3",
            "--rpc-allow-nonpublic",
            "--interceptable",
            "--log",
            "auto",
            "--log-prefix",
            "cbm-interceptable",
            "-cp",
            ittAppsClasspath);
    logger.info("Interceptable peer launched successfully");

    // Create a ThinPeer to read the WAL for debugging purposes
    // Find the log name created by the peer (starts with "cbm-interceptable")
    DirectoryConnectionProvider directoryConnectionProvider =
        new DirectoryConnectionProvider(palDirectory);
    PalDirectory directory =
        directoryConnectionProvider
            .get()
            .orElseThrow(() -> new RuntimeException("Could not connect to directory"));
    try {
      String walLogName = null;
      for (LogInfo log : directory.listAllLogs()) {
        if (log.getName().startsWith("cbm-interceptable")) {
          walLogName = log.getName();
          logger.info("Found WAL log: {}", walLogName);
          break;
        }
      }
      if (walLogName != null) {
        walReaderThinPeer = createLogReaderThinPeer(walLogName);
        logger.info("Created WAL reader ThinPeer for log: {}", walLogName);
      } else {
        logger.warn("Could not find WAL log starting with 'cbm-interceptable'");
      }
    } finally {
      directory.close();
    }
  }

  /**
   * Stops the shared interceptable peer after all tests complete.
   *
   * @throws Exception if peer stop fails
   */
  @AfterClass
  public static void stopSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Stopping shared interceptable peer");
    logger.info("============================================================");

    if (instance != null) {
      // Stop peer process
      if (interceptablePeerProcess != null) {
        logger.info("Stopping interceptable peer process...");
        instance.stopPeer(interceptablePeerProcess);
        interceptablePeerProcess = null;
        logger.info("Interceptable peer process stopped");
      }

      // Dump WAL contents for debugging before cleanup
      dumpWalMessages();

      // Close the WAL reader ThinPeer
      if (walReaderThinPeer != null) {
        try {
          walReaderThinPeer.close();
          logger.info("WAL reader ThinPeer closed");
        } catch (Exception e) {
          logger.warn("Error closing WAL reader ThinPeer", e);
        }
        walReaderThinPeer = null;
      }

      // Now unregister peer from the directory (after process is stopped)
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
        logger.info("Peer unregistered from directory");

        // Delete logs created by this suite (with prefix "cbm")
        logger.info("Deleting logs created by CallbackMechanismTestSuite");
        for (LogInfo log : palDirectory.listAllLogs()) {
          if (log.getName().startsWith("cbm")) {
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

      logger.info("Shared interceptable peer stopped and cleaned up successfully");
    }
  }

  /**
   * Dumps all WAL messages to the debug log for test debugging.
   *
   * <p>This method reads all messages from the interceptable peer's WAL and logs them at DEBUG
   * level as pretty-printed JSON. The output can be viewed in modules/itt/logs/tests.log when
   * debugging test failures.
   */
  private static void dumpWalMessages() {
    if (walReaderThinPeer == null) {
      logger.warn("WAL reader not available, skipping WAL dump");
      return;
    }

    try {
      logger.info("============================================================");
      logger.info("Dumping WAL messages for debugging");
      logger.info("============================================================");

      List<LogMessage<?>> messages = walReaderThinPeer.getAllWalMessages();
      logger.info("WAL contains {} messages", messages.size());

      int index = 0;
      for (LogMessage<?> message : messages) {
        String prettyJson = getMessageContentAsPrettyJson(message);
        logger.debug("WAL[{}]:\n{}", index++, prettyJson);
      }

      logger.info("WAL dump complete");
    } catch (Exception e) {
      logger.warn("Failed to dump WAL messages", e);
    }
  }

  /**
   * Converts the content of a log message into a pretty-printed JSON string.
   *
   * @param logMessage the log message containing the content to be serialized
   * @return a pretty-printed JSON representation of the message content
   */
  private static String getMessageContentAsPrettyJson(LogMessage<?> logMessage) {
    if (logMessage.getContent() instanceof Message message) {
      return ColferUtils.toJson(message, true);
    } else if (logMessage.getContent() instanceof JsonRpcMessage jsonRpcMessage) {
      try {
        return JsonRpcSerializer.toPrettyJson(jsonRpcMessage);
      } catch (Exception e) {
        return "Failed to serialize JSON-RPC message: " + e.getMessage();
      }
    } else {
      return "Unknown message type: " + logMessage.getContent().getClass().getName();
    }
  }
}
