package com.ittera.cometa.core.exec;

import com.ittera.cometa.core.ZmqEnabledTest;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InterceptRequest;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;
import com.ittera.cometa.core.messages.OutboundMsg;

import org.zeromq.*;
import org.zeromq.ZMQ.Socket;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DispatcherConnectorTest extends ZmqEnabledTest {
	private static final Logger logger = LoggerFactory.getLogger("tests");

	private final class OutgoingMessageDispatcherStub implements Runnable {
		List<Object> messagesReceived = new ArrayList<>();
		List<InternalHeader> headersReceived = new ArrayList<>();

		void clear() {
			outDispatcherStub.messagesReceived.clear();
			outDispatcherStub.headersReceived.clear();
		}

		@Override
		public void run() {
			Socket repSocket = context.createSocket(SocketType.REP);
			repSocket.bind(OUTCELL_ADDR);

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
					// add headers & message to lists for verification
					headersReceived.addAll(msg.getHeaders());
					if (msg.getMessageType().equals(MessageType.ExecMessage)) {
						messagesReceived.add(ExecMessage.parseFrom(msg.getBody()));
					} else if (msg.getMessageType().equals(MessageType.InterceptRequest)) {
						messagesReceived.add(InterceptRequest.parseFrom(msg.getBody()));
					} else {
						throw new RuntimeException(format("unhandled message type: %s", msg.getMessageType()));
					}
					// reply: pretend message has no actors and send 0 back
					repSocket.send("0");
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
					if (msg != null) {
						msg.destroy();
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
		assertThat(outDispatcherStub.headersReceived.get(0), is(WRITE_AHEAD_HEADER));
	}
}