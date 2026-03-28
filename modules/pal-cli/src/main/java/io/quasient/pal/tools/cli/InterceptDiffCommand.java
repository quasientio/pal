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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.dsl.intercept.InterceptBundleSpec;
import io.quasient.pal.dsl.intercept.InterceptDiff;
import io.quasient.pal.dsl.intercept.InterceptManager;
import io.quasient.pal.dsl.intercept.InterceptSpec;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Compares a YAML bundle file against the current directory state.
 *
 * <p>This command reads a YAML bundle definition, resolves peers, and calls {@link
 * InterceptManager#diff(InterceptBundleSpec)} to compare each intercept spec against the current
 * directory. It displays each entry with a marker: {@code +} (create), {@code =} (unchanged),
 * {@code ~} (modified), and prints a summary line.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal intercept diff fraud-check.yaml
 * </pre>
 *
 * @see InterceptManager
 * @see InterceptDiff
 */
@Command(
    name = "diff",
    description = "Compare YAML bundle against current directory state",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "Fields are read by picocli framework via reflection")
public class InterceptDiffCommand extends AbstractPalSubcommand {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptDiffCommand.class);

  /** The parent command to which this subcommand belongs. */
  @ParentCommand PalCommand palCommand;

  /** The YAML bundle file to compare. */
  @Parameters(index = "0", description = "YAML bundle file", paramLabel = "FILE")
  File file;

  /** Flag indicating whether the help message is requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Constructs a new {@code InterceptDiffCommand} instance. */
  public InterceptDiffCommand() {}

  /** No validation needed for this command. */
  @Override
  public void validateInput() {}

  /**
   * Initializes the subcommand by setting up the directory connection.
   *
   * @throws Exception if initialization fails
   */
  @Override
  protected void initialize() throws Exception {
    initializeDirectoryConnectionProvider(palCommand.getPalDirectoryConnectionString());
  }

  /**
   * Compares the YAML bundle against the current directory state.
   *
   * <p>Parses the YAML file, calls {@link InterceptManager#diff}, and prints each entry with a
   * marker: {@code +} for create, {@code =} for unchanged, {@code ~} for modified. Prints a summary
   * line with counts.
   *
   * @return 0 on success, 1 on error
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    try {
      if (directoryConnectionProvider == null || directoryConnectionProvider.get().isEmpty()) {
        err.println(
            "Error: pal intercept diff requires a PAL directory.\n"
                + "Specify with --directory/-d option or PAL_DIRECTORY environment variable.");
        return 1;
      }
    } catch (RuntimeException e) {
      err.println("Error: Cannot connect to PAL directory.");
      return 1;
    }

    InterceptBundleSpec bundle = parseYamlFile(file, err);
    if (bundle == null) {
      return 1;
    }

    InterceptManager manager = new InterceptManager(getPalDirectory());

    try {
      List<InterceptDiff> diffs = manager.diff(bundle);
      printDiffResults(out, bundle.getBundleName(), diffs);
      return 0;
    } catch (IllegalArgumentException e) {
      err.printf("Error: %s%n", e.getMessage());
      return 1;
    } catch (Exception e) {
      err.printf("Error during diff: %s%n", e.getMessage());
      logger.error("Diff failed for bundle \"{}\"", bundle.getBundleName(), e);
      return 1;
    }
  }

  /**
   * Parses a YAML bundle file, printing error messages to the given error stream on failure.
   *
   * <p>This method is shared with {@link InterceptApply} to avoid code duplication in YAML file
   * parsing and error handling.
   *
   * @param yamlFile the YAML file to parse
   * @param errStream the error stream for printing error messages
   * @return the parsed bundle spec, or {@code null} if parsing failed
   */
  static InterceptBundleSpec parseYamlFile(File yamlFile, PrintStream errStream) {
    try {
      return InterceptBundleSpec.fromYamlFile(yamlFile.toPath());
    } catch (NoSuchFileException | InvalidPathException e) {
      errStream.printf("Error: File not found: %s%n", yamlFile);
      return null;
    } catch (IOException e) {
      errStream.printf("Error: Cannot read file %s: %s%n", yamlFile, e.getMessage());
      return null;
    } catch (Exception e) {
      errStream.printf("Error: Invalid YAML: %s%n", e.getMessage());
      return null;
    }
  }

  /**
   * Renders diff results to the given output stream.
   *
   * <p>Prints a header line, per-entry markers ({@code +} create, {@code =} unchanged, {@code ~}
   * modified), and a summary line with counts. This method is shared with {@link InterceptApply}
   * for the {@code --dry-run} mode.
   *
   * @param output the stream to print to
   * @param bundleName the bundle name for the header line
   * @param diffs the diff entries to render
   */
  static void printDiffResults(PrintStream output, String bundleName, List<InterceptDiff> diffs) {
    output.printf("Comparing bundle \"%s\" against directory...%n", bundleName);
    long createCount = 0;
    long unchangedCount = 0;
    long modifiedCount = 0;
    for (InterceptDiff diff : diffs) {
      InterceptSpec spec = diff.getInterceptSpec();
      String target = spec.getTargetClass() + "." + spec.getTargetName();
      switch (diff.getDiffType()) {
        case CREATE -> {
          output.printf("  + %s %s   (would be created)%n", spec.getType(), target);
          createCount++;
        }
        case UNCHANGED -> {
          output.printf("  = %s %s   (already exists, matches)%n", spec.getType(), target);
          unchangedCount++;
        }
        case MODIFIED -> {
          output.printf(
              "  ~ %s %s   (exists, but differs: %s)%n", spec.getType(), target, diff.getDetails());
          modifiedCount++;
        }
        default -> {}
      }
    }
    output.printf(
        "%nSummary: %d to create, %d unchanged, %d to update%n",
        createCount, unchangedCount, modifiedCount);
  }
}
