package com.ittera.cometa.core.exec;

import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.UUIDUtils;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InterceptRequest;

import com.google.common.primitives.Ints;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import org.zeromq.ZContext;
import zmq.ZError;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.inject.Named;

@Singleton
public class DispatcherConnector {

	private final static Logger logger = LoggerFactory.getLogger(DispatcherConnector.class);

	private final ZContext zmqContext;
	private final String outCellAddress;

	private final InternalHeader WRITE_AHEAD_HEADER;

	// flag to avoid creating the threadLocal socket when we're trying to close it before having been created
	private final ThreadLocal<Boolean> threadSocketCreated = ThreadLocal.withInitial(() -> false);

	// per-thread REQ socket to send out messages
	private final ThreadLocal<Socket> threadSocket = new ThreadLocal<Socket>() {
		protected Socket initialValue() {
			Socket worker = zmqContext.createSocket(SocketType.REQ);
			worker.connect(outCellAddress);
			if (logger.isDebugEnabled()) {
				logger.debug("Created and connected REQ new socket to outCellAddress: {}", outCellAddress);
			}
			threadSocketCreated.set(true);
			return worker;
		}
	};

	@Inject
	public DispatcherConnector(ZContext zmqContext, UUID peerUuid, MessageBuilder messageBuilder,
														 @Named("out.cell") String outCellAddress) {
		this.zmqContext = zmqContext;
		this.outCellAddress = outCellAddress;
		this.WRITE_AHEAD_HEADER = messageBuilder.buildWriteAheadHeader(peerUuid);
	}

	public ExecMessage sendExecMessage(ExecMessage message) {
		return sendExecMessage(message, null);
	}

	public boolean sendInterceptRequestMessage(InterceptRequest message) {
		return sendInterceptRequest(message, null);
	}

	private ExecMessage sendExecMessage(ExecMessage message, @Nullable List<InternalHeader> headers) {
		if (logger.isTraceEnabled()) {
			logger.trace("sendExecMessage:in w/ message with uuid: {}", message.getMessageUuid());
		}
		Socket outSocket = threadSocket.get();
		UUID followingUuid = message.hasFollowingUuid() ? UUID.fromString(message.getFollowingUuid()) : null;
		send(outSocket, message.toByteArray(), MessageType.ExecMessage, UUID.fromString(message.getMessageUuid()),
			followingUuid, headers);

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

		ExecMessage returnValue;
		if ("0".equals(rcvdString)) {
			if (logger.isDebugEnabled()) {
				logger.debug("0 means return same message");
			}
			returnValue = message;
		} else {
			logger.error("We should not get here");
			returnValue = null;
		}
		if (logger.isTraceEnabled()) {
			logger.trace("out w/ {}", returnValue);
		}
		return returnValue;
	}

	private boolean sendInterceptRequest(InterceptRequest message, @Nullable List<InternalHeader> headers) {
		if (logger.isTraceEnabled()) {
			logger.trace("sendInterceptRequest:in w/ message with uuid: {}", message.getMessageUuid());
		}

		Socket outSocket = threadSocket.get();
		send(outSocket, message.toByteArray(), MessageType.InterceptRequest, UUID.fromString(message.getMessageUuid()),
			null, headers);

		String rcvdString = null;
		try {
			rcvdString = outSocket.recvStr();
		} catch (ZMQException ex) {
			int errorCode = ex.getErrorCode();
			if (errorCode == ZError.ETERM) {
				logger.warn("Caught ETERM during blocking read. Will close socket");
				outSocket.close();
				return false;
			} else if (errorCode == ZError.EINTR) {
				logger.warn("Caught EINTR during blocking read. Will close socket.");
				outSocket.close();
				return false;
			}
		}

		boolean ok = "0".equals(rcvdString);

		if (!ok) {
			logger.error("Intercept request message sent, but got unexpected reply: {}", rcvdString);
		}
		if (logger.isTraceEnabled()) {
			logger.trace("out w/ {}", ok);
		}
		return ok;
	}

	private void send(Socket outSocket, byte[] message, MessageType messageType, UUID messageUuid,
										@Nullable UUID followingUuid, @Nullable List<InternalHeader> headers) {

		// 0. send type of message to follow
		outSocket.send(Ints.toByteArray(messageType.ordinal()), ZMQ.SNDMORE);

		if (headers != null && !headers.isEmpty()) {
			// 1. send number of headers to follow,
			outSocket.send(Ints.toByteArray(headers.size()), ZMQ.SNDMORE);

			// 2. send all headers
			for (InternalHeader header : headers) {
				outSocket.send(header.toByteArray(), ZMQ.SNDMORE);
			}
		} else {
			outSocket.send(Ints.toByteArray(0), ZMQ.SNDMORE);
		}

		// 3. send message uuid
		outSocket.send(UUIDUtils.toBytes(messageUuid), ZMQ.SNDMORE);

		// 4. send followingUuid
		if (followingUuid != null) {
			outSocket.send(UUIDUtils.toBytes(followingUuid), ZMQ.SNDMORE);
		} else {
			outSocket.send(Ints.toByteArray(0), ZMQ.SNDMORE);
		}

		// 5. send actual message
		outSocket.send(message);
	}

	public void writeAhead(ExecMessage message) {
		if (logger.isTraceEnabled()) {
			logger.trace("writeAhead:in w/ message with uuid: {},from {}",
				message.getMessageUuid(),
				message.getPeerUuid());
		}

		// by sending out <write_ahead> header the Log Writer will serialize it with a <dispatching-by> header
		List<InternalHeader> headers = Collections.singletonList(this.WRITE_AHEAD_HEADER);
		sendExecMessage(message, headers);
	}

	void closeThreadLocalSocket() {
		if (threadSocketCreated.get()) {
			Socket outSocket = threadSocket.get();
			if (outSocket != null) {
				outSocket.close();
				if (logger.isDebugEnabled()) {
					logger.debug("Thread local socket closed");
				}
			}
		}
	}
}
