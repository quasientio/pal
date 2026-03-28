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
package io.quasient.pal.core.replay;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import io.quasient.pal.core.transport.MessageChannelType;
import io.quasient.pal.messages.colfer.ExecMessage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drives WAL-based input injection on a non-self-caller thread during deterministic replay.
 *
 * <p>Each instance reads entry-point operations from a pre-built list of {@link WalEntry} instances
 * (extracted from the {@link io.quasient.pal.common.replay.WalIndex} for a specific thread) and
 * injects them into the runtime via {@link IncomingMessageDispatcher#incomingCall}. This causes the
 * replayed peer to re-execute incoming RPC calls in the correct order.
 *
 * <p>The injection sequence is:
 *
 * <ol>
 *   <li>Wait for the {@code readyLatch} to be counted down (peer initialization complete)
 *   <li>For each entry point, wait for the {@link ReplayGate} to reach the required WAL offset
 *   <li>Pass the raw {@link ExecMessage} from the {@link WalEntry} to {@link
 *       IncomingMessageDispatcher#incomingCall} with {@link MessageChannelType#CLI_RPC}
 *   <li>After all entry points are injected, mark the injector as complete
 * </ol>
 *
 * <p>Nested operations within each injected method flow through the normal {@code dispatch()} →
 * {@code dispatchReplay()} path on the same thread.
 *
 * <p>Thread safety: The {@code complete} field is {@code volatile} for cross-thread visibility.
 * This class is designed to run on a single thread whose name matches the WAL thread name.
 */
@SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "ReplayContext is intentionally shared across replay components")
public class ReplayInputInjector implements Runnable {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(ReplayInputInjector.class);

  /** The WAL thread name this injector is responsible for. */
  private final String threadName;

  /** The entry-point operations to inject, in WAL offset order. */
  private final List<WalEntry> entryPoints;

  /** The dispatcher used to inject entry-point messages into the runtime. */
  private final IncomingMessageDispatcher incomingMessageDispatcher;

  /** The ordering barrier for cross-thread replay coordination. */
  private final ReplayGate replayGate;

  /** The replay context for checking if entry points were already handled. */
  private final ReplayContext replayContext;

  /** Latch that gates injection start on peer initialization completion. */
  private final CountDownLatch readyLatch;

  /**
   * Delay in milliseconds before injecting each entry point. Used for slow-motion replay
   * visualization. A value of {@code 0} disables the delay.
   */
  private final long operationDelayMs;

  /**
   * The WAL index for looking up completion offsets. Used to wait for each entry point's span to
   * complete before proceeding to the next entry point.
   */
  private final WalIndex walIndex;

  /** Whether all entry points have been injected. Volatile for cross-thread visibility. */
  private volatile boolean complete;

  /**
   * Constructs a new {@code ReplayInputInjector} with no operation delay.
   *
   * @param threadName the WAL thread name this injector is responsible for
   * @param entryPoints the entry-point operations to inject, in WAL offset order
   * @param incomingMessageDispatcher the dispatcher for injecting messages into the runtime
   * @param replayGate the WAL-offset ordering barrier for cross-thread coordination
   * @param replayContext the replay context for checking if entry points were already handled
   * @param readyLatch latch that must be counted down before injection starts (peer initialization)
   * @param walIndex the WAL index for looking up completion offsets
   */
  public ReplayInputInjector(
      String threadName,
      List<WalEntry> entryPoints,
      IncomingMessageDispatcher incomingMessageDispatcher,
      ReplayGate replayGate,
      ReplayContext replayContext,
      CountDownLatch readyLatch,
      WalIndex walIndex) {
    this(
        threadName,
        entryPoints,
        incomingMessageDispatcher,
        replayGate,
        replayContext,
        readyLatch,
        0L,
        walIndex);
  }

  /**
   * Constructs a new {@code ReplayInputInjector} with a configurable operation delay.
   *
   * @param threadName the WAL thread name this injector is responsible for
   * @param entryPoints the entry-point operations to inject, in WAL offset order
   * @param incomingMessageDispatcher the dispatcher for injecting messages into the runtime
   * @param replayGate the WAL-offset ordering barrier for cross-thread coordination
   * @param replayContext the replay context for checking if entry points were already handled
   * @param readyLatch latch that must be counted down before injection starts (peer initialization)
   * @param operationDelayMs delay in milliseconds before each entry point injection (0 to disable)
   * @param walIndex the WAL index for looking up completion offsets
   */
  public ReplayInputInjector(
      String threadName,
      List<WalEntry> entryPoints,
      IncomingMessageDispatcher incomingMessageDispatcher,
      ReplayGate replayGate,
      ReplayContext replayContext,
      CountDownLatch readyLatch,
      long operationDelayMs,
      WalIndex walIndex) {
    this.threadName = threadName;
    this.entryPoints = Collections.unmodifiableList(new ArrayList<>(entryPoints));
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.replayGate = replayGate;
    this.replayContext = replayContext;
    this.readyLatch = readyLatch;
    this.operationDelayMs = operationDelayMs;
    this.walIndex = walIndex;
  }

  /**
   * Runs the injection loop: waits for the ready latch, then injects each entry point in sequence.
   *
   * <p>If the thread is interrupted while waiting on the ready latch, the interrupt flag is
   * restored and the method returns without injecting any entry points.
   */
  @Override
  public void run() {
    try {
      readyLatch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.warn(
          "ReplayInputInjector for thread '{}' interrupted during ready-latch wait", threadName);
      return;
    }

    logger.info(
        "ReplayInputInjector for thread '{}' starting injection of {} entry points",
        threadName,
        entryPoints.size());

    int entryPointIndex = 0;
    for (WalEntry entryPoint : entryPoints) {
      entryPointIndex++;
      logger.info(
          "[{}] Waiting for entry point {}/{} at offset {} (gate currently at {}): {}.{}",
          threadName,
          entryPointIndex,
          entryPoints.size(),
          entryPoint.getOffset(),
          replayGate.getCompletedOffset(),
          entryPoint.getClassName(),
          entryPoint.getExecutableName());

      replayGate.waitForOffset(entryPoint.getOffset());

      // NOTE: We intentionally do NOT check isEntryPointHandled() here on the injector thread.
      // There's a memory visibility race: dispatchReplay on the FX thread might mark the entry
      // point as handled and advance the gate, but this thread might see the gate advanced
      // without seeing the handled flag (due to cross-data-structure visibility issues).
      //
      // Instead, we always inject (queue the callback) and let the callback's isEntryPointHandled
      // check handle it. The callback runs on the FX thread, same as dispatchReplay, so there
      // are no visibility issues.

      if (logger.isDebugEnabled()) {
        logger.debug(
            "[{}] Injecting entry point at offset {}: {}.{}",
            threadName,
            entryPoint.getOffset(),
            entryPoint.getClassName(),
            entryPoint.getExecutableName());
      }

      // Advance the gate to signal that we've reached this entry point's offset. This allows
      // other threads waiting on subsequent offsets to proceed. We do this HERE (in the
      // injector) rather than in dispatchIncoming() because the injector knows the offset
      // from the WalEntry, while dispatchIncoming() would have to peek the cursor (which is
      // racy since the cursor is owned by the target thread, not the injector thread).
      replayGate.advanceTo(entryPoint.getOffset());

      // Apply slow-motion delay if configured (for visual debugging)
      if (operationDelayMs > 0) {
        try {
          Thread.sleep(operationDelayMs);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          if (logger.isDebugEnabled()) {
            logger.debug("Replay delay interrupted for thread '{}'", threadName);
          }
          return;
        }
      }

      // Push the entry point offset to the pending injection queue BEFORE calling incomingCall().
      // The callback in dispatchIncoming() will pop this offset to know the exact entry point
      // being injected, avoiding race conditions where the cursor has advanced by the time
      // the callback runs (especially with Platform.runLater() for JavaFX thread affinity).
      replayContext.pushPendingInjection(threadName, entryPoint.getOffset());

      ExecMessage msg = entryPoint.getRawMessage();
      incomingMessageDispatcher.incomingCall(
          msg, entryPoint.getMessageType(), MessageChannelType.REPLAY_INJECTION);

      // Wait for the entry point's completion before proceeding to the next entry point.
      // This ensures all nested operations within the entry point's span are processed,
      // preventing race conditions where the next injection overlaps with the current one's
      // nested operations (which caused cursor misalignment without --delay).
      Long completionOffset = walIndex.getCompletionOffset(entryPoint.getOffset());
      if (completionOffset != null) {
        if (logger.isDebugEnabled()) {
          logger.debug(
              "[{}] Waiting for completion of entry point at offset {} (completion at {})",
              threadName,
              entryPoint.getOffset(),
              completionOffset);
        }
        replayGate.waitForOffset(completionOffset + 1);
        if (logger.isDebugEnabled()) {
          logger.debug(
              "[{}] Entry point at offset {} completed (gate at {})",
              threadName,
              entryPoint.getOffset(),
              replayGate.getCompletedOffset());
        }
      } else {
        logger.warn(
            "[{}] No completion offset found for entry point at offset {} - proceeding without wait",
            threadName,
            entryPoint.getOffset());
      }

      if (logger.isDebugEnabled()) {
        logger.debug(
            "[{}] Finished injecting entry point at offset {}", threadName, entryPoint.getOffset());
      }
    }

    complete = true;
    logger.info("ReplayInputInjector for thread '{}' completed", threadName);
  }

  /**
   * Returns whether all entry points have been injected.
   *
   * @return {@code true} if the injection loop has completed, {@code false} otherwise
   */
  public boolean isComplete() {
    return complete;
  }

  /**
   * Returns the WAL thread name this injector is responsible for.
   *
   * @return the thread name
   */
  public String getThreadName() {
    return threadName;
  }
}
