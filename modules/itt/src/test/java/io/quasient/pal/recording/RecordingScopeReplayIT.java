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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Integration tests verifying that replay with the same recording scope flags as the original
 * recording produces zero divergences, and that scope mismatch is handled gracefully.
 *
 * <p>Records a WAL using {@code MinimalReceiptCalculator} with various {@code --scope} flags, then
 * replays the WAL with the same flags and verifies deterministic replay correctness. Parameterized
 * over Chronicle Queue and Kafka backends following the {@code ReplayIT} pattern.
 *
 * <p>These are end-to-end acceptance tests for the recording scope + replay compatibility, using
 * real PAL infrastructure (WAL, weaving, dispatch, replay).
 *
 * @see io.quasient.pal.core.recording.RecordingScope
 */
@RunWith(Parameterized.class)
@SuppressWarnings("UnusedVariable") // Fields used by implementation in #1279
public class RecordingScopeReplayIT extends AbstractCliIT {

  /** Fully qualified name of the test application used for recording and replay. */
  private static final String MAIN_CLASS =
      "io.quasient.foobar.apps.quantized.replay.MinimalReceiptCalculator";

  /** The WAL backend type for this test run ("chronicle" or "kafka"). */
  private final String backend;

  /** WAL spec created fresh for each test method. */
  private String walSpec;

  /**
   * Creates a parameterized test instance for the given backend.
   *
   * @param backend the WAL backend type ("chronicle" or "kafka")
   */
  public RecordingScopeReplayIT(String backend) {
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
   * Creates a fresh WAL spec for each test method.
   *
   * <p>For Chronicle, returns a {@code file:}-prefixed path and registers it for cleanup. For
   * Kafka, returns a unique topic name.
   */
  @Before
  public void setUp() {
    walSpec = createWalSpec("scope-replay-");
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
   * Verifies that recording with {@code --scope io.quasient.foobar.**} and {@code --scope-default
   * skip}, then replaying with the same scope flags, produces zero divergences.
   *
   * <p>This is the fundamental correctness test: same scope on record and replay must yield a clean
   * replay with exit code 0 and no DIVERGENCE output.
   */
  @Test
  @Ignore("Awaiting implementation in #1279")
  public void recordAndReplayWithSameScope() throws Exception {
    // Given: MinimalReceiptCalculator recorded with
    //        --scope io.quasient.foobar.** --scope-default skip
    // When: The WAL is replayed with the same --scope and --scope-default flags
    // Then: Exit code is 0
    //       No DIVERGENCE is reported in stdout or stderr

    // TODO(#1279): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that recording with {@code --scope-exclude java.lang.String.**} and replaying with the
   * same {@code --scope-exclude} flag produces zero divergences.
   *
   * <p>Exclude-based scoping must also produce a WAL that replays cleanly when the same exclude
   * rules are applied during replay.
   */
  @Test
  @Ignore("Awaiting implementation in #1279")
  public void recordAndReplayWithScopeExclude() throws Exception {
    // Given: MinimalReceiptCalculator recorded with
    //        --scope-exclude java.lang.String.**
    // When: The WAL is replayed with the same --scope-exclude flag
    // Then: Exit code is 0
    //       No DIVERGENCE is reported in stdout or stderr

    // TODO(#1279): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that recording with {@code --scope io.quasient.foobar.**}, {@code --scope-io}, and
   * {@code --scope-default skip}, then replaying with the same flags plus {@code
   * --replay-shield-io}, produces a clean replay.
   *
   * <p>The {@code --scope-io} flag records I/O boundary operations to the WAL, and {@code
   * --replay-shield-io} stubs those operations during replay. The combination must be compatible.
   */
  @Test
  @Ignore("Awaiting implementation in #1279")
  public void recordAndReplayWithScopeIo() throws Exception {
    // Given: MinimalReceiptCalculator recorded with
    //        --scope io.quasient.foobar.** --scope-io --scope-default skip
    // When: The WAL is replayed with the same --scope flags plus --replay-shield-io
    // Then: Exit code is 0

    // TODO(#1279): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that replaying a scoped WAL with different application input detects a value mismatch
   * for in-scope operations.
   *
   * <p>Records with input "milk:1" and replays with input "bread:2". Since the in-scope operations
   * produce different values, replay must detect VALUE_MISMATCH and exit with code 2.
   */
  @Test
  @Ignore("Awaiting implementation in #1279")
  public void replayWithDifferentInputDetectsValueMismatch() throws Exception {
    // Given: MinimalReceiptCalculator recorded with
    //        --scope io.quasient.foobar.** --scope-default skip
    //        and application input "milk:1"
    // When: The WAL is replayed with the same --scope flags
    //       but different application input "bread:2"
    // Then: Exit code is 2 (VALUE_MISMATCH) for in-scope operations

    // TODO(#1279): Implement test logic
    fail("Not yet implemented");
  }
}
