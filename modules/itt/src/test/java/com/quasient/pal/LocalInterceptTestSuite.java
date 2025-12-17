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
import com.quasient.pal.intercept.local.constructor.LocalConstructorAsyncCallbackIT;
import com.quasient.pal.intercept.local.constructor.LocalConstructorSyncCallbackIT;
import com.quasient.pal.intercept.local.field.LocalFieldAsyncCallbackIT;
import com.quasient.pal.intercept.local.field.LocalFieldSyncCallbackIT;
import com.quasient.pal.intercept.local.method.LocalMethodAsyncCallbackIT;
import com.quasient.pal.intercept.local.method.LocalMethodSyncCallbackIT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test suite for local intercept integration tests.
 *
 * <p>Local intercepts are intercepts where the callback peer UUID equals the interceptable peer
 * UUID, meaning the callback handler runs in the same JVM as the intercepted code. This enables:
 *
 * <ul>
 *   <li><b>No serialization:</b> Arguments and return values are live Java objects
 *   <li><b>No network latency:</b> Direct method invocation (~1μs vs ~1ms for remote)
 *   <li><b>Same heap:</b> No ObjectRef translation needed
 * </ul>
 *
 * <p><b>Architecture:</b> This suite launches only one peer:
 *
 * <ul>
 *   <li><b>Interceptable peer</b> ({@link #INTERCEPTABLE_PEER_UUID}): Runs the test applications
 *       (InterceptableApp, etc.) with {@code --interceptable} flag. Both handles intercept matching
 *       AND executes callbacks locally (since callback peer = interceptable peer).
 * </ul>
 *
 * <p><b>Note:</b> Unlike {@link InterceptEndToEndTestSuite} which requires two peers (interceptable
 * + interceptor), local intercepts only need one peer since the callback executes in the same JVM.
 *
 * <p>The shared interceptable peer is configured with:
 *
 * <ul>
 *   <li>ZMQ RPC on port 5659
 *   <li>3 RPC threads
 *   <li>Allow non-public method invocation
 *   <li><b>Interception enabled (--interceptable)</b>
 *   <li>ITT apps classes on classpath
 * </ul>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  // Local method intercept tests
  LocalMethodSyncCallbackIT.class,
  LocalMethodAsyncCallbackIT.class,

  // Local constructor intercept tests
  LocalConstructorSyncCallbackIT.class,
  LocalConstructorAsyncCallbackIT.class,

  // Local field intercept tests
  LocalFieldSyncCallbackIT.class,
  LocalFieldAsyncCallbackIT.class
})
public class LocalInterceptTestSuite extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(LocalInterceptTestSuite.class);

  /** Path to the application log file where callback invocations are logged. */
  private static Path appLogPath;

  /** Default timeout in seconds for waiting for log lines. */
  private static final int APP_LOG_TIMEOUT_SECONDS = 5;

  /**
   * Well-known UUID for the shared interceptable peer.
   *
   * <p>This UUID is shared with other intercept test suites since they all target the same
   * interceptable peer. For local intercepts, this peer also serves as the callback peer.
   */
  public static final UUID INTERCEPTABLE_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000002");

  /** Interceptable peer process. */
  private static PeerProcess interceptablePeerProcess;

  /** Helper instance to access non-static methods from AbstractIntegrationTest. */
  private static LocalInterceptTestSuite instance;

  /**
   * Waits for a log line matching the given regex pattern to appear in the application log.
   *
   * @param regex the regular expression pattern to match
   * @return true if a matching line was found, false if timeout was reached
   */
  public static boolean waitForAppLogLine(String regex) {
    return waitForAppLogLine(regex, APP_LOG_TIMEOUT_SECONDS);
  }

  /**
   * Waits for a log line matching the given regex pattern to appear in the application log.
   *
   * <p>This method polls the application log file (itt-apps.log) until a line matching the pattern
   * is found or the timeout is reached.
   *
   * @param regex the regular expression pattern to match
   * @param timeoutSeconds maximum time to wait in seconds
   * @return true if a matching line was found, false if timeout was reached
   */
  public static boolean waitForAppLogLine(String regex, int timeoutSeconds) {
    Pattern pattern = Pattern.compile(regex);
    long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

    while (System.currentTimeMillis() < deadline) {
      try {
        if (Files.exists(appLogPath)) {
          for (String line : Files.readAllLines(appLogPath)) {
            if (pattern.matcher(line).find()) {
              return true;
            }
          }
        }
        Thread.sleep(100);
      } catch (IOException | InterruptedException e) {
        logger.warn("Error reading application log", e);
        return false;
      }
    }
    return false;
  }

  /**
   * Clears the application log file before each test run.
   *
   * @throws IOException if the log file cannot be deleted
   */
  public static void clearAppLog() throws IOException {
    if (Files.exists(appLogPath)) {
      Files.delete(appLogPath);
    }
  }

  /**
   * Launches the shared interceptable peer before any tests run.
   *
   * <p>Only one peer is launched because for local intercepts, the callback peer = interceptable
   * peer. The peer has both the interceptable applications and the callback handlers on its
   * classpath.
   *
   * @throws Exception if peer launch fails
   */
  @BeforeClass
  public static void launchSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Launching shared peer for local intercept tests");
    logger.info("  Interceptable peer UUID: {}", INTERCEPTABLE_PEER_UUID);
    logger.info("============================================================");

    // Create helper instance to access non-static methods
    instance = new LocalInterceptTestSuite();

    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    // Initialize application log path and clear any existing log
    appLogPath = Paths.get(palHome, "logs", "itt-apps.log");
    try {
      Files.deleteIfExists(appLogPath);
      logger.info("Cleared application log file: {}", appLogPath);
    } catch (IOException e) {
      logger.warn("Failed to clear application log file: {}", appLogPath, e);
    }

    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Build classpath for itt-apps (includes both interceptable classes and callback handlers)
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

    // Launch interceptable peer
    logger.info("Launching interceptable peer...");
    interceptablePeerProcess =
        instance.launchPeer(
            INTERCEPTABLE_PEER_UUID,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            "local-intercept-peer",
            "--zmq-rpc",
            "5659",
            "--rpc-threads",
            "3",
            "--rpc-allow-nonpublic",
            "--interceptable",
            "--log",
            "auto",
            "--log-prefix",
            "local-intercept",
            "-cp",
            ittAppsClasspath);
    logger.info("Interceptable peer launched successfully");
  }

  /**
   * Stops the shared interceptable peer after all tests complete.
   *
   * @throws Exception if peer stop fails
   */
  @AfterClass
  public static void stopSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Stopping shared peer for local intercept tests");
    logger.info("============================================================");

    if (instance != null) {
      // Stop peer process
      if (interceptablePeerProcess != null) {
        logger.info("Stopping interceptable peer process...");
        instance.stopPeer(interceptablePeerProcess);
        interceptablePeerProcess = null;
        logger.info("Interceptable peer process stopped");
      }

      // Unregister peer from the directory
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

        // Delete logs created by this suite (with prefix "local-intercept")
        logger.info("Deleting logs created by LocalInterceptTestSuite");
        for (LogInfo log : palDirectory.listAllLogs()) {
          if (log.getName().startsWith("local-intercept")) {
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

      logger.info("Shared peer stopped and cleaned up successfully");
    }
  }
}
