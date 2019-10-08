package com.ittera.cometa.core.exec.java;

import com.ittera.cometa.common.ObjectService;

import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.core.exec.DispatcherConnector;

import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.Unwrapper;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public class SetClassVariableDispatcher extends SetFieldDispatcher {

	@Singleton
	@Inject
	public SetClassVariableDispatcher(UUID peerUuid, MessageBuilder messageBuilder, DispatcherConnector connector,
																		ObjectService objectService) {
		setPeerUuid(peerUuid);
		setMessageBuilder(messageBuilder);
		setConnector(connector);
		setObjectService(objectService);
	}

	@Override
	protected final Type getBeforeExecMessageType() {
		return Type.PUT_STATIC;
	}

	@Override
	protected final Type getAfterExecMessageType() {
		return Type.PUT_STATIC_DONE;
	}

	@Override
	protected AccessibleObject loadAccessibleObject(Wrappers.ExecMessage execMessage, List<Class> parameterTypes,
																									List<Object> args) throws ReflectiveOperationException {

		Class clazz = Class.forName(execMessage.getStaticFieldPut().getClass_().getName(), true,
			Thread.currentThread().getContextClassLoader());
		return clazz.getDeclaredField(execMessage.getStaticFieldPut().getField().getName());
	}

	@Override
	protected Optional<Object> getValueFromMessage(final ExecMessage execMessage,
																								 final Optional<AccessibleObject> accessibleObject) {

		final Object value;
		final Field field = (Field) accessibleObject.get();

		if (execMessage.getStaticFieldPut().hasValueObject()) {
			value = Unwrapper.unwrapObject(execMessage.getStaticFieldPut().getValueObject(), field.getType());
			if (logger.isTraceEnabled()) {
				logger.trace("Unwrapped value: {}", value);
			}
		} else {
			value = objectService.lookupObject(ObjectRef.from(execMessage.getStaticFieldPut().getValueObjectRef()));
			if (logger.isTraceEnabled()) {
				logger.trace("Loaded value: {}", value);
			}
		}

		return Optional.ofNullable(value);
	}

	@Override
	protected ExecMessage wrapAfterExecMessage(ExecMessage execMessage, Object valueObject, ObjectRef valueObjRef,
																						 Optional<AccessibleObject> accessibleObject,
																						 Throwable exceptionWhileLoading, Throwable exceptionWhileInvoking) {
		String messageUuid = execMessage.getMessageUuid();
		if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
			return wrapAfterExecThrowableMessage(messageUuid, accessibleObject, getExecutableObjectType(),
				exceptionWhileLoading, exceptionWhileInvoking);
		}
		return messageBuilder.buildPutStaticDone(peerUuid, accessibleObject.get(), messageUuid, messageUuid);
	}
}
