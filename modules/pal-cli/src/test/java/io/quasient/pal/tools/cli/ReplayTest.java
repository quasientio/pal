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
package io.quasient.pal.tools.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
   * Sets a field value on an object via reflection, searching the class hierarchy.
   *
   * @param target the object on which to set the field
   * @param fieldName the name of the field to set
   * @param value the value to set
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    f.set(target, value);
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

  /** Verifies that all required and optional fields are correctly parsed and validated. */
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
    replay.validateInput(); // mainClass and appArgs are set during validation

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
            "--replay-threading",
            "ordered",
            "-cp",
            "target/app.jar",
            "--rpc-default-action",
            "ALLOW",
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
            "--replay-threading",
            "ordered",
            "-cp",
            "app.jar",
            "--rpc-default-action",
            "ALLOW",
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

  // ===========================================================================
  // Kafka and PalDirectory support tests
  // ===========================================================================

  /** Verifies that the --kafka-servers option is parsed correctly via picocli. */
  @Test
  public void testParseKafkaServersOption() throws Exception {
    Replay replay =
        parseReplay(
            "--kafka-servers", "localhost:29092", "-w", "my-topic", "-cp", "app.jar", "MyMain");

    assertThat(getField(replay, "kafkaServers"), is("localhost:29092"));
  }

  /** Verifies that the short -k form of --kafka-servers is parsed correctly. */
  @Test
  public void testParseShortKafkaServersOption() throws Exception {
    Replay replay =
        parseReplay("-k", "localhost:29092", "-w", "my-topic", "-cp", "app.jar", "MyMain");

    assertThat(getField(replay, "kafkaServers"), is("localhost:29092"));
  }

  /** Verifies that buildMainArgs includes -k when Kafka servers are specified. */
  @Test
  public void testBuildMainArgsWithKafka() throws Exception {
    Replay replay =
        parseReplay("-k", "localhost:29092", "--wal", "my-topic", "-cp", "app.jar", "MyMain");
    replay.validateInput();
    setField(replay, "resolvedKafkaServers", "localhost:29092");

    String[] args = replay.buildMainArgs();
    assertThat(
        args,
        arrayContaining(
            "--replay-wal",
            "my-topic",
            "--replay-divergence-policy",
            "WARN",
            "--replay-threading",
            "ordered",
            "-k",
            "localhost:29092",
            "-cp",
            "app.jar",
            "--rpc-default-action",
            "ALLOW",
            "MyMain"));
  }

  /** Verifies that buildMainArgs excludes -k when using Chronicle WAL without Kafka. */
  @Test
  public void testBuildMainArgsWithChronicleNoKafka() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/my-wal", "-cp", "app.jar", "MyMain");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, not(hasItemInArray("-k")));
    assertThat(args, hasItemInArray("file:/tmp/my-wal"));
  }

  /** Verifies that buildMainArgs includes resolved Kafka servers from PalDirectory. */
  @Test
  public void testBuildMainArgsWithPalDirectory() throws Exception {
    Replay replay = parseReplay("--wal", "my-topic", "-cp", "app.jar", "MyMain");
    setField(replay, "resolvedKafkaServers", "broker1:9092,broker2:9092");

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("-k"));
    assertThat(args, hasItemInArray("broker1:9092,broker2:9092"));
  }

  /** Verifies that validateInput rejects a Kafka WAL topic without -k or -d. */
  @Test
  public void testValidateInputRejectsKafkaWithoutServersAndWithoutDirectory() {
    Replay replay = parseReplay("--wal", "my-topic", "-cp", "app.jar", "MyMain");

    RuntimeException e = assertThrows(RuntimeException.class, replay::validateInput);
    assertThat(e.getMessage(), containsString("--kafka-servers (-k)"));
    assertThat(e.getMessage(), containsString("-d"));
  }

  /** Verifies that validateInput accepts a Kafka WAL topic when -k is provided. */
  @Test
  public void testValidateInputAcceptsKafkaWithServers() {
    Replay replay =
        parseReplay("-k", "localhost:29092", "--wal", "my-topic", "-cp", "app.jar", "MyMain");
    replay.validateInput();
  }

  /** Verifies that validateInput accepts a Chronicle WAL without Kafka servers. */
  @Test
  public void testValidateInputAcceptsChronicleWithoutKafka() {
    Replay replay = parseReplay("--wal", "file:/tmp/my-wal", "-cp", "app.jar", "MyMain");
    replay.validateInput();
  }

  // ===========================================================================
  // --replay-threading option tests
  // ===========================================================================

  /** Verifies that the default value for --replay-threading is "ordered". */
  @Test
  public void testDefaultReplayThreading() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-cp", "app.jar", "com.example.Main");

    assertThat(getField(replay, "replayThreading"), is("ordered"));
  }

  /** Verifies that --replay-threading can be set to "unordered". */
  @Test
  public void testReplayThreadingUnordered() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--threading",
            "unordered",
            "-cp",
            "app.jar",
            "com.example.Main");

    assertThat(getField(replay, "replayThreading"), is("unordered"));
  }

  /** Verifies that buildMainArgs includes --replay-threading with default value. */
  @Test
  public void testBuildMainArgsIncludesReplayThreadingDefault() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--replay-threading"));
    assertThat(args, hasItemInArray("ordered"));
  }

  /** Verifies that buildMainArgs includes --replay-threading with explicit unordered value. */
  @Test
  public void testBuildMainArgsIncludesReplayThreadingUnordered() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--threading",
            "unordered",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--replay-threading"));
    assertThat(args, hasItemInArray("unordered"));
  }

  /** Verifies the full argument array with --replay-threading included. */
  @Test
  public void testBuildMainArgsWithReplayThreadingFullArgs() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/my-wal",
            "--divergence-policy",
            "HALT",
            "--threading",
            "unordered",
            "-cp",
            "target/app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(
        args,
        arrayContaining(
            "--replay-wal",
            "file:/tmp/my-wal",
            "--replay-divergence-policy",
            "HALT",
            "--replay-threading",
            "unordered",
            "-cp",
            "target/app.jar",
            "--rpc-default-action",
            "ALLOW",
            "com.example.Main"));
  }

  // ===========================================================================
  // -jar option tests
  // ===========================================================================

  /** Verifies that the -jar option is parsed correctly. */
  @Test
  public void testParseJarOption() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-jar", "myapp.jar");
    replay.validateInput();

    assertThat(getField(replay, "jarFile"), is("myapp.jar"));
  }

  /** Verifies that -jar works with app arguments. */
  @Test
  public void testParseJarOptionWithAppArgs() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-jar", "myapp.jar", "arg1", "arg2");
    replay.validateInput(); // appArgs are set during validation when using -jar

    assertThat(getField(replay, "jarFile"), is("myapp.jar"));
    @SuppressWarnings("unchecked")
    List<String> appArgs = (List<String>) getField(replay, "appArgs");
    assertThat(appArgs, is(Arrays.asList("arg1", "arg2")));
  }

  /** Verifies that -jar works with optional -cp for additional classpath entries. */
  @Test
  public void testParseJarOptionWithClasspath() throws Exception {
    Replay replay =
        parseReplay("--wal", "file:/tmp/wal", "-cp", "lib/extra.jar", "-jar", "myapp.jar");
    replay.validateInput();

    assertThat(getField(replay, "jarFile"), is("myapp.jar"));
    assertThat(getField(replay, "classpath"), is("lib/extra.jar"));
  }

  /** Verifies that validateInput accepts -jar without -cp. */
  @Test
  public void testValidateInputAcceptsJarWithoutClasspath() {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-jar", "myapp.jar");
    replay.validateInput(); // should not throw
  }

  /** Verifies that validateInput rejects missing both mainClass and -jar. */
  @Test
  public void testValidateInputRejectsMissingMainClassAndJar() {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-cp", "app.jar");

    RuntimeException e = assertThrows(RuntimeException.class, replay::validateInput);
    assertThat(e.getMessage(), containsString("main class or -jar must be specified"));
  }

  /** Verifies that validateInput rejects mainClass without -cp (when not using -jar). */
  @Test
  public void testValidateInputRejectsMainClassWithoutClasspath() {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "com.example.Main");

    RuntimeException e = assertThrows(RuntimeException.class, replay::validateInput);
    assertThat(e.getMessage(), containsString("Classpath"));
    assertThat(e.getMessage(), containsString("required"));
  }

  /** Verifies that buildMainArgs includes -jar when specified. */
  @Test
  public void testBuildMainArgsWithJar() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/my-wal", "-jar", "myapp.jar");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(
        args,
        arrayContaining(
            "--replay-wal",
            "file:/tmp/my-wal",
            "--replay-divergence-policy",
            "WARN",
            "--replay-threading",
            "ordered",
            "--rpc-default-action",
            "ALLOW",
            "-jar",
            "myapp.jar"));
  }

  /** Verifies that buildMainArgs includes -jar with -cp when both are specified. */
  @Test
  public void testBuildMainArgsWithJarAndClasspath() throws Exception {
    Replay replay =
        parseReplay("--wal", "file:/tmp/my-wal", "-cp", "lib/extra.jar", "-jar", "myapp.jar");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(
        args,
        arrayContaining(
            "--replay-wal",
            "file:/tmp/my-wal",
            "--replay-divergence-policy",
            "WARN",
            "--replay-threading",
            "ordered",
            "-cp",
            "lib/extra.jar",
            "--rpc-default-action",
            "ALLOW",
            "-jar",
            "myapp.jar"));
  }

  /** Verifies that buildMainArgs includes -jar with app arguments. */
  @Test
  public void testBuildMainArgsWithJarAndAppArgs() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/my-wal", "-jar", "myapp.jar", "arg1", "arg2");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(
        args,
        arrayContaining(
            "--replay-wal",
            "file:/tmp/my-wal",
            "--replay-divergence-policy",
            "WARN",
            "--replay-threading",
            "ordered",
            "--rpc-default-action",
            "ALLOW",
            "-jar",
            "myapp.jar",
            "arg1",
            "arg2"));
  }

  /** Verifies that buildMainArgs includes -jar with Kafka servers. */
  @Test
  public void testBuildMainArgsWithJarAndKafka() throws Exception {
    Replay replay = parseReplay("-k", "localhost:29092", "--wal", "my-topic", "-jar", "myapp.jar");
    replay.validateInput();
    setField(replay, "resolvedKafkaServers", "localhost:29092");

    String[] args = replay.buildMainArgs();
    assertThat(
        args,
        arrayContaining(
            "--replay-wal",
            "my-topic",
            "--replay-divergence-policy",
            "WARN",
            "--replay-threading",
            "ordered",
            "-k",
            "localhost:29092",
            "--rpc-default-action",
            "ALLOW",
            "-jar",
            "myapp.jar"));
  }

  /**
   * Verifies that buildMainArgs treats positional args as app args when -jar is specified, not as
   * mainClass.
   */
  @Test
  public void testBuildMainArgsWithJarTreatsPositionalArgsAsAppArgs() throws Exception {
    // When -jar is specified, positional args like "SomeClass" become app arguments,
    // not the main class (which is read from the JAR manifest by Main)
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-jar", "myapp.jar", "SomeClass");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("-jar"));
    assertThat(args, hasItemInArray("myapp.jar"));
    assertThat(args, hasItemInArray("SomeClass")); // treated as app arg, not main class
  }

  // ===========================================================================
  // Side-effect shielding option tests
  // ===========================================================================

  /** Verifies that --policy is parsed correctly. */
  @Test
  public void testParseReplayPolicyOption() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--policy",
            "/tmp/policy.yaml",
            "-cp",
            "app.jar",
            "com.example.Main");

    assertThat(getField(replay, "replayPolicyPath"), is("/tmp/policy.yaml"));
  }

  /** Verifies that --shield-io is parsed as boolean. */
  @Test
  public void testParseShieldIoOption() throws Exception {
    Replay replay =
        parseReplay("--wal", "file:/tmp/wal", "--shield-io", "-cp", "app.jar", "com.example.Main");

    assertThat(getField(replay, "shieldIo"), is(true));
  }

  /** Verifies that --re-execute parses comma-separated patterns. */
  @Test
  public void testParseReExecutePatterns() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--re-execute",
            "com.example.**,com.other.**",
            "-cp",
            "app.jar",
            "com.example.Main");

    String[] patterns = (String[]) getField(replay, "reExecutePatterns");
    assertThat(patterns, arrayContaining("com.example.**", "com.other.**"));
  }

  /** Verifies that --stub parses comma-separated patterns. */
  @Test
  public void testParseStubPatterns() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--stub",
            "java.io.**,java.net.**",
            "-cp",
            "app.jar",
            "com.example.Main");

    String[] patterns = (String[]) getField(replay, "stubPatterns");
    assertThat(patterns, arrayContaining("java.io.**", "java.net.**"));
  }

  /** Verifies that --stub-all-else is parsed as boolean. */
  @Test
  public void testParseStubAllElseOption() throws Exception {
    Replay replay =
        parseReplay(
            "--wal", "file:/tmp/wal", "--stub-all-else", "-cp", "app.jar", "com.example.Main");

    assertThat(getField(replay, "stubAllElse"), is(true));
  }

  /** Verifies that --force-stub is parsed as boolean. */
  @Test
  public void testParseForceStubOption() throws Exception {
    Replay replay =
        parseReplay("--wal", "file:/tmp/wal", "--force-stub", "-cp", "app.jar", "com.example.Main");

    assertThat(getField(replay, "forceStub"), is(true));
  }

  /** Verifies that buildMainArgs includes --replay-policy when specified. */
  @Test
  public void testBuildMainArgsWithReplayPolicy() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--policy",
            "/tmp/policy.yaml",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--replay-policy"));
    assertThat(args, hasItemInArray("/tmp/policy.yaml"));
  }

  /** Verifies that buildMainArgs includes --replay-shield-io when --shield-io is set. */
  @Test
  public void testBuildMainArgsWithShieldIo() throws Exception {
    Replay replay =
        parseReplay("--wal", "file:/tmp/wal", "--shield-io", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--replay-shield-io"));
  }

  /** Verifies that buildMainArgs includes --replay-re-execute with joined patterns. */
  @Test
  public void testBuildMainArgsWithReExecutePatterns() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--re-execute",
            "com.example.**,com.other.**",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--replay-re-execute"));
    assertThat(args, hasItemInArray("com.example.**,com.other.**"));
  }

  /** Verifies that buildMainArgs includes --replay-stub with joined patterns. */
  @Test
  public void testBuildMainArgsWithStubPatterns() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--stub",
            "java.io.**,java.net.**",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--replay-stub"));
    assertThat(args, hasItemInArray("java.io.**,java.net.**"));
  }

  /** Verifies that buildMainArgs includes --replay-stub-all-else when --stub-all-else is set. */
  @Test
  public void testBuildMainArgsWithStubAllElse() throws Exception {
    Replay replay =
        parseReplay(
            "--wal", "file:/tmp/wal", "--stub-all-else", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--replay-stub-all-else"));
  }

  /** Verifies that buildMainArgs includes --replay-force-stub when --force-stub is set. */
  @Test
  public void testBuildMainArgsWithForceStub() throws Exception {
    Replay replay =
        parseReplay("--wal", "file:/tmp/wal", "--force-stub", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--replay-force-stub"));
  }

  /** Verifies that buildMainArgs omits side-effect options when not specified. */
  @Test
  public void testBuildMainArgsOmitsSideEffectOptionsWhenNotSet() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, not(hasItemInArray("--replay-policy")));
    assertThat(args, not(hasItemInArray("--replay-shield-io")));
    assertThat(args, not(hasItemInArray("--replay-re-execute")));
    assertThat(args, not(hasItemInArray("--replay-stub")));
    assertThat(args, not(hasItemInArray("--replay-stub-all-else")));
    assertThat(args, not(hasItemInArray("--replay-force-stub")));
  }

  // ===========================================================================
  // Recording scope option tests
  // ===========================================================================

  /** Verifies that --scope parses comma-separated patterns. */
  @Test
  public void testParseScopePatterns() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--scope",
            "com.example.**,com.other.**",
            "-cp",
            "app.jar",
            "com.example.Main");

    String[] patterns = (String[]) getField(replay, "scopePatterns");
    assertThat(patterns, arrayContaining("com.example.**", "com.other.**"));
  }

  /** Verifies that --scope-exclude parses comma-separated patterns. */
  @Test
  public void testParseScopeExcludePatterns() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--scope-exclude",
            "java.lang.**,java.util.**",
            "-cp",
            "app.jar",
            "com.example.Main");

    String[] patterns = (String[]) getField(replay, "scopeExcludePatterns");
    assertThat(patterns, arrayContaining("java.lang.**", "java.util.**"));
  }

  /** Verifies that --scope-io is parsed as boolean. */
  @Test
  public void testParseScopeIoOption() throws Exception {
    Replay replay =
        parseReplay("--wal", "file:/tmp/wal", "--scope-io", "-cp", "app.jar", "com.example.Main");

    assertThat(getField(replay, "scopeIo"), is(true));
  }

  /** Verifies that --scope-policy is parsed correctly. */
  @Test
  public void testParseScopePolicyOption() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--scope-policy",
            "/tmp/scope.yaml",
            "-cp",
            "app.jar",
            "com.example.Main");

    assertThat(getField(replay, "scopePolicyPath"), is("/tmp/scope.yaml"));
  }

  /** Verifies that --scope-default is parsed correctly. */
  @Test
  public void testParseScopeDefaultOption() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--scope-default",
            "skip",
            "-cp",
            "app.jar",
            "com.example.Main");

    assertThat(getField(replay, "scopeDefaultAction"), is("skip"));
  }

  /** Verifies that buildMainArgs includes --scope with joined patterns. */
  @Test
  public void testBuildMainArgsWithScopePatterns() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--scope",
            "com.example.**,com.other.**",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--scope"));
    assertThat(args, hasItemInArray("com.example.**,com.other.**"));
  }

  /** Verifies that buildMainArgs includes --scope-exclude with joined patterns. */
  @Test
  public void testBuildMainArgsWithScopeExcludePatterns() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--scope-exclude",
            "java.lang.**,java.util.**",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--scope-exclude"));
    assertThat(args, hasItemInArray("java.lang.**,java.util.**"));
  }

  /** Verifies that buildMainArgs includes --scope-io when set. */
  @Test
  public void testBuildMainArgsWithScopeIo() throws Exception {
    Replay replay =
        parseReplay("--wal", "file:/tmp/wal", "--scope-io", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--scope-io"));
  }

  /** Verifies that buildMainArgs includes --scope-policy when specified. */
  @Test
  public void testBuildMainArgsWithScopePolicy() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--scope-policy",
            "/tmp/scope.yaml",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--scope-policy"));
    assertThat(args, hasItemInArray("/tmp/scope.yaml"));
  }

  /** Verifies that buildMainArgs includes --scope-default when specified. */
  @Test
  public void testBuildMainArgsWithScopeDefault() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--scope-default",
            "skip",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--scope-default"));
    assertThat(args, hasItemInArray("skip"));
  }

  /** Verifies that buildMainArgs omits scope options when not specified. */
  @Test
  public void testBuildMainArgsOmitsScopeOptionsWhenNotSet() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/wal", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, not(hasItemInArray("--scope")));
    assertThat(args, not(hasItemInArray("--scope-exclude")));
    assertThat(args, not(hasItemInArray("--scope-io")));
    assertThat(args, not(hasItemInArray("--scope-policy")));
    assertThat(args, not(hasItemInArray("--scope-default")));
  }

  /** Verifies that buildMainArgs includes all scope options together. */
  @Test
  public void testBuildMainArgsWithAllScopeOptions() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--scope",
            "com.example.**",
            "--scope-exclude",
            "com.example.internal.**",
            "--scope-io",
            "--scope-policy",
            "/tmp/scope.yaml",
            "--scope-default",
            "skip",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--scope"));
    assertThat(args, hasItemInArray("com.example.**"));
    assertThat(args, hasItemInArray("--scope-exclude"));
    assertThat(args, hasItemInArray("com.example.internal.**"));
    assertThat(args, hasItemInArray("--scope-io"));
    assertThat(args, hasItemInArray("--scope-policy"));
    assertThat(args, hasItemInArray("/tmp/scope.yaml"));
    assertThat(args, hasItemInArray("--scope-default"));
    assertThat(args, hasItemInArray("skip"));
  }

  /** Verifies that buildMainArgs includes all side-effect options together. */
  @Test
  public void testBuildMainArgsWithAllSideEffectOptions() throws Exception {
    Replay replay =
        parseReplay(
            "--wal",
            "file:/tmp/wal",
            "--policy",
            "/tmp/p.yaml",
            "--shield-io",
            "--re-execute",
            "com.example.**",
            "--stub",
            "java.io.**",
            "--stub-all-else",
            "--force-stub",
            "-cp",
            "app.jar",
            "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    assertThat(args, hasItemInArray("--replay-policy"));
    assertThat(args, hasItemInArray("/tmp/p.yaml"));
    assertThat(args, hasItemInArray("--replay-shield-io"));
    assertThat(args, hasItemInArray("--replay-re-execute"));
    assertThat(args, hasItemInArray("com.example.**"));
    assertThat(args, hasItemInArray("--replay-stub"));
    assertThat(args, hasItemInArray("java.io.**"));
    assertThat(args, hasItemInArray("--replay-stub-all-else"));
    assertThat(args, hasItemInArray("--replay-force-stub"));
  }

  // ===========================================================================
  // Relative WAL path resolution tests
  // ===========================================================================

  /** Verifies that a relative Chronicle WAL path is resolved to an absolute path. */
  @Test
  public void testValidateInputResolvesRelativeChronicleWalPath() throws Exception {
    Replay replay = parseReplay("--wal", "file:app.wal", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String resolvedWalPath = (String) getField(replay, "walPath");
    assertThat(
        "Relative path should be resolved to absolute", resolvedWalPath, containsString("file:/"));
    assertThat(
        "Resolved path should end with app.wal", resolvedWalPath, containsString("/app.wal"));
    assertThat(
        "Resolved path should not contain relative segments",
        resolvedWalPath,
        not(containsString("/./")));
  }

  /** Verifies that an absolute Chronicle WAL path remains unchanged after validation. */
  @Test
  public void testValidateInputPreservesAbsoluteChronicleWalPath() throws Exception {
    Replay replay = parseReplay("--wal", "file:/tmp/my-wal", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    assertThat(getField(replay, "walPath"), is("file:/tmp/my-wal"));
  }

  /** Verifies that a Kafka WAL topic name is not modified by path resolution. */
  @Test
  public void testValidateInputDoesNotModifyKafkaWalPath() throws Exception {
    Replay replay =
        parseReplay("-k", "localhost:29092", "--wal", "my-topic", "-cp", "app.jar", "MyMain");
    replay.validateInput();

    assertThat(getField(replay, "walPath"), is("my-topic"));
  }

  /** Verifies that buildMainArgs uses the resolved absolute path for relative Chronicle WALs. */
  @Test
  public void testBuildMainArgsWithRelativeChronicleWal() throws Exception {
    Replay replay = parseReplay("--wal", "file:my-wal", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String[] args = replay.buildMainArgs();
    String walArg = args[1]; // second element is the WAL path
    assertThat(
        "WAL arg should be absolute after resolution", walArg.startsWith("file:/"), is(true));
    assertThat("WAL arg should end with my-wal", walArg, containsString("/my-wal"));
  }

  /** Verifies that a relative path with subdirectory is resolved correctly. */
  @Test
  public void testValidateInputResolvesRelativeSubdirectoryChronicleWalPath() throws Exception {
    Replay replay = parseReplay("--wal", "file:data/app.wal", "-cp", "app.jar", "com.example.Main");
    replay.validateInput();

    String resolvedWalPath = (String) getField(replay, "walPath");
    assertThat(
        "Relative subdirectory path should be resolved to absolute",
        resolvedWalPath,
        containsString("file:/"));
    assertThat(
        "Resolved path should contain data/app.wal",
        resolvedWalPath,
        containsString("/data/app.wal"));
  }
}
