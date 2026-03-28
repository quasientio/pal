/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.intercept.chain.AroundChainExceptionIT;
import io.quasient.pal.intercept.chain.AroundChainIT;
import io.quasient.pal.intercept.endtoend.activation.ImmediateActivationIT;
import io.quasient.pal.intercept.endtoend.constructor.AfterConstructorAsyncCallbackIT;
import io.quasient.pal.intercept.endtoend.constructor.AfterConstructorCallbackIT;
import io.quasient.pal.intercept.endtoend.constructor.AroundConstructorCallbackIT;
import io.quasient.pal.intercept.endtoend.constructor.BeforeConstructorAsyncCallbackIT;
import io.quasient.pal.intercept.endtoend.constructor.BeforeConstructorCallbackIT;
import io.quasient.pal.intercept.endtoend.field.AfterFieldAsyncCallbackIT;
import io.quasient.pal.intercept.endtoend.field.AfterFieldCallbackIT;
import io.quasient.pal.intercept.endtoend.field.AroundFieldCallbackIT;
import io.quasient.pal.intercept.endtoend.field.BeforeFieldAsyncCallbackIT;
import io.quasient.pal.intercept.endtoend.field.BeforeFieldCallbackIT;
import io.quasient.pal.intercept.endtoend.method.AfterMethodAsyncCallbackIT;
import io.quasient.pal.intercept.endtoend.method.AfterMethodCallbackIT;
import io.quasient.pal.intercept.endtoend.method.AroundMethodCallbackIT;
import io.quasient.pal.intercept.endtoend.method.BeforeMethodAsyncCallbackIT;
import io.quasient.pal.intercept.endtoend.method.BeforeMethodCallbackIT;
import io.quasient.pal.intercept.endtoend.timeout.CallbackTimeoutIT;
import io.quasient.pal.intercept.exception.ExceptionHandlingIT;
import io.quasient.pal.intercept.local.combined.LocalAndRemoteCombinedIT;
import io.quasient.pal.intercept.order.InterceptExecutionOrderIT;
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
 * Test suite for intercept callback handler tests.
 *
 * <p>This suite tests that callback handlers execute correctly and can mutate arguments, override
 * return values, and throw exceptions. These tests verify the <b>execution</b> of callback handler
 * methods in the interceptor peer.
 *
 * <p><b>Testing Pattern - Callback Handler Execution:</b>
 *
 * <ol>
 *   <li>Test registers intercept with <b>{@code INTERCEPTOR_PEER_UUID}</b> as callback peer
 *   <li>Test invokes intercepted method on interceptable peer
 *   <li>Interceptable peer matches intercept and sends {@code InterceptCallbackRequestMessage} to
 *       interceptor peer
 *   <li><b>Interceptor peer executes the actual callback handler method</b>
 *   <li>Test verifies the side effects (mutated args, overridden return, thrown exception)
 * </ol>
 *
 * <p><b>Key Difference from InterceptFlowTestSuite:</b> This suite tests that handlers <b>run and
 * produce effects</b>. For testing that callbacks are <b>correctly dispatched and structured</b>
 * (without executing handlers), see {@link InterceptFlowTestSuite}.
 *
 * <p><b>Architecture:</b> This suite launches two peers:
 *
 * <ul>
 *   <li><b>Interceptable peer</b> ({@link #INTERCEPTABLE_PEER_UUID}): Runs the test applications
 *       (StringMethods, InterceptableApp, etc.) with {@code --interceptable} flag. Sends callback
 *       requests when intercepts match.
 *   <li><b>Interceptor peer</b> ({@link #INTERCEPTOR_PEER_UUID}): Receives {@code
 *       InterceptCallbackRequestMessage} messages and invokes the specified callback handler
 *       methods. Has callback handler classes (InstanceMethodHandlers, etc.) on its classpath.
 * </ul>
 *
 * <p>The peers are configured with:
 *
 * <ul>
 *   <li>ZMQ RPC on ports 5657 (interceptable) and 5658 (interceptor)
 *   <li>JSON-RPC on port 7790
 *   <li>3 RPC threads (interceptable), 1 RPC thread (interceptor)
 *   <li><b>Interception enabled (--interceptable)</b> on interceptable peer
 *   <li>ITT apps classes on classpath
 * </ul>
 *
 * <p><b>Note:</b> These peers are shared across all tests in the suite for performance. Tests must
 * not rely on mutable peer state or interfere with each other.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  // Method callback handler tests (SYNC)
  BeforeMethodCallbackIT.class,
  AfterMethodCallbackIT.class,
  AroundMethodCallbackIT.class,
  // Method callback handler tests (ASYNC)
  BeforeMethodAsyncCallbackIT.class,
  AfterMethodAsyncCallbackIT.class,
  // Constructor callback handler tests (SYNC)
  BeforeConstructorCallbackIT.class,
  AfterConstructorCallbackIT.class,
  AroundConstructorCallbackIT.class,
  // Constructor callback handler tests (ASYNC)
  BeforeConstructorAsyncCallbackIT.class,
  AfterConstructorAsyncCallbackIT.class,
  // Field callback handler tests (SYNC)
  BeforeFieldCallbackIT.class,
  AfterFieldCallbackIT.class,
  AroundFieldCallbackIT.class,
  // Field callback handler tests (ASYNC)
  BeforeFieldAsyncCallbackIT.class,
  AfterFieldAsyncCallbackIT.class,

  // Combined local and remote intercept tests
  LocalAndRemoteCombinedIT.class,

  // Intercept execution order tests
  InterceptExecutionOrderIT.class,

  // AROUND intercept chain tests (onion model)
  AroundChainIT.class,
  AroundChainExceptionIT.class,

  // Immediate activation tests (verifies behavior when --in-flight-tracking is disabled)
  ImmediateActivationIT.class,

  // Exception handling tests (API misuse, business exceptions, policies)
  ExceptionHandlingIT.class,

  // Callback timeout tests (global vs per-intercept timeout overrides)
  CallbackTimeoutIT.class
})
public class InterceptEndToEndTestSuite extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(InterceptEndToEndTestSuite.class);

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
   * <p>This peer runs the callback handlers and receives InterceptCallbackRequestMessage messages
   * from the interceptable peer.
   */
  public static final UUID INTERCEPTOR_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000003");

  /** Interceptable peer process (the one being intercepted). */
  private static PeerProcess interceptablePeerProcess;

  /** Interceptor peer process (handles callbacks). */
  private static PeerProcess interceptorPeerProcess;

  /**
   * Returns the interceptable peer process.
   *
   * <p>Tests can use this to access the peer's log file for verification.
   *
   * @return the interceptable peer process, or null if not yet launched
   */
  public static PeerProcess getInterceptablePeerProcess() {
    return interceptablePeerProcess;
  }

  /**
   * Returns the interceptor peer process.
   *
   * <p>Tests can use this to access the peer's log file for verification, which is useful for
   * verifying that async callback handlers logged expected messages.
   *
   * @return the interceptor peer process, or null if not yet launched
   */
  public static PeerProcess getInterceptorPeerProcess() {
    return interceptorPeerProcess;
  }

  /** Path to the application log file (itt-apps.log) for callback handler logs. */
  private static Path appLogPath;

  /** Timeout for waiting for application log lines (seconds). */
  private static final int APP_LOG_TIMEOUT_SECONDS = 10;

  /**
   * Returns the path to the application log file.
   *
   * <p>This is the log file where callback handlers write their logs, separate from the peer
   * runtime logs.
   *
   * @return the path to itt-apps.log
   */
  public static Path getAppLogPath() {
    return appLogPath;
  }

  /**
   * Waits for a log line matching the given regex pattern to appear in the application log.
   *
   * <p>This method polls the application log file (itt-apps.log) until a line matching the pattern
   * is found or the timeout is reached.
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
   * Clears the application log file by truncating it.
   *
   * <p>This method truncates rather than deletes the file to preserve the file's inode, so any open
   * file handles continue to write to the same file.
   *
   * @throws IOException if the log file cannot be truncated
   */
  public static void clearAppLog() throws IOException {
    if (Files.exists(appLogPath)) {
      // Truncate instead of delete - preserves file's inode so open FileOutputStream
      // continues to write to the same file
      try (FileOutputStream fos = new FileOutputStream(appLogPath.toFile(), false)) {
        // Opening with append=false truncates the file to zero length
      }
    }
  }

  /** Helper instance to access non-static methods from AbstractIntegrationTest. */
  private static InterceptEndToEndTestSuite instance;

  /**
   * Launches the shared intercept test peers before any tests run.
   *
   * <p>Two peers are launched:
   *
   * <ul>
   *   <li><b>Interceptable peer</b> ({@link #INTERCEPTABLE_PEER_UUID}): Runs the test applications
   *       (StringMethods, InterceptableApp, etc.). Configured with --interceptable flag.
   *   <li><b>Interceptor peer</b> ({@link #INTERCEPTOR_PEER_UUID}): Handles intercept callbacks.
   *       Receives InterceptCallbackRequestMessage messages and invokes callback handlers.
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
    instance = new InterceptEndToEndTestSuite();

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

    // Build classpath for itt-apps (both peers need access to test application classes)
    // NOTE: Include slf4j-api AND logback so callback handler code can log properly.
    // The itt-apps module has a logback.xml that configures logging to write to
    // ${PAL_HOME}/logs/itt-apps.log so test assertions can verify callback execution.
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
    // NOTE: Explicitly disable in-flight tracking to test immediate activation behavior.
    // Tests that need tracking enabled should use InFlightTrackingTestSuite instead.
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
            "--interceptable",
            "--in-flight-tracking",
            "false",
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
            "--rpc-threads",
            "1",
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
        logger.info("Deleting logs created by InterceptEndToEndTestSuite");
        for (LogInfo log : palDirectory.listAllLogs()) {
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
