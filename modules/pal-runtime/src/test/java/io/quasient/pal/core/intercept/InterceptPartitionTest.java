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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Unit tests for the {@code InterceptPartition} class that replaces four stream-based filter
 * methods in {@code BaseExecMessageDispatcher} with a single-pass partitioning approach using
 * pre-allocated thread-local lists.
 *
 * <p>The {@code InterceptPartition} class eliminates ~12 allocations per dispatch by replacing four
 * separate {@code stream().filter().toList()} calls with a single iteration that partitions
 * intercepts into five categories: BEFORE, BEFORE_ASYNC, AFTER, AFTER_ASYNC, and AROUND.
 *
 * <p>These tests define the contract for {@code InterceptPartition} and validate correctness across
 * single-threaded reuse, multi-threaded isolation, and equivalence with the original stream-based
 * filtering approach.
 *
 * @see InterceptType
 */
public class InterceptPartitionTest {

  /** Shared message builder for constructing intercept messages. */
  private final MessageBuilder msgBuilder = new MessageBuilder();

  /** Shared peer UUID for test intercept messages. */
  private final UUID peerUuid = UUID.randomUUID();

  /**
   * Creates an {@link InterceptMessage} with the given intercept type for testing.
   *
   * @param type the intercept type to assign
   * @return a new InterceptMessage configured with the specified type
   */
  private InterceptMessage buildIntercept(InterceptType type) {
    return msgBuilder.buildInterceptMessage(
        peerUuid, type, "com.example.Foo", "bar", List.of(), "callback.Class", "callbackMethod");
  }

  /**
   * Verifies that partitioning an empty list produces empty sub-lists for all intercept types.
   *
   * <p>This is the boundary condition: no intercepts registered means no work to do, and the
   * partition must reflect that cleanly.
   */
  @Test
  public void shouldPartitionEmptyList() {
    InterceptPartition partition = new InterceptPartition();
    partition.partition(List.of());

    assertThat(partition.before(), hasSize(0));
    assertThat(partition.beforeAsync(), hasSize(0));
    assertThat(partition.after(), hasSize(0));
    assertThat(partition.afterAsync(), hasSize(0));
    assertThat(partition.around(), hasSize(0));
  }

  /**
   * Verifies that a list containing a single BEFORE intercept is partitioned correctly, with only
   * the {@code before} sub-list containing the element and all other sub-lists remaining empty.
   */
  @Test
  public void shouldPartitionSingleBeforeIntercept() {
    InterceptMessage beforeMsg = buildIntercept(InterceptType.BEFORE);
    InterceptPartition partition = new InterceptPartition();
    partition.partition(List.of(beforeMsg));

    assertThat(partition.before(), hasSize(1));
    assertThat(partition.before().get(0), is(sameInstance(beforeMsg)));
    assertThat(partition.beforeAsync(), hasSize(0));
    assertThat(partition.after(), hasSize(0));
    assertThat(partition.afterAsync(), hasSize(0));
    assertThat(partition.around(), hasSize(0));
  }

