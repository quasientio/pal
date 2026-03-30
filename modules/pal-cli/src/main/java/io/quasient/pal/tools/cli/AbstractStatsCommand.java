/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.tools.cli;

import com.google.common.base.Splitter;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.Reflectable;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.tools.stats.Counters;
import java.io.IOException;

/**
 * Base class for stats commands, providing shared counter logic for message processing.
 *
 * <p>Both {@link LogStats} and {@link PeerStats} extend this class to share the common message
 * counter update logic, field increment methods, and short class name extraction.
 *
 * @see LogStats
 * @see PeerStats
 * @see Counters
 */
abstract class AbstractStatsCommand extends AbstractPalSubcommand {

  /** Aggregates various statistics counters for processed messages. */
  private final Counters counters = new Counters();

  /** Constructs a new {@code AbstractStatsCommand} instance. */
  protected AbstractStatsCommand() {}

  /**
   * Retrieves the counters containing aggregated statistics.
   *
   * @return the {@link Counters} instance with current statistics
   */
  public Counters getCounters() {
    return counters;
  }

  /**
   * Extracts the short class name from a fully qualified class name.
   *
   * @param classname the fully qualified class name
   * @return the short class name without package prefixes
   */
  String getShortClassname(String classname) {
    String[] classnameParts = Splitter.on('.').splitToList(classname).toArray(new String[0]);
    return classnameParts.length > 0 ? classnameParts[classnameParts.length - 1] : classname;
  }

  /**
   * Updates the counters based on the provided message.
   *
   * <p>Increments total message count and updates counts by message type, peer UUID, thread name,
   * and field access types according to the message content.
   *
   * @param message the {@link Message} to process and update counters for
   * @throws IllegalStateException if an unexpected message type is encountered
   */
  void updateCounters(Message message) {
    counters.getNumberOfMessages().getAndIncrement();
    counters.incrementMessagesByType(getMessageTypeName(message));
    counters.incrementMessagesFromPeer(getPeerUuid(message));

    final ExecMessage execMessage = message.getExecMessage();
    if (execMessage == null) {
      return;
    }

    counters.incrementMessagesByThread(execMessage.getThreadName());
    counters.updateTimeSpan(execMessage.getCurrentTime());

    if (execMessage.getEntryPoint()) {
      counters.getEntryPointCount().incrementAndGet();
    }

    String className;
    String methodName;
    String fieldName;
    String classFieldKey;
    MessageType messageType = MessageType.fromId(message.getMessageType());
    switch (messageType) {
      case EXEC_CONSTRUCTOR -> {
        String objClassKey = execMessage.getConstructorCall().getClazz().getName();
        incrementObjectsCreated(objClassKey);
      }
      case EXEC_INSTANCE_METHOD -> {
        className = execMessage.getInstanceMethodCall().getClazz().getName();
        methodName = execMessage.getInstanceMethodCall().getName();
        String classMethodKey = String.format("%s.%s()", getShortClassname(className), methodName);
        incrementMethodCalls(classMethodKey);
      }
      case EXEC_CLASS_METHOD -> {
        className = execMessage.getClassMethodCall().getClazz().getName();
        methodName = execMessage.getClassMethodCall().getName();
        String classMethodKey = String.format("%s.%s()", getShortClassname(className), methodName);
        incrementMethodCalls(classMethodKey);
      }
      case EXEC_GET_STATIC -> {
        className = execMessage.getStaticFieldGet().getClazz().getName();
        fieldName = execMessage.getStaticFieldGet().getField().getName();
        classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        incrementFieldReads(classFieldKey);
      }
      case EXEC_GET_FIELD -> {
        className = execMessage.getInstanceFieldGet().getClazz().getName();
        fieldName = execMessage.getInstanceFieldGet().getField().getName();
        classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        incrementFieldReads(classFieldKey);
      }
      case EXEC_PUT_STATIC -> {
        className = execMessage.getStaticFieldPut().getClazz().getName();
        fieldName = execMessage.getStaticFieldPut().getField().getName();
        classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        incrementFieldWrites(classFieldKey);
      }
      case EXEC_PUT_FIELD -> {
        className = execMessage.getInstanceFieldPut().getClazz().getName();
        fieldName = execMessage.getInstanceFieldPut().getField().getName();
        classFieldKey = String.format("%s.%s", getShortClassname(className), fieldName);
        incrementFieldWrites(classFieldKey);
      }
      case EXEC_THROWABLE -> updateExceptionCounters(execMessage);
      default -> {}
    }
  }

  /**
   * Updates exception-related counters from a throwable message.
   *
   * <p>Extracts the exception type and the method from which it was thrown, incrementing the
   * corresponding counters.
   *
   * @param execMessage the exec message containing the raised throwable
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private void updateExceptionCounters(ExecMessage execMessage) {
    RaisedThrowable raised = execMessage.getRaisedThrowable();
    if (raised == null) {
      return;
    }
    io.quasient.pal.messages.colfer.Throwable thrown = raised.getThrowable();
    if (thrown != null && thrown.getType() != null) {
      counters.incrementExceptionsByType(thrown.getType());
    }
    Reflectable from = raised.getFrom();
    if (from != null && from.getMethod() != null && from.getMethod().getName() != null) {
      String fromClass =
          from.getMethod().getClazz() != null ? from.getMethod().getClazz().getName() : "";
      String methodKey =
          String.format("%s.%s()", getShortClassname(fromClass), from.getMethod().getName());
      counters.incrementExceptionsPerMethod(methodKey);
    }
  }

  /**
   * Increments the count of objects created for a specific class.
   *
   * @param key the class name key representing the object type
   */
  void incrementObjectsCreated(String key) {
    counters.incrementObjectsCreated(key);
  }

  /**
   * Increments the count of method calls for a specific method.
   *
   * @param key the key representing the method (e.g., "ClassName.methodName()")
   */
  void incrementMethodCalls(String key) {
    counters.incrementMethodsCalled(key);
  }

  /**
   * Increments the count of field read accesses for a specific field.
   *
   * @param key the key representing the field (e.g., "ClassName.fieldName")
   */
  void incrementFieldReads(String key) {
    counters.incrementFieldReads(key);
  }

  /**
   * Increments the count of field write accesses for a specific field.
   *
   * @param key the key representing the field (e.g., "ClassName.fieldName")
   */
  void incrementFieldWrites(String key) {
    counters.incrementFieldWrites(key);
  }

  /**
   * Closes resources. Overridden to handle the case where no directory connection was established,
   * which occurs when stats commands are used programmatically (not via CLI).
   *
   * @throws IOException if an I/O error occurs while closing resources
   */
  @Override
  protected void closeResources() throws IOException {
    if (directoryConnectionProvider != null) {
      super.closeResources();
    }
  }
}
