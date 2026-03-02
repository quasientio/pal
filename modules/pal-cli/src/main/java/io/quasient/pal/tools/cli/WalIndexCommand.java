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
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalEntryKind;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.common.replay.WalReader;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * CLI command that indexes and analyzes a Write-Ahead Log (WAL).
 *
 * <p>This command reads a WAL from either a Chronicle Queue or a Kafka topic, builds a {@link
 * WalIndex}, and prints a summary of the WAL structure including entry counts, operation/completion
 * pairing, thread listing, and any structural issues detected.
 *
 * <p>Chronicle WALs use the {@code file:} prefix (e.g., {@code file:/tmp/my-wal}). Kafka WALs are
 * specified by topic name and require either {@code --kafka-servers} ({@code -k}) or a PAL
 * directory connection ({@code -d}) from which to resolve the log's bootstrap servers.
 *
 * <p>The {@code --verbose} flag enables per-entry detail output showing offset, kind, thread name,
 * and operation signature for each WAL entry.
 *
 * <p><strong>Exit codes:</strong>
 *
 * <ul>
 *   <li>{@code 0} — analysis completed successfully
 *   <li>{@code 1} — error (invalid path, missing log, etc.)
 * </ul>
 */
@Command(
    name = "wal-index",
    customSynopsis = {
      "pal wal-index [OPTIONS] file:/path       (Chronicle Queue)",
      "pal wal-index -k <servers> [OPTIONS] <topic>  (Kafka)",
      "pal wal-index -d <url> [OPTIONS] <name>       (PalDirectory)%n"
    },
    description = "Index and analyze a WAL",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = {"URF_UNREAD_FIELD", "UWF_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR"},
    justification = "Fields set by picocli framework via annotation-driven injection")
public class WalIndexCommand extends AbstractPalSubcommand {

  /** The {@code file:} prefix used for Chronicle Queue log paths. */
  static final String CHRONICLE_FILE_PREFIX = "file:";

  /** Parent command that provides access to the main PalCommand context. */
  @ParentCommand PalCommand palCommand;

  /**
   * The log path parameter specifying the WAL to analyze. Use {@code file:/path} for Chronicle
   * Queue WALs or a plain topic name for Kafka WALs.
   */
  @Parameters(
      index = "0",
      paramLabel = "name|file:/path",
      description = "Log path: file:/path for Chronicle Queue, or topic name for Kafka")
  private String logPath;

  /**
   * Kafka bootstrap servers for direct access to a Kafka WAL topic without PalDirectory.
   *
   * <p>Required when specifying a Kafka topic name without a PalDirectory connection ({@code -d}).
   */
  @Option(
      names = {"-k", "--kafka-servers"},
      paramLabel = "bootstrap_servers",
      description = "Kafka bootstrap servers (required for Kafka WAL topics without -d)")
  private String kafkaServers;

  /** When {@code true}, prints detailed per-entry listing in addition to the summary. */
  @Option(
      names = {"-v", "--verbose"},
      description = "Show detailed entry listing")
  private boolean verbose;

  /** Displays the help message when requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Constructs a new {@code WalIndexCommand} instance. */
  WalIndexCommand() {}

  /**
   * Validates the command-line input.
   *
   * <p>Accepts Chronicle WALs (with {@code file:} prefix) unconditionally. For Kafka WAL topics
   * (plain topic names without the prefix), requires either {@code --kafka-servers} ({@code -k}) or
   * a PalDirectory connection ({@code -d}) to resolve the log's bootstrap servers.
   *
   * @throws RuntimeException if the log path is a Kafka topic without {@code -k} or {@code -d}
   */
  @Override
  protected void validateInput() {
    if (logPath.startsWith(CHRONICLE_FILE_PREFIX)) {
      return;
    }
    if (kafkaServers != null) {
      return;
    }
    if (hasPalDirectory()) {
      return;
    }
    throw new RuntimeException(
        "Kafka WAL topics require --kafka-servers (-k) or a PAL directory connection (-d). "
            + "For Chronicle Queue WALs, use the 'file:' prefix (e.g., file:/tmp/my-wal).");
  }

