/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.runtime;

import jakarta.inject.Inject;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Facilitates the decoupling of dispatching mechanisms from aspect implementations by forwarding
 * dispatch calls through a ProxyDispatcher interface.
 *
 * <p>This class serves as an intermediary between AspectProxyDispatcher and the core Dispatcher
 * classes, ensuring that the aspects module only depends on the core-api module. By delegating
 * dispatch operations to the injected ProxyDispatcher, it avoids direct dependencies and prevents
 * circular dependency issues at runtime.
 *
 * <p>All dispatch methods are static and delegate to the configured ProxyDispatcher instance.
 */
public final class DispatchForwarder {

  /**
   * The shared ProxyDispatcher used to forward dispatch calls. This dispatcher is injected and
   * facilitates the decoupling between dispatching mechanisms and aspect implementations.
   */
  @Inject private static ProxyDispatcher dispatcher;

  /** Private constructor to prevent instantiation of the DispatchForwarder class. */
  private DispatchForwarder() {}

  /**
   * Dispatches a constructor call with the given join point.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the newly created object instance
   * @throws Throwable if dispatching the constructor fails
   */
  public static Object constructor(ProceedingJoinPoint pjp) throws Throwable {
    return dispatcher.constructor(pjp);
  }

  /**
   * Dispatches a void instance method call with the given join point.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if dispatching the method call fails
   */
  public static void voidInstanceMethod(ProceedingJoinPoint pjp) throws Throwable {
    dispatcher.voidInstanceMethod(pjp);
  }

  /**
   * Dispatches a void static class method call with the given join point.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if dispatching the method call fails
   */
  public static void voidClassMethod(ProceedingJoinPoint pjp) throws Throwable {
    dispatcher.voidClassMethod(pjp);
  }

  /**
   * Dispatches a non-void instance method call with the given join point.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the result of the method invocation
   * @throws Throwable if dispatching the method call fails
   */
  public static Object nonVoidInstanceMethod(ProceedingJoinPoint pjp) throws Throwable {
    return dispatcher.nonVoidInstanceMethod(pjp);
  }

  /**
   * Dispatches a non-void static class method call with the given join point.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the result of the method invocation
   * @throws Throwable if dispatching the method call fails
   */
  public static Object nonVoidClassMethod(ProceedingJoinPoint pjp) throws Throwable {
    return dispatcher.nonVoidClassMethod(pjp);
  }

  /**
   * Dispatches a static field retrieval with the given join point.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the value of the static field
   */
  public static Object getStatic(ProceedingJoinPoint pjp) {
    try {
      return dispatcher.getStatic(pjp);
    } catch (Throwable e) {
      throw rethrowPreservingType(e);
    }
  }

  /**
   * Dispatches an instance field retrieval with the given join point.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the value of the instance field
   */
  public static Object getObject(ProceedingJoinPoint pjp) {
    try {
      return dispatcher.getObject(pjp);
    } catch (Throwable e) {
      throw rethrowPreservingType(e);
    }
  }

  /**
   * Dispatches a static field assignment with the given join point.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   */
  public static void putStatic(ProceedingJoinPoint pjp) {
    try {
      dispatcher.putStatic(pjp);
    } catch (Throwable e) {
      throw rethrowPreservingType(e);
    }
  }

  /**
   * Dispatches an instance field assignment with the given join point.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   */
  public static void putField(ProceedingJoinPoint pjp) {
    try {
      dispatcher.putField(pjp);
    } catch (Throwable e) {
      throw rethrowPreservingType(e);
    }
  }

  /**
   * Rethrows an exception while preserving its original type.
   *
   * <p>For RuntimeException and Error, the original exception is rethrown directly. For checked
   * exceptions, they are wrapped in RuntimeException. This preserves exception types for intercept
   * callbacks that throw unchecked exceptions.
   *
   * @param e the exception to rethrow
   * @return never returns normally (always throws)
   */
  private static RuntimeException rethrowPreservingType(Throwable e) {
    if (e instanceof RuntimeException rex) {
      throw rex;
    }
    if (e instanceof Error err) {
      throw err;
    }
    // Checked exception - wrap in RuntimeException but preserve as cause
    throw new RuntimeException(e);
  }
}
