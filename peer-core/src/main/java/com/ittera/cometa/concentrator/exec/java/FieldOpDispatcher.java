package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import java.lang.reflect.Field;

import java.util.List;

public abstract class FieldOpDispatcher extends BaseDispatcher {

	@Override
	protected final DataMessage wrapBeforeExecMessage(Context ctxt, Object sender, Object target,
																										Object[] args) {
		Object parameter = (args == null || args.length == 0) ? null : args[0];
		return messageBuilder.buildFieldOp(peerUuid, ctxt, getBeforeExecMessageType(), sender, storeObject(sender),
			target, storeObject(target), parameter, storeObject(parameter));
	}

	@Override
	protected final DataMessage wrapAfterExecMessage(Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

		Field field = ((FieldSignature) ctxt.getSignature()).getField();

		if (value instanceof InvocationExceptionWrapper) {
			Exception invocationException = ((InvocationExceptionWrapper) value).getException();
			return messageBuilder.buildAccessibleObjectThrowable(peerUuid, field, invocationException, null);
		} else {
			if (!returnsVoid()) {
				return messageBuilder.buildReturnValue(peerUuid, value, field.getType(), objectRef, false, null);
			} else {
				return messageBuilder.buildFieldOpDone(peerUuid, ctxt, getAfterExecMessageType());
			}
		}
	}

	@Override
	protected List<Primitives.Parameter> getParameterList(DataMessage dataMessage) {
		return null;
	}

	abstract protected boolean returnsVoid();

	abstract protected Type getAfterExecMessageType();
}
