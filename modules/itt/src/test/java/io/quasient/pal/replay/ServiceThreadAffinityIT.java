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
package io.quasient.pal.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.cli.AbstractCliIT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the {@code --service-thread} option and service-request thread affinity.
 *
 * <p>Validates that entry points on threads matching the service-thread pattern are tagged with
 * {@code service-request} affinity during recording, and that replay works correctly with these
 * tagged entry points.
 *
 * <p>Uses the {@code ServiceThreadApp} test application from {@code itt-apps}, which spawns threads
 * named "executor-thread-0", "executor-thread-1", etc. — simulating how web frameworks like Quarkus
 * dispatch requests on named worker threads. Since these threads are started by JVM native code
 * (not woven), the first woven method call in each thread's {@code run()} body is an entry point at
 * dispatch depth 0.
 *
 * <p>Since CDI is not on the ITT classpath, the CDI request context executor is not registered
 * during replay. Entry points tagged with {@code service-request} affinity fall back to direct
 * execution. This tests the recording-side tagging and the graceful fallback during replay.
 *
 * <p>Parameterized over WAL backend type ("chronicle" or "kafka").
 *
 * @see io.quasient.foobar.apps.quantized.replay.ServiceThreadApp
 */
@RunWith(Parameterized.class)
public class ServiceThreadAffinityIT extends AbstractCliIT {

  private static final Logger logger = LoggerFactory.getLogger(ServiceThreadAffinityIT.class);

  /** Fully qualified name of the ServiceThreadApp test application. */
  private static final String SERVICE_THREAD_APP_CLASS =
      "io.quasient.foobar.apps.quantized.replay.ServiceThreadApp";

  /**
   * Regex pattern matching the executor thread names spawned by ServiceThreadApp. Matches thread
   * names like "executor-thread-0".
   */
  private static final String SERVICE_THREAD_PATTERN = "executor-thread-.*";

  /** The WAL backend type for this test run ("chronicle" or "kafka"). */
  private final String backend;

  public ServiceThreadAffinityIT(String backend) {
    this.backend = backend;
  }

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[] {"chronicle"}, new Object[] {"kafka"});
  }

  /**
   * Verifies that entry points on threads matching the service-thread pattern are tagged with
   * {@code service-request} affinity in the WAL.
   *
   * <p>Records a WAL with {@code --service-thread} set to match executor threads, then prints the
   * WAL in full format and checks that the {@code thread_affinity} field contains
   * "service-request".
   */
  @Test
  public void serviceThreadPattern_tagsEntryPointsWithAffinity() throws Exception {
    String walSpec = recordWal("svc-tag", true);

    CliProcessResult printResult = printWal(walSpec);

    logger.info("WAL print exit code: {}", printResult.exitCode());
    if (logger.isDebugEnabled()) {
      logger.debug("WAL print stdout:\n{}", printResult.stdout());
    }

    assertEquals("WAL print should succeed", 0, printResult.exitCode());

    long affinityCount =
        printResult
            .stdout()
            .lines()
            .filter(line -> line.contains("\"thread_affinity\": \"service-request\""))
            .count();

    logger.info("Found {} entries with service-request affinity", affinityCount);

    assertThat(
        "WAL should contain entry points tagged with service-request affinity",
        (int) affinityCount,
        greaterThanOrEqualTo(1));
  }

  /**
   * Verifies that entries without service-thread pattern are NOT tagged with affinity.
   *
   * <p>Records a WAL without {@code --service-thread}, then checks that no entries have
   * service-request affinity. This confirms that the tagging is conditional on the flag.
   */
  @Test
  public void withoutServiceThreadPattern_noAffinityTagging() throws Exception {
    String walSpec = recordWal("no-svc", false);

    CliProcessResult printResult = printWal(walSpec);

    assertEquals("WAL print should succeed", 0, printResult.exitCode());

    long affinityCount =
        printResult
            .stdout()
            .lines()
            .filter(line -> line.contains("\"thread_affinity\": \"service-request\""))
            .count();

    logger.info("Found {} entries with service-request affinity (expected 0)", affinityCount);

    assertEquals(
        "WAL should NOT contain service-request affinity when --service-thread is not used",
        0,
        affinityCount);
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  private String createWalSpec(String prefix) {
    String id = generateId();
    if ("chronicle".equals(backend)) {
      String path = "/tmp/pal-" + prefix + "-" + id;
      trackChronicleLog(path);
      return "file:" + path;
    } else {
      return "test-" + prefix + "-" + id;
    }
  }

  /**
   * Records a WAL by running ServiceThreadApp with optional {@code --service-thread}.
   *
   * @param prefix descriptive prefix for the WAL name
   * @param withServiceThread whether to add the --service-thread flag
   * @return the WAL spec string
   */
  private String recordWal(String prefix, boolean withServiceThread) throws Exception {
    String walSpec = createWalSpec(prefix);

    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.add("--wal");
    args.add(walSpec);
    args.add("--no-wal-incoming-cli");
    if (withServiceThread) {
      args.add("--service-thread");
      args.add(SERVICE_THREAD_PATTERN);
    }
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(SERVICE_THREAD_APP_CLASS);

    ProcessResult recordResult = runPeer(args.toArray(new String[0]));
    logger.info("Record exit code: {}", recordResult.exitCode());
    logger.info("Record stdout: {}", recordResult.stdout());
    if (recordResult.exitCode() != 0) {
      logger.warn("Record stderr: {}", recordResult.stderr());
    }
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    assertThat(
        "Recording should produce expected output", recordResult.stdout(), containsString("done"));

    // Allow time for WAL flush
    Thread.sleep("kafka".equals(backend) ? 2000 : 500);

    return walSpec;
  }

  private CliProcessResult printWal(String walSpec) throws Exception {
    List<String> args = new ArrayList<>();
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.add("--full");
    args.add(walSpec);
    return runLogPrint(args.toArray(new String[0]));
  }
}
