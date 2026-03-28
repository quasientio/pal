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
   * Constructs a new {@code LogResolver}.
   *
   * @param directoryConnectionProvider provider for the PAL directory connection, or {@code null}
   *     if no directory is available
   * @param kafkaServers Kafka bootstrap servers string, or {@code null} if Kafka is not available
   */
  public LogResolver(
      @Nullable DirectoryConnectionProvider directoryConnectionProvider,
      @Nullable String kafkaServers) {
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.kafkaServers = kafkaServers;
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
      String path = logNameOrPath.substring(CHRONICLE_FILE_PREFIX.length());
      LogInfo logInfo = new LogInfo(path);
      logInfo.setLogType(LogType.CHRONICLE);
      logger.debug("Resolved Chronicle log from file: prefix: {}", path);
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
}
