package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import org.zeromq.ZContext;
import zmq.ZError;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.inject.Named;

public class ReqSocketDispatcherConnector implements DispatcherConnector {

	protected final static Logger logger = LoggerFactory.getLogger(ReqSocketDispatcherConnector.class);

	private final ZContext zmqContext;

	private final String outCellAddress;

	// flag to avoid creating the threadLocal socket when we're trying to close it before having been created
	private final ThreadLocal<Boolean> threadSocketCreated = ThreadLocal.withInitial(() -> false);

	// per-thread REP socket to send out messages
	private final ThreadLocal<Socket> threadSocket = new ThreadLocal<Socket>() {
		@Override
		protected Socket initialValue() {
			Socket worker = zmqContext.createSocket(SocketType.REQ);
			worker.connect(outCellAddress);
			logger.debug("Created and connected REQ new socket to outCellAddress: {}", outCellAddress);
			threadSocketCreated.set(true);
			return worker;
		}
	};

	@Singleton
	@Inject
	public ReqSocketDispatcherConnector(ZContext zmqContext, @Named("out.cell") String outCellAddress) {
		this.zmqContext = zmqContext;
		this.outCellAddress = outCellAddress;
	}

	@Override
	public final DataMessage sendAndRecv(DataMessage message) {
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

	@Override
	public void closeThreadLocalSocket() {
		if (threadSocketCreated.get()) {
			Socket outSocket = threadSocket.get();
			if (outSocket != null) {
				outSocket.close();
				logger.debug("Thread local socket closed");
			}
		}
	}
}
