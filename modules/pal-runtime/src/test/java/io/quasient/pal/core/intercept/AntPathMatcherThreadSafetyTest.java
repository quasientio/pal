/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.azagniotov.matcher.AntPathMatcherArrays;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/**
 * Thread-safety verification tests for the static {@link AntPathMatcherArrays} instance used in
 * {@link InterceptRequestEntry} (line 61) and {@link InFlightDispatchTracker} (line 237).
 *
 * <p>Analysis of the {@code AntPathMatcherArrays} bytecode (v1.0.0) confirms the class is
 * <b>unconditionally thread-safe</b>:
 *
 * <ul>
 *   <li>All 4 instance fields ({@code pathSeparator}, {@code ignoreCase}, {@code matchStart},
 *       {@code trimTokens}) are {@code private final}
 *   <li>All 4 static fields ({@code ASTERISK}, {@code QUESTION}, {@code BLANK}, {@code
 *       ASCII_CASE_DIFFERENCE_VALUE}) are {@code private static final} compile-time constants
 *   <li>There are <b>no caches, buffers, counters, or any mutable state</b>
 *   <li>{@code isMatch()} is a <b>pure function</b>: it creates only method-local {@code char[]}
 *       arrays (via {@code String.toCharArray()}) and delegates to recursive private helpers that
 *       read only method parameters and final fields
 *   <li>No {@code synchronized} blocks, no {@code volatile} fields, no lazy initialization
 * </ul>
 *
 * <p>These tests empirically verify thread safety by hammering the shared static instance from many
 * threads concurrently and checking that all results remain correct.
 *
 * <p>Phase 0 of the hot-path optimization plan (#668): safety verification before proceeding with
 * optimization work that assumes thread-safe read-only matching.
 *
 * @see InterceptRequestEntry
 * @see InFlightDispatchTracker
 */
@SuppressWarnings("UnusedVariable")
public class AntPathMatcherThreadSafetyTest {

  /** Timeout rule to prevent hanging tests (30 seconds). */
  @Rule public Timeout globalTimeout = Timeout.seconds(30);

  /** Number of concurrent threads used in each test. */
  private static final int THREAD_COUNT = 64;

  /** Number of iterations each thread performs. */
  private static final int ITERATIONS_PER_THREAD = 10_000;

  /**
   * Static AntPathMatcherArrays instance configured identically to the one in {@link
   * InterceptRequestEntry}: dot separator, trimTokens, ignoreCase.
   */
  private static final AntPathMatcherArrays matcher =
      new AntPathMatcherArrays.Builder()
          .withPathSeparator('.')
          .withTrimTokens()
          .withIgnoreCase()
          .build();

  /** Shared message builder for constructing InterceptRequestEntry instances in test 5. */
  private final MessageBuilder msgBuilder = new MessageBuilder();

  /** Shared peer UUID for test intercept messages. */
  private final UUID peerUuid = UUID.randomUUID();

  /** Executor service for running concurrent threads. */
  private ExecutorService executor;

  /** Sets up the thread pool before each test. */
  @Before
  public void setUp() {
    executor = Executors.newFixedThreadPool(THREAD_COUNT);
  }

  /** Shuts down the thread pool after each test. */
  @After
  public void tearDown() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  /**
   * Verifies that 64 threads can concurrently call {@code isMatch()} with exact (non-wildcard)
   * patterns and get correct results every time.
   *
   * <p>This tests the most common matching path where both pattern and path are fully qualified
   * dot-separated names with no wildcards.
   */
  @Test
  public void shouldMatchConcurrentlyWithExactPatterns() throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    AtomicInteger failures = new AtomicInteger(0);
    CountDownLatch done = new CountDownLatch(THREAD_COUNT);

