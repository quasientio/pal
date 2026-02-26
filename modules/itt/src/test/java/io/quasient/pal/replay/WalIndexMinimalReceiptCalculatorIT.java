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
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for the {@code pal wal-index} command with MinimalReceiptCalculator.
 *
 * <p>Validates the Phase 0 pipeline end-to-end: peer records WAL with Chronicle Queue, WalReader
 * reads it, WalIndex pairs and spans entries, and the CLI displays results.
 *
 * <p>All tests use Chronicle-only WAL paths ({@code file:} prefix). Each test records a fresh WAL
 * and tracks it for cleanup.
 */
public class WalIndexMinimalReceiptCalculatorIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger =
      LoggerFactory.getLogger(WalIndexMinimalReceiptCalculatorIT.class);

  /** Fully qualified name of the test application used for recording. */
  private static final String MAIN_CLASS =
      "io.quasient.pal.apps.quantized.replay.MinimalReceiptCalculator";

  /**
   * Records a WAL by running the peer with the given application arguments.
   *
   * <p>Launches a peer with {@code pal run} using a Chronicle WAL at the specified path. The peer
   * runs the {@link #MAIN_CLASS} application, writes all operations to the WAL, then exits.
   *
   * @param walPath absolute path for the Chronicle WAL directory
   * @param appArgs application arguments passed to the main class
   * @return the process result containing exit code, stdout, and stderr
   * @throws Exception if recording fails
   */
  private ProcessResult recordWal(String walPath, String... appArgs) throws Exception {
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
   * index reports balanced operation/completion pairs with at most one structural issue (the
   * expected orphaned main() completion from SelfBootstrapInvoker).
   *
   * <p>The WAL always contains one orphaned completion for the {@code main()} method because
   * SelfBootstrapInvoker invokes main() outside of AspectJ weaving, so the operation is not
   * captured in the WAL but its completion is recorded. Every other operation has a matching
   * completion, verified by the pairs count equaling the operations count.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void walIndexShowsBalancedPairs() throws Exception {
    String walPath = "/tmp/pal-walindex-balanced-" + generateId();
    trackChronicleLog(walPath);

    // Record WAL with MinimalReceiptCalculator
    ProcessResult recordResult = recordWal(walPath, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    logger.info("WAL recorded at: {}", walPath);

    // Run wal-index on the recorded WAL
    CliProcessResult indexResult = runWalIndex("file:" + walPath);

    logger.info("wal-index exit code: {}", indexResult.exitCode());
    logger.info("wal-index stdout:\n{}", indexResult.stdout());
    logger.info("wal-index stderr:\n{}", indexResult.stderr());

    // Verify exit code 0
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    // Verify positive entry count
    assertThat(
        "Output should contain Entries count", indexResult.stdout(), containsString("Entries:"));
    int entries = extractCount(indexResult.stdout(), "Entries:");
    assertThat("Should have positive entry count", entries, greaterThan(0));

    // Verify positive pair count
    assertThat("Output should contain Pairs count", indexResult.stdout(), containsString("Pairs:"));
    int pairs = extractCount(indexResult.stdout(), "Pairs:");
    assertThat("Should have positive pair count", pairs, greaterThan(0));

    // Verify operations and completions counts are positive
    int operations = extractCount(indexResult.stdout(), "Operations:");
    int completions = extractCount(indexResult.stdout(), "Completions:");
    assertThat("Should have positive operation count", operations, greaterThan(0));
    assertThat("Should have positive completion count", completions, greaterThan(0));

    // Every recorded operation has a matching completion (pairs == operations)
    assertEquals(
        "Every operation should have a matching completion (pairs == operations)",
        operations,
        pairs);

    // The WAL has at most one orphaned completion (from the main() bootstrap).
    // completions - operations accounts for the main() completion that has no matching operation.
    int issues = extractCount(indexResult.stdout(), "Issues:");
    assertThat(
        "Output should contain Issues count", indexResult.stdout(), containsString("Issues:"));
    assertEquals(
        "At most one structural issue (orphaned main() completion)",
        completions - operations,
        issues);
  }

  /**
   * Records a WAL with MinimalReceiptCalculator, runs {@code pal wal-index --verbose}, and verifies
   * that the verbose output contains entry details including OPERATION/COMPLETION kinds and the
   * MinimalReceiptCalculator class name.
   *
   * @throws Exception if test execution fails
   */
  @Test
  public void walIndexVerboseShowsEntries() throws Exception {
    String walPath = "/tmp/pal-walindex-verbose-" + generateId();
    trackChronicleLog(walPath);

    // Record WAL with MinimalReceiptCalculator
    ProcessResult recordResult = recordWal(walPath, "milk:2,bread:1,apple:5");
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
