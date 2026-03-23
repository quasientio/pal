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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    return null;
  }
}
