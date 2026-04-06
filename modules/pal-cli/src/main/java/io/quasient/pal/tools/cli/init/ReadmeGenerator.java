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
 * Generates a {@code README.md} for new PAL projects.
 *
 * <p>Content varies by build tool (Maven vs Gradle) and includes build commands, a short
 * explanation of the weaving lifecycle, and a run command. Skips generation if a {@code README.md}
 * already exists (unless {@code force=true}).
 *
 * <p>Respects {@link InitConfig#isDryRun()}: when true, reports what would be generated but does
 * not write files.
 *
 * @since 1.0.0
 */
public final class ReadmeGenerator {

  /** The init configuration. */
  private final InitConfig config;

  /**
   * Creates a new generator with the given configuration.
   *
   * @param config the init configuration
   */
  public ReadmeGenerator(InitConfig config) {
    this.config = config;
  }

  /**
   * Generates {@code README.md} in the given directory.
   *
   * <p>When {@code dryRun=true}, returns the file path without writing. When the file already
   * exists and {@code force=false}, returns an empty list.
   *
   * @param targetDir the project root directory
   * @return the list containing the generated file path, or empty if skipped
   * @throws IOException if an I/O error occurs during file writing
   */
  public List<Path> generate(Path targetDir) throws IOException {
    Path readmeFile = targetDir.resolve("README.md");

    if (!config.isDryRun() && Files.exists(readmeFile) && !config.isForce()) {
      return Collections.emptyList();
    }

    List<Path> generated = new ArrayList<>();
    generated.add(readmeFile);

    if (!config.isDryRun()) {
      Files.createDirectories(targetDir);
      Files.writeString(readmeFile, buildContent(), StandardCharsets.UTF_8);
    }

    return Collections.unmodifiableList(generated);
  }

  /**
   * Builds the README content based on the build tool and configuration.
   *
   * @return the file content
   */
  private String buildContent() {
    StringBuilder sb = new StringBuilder();
    String artifactId = safe(config.getArtifactId());
    String mainClass = resolveMainClass();

    sb.append("# ").append(artifactId).append("\n\n");
    sb.append("## Build\n\n");

    if (config.getBuildTool() == BuildTool.GRADLE) {
      appendGradleBuild(sb);
    } else {
      appendMavenBuild(sb);
    }

    sb.append("\n## Run\n\n");
    appendRunSection(sb, mainClass);

    sb.append("\n## Configuration\n\n");
    sb.append("See `config/` for PAL runtime policies (RPC filtering, recording scope,\n");
    sb.append("interception bundles). See `.env.pal` for environment variables (directory,\n");
    sb.append("Kafka, JVM tuning, logging, and more).\n");

    return sb.toString();
  }

  /**
   * Appends Maven build commands and weaving explanation.
   *
   * @param sb the string builder
   */
  private void appendMavenBuild(StringBuilder sb) {
    String mvn = config.isNewProject() ? "./mvnw" : "mvn";
    sb.append("```sh\n");
    if (config.needsWeaving()) {
      sb.append(mvn).append(" test                  # compile and test (unwoven classes)\n");
      sb.append(mvn).append(" package               # compile, test, weave, and package\n");
      sb.append(mvn).append(" package -DskipTests   # weave and package without tests\n");
      sb.append("```\n\n");
      sb.append("AspectJ weaving runs at the `prepare-package` phase, after tests.\n");
      sb.append("Unit tests always execute against **unwoven** classes.\n");
    } else {
      sb.append(mvn).append(" test                  # compile and test\n");
      sb.append(mvn).append(" package               # compile, test, and package\n");
      sb.append(mvn).append(" package -DskipTests   # package without tests\n");
      sb.append("```\n");
    }
  }

  /**
   * Appends Gradle build commands and weaving explanation.
   *
   * @param sb the string builder
   */
  private void appendGradleBuild(StringBuilder sb) {
    String gradle = config.isNewProject() ? "./gradlew" : "gradle";
    sb.append("```sh\n");
    if (config.needsWeaving()) {
      sb.append(gradle).append(" test               # compile and test (unwoven classes)\n");
      sb.append(gradle).append(" build              # compile, test, weave, and package\n");
      sb.append(gradle).append(" build -x weaveClasses  # build without weaving\n");
      sb.append("```\n\n");
      sb.append("The `weaveClasses` task runs after tests and before `jar`.\n");
      sb.append("Unit tests always execute against **unwoven** classes.\n");
    } else {
      sb.append(gradle).append(" test               # compile and test\n");
      sb.append(gradle).append(" build              # compile, test, and package\n");
      sb.append("```\n");
    }
  }

  /**
   * Appends the run section with a {@code pal run} command.
   *
   * @param sb the string builder
   * @param mainClass the resolved main class name
   */
  private void appendRunSection(StringBuilder sb, String mainClass) {
    String cpDir =
        config.getBuildTool() == BuildTool.GRADLE ? "build/classes/java/main" : "target/classes";

    String runCmd = buildRunCommand(cpDir, mainClass);
    sb.append("```sh\n");
    sb.append(runCmd).append('\n');
    sb.append("```\n\n");

    if (config.isJsonRpc()) {
      appendJsonRpcCallSection(sb);
    }

    sb.append("To enable the write-ahead log (message recording, replay, event sourcing):\n\n");
    sb.append("```sh\n");
    sb.append("# Chronicle (local, no Kafka needed)\n");
    sb.append(runCmd.replace("pal run", "pal run --wal file:./wal")).append('\n');
    if (config.needsKafka()) {
      sb.append("\n# Kafka\n");
      String kafkaCmd = runCmd.replace("pal run", "pal run --wal my-wal -k localhost:29092");
      sb.append(kafkaCmd).append('\n');
    }
    sb.append("```\n\n");

    if (config.isInfra()) {
      sb.append("Before running, start the infrastructure:\n\n");
      sb.append("```sh\n");
      sb.append("infra/start.sh\n");
      sb.append("```\n\n");
    }

    sb.append("Optionally, source `.env.pal` before running to set environment variables.\n");
    sb.append("See `pal run --help` for all available flags.\n");
  }

  /**
   * Appends a section showing how to call JSON-RPC methods from the CLI.
   *
   * <p>Demonstrates piping JSON-RPC requests to {@code pal peer call} via stdin.
   *
   * @param sb the string builder
   */
  private void appendJsonRpcCallSection(StringBuilder sb) {
    String pkg = safe(config.getPackageName());
    sb.append("Once the peer is running, call methods from the CLI:\n\n");
    sb.append("```sh\n");
    sb.append("echo '{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"call\",");
    sb.append("\"params\":{\"type\":\"").append(pkg).append(".Api\",");
    sb.append("\"method\":\"greet\",");
    sb.append("\"args\":[{\"type\":\"java.lang.String\",\"value\":\"World\"}]}}' | \\\n");
    sb.append("  pal peer call ws://localhost:7070\n");
    sb.append("```\n\n");
  }

  /**
   * Builds the {@code pal run} command string with intent-aware flags.
   *
   * @param cpDir the classpath directory
   * @param mainClass the main class name fallback
   * @return the formatted run command
   */
  private String buildRunCommand(String cpDir, String mainClass) {
    return config.buildRunCommand(cpDir, mainClass);
  }

  /**
   * Resolves the main class name from config, falling back to groupId + ".Main".
   *
   * @return the main class name
   */
  private String resolveMainClass() {
    if (config.getMainClass() != null) {
      return config.getMainClass();
    }
    return safe(config.getGroupId()) + ".Main";
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
}
