/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cli;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for intercept bundle idempotency and status operations.
 *
 * <p>Tests that double-apply is idempotent, that diff after apply shows unchanged state, and that
 * status after apply reports all intercepts as active.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class InterceptBundleIdempotencyIT extends AbstractCliIT {

  /** Primary peer process managed by the test lifecycle. */
  private PeerProcess peerProcess;

  /** PalDirectory client used to verify intercepts programmatically. */
  private PalDirectory palDirectory;

  /** Sets up test state before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
    palDirectory = null;
  }

  /**
   * Tears down test state after each test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    if (palDirectory != null) {
      palDirectory.close();
      palDirectory = null;
    }
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Tests that applying the same YAML file twice is idempotent.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testApply_idempotent_doubleApplySucceeds() throws Exception {
    // Given: A YAML temp file with 3 intercepts and a running peer
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "idemp-peer-" + generateId();
    String walName = "wal-idemp-" + generateId();
    String bundleName = "idemp-bundle-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "--interceptable",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    File yamlFile = createBundleYaml(bundleName, peerName);

    // When: Running `pal intercept apply -d <url> <file>` twice
    CliProcessResult first = runInterceptApply("-d", palDir, yamlFile.getAbsolutePath());
    assertThat("First apply should succeed", first.exitCode(), is(0));

    CliProcessResult second = runInterceptApply("-d", palDir, yamlFile.getAbsolutePath());

    // Then: Second apply exits 0 with "skipped" count,
    //       intercepts in etcd unchanged (same count)
    assertThat(second.exitCode(), is(0));
    assertThat(second.stdout(), containsString("skipped"));

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    int interceptCount = palDirectory.listInterceptsForPeer(peerId).size();
    assertThat("Intercept count should remain 3 after double apply", interceptCount, is(3));
  }

  /**
   * Tests that diff after apply shows all intercepts as unchanged.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testApply_thenDiff_showsUnchanged() throws Exception {
    // Given: A bundle has been applied (3 intercepts exist in etcd)
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "diff-peer-" + generateId();
    String walName = "wal-diff-" + generateId();
    String bundleName = "diff-bundle-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "--interceptable",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    File yamlFile = createBundleYaml(bundleName, peerName);
    CliProcessResult applyResult = runInterceptApply("-d", palDir, yamlFile.getAbsolutePath());
    assertThat("Apply should succeed", applyResult.exitCode(), is(0));

    // When: Running `pal intercept diff -d <url> <file>`
    CliProcessResult diffResult = runInterceptDiff("-d", palDir, yamlFile.getAbsolutePath());

    // Then: Output shows all intercepts as "unchanged"
    assertThat(diffResult.exitCode(), is(0));
    assertThat(diffResult.stdout(), containsString("already exists, matches"));
    assertThat(diffResult.stdout(), containsString("0 to create"));
    assertThat(diffResult.stdout(), containsString("3 unchanged"));
  }

  /**
   * Tests that status after apply shows all intercepts as active.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testApply_thenStatus_showsAllActive() throws Exception {
    // Given: A bundle has been applied (3 intercepts exist in etcd)
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "status-peer-" + generateId();
    String walName = "wal-status-" + generateId();
    String bundleName = "status-bundle-" + generateId();

    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDir,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--zmq-rpc",
            "auto",
            "--interceptable",
            "--as-service",
            "-cp",
            getIttAppsClasspath());

    File yamlFile = createBundleYaml(bundleName, peerName);
    CliProcessResult applyResult = runInterceptApply("-d", palDir, yamlFile.getAbsolutePath());
    assertThat("Apply should succeed", applyResult.exitCode(), is(0));

    // When: Running `pal intercept status -d <url> -f <file>`
    CliProcessResult statusResult =
        runInterceptStatus("-d", palDir, "-f", yamlFile.getAbsolutePath());

    // Then: Output shows all intercepts as active with correct count summary
    assertThat(statusResult.exitCode(), is(0));
    assertThat(statusResult.stdout(), containsString("3/3 active"));
  }

  /**
   * Creates a YAML bundle file with 2 method intercepts and 1 field intercept.
   *
   * @param bundleName the bundle name
   * @param peerName the peer name
   * @return the temp YAML file
   * @throws IOException if file creation fails
   */
  private File createBundleYaml(String bundleName, String peerName) throws IOException {
    String yaml =
        "bundle: \""
            + bundleName
            + "\"\n"
            + "defaults:\n"
            + "  peer: \""
            + peerName
            + "\"\n"
            + "intercepts:\n"
            + "  - target: com.acme.OrderService.placeOrder\n"
            + "    type: BEFORE\n"
            + "    callback:\n"
            + "      class: com.acme.FraudChecker\n"
            + "      method: verify\n"
            + "  - target: com.acme.OrderService.refund\n"
            + "    type: AROUND\n"
            + "    callback:\n"
            + "      class: com.acme.FraudChecker\n"
            + "      method: wrapRefund\n"
            + "  - target: com.acme.OrderService.status\n"
            + "    kind: field\n"
            + "    fieldOp: GET\n"
            + "    type: AFTER\n"
            + "    callback:\n"
            + "      class: com.acme.FieldAuditor\n"
            + "      method: onFieldRead\n";
    File tempFile = File.createTempFile("bundle-", ".yaml");
    tempFile.deleteOnExit();
    Files.writeString(tempFile.toPath(), yaml, StandardCharsets.UTF_8);
    return tempFile;
  }
}
