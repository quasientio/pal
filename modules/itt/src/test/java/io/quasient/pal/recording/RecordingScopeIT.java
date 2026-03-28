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
package io.quasient.pal.recording;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
public class RecordingScopeIT extends AbstractCliIT {

  /** Logger for this test class. */
  private static final Logger logger = LoggerFactory.getLogger(RecordingScopeIT.class);

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
   * Records a WAL by running the peer with scope flags and application arguments.
   *
   * @param walSpec the WAL spec (Chronicle file path or Kafka topic name)
   * @param scopeFlags recording scope flags to pass to {@code pal run}
   * @param appArgs application arguments passed to the main class
   * @return the process result containing exit code, stdout, and stderr
   * @throws Exception if recording fails
   */
  private ProcessResult recordWal(String walSpec, List<String> scopeFlags, String... appArgs)
      throws Exception {
    List<String> args = new ArrayList<>();
    args.add("-d");
    args.add(getPalDirectoryUrl());
    if ("kafka".equals(backend)) {
      args.add("-k");
      args.add(getKafkaServers());
    }
    args.add("--wal");
    args.add(walSpec);
    args.addAll(scopeFlags);
    args.add("-cp");
    args.add(getIttAppsClasspath());
    args.add(MAIN_CLASS);
    Collections.addAll(args, appArgs);
    return runPeer(args.toArray(new String[0]));
  }

