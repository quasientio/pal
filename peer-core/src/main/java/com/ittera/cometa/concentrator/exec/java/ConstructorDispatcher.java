package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.lang.reflect.Constructor;

import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.lang.reflect.ConstructorSignature;

import javax.inject.Singleton;

public class ConstructorDispatcher extends BaseDispatcher {

	@Singleton
	public ConstructorDispatcher() {
	}

	@Override
	protected final DataMessage wrapBeforeExecMessage(StaticPart staticPart, Object sender, Object target, Object[] args) {

		return messageBuilder.buildConstructor(peerUuid, staticPart, sender, args);
	}

	@Override
	protected final DataMessage wrapAfterExecMessage(StaticPart staticPart, Object value, String objectRef) {

		final Constructor constructor = ((ConstructorSignature) staticPart.getSignature()).getConstructor();

		if (value instanceof InvocationException) {
			Exception invocationException = ((InvocationException) value).getException();
			return messageBuilder.buildAccessibleObjectThrowable(peerUuid, constructor, invocationException,
				null);
		} else {
			return messageBuilder.buildReturnValue(peerUuid, value, constructor.getClass(), objectRef, false,
				null);
		}
	}

	@Override
	protected final Object invoke(StaticPart staticPart, Object sender, Object target, Object[] args) {

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
	protected final boolean returnsVoid() {
		return false;
	}

	@Override
	protected final Type getBeforeExecMessageType() {
		return Type.CONSTRUCTOR;
	}

	@Override
	protected final Type getAfterExecMessageType() {
		return Type.RETURN_VALUE;
	}
}
