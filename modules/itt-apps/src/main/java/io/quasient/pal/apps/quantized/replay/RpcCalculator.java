/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.apps.quantized.replay;

import java.util.concurrent.CountDownLatch;

/**
 * Test application for multi-threaded RPC replay integration tests.
 *
 * <p>This class provides deterministic methods callable via RPC that include an artificial delay
 * ({@link Thread#sleep(long)}) to ensure round-robin distribution across multiple RPC worker
 * threads. The peer stays alive via a {@link CountDownLatch} in {@link #main(String[])}, waiting
 * for a {@link #shutdown()} call.
 *
 * <p><strong>Usage in Integration Tests:</strong>
 *
 * <ol>
 *   <li>Start a peer with {@code --wal}, an RPC interface, and {@code --rpc-threads 2} or more
 *   <li>Use {@code pal call} to invoke {@link #factorial(int)} and {@link #sum(int, int)} multiple
 *       times
 *   <li>Call {@link #shutdown()} to terminate the peer
 *   <li>Replay the recorded WAL and verify deterministic output
 * </ol>
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * factorial(0)  → 1
 * factorial(5)  → 120
 * factorial(10) → 3628800
 *
 * sum(2, 3)     → 5
 * sum(-1, 1)    → 0
 * }</pre>
 */
public class RpcCalculator {

  /** Latch that keeps the peer alive until {@link #shutdown()} is called. */
  private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);

  /**
   * Keeps the peer alive waiting for RPC calls until {@link #shutdown()} is invoked.
   *
   * @param args command-line arguments (ignored)
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public static void main(String[] args) throws InterruptedException {
    SHUTDOWN_LATCH.await();
  }

  /**
   * Computes the factorial of {@code n} with an artificial delay to distribute work across RPC
   * threads.
   *
   * <p>The 50ms sleep ensures that when multiple RPC calls arrive concurrently, they are handled by
   * different RPC worker threads rather than all serializing onto the same thread.
   *
   * @param n non-negative integer whose factorial is computed
   * @return {@code n!} (factorial of n)
   */
  public static long factorial(int n) {
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    long result = 1;
    for (int i = 2; i <= n; i++) {
      result *= i;
    }
    return result;
  }

  /**
   * Computes the sum of two integers with an artificial delay to distribute work across RPC
   * threads.
   *
   * <p>The 50ms sleep ensures that when multiple RPC calls arrive concurrently, they are handled by
   * different RPC worker threads rather than all serializing onto the same thread.
   *
   * @param a first operand
   * @param b second operand
   * @return {@code a + b}
   */
  public static int sum(int a, int b) {
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return a + b;
  }

  /**
   * Signals the peer to shut down by releasing the latch in {@link #main(String[])}.
   *
   * <p>Called by the integration test after all RPC calls have been made, allowing the peer process
   * to exit cleanly.
   */
  public static void shutdown() {
    SHUTDOWN_LATCH.countDown();
  }
}