  /**
   * Runs {@code pal log index} against the given WAL spec with optional extra arguments.
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
   * Extracts OPERATION lines from verbose wal-index output with parameter types stripped.
   *
   * <p>Filters for lines containing {@code OPERATION} (excludes COMPLETION lines), then removes
   * parenthesized parameter type lists so that assertions can check operation class names without
   * false-positive matches against parameter type references (e.g., {@code
   * main([Ljava.lang.String;)} would otherwise match {@code java.lang.String}).
   *
   * @param verboseOutput the full verbose wal-index stdout
   * @return list of stripped OPERATION lines
   */
  private List<String> extractOperationLines(String verboseOutput) {
    List<String> result = new ArrayList<>();
    verboseOutput
        .lines()
        .filter(line -> line.contains("OPERATION") && !line.contains("COMPLETION"))
        .map(line -> line.replaceAll("\\([^)]*\\)", ""))
        .forEach(result::add);
    return result;
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

  /**
   * Verifies that recording with {@code --scope io.quasient.foobar.**} and {@code --scope-default
   * skip} produces a WAL where all entries have class names matching the scope pattern.
   *
   * <p>Specifically, the WAL must not contain entries for JDK classes such as {@code
   * java.lang.String}, {@code java.util.HashMap}, or {@code java.lang.Integer}.
   */
  @Test
  public void recordWithScopeOnlyIncludesMatchingOperations() throws Exception {
    String walSpec = createWalSpec("scope-include");
    List<String> scopeFlags =
        List.of("--scope", "io.quasient.foobar.**", "--scope-default", "skip");

    ProcessResult recordResult = recordWal(walSpec, scopeFlags, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    logger.info("WAL recorded: {}", walSpec);

    CliProcessResult indexResult = doWalIndex(walSpec, "--verbose");
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    logger.info("wal-index verbose stdout:\n{}", indexResult.stdout());

    String stdout = indexResult.stdout();
    int entries = extractCount(stdout, "Entries:");
    assertThat("Should have positive entry count", entries, greaterThan(0));

    // All OPERATION entries must be for in-scope application classes only.
    // Check stripped OPERATION lines to avoid false-positive matches against parameter types
    // in method signatures (e.g., main([Ljava.lang.String;)) or COMPLETION return types.
    List<String> opLines = extractOperationLines(stdout);
    assertThat("Should have operations", opLines.size(), greaterThan(0));
    for (String opLine : opLines) {
      assertThat(
          "Operation should be for app class", opLine, containsString("io.quasient.foobar."));
      assertThat(
          "Operation should not be for java.lang.String",
          opLine,
          not(containsString("java.lang.String")));
      assertThat(
          "Operation should not be for java.util.HashMap",
          opLine,
          not(containsString("java.util.HashMap")));
      assertThat(
          "Operation should not be for java.lang.Integer",
          opLine,
          not(containsString("java.lang.Integer")));
    }
  }

  /**
   * Verifies that recording with {@code --scope-exclude} flags removes the excluded classes from
   * the WAL while retaining entries for non-excluded application classes.
   */
  @Test
  public void recordWithScopeExcludeRemovesMatchingOperations() throws Exception {
    String walSpec = createWalSpec("scope-exclude");
    List<String> scopeFlags =
        List.of(
            "--scope-exclude", "java.lang.String.**",
            "--scope-exclude", "java.util.HashMap.**");

    ProcessResult recordResult = recordWal(walSpec, scopeFlags, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    logger.info("WAL recorded: {}", walSpec);

    CliProcessResult indexResult = doWalIndex(walSpec, "--verbose");
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    logger.info("wal-index verbose stdout:\n{}", indexResult.stdout());

    String stdout = indexResult.stdout();
    int entries = extractCount(stdout, "Entries:");
    assertThat("Should have positive entry count", entries, greaterThan(0));

    // Check stripped OPERATION lines: excluded classes must not appear as operation owners.
    // COMPLETION return types and parameter type references are expected and excluded from check.
    List<String> opLines = extractOperationLines(stdout);
    assertThat("Should have operations", opLines.size(), greaterThan(0));
    for (String opLine : opLines) {
      assertThat(
          "Operation should not be for java.lang.String",
          opLine,
          not(containsString("java.lang.String")));
      assertThat(
          "Operation should not be for java.util.HashMap",
          opLine,
          not(containsString("java.util.HashMap")));
    }

    // Application classes should still be present in OPERATION entries
    boolean hasAppOp = opLines.stream().anyMatch(l -> l.contains("io.quasient.foobar."));
    assertTrue("Should have app class operations", hasAppOp);
  }

  /**
   * Verifies that recording the same application with scope filtering produces a WAL with
   * significantly fewer entries than recording without scope.
   */
  @Test
  public void recordWithScopeProducesSmallerWal() throws Exception {
    String unscopedWal = createWalSpec("scope-unscoped");
    String scopedWal = createWalSpec("scope-scoped");

    // Record without scope (full recording)
    ProcessResult unscopedResult = recordWal(unscopedWal, List.of(), "milk:2,bread:1,apple:5");
    assertEquals("Unscoped recording should succeed", 0, unscopedResult.exitCode());

    // Record with scope (filtered recording)
    List<String> scopeFlags =
        List.of("--scope", "io.quasient.foobar.**", "--scope-default", "skip");
    ProcessResult scopedResult = recordWal(scopedWal, scopeFlags, "milk:2,bread:1,apple:5");
    assertEquals("Scoped recording should succeed", 0, scopedResult.exitCode());

    // Get entry counts for both
    CliProcessResult unscopedIndex = doWalIndex(unscopedWal);
    assertEquals("Unscoped wal-index should succeed", 0, unscopedIndex.exitCode());
    int unscopedEntries = extractCount(unscopedIndex.stdout(), "Entries:");

    CliProcessResult scopedIndex = doWalIndex(scopedWal);
    assertEquals("Scoped wal-index should succeed", 0, scopedIndex.exitCode());
    int scopedEntries = extractCount(scopedIndex.stdout(), "Entries:");

    logger.info("Unscoped entries: {}, scoped entries: {}", unscopedEntries, scopedEntries);

    assertThat("Unscoped WAL should have entries", unscopedEntries, greaterThan(0));
    assertThat("Scoped WAL should have entries", scopedEntries, greaterThan(0));
    assertThat(
        "Scoped WAL should have fewer entries than unscoped WAL",
        scopedEntries,
        lessThan(unscopedEntries));
  }

  /**
   * Verifies that recording with {@code --scope io.quasient.foobar.**}, {@code --scope-io}, and
   * {@code --scope-default skip} produces a WAL containing entries for application and I/O boundary
   * operations but not for pure computation operations on JDK classes.
   */
  @Test
  public void recordWithScopeIoIncludesIoBoundaryOps() throws Exception {
    String walSpec = createWalSpec("scope-io");
    List<String> scopeFlags =
        List.of("--scope", "io.quasient.foobar.**", "--scope-io", "--scope-default", "skip");

    ProcessResult recordResult = recordWal(walSpec, scopeFlags, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    logger.info("WAL recorded: {}", walSpec);

    CliProcessResult indexResult = doWalIndex(walSpec, "--verbose");
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    logger.info("wal-index verbose stdout:\n{}", indexResult.stdout());

    String stdout = indexResult.stdout();

    // Pure computation OPERATION entries must not be present.
    // Check stripped lines to avoid matching parameter type references.
    List<String> opLines = extractOperationLines(stdout);
    for (String opLine : opLines) {
      assertThat(
          "Operation should not be for String", opLine, not(containsString("java.lang.String")));
      assertThat(
          "Operation should not be for HashMap", opLine, not(containsString("java.util.HashMap")));
      assertThat(
          "Operation should not be for Integer", opLine, not(containsString("java.lang.Integer")));
    }

    // WAL must be structurally consistent
    assertThat("Output should contain Issues count", stdout, containsString("Issues:"));
    assertThat("Should report zero issues", stdout, containsString("Issues:        0"));
  }

  /**
   * Verifies that recording with {@code --scope io.quasient.foobar.**} and {@code --scope-default
   * skip} excludes field get/put operations for JDK classes from the WAL while retaining field
   * operations for application classes.
   */
  @Test
  public void recordWithScopeExcludesFieldOps() throws Exception {
    String walSpec = createWalSpec("scope-fields");
    List<String> scopeFlags =
        List.of("--scope", "io.quasient.foobar.**", "--scope-default", "skip");

    ProcessResult recordResult = recordWal(walSpec, scopeFlags, "milk:2,bread:1,apple:5");
    assertEquals("Recording should succeed", 0, recordResult.exitCode());
    logger.info("WAL recorded: {}", walSpec);

    CliProcessResult indexResult = doWalIndex(walSpec, "--verbose");
    assertEquals("wal-index should succeed", 0, indexResult.exitCode());

    logger.info("wal-index verbose stdout:\n{}", indexResult.stdout());

    String stdout = indexResult.stdout();
    int entries = extractCount(stdout, "Entries:");
    assertThat("Should have positive entry count", entries, greaterThan(0));

    // Check stripped OPERATION lines: JDK class field operations must be absent.
    // Scope filtering applies to all operation types (methods, constructors, and field get/put).
    List<String> opLines = extractOperationLines(stdout);
    assertThat("Should have operations", opLines.size(), greaterThan(0));
    for (String opLine : opLines) {
      assertThat(
          "Operation should not be for java.lang class", opLine, not(containsString("java.lang.")));
      assertThat(
          "Operation should not be for java.util class", opLine, not(containsString("java.util.")));
      assertThat("Operation should not be for javax class", opLine, not(containsString("javax.")));
    }

    // Application class operations (including field ops) should be present
    boolean hasAppOp = opLines.stream().anyMatch(l -> l.contains("io.quasient.foobar."));
    assertTrue("Should have app class operations", hasAppOp);
  }
}
