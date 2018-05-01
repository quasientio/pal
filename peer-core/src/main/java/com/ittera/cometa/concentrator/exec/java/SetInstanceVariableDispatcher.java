package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.ObjectService;

import com.ittera.cometa.concentrator.exec.DispatcherConnector;

import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.Unwrapper;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public class SetInstanceVariableDispatcher extends SetFieldDispatcher {

	@Singleton
	@Inject
	public SetInstanceVariableDispatcher(UUID peerUuid, DataMessageBuilder messageBuilder, DispatcherConnector connector,
																			 ObjectService objectService) {
		setPeerUuid(peerUuid);
		setMessageBuilder(messageBuilder);
		setConnector(connector);
		setObjectService(objectService);
	}

	@Override
	protected final Type getBeforeExecMessageType() {
		return Type.PUT_FIELD;
	}

	@Override
	protected final Type getAfterExecMessageType() {
		return Type.PUT_FIELD_DONE;
	}

	@Override
	protected Optional<Object> getTargetFromMessage(DataMessage dataMessage) throws ClassNotFoundException {
		Object target;
		if (dataMessage.getInstanceFieldPut().hasObject()) {
			Class objClass = Class.forName(dataMessage.getInstanceFieldPut().getClass_().getName());
			// originally in Concentrator:
			//target = Unwrapper.unwrapObject(dataMessage.getInstanceFieldPut().getObject(), field.getType());
			target = Unwrapper.unwrapObject(dataMessage.getInstanceFieldPut().getObject(), objClass);
			logger.debug("Unwrapped target: {}", target);
		} else {
			target = objectService.lookupObject(dataMessage.getInstanceFieldPut().getObjectRef());
			logger.debug("Loaded target: {}", target);
		}
		return Optional.of(target);
	}

	@Override
	protected AccessibleObject loadAccessibleObject(Wrappers.DataMessage dataMessage, List<Class> parameterTypes,
																									List<Object> args) throws ReflectiveOperationException {

		Class clazz = Class.forName(dataMessage.getInstanceFieldPut().getClass_().getName());
		AccessibleObject accessibleObject = clazz.getDeclaredField(dataMessage.getInstanceFieldPut().getField().getName());
		return accessibleObject;
	}

	@Override
	protected Optional<Object> getValueFromMessage(final DataMessage dataMessage, final AccessibleObject accessibleObject) {

		final Object value;
		final Field field = (Field) accessibleObject;

		if (dataMessage.getInstanceFieldPut().hasValueObject()) {
			value = Unwrapper.unwrapObject(dataMessage.getInstanceFieldPut().getValueObject(), field.getType());
			logger.debug("Unwrapped value: {}", value);
		} else {
			value = objectService.lookupObject(dataMessage.getInstanceFieldPut().getValueObjectRef());
			logger.debug("Loaded value: {}", value);
		}
		return Optional.of(value);
	}

	@Override
	protected DataMessage wrapAfterExecMessage(DataMessage dataMessage, Object valueObject, String valueObjKey,
																						 AccessibleObject accessibleObject, Exception exceptionWhileLoading,
																						 Exception exceptionWhileInvoking) {

		String messageUuid = dataMessage.getMessageUuid();

		if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
			return wrapAfterExecThrowableMessage(messageUuid, accessibleObject, exceptionWhileLoading, exceptionWhileInvoking);
		}

		Class fieldType = ((Field) accessibleObject).getType();
		return messageBuilder.buildPutObjectDone(peerUuid, messageUuid, dataMessage.getInstanceFieldPut(), fieldType,
			messageUuid);
	}
}
