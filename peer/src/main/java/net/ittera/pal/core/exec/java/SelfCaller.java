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

package net.ittera.pal.core.exec.java;

import com.google.common.primitives.Longs;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.IntStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.util.UUIDUtils;
import net.ittera.pal.core.PeerException;
import net.ittera.pal.core.RunOptions;
import net.ittera.pal.core.exec.UnsupportedMessageException;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.serdes.colfer.ColferMessageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

@Singleton
public class SelfCaller {

  private static final Logger logger = LoggerFactory.getLogger(SelfCaller.class);

  private final UUID peerUuid;
  private final IncomingMessageDispatcher incomingMessageDispatcher;
  private final ColferMessageBuilder messageBuilder;
  private final ClassLoader customClassloader;
  private final ZContext context;
  private final String offsetPubAddress;
  private final Set<RunOptions> runOptions;

  @Inject
  SelfCaller(
      UUID peerUuid,
      IncomingMessageDispatcher incomingMessageDispatcher,
      ColferMessageBuilder messageBuilder,
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

  public ExecMessage callMain(String className, List<String> argList) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Preparing message to call {}.main() with args: [{}]",
          className,
          argList == null ? "" : String.join(",", argList));
    }

    // prepare arrays for message construction
    final Class[] parameterTypes = new Class[] {String[].class};
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
                ExecMessage reply = incomingMessageDispatcher.incomingCall(request, true);
                replies.add(reply);
              } catch (UnsupportedMessageException e) {
                logger.error("Unsupported message", e);
              }
            });
    invokingThread.setName("self-caller");
    invokingThread.setContextClassLoader(customClassloader);

    // prepare offset subscriber
    Socket offsetSubscriber = null;
    if (!runOptions.contains(RunOptions.NO_OUTLOG)) {
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
    // get reply message
    final ExecMessage reply = replies.get(0);
    if (reply == null) {
      return null;
    }

    // wait for the reply message offset, to ensure all msg's from have been written to the log
    if (!runOptions.contains(RunOptions.NO_OUTLOG)) {
      boolean offsetPublished = false;
      long offset = -1;
      UUID uuid = null;
      while (!offsetPublished) {
        // multi-part msg: 1) offset as byte[], 2) uuid as byte[]
        offset = Longs.fromByteArray(offsetSubscriber.recv());
        uuid = UUIDUtils.fromBytes(offsetSubscriber.recv());
        if (reply.getMessageUuid().equalsIgnoreCase(uuid.toString())) {
          offsetPublished = true;
        }
      }
      // close socket
      offsetSubscriber.close();
      if (logger.isDebugEnabled()) {
        logger.debug("Returning reply message with offset={} and uuid={}", offset, uuid);
      }
    } else {
      if (logger.isDebugEnabled()) {
        logger.debug("Returning reply message with uuid={}", reply.getMessageUuid());
      }
    }
    return reply;
  }

  public ExecMessage callJar(String jarFile, List<String> argList) throws PeerException {
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
      throw new PeerException(PeerException.FatalCode.ERROR_NO_MAINCLASS_IN_JAR_MANIFEST);
    }
    return callMain(mainClass, argList);
  }
}
