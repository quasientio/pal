/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.Test;

/**
 * Integration tests for `io.quasient.pal.core.service.Main` that test fatal exit conditions by
 * launching the peer process and checking exit codes and stderr.
 */
public class MainIT extends AbstractIntegrationTest {

  @Test
  public void testLogWithoutKafkaServers_fatalExitNoKafkaServers() throws Exception {
    ProcessResult result = runPeer("--log", "test-log");

    assertEquals(
        "Expected fatal exit for missing Kafka servers",
        PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getMessage()));
  }

  @Test
  public void testInitLogsWithUnreachableKafka_fatalExitInitializingLogs() throws Exception {
    // Use localhost:1 to simulate fast unreachable Kafka and reduce kafka-timeout to 3s
    ProcessResult result =
        runPeer(
            "--log",
            "test-log",
            "--kafka-servers",
            "localhost:1",
            "--kafka-timeout",
            "3000",
            "com.example.DummyMain");

    assertEquals(
        "Expected fatal exit for Kafka initialization failure",
        PeerException.FatalCode.ERROR_INITIALIZING_LOGS.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_INITIALIZING_LOGS.getMessage()));
  }

  @Test
  public void testSourceLogWithoutKafkaServers_fatalExitNoKafkaServers() throws Exception {
    ProcessResult result = runPeer("--source-log", "test-source-log");

    assertEquals(
        "Expected fatal exit for missing Kafka servers",
        PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getMessage()));
  }

  @Test
  public void testWalWithoutKafkaServers_fatalExitNoKafkaServers() throws Exception {
    ProcessResult result = runPeer("--wal", "test-wal");

    assertEquals(
        "Expected fatal exit for missing Kafka servers",
        PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(PeerException.FatalCode.ERROR_NO_KAFKA_SERVERS_GIVEN.getMessage()));
  }

  @Test
  public void testNonExistentJarFile_fatalExitJarNotFound() throws Exception {
    ProcessResult result = runPeer("-jar", "/nonexistent/path/to/app.jar");

    assertEquals(
        "Expected fatal exit for non-existent JAR",
        PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST.getCode(),
        result.exitCode());
    assertThat(
        "Expected error message in stderr",
        result.stderr(),
        containsString(
            PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST.getMessage()));
  }

  @Test
  public void testInvalidJarFile_fatalExitJarNotFoundOrMissingManifest() throws Exception {
    // Create a JAR with a MANIFEST that lacks Main-Class to deterministically trigger
    // ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST across environments
    Path tempJar = createJarWithManifestWithoutMainClass();

    try {
      ProcessResult result = runPeer("-jar", tempJar.toString());

      assertEquals(
          "Expected fatal exit for JAR without Main-Class in MANIFEST",
          PeerException.FatalCode.ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST.getCode(),
          result.exitCode());
      assertThat(
          result.stderr(),
          containsString(PeerException.FatalCode.ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST.getMessage()));
    } finally {
      Files.deleteIfExists(tempJar);
    }
  }

  /**
   * Creates a temporary JAR file without a Main-Class entry in MANIFEST.MF to test
   * ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST.
   */
  private Path createJarWithManifestWithoutMainClass() throws IOException {
    Path tempDir = Files.createTempDirectory("jar-no-main");
    Path manifest = Files.createDirectories(tempDir.resolve("META-INF")).resolve("MANIFEST.MF");
    Files.writeString(manifest, "Manifest-Version: 1.0\n");

    Path tempJar = Files.createTempFile("test", ".jar");
    ProcessBuilder pb =
        new ProcessBuilder(
            "jar", "cfm", tempJar.toString(), manifest.toString(), "-C", tempDir.toString(), ".");
    try {
      Process process = pb.start();
      if (process.waitFor() != 0) {
        throw new IOException("Failed to create test jar: jar tool returned non-zero exit code");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted while creating test JAR", e);
    }

    // Cleanup temp dir; jar has already been created
    try (Stream<Path> files = Files.walk(tempDir)) {
      files
          .sorted((a, b) -> b.getNameCount() - a.getNameCount())
          .forEach(
              p -> {
                try {
                  Files.deleteIfExists(p);
                } catch (IOException ignored) {
                  // fine then, keep going
                }
              });
    } catch (Exception ignored) {
      // not an issue
    }

    return tempJar;
  }
}
