package net.ittera.pal.core.exec.java;

import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.Optional;
import net.ittera.pal.common.lang.Context;
import net.ittera.pal.common.lang.ObjectRef;
import net.ittera.pal.common.lang.reflect.ExecutableObjectType;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;
import net.ittera.pal.messages.protobuf.Primitives;

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