  /**
   * Checks whether a PalDirectory connection is available via the parent command.
   *
   * @return {@code true} if a non-{@link PalDirectory#NO_URL} directory URL is configured
   */
  private boolean hasPalDirectory() {
    if (palCommand == null) {
      return false;
    }
    String palDirUrl = palCommand.getPalDirectoryConnectionString();
    return palDirUrl != null && !PalDirectory.NO_URL.equals(palDirUrl);
  }

  /**
   * Initializes external service connections if needed.
   *
   * <p>When a PalDirectory connection is available (via {@code -d}), the directory connection
   * provider is initialized to support Kafka log name resolution.
   *
   * @throws Exception if an error occurs during initialization
   */
  @Override
  protected void initialize() throws Exception {
    if (hasPalDirectory()) {
      initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
    }
  }

  /**
   * Executes the wal-index command by reading and indexing the WAL, then printing the summary.
   *
   * <p>Routes to the appropriate backend based on the log path:
   *
   * <ul>
   *   <li>If the path starts with {@code file:}, reads from a Chronicle Queue
   *   <li>Otherwise, reads from a Kafka topic, resolving bootstrap servers via {@code -k} or
   *       PalDirectory
   * </ul>
   *
   * <p>If {@link #verbose} is enabled, also prints per-entry details.
   *
   * @return {@code 0} on success, {@code 1} on error
   * @throws Exception if an unexpected error occurs during WAL reading
   */
  @Override
  protected int runCommand() throws Exception {
    WalIndex index;

    if (logPath.startsWith(CHRONICLE_FILE_PREFIX)) {
      String filesystemPath = logPath.substring(CHRONICLE_FILE_PREFIX.length());
      Path queuePath = Paths.get(filesystemPath);

      if (!ChronicleLogUtil.queueExists(queuePath)) {
        err.println("Chronicle log does not exist at path: " + queuePath);
        return 1;
      }

      index = WalReader.readAndIndexChronicleWal(queuePath);
    } else {
      String resolvedServers = resolveKafkaBootstrapServers(logPath);
      if (resolvedServers == null) {
        return 1;
      }
      index = WalReader.readAndIndexKafkaWal(resolvedServers, logPath);
    }

    if (verbose) {
      printVerboseEntries(index);
    }
    printSummary(index);

    return 0;
  }

  /**
   * Resolves Kafka bootstrap servers for the given topic name.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>If PalDirectory is available, resolve the log by name to obtain {@link LogInfo} with
   *       bootstrap servers
   *   <li>If {@code --kafka-servers} ({@code -k}) is provided, use those servers directly
   *   <li>If neither is available, prints an error and returns {@code null}
   * </ol>
   *
   * @param topicName the Kafka topic name to resolve
   * @return the bootstrap servers string, or {@code null} if resolution fails
   */
  private String resolveKafkaBootstrapServers(String topicName) {
    if (directoryConnectionProvider != null) {
      try {
        Optional<PalDirectory> palDirOpt = directoryConnectionProvider.get();
        if (palDirOpt.isPresent()) {
          LogInfo logInfo = resolveLogInfo(palDirOpt.get(), topicName);
          if (logInfo != null && logInfo.getBootstrapServers() != null) {
            return logInfo.getBootstrapServers();
          }
        }
      } catch (RuntimeException e) {
        err.println("Error resolving log via PalDirectory: " + e.getMessage());
      }
    }

    if (kafkaServers != null) {
      return kafkaServers;
    }

    err.println(
        "Cannot resolve Kafka bootstrap servers for topic: "
            + topicName
            + ". Use --kafka-servers (-k) or ensure the log is registered in the PAL directory.");
    return null;
  }

  /**
   * Resolves a {@link LogInfo} from the PalDirectory by log name or UUID.
   *
   * <p>Follows the same resolution pattern as {@link MessageStreamPrinter}: first attempts to look
   * up the log by name, then falls back to treating the identifier as a UUID.
   *
   * @param palDirectory the PalDirectory instance for accessing log information
   * @param logIdentifier the name or UUID of the log to resolve
   * @return the resolved {@link LogInfo}, or {@code null} if no matching log is found
   */
  private LogInfo resolveLogInfo(PalDirectory palDirectory, String logIdentifier) {
    LogInfo log = null;
    try {
      log = palDirectory.getLogInfo(logIdentifier);
    } catch (RuntimeException | ExecutionException | InterruptedException e) {
      // not found by name, try UUID below
    }

    if (log == null) {
      UUID parsedUuid = null;
      try {
        parsedUuid = UUID.fromString(logIdentifier);
      } catch (IllegalArgumentException iae) {
        // not a valid UUID
      }
      if (parsedUuid != null) {
        final UUID logUuid = parsedUuid;
        try {
          Optional<LogInfo> logInfoByUuid =
              palDirectory.listAllLogs().stream()
                  .filter(l -> logUuid.equals(l.getUuid()))
                  .findFirst();
          if (logInfoByUuid.isPresent()) {
            log = logInfoByUuid.get();
          }
        } catch (RuntimeException | ExecutionException | InterruptedException e) {
          // unable to list logs
        }
      }
    }
    return log;
  }

