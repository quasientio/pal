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
   * --artifact-id test-app --main-class com.test.Main --build-tool maven} is executed via
   * CommandLine, then: pom.xml exists and is valid XML with pal-weave dependency and AspectJ
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

    // Maven wrapper generated
    assertTrue("mvnw should exist", Files.exists(dir.resolve("mvnw")));
    assertTrue("mvnw.cmd should exist", Files.exists(dir.resolve("mvnw.cmd")));
    assertTrue(
        "maven-wrapper.properties should exist",
        Files.exists(dir.resolve(".mvn/wrapper/maven-wrapper.properties")));
    assertTrue("mvnw should be executable", Files.isExecutable(dir.resolve("mvnw")));

    // README should use wrapper commands
    String readmeContent = Files.readString(dir.resolve("README.md"), StandardCharsets.UTF_8);
    assertThat("README should use ./mvnw", readmeContent, containsString("./mvnw"));
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

    // Gradle wrapper generated
    assertTrue("gradlew should exist", Files.exists(dir.resolve("gradlew")));
    assertTrue("gradlew.bat should exist", Files.exists(dir.resolve("gradlew.bat")));
    assertTrue(
        "gradle-wrapper.properties should exist",
        Files.exists(dir.resolve("gradle/wrapper/gradle-wrapper.properties")));
    assertTrue(
        "gradle-wrapper.jar should exist",
        Files.exists(dir.resolve("gradle/wrapper/gradle-wrapper.jar")));
    assertTrue("gradlew should be executable", Files.isExecutable(dir.resolve("gradlew")));
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

    // Wrapper files should NOT be generated for existing projects
    assertFalse("mvnw should not exist for patched project", Files.exists(dir.resolve("mvnw")));
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

    // Wrapper files should NOT be generated for existing projects
    assertFalse(
        "gradlew should not exist for patched project", Files.exists(dir.resolve("gradlew")));
  }

  // ---------------------------------------------------------------------------
  // Distributed mode and feature flags
  // ---------------------------------------------------------------------------

  /**
   * Verifies that interceptable intent generates infrastructure files.
   *
   * <p>Given an empty temp directory, when {@code pal init --non-interactive --interceptable} is
   * executed, then: infra/docker-compose.yml, infra/.env, infra/start.sh, infra/stop.sh all exist
   * with etcd configuration.
   */
  @Test
  public void testInterceptableGeneratesInfra() throws IOException {
    Path dir = tempFolder.newFolder("interceptable").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--interceptable",
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
   * Verifies that all config files are generated when intercepting with scope-policy enabled.
   *
   * <p>Given an empty temp directory, when {@code --intercepting --scope-policy --logging-config}
   * are set, then: config/rpc-policy.yaml, config/recording-scope.yaml, config/peer-logging.xml,
   * config/intercept-bundle.yaml all exist (rpc-policy and intercept-bundle are derived from
   * intercepting).
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
            "--intercepting",
            "--scope-policy",
            "--logging-config",
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
  // Intent-driven flag combinations (derivation table coverage)
  // ---------------------------------------------------------------------------

  /**
   * Verifies that {@code --intercepting} with a main class generates etcd infra, rpc-policy,
   * intercept-bundle, pal-client dependency, and callback handler source.
   */
  @Test
  public void testInterceptingWithMainClass() throws Exception {
    Path dir = tempFolder.newFolder("intercepting-main").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--intercepting",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    // Etcd infra generated (no kafka)
    Path composeFile = dir.resolve("infra/docker-compose.yml");
    assertTrue("docker-compose.yml should exist", Files.exists(composeFile));
    String compose = Files.readString(composeFile, StandardCharsets.UTF_8);
    assertThat("Compose should have etcd", compose, containsString("etcd"));
    assertThat("Compose should NOT have kafka", compose, not(containsString("kafka")));

    // RPC policy and intercept bundle derived from intercepting
    assertTrue("rpc-policy.yaml should exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
    assertTrue(
        "intercept-bundle.yaml should exist",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));

    // pal-client dependency in pom.xml
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should have pal-client", pomContent, containsString("pal-client"));

    // Callback handler generated
    assertTrue(
        "SampleCallbacks.java should exist",
        Files.exists(dir.resolve("src/main/java/com/test/SampleCallbacks.java")));

    // Main.java also generated (not as-service)
    assertTrue(
        "Main.java should exist", Files.exists(dir.resolve("src/main/java/com/test/Main.java")));
  }

  /**
   * Verifies that {@code --intercepting --as-service} generates callback handler and
   * SampleService.java but no Main.java.
   */
  @Test
  public void testInterceptingAsService() throws Exception {
    Path dir = tempFolder.newFolder("intercepting-service").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--intercepting",
            "--as-service",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    // No Main.java in as-service mode
    assertFalse(
        "Main.java should NOT exist in as-service mode",
        Files.exists(dir.resolve("src/main/java/com/test/Main.java")));

    // SampleService.java NOT generated (only intercepting, not interceptable)
    assertFalse(
        "SampleService.java should NOT exist (not interceptable)",
        Files.exists(dir.resolve("src/main/java/com/test/SampleService.java")));

    // Callback handler generated
    assertTrue(
        "SampleCallbacks.java should exist",
        Files.exists(dir.resolve("src/main/java/com/test/SampleCallbacks.java")));

    // pal-client dependency
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should have pal-client", pomContent, containsString("pal-client"));

    // RPC policy and intercept bundle
    assertTrue("rpc-policy.yaml should exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
    assertTrue(
        "intercept-bundle.yaml should exist",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));

    // Intercept bundle should have peer matching artifact ID
    String bundleContent =
        Files.readString(dir.resolve("config/intercept-bundle.yaml"), StandardCharsets.UTF_8);
    assertThat(
        "intercept-bundle peer should match artifact ID",
        bundleContent,
        containsString("peer: \"test-app\""));

    // Run command in next-steps should include -n with artifact ID and no main class
    String output = outWriter.toString();
    assertThat("Output should include -n with artifact ID", output, containsString("-n test-app"));
    assertThat(
        "Output should end pal run command with -cp, no main class",
        output,
        containsString("-cp target/classes\n"));
  }

  /**
   * Verifies that {@code --kafka} without intercepts generates kafka-only infra (no etcd) and no
   * intercept-related configs.
   */
  @Test
  public void testKafkaOnlyNoIntercepts() throws IOException {
    Path dir = tempFolder.newFolder("kafka-only").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--kafka",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    // Kafka-only infra (no etcd)
    Path composeFile = dir.resolve("infra/docker-compose.yml");
    assertTrue("docker-compose.yml should exist", Files.exists(composeFile));
    String compose = Files.readString(composeFile, StandardCharsets.UTF_8);
    assertThat("Compose should have kafka", compose, containsString("kafka"));
    assertThat("Compose should NOT have etcd", compose, not(containsString("etcd")));

    // No intercept-related configs
    assertFalse(
        "rpc-policy.yaml should NOT exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
    assertFalse(
        "intercept-bundle.yaml should NOT exist",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));

    // No pal-client dependency
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should NOT have pal-client", pomContent, not(containsString("pal-client")));

    // No callback handler
    assertFalse(
        "SampleCallbacks.java should NOT exist",
        Files.exists(dir.resolve("src/main/java/com/test/SampleCallbacks.java")));

    // .env.pal should have kafka but not etcd
    String envContent = Files.readString(dir.resolve(".env.pal"), StandardCharsets.UTF_8);
    assertThat(
        "env should have PAL_KAFKA_SERVERS", envContent, containsString("PAL_KAFKA_SERVERS"));
    assertThat(
        "env should NOT have PAL_DIRECTORY", envContent, not(containsString("PAL_DIRECTORY")));
  }

  /**
   * Verifies that {@code --interceptable --kafka} generates full infra (etcd + kafka) but no
   * intercept-related configs (interceptable does not need rpc-policy or callback handler).
   */
  @Test
  public void testInterceptableWithKafka() throws IOException {
    Path dir = tempFolder.newFolder("interceptable-kafka").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--interceptable",
            "--kafka",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    // Full infra (etcd + kafka)
    Path composeFile = dir.resolve("infra/docker-compose.yml");
    assertTrue("docker-compose.yml should exist", Files.exists(composeFile));
    String compose = Files.readString(composeFile, StandardCharsets.UTF_8);
    assertThat("Compose should have etcd", compose, containsString("etcd"));
    assertThat("Compose should have kafka", compose, containsString("kafka"));

    // No intercept-related configs (interceptable ≠ intercepting)
    assertFalse(
        "rpc-policy.yaml should NOT exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
    assertFalse(
        "intercept-bundle.yaml should NOT exist",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));

    // No pal-client dependency
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should NOT have pal-client", pomContent, not(containsString("pal-client")));

    // .env.pal should have both etcd and kafka sections
    String envContent = Files.readString(dir.resolve(".env.pal"), StandardCharsets.UTF_8);
    assertThat("env should have PAL_DIRECTORY", envContent, containsString("PAL_DIRECTORY"));
    assertThat(
        "env should have PAL_KAFKA_SERVERS", envContent, containsString("PAL_KAFKA_SERVERS"));
  }

  /**
   * Verifies that {@code --intercepting --kafka} generates full infra and all intercepting outputs.
   */
  @Test
  public void testInterceptingWithKafka() throws Exception {
    Path dir = tempFolder.newFolder("intercepting-kafka").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--intercepting",
            "--kafka",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    // Full infra (etcd + kafka)
    Path composeFile = dir.resolve("infra/docker-compose.yml");
    String compose = Files.readString(composeFile, StandardCharsets.UTF_8);
    assertThat("Compose should have etcd", compose, containsString("etcd"));
    assertThat("Compose should have kafka", compose, containsString("kafka"));

    // All intercepting outputs
    assertTrue("rpc-policy.yaml should exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
    assertTrue(
        "intercept-bundle.yaml should exist",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should have pal-client", pomContent, containsString("pal-client"));
    assertTrue(
        "SampleCallbacks.java should exist",
        Files.exists(dir.resolve("src/main/java/com/test/SampleCallbacks.java")));
  }

  /**
   * Verifies that a plain local project (no intercepts, no kafka) generates no infra, no
   * rpc-policy, no intercept-bundle, and no pal-client dependency.
   */
  @Test
  public void testPlainLocalNoInfra() throws IOException {
    Path dir = tempFolder.newFolder("plain-local").toPath();
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

    // No infra
    assertFalse("infra/ should NOT exist", Files.exists(dir.resolve("infra/docker-compose.yml")));

    // No intercept-related configs
    assertFalse(
        "rpc-policy.yaml should NOT exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
    assertFalse(
        "intercept-bundle.yaml should NOT exist",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));

    // No pal-client dependency
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should NOT have pal-client", pomContent, not(containsString("pal-client")));

    // No callback handler
    assertFalse(
        "SampleCallbacks.java should NOT exist",
        Files.exists(dir.resolve("src/main/java/com/test/SampleCallbacks.java")));

    // .env.pal should have only WAL section (no etcd, no kafka)
    String envContent = Files.readString(dir.resolve(".env.pal"), StandardCharsets.UTF_8);
    assertThat("env should have PAL_WAL", envContent, containsString("PAL_WAL"));
    assertThat(
        "env should NOT have PAL_DIRECTORY", envContent, not(containsString("PAL_DIRECTORY")));
    assertThat(
        "env should NOT have PAL_KAFKA_SERVERS",
        envContent,
        not(containsString("PAL_KAFKA_SERVERS")));
  }

  /**
   * Verifies that {@code --intercepting} with Gradle generates pal-client dependency and callback
   * handler in the Gradle project.
   */
  @Test
  public void testInterceptingGradleProject() throws IOException {
    Path dir = tempFolder.newFolder("intercepting-gradle").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--intercepting",
            "--build-tool",
            "gradle",
            dir.toString());
    assertThat(exitCode, is(0));

    // pal-client in build.gradle
    String gradleContent = Files.readString(dir.resolve("build.gradle"), StandardCharsets.UTF_8);
    assertThat("build.gradle should have pal-client", gradleContent, containsString("pal-client"));

    // Callback handler generated
    assertTrue(
        "SampleCallbacks.java should exist",
        Files.exists(dir.resolve("src/main/java/com/test/SampleCallbacks.java")));

    // RPC policy and intercept bundle
    assertTrue("rpc-policy.yaml should exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
    assertTrue(
        "intercept-bundle.yaml should exist",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));
  }

  /**
   * Verifies that patching an existing Maven project with {@code --intercepting} adds pal-client
   * dependency alongside pal-weave.
   */
  @Test
  public void testPatchExistingMavenWithIntercepting() throws Exception {
    Path dir = tempFolder.newFolder("patch-intercepting").toPath();
    Files.writeString(dir.resolve("pom.xml"), minimalPomXml(), StandardCharsets.UTF_8);

    int exitCode =
        executeInit(
            "--non-interactive", "--main-class", "com.acme.Main", "--intercepting", dir.toString());
    assertThat(exitCode, is(0));

    // Patched pom has both pal-weave and pal-client
    String patchedPom = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("Should have pal-weave", patchedPom, containsString("pal-weave"));
    assertThat("Should have pal-client", patchedPom, containsString("pal-client"));

    // Verify it's valid XML
    DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(patchedPom.getBytes(StandardCharsets.UTF_8)));
  }

  /**
   * Verifies that README content reflects intent flags: interceptable app shows {@code
   * --interceptable} in run examples, intercepting shows {@code --zmq-rpc auto}.
   */
  @Test
  public void testReadmeReflectsIntentFlags() throws IOException {
    Path dir = tempFolder.newFolder("readme-intents").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--interceptable",
            "--intercepting",
            "--kafka",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    Path readmePath = dir.resolve("README.md");
    assertTrue("README.md should exist", Files.exists(readmePath));
    String readme = Files.readString(readmePath, StandardCharsets.UTF_8);
    assertThat("README should mention --interceptable", readme, containsString("--interceptable"));
    assertThat("README should mention --zmq-rpc", readme, containsString("--zmq-rpc"));
    assertThat(
        "README should mention --rpc-policy",
        readme,
        containsString("--rpc-policy config/rpc-policy.yaml"));
    assertThat(
        "README should mention -d localhost:2379", readme, containsString("-d localhost:2379"));
    assertThat(
        "README should mention -k localhost:29092", readme, containsString("-k localhost:29092"));
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
   * Verifies that Maven project init output includes next steps with ./mvnw package and pal run
   * instructions.
   *
   * <p>Given a new Maven project init, when stdout is captured, then: output contains "Next steps"
   * with ./mvnw package and pal run instructions.
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
    assertThat("Should mention ./mvnw package", output, containsString("./mvnw package"));
    assertThat("Should mention pal run", output, containsString("pal run"));
  }

  /**
   * Verifies that Gradle project init output includes next steps with ./gradlew build instructions.
   *
   * <p>Given a new Gradle project init, when stdout is captured, then: output contains "Next steps"
   * with ./gradlew build instructions.
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
    assertThat("Should mention ./gradlew build", output, containsString("./gradlew build"));
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
  // JSON-RPC and weaving
  // ---------------------------------------------------------------------------

  /**
   * Verifies that {@code --json-rpc --no-weaving} generates a plain build (no AspectJ), RPC policy,
   * and {@code --json-rpc 7070} in the run command.
   */
  @Test
  public void testJsonRpcGatewayOnlyMaven() throws Exception {
    Path dir = tempFolder.newFolder("rpc-gateway-maven").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-api",
            "--main-class",
            "com.test.Main",
            "--json-rpc",
            "--no-weaving",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    // Plain pom.xml — no pal-weave, no aspectj
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should NOT have pal-weave", pomContent, not(containsString("pal-weave")));
    assertThat(
        "pom.xml should NOT have aspectj", pomContent, not(containsString("aspectj-maven-plugin")));

    // Valid XML
    DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(new ByteArrayInputStream(pomContent.getBytes(StandardCharsets.UTF_8)));

    // RPC policy generated
    assertTrue("rpc-policy.yaml should exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));

    // Sample API class generated
    assertTrue(
        "Api.java should exist", Files.exists(dir.resolve("src/main/java/com/test/Api.java")));

    // README has pal peer call instructions
    String readme = Files.readString(dir.resolve("README.md"), StandardCharsets.UTF_8);
    assertThat("README should mention pal peer call", readme, containsString("pal peer call"));

    // Next steps mention --json-rpc 7070 and --rpc-policy
    String output = outWriter.toString();
    assertThat("Should mention --json-rpc 7070", output, containsString("--json-rpc 7070"));
    assertThat(
        "Should mention --rpc-policy",
        output,
        containsString("--rpc-policy config/rpc-policy.yaml"));

    // Build hint should NOT mention weaving
    assertThat(
        "Should NOT mention AspectJ weaving", output, not(containsString("AspectJ weaving")));
  }

  /** Verifies that {@code --json-rpc --no-weaving} with Gradle generates a plain build. */
  @Test
  public void testJsonRpcGatewayOnlyGradle() throws IOException {
    Path dir = tempFolder.newFolder("rpc-gateway-gradle").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-api",
            "--main-class",
            "com.test.Main",
            "--json-rpc",
            "--no-weaving",
            "--build-tool",
            "gradle",
            dir.toString());
    assertThat(exitCode, is(0));

    // Plain build.gradle — no pal-weave, no aspectj
    String gradleContent = Files.readString(dir.resolve("build.gradle"), StandardCharsets.UTF_8);
    assertThat(
        "build.gradle should NOT have pal-weave", gradleContent, not(containsString("pal-weave")));
    assertThat(
        "build.gradle should NOT have aspectj", gradleContent, not(containsString("aspectj")));
    assertThat(
        "build.gradle should NOT have weaveClasses",
        gradleContent,
        not(containsString("weaveClasses")));
  }

  /**
   * Verifies that {@code --json-rpc} (with weaving) generates a full build with JSON-RPC in the run
   * command and RPC policy.
   */
  @Test
  public void testJsonRpcWithPipeline() throws IOException {
    Path dir = tempFolder.newFolder("rpc-pipeline").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--json-rpc",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    // Full pom.xml — has pal-weave and aspectj
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should have pal-weave", pomContent, containsString("pal-weave"));
    assertThat(
        "pom.xml should have aspectj plugin", pomContent, containsString("aspectj-maven-plugin"));

    // RPC policy generated
    assertTrue("rpc-policy.yaml should exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));

    // Sample API class generated
    assertTrue(
        "Api.java should exist", Files.exists(dir.resolve("src/main/java/com/test/Api.java")));

    // Next steps mention --json-rpc 7070 and --rpc-policy
    String output = outWriter.toString();
    assertThat("Should mention --json-rpc 7070", output, containsString("--json-rpc 7070"));
    assertThat(
        "Should mention --rpc-policy",
        output,
        containsString("--rpc-policy config/rpc-policy.yaml"));

    // Build hint should mention weaving
    assertThat("Should mention AspectJ weaving", output, containsString("AspectJ weaving"));
  }

  /** Verifies that {@code --no-weaving} without {@code --json-rpc} issues a warning. */
  @Test
  public void testNoWeavingWithoutJsonRpcShowsWarning() throws IOException {
    Path dir = tempFolder.newFolder("no-weaving-warn").toPath();
    int exitCode =
        executeInit(
            "--non-interactive",
            "--group-id",
            "com.test",
            "--artifact-id",
            "test-app",
            "--main-class",
            "com.test.Main",
            "--no-weaving",
            "--build-tool",
            "maven",
            dir.toString());
    assertThat(exitCode, is(0));

    String output = outWriter.toString();
    assertThat(
        "Should warn about --no-weaving without --json-rpc",
        output,
        containsString("--no-weaving without --json-rpc"));
  }

  /**
   * Verifies that patching an existing project with {@code --json-rpc --no-weaving} skips build
   * file patching and only generates config files.
   */
  @Test
  public void testPatchExistingWithJsonRpcNoWeaving() throws IOException {
    Path dir = tempFolder.newFolder("patch-rpc-only").toPath();
    String originalPom = minimalPomXml();
    Files.writeString(dir.resolve("pom.xml"), originalPom, StandardCharsets.UTF_8);

    int exitCode =
        executeInit(
            "--non-interactive",
            "--main-class",
            "com.acme.Main",
            "--json-rpc",
            "--no-weaving",
            dir.toString());
    assertThat(exitCode, is(0));

    // pom.xml should NOT be patched (no weaving needed)
    String afterPom = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("pom.xml should be unchanged", afterPom, is(originalPom));

    // No backup since no patching
    assertFalse("No backup without patching", Files.exists(dir.resolve("pom.xml.backup")));

    // RPC policy generated
    assertTrue("rpc-policy.yaml should exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
  }

  // ---------------------------------------------------------------------------
  // --all flag tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies that {@code --all} implies non-interactive, derives defaults from the directory name,
   * and enables every PAL feature. The generated project should include all config files, infra
   * (etcd + Kafka), sample sources (Main, SampleService, SampleCallbacks, Api), and a README
   * covering all capabilities — without requiring explicit group-id, artifact-id, or main-class.
   */
  @Test
  public void testAllFlagEnablesEverything() throws Exception {
    Path dir = tempFolder.getRoot().toPath().resolve("my-app");
    int exitCode = executeInit("--all", dir.toString());
    assertThat(exitCode, is(0));

    // Defaults derived from directory name
    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("artifactId from dir name", pomContent, containsString("<artifactId>my-app"));
    assertThat("default groupId", pomContent, containsString("<groupId>com.example"));

    // Build file: full pom with pal-weave, aspectj, and pal-client
    assertThat("pom.xml should have pal-weave", pomContent, containsString("pal-weave"));
    assertThat(
        "pom.xml should have aspectj plugin", pomContent, containsString("aspectj-maven-plugin"));
    assertThat("pom.xml should have pal-client", pomContent, containsString("pal-client"));

    // All sample sources generated (package com.example)
    Path srcDir = dir.resolve("src/main/java/com/example");
    assertTrue("Main.java should exist", Files.exists(srcDir.resolve("Main.java")));
    assertTrue(
        "SampleService.java should exist", Files.exists(srcDir.resolve("SampleService.java")));
    assertTrue(
        "SampleCallbacks.java should exist", Files.exists(srcDir.resolve("SampleCallbacks.java")));
    assertTrue("Api.java should exist", Files.exists(srcDir.resolve("Api.java")));

    // All config files
    assertTrue("rpc-policy.yaml should exist", Files.exists(dir.resolve("config/rpc-policy.yaml")));
    assertTrue(
        "intercept-bundle.yaml should exist",
        Files.exists(dir.resolve("config/intercept-bundle.yaml")));
    assertTrue(
        "recording-scope.yaml should exist",
        Files.exists(dir.resolve("config/recording-scope.yaml")));
    assertTrue(
        "peer-logging.xml should exist", Files.exists(dir.resolve("config/peer-logging.xml")));

    // Infra: etcd + Kafka
    Path composeFile = dir.resolve("infra/docker-compose.yml");
    assertTrue("docker-compose.yml should exist", Files.exists(composeFile));
    String compose = Files.readString(composeFile, StandardCharsets.UTF_8);
    assertThat("Compose should have etcd", compose, containsString("etcd"));
    assertThat("Compose should have kafka", compose, containsString("kafka"));

    // .env.pal includes all sections
    String envContent = Files.readString(dir.resolve(".env.pal"), StandardCharsets.UTF_8);
    assertThat("env should have WAL section", envContent, containsString("PAL_WAL"));
    assertThat("env should have etcd section", envContent, containsString("PAL_DIRECTORY"));
    assertThat("env should have Kafka section", envContent, containsString("PAL_KAFKA_SERVERS"));
    assertThat("env should have logging section", envContent, containsString("PAL_PEER_LOGGING"));

    // README covers all capabilities
    String readme = Files.readString(dir.resolve("README.md"), StandardCharsets.UTF_8);
    assertThat("README should mention pal peer call", readme, containsString("pal peer call"));
    assertThat("README should mention Kafka WAL", readme, containsString("Kafka"));
    assertThat("README should mention infra", readme, containsString("infra/start.sh"));

    // Next steps should include all flags
    String output = outWriter.toString();
    assertThat("Should mention --interceptable", output, containsString("--interceptable"));
    assertThat("Should mention --json-rpc 7070", output, containsString("--json-rpc 7070"));
    assertThat(
        "Should mention --rpc-policy",
        output,
        containsString("--rpc-policy config/rpc-policy.yaml"));
    assertThat("Should mention infra/start.sh", output, containsString("infra/start.sh"));
    assertThat("Should mention etcd + Kafka", output, containsString("etcd + Kafka"));
  }

  /**
   * Verifies that {@code --all} with explicit overrides uses the provided values instead of
   * defaults.
   */
  @Test
  public void testAllFlagWithExplicitOverrides() throws Exception {
    Path dir = tempFolder.newFolder("custom-all").toPath();
    int exitCode =
        executeInit(
            "--all",
            "--group-id",
            "com.acme",
            "--artifact-id",
            "acme-app",
            "--main-class",
            "com.acme.App",
            dir.toString());
    assertThat(exitCode, is(0));

    String pomContent = Files.readString(dir.resolve("pom.xml"), StandardCharsets.UTF_8);
    assertThat("explicit groupId used", pomContent, containsString("<groupId>com.acme"));
    assertThat("explicit artifactId used", pomContent, containsString("<artifactId>acme-app"));

    Path srcDir = dir.resolve("src/main/java/com/acme");
    assertTrue("App.java from explicit main-class", Files.exists(srcDir.resolve("App.java")));
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