    for (int t = 0; t < THREAD_COUNT; t++) {
      var unused =
          executor.submit(
              () -> {
                try {
                  barrier.await();
                  for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    // Exact match: same pattern and path
                    if (!matcher.isMatch("com.example.Foo.bar", "com.example.Foo.bar")) {
                      failures.incrementAndGet();
                    }
                    // Exact non-match: different method name
                    if (matcher.isMatch("com.example.Foo.bar", "com.example.Foo.baz")) {
                      failures.incrementAndGet();
                    }
                    // Exact match: different class
                    if (!matcher.isMatch(
                        "java.io.PrintStream.println", "java.io.PrintStream.println")) {
                      failures.incrementAndGet();
                    }
                    // Exact non-match: different method
                    if (matcher.isMatch("java.lang.System.gc", "java.lang.System.exit")) {
                      failures.incrementAndGet();
                    }
                  }
                } catch (Exception e) {
                  failures.incrementAndGet();
                } finally {
                  done.countDown();
                }
              });
    }

    done.await();
    assertEquals(
        "Expected zero failures across " + THREAD_COUNT + " threads with exact patterns",
        0,
        failures.get());
  }

  /**
   * Verifies that 64 threads can concurrently call {@code isMatch()} with wildcard patterns
   * (single-star {@code *}, double-star {@code **}, question mark {@code ?}) and get correct
   * results every time.
   *
   * <p>Wildcard matching exercises different code paths within AntPathMatcherArrays that may
   * involve internal array tokenization and comparison loops.
   */
  @Test
  public void shouldMatchConcurrentlyWithWildcardPatterns() throws Exception {
    CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    AtomicInteger failures = new AtomicInteger(0);
    CountDownLatch done = new CountDownLatch(THREAD_COUNT);

    for (int t = 0; t < THREAD_COUNT; t++) {
      var unused =
          executor.submit(
              () -> {
                try {
                  barrier.await();
                  for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    // Single wildcard: matches any single segment
                    if (!matcher.isMatch("com.example.*.bar", "com.example.Foo.bar")) {
                      failures.incrementAndGet();
                    }
                    if (matcher.isMatch("com.example.*.bar", "com.example.Foo.baz")) {
                      failures.incrementAndGet();
                    }

                    // Double wildcard: matches multiple segments
                    if (!matcher.isMatch("com.**.bar", "com.example.deep.nested.Foo.bar")) {
                      failures.incrementAndGet();
                    }
                    if (matcher.isMatch("com.**.bar", "com.example.Foo.baz")) {
                      failures.incrementAndGet();
                    }

                    // Trailing wildcard
                    if (!matcher.isMatch("com.example.Foo.*", "com.example.Foo.bar")) {
                      failures.incrementAndGet();
                    }
                    if (matcher.isMatch("com.example.Foo.*", "com.other.Foo.bar")) {
                      failures.incrementAndGet();
                    }

                    // Question mark: matches single character
                    if (!matcher.isMatch("com.example.Fo?.bar", "com.example.Foo.bar")) {
                      failures.incrementAndGet();
                    }
                    if (matcher.isMatch("com.example.Fo?.bar", "com.example.Food.bar")) {
                      failures.incrementAndGet();
                    }
                  }
                } catch (Exception e) {
                  failures.incrementAndGet();
                } finally {
                  done.countDown();
                }
              });
    }

    done.await();
    assertEquals(
        "Expected zero failures across " + THREAD_COUNT + " threads with wildcard patterns",
        0,
        failures.get());
  }

  /**
   * Verifies that 64 threads concurrently calling {@code isMatch()} with a mix of matching and
   * non-matching inputs produce results consistent with single-threaded execution.
   *
   * <p>This test alternates between inputs that should match and inputs that should not match,
   * exercising both the "match found" and "match not found" code paths concurrently.
   */
  @Test
  public void shouldMatchConcurrentlyWithMixedMatchAndNonMatch() throws Exception {
    // Pre-compute test cases with expected results
    List<Object[]> testCases = new ArrayList<>();
    testCases.add(new Object[] {"com.example.Foo.bar", "com.example.Foo.bar", true});
    testCases.add(new Object[] {"com.example.Foo.bar", "com.example.Foo.baz", false});
    testCases.add(new Object[] {"com.example.*.bar", "com.example.Foo.bar", true});
    testCases.add(new Object[] {"com.example.*.bar", "com.other.Foo.bar", false});
    testCases.add(new Object[] {"com.**.process", "com.deep.nested.Handler.process", true});
    testCases.add(new Object[] {"com.**.process", "com.deep.nested.Handler.execute", false});
    testCases.add(new Object[] {"io.quasient.pal.*.run", "io.quasient.pal.Main.run", true});
    testCases.add(new Object[] {"io.quasient.pal.*.run", "io.quasient.pal.Main.stop", false});
    testCases.add(new Object[] {"*.*.Foo.bar", "com.example.Foo.bar", true});
    testCases.add(new Object[] {"*.*.Foo.bar", "com.example.Bar.bar", false});
    testCases.add(new Object[] {"com.example.Fo?.bar", "com.example.Fox.bar", true});
    testCases.add(new Object[] {"com.example.Fo?.bar", "com.example.Foxx.bar", false});

    // Verify single-threaded correctness first
    for (Object[] tc : testCases) {
      boolean result = matcher.isMatch((String) tc[0], (String) tc[1]);
      assertEquals(
          "Pre-check failed for pattern='" + tc[0] + "' path='" + tc[1] + "'", tc[2], result);
    }

    CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    AtomicInteger failures = new AtomicInteger(0);
    CountDownLatch done = new CountDownLatch(THREAD_COUNT);

    for (int t = 0; t < THREAD_COUNT; t++) {
      var unused =
          executor.submit(
              () -> {
                try {
                  barrier.await();
                  for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    for (Object[] tc : testCases) {
                      boolean result = matcher.isMatch((String) tc[0], (String) tc[1]);
                      if (result != (boolean) tc[2]) {
                        failures.incrementAndGet();
                      }
                    }
                  }
                } catch (Exception e) {
                  failures.incrementAndGet();
                } finally {
                  done.countDown();
                }
              });
    }

    done.await();
    assertEquals(
        "Expected zero failures across "
            + THREAD_COUNT
            + " threads with mixed match/non-match inputs",
        0,
        failures.get());
  }

  /**
   * Verifies that the double-wildcard pattern {@code **.*} (match everything) is thread-safe under
   * concurrent access.
   *
   * <p>The {@code **} pattern exercises the most complex matching logic in AntPathMatcherArrays
   * since it must match an arbitrary number of path segments. This test ensures the greedy matching
   * algorithm has no shared mutable state.
   */
  @Test
  public void shouldMatchConcurrentlyWithDoubleWildcard() throws Exception {
    String[] paths = {
      "com.example.Foo.bar",
      "java.io.PrintStream.println",
      "a.b",
      "very.deep.package.structure.ClassName.methodName",
      "Single.method"
    };

    CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    AtomicInteger failures = new AtomicInteger(0);
    CountDownLatch done = new CountDownLatch(THREAD_COUNT);

    for (int t = 0; t < THREAD_COUNT; t++) {
      var unused =
          executor.submit(
              () -> {
                try {
                  barrier.await();
                  for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    for (String path : paths) {
                      if (!matcher.isMatch("**.*", path)) {
                        failures.incrementAndGet();
                      }
                    }
                  }
                } catch (Exception e) {
                  failures.incrementAndGet();
                } finally {
                  done.countDown();
                }
              });
    }

    done.await();
    assertEquals(
        "Expected zero failures: '**.*' should match all paths across " + THREAD_COUNT + " threads",
        0,
        failures.get());
  }

  /**
   * Verifies that the full {@link InterceptRequestEntry#matches(String, String, String[])} method
   * is thread-safe when called concurrently on the SAME entry object by 64 threads.
   *
   * <p>This goes beyond just testing {@code AntPathMatcherArrays.isMatch()} -- it also exercises
   * the {@code String.format()} and {@code String.join()} calls within matches(), as well as the
   * parameter type comparison logic. The entry object's fields (pattern, paramTypes) are final and
   * should be safely published, but this test verifies it empirically.
   *
   * <p>Also tests the optimized overload {@link InterceptRequestEntry#matches(String, String)}
   * concurrently to verify both code paths.
   */
  @Test
  public void shouldMatchConcurrentlyWithParameterTypes() throws Exception {
    // Build entry: com.example.Foo.bar(int,String)
    InterceptMessage msgFooBar =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "com.example.Foo",
            "bar",
            List.of("int", "String"),
            "callback.Class",
            "callbackMethod");
    InterceptRequestEntry entryFooBar = new InterceptRequestEntry(msgFooBar);

    // Build entry: com.example.*.process(long)
    InterceptMessage msgWildcard =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "com.example.*",
            "process",
            List.of("long"),
            "callback.Class",
            "callbackMethod");
    InterceptRequestEntry entryWildcard = new InterceptRequestEntry(msgWildcard);

    // Build entry: com.example.Foo.bar() (zero-arg)
    InterceptMessage msgNoArgs =
        msgBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            "com.example.Foo",
            "bar",
            List.of(),
            "callback.Class",
            "callbackMethod");
    InterceptRequestEntry entryNoArgs = new InterceptRequestEntry(msgNoArgs);

    // Verify single-threaded correctness first
    assertTrue(entryFooBar.matches("com.example.Foo", "bar", new String[] {"int", "String"}));
    assertTrue(!entryFooBar.matches("com.example.Foo", "bar", new String[] {"int", "int"}));
    assertTrue(!entryFooBar.matches("com.example.Foo", "bar", new String[] {"int"}));

    assertTrue(entryWildcard.matches("com.example.Handler", "process", new String[] {"long"}));
    assertTrue(!entryWildcard.matches("com.example.Handler", "process", new String[] {"int"}));

    assertTrue(entryNoArgs.matches("com.example.Foo", "bar", new String[0]));
    assertTrue(!entryNoArgs.matches("com.example.Foo", "bar", new String[] {"int"}));

    // Verify optimized overload
    assertTrue(entryFooBar.matches("com.example.Foo.bar", "int,String"));
    assertTrue(!entryFooBar.matches("com.example.Foo.bar", "int,int"));
    assertTrue(entryNoArgs.matches("com.example.Foo.bar", ""));
    assertTrue(!entryNoArgs.matches("com.example.Foo.bar", "int"));

    CyclicBarrier barrier = new CyclicBarrier(THREAD_COUNT);
    AtomicInteger failures = new AtomicInteger(0);
    CountDownLatch done = new CountDownLatch(THREAD_COUNT);

    for (int t = 0; t < THREAD_COUNT; t++) {
      var unused =
          executor.submit(
              () -> {
                try {
                  barrier.await();
                  for (int i = 0; i < ITERATIONS_PER_THREAD; i++) {
                    // entryFooBar: com.example.Foo.bar(int,String)
                    if (!entryFooBar.matches(
                        "com.example.Foo", "bar", new String[] {"int", "String"})) {
                      failures.incrementAndGet();
                    }
                    if (entryFooBar.matches(
                        "com.example.Foo", "bar", new String[] {"int", "int"})) {
                      failures.incrementAndGet();
                    }
                    if (entryFooBar.matches("com.example.Foo", "bar", new String[] {"int"})) {
                      failures.incrementAndGet();
                    }

                    // entryWildcard: com.example.*.process(long)
                    if (!entryWildcard.matches(
                        "com.example.Handler", "process", new String[] {"long"})) {
                      failures.incrementAndGet();
                    }
                    if (entryWildcard.matches(
                        "com.example.Handler", "process", new String[] {"int"})) {
                      failures.incrementAndGet();
                    }

                    // entryNoArgs: com.example.Foo.bar()
                    if (!entryNoArgs.matches("com.example.Foo", "bar", new String[0])) {
                      failures.incrementAndGet();
                    }
                    if (entryNoArgs.matches("com.example.Foo", "bar", new String[] {"int"})) {
                      failures.incrementAndGet();
                    }

                    // Optimized overload: matches(String, String)
                    if (!entryFooBar.matches("com.example.Foo.bar", "int,String")) {
                      failures.incrementAndGet();
                    }
                    if (entryFooBar.matches("com.example.Foo.bar", "int,int")) {
                      failures.incrementAndGet();
                    }
                    if (!entryNoArgs.matches("com.example.Foo.bar", "")) {
                      failures.incrementAndGet();
                    }
                    if (entryNoArgs.matches("com.example.Foo.bar", "int")) {
                      failures.incrementAndGet();
                    }
                  }
                } catch (Exception e) {
                  failures.incrementAndGet();
                } finally {
                  done.countDown();
                }
              });
    }

    done.await();
    assertEquals(
        "Expected zero failures across "
            + THREAD_COUNT
            + " threads with InterceptRequestEntry.matches()",
        0,
        failures.get());
  }
}