  /**
   * Verifies correct partitioning of a mixed list containing all five intercept types.
   *
   * <p>The input list contains 2 BEFORE, 1 AFTER, 1 BEFORE_ASYNC, 3 AFTER_ASYNC, and 2 AROUND
   * intercepts. After partitioning, each sub-list must have the correct count and contain the
   * correct elements (same object references, preserving insertion order within each partition).
   */
  @Test
  public void shouldPartitionMixedInterceptTypes() {
    InterceptMessage before1 = buildIntercept(InterceptType.BEFORE);
    InterceptMessage before2 = buildIntercept(InterceptType.BEFORE);
    InterceptMessage after1 = buildIntercept(InterceptType.AFTER);
    InterceptMessage beforeAsync1 = buildIntercept(InterceptType.BEFORE_ASYNC);
    InterceptMessage afterAsync1 = buildIntercept(InterceptType.AFTER_ASYNC);
    InterceptMessage afterAsync2 = buildIntercept(InterceptType.AFTER_ASYNC);
    InterceptMessage afterAsync3 = buildIntercept(InterceptType.AFTER_ASYNC);
    InterceptMessage around1 = buildIntercept(InterceptType.AROUND);
    InterceptMessage around2 = buildIntercept(InterceptType.AROUND);

    List<InterceptMessage> all =
        List.of(
            before1,
            afterAsync1,
            around1,
            before2,
            after1,
            beforeAsync1,
            afterAsync2,
            around2,
            afterAsync3);

    InterceptPartition partition = new InterceptPartition();
    partition.partition(all);

    assertThat(partition.before(), hasSize(2));
    assertThat(partition.before().get(0), is(sameInstance(before1)));
    assertThat(partition.before().get(1), is(sameInstance(before2)));

    assertThat(partition.after(), hasSize(1));
    assertThat(partition.after().get(0), is(sameInstance(after1)));

    assertThat(partition.beforeAsync(), hasSize(1));
    assertThat(partition.beforeAsync().get(0), is(sameInstance(beforeAsync1)));

    assertThat(partition.afterAsync(), hasSize(3));
    assertThat(partition.afterAsync().get(0), is(sameInstance(afterAsync1)));
    assertThat(partition.afterAsync().get(1), is(sameInstance(afterAsync2)));
    assertThat(partition.afterAsync().get(2), is(sameInstance(afterAsync3)));

    assertThat(partition.around(), hasSize(2));
    assertThat(partition.around().get(0), is(sameInstance(around1)));
    assertThat(partition.around().get(1), is(sameInstance(around2)));
  }

  /**
   * Verifies that reusing an {@code InterceptPartition} instance clears previous state.
   *
   * <p>After a first partition() call populates all sub-lists, a second call with a different input
   * list must produce results reflecting only the new input — no stale elements from the previous
   * partitioning should remain.
   */
  @Test
  public void shouldClearOnReuse() {
    InterceptPartition partition = new InterceptPartition();

    // First partition: all five types
    partition.partition(
        List.of(
            buildIntercept(InterceptType.BEFORE),
            buildIntercept(InterceptType.AFTER),
            buildIntercept(InterceptType.BEFORE_ASYNC),
            buildIntercept(InterceptType.AFTER_ASYNC),
            buildIntercept(InterceptType.AROUND)));

    assertThat(partition.before(), hasSize(1));
    assertThat(partition.after(), hasSize(1));
    assertThat(partition.beforeAsync(), hasSize(1));
    assertThat(partition.afterAsync(), hasSize(1));
    assertThat(partition.around(), hasSize(1));

    // Second partition: only AFTER
    InterceptMessage afterOnly = buildIntercept(InterceptType.AFTER);
    partition.partition(List.of(afterOnly));

    assertThat(partition.before(), hasSize(0));
    assertThat(partition.after(), hasSize(1));
    assertThat(partition.after().get(0), is(sameInstance(afterOnly)));
    assertThat(partition.beforeAsync(), hasSize(0));
    assertThat(partition.afterAsync(), hasSize(0));
    assertThat(partition.around(), hasSize(0));
  }

  /**
   * Verifies that a {@code ThreadLocal<InterceptPartition>} returns the same instance on repeated
   * access from the same thread, and that the results are always correct after each partition()
   * call.
   *
   * <p>This validates the intended usage pattern where a thread-local partition object is reused
   * across multiple dispatch cycles without creating new instances.
   */
  @Test
  public void shouldBeReusableViaThreadLocal() {
    ThreadLocal<InterceptPartition> tl = ThreadLocal.withInitial(InterceptPartition::new);

    InterceptPartition first = tl.get();
    InterceptPartition second = tl.get();
    assertThat(second, is(sameInstance(first)));

    // First use
    first.partition(List.of(buildIntercept(InterceptType.BEFORE)));
    assertThat(first.before(), hasSize(1));
    assertThat(first.after(), hasSize(0));

    // Reuse same instance
    second.partition(
        List.of(buildIntercept(InterceptType.AFTER), buildIntercept(InterceptType.AFTER)));
    assertThat(second.before(), hasSize(0));
    assertThat(second.after(), hasSize(2));
  }

