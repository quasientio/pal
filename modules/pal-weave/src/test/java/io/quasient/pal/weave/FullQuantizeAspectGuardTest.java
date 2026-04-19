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
package io.quasient.pal.weave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Specification tests for the {@code TL_CALL_ADVICE_DEPTH} thread-local guard counter in {@link
 * FullQuantizeAspect}.
 *
 * <p>The guard counter prevents double-dispatch between call-site and execution-site advice: when
 * call-site advice is active (depth &gt; 0), execution-site advice must skip dispatch and simply
 * proceed. These tests verify the counter's invariants under nested, concurrent, and exceptional
 * scenarios.
 */
public class FullQuantizeAspectGuardTest {

  /** Ensures the thread-local starts fresh on the test thread. */
  @Before
  public void resetBefore() {
    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.remove();
  }

  /** Removes any state so the counter does not leak into other tests on the same thread. */
  @After
  public void resetAfter() {
    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.remove();
  }

  /**
   * Verifies the guard counter is initialized to 0 on a fresh thread.
   *
   * <p>This is the baseline invariant: any thread that has never touched the counter should observe
   * a value of 0, which is the "no call-site advice active" state.
   */
  @Test
  public void shouldInitializeCounterToZero() {
    assertEquals(
        "Fresh thread-local must initialize to 0",
        Integer.valueOf(0),
        FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());
  }

  /**
   * Verifies that a single increment followed by a matching decrement returns the counter to 0.
   *
   * <p>Models the simplest call-site advice lifecycle: enter advice (increment), exit advice
   * (decrement in finally).
   */
  @Test
  public void shouldIncrementAndDecrementInPairs() {
    assertEquals(Integer.valueOf(0), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());

    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() + 1);
    assertEquals(Integer.valueOf(1), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());

    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() - 1);
    assertEquals(Integer.valueOf(0), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());
  }

  /**
   * Verifies nested increments and matching decrements balance correctly.
   *
   * <p>Models woven→woven→woven call chains: each nested call-site advice adds to the depth, and
   * each exit subtracts. After all exits, the counter must be back to 0.
   */
  @Test
  public void shouldHandleNestedIncrements() {
    assertEquals(Integer.valueOf(0), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());

    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() + 1);
    assertEquals(Integer.valueOf(1), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());

    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() + 1);
    assertEquals(Integer.valueOf(2), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());

    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() + 1);
    assertEquals(Integer.valueOf(3), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());

    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() - 1);
    assertEquals(Integer.valueOf(2), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());

    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() - 1);
    assertEquals(Integer.valueOf(1), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());

    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() - 1);
    assertEquals(Integer.valueOf(0), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());
  }

  /**
   * Verifies the counter is thread-local and changes on one thread do not leak to others.
   *
   * <p>Concurrent peer threads must not interfere with each other's guard state, otherwise
   * execution-site advice on one thread could be incorrectly suppressed by call-site activity on
   * another.
   */
  @Test
  public void shouldIsolateCountersPerThread() throws InterruptedException {
    final CountDownLatch mainIncremented = new CountDownLatch(1);
    final CountDownLatch workerChecked = new CountDownLatch(1);
    final AtomicInteger workerObserved = new AtomicInteger(-1);

    Thread worker =
        new Thread(
            () -> {
              try {
                mainIncremented.await(5, TimeUnit.SECONDS);
                workerObserved.set(FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.remove();
                workerChecked.countDown();
              }
            });
    worker.start();

    FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(42);
    mainIncremented.countDown();

    workerChecked.await(5, TimeUnit.SECONDS);
    worker.join(5_000);

    assertEquals(
        "Worker thread must observe its own fresh counter, not the main thread's value",
        0,
        workerObserved.get());
    assertEquals(
        "Main thread's counter must remain unchanged by worker thread",
        Integer.valueOf(42),
        FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());
  }

  /**
   * Verifies the counter is correctly decremented when the guarded body throws.
   *
   * <p>The advice wraps dispatch in a try/finally; an exception thrown from within the guarded
   * region must still result in the counter being decremented, so subsequent advice on the same
   * thread observes the correct depth.
   */
  @Test
  public void shouldDecrementOnExceptionPath() {
    assertEquals(Integer.valueOf(0), FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());

    assertThrows(
        RuntimeException.class,
        () -> {
          FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(
              FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() + 1);
          try {
            throw new RuntimeException("simulated dispatch failure");
          } finally {
            FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.set(
                FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get() - 1);
          }
        });

    assertEquals(
        "Counter must be decremented back to 0 on the exceptional return path",
        Integer.valueOf(0),
        FullQuantizeAspect.TL_CALL_ADVICE_DEPTH.get());
  }
}
