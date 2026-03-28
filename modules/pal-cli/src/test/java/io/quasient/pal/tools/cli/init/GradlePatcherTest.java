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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link GradlePatcher}, which modifies existing Gradle build files to add PAL
 * weaving support.
 *
 * <p>GradlePatcher uses text-based manipulation (not DOM) since Gradle files are Groovy/Kotlin
 * scripts, not structured XML. This makes thorough testing critical — edge cases around block
 * detection, idempotency, and syntax preservation must all be covered.
 *
 * @see GradlePatcher
 */
public class GradlePatcherTest {

  /** Temporary directory for build files. */
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Verifies that patching a {@code build.gradle} with an existing {@code dependencies} block adds
   * {@code pal-weave} as an {@code aspect} dependency with the correct PAL version.
   */
  @Test
  public void testAddsPalWeaveDependency() throws Exception {
    // Given
    Path buildFile = writeBuildGradle(BASIC_BUILD_GRADLE);
    InitConfig config = defaultConfig().build();

    // When
    PatchResult result = new GradlePatcher().patch(config, buildFile);

    // Then
    String content = Files.readString(buildFile, StandardCharsets.UTF_8);
    assertThat(content, containsString("aspect 'io.quasient.pal:pal-weave:1.0.0'"));
    assertFalse(result.getAdditions().isEmpty());
  }

  /**
   * Verifies that patching a {@code build.gradle} with an existing {@code plugins} block adds the
   * {@code io.freefair.aspectj.post-compile-weaving} plugin.
   */
  @Test
  public void testAddsAspectjPlugin() throws Exception {
    // Given
    Path buildFile = writeBuildGradle(BASIC_BUILD_GRADLE);
    InitConfig config = defaultConfig().build();

    // When
    new GradlePatcher().patch(config, buildFile);

    // Then
    String content = Files.readString(buildFile, StandardCharsets.UTF_8);
    assertThat(content, containsString("io.freefair.aspectj.post-compile-weaving"));
  }

  /**
   * Verifies that patching a {@code build.gradle} with no {@code dependencies} block creates one
   * and adds the {@code pal-weave} dependency.
   */
  @Test
  public void testCreatesDependenciesBlockIfMissing() throws Exception {
    // Given
    String noDeps = "plugins {\n    id 'java'\n}\n";
    Path buildFile = writeBuildGradle(noDeps);
    InitConfig config = defaultConfig().build();

    // When
    new GradlePatcher().patch(config, buildFile);

    // Then
    String content = Files.readString(buildFile, StandardCharsets.UTF_8);
    assertThat(content, containsString("dependencies {"));
    assertThat(content, containsString("pal-weave"));
  }

  /**
   * Verifies idempotency: patching a {@code build.gradle} that already contains the {@code
   * pal-weave} dependency and the AspectJ plugin does not produce duplicate entries.
   */
  @Test
  public void testIdempotency() throws Exception {
    // Given
    Path buildFile = writeBuildGradle(BASIC_BUILD_GRADLE);
    InitConfig config = defaultConfig().build();
    GradlePatcher patcher = new GradlePatcher();

    // When: patch twice
    patcher.patch(config, buildFile);
    String afterFirst = Files.readString(buildFile, StandardCharsets.UTF_8);
    patcher.patch(config, buildFile);
    String afterSecond = Files.readString(buildFile, StandardCharsets.UTF_8);

    // Then: content should be the same after second patch
    assertThat(afterSecond, is(afterFirst));
  }

  /**
   * Verifies that patching creates a backup of the original {@code build.gradle} at {@code
   * build.gradle.backup} with the original file content preserved.
   */
  @Test
  public void testCreatesBackup() throws Exception {
    // Given
    Path buildFile = writeBuildGradle(BASIC_BUILD_GRADLE);
    String originalContent = Files.readString(buildFile, StandardCharsets.UTF_8);
    InitConfig config = defaultConfig().build();

    // When
    new GradlePatcher().patch(config, buildFile);

    // Then
    Path backupFile = buildFile.resolveSibling("build.gradle.backup");
    assertTrue(Files.exists(backupFile));
    String backupContent = Files.readString(backupFile, StandardCharsets.UTF_8);
    assertThat(backupContent, is(originalContent));
  }

  /**
   * Verifies that patching preserves all existing dependencies in the {@code build.gradle} while
   * adding the {@code pal-weave} dependency alongside them.
   */
  @Test
  public void testPreservesExistingDependencies() throws Exception {
    // Given
    Path buildFile = writeBuildGradle(BASIC_BUILD_GRADLE);
    InitConfig config = defaultConfig().build();

    // When
    new GradlePatcher().patch(config, buildFile);

    // Then
    String content = Files.readString(buildFile, StandardCharsets.UTF_8);
    assertThat(content, containsString("implementation 'com.google.guava:guava:33.0.0-jre'"));
    assertThat(content, containsString("pal-weave"));
  }

  /**
   * Verifies that patching preserves all existing plugins in the {@code build.gradle} while adding
   * the AspectJ plugin alongside them.
   */
  @Test
  public void testPreservesExistingPlugins() throws Exception {
    // Given
    Path buildFile = writeBuildGradle(BASIC_BUILD_GRADLE);
    InitConfig config = defaultConfig().build();

    // When
    new GradlePatcher().patch(config, buildFile);

    // Then
    String content = Files.readString(buildFile, StandardCharsets.UTF_8);
    assertThat(content, containsString("id 'java'"));
    assertThat(content, containsString("io.freefair.aspectj.post-compile-weaving"));
  }

