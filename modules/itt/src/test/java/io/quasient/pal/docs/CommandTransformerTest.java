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
package io.quasient.pal.docs;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@code CommandTransformer}, the component that adapts raw documentation commands
 * for safe execution in the test environment.
 *
 * <p>CommandTransformer takes a {@link DocCommand} and produces a result containing the adapted
 * argument list ready for execution, plus metadata about what was changed. These tests define the
 * substitution contract for addresses, classpaths, main classes, names, flags, and structural
 * output required by the integration test harness.
 */
public class CommandTransformerTest {

  /** Test etcd directory URL. */
  private static final String TEST_PAL_DIR = "localhost:12379";

  /** Test Kafka bootstrap servers. */
  private static final String TEST_KAFKA = "localhost:39092";

  /** Test itt-apps classpath. */
  private static final String TEST_CP = "/path/to/itt-apps/target/classes";

  /** Source file path used for test commands. */
  private static final Path TEST_FILE = Paths.get("docs/user/docs/test.md");

  /** Transformer under test. */
  private CommandTransformer transformer;

  /** Sets up the transformer with test configuration before each test. */
  @Before
  public void setUp() {
    transformer = new CommandTransformer(TEST_PAL_DIR, TEST_KAFKA, TEST_CP);
  }

  /**
   * Creates a {@link DocCommand} from a normalized command string.
   *
   * @param normalizedText the command text
   * @return a DocCommand with default test metadata
   */
  private static DocCommand cmd(String normalizedText) {
    return new DocCommand(
        TEST_FILE, 1, normalizedText, normalizedText, DocCommandType.classify(normalizedText));
  }

  /**
   * Creates a {@link DocCommand} with a specific type override.
   *
   * @param normalizedText the command text
   * @param type the command type to use
   * @return a DocCommand with the given type
   */
  private static DocCommand cmd(String normalizedText, DocCommandType type) {
    return new DocCommand(TEST_FILE, 1, normalizedText, normalizedText, type);
  }

  /** Verifies that the etcd {@code -d} flag value is replaced with the test palDirectoryUrl. */
  @Test
  public void shouldSubstituteEtcdAddress() {
    DocCommand command = cmd("pal peer ls -d localhost:2379");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int dIdx = allArgs.indexOf("-d");
    assertTrue("Expected -d flag in args", dIdx >= 0);
    assertThat(allArgs.get(dIdx + 1), is(TEST_PAL_DIR));
  }

