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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Tests for {@code JavaFxInvocationExecutor}, which marshals invocations onto the JavaFX
 * Application Thread via {@code Platform.runLater()}.
 *
 * <p>These tests require JavaFX on the test classpath. If JavaFX is not available, the constructor
 * test verifies fail-fast behavior. If JavaFX IS available, the other tests verify thread-routing
 * and exception propagation.
 *
 * <p>Note: The executor uses reflection to access JavaFX classes at runtime, avoiding a
 * compile-time dependency on the JavaFX SDK.
 */
public class JavaFxInvocationExecutorTest {

  /**
   * Tests the fail-fast behavior when JavaFX is not on the classpath.
   *
   * <p>If JavaFX IS on the test classpath, this test verifies the constructor succeeds instead.
   *
   * @throws Exception if reflection fails unexpectedly
   */
  @Test
  public void constructorFailsWithoutJavaFxOnClasspath() throws Exception {
    if (isJavaFxAvailable()) {
      // JavaFX is on classpath - verify constructor succeeds
      JavaFxInvocationExecutor executor = new JavaFxInvocationExecutor();
      assertThat(executor != null, is(true));
    } else {
      // JavaFX is not on classpath - verify fail-fast
      try {
        new JavaFxInvocationExecutor();
        fail("Expected IllegalStateException when JavaFX is not on classpath");
      } catch (IllegalStateException e) {
        assertThat(e.getMessage(), containsString("JavaFX not on classpath"));
      }
    }
  }

  /**
   * Tests that the executor routes callable execution to the JavaFX Application Thread.
   *
   * <p>Requires JavaFX toolkit to be initialized (e.g., via {@code Platform.startup(() -> {})}).
   *
   * @throws Exception if invocation or toolkit initialization fails
   */
  @Test
  public void executesOnFxThread() throws Exception {
    assumeTrue("JavaFX not available on test classpath", isJavaFxAvailable());
    initFxToolkit();

    JavaFxInvocationExecutor executor = new JavaFxInvocationExecutor();
    AtomicBoolean onFxThread = new AtomicBoolean(false);

    executor.execute(
        () -> {
          onFxThread.set(isFxApplicationThread());
          return null;
        });

    assertThat(onFxThread.get(), is(true));
  }

  /**
   * Tests that when already on the FX Application Thread, the executor invokes the callable
   * directly without using {@code Platform.runLater()}.
   *
   * <p>Requires JavaFX toolkit to be initialized.
   *
   * @throws Exception if invocation or toolkit initialization fails
   */
  @Test
  public void directExecutionWhenAlreadyOnFxThread() throws Exception {
    assumeTrue("JavaFX not available on test classpath", isJavaFxAvailable());
    initFxToolkit();

    JavaFxInvocationExecutor executor = new JavaFxInvocationExecutor();
    AtomicReference<Object> result = new AtomicReference<>();
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    runOnFxThread(
        () -> {
          try {
            result.set(executor.execute(() -> "from-fx-thread"));
          } catch (Exception e) {
            error.set(e);
          } finally {
            latch.countDown();
          }
        });

    latch.await();
    if (error.get() != null) {
      throw new RuntimeException("Unexpected error on FX thread", error.get());
    }
    assertThat(result.get(), is("from-fx-thread"));
  }

  /**
   * Tests that exceptions thrown by the callable on the FX thread are propagated back to the
   * calling thread.
   *
   * <p>Requires JavaFX toolkit to be initialized.
   *
   * @throws Exception if invocation or toolkit initialization fails
   */
  @Test
  public void propagatesExceptionFromFxThread() throws Exception {
    assumeTrue("JavaFX not available on test classpath", isJavaFxAvailable());
    initFxToolkit();

    JavaFxInvocationExecutor executor = new JavaFxInvocationExecutor();
    try {
      executor.execute(
          () -> {
            throw new RuntimeException("fx error");
          });
      fail("Expected RuntimeException to be propagated");
    } catch (RuntimeException e) {
      assertThat(e.getMessage(), is("fx error"));
    }
  }

  /**
   * Checks whether JavaFX classes are available on the test classpath.
   *
   * @return true if javafx.application.Platform can be loaded
   */
  private static boolean isJavaFxAvailable() {
    try {
      Class.forName("javafx.application.Platform");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Initializes the JavaFX toolkit if not already initialized.
   *
   * @throws Exception if toolkit initialization fails
   */
  private static void initFxToolkit() throws Exception {
    Class<?> platform = Class.forName("javafx.application.Platform");
    try {
      platform.getMethod("startup", Runnable.class).invoke(null, (Runnable) () -> {});
    } catch (Exception e) {
      // Toolkit already initialized - ignore
    }
  }

  /**
   * Checks if the current thread is the FX Application Thread via reflection.
   *
   * @return true if on the FX thread
   */
  private static boolean isFxApplicationThread() {
    try {
      Class<?> platform = Class.forName("javafx.application.Platform");
      return (boolean) platform.getMethod("isFxApplicationThread").invoke(null);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Submits a runnable to the FX Application Thread via reflection.
   *
   * @param task the task to run on the FX thread
   * @throws Exception if reflection or invocation fails
   */
  private static void runOnFxThread(Runnable task) throws Exception {
    Class<?> platform = Class.forName("javafx.application.Platform");
    platform.getMethod("runLater", Runnable.class).invoke(null, task);
  }
}
