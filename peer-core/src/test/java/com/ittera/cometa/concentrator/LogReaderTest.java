package com.ittera.cometa.concentrator;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import com.google.protobuf.InvalidProtocolBufferException;
import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.ZkClient;
import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.common.TopicPartition;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

import static org.hamcrest.Matchers.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/*
 a class for Workers (which REPly to Dealer) IRL: LogMessageInvoker's
 */
class Worker implements Runnable {

	private final UUID peerUuid = UUID.randomUUID();
	private ZMQ.Socket socket;
	private ZContext context;
	private String dealerAddress;
	private Set<String> rcvdMsgUuids = new TreeSet<>();

	Worker(ZContext context, String dealerAddress) {
		this.context = context;
		this.dealerAddress = dealerAddress;
		this.socket = this.context.createSocket(SocketType.REP);
	}

	@Override
	public void run() {
		// connect to dealer
		this.socket.connect(this.dealerAddress);

		// process requests
		while (!Thread.interrupted()) {
			//
			try {
				String offset = socket.recvStr();
				long logOffset = Long.parseLong(offset);
				System.out.printf("received offset = %d%n", logOffset);
				byte[] req = socket.recv();
				DataMessage msg = DataMessage.parseFrom(req);
				System.out.printf("msg received = %s%n", msg);
				rcvdMsgUuids.add(msg.getMessageUuid());
			} catch (ZMQException ex) {
				int errorCode = ex.getErrorCode();
				if (errorCode == ZError.ETERM) {
					System.err.println("context terminated");
					break;
				} else if (errorCode == ZError.EINTR) {
					System.err.println("interrupted during recv()");
					break;
				} else {
					ex.printStackTrace();
				}
			} catch (InvalidProtocolBufferException e) {
				e.printStackTrace();
			}
		}

		this.socket.close();
	}

	Set<String> getReceivedMessages() {
		return rcvdMsgUuids;
	}
}

/**
 * CAVEAT: this test doesn't cover Headers as they're not supported by MockConsumer
 */
public class LogReaderTest {

	private ExecutorService execService = Executors.newSingleThreadExecutor();
	private ZContext zmqContext;
	private LogReader logReader;
	private UUID peerUuid = UUID.randomUUID();
	private ZkClient registry;
	private ServiceManager manager;
	private MockConsumer<String, DataMessage> consumer;
	private LogInfo log;
	private final int partition = 0;
	private final String dealerAddr = "inproc://inlog_tests";
	private final String offsetPubAddr = "inproc://offsets_tests";
	private static final String TESTS_ZK_ROOT_PATH = "/cometa_tests";
	private static final String ZK_HOST = "localhost:2181";

	private ZContext createContext() {
		ZContext ctxt = new ZContext();
		ctxt.setLinger(1000);
		ctxt.setRcvHWM(10000);
		ctxt.setSndHWM(10000);
		return ctxt;
	}

	@After
	public void cleanup() throws Exception {
		execService.shutdown();
		execService.awaitTermination(2, TimeUnit.SECONDS);
		this.registry = null;
		this.zmqContext.close();
	}

	@Before
	public void setup() throws Exception {
		this.registry = ZkClient.getConnectedClient(ZK_HOST, TESTS_ZK_ROOT_PATH);
		this.zmqContext = this.createContext();
		this.consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
		this.logReader = new LogReader(
			zmqContext,
			dealerAddr,
			offsetPubAddr,
			registry,
			consumer,
			peerUuid,
			10);
		this.log = registry.createLog("testapp");
		TopicPartition topicPartition = new TopicPartition(log.getName(), 0);
		final List<TopicPartition> topicPartitionList = Collections.singletonList(topicPartition);
		consumer.assign(topicPartitionList);
		consumer.seek(topicPartition, 0);

		final Set<Service> services = new HashSet<>(Arrays.asList(this.logReader));
		this.manager = new ServiceManager(services);
	}

