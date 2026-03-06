/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static picocli.CommandLine.Option;
import static picocli.CommandLine.Parameters;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.core.service.Main;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * CLI command that replays an application deterministically from a recorded Write-Ahead Log (WAL).
 *
 * <p>This command wraps {@code pal run} with replay-specific options and a simplified argument
 * surface. It supports both Chronicle Queue WALs (local, via {@code file:} prefix) and Kafka WAL
 * topics (distributed, via topic name). When a PalDirectory is available ({@code -d}), log names
 * are resolved to their backing Kafka bootstrap servers automatically.
 *
 * <p>Internally, it composes the appropriate {@code --replay-wal} and related flags and delegates
 * to {@link Main} for peer startup and replay execution.
 *
 * <p><strong>Exit codes:</strong>
 *
 * <ul>
 *   <li>{@code 0} — replay completed with zero divergences
 *   <li>{@code 1} — application error (e.g., missing class, uncaught exception)
 *   <li>{@code 2} — divergences detected between live execution and WAL oracle
 * </ul>
 */
@Command(
    name = "replay",
    customSynopsis = {
      "pal replay [OPTIONS] class [args...]",
      "            (to replay a class)",
      "    or pal replay [OPTIONS] -jar jarFile [args...]",
      "            (to replay a jar file)%n"
    },
    description = "Replay application deterministically from a recorded WAL",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = {"URF_UNREAD_FIELD", "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"},
    justification = "Fields set by picocli framework via annotation-driven injection")
public class Replay extends AbstractPalSubcommand {

  /** Exit code returned when divergences are detected during replay. */
  static final int EXIT_CODE_DIVERGENCES = 2;

  /** Parent command that provides access to the PalDirectory connection string. */
  @ParentCommand PalCommand palCommand;

  /**
   * Path to the WAL to replay from. Use the {@code file:} prefix for Chronicle Queue WALs (e.g.,
   * {@code file:/tmp/my-wal}), or a plain topic name for Kafka WALs.
   */
  @Option(
      names = {"-w", "--wal"},
      required = true,
      paramLabel = "name|file:/path",
      description = "WAL path to replay from (file:/path for Chronicle, or topic name for Kafka)")
  private String walPath;

  /**
   * Kafka bootstrap servers for direct access to Kafka WAL topics. Required for Kafka WAL topics
   * when PalDirectory ({@code -d}) is not available.
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      paramLabel = "bootstrap_servers",
      description = "Kafka bootstrap servers (required for Kafka WAL topics without -d)")
  private String kafkaServers;

  /** Kafka servers resolved from PalDirectory during initialization. */
  private String resolvedKafkaServers;

  /**
   * Policy controlling how divergences between live execution and the WAL oracle are handled.
   *
   * <ul>
   *   <li>{@code WARN} — log each divergence and continue (default)
   *   <li>{@code HALT} — stop immediately on the first divergence
   *   <li>{@code IGNORE} — silently record divergences without logging
   * </ul>
   */
  @Option(
      names = {"--divergence-policy"},
      defaultValue = "WARN",
      paramLabel = "WARN|HALT|IGNORE",
      description = "Action on divergence: WARN, HALT, IGNORE (default: ${DEFAULT-VALUE})")
  private String divergencePolicy;

  /**
   * Thread ordering mode for multi-threaded deterministic replay. Controls whether entry-point
   * injection follows WAL-offset ordering ({@code ordered}) or runs without ordering constraints
   * ({@code unordered}).
   */
  @Option(
      names = {"--replay-threading"},
      defaultValue = "ordered",
      paramLabel = "ordered|unordered",
      description =
          "Thread ordering for multi-threaded replay: ordered or unordered"
              + " (default: ${DEFAULT-VALUE})")
  private String replayThreading;

  /**
   * Classpath for the application to replay. Specifies folders or JAR files containing the
   * application classes. Required when replaying a class; optional when using {@code -jar}.
   */
  @Option(
      names = {"-cp", "--classpath"},
      paramLabel = "CLASSPATH",
      description = "Classpath for the application (required when replaying a class)")
  private String classpath;

  /**
   * JAR file to replay. When specified, the Main-Class is read from the JAR's MANIFEST.MF file. The
   * JAR is automatically added to the classpath. Mutually exclusive with specifying a main class
   * directly.
   */
  @Option(
      names = {"-jar"},
      paramLabel = "jarFile",
      description = "JAR file to replay (Main-Class from manifest)")
  private String jarFile;

