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
import java.lang.reflect.Field;
import java.util.List;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.ExecMessage;

public abstract class GetFieldDispatcher extends FieldOpDispatcher {

  @Override
  protected final Object invoke(Context ctxt, Object sender, Object target, Object[] args) {

    Field field = ((FieldSignature) ctxt.getSignature()).getField();
    field.setAccessible(true);

    Object fieldValue;
    try {
      fieldValue = field.get(target);
    } catch (Exception ex) {
      logger.error("Caught exception while invoking field operation. Will wrap and return it.", ex);
      return new InvocationExceptionWrapper(ex);
    }

    return fieldValue;
  }

  @Override
  protected Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<Object> args, Object value)
      throws Exception {

    // discard args and value
    return invokeIncoming(accessibleObject, target);
  }

  private Object invokeIncoming(AccessibleObject accessibleObject, Object target) throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "invokeIncoming:in w/ accessibleObject: {}, target: {}", accessibleObject, target);
    }
    Field field = (Field) accessibleObject;
    return field.get(target);
  }

  @Override
  protected ExecMessage createAfterExecMessage(
      ExecMessage execMessage,
      Object valueObject,
      ObjectRef valueObjRef,
      AccessibleObject accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking) {

    String messageId = execMessage.getMessageId();

    if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
      return wrapAfterExecThrowableMessage(
          messageId, accessibleObject, exceptionWhileLoading, exceptionWhileInvoking);
    }

    return messageBuilder.buildReturnValue(
        peerUuid, valueObject, accessibleObject, valueObjRef, returnsVoid(), messageId);
  }

  @Override
  protected final boolean returnsVoid() {
    return false;
  }

  @Override
  protected boolean returnsVoid(AccessibleObject accessibleObject) {
    return false;
  }
}
