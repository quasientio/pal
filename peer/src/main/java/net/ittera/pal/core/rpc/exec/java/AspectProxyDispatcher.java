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

@Singleton
public class AspectProxyDispatcher implements ProxyDispatcher {

  // constructor & method dispatchers
  @SuppressWarnings("unused")
  @Inject
  private ConstructorDispatcher constructorDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private ClassMethodDispatcher classMethodDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private InstanceMethodDispatcher instanceMethodDispatcher;

  // field op dispatchers
  @SuppressWarnings("unused")
  @Inject
  private GetClassVariableDispatcher getClassVariableDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private SetClassVariableDispatcher setClassVariableDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private GetInstanceVariableDispatcher getInstanceVariableDispatcher;

  @SuppressWarnings("unused")
  @Inject
  private SetInstanceVariableDispatcher setInstanceVariableDispatcher;

  @Override
  public Object constructor(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return constructorDispatcher.dispatch(ctxt, sender, target, args);
  }

  @Override
  public void voidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    instanceMethodDispatcher.dispatch(ctxt, sender, target, args);
  }

  @Override
  public void voidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    classMethodDispatcher.dispatch(ctxt, sender, target, args);
  }

  @Override
  public Object nonVoidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return instanceMethodDispatcher.dispatch(ctxt, sender, target, args);
  }

  @Override
  public Object nonVoidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return classMethodDispatcher.dispatch(ctxt, sender, target, args);
  }

  @Override
  public Object getStatic(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return getClassVariableDispatcher.dispatch(ctxt, sender, target, args);
  }

  @Override
  public Object getObject(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return getInstanceVariableDispatcher.dispatch(ctxt, sender, target, args);
  }

  @Override
  public void putStatic(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    setClassVariableDispatcher.dispatch(ctxt, sender, target, args);
  }

  @Override
  public void putField(Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
    setInstanceVariableDispatcher.dispatch(ctxt, sender, target, args);
  }
}
