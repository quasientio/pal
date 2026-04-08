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
   * Appends the run section with progressive {@code pal run} examples, from basic to full-featured.
   *
   * @param sb the string builder
   * @param mainClass the resolved main class name
   */
  private void appendRunSection(StringBuilder sb, String mainClass) {
    String cpDir =
        config.getBuildTool() == BuildTool.GRADLE ? "build/classes/java/main" : "target/classes";
    String cpOnly = " -cp " + cpDir;
    String tail = cpOnly;
    if (mainClass != null && !config.isAsService()) {
      tail += " " + mainClass;
    }

    // 1. Basic
    sb.append("```sh\n");
    sb.append("pal run").append(tail).append('\n');
    sb.append("```\n");

    // 2. WAL examples (always shown — fundamental PAL feature)
    sb.append("\n### Write-ahead log\n\n");
    sb.append("Record every operation for replay, debugging, and event sourcing:\n\n");
    sb.append("```sh\n");
    sb.append("# Chronicle (local, no infrastructure needed)\n");
    sb.append("pal run --wal file:./wal").append(tail).append('\n');
    sb.append('\n');
    sb.append("# Kafka (distributed)\n");
    sb.append("pal run --wal my-wal -k localhost:29092").append(tail).append('\n');
    sb.append("```\n");

    // 3. JSON-RPC
    if (config.isJsonRpc()) {
      sb.append("\n### JSON-RPC\n\n");
      sb.append("Expose methods via JSON-RPC:\n\n");
      sb.append("```sh\n");
      sb.append("pal run --json-rpc 7070 --rpc-policy config/rpc-policy.yaml")
          .append(cpOnly)
          .append('\n');
      sb.append("```\n\n");
      appendJsonRpcCallSection(sb);
    }

    // 4. Interception
    if (config.isInterceptable() && config.isIntercepting()) {
      appendInterceptionWorkflow(sb, cpOnly);
    } else {
      if (config.isInterceptable()) {
        sb.append("\n### Interceptable peer\n\n");
        sb.append("Allow other peers to intercept this app's operations at runtime:\n\n");
        sb.append("```sh\n");
        sb.append("pal run --interceptable -d localhost:2379").append(tail).append('\n');
        sb.append("```\n");
      }
      if (config.isIntercepting()) {
        String name = safe(config.getArtifactId());
        if (name.isEmpty()) {
          name = "my-app";
        }
        sb.append("\n### Intercepting peer\n\n");
        sb.append("Intercept other peers' operations (ZMQ-RPC required for callbacks):\n\n");
        sb.append("```sh\n");
        sb.append("pal run -n ").append(name);
        sb.append(" --zmq-rpc auto --rpc-policy config/rpc-policy.yaml");
        sb.append(" -d localhost:2379").append(tail).append('\n');
        sb.append("```\n");
      }
    }

    // Infrastructure note
    if (config.isInfra()) {
      sb.append("\nBefore using etcd or Kafka, start the infrastructure:\n\n");
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
    sb.append("Call a method from the CLI:\n\n");
    sb.append("```sh\n");
    sb.append("echo '{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"call\",");
    sb.append("\"params\":{\"type\":\"").append(pkg).append(".Api\",");
    sb.append("\"method\":\"greet\",");
    sb.append("\"args\":[{\"type\":\"java.lang.String\",\"value\":\"World\"}]}}' | \\\n");
    sb.append("  pal peer call ws://localhost:7070\n");
    sb.append("```\n");
  }

  /**
   * Appends a full interception workflow showing how to start two peers, apply an intercept bundle,
   * and verify the callback fires.
   *
   * <p>This is used when both {@code interceptable} and {@code intercepting} are enabled, giving
   * the user a complete runnable example with three terminals.
   *
   * @param sb the string builder
   * @param cpOnly the classpath flag (e.g., {@code " -cp target/classes"})
   */
  private void appendInterceptionWorkflow(StringBuilder sb, String cpOnly) {
    String name = safe(config.getArtifactId());
    if (name.isEmpty()) {
      name = "my-app";
    }
    String pkg = safe(config.getPackageName());

    sb.append("\n### Interception\n\n");
    sb.append("Intercept operations at runtime. This example registers a BEFORE\n");
    sb.append("intercept on `processOrder` — the callback fires before each call.\n\n");

    // Terminal 1: target peer
    sb.append("**Terminal 1** — start the target peer (interceptable):\n\n");
    sb.append("```sh\n");
    sb.append("pal run --interceptable --json-rpc 7070 \\\n");
    sb.append("  --rpc-policy config/rpc-policy.yaml -d localhost:2379")
        .append(cpOnly)
        .append('\n');
    sb.append("```\n\n");

    // Terminal 2: callback peer
    sb.append("**Terminal 2** — start the callback peer:\n\n");
    sb.append("```sh\n");
    sb.append("pal run -n ").append(name);
    sb.append(" --zmq-rpc auto --rpc-policy config/rpc-policy.yaml \\\n");
    sb.append("  -d localhost:2379").append(cpOnly).append('\n');
    sb.append("```\n\n");

    // Terminal 3: apply + call
    sb.append("**Terminal 3** — apply the intercept bundle, then call the method:\n\n");
    sb.append("```sh\n");
    sb.append("pal -d localhost:2379 intercept apply config/intercept-bundle.yaml\n\n");
    sb.append("echo '{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"call\",");
    sb.append("\"params\":{\"type\":\"").append(pkg).append(".SampleService\",");
    sb.append("\"method\":\"processOrder\",");
    sb.append("\"args\":[{\"type\":\"java.lang.String\",\"value\":\"Laptop\"},");
    sb.append("{\"type\":\"int\",\"value\":\"1\"},");
    sb.append("{\"type\":\"double\",\"value\":\"999.99\"}]}}' | \\\n");
    sb.append("  pal peer call ws://localhost:7070\n");
    sb.append("```\n\n");

    // Expected output
    sb.append("Terminal 2 shows the callback:\n\n");
    sb.append("```\n");
    sb.append("[intercept] BEFORE callback, args=[Laptop, 1, 999.99]\n");
    sb.append("```\n");
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
