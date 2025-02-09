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

package net.ittera.pal.core.rpc.exec.java;

import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;

import com.google.inject.name.Named;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.IntStream;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.core.PeerException;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.messages.PublishedOffsetMsg;
import net.ittera.pal.core.rpc.IncomingMessageDispatcher;
import net.ittera.pal.core.rpc.UnsupportedMessageException;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.Unwrapper;
import net.ittera.pal.serdes.colfer.MessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

@Singleton
public class SelfCaller {

  private static final Logger logger = LoggerFactory.getLogger(SelfCaller.class);

  static final int DEFAULT_EXIT_VALUE = -9999;
  static final int DEFAULT_ERROR_EXIT_VALUE = -8888;
  private final UUID peerUuid;
  private final IncomingMessageDispatcher incomingMessageDispatcher;
  private final MessageBuilder messageBuilder;
  private final ClassLoader customClassloader;
  private final ZContext context;
  private final String offsetPubAddress;
  private final Set<RunOptions> runOptions;

  @Inject
  SelfCaller(
      UUID peerUuid,
      IncomingMessageDispatcher incomingMessageDispatcher,
      MessageBuilder messageBuilder,
      CustomClassloader customClassloader,
      ZContext context,
      @Named("offset.pub") String offsetPubAddress,
      Set<RunOptions> runOptions) {
    this.peerUuid = peerUuid;
    this.incomingMessageDispatcher = incomingMessageDispatcher;
    this.messageBuilder = messageBuilder;
    this.customClassloader = customClassloader;
    this.context = context;
    this.offsetPubAddress = offsetPubAddress;
    this.runOptions = runOptions;
  }

  public int callMain(String className, List<String> argList) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Preparing message to call {}.main() with args: [{}]",
          className,
          argList == null ? "" : String.join(",", argList));
    }

    // prepare arrays for message construction
    final Class<?>[] parameterTypes = new Class[] {String[].class};
    final String[] parameterTypesNamesArray = new String[parameterTypes.length];
    IntStream.range(0, parameterTypes.length)
        .forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
    final Object[] parameters = new Object[] {new String[] {}};
    if (argList != null) {
      parameters[0] = argList.toArray(new String[0]);
    }

    final List<ExecMessage> replies = new ArrayList<>();

    // dispatch it with a new named thread, also provided with our custom classloader
    Thread invokingThread =
        new Thread(
            () -> {
              // build request message
              ExecMessage request =
                  messageBuilder.buildClassMethod(
                      peerUuid,
                      className,
                      "main",
                      parameterTypesNamesArray,
                      this,
                      null,
                      parameters,
                      new ObjectRef[parameterTypes.length]);
              try {
                replies.add(
                    incomingMessageDispatcher.incomingCall(
                        request, MessageType.EXEC_CLASS_METHOD, true));
              } catch (UnsupportedMessageException e) {
                logger.error("Unsupported message", e);
              }
            });
    invokingThread.setName("self-caller");
    invokingThread.setContextClassLoader(customClassloader);

    // prepare offset subscriber
    Socket offsetSubscriber = null;
    if (runOptions.contains(RunOptions.WITH_OUT_LOG)) {
      offsetSubscriber = context.createSocket(SocketType.SUB);
      offsetSubscriber.connect(offsetPubAddress);
      offsetSubscriber.subscribe(ZMQ.SUBSCRIPTION_ALL);
    }

    // start thread and wait for completion
    invokingThread.start();
    try {
      invokingThread.join();
    } catch (InterruptedException e) {
      logger.error("Thread interrupted", e);
    }
    // get response message
    final ExecMessage response = replies.get(0);
    assert response != null;

    // wait for the response message offset, to ensure all msg's from have been written to the log
    if (runOptions.contains(RunOptions.WITH_OUT_LOG)) {
      boolean offsetPublished = false;
      long offset = -1;
      String msgId = null;
      while (!offsetPublished) {
        assert offsetSubscriber != null;
        PublishedOffsetMsg publishedOffsetMsg = PublishedOffsetMsg.receive(offsetSubscriber, true);
        offset = publishedOffsetMsg.getOffset();
        msgId = publishedOffsetMsg.getMessageId();
        if (response.getMessageId().equalsIgnoreCase(msgId)) {
          offsetPublished = true;
        }
      }
      // close socket
      offsetSubscriber.close();
      if (logger.isDebugEnabled()) {
        logger.debug("Returned response message with offset={} and id={}", offset, msgId);
      }
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Returned response message with id={}", response.getMessageId());
      }
    }

    return getExitValueFromResponse(response);
  }

  public int callJar(String jarFile, List<String> argList) throws PeerException {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Call jar `{}` with args: [{}]",
          jarFile,
          argList == null ? "" : String.join(",", argList));
    }

    final Attributes attributes;

    try (JarFile jar = new JarFile(jarFile)) {
      attributes = jar.getManifest().getMainAttributes();
    } catch (IOException e) {
      logger.error("Error loading Manifest from JAR", e);
      throw new PeerException(PeerException.FatalCode.ERROR_JAR_NOT_FOUND_OR_MISSING_MANIFEST);
    }
    final String mainClass = attributes.getValue("Main-Class");
    if (mainClass == null) {
      throw new PeerException(PeerException.FatalCode.ERROR_NO_MAIN_CLASS_IN_JAR_MANIFEST);
    }
    return callMain(mainClass, argList);
  }

  private int getExitValueFromResponse(ExecMessage mainResponseMessage) {
    final MessageType messageType = getMessageTypeOf(mainResponseMessage);
    return switch (messageType) {
      case EXEC_RETURN_VALUE, EXEC_GET_STATIC, EXEC_GET_FIELD ->
          getIntFromReturnValue(mainResponseMessage);
      case EXEC_THROWABLE -> {
        yield DEFAULT_ERROR_EXIT_VALUE;
      }
      default -> {
        logger.error("Unexpected message type: {}", messageType);
        yield DEFAULT_EXIT_VALUE;
      }
    };
  }

  private int getIntFromReturnValue(ExecMessage message) {
    if (message.getReturnValue().getObject() != null) {
      Object returnedObject;
      try {
        returnedObject = Unwrapper.unwrapObject(message.getReturnValue().getObject());
      } catch (ClassNotFoundException e) {
        logger.error("Error unwrapping object", e);
        return DEFAULT_EXIT_VALUE;
      }
      if (returnedObject instanceof Integer) {
        return (Integer) returnedObject;
      } else {
        logger.error("Unsupported return value type: {}", returnedObject.getClass().getName());
      }
    }
    return DEFAULT_EXIT_VALUE;
  }
}
