package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.lang.reflect.Constructor;

import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.reflect.ConstructorSignature;

import javax.inject.Singleton;

public class ConstructorDispatcher extends JavaDispatcher {

	@Singleton
	public ConstructorDispatcher() {
	}

	@Override
	protected DataMessage wrapBeforeExecMessage(StaticPart staticPart, Object sender, Object target, Object[] args) {

		return messageBuilder.buildConstructor(peerUuid, staticPart, sender, args);
	}

	@Override
	protected DataMessage wrapAfterExecMessage(StaticPart staticPart, Object returnValue, String objectRef) {

		final Constructor constructor = ((ConstructorSignature) staticPart.getSignature()).getConstructor();

		if (returnValue instanceof InvocationException) {
			Exception invocationException = ((InvocationException) returnValue).getException();
			return messageBuilder.buildAccessibleObjectThrowable(peerUuid, constructor, invocationException,
				null);
		} else {
			return messageBuilder.buildReturnValue(peerUuid, returnValue, constructor.getClass(), objectRef, false,
				null);
		}
	}

	@Override
	protected Object invoke(StaticPart staticPart, Object sender, Object target, Object[] args) {

		final Constructor constructor = ((ConstructorSignature) staticPart.getSignature()).getConstructor();

		Object newObject;
		constructor.setAccessible(true);
		try {
			newObject = constructor.newInstance(args);
		} catch (Exception ex) {
			logger.error("Caught exception while invoking constructor. Will wrap and return it.", ex);
			return new InvocationException(ex);
		}

		return newObject;
	}

	@Override
	protected boolean returnsVoid() {
		return false;
	}
}
