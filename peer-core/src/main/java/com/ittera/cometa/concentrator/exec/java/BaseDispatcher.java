package com.ittera.cometa.concentrator.exec.java;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.AccessibleObject;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.Context;

import com.ittera.cometa.concentrator.exec.DispatcherConnector;

import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.inject.Inject;

public abstract class BaseDispatcher implements Dispatcher {

	protected final Logger logger = LoggerFactory.getLogger(this.getClass());

	protected UUID peerUuid;
	protected DataMessageBuilder messageBuilder;
	protected ObjectService objectService;
	protected DispatcherConnector connector;

	//TODO load from config
	protected static final boolean ENFORCE_JAVALANG_ACCESS = false;

	@Override
	public final Object dispatch(Context ctxt, Object sender, Object target, Object[] args)
		throws Throwable {

		logger.trace("dispatch:in w/ signature: {}, sender: {}, target: {}, args: {}", ctxt.getSignature(), sender,
			target, args);

		// 1. Wrap message
		final DataMessage beforeExecMsg = wrapBeforeExecMessage(ctxt, sender, target, args);

		// 2. Send message
		final DataMessage beforeExecReplyMsg = connector.sendAndRecv(beforeExecMsg);

		// TODO if beforeExecReplyMsg != beforeExecMsg, unpack and exec reply msg

		// 3. Invoke
		Object returnValue = invoke(ctxt, sender, target, args);

		// 4. Store? object in object map
		String objectRef = null;
		if (!returnsVoid() && returnValue != null) {
			objectRef = storeObject(returnValue);
		}

		// 5. Wrap object or exception
		final DataMessage afterExecMsg = wrapAfterExecMessage(ctxt, returnValue, objectRef);

		// 6. Send object or exception
		final DataMessage afterExecReplyMsg = connector.sendAndRecv(afterExecMsg);

		// TODO if afterExecReplyMsg != afterExecMsg, unpack exception or return value

		// 7. Return object or re-raise exception
		if (returnValue instanceof InvocationException) {
			logger.trace("dispatch:out re-raising exception: {}", returnValue);
			Exception invocationException = ((InvocationException) returnValue).getException();
			// we want to throw the cause exception
			if (invocationException instanceof InvocationTargetException) {
				throw invocationException.getCause();
			} else {
				throw invocationException;
			}
		}

		// TODO return Optional? for dispatch of voids, OR have our own Void class

		logger.trace("dispatch:out returning object: {}", returnValue);
		return returnValue;
	}

	@Override
	public DataMessage dispatchIncoming(DataMessage incomingCall) {

		String messageUuid = incomingCall.getMessageUuid();

		logger.trace("dispatchIncoming:in w/ message uuid: {}", incomingCall.getMessageUuid());

		/**TODO: Verify that message is invokable:
		 * - Class can be loaded/found
		 * - Method or field can be found in class
		 * - Parameters can be unwrapped or loaded (if refs). What if they are remote?
		 */

		/**TODO: What if this message has intercepts (i.e. around or sequential pre-) ?
		 * We should call an inner zmq service/connector and wait/get them, then execute that or go ahead and execute
		 * this message.
		 */

		Exception exceptionWhileLoading = null, exceptionWhileInvoking = null;
		AccessibleObject accessibleObject = null;
		Optional<Object> target = null, value = null;
		List<Object> args = null;

		// Loading phase
		try {
			// 1. Extract and load parameter types from message
			List<Class> parameterTypes = null;
			parameterTypes = getParameterTypesFromMessage(incomingCall);

			// 2. Unwrap and load arguments
			args = getArgsFromMessage(incomingCall, parameterTypes);

			// 3. Load constructor/method/field to call
			accessibleObject = loadAccessibleObject(incomingCall, parameterTypes, args);

			// 4. Load target for instance methods/field ops
			target = getTargetFromMessage(incomingCall);

			// 5. Load value for assigning field ops
			value = getValueFromMessage(incomingCall, accessibleObject);

			// 6. (Optionally) Set field/method accessible, allowing to break Java access rules
			if (!ENFORCE_JAVALANG_ACCESS) {
				accessibleObject.setAccessible(true);
			}
		} catch (Exception ex) {
			logger.error("Error during loading phase (before invocation)", ex);
			exceptionWhileLoading = ex;
		}

		// Invocation phase
		Object returnValue = null;
		String objectRef = null;
		if (exceptionWhileLoading == null) {
			try {
				// 7. Invoke constructor/method/field
				returnValue = invokeIncoming(accessibleObject, target, args, value);

				// 8. Store? object in object map
				if (!returnsVoid() && returnValue != null) {
					objectRef = storeObject(returnValue);
				}
			} catch (Exception e) {
				logger.error("Error during invocation phase", e);
				exceptionWhileInvoking = e;
			}
		}

		// 9. Wrap object or exception
		final DataMessage afterExecMsg = wrapAfterExecMessage(incomingCall, returnValue, objectRef, accessibleObject,
			exceptionWhileLoading, exceptionWhileInvoking);

		// 10. Send object or exception, and receive
		final DataMessage afterExecReplyMsg = connector.sendAndRecv(afterExecMsg);

		// 11. Return received message
		logger.trace("dispatchIncoming:out returning message: {}", afterExecReplyMsg);
		return afterExecReplyMsg;
	}

	private String storeObject(Object object) {
		return object != null ? objectService.storeObject(object) : null;
	}

