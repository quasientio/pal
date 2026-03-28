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
import io.quasient.pal.dsl.intercept.BundleStatus;
import io.quasient.pal.dsl.intercept.InterceptBundleSpec;
import io.quasient.pal.dsl.intercept.InterceptManager;
import io.quasient.pal.dsl.intercept.InterceptSpec;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Checks the status of an intercept bundle in the directory.
 *
 * <p>This command supports two modes of operation:
 *
 * <ul>
 *   <li>{@code -f/--file}: Reads a YAML bundle file and checks the status of each intercept via
 *       {@link InterceptManager#status(InterceptBundleSpec)}.
 *   <li>{@code --bundle}: Reads bundle metadata from the directory and checks status via {@link
 *       InterceptManager#statusByBundle(String)}.
 * </ul>
 *
 * <p>For each intercept, displays whether it is active or not found, and prints a summary line
 * showing "N/M active".
 *
 * <p>Examples:
 *
 * <pre>
 *   pal intercept status -f fraud-check.yaml
 *   pal intercept status --bundle fraud-check-v1
 * </pre>
 *
 * @see InterceptManager
 * @see BundleStatus
 */
@Command(
    name = "status",
    description = "Check status of intercept bundle",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "Fields are read by picocli framework via reflection")
public class InterceptStatusCommand extends AbstractPalSubcommand {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptStatusCommand.class);

  /** The parent command to which this subcommand belongs. */
  @ParentCommand PalCommand palCommand;

  /** Optional YAML file for checking status of intercepts in the bundle. */
  @Option(
      names = {"-f", "--file"},
      description = "YAML bundle file",
      paramLabel = "FILE")
  File file;

  /** Optional bundle name for checking status via stored metadata. */
  @Option(
      names = {"--bundle"},
      description = "bundle name",
      paramLabel = "NAME")
  String bundleName;

  /** Flag indicating whether the help message is requested. */
  @SuppressWarnings("unused")
  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "display this help message")
  private boolean helpRequested = false;

  /** Constructs a new {@code InterceptStatusCommand} instance. */
  public InterceptStatusCommand() {}

  /** No validation needed; mode checking is done in runCommand. */
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
   * Checks the status of an intercept bundle.
   *
   * <p>If {@code -f/--file} is specified, parses the YAML and calls {@link
   * InterceptManager#status}. If {@code --bundle} is specified, calls {@link
   * InterceptManager#statusByBundle}. Prints per-intercept status and a summary line.
   *
   * @return 0 on success, 1 on error
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    try {
      if (directoryConnectionProvider == null || directoryConnectionProvider.get().isEmpty()) {
        err.println(
            "Error: pal intercept status requires a PAL directory.\n"
                + "Specify with --directory/-d option or PAL_DIRECTORY environment variable.");
        return 1;
      }
    } catch (RuntimeException e) {
      err.println("Error: Cannot connect to PAL directory.");
      return 1;
    }

    boolean hasFile = file != null;
    boolean hasBundle = bundleName != null && !bundleName.isEmpty();

    if (!hasFile && !hasBundle) {
      err.println("Error: Specify either -f/--file or --bundle.");
      return 1;
    }

    if (hasFile) {
      return statusByFile();
    }
    return statusByBundle();
  }

  /**
   * Checks bundle status using a YAML file.
   *
   * @return 0 on success, 1 on error
   */
  private int statusByFile() {
    InterceptBundleSpec bundle;
    try {
      bundle = InterceptBundleSpec.fromYamlFile(file.toPath());
    } catch (NoSuchFileException | InvalidPathException e) {
      err.printf("Error: File not found: %s%n", file);
      return 1;
    } catch (IOException e) {
      err.printf("Error: Cannot read file %s: %s%n", file, e.getMessage());
      return 1;
    } catch (Exception e) {
      err.printf("Error: Invalid YAML: %s%n", e.getMessage());
      return 1;
    }

    try {
      InterceptManager manager = new InterceptManager(getPalDirectory());
      BundleStatus status = manager.status(bundle);
      printBundleStatus(status);
      return 0;
    } catch (IllegalArgumentException e) {
      err.printf("Error: %s%n", e.getMessage());
      return 1;
    } catch (Exception e) {
      err.printf("Error checking status: %s%n", e.getMessage());
      logger.error("Status check failed for file {}", file, e);
      return 1;
    }
  }

  /**
   * Checks bundle status using stored bundle metadata.
   *
   * @return 0 on success, 1 on error
   */
  private int statusByBundle() {
    try {
      InterceptManager manager = new InterceptManager(getPalDirectory());
      BundleStatus status = manager.statusByBundle(bundleName);
      printBundleStatus(status);
      return 0;
    } catch (IllegalArgumentException e) {
      err.printf("Error: %s%n", e.getMessage());
      return 1;
    } catch (Exception e) {
      err.printf("Error checking bundle \"%s\": %s%n", bundleName, e.getMessage());
      logger.error("Status check failed for bundle \"{}\"", bundleName, e);
      return 1;
    }
  }

  /**
   * Prints the bundle status with per-intercept entries and a summary line.
   *
   * @param status the bundle status to print
   */
  private void printBundleStatus(BundleStatus status) {
    out.printf("Bundle \"%s\"", status.getBundleName());
    if (status.getPeerName() != null || status.getPeerUuid() != null) {
      out.printf(
          " (peer: %s / %s)",
          status.getPeerName() != null ? status.getPeerName() : "unknown",
          status.getPeerUuid() != null ? status.getPeerUuid() : "unknown");
    }
    out.println();

    for (BundleStatus.InterceptStatusEntry entry : status.getEntries()) {
      InterceptSpec spec = entry.getSpec();
      String target = spec.getTargetClass() + "." + spec.getTargetName();
      if (entry.isActive()) {
        out.printf("  + %s %s   registered%n", spec.getType(), target);
      } else {
        out.printf("  - %s %s   not found%n", spec.getType(), target);
      }
    }

    out.printf("%nStatus: %d/%d active%n", status.getActiveCount(), status.getTotalCount());
  }
}
