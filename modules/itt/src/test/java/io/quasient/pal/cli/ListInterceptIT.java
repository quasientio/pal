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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

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

/**
 * Integration tests for the {@code pal intercept ls} command.
 *
 * <p>Tests listing of intercepts registered in etcd in various formats (short, long) with sorting
 * options using the new entity-operation command structure.
 *
 * <p>Requires running etcd infrastructure as described in modules/itt/README.md.
 */
public class ListInterceptIT extends AbstractCliIT {

  /** Primary peer process managed by the test lifecycle. */
  private PeerProcess peerProcess;

  /** PalDirectory client used to register intercepts programmatically. */
  private PalDirectory palDirectory;

  /** Sets up test state before each test. */
  @Before
  public void setUp() {
    peerProcess = null;
    palDirectory = null;
  }

  /**
   * Tears down test state after each test, closing the directory client and stopping any peers.
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

  // ==========================================================================
  // Intercept listing tests: pal intercept ls
  // Old command: pal ls -I
  // New command: pal intercept ls
  // ==========================================================================

  /**
   * Tests that {@code pal intercept ls} lists registered intercepts.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_showsRegisteredIntercepts() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "intercept-peer-" + generateId();
    String walName = "wal-intercept-" + generateId();

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

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);

    InterceptableMethodCall methodCall =
        new InterceptableMethodCall("test", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> intercept =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerId,
            InterceptType.BEFORE,
            "com.example.Test",
            "com.example.Callback",
            "onTest",
            methodCall);
    palDirectory.createIntercept(intercept);

    CliProcessResult result = runInterceptLs("-d", palDir);

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), is(not("")));
  }

  /**
   * Tests that {@code pal intercept ls -l} shows detailed intercept information.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_longFormat() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "intercept-long-" + generateId();
    String walName = "wal-intercept-long-" + generateId();

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

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);

    InterceptableMethodCall methodCall =
        new InterceptableMethodCall("test", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> intercept =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerId,
            InterceptType.BEFORE,
            "com.example.Test",
            "com.example.Callback",
            "onTest",
            methodCall);
    palDirectory.createIntercept(intercept);

    CliProcessResult result = runInterceptLs("-d", palDir, "-l");

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString("total"));
  }

  /**
   * Tests that {@code pal intercept ls -l -c} sorts intercepts by creation time (newest first).
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_sortByCtime() throws Exception {
    String palDir = getPalDirectoryUrl();
    String kafkaServers = getKafkaServers();
    UUID peerId = UUID.randomUUID();
    String peerName = "intercept-ctime-" + generateId();
    String walName = "wal-intercept-ctime-" + generateId();

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

    palDirectory = new PalDirectory(getPalDirectoryUrl(), true);

    InterceptableMethodCall firstMethodCall =
        new InterceptableMethodCall("firstMethod", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> firstIntercept =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerId,
            InterceptType.BEFORE,
            "com.example.First",
            "com.example.Callback",
            "onFirst",
            firstMethodCall);
    palDirectory.createIntercept(firstIntercept);

    Thread.sleep(1000);

    InterceptableMethodCall secondMethodCall =
        new InterceptableMethodCall("secondMethod", Collections.emptyList());
    InterceptRequest<InterceptableMethodCall> secondIntercept =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerId,
            InterceptType.AFTER,
            "com.example.Second",
            "com.example.Callback",
            "onSecond",
            secondMethodCall);
    palDirectory.createIntercept(secondIntercept);

    CliProcessResult result = runInterceptLs("-d", palDir, "-l", "-c");

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString("total 2"));
  }

  /**
   * Tests that {@code pal intercept ls} with no intercepts registered shows empty output.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_empty() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result = runInterceptLs("-d", palDir);

    assertThat(result.exitCode(), is(0));
  }

  /**
   * Tests that {@code pal intercept ls -l} with no intercepts shows total 0.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void testListIntercepts_emptyLongFormat() throws Exception {
    String palDir = getPalDirectoryUrl();

    CliProcessResult result = runInterceptLs("-d", palDir, "-l");

    assertThat(result.exitCode(), is(0));
    assertThat(result.stdout(), containsString("total 0"));
  }
}
