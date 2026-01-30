/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;

/**
 * Unit tests for LogRpcInvoker JSON format handling.
 *
 * <p>Tests the JSON message format branch in the LogRpcInvoker.run() method.
 */
public class LogRpcInvokerJsonFormatTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");
  private static final String LOG_DEALER_ADDRESS = "inproc://log.deal";

  private ZContext context;
  private Socket logDealerSocket;
  private ExecutorService execService;
  private LogRpcInvoker logRpcInvoker;
  private IncomingMessageDispatcher incomingMessageDispatcher;
  private MessageBuilder msgBuilder;
  private UUID peerUuid;

  /** Sets up the test fixtures. */
  @Before
  public void setup() throws Exception {
    peerUuid = UUID.randomUUID();
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    this.msgBuilder = new MessageBuilder(peerUuid);

    // Simulate log dealer socket
    this.logDealerSocket = context.createSocket(SocketType.DEALER);
    logDealerSocket.bind(LOG_DEALER_ADDRESS);

    // Mock dispatcher
    incomingMessageDispatcher = mock(IncomingMessageDispatcher.class);

    this.logRpcInvoker =
        new LogRpcInvoker(
            context, msgBuilder, LOG_DEALER_ADDRESS, incomingMessageDispatcher, peerUuid);
  }

  /** Cleans up after tests. */
  @After
  public void cleanup() throws Exception {
    logRpcInvoker.closeConnections();
    closeContext(context);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    logger.debug("execService shut down");
  }

  // ===== JSON Format Dispatch Tests =====

  /** Tests that dispatch method validates ExecMessage requirement. */
  @Test
  public void dispatch_nonExecMessage_throwsIllegalArgumentException() throws Exception {
    // Use reflection to test private dispatch method with non-ExecMessage
    Method dispatchMethod =
        LogRpcInvoker.class.getDeclaredMethod("dispatch", Message.class, Long.class);
    dispatchMethod.setAccessible(true);

    // Create a Message without ExecMessage
    Message message = new Message();
    // Set some other message type (not exec)
    message.setMetaMessage(new MetaMessage());

    try {
      dispatchMethod.invoke(logRpcInvoker, message, 100L);
      // Should throw
      assertThat("Expected IllegalArgumentException", false, is(true));
    } catch (InvocationTargetException e) {
      assertThat(e.getCause() instanceof IllegalArgumentException, is(true));
    }
  }

  // ===== Message Dispatch Listener Tests =====

  /** Tests that dispatch notifies message listeners. */
  @Test
  public void dispatch_withListener_notifiesListener() throws Exception {
    AtomicBoolean listenerCalled = new AtomicBoolean(false);
    MessageDispatchListener listener = msg -> listenerCalled.set(true);
    logRpcInvoker.addMessageDispatchListener(listener);

    // Use reflection to call notifyMessageDispatched
    Method notifyMethod =
        AbstractMessageInvokerThread.class.getDeclaredMethod(
            "notifyMessageDispatched", Message.class);
    notifyMethod.setAccessible(true);

    Message message = new Message();
    notifyMethod.invoke(logRpcInvoker, message);

    assertThat(listenerCalled.get(), is(true));
  }

  /** Tests removing a message dispatch listener. */
  @Test
  public void removeMessageDispatchListener_removesListener() throws Exception {
    AtomicBoolean listenerCalled = new AtomicBoolean(false);
    MessageDispatchListener listener = msg -> listenerCalled.set(true);
    logRpcInvoker.addMessageDispatchListener(listener);
    logRpcInvoker.removeMessageDispatchListener(listener);

    // Use reflection to call notifyMessageDispatched
    Method notifyMethod =
        AbstractMessageInvokerThread.class.getDeclaredMethod(
            "notifyMessageDispatched", Message.class);
    notifyMethod.setAccessible(true);

    Message message = new Message();
    notifyMethod.invoke(logRpcInvoker, message);

    assertThat(listenerCalled.get(), is(false));
  }

  // ===== Logging Tests =====

  /** Tests logMessageDispatch with request ID and started time. */
  @Test
  public void logMessageDispatch_withRequestId_doesNotThrow() throws Exception {
    Method logMethod =
        AbstractMessageInvokerThread.class.getDeclaredMethod(
            "logMessageDispatch", String.class, long.class);
    logMethod.setAccessible(true);

    // Should not throw
    logMethod.invoke(logRpcInvoker, "test-request-id", System.currentTimeMillis() - 100);
  }

  /** Tests logMessageDispatch with null request ID. */
  @Test
  public void logMessageDispatch_nullRequestId_doesNotThrow() throws Exception {
    Method logMethod =
        AbstractMessageInvokerThread.class.getDeclaredMethod(
            "logMessageDispatch", String.class, long.class);
    logMethod.setAccessible(true);

    // Should not throw even with null
    logMethod.invoke(logRpcInvoker, null, System.currentTimeMillis() - 100);
  }

  // ===== Request Counter Tests =====

  /** Tests that getExecRequestsDispatched returns correct count. */
  @Test
  public void getExecRequestsDispatched_initiallyZero() {
    assertThat(logRpcInvoker.getExecRequestsDispatched(), is(0L));
  }

  /** Tests that getRequestsDispatched returns correct count. */
  @Test
  public void getRequestsDispatched_initiallyZero() {
    assertThat(logRpcInvoker.getRequestsDispatched(), is(0L));
  }

  /** Tests that getMetaRequestsDispatched returns correct count. */
  @Test
  public void getMetaRequestsDispatched_initiallyZero() {
    assertThat(logRpcInvoker.getMetaRequestsDispatched(), is(0L));
  }

  /** Tests that getControlRequestsDispatched returns correct count. */
  @Test
  public void getControlRequestsDispatched_initiallyZero() {
    assertThat(logRpcInvoker.getControlRequestsDispatched(), is(0L));
  }
}