  /**
   * Verifies thread isolation when 16 threads concurrently partition different lists using
   * thread-local {@code InterceptPartition} instances.
   *
   * <p>Each thread creates a unique input list with a known distribution of intercept types and
   * partitions it independently. After all threads complete, each thread's results must match the
   * expected partition for its specific input — no cross-thread interference should occur.
   *
   * <p>A {@link CyclicBarrier} is used to maximize contention by ensuring all threads start
   * partitioning simultaneously.
   */
  @Test
  public void shouldHandleConcurrentPartitioningOnDifferentThreads() throws Exception {
    int threadCount = 16;
    CyclicBarrier barrier = new CyclicBarrier(threadCount);
    ThreadLocal<InterceptPartition> tl = ThreadLocal.withInitial(InterceptPartition::new);
    AtomicReference<AssertionError> failure = new AtomicReference<>();

    Thread[] threads = new Thread[threadCount];
    for (int t = 0; t < threadCount; t++) {
      final int threadIdx = t;
      threads[t] =
          new Thread(
              () -> {
                try {
                  // Each thread builds a unique distribution:
                  // thread i gets (i+1) BEFORE messages and 1 AFTER message
                  List<InterceptMessage> input = new ArrayList<>();
                  for (int b = 0; b <= threadIdx; b++) {
                    input.add(buildIntercept(InterceptType.BEFORE));
                  }
                  input.add(buildIntercept(InterceptType.AFTER));

                  barrier.await();

                  InterceptPartition partition = tl.get();
                  partition.partition(input);

                  assertThat(partition.before(), hasSize(threadIdx + 1));
                  assertThat(partition.after(), hasSize(1));
                  assertThat(partition.beforeAsync(), hasSize(0));
                  assertThat(partition.afterAsync(), hasSize(0));
                  assertThat(partition.around(), hasSize(0));
                } catch (AssertionError e) {
                  failure.compareAndSet(null, e);
                } catch (Exception e) {
                  failure.compareAndSet(null, new AssertionError("Thread exception", e));
                }
              });
      threads[t].start();
    }

    for (Thread thread : threads) {
      thread.join(5000);
    }

    if (failure.get() != null) {
      throw failure.get();
    }
  }

  /**
   * Verifies that the single-pass partition approach produces results identical to the original
   * stream-based filtering used in {@code BaseExecMessageDispatcher}.
   *
   * <p>This test applies both approaches to the same mixed list of intercepts and compares the
   * results element-by-element. The stream-based approach serves as the reference implementation:
   *
   * <pre>{@code
   * // Original stream-based filtering (reference):
   * intercepts.stream()
   *     .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.BEFORE)
   *     .toList();
   * }</pre>
   *
   * <p>The partition-based approach must produce identical lists for all five intercept types.
   */
  @Test
  public void shouldProduceResultsEquivalentToStreamFiltering() {
    List<InterceptMessage> mixed =
        List.of(
            buildIntercept(InterceptType.AROUND),
            buildIntercept(InterceptType.BEFORE),
            buildIntercept(InterceptType.AFTER_ASYNC),
            buildIntercept(InterceptType.BEFORE_ASYNC),
            buildIntercept(InterceptType.AFTER),
            buildIntercept(InterceptType.BEFORE),
            buildIntercept(InterceptType.AROUND),
            buildIntercept(InterceptType.AFTER_ASYNC));

    // Reference: original stream-based filtering
    List<InterceptMessage> refBefore =
        mixed.stream()
            .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.BEFORE)
            .toList();
    List<InterceptMessage> refAfter =
        mixed.stream()
            .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AFTER)
            .toList();
    List<InterceptMessage> refBeforeAsync =
        mixed.stream()
            .filter(
                im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.BEFORE_ASYNC)
            .toList();
    List<InterceptMessage> refAfterAsync =
        mixed.stream()
            .filter(
                im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AFTER_ASYNC)
            .toList();
    List<InterceptMessage> refAround =
        mixed.stream()
            .filter(im -> InterceptType.fromByte(im.getInterceptType()) == InterceptType.AROUND)
            .toList();

    // Partition approach
    InterceptPartition partition = new InterceptPartition();
    partition.partition(mixed);

    assertThat(partition.before(), is(refBefore));
    assertThat(partition.after(), is(refAfter));
    assertThat(partition.beforeAsync(), is(refBeforeAsync));
    assertThat(partition.afterAsync(), is(refAfterAsync));
    assertThat(partition.around(), is(refAround));
  }
}
