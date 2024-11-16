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

package net.ittera.pal.core.exec.java;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ExecMessage;

public abstract class MethodDispatcher extends BaseExecMessageDispatcher {

  @Override
  protected Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<Object> args, Object value)
      throws Exception {
    // discard value
    return invokeIncoming(accessibleObject, target, args);
  }

  private Object invokeIncoming(AccessibleObject accessibleObject, Object target, List<Object> args)
      throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "invokeIncoming:in w/ accessibleObject: {}, target: {}, args: {}",
          accessibleObject,
          target,
          args);
    }
    Method method = (Method) accessibleObject;
    return method.invoke(target, args.toArray());
  }

  @Override
  protected ExecMessage wrapAfterExecMessage(
      ExecMessage execMessage,
      Object valueObject,
      ObjectRef valueObjRef,
      AccessibleObject accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking) {

    String messageUuid = execMessage.getMessageUuid();

    if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
      return wrapAfterExecThrowableMessage(
          messageUuid, accessibleObject, exceptionWhileLoading, exceptionWhileInvoking);
    }

    return messageBuilder.buildReturnValue(
        peerUuid,
        valueObject,
        accessibleObject,
        valueObjRef,
        returnsVoid(accessibleObject),
        messageUuid);
  }

  @Override
  protected boolean returnsVoid(AccessibleObject accessibleObject) {
    return ((Method) accessibleObject).getReturnType().equals(java.lang.Void.TYPE);
  }
}
