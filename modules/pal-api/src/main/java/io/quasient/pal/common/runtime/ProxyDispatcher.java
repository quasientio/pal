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

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Defines the contract for dispatching method invocations, including constructors, instance
 * methods, class methods, and field access operations.
 */
public interface ProxyDispatcher {

  /**
   * Invokes a constructor on the target object with the specified arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the newly created object instance
   * @throws Throwable if an error occurs during constructor invocation
   */
  Object constructor(ProceedingJoinPoint pjp) throws Throwable;

  /**
   * Executes a void instance method on the target object with the given arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if an error occurs during method invocation
   */
  void voidInstanceMethod(ProceedingJoinPoint pjp) throws Throwable;

  /**
   * Executes a void class method with the specified arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if an error occurs during method invocation
   */
  void voidClassMethod(ProceedingJoinPoint pjp) throws Throwable;

  /**
   * Executes a non-void instance method on the target object with the given arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the result of the method invocation
   * @throws Throwable if an error occurs during method invocation
   */
  Object nonVoidInstanceMethod(ProceedingJoinPoint pjp) throws Throwable;

  /**
   * Executes a non-void class method with the specified arguments.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the result of the method invocation
   * @throws Throwable if an error occurs during method invocation
   */
  Object nonVoidClassMethod(ProceedingJoinPoint pjp) throws Throwable;

  /**
   * Retrieves the value of a static field from the target class.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the value of the static field
   * @throws Throwable if an error occurs during field access
   */
  Object getStatic(ProceedingJoinPoint pjp) throws Throwable;

  /**
   * Retrieves the value of an instance field from the target object.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the value of the instance field
   * @throws Throwable if an error occurs during field access
   */
  Object getObject(ProceedingJoinPoint pjp) throws Throwable;

  /**
   * Sets the value of a static field on the target class.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if an error occurs during field modification
   */
  void putStatic(ProceedingJoinPoint pjp) throws Throwable;

  /**
   * Sets the value of an instance field on the target object.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if an error occurs during field modification
   */
  void putField(ProceedingJoinPoint pjp) throws Throwable;
}
