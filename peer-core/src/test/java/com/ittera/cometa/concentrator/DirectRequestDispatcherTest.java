package com.ittera.cometa.concentrator;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

import java.util.*;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class DirectRequestDispatcherTest {

	/*
	 a class for Workers (which REPly to Dealer) IRL: PeerMessageInvoker's
	 */
	private class Worker implements Runnable {

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
				System.out.printf("worker '%s' starts dispatching%n", peerUuid);
				try {
					String from = socket.recvStr();
					String msg = socket.recvStr();
					socket.send(String.format("OK - worker <%s> got msg: %s from peer: %s", peerUuid, msg, from));
				} catch (ZMQException ex) {
					int errorCode = ex.getErrorCode();
					if (errorCode == ZError.ETERM) {
						break;
					} else if (errorCode == ZError.EINTR) {
						break;
					}
				} catch (Exception ex) {
					socket.send("ERROR");
				}
			}

			System.out.printf("worker '%s' exits%n", peerUuid);
			this.socket.close();
		}
	}

	/*
	 a class for Clients (which REQuest to Router)
	 */
	private class Client implements Callable {
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
		public Object call() {
			// connect to router
			System.out.printf("new client with identity: %s%n", peerUuid.toString());
			this.socket.setIdentity(peerUuid.toString().getBytes(ZMQ.CHARSET));
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

	private final String ROUTER_ADDR = "tcp://0.0.0.0:5671";
	private final String DEALER_ADDR = "inproc://deal";
	private final String PROXY_CTRL_ADDR = "inproc://ctrl-proxy";
	private ZContext context;
	private List<Worker> workers;
	private List<Client> clients;
	private ServiceManager manager;
	private ExecutorService execService;
	private DirectRequestDispatcher directRequestDispatcher;

	@Before
	public void setup() {
		this.context = createContext();
		this.execService = Executors.newCachedThreadPool();
		this.directRequestDispatcher = new DirectRequestDispatcher(
			ROUTER_ADDR,
			DEALER_ADDR,
			PROXY_CTRL_ADDR,
			context);
		initWorkers(3);

		final Set<Service> services = new HashSet<>(Arrays.asList(this.directRequestDispatcher));
		this.manager = new ServiceManager(services);
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
		System.out.println("executor shut down");
	}

	private void stopProxy() {
		// stop proxy
		Socket ctrl = context.createSocket(SocketType.PAIR);
		ctrl.connect("inproc://ctrl-proxy");
		ctrl.send(ZMQ.PROXY_TERMINATE);
		ctrl.close();
	}

	private ZContext createContext() {
		ZContext ctxt = new ZContext();
		ctxt.setLinger(1000);
		ctxt.setRcvHWM(10000);
		ctxt.setSndHWM(10000);
		return ctxt;
	}

	private void initWorkers(int numberOfWorkers) {
		workers = new ArrayList<>();
		for (int i = 0; i < numberOfWorkers; i++) {
			Worker worker = new Worker(this.context, DEALER_ADDR);
			workers.add(worker);
		}
		workers.stream().forEach(w -> execService.submit(w));
	}

	@Test
	public void clientsSendReqsGetWorkersRep() throws Exception {
		assertThat(directRequestDispatcher.isRunning(), is(false));

		// start service
		manager.startAsync();
		Thread.sleep(500);
		assertThat(directRequestDispatcher.isRunning(), is(true));

		// init clients
		clients = new ArrayList<>();
		ZContext remoteCtxt = createContext();
		short numberOfClients = 3;
		final CountDownLatch shutdownLatch = new CountDownLatch(numberOfClients);
		for (int i = 0; i < numberOfClients; i++) {
			clients.add(new Client(remoteCtxt, ROUTER_ADDR, Arrays.asList("Hello", "World", "!"), shutdownLatch));
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
		futureReplies.entrySet().stream().forEach(entry -> {
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


		// shut down
		stopProxy();
		manager.stopAsync();

		// close remote context
		remoteCtxt.close();
		System.out.println("remote ctxt closed");
	}
}
