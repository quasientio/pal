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
 * Generates a new Maven {@code pom.xml} for fresh PAL projects.
 *
 * <p>Loads {@code pom.xml.template} from the classpath (pre-filtered by Maven resource filtering
 * for build-time constants like the AspectJ plugin version) and substitutes runtime variables such
 * as group ID, artifact ID, project version, PAL version, and AspectJ version.
 *
 * <p>The generated {@code pom.xml} includes Java 17 compiler properties, the {@code pal-weave}
 * dependency, the {@code aspectjrt} runtime dependency, and the {@code aspectj-maven-plugin} with
 * {@code pal-weave} configured as an aspect library.
 *
 * <p>Respects {@link InitConfig#isDryRun()}: when true, computes what would be generated but does
 * not write files.
 *
 * @since 1.0.0
 */
public final class PomGenerator {

  /** Resource path prefix for template files. */
  private static final String TEMPLATE_PREFIX = "/init/";

  /** The init configuration. */
  private final InitConfig config;

  /**
   * Creates a new generator with the given configuration.
   *
   * @param config the init configuration
   */
  public PomGenerator(InitConfig config) {
    this.config = config;
  }

  /**
   * Generates {@code pom.xml} in the given directory.
   *
   * <p>When {@code dryRun=true}, no files are written to disk.
   *
   * @param outputDir the directory in which to create the pom.xml
   * @throws IOException if an I/O error occurs during file writing or template loading
   */
  public void generate(Path outputDir) throws IOException {
    String content = loadTemplate("pom.xml.template");
    content =
        content
            .replace("{{groupId}}", safe(config.getGroupId()))
            .replace("{{artifactId}}", safe(config.getArtifactId()))
            .replace("{{projectVersion}}", safe(config.getProjectVersion()))
            .replace("{{palVersion}}", safe(config.getPalVersion()))
            .replace("{{aspectjVersion}}", safe(config.getAspectjVersion()));

    if (!config.isDryRun()) {
      Files.createDirectories(outputDir);
      Files.writeString(outputDir.resolve("pom.xml"), content, StandardCharsets.UTF_8);
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
    try (InputStream is = PomGenerator.class.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Template not found: " + resourcePath);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
