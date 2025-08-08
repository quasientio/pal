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
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Defines the contract for dispatching method invocations, including constructors, instance
 * methods, class methods, and field access operations.
 */
public interface ProxyDispatcher {

  /**
   * Invokes a constructor on the target object with the specified arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the newly created object instance
   * @throws Throwable if an error occurs during constructor invocation
   */
  Object constructor(Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable;

  /**
   * Executes a void instance method on the target object with the given arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if an error occurs during method invocation
   */
  void voidInstanceMethod(Context ctxt, ProceedingJoinPoint pjp, VoidProceed proceed)
      throws Throwable;

  /**
   * Executes a void class method with the specified arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if an error occurs during method invocation
   */
  void voidClassMethod(Context ctxt, ProceedingJoinPoint pjp, VoidProceed proceed) throws Throwable;

  /**
   * Executes a non-void instance method on the target object with the given arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the result of the method invocation
   * @throws Throwable if an error occurs during method invocation
   */
  Object nonVoidInstanceMethod(Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable;

  /**
   * Executes a non-void class method with the specified arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the result of the method invocation
   * @throws Throwable if an error occurs during method invocation
   */
  Object nonVoidClassMethod(Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable;

  /**
   * Retrieves the value of a static field from the target class.
   *
   * @param ctxt the context in which the retrieval occurs
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the value of the static field
   * @throws Throwable if an error occurs during field access
   */
  Object getStatic(Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed) throws Throwable;

  /**
   * Retrieves the value of an instance field from the target object.
   *
   * @param ctxt the context in which the retrieval occurs
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the value of the instance field
   * @throws Throwable if an error occurs during field access
   */
  Object getObject(Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed) throws Throwable;

  /**
   * Sets the value of a static field on the target class.
   *
   * @param ctxt the context in which the modification occurs
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if an error occurs during field modification
   */
  void putStatic(Context ctxt, ProceedingJoinPoint pjp, VoidProceed proceed) throws Throwable;

  /**
   * Sets the value of an instance field on the target object.
   *
   * @param ctxt the context in which the modification occurs
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if an error occurs during field modification
   */
  void putField(Context ctxt, ProceedingJoinPoint pjp, VoidProceed proceed) throws Throwable;
}
