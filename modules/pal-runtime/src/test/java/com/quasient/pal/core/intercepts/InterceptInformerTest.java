/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercepts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.quasient.pal.common.directory.events.InterceptEvent;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import com.quasient.pal.core.ZmqEnabledTest;
import com.quasient.pal.core.messages.InterceptEventMsg;
import com.quasient.pal.cxn.DirectoryConnectionProvider;
import com.quasient.pal.cxn.PalDirectory;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.serdes.colfer.MessageBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

public class InterceptInformerTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  private final UUID peerUuid = UUID.randomUUID();
  private ZContext context;
  private ExecutorService execService;
  private InterceptInformer interceptInformer;
  private final MessageBuilder msgBuilder = new MessageBuilder();
  private DirectoryConnectionProvider directoryConnectionProvider;
  private PalDirectory palDirectory;
  private List<InterceptMessage> interceptRequestMessages;
  private List<String> requestsToUnregister;
  private static final String INTERCEPT_REG_ADDRESS = "inproc://intercepts.reg";

  private class InterceptsStub implements Runnable {
    @Override
    public void run() {
      Socket repSocket = context.createSocket(SocketType.REP);
      repSocket.bind(INTERCEPT_REG_ADDRESS);
      while (!Thread.interrupted()) {
        try {
          InterceptEventMsg interceptEventMsg = InterceptEventMsg.receive(repSocket, true);
          assert interceptEventMsg != null;
          if (interceptEventMsg.getType().equals(InterceptEventMsg.Type.REGISTER)) {
            InterceptMessage interceptMessage = new InterceptMessage();
            interceptMessage.unmarshal(interceptEventMsg.getBody(), 0);
            interceptRequestMessages.add(interceptMessage);
          } else { // Type.UNREGISTER
            requestsToUnregister.add(interceptEventMsg.getInterceptMessageId());
          }
          repSocket.send("0");
        } catch (ZMQException e) {
          break;
        } catch (Exception e) {
          logger.debug("Exception caught in receive loop", e);
          break;
        }
      }
    }
  }

  @Before
  public void setup() {
    context = createContext();
    execService = Executors.newCachedThreadPool();
    interceptRequestMessages = new ArrayList<>();
    requestsToUnregister = new ArrayList<>();
    palDirectory = mock(PalDirectory.class);
    directoryConnectionProvider = mock(DirectoryConnectionProvider.class);
    when(directoryConnectionProvider.get()).thenReturn(Optional.of(palDirectory));
  }

  @After
  public void cleanup() throws Exception {
    interceptRequestMessages.clear();
    requestsToUnregister.clear();
    closeContext(context);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    Mockito.reset(palDirectory, directoryConnectionProvider);
  }

  @Test
  public void interceptRequestFromRemotePeer() {
    var interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(), // remote peer
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", null));

    // simulate Intercepts registration endpoint
    execService.execute(new InterceptsStub());

    // create and send new intercept event to informer
    final UUID remotePeerUuid = UUID.randomUUID();
    final String interceptId = UUID.randomUUID().toString();
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, peerUuid, INTERCEPT_REG_ADDRESS);
    final InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-id",
            remotePeerUuid,
            interceptId,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that intercept messages were sent
    assertThat(interceptRequestMessages.size(), is(1));
  }

  @Test
  public void unregisterRequestFromRemotePeer() {
    var interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(), // remote peer
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", null));

    // simulate Intercepts registration endpoint
    execService.execute(new InterceptsStub());

    // create and send new intercept event to informer
    final UUID remotePeerUuid = UUID.randomUUID();
    final String interceptId = UUID.randomUUID().toString();
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, peerUuid, INTERCEPT_REG_ADDRESS);
    InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-id",
            remotePeerUuid,
            interceptId,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that intercept messages were sent
    assertThat(interceptRequestMessages.size(), is(1));

    // now unregister the request
    interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_REMOVED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-id",
            remotePeerUuid,
            interceptId,
            null);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that unregister messages were sent
    assertThat(requestsToUnregister.size(), is(1));
  }

  @Test
  public void interceptRequestFromThisPeer() {
    var interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerUuid, // this peer (self)
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", null));

    // simulate Intercepts registration endpoint
    execService.execute(new InterceptsStub());

    // create and send new intercept event to informer
    final String interceptId = UUID.randomUUID().toString();
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, peerUuid, INTERCEPT_REG_ADDRESS);
    final InterceptEvent interceptEvent =
        new InterceptEvent(
            InterceptEvent.Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-id",
            peerUuid,
            interceptId,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that NO intercept messages were sent
    assertThat(interceptRequestMessages.size(), is(0));
  }
}
