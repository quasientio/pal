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
import io.quasient.pal.core.service.Main;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * CLI command that replays an application deterministically from a recorded Write-Ahead Log (WAL).
 *
 * <p>This command wraps {@code pal run} with replay-specific options and a simplified argument
 * surface. It does not require etcd, Kafka, or intercept configuration — replay runs locally
 * against a Chronicle Queue WAL.
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
    customSynopsis = "pal replay [OPTIONS] class [args...]%n",
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

  /**
   * Path to the WAL to replay from. Must use the {@code file:} prefix for Chronicle Queue WALs
   * (e.g., {@code file:/tmp/my-wal}).
   */
  @Option(
      names = {"-w", "--wal"},
      required = true,
      paramLabel = "file:/path",
      description = "WAL path to replay from (e.g., file:/tmp/my-wal)")
  private String walPath;

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
   * Classpath for the application to replay. Specifies folders or JAR files containing the
   * application classes.
   */
  @Option(
      names = {"-cp", "--classpath"},
      required = true,
      paramLabel = "CLASSPATH",
      description = "Classpath for the application")
  private String classpath;

  /** Displays the help message when requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** The fully qualified name of the main class to replay. */
  @Parameters(index = "0", description = "Main class to replay")
  private String mainClass;

  /** Optional application arguments passed to the main class during replay. */
  @SuppressWarnings("unused")
  @Parameters(index = "1..*", arity = "0..*", description = "Application arguments")
  private List<String> appArgs;

  /** Constructs a new {@code Replay} command instance. */
  Replay() {}

  /**
   * Validates the command-line input.
   *
   * <p>Ensures the {@code --divergence-policy} value is one of the accepted values: {@code WARN},
   * {@code HALT}, or {@code IGNORE}.
   *
   * @throws RuntimeException if the divergence policy is not a valid value
   */
  @Override
  protected void validateInput() {
    String upper = divergencePolicy.toUpperCase(Locale.ROOT);
    if (!"WARN".equals(upper) && !"HALT".equals(upper) && !"IGNORE".equals(upper)) {
      throw new RuntimeException(
          "Invalid divergence policy: '" + divergencePolicy + "'. Must be WARN, HALT, or IGNORE.");
    }
    divergencePolicy = upper;
  }

  /**
   * Initialization step — no-op for replay since no external services are required.
   *
   * @throws Exception never thrown
   */
  @Override
  protected void initialize() throws Exception {
    // Replay requires no external services (no etcd, no Kafka).
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
   * Closes resources — no-op for replay since no directory connection is established.
   *
   * @throws IOException never thrown
   */
  @Override
  protected void closeResources() throws IOException {
    // No directory connection to close.
  }

  /**
   * Builds the argument array to pass to {@link Main}.
   *
   * <p>Composes the following flags:
   *
   * <ul>
   *   <li>{@code --replay-wal <walPath>} — the WAL path for deterministic replay
   *   <li>{@code --replay-divergence-policy <policy>} — the divergence handling policy
   *   <li>{@code -cp <classpath>} — the application classpath
   *   <li>The main class name
   *   <li>Any additional application arguments
   * </ul>
   *
   * <p>No etcd ({@code -d}), Kafka ({@code -k}), or intercept flags are included.
   *
   * @return the composed argument array
   */
  String[] buildMainArgs() {
    List<String> args = new ArrayList<>();
    args.add("--replay-wal");
    args.add(walPath);
    args.add("--replay-divergence-policy");
    args.add(divergencePolicy);
    args.add("-cp");
    args.add(classpath);
    args.add(mainClass);
    if (appArgs != null) {
      args.addAll(appArgs);
    }
    return args.toArray(new String[0]);
  }
}
