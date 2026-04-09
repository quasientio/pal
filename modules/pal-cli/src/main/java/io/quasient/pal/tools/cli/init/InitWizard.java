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
import java.util.stream.Stream;

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
 *   <li>Prompt for intent (JSON-RPC, interceptable, intercepting, main class, Kafka)
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

  /**
   * Regex for Gradle main class in the {@code application} block. Matches Kotlin DSL ({@code
   * mainClass = "..."} or {@code mainClass.set("...")}), Groovy DSL ({@code mainClass = '...'}),
   * and the legacy {@code mainClassName} property.
   */
  private static final Pattern GRADLE_MAIN_CLASS =
      Pattern.compile(
          "mainClass(?:Name)?\\s*(?:=\\s*|\\.set\\s*\\(\\s*)['\"]([^'\"]+)['\"]",
          Pattern.MULTILINE);

  /** Regex for Maven {@code <mainClass>} element in plugin configurations. */
  private static final Pattern POM_MAIN_CLASS =
      Pattern.compile("<mainClass>\\s*([^<]+?)\\s*</mainClass>");

  /** The prompt provider for user interaction. */
  private final PromptProvider promptProvider;

  /** The target directory for project initialization. */
  private final Path targetDir;

  /** Override for PAL version (used in testing). */
  private final String palVersionOverride;

  /** CLI flag overrides that skip prompts for already-provided values. */
  private final WizardOverrides overrides;

  /** Main class detected from the existing build file or source tree, set during configuration. */
  private String detectedMainClass;

  /**
   * Creates a wizard with the given prompt provider and target directory.
   *
   * @param promptProvider the provider for interactive prompts
   * @param targetDir the target directory for initialization
   */
  public InitWizard(PromptProvider promptProvider, Path targetDir) {
    this(promptProvider, targetDir, null, WizardOverrides.none());
  }

  /**
   * Creates a wizard with the given prompt provider, target directory, and CLI flag overrides.
   *
   * @param promptProvider the provider for interactive prompts
   * @param targetDir the target directory for initialization
   * @param overrides CLI flag overrides that skip prompts
   */
  public InitWizard(PromptProvider promptProvider, Path targetDir, WizardOverrides overrides) {
    this(promptProvider, targetDir, null, overrides);
  }

  /**
   * Creates a wizard with the given prompt provider, target directory, and PAL version override.
   *
   * <p>Package-private to allow tests to inject a specific PAL version without overrides.
   *
   * @param promptProvider the provider for interactive prompts
   * @param targetDir the target directory for initialization
   * @param palVersionOverride override for the PAL version, or {@code null} to resolve from runtime
   */
  InitWizard(PromptProvider promptProvider, Path targetDir, String palVersionOverride) {
    this(promptProvider, targetDir, palVersionOverride, WizardOverrides.none());
  }

  /**
   * Creates a wizard with the given prompt provider, target directory, optional PAL version
   * override, and CLI flag overrides.
   *
   * <p>Package-private to allow tests to inject a specific PAL version.
   *
   * @param promptProvider the provider for interactive prompts
   * @param targetDir the target directory for initialization
   * @param palVersionOverride override for the PAL version, or {@code null} to resolve from runtime
   * @param overrides CLI flag overrides that skip prompts
   */
  InitWizard(
      PromptProvider promptProvider,
      Path targetDir,
      String palVersionOverride,
      WizardOverrides overrides) {
    this.promptProvider = promptProvider;
    this.targetDir = targetDir;
    this.palVersionOverride = palVersionOverride;
    this.overrides = overrides;
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
    String detectedMainClass = null;
    if (isExistingProject) {
      groupId = configureExistingProject(builder, detectedBuildTool);
      detectedMainClass = this.detectedMainClass;
    } else {
      groupId = configureNewProject(builder);
    }

    promptIntents(builder, groupId, detectedMainClass);
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

    // CLI flags override parsed identity
    String groupId = overrides.getGroupId() != null ? overrides.getGroupId() : identity.groupId;
    String artifactId =
        overrides.getArtifactId() != null ? overrides.getArtifactId() : identity.artifactId;
    String version =
        overrides.getProjectVersion() != null ? overrides.getProjectVersion() : identity.version;

    if (groupId != null) {
      builder.groupId(groupId);
    }
    if (artifactId != null) {
      builder.artifactId(artifactId);
    }
    if (version != null) {
      builder.projectVersion(version);
    }

    // Detect main class: build file first, then source scan
    String mainClass = identity.mainClass;
    if (mainClass == null) {
      Path buildDir = buildFile.getParent();
      if (buildDir != null) {
        mainClass = scanSourcesForMainClass(buildDir.resolve("src/main/java"));
      }
    }
    this.detectedMainClass = mainClass;

    String desc =
        "Detected existing "
            + detectedBuildTool
            + " project"
            + (identity.groupId != null ? ": " + identity.groupId : "")
            + (identity.artifactId != null ? ":" + identity.artifactId : "")
            + (identity.version != null ? " (" + identity.version + ")" : "");
    promptProvider.println(desc);

    return groupId;
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

    BuildTool buildTool;
    if (overrides.getBuildTool() != null) {
      buildTool = overrides.getBuildTool();
    } else {
      List<BuildTool> buildTools = Arrays.asList(BuildTool.values());
      buildTool = promptProvider.promptSelect("Build tool", buildTools, BuildTool.GRADLE);
    }
    builder.buildTool(buildTool);

    String groupId;
    if (overrides.getGroupId() != null) {
      groupId = overrides.getGroupId();
    } else {
      groupId = promptProvider.promptText("Project group ID", DEFAULT_GROUP_ID);
    }
    builder.groupId(groupId);

    String artifactId;
    if (overrides.getArtifactId() != null) {
      artifactId = overrides.getArtifactId();
    } else {
      artifactId = promptProvider.promptText("Project artifact ID", DEFAULT_ARTIFACT_ID);
    }
    builder.artifactId(artifactId);

    String version;
    if (overrides.getProjectVersion() != null) {
      version = overrides.getProjectVersion();
    } else {
      version = promptProvider.promptText("Project version", DEFAULT_VERSION);
    }
    builder.projectVersion(version);

    return groupId;
  }

  /**
   * Prompts for intent-based configuration: JSON-RPC, interceptable, intercepting, main class, and
   * Kafka.
   *
   * <p>The JSON-RPC question is asked first. When the user selects "gateway only", the
   * interceptable/intercepting/Kafka questions are skipped (they are irrelevant for RPC-only mode)
   * and weaving is disabled.
   *
   * @param builder the config builder
   * @param groupId the project group ID, or {@code null} if unknown
   * @param detectedMainClass the main class detected from the build file or sources, or {@code
   *     null}
   */
  private void promptIntents(InitConfig.Builder builder, String groupId, String detectedMainClass) {
    // 1. RPC question
    boolean jsonRpc;
    boolean rpcGatewayOnly = false;
    if (overrides.getJsonRpc() != null) {
      jsonRpc = overrides.getJsonRpc();
      builder.jsonRpc(jsonRpc);
    } else {
      String noRpc = "No";
      String gatewayOnly = "Yes, RPC only (no weaving needed)";
      String withPipeline = "Yes, alongside message pipeline";
      List<String> rpcOptions = Arrays.asList(noRpc, withPipeline, gatewayOnly);
      String rpcChoice =
          promptProvider.promptSelect("Will you expose methods via JSON-RPC?", rpcOptions, noRpc);

      jsonRpc = !noRpc.equals(rpcChoice);
      rpcGatewayOnly = gatewayOnly.equals(rpcChoice);
      builder.jsonRpc(jsonRpc);
      if (rpcGatewayOnly) {
        builder.weaving(false);
      }
    }

    // 2. Intercept questions (skip for gateway-only)
    boolean intercepting = false;
    if (!rpcGatewayOnly) {
      boolean interceptable;
      if (overrides.getInterceptable() != null) {
        interceptable = overrides.getInterceptable();
      } else {
        interceptable =
            promptProvider.promptYesNo("Will this app be interceptable by other peers?", false);
      }
      builder.interceptable(interceptable);

      if (overrides.getIntercepting() != null) {
        intercepting = overrides.getIntercepting();
      } else {
        intercepting = promptProvider.promptYesNo("Will this app intercept other peers?", false);
      }
      builder.intercepting(intercepting);
    }

    // 3. Main class
    if (overrides.isAsService()) {
      // --as-service: no main class, skip prompt
    } else if (overrides.getMainClass() != null) {
      builder.mainClass(overrides.getMainClass());
    } else {
      String defaultMainClass;
      if (detectedMainClass != null) {
        defaultMainClass = detectedMainClass;
      } else {
        defaultMainClass =
            (groupId != null ? groupId : DEFAULT_GROUP_ID) + "." + DEFAULT_MAIN_CLASS_SIMPLE;
      }
      if (intercepting || rpcGatewayOnly) {
        String asServiceOption = "Run as service (no main class)";
        List<String> options = Arrays.asList(asServiceOption, defaultMainClass);
        String selected = promptProvider.promptSelect("Run mode", options, asServiceOption);
        if (!asServiceOption.equals(selected)) {
          builder.mainClass(selected);
        }
      } else {
        String mainClass = promptProvider.promptText("Main class (for pal run)", defaultMainClass);
        if (mainClass != null && !mainClass.isBlank()) {
          builder.mainClass(mainClass);
        }
      }
    }

    // 4. Kafka (skip for gateway-only)
    if (!rpcGatewayOnly) {
      boolean kafka;
      if (overrides.getKafka() != null) {
        kafka = overrides.getKafka();
      } else {
        kafka = promptProvider.promptYesNo("Will you use Kafka for WAL (write-ahead log)?", false);
      }
      builder.kafka(kafka);
    }
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
   * Resolves the path to the build file for the detected build tool. Delegates to {@link
   * BuildToolStrategy#findBuildFile(Path, BuildTool)} which handles both root-level and
   * multi-module Gradle project layouts.
   *
   * @param buildTool the detected build tool
   * @return the path to the build file
   */
  private Path resolveBuildFile(BuildTool buildTool) {
    Path found = BuildToolStrategy.findBuildFile(targetDir, buildTool);
    if (found != null) {
      return found;
    }
    // Fallback: return expected root-level path for downstream handling
    BuildToolStrategy strategy = BuildToolStrategy.forType(buildTool);
    return targetDir.resolve(strategy.getBuildFileName());
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
      return new ProjectIdentity(null, null, null, null);
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
    // mainClass lives inside <build>, so search the full content
    String mainClass = firstMatch(POM_MAIN_CLASS, content);
    return new ProjectIdentity(groupId, artifactId, version, mainClass);
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
    String mainClass = firstMatch(GRADLE_MAIN_CLASS, content);
    return new ProjectIdentity(groupId, null, version, mainClass);
  }

  /**
   * Scans a source root directory for Java files containing a {@code public static void main}
   * method and returns the fully-qualified class name of the first match.
   *
   * @param sourceRoot the source root directory (e.g., {@code src/main/java})
   * @return the FQCN of the first class with a main method, or {@code null} if none found
   */
  public static String scanSourcesForMainClass(Path sourceRoot) {
    if (!Files.isDirectory(sourceRoot)) {
      return null;
    }
    try (Stream<Path> files = Files.walk(sourceRoot)) {
      return files
          .filter(p -> p.toString().endsWith(".java"))
          .filter(InitWizard::containsMainMethod)
          .findFirst()
          .map(p -> javaFileToFqcn(sourceRoot, p))
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Checks whether a Java source file contains a {@code public static void main} declaration.
   *
   * @param javaFile the path to the Java file
   * @return {@code true} if the file contains a main method signature
   */
  private static boolean containsMainMethod(Path javaFile) {
    try {
      String content = Files.readString(javaFile, StandardCharsets.UTF_8);
      return content.contains("public static void main");
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Converts a Java source file path to a fully-qualified class name relative to the source root.
   *
   * @param sourceRoot the source root directory
   * @param javaFile the path to the Java file
   * @return the fully-qualified class name
   */
  private static String javaFileToFqcn(Path sourceRoot, Path javaFile) {
    String relative = sourceRoot.relativize(javaFile).toString();
    return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
  }

  /**
   * Returns the first capture group match for a pattern in the given text.
   *
   * @param pattern the regex pattern with at least one capture group
   * @param text the text to search
   * @return the captured string, or {@code null} if none found
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

    /** The main class, or {@code null} if not found in the build file. */
    final String mainClass;

    /**
     * Constructs a project identity.
     *
     * @param groupId the group ID
     * @param artifactId the artifact ID
     * @param version the version
     * @param mainClass the main class
     */
    ProjectIdentity(String groupId, String artifactId, String version, String mainClass) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.mainClass = mainClass;
    }
  }
}
