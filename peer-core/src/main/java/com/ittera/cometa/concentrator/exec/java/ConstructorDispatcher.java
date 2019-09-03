package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ExecutableObjectType;
import com.ittera.cometa.common.lang.reflect.ConstructorSignature;

import com.ittera.cometa.concentrator.exec.DispatcherConnector;

import com.ittera.cometa.messages.ExecMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import javax.inject.Singleton;
import javax.inject.Inject;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;

import java.util.Arrays;
import java.util.UUID;
import java.util.List;
import java.util.Optional;

import static java.lang.Class.*;

public class ConstructorDispatcher extends BaseDispatcher {

	@Singleton
	@Inject
	public ConstructorDispatcher(UUID peerUuid, ExecMessageBuilder messageBuilder, DispatcherConnector connector,
															 ObjectService objectService) {
		setPeerUuid(peerUuid);
		setMessageBuilder(messageBuilder);
		setConnector(connector);
		setObjectService(objectService);
	}

	@Override
	protected final ExecMessage wrapBeforeExecMessage(Context ctxt, Object sender, Object target, Object[] args) {

		return messageBuilder.buildConstructor(peerUuid, ctxt, sender, storeObject(sender), args, Arrays.stream(args).map(
			this::storeObject).toArray(ObjectRef[]::new));
	}

	@Override
	protected final ExecMessage wrapAfterExecMessage(Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

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
	protected ExecMessage wrapAfterExecMessage(ExecMessage execMessage, Object valueObject, ObjectRef valueObjRef,
																						 Optional<AccessibleObject> accessibleObject, Throwable exceptionWhileLoading,
																						 Throwable exceptionWhileInvoking) {

		String messageUuid = execMessage.getMessageUuid();

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
		return constructor.newInstance(args.toArray(new Object[0]));
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
	protected List<Primitives.Parameter> getParameterList(ExecMessage execMessage) {
		return execMessage.getConstructorCall().getParameterList();
	}

	@Override
	protected AccessibleObject loadAccessibleObject(ExecMessage execMessage, List<Class> parameterTypes,
																									List<Object> args) throws ReflectiveOperationException {
		// TODO why are we not using ReflectionHelper to get the constructor?
		Class clazz = forName(execMessage.getConstructorCall().getClass_().getName(), true,
			Thread.currentThread().getContextClassLoader());
		return clazz.getDeclaredConstructor(parameterTypes.toArray(new Class[0]));
	}
}
