/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Permission;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for Kafka/Chronicle flag consistency validation in {@link Main}.
 *
 * <p>Validates that incompatible combinations of Kafka-specific flags ({@code --kafka-servers}) and
 * Chronicle-specific flags ({@code --chronicle-base-dir}) with log backend types are properly
 * detected and reported via fatal exit or warning messages.
 */
@SuppressWarnings("removal") // SecurityManager deprecated but still functional for testing
public class MainKafkaChronicleConsistencyTest {

  /** Original System.err stream, saved for restoration after tests. */
  private PrintStream originalSystemErr;

  /** Captured System.err output for verification. */
  private ByteArrayOutputStream capturedErr;

  /** Original SecurityManager, saved for restoration after tests. */
  private SecurityManager originalSecurityManager;

  @Before
  public void setUp() {
    originalSystemErr = System.err;
    capturedErr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(capturedErr));

    originalSecurityManager = System.getSecurityManager();
    System.setSecurityManager(new ExitTrappingSecurityManager());
  }

  @After
  public void tearDown() {
    System.setErr(originalSystemErr);
    System.setSecurityManager(originalSecurityManager);
  }

  // ===== Reflection helpers =====

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = Main.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  private static void setReplayDefaults(Main m) throws Exception {
    setField(m, "replayDivergencePolicy", "WARN");
    setField(m, "replayThreading", "ordered");
    setField(m, "replayDelay", "0");
  }

  private static int callValidateAndTrapExit(Main m) throws Exception {
    Method validate = Main.class.getDeclaredMethod("validateInput");
    validate.setAccessible(true);
    try {
      validate.invoke(m);
    } catch (InvocationTargetException e) {
      if (e.getCause() instanceof ExitTrappedException) {
        return ((ExitTrappedException) e.getCause()).getExitCode();
      }
      throw e;
    }
    return -1; // no exit
  }

  private static void callValidateAndAddMisc(Main m) throws Exception {
    Method validate = Main.class.getDeclaredMethod("validateInput");
    validate.setAccessible(true);
    validate.invoke(m);
    Method addMisc = Main.class.getDeclaredMethod("addMiscProperties");
    addMisc.setAccessible(true);
    addMisc.invoke(m);
  }

  // ===== Fatal exit: --kafka-servers with all-Chronicle logs =====

  /**
   * Tests that --kafka-servers with a Chronicle-only WAL triggers a fatal exit.
   *
   * <p>When the WAL uses a Chronicle queue (file: path) and no Kafka-based log is configured,
   * specifying --kafka-servers is an inconsistency that should be rejected.
   */
  @Test
  public void validate_kafkaServersWithChronicleWalOnly_fatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "wal", "file:/tmp/wal");
    setField(m, "kafkaServers", "localhost:9092");

    int exitCode = callValidateAndTrapExit(m);
    assertThat(exitCode, is(PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES.getCode()));
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, containsString("--kafka-servers"));
    assertThat(errOutput, containsString("Chronicle"));
  }

  /** Tests that --kafka-servers with both WAL and source-log as Chronicle triggers a fatal exit. */
  @Test
  public void validate_kafkaServersWithAllChronicleLogs_fatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "wal", "file:/tmp/wal");
    setField(m, "sourceLog", "file:/tmp/source");
    setField(m, "kafkaServers", "localhost:9092");

    int exitCode = callValidateAndTrapExit(m);
    assertThat(exitCode, is(PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES.getCode()));
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, containsString("--kafka-servers"));
    assertThat(errOutput, containsString("Chronicle"));
  }

  /**
   * Tests that --kafka-servers with --log using a Chronicle path triggers a fatal exit.
   *
   * <p>The --log flag sets both source-log and WAL to the same value. When that value is a
   * Chronicle path, --kafka-servers is inconsistent.
   */
  @Test
  public void validate_kafkaServersWithChronicleLogShorthand_fatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "log", "file:/tmp/mylog");
    setField(m, "kafkaServers", "localhost:9092");

    int exitCode = callValidateAndTrapExit(m);
    assertThat(exitCode, is(PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES.getCode()));
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, containsString("--kafka-servers"));
  }

  // ===== Fatal exit: --chronicle-base-dir with all-Kafka logs =====

  /** Tests that --chronicle-base-dir with a Kafka-only WAL triggers a fatal exit. */
  @Test
  public void validate_chronicleBaseDirWithKafkaWalOnly_fatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "wal", "my-kafka-topic");
    setField(m, "kafkaServers", "localhost:9092");
    setField(m, "chronicleBaseDir", "/tmp/chronicle");

    int exitCode = callValidateAndTrapExit(m);
    assertThat(exitCode, is(PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES.getCode()));
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, containsString("--chronicle-base-dir"));
    assertThat(errOutput, containsString("Kafka"));
  }

  /**
   * Tests that --chronicle-base-dir with both WAL and source-log as Kafka triggers a fatal exit.
   */
  @Test
  public void validate_chronicleBaseDirWithAllKafkaLogs_fatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "wal", "my-kafka-wal");
    setField(m, "sourceLog", "my-kafka-source");
    setField(m, "kafkaServers", "localhost:9092");
    setField(m, "chronicleBaseDir", "/tmp/chronicle");

    int exitCode = callValidateAndTrapExit(m);
    assertThat(exitCode, is(PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES.getCode()));
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, containsString("--chronicle-base-dir"));
    assertThat(errOutput, containsString("Kafka"));
  }

  // ===== Warning: mixed backends =====

  /** Tests that source-log Kafka + WAL Chronicle prints a warning but does not exit. */
  @Test
  public void validate_mixedBackends_sourceKafkaWalChronicle_printsWarning() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "sourceLog", "my-kafka-source");
    setField(m, "wal", "file:/tmp/wal");
    setField(m, "kafkaServers", "localhost:9092");

    int exitCode = callValidateAndTrapExit(m);
    assertThat("Should not fatal exit for mixed backends", exitCode, is(-1));
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, containsString("WARNING"));
    assertThat(errOutput, containsString("different backends"));
  }

  /** Tests that source-log Chronicle + WAL Kafka prints a warning but does not exit. */
  @Test
  public void validate_mixedBackends_sourceChronicleWalKafka_printsWarning() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "sourceLog", "file:/tmp/source");
    setField(m, "wal", "my-kafka-wal");
    setField(m, "kafkaServers", "localhost:9092");

    int exitCode = callValidateAndTrapExit(m);
    assertThat("Should not fatal exit for mixed backends", exitCode, is(-1));
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, containsString("WARNING"));
    assertThat(errOutput, containsString("different backends"));
  }

  // ===== No error for valid configurations =====

  /**
   * Tests that --kafka-servers with at least one Kafka log does not trigger an error, even when the
   * other log uses Chronicle (mixed configuration).
   */
  @Test
  public void validate_kafkaServersWithMixedLogs_noFatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "sourceLog", "my-kafka-source");
    setField(m, "wal", "file:/tmp/wal");
    setField(m, "kafkaServers", "localhost:9092");

    int exitCode = callValidateAndTrapExit(m);
    assertThat("Should not fatal exit when at least one log uses Kafka", exitCode, is(-1));
  }

  /** Tests that --chronicle-base-dir with at least one Chronicle log does not trigger an error. */
  @Test
  public void validate_chronicleBaseDirWithMixedLogs_noFatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "sourceLog", "my-kafka-source");
    setField(m, "wal", "file:/tmp/wal");
    setField(m, "kafkaServers", "localhost:9092");
    setField(m, "chronicleBaseDir", "/tmp/chronicle");

    int exitCode = callValidateAndTrapExit(m);
    assertThat("Should not fatal exit when at least one log uses Chronicle", exitCode, is(-1));
  }

  /**
   * Tests that --kafka-servers without any logs configured does not trigger an error.
   *
   * <p>The user may have kafka-servers set via environment variable without using any logs.
   */
  @Test
  public void validate_kafkaServersNoLogs_noFatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "kafkaServers", "localhost:9092");

    int exitCode = callValidateAndTrapExit(m);
    assertThat("Should not fatal exit when no logs configured", exitCode, is(-1));
  }

  /** Tests that --chronicle-base-dir without any logs configured does not trigger an error. */
  @Test
  public void validate_chronicleBaseDirNoLogs_noFatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "chronicleBaseDir", "/tmp/chronicle");

    int exitCode = callValidateAndTrapExit(m);
    assertThat("Should not fatal exit when no logs configured", exitCode, is(-1));
  }

  /** Tests that matching backends (both Kafka) with kafka-servers does not produce a warning. */
  @Test
  public void validate_bothKafkaLogs_noWarning() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "sourceLog", "my-kafka-source");
    setField(m, "wal", "my-kafka-wal");
    setField(m, "kafkaServers", "localhost:9092");

    callValidateAndAddMisc(m);
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, not(containsString("different backends")));
  }

  /** Tests that matching backends (both Chronicle) does not produce a mixed-backend warning. */
  @Test
  public void validate_bothChronicleLogs_noMixedWarning() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "sourceLog", "file:/tmp/source");
    setField(m, "wal", "file:/tmp/wal");

    callValidateAndAddMisc(m);
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, not(containsString("different backends")));
  }

  // ===== Replay mode consistency checks =====

  /** Tests that --replay-wal with a Chronicle path and --kafka-servers triggers a fatal exit. */
  @Test
  public void validate_replayWalChronicleWithKafkaServers_fatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "replayWalPath", "file:/tmp/replay-wal");
    setField(m, "kafkaServers", "localhost:9092");
    setReplayDefaults(m);

    int exitCode = callValidateAndTrapExit(m);
    assertThat(exitCode, is(PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES.getCode()));
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, containsString("--kafka-servers"));
    assertThat(errOutput, containsString("--replay-wal"));
  }

  /** Tests that --replay-wal with a Kafka topic and --chronicle-base-dir triggers a fatal exit. */
  @Test
  public void validate_replayWalKafkaWithChronicleBaseDir_fatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "replayWalPath", "my-replay-topic");
    setField(m, "kafkaServers", "localhost:9092");
    setField(m, "chronicleBaseDir", "/tmp/chronicle");
    setReplayDefaults(m);

    int exitCode = callValidateAndTrapExit(m);
    assertThat(exitCode, is(PeerException.FatalCode.ERROR_VALIDATING_PROPERTIES.getCode()));
    String errOutput = capturedErr.toString(UTF_8);
    assertThat(errOutput, containsString("--chronicle-base-dir"));
    assertThat(errOutput, containsString("--replay-wal"));
  }

  /**
   * Tests that --replay-wal with a Kafka topic and --kafka-servers (no chronicle-base-dir) does not
   * trigger an error.
   */
  @Test
  public void validate_replayWalKafkaWithKafkaServers_noFatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "replayWalPath", "my-replay-topic");
    setField(m, "kafkaServers", "localhost:9092");
    setReplayDefaults(m);

    int exitCode = callValidateAndTrapExit(m);
    assertThat("Should not fatal exit for valid Kafka replay", exitCode, is(-1));
  }

  /**
   * Tests that --replay-wal with a Chronicle path and --chronicle-base-dir (no kafka-servers) does
   * not trigger an error.
   */
  @Test
  public void validate_replayWalChronicleWithChronicleBaseDir_noFatalExit() throws Exception {
    Main m = new Main();
    setField(m, "uuid", UUID.randomUUID());
    setField(m, "replayWalPath", "file:/tmp/replay-wal");
    setField(m, "chronicleBaseDir", "/tmp/chronicle");
    setReplayDefaults(m);

    int exitCode = callValidateAndTrapExit(m);
    assertThat("Should not fatal exit for valid Chronicle replay", exitCode, is(-1));
  }

  // ===== Helper classes for System.exit() trapping =====

  /** Exception thrown when System.exit() is called during testing. */
  private static class ExitTrappedException extends SecurityException {
    private final int exitCode;

    ExitTrappedException(int exitCode) {
      super("System.exit(" + exitCode + ") was trapped");
      this.exitCode = exitCode;
    }

    int getExitCode() {
      return exitCode;
    }
  }

  /** SecurityManager that traps System.exit() calls. */
  @SuppressWarnings("removal")
  private static class ExitTrappingSecurityManager extends SecurityManager {
    @Override
    public void checkPermission(Permission perm) {
      // Allow all
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
      // Allow all
    }

    @Override
    public void checkExit(int status) {
      throw new ExitTrappedException(status);
    }
  }
}
