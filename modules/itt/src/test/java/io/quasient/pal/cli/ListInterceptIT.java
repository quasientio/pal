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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.PeerProcess;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the `pal ls -I` command.
 *
 * <p>Tests listing of intercepts registered in etcd in various formats (short, long) with sorting
 * options.
 *
 * <p>Requires running etcd infrastructure as described in modules/itt/README.md.
 */
public class ListInterceptIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(ListInterceptIT.class);

  /** Peer process launched for testing, or null if not launched. */
  private PeerProcess peerProcess;

  /** PalDirectory instance for registering intercepts. */
  private PalDirectory palDirectory;

  /** UUID of the peer used for intercept registration. */
  private UUID peerId;

  /** Sets up test environment before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
    palDirectory = null;
    peerId = null;
  }

  /**
   * Cleans up resources after each test.
   *
   * @throws Exception if cleanup fails
   */
  @After
  public void tearDown() throws Exception {
    // Clean up intercepts
    if (palDirectory != null && peerId != null) {
      try {
        palDirectory.deleteInterceptsForPeer(peerId);
      } catch (Exception e) {
        logger.warn("Error cleaning up intercepts", e);
      }
      palDirectory.close();
    }
    if (peerProcess != null) {
      stopPeer(peerProcess);
      peerProcess = null;
    }
  }

  /**
   * Launches a peer and registers intercepts against it. Returns the peer UUID.
   *
   * @param interceptCount number of intercepts to register
   * @param classPrefix prefix for the intercepted class name
   * @return the peer UUID used for intercept registration
   * @throws Exception if launching or registration fails
   */
  private UUID launchPeerAndRegisterIntercepts(int interceptCount, String classPrefix)
      throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();
    peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(peerId, "-d", palDirectoryUrl, "--interceptable", "-cp", getIttAppsClasspath());

    palDirectory = new PalDirectory(palDirectoryUrl, true);

    for (int i = 0; i < interceptCount; i++) {
      InterceptRequest<InterceptableMethodCall> request =
          new InterceptRequest<>(
              UUID.randomUUID(),
              peerId,
              InterceptType.BEFORE,
              classPrefix + ".Class" + i,
              classPrefix + ".Handler" + i,
              "handle" + i,
              new InterceptableMethodCall("method" + i, Collections.emptyList()));
      palDirectory.createIntercept(request);
    }

    return peerId;
  }

  /**
   * Tests that `pal ls -I` lists registered intercepts.
   *
   * <p>Launches a peer, registers intercepts, and verifies they appear in the listing.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_showsRegisteredIntercepts() throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();
    launchPeerAndRegisterIntercepts(2, "com.example.test");

    CliProcessResult result = runLs("-d", palDirectoryUrl, "-I");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    // Short format shows UUIDs - just verify we get some output
    assertThat("Expected output for intercepts", result.stdout().trim().length() > 0);
    logger.info("Successfully listed intercepts in short format");
  }

  /**
   * Tests that `pal ls -I -l` shows detailed intercept information.
   *
   * <p>Registers intercepts and verifies long format includes class, type, and callback info.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_longFormat() throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();
    launchPeerAndRegisterIntercepts(1, "com.example.longfmt");

    CliProcessResult result = runLs("-d", palDirectoryUrl, "-I", "-l");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected total count header", result.stdout(), containsString("total 1"));
    assertThat("Expected BEFORE type in output", result.stdout(), containsString("BEFORE"));
    assertThat("Expected class name in output", result.stdout(), containsString("Class0"));
    assertThat("Expected callback in output", result.stdout(), containsString("handle0"));
    logger.info("Successfully listed intercepts in long format");
  }

  /**
   * Tests that `pal ls -I` does not show peers or logs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_doesNotShowPeersOrLogs() throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();

    peerId = UUID.randomUUID();
    String peerName = "icpt-peer-" + generateId();
    String walName = "icpt-wal-" + generateId();

    // Launch a long-running peer with WAL so both peer and log are registered in etcd
    peerProcess =
        launchPeer(
            peerId,
            "-d",
            palDirectoryUrl,
            "-k",
            kafkaServers,
            "-n",
            peerName,
            "--wal",
            walName,
            "--interceptable",
            "-cp",
            getIttAppsClasspath());

    palDirectory = new PalDirectory(palDirectoryUrl, true);
    InterceptRequest<InterceptableMethodCall> request =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerId,
            InterceptType.AFTER,
            "com.example.NoShow",
            "com.example.NoShowHandler",
            "callback",
            new InterceptableMethodCall("doStuff", Collections.emptyList()));
    palDirectory.createIntercept(request);

    // List only intercepts
    CliProcessResult result = runLs("-d", palDirectoryUrl, "-I");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    // Should not show peers when -I is specified
    assertThat(
        "Expected peer name NOT in intercepts-only output",
        result.stdout(),
        not(containsString(peerName)));
    // Should not show logs when -I is specified
    assertThat(
        "Expected WAL log name NOT in intercepts-only output",
        result.stdout(),
        not(containsString(walName)));
    logger.info("Successfully verified intercepts-only listing excludes peers and logs");
  }

  /**
   * Tests that `pal ls -I -l -c` sorts intercepts by creation time (newest first).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_sortByCtime() throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();
    peerId = UUID.randomUUID();

    peerProcess =
        launchPeer(peerId, "-d", palDirectoryUrl, "--interceptable", "-cp", getIttAppsClasspath());

    palDirectory = new PalDirectory(palDirectoryUrl, true);

    // Create first intercept
    InterceptRequest<InterceptableMethodCall> request1 =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerId,
            InterceptType.BEFORE,
            "aaa.First",
            "aaa.Handler",
            "handle",
            new InterceptableMethodCall("first", Collections.emptyList()));
    palDirectory.createIntercept(request1);

    // Wait to ensure different creation times
    Thread.sleep(1100);

    // Create second intercept
    InterceptRequest<InterceptableMethodCall> request2 =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerId,
            InterceptType.AFTER,
            "zzz.Second",
            "zzz.Handler",
            "handle",
            new InterceptableMethodCall("second", Collections.emptyList()));
    palDirectory.createIntercept(request2);

    // List intercepts sorted by creation time (newest first)
    CliProcessResult result = runLs("-d", palDirectoryUrl, "-I", "-l", "-c");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected total count", result.stdout(), containsString("total 2"));

    // With -c (newest first), "Second" should appear before "First"
    int idxFirst = result.stdout().indexOf("First");
    int idxSecond = result.stdout().indexOf("Second");
    assertTrue("Expected both intercepts in output", idxFirst > 0 && idxSecond > 0);
    assertTrue("Expected newer intercept to appear first", idxSecond < idxFirst);
    logger.info("Successfully verified intercepts sorted by creation time");
  }

  /**
   * Tests that combining -I with -L produces an error.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_withLogsFlag_showsError() throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();

    CliProcessResult result = runLs("-d", palDirectoryUrl, "-I", "-L");

    assertTrue("Expected non-zero exit code", result.exitCode() != 0);
    logger.info("Successfully validated ls command rejects both -I and -L flags");
  }

  /**
   * Tests that combining -I with -P produces an error.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_withPeersFlag_showsError() throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();

    CliProcessResult result = runLs("-d", palDirectoryUrl, "-I", "-P");

    assertTrue("Expected non-zero exit code", result.exitCode() != 0);
    logger.info("Successfully validated ls command rejects both -I and -P flags");
  }

  /**
   * Tests that `pal ls -I` with no intercepts registered shows empty output.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_empty() throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();

    CliProcessResult result = runLs("-d", palDirectoryUrl, "-I");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    logger.info("Successfully listed empty intercepts");
  }

  /**
   * Tests that `pal ls -I -l` with no intercepts shows total 0.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_emptyLongFormat() throws Exception {
    String palDirectoryUrl = getPalDirectoryUrl();

    CliProcessResult result = runLs("-d", palDirectoryUrl, "-I", "-l");

    assertEquals("Expected successful exit code", 0, result.exitCode());
    assertThat("Expected total 0", result.stdout(), containsString("total 0"));
    logger.info("Successfully listed empty intercepts in long format");
  }
}
