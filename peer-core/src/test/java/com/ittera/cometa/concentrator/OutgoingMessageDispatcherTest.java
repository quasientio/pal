package com.ittera.cometa.concentrator;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.messages.ExecMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufExecMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeaderType;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class OutgoingMessageDispatcherTest extends ZmqEnabledTest {
	private static final Logger logger = LoggerFactory.getLogger("tests");

	private final UUID peerUuid = UUID.randomUUID();
	private final String OUTCELL_ADDR = "inproc://cell";
	private final String OUTPUB_ADDR = "inproc://pub";
	private ZContext context;
	private ServiceManager manager;
	private ExecutorService execService;
	private OutgoingMessageDispatcher outgoingMessageDispatcher;
	private final ExecMessageBuilder msgBuilder = new ProtobufExecMessageBuilder();
	private InternalHeader WRITE_AHEAD_HEADER;

	@Before
	public void setup() {
		this.WRITE_AHEAD_HEADER = msgBuilder.buildWriteAheadHeader(peerUuid);
		this.context = createContext();
		this.execService = Executors.newCachedThreadPool();
		this.outgoingMessageDispatcher = new OutgoingMessageDispatcher(
			OUTCELL_ADDR,
			OUTPUB_ADDR,
			context);
		final Set<Service> services = new HashSet<>(Arrays.asList(this.outgoingMessageDispatcher));
		this.manager = new ServiceManager(services);
	}

	@After
	public void cleanup() throws Exception {
		// close local context
		execService.submit(() -> {
			context.close();
			logger.debug("context terminated");
		});

		// stop executor
		execService.shutdown();
		execService.awaitTermination(3, TimeUnit.SECONDS);
	}

	@Test
	public void startAndStop() throws Exception {
		assertThat(outgoingMessageDispatcher.isRunning(), is(false));

		// start service
		manager.startAsync();
		Thread.sleep(500);
		assertThat(outgoingMessageDispatcher.isRunning(), is(true));

		// shut down
		manager.stopAsync();
	}

	@Test
	public void sendOneReq() throws Exception {
		assertThat(outgoingMessageDispatcher.isRunning(), is(false));

		// start service
		manager.startAsync();
		Thread.sleep(500);
		assertThat(outgoingMessageDispatcher.isRunning(), is(true));

		// create REQ socket to simulate requests (IRL: DispatcherConnector)
		Socket req = context.createSocket(SocketType.REQ);
		req.connect(OUTCELL_ADDR);

		// create SUB socket to simulate LogWriter
		Socket sub = context.createSocket(SocketType.SUB);
		sub.bind(OUTPUB_ADDR);
		sub.subscribe(ZMQ.SUBSCRIPTION_ALL);

		// send 1 message request
		ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
		// send number of headers to follow (= 0), then message
		req.send(Ints.toByteArray(0), ZMQ.SNDMORE);
		req.send(msg.toByteArray());
		// expect a 0-reply
		String reply = req.recvStr();
		assertThat(reply, is("0"));

		// check if it was published
		byte[] buff = sub.recv();
		int headerCount = Ints.fromByteArray(buff);
		if (headerCount > 0) {
			// TODO
		}
		buff = sub.recv();
		ExecMessage publishedMsg = ExecMessage.parseFrom(buff);

		// verify message is what we sent
		assertThat(publishedMsg, is(msg));

		// close local sockets
		req.close();
		sub.close();

		// shut down
		manager.stopAsync();
	}

	@Test
	public void sendOneReqWithHeaders() throws Exception {
		assertThat(outgoingMessageDispatcher.isRunning(), is(false));

		// start service
		manager.startAsync();
		Thread.sleep(500);
		assertThat(outgoingMessageDispatcher.isRunning(), is(true));

		// create REQ socket to simulate requests (IRL: DispatcherConnector)
		Socket req = context.createSocket(SocketType.REQ);
		req.connect(OUTCELL_ADDR);

		// create SUB socket to simulate LogWriter
		Socket sub = context.createSocket(SocketType.SUB);
		sub.bind(OUTPUB_ADDR);
		sub.subscribe(ZMQ.SUBSCRIPTION_ALL);

		// send 1 message request
		ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
		List<InternalHeader> headers = Collections.singletonList(this.WRITE_AHEAD_HEADER);
		// send number of headers to follow (= 1), followed by header, then message
		req.send(Ints.toByteArray(headers.size()), ZMQ.SNDMORE);
		for (InternalHeader header : headers) {
			req.send(header.toByteArray(), ZMQ.SNDMORE);
		}
		req.send(msg.toByteArray());

		// expect a 0-reply
		String reply = req.recvStr();
		assertThat(reply, is("0"));

		// get what was published
		byte[] buff = sub.recv();
		int headerCount = Ints.fromByteArray(buff);
		List<InternalHeader> rcvdHeaders = new ArrayList<>();
		if (headerCount > 0) {
			for (int i = 0; i < headerCount; i++) {
				buff = sub.recv();
				try {
					rcvdHeaders.add(InternalHeader.parseFrom(buff));
				} catch (InvalidProtocolBufferException e) {
					logger.error("Error parsing internal header", e);
				}
			}
		}
		buff = sub.recv();
		ExecMessage publishedMsg = ExecMessage.parseFrom(buff);

		// verify header and msg as expected
		assertThat(rcvdHeaders.get(0).getHeaderType(), is(InternalHeaderType.WRITE_AHEAD));
		assertThat(rcvdHeaders.get(0).getValue(), is(peerUuid.toString()));
		assertThat(publishedMsg, is(msg));

		// close local sockets
		req.close();
		sub.close();

		// shut down
		manager.stopAsync();
	}
}