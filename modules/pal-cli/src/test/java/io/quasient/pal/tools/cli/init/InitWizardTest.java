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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link InitWizard}, the interactive prompt orchestrator that guides users through
 * project setup.
 *
 * <p>Tests use {@link TestPromptProvider} to supply deterministic answers instead of requiring a
 * real terminal. The wizard must detect existing projects (Maven or Gradle), pre-populate fields
 * from build files, apply sensible defaults, and collect all required configuration for {@link
 * InitConfig}.
 *
 * <p>The wizard prompts for intent (interceptable, intercepting, main class, kafka) rather than
 * low-level feature toggles. Infrastructure, RPC policy, intercept bundles, and pal-client
 * dependency are all derived from intent.
 */
public class InitWizardTest {

  /** Temporary directory for each test. */
  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Verifies that the wizard detects an existing Maven project by reading {@code pom.xml} and
   * pre-populates the {@code InitConfig} with the project's groupId and artifactId.
   */
  @Test
  public void testDetectsExistingMavenProject() throws IOException {
    // Given: directory with pom.xml containing groupId="com.acme", artifactId="order-service"
    Path dir = tempFolder.getRoot().toPath();
    Files.writeString(
        dir.resolve("pom.xml"),
        """
        <project>
          <groupId>com.acme</groupId>
          <artifactId>order-service</artifactId>
          <version>1.2.0</version>
        </project>
        """,
        StandardCharsets.UTF_8);

    TestPromptProvider provider = new TestPromptProvider();
    // Existing project prompts: rpc, interceptable, intercepting, mainClass, kafka
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.acme.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: InitConfig.isNewProject()=false, groupId/artifactId pre-populated from pom.xml
    assertThat(config.isNewProject(), is(false));
    assertThat(config.getGroupId(), is("com.acme"));
    assertThat(config.getArtifactId(), is("order-service"));
    assertThat(config.getBuildTool(), is(BuildTool.MAVEN));
  }

  /**
   * Verifies that the wizard detects an existing Gradle project by reading {@code build.gradle} and
   * sets the build tool to GRADLE in the resulting {@code InitConfig}.
   */
  @Test
  public void testDetectsExistingGradleProject() throws IOException {
    // Given: directory with build.gradle containing group = 'com.acme'
    Path dir = tempFolder.getRoot().toPath();
    Files.writeString(
        dir.resolve("build.gradle"),
        "group = 'com.acme'\nversion = '2.0.0'\n",
        StandardCharsets.UTF_8);

    TestPromptProvider provider = new TestPromptProvider();
    // Existing project prompts: rpc, interceptable, intercepting, mainClass, kafka
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.acme.App"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: InitConfig.isNewProject()=false, buildTool=GRADLE
    assertThat(config.isNewProject(), is(false));
    assertThat(config.getBuildTool(), is(BuildTool.GRADLE));
  }

  /**
   * Verifies that the wizard recognizes an empty directory as a new project and prompts for all
   * required fields.
   */
  @Test
  public void testDetectsNewProject() {
    // Given: empty directory (no pom.xml, no build.gradle)
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    // New project prompts: buildTool, groupId, artifactId, version, then intents
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: InitConfig.isNewProject()=true
    assertThat(config.isNewProject(), is(true));
    assertThat(config.getExistingBuildFile(), is(nullValue()));
  }

  /**
   * Verifies that the wizard correctly collects all fields for a new project and stores them in the
   * resulting {@code InitConfig}.
   */
  @Test
  public void testNewProjectWizardCollectsAllFields() {
    // Given: new project (empty directory)
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: InitConfig has all values correctly set
    assertThat(config.getGroupId(), is("com.test"));
    assertThat(config.getArtifactId(), is("my-app"));
    assertThat(config.getProjectVersion(), is("1.0"));
    assertThat(config.getMainClass(), is("com.test.Main"));
    assertThat(config.getBuildTool(), is(BuildTool.MAVEN));
  }

  /**
   * Verifies that sensible defaults are applied when the user accepts default values for all
   * optional fields.
   */
  @Test
  public void testDefaultsAppliedWhenUserAccepts() {
    // Given: new project, TestPromptProvider returns empty/default for all optional fields
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    // All prompts return defaults (empty queues trigger default values)

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: defaults applied
    assertThat(config.getProjectVersion(), is("1.0-SNAPSHOT"));
    assertThat(config.isSampleApp(), is(true));
    assertThat(config.isLoggingConfig(), is(true));
    assertThat(config.getBuildTool(), is(BuildTool.GRADLE));
    assertThat(config.isInterceptable(), is(false));
    assertThat(config.isIntercepting(), is(false));
    assertThat(config.isKafka(), is(false));
  }

