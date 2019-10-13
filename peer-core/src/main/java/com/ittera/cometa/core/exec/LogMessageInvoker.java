package com.ittera.cometa.core.exec;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import com.ittera.cometa.core.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InterceptRequest;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;


import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import zmq.ZError;

class LogMessageInvoker extends AbstractMessageInvokerThread {

	public LogMessageInvoker(ThreadGroup group, Runnable target, String name, ZContext zmqContext,
													 MessageBuilder messageBuilder, String dealerAddress, IncomingMessageDispatcher
														 incomingMessageDispatcher, DispatcherConnector dispatcherConnector) {
		super(group, target, name, zmqContext, messageBuilder, dealerAddress, incomingMessageDispatcher,
			dispatcherConnector);
	}

	LogMessageInvoker(ZContext zmqContext, MessageBuilder messageBuilder, String dealerAddress,
										IncomingMessageDispatcher incomingMessageDispatcher) {
		super(zmqContext, messageBuilder, dealerAddress, incomingMessageDispatcher);
	}


	@Override
	public void run() {

		// create REP socket
		socket = zmqContext.createSocket(SocketType.REP);
		socket.connect(dealerAddress);

		if (logger.isDebugEnabled()) {
			logger.debug("Start getting requests from socket");
		}
		while (!Thread.interrupted()) {

			long logOffset;
			MessageType msgType;
			byte[] msg;

			// recv req
			try {
				byte[] buff = socket.recv();
				logOffset = Longs.fromByteArray(buff);
				if (logger.isDebugEnabled()) {
					logger.debug("Getting message with kafka offset: {}", logOffset);
				}
				buff = socket.recv();
				msgType = MessageType.values[Ints.fromByteArray(buff)];
				msg = socket.recv();
			} catch (ZMQException ex) {
				int errorCode = ex.getErrorCode();
				if (errorCode == ZError.ETERM) {
					if (logger.isDebugEnabled()) {
						logger.debug("Caught ETERM during blocking read. Breaking out.");
					}
					break;
				} else if (errorCode == ZError.EINTR) {
					if (logger.isDebugEnabled()) {
						logger.debug("Caught EINTR during blocking read. Breaking out.");
					}
					break;
				} else {
					throw ex;
				}
			}

			Object requestMsg = null;
			long started = System.currentTimeMillis();

			// parse req
			try {
				if (msgType.equals(MessageType.ExecMessage)) {
					requestMsg = ExecMessage.parseFrom(msg);
				} else if (msgType.equals(MessageType.InterceptRequest)) {
					requestMsg = InterceptRequest.parseFrom(msg);
				} else {
					logger.error("Received unknown message type: {}", msgType);
				}
			} catch (Exception e) {
				logger.error("Caught exception parsing message", e);
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Received message with offset: {}, type: {}, uuid: {}",
					logOffset, msgType, getMessageUuid(requestMsg));
			}

			// dispatch it
			if (requestMsg != null) {
				dispatch(requestMsg, logOffset);
				if (logger.isDebugEnabled()) {
					final long took = System.currentTimeMillis() - started;
					if (logger.isDebugEnabled()) {
						logger.debug("Dispatched log message with uuid: {} in {} millisecs", getMessageUuid(requestMsg), took);
					}
				}
			}
		}

		closeConnections();
	}
}
