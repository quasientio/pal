/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.runtime;

import com.quasient.pal.common.weave.Proceed;
import com.quasient.pal.common.weave.VoidProceed;
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
   * Dispatches a constructor call with the given join point and arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the newly created object instance
   * @throws Throwable if dispatching the constructor fails
   */
  public static Object constructor(ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable {
    return dispatcher.constructor(pjp, proceed);
  }

  /**
   * Dispatches a void instance method call with the given join point and arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if dispatching the method call fails
   */
  public static void voidInstanceMethod(ProceedingJoinPoint pjp, VoidProceed proceed)
      throws Throwable {
    dispatcher.voidInstanceMethod(pjp, proceed);
  }

  /**
   * Dispatches a void static class method call with the given join point and arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if dispatching the method call fails
   */
  public static void voidClassMethod(ProceedingJoinPoint pjp, VoidProceed proceed)
      throws Throwable {
    dispatcher.voidClassMethod(pjp, proceed);
  }

  /**
   * Dispatches a non-void instance method call with the given join point and arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the result of the method invocation
   * @throws Throwable if dispatching the method call fails
   */
  public static Object nonVoidInstanceMethod(ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable {
    return dispatcher.nonVoidInstanceMethod(pjp, proceed);
  }

  /**
   * Dispatches a non-void static class method call with the given join point and arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the result of the method invocation
   * @throws Throwable if dispatching the method call fails
   */
  public static Object nonVoidClassMethod(ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable {
    return dispatcher.nonVoidClassMethod(pjp, proceed);
  }

  /**
   * Dispatches a static field retrieval with the given join point and arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the value of the static field
   */
  public static Object getStatic(ProceedingJoinPoint pjp, Proceed<Object> proceed) {
    try {
      return dispatcher.getStatic(pjp, proceed);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Dispatches an instance field retrieval with the given join point and arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the value of the instance field
   */
  public static Object getObject(ProceedingJoinPoint pjp, Proceed<Object> proceed) {
    try {
      return dispatcher.getObject(pjp, proceed);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Dispatches a static field assignment with the given join point and arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   */
  public static void putStatic(ProceedingJoinPoint pjp, VoidProceed proceed) {
    try {
      dispatcher.putStatic(pjp, proceed);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Dispatches an instance field assignment with the given join point and arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   */
  public static void putField(ProceedingJoinPoint pjp, VoidProceed proceed) {
    try {
      dispatcher.putField(pjp, proceed);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
