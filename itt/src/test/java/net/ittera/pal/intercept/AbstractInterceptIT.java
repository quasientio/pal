/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.intercept;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.ittera.pal.common.api.rmi.ConstructorCall;
import net.ittera.pal.common.api.rmi.InstanceFieldGet;
import net.ittera.pal.common.api.rmi.InstanceMethodCall;
import net.ittera.pal.common.api.rmi.StaticMethodCall;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.cxn.ThinPeer;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.rmi.ExecMessageAssertions;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * Intercept tests may be a little hard to follow, so some notes to clarify.
 *
 * <pre>
 * - Two ThinPeers are used, one for invoking a call (to a method or fieldop)
 *   and a second one to send messages to the peer before/after callbacks in
 *   order to verify state object/class values.
 * - The peer against which we run the tests must be started with at least two
 *   REQ threads (--tcp-req-core-threads=2), one to receive the method/fieldop
 *   invocation, and another one to get requests that verify state.
 * - Verification of object/class values cannot fail() or throw assertion
 *   errors since these verifications happen in a separate thread (via an
 *   executor) and are not caught by JUnit (which only catches AssertionErrors
 *   thrown from its main thread). So we save any assertion errors until we can
 *   throw them from the main thread after calls are finished.
 *
 * </pre>
 */
public class AbstractInterceptIT implements ExecMessageAssertions {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  protected static final long INTERCEPT_REGISTRATION_MAX_DELAY_MS = 100;
  private static final String REQ_ADDRESS = "tcp://localhost:7890";

  protected MessageBuilder messageBuilder;
  protected final UUID myPeerUuid = UUID.randomUUID();
  protected ExecutorService executor;

  private PALDirectory palDirectory;
  private PeerInfo interceptablePeer;
  private ZContext zmqContext;

  // a ThinPeer to invoke methods/fieldops that will trigger callbacks
  private ThinPeer thinPeer;

  // a 2nd ThinPeer to be used for verifications from callback threads
  protected ThinPeer verifierThinPeer;

  // placeholder for assertion errors that happen outside junit's main thread
  protected AssertionError assertionError;

  // per-thread REP socket to receive callbacks
  private final ThreadLocal<Socket> threadRepSocket =
      new ThreadLocal<Socket>() {
        @Override
        protected Socket initialValue() {
          Socket callbackSocket = zmqContext.createSocket(SocketType.REP);
          callbackSocket.bind(REQ_ADDRESS);
          logger.debug("Created and connected REP new socket to address: {}", REQ_ADDRESS);
          threadRepSocketCreated.set(true);
          return callbackSocket;
        }
      };
  // flag to avoid closing a socket that hasn't been created
  private final ThreadLocal<Boolean> threadRepSocketCreated = ThreadLocal.withInitial(() -> false);

