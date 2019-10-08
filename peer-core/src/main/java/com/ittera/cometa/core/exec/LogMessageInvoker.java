package com.ittera.cometa.core.exec;

import com.ittera.cometa.core.exec.java.IncomingMessageDispatcher;

import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

class LogMessageInvoker extends Thread {

	private static final Logger logger = LoggerFactory.getLogger(LogMessageInvoker.class);

	private final AtomicLong requestsDispatched = new AtomicLong(0);

	// zmq stuff
	private final ZContext zmqContext;
	private final String inLogAddress;
	private Socket socket;

	private final IncomingMessageDispatcher incomingMessageDispatcher;
	private final DispatcherConnector dispatcherConnector;
	private final MessageBuilder messageBuilder;
	private final UUID peerUuid;

	public LogMessageInvoker(ThreadGroup group, Runnable target, String name, ZContext zmqContext,
													 MessageBuilder messageBuilder, String inLogAddress, IncomingMessageDispatcher
														 incomingMessageDispatcher, DispatcherConnector dispatcherConnector, UUID peerUuid) {
		super(group, target, name);
		this.zmqContext = zmqContext;
		this.messageBuilder = messageBuilder;
		this.inLogAddress = inLogAddress;
		this.incomingMessageDispatcher = incomingMessageDispatcher;
		this.dispatcherConnector = dispatcherConnector;
		this.peerUuid = peerUuid;
		if (logger.isDebugEnabled()) {
			logger.debug("Initialized new log message invoker thread named: {} with inLogAddress: {}", name, inLogAddress);
		}
	}

	/**
	 * Constructor exclusive for unit-testing -- to avoid ExecutorService and ThreadFactory dependencies.
	 * NOTE: dispatcherConnector is set to null, since it's not required
	 *
	 * @param zmqContext
	 * @param messageBuilder
	 * @param inLogAddress
	 * @param incomingMessageDispatcher
	 * @param peerUuid
	 */
	LogMessageInvoker(ZContext zmqContext, MessageBuilder messageBuilder, String inLogAddress,
										IncomingMessageDispatcher incomingMessageDispatcher, UUID peerUuid) {
		this.zmqContext = zmqContext;
		this.messageBuilder = messageBuilder;
		this.inLogAddress = inLogAddress;
		this.incomingMessageDispatcher = incomingMessageDispatcher;
		this.dispatcherConnector = null;
		this.peerUuid = peerUuid;
		if (logger.isDebugEnabled()) {
			logger.debug("Initialized new log message invoker thread with inLogAddress: {}", inLogAddress);
		}
	}

	@Override
	public void run() {

		// create REP socket
		socket = zmqContext.createSocket(SocketType.REP);
		socket.connect(inLogAddress);

		ExecMessage requestMsg;

		if (logger.isDebugEnabled()) {
			logger.debug("Start getting requests from socket");
		}
		while (!Thread.interrupted()) {

			String offset;
			long logOffset;
			byte[] req;

			// recv req
			try {
				offset = socket.recvStr();
				if (logger.isDebugEnabled()) {
					logger.debug("Getting message with kafka offset: {}", offset);
				}
				logOffset = Long.parseLong(offset);
				req = socket.recv();
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

			requestMsg = null;
			long started = System.currentTimeMillis();

			// parse req
			try {
				requestMsg = ExecMessage.parseFrom(req);
			} catch (Exception e) {
				logger.error("Caught exception parsing message", e);
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Received req message with uuid: {}", requestMsg != null ? requestMsg.getMessageUuid() : null);
			}

			// dispatch it
			if (requestMsg != null) {
				dispatch(requestMsg, logOffset);
				if (logger.isDebugEnabled()) {
					final long took = System.currentTimeMillis() - started;
					if (logger.isDebugEnabled()) {
						logger.debug("Dispatched log message with uuid: {} in {} millisecs", requestMsg.getMessageUuid(), took);
					}
				}
			}
		}

		closeConnections();

		if (logger.isDebugEnabled()) {
			logger.debug("Stopped log executor thread: {}, dispatched={}", getName(), requestsDispatched.get());
		}
	}

	private void closeConnections() {

		if (socket != null) {
			socket.close();
		}

		if (dispatcherConnector != null) {
			dispatcherConnector.closeThreadLocalSocket();
		}
	}

	private void dispatch(ExecMessage requestMsg, long recordOffset) {
		ExecMessage replyMsg = incomingMessageDispatcher.incomingCall(requestMsg, false);
		if (logger.isDebugEnabled()) {
			logger.debug("Invoker dispatched log request message uuid: {} and recordOffset: {}, reply uuid: {}",
				requestMsg.getMessageUuid(), recordOffset, replyMsg.getMessageUuid());
		}
		requestsDispatched.getAndIncrement();
		messageBuilder.resetThreadLocalSequence();
	}

	public AtomicLong getRequestsDispatched() {
		return requestsDispatched;
	}
}
