/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.recording;

import static org.junit.Assert.fail;

import io.quasient.pal.cli.AbstractCliIT;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Integration tests verifying that recording with {@code --scope} flags produces a WAL containing
 * only in-scope operations and that WAL size is reduced compared to unscoped recording.
 *
 * <p>Uses {@code MinimalReceiptCalculator} from itt-apps and examines WAL contents via {@code pal
 * log index}. Parameterized over Chronicle Queue and Kafka backends following the {@code
 * WalIndexMinimalReceiptCalculatorIT} pattern.
 *
 * <p>These are end-to-end acceptance tests for the recording scope feature, using real PAL
 * infrastructure (WAL, weaving, dispatch).
 *
 * @see io.quasient.pal.core.recording.RecordingScope
 */
@RunWith(Parameterized.class)
@SuppressWarnings("UnusedVariable") // Fields used by implementation in #1278
public class RecordingScopeIT extends AbstractCliIT {

  /** Fully qualified name of the test application used for recording. */
  private static final String MAIN_CLASS =
      "io.quasient.foobar.apps.quantized.replay.MinimalReceiptCalculator";

  /** The WAL backend type for this test run ("chronicle" or "kafka"). */
  private final String backend;

  /**
   * Creates a parameterized test instance for the given backend.
   *
   * @param backend the WAL backend type ("chronicle" or "kafka")
   */
  public RecordingScopeIT(String backend) {
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
   * Verifies that recording with {@code --scope io.quasient.foobar.**} and {@code --scope-default
   * skip} produces a WAL where all entries have class names matching the scope pattern.
   *
   * <p>Specifically, the WAL must not contain entries for JDK classes such as {@code
   * java.lang.String}, {@code java.util.HashMap}, or {@code java.lang.Integer}.
   */
  @Test
  @Ignore("Awaiting implementation in #1278")
  public void recordWithScopeOnlyIncludesMatchingOperations() throws Exception {
    // Given: MinimalReceiptCalculator app recorded with --scope io.quasient.foobar.**
    //        and --scope-default skip
    // When: pal log index is run on the resulting WAL
    // Then: All WAL entries have className matching io.quasient.foobar.*
    //       No entries exist for java.lang.String, java.util.HashMap, or java.lang.Integer

    // TODO(#1278): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that recording with {@code --scope-exclude} flags removes the excluded classes from
   * the WAL while retaining entries for non-excluded application classes.
   */
  @Test
  @Ignore("Awaiting implementation in #1278")
  public void recordWithScopeExcludeRemovesMatchingOperations() throws Exception {
    // Given: MinimalReceiptCalculator app recorded with
    //        --scope-exclude java.lang.String.** --scope-exclude java.util.HashMap.**
    // When: pal log index is run on the resulting WAL
    // Then: No entries exist for java.lang.String or java.util.HashMap
    //       Entries for application classes (io.quasient.foobar.*) are present

    // TODO(#1278): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that recording the same application with scope filtering produces a WAL with
   * significantly fewer entries than recording without scope.
   */
  @Test
  @Ignore("Awaiting implementation in #1278")
  public void recordWithScopeProducesSmallerWal() throws Exception {
    // Given: MinimalReceiptCalculator app recorded twice:
    //        (1) without any --scope flags (full recording)
    //        (2) with --scope io.quasient.foobar.** --scope-default skip
    // When: pal log index is run on both resulting WALs
    // Then: The scoped WAL has significantly fewer entries than the unscoped WAL

    // TODO(#1278): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that recording with {@code --scope-io} and {@code --scope-default skip} produces a WAL
   * containing entries for I/O boundary operations (e.g., {@code System.currentTimeMillis}) but not
   * for pure computation operations.
   */
  @Test
  @Ignore("Awaiting implementation in #1278")
  public void recordWithScopeIoIncludesIoBoundaryOps() throws Exception {
    // Given: An app that makes I/O calls, recorded with --scope-io --scope-default skip
    // When: pal log index is run on the resulting WAL
    // Then: WAL contains entries for I/O boundary operations
    //       (e.g., System.currentTimeMillis, time-related calls)
    //       WAL does not contain entries for pure computation operations
    //       (e.g., String.split, HashMap.put)

    // TODO(#1278): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that recording with {@code --scope io.quasient.foobar.**} and {@code --scope-default
   * skip} excludes field get/put operations for JDK classes from the WAL while retaining field
   * operations for application classes.
   */
  @Test
  @Ignore("Awaiting implementation in #1278")
  public void recordWithScopeExcludesFieldOps() throws Exception {
    // Given: MinimalReceiptCalculator app recorded with
    //        --scope io.quasient.foobar.** --scope-default skip
    // When: pal log index --verbose is run on the resulting WAL
    // Then: Field get/put operations for JDK classes (java.*, javax.*) are absent
    //       Field get/put operations for app classes (io.quasient.foobar.*) are present

    // TODO(#1278): Implement test logic
    fail("Not yet implemented");
  }
}
