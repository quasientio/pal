package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ExecutableObjectType;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

public abstract class MethodDispatcher extends BaseExecMessageDispatcher {

  @Override
  protected Object invokeIncoming(
      Optional<AccessibleObject> accessibleObject,
      Object target,
      List<Object> args,
      Optional<Object> value)
      throws Exception {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "invokeIncoming:in w/ accessibleObject: {}, target: {}, args: {}, value: {}",
          accessibleObject,
          target,
          args,
          value);
    }
    Method method = (Method) accessibleObject.get();
    return method.invoke(target, args.toArray());
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
        peerUuid,
        valueObject,
        accessibleObject.get(),
        valueObjRef,
        returnsVoid(accessibleObject),
        messageUuid);
  }

  @Override
  protected boolean returnsVoid(Optional<AccessibleObject> accessibleObject) {
    return accessibleObject
        .map(ao -> ((Method) ao).getReturnType())
        .map(java.lang.Void.TYPE::equals)
        .get();
  }

  @Override
  protected final ExecutableObjectType getExecutableObjectType() {
    return ExecutableObjectType.METHOD;
  }
}
