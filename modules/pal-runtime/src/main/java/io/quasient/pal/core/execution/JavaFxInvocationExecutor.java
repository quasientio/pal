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

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executor that marshals invocations onto the JavaFX Application Thread via {@code
 * Platform.runLater()}, blocking the calling (invoker) thread until the FX thread completes the
 * operation.
 *
 * <p>Uses reflection exclusively to access JavaFX classes at runtime, avoiding a compile-time
 * dependency on the JavaFX SDK. The constructor loads {@code javafx.application.Platform} via
 * {@link Class#forName(String)} and caches the {@code runLater} and {@code isFxApplicationThread}
 * method references.
 *
 * <p>When already on the FX Application Thread, the callable is invoked directly without using
 * {@code Platform.runLater()} to avoid self-deadlock.
 */
public final class JavaFxInvocationExecutor implements InvocationExecutor {

  /** Default timeout in milliseconds for waiting on FX thread completion. */
  public static final long DEFAULT_TIMEOUT_MS = 30_000L;

  /** Cached reference to {@code Platform.runLater(Runnable)}. */
  private final Method runLaterMethod;

  /** Cached reference to {@code Platform.isFxApplicationThread()}. */
  private final Method isFxApplicationThreadMethod;

  /** Maximum time in milliseconds to wait for FX thread completion. */
  private final long timeoutMs;

  /**
   * Creates a new executor with the default timeout of 30 seconds, using the current thread's
   * context classloader.
   *
   * @throws IllegalStateException if JavaFX is not on the classpath
   */
  public JavaFxInvocationExecutor() {
    this(DEFAULT_TIMEOUT_MS, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a new executor with the specified timeout, using the current thread's context
   * classloader.
   *
   * @param timeoutMs the maximum time in milliseconds to wait for FX thread completion
   * @throws IllegalStateException if JavaFX is not on the classpath
   */
  public JavaFxInvocationExecutor(long timeoutMs) {
    this(timeoutMs, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a new executor with the specified timeout and classloader.
   *
   * @param timeoutMs the maximum time in milliseconds to wait for FX thread completion
   * @param classLoader the classloader to use for loading JavaFX classes
   * @throws IllegalStateException if JavaFX is not on the classpath
   */
  public JavaFxInvocationExecutor(long timeoutMs, ClassLoader classLoader) {
    this.timeoutMs = timeoutMs;
    try {
      Class<?> platform = Class.forName("javafx.application.Platform", true, classLoader);
      this.runLaterMethod = platform.getMethod("runLater", Runnable.class);
      this.isFxApplicationThreadMethod = platform.getMethod("isFxApplicationThread");
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(
          "JavaFX not on classpath. Cannot use --fx-thread without JavaFX runtime.", e);
    }
  }

  @Override
  public Object execute(Callable<Object> invocation) throws Exception {
    if ((boolean) isFxApplicationThreadMethod.invoke(null)) {
      return invocation.call();
    }
    CompletableFuture<Object> future = new CompletableFuture<>();
    Runnable task =
        () -> {
          try {
            future.complete(invocation.call());
          } catch (Throwable t) {
            future.completeExceptionally(t);
          }
        };
    runLaterMethod.invoke(null, task);
    try {
      return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof Exception ex) {
        throw ex;
      }
      throw new RuntimeException(cause);
    } catch (TimeoutException e) {
      throw new RuntimeException("JavaFX thread execution timed out after " + timeoutMs + "ms", e);
    }
  }
}
