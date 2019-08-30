package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.exec.java.IncomingMessageDispatcher;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.lang.reflect.Constructor;

import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.is;

import org.mockito.stubbing.Answer;

import static org.mockito.Mockito.*;

public class LogMessageInvokerTest {
	private final UUID peerUuid = UUID.randomUUID();
	private final String INLOG_ADDR = "inproc://inlog";
	private ZContext context;
	private Socket dealerSocket;
	private ExecutorService execService;
	private LogMessageInvoker logMessageInvoker;
	private IncomingMessageDispatcher incomingMessageDispatcher;
	private final DataMessageBuilder msgBuilder = new ProtobufDataMessageBuilder();
	private List<DataMessage> messageReplies = new ArrayList<>();

	@Before
	public void setup() {
		this.context = createContext();
		this.execService = Executors.newCachedThreadPool();
		// simulate LogReader's DEALER socket
		this.dealerSocket = context.createSocket(SocketType.DEALER);
		dealerSocket.bind(INLOG_ADDR);

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
				messageReplies.add(reply);
				return reply;
			});

		this.logMessageInvoker = new LogMessageInvoker(
			context,
			msgBuilder,
			INLOG_ADDR,
			incomingMessageDispatcher,
			peerUuid);
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

		messageReplies.clear();
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
		execService.submit(logMessageInvoker);

		// deal msg
		int fakeOffset = 0;
		DataMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
		dealerSocket.send("", ZMQ.SNDMORE); //1st frame empty to emulate REQ envelope
		dealerSocket.send(String.valueOf(fakeOffset++), ZMQ.SNDMORE);
		dealerSocket.send(invokable.toByteArray(), 0);

		// wait for msg to be rcvd
		while (logMessageInvoker.getRequestsDispatched().get() < 1) {
			Thread.sleep(100);
		}
		verify(incomingMessageDispatcher, times(1)).incomingCall(any(), anyBoolean());

		assertThat(messageReplies.size(), is(1));

		// assert reply msg followsUuid of original
		assertThat(messageReplies.get(0).getFollowingUuid(), is(invokable.getMessageUuid()));
	}

	@Test
	public void invokeManyMessages() throws Exception {

		// start invoker thread
		execService.submit(logMessageInvoker);

		// deal msg
		int fakeOffset = 0;
		int msgCount = 10;
		List<DataMessage> msgsToInvoke = new ArrayList<>();
		for (int i = 0; i < msgCount; i++) {
			DataMessage invokable = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
			dealerSocket.send("", ZMQ.SNDMORE); //1st frame empty to emulate REQ envelope
			dealerSocket.send(String.valueOf(fakeOffset++), ZMQ.SNDMORE);
			dealerSocket.send(invokable.toByteArray(), 0);
			msgsToInvoke.add(invokable);
		}

		// wait for msg to be rcvd
		while (logMessageInvoker.getRequestsDispatched().get() < msgCount) {
			Thread.sleep(100);
		}

		// assert number of calls
		verify(incomingMessageDispatcher, times(msgCount)).incomingCall(any(), anyBoolean());
		assertThat(messageReplies.size(), is(msgCount));

		// assert reply msg followsUuid of original
		for (int i = 0; i < msgCount; i++) {
			assertThat(messageReplies.get(i).getFollowingUuid(), is(msgsToInvoke.get(i).getMessageUuid()));
		}
	}
}