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
import static org.junit.Assert.fail;

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
 * Integration tests for the {@code pal wal-index} command with MinimalReceiptCalculator.
 *
 * <p>Validates the WAL index pipeline end-to-end for both Chronicle Queue and Kafka backends: peer
 * records WAL, WalReader reads it, WalIndex pairs and spans entries, and the CLI displays results.
 *
 * <p>Parameterized over backend type: "chronicle" uses {@code file:} prefix WAL paths, "kafka" uses
 * Kafka topic names with {@code -k} bootstrap servers.
 */
@RunWith(Parameterized.class)
public class WalIndexMinimalReceiptCalculatorIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger =
      LoggerFactory.getLogger(WalIndexMinimalReceiptCalculatorIT.class);

  /** Fully qualified name of the test application used for recording. */
  private static final String MAIN_CLASS =
      "io.quasient.pal.apps.quantized.replay.MinimalReceiptCalculator";

  /** The WAL backend type for this test run ("chronicle" or "kafka"). */
  private final String backend;

  /**
   * Creates a parameterized test instance for the given backend.
   *
   * @param backend the WAL backend type ("chronicle" or "kafka")
   */
  public WalIndexMinimalReceiptCalculatorIT(String backend) {
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
   * Records a WAL by running the peer with the given application arguments using a Chronicle WAL.
   *
   * <p>Launches a peer with {@code pal run} using a Chronicle WAL at the specified path. The peer
   * runs the {@link #MAIN_CLASS} application, writes all operations to the WAL, then exits.
   *
   * @param walPath absolute path for the Chronicle WAL directory
   * @param appArgs application arguments passed to the main class
   * @return the process result containing exit code, stdout, and stderr
   * @throws Exception if recording fails
   */
  private ProcessResult recordChronicleWal(String walPath, String... appArgs) throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    args.add("--wal");
    args.add("file:" + walPath);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(MAIN_CLASS);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Records a WAL with MinimalReceiptCalculator, runs {@code pal wal-index}, and verifies that the
   * index reports zero structural issues and balanced operation/completion pairs.
   *
   * <p>For the "chronicle" backend, the full test runs end-to-end. For the "kafka" backend, the
   * test is a specification stub awaiting implementation in #854.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void walIndexShowsBalancedPairs() throws Exception {
    if ("kafka".equals(backend)) {
      // Given: A WAL recorded with MinimalReceiptCalculator using Kafka backend
      // When: pal wal-index -k kafkaServers topicName
      // Then: Exit code 0, Issues: 0, operations count == completions count, Pairs > 0

      // TODO(#854): Implement Kafka backend test logic
      fail("Not yet implemented");
    }

    String walPath = "/tmp/pal-walindex-balanced-" + generateId();
    trackChronicleLog(walPath);

    // Record WAL with MinimalReceiptCalculator
    ProcessResult recordResult = recordChronicleWal(walPath, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    logger.info("WAL recorded at: {}", walPath);

    // Run wal-index on the recorded WAL
    CliProcessResult indexResult = runWalIndex("file:" + walPath);

    logger.info("wal-index exit code: {}", indexResult.exitCode());
    logger.info("wal-index stdout:\n{}", indexResult.stdout());
    logger.info("wal-index stderr:\n{}", indexResult.stderr());

    // Verify exit code 0
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    // Verify zero structural issues
    assertThat(
        "Output should contain Issues count", indexResult.stdout(), containsString("Issues:"));
    assertThat(
        "Output should report zero issues", indexResult.stdout(), containsString("Issues:      0"));

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
   * Records a WAL with MinimalReceiptCalculator, runs {@code pal wal-index --verbose}, and verifies
   * that the verbose output contains entry details including OPERATION/COMPLETION kinds and the
   * MinimalReceiptCalculator class name.
   *
   * <p>For the "chronicle" backend, the full test runs end-to-end. For the "kafka" backend, the
   * test is a specification stub awaiting implementation in #854.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void walIndexVerboseShowsEntries() throws Exception {
    if ("kafka".equals(backend)) {
      // Given: A WAL recorded with MinimalReceiptCalculator using Kafka backend
      // When: pal wal-index -k kafkaServers --verbose topicName
      // Then: Exit code 0, output contains OPERATION and COMPLETION entries,
      //       contains MinimalReceiptCalculator class name

      // TODO(#854): Implement Kafka backend test logic
      fail("Not yet implemented");
    }

    String walPath = "/tmp/pal-walindex-verbose-" + generateId();
    trackChronicleLog(walPath);

    // Record WAL with MinimalReceiptCalculator
    ProcessResult recordResult = recordChronicleWal(walPath, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    logger.info("WAL recorded at: {}", walPath);

    // Run wal-index with --verbose flag
    CliProcessResult indexResult = runWalIndex("--verbose", "file:" + walPath);

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

    // Verify verbose output contains the MinimalReceiptCalculator class name
    assertThat(
        "Verbose output should contain MinimalReceiptCalculator class name",
        indexResult.stdout(),
        containsString("MinimalReceiptCalculator"));
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
