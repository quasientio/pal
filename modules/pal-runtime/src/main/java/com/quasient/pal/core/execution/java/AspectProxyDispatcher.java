/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.common.runtime.ProxyDispatcher;
import com.quasient.pal.common.weave.Proceed;
import com.quasient.pal.common.weave.VoidProceed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * A proxy dispatcher that delegates method invocations and field accesses to specialized dispatcher
 * components.
 *
 * <p>This class supports constructor invocations, static and instance method calls (with and
 * without return values), as well as field retrieval and assignment. Each operation is routed to an
 * appropriate dispatcher instance injected via CDI.
 */
@Singleton
public class AspectProxyDispatcher implements ProxyDispatcher {

  /**
   * Dispatcher responsible for handling constructor invocations. Provides the mechanism for object
   * creation by delegating the constructor call.
   */
  @SuppressWarnings("unused")
  @Inject
  private ConstructorDispatcher constructorDispatcher;

  /**
   * Dispatcher responsible for handling static (class) method invocations. Routes static method
   * calls to the proper execution logic.
   */
  @SuppressWarnings("unused")
  @Inject
  private ClassMethodDispatcher classMethodDispatcher;

  /**
   * Dispatcher responsible for handling instance method invocations. Delegates instance method
   * calls to the corresponding execution logic.
   */
  @SuppressWarnings("unused")
  @Inject
  private InstanceMethodDispatcher instanceMethodDispatcher;

  /**
   * Dispatcher responsible for retrieving static (class) field values. Facilitates access to class
   * variables in the target object.
   */
  @SuppressWarnings("unused")
  @Inject
  private GetClassVariableDispatcher getClassVariableDispatcher;

  /**
   * Dispatcher responsible for setting static (class) field values. Manages updates to class
   * variables on the target object.
   */
  @SuppressWarnings("unused")
  @Inject
  private SetClassVariableDispatcher setClassVariableDispatcher;

  /**
   * Dispatcher responsible for retrieving instance field values. Enables access to object member
   * variables.
   */
  @SuppressWarnings("unused")
  @Inject
  private GetInstanceVariableDispatcher getInstanceVariableDispatcher;

  /**
   * Dispatcher responsible for setting instance field values. Handles updates to object member
   * variables.
   */
  @SuppressWarnings("unused")
  @Inject
  private SetInstanceVariableDispatcher setInstanceVariableDispatcher;

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the constructor invocation to the injected {@link ConstructorDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the newly created object instance as a result of the constructor invocation
   * @throws Throwable if an error occurs during constructor dispatch
   */
  @Override
  public Object constructor(Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable {
    return constructorDispatcher.dispatch(ctxt, pjp, proceed);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of an instance method that does not return a value to the injected
   * {@link InstanceMethodDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if an error occurs during instance method dispatch
   */
  @Override
  public void voidInstanceMethod(Context ctxt, ProceedingJoinPoint pjp, VoidProceed proceed)
      throws Throwable {
    instanceMethodDispatcher.dispatch(ctxt, pjp, proceed);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of a static (class) method that does not return a value to the
   * injected {@link ClassMethodDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if an error occurs during class method dispatch
   */
  @Override
  public void voidClassMethod(Context ctxt, ProceedingJoinPoint pjp, VoidProceed proceed)
      throws Throwable {
    classMethodDispatcher.dispatch(ctxt, pjp, proceed);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of an instance method that returns a value to the injected {@link
   * InstanceMethodDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the result returned by the instance method invocation
   * @throws Throwable if an error occurs during instance method dispatch
   */
  @Override
  public Object nonVoidInstanceMethod(
      Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed) throws Throwable {
    return instanceMethodDispatcher.dispatch(ctxt, pjp, proceed);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of a static (class) method that returns a value to the injected
   * {@link ClassMethodDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the result returned by the class method invocation
   * @throws Throwable if an error occurs during class method dispatch
   */
  @Override
  public Object nonVoidClassMethod(Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable {
    return classMethodDispatcher.dispatch(ctxt, pjp, proceed);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the retrieval of a static (class) field value to the injected {@link
   * GetClassVariableDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the static field value obtained through dispatch
   * @throws Throwable if an error occurs during static field dispatch
   */
  @Override
  public Object getStatic(Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable {
    return getClassVariableDispatcher.dispatch(ctxt, pjp, proceed);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the retrieval of an instance field value to the injected {@link
   * GetInstanceVariableDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the instance field value obtained through dispatch
   * @throws Throwable if an error occurs during instance field dispatch
   */
  @Override
  public Object getObject(Context ctxt, ProceedingJoinPoint pjp, Proceed<Object> proceed)
      throws Throwable {
    return getInstanceVariableDispatcher.dispatch(ctxt, pjp, proceed);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the update of a static (class) field value to the injected {@link
   * SetClassVariableDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if an error occurs during static field assignment dispatch
   */
  @Override
  public void putStatic(Context ctxt, ProceedingJoinPoint pjp, VoidProceed proceed)
      throws Throwable {
    setClassVariableDispatcher.dispatch(ctxt, pjp, proceed);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the update of an instance field value to the injected {@link
   * SetInstanceVariableDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link VoidProceed} callback handle
   * @throws Throwable if an error occurs during instance field assignment dispatch
   */
  @Override
  public void putField(Context ctxt, ProceedingJoinPoint pjp, VoidProceed proceed)
      throws Throwable {
    setInstanceVariableDispatcher.dispatch(ctxt, pjp, proceed);
  }
}
