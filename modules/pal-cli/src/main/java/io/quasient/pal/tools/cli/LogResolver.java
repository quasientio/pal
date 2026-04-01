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

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.LogInfo.LogType;
import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a log name or path into a {@link LogInfo} object.
 *
 * <p>This shared utility consolidates the duplicated {@code resolveLogInfo} logic previously found
 * in {@code Caller}, {@code Remove}, and {@code MessageStreamPrinter}. The resolution strategy
 * follows this order:
 *
 * <ol>
 *   <li>If the log name starts with {@code file:}, return a Chronicle-backed {@link LogInfo}.
 *   <li>If a PAL directory is available, look up the log by name in the directory.
 *   <li>If Kafka servers are configured, return a Kafka-backed {@link LogInfo}.
 *   <li>Otherwise, return {@code null}.
 * </ol>
 */
public class LogResolver {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(LogResolver.class);

  /** The prefix that identifies Chronicle Queue log paths. */
  private static final String CHRONICLE_FILE_PREFIX = "file:";

  /** Provider for obtaining a connection to the PAL directory (etcd). May be null. */
  @Nullable private final DirectoryConnectionProvider directoryConnectionProvider;

  /** Kafka bootstrap servers string. May be null. */
  @Nullable private final String kafkaServers;

  /**
   * Base directory for resolving relative Chronicle log paths. When set (typically via {@code
   * PAL_CHRONICLE_BASE_DIR}), relative paths are checked here first, then against the current
   * working directory. May be null, in which case only the CWD is used.
   */
  @Nullable private final Path chronicleBaseDir;

  /**
   * Constructs a new {@code LogResolver} without a Chronicle base directory.
   *
   * <p>Relative Chronicle paths will be resolved against the current working directory only.
   *
   * @param directoryConnectionProvider provider for the PAL directory connection, or {@code null}
   *     if no directory is available
   * @param kafkaServers Kafka bootstrap servers string, or {@code null} if Kafka is not available
   */
  public LogResolver(
      @Nullable DirectoryConnectionProvider directoryConnectionProvider,
      @Nullable String kafkaServers) {
    this(directoryConnectionProvider, kafkaServers, null);
  }

  /**
   * Constructs a new {@code LogResolver} with an optional Chronicle base directory.
   *
   * <p>When {@code chronicleBaseDir} is non-null, relative Chronicle paths (e.g., {@code
   * file:mylog}) are resolved by checking {@code chronicleBaseDir/relativePath} first; if that
   * location does not exist, the current working directory is checked as a fallback. This mirrors
   * the resolution strategy used by {@code pal run --chronicle-base-dir}.
   *
   * @param directoryConnectionProvider provider for the PAL directory connection, or {@code null}
   *     if no directory is available
   * @param kafkaServers Kafka bootstrap servers string, or {@code null} if Kafka is not available
   * @param chronicleBaseDir base directory for relative Chronicle paths, or {@code null} to resolve
   *     against CWD only
   */
  public LogResolver(
      @Nullable DirectoryConnectionProvider directoryConnectionProvider,
      @Nullable String kafkaServers,
      @Nullable Path chronicleBaseDir) {
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.kafkaServers = kafkaServers;
    this.chronicleBaseDir = chronicleBaseDir;
  }

