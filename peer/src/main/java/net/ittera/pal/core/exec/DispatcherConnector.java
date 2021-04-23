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

import static net.ittera.pal.serdes.colfer.ColferUtils.format;
import static net.ittera.pal.serdes.colfer.ColferUtils.toBytes;

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
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.core.InterceptMatcher;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.cxn.DirectoryConnectionProvider;
import net.ittera.pal.cxn.PALDirectory;
import net.ittera.pal.messages.ExecMessageType;
import net.ittera.pal.messages.MessageType;
import net.ittera.pal.messages.OutboundMsg;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

@Singleton
public class DispatcherConnector {

  private static final Logger logger = LoggerFactory.getLogger(DispatcherConnector.class);
  private static final int CALLBACK_RECV_TIMEOUT_MS = 3000;

  private final ZContext zmqContext;
  private final UUID peerUuid;
  private final MessageBuilder messageBuilder;
  private final DirectoryConnectionProvider directoryConnectionProvider;
  private final InterceptMatcher interceptMatcher;
  private final String msgPublisherAddress;
  private final List<InternalHeader> WRITE_AHEAD_HEADERS;
  private final Set<RunOptions> runOptions;

  private final AtomicLong totalPubSocketTime = new AtomicLong();
  private final AtomicLong totalPubReqs = new AtomicLong();
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

  private final ThreadLocal<Map<UUID, Socket>> threadSockets =
      ThreadLocal.withInitial(HashMap::new);

  @Inject
  public DispatcherConnector(
      ZContext zmqContext,
      UUID peerUuid,
      MessageBuilder messageBuilder,
      DirectoryConnectionProvider directoryConnectionProvider,
      Set<RunOptions> runOptions,
      InterceptMatcher interceptMatcher,
      @Named("out.cell") String msgPublisherAddress) {
    this.zmqContext = zmqContext;
    this.peerUuid = peerUuid;
    this.messageBuilder = messageBuilder;
    this.directoryConnectionProvider = directoryConnectionProvider;
    this.runOptions = runOptions;
    this.interceptMatcher = interceptMatcher;
    this.msgPublisherAddress = msgPublisherAddress;
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

    final String followingUuidStr = execMessage.getFollowingUuid();
    UUID followingUuid =
        followingUuidStr == null || followingUuidStr.isEmpty()
            ? null
            : UUID.fromString(execMessage.getFollowingUuid());

    ExecMessageType execMessageType = ExecMessageType.values()[execMessage.getExecMessageType()];
    List<InterceptMessage> matchingIntercepts = null;

    if (!runOptions.contains(RunOptions.NO_INTERCEPTS) && isInterceptableType(execMessageType)) {
      // find matching intercepts for execMessage
      matchingIntercepts = interceptMatcher.getMatchingIntercepts(execMessage, execPhase);
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
              messageBuilder.wrap(execMessage));
      publishMessage(msg);
    }

    // if no intercepts, return received ExecMessage
    if (matchingIntercepts == null || matchingIntercepts.isEmpty()) {
      if (logger.isDebugEnabled()) {
        logger.debug("No intercepts for execMessage: {}", format(execMessage));
      }
      return execMessage;
    }

    // deal with intercepts
    final ExecMessage returnValue;
    // for now we only care about the first intercept request
    InterceptMessage interceptMessage = matchingIntercepts.get(0);
    ExecMessage callbackMessage =
        messageBuilder.buildCallbackForInterceptRequest(peerUuid, execMessage, interceptMessage);
    UUID interceptor = UUID.fromString(interceptMessage.getPeerUuid());
    final byte[] reply;
    InterceptType interceptType = InterceptType.values()[interceptMessage.getInterceptType()];
    try {
      if (interceptType.equals(InterceptType.BEFORE_ASYNC)
          || interceptType.equals(InterceptType.AFTER_ASYNC)) {
        sendAsyncCallbackToPeer(interceptor, callbackMessage);
      } else if (interceptType.equals(InterceptType.BEFORE)
          || interceptType.equals(InterceptType.AFTER)) {
        reply = sendCallbackToPeer(interceptor, callbackMessage);
      } else {
        logger.error("Unsupported callback type: {}", interceptType);
      }
    } catch (Exception ex) {
      logger.error(
          "Error sending callback to peer w/uuid: {}, callback execMessage: {}",
          interceptor,
          ColferUtils.format(callbackMessage),
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

    if (logger.isTraceEnabled()) {
      logger.trace("out w/ {}", ColferUtils.format(returnValue));
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

    final String followingUuidStr = message.getFollowingUuid();
    UUID followingUuid =
        followingUuidStr == null || followingUuidStr.isEmpty()
            ? null
            : UUID.fromString(message.getFollowingUuid());
    final OutboundMsg msg =
        new OutboundMsg(
            MessageType.ExecMessage,
            ExecPhase.BEFORE,
            WRITE_AHEAD_HEADERS,
            UUID.fromString(message.getMessageUuid()),
            followingUuid,
            messageBuilder.wrap(message));

    // no intercept matching, just publish it
    publishMessage(msg);
  }

  private @Nullable byte[] sendCallbackMessageToPeer(
      UUID interceptor, ExecMessage message, boolean getReply) throws Exception {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sending callback message: {} to peer w/uuid: {}",
          ColferUtils.format(message),
          interceptor);
    }
    byte[] reply;
    // get socket for peer and send callback msg
    Socket req = getConnectedREQSocketFor(interceptor);
    final boolean sentOk = req.send(toBytes(messageBuilder.wrap(message)), 0);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Sent callback message: {} (ret={}) to peer w/uuid: {}",
          ColferUtils.format(message),
          sentOk,
          interceptor);
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
            final Message replyMessage = new Message();
            message.unmarshal(reply, 0);
            logger.debug("Got reply from callback: {}", ColferUtils.format(replyMessage));
          }
        } else { // we hit the timeout, check if peer is alive
          final PALDirectory palDirectory =
              directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
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
    final PALDirectory palDirectory =
        directoryConnectionProvider.get().orElseThrow(RuntimeException::new);
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
    logger.info("totalPubReqs = {}", totalPubReqs);
  }

  void closeThreadLocalSockets() {
    printStats();
    if (totalPubReqs.longValue() != 0) {
      logger.info(
          "avg ms per pub = {} ms", totalPubSocketTime.longValue() / totalPubReqs.longValue());
    }

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
