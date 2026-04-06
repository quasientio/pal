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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates Docker infrastructure files based on the project's intent flags.
 *
 * <p>Selects between three compose templates depending on which infrastructure is needed:
 *
 * <ul>
 *   <li>etcd only — for intercepts without Kafka
 *   <li>Kafka only — for WAL without intercepts
 *   <li>etcd + Kafka — for intercepts with Kafka WAL
 * </ul>
 *
 * <p>Also generates service-aware start/stop scripts that accept an optional service argument.
 *
 * <p>Respects {@link InitConfig#isDryRun()}: when true, computes and reports what would be
 * generated but does not write files.
 *
 * @since 1.0.0
 */
public final class InfraGenerator {

  /** Resource path prefix for template files. */
  private static final String TEMPLATE_PREFIX = "/init/";

  /** The init configuration. */
  private final InitConfig config;

  /**
   * Creates a new generator with the given configuration.
   *
   * @param config the init configuration
   */
  public InfraGenerator(InitConfig config) {
    this.config = config;
  }

  /**
   * Generates Docker infrastructure files.
   *
   * <p>When no infrastructure is needed, returns an empty list. When {@code dryRun=true}, returns
   * the list of files that would be generated without writing them.
   *
   * @param targetDir the project root directory
   * @return the list of generated (or would-be-generated) file paths
   * @throws IOException if an I/O error occurs during file writing
   */
  public List<Path> generate(Path targetDir) throws IOException {
    if (!config.isInfra()) {
      return Collections.emptyList();
    }

    Path infraDir = targetDir.resolve("infra");
    List<Path> generated = new ArrayList<>();

    // docker-compose.yml — selected based on intent
    Path composeFile = infraDir.resolve("docker-compose.yml");
    String composeContent = loadTemplate(selectComposeTemplate());
    generated.add(composeFile);

    // .env — built inline based on what services are present
    Path envFile = infraDir.resolve(".env");
    String envContent = buildEnvContent();
    generated.add(envFile);

    // start.sh
    Path startScript = infraDir.resolve("start.sh");
    String startContent = loadTemplate("start.sh.template");
    generated.add(startScript);

    // stop.sh
    Path stopScript = infraDir.resolve("stop.sh");
    String stopContent = loadTemplate("stop.sh.template");
    generated.add(stopScript);

    if (!config.isDryRun()) {
      Files.createDirectories(infraDir);
      Files.writeString(composeFile, composeContent, StandardCharsets.UTF_8);
      Files.writeString(envFile, envContent, StandardCharsets.UTF_8);
      Files.writeString(startScript, startContent, StandardCharsets.UTF_8);
      startScript.toFile().setExecutable(true);
      Files.writeString(stopScript, stopContent, StandardCharsets.UTF_8);
      stopScript.toFile().setExecutable(true);
    }

    return Collections.unmodifiableList(generated);
  }

  /**
   * Selects the compose template based on which infrastructure is needed.
   *
   * @return the template file name
   */
  private String selectComposeTemplate() {
    if (config.needsEtcd() && config.needsKafka()) {
      return "docker-compose-full.yml.template";
    } else if (config.needsEtcd()) {
      return "docker-compose-etcd.yml.template";
    } else {
      return "docker-compose-kafka.yml.template";
    }
  }

  /**
   * Builds the {@code .env} content with port configuration for the active services.
   *
   * @return the env file content
   */
  private String buildEnvContent() {
    StringBuilder sb = new StringBuilder();
    sb.append("# Docker environment for PAL infrastructure\n");
    if (config.needsEtcd()) {
      sb.append("ETCD_CLIENT_PORT=2379\n");
      sb.append("ETCD_PEER_PORT=2380\n");
    }
    if (config.needsKafka()) {
      sb.append("KAFKA_PORT=9092\n");
      sb.append("KAFKA_HOST_PORT=29092\n");
      sb.append("KAFKA_CONTROLLER_PORT=9093\n");
    }
    return sb.toString();
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
    try (InputStream is = InfraGenerator.class.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Template not found: " + resourcePath);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
