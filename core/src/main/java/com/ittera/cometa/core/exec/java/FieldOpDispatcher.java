package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ExecutableObjectType;
import com.ittera.cometa.common.lang.reflect.FieldSignature;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessageType;
import com.ittera.cometa.messages.protobuf.Primitives;
import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.Optional;

public abstract class FieldOpDispatcher extends BaseExecMessageDispatcher {

  @Override
  protected final ExecMessage wrapBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args) {
    Object parameter = (args == null || args.length == 0) ? null : args[0];
    return messageBuilder.buildFieldOp(
        peerUuid,
        ctxt,
        getBeforeExecMessageType(),
        sender,
        storeObject(sender),
        target,
        storeObject(target),
        parameter,
        storeObject(parameter));
  }

  @Override
  protected final ExecMessage wrapAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

    Optional<AccessibleObject> field =
        Optional.of(((FieldSignature) ctxt.getSignature()).getField());

    if (value instanceof InvocationExceptionWrapper) {
      Exception invocationException = ((InvocationExceptionWrapper) value).getException();
      return messageBuilder.buildAccessibleObjectThrowable(
          peerUuid, field, getExecutableObjectType(), invocationException, null);
    } else {
      if (!returnsVoid()) {
        return messageBuilder.buildReturnValue(
            peerUuid, value, field.get(), objectRef, false, null);
      } else {
        return messageBuilder.buildFieldOpDone(
            peerUuid, field.get(), ctxt, getAfterExecMessageType());
      }
    }
  }

  @Override
  protected List<Primitives.Parameter> getParameterList(ExecMessage execMessage) {
    return null;
  }

  @Override
  protected final ExecutableObjectType getExecutableObjectType() {
    return ExecutableObjectType.FIELD;
  }

  protected abstract boolean returnsVoid();

  protected abstract ExecMessageType getAfterExecMessageType();
}
