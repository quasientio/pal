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

package net.ittera.pal.core.rpc.exec.java;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.common.runtime.ProxyDispatcher;

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
   * @param sender the originating object triggering the constructor dispatch
   * @param target the target class or object on which the constructor is invoked
   * @param args an array of arguments to be passed to the constructor
   * @return the newly created object instance as a result of the constructor invocation
   * @throws Throwable if an error occurs during constructor dispatch
   */
  @Override
  public Object constructor(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return constructorDispatcher.dispatch(ctxt, sender, target, args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of an instance method that does not return a value to the injected
   * {@link InstanceMethodDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param sender the originating object triggering the method call
   * @param target the object instance on which the method is invoked
   * @param args an array of arguments to be passed to the method
   * @throws Throwable if an error occurs during instance method dispatch
   */
  @Override
  public void voidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    instanceMethodDispatcher.dispatch(ctxt, sender, target, args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of a static (class) method that does not return a value to the
   * injected {@link ClassMethodDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param sender the originating object triggering the method call
   * @param target the target class on which the static method is invoked
   * @param args an array of arguments to be passed to the method
   * @throws Throwable if an error occurs during class method dispatch
   */
  @Override
  public void voidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    classMethodDispatcher.dispatch(ctxt, sender, target, args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of an instance method that returns a value to the injected {@link
   * InstanceMethodDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param sender the originating object triggering the method call
   * @param target the object instance on which the method is invoked
   * @param args an array of arguments to be passed to the method
   * @return the result returned by the instance method invocation
   * @throws Throwable if an error occurs during instance method dispatch
   */
  @Override
  public Object nonVoidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return instanceMethodDispatcher.dispatch(ctxt, sender, target, args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates an invocation of a static (class) method that returns a value to the injected
   * {@link ClassMethodDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param sender the originating object triggering the method call
   * @param target the target class on which the static method is invoked
   * @param args an array of arguments to be passed to the method
   * @return the result returned by the class method invocation
   * @throws Throwable if an error occurs during class method dispatch
   */
  @Override
  public Object nonVoidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return classMethodDispatcher.dispatch(ctxt, sender, target, args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the retrieval of a static (class) field value to the injected {@link
   * GetClassVariableDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param sender the originating object requesting the field value
   * @param target the target class from which the field is retrieved
   * @param args an array of parameters to be used in retrieving the field value
   * @return the static field value obtained through dispatch
   * @throws Throwable if an error occurs during static field dispatch
   */
  @Override
  public Object getStatic(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return getClassVariableDispatcher.dispatch(ctxt, sender, target, args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the retrieval of an instance field value to the injected {@link
   * GetInstanceVariableDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param sender the originating object requesting the field value
   * @param target the object instance from which the field is retrieved
   * @param args an array of parameters to be used in retrieving the field value
   * @return the instance field value obtained through dispatch
   * @throws Throwable if an error occurs during instance field dispatch
   */
  @Override
  public Object getObject(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return getInstanceVariableDispatcher.dispatch(ctxt, sender, target, args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the update of a static (class) field value to the injected {@link
   * SetClassVariableDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param sender the originating object performing the field update
   * @param target the target class on which the static field is updated
   * @param args an array of parameters representing the new field value
   * @throws Throwable if an error occurs during static field assignment dispatch
   */
  @Override
  public void putStatic(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    setClassVariableDispatcher.dispatch(ctxt, sender, target, args);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the update of an instance field value to the injected {@link
   * SetInstanceVariableDispatcher}.
   *
   * @param ctxt the runtime execution context containing invocation metadata
   * @param sender the originating object performing the field update
   * @param target the object instance on which the field is updated
   * @param args an array of parameters representing the new field value
   * @throws Throwable if an error occurs during instance field assignment dispatch
   */
  @Override
  public void putField(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
    setInstanceVariableDispatcher.dispatch(ctxt, sender, target, args);
  }
}
