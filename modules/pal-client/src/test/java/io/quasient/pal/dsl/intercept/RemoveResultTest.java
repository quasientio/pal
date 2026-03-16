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

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for the {@code RemoveResult} value class.
 *
 * <p>These tests define the contract for {@code RemoveResult}. Each test documents expected
 * behavior via Given/When/Then comments.
 */
public class RemoveResultTest {

  @Test
  public void countsAreCorrect() {
    // Given: A RemoveResult with 3 removed and 1 notFound entries
    RemoveResult result =
        new RemoveResult(
            Arrays.asList(
                new RemoveResult.Entry(UUID.randomUUID(), RemoveResult.Status.REMOVED),
                new RemoveResult.Entry(UUID.randomUUID(), RemoveResult.Status.REMOVED),
                new RemoveResult.Entry(UUID.randomUUID(), RemoveResult.Status.REMOVED),
                new RemoveResult.Entry(UUID.randomUUID(), RemoveResult.Status.NOT_FOUND)));

    // When/Then: counts match
    assertThat(result.getRemovedCount(), is(3L));
    assertThat(result.getNotFoundCount(), is(1L));
  }

  @Test
  public void emptyResult() {
    // Given: An empty RemoveResult (no entries)
    RemoveResult result = new RemoveResult(Collections.emptyList());

    // When/Then: both counts return 0
    assertThat(result.getRemovedCount(), is(0L));
    assertThat(result.getNotFoundCount(), is(0L));
  }
}
