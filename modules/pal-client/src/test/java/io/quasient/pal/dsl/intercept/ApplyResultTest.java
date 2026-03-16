/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.intercept;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.common.lang.intercept.InterceptType;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for the {@code ApplyResult} value class.
 *
 * <p>These tests define the contract for {@code ApplyResult}. Each test documents expected behavior
 * via Given/When/Then comments.
 */
public class ApplyResultTest {

  @Test
  public void countsAreCorrect() {
    // Given: An ApplyResult with 2 created, 1 skipped, and 0 failed entries
    InterceptSpec spec = makeSpec();

    ApplyResult result =
        new ApplyResult(
            Arrays.asList(
                new ApplyResult.Entry(spec, UUID.randomUUID(), ApplyResult.Status.CREATED, null),
                new ApplyResult.Entry(spec, UUID.randomUUID(), ApplyResult.Status.CREATED, null),
                new ApplyResult.Entry(spec, UUID.randomUUID(), ApplyResult.Status.SKIPPED, null)));

    // When/Then: counts match
    assertThat(result.getCreatedCount(), is(2L));
    assertThat(result.getSkippedCount(), is(1L));
    assertThat(result.getFailedCount(), is(0L));
  }

  @Test
  public void entriesAreAccessible() {
    // Given: An ApplyResult with per-intercept detail entries
    InterceptSpec spec = makeSpec();
    UUID uuid1 = UUID.randomUUID();
    UUID uuid2 = UUID.randomUUID();

    ApplyResult result =
        new ApplyResult(
            Arrays.asList(
                new ApplyResult.Entry(spec, uuid1, ApplyResult.Status.CREATED, null),
                new ApplyResult.Entry(spec, uuid2, ApplyResult.Status.FAILED, "timeout")));

    // When/Then: each entry is accessible with correct status and info
    assertThat(result.getEntries().size(), is(2));
    assertThat(result.getEntries().get(0).getUuid(), is(uuid1));
    assertThat(result.getEntries().get(0).getStatus(), is(ApplyResult.Status.CREATED));
    assertThat(result.getEntries().get(1).getUuid(), is(uuid2));
    assertThat(result.getEntries().get(1).getStatus(), is(ApplyResult.Status.FAILED));
    assertThat(result.getEntries().get(1).getErrorMessage(), is("timeout"));
    assertThat(result.getEntries().get(0).getInterceptSpec(), is(spec));
  }

  @Test
  public void emptyResult() {
    // Given: An empty ApplyResult (no entries)
    ApplyResult result = new ApplyResult(Collections.emptyList());

    // When/Then: all counts return 0
    assertThat(result.getCreatedCount(), is(0L));
    assertThat(result.getSkippedCount(), is(0L));
    assertThat(result.getFailedCount(), is(0L));
  }

  /**
   * Creates a minimal InterceptSpec for testing.
   *
   * @return a test InterceptSpec
   */
  private static InterceptSpec makeSpec() {
    return InterceptSpec.builder()
        .targetClass("com.acme.Foo")
        .targetName("bar")
        .type(InterceptType.BEFORE)
        .callbackClass("com.acme.Cb")
        .callbackMethod("onBar")
        .build();
  }
}
