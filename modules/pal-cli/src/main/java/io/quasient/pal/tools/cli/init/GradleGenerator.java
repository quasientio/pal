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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates new Gradle build files ({@code build.gradle} and {@code settings.gradle}) for fresh PAL
 * projects.
 *
 * <p>Loads templates from the classpath (pre-filtered by Maven resource filtering for build-time
 * constants like the freefair plugin version) and substitutes runtime variables such as group ID,
 * artifact ID, project version, PAL version, and AspectJ version.
 *
 * <p>The generated {@code build.gradle} uses the {@code io.freefair.aspectj.post-compile-weaving}
 * plugin for AspectJ post-compile weaving, the {@code aspect} configuration for {@code pal-weave},
 * and {@code implementation} for {@code aspectjrt}.
 *
 * <p>Respects {@link InitConfig#isDryRun()}: when true, computes what would be generated but does
 * not write files.
 *
 * @since 1.0.0
 */
public final class GradleGenerator {

  /** Resource path prefix for template files. */
  private static final String TEMPLATE_PREFIX = "/init/";

  /** The init configuration. */
  private final InitConfig config;

  /**
   * Creates a new generator with the given configuration.
   *
   * @param config the init configuration
   */
  public GradleGenerator(InitConfig config) {
    this.config = config;
  }

  /**
   * Generates {@code build.gradle} and {@code settings.gradle} in the given directory.
   *
   * <p>When {@code dryRun=true}, no files are written to disk.
   *
   * @param outputDir the directory in which to create the build files
   * @throws IOException if an I/O error occurs during file writing or template loading
   */
  public void generate(Path outputDir) throws IOException {
    String buildContent = loadTemplate("build.gradle.template");
    buildContent =
        buildContent
            .replace("{{groupId}}", safe(config.getGroupId()))
            .replace("{{projectVersion}}", safe(config.getProjectVersion()))
            .replace("{{palVersion}}", safe(config.getPalVersion()))
            .replace("{{aspectjVersion}}", safe(config.getAspectjVersion()));

    String settingsContent = loadTemplate("settings.gradle.template");
    settingsContent = settingsContent.replace("{{artifactId}}", safe(config.getArtifactId()));

    if (!config.isDryRun()) {
      Files.createDirectories(outputDir);
      Files.writeString(outputDir.resolve("build.gradle"), buildContent, StandardCharsets.UTF_8);
      Files.writeString(
          outputDir.resolve("settings.gradle"), settingsContent, StandardCharsets.UTF_8);
    }
  }

  /**
   * Returns the given string, or an empty string if null.
   *
   * @param value the value to null-check
   * @return the value or empty string
   */
  private static String safe(String value) {
    return value != null ? value : "";
  }

  /**
   * Loads a template resource from the classpath.
   *
   * @param templateName the template file name
   * @return the template content as a string
   * @throws IOException if the template cannot be read
   */
  private static String loadTemplate(String templateName) throws IOException {
    String resourcePath = TEMPLATE_PREFIX + templateName;
    try (InputStream is = GradleGenerator.class.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Template not found: " + resourcePath);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
