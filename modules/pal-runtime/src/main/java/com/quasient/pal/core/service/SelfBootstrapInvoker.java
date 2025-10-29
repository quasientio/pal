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

  /**
   * Exit code when main method completes successfully (void return or explicit 0).
   *
   * <p>Matches standard Unix/Java convention for success.
   */
  static final int EXIT_SUCCESS = 0;

  /**
   * Exit code when main method throws an exception.
   *
   * <p>Indicates the invoked main method threw an uncaught exception.
   */
  static final int EXIT_MAIN_THREW_EXCEPTION = 1;

  /**
   * Exit code when the response message type is unexpected.
   *
   * <p>Indicates an internal error in message handling - the response from the main method
   * invocation was not of an expected type (EXEC_RETURN_VALUE, EXEC_THROWABLE, etc.).
   */
  static final int EXIT_UNEXPECTED_RESPONSE_TYPE = 125;

  /**
   * Exit code when the return value cannot be unwrapped or converted to an integer.
   *
   * <p>Indicates the main method returned a non-void, non-Integer value that cannot be interpreted
   * as an exit code, or there was an error during deserialization.
   */
  static final int EXIT_INVALID_RETURN_VALUE = 126;

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
  public int callMain(String className, List<String> argList) throws PeerException {
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
    if (runOptions.contains(RunOptions.WITH_WAL)) {
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
    if (runOptions.contains(RunOptions.WITH_WAL)) {
      boolean offsetPublished = false;
      long offset = -1;
      String msgId = null;
      try {
        while (!offsetPublished) {
          if (offsetSubscriber == null) {
            throw new PeerException(PeerException.FatalCode.UNEXPECTED_ERROR_LAUNCHING_MAIN);
          }
          PublishedOffsetMsg publishedOffsetMsg =
              PublishedOffsetMsg.receive(offsetSubscriber, true);
          offset = publishedOffsetMsg.getOffset();
          msgId = publishedOffsetMsg.getMessageId();
          if (response.getMessageId().equalsIgnoreCase(msgId)) {
            offsetPublished = true;
          }
        }
      } finally {
        if (offsetSubscriber != null) {
          offsetSubscriber.close();
        }
      }
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
   * returns an error exit code; and for unexpected types, it returns a special exit code indicating
   * an internal error.
   *
   * @param mainResponseMessage the response message containing the outcome of the execution.
   * @return the exit code as determined by the content and type of the response message.
   */
  private int getExitValueFromResponse(ExecMessage mainResponseMessage) {
    final MessageType messageType = getMessageTypeOf(mainResponseMessage);
    return switch (messageType) {
      case EXEC_RETURN_VALUE, EXEC_GET_STATIC, EXEC_GET_FIELD ->
          getIntFromReturnValue(mainResponseMessage);
      case EXEC_THROWABLE -> EXIT_MAIN_THREW_EXCEPTION;
      default -> {
        logger.error("Unexpected message type: {}", messageType);
        yield EXIT_UNEXPECTED_RESPONSE_TYPE;
      }
    };
  }

  /**
   * Extracts an integer exit value from the return value of the execution message.
   *
   * <p>If the message contains a non-null return object, this method attempts to unwrap it to an
   * Integer. In case of a failure during unwrapping or if the object is not an Integer, it returns
   * an error exit code. If the return value is null (void return from main method), it returns 0 to
   * indicate success, matching standard Java behavior.
   *
   * @param message the execution message from which to obtain the return value.
   * @return the integer value of the return object, 0 for void returns, or an error exit code if
   *     extraction fails.
   */
  private int getIntFromReturnValue(ExecMessage message) {
    if (message.getReturnValue().getObject() != null) {
      Object returnedObject;
      try {
        returnedObject = Unwrapper.unwrapObject(message.getReturnValue().getObject());
      } catch (ClassNotFoundException e) {
        logger.error("Error unwrapping return value object", e);
        return EXIT_INVALID_RETURN_VALUE;
      }
      if (returnedObject == null) {
        // Unwrapped to null (void return) - treat as success
        return EXIT_SUCCESS;
      } else if (returnedObject instanceof Integer intObj) {
        return intObj;
      } else {
        logger.error(
            "Main method returned non-Integer type: {} (cannot be used as exit code)",
            returnedObject.getClass().getName());
        return EXIT_INVALID_RETURN_VALUE;
      }
    }
    // Null return value (void) from main method should be treated as success
    return EXIT_SUCCESS;
  }
}
