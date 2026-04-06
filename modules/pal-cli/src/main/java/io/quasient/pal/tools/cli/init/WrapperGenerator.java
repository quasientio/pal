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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Generates build-tool wrapper scripts ({@code mvnw}/{@code gradlew}) for new PAL projects.
 *
 * <p>Wrappers let users build without a system-wide Maven or Gradle installation. The wrapper
 * scripts and configuration files are bundled as classpath resources and copied into the project
 * root during generation.
 *
 * <p>Only runs for new projects ({@link InitConfig#isNewProject()}). Existing projects already have
 * their own build tool setup.
 *
 * <p>Respects {@link InitConfig#isDryRun()}: when true, computes and reports what would be
 * generated but does not write files.
 *
 * @since 1.0.0
 */
public final class WrapperGenerator {

  /** Resource path prefix for wrapper files. */
  private static final String RESOURCE_PREFIX = "/init/";

  /** The init configuration. */
  private final InitConfig config;

  /**
   * Creates a new generator with the given configuration.
   *
   * @param config the init configuration
   */
  public WrapperGenerator(InitConfig config) {
    this.config = config;
  }

  /**
   * Generates wrapper scripts and configuration for the configured build tool.
   *
   * <p>For Maven: creates {@code mvnw}, {@code mvnw.cmd}, and {@code .mvn/wrapper/
   * maven-wrapper.properties}. For Gradle: creates {@code gradlew}, {@code gradlew.bat}, {@code
   * gradle/wrapper/gradle-wrapper.properties}, and {@code gradle/wrapper/gradle-wrapper.jar}.
   *
   * <p>When {@code dryRun=true}, returns the list of files that would be generated without writing
   * them.
   *
   * @param targetDir the project root directory
   * @return the list of generated (or would-be-generated) file paths
   * @throws IOException if an I/O error occurs during file writing
   */
  public List<Path> generate(Path targetDir) throws IOException {
    if (config.getBuildTool() == BuildTool.GRADLE) {
      return generateGradleWrapper(targetDir);
    } else {
      return generateMavenWrapper(targetDir);
    }
  }

  /**
   * Generates Maven wrapper files.
   *
   * @param targetDir the project root directory
   * @return the list of generated file paths
   * @throws IOException if an I/O error occurs
   */
  private List<Path> generateMavenWrapper(Path targetDir) throws IOException {
    List<Path> generated = new ArrayList<>();

    Path mvnw = targetDir.resolve("mvnw");
    Path mvnwCmd = targetDir.resolve("mvnw.cmd");
    Path propsDir = targetDir.resolve(".mvn/wrapper");
    Path props = propsDir.resolve("maven-wrapper.properties");

    generated.add(mvnw);
    generated.add(mvnwCmd);
    generated.add(props);

    if (!config.isDryRun()) {
      copyResource("mvnw", mvnw);
      copyResource("mvnw.cmd", mvnwCmd);
      Files.createDirectories(propsDir);
      copyResource("maven-wrapper.properties", props);
      makeExecutable(mvnw);
    }

    return Collections.unmodifiableList(generated);
  }

  /**
   * Generates Gradle wrapper files.
   *
   * @param targetDir the project root directory
   * @return the list of generated file paths
   * @throws IOException if an I/O error occurs
   */
  private List<Path> generateGradleWrapper(Path targetDir) throws IOException {
    List<Path> generated = new ArrayList<>();

    Path gradlew = targetDir.resolve("gradlew");
    Path gradlewBat = targetDir.resolve("gradlew.bat");
    Path wrapperDir = targetDir.resolve("gradle/wrapper");
    Path props = wrapperDir.resolve("gradle-wrapper.properties");
    Path jar = wrapperDir.resolve("gradle-wrapper.jar");

    generated.add(gradlew);
    generated.add(gradlewBat);
    generated.add(props);
    generated.add(jar);

    if (!config.isDryRun()) {
      copyResource("gradlew", gradlew);
      copyResource("gradlew.bat", gradlewBat);
      Files.createDirectories(wrapperDir);
      copyResource("gradle-wrapper.properties", props);
      copyResource("gradle-wrapper.jar", jar);
      makeExecutable(gradlew);
    }

    return Collections.unmodifiableList(generated);
  }

  /**
   * Copies a classpath resource to a target file.
   *
   * @param resourceName the resource file name (under {@code /init/})
   * @param target the target file path
   * @throws IOException if the resource cannot be found or copied
   */
  private static void copyResource(String resourceName, Path target) throws IOException {
    String resourcePath = RESOURCE_PREFIX + resourceName;
    InputStream is = WrapperGenerator.class.getResourceAsStream(resourcePath);
    if (is == null) {
      throw new IOException("Wrapper resource not found: " + resourcePath);
    }
    try (is) {
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.copy(is, target);
    }
  }

  /**
   * Sets the executable permission on a file (Unix/Linux/macOS only).
   *
   * <p>On systems that do not support POSIX file permissions (e.g., Windows), this method is a
   * no-op.
   *
   * @param file the file to make executable
   */
  private static void makeExecutable(Path file) {
    try {
      Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file);
      perms.add(PosixFilePermission.OWNER_EXECUTE);
      perms.add(PosixFilePermission.GROUP_EXECUTE);
      perms.add(PosixFilePermission.OTHERS_EXECUTE);
      Files.setPosixFilePermissions(file, perms);
    } catch (UnsupportedOperationException | IOException e) {
      // Non-POSIX filesystem (e.g., Windows) — skip
    }
  }
}
