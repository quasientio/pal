package com.ittera.cometa.core;

import com.ittera.cometa.core.messages.OutboundMsg;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.inject.Named;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.zeromq.*;
import org.zeromq.ZMQ.Socket;
import zmq.ZError;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class OutgoingMessageDispatcher extends ConnectedService {

	private static final Logger logger = LoggerFactory.getLogger(OutgoingMessageDispatcher.class);

	// counters
	private final AtomicLong totalReadBlockingQueueNanos = new AtomicLong(0);
	private final AtomicLong totalPollingNanos = new AtomicLong(0);
	private final AtomicInteger totalReadCalls = new AtomicInteger(0);
	private final AtomicInteger totalPolls = new AtomicInteger(0);
	private final AtomicInteger messagesQueuedToSend = new AtomicInteger(0);
	private final AtomicInteger messagesRcvd = new AtomicInteger(0);

	// zmq stuff
	private Socket repSocket, pubSocket;
	private final String outCellAddress, outPubAddress;

	@Inject
	public OutgoingMessageDispatcher(UUID peerUuid,
																	 ZContext context,
																	 @Named("sync.ready") String syncSocketAddress,
																	 ThreadGroup serviceThreadGroup,
																	 @Named("OutgoingMessageDispatcher.service") String serviceName,
																	 @Named("out.cell") String outCellAddress,
																	 @Named("out.pub") String outPubAddress) {
		super(peerUuid, context, syncSocketAddress, serviceThreadGroup, serviceName);
		this.outCellAddress = outCellAddress;
		this.outPubAddress = outPubAddress;
	}

	@Override
	protected void openConnections() {
		// open REP and PUB sockets
		repSocket = zmqContext.createSocket(SocketType.REP);
		repSocket.bind(outCellAddress);
		pubSocket = zmqContext.createSocket(SocketType.PUB);
		pubSocket.bind(outPubAddress);
	}

	@Override
	public final void run() {
		while (!Thread.interrupted()) {
			OutboundMsg msg = null;
			ZMsg zmsg = null;
			try {
				zmsg = ZMsg.recvMsg(repSocket, ZMQ.DONTWAIT);
				if (zmsg == null) {
					continue;
				}
				msg = OutboundMsg.from(zmsg);
				if (logger.isDebugEnabled()) {
					logger.debug("Received new message ({} bytes)", msg.contentSize());
				}
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
			} catch (Exception e) {
				logger.error("Error parsing received message", e);
			} finally {
				if (zmsg != null) {
					zmsg.destroy();
				}
			}
			// reply to message
			if (msg != null) {
				// pretend message has no actors and send 0 back (should we do this after PUBlishing?)
				repSocket.send("0");
			} else {
				repSocket.send("1"); //  1 == error
			}

			// publish message
			if (msg != null) {
				msg.send(pubSocket, false);
				if (logger.isDebugEnabled()) {
					logger.debug("Published new message ({} bytes)", msg.contentSize());
				}
				// clean up
				msg.destroy();
			}
		}
	}

	@Override
	protected void closeConnections() {
		closeConnection(repSocket, "Error closing REP socket");
		closeConnection(pubSocket, "Error closing PUB socket");
	}

	//TODO
	private boolean hasActors(/* headers or message */) {
		return false;
	}

	protected void printDebugStats() {
		if (logger.isDebugEnabled()) {
			logger.debug("--------STATS--------");
			logger.debug("# of messages queued to send: {}", messagesQueuedToSend.get());
			logger.debug("# of messages received from k-log: {}", messagesRcvd.get());
			logger.debug("# polling nanoseconds: {}", totalPollingNanos.get());
			logger.debug("# polls: {}", totalPolls.get());
			logger.debug("# queue reads: {}", totalReadCalls.get());
			logger.debug("Total waiting time reading from queue in nanoseconds: {}", totalReadBlockingQueueNanos.get());
			logger.debug("-----END OF STATS-----");
		}
	}
}
