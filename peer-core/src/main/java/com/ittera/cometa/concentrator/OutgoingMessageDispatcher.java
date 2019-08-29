package com.ittera.cometa.concentrator;

import com.google.common.primitives.Ints;

import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

@Singleton
class OutgoingMessageDispatcher extends AbstractExecutionThreadService {

	private static final Logger logger = LoggerFactory.getLogger(OutgoingMessageDispatcher.class);

	// counters
	private final AtomicLong totalReadBlockingQueueNanos = new AtomicLong(0);
	private final AtomicLong totalPollingNanos = new AtomicLong(0);
	private final AtomicInteger totalReadCalls = new AtomicInteger(0);
	private final AtomicInteger totalPolls = new AtomicInteger(0);
	private final AtomicInteger messagesQueuedToSend = new AtomicInteger(0);
	private final AtomicInteger messagesRcvd = new AtomicInteger(0);

	// zmq stuff
	private ZContext context;
	private Socket repSocket, pubSocket;
	private final String outCellAddress, outPubAddress;

	@Inject
	public OutgoingMessageDispatcher(@Named("out.cell") String outCellAddress,
																	 @Named("out.pub") String outPubAddress,
																	 ZContext context) {
		this.outCellAddress = outCellAddress;
		this.outPubAddress = outPubAddress;
		this.context = context;
		logger.info("Initialized outgoing message dispatcher");
	}

	private void openConnections() {

		repSocket = context.createSocket(SocketType.REP);
		repSocket.bind(outCellAddress);

		pubSocket = context.createSocket(SocketType.PUB);
		pubSocket.connect(outPubAddress);

		logger.info("All connections open");
	}

	private void closeConnections() {

		if (repSocket != null) {
			repSocket.close();
		}

		if (pubSocket != null) {
			pubSocket.close();
		}

		logger.info("All connections closed");
	}

	@Override
	public final void run() {
		while (isRunning() && !Thread.interrupted()) {

			byte[] headerCntBuff, msgBuff;
			List<byte[]> headerBuffs = new ArrayList<>();
			int headerCount;

			try {
				/* multi-part message request */

				// part 1. how many headers?
				headerCntBuff = repSocket.recv(ZMQ.DONTWAIT);
				if (headerCntBuff == null) {
					// TODO should we sleep a bit here?
					continue;
				}
				headerCount = Ints.fromByteArray(headerCntBuff);

				// part 2. [headers]
				if (headerCount > 0) {
					for (int i = 0; i < headerCount; i++) {
						headerBuffs.add(repSocket.recv());
					}
				}

				// part 3. message
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
			pubSocket.send(headerCntBuff, ZMQ.SNDMORE);
			if (headerCount > 0) {
				headerBuffs.forEach(b -> pubSocket.send(b, ZMQ.SNDMORE));
			}
			pubSocket.send(msgBuff);
		}

		closeConnections();
	}

	//TODO
	private boolean hasActors(/* headers or message */) {
		return false;
	}

	@Override
	protected void triggerShutdown() {

		logger.info("OUT Message dispatcher shutting down.");
	}

	@Override
	protected void startUp() {
		openConnections();
	}

	@Override
	protected void shutDown() {

		logger.info("OUT Message dispatcher shut down");
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
