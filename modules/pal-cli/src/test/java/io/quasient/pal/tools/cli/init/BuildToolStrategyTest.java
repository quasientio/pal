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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit test specifications for {@code BuildToolStrategy}, the interface and factory that abstracts
 * build-tool-specific operations (Maven vs Gradle) behind a common API.
 *
 * <p>BuildToolStrategy provides a {@code forType(BuildTool)} factory method that returns the
 * appropriate strategy implementation ({@code MavenBuildToolStrategy} or {@code
 * GradleBuildToolStrategy}), and a {@code detect(Path)} method that inspects a directory for
 * existing build files to determine the project's build tool. These tests verify factory dispatch,
 * build tool detection from filesystem markers ({@code pom.xml}, {@code build.gradle}, {@code
 * build.gradle.kts}), and precedence rules when multiple build files exist.
 *
 * <p>Tests that inspect the filesystem use a {@code @Rule TemporaryFolder} to create isolated
 * directories with specific build file markers.
 */
public class BuildToolStrategyTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Verifies that {@code BuildToolStrategy.forType(BuildTool.MAVEN)} returns an instance of {@code
   * MavenBuildToolStrategy}.
   */
  @Test
  public void testFactoryReturnsMavenStrategy() {
    // Given: BuildTool.MAVEN
    // When: BuildToolStrategy.forType(MAVEN) called
    BuildToolStrategy strategy = BuildToolStrategy.forType(BuildTool.MAVEN);

    // Then: returns instance of MavenBuildToolStrategy
    assertThat(strategy, is(instanceOf(MavenBuildToolStrategy.class)));
  }

  /**
   * Verifies that {@code BuildToolStrategy.forType(BuildTool.GRADLE)} returns an instance of {@code
   * GradleBuildToolStrategy}.
   */
  @Test
  public void testFactoryReturnsGradleStrategy() {
    // Given: BuildTool.GRADLE
    // When: BuildToolStrategy.forType(GRADLE) called
    BuildToolStrategy strategy = BuildToolStrategy.forType(BuildTool.GRADLE);

    // Then: returns instance of GradleBuildToolStrategy
    assertThat(strategy, is(instanceOf(GradleBuildToolStrategy.class)));
  }

  /**
   * Verifies that {@code BuildToolStrategy.detect(dir)} returns {@code BuildTool.MAVEN} when the
   * directory contains a {@code pom.xml} file.
   *
   * <p>Uses a {@code @Rule TemporaryFolder} with a {@code pom.xml} file created inside it.
   */
  @Test
  public void testDetectMavenProject() throws IOException {
    // Given: temp directory containing pom.xml
    tempFolder.newFile("pom.xml");

    // When: BuildToolStrategy.detect(dir) called
    BuildTool detected = BuildToolStrategy.detect(tempFolder.getRoot().toPath());

    // Then: returns MAVEN
    assertThat(detected, is(BuildTool.MAVEN));
  }

  /**
   * Verifies that {@code BuildToolStrategy.detect(dir)} returns {@code BuildTool.GRADLE} when the
   * directory contains a {@code build.gradle} file.
   *
   * <p>Uses a {@code @Rule TemporaryFolder} with a {@code build.gradle} file created inside it.
   */
  @Test
  public void testDetectGradleProject() throws IOException {
    // Given: temp directory containing build.gradle
    tempFolder.newFile("build.gradle");

    // When: BuildToolStrategy.detect(dir) called
    BuildTool detected = BuildToolStrategy.detect(tempFolder.getRoot().toPath());

    // Then: returns GRADLE
    assertThat(detected, is(BuildTool.GRADLE));
  }

  /**
   * Verifies that {@code BuildToolStrategy.detect(dir)} returns {@code BuildTool.GRADLE} when the
   * directory contains a {@code build.gradle.kts} file (Kotlin DSL variant).
   *
   * <p>Uses a {@code @Rule TemporaryFolder} with a {@code build.gradle.kts} file created inside it.
   */
  @Test
  public void testDetectGradleKotlinProject() throws IOException {
    // Given: temp directory containing build.gradle.kts
    tempFolder.newFile("build.gradle.kts");

    // When: BuildToolStrategy.detect(dir) called
    BuildTool detected = BuildToolStrategy.detect(tempFolder.getRoot().toPath());

    // Then: returns GRADLE
    assertThat(detected, is(BuildTool.GRADLE));
  }

  /**
   * Verifies that {@code BuildToolStrategy.detect(dir)} returns {@code null} when the directory
   * contains no recognized build files, indicating a brand-new project.
   *
   * <p>Uses a {@code @Rule TemporaryFolder} with no files created inside it.
   */
  @Test
  public void testDetectNoBuildFile() {
    // Given: empty temp directory
    // When: BuildToolStrategy.detect(dir) called
    BuildTool detected = BuildToolStrategy.detect(tempFolder.getRoot().toPath());

    // Then: returns null (new project)
    assertThat(detected, is(nullValue()));
  }

  /**
   * Verifies that when both {@code pom.xml} and {@code build.gradle} exist in the same directory,
   * {@code BuildToolStrategy.detect(dir)} returns {@code BuildTool.MAVEN} because Maven takes
   * precedence.
   *
   * <p>Uses a {@code @Rule TemporaryFolder} with both {@code pom.xml} and {@code build.gradle}
   * files created inside it.
   */
  @Test
  public void testDetectPrefersPomWhenBothExist() throws IOException {
    // Given: temp directory with both pom.xml and build.gradle
    tempFolder.newFile("pom.xml");
    tempFolder.newFile("build.gradle");

    // When: BuildToolStrategy.detect(dir) called
    BuildTool detected = BuildToolStrategy.detect(tempFolder.getRoot().toPath());

    // Then: returns MAVEN (Maven takes precedence)
    assertThat(detected, is(BuildTool.MAVEN));
  }

  /**
   * Verifies that {@code BuildToolStrategy.detect(dir)} returns {@code BuildTool.GRADLE} when the
   * directory contains a {@code settings.gradle} file but no build file at root. This is common for
   * multi-module Gradle projects.
   */
  @Test
  public void testDetectGradleFromSettingsFile() throws IOException {
    // Given: temp directory containing only settings.gradle
    tempFolder.newFile("settings.gradle");

    // When: BuildToolStrategy.detect(dir) called
    BuildTool detected = BuildToolStrategy.detect(tempFolder.getRoot().toPath());

    // Then: returns GRADLE
    assertThat(detected, is(BuildTool.GRADLE));
  }

  /**
   * Verifies that {@code BuildToolStrategy.detect(dir)} returns {@code BuildTool.GRADLE} when the
   * directory contains a {@code settings.gradle.kts} file (Kotlin DSL variant) but no build file at
   * root. This matches the default layout produced by {@code gradle init}.
   */
  @Test
  public void testDetectGradleFromSettingsKtsFile() throws IOException {
    // Given: temp directory containing only settings.gradle.kts
    tempFolder.newFile("settings.gradle.kts");

    // When: BuildToolStrategy.detect(dir) called
    BuildTool detected = BuildToolStrategy.detect(tempFolder.getRoot().toPath());

    // Then: returns GRADLE
    assertThat(detected, is(BuildTool.GRADLE));
  }

  /**
   * Verifies that {@code findBuildFile} locates a build file in a subproject directory when the
   * root has only a settings file with an {@code include} directive.
   */
  @Test
  public void testFindBuildFileInSubproject() throws IOException {
    // Given: multi-module Gradle layout with settings.gradle.kts and app/build.gradle.kts
    Path root = tempFolder.getRoot().toPath();
    Files.writeString(
        root.resolve("settings.gradle.kts"),
        "rootProject.name = \"myapp\"\ninclude(\"app\")\n",
        StandardCharsets.UTF_8);
    File appDir = tempFolder.newFolder("app");
    Files.writeString(appDir.toPath().resolve("build.gradle.kts"), "plugins { }\n");

    // When: findBuildFile called
    Path buildFile = BuildToolStrategy.findBuildFile(root, BuildTool.GRADLE);

    // Then: returns app/build.gradle.kts
    assertThat(buildFile, is(notNullValue()));
    assertThat(buildFile.getFileName().toString(), is("build.gradle.kts"));
    assertThat(buildFile.getParent().getFileName().toString(), is("app"));
  }

  /**
   * Verifies that {@code findBuildFile} returns the root-level build file when one exists, even if
   * a settings file is also present.
   */
  @Test
  public void testFindBuildFileAtRoot() throws IOException {
    // Given: Gradle project with root build.gradle.kts
    tempFolder.newFile("build.gradle.kts");
    tempFolder.newFile("settings.gradle.kts");

    // When: findBuildFile called
    Path buildFile =
        BuildToolStrategy.findBuildFile(tempFolder.getRoot().toPath(), BuildTool.GRADLE);

    // Then: returns root build.gradle.kts
    assertThat(buildFile, is(notNullValue()));
    assertThat(buildFile.getParent().toRealPath(), is(tempFolder.getRoot().toPath().toRealPath()));
  }

  /** Verifies that {@code findBuildFile} returns pom.xml for Maven projects. */
  @Test
  public void testFindBuildFileReturnsMaven() throws IOException {
    // Given: directory with pom.xml
    tempFolder.newFile("pom.xml");

    // When: findBuildFile called
    Path buildFile =
        BuildToolStrategy.findBuildFile(tempFolder.getRoot().toPath(), BuildTool.MAVEN);

    // Then: returns pom.xml
    assertThat(buildFile, is(notNullValue()));
    assertThat(buildFile.getFileName().toString(), is("pom.xml"));
  }

  /** Verifies that {@code findBuildFile} returns null when no build file exists anywhere. */
  @Test
  public void testFindBuildFileReturnsNullWhenNoBuildFile() {
    // Given: empty directory
    // When: findBuildFile called
    Path buildFile =
        BuildToolStrategy.findBuildFile(tempFolder.getRoot().toPath(), BuildTool.GRADLE);

    // Then: returns null
    assertThat(buildFile, is(nullValue()));
  }

  /** Verifies that {@code findBuildFile} handles Groovy DSL settings with multi-value include. */
  @Test
  public void testFindBuildFileGroovyMultiInclude() throws IOException {
    // Given: settings.gradle with include 'app', 'lib'
    Path root = tempFolder.getRoot().toPath();
    Files.writeString(
        root.resolve("settings.gradle"),
        "rootProject.name = 'myapp'\ninclude 'app', 'lib'\n",
        StandardCharsets.UTF_8);
    File appDir = tempFolder.newFolder("app");
    Files.writeString(appDir.toPath().resolve("build.gradle"), "apply plugin: 'java'\n");

    // When: findBuildFile called
    Path buildFile = BuildToolStrategy.findBuildFile(root, BuildTool.GRADLE);

    // Then: returns app/build.gradle (first match)
    assertThat(buildFile, is(notNullValue()));
    assertThat(buildFile.getFileName().toString(), is("build.gradle"));
    assertThat(buildFile.getParent().getFileName().toString(), is("app"));
  }
}
