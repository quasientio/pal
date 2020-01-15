package net.ittera.pal.core.exec;

import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import net.ittera.pal.common.ExecPhase;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.messages.InterceptsMsg;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.MessageType;
import net.ittera.pal.messages.OutboundMsg;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;
import net.ittera.pal.messages.protobuf.Headers.InternalHeader;
import net.ittera.pal.messages.protobuf.Intercepts;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptKeyMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptType;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import zmq.ZError;

@Singleton
public class DispatcherConnector {

  private static final Logger logger = LoggerFactory.getLogger(DispatcherConnector.class);
  private static final int CALLBACK_RECV_TIMEOUT_MS = 3000;

  private final ZContext zmqContext;
  private final UUID peerUuid;
  private final MessageBuilder messageBuilder;
  private final PALDirectory palDirectory;
  private final String msgPublisherAddress;
  private final String interceptMatchAddress;
  private final List<InternalHeader> WRITE_AHEAD_HEADERS;
  private final Set<RunOptions> runOptions;

  private final AtomicLong totalPubSocketTime = new AtomicLong();
  private final AtomicLong totalIntrcptSocketTime = new AtomicLong();
  private final AtomicLong totalPubReqs = new AtomicLong();
  private final AtomicLong totalMatchReqs = new AtomicLong();
  /*
  2 sockets per thread: 1 to send REQs to Intercepts; 1 to send REQs to MessagePublisher
  */

