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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
 * Integration tests for the {@code pal intercept rm} command.
 *
 * <p>Tests removal of intercept bundles by file, by bundle name, and by peer name. Each test first
 * applies a bundle, then removes it using a different strategy and verifies cleanup.
 *
 * <p>Requires running etcd infrastructure as described in modules/itt/README.md.
 */
public class InterceptBundleRemoveIT extends AbstractCliIT {

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
   * Tests that {@code pal intercept rm -f <file>} removes all intercepts defined in the file.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemove_byFile_removesAllIntercepts() throws Exception {
    // Given: A bundle has been applied (3 intercepts exist in etcd)
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String peerName = "rm-file-peer-" + generateId();
    String bundleName = "rm-file-bundle-" + generateId();

    peerProcess =
        launchPeer(peerId, "-d", palDir, "-n", peerName, "--interceptable", "--as-service");

    File yamlFile = createBundleYaml(bundleName, peerName);
    CliProcessResult applyResult = runInterceptApply("-d", palDir, yamlFile.getAbsolutePath());
    assertThat("Apply should succeed", applyResult.exitCode(), is(0));

    // When: Running `pal intercept rm -d <url> -f <file>`
    CliProcessResult rmResult = runInterceptRm("-d", palDir, "-f", yamlFile.getAbsolutePath());

    // Then: Exit code is 0, PalDirectory.listInterceptsForPeer() returns empty,
    //       PalDirectory.getBundleMetadata(bundleName) returns null
    assertThat(rmResult.exitCode(), is(0));
    assertThat(rmResult.stdout(), containsString("removed"));

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    assertTrue(
        "All intercepts should be removed", palDirectory.listInterceptsForPeer(peerId).isEmpty());
    assertNull("Bundle metadata should be removed", palDirectory.getBundleMetadata(bundleName));
  }

  /**
   * Tests that {@code pal intercept rm --bundle <name>} removes all intercepts in the bundle.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemove_byBundle_removesAllIntercepts() throws Exception {
    // Given: A bundle has been applied (3 intercepts exist in etcd)
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String peerName = "rm-bundle-peer-" + generateId();
    String bundleName = "rm-bundle-" + generateId();

    peerProcess =
        launchPeer(peerId, "-d", palDir, "-n", peerName, "--interceptable", "--as-service");

    File yamlFile = createBundleYaml(bundleName, peerName);
    CliProcessResult applyResult = runInterceptApply("-d", palDir, yamlFile.getAbsolutePath());
    assertThat("Apply should succeed", applyResult.exitCode(), is(0));

    // When: Running `pal intercept rm -d <url> --bundle <name>`
    CliProcessResult rmResult = runInterceptRm("-d", palDir, "--bundle", bundleName);

    // Then: Exit code is 0, PalDirectory.listInterceptsForPeer() returns empty,
    //       PalDirectory.getBundleMetadata(bundleName) returns null
    assertThat(rmResult.exitCode(), is(0));
    assertThat(rmResult.stdout(), containsString("removed"));

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    assertTrue(
        "All intercepts should be removed", palDirectory.listInterceptsForPeer(peerId).isEmpty());
    assertNull("Bundle metadata should be removed", palDirectory.getBundleMetadata(bundleName));
  }

  /**
   * Tests that {@code pal intercept rm --peer <name>} removes all intercepts for a peer.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testRemove_byPeer_removesAllIntercepts() throws Exception {
    // Given: A bundle has been applied (3 intercepts exist in etcd for the peer)
    String palDir = getPalDirectoryUrl();
    UUID peerId = UUID.randomUUID();
    String peerName = "rm-peer-peer-" + generateId();
    String bundleName = "rm-peer-bundle-" + generateId();

    peerProcess =
        launchPeer(peerId, "-d", palDir, "-n", peerName, "--interceptable", "--as-service");

    File yamlFile = createBundleYaml(bundleName, peerName);
    CliProcessResult applyResult = runInterceptApply("-d", palDir, yamlFile.getAbsolutePath());
    assertThat("Apply should succeed", applyResult.exitCode(), is(0));

    // When: Running `pal intercept rm -d <url> --peer <name>`
    CliProcessResult rmResult = runInterceptRm("-d", palDir, "--peer", peerName);

    // Then: Exit code is 0, all intercepts for that peer are gone from etcd
    assertThat(rmResult.exitCode(), is(0));

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);
    assertTrue(
        "All intercepts for peer should be removed",
        palDirectory.listInterceptsForPeer(peerId).isEmpty());
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
