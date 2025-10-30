/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.intercept;

import com.quasient.pal.AbstractIntegrationTest;
import com.quasient.pal.InterceptTestSuite;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.cxn.ThinPeer;
import com.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import com.quasient.pal.cxn.directory.PalDirectory;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Message;
import com.quasient.pal.rpc.binary.ExecMessageAssertions;
import com.quasient.pal.serdes.colfer.ColferUtils;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
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
 * - Two ThinPeers are used, one for invoking a call (to a method or field op)
 *   and a second one to send messages to the peer before/after callbacks in
 *   order to verify state object/class values.
 * - The peer against which we run the tests must be started with at least two
 *   RPC threads (--rpc-threads=2), one to receive the method/field op
 *   invocation, and another one to get requests that verify state.
 * - Verification of object/class values cannot fail() or throw assertion
 *   errors since these verifications happen in a separate thread (via an
 *   executor) and are not caught by JUnit (which only catches AssertionErrors
 *   thrown from its main thread). So we save any assertion errors until we can
 *   throw them from the main thread after calls are finished.
 *
 * </pre>
 */
public class AbstractInterceptIT extends AbstractIntegrationTest implements ExecMessageAssertions {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  protected static final long INTERCEPT_REGISTRATION_MAX_DELAY_MS = 100;
  private static final String RPC_ADDRESS = "tcp://localhost:7890";

  protected MessageBuilder messageBuilder;
  protected final UUID myPeerUuid = UUID.randomUUID();
  protected ExecutorService executor;

  private PalDirectory palDirectory;
  private static ZContext zmqContext;

  // a ThinPeer to invoke methods/field ops that will trigger callbacks
  private ThinPeer thinPeer;

  // a 2nd ThinPeer to be used for verifications from callback threads
  protected ThinPeer verifierThinPeer;

  // placeholder for assertion errors that happen outside JUnit's main thread
  protected AssertionError assertionError;

  // per-thread REP socket to receive callbacks
  private static final ThreadLocal<Socket> threadRepSocket =
      new ThreadLocal<>() {
        @Override
        protected Socket initialValue() {
          Socket callbackSocket = zmqContext.createSocket(SocketType.REP);
          callbackSocket.bind(RPC_ADDRESS);
          logger.debug("Created and connected REP new socket to address: {}", RPC_ADDRESS);
          threadRepSocketCreated.set(true);
          return callbackSocket;
        }
      };
  // flag to avoid closing a socket that hasn't been created
  private static final ThreadLocal<Boolean> threadRepSocketCreated =
      ThreadLocal.withInitial(() -> false);

  private static class ExceptionCatchingThreadFactory implements ThreadFactory {
    @Override
    public Thread newThread(@Nonnull Runnable r) {
      Thread thread = new Thread(r);
      thread.setUncaughtExceptionHandler(
          (t, e) -> logger.error("Uncaught exception in executor thread", e));
      return thread;
    }
  }

  @Before
  public void setUp() throws Exception {
    DirectoryConnectionProvider directoryConnectionProvider =
        new DirectoryConnectionProvider(getPalDirectoryUrl(), null, true);
    this.palDirectory =
        directoryConnectionProvider
            .get()
            .orElseThrow(() -> new RuntimeException("No connection for PalDirectory"));
    // Peer launched by InterceptTestSuite
    // Use the well-known shared peer UUID instead of searching for a peer
    // Retry a few times to allow for directory registration delay
    PeerInfo interceptablePeer = null;
    for (int i = 0; i < 10; i++) {
      interceptablePeer = palDirectory.getPeer(InterceptTestSuite.SHARED_PEER_UUID);
      if (interceptablePeer != null) {
        break;
      }
      logger.debug("Waiting for peer to register in directory (attempt {})", i + 1);
      Thread.sleep(500);
    }
    if (interceptablePeer == null) {
      throw new RuntimeException(
          "Shared intercept test peer not found in directory after 5 seconds");
    }
    this.thinPeer =
        new ThinPeer()
            .withUuid(myPeerUuid)
            .withName("InterceptTestClient")
            .withSelfRegistration(true)
            .withZmqRpcAddress(RPC_ADDRESS)
            .withInitialPeer(interceptablePeer)
            .withDirectoryProvider(directoryConnectionProvider)
            .init();
    this.verifierThinPeer =
        new ThinPeer()
            .withUuid(UUID.randomUUID())
            .withName("Verifier")
            .withInitialPeer(interceptablePeer)
            .withDirectoryProvider(directoryConnectionProvider)
            .init();
    this.messageBuilder = new MessageBuilder(myPeerUuid);
    zmqContext = createZmqContext();
    this.executor = Executors.newFixedThreadPool(1, new ExceptionCatchingThreadFactory());
  }

  protected Message receiveCallbackVerifyAndResponse(Supplier<AssertionError> test) {
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
        messageBuilder.buildReturnValue("dummy return value", dummyMethod, null, true, null);
    Message wrappedMessage = messageBuilder.wrap(returnValue);
    byte[] messageAsBytes = ColferUtils.toBytes(wrappedMessage);
    callbackSocket.send(messageAsBytes);
    logger.debug("Sent callback fake return value: {}", ColferUtils.format(wrappedMessage));
    return callbackMsg;
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
    palDirectory.createIntercept(interceptRequest);
  }

  protected ExecMessage invoke(ExecMessage execMessage) {
    return thinPeer.sendToPeer(execMessage);
  }

  protected ExecMessage invoke(ExecMessage execMessage, ThinPeer withThinPeer) {
    return withThinPeer.sendToPeer(execMessage);
  }

  protected void closeContext() throws InterruptedException {
    ExecutorService execService = Executors.newCachedThreadPool();
    execService.execute(
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
    logger.info("===== AbstractInterceptIT.tearDown: STARTING =====");
    logger.info("Deleting intercepts for peer: {}", myPeerUuid);
    palDirectory.deleteInterceptsForPeer(myPeerUuid);
    assertionError = null;
    if (threadRepSocketCreated.get()) {
      logger.info("Closing threadRepSocket");
      Socket socket = threadRepSocket.get();
      if (socket != null) {
        socket.close();
      }
      threadRepSocket.remove();
      logger.info("threadRepSocket closed");
    }
    logger.info("Closing zmq context");
    closeContext();
    logger.info("Closing thinPeer");
    thinPeer.close();
    logger.info("Closing verifierThinPeer");
    verifierThinPeer.close();
    logger.info("Closing palDirectory");
    palDirectory.close();
    logger.info("Shutting down executor");
    executor.shutdownNow();
    logger.info("===== AbstractInterceptIT.tearDown: COMPLETED =====");
  }
}
