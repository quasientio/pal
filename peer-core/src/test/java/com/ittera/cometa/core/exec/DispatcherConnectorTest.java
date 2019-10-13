package com.ittera.cometa.core.exec;

import com.ittera.cometa.core.ZmqEnabledTest;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InterceptRequest;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;

import com.google.common.primitives.Ints;
import com.google.protobuf.InvalidProtocolBufferException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class DispatcherConnectorTest extends ZmqEnabledTest {
	private static final Logger logger = LoggerFactory.getLogger("tests");

	private final class OutgoingMessageDispatcherStub implements Runnable {
		List<Object> messagesReceived = new ArrayList<>();
		List<byte[]> headersReceived = new ArrayList<>();

		void clear() {
			outDispatcherStub.messagesReceived.clear();
			outDispatcherStub.headersReceived.clear();
		}

		@Override
		public void run() {
			Socket repSocket = context.createSocket(SocketType.REP);
			repSocket.bind(OUTCELL_ADDR);

			while (!Thread.interrupted()) {

				byte[] headerCntBuff, msgBuff, typeBuff, uuidBuff, followingUuidBuff;
				List<byte[]> headerBuffs = new ArrayList<>();
				int headerCount;

				try {
					// part 0. get type of message to follow
					typeBuff = repSocket.recv(ZMQ.DONTWAIT);
					if (typeBuff == null) {
						continue;
					}
					MessageType messageType = MessageType.values[Ints.fromByteArray(typeBuff)];

					// part 1. how many headers?
					headerCntBuff = repSocket.recv();
					headerCount = Ints.fromByteArray(headerCntBuff);

					// part 2. [headers]
					if (headerCount > 0) {
						for (int i = 0; i < headerCount; i++) {
							byte[] hdrBuff = repSocket.recv();
							headerBuffs.add(hdrBuff);
							headersReceived.add(hdrBuff);
						}
					}

					// part 3. message uuid
					uuidBuff = repSocket.recv();

					// part 4. followingUuid
					followingUuidBuff = repSocket.recv();

					// part 5. message
					msgBuff = repSocket.recv();
					try {
						if (messageType.equals(MessageType.ExecMessage)) {
							messagesReceived.add(ExecMessage.parseFrom(msgBuff));
						} else if (messageType.equals(MessageType.InterceptRequest)) {
							messagesReceived.add(InterceptRequest.parseFrom(msgBuff));
						} else {
							throw new RuntimeException(format("unhandled message type: %s", messageType));
						}
					} catch (InvalidProtocolBufferException e) {
						logger.error("Error parsing receieved msg", e);
					}

					// reply: pretend message has no actors and send 0 back
					repSocket.send("0");
				} catch (ZMQException ex) {
					int errorCode = ex.getErrorCode();
					if (errorCode == ZError.ETERM) {
						break;
					} else if (errorCode == ZError.EINTR) {
						break;
					} else {
						throw ex;
					}
				}
			}
		}
	}

	private final UUID peerUuid = UUID.randomUUID();
	private final String OUTCELL_ADDR = "inproc://cell";
	private ZContext context;
	private ExecutorService execService;
	private DispatcherConnector dispatcherConnector;
	private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
	private final OutgoingMessageDispatcherStub outDispatcherStub = new OutgoingMessageDispatcherStub();
	private InternalHeader WRITE_AHEAD_HEADER;

	@Before
	public void setup() {
		this.WRITE_AHEAD_HEADER = msgBuilder.buildWriteAheadHeader(peerUuid);
		this.context = createContext();
		this.execService = Executors.newCachedThreadPool();
		this.dispatcherConnector = new DispatcherConnector(
			context,
			peerUuid,
			msgBuilder,
			OUTCELL_ADDR);

		// simulate OutgoingMessageDispatcher
		execService.submit(outDispatcherStub);
	}

	@After
	public void cleanup() throws Exception {
		// close local context
		context.close();

		// stop executor
		execService.shutdownNow();
		execService.awaitTermination(2, TimeUnit.SECONDS);

		outDispatcherStub.clear();
	}

	@Test
	public void sendExecMessage() throws Exception {
		// sends msg and get reply
		ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
		ExecMessage returnedMsg = dispatcherConnector.sendExecMessage(msg);

		// should return same message as sent (if reply == 0), null otherwise
		assertThat(returnedMsg, is(msg));
		assertThat(outDispatcherStub.messagesReceived.size(), is(1));
		assertThat(outDispatcherStub.messagesReceived, is(Collections.singletonList(msg)));
	}

	@Test
	public void sendInterceptRequestMessage() throws Exception {
		// sends msg and get reply
		InterceptRequest msg = msgBuilder.buildInterceptRequest(peerUuid, "java.io.PrintStream",
			"println", null, this.getClass().getName(), "someCallbackMethod");
		boolean ok = dispatcherConnector.sendInterceptRequestMessage(msg);

		assertThat(ok, is(true));
		assertThat(outDispatcherStub.messagesReceived.size(), is(1));
		assertThat(outDispatcherStub.messagesReceived, is(Collections.singletonList(msg)));
	}

	@Test
	public void sendExecMessageMany() throws Exception {

		int msgsToSend = 10;
		List<ExecMessage> sentMessages = new ArrayList<>();
		List<ExecMessage> returnedMessages = new ArrayList<>();

		// sends msgs and get replies
		for (int i = 0; i < msgsToSend; i++) {
			ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
			sentMessages.add(msg);
			ExecMessage returnedMsg = dispatcherConnector.sendExecMessage(msg);
			returnedMessages.add(returnedMsg);
		}

		assertThat(returnedMessages, is(sentMessages));
		assertThat(outDispatcherStub.messagesReceived.size(), is(msgsToSend));
		assertThat(outDispatcherStub.messagesReceived, is(sentMessages));
	}

	@Test
	public void writeAhead() throws Exception {

		ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
		dispatcherConnector.writeAhead(msg);

		// verify messages received by stub
		assertThat(outDispatcherStub.messagesReceived.size(), is(1));
		assertThat(outDispatcherStub.messagesReceived, is(Collections.singletonList(msg)));

		// stub should have received a WRITE_AHEAD_HEADER
		assertThat(outDispatcherStub.headersReceived.size(), is(1));
		assertThat(InternalHeader.parseFrom(outDispatcherStub.headersReceived.get(0)), is(WRITE_AHEAD_HEADER));
	}
}