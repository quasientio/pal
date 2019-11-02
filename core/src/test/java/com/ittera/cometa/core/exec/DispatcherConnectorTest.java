package com.ittera.cometa.core.exec;

import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.ittera.cometa.core.ZmqEnabledTest;
import com.ittera.cometa.core.messages.OutboundMsg;
import com.ittera.cometa.cxn.PALDirectory;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.MessageType;
import com.ittera.cometa.messages.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeader;
import com.ittera.cometa.messages.protobuf.Intercepts;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptRequest;
import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessage;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.*;
import org.zeromq.ZMQ.Socket;
import zmq.ZError;

public class DispatcherConnectorTest extends ZmqEnabledTest {
  private static final Logger logger = LoggerFactory.getLogger("tests");

  private final class OutgoingMessageDispatcherStub implements Runnable {
    List<Object> messagesReceived = new ArrayList<>();
    List<InternalHeader> headersReceived = new ArrayList<>();

    void clear() {
      outDispatcherStub.messagesReceived.clear();
      outDispatcherStub.headersReceived.clear();
    }

    @Override
    public void run() {
      Socket repSocket = context.createSocket(SocketType.REP);
      repSocket.bind(OUTCELL_ADDR);

      while (!Thread.interrupted()) {
        OutboundMsg msg = null;
        try {
          msg = OutboundMsg.recvMsg(repSocket);
          if (msg == null) {
            continue;
          }
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Received new message w/uuid: {} ({} bytes)", msg.getMessageUuid(), msg.getSize());
          }
          // add headers & message to lists for verification
          if (msg.getHeaders() != null) {
            headersReceived.addAll(msg.getHeaders());
          }
          if (msg.getMessageType().equals(MessageType.ExecMessage)) {
            messagesReceived.add(ExecMessage.parseFrom(msg.getBody()));
          } else if (msg.getMessageType().equals(MessageType.InterceptRequest)) {
            messagesReceived.add(InterceptRequest.parseFrom(msg.getBody()));
          } else {
            throw new RuntimeException(format("unhandled message type: %s", msg.getMessageType()));
          }
          // reply: pretend message has no actors and send 0 back
          repSocket.send("0");
        } catch (ZMQException ex) {
          int errorCode = ex.getErrorCode();
          if (errorCode == ZError.ETERM) {
            if (logger.isDebugEnabled()) {
              logger.debug("Caught ETERM during blocking read. Breaking out.");
            }
            break;
          } else if (errorCode == ZError.EINTR) {
            if (logger.isDebugEnabled()) {
              logger.debug("Caught EINTR during blocking read. Breaking out.");
            }
            break;
          } else {
            throw ex;
          }
        } catch (Exception e) {
          logger.error("Error parsing received message", e);
        }
      }
    }
  }

  private final UUID peerUuid = UUID.randomUUID();
  private final String OUTCELL_ADDR = "inproc://cell";
  private ZContext context;
  private ExecutorService execService;
  private DispatcherConnector dispatcherConnector;
  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();
  private final OutgoingMessageDispatcherStub outDispatcherStub =
      new OutgoingMessageDispatcherStub();
  private InternalHeader WRITE_AHEAD_HEADER;
  private static final int TEST_PORT = 2182;
  private static final String CONNECTION_STR = String.format("localhost:%d", TEST_PORT);
  private TestingServer testingServer;
  private PALDirectory palDirectory;

  @Before
  public void setup() throws Exception {
    this.WRITE_AHEAD_HEADER = msgBuilder.buildWriteAheadHeader(peerUuid);
    this.context = createContext();
    this.execService = Executors.newCachedThreadPool();
    testingServer = new TestingServer(TEST_PORT, true);
    palDirectory = new PALDirectory(CONNECTION_STR);
    this.dispatcherConnector =
        new DispatcherConnector(context, peerUuid, msgBuilder, palDirectory, OUTCELL_ADDR);

    // simulate OutgoingMessageDispatcher
    execService.submit(outDispatcherStub);
  }

  @After
  public void cleanup() throws Exception {
    // close local context
    context.close();

    // stop executor
    execService.shutdownNow();
    execService.awaitTermination(2, TimeUnit.SECONDS);

    outDispatcherStub.clear();
    palDirectory.close();
    testingServer.close();
  }

  @Test
  public void sendExecMessage() throws Exception {
    // sends msg and get reply
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    ExecMessage returnedMsg = dispatcherConnector.sendExecMessage(msg, ExecPhase.BEFORE);

    // should return same message as sent (if reply == 0), null otherwise
    assertThat(returnedMsg, is(msg));
    assertThat(outDispatcherStub.messagesReceived.size(), is(1));
    assertThat(outDispatcherStub.messagesReceived, is(Collections.singletonList(msg)));
  }

  @Test
  public void sendInterceptRequestMessage() throws Exception {
    // sends msg and get reply
    InterceptRequest msg =
        msgBuilder.buildInterceptRequest(
            peerUuid,
            Intercepts.InterceptType.BEFORE,
            "java.io.PrintStream",
            "println",
            Collections.EMPTY_LIST,
            this.getClass().getName(),
            "someCallbackMethod");
    int resultCode = dispatcherConnector.sendOutInterceptRequestMessage(msg);

    assertThat(resultCode, is(0));
    assertThat(outDispatcherStub.messagesReceived.size(), is(1));
    assertThat(outDispatcherStub.messagesReceived, is(Collections.singletonList(msg)));
  }

  @Test
  public void sendExecMessageMany() throws Exception {
    int msgsToSend = 10;
    List<ExecMessage> sentMessages = new ArrayList<>();
    List<ExecMessage> returnedMessages = new ArrayList<>();

    // sends msgs and get replies
    for (int i = 0; i < msgsToSend; i++) {
      ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
      sentMessages.add(msg);
      ExecMessage returnedMsg = dispatcherConnector.sendExecMessage(msg, ExecPhase.BEFORE);
      returnedMessages.add(returnedMsg);
    }

    assertThat(returnedMessages, is(sentMessages));
    assertThat(outDispatcherStub.messagesReceived.size(), is(msgsToSend));
    assertThat(outDispatcherStub.messagesReceived, is(sentMessages));
  }

  @Test
  public void writeAhead() throws Exception {
    ExecMessage msg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    dispatcherConnector.writeAhead(msg);

    // verify messages received by stub
    assertThat(outDispatcherStub.messagesReceived.size(), is(1));
    assertThat(outDispatcherStub.messagesReceived, is(Collections.singletonList(msg)));

    // stub should have received a WRITE_AHEAD_HEADER
    assertThat(outDispatcherStub.headersReceived.size(), is(1));
    assertThat(outDispatcherStub.headersReceived.get(0), is(WRITE_AHEAD_HEADER));
  }
}
