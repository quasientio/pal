package com.ittera.cometa.concentrator.exec.java;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.AccessibleObject;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.Dispatcher;
import com.ittera.cometa.common.lang.ObjectNotFoundException;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ExecutableObjectType;

import com.ittera.cometa.common.util.Classes;
import com.ittera.cometa.concentrator.exec.DispatcherConnector;

import com.ittera.cometa.messages.ExecMessageBuilder;
import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.inject.Inject;

public abstract class BaseDispatcher implements Dispatcher, ExecMessageDispatcher {

	final Logger logger = LoggerFactory.getLogger(this.getClass());

	UUID peerUuid;
	ExecMessageBuilder messageBuilder;
	ObjectService objectService;
	private DispatcherConnector connector;

	//TODO load from config
	private static final boolean ENFORCE_JAVALANG_ACCESS = false;

	@Override
	public final Object dispatch(Context ctxt, Object sender, Object target, Object[] args)
		throws Throwable {

		if (logger.isTraceEnabled()) {
			logger.trace("dispatch:in w/ signature: {}, sender: {}, target: {}, args: {}", ctxt.getSignature(), sender,
				target, args);
		}

		// 1. Wrap message
		final ExecMessage beforeExecMsg = wrapBeforeExecMessage(ctxt, sender, target, args);

		// 2. Send message
		final ExecMessage beforeExecReplyMsg = connector.sendAndRecv(beforeExecMsg);

		// TODO if beforeExecReplyMsg != beforeExecMsg, unpack and exec reply msg

		// 3. Invoke
		Object returnValue = invoke(ctxt, sender, target, args);
		if (logger.isTraceEnabled()) {
			logger.trace("invoke() returned: {}", returnValue);
		}

		// 4. Store? object in object map
		ObjectRef objectRef = null;
		boolean returnsVoid = returnValue == Void.getInstance();

		if (!returnsVoid && returnValue != null) {
			objectRef = storeObject(returnValue);
		}

		// 5. Wrap object or exception
		final ExecMessage afterExecMsg = wrapAfterExecMessage(ctxt, returnValue, objectRef, returnsVoid);

		// 6. Send object or exception
		final ExecMessage afterExecReplyMsg = connector.sendAndRecv(afterExecMsg);

		// TODO if afterExecReplyMsg != afterExecMsg, unpack exception or return value

		// 7. Return object or re-raise exception
		if (returnValue instanceof InvocationExceptionWrapper) {
			if (logger.isTraceEnabled()) {
				logger.trace("dispatch:out re-raising exception: {}", returnValue);
			}
			Exception invocationException = ((InvocationExceptionWrapper) returnValue).getException();
			// we want to throw the cause exception
			if (invocationException instanceof InvocationTargetException) {
				throw invocationException.getCause();
			} else {
				throw invocationException;
			}
		}

		// TODO return Optional? for dispatch of voids, OR have our own Void class

		if (logger.isTraceEnabled()) {
			logger.trace("dispatch:out returning object: {}", returnValue);
		}
		return returnValue;
	}

	@Override
	public ExecMessage dispatchIncoming(ExecMessage incomingCall) {
		return dispatchIncoming(incomingCall, true);
	}

