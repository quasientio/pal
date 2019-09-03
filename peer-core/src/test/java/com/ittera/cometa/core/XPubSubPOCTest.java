package com.ittera.cometa.core;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class XPubSubPOCTest {

	/*
	 a class for Publishers
	 */
	class Publisher implements Runnable {

		private final UUID peerUuid = UUID.randomUUID();
		private Socket socket;
		private ZContext context;
		private String xpubAddress;
		private List<String> msgsToSend;

		Publisher(ZContext context, String xpubAddress, List<String> msgsToSend) {
			this.context = context;
			this.xpubAddress = xpubAddress;
			this.socket = this.context.createSocket(SocketType.PUB);
			this.msgsToSend = msgsToSend;
		}

		@Override
		public void run() {
			// connect to xpub endpoint
			this.socket.connect(this.xpubAddress);

			// process requests
			int messagesSent = 0;
			while (!Thread.interrupted() && messagesSent <= msgsToSend.size()) {
				try {
					socket.send(msgsToSend.get(messagesSent++));
					System.out.printf("publisher sent message: %s%n", msgsToSend.get(messagesSent));
				} catch (Exception ex) {
					socket.send("ERROR");
				}
			}
			this.socket.close();
			System.out.printf("publisher w/ uuid <%s> is finished%n", peerUuid);
		}
	}

	/*
	 a class for Subscriber
	 */
	class Subscriber implements Callable {
		private final UUID peerUuid = UUID.randomUUID();
		private Socket socket;
		private ZContext context;
		private String xsubAddress;
		private int expectedMessages;
		private final CountDownLatch readyLatch;
		private final CountDownLatch shutdownLatch;

		Subscriber(ZContext context, String xsubAddress, int expectedMessages, CountDownLatch readyLatch,
							 CountDownLatch shutdownLatch) {
			this.context = context;
			this.xsubAddress = xsubAddress;
			this.socket = this.context.createSocket(SocketType.SUB);
			this.expectedMessages = expectedMessages;
			this.readyLatch = readyLatch;
			this.shutdownLatch = shutdownLatch;
		}

		@Override
		public Object call() throws Exception {
			System.out.printf("new subscriber with identity: %s%n", peerUuid.toString());
			this.socket.setIdentity(peerUuid.toString().getBytes(ZMQ.CHARSET));
			this.socket.connect(this.xsubAddress);
			this.socket.subscribe(ZMQ.SUBSCRIPTION_ALL);

			final List<String> received = new ArrayList<>();

			readyLatch.countDown();
			while (!Thread.interrupted() && received.size() < expectedMessages) {
				System.out.printf("waiting for msg%n");
				String rcvd = this.socket.recvStr();
				received.add(rcvd);
				System.out.printf("got msg: %s%n", rcvd);
			}

			this.socket.close();
			System.out.printf("subscriber w/ uuid <%s> is finished%n", peerUuid);
			shutdownLatch.countDown();

			return received;
		}
	}

	private String xpubAddress = "inproc://xpub";
	private String xsubAddress = "inproc://xsub";
	private String ctrlProxyAddress = "inproc://ctrl-proxy";
	private ZContext context;
	private Socket xpub, xsub;
	private List<Publisher> publishers = new ArrayList<>();
	private List<Subscriber> subscribers = new ArrayList<>();
	private ExecutorService execService = Executors.newCachedThreadPool();


	private ZContext createContext() {
		ZContext ctxt = new ZContext();
		ctxt.setLinger(1000);
		ctxt.setRcvHWM(10000);
		ctxt.setSndHWM(10000);
		return ctxt;
	}

	private void initXpubXsubProxy() {
		execService.submit(() -> {
			this.xpub = context.createSocket(SocketType.XPUB);
			xpub.bind(xpubAddress);

			this.xsub = context.createSocket(SocketType.XSUB);
			xsub.bind(xsubAddress);

			Socket ctrl = context.createSocket(SocketType.PAIR);
			ctrl.bind(this.ctrlProxyAddress);

			System.out.println("starting xpub-xsub proxy");
			ZMQ.proxy(xpub, xsub, null, ctrl);
			System.out.println("exited xpub-xsub proxy");
		});
	}

	private void initPublishers(int numberOfPublishers, List<String> msgsToSend) {
		for (int i = 0; i < numberOfPublishers; i++) {
			Publisher publisher = new Publisher(this.context, xpubAddress, msgsToSend);
			publishers.add(publisher);
		}
		publishers.stream().forEach(w -> execService.submit(w));
	}

//	@Test
	public void testXPubSub() throws InterruptedException {

		int numberOfPublishers = 3;
		int numberOfSubscribers = 1;
		this.context = createContext();
		initXpubXsubProxy();

		List<String> messages = Arrays.asList("Hello", "world!", "what is going on", "?");

		// init subscriber
		int expectedMessages = numberOfPublishers * messages.size();
		final CountDownLatch shutdownLatch = new CountDownLatch(numberOfSubscribers);
		final CountDownLatch readyLatch = new CountDownLatch(numberOfSubscribers);
		Subscriber subscriber = new Subscriber(this.context, xsubAddress, expectedMessages, readyLatch, shutdownLatch);
		this.subscribers.add(subscriber);

		// run subscriber threads and store Future replies
		Map<Subscriber, Future<List<String>>> futureReplies = new HashMap<>();
		subscribers.stream().forEach(c -> {
			Future<List<String>> cliReplies = execService.submit(c);
			futureReplies.put(c, cliReplies);
		});

		// IMPORTANT to start publishers after subscribers are started and ready
		readyLatch.await();
		System.out.printf("all subscribers ready, starting publishers%n");
		initPublishers(numberOfPublishers, messages);

		// wait for all subscribers to be finished
		shutdownLatch.await();

		// assert Future replies contain the client (i.e. sender) UUID as returned by the publisher
		assertThat(futureReplies.values().size(), is(subscribers.size()));
		futureReplies.values().stream().forEach(v -> {
			try {
				assertThat(v.get().size(), is(expectedMessages));
			} catch (Exception e) {
				e.printStackTrace();
				fail();
			}
		});
		// stop proxy
		Socket ctrl = context.createSocket(SocketType.PAIR);
		ctrl.connect(this.ctrlProxyAddress);
		ctrl.send(ZMQ.PROXY_TERMINATE);
		ctrl.close();

		// stop executor
		execService.shutdownNow();
		execService.awaitTermination(2, TimeUnit.SECONDS);
		System.out.println("executor shut down");

		// close context
		this.context.close();
		System.out.println("local ctxt closed");
	}
}
