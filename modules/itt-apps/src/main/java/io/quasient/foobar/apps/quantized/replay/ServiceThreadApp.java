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
 * Test application that simulates a web framework dispatching requests on named executor threads.
 *
 * <p>Spawns threads named "executor-thread-0", "executor-thread-1", etc. Each thread calls
 * deterministic methods. Since the thread is started from JVM native code (not woven), the first
 * woven call inside each thread's {@code run()} method is an entry point at dispatch depth 0. This
 * mirrors how web frameworks like Quarkus dispatch HTTP requests on named worker threads.
 *
 * <p>Used by {@code ServiceThreadAffinityIT} to verify that entry points on threads matching a
 * {@code --service-thread} pattern are tagged with {@code service-request} affinity.
 */
public class ServiceThreadApp {

  /** Number of executor threads to spawn. */
  private static final int THREAD_COUNT = 3;

  /** Number of operations each thread performs. */
  private static final int OPS_PER_THREAD = 2;

  /**
   * Spawns executor threads that call deterministic methods, then waits for all to complete.
   *
   * @param args ignored
   * @throws InterruptedException if interrupted while waiting
   */
  public static void main(String[] args) throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

    for (int i = 0; i < THREAD_COUNT; i++) {
      final int threadId = i;
      Thread t =
          new Thread(
              () -> {
                try {
                  for (int op = 0; op < OPS_PER_THREAD; op++) {
                    int result = compute(threadId, op);
                    System.out.println("thread-" + threadId + " op-" + op + " = " + result);
                  }
                } finally {
                  latch.countDown();
                }
              },
              "executor-thread-" + i);
      t.start();
    }

    latch.await();
    System.out.println("done");
  }

  /**
   * Deterministic computation: returns {@code (threadId + 1) * (op + 1)}.
   *
   * @param threadId the thread identifier
   * @param op the operation index
   * @return the computed result
   */
  public static int compute(int threadId, int op) {
    return multiply(threadId + 1, op + 1);
  }

  /**
   * Multiplies two integers. Separated into its own method to produce additional woven call depth.
   *
   * @param a first operand
   * @param b second operand
   * @return a * b
   */
  public static int multiply(int a, int b) {
    return a * b;
  }
}
