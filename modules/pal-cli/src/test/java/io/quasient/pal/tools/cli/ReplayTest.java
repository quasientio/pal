/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Unit tests for the {@link Replay} CLI command.
 *
 * <p>Tests cover argument parsing, validation, and the argument composition logic that builds the
 * argument array delegated to {@link io.quasient.pal.core.service.Main}.
 */
public class ReplayTest {

  // ===========================================================================
  // Helper methods
  // ===========================================================================

  /**
   * Gets a field value from an object via reflection, searching the class hierarchy.
   *
   * @param target the object from which to read the field
   * @param fieldName the name of the field to read
   * @return the field value
   */
  private static Object getField(Object target, String fieldName) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    return f.get(target);
  }

  /**
   * Finds a field by name in the given class or its superclasses.
   *
   * @param clazz the class to search
   * @param name the field name
   * @return the found Field
   * @throws NoSuchFieldException if the field is not found in the class hierarchy
   */
  private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }

  /**
   * Creates a Replay instance with fields populated via picocli parsing.
   *
   * @param args the command-line arguments to parse
   * @return a configured Replay instance
   */
  private static Replay parseReplay(String... args) {
    Replay replay = new Replay();
    new CommandLine(replay).parseArgs(args);
    return replay;
  }

  // ===========================================================================
  // Argument parsing tests
  // ===========================================================================

  /** Verifies that all required and optional fields are correctly parsed. */
  @Test
  public void testParseAllOptions() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/my-wal",
            "--divergence-policy",
            "HALT",
            "-cp",
            "target/app.jar",
            "com.example.Main",
            "arg1",
            "arg2");

    assertThat(getField(replay, "walPath"), is("file:/tmp/my-wal"));
    assertThat(getField(replay, "divergencePolicy"), is("HALT"));
    assertThat(getField(replay, "classpath"), is("target/app.jar"));
    assertThat(getField(replay, "mainClass"), is("com.example.Main"));

    @SuppressWarnings("unchecked")
    List<String> appArgs = (List<String>) getField(replay, "appArgs");
    assertThat(appArgs, is(Arrays.asList("arg1", "arg2")));
  }

  /** Verifies that the default divergence policy is WARN when not specified. */
  @Test
  public void testDefaultDivergencePolicy() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-cp", "app.jar", "com.example.Main");

    assertThat(getField(replay, "divergencePolicy"), is("WARN"));
  }

  /** Verifies that the short-form -w flag works for --wal. */
  @Test
  public void testShortWalFlag() throws Exception {
    Replay replay = parseReplay("-w", "file:/tmp/wal", "-cp", "app.jar", "com.example.Main");

    assertThat(getField(replay, "walPath"), is("file:/tmp/wal"));
  }

  /** Verifies that --classpath works as an alias for -cp. */
  @Test
  public void testLongClasspathFlag() throws Exception {
    Replay replay =
        parseReplay("--wal", "file:/tmp/wal", "--classpath", "app.jar", "com.example.Main");

    assertThat(getField(replay, "classpath"), is("app.jar"));
  }

  // ===========================================================================
  // Validation tests
  // ===========================================================================

  /** Verifies that validateInput accepts valid divergence policy values (case-insensitive). */
  @Test
  public void testValidateInputAcceptsValidPolicies() throws Exception {
    for (String policy : new String[] {"WARN", "HALT", "IGNORE", "warn", "halt", "ignore"}) {
      Replay replay =
          parseReplay(
              "--wal",
              "file:/tmp/wal",
              "--divergence-policy",
              policy,
              "-cp",
              "app.jar",
              "com.example.Main");
      replay.validateInput(); // should not throw
    }
  }

  /** Verifies that validateInput rejects invalid divergence policy values. */
  @Test
  public void testValidateInputRejectsInvalidPolicy() {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--divergence-policy",
            "INVALID",
            "-cp",
            "app.jar",
            "com.example.Main");

    RuntimeException e = assertThrows(RuntimeException.class, replay::validateInput);
    assertThat(e.getMessage(), containsString("Invalid divergence policy"));
    assertThat(e.getMessage(), containsString("INVALID"));
  }

  /** Verifies that validateInput normalizes the policy to uppercase. */
  @Test
  public void testValidateInputNormalizesPolicy() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--divergence-policy",
            "halt",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    assertThat(getField(replay, "divergencePolicy"), is("HALT"));
  }

  // ===========================================================================
  // buildMainArgs tests
  // ===========================================================================

  /** Verifies the argument array built for Main with all options including app args. */
  @Test
  public void testBuildMainArgsWithAppArgs() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/my-wal",
            "--divergence-policy",
            "HALT",
            "-cp",
            "target/app.jar",
            "com.example.Main",
            "arg1",
            "arg2");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(
        args,
        arrayContaining(
            "--replay-wal",
            "file:/tmp/my-wal",
            "--replay-divergence-policy",
            "HALT",
            "-cp",
            "target/app.jar",
            "com.example.Main",
            "arg1",
            "arg2"));
  }

  /** Verifies the argument array built for Main without app args. */
  @Test
  public void testBuildMainArgsWithoutAppArgs() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(
        args,
        arrayContaining(
            "--replay-wal",
            "file:/tmp/wal",
            "--replay-divergence-policy",
            "WARN",
            "-cp",
            "app.jar",
            "com.example.Main"));
  }

  /** Verifies that no etcd, Kafka, or intercept flags are included in the composed args. */
  @Test
  public void testBuildMainArgsNoInfrastructureFlags() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    String joined = String.join(" ", args);

    // Must not contain any infrastructure flags
    assertThat("Should not contain -d flag", joined.contains("-d "), is(false));
    assertThat("Should not contain --dir flag", joined.contains("--dir "), is(false));
    assertThat("Should not contain -k flag", joined.contains("-k "), is(false));
    assertThat(
        "Should not contain --kafka-servers flag", joined.contains("--kafka-servers "), is(false));
    assertThat(
        "Should not contain --interceptable flag", joined.contains("--interceptable"), is(false));
  }

  /** Verifies that the exit code constant for divergences is 2. */
  @Test
  public void testExitCodeDivergencesConstant() {
    assertThat(Replay.EXIT_CODE_DIVERGENCES, is(2));
  }

  /** Verifies that closeResources does not throw (no-op). */
  @Test
  public void testCloseResourcesNoOp() throws Exception {
    Replay replay = new Replay();
    replay.closeResources(); // should not throw
  }

  /** Verifies that initialize does not throw (no-op). */
  @Test
  public void testInitializeNoOp() throws Exception {
    Replay replay = new Replay();
    replay.initialize(); // should not throw
  }
}
