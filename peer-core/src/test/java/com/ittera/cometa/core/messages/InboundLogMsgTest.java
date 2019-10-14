package com.ittera.cometa.core.messages;

import com.ittera.cometa.core.ZmqEnabledTest;
import com.ittera.cometa.messages.MessageType;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InboundLogMsgTest extends ZmqEnabledTest {

	@Test
	public void send() {
		long offset = 199;
		byte[] body = "whatever".getBytes();

		InboundLogMsg msg = new InboundLogMsg(MessageType.ExecMessage, offset, body);

		// send
		String socketAddr = "inproc://here";
		assertThat(msg.isEmpty(), is(false));
		ZContext zContext = createContext();
		ZMQ.Socket in = zContext.createSocket(SocketType.REP);
		in.bind(socketAddr);
		ZMQ.Socket out = zContext.createSocket(SocketType.REQ);
		out.connect(socketAddr);
		msg.send(out);

		// verify destroyed
		assertThat(msg.isEmpty(), is(true));

		out.close();
		zContext.destroy();
	}

	@Test
	public void build() {
		long offset = 199;
		byte[] body = "whatever".getBytes();

		InboundLogMsg msg = new InboundLogMsg(MessageType.ExecMessage, offset, body);

		// verify # of frames == 3 actual frames + Empty initial frame to emulate REQ body
		assertThat(msg.size(), is(3 + 1));

		// verify getters
		assertThat(msg.getMessageType(), is(MessageType.ExecMessage));
		assertThat(msg.getOffset(), is(offset));
		assertThat(msg.getBody(), is(body));
	}

	@Test
	public void from() {
		long offset = 199;
		byte[] body = "whatever".getBytes();

		InboundLogMsg msg1 = new InboundLogMsg(MessageType.ExecMessage, offset, body);
		// construct from inner (duplicate)
		InboundLogMsg msg2 = InboundLogMsg.from(msg1.getInner());

		// verify equal contents
		assertTrue(msg2 != msg1);
		assertThat(msg2, is(msg1));

		// clean up
		msg1.destroy();
		msg2.destroy();
	}

	@Test
	public void testEquals() {
		long offset = 199;
		byte[] body = "whatever".getBytes();

		InboundLogMsg msg1 = new InboundLogMsg(MessageType.ExecMessage, offset, body);

		// assert content equality
		assertThat(new InboundLogMsg(MessageType.ExecMessage, offset, body), is(msg1));

		// different type
		assertThat(new InboundLogMsg(MessageType.InterceptRequest, offset, body), is(not(msg1)));

		// different offset
		assertThat(new InboundLogMsg(MessageType.ExecMessage, offset + 1, body), is(not(msg1)));

		// different body
		body = "whatevah".getBytes();
		assertThat(new InboundLogMsg(MessageType.ExecMessage, offset, body), is(not(msg1)));
	}
}