	@Override
	public ExecMessage dispatchIncoming(ExecMessage incomingCall, boolean isDirect) {
		if (logger.isTraceEnabled()) {
			logger.trace("dispatchIncoming:in w/ message uuid: {}, isDirect: {}", incomingCall.getMessageUuid(), isDirect);
		}

		/**TODO: Verify that message is invokable:
		 * - Class can be loaded/found
		 * - Method or field can be found in class
		 * - Parameters can be unwrapped or loaded (if refs). What if they are remote?
		 */

		/**TODO: What if this message has intercepts (i.e. around or sequential pre-) ?
		 * We should call an inner zmq service/connector and wait/get them, then execute that or go ahead and execute
		 * this message.
		 */

		// message doesn't come from log so we write-ahead before executing
		if (isDirect) {
			connector.writeAhead(incomingCall);
		}

		Throwable exceptionWhileLoading = null, exceptionWhileInvoking = null;
		Optional<AccessibleObject> accessibleObject = Optional.empty();
		Object target = null;
		Optional<Object> value = Optional.empty();
		List<Object> args = null;

		// Loading phase
		try {
			// 1. Extract and load parameter types from message
			List<Class> parameterTypes = getParameterTypesFromMessage(incomingCall);

			// 2. Unwrap and load arguments
			args = getArgsFromMessage(incomingCall, parameterTypes);

			// 3. Load constructor/method/field to call
			accessibleObject = Optional.of(loadAccessibleObject(incomingCall, parameterTypes, args));

			// 4. Load target for instance methods/field ops
			target = getTargetFromMessage(incomingCall, accessibleObject);

			// 5. Load value for assigning field ops
			value = getValueFromMessage(incomingCall, accessibleObject);

			// 6. (Optionally) Set field/method accessible, allowing to break Java access rules
			if (!ENFORCE_JAVALANG_ACCESS) {
				accessibleObject.ifPresent(aobj -> aobj.setAccessible(true));
			}
		} catch (Exception ex) {
			logger.error("Error during loading phase (before invocation)", ex);
			exceptionWhileLoading = ex;
		}

		// Invocation phase
		Object returnValue = null;
		ObjectRef objectRef = null;
		if (exceptionWhileLoading == null) {
			try {
				// 7. Invoke constructor/method/field
				returnValue = invokeIncoming(accessibleObject, target, args, value);

				// 8. Store? object in object map
				if (!returnsVoid(accessibleObject) && returnValue != null) {
					objectRef = storeObject(returnValue);
				}
			} catch (Exception e) {
				logger.error("Error during invocation phase", e);
				exceptionWhileInvoking = e.getCause();
			}
		}

		// 9. Wrap object or exception
		final ExecMessage afterExecMsg = wrapAfterExecMessage(incomingCall, returnValue, objectRef, accessibleObject,
			exceptionWhileLoading, exceptionWhileInvoking);

		// 10. Send object or exception, and receive
		final ExecMessage afterExecReplyMsg = connector.sendAndRecv(afterExecMsg);

		// 11. Return received message
		if (logger.isTraceEnabled()) {
			logger.trace("dispatchIncoming:out returning message: {}", afterExecReplyMsg);
		}
		return afterExecReplyMsg;
	}

	final ObjectRef storeObject(Object object) {
		return object != null ? objectService.storeObject(object) : null;
	}

	/**
	 * @param execMessage
	 * @return List of loaded classes for each parameter, or null if execMessage is not a call to constructor/method.
	 * @throws ClassNotFoundException
	 */
	private List<Class> getParameterTypesFromMessage(ExecMessage execMessage) throws ClassNotFoundException {

		final List<Class> paramClasses = new ArrayList<>();
		List<Primitives.Parameter> parameterList = getParameterList(execMessage);

		if (execMessage.hasConstructorCall() || execMessage.hasClassMethodCall() || execMessage.hasInstanceMethodCall()) {
			for (Primitives.Parameter param : parameterList) {
				Class paramClass = Classes.getClassForPrimitive(param.getType().getName());
				if (paramClass == null) { // ie. not a primitive
					paramClass = Class.forName(param.getType().getName(), true, Thread.currentThread().getContextClassLoader());
				}
				paramClasses.add(paramClass);
			}
		} else {
			return null;
		}

		return paramClasses;
	}

	private List<Object> getArgsFromMessage(ExecMessage execMessage, List<Class> parameterTypes) {

		List<Object> args = new ArrayList<>();
		List<Primitives.Parameter> parameterList = getParameterList(execMessage);

		int i = 0;
		if (parameterList != null) {
			for (Primitives.Parameter parameter : parameterList) {
				Primitives.Object obj = parameter.getValue();
				if (obj.getIsNull()) {
					args.add(null);
				} else if (obj.hasRef()) {
					args.add(objectService.lookupObject(ObjectRef.from(obj.getRef())));
				} else {
					args.add(Unwrapper.unwrapObject(obj, parameterTypes.get(i)));
				}
				i++;
			}
		}

		return args;
	}

