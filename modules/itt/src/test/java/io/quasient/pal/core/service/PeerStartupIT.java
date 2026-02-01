/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for peer startup behavior with various configurations.
 *
 * <p>These tests verify Main class startup paths including successful startup scenarios, error
 * conditions with specific exit codes, and graceful shutdown behavior.
 *
 * <p>Test Infrastructure Requirements:
 *
 * <ul>
 *   <li>Standalone integration tests (no managed peer from suite)
 *   <li>Requires etcd and Kafka containers running
 *   <li>Each test launches its own peer process
 * </ul>
 *
 * <p>Related tests in other classes:
 *
 * <ul>
 *   <li>{@link MainIT} - Fatal exit conditions for missing Kafka servers, JAR errors
 *   <li>{@link MainEtcdRegistrationIT} - etcd unreachable error conditions
 *   <li>{@link MainInvalidInputIT} - Invalid CLI input combinations
 *   <li>{@link MainInvalidCombosIT} - Invalid flag combinations
 *   <li>{@link MainClassNotFoundIT} - Main class not found errors
 * </ul>
 *
 * @see <a href="https://github.com/quasient/pal/issues/484">Issue #484</a>
 */
public class PeerStartupIT extends AbstractIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(PeerStartupIT.class);

  /**
   * Verifies peer starts successfully with WAL and RPC configuration.
   *
   * <p>Given: Valid etcd and Kafka configuration When: Peer started with --wal and --zmq-rpc flags
   * Then: Peer registers in directory; RPC endpoints available
   */
  @Test
  public void main_withWalAndRpc_startsSuccessfully() throws Exception {
    UUID peerId = UUID.randomUUID();
    String walName = "test-startup-wal-" + generateId();
    PeerProcess peer = null;
    PalDirectory directory = null;

    try {
      // Build classpath for itt-apps (same as RpcTestSuite)
      String palHome = System.getenv("PAL_HOME");
      String ittAppsClasspath =
          String.format(
              "%s/modules/itt-apps/target/classes:%s/modules/itt-apps/target/classes",
              palHome, palHome);

      // When: Peer started with --wal and --zmq-rpc flags
      // Note: We use --as-service to keep the peer running after main() returns
      peer =
          launchPeer(
              peerId,
              "-d",
              getPalDirectoryUrl(),
              "-k",
              getKafkaServers(),
              "--wal",
              walName,
              "--zmq-rpc",
              "auto",
              "--as-service",
              "-cp",
              ittAppsClasspath,
              "io.quasient.pal.apps.quantized.rpc.Methods");

      // Then: Peer registers in directory
      assertTrue("Peer process should be alive", peer.isAlive());

      // Verify peer is registered in etcd directory
      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = directory.getPeer(peerId);
      assertNotNull("Peer should be registered in directory", peerInfo);
      assertThat("Peer should have ZMQ RPC address", peerInfo.getZmqRpcAddress(), notNullValue());

      logger.info(
          "Peer {} successfully started with WAL and RPC, ZMQ address: {}",
          peerId,
          peerInfo.getZmqRpcAddress());

    } finally {
      // Cleanup
      if (peer != null) {
        stopPeer(peer);
      }
      if (directory != null) {
        try {
          directory.deletePeer(peerId);
          directory.deleteLog(walName);
        } catch (Exception e) {
          logger.warn("Cleanup error (may already be cleaned)", e);
        }
        directory.close();
      }
    }
  }

  /**
   * Verifies peer starts with Chronicle queue WAL without requiring Kafka.
   *
   * <p>Given: Chronicle queue path (file:/tmp/test-wal) When: Peer started with --wal file:/path
   * Then: Peer starts successfully without Kafka
   */
  @Test
  public void main_withChronicleWal_startsWithoutKafka() throws Exception {
    UUID peerId = UUID.randomUUID();
    Path chronicleDir = Files.createTempDirectory("test-chronicle-wal-");
    PeerProcess peer = null;

    try {
      // Build classpath for itt-apps
      String palHome = System.getenv("PAL_HOME");
      String ittAppsClasspath =
          String.format(
              "%s/modules/itt-apps/target/classes:%s/modules/itt-apps/target/classes",
              palHome, palHome);

      // When: Peer started with Chronicle WAL (no Kafka required)
      // Note: We use --zmq-rpc auto and --as-service to keep peer running after main() returns
      peer =
          launchPeer(
              peerId,
              "--wal",
              "file:" + chronicleDir.toString(),
              "--zmq-rpc",
              "auto",
              "--as-service",
              "-cp",
              ittAppsClasspath,
              "io.quasient.pal.apps.quantized.rpc.Methods");

      // Then: Peer starts successfully without Kafka
      assertTrue("Peer process should be alive", peer.isAlive());

      logger.info("Peer {} successfully started with Chronicle WAL (no Kafka)", peerId);

    } finally {
      // Cleanup
      if (peer != null) {
        stopPeer(peer);
      }
      // Delete Chronicle directory
      deleteDirectoryRecursively(chronicleDir);
    }
  }

  /**
   * Verifies peer exits with code 14 when etcd is unavailable.
   *
   * <p>Given: Invalid etcd endpoint When: Peer started with --dir flag Then: Process exits with
   * code 14 (ERROR_UNREACHABLE_ETCD)
   */
  @Test
  public void main_etcdUnavailable_failsWithExitCode14() throws Exception {
    // Given: Invalid etcd endpoint (non-routable TEST-NET-1 address)
    String unreachableEtcd = "192.0.2.1:2379";

    // When: Peer started with --dir pointing to invalid etcd
    ProcessResult result =
        runPeerWithEnv(
            unreachableEtcd,
            "--dir",
            unreachableEtcd,
            "--etcd-timeout",
            "3000",
            "com.example.DummyMain");

    // Then: Process exits with code 14 (ERROR_UNREACHABLE_ETCD)
    assertEquals(
        "Expected fatal exit for unreachable etcd",
        PeerException.FatalCode.ERROR_UNREACHABLE_ETCD.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_UNREACHABLE_ETCD.getMessage()));
  }

  /**
   * Verifies peer exits with code 7 when Kafka is unavailable.
   *
   * <p>Given: Invalid Kafka bootstrap servers When: Peer started with Kafka --wal Then: Process
   * exits with code 7 (ERROR_INITIALIZING_LOGS)
   */
  @Test
  public void main_kafkaUnavailable_failsWithExitCode7() throws Exception {
    // Given: Invalid Kafka bootstrap servers
    // When: Peer started with Kafka --wal
    ProcessResult result =
        runPeer(
            "--wal",
            "test-log",
            "--kafka-servers",
            "localhost:1",
            "--kafka-timeout",
            "3000",
            "com.example.DummyMain");

    // Then: Process exits with code 7 (ERROR_INITIALIZING_LOGS)
    assertEquals(
        "Expected fatal exit for Kafka initialization failure",
        PeerException.FatalCode.ERROR_INITIALIZING_LOGS.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_INITIALIZING_LOGS.getMessage()));
  }

  /**
   * Verifies peer exits with code 12 when given invalid ZMQ RPC port.
   *
   * <p>Given: Invalid --zmq-rpc port value When: Peer started Then: Process exits with code 12
   * (ERROR_PARSING_ZMQ_RPC_PORT_NUMBER)
   */
  @Test
  public void main_invalidRpcPort_failsWithExitCode12() throws Exception {
    // Given: Invalid --zmq-rpc port value
    // When: Peer started
    ProcessResult result = runPeer("--zmq-rpc", "not-a-port", "com.example.DummyMain");

    // Then: Process exits with code 12 (ERROR_PARSING_ZMQ_RPC_PORT_NUMBER)
    assertEquals(
        "Expected fatal exit for invalid ZMQ RPC port",
        PeerException.FatalCode.ERROR_PARSING_ZMQ_RPC_PORT_NUMBER.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_PARSING_ZMQ_RPC_PORT_NUMBER.getMessage()));
  }

  /**
   * Verifies peer deregisters from directory on graceful shutdown.
   *
   * <p>Given: Running peer registered in directory When: Peer process stopped (SIGTERM) Then: Peer
   * deregisters from etcd before exit
   */
  @Test
  public void main_gracefulShutdown_deregistersFromDirectory() throws Exception {
    UUID peerId = UUID.randomUUID();
    String walName = "test-shutdown-wal-" + generateId();
    PeerProcess peer = null;
    PalDirectory directory = null;

    try {
      // Build classpath for itt-apps
      String palHome = System.getenv("PAL_HOME");
      String ittAppsClasspath =
          String.format(
              "%s/modules/itt-apps/target/classes:%s/modules/itt-apps/target/classes",
              palHome, palHome);

      // Given: Running peer registered in directory
      // Note: We use --as-service to keep the peer running after main() returns
      // Otherwise the peer shuts down immediately and we can't verify registration
      peer =
          launchPeer(
              peerId,
              "-d",
              getPalDirectoryUrl(),
              "-k",
              getKafkaServers(),
              "--wal",
              walName,
              "--zmq-rpc",
              "auto",
              "--as-service",
              "-cp",
              ittAppsClasspath,
              "io.quasient.pal.apps.quantized.rpc.Methods");

      // Verify peer is registered in etcd using PalDirectory.getPeer()
      directory = new PalDirectory(getPalDirectoryUrl(), null, true);
      PeerInfo peerInfo = directory.getPeer(peerId);
      assertNotNull("Peer should be registered in directory before shutdown", peerInfo);
      logger.info(
          "Peer {} registered in directory with ZMQ: {}", peerId, peerInfo.getZmqRpcAddress());

      // When: SIGTERM sent to peer process
      stopPeer(peer);
      peer = null; // Mark as stopped

      // Give etcd a moment to process the lease expiration/deletion
      Thread.sleep(1000);

      // Then: Peer deregisters from etcd before exit
      PeerInfo peerInfoAfter = directory.getPeer(peerId);
      assertThat("Peer should be deregistered from directory", peerInfoAfter, nullValue());

      logger.info("Peer {} successfully deregistered from directory on graceful shutdown", peerId);

    } finally {
      // Cleanup
      if (peer != null) {
        stopPeer(peer);
      }
      if (directory != null) {
        try {
          directory.deleteLog(walName);
        } catch (Exception e) {
          logger.warn("Cleanup error (may already be cleaned)", e);
        }
        directory.close();
      }
    }
  }

  /**
   * Recursively deletes a directory and all its contents.
   *
   * @param dir the directory to delete
   */
  private void deleteDirectoryRecursively(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }
    try (var stream = Files.walk(dir)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  logger.warn("Failed to delete {}", path, e);
                }
              });
    } catch (IOException e) {
      logger.warn("Failed to delete directory recursively: {}", dir, e);
    }
  }
}
