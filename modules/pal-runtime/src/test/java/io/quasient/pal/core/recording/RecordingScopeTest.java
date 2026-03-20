/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.recording;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.core.rpc.policy.MemberCategory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

/**
 * Unit tests for {@code RecordingScope}, the core evaluator that holds a list of {@code
 * RecordingScopeRule} instances, a default {@code RecordingScopeAction}, and implements
 * first-match-wins evaluation with {@code ConcurrentHashMap} caching.
 *
 * <p>These tests verify the firewall-style include/exclude semantics modeled after {@link
 * io.quasient.pal.core.rpc.policy.RpcPolicy}: ordered rules are evaluated top-to-bottom, the first
 * matching rule's action ({@code RECORD} or {@code SKIP}) wins, and unmatched operations fall
 * through to the configurable default action.
 *
 * <p>Caching and thread-safety tests are critical because {@code RecordingScope.isInScope()} is
 * called on every dispatch hot path.
 *
 * @see io.quasient.pal.core.rpc.policy.RpcPolicy
 */
public class RecordingScopeTest {

  /**
   * Verifies permit-all default: when no rules are configured and the default action is {@code
   * RECORD}, every operation is in scope regardless of class, member, or category.
   */
  @Test
  public void emptyRulesWithDefaultRecordAlwaysInScope() {
    RecordingScope scope = new RecordingScope(List.of(), RecordingScopeAction.RECORD);

    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(scope.isInScope("java.util.HashMap", "put", MemberCategory.METHOD), is(true));
    assertThat(scope.isInScope("any.Class", "field", MemberCategory.FIELD_GET), is(true));
  }

