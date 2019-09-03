package com.ittera.cometa.core;

import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.PeerLogDirectory;
import com.ittera.cometa.cxn.ZkClient;
import com.ittera.cometa.messages.ExecMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufExecMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import org.apache.kafka.clients.producer.MockProducer;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

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
	private ZkClient registry;
	private ServiceManager manager;
	private MockProducer<String, ExecMessage> producer;
	private LogInfo log;
	private ZMQ.Socket pubSocket;
	private final String OUT_PUB_ADDR = "inproc://pub";
	private final String OFFSET_PUB_ADDR = "inproc://offsets";
	private static final Set<String> createdLogs = new HashSet<>();
	private final ExecMessageBuilder msgBuilder = new ProtobufExecMessageBuilder();

	private static final String TESTS_ZK_ROOT_PATH = "/cometa_tests";
	private static final String ZK_HOST = "localhost:2181";

	private static void deleteCreatedLogs() throws Exception {
		PeerLogDirectory zkCli = ZkClient.getConnectedClient(ZK_HOST, TESTS_ZK_ROOT_PATH);
		for (String log : createdLogs) {
			zkCli.deleteLogNamed(log);
			logger.debug("Cleaned up left over log: {}", log);
		}
		zkCli.close();
	}

	@AfterClass
	public static void deleteTestRootPaths() throws Exception {
		PeerLogDirectory zkCli = ZkClient.getConnectedClient(ZK_HOST, TESTS_ZK_ROOT_PATH);
		zkCli.deleteRootPaths();
	}

	@After
	public void cleanup() throws Exception {
		execService.shutdown();
		execService.awaitTermination(2, TimeUnit.SECONDS);
		this.registry = null;
		this.zmqContext.close();
		deleteCreatedLogs();
	}

	@Before
	public void setup() throws Exception {
		registry = ZkClient.getConnectedClient(ZK_HOST, TESTS_ZK_ROOT_PATH);
		zmqContext = this.createContext();
		producer = new MockProducer<>();
		logWriter = new LogWriter(
			OUT_PUB_ADDR,
			OFFSET_PUB_ADDR,
			producer,
			zmqContext,
			registry,
			peerUuid);

		final Set<Service> services = new HashSet<>(Arrays.asList(this.logWriter));
		manager = new ServiceManager(services);
		log = registry.createLog("testapp");
		createdLogs.add(log.getName());
		logWriter.writeToLog(log, log, false);
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
	public void publishedMessages() throws Exception {
		assertThat(logWriter.isRunning(), is(false));

		// start services
		manager.startAsync();
		Thread.sleep(500);
		assertThat(logWriter.isRunning(), is(true));

		// we create outPub socket and PUBlish some messages
		pubSocket = zmqContext.createSocket(SocketType.PUB);
		pubSocket.connect(OUT_PUB_ADDR);

		int messagesToSend = 15;
		List<ExecMessage> msgsCreated = new ArrayList<>();
		// create msgs
		for (int i = 0; i < messagesToSend; i++) {
			ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
			msgsCreated.add(msg);
		}

		// PUB them
		msgsCreated.stream().forEach(msg -> {
			// no headers
			pubSocket.send(Ints.toByteArray(0), ZMQ.SNDMORE);
			pubSocket.send(msg.toByteArray());
		});

		// give it some time
		Thread.sleep(1500);

		// shut down
		manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);

		// assert published messages are produced to the log
		List<String> producedMsgUuids = producer.history().stream().map(r -> r.value().getMessageUuid()).collect(Collectors.toList());
		List<String> sentMsgUuids = msgsCreated.stream().map(m -> m.getMessageUuid()).collect(Collectors.toList());
		assertThat(producer.history().size(), is(messagesToSend));
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
		pubSocket.connect(OUT_PUB_ADDR);

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
			// 1. send number of headers to follow,
			pubSocket.send(Ints.toByteArray(headers.size()), ZMQ.SNDMORE);
			// 2. send all headers
			for (InternalHeader hdr : headers) {
				pubSocket.send(header.toByteArray(), ZMQ.SNDMORE);
			}
			// 3. send actual message
			pubSocket.send(msg.toByteArray());
		});

		// give it some time
		Thread.sleep(1500);

		// shut down
		manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);

		// assert published messages are produced to the log
		List<String> producedMsgUuids = producer.history().stream().map(r -> r.value().getMessageUuid()).collect(Collectors.toList());
		List<String> sentMsgUuids = msgsCreated.stream().map(m -> m.getMessageUuid()).collect(Collectors.toList());
		assertThat(producer.history().size(), is(messagesToSend));
		assertThat(producedMsgUuids, is(sentMsgUuids));
	}
}