  /**
   * Verifies that the wizard skips identity prompts (groupId, artifactId, version) for existing
   * Maven projects, reading those values from the {@code pom.xml} instead.
   */
  @Test
  public void testExistingProjectSkipsIdentityPrompts() throws IOException {
    // Given: existing Maven project with pom.xml containing groupId, artifactId, version
    Path dir = tempFolder.getRoot().toPath();
    Files.writeString(
        dir.resolve("pom.xml"),
        """
        <project>
          <groupId>com.acme</groupId>
          <artifactId>order-service</artifactId>
          <version>2.0.0</version>
        </project>
        """,
        StandardCharsets.UTF_8);

    TestPromptProvider provider = new TestPromptProvider();
    // Only intent prompts — no buildTool, groupId, artifactId, version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.acme.OrderMain"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: reads from pom.xml, does NOT prompt for groupId, artifactId, version
    assertThat(config.getGroupId(), is("com.acme"));
    assertThat(config.getArtifactId(), is("order-service"));
    assertThat(config.getProjectVersion(), is("2.0.0"));
    assertThat(config.getMainClass(), is("com.acme.OrderMain"));
    assertThat(config.getExistingBuildFile(), is(notNullValue()));
  }

  /**
   * Verifies that a new project wizard allows the user to select GRADLE as the build tool and that
   * the selection is reflected in the resulting {@code InitConfig}.
   */
  @Test
  public void testBuildToolSelectionForNewProject() {
    // Given: new project, PromptProvider returns buildTool=GRADLE
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.GRADLE); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-gradle-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: InitConfig.getBuildTool()=GRADLE
    assertThat(config.getBuildTool(), is(BuildTool.GRADLE));
  }

  /**
   * Verifies that selecting interceptable=true causes infra and needsEtcd to be derived as true.
   */
  @Test
  public void testInterceptableEnablesEtcdInfra() {
    // Given: user answers interceptable=yes
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(true); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: needsEtcd and isInfra are true, but no kafka
    assertThat(config.isInterceptable(), is(true));
    assertThat(config.needsEtcd(), is(true));
    assertThat(config.isInfra(), is(true));
    assertThat(config.needsKafka(), is(false));
  }

  /**
   * Verifies that selecting intercepting=true causes rpcPolicy, interceptBundle, isPalClient, and
   * needsEtcd to be derived as true.
   */
  @Test
  public void testInterceptingEnablesDerivedFlags() {
    // Given: user answers intercepting=yes
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(true); // intercepting
    provider.enqueueSelect("com.test.Main"); // run mode select
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: derived flags are all true
    assertThat(config.isIntercepting(), is(true));
    assertThat(config.getMainClass(), is("com.test.Main"));
    assertThat(config.isRpcPolicy(), is(true));
    assertThat(config.isInterceptBundle(), is(true));
    assertThat(config.isPalClient(), is(true));
    assertThat(config.needsEtcd(), is(true));
    assertThat(config.isInfra(), is(true));
  }

  /**
   * Verifies that selecting --as-service in the run mode prompt results in no main class and
   * as-service mode.
   */
  @Test
  public void testInterceptingAsServiceViaSelect() {
    // Given: user answers intercepting=yes and selects --as-service
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(true); // intercepting
    provider.enqueueSelect("Run as service (no main class)"); // run mode select
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: no main class, as-service mode
    assertThat(config.getMainClass(), is(nullValue()));
    assertThat(config.isAsService(), is(true));
    assertThat(config.isIntercepting(), is(true));
  }

  /**
   * Verifies that a plain local project (no intercepts, no kafka) has no infrastructure generated.
   */
  @Test
  public void testPlainLocalDisablesInfra() {
    // Given: user answers no to all intent questions
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: no infra
    assertThat(config.isInfra(), is(false));
    assertThat(config.needsEtcd(), is(false));
    assertThat(config.needsKafka(), is(false));
  }

  /**
   * Verifies that the PAL version in the resulting {@code InitConfig} is set from the runtime,
   * matching the provided PAL version.
   */
  @Test
  public void testPalVersionSetFromRuntime() {
    // Given: wizard runs with PAL version "1.0.0" available at runtime
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    // All defaults

    // When: InitConfig built
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: palVersion="1.0.0"
    assertThat(config.getPalVersion(), is("1.0.0"));
  }

  /**
   * Verifies that the wizard correctly parses the {@code <groupId>} element from an existing {@code
   * pom.xml} and pre-populates it in the {@code InitConfig}.
   */
  @Test
  public void testParsesGroupIdFromExistingPom() throws IOException {
    // Given: pom.xml with <groupId>com.acme</groupId>
    Path dir = tempFolder.getRoot().toPath();
    Files.writeString(
        dir.resolve("pom.xml"),
        """
        <project>
          <groupId>com.acme</groupId>
          <artifactId>my-service</artifactId>
        </project>
        """,
        StandardCharsets.UTF_8);

    TestPromptProvider provider = new TestPromptProvider();
    // Intent prompts only
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.acme.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard detects existing project
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: pre-populates groupId="com.acme" in InitConfig
    assertThat(config.getGroupId(), is("com.acme"));
  }

  /**
   * Verifies that the wizard correctly parses the {@code group} property from an existing {@code
   * build.gradle} and pre-populates it in the {@code InitConfig}.
   */
  @Test
  public void testParsesGroupIdFromExistingGradle() throws IOException {
    // Given: build.gradle with group = 'com.acme'
    Path dir = tempFolder.getRoot().toPath();
    Files.writeString(
        dir.resolve("build.gradle"),
        "group = 'com.acme'\nversion = '1.0.0'\n",
        StandardCharsets.UTF_8);

    TestPromptProvider provider = new TestPromptProvider();
    // Intent prompts only
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.acme.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard detects existing project
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: pre-populates groupId="com.acme" in InitConfig
    assertThat(config.getGroupId(), is("com.acme"));
  }

  /** Verifies that selecting kafka=true without intercepts generates kafka-only infra (no etcd). */
  @Test
  public void testKafkaWithoutInterceptsNoEtcd() {
    // Given: user answers kafka=yes, no intercepts
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueYesNo(true); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: kafka infra but no etcd
    assertThat(config.needsKafka(), is(true));
    assertThat(config.needsEtcd(), is(false));
    assertThat(config.isInfra(), is(true));
  }

  /**
   * Verifies that selecting "RPC only" enables JSON-RPC, disables weaving, skips
   * interceptable/intercepting/kafka prompts, and defaults to as-service mode.
   */
  @Test
  public void testRpcGatewayOnlySetsJsonRpcAndDisablesWeaving() {
    // Given: user selects RPC only
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-api"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("Yes, RPC only (no weaving needed)"); // rpc
    provider.enqueueSelect("Run as service (no main class)"); // run mode

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: JSON-RPC enabled, weaving disabled, as-service, no intercepts, no kafka
    assertThat(config.isJsonRpc(), is(true));
    assertThat(config.needsWeaving(), is(false));
    assertThat(config.isAsService(), is(true));
    assertThat(config.isRpcPolicy(), is(true));
    assertThat(config.isInterceptable(), is(false));
    assertThat(config.isIntercepting(), is(false));
    assertThat(config.isKafka(), is(false));
    assertThat(config.isInfra(), is(false));
  }

  /**
   * Verifies that selecting "RPC only" with a main class sets the main class and disables
   * as-service mode.
   */
  @Test
  public void testRpcGatewayOnlyWithMainClass() {
    // Given: user selects RPC only with a main class
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-api"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("Yes, RPC only (no weaving needed)"); // rpc
    provider.enqueueSelect("com.test.Main"); // run mode — select main class

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: JSON-RPC enabled, has main class, not as-service
    assertThat(config.isJsonRpc(), is(true));
    assertThat(config.needsWeaving(), is(false));
    assertThat(config.getMainClass(), is("com.test.Main"));
    assertThat(config.isAsService(), is(false));
  }

  /**
   * Verifies that selecting "RPC alongside message pipeline" enables JSON-RPC but keeps weaving
   * enabled, and continues with the full intent flow.
   */
  @Test
  public void testRpcWithPipelineKeepsWeaving() {
    // Given: user selects RPC with pipeline, then interceptable=yes
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueSelect("Yes, alongside message pipeline"); // rpc
    provider.enqueueYesNo(true); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: JSON-RPC enabled, weaving still on, interceptable set
    assertThat(config.isJsonRpc(), is(true));
    assertThat(config.needsWeaving(), is(true));
    assertThat(config.isInterceptable(), is(true));
    assertThat(config.isRpcPolicy(), is(true));
    assertThat(config.needsEtcd(), is(true));
  }

  /**
   * Verifies that the wizard detects an existing multi-module Gradle project (with
   * settings.gradle.kts and a subproject build.gradle.kts) and sets the build tool to GRADLE in the
   * resulting config.
   */
  @Test
  public void testDetectsExistingMultiModuleGradleProject() throws IOException {
    // Given: multi-module Gradle layout (settings.gradle.kts at root, app/build.gradle.kts)
    Path dir = tempFolder.getRoot().toPath();
    Files.writeString(
        dir.resolve("settings.gradle.kts"),
        "rootProject.name = \"grapp\"\ninclude(\"app\")\n",
        StandardCharsets.UTF_8);
    Path appDir = Files.createDirectories(dir.resolve("app"));
    Files.writeString(
        appDir.resolve("build.gradle.kts"),
        "group = \"org.example\"\nversion = \"1.0.0\"\n",
        StandardCharsets.UTF_8);

    TestPromptProvider provider = new TestPromptProvider();
    // Existing project prompts: rpc, interceptable, intercepting, mainClass, kafka
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    provider.enqueueText("org.example.Main"); // mainClass
    provider.enqueueYesNo(false); // kafka

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: detected as existing GRADLE project with identity parsed from subproject build file
    assertThat(config.isNewProject(), is(false));
    assertThat(config.getBuildTool(), is(BuildTool.GRADLE));
    assertThat(config.getGroupId(), is("org.example"));
    assertThat(config.getExistingBuildFile(), is(notNullValue()));
    assertThat(config.getExistingBuildFile().getFileName().toString(), is("build.gradle.kts"));
  }

  // ---------------------------------------------------------------------------
  // CLI flag override tests
  // ---------------------------------------------------------------------------

  /**
   * Verifies that CLI flag overrides skip the corresponding wizard prompts. When {@code --group-id}
   * and {@code --interceptable} are provided, the wizard should not prompt for them.
   */
  @Test
  public void testOverridesSkipPrompts() throws IOException {
    Path dir = tempFolder.newFolder("overrides").toPath();
    TestPromptProvider provider = new TestPromptProvider();

    // Only enqueue answers for prompts that are NOT overridden:
    // build tool, artifactId, version, rpc, intercepting, mainClass, kafka
    provider.enqueueSelect(BuildTool.MAVEN); // build tool (not overridden)
    provider.enqueueText("my-app"); // artifactId (not overridden)
    provider.enqueueText("1.0-SNAPSHOT"); // version (not overridden)
    provider.enqueueSelect("No"); // rpc (not overridden)
    // interceptable is overridden — no prompt
    provider.enqueueYesNo(false); // intercepting (not overridden)
    provider.enqueueText("org.cometera.Main"); // mainClass (not overridden)
    provider.enqueueYesNo(false); // kafka (not overridden)

    WizardOverrides overrides =
        WizardOverrides.builder().groupId("org.cometera").interceptable(true).build();

    InitWizard wizard = new InitWizard(provider, dir, "1.0.0", overrides);
    InitConfig config = wizard.run();

    assertThat(config.getGroupId(), is("org.cometera"));
    assertThat(config.isInterceptable(), is(true));
    assertThat(config.getArtifactId(), is("my-app"));
  }

  /**
   * Verifies that overriding {@code --main-class} skips both the text prompt and the select prompt
   * for run mode.
   */
  @Test
  public void testMainClassOverrideSkipsPrompt() throws IOException {
    Path dir = tempFolder.newFolder("main-override").toPath();
    TestPromptProvider provider = new TestPromptProvider();

    // Only enqueue non-overridden prompts
    provider.enqueueSelect(BuildTool.MAVEN); // build tool
    provider.enqueueText("com.example"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0-SNAPSHOT"); // version
    provider.enqueueSelect("No"); // rpc
    provider.enqueueYesNo(false); // interceptable
    provider.enqueueYesNo(false); // intercepting
    // mainClass is overridden — no prompt
    provider.enqueueYesNo(false); // kafka

    WizardOverrides overrides = WizardOverrides.builder().mainClass("com.example.App").build();

    InitWizard wizard = new InitWizard(provider, dir, "1.0.0", overrides);
    InitConfig config = wizard.run();

    assertThat(config.getMainClass(), is("com.example.App"));
  }

  /**
   * Verifies that overriding all intent flags skips all intent prompts, requiring only identity
   * prompts.
   */
  @Test
  public void testAllIntentOverridesSkipAllIntentPrompts() throws IOException {
    Path dir = tempFolder.newFolder("all-intents").toPath();
    TestPromptProvider provider = new TestPromptProvider();

    // Only identity prompts remain (no intent prompts)
    provider.enqueueSelect(BuildTool.GRADLE); // build tool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("test-app"); // artifactId
    provider.enqueueText("2.0.0"); // version

    WizardOverrides overrides =
        WizardOverrides.builder()
            .jsonRpc(true)
            .interceptable(true)
            .intercepting(true)
            .kafka(true)
            .mainClass("com.test.Main")
            .build();

    InitWizard wizard = new InitWizard(provider, dir, "1.0.0", overrides);
    InitConfig config = wizard.run();

    assertThat(config.isJsonRpc(), is(true));
    assertThat(config.isInterceptable(), is(true));
    assertThat(config.isIntercepting(), is(true));
    assertThat(config.isKafka(), is(true));
    assertThat(config.getMainClass(), is("com.test.Main"));
    assertThat(config.getBuildTool(), is(BuildTool.GRADLE));
  }
}
