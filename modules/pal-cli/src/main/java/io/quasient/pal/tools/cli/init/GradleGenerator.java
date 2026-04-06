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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates new Gradle build files ({@code build.gradle} and {@code settings.gradle}) for fresh PAL
 * projects.
 *
 * <p>Loads templates from the classpath and substitutes runtime variables. When {@link
 * InitConfig#needsWeaving()} is true, uses the full template with AspectJ weaving configuration;
 * otherwise uses a plain template with only Java 17 setup.
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
    String buildTemplateName =
        config.needsWeaving() ? "build.gradle.template" : "build-plain.gradle.template";
    String buildContent = loadTemplate(buildTemplateName);
    buildContent =
        buildContent
            .replace("{{groupId}}", safe(config.getGroupId()))
            .replace("{{projectVersion}}", safe(config.getProjectVersion()))
            .replace("{{palVersion}}", safe(config.getPalVersion()))
            .replace("{{aspectjVersion}}", safe(config.getAspectjVersion()));

    if (config.isPalClient()) {
      buildContent = insertPalClientDependency(buildContent, safe(config.getPalVersion()));
    }

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
   * Inserts a {@code pal-client} implementation dependency after the aspectjrt line.
   *
   * @param content the build.gradle content
   * @param palVersion the PAL version string
   * @return the modified content
   */
  private static String insertPalClientDependency(String content, String palVersion) {
    String marker = "implementation 'org.aspectj:aspectjrt:";
    int markerIdx = content.indexOf(marker);
    if (markerIdx < 0) {
      return content;
    }
    int eol = content.indexOf('\n', markerIdx);
    if (eol < 0) {
      eol = content.length();
    }
    String clientDep = "\n    implementation 'io.quasient.pal:pal-client:" + palVersion + "'";
    return content.substring(0, eol) + clientDep + content.substring(eol);
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
