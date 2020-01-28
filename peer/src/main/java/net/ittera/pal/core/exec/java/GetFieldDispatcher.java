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
import java.util.Optional;
import net.ittera.pal.common.lang.Context;
import net.ittera.pal.common.lang.ObjectRef;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;

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
      Optional<AccessibleObject> accessibleObject,
      Object target,
      List<Object> args,
      Optional<Object> value)
      throws Exception {
    Field field = (Field) accessibleObject.get();
    return field.get(target);
  }

  @Override
  protected ExecMessage wrapAfterExecMessage(
      ExecMessage execMessage,
      Object valueObject,
      ObjectRef valueObjRef,
      Optional<AccessibleObject> accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking) {

    String messageUuid = execMessage.getMessageUuid();

    if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
      return wrapAfterExecThrowableMessage(
          messageUuid,
          accessibleObject,
          getExecutableObjectType(),
          exceptionWhileLoading,
          exceptionWhileInvoking);
    }

    return messageBuilder.buildReturnValue(
        peerUuid, valueObject, accessibleObject.get(), valueObjRef, returnsVoid(), messageUuid);
  }

  @Override
  protected final boolean returnsVoid() {
    return false;
  }

  @Override
  protected boolean returnsVoid(Optional<AccessibleObject> accessibleObject) {
    return false;
  }
}
