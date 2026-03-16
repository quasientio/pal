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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.cli.PalCommand;
import io.quasient.pal.dsl.intercept.ApplyResult;
import io.quasient.pal.dsl.intercept.InterceptBundleSpec;
import io.quasient.pal.dsl.intercept.InterceptDiff;
import io.quasient.pal.dsl.intercept.InterceptManager;
import io.quasient.pal.dsl.intercept.InterceptSpec;
import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Applies intercepts from a YAML bundle file to the PAL directory.
 *
 * <p>This command reads a YAML bundle definition, resolves peers, and creates intercepts via {@link
 * InterceptManager#apply(InterceptBundleSpec)}. It supports a {@code --dry-run} mode that shows
 * what would be applied without making changes.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal intercept apply fraud-check.yaml
 *   pal intercept apply --dry-run fraud-check.yaml
 *   pal intercept apply -q fraud-check.yaml
 * </pre>
 *
 * @see InterceptManager
 * @see InterceptBundleSpec
 */
@Command(
    name = "apply",
    description = "Apply intercepts from a YAML bundle file",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "Fields are read by picocli framework via reflection")
public class InterceptApply extends AbstractPalSubcommand {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptApply.class);

  /** The parent command to which this subcommand belongs. */
  @ParentCommand PalCommand palCommand;

  /** The YAML bundle file to apply. */
  @Parameters(index = "0", description = "YAML bundle file", paramLabel = "FILE")
  File file;

  /** Flag indicating whether to show diff without applying. */
  @Option(
      names = {"--dry-run"},
      description = "show diff without applying")
  private boolean dryRun;

  /** Flag indicating whether to suppress detailed output. */
  @Option(
      names = {"-q", "--quiet"},
      description = "suppress detailed output")
  private boolean quiet;

  /** Flag indicating whether the help message is requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Constructs a new {@code InterceptApply} instance. */
  public InterceptApply() {}

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
   * Applies intercepts from the YAML bundle file.
   *
   * <p>If {@code --dry-run} is specified, calls {@link InterceptManager#diff} and displays what
   * would change. Otherwise, calls {@link InterceptManager#apply} and prints per-intercept results
   * and a summary line.
   *
   * @return 0 on success, 1 on error
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    try {
      if (directoryConnectionProvider == null || directoryConnectionProvider.get().isEmpty()) {
        err.println(
            "Error: pal intercept apply requires a PAL directory.\n"
                + "Specify with --directory/-d option or PAL_DIRECTORY environment variable.");
        return 1;
      }
    } catch (RuntimeException e) {
      err.println("Error: Cannot connect to PAL directory.");
      return 1;
    }

    InterceptBundleSpec bundle = InterceptDiffCommand.parseYamlFile(file, err);
    if (bundle == null) {
      return 1;
    }

    InterceptManager manager = new InterceptManager(getPalDirectory());

    if (dryRun) {
      return executeDryRun(manager, bundle);
    }
    return executeApply(manager, bundle);
  }

  /**
   * Executes a dry-run, showing what would change without applying.
   *
   * @param manager the intercept manager
   * @param bundle the parsed bundle spec
   * @return 0 on success, 1 on error
   */
  private int executeDryRun(InterceptManager manager, InterceptBundleSpec bundle) {
    try {
      List<InterceptDiff> diffs = manager.diff(bundle);
      InterceptDiffCommand.printDiffResults(out, bundle.getBundleName(), diffs);
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
   * Executes the apply operation.
   *
   * @param manager the intercept manager
   * @param bundle the parsed bundle spec
   * @return 0 on success, 1 on error
   */
  private int executeApply(InterceptManager manager, InterceptBundleSpec bundle) {
    try {
      ApplyResult result = manager.apply(bundle);
      if (!quiet) {
        out.printf(
            "Applying bundle \"%s\" (%d intercepts)...%n",
            bundle.getBundleName(), bundle.getIntercepts().size());
        for (ApplyResult.Entry entry : result.getEntries()) {
          InterceptSpec spec = entry.getInterceptSpec();
          String target = spec.getTargetClass() + "." + spec.getTargetName();
          switch (entry.getStatus()) {
            case CREATED -> out.printf("  + %s %s -> created%n", spec.getType(), target);
            case SKIPPED ->
                out.printf("  - %s %s -> already exists (skipped)%n", spec.getType(), target);
            case FAILED ->
                out.printf(
                    "  ! %s %s -> FAILED: %s%n", spec.getType(), target, entry.getErrorMessage());
          }
        }
      }
      out.printf(
          "Applied: %d created, %d skipped, %d failed%n",
          result.getCreatedCount(), result.getSkippedCount(), result.getFailedCount());
      return 0;
    } catch (IllegalArgumentException e) {
      err.printf("Error: %s%n", e.getMessage());
      return 1;
    } catch (Exception e) {
      err.printf("Error applying bundle: %s%n", e.getMessage());
      logger.error("Apply failed for bundle \"{}\"", bundle.getBundleName(), e);
      return 1;
    }
  }
}
