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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.core.InterceptMatcher;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.ZmqEnabledTest;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.messages.OutboundMsg;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.serdes.colfer.ColferMessageBuilder;
import org.apache.curator.test.TestingServer;
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

public class DispatcherConnectorTest extends ZmqEnabledTest {

  /*
  MessagePublisher service stub
   */
  private final class MessagePublisherStub implements Runnable {
    List<Message> messagesReceived = new ArrayList<>();
    List<InternalHeader> headersReceived = new ArrayList<>();
    private volatile boolean stopRequested;

    void requestStop() {
      stopRequested = true;
    }

    @Override
    public void run() {
      Socket repSocket = context.createSocket(SocketType.REP);
      repSocket.bind(MSG_PUBLISHER_ADDR);

      while (!stopRequested && !Thread.interrupted()) {
        OutboundMsg msg;
        try {
          msg = OutboundMsg.recvMsg(repSocket, true);
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
          logger.debug(
              "Publisher stub replied to received message w/uuid: {}", msg.getMessageUuid());
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
      logger.debug("MessagePublisherStub: exiting");
    }
  }

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private static final String MSG_PUBLISHER_ADDR = "inproc://cell";
  private static final int TEST_PORT = 2182;
  private static final String CONNECTION_STR = String.format("localhost:%d", TEST_PORT);

  private final UUID peerUuid = UUID.randomUUID();
  private ZContext context;
  private ExecutorService execService;
  private InterceptMatcher interceptMatcher;
  private DispatcherConnector dispatcherConnector;
  private final ColferMessageBuilder msgBuilder = new ColferMessageBuilder();
  private MessagePublisherStub messagePublisherStub;
  private InternalHeader WRITE_AHEAD_HEADER;
  private TestingServer testingServer;
  private DirectoryConnectionProvider directoryConnectionProvider;
  private List<ExecMessage> messagesToMatchReceived;

  @Before
  public void setup() throws Exception {
    this.WRITE_AHEAD_HEADER = msgBuilder.buildWriteAheadHeader(peerUuid);
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    testingServer = new TestingServer(TEST_PORT, true);
    directoryConnectionProvider = new DirectoryConnectionProvider(CONNECTION_STR);
    messagesToMatchReceived = new ArrayList<>();
    interceptMatcher = mock(InterceptMatcher.class);
    when(interceptMatcher.getMatchingIntercepts(any(), any()))
        .thenAnswer(
            (Answer)
                invocation -> {
                  ExecMessage execMessage = (ExecMessage) invocation.getArguments()[0];
                  messagesToMatchReceived.add(execMessage);
                  // we're not testing matching here, so just return nothing
                  return Collections.emptyList();
                });

    // start stub services
    messagePublisherStub = new MessagePublisherStub();
    execService.submit(messagePublisherStub);
  }

  @After
  public void cleanup() throws Exception {
    logger.trace("entering cleanup");
    messagesToMatchReceived.clear();
    messagePublisherStub.requestStop();
    dispatcherConnector.closeThreadLocalSockets();
    directoryConnectionProvider.get().get().close();
    testingServer.close();
    closeContext(context);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);
    logger.trace("leaving cleanup");
  }

  private DispatcherConnector initDispatcherConnector(boolean publishing) {
    Set<RunOptions> runOptions;
    if (!publishing) {
      runOptions = EnumSet.of(RunOptions.NO_PUBLISHING);
    } else {
      runOptions = EnumSet.noneOf(RunOptions.class);
    }
    return new DispatcherConnector(
        context,
        peerUuid,
        msgBuilder,
        directoryConnectionProvider,
        runOptions,
        interceptMatcher,
        MSG_PUBLISHER_ADDR);
  }

