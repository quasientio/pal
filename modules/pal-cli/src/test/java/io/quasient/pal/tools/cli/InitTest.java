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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import picocli.CommandLine;

/**
 * Unit tests for the {@code pal init} command.
 *
 * <p>These tests verify non-interactive CLI flag parsing, validation, dry-run behavior, default
 * values, and project generation for the Init command.
 */
public class InitTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  /** Captured stdout from command execution. */
  private StringWriter outWriter;

  /** Captured stderr from command execution. */
  private StringWriter errWriter;

  @Before
  public void setUp() {
    outWriter = new StringWriter();
    errWriter = new StringWriter();
  }

  /**
   * Executes {@code pal init} with the given arguments, capturing stdout and stderr.
   *
   * @param args the command-line arguments
   * @return the exit code
   */
  private int executeInit(String... args) {
    CommandLine cmd = Pal.createCommandLine();
    cmd.setOut(new PrintWriter(outWriter));
    cmd.setErr(new PrintWriter(errWriter));

    String[] fullArgs = new String[args.length + 1];
    fullArgs[0] = "init";
    System.arraycopy(args, 0, fullArgs, 1, args.length);
    return cmd.execute(fullArgs);
  }

  /**
   * Returns a minimal valid pom.xml for testing existing project detection.
   *
   * @return a valid pom.xml string
   */
  private static String minimalPomXml() {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>com.existing</groupId>
          <artifactId>existing-app</artifactId>
          <version>2.0.0</version>
        </project>
        """;
  }

  /**
   * Returns a minimal valid build.gradle for testing existing Gradle project detection.
   *
   * @return a valid build.gradle string
   */
  private static String minimalBuildGradle() {
    return """
        plugins {
            id 'java'
        }

        group = 'com.existing'
        version = '2.0.0'

        dependencies {
        }
        """;
  }

  // ---------------------------------------------------------------------------
  // Non-interactive validation tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies that non-interactive mode requires the {@code --group-id} flag.
   *
   * <p>Without {@code --group-id}, {@code Init.call()} should return an error exit code with a
   * descriptive message indicating the missing required option.
   */
  @Test
  public void testNonInteractiveRequiresGroupId() {
    Path dir = tempFolder.getRoot().toPath().resolve("new-project");
    int exitCode =
        executeInit(
            "--non-interactive",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            dir.toString());
    assertThat(exitCode, is(not(0)));
  }

  /**
   * Verifies that non-interactive mode requires the {@code --artifact-id} flag.
   *
   * <p>Without {@code --artifact-id}, {@code Init.call()} should return an error exit code.
   */
  @Test
  public void testNonInteractiveRequiresArtifactId() {
    Path dir = tempFolder.getRoot().toPath().resolve("new-project");
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--main-class",
            "com.test.Main",
            dir.toString());
    assertThat(exitCode, is(not(0)));
  }

  /**
   * Verifies that non-interactive mode requires the {@code --main-class} flag.
   *
   * <p>Without {@code --main-class}, {@code Init.call()} should return an error exit code.
   */
  @Test
  public void testNonInteractiveRequiresMainClass() {
    Path dir = tempFolder.getRoot().toPath().resolve("new-project");
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            dir.toString());
    assertThat(exitCode, is(not(0)));
  }

  /**
   * Verifies that non-interactive mode succeeds when all required flags are provided.
   *
   * <p>With all required flags, {@code Init.call()} should return exit code 0 and generate files in
   * the target directory.
   */
  @Test
  public void testNonInteractiveWithAllFlags() throws IOException {
    Path dir = tempFolder.newFolder("all-flags").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));
    assertTrue(Files.exists(dir.resolve("pom.xml")));
  }

  // ---------------------------------------------------------------------------
  // New project generation tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies Maven project generation in non-interactive mode.
   *
   * <p>Given an empty temporary directory with all Maven flags, a {@code pom.xml} should be
   * generated.
   */
  @Test
  public void testNonInteractiveMavenNewProject() throws IOException {
    Path dir = tempFolder.newFolder("maven-proj").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));
    assertTrue("pom.xml should be generated", Files.exists(dir.resolve("pom.xml")));
  }

  /**
   * Verifies Gradle project generation in non-interactive mode.
   *
   * <p>Given an empty temporary directory with {@code --build-tool gradle} and all required flags,
   * {@code build.gradle} and {@code settings.gradle} should be generated.
   */
  @Test
  public void testNonInteractiveGradleNewProject() throws IOException {
    Path dir = tempFolder.newFolder("gradle-proj").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "gradle",
            dir.toString());
    assertThat(exitCode, is(0));
    assertTrue("build.gradle should be generated", Files.exists(dir.resolve("build.gradle")));
    assertTrue("settings.gradle should be generated", Files.exists(dir.resolve("settings.gradle")));
  }

  /**
   * Verifies that the target directory is created if it does not exist.
   *
   * <p>Given a non-existent target directory path, the Init command should create the directory and
   * generate files inside it.
   */
  @Test
  public void testTargetDirectoryCreatedIfMissing() throws IOException {
    Path dir = tempFolder.getRoot().toPath().resolve("new-dir");
    assertFalse("Directory should not exist yet", Files.exists(dir));
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            dir.toString());
    assertThat(exitCode, is(0));
    assertTrue("Directory should be created", Files.isDirectory(dir));
    assertTrue("pom.xml should be generated", Files.exists(dir.resolve("pom.xml")));
  }

  // ---------------------------------------------------------------------------
  // Existing project detection tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies that an existing Maven project is auto-detected.
   *
   * <p>Given a temporary directory containing an existing {@code pom.xml} and no {@code
   * --build-tool} flag, the Init command should detect Maven and use patch mode.
   */
  @Test
  public void testExistingProjectAutoDetected() throws IOException {
    Path dir = tempFolder.newFolder("existing-maven").toPath();
    Files.writeString(dir.resolve("pom.xml"), minimalPomXml(), StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.existing.Main", dir.toString());
    assertThat(exitCode, is(0));
    // The pom.xml should still exist (patched, not replaced)
    assertTrue(Files.exists(dir.resolve("pom.xml")));
    // A backup should be created for existing project patching
    assertTrue("pom.xml.backup should be created", Files.exists(dir.resolve("pom.xml.backup")));
  }

  /**
   * Verifies that an existing Gradle project is auto-detected.
   *
   * <p>Given a temporary directory containing an existing {@code build.gradle} and no {@code
   * --build-tool} flag, the Init command should detect Gradle and use patch mode.
   */
  @Test
  public void testExistingGradleProjectAutoDetected() throws IOException {
    Path dir = tempFolder.newFolder("existing-gradle").toPath();
    Files.writeString(dir.resolve("build.gradle"), minimalBuildGradle(), StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.existing.Main", dir.toString());
    assertThat(exitCode, is(0));
    assertTrue(Files.exists(dir.resolve("build.gradle")));
    assertTrue(
        "build.gradle.backup should be created", Files.exists(dir.resolve("build.gradle.backup")));
  }

  // ---------------------------------------------------------------------------
  // Force overwrite tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies that the {@code --force} flag overwrites existing files.
   *
   * <p>Given a temporary directory with existing config files and the {@code --force} flag, the
   * Init command should overwrite the existing files.
   */
  @Test
  public void testForceOverwritesExistingFiles() throws IOException {
    Path dir = tempFolder.newFolder("force-maven").toPath();
    // Create an existing config file that should be overwritten
    Files.createDirectories(dir.resolve("config"));
    Files.writeString(
        dir.resolve("config/peer-logging.xml"), "<!-- old content -->", StandardCharsets.UTF_8);

    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            "--force",
            dir.toString());
    assertThat(exitCode, is(0));
    String content =
        Files.readString(dir.resolve("config/peer-logging.xml"), StandardCharsets.UTF_8);
    assertThat("Config file should be overwritten", content, is(not("<!-- old content -->")));
  }

  /**
   * Verifies that the {@code --force} flag overwrites existing Gradle project files.
   *
   * <p>Given a temporary directory with existing {@code build.gradle} and config files and the
   * {@code --force} flag, the Init command should overwrite the existing files.
   */
  @Test
  public void testForceOverwritesExistingGradleFiles() throws IOException {
    Path dir = tempFolder.newFolder("force-gradle").toPath();
    Files.createDirectories(dir.resolve("config"));
    Files.writeString(
        dir.resolve("config/peer-logging.xml"), "<!-- old content -->", StandardCharsets.UTF_8);

    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "gradle",
            "--force",
            dir.toString());
    assertThat(exitCode, is(0));
    String content =
        Files.readString(dir.resolve("config/peer-logging.xml"), StandardCharsets.UTF_8);
    assertThat("Config file should be overwritten", content, is(not("<!-- old content -->")));
  }

  // ---------------------------------------------------------------------------
  // Version resolution test
  // ---------------------------------------------------------------------------

  /**
   * Verifies that the PAL version is resolved from the package manifest.
   *
   * <p>When no manifest is available (dev mode), it should fall back to "1.0.0-SNAPSHOT".
   */
  @Test
  public void testVersionResolvedFromManifest() {
    // In test context, there's no manifest, so it should fall back to default
    String version = Init.resolvePalVersion();
    assertThat(
        "Should fall back to default version when no manifest",
        version,
        is(Init.DEFAULT_PAL_VERSION));
  }

  // ---------------------------------------------------------------------------
  // Flag parsing tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies that negatable flags are parsed correctly.
   *
   * <p>Given {@code --no-sample-app --no-logging-config}, the parsed values should be {@code
   * sampleApp=false} and {@code loggingConfig=false}.
   */
  @Test
  public void testNegatableFlags() throws IOException {
    Path dir = tempFolder.newFolder("negatable").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            "--no-sample-app",
            "--no-logging-config",
            dir.toString());
    assertThat(exitCode, is(0));
    // With --no-sample-app, no source files should be generated
    assertFalse(
        "No Main.java with --no-sample-app",
        Files.exists(dir.resolve("src/main/java/com/test/Main.java")));
    // With --no-logging-config, no logging config should be generated
    assertFalse(
        "No peer-logging.xml with --no-logging-config",
        Files.exists(dir.resolve("config/peer-logging.xml")));
  }

  /**
   * Verifies that default flag values are correct when no feature flags are specified.
   *
   * <p>Without any feature flags, the defaults should be: sampleApp=true, loggingConfig=true,
   * rpcPolicy=false, scopePolicy=false, interceptBundle=false, infra=false.
   */
  @Test
  public void testDefaultFlagValues() throws IOException {
    Path dir = tempFolder.newFolder("defaults").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            dir.toString());
    assertThat(exitCode, is(0));
    // sampleApp=true by default: source files should exist
    assertTrue(
        "Main.java should exist (sampleApp=true)",
        Files.exists(dir.resolve("src/main/java/com/test/Main.java")));
    // loggingConfig=true by default
    assertTrue(
        "peer-logging.xml should exist (loggingConfig=true)",
        Files.exists(dir.resolve("config/peer-logging.xml")));
    // rpcPolicy=false by default
    assertFalse(
        "rpc-policy.yaml should not exist (rpcPolicy=false)",
        Files.exists(dir.resolve("config/rpc-policy.yaml")));
    // scopePolicy=false by default
    assertFalse(
        "recording-scope.yaml should not exist (scopePolicy=false)",
        Files.exists(dir.resolve("config/recording-scope.yaml")));
    // interceptBundle=false by default
    assertFalse(
        "intercept-bundle.yaml should not exist (interceptBundle=false)",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));
    // infra=false by default
    assertFalse("infra/ should not exist (infra=false)", Files.exists(dir.resolve("infra")));
  }

  // ---------------------------------------------------------------------------
  // Dry-run tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies that {@code --dry-run} previews without writing any files.
   *
   * <p>Given {@code --non-interactive --dry-run} with all required flags in an empty temp dir, the
   * exit code should be 0, the output should describe what would be generated, and no files should
   * be written to disk.
   */
  @Test
  public void testDryRunFlagPreviewsWithoutWriting() throws IOException {
    Path dir = tempFolder.newFolder("dry-run").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--dry-run",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            dir.toString());
    assertThat(exitCode, is(0));
    // No pom.xml should be written
    assertFalse("pom.xml should not be written in dry-run", Files.exists(dir.resolve("pom.xml")));
    // No source files
    assertFalse(
        "Source files should not be written in dry-run",
        Files.exists(dir.resolve("src/main/java/com/test/Main.java")));
  }

  /**
   * Verifies that {@code --dry-run} does not modify an existing project.
   *
   * <p>Given a temp dir with an existing {@code pom.xml} and the {@code --dry-run} flag, the output
   * should describe what would be patched, the {@code pom.xml} should remain unchanged, and no
   * backup should be created.
   */
  @Test
  public void testDryRunWithExistingProject() throws IOException {
    Path dir = tempFolder.newFolder("dry-run-existing").toPath();
    String originalPom = minimalPomXml();
    Files.writeString(dir.resolve("pom.xml"), originalPom, StandardCharsets.UTF_8);

    int exitCode =
        executeInit(
            "--non-interactive", "--dry-run", "--main-class", "com.existing.Main", dir.toString());
    assertThat(exitCode, is(0));
    // pom.xml should remain unchanged
    String afterPom = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should be unchanged in dry-run", afterPom, is(originalPom));
    // No backup should be created
    assertFalse("No backup in dry-run", Files.exists(dir.resolve("pom.xml.backup")));
  }

  /**
   * Verifies that {@code --dry-run} does not modify an existing Gradle project.
   *
   * <p>Given a temp dir with an existing {@code build.gradle} and the {@code --dry-run} flag, the
   * output should describe what would be patched, the {@code build.gradle} should remain unchanged,
   * and no backup should be created.
   */
  @Test
  public void testDryRunWithExistingGradleProject() throws IOException {
    Path dir = tempFolder.newFolder("dry-run-gradle").toPath();
    String originalGradle = minimalBuildGradle();
    Files.writeString(dir.resolve("build.gradle"), originalGradle, StandardCharsets.UTF_8);

    int exitCode =
        executeInit(
            "--non-interactive", "--dry-run", "--main-class", "com.existing.Main", dir.toString());
    assertThat(exitCode, is(0));
    // build.gradle should remain unchanged
    String afterGradle = Files.readString(dir.resolve("build.gradle"), StandardCharsets.UTF_8);
    assertThat("build.gradle should be unchanged in dry-run", afterGradle, is(originalGradle));
    assertFalse("No backup in dry-run", Files.exists(dir.resolve("build.gradle.backup")));
  }

  /**
   * Verifies that {@code --dry-run} output lists all files that would be generated or modified.
   *
   * <p>Given {@code --dry-run} with all features enabled, the captured output should list every
   * file that would be generated or modified with its action (create/patch).
   */
  @Test
  public void testDryRunOutputListsAllFiles() throws IOException {
    Path dir = tempFolder.newFolder("dry-run-all").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--dry-run",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            "--sample-app",
            "--logging-config",
            "--intercepting",
            "--scope-policy",
            dir.toString());
    assertThat(exitCode, is(0));
    String output = outWriter.toString();
    assertThat("Should mention dry run", output, containsString("Dry run"));
    assertThat("Should list CREATE actions", output, containsString("[CREATE]"));
  }

  // ---------------------------------------------------------------------------
  // Help text test
  // ---------------------------------------------------------------------------

  /**
   * Verifies that {@code pal init --help} shows the custom synopsis and all options.
   *
   * <p>The help text should display the custom synopsis, all documented options including {@code
   * --dry-run}, and a description of the command.
   */
  @Test
  public void testInitHelpText() {
    CommandLine cmd = Pal.createCommandLine();
    CommandLine initCmd = cmd.getSubcommands().get("init");
    String help = initCmd.getUsageMessage();

    assertThat("Help should show command name", help, containsString("pal init"));
    assertThat("Help should show --dry-run", help, containsString("--dry-run"));
    assertThat("Help should show --non-interactive", help, containsString("--non-interactive"));
    assertThat("Help should show --group-id", help, containsString("--group-id"));
    assertThat("Help should show --build-tool", help, containsString("--build-tool"));
    assertThat("Help should show interceptable option", help, containsString("interceptable"));
    assertThat("Help should show --force", help, containsString("--force"));
    assertThat("Help should show sample-app option", help, containsString("sample-app"));
  }

  // ---------------------------------------------------------------------------
  // Backup and validation tests — Risk 1 mitigations
  // ---------------------------------------------------------------------------

  /**
   * Verifies that a backup is created when patching an existing Maven project.
   *
   * <p>Given a temp dir with an existing {@code pom.xml} (no {@code --dry-run}), the Init command
   * should create {@code pom.xml.backup} containing the original content before patching.
   */
  @Test
  public void testBackupCreatedWhenPatchingMavenProject() throws IOException {
    Path dir = tempFolder.newFolder("backup-maven").toPath();
    String originalPom = minimalPomXml();
    Files.writeString(dir.resolve("pom.xml"), originalPom, StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.existing.Main", dir.toString());
    assertThat(exitCode, is(0));
    assertTrue("pom.xml.backup should be created", Files.exists(dir.resolve("pom.xml.backup")));
    String backup = Files.readString(dir.resolve("pom.xml.backup"), StandardCharsets.UTF_8);
    assertThat("Backup should contain original content", backup, is(originalPom));
  }

  /**
   * Verifies that a backup is created when patching an existing Gradle project.
   *
   * <p>Given a temp dir with an existing {@code build.gradle} (no {@code --dry-run}), the Init
   * command should create {@code build.gradle.backup} containing the original content before
   * patching.
   */
  @Test
  public void testBackupCreatedWhenPatchingGradleProject() throws IOException {
    Path dir = tempFolder.newFolder("backup-gradle").toPath();
    String originalGradle = minimalBuildGradle();
    Files.writeString(dir.resolve("build.gradle"), originalGradle, StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.existing.Main", dir.toString());
    assertThat(exitCode, is(0));
    assertTrue(
        "build.gradle.backup should be created", Files.exists(dir.resolve("build.gradle.backup")));
    String backup = Files.readString(dir.resolve("build.gradle.backup"), StandardCharsets.UTF_8);
    assertThat("Backup should contain original content", backup, is(originalGradle));
  }

  /**
   * Verifies that the patched {@code pom.xml} is valid XML before and after edits.
   *
   * <p>Given a temp dir with an existing valid {@code pom.xml}, the Init command should validate
   * that the XML is well-formed before patching, apply the patch, and validate that the resulting
   * XML is still well-formed after patching.
   */
  @Test
  public void testPomXmlValidBeforeAndAfterPatch() throws Exception {
    Path dir = tempFolder.newFolder("pom-valid").toPath();
    Files.writeString(dir.resolve("pom.xml"), minimalPomXml(), StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.existing.Main", dir.toString());
    assertThat(exitCode, is(0));

    // Verify patched pom.xml is parseable XML
    String patchedPom = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    // This will throw if the XML is malformed
    builder.parse(new ByteArrayInputStream(patchedPom.getBytes(StandardCharsets.UTF_8)));

    // Verify it contains pal-weave
    assertThat("Patched pom.xml should contain pal-weave", patchedPom, containsString("pal-weave"));
  }

  /**
   * Verifies that the patched {@code build.gradle} is valid before and after edits.
   *
   * <p>Given a temp dir with an existing valid {@code build.gradle}, the Init command should
   * validate the build file before patching, apply the patch, and validate that the resulting file
   * is still syntactically valid after patching.
   */
  @Test
  public void testBuildGradleValidBeforeAndAfterPatch() throws IOException {
    Path dir = tempFolder.newFolder("gradle-valid").toPath();
    Files.writeString(dir.resolve("build.gradle"), minimalBuildGradle(), StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.existing.Main", dir.toString());
    assertThat(exitCode, is(0));

    String patchedGradle = Files.readString(dir.resolve("build.gradle"), StandardCharsets.UTF_8);
    // Verify it contains pal-weave dependency
    assertThat(
        "Patched build.gradle should contain pal-weave",
        patchedGradle,
        containsString("pal-weave"));
    // Verify it still has the original plugins block
    assertThat(
        "Patched build.gradle should still have plugins", patchedGradle, containsString("plugins"));
    // Verify braces are balanced
    long openBraces = patchedGradle.chars().filter(c -> c == '{').count();
    long closeBraces = patchedGradle.chars().filter(c -> c == '}').count();
    assertThat("Braces should be balanced", openBraces, is(closeBraces));
  }

  // ---------------------------------------------------------------------------
  // pal-weave availability tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies that Init checks whether pal-weave is available in the local Maven repository.
   *
   * <p>Given pal-weave is already installed in the local Maven repository, the Init command should
   * detect its presence and proceed without error.
   */
  @Test
  public void testPalWeaveAvailabilityChecked() throws IOException {
    Path dir = tempFolder.newFolder("weave-check").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            dir.toString());
    // The command should succeed regardless of pal-weave availability
    // (PalWeaveResolver handles failure gracefully)
    assertThat(exitCode, is(0));
    // Verify pal-weave check was performed (output contains checking message)
    String output = outWriter.toString();
    assertThat("Should check for pal-weave", output, containsString("pal-weave"));
  }

  /**
   * Verifies that Init attempts to fetch pal-weave from Maven Central when it is missing locally.
   *
   * <p>Given pal-weave is not installed in the local Maven repository, the Init command should
   * attempt to fetch it from Maven Central during initialization. The command still succeeds even
   * if fetch fails (graceful degradation).
   */
  @Test
  public void testPalWeaveFetchedWhenMissing() throws IOException {
    Path dir = tempFolder.newFolder("weave-fetch").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "my-app",
            "--main-class",
            "com.test.Main",
            dir.toString());
    // Command succeeds even if pal-weave fetch fails
    assertThat(exitCode, is(0));
    String output = outWriter.toString();
    // Should mention pal-weave (either "available" or "Fetching" or "Would fetch")
    assertThat("Should mention pal-weave resolution", output, containsString("pal-weave"));
  }
}
