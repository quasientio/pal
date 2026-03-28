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
 * Test application that calls non-deterministic operations: {@code System.currentTimeMillis()} and
 * {@code Math.random()}.
 *
 * <p>Used by {@code StubFromWalReplayIT} to verify that replay with {@code --shield-io} stubs these
 * non-deterministic calls from the WAL. Without stubbing, the different return values on replay
 * would cause divergences.
 *
 * <p>Supports a 2-thread RPC variant: launch with {@code --as-service} and call {@link
 * #compute(String[])} via RPC. The RPC method returns a string combining time and random values.
 * Call {@link #shutdown(String[])} to release the latch and allow the peer to exit.
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * // Single-thread: prints time-dependent output
 * TimeDependentApp.main(new String[]{"run"});
 * // Output: "TimeDep: time=1699564800000 random=0.42..."
 *
 * // Multi-thread via RPC:
 * TimeDependentApp.compute(new String[]{})
 * // Returns: "time=1699564800000 random=0.42..."
 * }</pre>
 */
public class TimeDependentApp {

  /**
   * Latch that keeps the peer alive in service mode until {@link #shutdown(String[])} is called.
   */
  private static final CountDownLatch SHUTDOWN_LATCH = new CountDownLatch(1);

  /**
   * Entry point that performs time-dependent computations.
   *
   * <p>If the first argument is {@code "service"}, the app waits for RPC calls. Otherwise, it
   * performs computations and prints the results directly.
   *
   * @param args command-line arguments; first arg may be "service" for RPC mode
   * @throws InterruptedException if the thread is interrupted while waiting
   */
  public static void main(String[] args) throws InterruptedException {
    if (args.length > 0 && "service".equals(args[0])) {
      SHUTDOWN_LATCH.await();
      return;
    }

    long time1 = System.currentTimeMillis();
    double rand1 = Math.random();
    long time2 = System.currentTimeMillis();
    double rand2 = Math.random();

    // Use the values in a deterministic computation
    long timeDiff = time2 - time1;
    double randSum = rand1 + rand2;

    System.out.println(
        "TimeDep: time1="
            + time1
            + " time2="
            + time2
            + " diff="
            + timeDiff
            + " randSum="
            + randSum
            + " rand1="
            + rand1
            + " rand2="
            + rand2);
  }

  /**
   * RPC-callable method that returns time-dependent values.
   *
   * <p>Called via {@code pal call -m compute} in multi-threaded tests. Returns a string containing
   * the current time and a random number.
   *
   * @param args ignored (required for {@code pal call -m} compatibility)
   * @return a string with time and random values
   */
  public static String compute(String[] args) {
    long time = System.currentTimeMillis();
    double rand = Math.random();
    return "time=" + time + " random=" + rand;
  }

  /**
   * Signals the peer to shut down by releasing the latch in {@link #main(String[])}.
   *
   * @param args ignored (required for {@code pal call -m} compatibility)
   */
  public static void shutdown(String[] args) {
    SHUTDOWN_LATCH.countDown();
  }
}
