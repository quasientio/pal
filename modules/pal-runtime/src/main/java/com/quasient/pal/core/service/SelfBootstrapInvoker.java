/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.service;

import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;

import com.google.inject.name.Named;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.core.dispatcher.IncomingMessageDispatcher;
import com.quasient.pal.core.dispatcher.UnsupportedMessageException;
import com.quasient.pal.core.execution.java.CustomClassloader;
import com.quasient.pal.core.internal.messages.PublishedOffsetMsg;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.serdes.Unwrapper;
import com.quasient.pal.serdes.colfer.MessageBuilder;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

/**
 * This class builds and dispatches execution messages to call the main method either from a
 * directly specified class or from a JAR's manifest-defined main class.
 */
@Singleton
public class SelfBootstrapInvoker {

  /** Logger instance. */
  private static final Logger logger = LoggerFactory.getLogger(SelfBootstrapInvoker.class);

  /** Default exit value used when an unexpected response is received. */
  static final int DEFAULT_EXIT_VALUE = -9999;

  /** Default exit value used when the response indicates an error condition. */
  static final int DEFAULT_ERROR_EXIT_VALUE = -8888;

  /** The unique identifier representing the current peer. */
  private final UUID peerUuid;

  /** Dispatcher for handling incoming execution response messages. */
  private final IncomingMessageDispatcher incomingMessageDispatcher;

  /** Builder for constructing execution messages. */
  private final MessageBuilder messageBuilder;

  /** Custom ClassLoader used for loading classes during the invocation of main methods. */
  private final ClassLoader customClassloader;

  /** ZeroMQ context used for managing socket communications. */
  private final ZContext context;

  /** Address of the offset publisher socket used for synchronizing log offsets. */
  private final String offsetPubAddress;

  /** Set of runtime options that control behavior such as logging. */
  private final Set<RunOptions> runOptions;

  /**
   * Constructs a SelfBootstrapInvoker instance with the specified dependencies.
   *
   * @param peerUuid the unique identifier for the peer invoking the call.
   * @param incomingMessageDispatcher the dispatcher to process incoming execution responses.
   * @param messageBuilder the builder to construct execution messages.
   * @param customClassloader the custom ClassLoader for context-specific class loading.
   * @param context the ZeroMQ context for socket operations.
   * @param offsetPubAddress the address of the offset publisher for log synchronization.
   * @param runOptions the set of runtime options influencing call behavior.
   */
  @Inject
  SelfBootstrapInvoker(
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

  /**
   * Invokes the main method of the specified Java class via a bootstrapping message.
   *
   * <p>This method builds an execution message to call the main method asynchronously on a new
   * thread. If the runtime option for log offset synchronization is enabled, it will subscribe to
   * offset messages to ensure log consistency. The exit value returned from the remote call is
   * extracted from the response.
   *
   * @param className the fully qualified name of the class whose main method should be executed.
   * @param argList a list of arguments to pass to the main method; may be null to denote no
   *     arguments.
   * @return the exit code resulting from the main method execution.
   */
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
                        request, MessageType.EXEC_CLASS_METHOD, MessageChannelType.CLI_RPC));
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

  /**
   * Invokes the main method of the main class specified in the manifest of the given JAR file.
   *
   * <p>The method opens the provided JAR file, retrieves its manifest to determine the main class,
   * and delegates the call to {@link #callMain(String, List)} with the extracted class name and
   * arguments.
   *
   * @param jarFile the file path of the JAR containing the main class.
   * @param argList a list of arguments for the main method; may be null if no arguments are
   *     required.
   * @return the exit code from the main method execution.
   * @throws PeerException if the JAR file is missing, its manifest cannot be read, or lacks a
   *     Main-Class entry.
   */
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

  /**
   * Determines the exit value based on the response message from a remote main method invocation.
   *
   * <p>This method inspects the message type of the response. For types that represent a return
   * value or static/field access, it extracts the integer exit value; for throwable messages, it
   * returns a predefined error exit value; and for unexpected types, it returns a default exit
   * code.
   *
   * @param mainResponseMessage the response message containing the outcome of the execution.
   * @return the exit code as determined by the content and type of the response message.
   */
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

  /**
   * Extracts an integer exit value from the return value of the execution message.
   *
   * <p>If the message contains a non-null return object, this method attempts to unwrap it to an
   * Integer. In case of a failure during unwrapping or if the object is not an Integer, it returns
   * a default exit code.
   *
   * @param message the execution message from which to obtain the return value.
   * @return the integer value of the return object, or a default exit value if extraction fails.
   */
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
