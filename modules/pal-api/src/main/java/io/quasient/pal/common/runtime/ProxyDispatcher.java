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
