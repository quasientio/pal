/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
    // Prompts for: mainClass, deploymentMode, feature toggles
    provider.enqueueText("com.acme.Main"); // mainClass
    provider.enqueueSelect(DeploymentMode.LOCAL); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    provider.enqueueYesNo(false); // infra

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
    provider.enqueueText("com.acme.App"); // mainClass
    provider.enqueueSelect(DeploymentMode.LOCAL); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    provider.enqueueYesNo(false); // infra

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
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueSelect(DeploymentMode.LOCAL); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    provider.enqueueYesNo(false); // infra

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
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueSelect(DeploymentMode.LOCAL); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    provider.enqueueYesNo(false); // infra

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: InitConfig has all values correctly set
    assertThat(config.getGroupId(), is("com.test"));
    assertThat(config.getArtifactId(), is("my-app"));
    assertThat(config.getProjectVersion(), is("1.0"));
    assertThat(config.getMainClass(), is("com.test.Main"));
    assertThat(config.getBuildTool(), is(BuildTool.MAVEN));
    assertThat(config.getDeploymentMode(), is(DeploymentMode.LOCAL));
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
    assertThat(config.getBuildTool(), is(BuildTool.MAVEN));
    assertThat(config.getDeploymentMode(), is(DeploymentMode.LOCAL));
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
    // Only enqueue non-identity prompts: mainClass, mode, feature toggles
    // No buildTool, groupId, artifactId, version prompts for existing projects
    provider.enqueueText("com.acme.OrderMain"); // mainClass
    provider.enqueueSelect(DeploymentMode.LOCAL); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    provider.enqueueYesNo(false); // infra

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
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueSelect(DeploymentMode.LOCAL); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    provider.enqueueYesNo(false); // infra

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: InitConfig.getBuildTool()=GRADLE
    assertThat(config.getBuildTool(), is(BuildTool.GRADLE));
  }

  /**
   * Verifies that selecting DISTRIBUTED mode causes the infrastructure flag to be prompted and/or
   * default to {@code true}.
   */
  @Test
  public void testDistributedModeEnablesInfra() {
    // Given: user selects mode=DISTRIBUTED
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueSelect(DeploymentMode.DISTRIBUTED); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    // infra: no answer enqueued, so it uses the default (true for distributed)

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: infra defaults to true for distributed mode
    assertThat(config.isInfra(), is(true));
    assertThat(config.getDeploymentMode(), is(DeploymentMode.DISTRIBUTED));
  }

  /**
   * Verifies that selecting LOCAL mode causes the infrastructure flag to default to {@code false}.
   */
  @Test
  public void testLocalModeDisablesInfraByDefault() {
    // Given: user selects mode=LOCAL
    Path dir = tempFolder.getRoot().toPath();

    TestPromptProvider provider = new TestPromptProvider();
    provider.enqueueSelect(BuildTool.MAVEN); // buildTool
    provider.enqueueText("com.test"); // groupId
    provider.enqueueText("my-app"); // artifactId
    provider.enqueueText("1.0"); // version
    provider.enqueueText("com.test.Main"); // mainClass
    provider.enqueueSelect(DeploymentMode.LOCAL); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    // infra: no answer enqueued, so it uses the default (false for local)

    // When: wizard runs
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: infra defaults to false for local mode
    assertThat(config.isInfra(), is(false));
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
    provider.enqueueText("com.acme.Main"); // mainClass
    provider.enqueueSelect(DeploymentMode.LOCAL); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    provider.enqueueYesNo(false); // infra

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
    provider.enqueueText("com.acme.Main"); // mainClass
    provider.enqueueSelect(DeploymentMode.LOCAL); // deploymentMode
    provider.enqueueYesNo(true); // sampleApp
    provider.enqueueYesNo(true); // loggingConfig
    provider.enqueueYesNo(false); // rpcPolicy
    provider.enqueueYesNo(false); // scopePolicy
    provider.enqueueYesNo(false); // interceptBundle
    provider.enqueueYesNo(false); // infra

    // When: wizard detects existing project
    InitWizard wizard = new InitWizard(provider, dir, "1.0.0");
    InitConfig config = wizard.run();

    // Then: pre-populates groupId="com.acme" in InitConfig
    assertThat(config.getGroupId(), is("com.acme"));
  }
}