	/**
	 * @param dataMessage
	 * @return List of loaded classes for each parameter, or null if dataMessage is not a call to constructor/method.
	 * @throws ClassNotFoundException
	 */
	private List<Class> getParameterTypesFromMessage(DataMessage dataMessage) throws ClassNotFoundException {

		/** TODO: after testing, refactor so that constructor param types are extracted same as method calls,
		 * for some reason we are not calling Unwrapper.getClassForPrimitive() for constructors.
		 * If it can't be done for constructor and methods, then push this down to Constructor and Method dispatchers */

		final List<Class> paramClasses = new ArrayList<>();
		List<Primitives.Parameter> parameterList = getParameterList(dataMessage);

		if (dataMessage.hasConstructorCall()) {
			for (Primitives.Parameter param : parameterList) {
				paramClasses.add(Class.forName(param.getType().getName()));
			}
		} else if (dataMessage.hasClassMethodCall() || dataMessage.hasInstanceMethodCall()) {
			for (Primitives.Parameter param : parameterList) {
				Primitives.Object obj = param.getValue();
				Class paramClass = Unwrapper.getClassForPrimitive(obj.getClass_().getName());
				if (paramClass == null) {
					paramClass = Class.forName(obj.getClass_().getName());
				}
				paramClasses.add(paramClass);
			}
		} else {
			return null;
		}

		return paramClasses;
	}

	private List<Object> getArgsFromMessage(DataMessage dataMessage, List<Class> parameterTypes) {

		List<Object> args = new ArrayList<>();
		List<Primitives.Parameter> parameterList = getParameterList(dataMessage);

		int i = 0;
		for (Primitives.Parameter parameter : parameterList) {
			Primitives.Object obj = parameter.getValue();
			if (obj.getIsNull()) {
				args.add(null);
			} else if (obj.hasRef()) {
				args.add(objectService.lookupObject(obj.getRef()));
			} else {
				args.add(Unwrapper.unwrapObject(obj, parameterTypes.get(i)));
			}
			i++;
		}

		return args;
	}

	/**
	 * To be overridden by dispatchers that assign a value (SetFieldDispatcher)
	 *
	 * @return
	 */
	protected Optional<Object> getValueFromMessage(DataMessage dataMessage, AccessibleObject accessibleObject) {
		return Optional.empty();
	}

	/**
	 * To be overridden by dispatchers that work on an instance method/variable
	 *
	 * @return
	 */
	protected Optional<Object> getTargetFromMessage(DataMessage dataMessage) throws ClassNotFoundException {
		return Optional.empty();
	}

	/**
	 * @param messageUuid
	 * @param accessibleObject
	 * @param exceptionWhileLoading  Either this or exceptionWhileInvoking must be non-null
	 * @param exceptionWhileInvoking
	 * @return
	 */
	protected final DataMessage wrapAfterExecThrowableMessage(String messageUuid, AccessibleObject accessibleObject,
																														Exception exceptionWhileLoading,
																														Exception exceptionWhileInvoking) {

		Exception exception = exceptionWhileLoading != null ? exceptionWhileLoading : exceptionWhileInvoking;
		return messageBuilder.buildAccessibleObjectThrowable(peerUuid, accessibleObject, exception, messageUuid);
	}

	@Inject
	protected final void setPeerUuid(UUID peerUuid) {
		this.peerUuid = peerUuid;
	}

	@Inject
	protected final void setMessageBuilder(DataMessageBuilder messageBuilder) {
		this.messageBuilder = messageBuilder;
	}

	@Inject
	protected final void setObjectService(ObjectService objectService) {
		this.objectService = objectService;
	}

	@Inject
	protected final void setConnector(DispatcherConnector connector) {
		this.connector = connector;
	}

	abstract protected DataMessage wrapBeforeExecMessage(Context ctxt, Object sender, Object target,
																											 Object[] args);

	// TODO generalize this method, using a Builder method taking Executable's
	// TODO create a Builder.buildVoidReturnValue() method
	abstract protected DataMessage wrapAfterExecMessage(Context ctxt, Object value, String objectRef);

	abstract protected DataMessage wrapAfterExecMessage(DataMessage dataMessage, Object valueObject, String valueObjKey,
																											AccessibleObject accessibleObject, Exception exceptionWhileLoading,
																											Exception exceptionWhileInvoking);

	abstract protected Object invoke(Context ctxt, Object sender, Object target, Object[] args);

	/**
	 * @param accessibleObject
	 * @param target           Present only for instance methods/field ops
	 * @param args
	 * @param value            Present only for value-assigning field ops.
	 * @return
	 * @throws Exception
	 */
	abstract protected Object invokeIncoming(AccessibleObject accessibleObject, Optional<Object> target, List<Object> args,
																					 Optional<Object> value) throws Exception;

	abstract protected boolean returnsVoid();

	abstract protected Type getBeforeExecMessageType();

	abstract protected Type getAfterExecMessageType();

	abstract protected List<Primitives.Parameter> getParameterList(DataMessage dataMessage);

	/**
	 * @param dataMessage
	 * @param parameterTypes Used only by constructor and method dispatchers
	 * @param args           Used only by method dispatchers
	 * @return
	 * @throws ReflectiveOperationException
	 */
	abstract protected AccessibleObject loadAccessibleObject(DataMessage dataMessage, List<Class> parameterTypes,
																													 List<Object> args) throws ReflectiveOperationException;
}