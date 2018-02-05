package com.ittera.cometa.concentrator.exec.java;

import java.util.UUID;

import java.lang.reflect.InvocationTargetException;

import org.aspectj.lang.JoinPoint.StaticPart;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import zmq.ZError;

import javax.inject.Inject;

public abstract class BaseDispatcher {

	protected static final Logger logger = LoggerFactory.getLogger(BaseDispatcher.class);

	@Inject
	protected UUID peerUuid;

	@Inject
	DataMessageBuilder messageBuilder;

	@Inject
	protected String outCellAddress;

	@Inject
	protected ZContext zmqContext;

	@Inject
	protected static ObjectService objectService;

	// flag to avoid creating the threadLocal socket when we're trying to close it before having been created
	private final ThreadLocal<Boolean> threadSocketCreated = ThreadLocal.withInitial(() -> false);

	// per-thread REP socket to send out messages
	private final ThreadLocal<Socket> threadSocket = new ThreadLocal<Socket>() {
		@Override
		protected Socket initialValue() {
			Socket worker = zmqContext.createSocket(ZMQ.REQ);
			worker.connect(outCellAddress);
			logger.debug("Created and connected REQ new socket to outCellAddress: {}", outCellAddress);
			threadSocketCreated.set(true);
			return worker;
		}
	};

	public final Object dispatch(StaticPart staticPart, Object sender, Object target, Object[] args)
		throws Throwable {

		logger.trace("dispatch:in w/ signature: {}, sender: {}, target: {}, args: {}", staticPart.getSignature(), sender,
			target, args);

		// 1. Wrap message
		final DataMessage beforeExecMsg = wrapBeforeExecMessage(staticPart, sender, target, args);

		// 2. Send message
		final DataMessage beforeExecReplyMsg = sendAndRecv(beforeExecMsg);

		// 3. Invoke
		// TODO if beforeExecReplyMsg != beforeExecMsg, unpack and exec reply msg
		Object returnValue = invoke(staticPart, sender, target, args);

		// 4. Store? object in object map
		String objectRef = null;
		if (!returnsVoid() && returnValue != null) {
			objectRef = storeObject(returnValue);
		}

		// 5. Wrap object or exception
		final DataMessage afterExecMsg = wrapAfterExecMessage(staticPart, returnValue, objectRef);

		// 6. Send object or exception
		final DataMessage afterExecReplyMsg = sendAndRecv(afterExecMsg);

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

	private final DataMessage sendAndRecv(DataMessage message) {
		logger.trace("sendAndRecv:in w/ message with uuid: {}", message.getMessageUuid());
		Socket outSocket = threadSocket.get();
		outSocket.send(message.toByteArray());

		String rcvdString = null;
		try {
			rcvdString = outSocket.recvStr();
		} catch (ZMQException ex) {
			int errorCode = ex.getErrorCode();
			if (errorCode == ZError.ETERM) {
				logger.warn("Caught ETERM during blocking read. Will close socket");
				outSocket.close();
				return null;
			} else if (errorCode == ZError.EINTR) {
				logger.warn("Caught EINTR during blocking read. Will close socket.");
				outSocket.close();
				return null;
			}
		}

		DataMessage returnValue;
		if ("0".equals(rcvdString)) {
			logger.debug("0 means return same message");
			returnValue = message;
		} else {
			logger.error("We should not get here");
			returnValue = null;
		}

		logger.trace("out w/ {}", returnValue);
		return returnValue;
	}

	private String storeObject(Object object) {
		return object != null ? objectService.storeObject(object) : null;
	}

	abstract protected DataMessage wrapBeforeExecMessage(StaticPart staticPart, Object sender, Object target,
																											 Object[] args);

	// TODO generalize this method, using a Builder method taking Executable's
	// TODO create a Builder.buildVoidReturnValue() method
	abstract protected DataMessage wrapAfterExecMessage(StaticPart staticPart, Object value, String objectRef);

	abstract protected Object invoke(StaticPart staticPart, Object sender, Object target, Object[] args);

	abstract protected boolean returnsVoid();

	abstract protected Type getBeforeExecMessageType();

	abstract protected Type getAfterExecMessageType();

}