package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.MethodSignature;

import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import com.ittera.cometa.concentrator.util.ReflectionHelper;
import com.ittera.cometa.concentrator.exec.DispatcherConnector;

import java.lang.reflect.Method;
import java.lang.reflect.AccessibleObject;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

public class InstanceMethodDispatcher extends MethodDispatcher {

	@Singleton
	@Inject
	public InstanceMethodDispatcher(UUID peerUuid, DataMessageBuilder messageBuilder, DispatcherConnector connector,
																	ObjectService objectService) {
		setPeerUuid(peerUuid);
		setMessageBuilder(messageBuilder);
		setConnector(connector);
		setObjectService(objectService);
	}

	@Override
	protected final DataMessage wrapBeforeExecMessage(Context ctxt, Object sender, Object target, Object[] args) {
		return messageBuilder.buildInstanceMethod(peerUuid, ctxt, sender, storeObject(sender), target, storeObject(target),
			args, Arrays.stream(args).map(a -> storeObject(a)).toArray(ObjectRef[]::new));
	}

	@Override
	protected DataMessage wrapAfterExecMessage(Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

		final Method method = ((MethodSignature) ctxt.getSignature()).getMethod();
		if (value instanceof InvocationException) {
			Exception invocationException = ((InvocationException) value).getException();
			return messageBuilder.buildAccessibleObjectThrowable(peerUuid, method, invocationException, null);
		} else {
			return messageBuilder.buildReturnValue(peerUuid, value, method.getClass(), objectRef, isVoid, null);
		}

	}

	@Override
	protected final Object invoke(Context ctxt, Object sender, Object target, Object[] args) {

		final MethodSignature methodSignature = (MethodSignature) ctxt.getSignature();
		Method method = methodSignature.getMethod();

		method.setAccessible(true);
		Object returnValue;
		try {
			returnValue = method.invoke(target, args);
		} catch (Exception ex) {
			logger.error("Caught exception while invoking instance method. Will wrap and return it.", ex);
			return new InvocationException(ex);
		}

		if (method.getReturnType().equals(java.lang.Void.TYPE)) {
			return Void.getInstance();
		} else {
			return returnValue;
		}
	}

	@Override
	protected final Type getBeforeExecMessageType() {
		return Type.INSTANCE_METHOD;
	}

	@Override
	protected List<Primitives.Parameter> getParameterList(DataMessage dataMessage) {
		return dataMessage.getInstanceMethodCall().getParameterList();
	}

	@Override
	protected Optional<Object> getTargetFromMessage(DataMessage dataMessage) throws ClassNotFoundException {
		Object target;
		if (dataMessage.getInstanceMethodCall().hasObject()) {
			Class objClass = Class.forName(dataMessage.getInstanceMethodCall().getClass_().getName());
			target = Unwrapper.unwrapObject(dataMessage.getInstanceMethodCall().getObject(), objClass);
			logger.debug("Unwrapped target: {}", target);
		} else {
			target = objectService.lookupObject(ObjectRef.from(dataMessage.getInstanceMethodCall().getObjectRef()));
			logger.debug("Loaded target: {}", target);
		}
		return Optional.of(target);
	}

	/**
	 * @param dataMessage
	 * @param parameterTypes Not used here.
	 * @param args
	 * @return
	 * @throws ReflectiveOperationException
	 */
	@Override
	protected AccessibleObject loadAccessibleObject(DataMessage dataMessage, List<Class> parameterTypes,
																									List<Object> args) throws ReflectiveOperationException {
		Class clazz = Class.forName(dataMessage.getInstanceMethodCall().getClass_().getName());
		AccessibleObject accessibleObject = ReflectionHelper.getMethodToInvoke(clazz, args.toArray(),
			dataMessage.getInstanceMethodCall().getParameterList().stream().map(p -> p.getValue()).collect(Collectors.toList()),
			dataMessage.getInstanceMethodCall().getName());
		if (accessibleObject == null) {
			//TODO perhaps this should be thrown by ReflectionHelper instead
			throw new NoSuchMethodException(String.format("Can't find method:%s in class:%s with given parameter types",
				dataMessage.getInstanceMethodCall().getName(), clazz.getName()));
		}
		return accessibleObject;
	}
}
