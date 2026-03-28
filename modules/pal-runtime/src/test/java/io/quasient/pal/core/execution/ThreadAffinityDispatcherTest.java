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
package io.quasient.pal.core.execution;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

/**
 * Tests for {@code ThreadAffinityDispatcher}, which routes invocations to registered executors
 * based on thread affinity key.
 *
 * <p>Verifies routing behavior for null/empty affinity (direct execution), registered affinities,
 * unknown affinities (fallback), registration validation, and exception propagation.
 */
public class ThreadAffinityDispatcherTest {

  /**
   * Tests that a {@code null} thread affinity causes direct execution without going through any
   * registered executor.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test
  public void nullAffinityUsesDirectExecution() throws Exception {
    ThreadAffinityDispatcher dispatcher = new ThreadAffinityDispatcher();
    AtomicBoolean customExecutorUsed = new AtomicBoolean(false);
    dispatcher.register(
        "fx-thread",
        invocation -> {
          customExecutorUsed.set(true);
          return invocation.call();
        });

    Object result = dispatcher.execute(null, () -> "direct");

    assertThat(result, is("direct"));
    assertThat(customExecutorUsed.get(), is(false));
  }

  /**
   * Tests that an empty string thread affinity causes direct execution without going through any
   * registered executor.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test
  public void emptyAffinityUsesDirectExecution() throws Exception {
    ThreadAffinityDispatcher dispatcher = new ThreadAffinityDispatcher();
    AtomicBoolean customExecutorUsed = new AtomicBoolean(false);
    dispatcher.register(
        "fx-thread",
        invocation -> {
          customExecutorUsed.set(true);
          return invocation.call();
        });

    Object result = dispatcher.execute("", () -> "direct");

    assertThat(result, is("direct"));
    assertThat(customExecutorUsed.get(), is(false));
  }

  /**
   * Tests that a registered affinity key routes execution through the corresponding executor.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test
  public void registeredAffinityRoutesToExecutor() throws Exception {
    ThreadAffinityDispatcher dispatcher = new ThreadAffinityDispatcher();
    AtomicBoolean customExecutorUsed = new AtomicBoolean(false);
    dispatcher.register(
        "fx-thread",
        invocation -> {
          customExecutorUsed.set(true);
          return invocation.call();
        });

    Object result = dispatcher.execute("fx-thread", () -> "routed");

    assertThat(result, is("routed"));
    assertThat(customExecutorUsed.get(), is(true));
  }

  /**
   * Tests that an unknown affinity key (no matching executor registered) falls back to direct
   * execution and logs a warning.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test
  public void unknownAffinityFallsBackToDirectExecution() throws Exception {
    ThreadAffinityDispatcher dispatcher = new ThreadAffinityDispatcher();

    Object result = dispatcher.execute("unknown-affinity", () -> "fallback");

    assertThat(result, is("fallback"));
  }

  /**
   * Tests that registering an executor with a {@code null} key throws {@link NullPointerException}.
   */
  @Test(expected = NullPointerException.class)
  public void registerRejectsNullKey() {
    ThreadAffinityDispatcher dispatcher = new ThreadAffinityDispatcher();
    dispatcher.register(null, invocation -> invocation.call());
  }

  /** Tests that registering a {@code null} executor throws {@link NullPointerException}. */
  @Test(expected = NullPointerException.class)
  public void registerRejectsNullExecutor() {
    ThreadAffinityDispatcher dispatcher = new ThreadAffinityDispatcher();
    dispatcher.register("fx-thread", null);
  }

  /**
   * Tests that {@code hasExecutor()} returns {@code true} for registered keys and {@code false} for
   * unregistered keys.
   */
  @Test
  public void hasExecutorReturnsCorrectly() {
    ThreadAffinityDispatcher dispatcher = new ThreadAffinityDispatcher();
    dispatcher.register("fx-thread", invocation -> invocation.call());

    assertThat(dispatcher.hasExecutor("fx-thread"), is(true));
    assertThat(dispatcher.hasExecutor("other"), is(false));
  }

  /**
   * Tests that exceptions thrown by a registered executor are propagated to the caller.
   *
   * @throws Exception if invocation fails unexpectedly
   */
  @Test(expected = RuntimeException.class)
  public void propagatesExceptionFromExecutor() throws Exception {
    ThreadAffinityDispatcher dispatcher = new ThreadAffinityDispatcher();
    dispatcher.register(
        "fx-thread",
        invocation -> {
          throw new RuntimeException("executor error");
        });

    dispatcher.execute("fx-thread", () -> "should not reach");
  }
}