	/**
	 * To be overridden by dispatchers that assign a value (SetFieldDispatcher)
	 *
	 * @return
	 */
	Optional<Object> getValueFromMessage(ExecMessage execMessage, Optional<AccessibleObject> accessibleObject) {
		return Optional.empty();
	}

	/**
	 * To be overridden by dispatchers that work on an instance method/variable
	 *
	 * @return
	 */
	Object getTargetFromMessage(ExecMessage execMessage, Optional<AccessibleObject> accessibleObject)
		throws ClassNotFoundException, ObjectNotFoundException {
		return null;
	}

	/**
	 * @param messageUuid
	 * @param accessibleObject
	 * @param executableObjectType
	 * @param exceptionWhileLoading  Either this or exceptionWhileInvoking must be non-null
	 * @param exceptionWhileInvoking
	 * @return
	 */
	final ExecMessage wrapAfterExecThrowableMessage(String messageUuid,
																									Optional<AccessibleObject> accessibleObject,
																									ExecutableObjectType executableObjectType,
																									Throwable exceptionWhileLoading,
																									Throwable exceptionWhileInvoking) {

		Throwable throwable = exceptionWhileLoading != null ? exceptionWhileLoading : exceptionWhileInvoking;
		return messageBuilder.buildAccessibleObjectThrowable(peerUuid, accessibleObject, executableObjectType,
			throwable, messageUuid);
	}

	@Inject
	final void setPeerUuid(UUID peerUuid) {
		this.peerUuid = peerUuid;
	}

	@Inject
	final void setMessageBuilder(ExecMessageBuilder messageBuilder) {
		this.messageBuilder = messageBuilder;
	}

	@Inject
	final void setObjectService(ObjectService objectService) {
		this.objectService = objectService;
	}

	@Inject
	final void setConnector(DispatcherConnector connector) {
		this.connector = connector;
	}

	abstract protected ExecMessage wrapBeforeExecMessage(Context ctxt, Object sender, Object target,
																											 Object[] args);

	// TODO generalize this method, using a Builder method taking Executable's
	// TODO create a Builder.buildVoidReturnValue() method
	abstract protected ExecMessage wrapAfterExecMessage(Context ctxt, Object value, ObjectRef objectRef, boolean isVoid);

	abstract protected ExecMessage wrapAfterExecMessage(ExecMessage execMessage, Object valueObject, ObjectRef valueObjRef,
																											Optional<AccessibleObject> accessibleObject,
																											Throwable exceptionWhileLoading, Throwable exceptionWhileInvoking);

	abstract protected Object invoke(Context ctxt, Object sender, Object target, Object[] args);

	/**
	 * @param accessibleObject
	 * @param target           Present only for instance methods/field ops
	 * @param args
	 * @param value            Present only for value-assigning field ops.
	 * @return
	 * @throws Exception
	 */
	abstract protected Object invokeIncoming(Optional<AccessibleObject> accessibleObject, Object target, List<Object> args,
																					 Optional<Object> value) throws Exception;

	abstract protected boolean returnsVoid(Optional<AccessibleObject> accessibleObject);

	abstract protected Type getBeforeExecMessageType();

	/**
	 * We need this method and the ExecutableObjectType enum for cases where a Field, Constructor or Method fails to
	 * be loaded (i.e. exceptionWhileLoading), and we require at least information about the type of accessible to
	 * include in the Throwable message
	 *
	 * @return
	 */
	abstract protected ExecutableObjectType getExecutableObjectType();

	abstract protected List<Primitives.Parameter> getParameterList(ExecMessage execMessage);

	/**
	 * @param execMessage
	 * @param parameterTypes Used only by constructor and method dispatchers
	 * @param args           Used only by method dispatchers
	 * @return
	 * @throws ReflectiveOperationException
	 */
	abstract protected AccessibleObject loadAccessibleObject(ExecMessage execMessage, List<Class> parameterTypes,
																													 List<Object> args) throws ReflectiveOperationException;
}