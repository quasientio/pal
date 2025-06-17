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

import jakarta.inject.Inject;

/**
 * Facilitates the decoupling of dispatching mechanisms from aspect implementations by forwarding
 * dispatch calls through a ProxyDispatcher interface.
 *
 * <p>This class serves as an intermediary between AspectProxyDispatcher and the core Dispatcher
 * classes, ensuring that the aspects module only depends on the common module. By delegating
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
   * Assigns a ProxyDispatcher to be used by the DispatchForwarder. This method allows dynamic
   * configuration of the dispatching mechanism.
   *
   * @param proxyDispatcher the ProxyDispatcher implementation to assign
   */
  static void setDispatcher(ProxyDispatcher proxyDispatcher) {
    dispatcher = proxyDispatcher;
  }

  /**
   * Dispatches a constructor call with the given context and arguments.
   *
   * @param ctxt the execution context
   * @param sender the object initiating the call
   * @param target the target object on which the constructor is invoked
   * @param args the arguments for the constructor
   * @return the newly created object instance
   * @throws Throwable if dispatching the constructor fails
   */
  public static Object constructor(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return dispatcher.constructor(ctxt, sender, target, args);
  }

  /**
   * Dispatches a void instance method call with the given context and arguments.
   *
   * @param ctxt the execution context
   * @param sender the object initiating the call
   * @param target the target instance on which the method is invoked
   * @param args the arguments for the method
   * @throws Throwable if dispatching the method call fails
   */
  public static void voidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    dispatcher.voidInstanceMethod(ctxt, sender, target, args);
  }

  /**
   * Dispatches a void static class method call with the given context and arguments.
   *
   * @param ctxt the execution context
   * @param sender the object initiating the call
   * @param target the target class on which the static method is invoked
   * @param args the arguments for the method
   * @throws Throwable if dispatching the method call fails
   */
  public static void voidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    dispatcher.voidClassMethod(ctxt, sender, target, args);
  }

  /**
   * Dispatches a non-void instance method call with the given context and arguments.
   *
   * @param ctxt the execution context
   * @param sender the object initiating the call
   * @param target the target instance on which the method is invoked
   * @param args the arguments for the method
   * @return the result of the method invocation
   * @throws Throwable if dispatching the method call fails
   */
  public static Object nonVoidInstanceMethod(
      Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
    return dispatcher.nonVoidInstanceMethod(ctxt, sender, target, args);
  }

  /**
   * Dispatches a non-void static class method call with the given context and arguments.
   *
   * @param ctxt the execution context
   * @param sender the object initiating the call
   * @param target the target class on which the static method is invoked
   * @param args the arguments for the method
   * @return the result of the method invocation
   * @throws Throwable if dispatching the method call fails
   */
  public static Object nonVoidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return dispatcher.nonVoidClassMethod(ctxt, sender, target, args);
  }

  /**
   * Dispatches a static field retrieval with the given context and arguments.
   *
   * @param ctxt the execution context
   * @param sender the object initiating the call
   * @param target the target class from which the static field is retrieved
   * @param args additional arguments if necessary
   * @return the value of the static field
   * @throws Throwable if dispatching the field retrieval fails
   */
  public static Object getStatic(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return dispatcher.getStatic(ctxt, sender, target, args);
  }

  /**
   * Dispatches an instance field retrieval with the given context and arguments.
   *
   * @param ctxt the execution context
   * @param sender the object initiating the call
   * @param target the target instance from which the field is retrieved
   * @param args additional arguments if necessary
   * @return the value of the instance field
   * @throws Throwable if dispatching the field retrieval fails
   */
  public static Object getObject(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return dispatcher.getObject(ctxt, sender, target, args);
  }

  /**
   * Dispatches a static field assignment with the given context and arguments.
   *
   * @param ctxt the execution context
   * @param sender the object initiating the call
   * @param target the target class on which the static field is set
   * @param args the value to assign to the static field
   * @throws Throwable if dispatching the field assignment fails
   */
  public static void putStatic(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    dispatcher.putStatic(ctxt, sender, target, args);
  }

  /**
   * Dispatches an instance field assignment with the given context and arguments.
   *
   * @param ctxt the execution context
   * @param sender the object initiating the call
   * @param target the target instance on which the field is set
   * @param args the value to assign to the instance field
   * @throws Throwable if dispatching the field assignment fails
   */
  public static void putField(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    dispatcher.putField(ctxt, sender, target, args);
  }
}
