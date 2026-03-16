/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.cli.AbstractCliIT;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the {@code pal wal-index} command with OrderTotal.
 *
 * <p>Validates the WAL index pipeline end-to-end for both Chronicle Queue and Kafka backends: peer
 * records WAL, WalReader reads it, WalIndex pairs and spans entries, and the CLI displays results.
 *
 * <p>Parameterized over backend type: "chronicle" uses {@code file:} prefix WAL paths, "kafka" uses
 * Kafka topic names with {@code -k} bootstrap servers.
 */
@RunWith(Parameterized.class)
public class WalIndexOrderTotalIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(WalIndexOrderTotalIT.class);

  /** Fully qualified name of the test application used for recording. */
  private static final String MAIN_CLASS = "io.quasient.foobar.apps.quantized.replay.OrderTotal";

  /** The WAL backend type for this test run ("chronicle" or "kafka"). */
  private final String backend;

  /**
   * Creates a parameterized test instance for the given backend.
   *
   * @param backend the WAL backend type ("chronicle" or "kafka")
   */
  public WalIndexOrderTotalIT(String backend) {
    this.backend = backend;
  }

  /**
   * Returns the parameterized backend types.
   *
   * @return collection of parameters: "chronicle" and "kafka"
   */
  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[] {"chronicle"}, new Object[] {"kafka"});
  }

  /**
   * Creates a WAL spec appropriate for the current backend.
   *
   * <p>For Chronicle, returns a {@code file:}-prefixed path and registers it for cleanup. For
   * Kafka, returns a unique topic name (no cleanup needed; topics are ephemeral).
   *
   * @param prefix a descriptive prefix for the WAL name
   * @return the WAL spec string
   */
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
   * Records a WAL by running the peer with the given application arguments.
   *
   * <p>Launches a peer with {@code pal run} using a WAL at the specified location. For Chronicle,
   * the WAL spec is a {@code file:}-prefixed path. For Kafka, a topic name is used with {@code -k}
   * bootstrap servers.
   *
   * @param walSpec the WAL spec (Chronicle file path or Kafka topic name)
   * @param appArgs application arguments passed to the main class
   * @return the process result containing exit code, stdout, and stderr
   * @throws Exception if recording fails
   */
  private ProcessResult recordWal(String walSpec, String... appArgs) throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.add("--wal");
    args.add(walSpec);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(MAIN_CLASS);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Runs {@code pal wal-index} against the given WAL spec with optional extra arguments.
   *
   * <p>For Kafka backend, {@code -k} bootstrap servers are included. Extra arguments (such as
   * {@code --verbose}) are inserted before the WAL spec positional argument.
   *
   * @param walSpec the WAL spec (Chronicle file path or Kafka topic name)
   * @param extraArgs additional arguments (e.g., {@code --verbose})
   * @return the CLI process result containing exit code, stdout, and stderr
   * @throws Exception if wal-index fails
   */
  private CliProcessResult doWalIndex(String walSpec, String... extraArgs) throws Exception {
    List<String> args = new ArrayList<>();
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    Collections.addAll(args, extraArgs);
    args.add(walSpec);
    return runLogIndex(args.toArray(new String[0]));
  }

  /**
   * Records a WAL with OrderTotal, runs {@code pal wal-index}, and verifies that the index reports
   * zero structural issues and balanced operation/completion pairs.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void walIndexShowsBalancedPairs() throws Exception {
    String walSpec = createWalSpec("walindex-ot-balanced");

    // Record WAL with OrderTotal
    ProcessResult recordResult = recordWal(walSpec, "TX|A|gum=1.00|0|");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    logger.info("WAL recorded: {}", walSpec);

    // Run wal-index on the recorded WAL
    CliProcessResult indexResult = doWalIndex(walSpec);

    logger.info("wal-index exit code: {}", indexResult.exitCode());
    logger.info("wal-index stdout:\n{}", indexResult.stdout());
    logger.info("wal-index stderr:\n{}", indexResult.stderr());

    // Verify exit code 0
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    // Verify zero structural issues
    assertThat(
        "Output should contain Issues count", indexResult.stdout(), containsString("Issues:"));
    assertThat(
        "Output should report zero issues",
        indexResult.stdout(),
        containsString("Issues:        0"));

    // Verify positive entry count
    assertThat(
        "Output should contain Entries count", indexResult.stdout(), containsString("Entries:"));
    int entries = extractCount(indexResult.stdout(), "Entries:");
    assertThat("Should have positive entry count", entries, greaterThan(0));

    // Verify positive pair count
    assertThat("Output should contain Pairs count", indexResult.stdout(), containsString("Pairs:"));
    int pairs = extractCount(indexResult.stdout(), "Pairs:");
    assertThat("Should have positive pair count", pairs, greaterThan(0));

    // Verify operations count equals completions count (balanced WAL)
    int operations = extractCount(indexResult.stdout(), "Operations:");
    int completions = extractCount(indexResult.stdout(), "Completions:");
    assertEquals("Operations should equal completions (balanced WAL)", operations, completions);
  }

  /**
   * Records a WAL with OrderTotal, runs {@code pal wal-index --verbose}, and verifies that the
   * verbose output contains entry details including OPERATION/COMPLETION kinds and the OrderTotal
   * class name.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void walIndexVerboseShowsEntries() throws Exception {
    String walSpec = createWalSpec("walindex-ot-verbose");

    // Record WAL with OrderTotal
    ProcessResult recordResult = recordWal(walSpec, "TX|A|gum=1.00|0|");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    logger.info("WAL recorded: {}", walSpec);

    // Run wal-index with --verbose flag
    CliProcessResult indexResult = doWalIndex(walSpec, "--verbose");

    logger.info("wal-index verbose exit code: {}", indexResult.exitCode());
    logger.info("wal-index verbose stdout:\n{}", indexResult.stdout());

    // Verify exit code 0
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    // Verify verbose output contains OPERATION entries
    assertThat(
        "Verbose output should contain OPERATION entries",
        indexResult.stdout(),
        containsString("OPERATION"));

    // Verify verbose output contains COMPLETION entries
    assertThat(
        "Verbose output should contain COMPLETION entries",
        indexResult.stdout(),
        containsString("COMPLETION"));

    // Verify verbose output contains the OrderTotal class name
    assertThat(
        "Verbose output should contain OrderTotal class name",
        indexResult.stdout(),
        containsString("OrderTotal"));
  }

  /**
   * Extracts a numeric count from a summary line in the wal-index output.
   *
   * <p>Looks for lines matching the pattern {@code label N} and extracts the integer value.
   *
   * @param output the full stdout output
   * @param label the label to search for (e.g., "Entries:", "Pairs:")
   * @return the extracted integer count
   * @throws IllegalStateException if no matching line is found
   */
  private int extractCount(String output, String label) {
    Pattern pattern = Pattern.compile(Pattern.quote(label) + "\\s+(\\d+)");
    Matcher matcher = pattern.matcher(output);
    if (matcher.find()) {
      return Integer.parseInt(matcher.group(1));
    }
    throw new IllegalStateException(
        "Could not find '" + label + "' with numeric value in output:\n" + output);
  }
}