  /**
   * Resolves a log name or path into a {@link LogInfo}.
   *
   * <p>The resolution strategy is:
   *
   * <ol>
   *   <li>If {@code logNameOrPath} starts with {@code file:}, strip the prefix and return a
   *       Chronicle-backed {@link LogInfo} immediately (no directory query).
   *   <li>If a {@link DirectoryConnectionProvider} is available and returns a connected {@link
   *       PalDirectory}, look up the log by name. If found, return the directory's {@link LogInfo}.
   *   <li>If Kafka servers are configured (either via constructor or absent from directory), create
   *       and return a Kafka-backed {@link LogInfo}.
   *   <li>If none of the above resolves the log, return {@code null}.
   * </ol>
   *
   * @param logNameOrPath the log name, topic name, or {@code file:}-prefixed Chronicle path to
   *     resolve
   * @return the resolved {@link LogInfo}, or {@code null} if the log cannot be resolved
   * @throws IllegalArgumentException if {@code logNameOrPath} is {@code null}
   */
  @Nullable
  public LogInfo resolveLogInfo(String logNameOrPath) {
    if (logNameOrPath == null) {
      throw new IllegalArgumentException("logNameOrPath must not be null");
    }

    // (1) file: prefix → Chronicle log
    if (logNameOrPath.startsWith(CHRONICLE_FILE_PREFIX)) {
      String pathStr = logNameOrPath.substring(CHRONICLE_FILE_PREFIX.length());
      Path path = Paths.get(pathStr);
      if (!path.isAbsolute()) {
        path = resolveChronicleRelativePath(path, chronicleBaseDir);
      }
      String resolvedPath = path.toString();
      LogInfo logInfo = new LogInfo(resolvedPath);
      logInfo.setLogType(LogType.CHRONICLE);
      logger.debug("Resolved Chronicle log from file: prefix: {}", resolvedPath);
      return logInfo;
    }

    // (2) Directory lookup
    if (directoryConnectionProvider != null) {
      try {
        var palDirOpt = directoryConnectionProvider.get();
        if (palDirOpt.isPresent()) {
          LogInfo logInfo = palDirOpt.get().getLogInfo(logNameOrPath);
          if (logInfo != null) {
            logger.debug("Resolved log '{}' from PAL directory", logNameOrPath);
            return logInfo;
          }
        }
      } catch (RuntimeException | ExecutionException | InterruptedException e) {
        logger.debug("PAL directory not available: {}", e.getMessage());
      }
    }

    // (3) Kafka fallback
    if (kafkaServers != null) {
      LogInfo logInfo = new LogInfo(logNameOrPath, kafkaServers);
      logInfo.setLogType(LogType.KAFKA);
      logger.debug(
          "Resolved Kafka log in direct mode: topic={}, servers={}", logNameOrPath, kafkaServers);
      return logInfo;
    }

    // (4) Cannot resolve
    return null;
  }

  /**
   * Resolves a relative Chronicle queue path, checking both the Chronicle base directory (if
   * configured) and the current working directory.
   *
   * <p>This method implements the dual-location resolution strategy used by CLI commands that query
   * existing logs. Since these commands only read or remove logs that were previously created by
   * {@code pal run}, the log may reside under the configured base directory or the CWD.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>If {@code chronicleBaseDir} is non-null and the path exists at {@code
   *       baseDir/relativePath}, use it.
   *   <li>If the path exists at {@code CWD/relativePath}, use it.
   *   <li>If neither exists: prefer the base directory path (if set), otherwise fall back to CWD.
   * </ol>
   *
   * @param relativePath the relative path to resolve (must not be absolute)
   * @param chronicleBaseDir the Chronicle base directory, or {@code null} if not configured
   * @return the resolved absolute path
   */
  static Path resolveChronicleRelativePath(Path relativePath, @Nullable Path chronicleBaseDir) {
    if (chronicleBaseDir != null) {
      Path baseDirPath = chronicleBaseDir.resolve(relativePath).normalize();
      if (Files.exists(baseDirPath)) {
        logger.debug(
            "Resolved relative Chronicle path '{}' against base dir: {}",
            relativePath,
            baseDirPath);
        return baseDirPath;
      }
    }

    Path cwdPath = relativePath.toAbsolutePath().normalize();
    if (Files.exists(cwdPath)) {
      logger.debug("Resolved relative Chronicle path '{}' against CWD: {}", relativePath, cwdPath);
      return cwdPath;
    }

    // Neither location exists — prefer base dir if configured
    if (chronicleBaseDir != null) {
      Path baseDirPath = chronicleBaseDir.resolve(relativePath).normalize();
      logger.debug(
          "Chronicle log not found at base dir ({}) or CWD ({}); defaulting to base dir path",
          baseDirPath,
          cwdPath);
      return baseDirPath;
    }
    return cwdPath;
  }
}
