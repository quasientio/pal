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
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Paths;
import org.junit.Test;

/**
 * Unit test specifications for {@code InitConfig}, the central POJO that carries all wizard choices
 * through the generation pipeline.
 *
 * <p>InitConfig holds project identity (groupId, artifactId, version, mainClass, packageName),
 * deployment mode, feature toggles (sampleApp, rpcPolicy, scopePolicy, loggingConfig,
 * interceptBundle, infra), build tool selection, PAL version, and optional existing build file
 * path. These tests verify default values, derived properties (package name inference, source
 * directory layout, new-vs-existing project detection), and enum convenience methods.
 */
public class InitConfigTest {

  /**
   * Verifies that an {@code InitConfig} built with only the required fields (groupId, artifactId,
   * mainClass) has correct default values for all optional fields.
   *
   * <p>Expected defaults: version is {@code "1.0-SNAPSHOT"}, mode is {@code LOCAL}, sampleApp is
   * {@code true}, rpcPolicy is {@code false}, scopePolicy is {@code false}, loggingConfig is {@code
   * true}, interceptBundle is {@code false}, infra is {@code false}, buildTool is {@code MAVEN}.
   */
  @Test
  public void testDefaultValues() {
    // Given: InitConfig built with only required fields (groupId, artifactId, mainClass)
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .mainClass("com.example.Main")
            .build();

    // Then: default values are correct
    assertThat(config.getProjectVersion(), is("1.0-SNAPSHOT"));
    assertThat(config.getDeploymentMode(), is(DeploymentMode.LOCAL));
    assertThat(config.isSampleApp(), is(true));
    assertThat(config.isRpcPolicy(), is(false));
    assertThat(config.isScopePolicy(), is(false));
    assertThat(config.isLoggingConfig(), is(true));
    assertThat(config.isInterceptBundle(), is(false));
    assertThat(config.isInfra(), is(false));
    assertThat(config.getBuildTool(), is(BuildTool.MAVEN));
    assertThat(config.isForce(), is(false));
    assertThat(config.isDryRun(), is(false));
  }

  /**
   * Verifies that when no explicit package name is provided, {@code getPackageName()} returns the
   * groupId as the inferred package name.
   *
   * <p>Uses an {@code InitConfig} with {@code groupId="com.example"} and no explicit package name
   * set.
   */
  @Test
  public void testPackageNameInferredFromGroupId() {
    // Given: InitConfig with groupId="com.example" and no explicit package
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .mainClass("com.example.Main")
            .build();

    // Then: getPackageName() returns the groupId
    assertThat(config.getPackageName(), is("com.example"));
  }

  /**
   * Verifies that an explicitly set package name takes precedence over the groupId-based inference.
   *
   * <p>Uses an {@code InitConfig} with {@code groupId="com.example"} and explicit {@code
   * packageName="com.example.app"}.
   */
  @Test
  public void testExplicitPackageOverridesGroupId() {
    // Given: InitConfig with groupId="com.example" and package="com.example.app"
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .mainClass("com.example.Main")
            .packageName("com.example.app")
            .build();

    // Then: getPackageName() returns the explicit package
    assertThat(config.getPackageName(), is("com.example.app"));
  }

  /**
   * Verifies that {@code isNewProject()} returns {@code true} when no existing build file path is
   * set (i.e., {@code existingBuildFile} is {@code null}).
   */
  @Test
  public void testIsNewProject() {
    // Given: InitConfig with existingBuildFile set to null (default)
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .mainClass("com.example.Main")
            .build();

    // Then: isNewProject() returns true
    assertThat(config.isNewProject(), is(true));
  }

  /**
   * Verifies that {@code isNewProject()} returns {@code false} when an existing build file path is
   * set (i.e., {@code existingBuildFile} points to a {@code Path}).
   */
  @Test
  public void testIsExistingProject() {
    // Given: InitConfig with existingBuildFile set to a Path
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .mainClass("com.example.Main")
            .existingBuildFile(Paths.get("pom.xml"))
            .build();

    // Then: isNewProject() returns false
    assertThat(config.isNewProject(), is(false));
  }

  /**
   * Verifies that the {@code BuildTool} enum has the expected values ({@code MAVEN} and {@code
   * GRADLE}) and their string representations are correct.
   */
  @Test
  public void testBuildToolEnum() {
    // Given/When: BuildTool enum values
    assertThat(BuildTool.MAVEN.name(), is("MAVEN"));
    assertThat(BuildTool.GRADLE.name(), is("GRADLE"));
    assertThat(BuildTool.values().length, is(2));
  }

  /**
   * Verifies that the {@code DeploymentMode} enum convenience methods work correctly: {@code
   * LOCAL.isLocal()} returns {@code true}, {@code DISTRIBUTED.isDistributed()} returns {@code
   * true}, and {@code BOTH} returns {@code true} for both {@code isLocal()} and {@code
   * isDistributed()}.
   */
  @Test
  public void testModeEnum() {
    // LOCAL
    assertThat(DeploymentMode.LOCAL.isLocal(), is(true));
    assertThat(DeploymentMode.LOCAL.isDistributed(), is(false));

    // DISTRIBUTED
    assertThat(DeploymentMode.DISTRIBUTED.isLocal(), is(false));
    assertThat(DeploymentMode.DISTRIBUTED.isDistributed(), is(true));

    // BOTH
    assertThat(DeploymentMode.BOTH.isLocal(), is(true));
    assertThat(DeploymentMode.BOTH.isDistributed(), is(true));
  }

  /**
   * Verifies that {@code getSourceDirectory()} converts the package name into the correct Maven/
   * Gradle source directory layout by replacing dots with path separators.
   *
   * <p>Uses an {@code InitConfig} with {@code packageName="com.example.app"} and expects the result
   * {@code "src/main/java/com/example/app"}.
   */
  @Test
  public void testSourceDirectoryLayout() {
    // Given: InitConfig with package="com.example.app"
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .mainClass("com.example.Main")
            .packageName("com.example.app")
            .build();

    // Then: getSourceDirectory() returns the correct path
    assertThat(config.getSourceDirectory(), is("src/main/java/com/example/app"));
  }

  /**
   * Verifies that {@code getPalVersion()} returns the PAL version string that was set on the
   * config.
   *
   * <p>Uses an {@code InitConfig} with {@code palVersion="1.0.0"}.
   */
  @Test
  public void testPalVersionFromRuntime() {
    // Given: InitConfig with palVersion set to "1.0.0"
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .mainClass("com.example.Main")
            .palVersion("1.0.0")
            .build();

    // Then: getPalVersion() returns "1.0.0"
    assertThat(config.getPalVersion(), is("1.0.0"));
  }
}