  /** Verifies that the Kafka {@code -k} flag value is replaced with the test kafkaServers. */
  @Test
  public void shouldSubstituteKafkaAddress() {
    DocCommand command =
        cmd("pal run -k localhost:29092 --wal my-wal -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int kIdx = allArgs.indexOf("-k");
    assertTrue("Expected -k flag in args", kIdx >= 0);
    assertThat(allArgs.get(kIdx + 1), is(TEST_KAFKA));
  }

  /**
   * Verifies that the Kafka bootstrap {@code -b} flag value is replaced with the test kafkaServers.
   */
  @Test
  public void shouldSubstituteKafkaBootstrapAddress() {
    DocCommand command = cmd("pal log stats -b localhost:29092");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    // -b is replaced with -k since -b is not a valid flag for most subcommands
    int kIdx = allArgs.indexOf("-k");
    assertTrue("Expected -k flag in args (replacing -b)", kIdx >= 0);
    assertThat(allArgs.get(kIdx + 1), is(TEST_KAFKA));
  }

  /**
   * Verifies that non-localhost etcd addresses are replaced (not skipped) to maximize test
   * coverage.
   */
  @Test
  public void shouldSubstituteNonLocalhostEtcdAddress() {
    DocCommand command = cmd("pal peer ls -d etcd:2379");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int dIdx = allArgs.indexOf("-d");
    assertTrue("Expected -d flag in args", dIdx >= 0);
    assertThat(allArgs.get(dIdx + 1), is(TEST_PAL_DIR));
  }

  /**
   * Verifies that non-localhost Kafka addresses are replaced (not skipped) to maximize test
   * coverage.
   */
  @Test
  public void shouldSubstituteNonLocalhostKafkaAddress() {
    DocCommand command = cmd("pal run -k kafka:9092 --wal my-wal -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int kIdx = allArgs.indexOf("-k");
    assertTrue("Expected -k flag in args", kIdx >= 0);
    assertThat(allArgs.get(kIdx + 1), is(TEST_KAFKA));
  }

  /** Verifies that {@code -cp app.jar} is replaced with the itt-apps classpath. */
  @Test
  public void shouldSubstituteClasspath() {
    DocCommand command = cmd("pal run -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int cpIdx = allArgs.indexOf("-cp");
    assertTrue("Expected -cp flag in args", cpIdx >= 0);
    assertThat(allArgs.get(cpIdx + 1), is(TEST_CP));
  }

  /** Verifies that {@code -cp target/classes} is replaced with the itt-apps classpath. */
  @Test
  public void shouldSubstituteTargetClassesClasspath() {
    DocCommand command = cmd("pal run -cp target/classes com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int cpIdx = allArgs.indexOf("-cp");
    assertTrue("Expected -cp flag in args", cpIdx >= 0);
    assertThat(allArgs.get(cpIdx + 1), is(TEST_CP));
  }

  /** Verifies that Gradle {@code build/libs/<artifact>.jar} paths are substituted. */
  @Test
  public void shouldSubstituteGradleBuildLibsJarClasspath() {
    DocCommand command =
        cmd("pal run -cp build/libs/myapp-1.0-SNAPSHOT.jar com.example.HelloService");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int cpIdx = allArgs.indexOf("-cp");
    assertTrue("Expected -cp flag in args", cpIdx >= 0);
    assertThat(allArgs.get(cpIdx + 1), is(TEST_CP));
  }

  /** Verifies that {@code -cp build/classes/java/test} (Gradle test classes) is substituted. */
  @Test
  public void shouldSubstituteGradleTestClassesClasspath() {
    DocCommand command = cmd("pal run -cp build/classes/java/test com.example.MyTest");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int cpIdx = allArgs.indexOf("-cp");
    assertTrue("Expected -cp flag in args", cpIdx >= 0);
    assertThat(allArgs.get(cpIdx + 1), is(TEST_CP));
  }

  /**
   * Verifies that {@code -jar target/my-app.jar} is converted to {@code -cp} with the itt-apps
   * classpath and main class appended if not already present.
   */
  @Test
  public void shouldSubstituteJarFlag() {
    DocCommand command = cmd("pal run -jar target/my-app.jar", DocCommandType.RUN);
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    // Should have -cp instead of -jar
    int cpIdx = allArgs.indexOf("-cp");
    assertTrue("Expected -cp flag in args after -jar substitution", cpIdx >= 0);
    assertThat(allArgs.get(cpIdx + 1), is(TEST_CP));
    // Should not contain -jar
    assertFalse("Should not contain -jar flag", allArgs.contains("-jar"));
    // Main class should be appended
    assertThat(result.getSubstitutions().toString(), containsString("main class"));
  }

  /**
   * Verifies that a placeholder main class like {@code com.example.Main} is replaced with a known
   * itt-apps main class.
   */
  @Test
  public void shouldSubstituteMainClass() {
    DocCommand command = cmd("pal run -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    assertTrue(
        "Expected itt-apps main class in args",
        allArgs.contains("io.quasient.foobar.apps.quantized.rpc.Methods"));
    assertFalse("Should not contain placeholder main class", allArgs.contains("com.example.Main"));
  }

  /**
   * Verifies that various placeholder main class patterns are each replaced with appropriate
   * itt-apps classes.
   */
  @Test
  public void shouldSubstituteVariousMainClasses() {
    // Calculator classes
    DocCommand calcCmd = cmd("pal run -cp app.jar com.example.Calculator");
    CommandTransformer.TransformedCommand calcResult = transformer.transform(calcCmd);
    List<String> calcArgs = Arrays.asList(calcResult.getArgs());
    assertTrue(
        "Calculator class should map to intercept.Calculator",
        calcArgs.contains("io.quasient.foobar.apps.quantized.intercept.Calculator"));

    // Service class
    DocCommand svcCmd = cmd("pal run -cp app.jar com.example.Service");
    CommandTransformer.TransformedCommand svcResult = transformer.transform(svcCmd);
    List<String> svcArgs = Arrays.asList(svcResult.getArgs());
    assertTrue(
        "Service class should map to rpc.Methods",
        svcArgs.contains("io.quasient.foobar.apps.quantized.rpc.Methods"));

    // tutorial.CalculatorService
    DocCommand tutCmd = cmd("pal run -cp app.jar tutorial.CalculatorService");
    CommandTransformer.TransformedCommand tutResult = transformer.transform(tutCmd);
    List<String> tutArgs = Arrays.asList(tutResult.getArgs());
    assertTrue(
        "tutorial.CalculatorService should map to intercept.Calculator",
        tutArgs.contains("io.quasient.foobar.apps.quantized.intercept.Calculator"));
  }

  /** Verifies that {@code --wal} names are uniquified with a prefix or suffix. */
  @Test
  public void shouldUniquifyWalNames() {
    DocCommand command = cmd("pal run --wal my-wal -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    String walName = result.getUniqueWalName();
    assertThat("WAL name should be tracked", walName, is(notNullValue()));
    assertThat("WAL name should contain original", walName, containsString("my-wal"));
    assertThat(
        "WAL name should have doc-test-wal prefix", walName, containsString("doc-test-wal-"));
  }

  /** Verifies that Chronicle WAL paths are uniquified with a temp directory component. */
  @Test
  public void shouldUniquifyChronicleWalPaths() {
    DocCommand command = cmd("pal run --wal file:/tmp/tutorial-wal -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    Path chroniclePath = result.getChroniclePath();
    assertThat("Chronicle path should be tracked", chroniclePath, is(notNullValue()));
    assertThat(
        "Chronicle path should contain unique component",
        chroniclePath.toString(),
        containsString("pal-doc-test-"));
    assertThat(
        "Chronicle path should preserve original name",
        chroniclePath.toString(),
        containsString("tutorial-wal"));
  }

  /** Verifies that peer names are uniquified with a doc-test prefix. */
  @Test
  public void shouldUniquifyPeerNames() {
    DocCommand command = cmd("pal run -n calculator -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    String peerName = result.getPeerName();
    assertThat("Peer name should be tracked", peerName, is(notNullValue()));
    assertThat("Peer name should have doc-test prefix", peerName, containsString("doc-test-"));
    assertThat("Peer name should contain original", peerName, containsString("calculator"));
  }

  /** Verifies that {@code --rpc-default-action ALLOW} is appended to run commands that lack it. */
  @Test
  public void shouldAppendRpcDefaultActionIfMissing() {
    DocCommand command = cmd("pal run -d localhost:2379 -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    assertTrue("Should contain --rpc-default-action", allArgs.contains("--rpc-default-action"));
    int rdaIdx = allArgs.indexOf("--rpc-default-action");
    assertThat(allArgs.get(rdaIdx + 1), is("ALLOW"));
  }

  /**
   * Verifies that {@code --rpc-default-action} is not duplicated when already present in the
   * command.
   */
  @Test
  public void shouldNotDuplicateRpcDefaultAction() {
    DocCommand command = cmd("pal run --rpc-default-action DENY -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int count = 0;
    for (String arg : allArgs) {
      if ("--rpc-default-action".equals(arg)) {
        count++;
      }
    }
    assertThat("Should have exactly one --rpc-default-action", count, is(1));
    int rdaIdx = allArgs.indexOf("--rpc-default-action");
    assertThat("Original value should be preserved", allArgs.get(rdaIdx + 1), is("DENY"));
  }

  /** Verifies that {@code --dry-run} is appended to {@code pal init} commands. */
  @Test
  public void shouldAppendDryRunToInitCommands() {
    DocCommand command = cmd("pal init my-project");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    assertTrue("Should contain --dry-run", allArgs.contains("--dry-run"));
  }

  /** Verifies that {@code --dry-run} is not duplicated when already present. */
  @Test
  public void shouldNotDuplicateDryRunIfPresent() {
    DocCommand command = cmd("pal init my-project --dry-run");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> allArgs = Arrays.asList(result.getArgs());
    int count = 0;
    for (String arg : allArgs) {
      if ("--dry-run".equals(arg)) {
        count++;
      }
    }
    assertThat("Should have exactly one --dry-run", count, is(1));
  }

  /**
   * Verifies that the result contains structured subcommand parts and args suitable for {@code
   * AbstractCliIT.runCliSubcommand()}.
   */
  @Test
  public void shouldReturnSubcommandPartsAndArgs() {
    DocCommand command = cmd("pal peer ls -d localhost:2379 -l");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    assertArrayEquals(
        "Subcommand parts should be [peer, ls]",
        new String[] {"peer", "ls"},
        result.getSubcommandParts());
    List<String> args = Arrays.asList(result.getArgs());
    assertTrue("Args should contain -d", args.contains("-d"));
    assertTrue("Args should contain -l", args.contains("-l"));
  }

  /**
   * Verifies that all substitutions performed are tracked as human-readable descriptions for INFO
   * logging.
   */
  @Test
  public void shouldTrackAllSubstitutionsForLogging() {
    DocCommand command =
        cmd(
            "pal run -d localhost:2379 -k localhost:29092 -cp app.jar com.example.Main --wal my-wal");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    List<String> subs = result.getSubstitutions();
    assertFalse("Should have substitutions", subs.isEmpty());
    // Should track address, classpath, main class, and WAL name substitutions
    assertTrue(
        "Should have at least 4 substitutions (address, kafka, cp, main class, wal)",
        subs.size() >= 4);
  }

  /**
   * Verifies that stdin data from echo or heredoc pipe patterns is extracted and preserved in the
   * result.
   */
  @Test
  public void shouldPreserveStdinData() {
    DocCommand command =
        cmd("echo '{\"jsonrpc\":\"2.0\",\"method\":\"test\"}' | pal peer call -d localhost:2379");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    assertThat("Stdin data should be extracted", result.getStdinData(), is(notNullValue()));
    assertThat(
        "Stdin data should contain the JSON", result.getStdinData(), containsString("jsonrpc"));
  }

  /** Verifies that commands requiring no substitutions are returned unchanged. */
  @Test
  public void shouldHandleCommandsWithNoSubstitutionsNeeded() {
    DocCommand command = cmd("pal help");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertFalse(result.isSkipped());
    assertArrayEquals(
        "Subcommand parts should be [help]", new String[] {"help"}, result.getSubcommandParts());
    assertTrue("Should have no substitutions or empty list", result.getSubstitutions().isEmpty());
  }

  /**
   * Verifies that truly untestable commands are marked as skipped with a meaningful reason string.
   */
  @Test
  public void shouldSkipTrulyUntestableCommands() {
    // A command with --fx-thread requires JavaFX runtime
    DocCommand command = cmd("pal run --fx-thread -cp app.jar com.example.Main");
    CommandTransformer.TransformedCommand result = transformer.transform(command);

    assertTrue("Should be skipped", result.isSkipped());
    assertThat("Skip reason should be meaningful", result.getSkipReason(), is(notNullValue()));
    assertThat(
        "Skip reason should mention JavaFX", result.getSkipReason(), containsString("JavaFX"));
  }
}
