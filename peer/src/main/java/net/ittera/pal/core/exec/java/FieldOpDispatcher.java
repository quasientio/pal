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
import java.util.List;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.types.MessageType;

public abstract class FieldOpDispatcher extends BaseExecMessageDispatcher {

  @Override
  protected final ExecMessage createBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args) {
    Object parameter = (args == null || args.length == 0) ? null : args[0];
    return messageBuilder.buildFieldOp(
        peerUuid,
        ctxt,
        getBeforeExecMessageType(),
        sender,
        storeObject(sender),
        storeObject(target),
        parameter,
        storeObject(parameter));
  }

  @Override
  protected final ExecMessage createAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    AccessibleObject field = ((FieldSignature) ctxt.getSignature()).getField();

    if (value instanceof InvocationExceptionWrapper) {
      Exception invocationException = ((InvocationExceptionWrapper) value).exception();
      return messageBuilder.buildAccessibleObjectThrowable(
          peerUuid, field, invocationException, null);
    } else {
      if (!returnsVoid()) {
        return messageBuilder.buildReturnValue(peerUuid, value, field, objectRef, false, null);
      } else {
        return messageBuilder.buildFieldOpDone(peerUuid, field, ctxt, getAfterExecMessageType());
      }
    }
  }

  @Override
  protected List<Parameter> getParameterList(ExecMessage execMessage) {
    return null;
  }

  protected abstract boolean returnsVoid();

  protected abstract MessageType getAfterExecMessageType();
}
