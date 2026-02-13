/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper around a peer {@link Process} that provides access to the peer's log file.
 *
 * <p>This class enables integration tests to:
 *
 * <ul>
 *   <li>Check if specific log lines have been written by the peer
 *   <li>Wait for specific log lines with a timeout (useful for async callbacks)
 *   <li>Access the underlying process for lifecycle management
 * </ul>
 *
 * <p><b>Usage in tests:</b>
 *
 * <pre>{@code
 * // For sync callbacks - immediate check
 * assertTrue(interceptorPeer.containsLogLine("logArgs.*args.*hello"));
 *
 * // For async callbacks - wait with timeout
 * assertTrue(interceptorPeer.waitForLogLine("logReturnValue.*returnValue.*42", 5000));
 * }</pre>
 */
public class PeerProcess {

  private static final Logger logger = LoggerFactory.getLogger(PeerProcess.class);

  /** Default timeout for waiting on log lines (5 seconds). */
  public static final long DEFAULT_LOG_WAIT_TIMEOUT_MS = 5000;

  /** Poll interval when waiting for log lines. */
  private static final long POLL_INTERVAL_MS = 100;

  /** The underlying process. */
  private final Process process;

  /** Path to the peer's log file. */
  private final Path logFilePath;

  /** The peer's name (for logging purposes). */
  private final String peerName;

  /** The peer's UUID, used for directory cleanup. */
  private final UUID peerId;

  /**
   * Creates a new PeerProcess wrapper.
   *
   * @param process the underlying process
   * @param logFilePath path to the peer's log file
   * @param peerName the peer's name (for logging)
   * @param peerId the peer's UUID
   */
  public PeerProcess(Process process, Path logFilePath, String peerName, UUID peerId) {
    this.process = process;
    this.logFilePath = logFilePath;
    this.peerName = peerName;
    this.peerId = peerId;
  }

  /**
   * Returns the underlying process.
   *
   * @return the process
   */
  public Process getProcess() {
    return process;
  }

  /**
   * Returns the path to the peer's log file.
   *
   * @return the log file path
   */
  public Path getLogFilePath() {
    return logFilePath;
  }

  /**
   * Returns the peer's name.
   *
   * @return the peer name
   */
  public String getPeerName() {
    return peerName;
  }

  /**
   * Returns the peer's UUID.
   *
   * @return the peer UUID
   */
  public UUID getPeerId() {
    return peerId;
  }

  /**
   * Checks if the peer is still running.
   *
   * @return true if the process is alive
   */
  public boolean isAlive() {
    return process != null && process.isAlive();
  }

  /**
   * Destroys the peer process.
   *
   * @see Process#destroy()
   */
  public void destroy() {
    if (process != null) {
      process.destroy();
    }
  }

  /**
   * Forcibly destroys the peer process.
   *
   * @see Process#destroyForcibly()
   */
  public void destroyForcibly() {
    if (process != null) {
      process.destroyForcibly();
    }
  }

  /**
   * Waits for the peer process to terminate.
   *
   * @param timeout the maximum time to wait
   * @param unit the time unit
   * @return true if the process has exited, false if timeout elapsed
   * @throws InterruptedException if interrupted while waiting
   * @see Process#waitFor(long, TimeUnit)
   */
  public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
    return process != null && process.waitFor(timeout, unit);
  }

  /**
   * Returns the exit value of the process.
   *
   * @return the exit value
   * @see Process#exitValue()
   */
  public int exitValue() {
    return process != null ? process.exitValue() : 0;
  }

  /**
   * Checks if the log file contains a line matching the given regex pattern.
   *
   * <p>This is a non-blocking check that reads the current log file contents.
   *
   * @param regex the regex pattern to search for
   * @return true if a matching line is found
   */
  public boolean containsLogLine(String regex) {
    if (!Files.exists(logFilePath)) {
      logger.debug("[{}] Log file does not exist yet: {}", peerName, logFilePath);
      return false;
    }

    Pattern pattern = Pattern.compile(regex);

    try {
      List<String> lines = Files.readAllLines(logFilePath, UTF_8);
      for (String line : lines) {
        if (pattern.matcher(line).find()) {
          logger.debug("[{}] Found matching log line: {}", peerName, line);
          return true;
        }
      }
    } catch (IOException e) {
      logger.warn("[{}] Error reading log file: {}", peerName, logFilePath, e);
    }

    return false;
  }

  /**
   * Waits for a log line matching the given regex pattern, with the default timeout.
   *
   * <p>This method polls the log file until a matching line is found or the timeout expires. Useful
   * for verifying async callback behavior where the log line may not be written immediately.
   *
   * @param regex the regex pattern to search for
   * @return true if a matching line is found within the timeout, false otherwise
   * @see #waitForLogLine(String, long)
   */
  public boolean waitForLogLine(String regex) {
    return waitForLogLine(regex, DEFAULT_LOG_WAIT_TIMEOUT_MS);
  }

  /**
   * Waits for a log line matching the given regex pattern, with a custom timeout.
   *
   * <p>This method polls the log file until a matching line is found or the timeout expires. Useful
   * for verifying async callback behavior where the log line may not be written immediately.
   *
   * @param regex the regex pattern to search for
   * @param timeoutMs maximum time to wait in milliseconds
   * @return true if a matching line is found within the timeout, false otherwise
   */
  public boolean waitForLogLine(String regex, long timeoutMs) {
    logger.debug(
        "[{}] Waiting for log line matching '{}' (timeout: {}ms)", peerName, regex, timeoutMs);

    long startTime = System.currentTimeMillis();
    long deadline = startTime + timeoutMs;

    Pattern pattern = Pattern.compile(regex);

    while (System.currentTimeMillis() < deadline) {
      if (Files.exists(logFilePath)) {
        try {
          List<String> lines = Files.readAllLines(logFilePath, UTF_8);
          for (String line : lines) {
            if (pattern.matcher(line).find()) {
              long elapsed = System.currentTimeMillis() - startTime;
              logger.debug("[{}] Found matching log line after {}ms: {}", peerName, elapsed, line);
              return true;
            }
          }
        } catch (IOException e) {
          logger.warn("[{}] Error reading log file: {}", peerName, logFilePath, e);
        }
      }

      // Sleep before next poll
      try {
        Thread.sleep(POLL_INTERVAL_MS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.warn("[{}] Interrupted while waiting for log line", peerName);
        return false;
      }
    }

    long elapsed = System.currentTimeMillis() - startTime;
    logger.warn(
        "[{}] Timeout after {}ms waiting for log line matching '{}'", peerName, elapsed, regex);
    return false;
  }

  /**
   * Reads all lines from the log file.
   *
   * @return list of log lines, or empty list if file doesn't exist or cannot be read
   */
  public List<String> readLogLines() {
    if (!Files.exists(logFilePath)) {
      logger.debug("[{}] Log file does not exist: {}", peerName, logFilePath);
      return List.of();
    }

    try {
      return Files.readAllLines(logFilePath, UTF_8);
    } catch (IOException e) {
      logger.warn("[{}] Error reading log file: {}", peerName, logFilePath, e);
      return List.of();
    }
  }

  /**
   * Returns the number of lines in the log file matching the given regex pattern.
   *
   * @param regex the regex pattern to count
   * @return the number of matching lines
   */
  public int countLogLines(String regex) {
    Pattern pattern = Pattern.compile(regex);
    return (int) readLogLines().stream().filter(line -> pattern.matcher(line).find()).count();
  }

  @Override
  public String toString() {
    return String.format(
        "PeerProcess[name=%s, logFile=%s, alive=%s]", peerName, logFilePath, isAlive());
  }
}
