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
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.dsl.intercept.InterceptBundleSpec;
import io.quasient.pal.dsl.intercept.InterceptManager;
import io.quasient.pal.dsl.intercept.RemoveResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Removes intercepts from the PAL directory.
 *
 * <p>This command supports multiple removal modes:
 *
 * <ul>
 *   <li>By UUID positional arguments: removes individual intercepts by their UUIDs
 *   <li>By YAML file ({@code -f}): removes all intercepts defined in the bundle
 *   <li>By bundle name ({@code --bundle}): removes all intercepts tracked in bundle metadata
 *   <li>By peer ({@code --peer}): removes all intercepts for the specified peer
 * </ul>
 *
 * <p>At most one removal mode may be specified. If none are specified, the command prints a usage
 * hint and returns exit code 1.
 *
 * <p>Examples:
 *
 * <pre>
 *   pal intercept rm abc12345-...
 *   pal intercept rm -f fraud-check.yaml
 *   pal intercept rm --bundle fraud-check-v1
 *   pal intercept rm --peer fraud-checker
 * </pre>
 *
 * @see InterceptManager
 */
@Command(
    name = "rm",
    description = "Remove intercepts",
    separator = " ",
    sortOptions = false,
    optionListHeading = "%nOptions:%n")
@SuppressFBWarnings(
    value = "URF_UNREAD_FIELD",
    justification = "Fields are read by picocli framework via reflection")
