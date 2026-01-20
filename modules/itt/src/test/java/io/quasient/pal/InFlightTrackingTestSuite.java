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

import io.quasient.pal.intercept.inflight.InFlightInterceptActivationIT;
import java.util.UUID;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test suite for in-flight dispatch tracking and intercept activation coordination.
 *
 * <p>This suite verifies that the in-flight tracking system correctly handles intercept
 * registration while method calls are actively executing. The suite launches a dedicated
 * interceptable peer configured with {@code --in-flight-tracking} enabled.
 *
 * <p><b>Key Differences from InterceptEndToEndTestSuite:</b>
 *
 * <ul>
 *   <li><b>In-flight tracking enabled:</b> Peer configured with {@code --in-flight-tracking} flag
 *   <li><b>Drain timeout configured:</b> Peer configured with {@code --drain-timeout-ms} for
 *       quiescence coordination
 *   <li><b>Test application:</b> Uses {@link
 *       io.quasient.pal.apps.quantized.intercept.SlowMethodApp} with controllable slow/blocking
 *       methods
 *   <li><b>Multi-threaded scenarios:</b> Tests spawn concurrent threads to create realistic
 *       in-flight conditions
 * </ul>
 *
 * <p><b>Test Architecture:</b>
 *
 * <p>This suite launches two peers:
 *
 * <ul>
 *   <li><b>Interceptable peer</b> ({@link #INTERCEPTABLE_PEER_UUID}): Runs SlowMethodApp with
 *       {@code --interceptable} and {@code --in-flight-tracking} enabled. Tracks in-flight
 *       dispatches and coordinates intercept activation.
 *   <li><b>Interceptor peer</b> ({@link #INTERCEPTOR_PEER_UUID}): Receives intercept callback
 *       requests and invokes callback handlers. Same as InterceptEndToEndTestSuite's interceptor
 *       peer.
 * </ul>
 *
 * <p>The peers are configured with:
 *
 * <ul>
 *   <li>ZMQ RPC on ports 5661 (interceptable) and 5662 (interceptor)
 *   <li>3 RPC threads (interceptable), 1 RPC thread (interceptor)
 *   <li>Allow non-public method invocation
 *   <li><b>Interception enabled (--interceptable)</b> on interceptable peer
 *   <li><b>In-flight tracking enabled (--in-flight-tracking)</b> on interceptable peer
 *   <li><b>Drain timeout configured (--drain-timeout-ms 5000)</b> on interceptable peer
 *   <li>ITT apps classes on classpath (includes SlowMethodApp)
 * </ul>
 *
 * <p><b>Note:</b> These peers use different UUIDs and ports than InterceptEndToEndTestSuite to
 * avoid conflicts when running test suites in parallel.
 *
 * @see InFlightInterceptActivationIT
 * @see io.quasient.pal.apps.quantized.intercept.SlowMethodApp
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({InFlightInterceptActivationIT.class})
public class InFlightTrackingTestSuite extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(InFlightTrackingTestSuite.class);

  /**
   * Well-known UUID for the shared interceptable peer (the peer being intercepted).
   *
   * <p>This UUID is different from InterceptEndToEndTestSuite to allow both suites to run
   * concurrently without conflicts.
   */
  public static final UUID INTERCEPTABLE_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000006");

  /**
   * Well-known UUID for the interceptor peer (receives and handles intercept callbacks).
   *
   * <p>This peer runs the callback handlers and receives InterceptCallbackRequestMessage messages
   * from the interceptable peer.
   */
  public static final UUID INTERCEPTOR_PEER_UUID =
      UUID.fromString("00000000-0000-0000-0000-000000000007");

  /** Interceptable peer process (the one being intercepted with in-flight tracking). */
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
   * <p>Tests can use this to access the peer's log file for verification.
   *
   * @return the interceptor peer process, or null if not yet launched
   */
  public static PeerProcess getInterceptorPeerProcess() {
    return interceptorPeerProcess;
  }

  /** Helper instance to access non-static methods from AbstractIntegrationTest. */
  private static InFlightTrackingTestSuite instance;

  /**
   * Launches the shared in-flight tracking test peers before any tests run.
   *
   * <p>Two peers are launched:
   *
   * <ul>
   *   <li><b>Interceptable peer</b> ({@link #INTERCEPTABLE_PEER_UUID}): Runs SlowMethodApp with
   *       in-flight tracking enabled. Configured with --interceptable and --in-flight-tracking
   *       flags.
   *   <li><b>Interceptor peer</b> ({@link #INTERCEPTOR_PEER_UUID}): Handles intercept callbacks.
   *       Receives InterceptCallbackRequestMessage messages and invokes callback handlers.
   * </ul>
   *
   * @throws Exception if peer launch fails
   */
  @BeforeClass
  public static void launchSharedPeers() throws Exception {
    logger.info("============================================================");
    logger.info("Launching shared in-flight tracking test peers");
    logger.info("  Interceptable peer UUID: {}", INTERCEPTABLE_PEER_UUID);
    logger.info("  Interceptor peer UUID:   {}", INTERCEPTOR_PEER_UUID);
    logger.info("============================================================");

    // Create helper instance to access non-static methods
    instance = new InFlightTrackingTestSuite();

    String palHome = System.getenv("PAL_HOME");
    if (palHome == null) {
      throw new RuntimeException("PAL_HOME environment variable is not set");
    }

    String palDirectory = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    // Build classpath for itt-apps (both peers need access to test application classes)
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

    // Launch interceptable peer (the peer being intercepted with in-flight tracking enabled)
    logger.info("Launching interceptable peer with in-flight tracking...");
    interceptablePeerProcess =
        instance.launchPeer(
            INTERCEPTABLE_PEER_UUID,
            "-d",
            palDirectory,
            "-k",
            kafkaServers,
            "-n",
            "inflight-interceptable-peer",
            "--zmq-rpc",
            "5661",
            "--rpc-threads",
            "3",
            "--rpc-allow-nonpublic",
            "--interceptable",
            "--in-flight-tracking",
            "--drain-timeout-ms",
            "5000",
            "--log",
            "auto",
            "--log-prefix",
            "itt-inflight-interceptable",
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
            "inflight-interceptor-peer",
            "--zmq-rpc",
            "5662",
            "--rpc-threads",
            "1",
            "--rpc-allow-nonpublic",
            "--log",
            "auto",
            "--log-prefix",
            "itt-inflight-interceptor",
            "-cp",
            ittAppsClasspath);
    logger.info("Interceptor peer launched successfully");

    logger.info("All in-flight tracking test peers launched successfully");
  }

  /**
   * Stops the shared in-flight tracking test peers after all tests complete.
   *
   * @throws Exception if peer stop fails
   */
  @AfterClass
  public static void stopSharedPeers() throws Exception {
    logger.info("============================================================");
    logger.info("Stopping shared in-flight tracking test peers");
    logger.info("============================================================");

    if (instance != null) {
      // Stop both peer processes
      if (interceptablePeerProcess != null) {
        logger.info("Stopping interceptable peer...");
        instance.stopPeer(interceptablePeerProcess);
        logger.info("Interceptable peer stopped");
      }

      if (interceptorPeerProcess != null) {
        logger.info("Stopping interceptor peer...");
        instance.stopPeer(interceptorPeerProcess);
        logger.info("Interceptor peer stopped");
      }
    }

    logger.info("All in-flight tracking test peers stopped successfully");
  }
}
