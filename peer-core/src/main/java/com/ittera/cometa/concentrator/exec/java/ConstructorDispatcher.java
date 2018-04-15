package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.ConstructorSignature;
import com.ittera.cometa.concentrator.exec.DispatcherConnector;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import javax.inject.Singleton;
import javax.inject.Inject;

import java.util.UUID;

import java.lang.reflect.Constructor;

public class ConstructorDispatcher extends BaseDispatcher {

	@Singleton
	@Inject
	public ConstructorDispatcher(UUID peerUuid, DataMessageBuilder messageBuilder, DispatcherConnector connector,
															 ObjectService objectService) {
		setPeerUuid(peerUuid);
		setMessageBuilder(messageBuilder);
		setConnector(connector);
		setObjectService(objectService);
	}


	@Singleton
	public ConstructorDispatcher() {
	}

	@Override
	protected final DataMessage wrapBeforeExecMessage(Context ctxt, Object sender, Object target, Object[] args) {

		return messageBuilder.buildConstructor(peerUuid, ctxt, sender, args);
	}

	@Override
	protected final DataMessage wrapAfterExecMessage(Context ctxt, Object value, String objectRef) {

		final Constructor constructor = ((ConstructorSignature) ctxt.getSignature()).getConstructor();

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
	protected final Object invoke(Context ctxt, Object sender, Object target, Object[] args) {

		final Constructor constructor = ((ConstructorSignature) ctxt.getSignature()).getConstructor();

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