  class ExceptionCatchingThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r);
      thread.setUncaughtExceptionHandler(
          (t, e) -> logger.error("Uncaught exception in executor thread", e));
      return thread;
    }
  }

  @Before
  public void setUp() throws Exception {
    final String palDirectoryURL = System.getenv("PAL_DIRECTORY");
    if (palDirectoryURL == null) {
      throw new RuntimeException(
          "Please set the environment variable PAL_DIRECTORY (eg. PAL_DIRECTORY=localhost:2379)");
    }
    DirectoryConnectionProvider directoryConnectionProvider =
        new DirectoryConnectionProvider(palDirectoryURL, null);
    this.palDirectory =
        directoryConnectionProvider
            .get()
            .orElseThrow(() -> new RuntimeException("No connection for PALDirectory"));
    this.interceptablePeer =
        findRegisteredPeerListening()
            .orElseThrow(() -> new RuntimeException("No registered peer listening for requests"));
    this.thinPeer =
        new ThinPeer()
            .withUUID(myPeerUuid)
            .withName("InterceptTestClient")
            .withReqAddress(REQ_ADDRESS)
            .withInitialPeer(interceptablePeer)
            .withDirectoryProvider(directoryConnectionProvider)
            .init();
    this.verifierThinPeer =
        new ThinPeer()
            .withUUID(UUID.randomUUID())
            .withName("Verifier")
            .withInitialPeer(interceptablePeer)
            .withDirectoryProvider(directoryConnectionProvider)
            .init();
    this.messageBuilder = new MessageBuilder();
    this.zmqContext = createContext();
    this.executor = Executors.newFixedThreadPool(1, new ExceptionCatchingThreadFactory());
  }

  protected Message receiveCallbackVerifyAndReply(Supplier<AssertionError> test) {
    byte[] req;
    logger.debug("Receiving callback message from socket");
    Socket callbackSocket = threadRepSocket.get();
    req = callbackSocket.recv(0);
    Message callbackMsg = new Message();
    callbackMsg.unmarshal(req, 0);
    logger.debug("Received callback message: {}", ColferUtils.format(callbackMsg));

    // verifications that should be done before replying
    assertionError = test.get();

    // fake a return value
    Method dummyMethod = null;
    try {
      dummyMethod = Object.class.getMethod("toString", (Class<?>[]) null);
    } catch (NoSuchMethodException e) {
      logger.error("Method not found", e);
    }
    ExecMessage returnValue =
        messageBuilder.buildReturnValue(
            myPeerUuid, "dummy return value", dummyMethod, null, true, null);
    Message wrappedMessage = messageBuilder.wrap(returnValue);
    byte[] messageAsBytes = ColferUtils.toBytes(wrappedMessage);
    callbackSocket.send(messageAsBytes);
    logger.debug("Sent callback fake return value: {}", ColferUtils.format(wrappedMessage));
    return callbackMsg;
  }

  private ZContext createContext() {
    ZContext ctxt = new ZContext();
    ctxt.setLinger(1000);
    ctxt.setRcvHWM(10000);
    ctxt.setSndHWM(10000);
    return ctxt;
  }

  protected InterceptRequest<InterceptableMethodCall> createMethodCallInterceptRequest(
      UUID uuid,
      InterceptType type,
      String classname,
      String callbackClass,
      String callbackMethod,
      InterceptableMethodCall interceptableMethodCall) {
    return new InterceptRequest<>(
        uuid, myPeerUuid, type, classname, callbackClass, callbackMethod, interceptableMethodCall);
  }

  protected final void register(InterceptRequest<?> interceptRequest) throws Exception {
    palDirectory.registerIntercept(interceptRequest);
  }

  private Optional<PeerInfo> findRegisteredPeerListening() throws Exception {
    return palDirectory.getAllPeers().stream()
        .filter(peer -> !myPeerUuid.equals(peer.getUuid()) && peer.getReqAddress() != null)
        .findFirst();
  }

  protected ExecMessage invoke(ConstructorCall constructorCall) {
    return thinPeer.sendToPeer(
        messageBuilder.buildConstructor(myPeerUuid, null, null, constructorCall));
  }

  protected ExecMessage invoke(StaticMethodCall staticMethodCall) {
    return thinPeer.sendToPeer(
        messageBuilder.buildClassMethod(myPeerUuid, null, null, staticMethodCall));
  }

  protected ExecMessage invoke(InstanceMethodCall instanceMethodCall) {
    return thinPeer.sendToPeer(messageBuilder.buildInstanceMethod(myPeerUuid, instanceMethodCall));
  }

  protected ExecMessage invoke(InstanceFieldGet instanceFieldGet, ThinPeer thinPeerToUse) {
    return thinPeerToUse.sendToPeer(messageBuilder.buildGetObject(myPeerUuid, instanceFieldGet));
  }

  protected void closeContext() throws InterruptedException {
    ExecutorService execService = Executors.newCachedThreadPool();
    execService.submit(
        () -> {
          zmqContext.close();
          logger.debug("zmq context terminated");
        });

    // stop executor
    execService.shutdown();
    execService.awaitTermination(1, TimeUnit.SECONDS);
  }

  @After
  public void tearDown() throws Exception {
    palDirectory.unregisterPeerInterceptRequests(myPeerUuid);
    assertionError = null;
    if (threadRepSocketCreated.get()) {
      Socket socket = threadRepSocket.get();
      if (socket != null) {
        socket.close();
      }
      threadRepSocket.remove();
    }
    closeContext();
    thinPeer.close();
    verifierThinPeer.close();
    palDirectory.close();
    executor.shutdownNow();
  }
}
