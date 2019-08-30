package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.lang.reflect.Constructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;

public class PeerMessageInvokerTest {
	private final UUID peerUuid = UUID.randomUUID();
	private final String DEALER_ADDR = "inproc://deal";
	private ZContext context;
	private Socket dealerSocket;
	private ExecutorService execService;
	private PeerMessageInvoker peerMessageInvoker;
	private IncomingMessageDispatcher incomingMessageDispatcher;
	private final DataMessageBuilder msgBuilder = new ProtobufDataMessageBuilder();

	@Before
	public void setup() {
		this.context = createContext();
		this.execService = Executors.newCachedThreadPool();
		// simulate DirectRequestDispatcher's DEALER socket
		this.dealerSocket = context.createSocket(SocketType.DEALER);
		dealerSocket.bind(DEALER_ADDR);

		/* mock incomingMessageDispatcher */
		incomingMessageDispatcher = mock(IncomingMessageDispatcher.class);

		// stub incomingCall to return a message which seems valid reply
		when(incomingMessageDispatcher.incomingCall(any(), anyBoolean())).thenAnswer(
			(Answer) invocation -> {
				Object[] args = invocation.getArguments();
				DataMessage incomingMsg = (DataMessage) args[0];
				Constructor constructor = null;
				try {
					constructor = String.class.getConstructor();
				} catch (NoSuchMethodException e) {
					e.printStackTrace();
				}
				DataMessage reply = msgBuilder.buildReturnValue(peerUuid, new String(), constructor, null,
					false, incomingMsg.getMessageUuid());
				return reply;
			});

		this.peerMessageInvoker = new PeerMessageInvoker(
			context,
			msgBuilder,
			DEALER_ADDR,
			incomingMessageDispatcher);
	}

	@After
	public void cleanup() throws Exception {
		// close local context
		execService.submit(() -> {
			context.close();
			System.out.println("context terminated");
		});

		// stop executor
		execService.shutdown();
		execService.awaitTermination(3, TimeUnit.SECONDS);
		System.out.println("execService shut down");
	}

	private ZContext createContext() {
		ZContext ctxt = new ZContext();
		ctxt.setLinger(1000);
		ctxt.setRcvHWM(10000);
		ctxt.setSndHWM(10000);
		return ctxt;
	}

	@Test
	public void invokeOneMessage() throws Exception {

		// start invoker thread
		execService.submit(peerMessageInvoker);

		// deal msg
		DataMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
		dealerSocket.send("", ZMQ.SNDMORE); //1st frame empty to emulate REQ envelope
		dealerSocket.send(invokable.toByteArray(), 0);
		// get reply
		dealerSocket.recv(); //1st frame empty to emulate REP envelope
		byte[] buff = dealerSocket.recv();
		DataMessage reply = DataMessage.parseFrom(buff);

		assertThat(peerMessageInvoker.getRequestsDispatched().get(), is(Long.valueOf(1)));
		verify(incomingMessageDispatcher, times(1)).incomingCall(any(), anyBoolean());

		// assert reply msg followsUuid of original
		assertThat(reply.getFollowingUuid(), is(invokable.getMessageUuid()));
	}

	@Test
	public void invokeManyMessages() throws Exception {

		// start invoker thread
		execService.submit(peerMessageInvoker);

		// deal msgs
		int msgCount = 10;
		List<DataMessage> msgsToInvoke = new ArrayList<>();
		List<DataMessage> replyMessages = new ArrayList<>();
		for (int i = 0; i < msgCount; i++) {
			// deal msg
			DataMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
			dealerSocket.send("", ZMQ.SNDMORE); //1st frame empty to emulate REQ envelope
			dealerSocket.send(invokable.toByteArray(), 0);
			msgsToInvoke.add(invokable);
			// get reply
			dealerSocket.recv(); //1st frame empty to emulate REP envelope
			byte[] buff = dealerSocket.recv();
			DataMessage reply = DataMessage.parseFrom(buff);
			replyMessages.add(reply);
		}

		// assert number of calls
		assertThat(peerMessageInvoker.getRequestsDispatched().get(), is(Long.valueOf(msgCount)));
		verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), anyBoolean());

		// assert reply msg followsUuid of original
		for (int i = 0; i < msgCount; i++) {
			assertThat(replyMessages.get(i).getFollowingUuid(), is(msgsToInvoke.get(i).getMessageUuid()));
		}
	}
}