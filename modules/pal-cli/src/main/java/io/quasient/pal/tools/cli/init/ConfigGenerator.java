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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates PAL configuration files based on templates.
 *
 * <p>Customises templates with the user's package name for logger patterns and scope rules.
 * Generates the following files based on config flags:
 *
 * <ul>
 *   <li>{@code config/peer-logging.xml} — logback config with package-specific logger
 *   <li>{@code config/rpc-policy.yaml} — package-based allow patterns
 *   <li>{@code config/recording-scope.yaml} — package-based include patterns
 *   <li>{@code config/intercept-bundle.yaml} — main class target
 * </ul>
 *
 * <p>Each file is only generated if the corresponding flag is true. Respects the {@code force} flag
 * for overwrite behaviour and {@code dryRun} mode.
 *
 * @since 1.0.0
 */
public final class ConfigGenerator {

  /** Resource path prefix for template files. */
  private static final String TEMPLATE_PREFIX = "/init/";

  /** The init configuration. */
  private final InitConfig config;

  /**
   * Creates a new generator with the given configuration.
   *
   * @param config the init configuration
   */
  public ConfigGenerator(InitConfig config) {
    this.config = config;
  }

  /**
   * Generates configuration files based on the config flags.
   *
   * <p>When {@code dryRun=true}, returns the list of files that would be generated without writing
   * them. When {@code force=false}, existing files are not overwritten.
   *
   * @param targetDir the project root directory
   * @return the list of generated (or would-be-generated) file paths
   * @throws IOException if an I/O error occurs during file writing
   */
  public List<Path> generate(Path targetDir) throws IOException {
    String packageName = config.getPackageName();
    String mainClass = config.getMainClass();
    String artifactId = config.getArtifactId();

    List<Path> generated = new ArrayList<>();
    Path configDir = targetDir.resolve("config");

    if (config.isLoggingConfig()) {
      Path file = configDir.resolve("peer-logging.xml");
      String content = loadTemplate("peer-logging.xml.template");
      content = content.replace("${package}", packageName);
      generated.add(file);
      writeIfAllowed(file, content);
    }

    if (config.isRpcPolicy()) {
      Path file = configDir.resolve("rpc-policy.yaml");
      String content = loadTemplate("rpc-policy.yaml.template");
      content = content.replace("${package}", packageName);
      generated.add(file);
      writeIfAllowed(file, content);
    }

    if (config.isScopePolicy()) {
      Path file = configDir.resolve("recording-scope.yaml");
      String content = loadTemplate("recording-scope.yaml.template");
      content = content.replace("${package}", packageName);
      generated.add(file);
      writeIfAllowed(file, content);
    }

    if (config.isInterceptBundle()) {
      Path file = configDir.resolve("intercept-bundle.yaml");
      String content = loadTemplate("intercept-bundle.yaml.template");
      content = content.replace("${package}", packageName != null ? packageName : "");
      content = content.replace("${mainClass}", mainClass != null ? mainClass : "");
      content = content.replace("${artifactId}", artifactId != null ? artifactId : "app");
      generated.add(file);
      writeIfAllowed(file, content);
    }

    return Collections.unmodifiableList(generated);
  }

  /**
   * Writes content to a file if allowed by dry-run and force settings.
   *
   * @param file the file to write
   * @param content the file content
   * @throws IOException if an I/O error occurs
   */
  private void writeIfAllowed(Path file, String content) throws IOException {
    if (config.isDryRun()) {
      return;
    }
    if (Files.exists(file) && !config.isForce()) {
      return;
    }
    Path parent = file.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(file, content, StandardCharsets.UTF_8);
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
    try (InputStream is = ConfigGenerator.class.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Template not found: " + resourcePath);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
