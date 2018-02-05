package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.lang.reflect.Field;

import org.aspectj.lang.JoinPoint;
import org.aspectj.runtime.reflect.FieldSignatureImpl;

public abstract class FieldOpDispatcher extends BaseDispatcher {

	@Override
	protected final DataMessage wrapBeforeExecMessage(JoinPoint.StaticPart staticPart, Object sender, Object target,
																										Object[] args) {

		Object parameter = (args == null || args.length == 0) ? null : args[0];
		return messageBuilder.buildFieldOp(peerUuid, staticPart, getBeforeExecMessageType(), sender, target, parameter);
	}

	@Override
	protected final DataMessage wrapAfterExecMessage(JoinPoint.StaticPart staticPart, Object value,
																									 String objectRef) {

		Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();

		if (value instanceof InvocationException) {
			Exception invocationException = ((InvocationException) value).getException();
			return messageBuilder.buildAccessibleObjectThrowable(peerUuid, field, invocationException, null);
		} else {
			if (!returnsVoid()) {
				return messageBuilder.buildReturnValue(peerUuid, value, field.getType(), objectRef, false, null);
			} else {
				return messageBuilder.buildFieldOpDone(peerUuid, staticPart, getAfterExecMessageType());
			}
		}
	}

	@Override
	protected final Object invoke(JoinPoint.StaticPart staticPart, Object sender, Object target, Object[] args) {

		Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
		field.setAccessible(true);

		Object fieldValue;
		try {
			fieldValue = field.get(null);
		} catch (Exception ex) {
			logger.error("Caught exception while invoking field operation. Will wrap and return it.", ex);
			return new InvocationException(ex);
		}

		if (returnsVoid()) {
			return Void.getInstance();
		} else {
			return fieldValue;
		}
	}
}