  private void sendExecMessage(boolean publishing) throws Exception {
    logger.trace("entering sendExecMessage w/publishing: {}", publishing);
    this.dispatcherConnector = initDispatcherConnector(publishing);
    // sends msg and get reply
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    ExecMessage returnedMsg = dispatcherConnector.sendExecMessage(msg, ExecPhase.BEFORE);
    logger.debug("Dispatcher sent msg w/uuid: {}", msg.getMessageUuid());

    // should return same message
    assertThat(returnedMsg, is(msg));

    // verify message was received by Intercepts service
    assertThat(messagesToMatchReceived.size(), is(1));
    assertThat(messagesToMatchReceived.get(0), is(msg));

    // verify message was received by Message Publisher
    if (publishing) {
      assertThat(messagePublisherStub.messagesReceived.size(), is(1));
      assertThat(
          messagePublisherStub.messagesReceived.stream()
              .map(Message::getExecMessage)
              .collect(Collectors.toList()),
          is(Collections.singletonList(msg)));
    } else {
      assertThat(messagePublisherStub.messagesReceived.size(), is(0));
    }

    logger.trace("leaving sendExecMessage");
  }

  @Test
  public void sendExecMessage() throws Exception {
    sendExecMessage(true);
  }

  @Test
  public void sendExecMessageNoPublishing() throws Exception {
    sendExecMessage(false);
  }

  private void sendExecMessageMany(boolean publishing) throws Exception {
    logger.trace("entering sendExecMessageMany");
    this.dispatcherConnector = initDispatcherConnector(publishing);
    int msgsToSend = 10;
    List<ExecMessage> sentMessages = new ArrayList<>();
    List<ExecMessage> returnedMessages = new ArrayList<>();

    // sends msgs and get replies
    for (int i = 0; i < msgsToSend; i++) {
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      sentMessages.add(msg);
      ExecMessage returnedMsg = dispatcherConnector.sendExecMessage(msg, ExecPhase.BEFORE);
      logger.debug("Dispatcher sent msg w/uuid: {}", msg.getMessageUuid());
      returnedMessages.add(returnedMsg);
    }

    // should return same messages
    assertThat(returnedMessages, is(sentMessages));

    // verify messages received by Intercepts service
    assertThat(messagesToMatchReceived.size(), is(msgsToSend));
    assertThat(messagesToMatchReceived, is(sentMessages));

    // verify messages received by Message Publisher
    if (publishing) {
      assertThat(messagePublisherStub.messagesReceived.size(), is(msgsToSend));
      assertThat(
          messagePublisherStub.messagesReceived.stream()
              .map(Message::getExecMessage)
              .collect(Collectors.toList()),
          is(sentMessages));
    } else {
      assertThat(messagePublisherStub.messagesReceived.size(), is(0));
    }
    logger.trace("leaving sendExecMessageMany");
  }

  @Test
  public void sendExecMessageMany() throws Exception {
    sendExecMessageMany(true);
  }

  @Test
  public void sendExecMessageManyNoPublishing() throws Exception {
    sendExecMessageMany(false);
  }

  private void writeAhead(boolean publishing) throws Exception {
    logger.debug("test writeAhead (publishing={})", publishing);
    this.dispatcherConnector = initDispatcherConnector(publishing);
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    dispatcherConnector.writeAhead(msg);

    // verify NO messages received by Intercepts service
    assertThat(messagesToMatchReceived.size(), is(0));

    // verify message and header received by Message Publisher
    if (publishing) {
      assertThat(messagePublisherStub.messagesReceived.size(), is(1));
      assertThat(
          messagePublisherStub.messagesReceived.stream()
              .map(Message::getExecMessage)
              .collect(Collectors.toList()),
          is(Collections.singletonList(msg)));

      assertThat(messagePublisherStub.headersReceived.size(), is(1));
      assertThat(messagePublisherStub.headersReceived.get(0), is(WRITE_AHEAD_HEADER));
    } else {
      assertThat(messagePublisherStub.messagesReceived.size(), is(0));
      assertThat(messagePublisherStub.headersReceived.size(), is(0));
    }
    logger.debug("test writeAhead done (publishing={})", publishing);
  }

  @Test
  public void writeAhead() throws Exception {
    writeAhead(true);
  }

  @Test
  public void writeAheadNoPublishing() throws Exception {
    writeAhead(false);
  }
}
