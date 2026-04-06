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
 * Generates sample Java source files for new PAL projects.
 *
 * <p>When {@code sampleApp=true} in the config, produces a main class and a sample service class
 * with interesting operations suitable for demonstrating PAL features. All generated code uses
 * standard Java only — no PAL imports — since weaving is transparent.
 *
 * <p>Respects {@link InitConfig#isDryRun()}: when true, computes and reports what would be
 * generated but does not write files.
 *
 * @since 1.0.0
 */
public final class SampleAppGenerator {

  /** Resource path prefix for template files. */
  private static final String TEMPLATE_PREFIX = "/init/";

  /** The init configuration. */
  private final InitConfig config;

  /**
   * Creates a new generator with the given configuration.
   *
   * @param config the init configuration
   */
  public SampleAppGenerator(InitConfig config) {
    this.config = config;
  }

  /**
   * Generates sample application source files.
   *
   * <p>When {@code sampleApp=false}, returns an empty list. When {@code dryRun=true}, returns the
   * list of files that would be generated without writing them.
   *
   * @param targetDir the project root directory
   * @return the list of generated (or would-be-generated) file paths
   * @throws IOException if an I/O error occurs during file writing
   */
  public List<Path> generate(Path targetDir) throws IOException {
    if (!config.isSampleApp()) {
      return Collections.emptyList();
    }

    List<Path> generated = new ArrayList<>();
    String packageName = config.getPackageName();
    String sourceDir = config.getSourceDirectory();
    Path sourceDirPath = targetDir.resolve(sourceDir);

    // Generate Main.java (skip for as-service mode)
    if (!config.isAsService()) {
      String mainClassName = extractSimpleClassName(config.getMainClass());
      String mainContent = loadTemplate("Main.java.template");
      mainContent = mainContent.replace("${package}", packageName);
      mainContent = mainContent.replace("${mainClassName}", mainClassName);
      Path mainFile = sourceDirPath.resolve(mainClassName + ".java");
      generated.add(mainFile);
      if (!config.isDryRun()) {
        Files.createDirectories(sourceDirPath);
        Files.writeString(mainFile, mainContent, StandardCharsets.UTF_8);
      }
    }

    // Generate SampleService.java
    String serviceContent = loadTemplate("SampleService.java.template");
    serviceContent = serviceContent.replace("${package}", packageName);
    Path serviceFile = sourceDirPath.resolve("SampleService.java");
    generated.add(serviceFile);

    // Generate SampleCallbacks.java when intercepting
    if (config.isIntercepting()) {
      String callbackContent = loadTemplate("CallbackHandler.java.template");
      callbackContent = callbackContent.replace("${package}", packageName);
      Path callbackFile = sourceDirPath.resolve("SampleCallbacks.java");
      generated.add(callbackFile);
      if (!config.isDryRun()) {
        Files.createDirectories(sourceDirPath);
        Files.writeString(callbackFile, callbackContent, StandardCharsets.UTF_8);
      }
    }

    if (!config.isDryRun()) {
      Files.createDirectories(sourceDirPath);
      Files.writeString(serviceFile, serviceContent, StandardCharsets.UTF_8);
    }

    return Collections.unmodifiableList(generated);
  }

  /**
   * Extracts the simple class name from a fully-qualified class name.
   *
   * @param fqcn the fully-qualified class name (e.g., {@code "com.example.Main"})
   * @return the simple class name (e.g., {@code "Main"})
   */
  private static String extractSimpleClassName(String fqcn) {
    if (fqcn == null) {
      return "Main";
    }
    int lastDot = fqcn.lastIndexOf('.');
    return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
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
    try (InputStream is = SampleAppGenerator.class.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Template not found: " + resourcePath);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
