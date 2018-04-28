package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.lang.reflect.Method;
import java.lang.reflect.AccessibleObject;

import java.util.List;
import java.util.Optional;

public abstract class MethodDispatcher extends BaseDispatcher {

	@Override
	protected Object invokeIncoming(AccessibleObject accessibleObject, Optional<Object> target, List<Object> args,
																	Optional<Object> value) throws Exception {

		Method method = (Method) accessibleObject;
		Object returnValue = method.invoke(target.isPresent() ? target.get() : null, args.toArray());
		return returnValue;
	}

	@Override
	protected DataMessage wrapAfterExecMessage(DataMessage dataMessage, Object valueObject, String valueObjKey,
																						 AccessibleObject accessibleObject, Exception exceptionWhileLoading,
																						 Exception exceptionWhileInvoking) {

		String messageUuid = dataMessage.getMessageUuid();

		if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
			return wrapAfterExecThrowableMessage(messageUuid, accessibleObject, exceptionWhileLoading, exceptionWhileInvoking);
		}

		Class methodReturnType = ((Method) accessibleObject).getReturnType();
		return messageBuilder.buildReturnValue(peerUuid, valueObject, methodReturnType, valueObjKey, returnsVoid(),
			messageUuid);
	}

}
