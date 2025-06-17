/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
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
