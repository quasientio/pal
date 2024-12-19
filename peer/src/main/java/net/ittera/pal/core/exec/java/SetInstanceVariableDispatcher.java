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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ittera.pal.common.objects.ObjectLookupStore;
import net.ittera.pal.common.objects.ObjectNotFoundException;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.colfer.MessageBuilder;

@Singleton
public class SetInstanceVariableDispatcher extends SetFieldDispatcher {

  @Inject
  public SetInstanceVariableDispatcher(
      UUID peerUuid,
      MessageBuilder messageBuilder,
      DispatcherConnector connector,
      @Named("rpc.allow_nonpublic") String allowNonPublicAccess,
      ObjectLookupStore objectLookupStore) {
    setPeerUuid(peerUuid);
    setMessageBuilder(messageBuilder);
    setConnector(connector);
    setAllowNonPublicAccess(allowNonPublicAccess);
    setObjectLookupStore(objectLookupStore);
  }

  @Override
  protected final MessageType getBeforeExecMessageType() {
    return MessageType.EXEC_PUT_FIELD;
  }

  @Override
  protected final MessageType getAfterExecMessageType() {
    return MessageType.EXEC_PUT_FIELD_DONE;
  }

  @Override
  protected Object getTargetFromMessage(ExecMessage execMessage) throws NullPointerException {
    Object target;
    ObjectRef targetObjRef = ObjectRef.from(execMessage.getInstanceFieldPut().getObjectRef());
    if (objectLookupStore.containsObjectRef(targetObjRef)) {
      target = objectLookupStore.lookupObject(targetObjRef);
    } else {
      Exception objectNotFoundException =
          new ObjectNotFoundException(
              String.format("No object found with objRef: %d", targetObjRef.getRef()));
      NullPointerException npe = new NullPointerException(objectNotFoundException.getMessage());
      npe.initCause(objectNotFoundException);
      throw npe;
    }
    if (logger.isTraceEnabled()) {
      logger.trace("Loaded target: {}", target);
    }
    return target;
  }

  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    return loadAccessibleObject(
        execMessage.getInstanceFieldPut().getClazz().getName(),
        execMessage.getInstanceFieldPut().getField().getName(),
        parameterTypes,
        args);
  }

  @Override
  protected @Nullable Object getValueFromMessage(
      final ExecMessage execMessage, final AccessibleObject accessibleObject) {
    return getValueFromMessage(
        execMessage.getInstanceFieldPut().getValueObject(),
        execMessage.getInstanceFieldPut().getValueObjectRef(),
        accessibleObject);
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
    return messageBuilder.buildPutObjectDone(peerUuid, accessibleObject, messageId, messageId);
  }

  @Override
  public MessageType getSupportedMessageType() {
    return MessageType.EXEC_PUT_FIELD;
  }
}
