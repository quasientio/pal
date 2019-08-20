package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.exec.java.IncomingMessageDispatcher;

import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

public class LogMessageInvoker extends Thread {

	protected static final Logger logger = LoggerFactory.getLogger(LogMessageInvoker.class);

	protected final AtomicLong requestsDispatched = new AtomicLong(0);

	// zmq stuff
	private final ZContext zmqContext;
	private final String inLogAddress;
	private Socket socket;

	protected final IncomingMessageDispatcher incomingMessageDispatcher;
	protected final DispatcherConnector dispatcherConnector;
	protected final DataMessageBuilder dataMessageBuilder;
	protected final UUID peerUuid;

	public LogMessageInvoker(ThreadGroup group, Runnable target, String name, ZContext zmqContext,
													 DataMessageBuilder dataMessageBuilder, String inLogAddress, IncomingMessageDispatcher
														 incomingMessageDispatcher, DispatcherConnector dispatcherConnector, UUID peerUuid) {
		super(group, target, name);
		this.zmqContext = zmqContext;
		this.inLogAddress = inLogAddress;
		this.dataMessageBuilder = dataMessageBuilder;
		this.incomingMessageDispatcher = incomingMessageDispatcher;
		this.dispatcherConnector = dispatcherConnector;
		this.peerUuid = peerUuid;
		if (logger.isDebugEnabled()) {
			logger.debug("Initialized new log message invoker thread named: {} with inLogAddress: {}", name, inLogAddress);
		}
	}

	@Override
	public void run() {

		// create REP socket
		socket = zmqContext.createSocket(SocketType.REP);
		socket.connect(inLogAddress);

		DataMessage requestMsg;

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
				requestMsg = DataMessage.parseFrom(req);
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
						logger.debug("Dispatched data message with uuid: {} in {} millisecs", requestMsg.getMessageUuid(), took);
					}
				}
			}
		}

		closeConnections();

		if (logger.isDebugEnabled()) {
			logger.debug("Stopped log executor thread: {}, dispatched={}", getName(), requestsDispatched.get());
		}
	}

	protected void closeConnections() {

		if (socket != null) {
			socket.close();
		}

		dispatcherConnector.closeThreadLocalSocket();
	}

	protected void dispatch(DataMessage requestMsg, long recordOffset) {
		DataMessage replyMsg = incomingMessageDispatcher.incomingCall(requestMsg);
		if (logger.isDebugEnabled()) {
			logger.debug("Invoker dispatched log request message uuid: {} and recordOffset: {}, reply uuid: {}",
				requestMsg.getMessageUuid(), recordOffset, replyMsg.getMessageUuid());
		}
		requestsDispatched.getAndIncrement();
		dataMessageBuilder.resetThreadLocalSequence();
	}
}
