/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import io.quasient.pal.common.replay.WalEntry;
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

  /** Latch that gates injection start on peer initialization completion. */
  private final CountDownLatch readyLatch;

  /** Whether all entry points have been injected. Volatile for cross-thread visibility. */
  private volatile boolean complete;

  /**
   * Constructs a new {@code ReplayInputInjector}.
   *
   * @param threadName the WAL thread name this injector is responsible for
   * @param entryPoints the entry-point operations to inject, in WAL offset order
   * @param incomingMessageDispatcher the dispatcher for injecting messages into the runtime
   * @param replayGate the WAL-offset ordering barrier for cross-thread coordination
   * @param readyLatch latch that must be counted down before injection starts (peer initialization)
   */
  public ReplayInputInjector(
      String threadName,
      List<WalEntry> entryPoints,
      IncomingMessageDispatcher incomingMessageDispatcher,
      ReplayGate replayGate,
      CountDownLatch readyLatch) {
    this.threadName = threadName;
    this.entryPoints = Collections.unmodifiableList(new ArrayList<>(entryPoints));
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.replayGate = replayGate;
    this.readyLatch = readyLatch;
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

    for (WalEntry entryPoint : entryPoints) {
      replayGate.waitForOffset(entryPoint.getOffset());
      ExecMessage msg = entryPoint.getRawMessage();
      incomingMessageDispatcher.incomingCall(
          msg, entryPoint.getMessageType(), MessageChannelType.CLI_RPC);
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
