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
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalEntryKind;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.common.replay.WalReader;
import io.quasient.pal.cxn.chronicle.ChronicleLogUtil;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import picocli.CommandLine.Command;

/**
 * CLI command that indexes and analyzes a Write-Ahead Log (WAL).
 *
 * <p>This command reads a Chronicle Queue WAL, builds a {@link WalIndex}, and prints a summary of
 * the WAL structure including entry counts, operation/completion pairing, thread listing, and any
 * structural issues detected.
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
    customSynopsis = "pal wal-index [OPTIONS] <logPath>%n",
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

  /**
   * The log path parameter specifying the WAL to analyze. Must use the {@code file:} prefix for
   * Chronicle Queue WALs (e.g., {@code file:/tmp/my-wal}).
   */
  @Parameters(index = "0", description = "Log path (e.g., file:/tmp/my-wal)")
  private String logPath;

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
   * <p>Ensures that the log path starts with the {@code file:} prefix, since only Chronicle Queue
   * WALs are currently supported.
   *
   * @throws RuntimeException if the log path does not start with {@code file:}
   */
  @Override
  protected void validateInput() {
    if (!logPath.startsWith(CHRONICLE_FILE_PREFIX)) {
      throw new RuntimeException(
          "Log path must start with '"
              + CHRONICLE_FILE_PREFIX
              + "' for Chronicle Queue WALs (e.g., file:/tmp/my-wal).");
    }
  }

  /**
   * Initialization step — no-op for wal-index since no external services are required.
   *
   * @throws Exception never thrown
   */
  @Override
  protected void initialize() throws Exception {
    // No external services required.
  }

  /**
   * Executes the wal-index command by reading and indexing the WAL, then printing the summary.
   *
   * <p>Parses the log path, verifies the Chronicle queue exists, reads and indexes the WAL via
   * {@link WalReader#readAndIndexChronicleWal(Path)}, and prints the summary to stdout. If {@link
   * #verbose} is enabled, also prints per-entry details.
   *
   * @return {@code 0} on success, {@code 1} on error
   * @throws Exception if an unexpected error occurs during WAL reading
   */
  @Override
  protected int runCommand() throws Exception {
    String filesystemPath = logPath.substring(CHRONICLE_FILE_PREFIX.length());
    Path queuePath = Paths.get(filesystemPath);

    if (!ChronicleLogUtil.queueExists(queuePath)) {
      err.println("Chronicle log does not exist at path: " + queuePath);
      return 1;
    }

    WalIndex index = WalReader.readAndIndexChronicleWal(queuePath);

    if (verbose) {
      printVerboseEntries(index);
    }
    printSummary(index);

    return 0;
  }

  /**
   * Closes resources — no-op for wal-index since no directory connection is established.
   *
   * @throws IOException never thrown
   */
  @Override
  protected void closeResources() throws IOException {
    // No directory connection to close.
  }

  /**
   * Prints the WAL index summary to stdout.
   *
   * <p>Displays total entry count, operation count, completion count, pair count, thread names, and
   * issue count. If structural issues exist, they are printed individually.
   *
   * @param index the WAL index to summarize
   */
  void printSummary(WalIndex index) {
    List<WalEntry> entries = index.getEntries();
    long operationCount =
        entries.stream().filter(e -> e.getKind() == WalEntryKind.OPERATION).count();
    long completionCount =
        entries.stream().filter(e -> e.getKind() == WalEntryKind.COMPLETION).count();
    long pairCount = index.getSpans().size();
    List<String> threads =
        entries.stream().map(WalEntry::getThreadName).distinct().sorted().toList();
    List<String> issues = index.getStructuralIssues();

    out.println("WAL Index Summary");
    out.printf("  Entries:     %d%n", entries.size());
    out.printf("  Operations:  %d%n", operationCount);
    out.printf("  Completions: %d%n", completionCount);
    out.printf("  Pairs:       %d%n", pairCount);
    out.printf("  Threads:     %s%n", threads);
    out.printf("  Issues:      %d%n", issues.size());

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
   * className.executableName(paramTypes)}
   *
   * @param index the WAL index whose entries to print
   */
  void printVerboseEntries(WalIndex index) {
    for (WalEntry entry : index.getEntries()) {
      out.printf(
          "[%d] %s %s %s%n",
          entry.getOffset(), entry.getKind(), entry.getThreadName(), formatSignature(entry));
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
