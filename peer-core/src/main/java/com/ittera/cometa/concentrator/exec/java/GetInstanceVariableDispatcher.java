package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.ObjectNotFoundException;
import com.ittera.cometa.common.lang.ObjectRef;

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
	protected Object getTargetFromMessage(DataMessage dataMessage, Optional<AccessibleObject> accessibleObject) throws
		ObjectNotFoundException {
		Object target;
		if (dataMessage.getInstanceFieldGet().hasObject()) {
			Class fieldType = ((Field) accessibleObject.get()).getType();
			target = Unwrapper.unwrapObject(dataMessage.getInstanceFieldGet().getObject(), fieldType);
			logger.debug("Unwrapped target: {}", target);
		} else {
			ObjectRef targetObjRef = ObjectRef.from(dataMessage.getInstanceFieldGet().getObjectRef());
			if (objectService.containsObjectRef(targetObjRef)) {
				target = objectService.lookupObject(targetObjRef);
			} else {
				throw new ObjectNotFoundException(String.format("No object found with objRef: %s", targetObjRef.getRef()));
			}
			logger.debug("Loaded target: {}", target);
		}
		return target;
	}

	@Override
	protected AccessibleObject loadAccessibleObject(Wrappers.DataMessage dataMessage, List<Class> parameterTypes,
																									List<Object> args) throws ReflectiveOperationException {

		Class clazz = Class.forName(dataMessage.getInstanceFieldGet().getClass_().getName(), true,
			Thread.currentThread().getContextClassLoader());
		AccessibleObject accessibleObject = clazz.getDeclaredField(dataMessage.getInstanceFieldGet().getField().getName());
		return accessibleObject;
	}
}
