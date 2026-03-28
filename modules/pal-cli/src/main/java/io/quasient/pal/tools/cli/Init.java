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
package io.quasient.pal.tools.cli;

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.tools.cli.init.BuildTool;
import io.quasient.pal.tools.cli.init.BuildToolStrategy;
import io.quasient.pal.tools.cli.init.ConfigGenerator;
import io.quasient.pal.tools.cli.init.DeploymentMode;
import io.quasient.pal.tools.cli.init.EnvFileGenerator;
import io.quasient.pal.tools.cli.init.InfraGenerator;
import io.quasient.pal.tools.cli.init.InitConfig;
import io.quasient.pal.tools.cli.init.InitWizard;
import io.quasient.pal.tools.cli.init.JLinePromptProvider;
import io.quasient.pal.tools.cli.init.PalWeaveResolver;
import io.quasient.pal.tools.cli.init.SampleAppGenerator;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * CLI command that initializes a Java project for PAL.
 *
 * <p>Supports both interactive (wizard) and non-interactive (flag-based) modes. Handles three
 * primary scenarios:
 *
 * <ul>
 *   <li>Scaffold a brand-new Maven or Gradle project
 *   <li>Patch an existing project's build file to add PAL weaving
 *   <li>Generate PAL-specific config files
 * </ul>
 *
 * <p>The {@code --dry-run} flag previews all changes without writing any files or fetching
 * dependencies. This is critical for allowing users to review pom.xml/build.gradle changes before
 * committing.
 *
 * <p>This command does NOT require a PalDirectory connection.
 *
 * @since 1.0.0
 */
@Command(
    name = "init",
    customSynopsis = {
      "pal init [OPTIONS] [DIRECTORY]",
      "  pal init                    Interactive wizard in current directory",
      "  pal init my-project         Create new project in my-project/",
      "  pal init --non-interactive  Use flags instead of prompts%n"
    },
    description = "Initialize a project for PAL",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = {"URF_UNREAD_FIELD", "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"},
    justification = "Fields set by picocli framework via annotation-driven injection")
public class Init extends AbstractPalSubcommand {

  /** Default PAL version fallback when manifest is unavailable. */
  static final String DEFAULT_PAL_VERSION = "1.0.0-SNAPSHOT";

  /** Default AspectJ version. */
  private static final String DEFAULT_ASPECTJ_VERSION = "1.9.24";

  /** Regex for Maven groupId in pom.xml. */
  private static final Pattern POM_GROUP_ID =
      Pattern.compile("<groupId>\\s*([^<]+?)\\s*</groupId>");

  /** Regex for Maven artifactId in pom.xml. */
  private static final Pattern POM_ARTIFACT_ID =
      Pattern.compile("<artifactId>\\s*([^<]+?)\\s*</artifactId>");

  /** Regex for Maven version in pom.xml. */
  private static final Pattern POM_VERSION_PATTERN =
      Pattern.compile("<version>\\s*([^<]+?)\\s*</version>");

