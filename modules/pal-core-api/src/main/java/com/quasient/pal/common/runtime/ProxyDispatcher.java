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

/**
 * Defines the contract for dispatching method invocations, including constructors, instance
 * methods, class methods, and field access operations.
 */
public interface ProxyDispatcher {

  /**
   * Invokes a constructor on the target object with the specified arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param sender the originator of the invocation
   * @param target the target object on which the constructor is invoked
   * @param args the arguments to pass to the constructor
   * @return the newly created object instance
   * @throws Throwable if an error occurs during constructor invocation
   */
  Object constructor(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  /**
   * Executes a void instance method on the target object with the given arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param sender the originator of the invocation
   * @param target the target object on which the method is invoked
   * @param args the arguments to pass to the method
   * @throws Throwable if an error occurs during method invocation
   */
  void voidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable;

  /**
   * Executes a void class method with the specified arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param sender the originator of the invocation
   * @param target the target class on which the method is invoked
   * @param args the arguments to pass to the method
   * @throws Throwable if an error occurs during method invocation
   */
  void voidClassMethod(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  /**
   * Executes a non-void instance method on the target object with the given arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param sender the originator of the invocation
   * @param target the target object on which the method is invoked
   * @param args the arguments to pass to the method
   * @return the result of the method invocation
   * @throws Throwable if an error occurs during method invocation
   */
  Object nonVoidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable;

  /**
   * Executes a non-void class method with the specified arguments.
   *
   * @param ctxt the context in which the invocation occurs
   * @param sender the originator of the invocation
   * @param target the target class on which the method is invoked
   * @param args the arguments to pass to the method
   * @return the result of the method invocation
   * @throws Throwable if an error occurs during method invocation
   */
  Object nonVoidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable;

  /**
   * Retrieves the value of a static field from the target class.
   *
   * @param ctxt the context in which the retrieval occurs
   * @param sender the originator of the retrieval
   * @param target the target class from which the field is retrieved
   * @param args the arguments specifying the field details
   * @return the value of the static field
   * @throws Throwable if an error occurs during field access
   */
  Object getStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  /**
   * Retrieves the value of an instance field from the target object.
   *
   * @param ctxt the context in which the retrieval occurs
   * @param sender the originator of the retrieval
   * @param target the target object from which the field is retrieved
   * @param args the arguments specifying the field details
   * @return the value of the instance field
   * @throws Throwable if an error occurs during field access
   */
  Object getObject(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  /**
   * Sets the value of a static field on the target class.
   *
   * @param ctxt the context in which the modification occurs
   * @param sender the originator of the modification
   * @param target the target class on which the field is modified
   * @param args the arguments specifying the field details and new value
   * @throws Throwable if an error occurs during field modification
   */
  void putStatic(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;

  /**
   * Sets the value of an instance field on the target object.
   *
   * @param ctxt the context in which the modification occurs
   * @param sender the originator of the modification
   * @param target the target object on which the field is modified
   * @param args the arguments specifying the field details and new value
   * @throws Throwable if an error occurs during field modification
   */
  void putField(Context ctxt, Object sender, Object target, Object[] args) throws Throwable;
}
