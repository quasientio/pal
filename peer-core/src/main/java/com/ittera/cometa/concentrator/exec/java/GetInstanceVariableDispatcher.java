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

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public class GetInstanceVariableDispatcher extends GetFieldDispatcher {

	@Singleton
	@Inject
	public GetInstanceVariableDispatcher(UUID peerUuid, DataMessageBuilder messageBuilder, DispatcherConnector connector,
																			 ObjectService objectService) {
		setPeerUuid(peerUuid);
		setMessageBuilder(messageBuilder);
		setConnector(connector);
		setObjectService(objectService);
	}

	@Override
	protected final Type getBeforeExecMessageType() {
		return Type.GET_FIELD;
	}

	@Override
	protected final Type getAfterExecMessageType() {
		return Type.RETURN_VALUE;
	}

	@Override
	protected Optional<Object> getTargetFromMessage(DataMessage dataMessage) throws ClassNotFoundException {
		Object target;
		if (dataMessage.getInstanceFieldGet().hasObject()) {
			Class objClass = Class.forName(dataMessage.getInstanceFieldGet().getClass_().getName());
			target = Unwrapper.unwrapObject(dataMessage.getInstanceFieldGet().getObject(), objClass);
			logger.debug("Unwrapped target: {}", target);
		} else {
			target = objectService.lookupObject(dataMessage.getInstanceFieldGet().getObjectRef());
			logger.debug("Loaded target: {}", target);
		}
		return Optional.of(target);
	}

	@Override
	protected AccessibleObject loadAccessibleObject(Wrappers.DataMessage dataMessage, List<Class> parameterTypes,
																									List<Object> args) throws ReflectiveOperationException {

		Class clazz = Class.forName(dataMessage.getInstanceFieldGet().getClass_().getName());
		AccessibleObject accessibleObject = clazz.getDeclaredField(dataMessage.getInstanceFieldGet().getField().getName());
		return accessibleObject;
	}
}
