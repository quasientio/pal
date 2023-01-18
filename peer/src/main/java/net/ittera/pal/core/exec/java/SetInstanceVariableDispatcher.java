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
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.common.objects.ObjectNotFoundException;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.objects.ObjectStore;
import net.ittera.pal.core.SessionStore;
import net.ittera.pal.core.exec.DispatcherConnector;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import net.ittera.pal.serdes.colfer.Unwrapper;

@Singleton
public class SetInstanceVariableDispatcher extends SetFieldDispatcher {

  @Inject
  public SetInstanceVariableDispatcher(
      UUID peerUuid,
      MessageBuilder messageBuilder,
      DispatcherConnector connector,
      ObjectStore objectStore,
      SessionStore sessionStore) {
    setPeerUuid(peerUuid);
    setMessageBuilder(messageBuilder);
    setConnector(connector);
    setObjectStore(objectStore);
    setSessionStore(sessionStore);
  }

  @Override
  protected final ExecMessageType getBeforeExecMessageType() {
    return ExecMessageType.PUT_FIELD;
  }

  @Override
  protected final ExecMessageType getAfterExecMessageType() {
    return ExecMessageType.PUT_FIELD_DONE;
  }

  @Override
  protected Object getTargetFromMessage(
      ExecMessage execMessage, Optional<AccessibleObject> accessibleObject)
      throws NullPointerException {
    Object target;
    Obj fieldPutObject = execMessage.getInstanceFieldPut().getObject();
    if (fieldPutObject != null) {
      Class fieldType = ((Field) accessibleObject.get()).getType();
      target = Unwrapper.unwrapObject(fieldPutObject, fieldType);
      if (logger.isTraceEnabled()) {
        logger.trace("Unwrapped target: {}", target);
      }
    } else {
      ObjectRef targetObjRef = ObjectRef.from(execMessage.getInstanceFieldPut().getObjectRef());
      if (objectStore.containsObjectRef(targetObjRef)) {
        target = objectStore.lookupObject(targetObjRef);
      } else {
        Exception onfe =
            new ObjectNotFoundException(
                String.format("No object found with objRef: %d", targetObjRef.getRef()));
        NullPointerException npe = new NullPointerException(onfe.getMessage());
        npe.initCause(onfe);
        throw npe;
      }
      if (logger.isTraceEnabled()) {
        logger.trace("Loaded target: {}", target);
      }
    }
    return target;
  }

  @Override
  protected AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    Class clazz =
        Class.forName(
            execMessage.getInstanceFieldPut().getClazz().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    return clazz.getDeclaredField(execMessage.getInstanceFieldPut().getField().getName());
  }

  @Override
  protected Optional<Object> getValueFromMessage(
      final ExecMessage execMessage, final Optional<AccessibleObject> accessibleObject) {

    final Object value;
    final Field field = (Field) accessibleObject.get();

    Obj valueObject = execMessage.getInstanceFieldPut().getValueObject();
    if (valueObject != null) {
      value = Unwrapper.unwrapObject(valueObject, field.getType());
      if (logger.isTraceEnabled()) {
        logger.trace("Unwrapped value: {}", value);
      }
    } else {
      value =
          objectStore.lookupObject(
              ObjectRef.from(execMessage.getInstanceFieldPut().getValueObjectRef()));
      if (logger.isTraceEnabled()) {
        logger.trace("Loaded value: {}", value);
      }
    }
    return Optional.ofNullable(value);
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
    return messageBuilder.buildPutObjectDone(
        peerUuid, accessibleObject.get(), messageUuid, messageUuid);
  }
}
