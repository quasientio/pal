package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

import javax.inject.Inject;

public class PeerMessageInvoker extends Thread {

	protected static final Logger logger = LoggerFactory.getLogger(PeerMessageInvoker.class);

	protected final AtomicLong requestsDispatched = new AtomicLong(0);
	protected final AtomicLong requestsDismissed = new AtomicLong(0);

	protected final DataMessageBuilder dataMessageBuilder;

	// zmq stuff
	private final ZContext zmqContext;
	private final String dealerAddress;
	private Socket socket;

	@Inject
	protected IncomingMessageDispatcher incomingMessageDispatcher;

	@Inject
	private ReqSocketDispatcherConnector reqSocketDispatcherConnector;

	public PeerMessageInvoker(ThreadGroup group, Runnable target, String name, ZContext zmqContext,
														DataMessageBuilder dataMessageBuilder, String dealerAddress) {
		super(group, target, name);
		this.zmqContext = zmqContext;
		this.dealerAddress = dealerAddress;
		this.dataMessageBuilder = dataMessageBuilder;
		logger.debug("Initialized new peer message invoker thread named: {} with dealerAddress: {}", name, dealerAddress);
	}

	@Override
	public void run() {

		// create REP socket
		socket = zmqContext.createSocket(ZMQ.REP);
		socket.connect(dealerAddress);

		DataMessage requestMsg, replyMsg;

		logger.debug("Start getting requests from socket");

		while (!Thread.interrupted()) {

			// recv req
			byte[] req = null;

			try {
				req = socket.recv();
			} catch (ZMQException ex) {
				int errorCode = ex.getErrorCode();
				if (errorCode == ZError.ETERM) {
					logger.debug("Caught ETERM during blocking read. Breaking out.");
					break;
				} else if (errorCode == ZError.EINTR) {
					logger.debug("Caught EINTR during blocking read. Breaking out.");
					break;
				} else {
					logger.debug("Re-throwing unexpected exception", ex);
					throw ex;
				}
			}

			final long started = System.currentTimeMillis();

			requestMsg = null;

			// parse req
			try {
				requestMsg = DataMessage.parseFrom(req);
			} catch (Exception e) {
				logger.error("Caught exception parsing message", e);
			}

			logger.debug("Received req message with uuid: {}", requestMsg != null ? requestMsg.getMessageUuid() : null);

			if (requestMsg != null) {

				//dispatch
				replyMsg = dispatch(requestMsg);

				//send reply
				socket.send(replyMsg.toByteArray());

				if (logger.isDebugEnabled()) {
					final long took = System.currentTimeMillis() - started;
					logger.debug("Dispatched and sent data message reply with uuid: {} in {} millisecs",
						requestMsg.getMessageUuid(), took);
				}
			}
		}

		closeConnections();

		logger.debug("Stopped peer executor thread: {}, dispatched={} dismissed={}", getName(), requestsDispatched.get(),
			requestsDismissed.get());
	}

	protected void closeConnections() {

		if (socket != null) {
			socket.close();
		}
		reqSocketDispatcherConnector.closeThreadLocalSocket();
	}

	private DataMessage dispatch(DataMessage requestMsg) {
		DataMessage replyMsg = incomingMessageDispatcher.incomingCall(requestMsg);
		logger.debug("Invoker dispatched peer request message uuid: {}, reply uuid: {}", requestMsg.getMessageUuid(),
			replyMsg.getMessageUuid());
		requestsDispatched.getAndIncrement();
		dataMessageBuilder.resetThreadLocalSequence();
		return replyMsg;
	}
}
