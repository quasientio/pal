/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.serdes.colfer.ColferUtils;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.serdes.jsonrpc.JsonRpcSerializer;
import io.quasient.pal.serdes.jsonrpc.JsonSerializationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.slf4j.Logger;

/**
 * Shared base class for {@link PeerCall} and {@link LogCall}, containing common response printing,
 * stdin reading, multi-client dispatch, and static method call builder logic.
 *
 * <p>This parallels how {@link AbstractStatsCommand} is the shared base for {@code PeerStats} and
 * {@code LogStats}.
 *
 * @see PeerCall
 * @see LogCall
 */
abstract class AbstractCallCommand extends AbstractPalSubcommand {

  /** Builder for constructing messages to be sent. */
  protected final MessageBuilder messageBuilder = new MessageBuilder();

  // ===========================================================================
  // Abstract accessors for subclass-specific picocli fields
  // ===========================================================================

  /**
   * Returns whether response printing is enabled.
   *
   * @return {@code true} if responses should be printed
   */
  protected abstract boolean isPrintResponses();

  // ===========================================================================
  // Response printing
  // ===========================================================================

  /**
   * Prints the JSON-RPC response if response printing is enabled.
   *
   * @param response the {@link JsonRpcResponse} to print
   */
  protected void printJsonRpcResponse(JsonRpcResponse response) {
    if (!isPrintResponses()) {
      return;
    }
    try {
      out.println(JsonRpcSerializer.toJson(response));
    } catch (JsonSerializationException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Prints the execution message response if response printing is enabled.
   *
   * @param response the {@link ExecMessage} to print
   */
  protected void printExecMessage(ExecMessage response) {
    if (!isPrintResponses()) {
      return;
    }
    if (response.getReturnValue() != null) {
      printReturnValue(response.getReturnValue());
    } else if (response.getRaisedThrowable() != null) {
      printRaisedThrowable(response.getRaisedThrowable());
    }
  }

  /**
   * Prints the return value of an executed method if the return value is not void.
   *
   * @param returnValue the {@link ReturnValue} to print
   */
  protected void printReturnValue(ReturnValue returnValue) {
    if (!isPrintResponses()) {
      return;
    }
    if (returnValue.getIsVoid()) {
      return;
    }
    Obj object = returnValue.getObject();
    if (object != null) {
      out.println(object.getValue());
    }
  }

  /**
   * Prints a raised throwable.
   *
   * @param raisedThrowable the {@link RaisedThrowable} to print
   */
  protected void printRaisedThrowable(RaisedThrowable raisedThrowable) {
    if (!isPrintResponses()) {
      return;
    }
    out.println(ColferUtils.format(raisedThrowable));
  }

  // ===========================================================================
  // Stdin reading
  // ===========================================================================

  /**
   * Reads request lines from standard input, if available.
   *
   * @return the list of lines read, or an empty list if stdin is not ready
   */
  protected List<String> readStdinRequests() {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset()))) {
      if (reader.ready()) {
        List<String> requests = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
          requests.add(line);
        }
        return requests;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new ArrayList<>();
  }

  /**
   * Validates that exactly one of className or stdinRequests is provided.
   *
   * @param className the class name from positional arguments
   * @param stdinRequests the requests read from stdin
   * @param stdinLabel a description of what stdin contains (e.g., "requests" or "JSON-RPC
   *     requests")
   */
  protected void validateClassNameOrStdin(
      String className, List<String> stdinRequests, String stdinLabel) {
    if (!optionGiven(className) && stdinRequests.isEmpty()) {
      throw new RuntimeException(
          "You must specify a class to call or provide " + stdinLabel + " through STDIN.");
    }
    if (optionGiven(className) && !stdinRequests.isEmpty()) {
      throw new RuntimeException(
          "Either specify a class or provide " + stdinLabel + " through STDIN, but not both.");
    }
  }

