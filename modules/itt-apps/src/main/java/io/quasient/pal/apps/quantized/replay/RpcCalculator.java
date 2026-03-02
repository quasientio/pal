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
 * <p>This class provides deterministic methods callable via RPC. All methods accept {@code
 * String[]} parameters because {@code pal call -m} only supports this signature type. The peer
 * stays alive via a {@link CountDownLatch} in {@link #main(String[])}, waiting for a {@link
 * #shutdown(String[])} call.
 *
 * <p>Thread distribution across RPC worker threads is handled by ZMQ's DEALER round-robin pattern.
 * A 50ms artificial delay in compute methods prevents them from being instantaneous, which helps
 * exercise the replay timing machinery.
 *
 * <p><strong>Broken mode:</strong> When the first argument to {@link #main(String[])} is {@code
 * "broken"}, the calculator deliberately produces incorrect results. This mode is used by
 * cross-divergence replay tests: record a WAL with normal mode, then replay with broken mode to
 * verify that the replay engine detects VALUE_MISMATCH divergences.
 *
 * <p><strong>Usage in Integration Tests:</strong>
 *
 * <ol>
 *   <li>Start a peer with {@code --wal}, an RPC interface, and {@code --rpc-threads 2} or more
 *   <li>Use {@code pal call} to invoke {@link #factorial(String[])} and {@link #sum(String[])}
 *       multiple times
 *   <li>Call {@link #shutdown(String[])} to terminate the peer
 *   <li>Replay the recorded WAL and verify deterministic output
 * </ol>
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * factorial({"0"})  → 1
 * factorial({"5"})  → 120
 * factorial({"10"}) → 3628800
 *
 * sum({"2", "3"})   → 5
 * sum({"-1", "1"})  → 0
 * }</pre>
 */
public class RpcCalculator {

  /** Latch that keeps the peer alive until {@link #shutdown(String[])} is called. */
  private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);

  /**
   * When {@code true}, {@link #factorial(String[])} and {@link #sum(String[])} deliberately produce
   * wrong results. Activated by passing {@code "broken"} as the first argument to {@link
   * #main(String[])}.
   */
  private static volatile boolean brokenMode = false;

  /**
   * Keeps the peer alive waiting for RPC calls until {@link #shutdown(String[])} is invoked.
   *
   * <p>If the first argument is {@code "broken"}, enables broken mode which causes {@link
   * #factorial(String[])} and {@link #sum(String[])} to return deliberately incorrect results. This
   * supports cross-divergence replay testing.
   *
   * @param args command-line arguments; first arg may be "broken" to enable broken mode
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public static void main(String[] args) throws InterruptedException {
    if (args.length > 0 && "broken".equals(args[0])) {
      brokenMode = true;
    }
    SHUTDOWN_LATCH.await();
  }

  /**
   * Computes the factorial of the integer in {@code args[0]} with a 50ms artificial delay.
   *
   * <p>The delay provides a non-zero execution time to exercise replay timing. Thread distribution
   * across RPC worker threads is handled by ZMQ's DEALER round-robin pattern.
   *
   * <p>In broken mode, returns {@code n! + 1} instead of {@code n!} to trigger VALUE_MISMATCH
   * during replay.
   *
   * @param args single-element array where {@code args[0]} is the integer whose factorial is
   *     computed
   * @return {@code n!} (factorial of n), or {@code n! + 1} in broken mode
   */
  public static long factorial(String[] args) {
    int n = Integer.parseInt(args[0]);
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    long result = 1;
    for (int i = 2; i <= n; i++) {
      result *= i;
    }
    return brokenMode ? result + 1 : result;
  }

  /**
   * Computes the sum of two integers in {@code args[0]} and {@code args[1]} with a 50ms artificial
   * delay.
   *
   * <p>The delay provides a non-zero execution time to exercise replay timing. Thread distribution
   * across RPC worker threads is handled by ZMQ's DEALER round-robin pattern.
   *
   * <p>In broken mode, returns {@code a + b + 1} instead of {@code a + b} to trigger VALUE_MISMATCH
   * during replay.
   *
   * @param args two-element array where {@code args[0]} is the first operand and {@code args[1]} is
   *     the second
   * @return {@code a + b}, or {@code a + b + 1} in broken mode
   */
  public static int sum(String[] args) {
    int a = Integer.parseInt(args[0]);
    int b = Integer.parseInt(args[1]);
    try {
      Thread.sleep(50);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return brokenMode ? a + b + 1 : a + b;
  }

  /**
   * Signals the peer to shut down by releasing the latch in {@link #main(String[])}.
   *
   * <p>Called by the integration test after all RPC calls have been made, allowing the peer process
   * to exit cleanly. The {@code args} parameter is required by the {@code pal call -m} invocation
   * pattern but is ignored.
   *
   * @param args ignored (required for {@code pal call -m} compatibility)
   */
  public static void shutdown(String[] args) {
    SHUTDOWN_LATCH.countDown();
  }
}