public class InterceptRemove extends AbstractPalSubcommand {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptRemove.class);

  /** The parent command to which this subcommand belongs. */
  @ParentCommand PalCommand palCommand;

  /** Optional UUID positional arguments for removing individual intercepts. */
  @Parameters(arity = "0..*", description = "Intercept UUID(s) to remove", paramLabel = "UUID")
  List<String> uuids;

  /** Optional YAML file for removing all intercepts defined in the bundle. */
  @Option(
      names = {"-f", "--file"},
      description = "YAML bundle file",
      paramLabel = "FILE")
  File file;

  /** Optional bundle name for removing all intercepts tracked in bundle metadata. */
  @Option(
      names = {"--bundle"},
      description = "bundle name",
      paramLabel = "NAME")
  String bundleName;

  /** Optional peer name or UUID for removing all intercepts for the specified peer. */
  @Option(
      names = {"--peer"},
      description = "peer name or UUID",
      paramLabel = "PEER")
  String peerNameOrUuid;

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

  /** Constructs a new {@code InterceptRemove} instance. */
  public InterceptRemove() {}

  /** No validation needed; mutual exclusion is checked in runCommand. */
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
   * Removes intercepts based on the provided options.
   *
   * @return 0 on success, 1 on error
   * @throws Exception if an error occurs during command execution
   */
  @Override
  protected int runCommand() throws Exception {
    try {
      if (directoryConnectionProvider == null || directoryConnectionProvider.get().isEmpty()) {
        err.println(
            "Error: pal intercept rm requires a PAL directory.\n"
                + "Specify with --directory/-d option or PAL_DIRECTORY environment variable.");
        return 1;
      }
    } catch (RuntimeException e) {
      err.println("Error: Cannot connect to PAL directory.");
      return 1;
    }

    boolean hasUuids = uuids != null && !uuids.isEmpty();
    boolean hasFile = file != null;
    boolean hasBundle = bundleName != null && !bundleName.isEmpty();
    boolean hasPeer = peerNameOrUuid != null && !peerNameOrUuid.isEmpty();

    int modeCount =
        (hasUuids ? 1 : 0) + (hasFile ? 1 : 0) + (hasBundle ? 1 : 0) + (hasPeer ? 1 : 0);

    if (modeCount == 0) {
      err.println(
          "Error: Specify at least one of: UUID argument(s), -f/--file, --bundle, or --peer.");
      return 1;
    }
    if (modeCount > 1) {
      err.println(
          "Error: Options are mutually exclusive."
              + " Specify only one of: UUID argument(s), -f/--file, --bundle, or --peer.");
      return 1;
    }

    if (hasFile) {
      return removeByFile();
    }
    if (hasBundle) {
      return removeByBundle();
    }
    if (hasPeer) {
      return removeByPeer();
    }
    return removeByUuids();
  }

  /**
   * Removes all intercepts defined in the YAML bundle file.
   *
   * @return 0 on success, 1 on error
   */
  private int removeByFile() {
    try {
      InterceptBundleSpec bundle = InterceptBundleSpec.fromYamlFile(file.toPath());
      InterceptManager manager = new InterceptManager(getPalDirectory());
      RemoveResult result = manager.remove(bundle);
      printRemoveResult(result);
      return 0;
    } catch (NoSuchFileException | InvalidPathException e) {
      err.printf("Error: File not found: %s%n", file);
      return 1;
    } catch (IOException e) {
      err.printf("Error: Cannot read file %s: %s%n", file, e.getMessage());
      return 1;
    } catch (IllegalArgumentException e) {
      err.printf("Error: %s%n", e.getMessage());
      return 1;
    } catch (Exception e) {
      err.printf("Error removing by file: %s%n", e.getMessage());
      logger.error("Remove by file failed", e);
      return 1;
    }
  }

  /**
   * Removes all intercepts tracked in the named bundle metadata.
   *
   * @return 0 on success, 1 on error
   */
  private int removeByBundle() {
    try {
      InterceptManager manager = new InterceptManager(getPalDirectory());
      RemoveResult result = manager.removeByBundle(bundleName);
      printRemoveResult(result);
      return 0;
    } catch (IllegalArgumentException e) {
      err.printf("Error: %s%n", e.getMessage());
      return 1;
    } catch (Exception e) {
      err.printf("Error removing bundle \"%s\": %s%n", bundleName, e.getMessage());
      logger.error("Remove by bundle \"{}\" failed", bundleName, e);
      return 1;
    }
  }

  /**
   * Removes all intercepts for the specified peer.
   *
   * @return 0 on success, 1 on error
   */
  @SuppressFBWarnings(
      value = "REC_CATCH_EXCEPTION",
      justification = "Catching all exceptions to provide user-friendly error messages in CLI")
  private int removeByPeer() {
    try {
      PalDirectory dir = getPalDirectory();
      PeerInfo peerInfo = dir.getPeerByName(peerNameOrUuid);
      if (peerInfo == null) {
        err.printf("Error: Peer not found: \"%s\"%n", peerNameOrUuid);
        return 1;
      }
      dir.deleteInterceptsForPeer(peerInfo.getUuid());
      if (!quiet) {
        out.printf(
            "Removed all intercepts for peer \"%s\" (%s)%n", peerNameOrUuid, peerInfo.getUuid());
      }
      return 0;
    } catch (Exception e) {
      err.printf("Error removing intercepts for peer \"%s\": %s%n", peerNameOrUuid, e.getMessage());
      logger.error("Remove by peer \"{}\" failed", peerNameOrUuid, e);
      return 1;
    }
  }

  /**
   * Removes intercepts by individual UUID positional arguments.
   *
   * @return 0 on success, 1 on error
   */
  private int removeByUuids() {
    PalDirectory dir = getPalDirectory();
    int removed = 0;
    int errors = 0;

    // Build a lookup of intercept UUID -> peer UUID from all registered intercepts
    Map<UUID, UUID> interceptToPeer = new HashMap<>();
    try {
      for (InterceptRequest<?> req : dir.listAllIntercepts()) {
        interceptToPeer.put(req.getUuid(), req.getPeer());
      }
    } catch (Exception e) {
      err.printf("Error listing intercepts: %s%n", e.getMessage());
      return 1;
    }

    for (String uuidStr : uuids) {
      try {
        UUID interceptUuid = UUID.fromString(uuidStr);
        UUID peerUuid = interceptToPeer.get(interceptUuid);
        if (peerUuid == null) {
          err.printf("Error: Intercept not found: %s%n", uuidStr);
          errors++;
          continue;
        }
        dir.deleteIntercept(peerUuid, interceptUuid);
        removed++;
        if (!quiet) {
          out.printf("Removed intercept %s%n", interceptUuid);
        }
      } catch (IllegalArgumentException e) {
        err.printf("Error: Invalid UUID: %s%n", uuidStr);
        errors++;
      } catch (Exception e) {
        err.printf("Error removing intercept %s: %s%n", uuidStr, e.getMessage());
        logger.error("Failed to remove intercept {}", uuidStr, e);
        errors++;
      }
    }

    out.printf("Removed: %d, errors: %d%n", removed, errors);
    return errors > 0 ? 1 : 0;
  }

  /**
   * Prints the remove result summary.
   *
   * @param result the remove result to print
   */
  private void printRemoveResult(RemoveResult result) {
    if (!quiet) {
      for (RemoveResult.Entry entry : result.getEntries()) {
        switch (entry.getStatus()) {
          case REMOVED -> out.printf("  - %s -> removed%n", entry.getUuid());
          case NOT_FOUND -> out.printf("  - %s -> not found%n", entry.getUuid());
        }
      }
    }
    out.printf("Removed: %d, not found: %d%n", result.getRemovedCount(), result.getNotFoundCount());
  }
}