  /** Displays the help message when requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /**
   * Enable JavaFX Application Thread execution for replaying JavaFX applications.
   *
   * <p>When enabled, entry points recorded on the JavaFX Application Thread are routed to the real
   * FX thread via {@code Platform.runLater()} during replay. Required for replaying JavaFX
   * applications that use button handlers, event callbacks, or other FX thread interactions.
   */
  @Option(
      names = {"--fx-thread"},
      description =
          "Enable JavaFX Application Thread execution for replaying JavaFX applications"
              + " (default: ${DEFAULT-VALUE})",
      defaultValue = "false")
  private boolean fxThread;

  /**
   * Delay in milliseconds before processing each OPERATION entry during replay.
   *
   * <p>Used for slow-motion replay visualization: pauses before each recorded operation to make it
   * easier to observe the replay on the UI. A value of {@code 0} disables the delay. The default of
   * {@code 2000} (2 seconds) provides a comfortable pace for visual debugging.
   */
  @Option(
      names = {"--delay"},
      paramLabel = "milliseconds",
      defaultValue = "0",
      description =
          "Delay in milliseconds before each operation for slow-motion replay visualization"
              + " (default: ${DEFAULT-VALUE})")
  private String delay;

  /**
   * Positional arguments from the command line. When not using {@code -jar}, the first element is
   * the main class name and the rest are application arguments. When using {@code -jar}, all
   * elements are application arguments.
   */
  @SuppressWarnings("unused")
  @Parameters(arity = "0..*", description = "Main class and/or application arguments")
  private List<String> positionalArgs;

  /**
   * The fully qualified name of the main class to replay. Parsed from {@code positionalArgs} in
   * {@link #validateInput()} when not using {@code -jar}.
   */
  private String mainClass;

  /** Application arguments passed to the main class during replay. */
  private List<String> appArgs;

  /** Constructs a new {@code Replay} command instance. */
  Replay() {}

  /**
   * Validates the command-line input.
   *
   * <p>Ensures the {@code --divergence-policy} value is one of the accepted values: {@code WARN},
   * {@code HALT}, or {@code IGNORE}. For non-Chronicle WAL paths, validates that either {@code -k}
   * (Kafka servers) or {@code -d} (PalDirectory) is available. Validates that either a main class
   * or {@code -jar} is specified, and that {@code -cp} is provided when using a main class.
   *
   * @throws RuntimeException if the divergence policy is not a valid value, if a Kafka WAL topic is
   *     specified without Kafka servers or PalDirectory, if neither main class nor -jar is
   *     specified, or if -cp is missing when using a main class
   */
  @Override
  protected void validateInput() {
    String upper = divergencePolicy.toUpperCase(Locale.ROOT);
    if (!"WARN".equals(upper) && !"HALT".equals(upper) && !"IGNORE".equals(upper)) {
      throw new RuntimeException(
          "Invalid divergence policy: '" + divergencePolicy + "'. Must be WARN, HALT, or IGNORE.");
    }
    divergencePolicy = upper;

    if (!walPath.startsWith("file:") && kafkaServers == null && !isPalDirectoryAvailable()) {
      throw new RuntimeException(
          "Kafka WAL topics require --kafka-servers (-k) or a PAL directory (-d). "
              + "For Chronicle Queue WALs, use the 'file:' prefix (e.g., file:/tmp/my-wal).");
    }

    // Parse positional arguments into mainClass and appArgs based on -jar usage
    if (jarFile != null) {
      // When using -jar, all positional args are application arguments
      appArgs = positionalArgs;
    } else if (positionalArgs != null && !positionalArgs.isEmpty()) {
      // When not using -jar, first positional arg is the main class
      mainClass = positionalArgs.get(0);
      appArgs = positionalArgs.size() > 1 ? positionalArgs.subList(1, positionalArgs.size()) : null;
    }

    // Validate that either mainClass or -jar is specified
    if (mainClass == null && jarFile == null) {
      throw new RuntimeException(
          "Either a main class or -jar must be specified. "
              + "Usage: pal replay [OPTIONS] class [args...] or pal replay [OPTIONS] -jar jarFile [args...]");
    }

    // Validate that -cp is provided when using a main class (not -jar)
    if (jarFile == null && classpath == null) {
      throw new RuntimeException(
          "Classpath (-cp/--classpath) is required when replaying a class. "
              + "Use -jar to replay a JAR file without specifying a classpath.");
    }
  }