	@Test
	public void notAcceptingConnections() throws Exception {
		assertThat(logReader.isRunning(), is(false));
		assertThat(logReader.isAcceptingRequests(), is(false));

		// start services
		manager.startAsync();
		Thread.sleep(500);
		assertThat(logReader.isRunning(), is(true));

		// DON'T START ACCEPTING REQUESTS
		assertThat(logReader.isAcceptingRequests(), is(false));

		// start worker(s)
		Worker logMsgInvoker = new Worker(this.zmqContext, this.dealerAddr);
		execService.submit(logMsgInvoker);

		// send 1 message
		DataMessageBuilder msgBuilder = new ProtobufDataMessageBuilder();
		String key = peerUuid.toString();
		DataMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

		this.consumer.addRecord(new ConsumerRecord<>
			(this.log.getName(), partition, 0, key, msg));

		// assert received = 0
		assertThat(logMsgInvoker.getReceivedMessages().size(), is(0));

		// shut down
		manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
		this.zmqContext.close();
	}

	@Test
	public void startRunStop() throws Exception {
		assertThat(logReader.isRunning(), is(false));
		assertThat(logReader.isAcceptingRequests(), is(false));

		// start services
		manager.startAsync();
		Thread.sleep(500);
		assertThat(logReader.isRunning(), is(true));
		logReader.acceptConnections(true);
		assertThat(logReader.isAcceptingRequests(), is(true));

		// start worker(s)
		Worker logMsgInvoker = new Worker(this.zmqContext, this.dealerAddr);
		execService.submit(logMsgInvoker);

		// send no messages

		Thread.sleep(500);

		// assert received = 0
		assertThat(logMsgInvoker.getReceivedMessages().size(), is(0));

		// shut down
		manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
		assertThat(logReader.isRunning(), is(false));
		assertThat(logReader.isAcceptingRequests(), is(false));
	}

	@Test
	public void startRun1MessageStop() throws Exception {
		assertThat(logReader.isRunning(), is(false));
		assertThat(logReader.isAcceptingRequests(), is(false));

		// start services
		manager.startAsync();
		Thread.sleep(500);
		assertThat(logReader.isRunning(), is(true));

		logReader.acceptConnections(true);
		assertThat(logReader.isAcceptingRequests(), is(true));

		// start worker(s)
		Worker logMsgInvoker = new Worker(this.zmqContext, this.dealerAddr);
		execService.submit(logMsgInvoker);

		// send 1 message
		DataMessageBuilder msgBuilder = new ProtobufDataMessageBuilder();
		String key = peerUuid.toString();
		DataMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

		this.consumer.addRecord(new ConsumerRecord<>
			(this.log.getName(), partition, 0, key, msg));

		Thread.sleep(1500);
		// assert received
		System.out.printf("received: %s%n", String.join(",", logMsgInvoker.getReceivedMessages()));
		assertThat(logMsgInvoker.getReceivedMessages().size(), is(1));
		assertThat(logMsgInvoker.getReceivedMessages().stream().anyMatch(u -> u.equals(msg.getMessageUuid())),
			is(true));

		// shut down
		consumer.wakeup();
		manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
		assertThat(logReader.isRunning(), is(false));
		assertThat(logReader.isAcceptingRequests(), is(false));
	}

	@Test
	public void startRun100MessageStop() throws Exception {
		assertThat(logReader.isRunning(), is(false));
		assertThat(logReader.isAcceptingRequests(), is(false));

		// start services
		manager.startAsync();
		Thread.sleep(500);
		assertThat(logReader.isRunning(), is(true));

		logReader.acceptConnections(true);
		assertThat(logReader.isAcceptingRequests(), is(true));

		// start worker(s)
		Worker logMsgInvoker = new Worker(this.zmqContext, this.dealerAddr);
		execService.submit(logMsgInvoker);

		// send 1 message
		DataMessageBuilder msgBuilder = new ProtobufDataMessageBuilder();
		String key = peerUuid.toString();
		Set<String> sentUuids = new TreeSet<>();

		int msgsToSend = 100;
		for (int i = 0; i < msgsToSend; i++) {
			DataMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
			this.consumer.addRecord(new ConsumerRecord<>
				(this.log.getName(), partition, i, key, msg));
			sentUuids.add(msg.getMessageUuid());
		}

		Thread.sleep(1500);
		// assert received
		System.out.printf("received: %s%n", String.join(",", logMsgInvoker.getReceivedMessages()));
		assertThat(logMsgInvoker.getReceivedMessages(), is(sentUuids));

		// shut down
		consumer.wakeup();
		manager.stopAsync().awaitStopped(2, TimeUnit.SECONDS);
		assertThat(logReader.isRunning(), is(false));
		assertThat(logReader.isAcceptingRequests(), is(false));
	}
}