  // ===========================================================================
  // Multi-client dispatch
  // ===========================================================================

  /**
   * Functional interface for a single-client request sender.
   *
   * @see #runManyClients(int, boolean, Logger, RequestSender)
   */
  @FunctionalInterface
  protected interface RequestSender {

    /**
     * Sends requests and returns the count of requests sent.
     *
     * @return the number of requests sent
     * @throws Exception if an error occurs
     */
    int send() throws Exception;
  }

  /**
   * Runs the given request sender in parallel across multiple threads.
   *
   * @param numberOfThreads the number of parallel threads to use (must be &gt; 1)
   * @param verbose whether to print timing information
   * @param logger the logger for error reporting
   * @param sender the request sender to invoke on each thread
   * @throws Exception if an error occurs
   */
  @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
  protected void runManyClients(
      int numberOfThreads, boolean verbose, Logger logger, RequestSender sender) throws Exception {
    if (numberOfThreads <= 1) {
      throw new IllegalArgumentException(
          "Method must be called with clients > 1. clients = " + numberOfThreads);
    }

    Thread[] clientList = new Thread[numberOfThreads];
    final AtomicInteger requestsSent = new AtomicInteger(0);
    final CountDownLatch latch = new CountDownLatch(numberOfThreads);

    long start = System.currentTimeMillis();

    IntStream.range(0, numberOfThreads)
        .forEach(
            i -> {
              Thread client =
                  new Thread(
                      () -> {
                        try {
                          int sent = sender.send();
                          requestsSent.getAndAdd(sent);
                        } catch (Exception e) {
                          logger.error("Caught error running requests", e);
                        } finally {
                          latch.countDown();
                        }
                      });
              clientList[i] = client;
            });

    Arrays.stream(clientList).forEach(Thread::start);
    latch.await();

    if (verbose) {
      err.printf(
          "sent %s requests with %s client(s) in %s ms%n",
          requestsSent.get(), numberOfThreads, (System.currentTimeMillis() - start));
    }
  }

  // ===========================================================================
  // Shared static method call builder base
  // ===========================================================================

  /**
   * Base class for constructing execution messages for static method calls. Contains the shared
   * fields and constructor logic used by both {@link PeerCall} and {@link LogCall}.
   */
  protected static class BaseStaticMethodCallBuilder {

    /** The UUID of the ThinPeer initiating the call. */
    protected final UUID thinPeerUuid;

    /** The array of parameter types for the target method (always String[]). */
    protected final Class<?>[] parameterTypes = new Class[] {String[].class};

    /** The simple name of the method to invoke. */
    protected final String methodName;

    /** The fully qualified name of the class containing the method. */
    protected final String className;

    /** The array of parameter type names, derived from {@code parameterTypes}. */
    protected final String[] parameterTypesNamesArray;

    /** The actual parameter values to pass to the method (wrapped in an Object[]). */
    protected final Object[] parameters;

    /** References for any object parameters. */
    protected final ObjectRef[] argObjRefs;

    /**
     * Constructs a new {@code BaseStaticMethodCallBuilder}.
     *
     * @param thinPeerUuid the UUID of the ThinPeer
     * @param className the name of the class whose method is to be called
     * @param methodName the name of the method to call
     * @param argList the list of arguments to pass to the method
     */
    protected BaseStaticMethodCallBuilder(
        UUID thinPeerUuid, String className, String methodName, List<String> argList) {
      parameterTypesNamesArray = new String[parameterTypes.length];
      IntStream.range(0, parameterTypes.length)
          .forEach(i -> parameterTypesNamesArray[i] = parameterTypes[i].getName());
      parameters = new Object[] {new String[] {}};
      argObjRefs = new ObjectRef[parameterTypes.length];
      this.thinPeerUuid = thinPeerUuid;
      this.className = className;
      this.methodName = methodName;
      if (argList != null) {
        parameters[0] = argList.toArray(new String[0]);
      }
    }
  }
}