  /**
   * Closes the directory connection provider if it was initialized.
   *
   * @throws IOException if an I/O error occurs while closing resources
   */
  @Override
  protected void closeResources() throws IOException {
    if (directoryConnectionProvider != null) {
      directoryConnectionProvider
          .get()
          .ifPresent(
              c -> {
                Optional<PalDirectory> palDirectory = directoryConnectionProvider.get();
                palDirectory.ifPresent(PalDirectory::close);
              });
    }
  }

  /**
   * Prints the WAL index summary to stdout.
   *
   * <p>Displays total entry count, operation count, completion count, pair count, entry-point
   * count, thread names, input thread names, and issue count. If structural issues exist, they are
   * printed individually.
   *
   * @param index the WAL index to summarize
   */
  void printSummary(WalIndex index) {
    List<WalEntry> entries = index.getEntries();
    long operationCount =
        entries.stream().filter(e -> e.getKind() == WalEntryKind.OPERATION).count();
    long completionCount =
        entries.stream().filter(e -> e.getKind() == WalEntryKind.COMPLETION).count();
    long entryPointCount = entries.stream().filter(WalEntry::isEntryPoint).count();
    long pairCount = index.getSpans().size();
    List<String> threads =
        entries.stream().map(WalEntry::getThreadName).distinct().sorted().toList();
    List<String> inputThreads = index.getInputThreadNames().stream().sorted().toList();
    List<String> issues = index.getStructuralIssues();

    out.println("WAL Index Summary");
    out.printf("  Entries:       %d%n", entries.size());
    out.printf("  Operations:    %d%n", operationCount);
    out.printf("  Completions:   %d%n", completionCount);
    out.printf("  Entry points:  %d%n", entryPointCount);
    out.printf("  Pairs:         %d%n", pairCount);
    out.printf("  Threads:       %s%n", threads);
    out.printf("  Input threads: %s%n", inputThreads);
    out.printf("  Issues:        %d%n", issues.size());

    if (!issues.isEmpty()) {
      out.println();
      out.println("Structural Issues:");
      for (String issue : issues) {
        out.printf("  - %s%n", issue);
      }
    }
  }

  /**
   * Prints detailed per-entry listing to stdout.
   *
   * <p>Each entry is formatted as: {@code [offset] kind threadName
   * className.executableName(paramTypes)}. Entry-point operations are annotated with a {@code
   * [ENTRY_POINT]} marker after the kind field.
   *
   * @param index the WAL index whose entries to print
   */
  void printVerboseEntries(WalIndex index) {
    for (WalEntry entry : index.getEntries()) {
      String epMarker = entry.isEntryPoint() ? " [ENTRY_POINT]" : "";
      out.printf(
          "[%d] %s%s %s %s%n",
          entry.getOffset(),
          entry.getKind(),
          epMarker,
          entry.getThreadName(),
          formatSignature(entry));
    }
    out.println();
  }

  /**
   * Formats a WAL entry's signature as {@code className.executableName(paramTypes)}.
   *
   * <p>If the executable name is null (for some completion types), only the class name is returned.
   * If parameter types are present, they are joined by commas within parentheses.
   *
   * @param entry the WAL entry to format
   * @return the formatted signature string
   */
  static String formatSignature(WalEntry entry) {
    String className = entry.getClassName() != null ? entry.getClassName() : "";
    String executableName = entry.getExecutableName();

    if (executableName == null) {
      return className;
    }

    List<String> paramTypes = entry.getParamTypes();
    if (paramTypes != null) {
      return className + "." + executableName + "(" + String.join(",", paramTypes) + ")";
    }

    return className + "." + executableName;
  }
}
