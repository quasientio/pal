package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.reflect.MethodSignature;

public abstract class ClassMethodDispatcher extends MethodDispatcher {

	@Override
	protected final DataMessage wrapBeforeExecMessage(StaticPart staticPart, Object sender, Object target, Object[] args) {

		return messageBuilder.buildClassMethod(peerUuid, staticPart, sender, args);
	}

	@Override
	protected DataMessage wrapAfterExecMessage(StaticPart staticPart, Object value, String objectRef) {

		final Method method = ((MethodSignature) staticPart.getSignature()).getMethod();

		if (value instanceof InvocationException) {
			Exception invocationException = ((InvocationException) value).getException();
			return messageBuilder.buildAccessibleObjectThrowable(peerUuid, method, invocationException, null);
		} else {
			return messageBuilder.buildReturnValue(peerUuid, value, method.getReturnType(), objectRef, returnsVoid(),
				null);
		}
	}

	@Override
	protected final Object invoke(StaticPart staticPart, Object sender, Object target, Object[] args) {

		final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();
		Method method = methodSignature.getMethod();

		Object returnValue;
		method.setAccessible(true);
		try {
			returnValue = method.invoke(null, args);
		} catch (Exception ex) {
			logger.error("Caught exception while invoking class method. Will wrap and return it.", ex);
			return new InvocationException(ex);
		}

		if (returnsVoid()) {
			return Void.getInstance();
		} else {
			return returnValue;
		}
	}

	@Override
	protected final Type getBeforeExecMessageType() {
		return Type.CLASS_METHOD;
	}
}
