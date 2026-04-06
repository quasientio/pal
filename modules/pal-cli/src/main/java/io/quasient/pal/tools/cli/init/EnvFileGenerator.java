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
import java.util.Collections;
import java.util.List;

/**
 * Generates a {@code .env.pal} sourceable shell file with commented-out environment variable
 * exports for WAL, etcd (when intercepts are enabled), Kafka (when Kafka WAL is enabled), and
 * logging configuration.
 *
 * <p>All variables are commented out by default so users uncomment only what they need. Sections
 * are included based on the project's intent flags. A header line points to {@code pal run --help}
 * for the full list of flags and environment variables.
 *
 * <p>Respects {@link InitConfig#isDryRun()}: when true, computes and reports what would be
 * generated but does not write files.
 *
 * @since 1.0.0
 */
public final class EnvFileGenerator {

  /** The init configuration. */
  private final InitConfig config;

  /**
   * Creates a new generator with the given configuration.
   *
   * @param config the init configuration
   */
  public EnvFileGenerator(InitConfig config) {
    this.config = config;
  }

  /**
   * Generates the {@code .env.pal} file.
   *
   * <p>When {@code dryRun=true}, returns the file path without writing.
   *
   * @param targetDir the project root directory
   * @return the list containing the generated file path
   * @throws IOException if an I/O error occurs during file writing
   */
  public List<Path> generate(Path targetDir) throws IOException {
    Path envFile = targetDir.resolve(".env.pal");
    String content = buildContent();

    List<Path> generated = new ArrayList<>();
    generated.add(envFile);

    if (!config.isDryRun()) {
      Files.createDirectories(targetDir);
      Files.writeString(envFile, content, StandardCharsets.UTF_8);
    }

    return Collections.unmodifiableList(generated);
  }

  /**
   * Builds the env file content based on the deployment mode.
   *
   * @return the file content
   */
  private String buildContent() {
    StringBuilder sb = new StringBuilder();
    sb.append("# PAL Environment Configuration\n");
    sb.append("# Source this file: source .env.pal\n");
    sb.append("# See 'pal run --help' for all available flags and environment variables.\n");
    sb.append('\n');

    appendWalSection(sb);

    if (config.needsEtcd()) {
      sb.append('\n');
      appendEtcdSection(sb);
    }

    if (config.needsKafka()) {
      sb.append('\n');
      appendKafkaSection(sb);
    }

    if (config.isLoggingConfig()) {
      sb.append('\n');
      appendLoggingSection(sb);
    }

    return sb.toString();
  }

  /**
   * Appends the local WAL configuration section (commented out).
   *
   * @param sb the string builder
   */
  private void appendWalSection(StringBuilder sb) {
    sb.append("# Write-ahead log (Chronicle Queue for local, or use Kafka below)\n");
    sb.append("# export PAL_WAL=\"file:./wal\"\n");
  }

  /**
   * Appends the etcd (directory) configuration section (commented out).
   *
   * @param sb the string builder
   */
  private void appendEtcdSection(StringBuilder sb) {
    sb.append("# PAL directory (etcd) — required for intercepts and peer discovery\n");
    sb.append("# export PAL_DIRECTORY=\"localhost:2379\"\n");
  }

  /**
   * Appends the Kafka configuration section (commented out).
   *
   * @param sb the string builder
   */
  private void appendKafkaSection(StringBuilder sb) {
    sb.append("# Kafka WAL\n");
    sb.append("# export PAL_KAFKA_SERVERS=\"localhost:29092\"\n");
  }

  /**
   * Appends logging configuration environment variables (commented out).
   *
   * @param sb the string builder
   */
  private void appendLoggingSection(StringBuilder sb) {
    sb.append("# Logging (configures PAL's own runtime logging, not your application's)\n");
    sb.append("# export PAL_PEER_LOGGING_CONFIG=\"config/peer-logging.xml\"\n");
    sb.append("# export PAL_CLI_LOGGING_CONFIG=\"config/cli-logging.xml\"\n");
  }
}
