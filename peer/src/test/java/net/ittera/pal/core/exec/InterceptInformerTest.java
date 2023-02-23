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

package net.ittera.pal.core.exec;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.common.directory.events.InterceptEvent;
import net.ittera.pal.common.directory.events.InterceptEvent.Type;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.core.messages.InterceptEvtMsg;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.serdes.colfer.MessageBuilder;
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
  private PALDirectory palDirectory;
  private Socket repSocket;
  private List<InterceptMessage> interceptRequestMessages;
  private List<UUID> requestsToUnregister;
  private static final String INTERCEPT_REG_ADDR = "inproc://intercepts.reg";

  private class InterceptsStub implements Runnable {
    @Override
    public void run() {
      repSocket = context.createSocket(SocketType.REP);
      repSocket.bind(INTERCEPT_REG_ADDR);
      while (!Thread.interrupted()) {
        try {
          InterceptEvtMsg interceptEvtMsg = InterceptEvtMsg.recvMsg(repSocket, true);
          if (interceptEvtMsg.getType().equals(InterceptEvtMsg.Type.REGISTER)) {
            InterceptMessage interceptMessage = new InterceptMessage();
            interceptMessage.unmarshal(interceptEvtMsg.getBody(), 0);
            interceptRequestMessages.add(interceptMessage);
          } else { // Type.UNREGISTER
            requestsToUnregister.add(interceptEvtMsg.getInterceptMsgUUID());
          }
          repSocket.send("0");
        } catch (ZMQException e) {
          break;
        } catch (Exception e) {
          logger.debug("Exception caught in recv loop", e);
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
    palDirectory = mock(PALDirectory.class);
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
    palDirectory.close();
    Mockito.reset(palDirectory, directoryConnectionProvider);
  }

  @Test
  public void interceptRequestFromRemotePeer() throws Exception {
    InterceptRequest interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(), // remote peer
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", null));

    // simulate Intercepts registration endpoint
    execService.submit(new InterceptsStub());

    // create and send new intercept event to informer
    final UUID remotePeerUuid = UUID.randomUUID();
    final UUID interceptUuid = UUID.randomUUID();
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, peerUuid, INTERCEPT_REG_ADDR);
    final InterceptEvent interceptEvent =
        new InterceptEvent(
            Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-uuid",
            remotePeerUuid,
            interceptUuid,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that intercept messages were sent
    assertThat(interceptRequestMessages.size(), is(1));
  }

  @Test
  public void unregisterRequestFromRemotePeer() throws Exception {
    InterceptRequest interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(), // remote peer
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", null));

    // simulate Intercepts registration endpoint
    execService.submit(new InterceptsStub());

    // create and send new intercept event to informer
    final UUID remotePeerUuid = UUID.randomUUID();
    final UUID interceptUuid = UUID.randomUUID();
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, peerUuid, INTERCEPT_REG_ADDR);
    InterceptEvent interceptEvent =
        new InterceptEvent(
            Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-uuid",
            remotePeerUuid,
            interceptUuid,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that intercept messages were sent
    assertThat(interceptRequestMessages.size(), is(1));

    // now unregister the request
    interceptEvent =
        new InterceptEvent(
            Type.INTERCEPT_REMOVED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-uuid",
            remotePeerUuid,
            interceptUuid,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that unregister messages were sent
    assertThat(requestsToUnregister.size(), is(1));
  }

  @Test
  public void interceptRequestFromThisPeer() throws Exception {
    InterceptRequest interceptRequest =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerUuid, // this peer (self)
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", null));

    // simulate Intercepts registration endpoint
    execService.submit(new InterceptsStub());

    // create and send new intercept event to informer
    final UUID interceptUuid = UUID.randomUUID();
    interceptInformer =
        new InterceptInformer(
            context, msgBuilder, directoryConnectionProvider, peerUuid, INTERCEPT_REG_ADDR);
    final InterceptEvent interceptEvent =
        new InterceptEvent(
            Type.INTERCEPT_ADDED,
            "/root/intercepts/dummy-peer-uuid/dummy-intercept-req-uuid",
            peerUuid,
            interceptUuid,
            interceptRequest);
    interceptInformer.interceptEvent(interceptEvent);

    // verify that NO intercept messages were sent
    assertThat(interceptRequestMessages.size(), is(0));
  }
}
