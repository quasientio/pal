package com.ittera.cometa.core.exec;

import com.ittera.cometa.core.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

class PeerMessageInvoker extends Thread {

	private static final Logger logger = LoggerFactory.getLogger(PeerMessageInvoker.class);

	private final AtomicLong requestsDispatched = new AtomicLong(0);
	private final AtomicLong requestsDismissed = new AtomicLong(0);

	private final IncomingMessageDispatcher incomingMessageDispatcher;
	private final DispatcherConnector dispatcherConnector;
	private final MessageBuilder messageBuilder;

	// zmq stuff
	private final ZContext zmqContext;
	private final String dealerAddress;
	private Socket socket;

	public PeerMessageInvoker(ThreadGroup group, Runnable target, String name, ZContext zmqContext,
														MessageBuilder messageBuilder, String dealerAddress, IncomingMessageDispatcher
															incomingMessageDispatcher, DispatcherConnector dispatcherConnector) {
		super(group, target, name);
		this.zmqContext = zmqContext;
		this.messageBuilder = messageBuilder;
		this.dealerAddress = dealerAddress;
		this.incomingMessageDispatcher = incomingMessageDispatcher;
		this.dispatcherConnector = dispatcherConnector;
		if (logger.isDebugEnabled()) {
			logger.debug("Initialized new peer message invoker thread named: {} with dealerAddress: {}", name, dealerAddress);
		}
	}

	/**
	 * Constructor exclusive for unit-testing -- to avoid ExecutorService and ThreadFactory dependencies.
	 * NOTE: dispatcherConnector is set to null, since it's not required
	 *
	 * @param zmqContext
	 * @param messageBuilder
	 * @param dealerAddress
	 * @param incomingMessageDispatcher
	 */
	PeerMessageInvoker(ZContext zmqContext, MessageBuilder messageBuilder, String dealerAddress,
										 IncomingMessageDispatcher incomingMessageDispatcher) {
		this.zmqContext = zmqContext;
		this.messageBuilder = messageBuilder;
		this.dealerAddress = dealerAddress;
		this.incomingMessageDispatcher = incomingMessageDispatcher;
		this.dispatcherConnector = null;
		if (logger.isDebugEnabled()) {
			logger.debug("Initialized new peer message invoker thread with dealerAddress: {}", dealerAddress);
		}
	}

	@Override
	public void run() {

		// create REP socket
		socket = zmqContext.createSocket(SocketType.REP);
		socket.connect(dealerAddress);

		ExecMessage requestMsg, replyMsg;

		if (logger.isDebugEnabled()) {
			logger.debug("Start getting requests from socket");
		}

		while (!Thread.interrupted()) {

			// recv req
			byte[] req;

			try {
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
					if (logger.isDebugEnabled()) {
						logger.debug("Re-throwing unexpected exception", ex);
					}
					throw ex;
				}
			}

			final long started = System.currentTimeMillis();

			requestMsg = null;

			// parse req
			try {
				requestMsg = ExecMessage.parseFrom(req);
			} catch (Exception e) {
				logger.error("Caught exception parsing message", e);
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Received req message with uuid: {}", requestMsg != null ? requestMsg.getMessageUuid() : null);
			}

			if (requestMsg != null) {

				//dispatch
				replyMsg = dispatch(requestMsg);

				//send reply
				socket.send(replyMsg.toByteArray());

				if (logger.isDebugEnabled()) {
					final long took = System.currentTimeMillis() - started;
					if (logger.isDebugEnabled()) {
						logger.debug("Dispatched and sent direct message reply with uuid: {} in {} millisecs",
							requestMsg.getMessageUuid(), took);
					}
				}
			}
		}

		closeConnections();

		if (logger.isDebugEnabled()) {
			logger.debug("Stopped peer executor thread: {}, dispatched={} dismissed={}", getName(), requestsDispatched.get(),
				requestsDismissed.get());
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

	private ExecMessage dispatch(ExecMessage requestMsg) {
		ExecMessage replyMsg = incomingMessageDispatcher.incomingCall(requestMsg, true);
		if (logger.isDebugEnabled()) {
			logger.debug("Invoker dispatched peer request message uuid: {}, reply uuid: {}", requestMsg.getMessageUuid(),
				replyMsg.getMessageUuid());
		}
		requestsDispatched.getAndIncrement();
		messageBuilder.resetThreadLocalSequence();
		return replyMsg;
	}

	public AtomicLong getRequestsDispatched() {
		return requestsDispatched;
	}
}