  // per-thread REQ socket to publish exec messages
  private final ThreadLocal<Socket> threadPubSocket =
      new ThreadLocal<Socket>() {
        @Override
        protected Socket initialValue() {
          Socket worker = zmqContext.createSocket(SocketType.REQ);
          worker.connect(msgPublisherAddress);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Created and connected REQ new socket to outCellAddress: {}", msgPublisherAddress);
          }
          threadPubSocketCreated.set(true);
          return worker;
        }
      };
  // flag to avoid creating the threadLocal socket when we're trying to close it before having been
  // created
  private final ThreadLocal<Boolean> threadPubSocketCreated = ThreadLocal.withInitial(() -> false);

  // per-thread REQ socket to get matching intercepts for exec messages
  private final ThreadLocal<Socket> threadInterceptsSocket =
      new ThreadLocal<Socket>() {
        @Override
        protected Socket initialValue() {
          Socket worker = zmqContext.createSocket(SocketType.REQ);
          worker.connect(interceptMatchAddress);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Created and connected REQ new socket to interceptMatchAddress: {}",
                interceptMatchAddress);
          }
          threadInterceptsSocketCreated.set(true);
          return worker;
        }
      };
  // flag to avoid creating the threadLocal socket when we're trying to close it before having been
  // created
  private final ThreadLocal<Boolean> threadInterceptsSocketCreated =
      ThreadLocal.withInitial(() -> false);

  private final ThreadLocal<Map<UUID, Socket>> threadSockets =
      ThreadLocal.withInitial(HashMap::new);

  @Inject
  public DispatcherConnector(
      ZContext zmqContext,
      UUID peerUuid,
      MessageBuilder messageBuilder,
      PALDirectory palDirectory,
      Set<RunOptions> runOptions,
      @Named("out.cell") String msgPublisherAddress,
      @Named("intercepts.mtx") String interceptMatchAddress) {
    this.zmqContext = zmqContext;
    this.peerUuid = peerUuid;
    this.messageBuilder = messageBuilder;
    this.palDirectory = palDirectory;
    this.runOptions = runOptions;
    this.msgPublisherAddress = msgPublisherAddress;
    this.interceptMatchAddress = interceptMatchAddress;
    this.WRITE_AHEAD_HEADERS =
        Collections.singletonList(messageBuilder.buildWriteAheadHeader(peerUuid));
  }

  public ExecMessage sendExecMessage(ExecMessage message, ExecPhase execPhase) {
    return sendExecMessage(message, execPhase, null);
  }

  private ExecMessage sendExecMessage(
      ExecMessage execMessage, ExecPhase execPhase, @Nullable List<InternalHeader> headers) {
    if (logger.isTraceEnabled()) {
      logger.trace("sendExecMessage:in w/ execMessage with uuid: {}", execMessage.getMessageUuid());
    }

    UUID followingUuid =
        execMessage.hasFollowingUuid() ? UUID.fromString(execMessage.getFollowingUuid()) : null;
    InterceptsMsg interceptsMsg = null;

    if (!runOptions.contains(RunOptions.NO_INTERCEPTS)
        && isInterceptableType(execMessage.getMsgType())) {
      Socket interceptsReqSocket = threadInterceptsSocket.get();
      // find matching intercepts for execMessage
      InterceptKeyMessage interceptKeyMessage = messageBuilder.buildInterceptKey(execMessage);
      final OutboundMsg msg =
          new OutboundMsg(
              MessageType.InterceptKey,
              execPhase,
              headers,
              UUID.fromString(execMessage.getMessageUuid()),
              followingUuid,
              messageBuilder.wrap(interceptKeyMessage).toByteArray());

      long start = Instant.now().toEpochMilli();
      msg.send(interceptsReqSocket);

      // receive intercepts, if any
      try {
        interceptsMsg = InterceptsMsg.recvMsg(interceptsReqSocket, true);
      } catch (ZMQException ex) {
        int errorCode = ex.getErrorCode();
        if (errorCode == ZError.ETERM) {
          logger.warn("Caught ETERM during blocking read. Will close socket");
          interceptsReqSocket.close();
        } else if (errorCode == ZError.EINTR) {
          logger.warn("Caught EINTR during blocking read. Will close socket.");
          interceptsReqSocket.close();
        }
      } catch (InvalidProtocolBufferException e) {
        logger.error("Error parsing received execMessage", e);
      } finally {
        totalIntrcptSocketTime.getAndAdd((Instant.now().toEpochMilli() - start));
        totalMatchReqs.getAndIncrement();
      }
    }

    // publish execMessage -- TODO should we in case of intercepts publish it after
    if (!runOptions.contains(RunOptions.NO_PUBLISHING)) {
      final OutboundMsg msg =
          new OutboundMsg(
              MessageType.ExecMessage,
              execPhase,
              headers,
              UUID.fromString(execMessage.getMessageUuid()),
              followingUuid,
              messageBuilder.wrap(execMessage).toByteArray());
      publishMessage(msg);
    }

    // in case of error, simply return received ExecMessage
    if (interceptsMsg == null) {
      return execMessage;
    }

    // deal with intercepts
    final ExecMessage returnValue;
    if (interceptsMsg.getIntercepts() == null) {
      if (logger.isDebugEnabled()) {
        logger.debug("No intercepts for execMessage");
      }
      returnValue = execMessage;
    } else {
      // for now we only care about the first intercept request
      Intercepts.InterceptMessage interceptMessage = interceptsMsg.getIntercepts().get(0);
      ExecMessage callbackMessage =
          messageBuilder.buildCallbackForInterceptRequest(peerUuid, execMessage, interceptMessage);
      UUID interceptor = UUID.fromString(interceptMessage.getPeerUuid());
      final byte[] reply;
      try {
        if (interceptMessage.getType().equals(InterceptType.BEFORE_ASYNC)
            || interceptMessage.getType().equals(InterceptType.AFTER_ASYNC)) {
          sendAsyncCallbackToPeer(interceptor, callbackMessage);
        } else if (interceptMessage.getType().equals(InterceptType.BEFORE)
            || interceptMessage.getType().equals(InterceptType.AFTER)) {
          reply = sendCallbackToPeer(interceptor, callbackMessage);
        } else {
          logger.error("Unsupported callback type: {}", interceptMessage.getType());
        }
      } catch (Exception ex) {
        logger.error(
            "Error sending callback to peer w/uuid: {}, callback execMessage: {}",
            interceptor,
            callbackMessage,
            ex);
      }

      //      if (interceptMessage.getType().equals(Intercepts.InterceptType.AROUND)) {
      // TODO in case of AROUND we should return the execMessage returned by callback only in
      // ExecPhase.After
      // reply = sendCallbackToPeer(interceptor, callbackMessage);
      // only parse execMessage when needed
      // final ExecMessage replyMsg = ExecMessage.parseFrom(reply);
      // returnValue = replyMsg;
      //      } else {
      returnValue = execMessage;
      //    }

    }

    if (logger.isTraceEnabled()) {
      logger.trace("out w/ {}", returnValue);
    }
    return returnValue;
  }

  private void publishMessage(OutboundMsg message) {
    Socket publisherReqSocket = threadPubSocket.get();
    long start = Instant.now().toEpochMilli();
    message.send(publisherReqSocket);
    try {
      String reply = publisherReqSocket.recvStr();
      if (!"0".equalsIgnoreCase(reply)) {
        logger.warn("Non-zero reply from message publisher for message: {}", message);
      }
    } catch (ZMQException e) {
      logger.error("Error receiving reply from publisher socket", e);
    } finally {
      totalPubSocketTime.getAndAdd((Instant.now().toEpochMilli() - start));
      totalPubReqs.getAndIncrement();
    }
  }

  public void writeAhead(ExecMessage message) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "writeAhead:in w/ message with uuid: {},from {}",
          message.getMessageUuid(),
          message.getPeerUuid());
    }

    if (runOptions.contains(RunOptions.NO_PUBLISHING)) {
      return;
    }

    final UUID followingUuid =
        message.hasFollowingUuid() ? UUID.fromString(message.getFollowingUuid()) : null;
    final OutboundMsg msg =
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            WRITE_AHEAD_HEADERS,
            UUID.fromString(message.getMessageUuid()),
            followingUuid,
            messageBuilder.wrap(message).toByteArray());

    // no intercept matching, just publish it
    publishMessage(msg);
  }

  private @Nullable byte[] sendCallbackMessageToPeer(
      UUID interceptor, ExecMessage message, boolean getReply) throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug("Sending callback message: {} to peer w/uuid: {}", message, interceptor);
    }
    byte[] reply;
    // get socket for peer and send callback msg
    Socket req = getConnectedREQSocketFor(interceptor);
    final boolean sentOk = req.send(messageBuilder.wrap(message).toByteArray(), 0);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sent callback message: {} (ret={}) to peer w/uuid: {}", message, sentOk, interceptor);
    }

    // block until we get a reply or peer is disconnected
    reply = null;
    if (getReply) {
      boolean peerIsUp = true;
      boolean gotReply = false;
      while (!gotReply && peerIsUp) {
        reply = req.recv(0);
        if (reply != null) {
          gotReply = true;
          if (logger.isDebugEnabled()) {
            try {
              Message replyMessage = Message.parseFrom(reply);
              logger.debug("Got reply from callback: {}", replyMessage);
            } catch (InvalidProtocolBufferException e) {
              logger.warn("Error parsing reply message", e);
            }
          }
        } else { // we hit the timeout, check if peer is alive
          peerIsUp = palDirectory.peerExists(interceptor);
          if (peerIsUp) {
            logger.warn(
                "Peer w/uuid: {} is taking long to reply, but is alive, so we wait", interceptor);
          } else {
            logger.warn("Peer w/uuid: {} is disconnected. Giving up, returning null", interceptor);
          }
        }
      }
    }
    // TODO getReply  == false --> we still have to recv() !!
    return reply;
  }

  private void sendAsyncCallbackToPeer(UUID interceptor, ExecMessage message) throws Exception {
    sendCallbackMessageToPeer(interceptor, message, false);
  }

  private byte[] sendCallbackToPeer(UUID interceptor, ExecMessage message) throws Exception {
    return sendCallbackMessageToPeer(interceptor, message, true);
  }

  private Socket getConnectedREQSocketFor(UUID peer) throws Exception {
    // first check if socket for peer is already open
    if (threadSockets.get().containsKey(peer)) {
      if (logger.isDebugEnabled()) {
        logger.debug("Returning existing REQ socket for peer w/uuid: {}", peer);
      }
      return threadSockets.get().get(peer);
    }

    // else, create and connect new socket
    if (logger.isDebugEnabled()) {
      logger.debug("Connecting new REQ socket to peer w/uuid: {}", peer);
    }
    final Socket reqSocket = zmqContext.createSocket(SocketType.REQ);
    // set receive timeout
    reqSocket.setReceiveTimeOut(CALLBACK_RECV_TIMEOUT_MS);
    // get peer's address
    String interceptorAddress = palDirectory.getPeerInfo(peer).getReqAddress();
    reqSocket.connect(interceptorAddress);
    // store in thread-local peer->socket map
    threadSockets.get().put(peer, reqSocket);

    return reqSocket;
  }

  private boolean isInterceptableType(ExecMessageType type) {
    switch (type) {
      case CONSTRUCTOR:
      case INSTANCE_METHOD:
      case CLASS_METHOD:
      case GET_STATIC:
      case GET_FIELD:
      case PUT_STATIC:
      case PUT_FIELD:
        return true;
      default:
        return false;
    }
  }

  // TODO call from single-thread
  void printStats() {
    logger.info("totalPubSocketTime = {} ms", totalPubSocketTime);
    logger.info("totalIntrcptSocketTime = {} ms", totalIntrcptSocketTime);
    logger.info("totalPubReqs = {}", totalPubReqs);
    logger.info("totalMatchReqs = {}", totalMatchReqs);
  }

  void closeThreadLocalSockets() {
    printStats();
    if (totalPubReqs.longValue() != 0) {
      logger.info(
          "avg ms per pub = {} ms", totalPubSocketTime.longValue() / totalPubReqs.longValue());
    }
    if (totalMatchReqs.longValue() != 0) {
      logger.info(
          "avg ms per match = {} ms",
          totalIntrcptSocketTime.longValue() / totalMatchReqs.longValue());
    }

    if (Boolean.TRUE.equals(threadInterceptsSocketCreated.get())) {
      Socket socket = threadInterceptsSocket.get();
      if (socket != null) {
        socket.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Thread local REQ socket for intercept matching closed");
        }
      }
      threadInterceptsSocket.remove();
    }
    threadInterceptsSocketCreated.remove();

    if (Boolean.TRUE.equals(threadPubSocketCreated.get())) {
      Socket socket = threadPubSocket.get();
      if (socket != null) {
        socket.close();
        if (logger.isDebugEnabled()) {
          logger.debug("Thread local REQ socket for publishing closed");
        }
      }
      threadPubSocket.remove();
    }
    threadPubSocketCreated.remove();

    threadSockets
        .get()
        .forEach(
            (uuid, socket) -> {
              if (socket != null) {
                socket.close();
              }
              if (logger.isDebugEnabled()) {
                logger.debug("Closed thread-local REQ socket to remote peer w/uuid: {}", uuid);
              }
            });
    threadSockets.remove();
  }
}
