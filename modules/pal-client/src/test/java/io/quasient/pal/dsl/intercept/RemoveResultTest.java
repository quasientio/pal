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

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for the {@code RemoveResult} value class.
 *
 * <p>These test stubs define the contract for {@code RemoveResult}. Each test documents expected
 * behavior via Given/When/Then comments. Implementation will be provided in #1233.
 */
public class RemoveResultTest {

  @Test
  @Ignore("Awaiting implementation in #1233")
  public void countsAreCorrect() {
    // Given: A RemoveResult with 3 removed and 1 notFound entries
    // When: getRemovedCount() and getNotFoundCount() are called
    // Then: They return 3 and 1 respectively

    // TODO(#1233): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1233")
  public void emptyResult() {
    // Given: An empty RemoveResult (no entries)
    // When: getRemovedCount() and getNotFoundCount() are called
    // Then: Both counts return 0

    // TODO(#1233): Implement test logic
    fail("Not yet implemented");
  }
}