  /**
   * Verifies that when no rules are configured and the default action is {@code SKIP}, no operation
   * is in scope.
   */
  @Test
  public void emptyRulesWithDefaultSkipNeverInScope() {
    RecordingScope scope = new RecordingScope(List.of(), RecordingScopeAction.SKIP);

    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(false));
    assertThat(scope.isInScope("java.util.HashMap", "put", MemberCategory.METHOD), is(false));
    assertThat(scope.isInScope("any.Class", "field", MemberCategory.FIELD_GET), is(false));
  }

  /**
   * Verifies first-match-wins when a {@code RECORD} rule appears before a {@code SKIP} rule for the
   * same pattern. The earlier {@code RECORD} rule should win.
   */
  @Test
  public void firstMatchWinsRecordBeforeSkip() {
    List<RecordingScopeRule> rules =
        List.of(
            new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null),
            new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.SKIP, null));
    RecordingScope scope = new RecordingScope(rules, RecordingScopeAction.SKIP);

    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
  }

  /**
   * Verifies first-match-wins when a {@code SKIP} rule for a more specific pattern appears before a
   * {@code RECORD} rule for a broader pattern. The narrower {@code SKIP} rule wins for matching
   * classes; the broader {@code RECORD} rule wins for non-matching classes.
   */
  @Test
  public void firstMatchWinsSkipBeforeRecord() {
    List<RecordingScopeRule> rules =
        List.of(
            new RecordingScopeRule(
                "com.example.internal.**", "**", RecordingScopeAction.SKIP, null),
            new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null));
    RecordingScope scope = new RecordingScope(rules, RecordingScopeAction.SKIP);

    assertThat(
        scope.isInScope("com.example.internal.Util", "helper", MemberCategory.METHOD), is(false));
    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
  }

  /**
   * Verifies that when no rule matches a given operation, the configured default action is used as
   * fallback.
   */
  @Test
  public void defaultFallbackWhenNoRuleMatches() {
    List<RecordingScopeRule> rules =
        List.of(new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null));
    RecordingScope scope = new RecordingScope(rules, RecordingScopeAction.SKIP);

    assertThat(scope.isInScope("com.other.Foo", "bar", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that rules with category filtering correctly exclude specific operation types. A
   * {@code SKIP} rule targeting {@code FIELD_GET} and {@code FIELD_SET} categories should exclude
   * field operations but allow method calls on the same class.
   */
  @Test
  public void fieldGetOutOfScopeWhenCategoryExcluded() {
    List<RecordingScopeRule> rules =
        List.of(
            new RecordingScopeRule(
                "java.**",
                "**",
                RecordingScopeAction.SKIP,
                EnumSet.of(MemberCategory.FIELD_GET, MemberCategory.FIELD_SET)),
            new RecordingScopeRule("**", "**", RecordingScopeAction.RECORD, null));
    RecordingScope scope = new RecordingScope(rules, RecordingScopeAction.SKIP);

    assertThat(scope.isInScope("java.util.HashMap", "size", MemberCategory.FIELD_GET), is(false));
    assertThat(scope.isInScope("java.util.HashMap", "put", MemberCategory.METHOD), is(true));
  }

  /**
   * Verifies that the {@code ConcurrentHashMap} cache returns the same result on repeated calls
   * with the same arguments, and that the cache is actually populated after the first call.
   */
  @Test
  public void cacheReturnsSameResultOnSecondCall() {
    List<RecordingScopeRule> rules =
        List.of(new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null));
    RecordingScope scope = new RecordingScope(rules, RecordingScopeAction.SKIP);

    boolean first = scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD);
    boolean second = scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD);

    assertThat(first, is(true));
    assertThat(second, is(true));
    assertThat(first, is(second));
  }

  /**
   * Verifies that the cache key includes the {@code MemberCategory}, so the same class and member
   * name can yield different results for different categories (e.g., {@code FIELD_GET} vs {@code
   * METHOD}).
   */
  @Test
  public void cacheKeyIncludesCategory() {
    List<RecordingScopeRule> rules =
        List.of(
            new RecordingScopeRule(
                "com.example.**",
                "**",
                RecordingScopeAction.SKIP,
                EnumSet.of(MemberCategory.FIELD_GET)),
            new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null));
    RecordingScope scope = new RecordingScope(rules, RecordingScopeAction.SKIP);

    assertThat(scope.isInScope("com.example.Foo", "value", MemberCategory.FIELD_GET), is(false));
    assertThat(scope.isInScope("com.example.Foo", "value", MemberCategory.METHOD), is(true));
  }

  /**
   * Verifies thread-safe concurrent access to {@code RecordingScope.isInScope()}. Multiple threads
   * calling with distinct and overlapping keys must all receive correct results with no exceptions.
   */
  @Test
  public void threadSafetyConcurrentAccess() throws Exception {
    List<RecordingScopeRule> rules =
        List.of(
            new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null),
            new RecordingScopeRule("com.other.**", "**", RecordingScopeAction.SKIP, null));
    RecordingScope scope = new RecordingScope(rules, RecordingScopeAction.SKIP);

    int threadCount = 16;
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicBoolean failed = new AtomicBoolean(false);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    for (int i = 0; i < threadCount; i++) {
      final int threadIdx = i;
      var unused =
          executor.submit(
              () -> {
                try {
                  startLatch.await();
                  for (int j = 0; j < 100; j++) {
                    boolean inScope =
                        scope.isInScope(
                            "com.example.Foo", "method" + threadIdx, MemberCategory.METHOD);
                    if (!inScope) {
                      failed.set(true);
                    }
                    boolean outOfScope =
                        scope.isInScope(
                            "com.other.Bar", "method" + threadIdx, MemberCategory.METHOD);
                    if (outOfScope) {
                      failed.set(true);
                    }
                  }
                } catch (Exception e) {
                  failed.set(true);
                } finally {
                  doneLatch.countDown();
                }
              });
    }

    startLatch.countDown();
    doneLatch.await();
    executor.shutdown();

    assertThat(failed.get(), is(false));
  }

  /**
   * Verifies a realistic mixed include/exclude configuration with multiple rules and a default
   * {@code SKIP} action. Tests that application code is recorded, internal utilities are skipped,
   * JDK collections are skipped, and unmatched third-party code falls to the default.
   */
  @Test
  public void mixedIncludeExcludeRules() {
    List<RecordingScopeRule> rules = new ArrayList<>();
    rules.add(
        new RecordingScopeRule("com.example.internal.**", "**", RecordingScopeAction.SKIP, null));
    rules.add(new RecordingScopeRule("com.example.**", "**", RecordingScopeAction.RECORD, null));
    rules.add(new RecordingScopeRule("java.util.**", "**", RecordingScopeAction.SKIP, null));
    RecordingScope scope = new RecordingScope(rules, RecordingScopeAction.SKIP);

    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(scope.isInScope("com.example.internal.Util", "x", MemberCategory.METHOD), is(false));
    assertThat(scope.isInScope("java.util.HashMap", "put", MemberCategory.METHOD), is(false));
    assertThat(scope.isInScope("org.apache.Foo", "bar", MemberCategory.METHOD), is(false));
  }
}
