package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.MethodSignature;
import com.ittera.cometa.common.ObjectService;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.DataMessageBuilder;

import com.ittera.cometa.concentrator.util.ReflectionHelper;
import com.ittera.cometa.concentrator.exec.DispatcherConnector;

import java.lang.reflect.Method;
import java.lang.reflect.AccessibleObject;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.UUID;

import javax.inject.Singleton;
import javax.inject.Inject;

public class ClassMethodDispatcher extends MethodDispatcher {

	@Singleton
	@Inject
	public ClassMethodDispatcher(UUID peerUuid, DataMessageBuilder messageBuilder, DispatcherConnector connector,
															 ObjectService objectService) {
		setPeerUuid(peerUuid);
		setMessageBuilder(messageBuilder);
		setConnector(connector);
		setObjectService(objectService);
	}

	@Override
	protected final DataMessage wrapBeforeExecMessage(Context ctxt, Object sender, Object target, Object[] args) {
		return messageBuilder.buildClassMethod(peerUuid, ctxt, sender, storeObject(sender), args, Arrays.stream(args).map(
			a -> storeObject(a)).toArray(ObjectRef[]::new));
	}

	@Override
	protected DataMessage wrapAfterExecMessage(Context ctxt, Object value, ObjectRef objectRef, boolean isVoid) {

		final Method method = ((MethodSignature) ctxt.getSignature()).getMethod();

		if (value instanceof InvocationException) {
			Exception invocationException = ((InvocationException) value).getException();
			return messageBuilder.buildAccessibleObjectThrowable(peerUuid, method, invocationException, null);
		} else {
			return messageBuilder.buildReturnValue(peerUuid, value, method.getReturnType(), objectRef, isVoid,
				null);
		}
	}

	@Override
	protected final Object invoke(Context ctxt, Object sender, Object target, Object[] args) {

		final MethodSignature methodSignature = (MethodSignature) ctxt.getSignature();
		Method method = methodSignature.getMethod();

		Object returnValue;
		method.setAccessible(true);
		try {
			returnValue = method.invoke(null, args);
		} catch (Exception ex) {
			logger.error("Caught exception while invoking class method. Will wrap and return it.", ex);
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
		return Type.CLASS_METHOD;
	}

	@Override
	protected List<Primitives.Parameter> getParameterList(DataMessage dataMessage) {
		return dataMessage.getClassMethodCall().getParameterList();
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

		Class clazz = Class.forName(dataMessage.getClassMethodCall().getClass_().getName());
		AccessibleObject accessibleObject = ReflectionHelper.getMethodToInvoke(clazz, args.toArray(),
			dataMessage.getClassMethodCall().getParameterList().stream().map(p -> p.getValue()).collect(Collectors.toList()),
			dataMessage.getClassMethodCall().getName());
		if (accessibleObject == null) {
			throw new NoSuchMethodException(String.format("Can't find method:%s in class:%s with given parameter types",
				dataMessage.getClassMethodCall().getName(), clazz.getName()));
		}
		return accessibleObject;
	}

}