  /**
   * Verifies that patching a {@code build.gradle.kts} file uses Kotlin DSL syntax for the {@code
   * pal-weave} dependency.
   */
  @Test
  public void testHandlesBuildGradleKts() throws Exception {
    // Given
    String kotlinBuild =
        """
        plugins {
            id("java")
        }

        dependencies {
            implementation("com.google.guava:guava:33.0.0-jre")
        }
        """;
    Path buildFile = tempDir.getRoot().toPath().resolve("build.gradle.kts");
    Files.writeString(buildFile, kotlinBuild, StandardCharsets.UTF_8);
    InitConfig config = defaultConfig().build();

    // When
    new GradlePatcher().patch(config, buildFile);

    // Then
    String content = Files.readString(buildFile, StandardCharsets.UTF_8);
    assertThat(content, containsString("aspect(\"io.quasient.pal:pal-weave:1.0.0\")"));
    assertThat(content, containsString("implementation(\"org.aspectj:aspectjrt:"));
    assertThat(content, containsString("id(\"io.freefair.aspectj.post-compile-weaving\")"));
  }

  /**
   * Verifies that the {@code PatchResult} returned by {@code patch()} accurately reports actions
   * taken.
   */
  @Test
  public void testPatchResultReportsActions() throws Exception {
    // Given: unpatched file
    Path buildFile = writeBuildGradle(BASIC_BUILD_GRADLE);
    InitConfig config = defaultConfig().build();
    GradlePatcher patcher = new GradlePatcher();

    // When
    PatchResult firstResult = patcher.patch(config, buildFile);

    // Then: first patch reports additions
    assertFalse(firstResult.getAdditions().isEmpty());

    // Given: already-patched file
    // When
    PatchResult secondResult = patcher.patch(config, buildFile);

    // Then: second patch reports already configured
    assertTrue(secondResult.isAlreadyConfigured());
  }

  /**
   * Verifies that when a {@code build.gradle} already contains a different AspectJ plugin, the
   * patcher emits a warning and does not add a conflicting plugin.
   */
  @Test
  public void testWarnsOnExistingAspectjPlugin() throws Exception {
    // Given
    String buildWithOtherPlugin =
        """
        plugins {
            id 'java'
            id 'io.github.nickhudkins.aspectj-pipeline' version '0.1'
        }

        dependencies {
            implementation 'com.google.guava:guava:33.0.0-jre'
        }
        """;
    Path buildFile = writeBuildGradle(buildWithOtherPlugin);
    InitConfig config = defaultConfig().build();

    // When
    PatchResult result = new GradlePatcher().patch(config, buildFile);

    // Then
    assertFalse(result.getWarnings().isEmpty());
    String content = Files.readString(buildFile, StandardCharsets.UTF_8);
    assertThat(content, not(containsString("io.freefair.aspectj.post-compile-weaving")));
  }

  /**
   * Verifies that patching adds {@code aspectjrt} as an {@code implementation} dependency when it
   * is not already present.
   */
  @Test
  public void testAddsAspectjRuntimeDependency() throws Exception {
    // Given
    Path buildFile = writeBuildGradle(BASIC_BUILD_GRADLE);
    InitConfig config = defaultConfig().build();

    // When
    new GradlePatcher().patch(config, buildFile);

    // Then
    String content = Files.readString(buildFile, StandardCharsets.UTF_8);
    assertThat(content, containsString("implementation 'org.aspectj:aspectjrt:"));
  }

  /**
   * Verifies that when {@code dryRun=true}, the patcher does not modify the original file, does not
   * create a backup, but still returns a {@code PatchResult} listing what would have been changed.
   */
  @Test
  public void testDryRunDoesNotModifyFile() throws Exception {
    // Given
    Path buildFile = writeBuildGradle(BASIC_BUILD_GRADLE);
    String originalContent = Files.readString(buildFile, StandardCharsets.UTF_8);
    InitConfig config = defaultConfig().dryRun(true).build();

    // When
    PatchResult result = new GradlePatcher().patch(config, buildFile);

    // Then: file unchanged
    String afterContent = Files.readString(buildFile, StandardCharsets.UTF_8);
    assertThat(afterContent, is(originalContent));

    // No backup created
    assertFalse(Files.exists(buildFile.resolveSibling("build.gradle.backup")));

    // PatchResult still reports what would have been done
    assertFalse(result.getAdditions().isEmpty());
  }

  /**
   * Writes a build.gradle file in the temporary directory.
   *
   * @param content the file content
   * @return the path to the created file
   * @throws Exception if the file cannot be written
   */
  private Path writeBuildGradle(String content) throws Exception {
    Path buildFile = tempDir.getRoot().toPath().resolve("build.gradle");
    Files.writeString(buildFile, content, StandardCharsets.UTF_8);
    return buildFile;
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

  /** A basic build.gradle with plugins and dependencies blocks. */
  private static final String BASIC_BUILD_GRADLE =
      """
      plugins {
          id 'java'
      }

      dependencies {
          implementation 'com.google.guava:guava:33.0.0-jre'
      }
      """;
}
