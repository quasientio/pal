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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.dsl.intercept.BundleMetadata;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the {@code pal intercept apply} command.
 *
 * <p>Tests that applying an intercept bundle YAML file creates the expected intercepts in etcd,
 * that dry-run mode does not create anything, and that bundle metadata is stored correctly.
 *
 * <p>Requires running etcd and Kafka infrastructure as described in modules/itt/README.md.
 */
public class InterceptBundleApplyIT extends AbstractCliIT {

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
   * Tests that {@code pal intercept apply} creates intercepts from a YAML file.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testApply_createsInterceptsFromYaml() throws Exception {
    // Given: A YAML temp file with 2 method intercepts (BEFORE, AROUND) + 1 field intercept
    //        (AFTER GET), and a running peer whose name matches the YAML peer field
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "apply-peer-" + generateId();
    String walName = "wal-apply-" + generateId();

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

    File yamlFile = createBundleYaml("apply-bundle-" + generateId(), peerName);

    // When: Running `pal intercept apply -d <url> <file>`
    CliProcessResult result = runInterceptApply("-d", palDir, yamlFile.getAbsolutePath());

    // Then: Exit code is 0, output contains "created",
    //       PalDirectory.listInterceptsForPeer() returns 3 intercepts
    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString("created"));

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    int interceptCount = palDirectory.listInterceptsForPeer(peerId).size();
    assertThat(interceptCount, is(3));
  }

  /**
   * Tests that {@code pal intercept apply --dry-run} does not create intercepts.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testApply_dryRun_doesNotCreateIntercepts() throws Exception {
    // Given: A YAML temp file with intercept definitions and a running peer
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "dryrun-peer-" + generateId();
    String walName = "wal-dryrun-" + generateId();

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

    File yamlFile = createBundleYaml("dryrun-bundle-" + generateId(), peerName);

    // When: Running `pal intercept apply -d <url> --dry-run <file>`
    CliProcessResult result =
        runInterceptApply("-d", palDir, "--dry-run", yamlFile.getAbsolutePath());

    // Then: Output contains diff markers, exit code is 0,
    //       PalDirectory.listInterceptsForPeer() returns empty (no intercepts created)
    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString("would be created"));

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    Set<?> intercepts = palDirectory.listInterceptsForPeer(peerId);
    assertTrue("Dry-run should not create intercepts", intercepts.isEmpty());
  }

  /**
   * Tests that applying a bundle stores bundle metadata in etcd.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testApply_bundleMetadataStored() throws Exception {
    // Given: A YAML temp file defining a bundle with 3 intercepts and a running peer
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "metadata-peer-" + generateId();
    String walName = "wal-metadata-" + generateId();
    String bundleName = "metadata-bundle-" + generateId();

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

    // When: Running `pal intercept apply -d <url> <file>`
    CliProcessResult result = runInterceptApply("-d", palDir, yamlFile.getAbsolutePath());

    // Then: PalDirectory.getBundleMetadata(bundleName) returns metadata with
    //       correct peer UUID and 3 intercept UUIDs
    assertThat(result.exitCode(), is(0));

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    BundleMetadata metadata = palDirectory.getBundleMetadata(bundleName);
    assertThat("Bundle metadata should be stored", metadata, is(notNullValue()));
    assertThat(metadata.getPeerUuid(), is(peerId));
    assertThat(metadata.getInterceptUuids().size(), is(3));
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
