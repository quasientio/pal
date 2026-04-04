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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link GradleGenerator}, which generates new Gradle build files ({@code
 * build.gradle} and {@code settings.gradle}) for fresh PAL projects.
 *
 * <p>GradleGenerator must produce a complete, functional {@code build.gradle} with a manual {@code
 * weaveClasses} task for AspectJ post-test weaving, the {@code pal-weave} aspect dependency, {@code
 * aspectjrt} runtime dependency, Java 17 target compatibility, and correct project identity (group,
 * version). It must also produce a {@code settings.gradle} with the project name.
 *
 * @see GradleGenerator
 */
public class GradleGeneratorTest {

  /** Temporary directory for generated output. */
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Verifies that calling {@code generate()} creates a {@code build.gradle} file in the target
   * directory.
   */
  @Test
  public void testGeneratesBuildGradle() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    assertTrue(Files.exists(tempDir.getRoot().toPath().resolve("build.gradle")));
  }

  /**
   * Verifies that calling {@code generate()} creates a {@code settings.gradle} file containing
   * {@code rootProject.name = 'my-app'} matching the configured artifact ID.
   */
  @Test
  public void testGeneratesSettingsGradle() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    Path settingsFile = tempDir.getRoot().toPath().resolve("settings.gradle");
    assertTrue(Files.exists(settingsFile));
    String content = Files.readString(settingsFile, StandardCharsets.UTF_8);
    assertThat(content, containsString("rootProject.name = 'my-app'"));
  }

  /**
   * Verifies that the generated {@code build.gradle} includes the {@code weaveClasses} task for
   * AspectJ weaving that runs after tests.
   */
  @Test
  public void testIncludesWeaveTask() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    String content = readBuildGradle();
    assertThat(content, containsString("tasks.register('weaveClasses', JavaExec)"));
    assertThat(content, containsString("mustRunAfter test"));
    assertThat(content, containsString("org.aspectj.tools.ajc.Main"));
  }

  /**
   * Verifies that the generated {@code build.gradle} includes {@code pal-weave} as an {@code
   * aspect} dependency with the correct PAL version from the config.
   */
  @Test
  public void testIncludesPalWeaveDependency() throws Exception {
    // Given
    InitConfig config = defaultConfig().palVersion("1.0.0").build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    String content = readBuildGradle();
    assertThat(content, containsString("aspect 'io.quasient.pal:pal-weave:1.0.0'"));
  }

  /**
   * Verifies that the generated {@code build.gradle} includes {@code aspectjrt} as an {@code
   * implementation} dependency for the AspectJ runtime.
   */
  @Test
  public void testIncludesAspectjRuntimeDependency() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    String content = readBuildGradle();
    assertThat(content, containsString("implementation 'org.aspectj:aspectjrt:"));
  }

  /**
   * Verifies that the generated {@code build.gradle} includes {@code aspectjtools} on the {@code
   * aspectjTools} configuration for the weave task.
   */
  @Test
  public void testIncludesAspectjToolsDependency() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    String content = readBuildGradle();
    assertThat(content, containsString("aspectjTools 'org.aspectj:aspectjtools:"));
  }

  /**
   * Verifies that the generated {@code build.gradle} includes a {@code configurations} block with
   * {@code aspectjTools} and {@code aspect} entries.
   */
  @Test
  public void testIncludesConfigurations() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    String content = readBuildGradle();
    assertThat(content, containsString("configurations {"));
    assertThat(content, containsString("aspectjTools"));
    assertThat(content, containsString("aspect"));
  }

  /**
   * Verifies that the generated {@code build.gradle} sets {@code sourceCompatibility} and {@code
   * targetCompatibility} to {@code JavaVersion.VERSION_17}.
   */
  @Test
  public void testJava17Target() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    String content = readBuildGradle();
    assertThat(content, containsString("sourceCompatibility = JavaVersion.VERSION_17"));
    assertThat(content, containsString("targetCompatibility = JavaVersion.VERSION_17"));
  }

  /**
   * Verifies that the generated {@code build.gradle} includes {@code mavenCentral()} in the {@code
   * repositories} block.
   */
  @Test
  public void testMavenCentralRepository() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    String content = readBuildGradle();
    assertThat(content, containsString("mavenCentral()"));
  }

  /**
   * Verifies that the generated {@code build.gradle} contains the correct {@code group} and {@code
   * version} values matching the {@code InitConfig}.
   */
  @Test
  public void testGroupAndVersion() throws Exception {
    // Given
    InitConfig config =
        defaultConfig().groupId("com.example").projectVersion("2.0-SNAPSHOT").build();

    // When
    new GradleGenerator(config).generate(tempDir.getRoot().toPath());

    // Then
    String content = readBuildGradle();
    assertThat(content, containsString("group = 'com.example'"));
    assertThat(content, containsString("version = '2.0-SNAPSHOT'"));
  }

  /**
   * Verifies that both {@code build.gradle} and {@code settings.gradle} are generated at the
   * correct file paths within the target directory.
   */
  @Test
  public void testOutputFileLocation() throws Exception {
    // Given
    InitConfig config = defaultConfig().build();
    Path target = tempDir.getRoot().toPath();

    // When
    new GradleGenerator(config).generate(target);

    // Then
    assertTrue(Files.exists(target.resolve("build.gradle")));
    assertTrue(Files.exists(target.resolve("settings.gradle")));
  }

  /**
   * Verifies that when {@code dryRun=true}, calling {@code generate()} does not write any files to
   * disk.
   */
  @Test
  public void testDryRunDoesNotWriteFiles() throws Exception {
    // Given
    InitConfig config = defaultConfig().dryRun(true).build();
    Path target = tempDir.getRoot().toPath();

    // When
    new GradleGenerator(config).generate(target);

    // Then
    assertFalse(Files.exists(target.resolve("build.gradle")));
    assertFalse(Files.exists(target.resolve("settings.gradle")));
  }

  /**
   * Creates a default config builder for tests.
   *
   * @return a builder with standard test values
   */
  private static InitConfig.Builder defaultConfig() {
    return InitConfig.builder()
        .groupId("com.example")
        .artifactId("my-app")
        .palVersion("1.0.0")
        .buildTool(BuildTool.GRADLE);
  }

  /**
   * Reads the generated build.gradle file content.
   *
   * @return the file content as a string
   * @throws Exception if the file cannot be read
   */
  private String readBuildGradle() throws Exception {
    return Files.readString(
        tempDir.getRoot().toPath().resolve("build.gradle"), StandardCharsets.UTF_8);
  }
}
