/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Integration tests for `com.quasient.pal.core.service.Main` that test fatal exit conditions by
 * launching the peer process and checking exit codes and log output.
 */
public class MainIT extends AbstractMainIT {

  @Test
  public void testLogWithoutKafkaServers_fatalExitNoKafkaServers() throws Exception {
    ProcessResult result = runPalCommand("--log", "test-log");

    assertEquals(
        "Expected fatal exit for missing Kafka servers",
        PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode(),
        result.exitCode);
    assertThat(
        "Expected error message in stderr",
        result.stderr,
        containsString(PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getMessage()));
  }

  @Test
  public void testSourceLogWithoutKafkaServers_fatalExitNoKafkaServers() throws Exception {
    ProcessResult result = runPalCommand("--source-log", "test-source-log");

    assertEquals(
        "Expected fatal exit for missing Kafka servers",
        PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode(),
        result.exitCode);
    assertThat(
        "Expected error message in stderr",
        result.stderr,
        containsString(PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getMessage()));
  }

  @Test
  public void testWalWithoutKafkaServers_fatalExitNoKafkaServers() throws Exception {
    ProcessResult result = runPalCommand("--wal", "test-wal");

    assertEquals(
        "Expected fatal exit for missing Kafka servers",
        PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode(),
        result.exitCode);
    assertThat(
        "Expected error message in stderr",
        result.stderr,
        containsString(PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getMessage()));
  }

  @Test
  public void testNonExistentJarFile_fatalExitJarNotFound() throws Exception {
    ProcessResult result = runPalCommand("-jar", "/nonexistent/path/to/app.jar");

    assertEquals(
        "Expected fatal exit for non-existent JAR",
        PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST.getCode(),
        result.exitCode);
    assertThat(
        "Expected error message in stderr",
        result.stderr,
        containsString(
            PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST.getMessage()));
  }

  @Test
  public void testInvalidJarFile_fatalExitJarNotFoundOrMissingManifest() throws Exception {
    // Create an invalid/empty JAR file to trigger ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST
    Path tempJar = createTempJarWithoutMainClass();

    try {
      ProcessResult result = runPalCommand("-jar", tempJar.toString());

      assertEquals(
          "Expected fatal exit for invalid JAR",
          PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST.getCode(),
          result.exitCode);
      assertThat(
          result.stderr,
          containsString(
              PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST.getMessage()));
    } finally {
      Files.deleteIfExists(tempJar);
    }
  }

  /**
   * Creates a temporary JAR file without a Main-Class entry in MANIFEST.MF to test
   * ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST.
   */
  private Path createTempJarWithoutMainClass() throws IOException {
    Path tempJar = Files.createTempFile("test", ".jar");

    // Create a minimal JAR file with MANIFEST.MF but no Main-Class entry
    ProcessBuilder pb =
        new ProcessBuilder(
            "jar", "cf", tempJar.toString(), "-C", System.getProperty("java.io.tmpdir"), ".");

    try {
      Process process = pb.start();
      process.waitFor();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while creating test JAR", e);
    }

    return tempJar;
  }

  // NOTE: The following error conditions require more complex setup and are documented
  // for future implementation:
  //
  // TODO: ERROR_LOADING_PROPERTIES - Create custom JAR with missing/corrupted peer.properties
  // TODO: ERROR_VALIDATING_PROPERTIES - Create custom properties with invalid power-of-2 values
  // TODO: ERROR_REGISTERING_SELF - Launch with PAL_DIRECTORY pointing to non-existent/rejecting
  // etcd
  // TODO: ERROR_REGISTERING_SELF_LOGS - Launch with PAL_DIRECTORY that rejects log registration
  // TODO: ERROR_INITIALIZING_LOGS - Launch with KAFKA_SERVERS pointing to
  // non-existent/misconfigured Kafka
  // TODO: ERROR_SERVICE_MANAGER_FAILED - Requires service startup failure simulation
  // TODO: ERROR_FINDING_RND_PORT - Launch with all ports in range occupied (unreliable to test)
  //
  // These could be implemented by:
  // 1. Creating custom scripts similar to peer4itts.sh with different configurations
  // 2. Using Docker containers with specific failure scenarios
  // 3. Setting up mock services that simulate failure conditions
}
