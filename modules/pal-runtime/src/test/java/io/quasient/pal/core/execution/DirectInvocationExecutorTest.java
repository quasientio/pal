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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
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
  public void executesCallableDirectly() throws Exception {
    DirectInvocationExecutor executor = new DirectInvocationExecutor();
    Thread callingThread = Thread.currentThread();

    Object result =
        executor.execute(
            () -> {
              assertThat(Thread.currentThread(), is(sameInstance(callingThread)));
              return "hello";
            });

    assertThat(result, is("hello"));
  }

  /**
   * Tests that runtime exceptions thrown by the callable are propagated without wrapping.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test(expected = RuntimeException.class)
  public void propagatesException() throws Exception {
    DirectInvocationExecutor executor = new DirectInvocationExecutor();
    executor.execute(
        () -> {
          throw new RuntimeException("test error");
        });
  }

  /**
   * Tests that checked exceptions thrown by the callable are propagated without wrapping.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test(expected = IOException.class)
  public void propagatesCheckedExceptions() throws Exception {
    DirectInvocationExecutor executor = new DirectInvocationExecutor();
    executor.execute(
        () -> {
          throw new IOException("test io error");
        });
  }
}
