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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrates the interactive prompt flow for {@code pal init}.
 *
 * <p>The wizard detects existing projects (Maven or Gradle), pre-populates fields from build files,
 * and prompts for remaining configuration using a {@link PromptProvider}. The result is an
 * immutable {@link InitConfig} ready for project generation or patching.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li>Detect existing project via {@link BuildToolStrategy#detect(Path)}
 *   <li>For existing projects: parse groupId/artifactId/version from the build file
 *   <li>For new projects: prompt for build tool and project coordinates
 *   <li>Prompt for intent (interceptable, intercepting, main class, Kafka)
 *   <li>Set PAL version from runtime
 *   <li>Build and return {@link InitConfig}
 * </ol>
 *
 * @since 1.0.0
 */
public final class InitWizard {

  /** Default group ID for new projects. */
  private static final String DEFAULT_GROUP_ID = "com.example";

  /** Default artifact ID for new projects. */
  private static final String DEFAULT_ARTIFACT_ID = "my-pal-app";

  /** Default project version. */
  private static final String DEFAULT_VERSION = "1.0-SNAPSHOT";

  /** Default simple class name appended to the group ID for the main class default. */
  private static final String DEFAULT_MAIN_CLASS_SIMPLE = "Main";

  /** Regex pattern for extracting Maven groupId from pom.xml. */
  private static final Pattern POM_GROUP_ID =
      Pattern.compile("<groupId>\\s*([^<]+?)\\s*</groupId>");

  /** Regex pattern for extracting Maven artifactId from pom.xml. */
  private static final Pattern POM_ARTIFACT_ID =
      Pattern.compile("<artifactId>\\s*([^<]+?)\\s*</artifactId>");

  /** Regex pattern for extracting Maven version from pom.xml. */
  private static final Pattern POM_VERSION = Pattern.compile("<version>\\s*([^<]+?)\\s*</version>");

  /**
   * Regex pattern for extracting Gradle group property. Matches both {@code group = 'value'} and
   * {@code group = "value"}.
   */
  private static final Pattern GRADLE_GROUP =
      Pattern.compile("^\\s*group\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.MULTILINE);

  /** Regex for Gradle version property. */
  private static final Pattern GRADLE_VERSION =
      Pattern.compile("^\\s*version\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.MULTILINE);

  /** The prompt provider for user interaction. */
  private final PromptProvider promptProvider;

  /** The target directory for project initialization. */
  private final Path targetDir;

  /** Override for PAL version (used in testing). */
  private final String palVersionOverride;

  /**
   * Creates a wizard with the given prompt provider and target directory.
   *
   * @param promptProvider the provider for interactive prompts
   * @param targetDir the target directory for initialization
   */
  public InitWizard(PromptProvider promptProvider, Path targetDir) {
    this(promptProvider, targetDir, null);
  }

  /**
   * Creates a wizard with the given prompt provider, target directory, and optional PAL version
   * override.
   *
   * <p>Package-private to allow tests to inject a specific PAL version.
   *
   * @param promptProvider the provider for interactive prompts
   * @param targetDir the target directory for initialization
   * @param palVersionOverride override for the PAL version, or {@code null} to resolve from runtime
   */
  InitWizard(PromptProvider promptProvider, Path targetDir, String palVersionOverride) {
    this.promptProvider = promptProvider;
    this.targetDir = targetDir;
    this.palVersionOverride = palVersionOverride;
  }

  /**
   * Runs the interactive wizard and returns the resulting configuration.
   *
   * @return the completed {@link InitConfig}
   */
  public InitConfig run() {
    InitConfig.Builder builder = InitConfig.builder().targetDir(targetDir);

    BuildTool detectedBuildTool = BuildToolStrategy.detect(targetDir);
    boolean isExistingProject = detectedBuildTool != null;

    String groupId;
    if (isExistingProject) {
      groupId = configureExistingProject(builder, detectedBuildTool);
    } else {
      groupId = configureNewProject(builder);
    }

    promptIntents(builder, groupId);
    setPalVersion(builder);

    return builder.build();
  }

  /**
   * Configures the builder for an existing project by parsing the build file and skipping identity
   * prompts.
   *
   * @param builder the config builder
   * @param detectedBuildTool the detected build tool
   * @return the detected group ID, or {@code null} if not found in the build file
   */
  private String configureExistingProject(InitConfig.Builder builder, BuildTool detectedBuildTool) {
    builder.buildTool(detectedBuildTool);

    Path buildFile = resolveBuildFile(detectedBuildTool);
    builder.existingBuildFile(buildFile);

    ProjectIdentity identity = parseBuildFile(buildFile, detectedBuildTool);

    if (identity.groupId != null) {
      builder.groupId(identity.groupId);
    }
    if (identity.artifactId != null) {
      builder.artifactId(identity.artifactId);
    }
    if (identity.version != null) {
      builder.projectVersion(identity.version);
    }

    String desc =
        "Detected existing "
            + detectedBuildTool
            + " project"
            + (identity.groupId != null ? ": " + identity.groupId : "")
            + (identity.artifactId != null ? ":" + identity.artifactId : "")
            + (identity.version != null ? " (" + identity.version + ")" : "");
    promptProvider.println(desc);

    return identity.groupId;
  }

  /**
   * Configures the builder for a new project by prompting for build tool and project coordinates.
   *
   * @param builder the config builder
   * @return the group ID entered by the user
   */
  private String configureNewProject(InitConfig.Builder builder) {
    promptProvider.println("Welcome to PAL! Let's set up your project.");
    promptProvider.println("");

    List<BuildTool> buildTools = Arrays.asList(BuildTool.values());
    BuildTool buildTool = promptProvider.promptSelect("Build tool", buildTools, BuildTool.MAVEN);
    builder.buildTool(buildTool);

    String groupId = promptProvider.promptText("Project group ID", DEFAULT_GROUP_ID);
    builder.groupId(groupId);

    String artifactId = promptProvider.promptText("Project artifact ID", DEFAULT_ARTIFACT_ID);
    builder.artifactId(artifactId);

    String version = promptProvider.promptText("Project version", DEFAULT_VERSION);
    builder.projectVersion(version);

    return groupId;
  }

  /**
   * Prompts for intent-based configuration: interceptable, intercepting, main class, and Kafka.
   *
   * <p>Replaces the previous deployment mode, main class, and feature toggle prompts with a
   * streamlined set of intent questions that drive all downstream generation.
   *
   * @param builder the config builder
   * @param groupId the project group ID, or {@code null} if unknown
   */
  private void promptIntents(InitConfig.Builder builder, String groupId) {
    boolean interceptable =
        promptProvider.promptYesNo("Will this app be interceptable by other peers?", false);
    builder.interceptable(interceptable);

    boolean intercepting =
        promptProvider.promptYesNo("Will this app intercept other peers?", false);
    builder.intercepting(intercepting);

    String defaultMainClass =
        (groupId != null ? groupId : DEFAULT_GROUP_ID) + "." + DEFAULT_MAIN_CLASS_SIMPLE;
    String prompt =
        intercepting
            ? "Main class (for pal run, leave blank for --as-service)"
            : "Main class (for pal run)";
    String mainClass = promptProvider.promptText(prompt, defaultMainClass);
    if (mainClass != null && !mainClass.isBlank()) {
      builder.mainClass(mainClass);
    }

    boolean kafka =
        promptProvider.promptYesNo("Will you use Kafka for WAL (write-ahead log)?", false);
    builder.kafka(kafka);
  }

  /**
   * Sets the PAL version from the runtime manifest or the override.
   *
   * @param builder the config builder
   */
  private void setPalVersion(InitConfig.Builder builder) {
    String version = palVersionOverride;
    if (version == null) {
      version = resolvePalVersionFromRuntime();
    }
    if (version != null) {
      builder.palVersion(version);
    }
  }

  /**
   * Resolves the PAL version from the runtime manifest.
   *
   * @return the PAL version string, or {@code null} if unavailable
   */
  static String resolvePalVersionFromRuntime() {
    Package pkg = InitWizard.class.getPackage();
    if (pkg != null) {
      return pkg.getImplementationVersion();
    }
    return null;
  }

  /**
   * Resolves the path to the build file for the detected build tool.
   *
   * @param buildTool the detected build tool
   * @return the path to the build file
   */
  private Path resolveBuildFile(BuildTool buildTool) {
    BuildToolStrategy strategy = BuildToolStrategy.forType(buildTool);
    Path buildFile = targetDir.resolve(strategy.getBuildFileName());
    if (Files.exists(buildFile)) {
      return buildFile;
    }
    // For Gradle, also check .kts variant
    if (buildTool == BuildTool.GRADLE) {
      Path ktsFile = targetDir.resolve("build.gradle.kts");
      if (Files.exists(ktsFile)) {
        return ktsFile;
      }
    }
    return buildFile;
  }

  /**
   * Parses project identity (groupId, artifactId, version) from a build file.
   *
   * @param buildFile the path to the build file
   * @param buildTool the build tool type
   * @return the parsed project identity
   */
  private ProjectIdentity parseBuildFile(Path buildFile, BuildTool buildTool) {
    try {
      String content = Files.readString(buildFile, StandardCharsets.UTF_8);
      if (buildTool == BuildTool.MAVEN) {
        return parseMavenPom(content);
      } else {
        return parseGradleBuild(content);
      }
    } catch (IOException e) {
      return new ProjectIdentity(null, null, null);
    }
  }

  /**
   * Parses project identity from a Maven pom.xml content string.
   *
   * <p>Only extracts the top-level project groupId, artifactId, and version — not those nested
   * inside {@code <parent>} or {@code <dependency>} elements. Uses a simple heuristic: takes the
   * first occurrence of each element before any {@code <dependencies>} or {@code <build>} section.
   *
   * @param content the pom.xml content
   * @return the parsed identity
   */
  static ProjectIdentity parseMavenPom(String content) {
    // Strip the <parent> block to avoid picking up parent coordinates
    String stripped = content.replaceAll("(?s)<parent>.*?</parent>", "");
    // Strip dependencies and build sections
    stripped = stripped.replaceAll("(?s)<dependencies>.*?</dependencies>", "");
    stripped = stripped.replaceAll("(?s)<build>.*?</build>", "");

    String groupId = firstMatch(POM_GROUP_ID, stripped);
    String artifactId = firstMatch(POM_ARTIFACT_ID, stripped);
    String version = firstMatch(POM_VERSION, stripped);
    return new ProjectIdentity(groupId, artifactId, version);
  }

  /**
   * Parses project identity from a Gradle build file content string.
   *
   * @param content the build.gradle or build.gradle.kts content
   * @return the parsed identity
   */
  static ProjectIdentity parseGradleBuild(String content) {
    String groupId = firstMatch(GRADLE_GROUP, content);
    String version = firstMatch(GRADLE_VERSION, content);
    return new ProjectIdentity(groupId, null, version);
  }

  /**
   * Returns the first capture group match for a pattern in the given text.
   *
   * @param pattern the regex pattern with at least one capture group
   * @param text the text to search
   * @return the captured string, or {@code null} if no match
   */
  private static String firstMatch(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /**
   * Holds the parsed project identity from a build file.
   *
   * <p>Package-private for testing.
   */
  static final class ProjectIdentity {

    /** The Maven/Gradle group ID, or {@code null} if not found. */
    final String groupId;

    /** The Maven artifact ID, or {@code null} if not found or not applicable. */
    final String artifactId;

    /** The project version, or {@code null} if not found. */
    final String version;

    /**
     * Constructs a project identity.
     *
     * @param groupId the group ID
     * @param artifactId the artifact ID
     * @param version the version
     */
    ProjectIdentity(String groupId, String artifactId, String version) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
    }
  }
}
