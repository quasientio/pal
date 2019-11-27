package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.ObjectStore;
import com.ittera.cometa.common.lang.ObjectNotFoundException;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.core.exec.DispatcherConnector;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.Unwrapper;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessageType;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SetInstanceVariableDispatcher extends SetFieldDispatcher {

  @Inject
  public SetInstanceVariableDispatcher(
      UUID peerUuid,
      MessageBuilder messageBuilder,
      DispatcherConnector connector,
      ObjectStore objectStore) {
    setPeerUuid(peerUuid);
    setMessageBuilder(messageBuilder);
    setConnector(connector);
    setObjectStore(objectStore);
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
      throws ObjectNotFoundException {
    Object target;
    if (execMessage.getInstanceFieldPut().hasObject()) {
      Class fieldType = ((Field) accessibleObject.get()).getType();
      target = Unwrapper.unwrapObject(execMessage.getInstanceFieldPut().getObject(), fieldType);
      if (logger.isTraceEnabled()) {
        logger.trace("Unwrapped target: {}", target);
      }
    } else {
      ObjectRef targetObjRef = ObjectRef.from(execMessage.getInstanceFieldPut().getObjectRef());
      if (objectStore.containsObjectRef(targetObjRef)) {
        target = objectStore.lookupObject(targetObjRef);
      } else {
        throw new ObjectNotFoundException(
            String.format("No object found with objRef: %d", targetObjRef.getRef()));
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
            execMessage.getInstanceFieldPut().getClass_().getName(),
            true,
            Thread.currentThread().getContextClassLoader());
    return clazz.getDeclaredField(execMessage.getInstanceFieldPut().getField().getName());
  }

  @Override
  protected Optional<Object> getValueFromMessage(
      final ExecMessage execMessage, final Optional<AccessibleObject> accessibleObject) {

    final Object value;
    final Field field = (Field) accessibleObject.get();

    if (execMessage.getInstanceFieldPut().hasValueObject()) {
      value =
          Unwrapper.unwrapObject(
              execMessage.getInstanceFieldPut().getValueObject(), field.getType());
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
