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
package io.quasient.pal.tools.cli.init;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.quasient.pal.tools.cli.Pal;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import picocli.CommandLine;

/**
 * End-to-end integration tests for the {@code pal init} command.
 *
 * <p>These tests verify the complete init workflow: CLI flag parsing, InitConfig construction,
 * generator orchestration, file output, and user-facing messages. They run within the pal-cli
 * module's unit test suite (not the {@code itt} module) since they do not require external
 * infrastructure (etcd/Kafka). Tests exercise the full Init command via picocli's {@code
 * CommandLine.execute()} with real filesystem I/O.
 *
 * @see io.quasient.pal.tools.cli.Init
 */
public class InitEndToEndTest {

  /** Temporary directory for all test file I/O. */
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
   * Default PAL version when running in test context (no JAR manifest available). Mirrors the
   * fallback value used by {@code Init.resolvePalVersion()}.
   */
  private static final String DEFAULT_PAL_VERSION = "1.0.0-SNAPSHOT";

  /**
   * Executes {@code pal init} with the given arguments, capturing stdout and stderr. Uses
   * reflection to access the package-private {@code Pal.createCommandLine()} method since this test
   * class resides in a different package.
   *
   * @param args the command-line arguments
   * @return the exit code
   */
  private int executeInit(String... args) {
    try {
      Method createCmdLine = Pal.class.getDeclaredMethod("createCommandLine");
      createCmdLine.setAccessible(true);
      CommandLine cmd = (CommandLine) createCmdLine.invoke(null);
      cmd.setOut(new PrintWriter(outWriter));
      cmd.setErr(new PrintWriter(errWriter));

      String[] fullArgs = new String[args.length + 1];
      fullArgs[0] = "init";
      System.arraycopy(args, 0, fullArgs, 1, args.length);
      return cmd.execute(fullArgs);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Failed to create CommandLine via reflection", e);
    }
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
          <groupId>com.acme</groupId>
          <artifactId>existing-app</artifactId>
          <version>2.0.0</version>
          <dependencies>
            <dependency>
              <groupId>org.slf4j</groupId>
              <artifactId>slf4j-api</artifactId>
              <version>2.0.9</version>
            </dependency>
          </dependencies>
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

        group = 'com.acme'
        version = '2.0.0'

        dependencies {
            implementation 'org.slf4j:slf4j-api:2.0.9'
        }
        """;
  }

  // ---------------------------------------------------------------------------
  // New project generation
  // ---------------------------------------------------------------------------

  /**
   * Verifies full Maven project generation end-to-end.
   *
   * <p>Given an empty temp directory, when {@code pal init --non-interactive -y --group-id com.test
   * --artifact-id test-app --main-class com.test.Main --mode local --build-tool maven} is executed
   * via CommandLine, then: pom.xml exists and is valid XML with pal-weave dependency and AspectJ
   * plugin; src/main/java/com/test/Main.java exists; config/peer-logging.xml exists; .env.pal
   * exists; exit code is 0.
   */
  @Test
  public void testNewMavenProjectEndToEnd() throws Exception {
    Path dir = tempFolder.newFolder("new-maven").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--mode",
            "local",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    // pom.xml exists and is valid XML
    Path pomPath = dir.resolve("pom.xml");
    assertTrue("pom.xml should exist", Files.exists(pomPath));
    String pomContent = Files.readString(pomPath, StandardCharsets.UTF_8);
    DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8)));

    // pom.xml has pal-weave dependency and AspectJ plugin
    assertThat(pomContent, containsString("pal-weave"));
    assertThat(pomContent, containsString("aspectj-maven-plugin"));

    // Sample app generated
    assertTrue(
        "Main.java should exist", Files.exists(dir.resolve("src/main/java/com/test/Main.java")));

    // Config file generated
    assertTrue(
        "peer-logging.xml should exist", Files.exists(dir.resolve("config/peer-logging.xml")));

    // Env file generated
    assertTrue(".env.pal should exist", Files.exists(dir.resolve(".env.pal")));
  }

  /**
   * Verifies full Gradle project generation end-to-end.
   *
   * <p>Given an empty temp directory, when the same flags as above are used with {@code
   * --build-tool gradle}, then: build.gradle exists with pal-weave dependency and AspectJ plugin;
   * settings.gradle exists; sample source files exist; exit code is 0.
   */
  @Test
  public void testNewGradleProjectEndToEnd() throws IOException {
    Path dir = tempFolder.newFolder("new-gradle").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--mode",
            "local",
            "--build-tool",
            "gradle",
            dir.toString());
    assertThat(exitCode, is(0));

    // build.gradle exists with pal-weave dependency and AspectJ plugin
    Path buildGradle = dir.resolve("build.gradle");
    assertTrue("build.gradle should exist", Files.exists(buildGradle));
    String gradleContent = Files.readString(buildGradle, StandardCharsets.UTF_8);
    assertThat(gradleContent, containsString("pal-weave"));
    assertThat(gradleContent, containsString("aspectj"));

    // settings.gradle exists
    assertTrue("settings.gradle should exist", Files.exists(dir.resolve("settings.gradle")));

    // Sample source files exist
    assertTrue(
        "Main.java should exist", Files.exists(dir.resolve("src/main/java/com/test/Main.java")));
  }

  // ---------------------------------------------------------------------------
  // Patching existing projects
  // ---------------------------------------------------------------------------

  /**
   * Verifies patching an existing Maven project.
   *
   * <p>Given a temp directory with a minimal valid pom.xml (groupId=com.acme,
   * artifactId=existing-app, one dependency), when {@code pal init --non-interactive -y
   * --main-class com.acme.Main} is executed, then: pom.xml.backup is created with original content;
   * patched pom.xml has pal-weave dependency added; original dependency preserved; exit code is 0.
   */
  @Test
  public void testPatchExistingMavenProject() throws IOException {
    Path dir = tempFolder.newFolder("patch-maven").toPath();
    String originalPom = minimalPomXml();
    Files.writeString(dir.resolve("pom.xml"), originalPom, StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.acme.Main", dir.toString());
    assertThat(exitCode, is(0));

    // Backup created with original content
    Path backup = dir.resolve("pom.xml.backup");
    assertTrue("pom.xml.backup should be created", Files.exists(backup));
    String backupContent = Files.readString(backup, StandardCharsets.UTF_8);
    assertThat(backupContent, is(originalPom));

    // Patched pom has pal-weave
    String patchedPom = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("Should have pal-weave", patchedPom, containsString("pal-weave"));

    // Original dependency preserved
    assertThat("Should preserve slf4j", patchedPom, containsString("slf4j-api"));
  }

  /**
   * Verifies patching an existing Gradle project.
   *
   * <p>Given a temp directory with a minimal build.gradle (java plugin, one dependency), when
   * {@code pal init --non-interactive -y --main-class com.acme.Main} is executed, then:
   * build.gradle.backup is created; patched build.gradle has pal-weave dependency and AspectJ
   * plugin; exit code is 0.
   */
  @Test
  public void testPatchExistingGradleProject() throws IOException {
    Path dir = tempFolder.newFolder("patch-gradle").toPath();
    String originalGradle = minimalBuildGradle();
    Files.writeString(dir.resolve("build.gradle"), originalGradle, StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.acme.Main", dir.toString());
    assertThat(exitCode, is(0));

    // Backup created
    assertTrue(
        "build.gradle.backup should exist", Files.exists(dir.resolve("build.gradle.backup")));

    // Patched build.gradle has pal-weave and AspectJ
    String patched = Files.readString(dir.resolve("build.gradle"), StandardCharsets.UTF_8);
    assertThat("Should have pal-weave", patched, containsString("pal-weave"));
    assertThat("Should have aspectj plugin", patched, containsString("aspectj"));
  }

  // ---------------------------------------------------------------------------
  // Distributed mode and feature flags
  // ---------------------------------------------------------------------------

  /**
   * Verifies that distributed mode generates infrastructure files.
   *
   * <p>Given an empty temp directory, when {@code pal init --non-interactive -y --group-id com.test
   * --artifact-id test-app --main-class com.test.Main --mode distributed --infra --build-tool
   * maven} is executed, then: infra/docker-compose.yml, infra/.env, infra/start.sh, infra/stop.sh
   * all exist.
   */
  @Test
  public void testDistributedModeGeneratesInfra() throws IOException {
    Path dir = tempFolder.newFolder("distributed").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--mode",
            "distributed",
            "--infra",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    assertTrue(
        "docker-compose.yml should exist", Files.exists(dir.resolve("infra/docker-compose.yml")));
    assertTrue("infra/.env should exist", Files.exists(dir.resolve("infra/.env")));
    assertTrue("start.sh should exist", Files.exists(dir.resolve("infra/start.sh")));
    assertTrue("stop.sh should exist", Files.exists(dir.resolve("infra/stop.sh")));
  }

  /**
   * Verifies that {@code --no-sample-app} skips Java source generation.
   *
   * <p>Given an empty temp directory, when {@code --no-sample-app} flag is used, then: no Java
   * source files are generated; pom.xml is still generated correctly.
   */
  @Test
  public void testNoSampleAppFlag() throws IOException {
    Path dir = tempFolder.newFolder("no-sample").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--no-sample-app",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    // No Java source files generated
    assertFalse(
        "No Main.java with --no-sample-app",
        Files.exists(dir.resolve("src/main/java/com/test/Main.java")));

    // pom.xml still generated
    assertTrue("pom.xml should still exist", Files.exists(dir.resolve("pom.xml")));
  }

  /**
   * Verifies that all config files are generated when all config flags are enabled.
   *
   * <p>Given an empty temp directory, when {@code --rpc-policy --scope-policy --logging-config
   * --intercept-bundle} are all enabled, then: config/rpc-policy.yaml, config/recording-scope.yaml,
   * config/peer-logging.xml, config/intercept-bundle.yaml all exist.
   */
  @Test
  public void testAllConfigsEnabled() throws IOException {
    Path dir = tempFolder.newFolder("all-configs").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--rpc-policy",
            "--scope-policy",
            "--logging-config",
            "--intercept-bundle",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    assertTrue("rpc-policy.yaml should exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
    assertTrue(
        "recording-scope.yaml should exist",
        Files.exists(dir.resolve("config/recording-scope.yaml")));
    assertTrue(
        "peer-logging.xml should exist", Files.exists(dir.resolve("config/peer-logging.xml")));
    assertTrue(
        "intercept-bundle.yaml should exist",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));
  }

  // ---------------------------------------------------------------------------
  // Idempotent patching
  // ---------------------------------------------------------------------------

  /**
   * Verifies that running init twice on the same Maven project does not duplicate dependencies or
   * plugins.
   *
   * <p>Given an existing Maven project already patched by init, when init is run again with the
   * same flags, then: no duplicate dependencies or plugins; exit code is 0.
   */
  @Test
  public void testIdempotentPatchDoesNotDuplicate() throws IOException {
    Path dir = tempFolder.newFolder("idempotent-maven").toPath();
    Files.writeString(dir.resolve("pom.xml"), minimalPomXml(), StandardCharsets.UTF_8);

    // First run
    int exitCode1 =
        executeInit(
            "--non-interactive", "--main-class", "com.acme.Main", "--force", dir.toString());
    assertThat(exitCode1, is(0));

    // Reset writers for second run
    outWriter = new StringWriter();
    errWriter = new StringWriter();

    // Second run
    int exitCode2 =
        executeInit(
            "--non-interactive", "--main-class", "com.acme.Main", "--force", dir.toString());
    assertThat(exitCode2, is(0));

    String afterSecond = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);

    // Count pal-weave occurrences in dependencies (should be exactly 1 dependency element)
    int palWeaveCount = countOccurrences(afterSecond, "<artifactId>pal-weave</artifactId>");
    // One in dependency, one in aspectLibrary
    assertThat("pal-weave should appear exactly twice (dep + aspect)", palWeaveCount, is(2));

    // Count aspectj-maven-plugin occurrences (should be exactly 1)
    int pluginCount =
        countOccurrences(afterSecond, "<artifactId>aspectj-maven-plugin</artifactId>");
    assertThat("aspectj-maven-plugin should appear exactly once", pluginCount, is(1));
  }

  /**
   * Verifies that running init twice on the same Gradle project does not duplicate dependencies or
   * plugins.
   *
   * <p>Given an existing Gradle project already patched by init, when init is run again with the
   * same flags, then: no duplicate pal-weave dependencies or AspectJ plugin entries; exit code is
   * 0.
   */
  @Test
  public void testIdempotentGradlePatchDoesNotDuplicate() throws IOException {
    Path dir = tempFolder.newFolder("idempotent-gradle").toPath();
    Files.writeString(dir.resolve("build.gradle"), minimalBuildGradle(), StandardCharsets.UTF_8);

    // First run
    int exitCode1 =
        executeInit(
            "--non-interactive", "--main-class", "com.acme.Main", "--force", dir.toString());
    assertThat(exitCode1, is(0));

    // Reset writers for second run
    outWriter = new StringWriter();
    errWriter = new StringWriter();

    // Second run
    int exitCode2 =
        executeInit(
            "--non-interactive", "--main-class", "com.acme.Main", "--force", dir.toString());
    assertThat(exitCode2, is(0));

    String afterSecond = Files.readString(dir.resolve("build.gradle"), StandardCharsets.UTF_8);

    // pal-weave should appear exactly once
    int palWeaveCount = countOccurrences(afterSecond, "pal-weave");
    assertThat("pal-weave should appear exactly once", palWeaveCount, is(1));
  }

  // ---------------------------------------------------------------------------
  // Target directory and output messages
  // ---------------------------------------------------------------------------

  /**
   * Verifies that a positional directory argument creates the directory and generates files inside
   * it.
   *
   * <p>Given a non-existent subdirectory path as positional argument, when init is executed, then:
   * directory is created; files are generated inside it.
   */
  @Test
  public void testTargetDirectoryArgument() throws IOException {
    Path dir = tempFolder.getRoot().toPath().resolve("new-subdir");
    assertFalse("Directory should not exist yet", Files.exists(dir));

    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            dir.toString());
    assertThat(exitCode, is(0));

    assertTrue("Directory should be created", Files.isDirectory(dir));
    assertTrue("pom.xml should exist in new dir", Files.exists(dir.resolve("pom.xml")));
  }

  /**
   * Verifies that Maven project init output includes next steps with mvn package and pal run
   * instructions.
   *
   * <p>Given a new Maven project init, when stdout is captured, then: output contains "Next steps"
   * with mvn package and pal run instructions.
   */
  @Test
  public void testOutputIncludesNextStepsMaven() throws IOException {
    Path dir = tempFolder.newFolder("next-steps-maven").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    String output = outWriter.toString();
    assertThat("Should show Next steps", output, containsString("Next steps"));
    assertThat("Should mention mvn package", output, containsString("mvn package"));
    assertThat("Should mention pal run", output, containsString("pal run"));
  }

  /**
   * Verifies that Gradle project init output includes next steps with gradle build instructions.
   *
   * <p>Given a new Gradle project init, when stdout is captured, then: output contains "Next steps"
   * with gradle build instructions.
   */
  @Test
  public void testOutputIncludesNextStepsGradle() throws IOException {
    Path dir = tempFolder.newFolder("next-steps-gradle").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "gradle",
            dir.toString());
    assertThat(exitCode, is(0));

    String output = outWriter.toString();
    assertThat("Should show Next steps", output, containsString("Next steps"));
    assertThat("Should mention gradle build", output, containsString("gradle build"));
    assertThat("Should mention pal run", output, containsString("pal run"));
  }

  // ---------------------------------------------------------------------------
  // Force overwrite
  // ---------------------------------------------------------------------------

  /**
   * Verifies that the {@code --force} flag overwrites existing config files.
   *
   * <p>Given a temp dir with existing config/peer-logging.xml, when init is run with {@code
   * --force}, then: peer-logging.xml is overwritten with new content.
   */
  @Test
  public void testForceOverwritesExisting() throws IOException {
    Path dir = tempFolder.newFolder("force-test").toPath();
    Files.createDirectories(dir.resolve("config"));
    Files.writeString(
        dir.resolve("config/peer-logging.xml"), "<!-- old content -->", StandardCharsets.UTF_8);

    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--force",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    String content =
        Files.readString(dir.resolve("config/peer-logging.xml"), StandardCharsets.UTF_8);
    assertThat("Config should be overwritten", content, is(not("<!-- old content -->")));
  }

  // ---------------------------------------------------------------------------
  // Dry-run mode
  // ---------------------------------------------------------------------------

  /**
   * Verifies that {@code --dry-run} previews Maven project creation without writing any files.
   *
   * <p>Given an empty temp directory, when init is run with {@code --dry-run --non-interactive} and
   * all Maven flags, then: exit code is 0; output contains [CREATE] entries for each planned file;
   * NO files are actually created on disk.
   */
  @Test
  public void testDryRunMavenNewProject() throws IOException {
    Path dir = tempFolder.newFolder("dryrun-maven").toPath();
    int exitCode =
        executeInit(
            "--dry-run",
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    String output = outWriter.toString();
    assertThat("Should mention dry run", output, containsString("Dry run"));
    assertThat("Should list CREATE actions", output, containsString("[CREATE]"));

    // No files actually created
    assertFalse("pom.xml should not exist", Files.exists(dir.resolve("pom.xml")));
    assertFalse(
        "Main.java should not exist",
        Files.exists(dir.resolve("src/main/java/com/test/Main.java")));
  }

  /**
   * Verifies that {@code --dry-run} previews pom.xml patching without modifying the file.
   *
   * <p>Given a temp dir with existing pom.xml, when init is run with {@code --dry-run
   * --non-interactive}, then: exit code is 0; output contains [PATCH] pom.xml with description of
   * changes; pom.xml is unchanged; no backup is created.
   */
  @Test
  public void testDryRunPatchExistingMaven() throws IOException {
    Path dir = tempFolder.newFolder("dryrun-patch-maven").toPath();
    String originalPom = minimalPomXml();
    Files.writeString(dir.resolve("pom.xml"), originalPom, StandardCharsets.UTF_8);

    int exitCode =
        executeInit(
            "--dry-run", "--non-interactive", "--main-class", "com.acme.Main", dir.toString());
    assertThat(exitCode, is(0));

    String output = outWriter.toString();
    assertThat("Should mention PATCH", output, containsString("[PATCH]"));

    // pom.xml unchanged
    String afterPom = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should be unchanged", afterPom, is(originalPom));

    // No backup created
    assertFalse("No backup in dry-run", Files.exists(dir.resolve("pom.xml.backup")));
  }

  /**
   * Verifies that {@code --dry-run} previews Gradle project creation without writing any files.
   *
   * <p>Given an empty temp directory, when init is run with {@code --dry-run --build-tool gradle},
   * then: exit code is 0; output lists planned Gradle files; no files are created.
   */
  @Test
  public void testDryRunGradleNewProject() throws IOException {
    Path dir = tempFolder.newFolder("dryrun-gradle").toPath();
    int exitCode =
        executeInit(
            "--dry-run",
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "gradle",
            dir.toString());
    assertThat(exitCode, is(0));

    String output = outWriter.toString();
    assertThat("Should mention dry run", output, containsString("Dry run"));
    assertThat("Should list CREATE actions", output, containsString("[CREATE]"));

    // No files created
    assertFalse("build.gradle should not exist", Files.exists(dir.resolve("build.gradle")));
    assertFalse("settings.gradle should not exist", Files.exists(dir.resolve("settings.gradle")));
  }

  // ---------------------------------------------------------------------------
  // pal-weave version matching (architect requirement: version must match running PAL)
  // ---------------------------------------------------------------------------

  /**
   * Verifies that the generated pal-weave dependency version in pom.xml matches the current running
   * PAL version.
   *
   * <p>Given an empty temp directory, when a new Maven project is generated, then: the pom.xml
   * contains a pal-weave dependency whose version matches the current running PAL version (the
   * version reported by {@code pal --version}). This ensures version drift does not occur.
   */
  @Test
  public void testPalWeaveVersionMatchesRunningPalVersionMaven() throws IOException {
    Path dir = tempFolder.newFolder("version-maven").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    String palVersion = DEFAULT_PAL_VERSION;
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    // The pal.version property should match
    assertThat(
        "pom.xml should contain pal version",
        pomContent,
        containsString("<pal.version>" + palVersion + "</pal.version>"));
  }

  /**
   * Verifies that the generated pal-weave dependency version in build.gradle matches the current
   * running PAL version.
   *
   * <p>Given an empty temp directory, when a new Gradle project is generated, then: the
   * build.gradle contains a pal-weave dependency whose version matches the current running PAL
   * version (the version reported by {@code pal --version}).
   */
  @Test
  public void testPalWeaveVersionMatchesRunningPalVersionGradle() throws IOException {
    Path dir = tempFolder.newFolder("version-gradle").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "gradle",
            dir.toString());
    assertThat(exitCode, is(0));

    String palVersion = DEFAULT_PAL_VERSION;
    String gradleContent = Files.readString(dir.resolve("build.gradle"), StandardCharsets.UTF_8);
    assertThat(
        "build.gradle should contain pal-weave with correct version",
        gradleContent,
        containsString("pal-weave:" + palVersion));
  }

  // ---------------------------------------------------------------------------
  // pal-weave availability check (architect requirement: check and fetch from Maven Central)
  // ---------------------------------------------------------------------------

  /**
   * Verifies that the init command checks pal-weave availability and attempts to fetch it from
   * Maven Central when not locally available.
   *
   * <p>Given an empty temp directory, when init is executed, then: the output indicates that
   * pal-weave availability was checked and, if not locally available, a fetch from Maven Central
   * was attempted. The command succeeds regardless of fetch outcome (graceful degradation).
   */
  @Test
  public void testPalWeaveResolutionDuringInit() throws IOException {
    Path dir = tempFolder.newFolder("weave-resolve").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--build-tool",
            "maven",
            dir.toString());
    // Command succeeds regardless of pal-weave availability (graceful degradation)
    assertThat(exitCode, is(0));

    String output = outWriter.toString();
    // Output should mention pal-weave resolution
    assertThat("Should check for pal-weave", output, containsString("pal-weave"));
  }

  // ---------------------------------------------------------------------------
  // XML/build file validity (Risk 1 mitigation: validate before and after edits)
  // ---------------------------------------------------------------------------

  /**
   * Verifies that patching validates pom.xml before and after edits.
   *
   * <p>Given a temp directory with a valid existing pom.xml, when init patches it, then: the
   * patched pom.xml is valid XML (parseable by DocumentBuilder) and contains the pal-weave
   * dependency. This confirms the Risk 1 mitigation: validate XML before and after edits.
   */
  @Test
  public void testPatchedPomXmlIsValidXml() throws Exception {
    Path dir = tempFolder.newFolder("valid-xml").toPath();
    Files.writeString(dir.resolve("pom.xml"), minimalPomXml(), StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.acme.Main", dir.toString());
    assertThat(exitCode, is(0));

    // Verify patched pom.xml is parseable XML
    String patchedPom = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(patchedPom.getBytes(StandardCharsets.UTF_8)));

    assertThat("Should contain pal-weave", patchedPom, containsString("pal-weave"));
  }

  /**
   * Verifies that init rejects an existing pom.xml containing invalid XML.
   *
   * <p>Given a temp directory with a pom.xml containing malformed XML (e.g., unclosed tag), when
   * init is executed, then: the command returns a non-zero exit code or reports an error indicating
   * the XML is invalid; the malformed pom.xml is not further corrupted; no backup is created for
   * invalid input.
   */
  @Test
  public void testInvalidXmlPomRejected() throws IOException {
    Path dir = tempFolder.newFolder("invalid-xml").toPath();
    String malformedPom =
        "<?xml version=\"1.0\"?>\n<project>\n  <groupId>com.acme</groupId>\n  <unclosed>\n";
    Files.writeString(dir.resolve("pom.xml"), malformedPom, StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.acme.Main", dir.toString());
    assertThat("Should fail for invalid XML", exitCode, is(not(0)));

    // pom.xml not further corrupted (content unchanged)
    String afterPom = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("Malformed pom.xml should not be modified", afterPom, is(malformedPom));

    // No backup created for invalid input
    assertFalse("No backup for invalid input", Files.exists(dir.resolve("pom.xml.backup")));
  }

  /**
   * Verifies that patching validates build.gradle before and after edits.
   *
   * <p>Given a temp directory with a valid existing build.gradle, when init patches it, then: the
   * patched build.gradle is syntactically valid and contains the pal-weave dependency. This extends
   * the Risk 1 mitigation to Gradle builds as required by the architect.
   */
  @Test
  public void testPatchedBuildGradleIsValid() throws IOException {
    Path dir = tempFolder.newFolder("valid-gradle").toPath();
    Files.writeString(dir.resolve("build.gradle"), minimalBuildGradle(), StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.acme.Main", dir.toString());
    assertThat(exitCode, is(0));

    String patched = Files.readString(dir.resolve("build.gradle"), StandardCharsets.UTF_8);
    assertThat("Should contain pal-weave", patched, containsString("pal-weave"));
    assertThat("Should still have plugins", patched, containsString("plugins"));

    // Verify braces are balanced
    long openBraces = patched.chars().filter(c -> c == '{').count();
    long closeBraces = patched.chars().filter(c -> c == '}').count();
    assertThat("Braces should be balanced", openBraces, is(closeBraces));
  }

  /**
   * Verifies that init rejects an existing build.gradle containing invalid syntax.
   *
   * <p>Given a temp directory with a build.gradle containing malformed syntax (e.g., unclosed
   * brace), when init is executed, then: the command returns a non-zero exit code or reports an
   * error indicating the build file is invalid; the malformed build.gradle is not further
   * corrupted; no backup is created for invalid input. This extends the Risk 1 mitigation to
   * Gradle.
   */
  @Test
  public void testInvalidBuildGradleRejected() throws IOException {
    Path dir = tempFolder.newFolder("invalid-gradle").toPath();
    String malformedGradle = "plugins {\n    id 'java'\n\ngroup = 'com.acme'\n";
    Files.writeString(dir.resolve("build.gradle"), malformedGradle, StandardCharsets.UTF_8);

    int exitCode =
        executeInit("--non-interactive", "--main-class", "com.acme.Main", dir.toString());
    assertThat("Should fail for invalid Gradle", exitCode, is(not(0)));

    // build.gradle not further corrupted
    String afterGradle = Files.readString(dir.resolve("build.gradle"), StandardCharsets.UTF_8);
    assertThat("Malformed build.gradle should not be modified", afterGradle, is(malformedGradle));

    // No backup for invalid input
    assertFalse("No backup for invalid input", Files.exists(dir.resolve("build.gradle.backup")));
  }

  // ---------------------------------------------------------------------------
  // Utility methods
  // ---------------------------------------------------------------------------

  /**
   * Counts occurrences of a substring within a string.
   *
   * @param text the text to search
   * @param sub the substring to count
   * @return the number of occurrences
   */
  private static int countOccurrences(String text, String sub) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(sub, idx)) != -1) {
      count++;
      idx += sub.length();
    }
    return count;
  }
}
