package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ExecutableObjectType;
import com.ittera.cometa.common.lang.reflect.ConstructorSignature;

import com.ittera.cometa.concentrator.exec.DispatcherConnector;

import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import javax.inject.Singleton;
import javax.inject.Inject;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;

import java.util.Arrays;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

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

	@Override
	protected final DataMessage wrapBeforeExecMessage(Context ctxt, Object sender, Object target, Object[] args) {

		return messageBuilder.buildConstructor(peerUuid, ctxt, sender, storeObject(sender), args, Arrays.stream(args).map(
			a -> storeObject(a)).toArray(ObjectRef[]::new));
	}

	@Override
	protected final DataMessage wrapAfterExecMessage(Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

		final Optional<AccessibleObject> constructor = Optional.of(((ConstructorSignature) ctxt.getSignature())
			.getConstructor());

		if (value instanceof InvocationExceptionWrapper) {
			Exception invocationException = ((InvocationExceptionWrapper) value).getException();
			return messageBuilder.buildAccessibleObjectThrowable(peerUuid, constructor, getExecutableObjectType(),
				invocationException, null);
		} else {
			return messageBuilder.buildReturnValue(peerUuid, value, constructor.get(), objectRef, false,
				null);
		}
	}

	@Override
	protected DataMessage wrapAfterExecMessage(DataMessage dataMessage, Object valueObject, ObjectRef valueObjRef,
																						 Optional<AccessibleObject> accessibleObject, Throwable exceptionWhileLoading,
																						 Throwable exceptionWhileInvoking) {

		String messageUuid = dataMessage.getMessageUuid();

		if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
			return wrapAfterExecThrowableMessage(messageUuid, accessibleObject, getExecutableObjectType(),
				exceptionWhileLoading, exceptionWhileInvoking);
		}

		return messageBuilder.buildReturnValue(peerUuid, valueObject, accessibleObject.get(), valueObjRef, false,
			messageUuid);
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
			return new InvocationExceptionWrapper(ex);
		}

		return newObject;
	}

	@Override
	protected Object invokeIncoming(Optional<AccessibleObject> accessibleObject, Object target,
																	List<Object> args, Optional<Object> value) throws Exception {
		Constructor constructor = (Constructor) accessibleObject.get();
		return constructor.newInstance(args.toArray(new Object[args.size()]));
	}

	@Override
	protected final boolean returnsVoid(Optional<AccessibleObject> accessibleObject) {
		return false;
	}

	@Override
	protected final Type getBeforeExecMessageType() {
		return Type.CONSTRUCTOR;
	}

	@Override
	protected final ExecutableObjectType getExecutableObjectType() {
		return ExecutableObjectType.CONSTRUCTOR;
	}

	@Override
	protected List<Primitives.Parameter> getParameterList(DataMessage dataMessage) {
		return dataMessage.getConstructorCall().getParameterList();
	}

	@Override
	protected AccessibleObject loadAccessibleObject(DataMessage dataMessage, List<Class> parameterTypes,
																									List<Object> args) throws ReflectiveOperationException {
		// TODO why are we not using ReflectionHelper to get the constructor?
		Class clazz = Class.forName(dataMessage.getConstructorCall().getClass_().getName(), true,
			Thread.currentThread().getContextClassLoader());
		return clazz.getDeclaredConstructor((Class[]) parameterTypes.toArray(new Class[parameterTypes.size()]));
	}
}
