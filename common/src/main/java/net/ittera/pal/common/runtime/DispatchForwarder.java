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

package net.ittera.pal.common.runtime;

import jakarta.inject.Inject;

/**
 * This class decouples the QuantizeAspect from the dispatch calls in AspectProxyDispatcher, by
 * means of the ProxyDispatcher interface, so that the aspect (and the aspects module) is not
 * directly dependent on the Dispatcher classes (in the core module), imported by
 * AspectProxyDispatcher. Therefore, aspects only needs to import common. This avoids a circular
 * dependency at runtime, where core depends on aspects (to classload the aspect)
 */
public final class DispatchForwarder {

  @Inject private static ProxyDispatcher dispatcher;

  private DispatchForwarder() {}

  static void setDispatcher(ProxyDispatcher proxyDispatcher) {
    dispatcher = proxyDispatcher;
  }

  public static Object constructor(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return dispatcher.constructor(ctxt, sender, target, args);
  }

  public static void voidInstanceMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    dispatcher.voidInstanceMethod(ctxt, sender, target, args);
  }

  public static void voidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    dispatcher.voidClassMethod(ctxt, sender, target, args);
  }

  public static Object nonVoidInstanceMethod(
      Context ctxt, Object sender, Object target, Object[] args) throws Throwable {
    return dispatcher.nonVoidInstanceMethod(ctxt, sender, target, args);
  }

  public static Object nonVoidClassMethod(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return dispatcher.nonVoidClassMethod(ctxt, sender, target, args);
  }

  public static Object getStatic(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return dispatcher.getStatic(ctxt, sender, target, args);
  }

  public static Object getObject(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    return dispatcher.getObject(ctxt, sender, target, args);
  }

  public static void putStatic(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    dispatcher.putStatic(ctxt, sender, target, args);
  }

  public static void putField(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {
    dispatcher.putField(ctxt, sender, target, args);
  }
}
