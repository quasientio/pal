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
package io.quasient.pal.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.PeerProcess;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code --with-source-context} peer flag.
 *
 * <p>Verifies that when a peer is launched with {@code --with-source-context}, the quantized
 * operation messages written to the WAL include source context fields (source file, line number,
 * source type). When the flag is absent (the default), these fields are omitted.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class SourceContextIT extends AbstractCliIT {

  /** Main class whose {@code main()} creates objects and calls various methods. */
  private static final String METHODS_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Peer process handle for the currently running peer, if any. */
  private PeerProcess peerProcess;

  /** Resets the peer process handle before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
  }

  /**
   * Stops any running peer process after each test.
   *
   * @throws Exception if stopping the peer fails
   */
  @After
  public void tearDown() throws Exception {
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Tests that when a peer runs with {@code --with-source-context}, the WAL messages contain source
   * location fields (file, line, type).
   *
   * <p>Launches a peer that executes {@code Methods.main()}, which creates objects and invokes
   * various methods. Each quantized operation should carry source context metadata identifying
   * where in the source code the operation originated.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void withSourceContext_enabled_messagesContainSourceLocation() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-src-ctx-on-" + generateId();
    UUID peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "--with-source-context",
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);
    joinPeer(peerProcess, 15);
    peerProcess = null;

    Thread.sleep(1000);

    CliProcessResult result = runLogPrint("-d", palDir, walName, "--json");
    assertEquals("pal log print should exit successfully", 0, result.exitCode());

    String output = result.stdout();
    assertThat("WAL should contain messages", output.length(), greaterThan(0));

    // When --with-source-context is enabled, messages should contain source location fields
    assertThat(
        "Expected source_location_file in WAL output",
        output.contains("source_location_file"),
        is(true));
    assertThat(
        "Expected source_location_line in WAL output",
        output.contains("source_location_line"),
        is(true));
    assertThat(
        "Expected source_location_type in WAL output",
        output.contains("source_location_type"),
        is(true));

    // The source file should be Methods.java since that is where the operations originate
    assertThat("Expected Methods.java as source file", output.contains("Methods.java"), is(true));

    // The source type should reference the Methods class
    assertThat(
        "Expected Methods class in source_location_type",
        output.contains("io.quasient.foobar.apps.quantized.rpc.Methods"),
        is(true));
  }

  /**
   * Tests that when a peer runs without {@code --with-source-context} (the default), the WAL
   * messages do not contain source location fields.
   *
   * <p>Launches the same {@code Methods.main()} workload but without the flag. The same quantized
   * operations should be present in the WAL, but without source context metadata.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void withSourceContext_disabled_messagesOmitSourceLocation() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    String walName = "test-src-ctx-off-" + generateId();
    UUID peerId = UUID.randomUUID();

    // Launch WITHOUT --with-source-context (default is false)
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "--wal",
            walName,
            "-cp",
            getIttAppsClasspath(),
            METHODS_CLASS);
    joinPeer(peerProcess, 15);
    peerProcess = null;

    Thread.sleep(1000);

    CliProcessResult result = runLogPrint("-d", palDir, walName, "--json");
    assertEquals("pal log print should exit successfully", 0, result.exitCode());

    String output = result.stdout();
    assertThat("WAL should contain messages", output.length(), greaterThan(0));

    // When --with-source-context is not set, messages should NOT contain source location fields
    assertThat(
        "Expected no source_location_file in WAL output",
        output.contains("source_location_file"),
        is(false));
    assertThat(
        "Expected no source_location_line in WAL output",
        output.contains("source_location_line"),
        is(false));
    assertThat(
        "Expected no source_location_type in WAL output",
        output.contains("source_location_type"),
        is(false));
  }
}
