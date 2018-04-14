package com.ittera.cometa.concentrator.exec.java;

import java.util.UUID;

import java.lang.reflect.InvocationTargetException;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.lang.Context;

import com.ittera.cometa.concentrator.exec.DispatcherConnector;
import com.ittera.cometa.messages.DataMessageBuilder;
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

	@Override
	public final Object dispatch(Context ctxt, Object sender, Object target, Object[] args)
		throws Throwable {

		logger.trace("dispatch:in w/ signature: {}, sender: {}, target: {}, args: {}", ctxt.getSignature(), sender,
			target, args);

		// 1. Wrap message
		final DataMessage beforeExecMsg = wrapBeforeExecMessage(ctxt, sender, target, args);

		// 2. Send message
		final DataMessage beforeExecReplyMsg = connector.sendAndRecv(beforeExecMsg);

		// 3. Invoke
		// TODO if beforeExecReplyMsg != beforeExecMsg, unpack and exec reply msg
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

		// 7. Return object or re-raise exception
		// TODO if afterExecReplyMsg != afterExecMsg, unpack exception or return value
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

	private String storeObject(Object object) {
		return object != null ? objectService.storeObject(object) : null;
	}

	abstract protected DataMessage wrapBeforeExecMessage(Context ctxt, Object sender, Object target,
																											 Object[] args);

	// TODO generalize this method, using a Builder method taking Executable's
	// TODO create a Builder.buildVoidReturnValue() method
	abstract protected DataMessage wrapAfterExecMessage(Context ctxt, Object value, String objectRef);

	abstract protected Object invoke(Context ctxt, Object sender, Object target, Object[] args);

	abstract protected boolean returnsVoid();

	abstract protected Type getBeforeExecMessageType();

	abstract protected Type getAfterExecMessageType();

	@Inject
	protected void setPeerUuid(UUID peerUuid) {
		this.peerUuid = peerUuid;
	}

	@Inject
	protected void setMessageBuilder(DataMessageBuilder messageBuilder) {
		this.messageBuilder = messageBuilder;
	}

	@Inject
	protected void setObjectService(ObjectService objectService) {
		this.objectService = objectService;
	}

	@Inject
	protected void setConnector(DispatcherConnector connector) {
		this.connector = connector;
	}

}