  /**
   * Checks whether a PalDirectory connection is available via the parent command.
   *
   * @return {@code true} if a non-empty directory connection string is configured
   */
  private boolean isPalDirectoryAvailable() {
    if (palCommand == null) {
      return false;
    }
    String connStr = palCommand.getPalDirectoryConnectionString();
    return connStr != null && !connStr.isEmpty() && !PalDirectory.NO_URL.equals(connStr);
  }

  /**
   * Initializes the replay command by resolving Kafka bootstrap servers if needed.
   *
   * <p>For Chronicle WAL paths (starting with {@code file:}), no initialization is required. For
   * Kafka WAL topics, this method resolves the bootstrap servers either from PalDirectory (if
   * available) or from the explicit {@code -k} option.
   *
   * @throws Exception if directory connection initialization fails
   */
  @Override
  protected void initialize() throws Exception {
    if (walPath == null || walPath.startsWith("file:")) {
      return;
    }

    if (isPalDirectoryAvailable()) {
      initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
      PalDirectory palDirectory = null;
      try {
        Optional<PalDirectory> palDirOpt = directoryConnectionProvider.get();
        palDirectory = palDirOpt.orElse(null);
      } catch (RuntimeException e) {
        // PalDirectory connection failed; fall through to use explicit -k
      }

      if (palDirectory != null) {
        LogInfo logInfo = palDirectory.getLogInfo(walPath);
        if (logInfo != null && logInfo.getBootstrapServers() != null) {
          resolvedKafkaServers = logInfo.getBootstrapServers();
        }
      }
    }

    if (resolvedKafkaServers == null && kafkaServers != null) {
      resolvedKafkaServers = kafkaServers;
    }
  }

  /**
   * Executes the replay by composing arguments and delegating to {@link Main}.
   *
   * <p>Builds an argument list with the {@code --replay-wal} flag and other replay-specific
   * options, then invokes {@link Main} via picocli's {@link CommandLine#execute(String...)} method.
   *
   * @return the exit code from Main, which is {@code 0} for zero divergences, {@code 1} for
   *     application errors, or {@code 2} for divergences detected
   * @throws Exception if an unexpected error occurs during delegation
   */
  @Override
  protected int runCommand() throws Exception {
    String[] mainArgs = buildMainArgs();
    return new CommandLine(new Main()).execute(mainArgs);
  }

  /**
   * Closes resources including the directory connection provider if it was initialized.
   *
   * @throws IOException if an I/O error occurs while closing resources
   */
  @Override
  protected void closeResources() throws IOException {
    if (directoryConnectionProvider != null) {
      super.closeResources();
    }
  }

  /**
   * Builds the argument array to pass to {@link Main}.
   *
   * <p>Composes the following flags:
   *
   * <ul>
   *   <li>{@code --replay-wal <walPath>} — the WAL path for deterministic replay
   *   <li>{@code --replay-divergence-policy <policy>} — the divergence handling policy
   *   <li>{@code -k <kafkaServers>} — Kafka bootstrap servers (if Kafka WAL, resolved or explicit)
   *   <li>{@code -cp <classpath>} — the application classpath (if provided)
   *   <li>{@code -jar <jarFile>} — the JAR file to replay (if specified), OR
   *   <li>The main class name (if not using -jar)
   *   <li>Any additional application arguments
   * </ul>
   *
   * @return the composed argument array
   */
  String[] buildMainArgs() {
    List<String> args = new ArrayList<>();
    args.add("--replay-wal");
    args.add(walPath);
    args.add("--replay-divergence-policy");
    args.add(divergencePolicy);
    args.add("--replay-threading");
    args.add(replayThreading);
    if (resolvedKafkaServers != null) {
      args.add("-k");
      args.add(resolvedKafkaServers);
    }
    if (classpath != null) {
      args.add("-cp");
      args.add(classpath);
    }
    if (fxThread) {
      args.add("--fx-thread");
    }
    if (delay != null && !"0".equals(delay)) {
      args.add("--replay-delay");
      args.add(delay);
    }
    if (jarFile != null) {
      args.add("-jar");
      args.add(jarFile);
    } else {
      args.add(mainClass);
    }
    if (appArgs != null) {
      args.addAll(appArgs);
    }
    return args.toArray(new String[0]);
  }
}
