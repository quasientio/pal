package com.ittera.cometa.core;

import com.google.common.primitives.Ints;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.inject.Named;

import com.ittera.cometa.messages.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
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
			byte[] headerCntBuff, typeBuff, uuidBuff, followingUuidBuff, msgBuff;
			List<byte[]> headerBuffs = new ArrayList<>();
			int headerCount;
			try {
				/* MULTI-PART message request */
				// part 0. get type of message to follow
				typeBuff = repSocket.recv(ZMQ.DONTWAIT);
				if (typeBuff == null) {
					continue;
				}
				MessageType messageType = MessageType.values[Ints.fromByteArray(typeBuff)];
				// part 1. how many headers
				headerCntBuff = repSocket.recv();
				headerCount = Ints.fromByteArray(headerCntBuff);
				// part 2. [headers]
				if (headerCount > 0) {
					for (int i = 0; i < headerCount; i++) {
						headerBuffs.add(repSocket.recv());
					}
				}
				// part 3. message uuid
				uuidBuff = repSocket.recv();
				// part 4. followingUuid
				followingUuidBuff = repSocket.recv();
				// part 5. actual message
				msgBuff = repSocket.recv();
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

			// pretend message has no actors and send 0 back (should we do this after PUBlishing?)
			repSocket.send("0");

			// send multi-part message as received to SUBscribers
			pubSocket.send(typeBuff, ZMQ.SNDMORE);
			pubSocket.send(headerCntBuff, ZMQ.SNDMORE);
			if (headerCount > 0) {
				headerBuffs.forEach(b -> pubSocket.send(b, ZMQ.SNDMORE));
			}
			pubSocket.send(uuidBuff, ZMQ.SNDMORE);
			pubSocket.send(followingUuidBuff, ZMQ.SNDMORE);
			pubSocket.send(msgBuff);
			if (logger.isDebugEnabled()) {
				logger.debug("Published new message with {} header(s) and {} bytes", headerCount, msgBuff.length);
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
