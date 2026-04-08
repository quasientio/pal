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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strategy interface abstracting build-tool-specific operations for the {@code pal init} command.
 *
 * <p>Implementations handle the differences between Maven and Gradle project generation and
 * patching. Use {@link #forType(BuildTool)} to obtain the appropriate strategy, or {@link
 * #detect(Path)} to auto-detect the build tool from existing files in a directory.
 *
 * @since 1.0.0
 */
public interface BuildToolStrategy {

  /**
   * Generates a new project build file in the given directory.
   *
   * @param config the init configuration
   * @param outputDir the directory in which to create the build file
   * @throws IOException if an I/O error occurs
   */
  void generate(InitConfig config, Path outputDir) throws IOException;

  /**
   * Patches an existing project build file to add PAL weaving configuration.
   *
   * @param config the init configuration
   * @param buildFile the path to the existing build file
   * @throws IOException if an I/O error occurs
   */
  void patch(InitConfig config, Path buildFile) throws IOException;

  /**
   * Returns the primary build file name for this build tool (e.g., {@code "pom.xml"}).
   *
   * @return the build file name
   */
  String getBuildFileName();

  /**
   * Returns the settings file name for this build tool (e.g., {@code "settings.xml"}).
   *
   * @return the settings file name
   */
  String getSettingsFileName();

  /**
   * Returns the {@code BuildToolStrategy} implementation for the given build tool type.
   *
   * @param buildTool the build tool
   * @return the corresponding strategy
   */
  static BuildToolStrategy forType(BuildTool buildTool) {
    return switch (buildTool) {
      case MAVEN -> new MavenBuildToolStrategy();
      case GRADLE -> new GradleBuildToolStrategy();
    };
  }

  /**
   * Detects the build tool used in the given directory by checking for known build files.
   *
   * <p>Detection order (first match wins):
   *
   * <ol>
   *   <li>{@code pom.xml} &rarr; {@link BuildTool#MAVEN}
   *   <li>{@code build.gradle} &rarr; {@link BuildTool#GRADLE}
   *   <li>{@code build.gradle.kts} &rarr; {@link BuildTool#GRADLE}
   *   <li>{@code settings.gradle} &rarr; {@link BuildTool#GRADLE}
   *   <li>{@code settings.gradle.kts} &rarr; {@link BuildTool#GRADLE}
   * </ol>
   *
   * @param directory the directory to inspect
   * @return the detected build tool, or {@code null} if no recognized build file is found
   */
  static BuildTool detect(Path directory) {
    if (Files.exists(directory.resolve("pom.xml"))) {
      return BuildTool.MAVEN;
    }
    if (Files.exists(directory.resolve("build.gradle"))) {
      return BuildTool.GRADLE;
    }
    if (Files.exists(directory.resolve("build.gradle.kts"))) {
      return BuildTool.GRADLE;
    }
    if (Files.exists(directory.resolve("settings.gradle"))) {
      return BuildTool.GRADLE;
    }
    if (Files.exists(directory.resolve("settings.gradle.kts"))) {
      return BuildTool.GRADLE;
    }
    return null;
  }

  /**
   * Finds the primary build file for the given build tool in the specified directory.
   *
   * <p>For Maven, checks for {@code pom.xml} in the directory. For Gradle, checks for {@code
   * build.gradle} and {@code build.gradle.kts} at the root level first. If no root-level build file
   * exists (common in multi-module Gradle projects created by {@code gradle init}), parses the
   * settings file ({@code settings.gradle} or {@code settings.gradle.kts}) for {@code include}
   * directives and looks for build files in included subproject directories.
   *
   * @param directory the project root directory
   * @param buildTool the detected build tool
   * @return the path to the build file, or {@code null} if not found
   */
  static Path findBuildFile(Path directory, BuildTool buildTool) {
    if (buildTool == BuildTool.MAVEN) {
      Path pom = directory.resolve("pom.xml");
      return Files.exists(pom) ? pom : null;
    }

    // Gradle: check root-level build files first
    Path buildGradle = directory.resolve("build.gradle");
    if (Files.exists(buildGradle)) {
      return buildGradle;
    }
    Path buildGradleKts = directory.resolve("build.gradle.kts");
    if (Files.exists(buildGradleKts)) {
      return buildGradleKts;
    }

    // Multi-module: parse settings file for subproject directories
    Path settingsFile = resolveSettingsFile(directory);
    if (settingsFile == null) {
      return null;
    }

    try {
      String content = Files.readString(settingsFile, StandardCharsets.UTF_8);
      for (String subproject : parseIncludedSubprojects(content)) {
        String subDir = subproject.replaceFirst("^:", "").replace(':', '/');
        Path subBuildKts = directory.resolve(subDir).resolve("build.gradle.kts");
        if (Files.exists(subBuildKts)) {
          return subBuildKts;
        }
        Path subBuild = directory.resolve(subDir).resolve("build.gradle");
        if (Files.exists(subBuild)) {
          return subBuild;
        }
      }
    } catch (IOException e) {
      // Silently ignore parse failures
    }
    return null;
  }

  /**
   * Resolves the Gradle settings file in the given directory.
   *
   * @param directory the directory to inspect
   * @return the path to the settings file, or {@code null} if not found
   */
  private static Path resolveSettingsFile(Path directory) {
    Path settings = directory.resolve("settings.gradle");
    if (Files.exists(settings)) {
      return settings;
    }
    Path settingsKts = directory.resolve("settings.gradle.kts");
    if (Files.exists(settingsKts)) {
      return settingsKts;
    }
    return null;
  }

  /**
   * Parses {@code include} directives from a Gradle settings file to extract subproject names.
   *
   * <p>Handles both Groovy DSL ({@code include 'app'}) and Kotlin DSL ({@code include("app")})
   * syntax, including multi-value includes like {@code include("app", "core")}.
   *
   * @param settingsContent the settings file content
   * @return the list of included subproject names
   */
  private static List<String> parseIncludedSubprojects(String settingsContent) {
    List<String> subprojects = new ArrayList<>();
    Pattern quoted = Pattern.compile("['\"]([^'\"]+)['\"]");
    settingsContent
        .lines()
        .map(String::trim)
        .filter(l -> l.startsWith("include(") || l.startsWith("include "))
        .forEach(
            line -> {
              Matcher m = quoted.matcher(line);
              while (m.find()) {
                subprojects.add(m.group(1));
              }
            });
    return subprojects;
  }
}
