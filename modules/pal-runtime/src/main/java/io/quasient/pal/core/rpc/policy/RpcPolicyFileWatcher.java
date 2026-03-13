/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.rpc.policy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Daemon thread that polls a YAML policy file for changes and reloads the {@link RpcPolicy} via
 * {@link RpcPolicyHolder} when the file's last-modified timestamp changes.
 *
 * <p>This class follows the {@code ObjectLookupStoreBackgroundProcessor} pattern: a plain daemon
 * thread with a polling loop, rather than {@code ConnectedService} (which requires ZMQ context
 * infrastructure). Polling with timestamp comparison is simple, predictable, and avoids the
 * platform-specific quirks of {@link java.nio.file.WatchService}.
 *
 * <p>On each poll cycle the watcher reads the file's last-modified time. If it has increased since
 * the last check, the watcher calls {@link RpcPolicyParser#fromOptions} to rebuild the policy from
 * the YAML file content, CLI presets, and default action. On success the holder is updated and an
 * INFO log is emitted; on failure (parse error, I/O error) an ERROR log is emitted and the current
 * policy is retained.
 *
 * <p>File deletion is handled gracefully: {@link #readLastModified()} returns {@code 0} when the
 * file is missing, which never exceeds the stored {@code lastModifiedTime}, so no reload is
 * triggered.
 *
 * <p>Threading: a single daemon thread named {@code rpc-policy-file-watcher} runs the poll loop.
 * {@link #start()} is idempotent — calling it when the watcher is already running has no effect.
 * {@link #stop()} interrupts the thread and joins with a 2-second timeout.
 */
public class RpcPolicyFileWatcher {

  /** Logger for reload success/failure messages. */
  private static final Logger LOG = LoggerFactory.getLogger(RpcPolicyFileWatcher.class);

  /** Default poll interval in milliseconds. */
  public static final long DEFAULT_POLL_INTERVAL_MS = 2000;

  /** Path to the YAML policy file to watch. */
  private final Path policyFilePath;

  /** Comma-separated CLI preset names, or {@code null} if none. */
  @Nullable private final String presetNames;

  /** CLI default action override, or {@code null} if none. */
  @Nullable private final String defaultAction;

  /** The holder whose policy is updated on successful reload. */
  private final RpcPolicyHolder policyHolder;

  /** Poll interval in milliseconds between file-modification checks. */
  private final long pollIntervalMs;

  /** Flag indicating whether the watcher loop should continue running. */
  private volatile boolean running;

  /** The daemon thread running the poll loop, or {@code null} if not started. */
  private Thread watcherThread;

  /** Last-known modification time of the policy file in epoch milliseconds. */
  private long lastModifiedTime;

  /**
   * Creates a new file watcher.
   *
   * @param policyFilePath path to the YAML policy file
   * @param presetNames comma-separated preset names to apply on reload, or {@code null}
   * @param defaultAction default action string to apply on reload, or {@code null}
   * @param policyHolder the holder to update when the policy is successfully reloaded
   * @param pollIntervalMs interval in milliseconds between modification-time checks
   */
  public RpcPolicyFileWatcher(
      Path policyFilePath,
      @Nullable String presetNames,
      @Nullable String defaultAction,
      RpcPolicyHolder policyHolder,
      long pollIntervalMs) {
    this.policyFilePath = policyFilePath;
    this.presetNames = presetNames;
    this.defaultAction = defaultAction;
    this.policyHolder = policyHolder;
    this.pollIntervalMs = pollIntervalMs;
  }

  /**
   * Starts the watcher daemon thread. Records the file's current last-modified time and begins
   * polling.
   *
   * <p>This method is idempotent: if the watcher is already running, calling {@code start()} again
   * has no effect.
   */
  public void start() {
    if (running) {
      return;
    }
    running = true;
    lastModifiedTime = readLastModified();
    watcherThread = new Thread(this::watchLoop, "rpc-policy-file-watcher");
    watcherThread.setDaemon(true);
    watcherThread.start();
  }

  /**
   * Stops the watcher by setting the running flag to {@code false}, interrupting the thread, and
   * joining with a 2-second timeout.
   *
   * <p>Follows the {@code ObjectLookupStoreBackgroundProcessor.stop()} pattern.
   */
  public void stop() {
    running = false;
    if (watcherThread != null) {
      watcherThread.interrupt();
      try {
        watcherThread.join(TimeUnit.SECONDS.toMillis(2));
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
      }
      watcherThread = null;
    }
  }

  /**
   * The main polling loop. Sleeps for {@link #pollIntervalMs} then checks whether the file has been
   * modified. Exits when {@link #running} becomes {@code false} or the thread is interrupted.
   */
  private void watchLoop() {
    while (running) {
      try {
        Thread.sleep(pollIntervalMs);
        checkAndReload();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  /**
   * Checks whether the policy file's last-modified time has changed and triggers a reload if so.
   * File deletion (last-modified returns 0) does not trigger a reload.
   */
  private void checkAndReload() {
    long currentModified = readLastModified();
    if (currentModified > lastModifiedTime && currentModified > 0) {
      lastModifiedTime = currentModified;
      reload();
    }
  }

  /**
   * Reloads the policy from the YAML file using {@link RpcPolicyParser#fromOptions}, preserving CLI
   * presets and default action. On success, updates the holder and logs at INFO. On failure, logs
   * at ERROR and keeps the current policy.
   */
  private void reload() {
    try {
      RpcPolicy newPolicy =
          RpcPolicyParser.fromOptions(policyFilePath.toString(), presetNames, defaultAction);
      policyHolder.updatePolicy(newPolicy);
      LOG.info(
          "RPC policy reloaded from {} ({} rules, default action: {})",
          policyFilePath,
          newPolicy.getRules().size(),
          newPolicy.getDefaultAction());
    } catch (Exception e) {
      LOG.error("Failed to reload RPC policy from {}; keeping current policy", policyFilePath, e);
    }
  }

  /**
   * Reads the last-modified time of the policy file.
   *
   * @return the last-modified time in epoch milliseconds, or {@code 0} if the file cannot be read
   *     (e.g. deleted or inaccessible)
   */
  private long readLastModified() {
    try {
      return Files.getLastModifiedTime(policyFilePath).toMillis();
    } catch (IOException e) {
      return 0;
    }
  }
}
