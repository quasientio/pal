package com.ittera.cometa.core;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InterceptRequest;

import org.apache.curator.test.TestingServer;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.Cluster;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.Matchers.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LogWriterTest extends ZmqEnabledTest {
	private static final Logger logger = LoggerFactory.getLogger("tests");
	private ExecutorService execService = Executors.newSingleThreadExecutor();
	private ZContext zmqContext;
	private LogWriter logWriter;
	private UUID peerUuid = UUID.randomUUID();
	private PALDirectory palDirectory;
	private TestingServer testingServer;
	private ServiceManager manager;
	private MockProducer<String, byte[]> producer;
	private LogInfo log;
	private ZMQ.Socket pubSocket;
	private final String OUT_PUB_ADDR = "inproc://pub";
	private final String OFFSET_PUB_ADDR = "inproc://offsets";
	private final String SYNC_SOCKET_ADDRESS = "inproc://sync_socket";
	private static final Set<String> createdLogs = new HashSet<>();
	private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
	private ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");

	private static final int TEST_PORT = 2182;
	private static final String CONNECTION_STR = String.format("localhost:%d", TEST_PORT);

	private void deleteCreatedLogs() throws Exception {
		for (String log : createdLogs) {
			palDirectory.unregisterLog(log);
			logger.debug("Cleaned up left over log: {}", log);
		}
	}

	@After
	public void cleanup() throws Exception {
		execService.shutdown();
		execService.awaitTermination(2, TimeUnit.SECONDS);
		this.zmqContext.close();
		deleteCreatedLogs();
		palDirectory.close();
		testingServer.close();
	}

	@Before
	public void setup() throws Exception {
		testingServer = new TestingServer(TEST_PORT, true);
		palDirectory = new PALDirectory(CONNECTION_STR);
		zmqContext = this.createContext();
		producer = new MockProducer<>(Cluster.empty(), true, null, null, null);
		logWriter = new LogWriter(
			UUID.randomUUID(),
			zmqContext,
			SYNC_SOCKET_ADDRESS,
			servicesThreadGroup,
			"LogWriterTest-Service",
			OUT_PUB_ADDR,
			OFFSET_PUB_ADDR,
			true,
			producer,
			palDirectory);
		final Set<Service> services = new HashSet<>(Arrays.asList(this.logWriter));
		manager = new ServiceManager(services);
		log = this.palDirectory.newLog("testapp");
		createdLogs.add(log.getName());
		logWriter.writeToLog(log, log, false);
	}

	private String getMessageUuid(Message msg) {
		if (msg instanceof ExecMessage) {
			return ((ExecMessage) msg).getMessageUuid();
		} else if (msg instanceof InterceptRequest) {
			return ((InterceptRequest) msg).getMessageUuid();
		}
		return null;
	}

	private String getFollowingUuid(Message msg) {
		if (msg instanceof ExecMessage) {
			ExecMessage execMessage = (ExecMessage) msg;
			if (execMessage.hasFollowingUuid()) {
				return execMessage.getFollowingUuid();
			}
		}
		return null;
	}

	@Test
	public void noPublishedMsgs() throws Exception {
		assertThat(logWriter.isRunning(), is(false));

		// start services
		manager.startAsync();
		Thread.sleep(500);
		assertThat(logWriter.isRunning(), is(true));

		// we PUBlish no messages

		// shut down
		manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);

		// assert NO published message is produced to the log
		assertThat(producer.history().isEmpty(), is(true));
	}

	@Test
	public void publishedMixedMessages() throws Exception {
		assertThat(logWriter.isRunning(), is(false));

		// start services
		manager.startAsync();
		Thread.sleep(500);
		assertThat(logWriter.isRunning(), is(true));

		// we create outPub socket and PUBlish some messages
		pubSocket = zmqContext.createSocket(SocketType.PUB);
		pubSocket.bind(OUT_PUB_ADDR);

		List<Message> msgsCreated = new ArrayList<>();
		// create ExecMessage's
		int execMessagesToSend = 15;
		for (int i = 0; i < execMessagesToSend; i++) {
			ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
			msgsCreated.add(msg);
		}
		// create InterceptRequestMessage's
		int interceptMessagesToSend = 5;
		for (int i = 0; i < interceptMessagesToSend; i++) {
			InterceptRequest msg = msgBuilder.buildInterceptRequest(peerUuid, "java.io.PrintStream",
				"println", null, this.getClass().getName(), "someCallbackMethod");
			msgsCreated.add(msg);
		}

		// PUB them
		msgsCreated.forEach(msg -> {
			// msg type
			pubSocket.send(Ints.toByteArray(MessageType.ExecMessage.ordinal()), ZMQ.SNDMORE);
			// no headers
			pubSocket.send(Ints.toByteArray(0), ZMQ.SNDMORE);
			// msg uuid
			pubSocket.send(getMessageUuid(msg), ZMQ.SNDMORE);
			// followingUuid
			String followingUuid = getFollowingUuid(msg);
			if (followingUuid != null) {
				pubSocket.send(followingUuid, ZMQ.SNDMORE);
			} else {
				pubSocket.send(Ints.toByteArray(0), ZMQ.SNDMORE);
			}
			// msg
			pubSocket.send(msg.toByteArray());
		});

		// give it some time
		Thread.sleep(500);

		// shut down
		manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);

		// assert published messages are produced to the log
		List<String> producedMsgUuids = new ArrayList<>();
		for (ProducerRecord<String, byte[]> record : producer.history()) {
			// since we have no headers, we have to try parsing different message types
			Message msg = null;
			try {
				msg = ExecMessage.parseFrom(record.value());
			} catch (InvalidProtocolBufferException e) {
			}
			if (msg == null) {
				try {
					msg = InterceptRequest.parseFrom(record.value());
				} catch (InvalidProtocolBufferException e) {
				}
			}
			producedMsgUuids.add(getMessageUuid(msg));
		}
		List<String> sentMsgUuids = msgsCreated.stream().map(m -> getMessageUuid(m)).collect(Collectors.toList());
		assertThat(producer.history().size(), is(execMessagesToSend + interceptMessagesToSend));
		assertThat(producedMsgUuids, is(sentMsgUuids));
	}

	@Test
	public void publishedMessagesWithHeader() throws Exception {
		assertThat(logWriter.isRunning(), is(false));

		// start services
		manager.startAsync();
		Thread.sleep(500);
		assertThat(logWriter.isRunning(), is(true));

		// we create outPub socket and PUBlish some messages with header
		pubSocket = zmqContext.createSocket(SocketType.PUB);
		pubSocket.bind(OUT_PUB_ADDR);

		InternalHeader header = msgBuilder.buildWriteAheadHeader(peerUuid);
		List<InternalHeader> headers = Arrays.asList(header);
		int messagesToSend = 5;
		List<ExecMessage> msgsCreated = new ArrayList<>();

		// create msgs
		for (int i = 0; i < messagesToSend; i++) {
			ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
			msgsCreated.add(msg);
		}

		// PUB them
		msgsCreated.stream().forEach(msg -> {
			// 0. send type of message to follow
			pubSocket.send(Ints.toByteArray(MessageType.ExecMessage.ordinal()), ZMQ.SNDMORE);
			// 1. send number of headers to follow,
			pubSocket.send(Ints.toByteArray(headers.size()), ZMQ.SNDMORE);
			// 2. send all headers
			for (InternalHeader hdr : headers) {
				pubSocket.send(hdr.toByteArray(), ZMQ.SNDMORE);
			}
			// 3. msg uuid
			pubSocket.send(msg.getMessageUuid(), ZMQ.SNDMORE);
			// 4. followingUuid
			if (msg.hasFollowingUuid()) {
				pubSocket.send(msg.getFollowingUuid(), ZMQ.SNDMORE);
			} else {
				pubSocket.send(Ints.toByteArray(0), ZMQ.SNDMORE);
			}
			// 5. send actual message
			pubSocket.send(msg.toByteArray());
		});

		// give it some time
		Thread.sleep(500);

		// shut down
		manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);

		// assert published messages are produced to the log
		List<String> producedMsgUuids = new ArrayList<>();
		for (ProducerRecord<String, byte[]> record : producer.history()) {
			producedMsgUuids.add(ExecMessage.parseFrom(record.value()).getMessageUuid());
		}
		List<String> sentMsgUuids = msgsCreated.stream().map(m -> m.getMessageUuid()).collect(Collectors.toList());
		assertThat(producer.history().size(), is(messagesToSend));
		assertThat(producedMsgUuids, is(sentMsgUuids));
	}
}