  /** Regex for Gradle group property. */
  private static final Pattern GRADLE_GROUP =
      Pattern.compile("^\\s*group\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.MULTILINE);

  /** Regex for Gradle version property. */
  private static final Pattern GRADLE_VERSION_PATTERN =
      Pattern.compile("^\\s*version\\s*=\\s*['\"]([^'\"]+)['\"]", Pattern.MULTILINE);

  /** Picocli command spec, injected by the framework. */
  @Spec private CommandSpec spec;

  /** Maven group ID for the project. */
  @Option(
      names = {"--group-id"},
      paramLabel = "GROUP_ID",
      description = "Maven/Gradle group ID")
  private String groupId;

  /** Maven artifact ID for the project. */
  @Option(
      names = {"--artifact-id"},
      paramLabel = "ARTIFACT_ID",
      description = "Maven/Gradle artifact ID")
  private String artifactId;

  /** Project version string. */
  @Option(
      names = {"--version"},
      paramLabel = "VERSION",
      description = "Project version (default: 1.0-SNAPSHOT)")
  private String projectVersion;

  /** Fully-qualified main class name. */
  @Option(
      names = {"--main-class"},
      paramLabel = "CLASS",
      description = "Fully qualified main class name")
  private String mainClass;

  /** Java package name. */
  @Option(
      names = {"--package"},
      paramLabel = "PACKAGE",
      description = "Java package name (inferred from group ID if omitted)")
  private String packageName;

  /** Build tool selection. */
  @Option(
      names = {"--build-tool"},
      paramLabel = "maven|gradle",
      description = "Build tool: maven or gradle (default: auto-detect or maven)")
  private String buildToolStr;

  /** Deployment mode. */
  @Option(
      names = {"--mode"},
      paramLabel = "local|distributed|both",
      defaultValue = "local",
      description = "Deployment mode: local, distributed, both (default: ${DEFAULT-VALUE})")
  private String mode;

  /** Skip interactive prompts and use flags/defaults. */
  @Option(
      names = {"--non-interactive", "-y"},
      description = "Skip interactive prompts, use defaults/flags")
  private boolean nonInteractive;

  /** Generate sample application code. */
  @Option(
      names = {"--sample-app"},
      negatable = true,
      defaultValue = "true",
      fallbackValue = "true",
      description = "Generate sample application code (default: ${DEFAULT-VALUE})")
  private boolean sampleApp;

  /** Generate RPC policy config. */
  @Option(
      names = {"--rpc-policy"},
      negatable = true,
      defaultValue = "false",
      fallbackValue = "true",
      description = "Generate RPC policy config (default: ${DEFAULT-VALUE})")
  private boolean rpcPolicy;

  /** Generate recording scope config. */
  @Option(
      names = {"--scope-policy"},
      negatable = true,
      defaultValue = "false",
      fallbackValue = "true",
      description = "Generate recording scope config (default: ${DEFAULT-VALUE})")
  private boolean scopePolicy;

  /** Generate logging configuration. */
  @Option(
      names = {"--logging-config"},
      negatable = true,
      defaultValue = "true",
      fallbackValue = "true",
      description = "Generate logging configuration (default: ${DEFAULT-VALUE})")
  private boolean loggingConfig;

  /** Generate intercept bundle example. */
  @Option(
      names = {"--intercept-bundle"},
      negatable = true,
      defaultValue = "false",
      fallbackValue = "true",
      description = "Generate intercept bundle example (default: ${DEFAULT-VALUE})")
  private boolean interceptBundle;

  /** Generate Docker infrastructure files. */
  @Option(
      names = {"--infra"},
      negatable = true,
      defaultValue = "false",
      fallbackValue = "true",
      description = "Generate Docker infrastructure files (default: ${DEFAULT-VALUE})")
  private boolean infra;

  /** Overwrite existing files without prompting. */
  @Option(
      names = {"--force"},
      description = "Overwrite existing files without prompting")
  private boolean force;

  /** Preview changes without writing any files. */
  @Option(
      names = {"--dry-run"},
      description = "Preview changes without writing any files")
  private boolean dryRun;

  /** Displays the help message when requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Target directory (positional). */
  @Parameters(
      index = "0",
      arity = "0..1",
      paramLabel = "DIRECTORY",
      description = "Target directory (default: current)")
  private Path targetDir;

  /** Detected build tool for existing projects, set during initialize(). */
  private BuildTool detectedBuildTool;

  /** Resolved PAL version, set during initialize(). */
  private String palVersion;

  /** Constructs a new {@code Init} command instance. */
  Init() {}

  /**
   * Validates command-line input.
   *
   * <p>In non-interactive mode, ensures required flags ({@code --group-id}, {@code --artifact-id},
   * {@code --main-class}) are provided for new projects. Validates {@code --build-tool} and {@code
   * --mode} values if specified.
   *
   * @throws RuntimeException if required flags are missing or values are invalid
   */
  @Override
  protected void validateInput() {
    if (buildToolStr != null) {
      String normalized = buildToolStr.toUpperCase(Locale.ROOT);
      if (!"MAVEN".equals(normalized) && !"GRADLE".equals(normalized)) {
        throw new RuntimeException(
            "Invalid build tool: '" + buildToolStr + "'. Must be 'maven' or 'gradle'.");
      }
    }

    String normalizedMode = mode.toUpperCase(Locale.ROOT);
    if (!"LOCAL".equals(normalizedMode)
        && !"DISTRIBUTED".equals(normalizedMode)
        && !"BOTH".equals(normalizedMode)) {
      throw new RuntimeException(
          "Invalid mode: '" + mode + "'. Must be 'local', 'distributed', or 'both'.");
    }

    if (nonInteractive) {
      validateNonInteractiveFlags();
    }
  }

  /**
   * Validates that required flags are present for non-interactive mode on new projects.
   *
   * @throws RuntimeException if required flags are missing
   */
  private void validateNonInteractiveFlags() {
    Path effectiveDir = resolveTargetDir();
    BuildTool detected = BuildToolStrategy.detect(effectiveDir);
    boolean isExistingProject = detected != null;

    if (!isExistingProject) {
      if (!optionGiven(groupId)) {
        throw new RuntimeException(
            "Missing required option '--group-id' for non-interactive mode on new projects.");
      }
      if (!optionGiven(artifactId)) {
        throw new RuntimeException(
            "Missing required option '--artifact-id' for non-interactive mode on new projects.");
      }
      if (!optionGiven(mainClass)) {
        throw new RuntimeException(
            "Missing required option '--main-class' for non-interactive mode on new projects.");
      }
    }
  }

  /**
   * Initializes the command by detecting existing projects and resolving the PAL version.
   *
   * <p>Unlike most subcommands, Init does NOT need a PalDirectory connection. This method skips
   * directory setup entirely.
   */
  @Override
  protected void initialize() {
    Path effectiveDir = resolveTargetDir();
    detectedBuildTool = BuildToolStrategy.detect(effectiveDir);
    palVersion = resolvePalVersion();

    // Redirect out to picocli's writer so captured output works in tests
    PrintWriter pw = spec.commandLine().getOut();
    this.out = new PrintStream(new PrintWriterOutputStream(pw), true, StandardCharsets.UTF_8);
  }

  /**
   * Executes the init command.
   *
   * <p>In non-interactive mode, builds an {@link InitConfig} from flags. In interactive mode,
   * launches the {@link InitWizard}. Then invokes generators in order:
   *
   * <ol>
   *   <li>BuildToolStrategy.generate/patch
   *   <li>SampleAppGenerator
   *   <li>ConfigGenerator
   *   <li>EnvFileGenerator
   *   <li>InfraGenerator
   * </ol>
   *
   * <p>After generators, invokes {@link PalWeaveResolver#ensureAvailable(String,
   * java.nio.file.Path)} to check/fetch pal-weave (skipped in dry-run).
   *
   * @return 0 on success, 1 on error
   * @throws Exception if an error occurs during generation
   */
  @Override
  protected int runCommand() throws Exception {
    InitConfig config;
    if (nonInteractive) {
      config = buildConfigFromFlags();
    } else {
      InitWizard wizard = new InitWizard(new JLinePromptProvider(out), resolveTargetDir());
      config = wizard.run();
      // Apply dry-run and force from CLI flags even in interactive mode
      config = applyFlagsToWizardConfig(config);
    }

    if (config.isDryRun()) {
      out.println("Dry run \u2014 no files will be written.");
      out.println();
    }

    Path effectiveDir = config.getTargetDir() != null ? config.getTargetDir() : resolveTargetDir();
    if (!config.isDryRun()) {
      Files.createDirectories(effectiveDir);
    }

    List<FileAction> actions = new ArrayList<>();

    // Step 1: Build tool generate/patch
    generateOrPatchBuildFiles(config, effectiveDir, actions);

    // Step 2: Sample app
    List<Path> sampleFiles = new SampleAppGenerator(config).generate(effectiveDir);
    for (Path f : sampleFiles) {
      actions.add(new FileAction("[CREATE]", f));
    }

    // Step 3: Config files
    List<Path> configFiles = new ConfigGenerator(config).generate(effectiveDir);
    for (Path f : configFiles) {
      actions.add(new FileAction("[CREATE]", f));
    }

    // Step 4: Env file
    List<Path> envFiles = new EnvFileGenerator(config).generate(effectiveDir);
    for (Path f : envFiles) {
      actions.add(new FileAction("[CREATE]", f));
    }

    // Step 5: Infra files
    List<Path> infraFiles = new InfraGenerator(config).generate(effectiveDir);
    for (Path f : infraFiles) {
      actions.add(new FileAction("[CREATE]", f));
    }

    // Print summary
    printSummary(config, actions);

    // Step 6: pal-weave resolution
    resolvePalWeave(config);

    // Print next steps
    printNextSteps(config, effectiveDir);

    return 0;
  }

  /**
   * Closes resources. Init does not open a directory connection, so this is a no-op.
   *
   * @throws IOException never thrown
   */
  @Override
  protected void closeResources() throws IOException {
    // No resources to close — Init does not use PalDirectory
  }

  /**
   * Builds an {@link InitConfig} from command-line flags for non-interactive mode.
   *
   * @return the constructed config
   */
  InitConfig buildConfigFromFlags() {
    Path effectiveDir = resolveTargetDir();
    BuildTool buildTool = resolveBuildTool();
    DeploymentMode deploymentMode = DeploymentMode.valueOf(mode.toUpperCase(Locale.ROOT));

    InitConfig.Builder builder =
        InitConfig.builder()
            .groupId(groupId)
            .artifactId(artifactId)
            .mainClass(mainClass)
            .buildTool(buildTool)
            .deploymentMode(deploymentMode)
            .palVersion(palVersion)
            .aspectjVersion(DEFAULT_ASPECTJ_VERSION)
            .targetDir(effectiveDir)
            .sampleApp(sampleApp)
            .rpcPolicy(rpcPolicy)
            .scopePolicy(scopePolicy)
            .loggingConfig(loggingConfig)
            .interceptBundle(interceptBundle)
            .infra(infra)
            .force(force)
            .dryRun(dryRun);

    if (projectVersion != null) {
      builder.projectVersion(projectVersion);
    }
    if (packageName != null) {
      builder.packageName(packageName);
    }

    // Detect existing project and parse identity from build file
    if (detectedBuildTool != null) {
      BuildToolStrategy strategy = BuildToolStrategy.forType(detectedBuildTool);
      Path buildFile = effectiveDir.resolve(strategy.getBuildFileName());
      if (!Files.exists(buildFile) && detectedBuildTool == BuildTool.GRADLE) {
        Path ktsFile = effectiveDir.resolve("build.gradle.kts");
        if (Files.exists(ktsFile)) {
          buildFile = ktsFile;
        }
      }
      if (Files.exists(buildFile)) {
        builder.existingBuildFile(buildFile);
        populateFromBuildFile(builder, buildFile, detectedBuildTool);
      }
    }

    return builder.build();
  }

  /**
   * Resolves the target directory from the positional parameter or defaults to the current working
   * directory.
   *
   * @return the resolved target directory
   */
  private Path resolveTargetDir() {
    if (targetDir != null) {
      return targetDir.toAbsolutePath();
    }
    return Path.of(".").toAbsolutePath().normalize();
  }

  /**
   * Resolves the build tool from the flag, auto-detection, or default.
   *
   * @return the resolved build tool
   */
  private BuildTool resolveBuildTool() {
    if (buildToolStr != null) {
      return BuildTool.valueOf(buildToolStr.toUpperCase(Locale.ROOT));
    }
    if (detectedBuildTool != null) {
      return detectedBuildTool;
    }
    return BuildTool.MAVEN;
  }

  /**
   * Populates the config builder with identity fields parsed from an existing build file. Only sets
   * fields that were not already provided via CLI flags.
   *
   * @param builder the config builder to populate
   * @param buildFile the path to the build file
   * @param buildTool the detected build tool type
   */
  private void populateFromBuildFile(
      InitConfig.Builder builder, Path buildFile, BuildTool buildTool) {
    try {
      String content = Files.readString(buildFile, StandardCharsets.UTF_8);
      if (buildTool == BuildTool.MAVEN) {
        String stripped = content.replaceAll("(?s)<parent>.*?</parent>", "");
        stripped = stripped.replaceAll("(?s)<dependencies>.*?</dependencies>", "");
        stripped = stripped.replaceAll("(?s)<build>.*?</build>", "");

        String parsedGroupId = firstMatch(POM_GROUP_ID, stripped);
        String parsedArtifactId = firstMatch(POM_ARTIFACT_ID, stripped);
        String parsedVersion = firstMatch(POM_VERSION_PATTERN, stripped);

        if (groupId == null && parsedGroupId != null) {
          builder.groupId(parsedGroupId);
        }
        if (artifactId == null && parsedArtifactId != null) {
          builder.artifactId(parsedArtifactId);
        }
        if (projectVersion == null && parsedVersion != null) {
          builder.projectVersion(parsedVersion);
        }
      } else {
        String parsedGroupId = firstMatch(GRADLE_GROUP, content);
        String parsedVersion = firstMatch(GRADLE_VERSION_PATTERN, content);

        if (groupId == null && parsedGroupId != null) {
          builder.groupId(parsedGroupId);
        }
        if (projectVersion == null && parsedVersion != null) {
          builder.projectVersion(parsedVersion);
        }
      }
    } catch (IOException e) {
      // Silently ignore parse failures; the builder will use defaults
    }
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
   * Resolves the PAL version from the JAR manifest, falling back to the default.
   *
   * @return the resolved PAL version
   */
  static String resolvePalVersion() {
    Package pkg = Init.class.getPackage();
    if (pkg != null) {
      String version = pkg.getImplementationVersion();
      if (version != null && !version.isEmpty()) {
        return version;
      }
    }
    return DEFAULT_PAL_VERSION;
  }

  /**
   * Applies dry-run and force flags to a wizard-produced config.
   *
   * @param wizardConfig the config from the wizard
   * @return the config with flags applied
   */
  private InitConfig applyFlagsToWizardConfig(InitConfig wizardConfig) {
    if (!dryRun && !force) {
      return wizardConfig;
    }
    return InitConfig.builder()
        .groupId(wizardConfig.getGroupId())
        .artifactId(wizardConfig.getArtifactId())
        .projectVersion(wizardConfig.getProjectVersion())
        .mainClass(wizardConfig.getMainClass())
        .packageName(wizardConfig.getPackageName())
        .buildTool(wizardConfig.getBuildTool())
        .deploymentMode(wizardConfig.getDeploymentMode())
        .palVersion(wizardConfig.getPalVersion())
        .aspectjVersion(wizardConfig.getAspectjVersion())
        .targetDir(wizardConfig.getTargetDir())
        .existingBuildFile(wizardConfig.getExistingBuildFile())
        .sampleApp(wizardConfig.isSampleApp())
        .rpcPolicy(wizardConfig.isRpcPolicy())
        .scopePolicy(wizardConfig.isScopePolicy())
        .loggingConfig(wizardConfig.isLoggingConfig())
        .interceptBundle(wizardConfig.isInterceptBundle())
        .infra(wizardConfig.isInfra())
        .force(force)
        .dryRun(dryRun)
        .build();
  }

  /**
   * Generates or patches build files depending on whether this is a new or existing project.
   *
   * @param config the init configuration
   * @param effectiveDir the target directory
   * @param actions the list to record file actions into
   * @throws IOException if an I/O error occurs
   */
  private void generateOrPatchBuildFiles(
      InitConfig config, Path effectiveDir, List<FileAction> actions) throws IOException {
    BuildToolStrategy strategy = BuildToolStrategy.forType(config.getBuildTool());

    if (config.isNewProject()) {
      strategy.generate(config, effectiveDir);
      actions.add(new FileAction("[CREATE]", effectiveDir.resolve(strategy.getBuildFileName())));
      if (config.getBuildTool() == BuildTool.GRADLE) {
        actions.add(
            new FileAction("[CREATE]", effectiveDir.resolve(strategy.getSettingsFileName())));
      }
    } else {
      Path buildFile = config.getExistingBuildFile();
      strategy.patch(config, buildFile);
      actions.add(new FileAction("[PATCH]", buildFile));
    }
  }

  /**
   * Prints a summary of generated or would-be-generated files.
   *
   * @param config the init configuration
   * @param actions the file actions performed
   */
  private void printSummary(InitConfig config, List<FileAction> actions) {
    if (config.isDryRun()) {
      boolean hasPatch = actions.stream().anyMatch(a -> "[PATCH]".equals(a.action));
      boolean hasCreate = actions.stream().anyMatch(a -> "[CREATE]".equals(a.action));

      if (hasPatch) {
        out.println("Would modify:");
        for (FileAction action : actions) {
          if ("[PATCH]".equals(action.action)) {
            out.println("  " + action.action + " " + action.path);
          }
        }
      }
      if (hasCreate) {
        out.println("Would create:");
        for (FileAction action : actions) {
          if ("[CREATE]".equals(action.action)) {
            out.println("  " + action.action + " " + action.path);
          }
        }
      }
    } else {
      for (FileAction action : actions) {
        out.println("  \u2713 " + action.action + " " + action.path);
      }
    }
  }

  /**
   * Resolves pal-weave availability, fetching from Maven Central if needed.
   *
   * @param config the init configuration
   */
  private void resolvePalWeave(InitConfig config) {
    PalWeaveResolver resolver = new PalWeaveResolver(out, config.isDryRun());
    Path repoRoot = PalWeaveResolver.getLocalRepoPath();
    resolver.ensureAvailable(config.getPalVersion(), repoRoot);
  }

  /**
   * Prints next steps tailored to build tool and deployment mode.
   *
   * @param config the init configuration
   * @param effectiveDir the target directory
   */
  private void printNextSteps(InitConfig config, Path effectiveDir) {
    if (config.isDryRun()) {
      return;
    }

    out.println();
    out.println("\u2713 Project initialized!");
    out.println();
    out.println("Next steps:");

    int step = 1;
    Path cwd = Path.of(".").toAbsolutePath().normalize();
    if (!effectiveDir.equals(cwd)) {
      out.println("  " + step + ". cd " + effectiveDir);
      step++;
    }

    if (config.isDistributed() && config.isInfra()) {
      out.println("  " + step + ". cd infra && ./start.sh   # Start etcd + Kafka");
      step++;
      out.println("  " + step + ". cd ..");
      step++;
    }

    out.println("  " + step + ". source .env.pal          # Set up environment");
    step++;

    String compileCmd = config.getBuildTool() == BuildTool.GRADLE ? "gradle build" : "mvn compile";
    out.println("  " + step + ". " + compileCmd + "              # Build with AspectJ weaving");
    step++;

    String runCmd;
    if (config.isDistributed()) {
      runCmd =
          "pal run -d localhost:2379 -k localhost:29092 --wal my-wal --zmq-rpc auto"
              + " -cp target/classes "
              + (config.getMainClass() != null
                  ? config.getMainClass()
                  : config.getGroupId() + ".Main");
    } else {
      String cpDir =
          config.getBuildTool() == BuildTool.GRADLE ? "build/classes/java/main" : "target/classes";
      runCmd =
          "pal run --wal file:./wal -cp "
              + cpDir
              + " "
              + (config.getMainClass() != null
                  ? config.getMainClass()
                  : config.getGroupId() + ".Main");
    }
    out.println("  " + step + ". " + runCmd);
  }

  /**
   * Records a file action (create or patch) for summary output.
   *
   * @param action the action type ("[CREATE]" or "[PATCH]")
   * @param path the file path
   */
  private record FileAction(String action, Path path) {}

  /** An {@link OutputStream} that delegates to a {@link PrintWriter}. */
  private static class PrintWriterOutputStream extends OutputStream {
    /** The underlying writer to delegate to. */
    private final PrintWriter pw;

    /** Creates a new stream that delegates to the given writer. */
    PrintWriterOutputStream(PrintWriter pw) {
      this.pw = pw;
    }

    @Override
    public void write(int b) throws IOException {
      pw.write(b);
      pw.flush();
    }

    @Override
    public void write(byte[] buf, int off, int len) throws IOException {
      pw.write(new String(buf, off, len, StandardCharsets.UTF_8));
      pw.flush();
    }

    @Override
    public void flush() throws IOException {
      pw.flush();
    }
  }
}
