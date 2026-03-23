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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
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
}
