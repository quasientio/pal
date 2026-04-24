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
package io.quasient.foobar.apps.quantized.replay;

import java.util.concurrent.CountDownLatch;

/**
 * Test application with a single quantized call followed by optional indefinite blocking.
 *
 * <p>Used by {@code ReplaySigtermIT} to exercise the shutdown-hook path of the replay divergence
 * reporter.
 *
 * <ul>
 *   <li>Recording: invoked as {@code BlockingReplayApp <int>}, calls {@link #compute(int)}, prints
 *       the result, and exits cleanly so the WAL is flushed on normal shutdown.
 *   <li>Replay: invoked as {@code BlockingReplayApp <int> block}, runs the same call (which
 *       diverges from the WAL when the integer differs from the recording) and then parks on a
 *       latch forever. The second argument value is ignored — any second argument triggers the
 *       block — so the test need not pass a {@code --}-prefixed flag that {@code pal replay} would
 *       consume as one of its own options. The test must deliver SIGTERM, which in turn must fire
 *       the shutdown hook and emit the divergence report on stderr.
 * </ul>
 */
public final class BlockingReplayApp {

  private BlockingReplayApp() {}

  /**
   * Multiplies the input by seven. Declared {@code static} so the quantized call appears in the WAL
   * without requiring instance construction.
   *
   * @param x the input value
   * @return {@code x * 7}
   */
  public static int compute(int x) {
    return x * 7;
  }

  /**
   * Prints {@code compute(arg)} to stdout, flushes, and optionally blocks forever on a latch.
   *
   * <p>The quantized-call sequence is independent of {@code args.length}, so the recording run (one
   * arg) and the replay run (two args) produce divergences only on {@link Integer#parseInt(String)}
   * and {@link #compute(int)} rather than on string comparisons against a blocking sentinel.
   *
   * @param args a single integer argument, optionally followed by any second argument to block
   * @throws InterruptedException if the blocking latch is interrupted (it never is in normal use)
   */
  public static void main(String[] args) throws InterruptedException {
    if (args.length < 1) {
      System.err.println("Usage: BlockingReplayApp <int> [block]");
      return;
    }
    int value = compute(Integer.parseInt(args[0]));
    System.out.println("value: " + value);
    System.out.flush();
    if (args.length >= 2) {
      new CountDownLatch(1).await();
    }
  }
}
