/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for {@code DirectInvocationExecutor}, the default executor that invokes callables directly
 * on the calling thread with zero overhead.
 *
 * <p>Verifies that the executor correctly returns values and propagates both runtime and checked
 * exceptions without wrapping.
 */
public class DirectInvocationExecutorTest {

  /**
   * Tests that the executor invokes the callable directly and returns its result.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test
  @Ignore("Awaiting implementation in #739")
  public void executesCallableDirectly() throws Exception {
    // Given: A DirectInvocationExecutor instance and a Callable returning "hello"
    // When: execute(callable) is called
    // Then: Returns "hello", executes on the calling thread

    // TODO(#739): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that runtime exceptions thrown by the callable are propagated without wrapping.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test
  @Ignore("Awaiting implementation in #739")
  public void propagatesException() throws Exception {
    // Given: A DirectInvocationExecutor and a Callable that throws RuntimeException
    // When: execute(callable) is called
    // Then: RuntimeException is propagated directly

    // TODO(#739): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Tests that checked exceptions thrown by the callable are propagated without wrapping.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test
  @Ignore("Awaiting implementation in #739")
  public void propagatesCheckedExceptions() throws Exception {
    // Given: A DirectInvocationExecutor and a Callable that throws IOException
    // When: execute(callable) is called
    // Then: IOException is propagated directly

    // TODO(#739): Implement test logic
    fail("Not yet implemented");
  }
}
