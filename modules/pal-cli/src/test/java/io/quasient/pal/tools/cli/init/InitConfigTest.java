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
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Paths;
import org.junit.Test;

/**
 * Unit test specifications for {@code InitConfig}, the central POJO that carries all wizard choices
 * through the generation pipeline.
 *
 * <p>InitConfig holds project identity (groupId, artifactId, version, mainClass, packageName),
 * intent flags (interceptable, intercepting, kafka), feature toggles (sampleApp, scopePolicy,
 * loggingConfig), build tool selection, PAL version, and optional existing build file path. These
 * tests verify default values, derived properties (package name inference, source directory layout,
 * new-vs-existing project detection, needsEtcd, needsKafka, isInfra, isPalClient, isAsService), and
 * enum convenience methods.
 */
public class InitConfigTest {

  /**
   * Verifies that an {@code InitConfig} built with only the required fields (groupId, artifactId,
   * mainClass) has correct default values for all optional fields.
   *
   * <p>Expected defaults: version is {@code "1.0-SNAPSHOT"}, sampleApp is {@code true},
   * interceptable is {@code false}, intercepting is {@code false}, kafka is {@code false},
   * scopePolicy is {@code false}, loggingConfig is {@code true}, buildTool is {@code MAVEN}.
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
    assertThat(config.isSampleApp(), is(true));
    assertThat(config.isInterceptable(), is(false));
    assertThat(config.isIntercepting(), is(false));
    assertThat(config.isKafka(), is(false));
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
   * set.
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
   * Verifies that {@code needsEtcd()} returns {@code true} when interceptable or intercepting is
   * set, and {@code false} otherwise. Kafka alone does not require etcd.
   */
  @Test
  public void testNeedsEtcd() {
    // interceptable only
    InitConfig interceptableConfig =
        InitConfig.builder().groupId("com.example").interceptable(true).build();
    assertThat(interceptableConfig.needsEtcd(), is(true));

    // intercepting only
    InitConfig interceptingConfig =
        InitConfig.builder().groupId("com.example").intercepting(true).build();
    assertThat(interceptingConfig.needsEtcd(), is(true));

    // kafka only — no etcd
    InitConfig kafkaConfig = InitConfig.builder().groupId("com.example").kafka(true).build();
    assertThat(kafkaConfig.needsEtcd(), is(false));

    // plain local — no etcd
    InitConfig plainConfig = InitConfig.builder().groupId("com.example").build();
    assertThat(plainConfig.needsEtcd(), is(false));
  }

  /** Verifies that {@code needsKafka()} returns {@code true} only when kafka is set. */
  @Test
  public void testNeedsKafka() {
    InitConfig kafkaConfig = InitConfig.builder().groupId("com.example").kafka(true).build();
    assertThat(kafkaConfig.needsKafka(), is(true));

    InitConfig noKafkaConfig = InitConfig.builder().groupId("com.example").build();
    assertThat(noKafkaConfig.needsKafka(), is(false));
  }

  /** Verifies that {@code isInfra()} returns {@code true} when etcd or kafka is needed. */
  @Test
  public void testIsInfraDerived() {
    // interceptable → etcd → infra
    InitConfig interceptableConfig =
        InitConfig.builder().groupId("com.example").interceptable(true).build();
    assertThat(interceptableConfig.isInfra(), is(true));

    // kafka → infra
    InitConfig kafkaConfig = InitConfig.builder().groupId("com.example").kafka(true).build();
    assertThat(kafkaConfig.isInfra(), is(true));

    // plain → no infra
    InitConfig plainConfig = InitConfig.builder().groupId("com.example").build();
    assertThat(plainConfig.isInfra(), is(false));
  }

  /** Verifies that {@code isPalClient()} returns {@code true} only when intercepting. */
  @Test
  public void testIsPalClient() {
    InitConfig interceptingConfig =
        InitConfig.builder().groupId("com.example").intercepting(true).build();
    assertThat(interceptingConfig.isPalClient(), is(true));

    InitConfig interceptableConfig =
        InitConfig.builder().groupId("com.example").interceptable(true).build();
    assertThat(interceptableConfig.isPalClient(), is(false));
  }

  /**
   * Verifies that {@code isAsService()} returns {@code true} when intercepting or jsonRpc with no
   * main class.
   */
  @Test
  public void testIsAsService() {
    // intercepting with no main class → as-service
    InitConfig asServiceConfig =
        InitConfig.builder().groupId("com.example").intercepting(true).build();
    assertThat(asServiceConfig.isAsService(), is(true));

    // intercepting with main class → not as-service
    InitConfig withMainConfig =
        InitConfig.builder()
            .groupId("com.example")
            .intercepting(true)
            .mainClass("com.example.Main")
            .build();
    assertThat(withMainConfig.isAsService(), is(false));

    // not intercepting, no main class, no jsonRpc → not as-service
    InitConfig plainConfig = InitConfig.builder().groupId("com.example").build();
    assertThat(plainConfig.isAsService(), is(false));

    // jsonRpc with no main class → as-service
    InitConfig rpcServiceConfig = InitConfig.builder().groupId("com.example").jsonRpc(true).build();
    assertThat(rpcServiceConfig.isAsService(), is(true));

    // jsonRpc with main class → not as-service
    InitConfig rpcWithMainConfig =
        InitConfig.builder()
            .groupId("com.example")
            .jsonRpc(true)
            .mainClass("com.example.Main")
            .build();
    assertThat(rpcWithMainConfig.isAsService(), is(false));
  }

  /**
   * Verifies that {@code isRpcPolicy()} is derived from intercepting or jsonRpc, and {@code
   * isInterceptBundle()} is derived from intercepting only.
   */
  @Test
  public void testDerivedFromIntercepting() {
    InitConfig interceptingConfig =
        InitConfig.builder().groupId("com.example").intercepting(true).build();
    assertThat(interceptingConfig.isRpcPolicy(), is(true));
    assertThat(interceptingConfig.isInterceptBundle(), is(true));

    InitConfig plainConfig = InitConfig.builder().groupId("com.example").build();
    assertThat(plainConfig.isRpcPolicy(), is(false));
    assertThat(plainConfig.isInterceptBundle(), is(false));

    // jsonRpc enables rpc policy but not intercept bundle
    InitConfig jsonRpcConfig = InitConfig.builder().groupId("com.example").jsonRpc(true).build();
    assertThat(jsonRpcConfig.isRpcPolicy(), is(true));
    assertThat(jsonRpcConfig.isInterceptBundle(), is(false));
  }

  /** Verifies that {@code needsWeaving()} defaults to true and can be disabled. */
  @Test
  public void testNeedsWeaving() {
    // Default: weaving enabled
    InitConfig defaultConfig = InitConfig.builder().groupId("com.example").build();
    assertThat(defaultConfig.needsWeaving(), is(true));

    // Explicitly disabled (RPC gateway only)
    InitConfig noWeavingConfig =
        InitConfig.builder().groupId("com.example").jsonRpc(true).weaving(false).build();
    assertThat(noWeavingConfig.needsWeaving(), is(false));

    // JSON-RPC with weaving (pipeline mode)
    InitConfig pipelineConfig = InitConfig.builder().groupId("com.example").jsonRpc(true).build();
    assertThat(pipelineConfig.needsWeaving(), is(true));
  }

  /**
   * Verifies that {@code getSourceDirectory()} converts the package name into the correct Maven/
   * Gradle source directory layout by replacing dots with path separators.
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
