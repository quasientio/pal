package com.ittera.cometa.concentrator;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import java.util.concurrent.*;

import org.junit.*;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

public class RouterDealerDispatchPOCTest {

	/*
	 a class for Workers (which REPly to Dealer)
	 */
	class Worker implements Runnable {

		private final UUID peerUuid = UUID.randomUUID();
		private Socket socket;
		private ZContext context;
		private String dealerAddress;

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
					String from = socket.recvStr();
					String msg = socket.recvStr();
					socket.send(String.format("OK - worker <%s> got msg: %s from peer: %s", peerUuid, msg, from));
				} catch (Exception ex) {
					socket.send("ERROR");
				}
			}

			this.socket.close();
		}
	}

	/*
	 a class for Clients (which REQuest to Router)
	 */
	class Client implements Callable {
		private final UUID peerUuid = UUID.randomUUID();
		private Socket socket;
		private ZContext context;
		private String routerAddress;
		private List<String> msgsToSend;
		private final CountDownLatch shutdownLatch;

		Client(ZContext context, String routerAddress, List<String> msgsToSend, CountDownLatch shutdownLatch) {
			this.context = context;
			this.routerAddress = routerAddress;
			this.msgsToSend = msgsToSend;
			this.socket = this.context.createSocket(SocketType.REQ);
			this.shutdownLatch = shutdownLatch;
		}

		@Override
		public Object call() throws Exception {
			// connect to router
			System.out.printf("new client with identity: %s%n", peerUuid.toString());
			this.socket.setIdentity(peerUuid.toString().getBytes(ZMQ.CHARSET));
			// System.out.printf("identity after set: %s%n", new String(this.socket.getIdentity(), ZMQ.CHARSET));
			this.socket.connect(this.routerAddress);

			final List<String> replies = new ArrayList<>();

			// send requests
			msgsToSend.stream().forEach(m -> {
				this.socket.send(peerUuid.toString(), ZMQ.SNDMORE);
				this.socket.send(m, 0);
				System.out.printf("sent req: %s%n", m);
				String reply = this.socket.recvStr();
				System.out.printf("got reply: %s%n", reply);
				replies.add(reply);
			});

			this.socket.close();
			System.out.println("client is done");
			shutdownLatch.countDown();

			return replies;
		}
	}

	private String routerAddress = "tcp://0.0.0.0:5671";
	private String dealerAddress = "inproc://deal";
	private ZContext context;
	private Socket router, dealer;
	private List<Worker> workers = new ArrayList<>();
	private List<Client> clients = new ArrayList<>();
	private ExecutorService execService = Executors.newCachedThreadPool();


	@Before
	public void setup() {
		this.context = createContext();
		initRouterDealer();
		initWorkers(2);
	}

	private ZContext createContext() {
		ZContext ctxt = new ZContext();
		ctxt.setLinger(1000);
		ctxt.setRcvHWM(10000);
		ctxt.setSndHWM(10000);
		return ctxt;
	}

	private void initRouterDealer() {
		execService.submit(() -> {
			this.router = context.createSocket(SocketType.ROUTER);
			router.bind(routerAddress);

			this.dealer = context.createSocket(SocketType.DEALER);
			dealer.bind(dealerAddress);

			ZMQ.Socket ctrl = context.createSocket(SocketType.PAIR);
			ctrl.bind("inproc://ctrl-proxy");

			System.out.println("starting router-dealer proxy");
			ZMQ.proxy(router, dealer, null, ctrl);
			System.out.println("exited router-dealer proxy");
		});
	}

	private void initWorkers(int numberOfWorkers) {
		for (int i = 0; i < numberOfWorkers; i++) {
			Worker worker = new Worker(this.context, dealerAddress);
			workers.add(worker);
		}
		workers.stream().forEach(w -> execService.submit(w));
	}

	@Test
	public void testIdentityHeader() throws InterruptedException {

		// init clients
		ZContext remoteCtxt = createContext();
		short numberOfClients = 2;
		final CountDownLatch shutdownLatch = new CountDownLatch(numberOfClients);
		for (int i = 0; i < numberOfClients; i++) {
			clients.add(new Client(remoteCtxt, routerAddress, Arrays.asList("Hello", "World", "!"), shutdownLatch));
		}

		// run clients and store Future replies
		Map<Client, Future<List<String>>> futureReplies = new HashMap<>();
		clients.stream().forEach(c -> {
			Future<List<String>> cliReplies = execService.submit(c);
			futureReplies.put(c, cliReplies);
		});

		// wait for all clients to be finished
		shutdownLatch.await();

		// assert Future replies contain the client (i.e. sender) UUID as returned by the worker
		futureReplies.entrySet().stream().forEach( entry -> {
			Client cli = entry.getKey();
			List<String> replies = null;
			try {
				replies = entry.getValue().get();
			} catch (Exception e) {
				fail();
				e.printStackTrace();
			}
			replies.stream().forEach(r -> assertThat(r, containsString("from peer: " + cli.peerUuid.toString())));
		});

		// stop proxy
		ZMQ.Socket ctrl = context.createSocket(SocketType.PAIR);
		ctrl.connect("inproc://ctrl-proxy");
		ctrl.send(ZMQ.PROXY_TERMINATE);
		ctrl.close();

		// stop executor
		execService.shutdownNow();
		execService.awaitTermination(2, TimeUnit.SECONDS);
		System.out.println("executor shut down");

		// close contexts
		remoteCtxt.close();
		System.out.println("remote ctxt closed");
		this.context.close();
		System.out.println("local ctxt closed");
	}
}
