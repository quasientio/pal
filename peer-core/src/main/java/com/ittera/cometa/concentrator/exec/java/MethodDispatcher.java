package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.ObjectRef;

import com.ittera.cometa.common.lang.reflect.AccessibleObjectType;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.lang.reflect.Method;
import java.lang.reflect.AccessibleObject;

import java.util.List;
import java.util.Optional;

public abstract class MethodDispatcher extends BaseDispatcher {

	@Override
	protected Object invokeIncoming(Optional<AccessibleObject> accessibleObject, Optional<Object> target, List<Object> args,
																	Optional<Object> value) throws Exception {

		Method method = (Method) accessibleObject.get();
		return method.invoke(target.orElse(null), args.toArray());
	}

	@Override
	protected DataMessage wrapAfterExecMessage(DataMessage dataMessage, Object valueObject, ObjectRef valueObjRef,
																						 Optional<AccessibleObject> accessibleObject, Throwable exceptionWhileLoading,
																						 Throwable exceptionWhileInvoking) {

		String messageUuid = dataMessage.getMessageUuid();

		if (exceptionWhileLoading != null || exceptionWhileInvoking != null) {
			return wrapAfterExecThrowableMessage(messageUuid, accessibleObject, getAccessibleObjectType(),
				exceptionWhileLoading, exceptionWhileInvoking);
		}

		Class methodReturnType = accessibleObject.map(ao -> ((Method) ao).getReturnType()).orElse(null);
		return messageBuilder.buildReturnValue(peerUuid, valueObject, methodReturnType, valueObjRef,
			returnsVoid(accessibleObject), messageUuid);
	}

	@Override
	protected boolean returnsVoid(Optional<AccessibleObject> accessibleObject) {
		return accessibleObject.map(ao -> ((Method) ao).getReturnType())
			.map(java.lang.Void.TYPE::equals).get();
	}

	@Override
	protected final AccessibleObjectType getAccessibleObjectType() {
		return AccessibleObjectType.METHOD;
	}
}
