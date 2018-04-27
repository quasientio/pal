package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import java.lang.reflect.Field;
import java.lang.reflect.AccessibleObject;

import java.util.List;
import java.util.Optional;

abstract public class GetFieldDispatcher extends FieldOpDispatcher {

	@Override
	protected final Object invoke(Context ctxt, Object sender, Object target, Object[] args) {

		Field field = ((FieldSignature) ctxt.getSignature()).getField();
		field.setAccessible(true);

		Object fieldValue;
		try {
			fieldValue = field.get(target);
		} catch (Exception ex) {
			logger.error("Caught exception while invoking field operation. Will wrap and return it.", ex);
			return new InvocationException(ex);
		}

		return fieldValue;
	}

	@Override
	protected Object invokeIncoming(AccessibleObject accessibleObject, Optional<Object> target, List<Object> args,
																	Optional<Object> value) throws Exception {
		Field field = (Field) accessibleObject;
		Object fieldValue = field.get(target.isPresent() ? target.get() : null);
		return fieldValue;
	}

	protected final boolean assignsValue() {
		return false;
	}

	@Override
	protected DataMessage wrapAfterExecMessage(DataMessage dataMessage, Object valueObject, String valueObjKey,
																						 AccessibleObject accessibleObject, Exception exceptionWhileLoading,
																						 Exception exceptionWhileInvoking) {

		String messageUuid = dataMessage.getMessageUuid();
		Class fieldType = ((Field) accessibleObject).getType();

		if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
			return wrapAfterExecThrowableMessage(messageUuid, accessibleObject, exceptionWhileLoading, exceptionWhileInvoking);
		}

		return messageBuilder.buildReturnValue(peerUuid, valueObject, fieldType, valueObjKey, returnsVoid(), messageUuid);
	}
}
