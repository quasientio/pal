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
package io.quasient.pal.core.execution.java;

import io.quasient.pal.common.runtime.ProxyDispatcher;
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
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the newly created object instance as a result of the constructor invocation
   * @throws Throwable if an error occurs during constructor dispatch
   */
  @Override
  public Object constructor(ProceedingJoinPoint pjp) throws Throwable {
    return constructorDispatcher.dispatch(pjp);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of an instance method that does not return a value to the injected
   * {@link InstanceMethodDispatcher}.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if an error occurs during instance method dispatch
   */
  @Override
  public void voidInstanceMethod(ProceedingJoinPoint pjp) throws Throwable {
    instanceMethodDispatcher.dispatch(pjp);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of a static (class) method that does not return a value to the
   * injected {@link ClassMethodDispatcher}.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if an error occurs during class method dispatch
   */
  @Override
  public void voidClassMethod(ProceedingJoinPoint pjp) throws Throwable {
    classMethodDispatcher.dispatch(pjp);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of an instance method that returns a value to the injected {@link
   * InstanceMethodDispatcher}.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the result returned by the instance method invocation
   * @throws Throwable if an error occurs during instance method dispatch
   */
  @Override
  public Object nonVoidInstanceMethod(ProceedingJoinPoint pjp) throws Throwable {
    return instanceMethodDispatcher.dispatch(pjp);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of a static (class) method that returns a value to the injected
   * {@link ClassMethodDispatcher}.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the result returned by the class method invocation
   * @throws Throwable if an error occurs during class method dispatch
   */
  @Override
  public Object nonVoidClassMethod(ProceedingJoinPoint pjp) throws Throwable {
    return classMethodDispatcher.dispatch(pjp);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the retrieval of a static (class) field value to the injected {@link
   * GetClassVariableDispatcher}.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the static field value obtained through dispatch
   * @throws Throwable if an error occurs during static field dispatch
   */
  @Override
  public Object getStatic(ProceedingJoinPoint pjp) throws Throwable {
    return getClassVariableDispatcher.dispatch(pjp);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the retrieval of an instance field value to the injected {@link
   * GetInstanceVariableDispatcher}.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @return the instance field value obtained through dispatch
   * @throws Throwable if an error occurs during instance field dispatch
   */
  @Override
  public Object getObject(ProceedingJoinPoint pjp) throws Throwable {
    return getInstanceVariableDispatcher.dispatch(pjp);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the update of a static (class) field value to the injected {@link
   * SetClassVariableDispatcher}.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if an error occurs during static field assignment dispatch
   */
  @Override
  public void putStatic(ProceedingJoinPoint pjp) throws Throwable {
    setClassVariableDispatcher.dispatch(pjp);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the update of an instance field value to the injected {@link
   * SetInstanceVariableDispatcher}.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @throws Throwable if an error occurs during instance field assignment dispatch
   */
  @Override
  public void putField(ProceedingJoinPoint pjp) throws Throwable {
    setInstanceVariableDispatcher.dispatch(pjp);
  }
}
