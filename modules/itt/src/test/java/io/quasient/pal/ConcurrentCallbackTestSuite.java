/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.intercept.concurrent.ConcurrentCallbackIT;
import java.io.FileOutputStream;
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
 * Test suite for concurrent callback integration tests.
 *
 * <p>This suite is specifically designed for testing concurrent callback invocations where multiple
 * threads invoke intercepted operations simultaneously. It differs from {@link
 * LocalInterceptTestSuite} in the following ways:
 *
 * <ul>
 *   <li><b>Higher RPC threads:</b> Configured with {@value #RPC_THREAD_COUNT} threads to handle
 *       concurrent RPC requests
 *   <li><b>Separate port:</b> Uses port {@value #ZMQ_RPC_PORT} to avoid conflicts with other suites
 *   <li><b>Longer timeouts:</b> Tests have longer timeouts to accommodate concurrent execution
 * </ul>
 *
 * <p><b>Architecture:</b> This suite launches a single peer that handles both interception and
 * callbacks (local intercept pattern where callback peer = interceptable peer).
 *
 * <p>The shared peer is configured with:
 *
 * <ul>
 *   <li>ZMQ RPC on port {@value #ZMQ_RPC_PORT}
 *   <li>{@value #RPC_THREAD_COUNT} RPC threads (required for 100+ concurrent calls)
 *   <li>Allow non-public method invocation
 *   <li><b>Interception enabled (--interceptable)</b>
 *   <li>ITT apps classes on classpath
 * </ul>
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({ConcurrentCallbackIT.class})
public class ConcurrentCallbackTestSuite extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(ConcurrentCallbackTestSuite.class);

  /**
   * Number of RPC threads for the interceptable peer.
   *
   * <p>This must be at least equal to the number of concurrent calls expected in tests (e.g., 100)
   * to avoid request serialization.
   */
  public static final int RPC_THREAD_COUNT = 100;

  /** ZMQ RPC port for this suite. Different from LocalInterceptTestSuite to avoid conflicts. */
  public static final int ZMQ_RPC_PORT = 5660;

  /** Path to the application log file where callback invocations are logged. */
  private static Path appLogPath;

  /** Default timeout in seconds for waiting for log lines. */
  private static final int APP_LOG_TIMEOUT_SECONDS = 10;

  /**
   * Well-known UUID for the shared interceptable peer.
   *
   * <p>This UUID is unique to this suite to avoid conflicts with other test suites. For local
   * intercepts, this peer also serves as the callback peer.
   */
  public static final UUID INTERCEPTABLE_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000004");

  /** Interceptable peer process. */
  private static PeerProcess interceptablePeerProcess;

  /** Helper instance to access non-static methods from AbstractIntegrationTest. */
  private static ConcurrentCallbackTestSuite instance;

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
   * <p>This method TRUNCATES the file rather than deleting it because the peer's logback
   * FileAppender holds an open file descriptor to the log file. Deleting the file would orphan that
   * file descriptor, causing subsequent log writes to go to /dev/null (the orphaned inode).
   *
   * @throws IOException if the log file cannot be truncated
   */
  public static void clearAppLog() throws IOException {
    if (Files.exists(appLogPath)) {
      // Truncate instead of delete - this preserves the file's inode so the open FileOutputStream
      // continues to write to the same file
      try (FileOutputStream fos = new FileOutputStream(appLogPath.toFile(), false)) {
        // Opening with append=false truncates the file to zero length
      }
    }
  }

  /**
   * Launches the shared interceptable peer before any tests run.
   *
   * <p>The peer is configured with a high number of RPC threads to handle concurrent callback
   * invocations.
   *
   * @throws Exception if peer launch fails
   */
  @BeforeClass
  public static void launchSharedPeer() throws Exception {
    logger.info("============================================================");
    logger.info("Launching shared peer for concurrent callback tests");
    logger.info("  Interceptable peer UUID: {}", INTERCEPTABLE_PEER_UUID);
    logger.info("  RPC thread count: {}", RPC_THREAD_COUNT);
    logger.info("  ZMQ RPC port: {}", ZMQ_RPC_PORT);
    logger.info("============================================================");

    // Create helper instance to access non-static methods
    instance = new ConcurrentCallbackTestSuite();

    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    // Initialize application log path and clear any existing log
    appLogPath = Paths.get(palHome, "logs", "itt-apps-concurrent.log");
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

    // Launch interceptable peer with high thread count
    logger.info("Launching interceptable peer with {} RPC threads...", RPC_THREAD_COUNT);
    interceptablePeerProcess =
        instance.launchPeer(
            INTERCEPTABLE_PEER_UUID,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            "concurrent-callback-peer",
            "--zmq-rpc",
            String.valueOf(ZMQ_RPC_PORT),
            "--rpc-threads",
            String.valueOf(RPC_THREAD_COUNT),
            "--interceptable",
            "--log",
            "auto",
            "--log-prefix",
            "concurrent-callback",
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
    logger.info("Stopping shared peer for concurrent callback tests");
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

        // Delete logs created by this suite (with prefix "concurrent-callback")
        logger.info("Deleting logs created by ConcurrentCallbackTestSuite");
        for (LogInfo log : palDirectory.listAllLogs()) {
          if (log.getName().startsWith("concurrent-callback")) {
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
