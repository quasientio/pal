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

package net.ittera.pal.core.rpc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.core.InterceptMatcher;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.core.messages.SessionCommandMsg;
import net.ittera.pal.core.messages.SessionReplyMsg;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PalDirectory;
import net.ittera.pal.messages.OutboundMsg;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.messages.types.SessionCommandType;
import net.ittera.pal.messages.types.SessionStatusType;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

@SuppressWarnings("DoNotMock")
public class DispatcherConnectorTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private static final String MSG_PUBLISHER_ADDRESS = "inproc://cell_test";
  private static final String SESSION_SERVICE_REQ_ADDRESS = "inproc://session_test";

  private final UUID peerUuid = UUID.randomUUID();
  private ZContext context;
  private ExecutorService execService;
  private InterceptMatcher interceptMatcher;
  private DispatcherConnector dispatcherConnector;
  private final MessageBuilder messageBuilder = new MessageBuilder();
  private MessagePublisherStub messagePublisherStub;
  private SessionServiceStub sessionServiceStub;
  private InternalHeader writeAheadHeader;
  private DirectoryConnectionProvider directoryConnectionProvider;
  private List<ExecMessage> messagesToMatchReceived;

  @Before
  public void setup() throws Exception {
    this.writeAheadHeader = messageBuilder.buildWriteAheadHeader(peerUuid);
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    PalDirectory mockDirectory = mock(PalDirectory.class);
    directoryConnectionProvider = mock(DirectoryConnectionProvider.class);
    when(directoryConnectionProvider.get()).thenReturn(Optional.of(mockDirectory));
    messagesToMatchReceived = new ArrayList<>();
    interceptMatcher = mock(InterceptMatcher.class);
    when(interceptMatcher.getMatchingIntercepts(any(), any(), any()))
        .thenAnswer(
            (Answer<?>)
                invocation -> {
                  ExecMessage execMessage = (ExecMessage) invocation.getArguments()[0];
                  messagesToMatchReceived.add(execMessage);
                  // we're not testing matching here, so just return nothing
                  return Collections.emptyList();
                });

    // start stub services
    CountDownLatch latch = new CountDownLatch(2);
    messagePublisherStub = new MessagePublisherStub(context, latch);
    sessionServiceStub = new SessionServiceStub(context, latch);
    execService.execute(messagePublisherStub);
    execService.execute(sessionServiceStub);

    // wait for services to start
    latch.await();
  }

  @After
  public void cleanup() throws Exception {
    messagesToMatchReceived.clear();
    messagePublisherStub.requestStop();
    sessionServiceStub.requestStop();
    dispatcherConnector.closeThreadLocalSockets();
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    closeContext(context);
  }

  private DispatcherConnector initDispatcherConnector(
      boolean withPublishing, boolean withIntercepts) {
    Set<RunOptions> runOptions = EnumSet.noneOf(RunOptions.class);
    if (withPublishing) {
      runOptions.add(RunOptions.WITH_TCP_PUB);
    }
    if (withIntercepts) {
      runOptions.add(RunOptions.WITH_INTERCEPTS);
    }
    return new DispatcherConnector(
        context,
        peerUuid,
        messageBuilder,
        directoryConnectionProvider,
        runOptions,
        interceptMatcher,
        MSG_PUBLISHER_ADDRESS,
        SESSION_SERVICE_REQ_ADDRESS);
  }

  // <editor-fold desc="Tests">
  @Test
  public void sendExecMessage() {
    sendExecMessageWithConditions(true, true);
  }

  @Test
  public void sendExecMessageNoPublishing() {
    sendExecMessageWithConditions(false, true);
  }

  @Test
  public void sendExecMessageMany() {
    sendExecMessageManyWithConditions(true, true);
  }

  @Test
  public void sendExecMessageManyNoPublishing() {
    sendExecMessageManyWithConditions(false, true);
  }

  @Test
  public void writeAhead() {
    writeAheadWithConditions(true, true);
  }

  @Test
  public void writeAheadNoPublishing() {
    writeAheadWithConditions(false, true);
  }

  @Test
  public void sendMessagesToSessionService() {
    logger.debug("entering sendMessageToSessionService");
    this.dispatcherConnector = initDispatcherConnector(false, false);

    // sends 2 messages and get reply
    SessionCommandMsg sessionCommandMsg1 =
        new SessionCommandMsg(
            SessionCommandType.STORE_OBJECT, UUID.randomUUID(), ObjectRef.from("39872356"));
    SessionReplyMsg returnedMsg1 =
        dispatcherConnector.sendMessageToSessionService(sessionCommandMsg1);

    SessionCommandMsg sessionCommandMsg2 =
        new SessionCommandMsg(
            SessionCommandType.STORE_OBJECT, UUID.randomUUID(), ObjectRef.from("7734876"));
    SessionReplyMsg returnedMsg2 =
        dispatcherConnector.sendMessageToSessionService(sessionCommandMsg2);

    // reply has status OK
    assertThat(returnedMsg1.getStatus(), is(SessionStatusType.OK));
    assertThat(returnedMsg2.getStatus(), is(SessionStatusType.OK));

    // verify messages received by SessionService service
    assertThat(sessionServiceStub.messagesReceived.size(), is(2));
    assertThat(sessionServiceStub.messagesReceived.get(0), is(sessionCommandMsg1));
    assertThat(sessionServiceStub.messagesReceived.get(1), is(sessionCommandMsg2));

    logger.debug("leaving sendMessageToSessionService");
  }

  // </editor-fold>

  // <editor-fold desc="Private helper methods">
  @SuppressWarnings("SameParameterValue")
  private void sendExecMessageWithConditions(boolean withPublishing, boolean withIntercepts) {
    logger.debug(
        "entering sendExecMessageWithConditions w/publishing: {}, w/intercepts: {}",
        withPublishing,
        withIntercepts);
    this.dispatcherConnector = initDispatcherConnector(withPublishing, withIntercepts);
    // sends msg and get reply
    ExecMessage msg = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    ExecMessage returnedMsg =
        dispatcherConnector.sendExecMessage(messageBuilder.wrap(msg), ExecPhase.BEFORE);

    // should return same message
    assertThat(returnedMsg, is(msg));

    // verify message was received by Intercepts service
    assertThat(messagesToMatchReceived.size(), is(1));
    assertThat(messagesToMatchReceived.get(0), is(msg));

    // verify message was received by Message Publisher
    if (withPublishing) {
      assertThat(messagePublisherStub.messagesReceived.size(), is(1));
      assertThat(
          messagePublisherStub.messagesReceived.stream()
              .map(Message::getExecMessage)
              .collect(Collectors.toList()),
          is(Collections.singletonList(msg)));
    } else {
      assertThat(messagePublisherStub.messagesReceived.size(), is(0));
    }

    logger.debug("leaving sendExecMessageWithConditions");
  }

  @SuppressWarnings("SameParameterValue")
  private void sendExecMessageManyWithConditions(boolean withPublishing, boolean withIntercepts) {
    logger.debug(
        "entering sendExecMessageManyWithConditions w/publishing: {}, w/intercepts: {}",
        withPublishing,
        withIntercepts);
    this.dispatcherConnector = initDispatcherConnector(withPublishing, withIntercepts);
    int messagesToSend = 10;
    List<ExecMessage> sentMessages = new ArrayList<>();
    List<ExecMessage> returnedMessages = new ArrayList<>();

    // sends messages and get replies
    for (int i = 0; i < messagesToSend; i++) {
      ExecMessage msg = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      sentMessages.add(msg);
      ExecMessage returnedMsg =
          dispatcherConnector.sendExecMessage(messageBuilder.wrap(msg), ExecPhase.BEFORE);
      returnedMessages.add(returnedMsg);
    }

    // should return same messages
    assertThat(returnedMessages, is(sentMessages));

    // verify messages received by Intercepts service
    assertThat(messagesToMatchReceived.size(), is(messagesToSend));
    assertThat(messagesToMatchReceived, is(sentMessages));

    // verify messages received by Message Publisher
    if (withPublishing) {
      assertThat(messagePublisherStub.messagesReceived.size(), is(messagesToSend));
      assertThat(
          messagePublisherStub.messagesReceived.stream()
              .map(Message::getExecMessage)
              .collect(Collectors.toList()),
          is(sentMessages));
    } else {
      assertThat(messagePublisherStub.messagesReceived.size(), is(0));
    }
    logger.debug("leaving sendExecMessageManyWithConditions");
  }

  @SuppressWarnings("SameParameterValue")
  private void writeAheadWithConditions(boolean withPublishing, boolean withIntercepts) {
    logger.debug(
        "test writeAheadWithConditions (publishing={}, intercepts={})",
        withPublishing,
        withIntercepts);
    this.dispatcherConnector = initDispatcherConnector(withPublishing, withIntercepts);
    ExecMessage msg = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    dispatcherConnector.writeAhead(msg, MessageType.EXEC_CONSTRUCTOR);

    // verify NO messages received by Intercepts service
    assertThat(messagesToMatchReceived.size(), is(0));

    // verify message and header received by Message Publisher
    if (withPublishing) {
      assertThat(messagePublisherStub.messagesReceived.size(), is(1));
      assertThat(
          messagePublisherStub.messagesReceived.stream()
              .map(Message::getExecMessage)
              .collect(Collectors.toList()),
          is(Collections.singletonList(msg)));

      assertThat(messagePublisherStub.headersReceived.size(), is(1));
      assertThat(messagePublisherStub.headersReceived.get(0), is(writeAheadHeader));
    } else {
      assertThat(messagePublisherStub.messagesReceived.size(), is(0));
      assertThat(messagePublisherStub.headersReceived.size(), is(0));
    }
    logger.debug("test writeAheadWithConditions done (publishing={})", withPublishing);
  }

  // </editor-fold>

  // <editor-fold desc="Stub classes">
  private static final class SessionServiceStub implements Runnable {
    List<SessionCommandMsg> messagesReceived = new ArrayList<>();
    private volatile boolean stopRequested;
    private final ZContext context;
    private final CountDownLatch latch;

    SessionServiceStub(ZContext context, CountDownLatch latch) {
      this.context = context;
      this.latch = latch;
    }

    void requestStop() {
      stopRequested = true;
    }

    @Override
    public void run() {
      Socket repSocket = context.createSocket(SocketType.REP);
      logger.debug("SessionServiceStub: binding to {}", SESSION_SERVICE_REQ_ADDRESS);
      repSocket.bind(SESSION_SERVICE_REQ_ADDRESS);
      logger.debug("SessionServiceStub: starting");
      latch.countDown();
      while (!stopRequested && !Thread.interrupted()) {
        SessionCommandMsg msg;
        try {
          msg = SessionCommandMsg.receive(repSocket, true);
          if (msg == null) {
            continue;
          }
          messagesReceived.add(msg);
          SessionReplyMsg replyMsg = new SessionReplyMsg(SessionStatusType.OK);
          replyMsg.send(repSocket);
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            break;
          } else if (errorCode == ZError.EINTR) {
            break;
          } else {
            throw ex;
          }
        } catch (Exception e) {
          logger.error("Error parsing received message", e);
        }
      }
      logger.debug("SessionServiceStub: exiting");
    }
  }

  private static final class MessagePublisherStub implements Runnable {
    List<Message> messagesReceived = new ArrayList<>();
    List<InternalHeader> headersReceived = new ArrayList<>();
    private volatile boolean stopRequested;
    private final ZContext context;
    private final CountDownLatch latch;

    MessagePublisherStub(ZContext context, CountDownLatch latch) {
      this.context = context;
      this.latch = latch;
    }

    void requestStop() {
      stopRequested = true;
    }

    @Override
    public void run() {
      Socket repSocket = context.createSocket(SocketType.REP);
      logger.debug("MessagePublisherStub: binding to {}", MSG_PUBLISHER_ADDRESS);
      repSocket.bind(MSG_PUBLISHER_ADDRESS);
      logger.debug("MessagePublisherStub: starting");
      latch.countDown();

      while (!stopRequested && !Thread.interrupted()) {
        OutboundMsg msg;
        try {
          msg = OutboundMsg.receive(repSocket, true);
          if (msg == null) {
            continue;
          }
          // add headers & message to lists for verification
          if (msg.getHeaders() != null) {
            headersReceived.addAll(msg.getHeaders());
          }
          Message message = new Message();
          message.unmarshal(msg.getBody(), 0);
          messagesReceived.add(message);
          repSocket.send("0"); // OK_REPLY
          logger.debug("Publisher stub replied to received message w/id: {}", msg.getMessageId());
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            break;
          } else if (errorCode == ZError.EINTR) {
            break;
          } else {
            logger.error("Unexpected ZMQException", ex);
            throw ex;
          }
        } catch (Exception e) {
          logger.error("Error parsing received message", e);
        }
      }
      logger.debug("MessagePublisherStub: exiting");
    }
  }
  // </editor-fold>
}
