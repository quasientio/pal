package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.lang.reflect.Method;

import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.reflect.MethodSignature;

public abstract class InstanceMethodDispatcher extends JavaDispatcher {

	@Override
	protected DataMessage wrapBeforeExecMessage(StaticPart staticPart, Object sender, Object target, Object[] args) {

		return messageBuilder.buildInstanceMethod(peerUuid, staticPart, sender, target, args);
	}

	@Override
	protected DataMessage wrapAfterExecMessage(StaticPart staticPart, Object returnValue, String objectRef) {

		final Method method = ((MethodSignature) staticPart.getSignature()).getMethod();
		if (returnValue instanceof InvocationException) {
			Exception invocationException = ((InvocationException) returnValue).getException();
			return messageBuilder.buildAccessibleObjectThrowable(peerUuid, method, invocationException, null);
		} else {
			return messageBuilder.buildReturnValue(peerUuid, returnValue, method.getClass(), objectRef, returnsVoid(),
				null);
		}

	}

	@Override
	protected Object invoke(StaticPart staticPart, Object sender, Object target, Object[] args) {

		final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();
		Method method = methodSignature.getMethod();

		method.setAccessible(true);
		Object returnValue;
		try {
			returnValue = method.invoke(target, args);
		} catch (Exception ex) {
			logger.error("Caught exception while invoking instance method. Will wrap and return it.", ex);
			return new InvocationException(ex);
		}

		if (returnsVoid()) {
			return Void.getInstance();
		} else {
			return returnValue;
		}
	}
}
